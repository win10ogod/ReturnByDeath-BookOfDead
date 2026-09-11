package dev.rbd.memory;

import com.google.gson.*;
import dev.rbd.io.AtomicJson;
import dev.rbd.io.SnapshotStore;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Immutable shared-prefix segment graph. World NBT holds branch heads; the archive survives returns. */
public final class MemoryArchive {
    public static final Gson GSON = new Gson();
    private final Path root;
    public MemoryArchive(Path root) throws IOException {
        this.root=root; Files.createDirectories(root.resolve("segments")); Files.createDirectories(root.resolve("books"));
    }
    public Path root(){return root;}
    public Segment begin(String parent) throws IOException {return new Segment(parent);}
    public final class Segment implements AutoCloseable {
        public final String id=UUID.randomUUID().toString();
        private final String parent;
        private final Path path;
        private final BufferedWriter writer;
        private long count;
        private boolean closed;
        Segment(String parent) throws IOException {
            this.parent=parent;path=root.resolve("segments").resolve(id+".jsonl.gz");
            writer=new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(path,StandardOpenOption.CREATE_NEW),65536),65536),java.nio.charset.StandardCharsets.UTF_8),65536);
        }
        public void append(MemoryFrame frame) throws IOException {if(closed)throw new IOException("Sealed memory segment");writer.write(GSON.toJson(frame));writer.newLine();count++;}
        public long count(){return count;}
        public void close() throws IOException {
            if(closed)return; writer.close();
            try(FileChannel f=FileChannel.open(path,StandardOpenOption.WRITE)){f.force(true);}
            JsonObject meta=new JsonObject();meta.addProperty("id",id);meta.addProperty("parent",parent);meta.addProperty("frames",count);meta.addProperty("sha256",SnapshotStore.hash(path));
            AtomicJson.write(root.resolve("segments").resolve(id+".json"),meta);closed=true;
        }
    }
    public JsonObject sealBook(UUID soul,String name,String head,String branch,boolean authority,String coverage,String cause) throws IOException {
        JsonObject book=new JsonObject();String id=UUID.randomUUID().toString();
        book.addProperty("id",id);book.addProperty("life",UUID.randomUUID().toString());book.addProperty("soul",soul.toString());
        book.addProperty("name",name);book.addProperty("head",head);book.addProperty("branch",branch);book.addProperty("authority",authority);
        book.addProperty("coverage",coverage);book.addProperty("cause",cause);book.addProperty("created",System.currentTimeMillis());
        AtomicJson.write(root.resolve("books").resolve(id+".json"),book);return book;
    }
    public List<JsonObject> books() throws IOException {
        List<JsonObject> result=new ArrayList<>();
        try(var files=Files.list(root.resolve("books"))){for(Path file:files.filter(p->p.toString().endsWith(".json")).sorted().toList())result.add(AtomicJson.read(file));}
        result.sort(Comparator.comparingLong(b->b.get("created").getAsLong()));return result;
    }
    public JsonObject book(String id) throws IOException {return AtomicJson.read(root.resolve("books").resolve(UUID.fromString(id)+".json"));}
    public ReaderFrames reader(String head) throws IOException {return new ReaderFrames(head);}
    public final class ReaderFrames implements AutoCloseable {
        private record SegmentRef(Path path,String checksum){}
        private final Deque<SegmentRef> segments=new ArrayDeque<>();
        private BufferedReader reader;
        ReaderFrames(String head) throws IOException {
            Set<String> visited=new HashSet<>();
            while(!head.isEmpty()){
                head=UUID.fromString(head).toString();if(!visited.add(head))throw new IOException("Cyclic memory prefix");
                JsonObject meta=AtomicJson.read(root.resolve("segments").resolve(head+".json"));
                Path path=root.resolve("segments").resolve(head+".jsonl.gz");
                segments.addFirst(new SegmentRef(path,meta.get("sha256").getAsString()));head=meta.get("parent").getAsString();
            }
        }
        public MemoryFrame next() throws IOException {
            for(;;){
                if(reader==null){if(segments.isEmpty())return null;var segment=segments.removeFirst();if(!SnapshotStore.hash(segment.path()).equals(segment.checksum()))throw new IOException("Memory checksum mismatch");reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(segment.path()),65536),65536),java.nio.charset.StandardCharsets.UTF_8),65536);}
                String line=reader.readLine();if(line!=null)return GSON.fromJson(line,MemoryFrame.class);
                reader.close();reader=null;
            }
        }
        public void close() throws IOException {if(reader!=null)reader.close();segments.clear();}
    }
}
