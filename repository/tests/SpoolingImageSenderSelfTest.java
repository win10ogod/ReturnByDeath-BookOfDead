import dev.rbd.io.SpoolingImageSender;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class SpoolingImageSenderSelfTest {
    private static int checks;
    private static void check(boolean condition,String detail){checks++;if(!condition)throw new AssertionError(detail);}
    private static void until(java.util.function.BooleanSupplier ready,String detail)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!ready.getAsBoolean()){
            if(System.nanoTime()>end)throw new AssertionError(detail);Thread.sleep(5);
        }
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("rbd-upload-test-");
        var packets=new LinkedBlockingQueue<SpoolingImageSender.Chunk>();
        var sender=new SpoolingImageSender(root,packets::add);
        byte[] first=new byte[SpoolingImageSender.CHUNK_BYTES*11+7],second=new byte[100_003];
        new Random(51).nextBytes(first);new Random(62).nextBytes(second);
        sender.enqueue(1920,1080,first);
        until(()->packets.size()==SpoolingImageSender.WINDOW_CHUNKS,"Initial send window");
        Thread.sleep(80);check(packets.size()==SpoolingImageSender.WINDOW_CHUNKS,"Network window overflowed without an ACK");
        var executor=Executors.newSingleThreadExecutor();
        executor.submit(()->{sender.enqueue(7680,4320,second);return null;}).get(2,TimeUnit.SECONDS);
        check(sender.pendingBytes()==first.length+second.length,"Encoder blocked or discarded data on a stalled connection");
        var peek=packets.peek();sender.acknowledge("obsolete-session",0,true);sender.acknowledge(peek.id(),9,true);
        Thread.sleep(30);check(packets.size()==SpoolingImageSender.WINDOW_CHUNKS,"Stale or out of order ACK opened the window");
        for(byte[] expected:List.of(first,second)){
            String id=null;var data=new StringBuilder();int count=0;
            do{
                var chunk=packets.poll(2,TimeUnit.SECONDS);check(chunk!=null,"Missing queued frame chunk");
                if(id==null)id=chunk.id();check(id.equals(chunk.id()),"Frames interleaved");
                check(chunk.part()==count++,"Chunk order changed");
                check(chunk.width()==(expected==first?1920:7680)&&chunk.height()==(expected==first?1080:4320),"Resolution changed");
                data.append(chunk.data());sender.acknowledge(chunk.id(),chunk.part(),true);
                sender.acknowledge(chunk.id(),chunk.part(),true); // Duplicate acknowledgement must be harmless.
                if(count==chunk.count())break;
            }while(true);
            check(Arrays.equals(expected,Base64.getDecoder().decode(data.toString())),"Lossless bytes changed");
        }
        until(()->sender.pendingBytes()==0,"Spool did not drain");
        try(var files=Files.list(sender.directory())){check(files.count()==0,"Delivered frames retained on disk");}
        sender.enqueue(1920,1080,first);until(()->!packets.isEmpty(),"Stalled disconnect setup");
        long start=System.nanoTime();sender.close();check(System.nanoTime()-start<TimeUnit.MILLISECONDS.toNanos(100),"Disconnect waited for ACK");
        until(sender::terminated,"Sender did not stop after disconnect");check(!Files.exists(sender.directory()),"Session staging leaked after disconnect");
        var rejectedPackets=new LinkedBlockingQueue<SpoolingImageSender.Chunk>();
        var rejected=new SpoolingImageSender(root,rejectedPackets::add);rejected.enqueue(1,1,second);
        var chunk=rejectedPackets.poll(2,TimeUnit.SECONDS);rejected.acknowledge(chunk.id(),chunk.part(),false);
        until(rejected::terminated,"Rejection did not stop sender");
        try{rejected.check();throw new AssertionError("Rejected upload was silently ignored");}catch(IOException expected){checks++;}
        rejected.close();executor.shutdownNow();Files.delete(root);
        System.out.println("SpoolingImageSenderSelfTest: "+checks+" checks passed (slow ACK, FIFO, exact bytes, 8K dimensions, stale ACK, disconnect, staging cleanup, server rejection)");
    }
}
