import com.google.gson.JsonObject;
import dev.rbd.network.*;
import dev.rbd.io.LosslessPng;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class AsyncImageUploadSelfTest {
    private static JsonObject chunk(String id,int part,int count,String data){
        var msg=new JsonObject();msg.addProperty("id",id);msg.addProperty("part",part);msg.addProperty("count",count);msg.addProperty("data",data);
        if(part==0){msg.addProperty("encoding","png");msg.addProperty("width",257);msg.addProperty("height",113);}return msg;
    }
    public static void main(String[] args)throws Exception{
        int[] pixels=new int[257*113];var random=new Random(75);for(int i=0;i<pixels.length;i++)pixels[i]=random.nextInt();
        byte[] png=LosslessPng.encode(257,113,pixels);String encoded=Base64.getEncoder().encodeToString(png);
        int split=encoded.length()/2;var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var done=new CompletableFuture<PreparedImage>();var acknowledgements=new AtomicInteger();var caller=Thread.currentThread();
        try(var receiver=new AsyncImageUpload()){
            receiver.accept(chunk("frame",0,2,encoded.substring(0,split)),(image,error)->{
                if(error!=null||image!=null||Thread.currentThread()==caller)done.completeExceptionally(new AssertionError("partial image or decoding on caller"));
                acknowledgements.incrementAndGet();entered.countDown();try{release.await();}catch(InterruptedException ex){Thread.currentThread().interrupt();}
            });
            if(!entered.await(5,TimeUnit.SECONDS))throw new AssertionError("worker did not start");
            long start=System.nanoTime();
            receiver.accept(chunk("frame",1,2,encoded.substring(split)),(image,error)->{
                if(error!=null)done.completeExceptionally(error);
                else if(acknowledgements.getAndIncrement()!=1||Thread.currentThread()==caller)done.completeExceptionally(new AssertionError("out of order or caller-thread decoding"));
                else done.complete(image);
            });
            if(System.nanoTime()-start>TimeUnit.MILLISECONDS.toNanos(100)||done.isDone())throw new AssertionError("enqueue blocked on earlier processing");
            release.countDown();var image=done.get(5,TimeUnit.SECONDS);
            if(image.width()!=257||image.height()!=113||!Arrays.equals(png,Base64.getDecoder().decode(image.png())))throw new AssertionError("PNG bytes or resolution changed");
            var invalid=new CompletableFuture<Throwable>();receiver.accept(chunk("bad",1,2,"bad"),(value,error)->invalid.complete(error));
            if(!(invalid.get(5,TimeUnit.SECONDS) instanceof java.io.IOException))throw new AssertionError("out-of-order data accepted");
        }finally{release.countDown();}
        try{PreparedImage.validate(258,113,encoded);throw new AssertionError("PNG header mismatch accepted");}catch(java.io.IOException expected){}
        try(var receiver=new AsyncImageUpload()){
            receiver.close();var closed=new CompletableFuture<Boolean>();receiver.accept(chunk("closed",0,1,encoded),(image,error)->closed.complete(image==null));
            if(!closed.get(5,TimeUnit.SECONDS))throw new AssertionError("closed generation published an image");
        }
        System.out.println("AsyncImageUploadSelfTest: ordered background assembly, nonblocking enqueue, exact PNG, dimensions, malformed chunks and closed generation PASS");
    }
}
