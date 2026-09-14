import dev.rbd.io.*;
import dev.rbd.memory.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.io.*;

public final class ArchiveStreamingSelfTest {
    private static MemoryFrame frame(long tick,String png){
        return new MemoryFrame(tick,"minecraft:overworld",0,1,2,0,0,20,"繁體中文 <>& \\ \"\n",
            List.of(),List.of(),"CLIENT_FIRST_PERSON",1920,1080,new int[0],png,null,100);
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0){limitedHeap(Path.of(args[0]));return;}
        Path root=Files.createTempDirectory("rbd-streaming-test-");
        try{
            // Serialization bytes and old JSONL segment compatibility, including escaped text.
            try(var archive=new MemoryArchive(root.resolve("format"),1024)){
                var empty=archive.begin("");empty.close();
                var segment=archive.begin(empty.id);var sample=frame(7,"png");segment.append(sample);segment.close();
                String expected=MemoryArchive.GSON.toJson(sample)+System.lineSeparator();
                try(var input=new GZIPInputStream(Files.newInputStream(archive.root().resolve("segments").resolve(segment.id+".jsonl.gz")))){
                    if(!new String(input.readAllBytes(),StandardCharsets.UTF_8).equals(expected))throw new AssertionError("JSONL format changed");
                }
                try(var reader=archive.reader(segment.id)){
                    if(!MemoryArchive.GSON.toJson(reader.next()).equals(MemoryArchive.GSON.toJson(sample))||reader.next()!=null)throw new AssertionError("archive contents changed");
                }
            }
            int[] pixels=new int[1920*1080];var random=new Random(76);
            for(int i=0;i<pixels.length;i++)pixels[i]=random.nextInt();
            Files.writeString(root.resolve("png.txt"),Base64.getEncoder().encodeToString(LosslessPng.encode(1920,1080,pixels)));
            String executable=System.getProperty("os.name").startsWith("Windows")?"java.exe":"java";
            var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",executable).toString(),
                "-Xmx48m","-cp",System.getProperty("java.class.path"),ArchiveStreamingSelfTest.class.getName(),root.toString()).inheritIO().start();
            if(!process.waitFor(90,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("limited-heap archive timed out");}
            if(process.exitValue()!=0)throw new AssertionError("full-resolution archive exhausted the limited heap");
            // A failed write and the subsequent close must preserve the original failure.
            try(var queue=new OrderedIo("RBD failure cleanup test",1024)){
                OrderedIo.await(queue.submit(1,()->{throw new IOException("original storage failure");}));
                throw new AssertionError("failure hidden");
            }catch(IOException expected){
                if(!expected.getMessage().equals("original storage failure")||expected.getSuppressed().length!=1)throw new AssertionError("cleanup masked original failure",expected);
            }
            System.out.println("ArchiveStreamingSelfTest: exact JSONL, original failure preserved, 16 native 1080p PNG frames written/read in a 48 MiB heap PASS");
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
    private static void limitedHeap(Path root)throws Exception{
        String png=Files.readString(root.resolve("png.txt"));
        try(var archive=new MemoryArchive(root.resolve("large"),64*1048576L)){
            var segment=archive.begin("");for(int i=0;i<16;i++)segment.append(frame(i,png));segment.close();
            int count=0;try(var reader=archive.reader(segment.id)){
                MemoryFrame value;while((value=reader.next())!=null){
                    if(!png.equals(value.png())||value.tick()!=count||value.sampleTicks()!=100)throw new AssertionError("PNG bytes, cadence or order changed");count++;
                }
            }
            if(count!=16)throw new AssertionError("frames lost");
        }
    }
}
