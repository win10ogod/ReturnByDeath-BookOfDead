package dev.rbd.network;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
/** Bounded transport assembly for one rendered image; the archive itself has no lifetime/retention cap. */
public final class ImageUpload {
    private String id;
    private int next,count;
    private StringBuilder data;
    private boolean png;
    private int width,height;
    public JsonObject accept(JsonObject chunk) throws IOException {
        String incoming=chunk.get("id").getAsString();int part=chunk.get("part").getAsInt(),total=chunk.get("count").getAsInt();
        if(total<1||total>4096)throw new IOException("Invalid image transport size");
        if(part==0){id=incoming;next=0;count=total;data=new StringBuilder();png=chunk.has("encoding")&&chunk.get("encoding").getAsString().equals("png");if(png){width=chunk.get("width").getAsInt();height=chunk.get("height").getAsInt();if(width<1||height<1||(long)width*height>64000000L)throw new IOException("Invalid image dimensions");}}
        if(data==null||!Objects.equals(id,incoming)||part!=next||count!=total)throw new IOException("Out of order memory image");
        data.append(chunk.get("data").getAsString());next++;
        if(next<count)return null;
        if(png){var view=new JsonObject();view.addProperty("width",width);view.addProperty("height",height);view.addProperty("png",data.toString());data=null;return view;}
        byte[] encoded=Base64.getDecoder().decode(data.toString());data=null;
        try(var in=new GZIPInputStream(new ByteArrayInputStream(encoded))){
            byte[] decoded=in.readNBytes(128*1024*1024+1);if(decoded.length>128*1024*1024)throw new IOException("Image exceeds the transport allocation");
            return JsonParser.parseString(new String(decoded,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
