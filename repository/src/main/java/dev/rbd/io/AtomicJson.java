package dev.rbd.io;
import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.UUID;
import static java.nio.file.StandardOpenOption.*;
public final class AtomicJson {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private AtomicJson(){}
    public static JsonObject read(Path file)throws IOException {
        try(Reader in=Files.newBufferedReader(file)){
            JsonElement e=JsonParser.parseReader(in);
            if(!e.isJsonObject())throw new IOException("Expected object: "+file);
            return e.getAsJsonObject();
        }catch(JsonParseException|IllegalStateException e){throw new IOException("Invalid JSON: "+file,e);}
    }
    public static void write(Path target,JsonObject data)throws IOException {
        Files.createDirectories(target.getParent());
        Path temp=target.resolveSibling(target.getFileName()+".tmp-"+UUID.randomUUID());
        byte[] bytes=GSON.toJson(data).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try{
            try(FileChannel channel=FileChannel.open(temp,CREATE_NEW,WRITE)){
                ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
            }
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            // Windows does not expose directory fsync through FileChannel. File contents are forced before atomic replacement.
            if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win"))
                try(FileChannel directory=FileChannel.open(target.getParent(),READ)){directory.force(true);}
        }finally{Files.deleteIfExists(temp);}
    }
}
