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
        System.out.println("AsyncMemorySelfTest: 201 exact frames, mutable input isolation, oversize backpressure, prefix ordering and I/O failure propagation PASS");
    }
}
