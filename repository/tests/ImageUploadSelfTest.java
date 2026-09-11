import com.google.gson.*;
import dev.rbd.io.LosslessPng;
import dev.rbd.network.ImageUpload;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public final class ImageUploadSelfTest {
    private static JsonObject chunk(String id,int part,int count,String data){var o=new JsonObject();o.addProperty("id",id);o.addProperty("part",part);o.addProperty("count",count);o.addProperty("data",data);return o;}
    public static void main(String[] args)throws Exception{
        byte[] png=LosslessPng.encode(3,2,new int[]{0xff00ffaa,0,0x12345678,-1,42,16777215});String encoded=Base64.getEncoder().encodeToString(png);
        var upload=new ImageUpload();var first=chunk("one",0,2,encoded.substring(0,13));first.addProperty("encoding","png");first.addProperty("width",3);first.addProperty("height",2);
        if(upload.accept(first)!=null)throw new AssertionError("Incomplete PNG emitted");
        var decoded=upload.accept(chunk("one",1,2,encoded.substring(13)));
        if(decoded.get("width").getAsInt()!=3||decoded.get("height").getAsInt()!=2||!Arrays.equals(png,Base64.getDecoder().decode(decoded.get("png").getAsString())))throw new AssertionError("PNG transport changed bytes/dimensions");
        var zipped=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(zipped)){gzip.write(decoded.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        var legacy=upload.accept(chunk("two",0,1,Base64.getEncoder().encodeToString(zipped.toByteArray())));
        if(!legacy.equals(decoded))throw new AssertionError("Legacy image transport lost compatibility");
        try{upload.accept(chunk("missing",1,2,"lost"));throw new AssertionError("Out-of-order image accepted");}catch(IOException expected){}
        first.addProperty("width",0);try{upload.accept(first);throw new AssertionError("Invalid dimensions accepted");}catch(IOException expected){}
        System.out.println("ImageUploadSelfTest: exact PNG bytes, legacy GZIP, partial images and ordering/dimension guards passed");
    }
}
