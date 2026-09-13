package dev.rbd.io;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Lossless FIFO staging. Only this daemon waits for delivery; callers never wait for the network. */
public final class SpoolingImageSender implements AutoCloseable {
    public static final int CHUNK_BYTES=18_000, WINDOW_CHUNKS=4;
    public record Chunk(String id,int part,int count,int width,int height,String data) {}
    @FunctionalInterface public interface Transport {void send(Chunk chunk) throws Exception;}
    private record Frame(Path path,String id,int width,int height,long size) {}
    private final Path directory;
    private final Transport transport;
    private final BlockingQueue<Frame> frames=new LinkedBlockingQueue<>();
    private final AtomicInteger writers=new AtomicInteger();
    private final Thread worker;
    private final Object delivery=new Object();
    private volatile boolean closed;
    private volatile IOException failure;
    private String currentId;
    private int sent,acknowledged;
    private final java.util.concurrent.atomic.AtomicLong pendingBytes=new java.util.concurrent.atomic.AtomicLong();

    public SpoolingImageSender(Path parent,Transport transport) throws IOException {
        Files.createDirectories(parent);directory=Files.createTempDirectory(parent,"upload-");this.transport=transport;
        worker=new Thread(this::run,"RBD memory upload");worker.setDaemon(true);worker.start();
    }
    /** Called by the PNG encoder, never by the game thread. Disk staging does not depend on socket speed. */
    public void enqueue(int width,int height,byte[] png) throws IOException {
        writers.incrementAndGet();Path file=null;
        try {
            check();if(closed)return;
            String id=UUID.randomUUID().toString();file=directory.resolve(id+".png");Files.write(file,png);
            pendingBytes.addAndGet(png.length);frames.add(new Frame(file,id,width,height,png.length));file=null;
        }finally {if(file!=null)Files.deleteIfExists(file);writers.decrementAndGet();}
    }
    public void check() throws IOException {if(failure!=null)throw failure;}
    public long pendingBytes(){return pendingBytes.get();}
    public Path directory(){return directory;}
    public boolean terminated(){return !worker.isAlive();}
    /** ACK handling is safe on the network thread and does not perform I/O. */
    public void acknowledge(String id,int part,boolean accepted) {
        synchronized(delivery){
            if(closed||!Objects.equals(currentId,id)||part!=acknowledged||part>=sent)return;
            if(!accepted)failure=new IOException("Server rejected a memory image chunk");
            else acknowledged++;
            delivery.notifyAll();
        }
    }
    private void run(){
        try {
            while(!closed){
                Frame frame=frames.take();
                synchronized(delivery){currentId=frame.id;sent=acknowledged=0;}
                int count=Math.toIntExact((frame.size+CHUNK_BYTES-1)/CHUNK_BYTES);
                if(count<1||count>4096)throw new IOException("Invalid image transport size");
                try(InputStream input=Files.newInputStream(frame.path)){
                    for(int part=0;part<count&&!closed;part++){
                        synchronized(delivery){
                            while(!closed&&failure==null&&sent-acknowledged>=WINDOW_CHUNKS)delivery.wait();
                            check();if(closed)break;sent++;
                        }
                        byte[] bytes=input.readNBytes(CHUNK_BYTES);
                        transport.send(new Chunk(frame.id,part,count,frame.width,frame.height,Base64.getEncoder().encodeToString(bytes)));
                    }
                    synchronized(delivery){
                        while(!closed&&failure==null&&acknowledged<count)delivery.wait();
                        check();
                    }
                }
                if(!closed){Files.delete(frame.path);pendingBytes.addAndGet(-frame.size);}
            }
        }catch(InterruptedException interrupted){if(!closed)failure=new IOException("Memory upload interrupted",interrupted);}
        catch(Exception error){failure=error instanceof IOException io?io:new IOException("Memory upload failed",error);}
        finally {
            closed=true;
            // A disconnect must not make the game thread join a sender waiting on the network or disk.
            while(writers.get()!=0){try{Thread.sleep(1);}catch(InterruptedException ignored){}}
            try(var files=Files.list(directory)){for(Path file:files.toList())Files.deleteIfExists(file);}
            catch(IOException error){if(failure==null)failure=error;}
            try{Files.deleteIfExists(directory);}catch(IOException error){if(failure==null)failure=error;}
            frames.clear();pendingBytes.set(0);
        }
    }
    @Override public void close(){closed=true;worker.interrupt();synchronized(delivery){delivery.notifyAll();}}
}
