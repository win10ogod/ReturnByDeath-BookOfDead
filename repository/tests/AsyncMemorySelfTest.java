import dev.rbd.io.*;
import dev.rbd.memory.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;

public final class AsyncMemorySelfTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var producer=Executors.newSingleThreadExecutor();
        try(var queue=new OrderedIo("RBD queue regression",1024)){
            queue.submit(8192,()->{entered.countDown();release.await();});entered.await();
            var next=producer.submit(()->{try{queue.submit(256,()->{});return true;}catch(IOException e){throw new RuntimeException(e);}});
            try{next.get(100,TimeUnit.MILLISECONDS);throw new AssertionError("oversized frame must apply backpressure, not drop or exceed its budget");}catch(TimeoutException expected){}
            check(queue.pendingBytes()==8192,"full oversized frame retained alone");release.countDown();next.get(3,TimeUnit.SECONDS);queue.flush();
        }finally{release.countDown();producer.shutdownNow();}
        var failed=new OrderedIo("RBD failure regression",1024);
        var failure=failed.submit(1,()->{throw new IOException("fixture disk full");});
        try{OrderedIo.await(failure);throw new AssertionError("write failure hidden");}catch(IOException expected){check(expected.getMessage().equals("fixture disk full"),"original write error surfaced");}
        try{failed.close();throw new AssertionError("flush failure hidden");}catch(IOException expected){}
        var root=Files.createTempDirectory("rbd-async-test-");
        try(var archive=new MemoryArchive(root,1024)){
            var first=archive.begin("");
            for(int i=0;i<200;i++){
                int[] pixels={0xFF123456,i};var sounds=new ArrayList<MemoryFrame.Sound>();sounds.add(new MemoryFrame.Sound("test:"+i,1,1));
                first.append(new MemoryFrame(i,"minecraft:overworld",i,2,3,0,0,20,"frame "+i,List.of(),sounds,"TEST",2,1,pixels,""));
                pixels[0]=0;sounds.clear();
            }
            first.close();var second=archive.begin(first.id);second.append(new MemoryFrame(200,"test:dimension",0,0,0,0,0,0,"ending",List.of(),List.of(),"TEST",1,1,new int[]{-1},"",new SomaticState(20,0,300,true,false,0,0,20,"drown",true)));second.close();
            try(var reader=archive.reader(second.id)){
                for(int i=0;i<200;i++){var frame=reader.next();check(frame!=null&&frame.tick()==i&&frame.pixels()[0]==0xFF123456&&frame.pixels()[1]==i&&frame.sounds().getFirst().id().equals("test:"+i),"all frames are detached, ordered and intact: "+i);}
                check(reader.next().body().terminal(),"terminal frame preserved after draining");check(reader.next()==null,"exact frame count");
            }
        }finally{try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        rotationBarrier();
        System.out.println("AsyncMemorySelfTest: exact frames, mutable input isolation, oversize backpressure, deferred rotation, durable prefix ordering and I/O failure propagation PASS");
    }
    private static void rotationBarrier() throws Exception {
        var root=Files.createTempDirectory("rbd-rotation-test-");var archive=new MemoryArchive(root,1048576);
        var release=new CountDownLatch(1);var entered=new CountDownLatch(1);var readerThread=Executors.newSingleThreadExecutor();
        try {
            var field=MemoryArchive.class.getDeclaredField("io");field.setAccessible(true);var queue=(OrderedIo)field.get(archive);
            queue.submit(1,()->{entered.countDown();release.await();});entered.await();
            var first=archive.begin("");
            first.append(new MemoryFrame(1,"minecraft:overworld",0,0,0,0,0,20,"before rotation",List.of(),List.of(),"TEST",1,1,new int[]{0xFF123456},""));
            first.closeAsync();
            var next=archive.begin(first.id);next.append(new MemoryFrame(2,"minecraft:overworld",0,0,0,0,0,20,"after rotation",List.of(),List.of(),"TEST",1,1,new int[]{0xFFABCDEF},""));next.closeAsync();
            check(!Files.exists(root.resolve("segments").resolve(first.id+".json")),"rotation returns while durable work is still pending");
            var read=readerThread.submit(()->{try(var reader=archive.reader(next.id)){check(reader.next().pixels()[0]==0xFF123456,"rotated prefix intact");check(reader.next().pixels()[0]==0xFFABCDEF,"following segment intact");check(reader.next()==null,"no dropped or duplicated rotated frames");return true;}});
            try{read.get(100,TimeUnit.MILLISECONDS);throw new AssertionError("reader exposed an unsealed prefix");}catch(TimeoutException expected){}
            release.countDown();check(read.get(3,TimeUnit.SECONDS),"reader drains deferred seals");
            archive.close();archive.flush(); // World saves after a transition may flush an already closed archive.
            check(Files.isRegularFile(root.resolve("segments").resolve(first.id+".json")),"durable prefix metadata exists after flush");
        } finally {
            release.countDown();archive.close();readerThread.shutdownNow();
            try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        }
    }
}
