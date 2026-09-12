package dev.rbd.io;

import java.io.IOException;
import java.util.concurrent.*;

/** Ordered background work with byte-weighted backpressure. Frames are never dropped. */
public final class OrderedIo implements AutoCloseable {
    @FunctionalInterface public interface Work { void run() throws Exception; }
    private final ExecutorService worker;
    private long budget;
    private long pending;
    private boolean closed;
    private IOException failure;

    public OrderedIo(String name,long budget) {
        if(budget<1)throw new IllegalArgumentException("Positive queue budget required");
        this.budget=budget;
        worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,name);t.setDaemon(true);return t;});
    }
    public synchronized void check() throws IOException {if(failure!=null)throw failure;}
    public synchronized long pendingBytes(){return pending;}
    public synchronized void budget(long bytes){if(bytes<1)throw new IllegalArgumentException("Positive queue budget required");budget=bytes;notifyAll();}
    public synchronized CompletableFuture<Void> submit(long bytes,Work work) throws IOException {
        long weight=Math.max(256,bytes);
        // One frame larger than the budget is accepted alone, at its full original resolution.
        while(!closed&&failure==null&&pending>0&&weight>budget-pending){
            try{wait();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Interrupted while waiting for memory I/O",e);}
        }
        check();if(closed)throw new IOException("Memory I/O queue is closed");
        pending+=weight;
        CompletableFuture<Void> result=new CompletableFuture<>();
        worker.execute(()->{
            try{check();work.run();result.complete(null);}
            catch(Throwable e){
                synchronized(this){if(failure==null)failure=e instanceof IOException io?io:new IOException("Background memory I/O failed",e);}
                result.completeExceptionally(failure);
            }finally{synchronized(this){pending-=weight;notifyAll();}}
        });
        return result;
    }
    public static void await(CompletableFuture<Void> result) throws IOException {
        try{result.get();}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Interrupted while draining memory I/O",e);}
        catch(ExecutionException e){throw e.getCause() instanceof IOException io?io:new IOException("Background memory I/O failed",e.getCause());}
    }
    public void flush() throws IOException {await(submit(1,()->{}));}
    @Override public void close() throws IOException {
        synchronized(this){if(closed){check();return;}}
        try{flush();}finally{synchronized(this){closed=true;notifyAll();}worker.shutdown();}
    }
}
