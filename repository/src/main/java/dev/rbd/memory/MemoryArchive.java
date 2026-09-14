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
public final class MemoryArchive implements AutoCloseable {
    public static final Gson GSON = new Gson();
    private final Path root;
    private final dev.rbd.io.OrderedIo io;
    private boolean closed;
    public MemoryArchive(Path root) throws IOException {
        this(root,0);
    }
    public MemoryArchive(Path root,long queueBytes) throws IOException {
        this.root=root; Files.createDirectories(root.resolve("segments")); Files.createDirectories(root.resolve("books"));
        io=queueBytes==0?null:new dev.rbd.io.OrderedIo("RBD memory archive",queueBytes);
    }
    public void check() throws IOException {if(io!=null)io.check();}
    public void queueBudget(long bytes){if(io!=null)io.budget(bytes);}
    public void flush() throws IOException {if(io!=null){if(closed)io.check();else io.flush();}}
    @Override public void close() throws IOException {if(io!=null)io.close();closed=true;}
    public Path root(){return root;}
    public Segment begin(String parent) throws IOException {return new Segment(parent);}
    public final class Segment implements AutoCloseable {
        public final String id=UUID.randomUUID().toString();
        private final String parent;
        private final Path path;
        private BufferedWriter writer;
        private long count;
        private boolean closed;
        private java.util.concurrent.CompletableFuture<Void> sealing;
        Segment(String parent) throws IOException {
            this.parent=parent;path=root.resolve("segments").resolve(id+".jsonl.gz");
            // Keep this constructor hook compatible with existing pack compression adapters.
            // Only the inexpensive stream setup stays here; serialization and compression run on the worker.
            writer=new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(path,StandardOpenOption.CREATE_NEW),65536),65536),java.nio.charset.StandardCharsets.UTF_8),65536);
        }
        public void append(MemoryFrame frame) throws IOException {
            if(closed)throw new IOException("Sealed memory segment");
            if(io==null)write(frame);
            else {
                // Detach the mutable collections before crossing the server-thread boundary.
                var copy=new MemoryFrame(frame.tick(),frame.dimension(),frame.x(),frame.y(),frame.z(),frame.yaw(),frame.pitch(),frame.health(),frame.caption(),List.copyOf(frame.contacts()),List.copyOf(frame.sounds()),frame.visualSource(),frame.width(),frame.height(),frame.pixels().clone(),frame.png(),frame.body(),frame.sampleTicks());
                long weight=512L+copy.pixels().length*4L+copy.png().length()*2L+copy.caption().length()*2L;
                for(var contact:copy.contacts())weight+=128L+contact.name().length()*2L+contact.appearance().length()*2L;
                for(var sound:copy.sounds())weight+=64L+sound.id().length()*2L;
                io.submit(weight,()->write(copy));
            }
            count++;
        }
        // Stream directly to the compressor. A complete JSON String duplicates the large PNG
        // and its expanding StringWriter buffer outside the configured queue's byte budget.
        private void write(MemoryFrame frame) throws IOException {GSON.toJson(frame,writer);writer.newLine();}
        public long count(){return count;}
        public void close() throws IOException {
            closeAsync();
            if(sealing!=null)dev.rbd.io.OrderedIo.await(sealing);
        }
        /** Routine rotation may enqueue sealing; persistence and terminal close still drain it. */
        public void closeAsync() throws IOException {
            if(closed){check();return;}
            if(io==null)seal();else sealing=io.submit(1,this::seal);
            closed=true;
        }
        private void seal() throws IOException {
            writer.close();writer=null;
            try(FileChannel f=FileChannel.open(path,StandardOpenOption.WRITE)){f.force(true);}
            JsonObject meta=new JsonObject();meta.addProperty("id",id);meta.addProperty("parent",parent);meta.addProperty("frames",count);meta.addProperty("sha256",SnapshotStore.hash(path));
            AtomicJson.write(root.resolve("segments").resolve(id+".json"),meta);
        }
    }
    public JsonObject sealBook(UUID soul,String name,String head,String branch,boolean authority,String coverage,String cause) throws IOException {
        flush();
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
        private com.google.gson.stream.JsonReader reader;
        ReaderFrames(String head) throws IOException {
            if(!head.isEmpty()&&!Files.isRegularFile(root.resolve("segments").resolve(UUID.fromString(head)+".json")))flush();
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
                if(reader==null){
                    if(segments.isEmpty())return null;var segment=segments.removeFirst();
                    if(!SnapshotStore.hash(segment.path()).equals(segment.checksum()))throw new IOException("Memory checksum mismatch");
                    var input=new BufferedReader(new InputStreamReader(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(segment.path()),65536),65536),java.nio.charset.StandardCharsets.UTF_8),65536);
                    // Reentrant unloads can seal a valid, empty prefix. JsonReader.peek() throws
                    // at the start of an empty document, so check EOF before creating the parser.
                    try{input.mark(1);if(input.read()==-1){input.close();continue;}input.reset();}
                    catch(IOException error){input.close();throw error;}
                    reader=new com.google.gson.stream.JsonReader(input);
                    // Existing segments contain consecutive JSON values separated by newlines.
                    reader.setLenient(true);
                }
                if(reader.peek()!=com.google.gson.stream.JsonToken.END_DOCUMENT)return GSON.fromJson(reader,MemoryFrame.class);
                reader.close();reader=null;
            }
        }
        public void close() throws IOException {if(reader!=null){reader.close();reader=null;}segments.clear();}
    }
}
