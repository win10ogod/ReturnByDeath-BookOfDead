package dev.rbd.network;

import com.google.gson.JsonObject;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/** One ordered receiver per connected player; acknowledgement follows background processing. */
public final class AsyncImageUpload implements AutoCloseable {
    private final ImageUpload assembly=new ImageUpload();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"RBD image receive");t.setDaemon(true);return t;});
    private volatile boolean closed;
    public synchronized void accept(JsonObject chunk,BiConsumer<PreparedImage,Throwable> completed) {
        if(closed){completed.accept(null,null);return;}
        worker.execute(()->{
            PreparedImage image=null;Throwable failure=null;
            try{
                if(!closed){
                    var view=assembly.accept(chunk);
                    if(view!=null)image=PreparedImage.validate(view.get("width").getAsInt(),view.get("height").getAsInt(),view.get("png").getAsString());
                }
            }catch(Exception error){failure=error;}
            completed.accept(closed?null:image,failure);
        });
    }
    /** Never joins background decoding on a logout or world transition. */
    @Override public synchronized void close(){closed=true;worker.shutdown();}
}
