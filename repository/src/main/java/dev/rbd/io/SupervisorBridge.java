package dev.rbd.io;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
/** No world files are copied/replaced by this class. The supervisor waits for JVM exit. */
public final class SupervisorBridge {
    private final Path control,world;
    private final String token;
    private String pendingId;
    public SupervisorBridge(MinecraftServer server)throws IOException {
        if(!server.isDedicatedServer())throw new IOException("Integrated-server rollback is not implemented in this draft");
        String c=System.getenv("RBD_CONTROL_DIR"),t=System.getenv("RBD_SUPERVISOR_TOKEN");
        if(c==null||t==null||t.length()<32)throw new IOException("Start via supervisor.py; no safe rollback supervisor found");
        control=Path.of(c).toRealPath();world=server.getWorldPath(LevelResource.ROOT).toRealPath();token=t;
        if(control.startsWith(world)||world.startsWith(control))throw new IOException("Control/world paths must be disjoint");
        JsonObject session=AtomicJson.read(control.resolve("session.json"));
        if(!session.get("token").getAsString().equals(token)||!Path.of(session.get("world").getAsString()).equals(world))
            throw new IOException("Supervisor session mismatch");
        if(Files.exists(control.resolve("request.json")))throw new IOException("Unresolved transaction; fail closed");
    }
    public Path control(){return control;}
    public synchronized void fault(String reason)throws IOException {
        JsonObject data=new JsonObject();data.addProperty("schema",1);data.addProperty("reason",reason);
        data.addProperty("world",world.toString());AtomicJson.write(control.resolve("fault.json"),data);
    }
    public JsonObject active()throws IOException {
        Path p=control.resolve("active_checkpoint.json");return Files.exists(p)?AtomicJson.read(p):null;
    }
    public synchronized String request(String operation,JsonObject death)throws IOException {
        if(pendingId!=null)throw new IOException("Transaction already pending");
        String id=UUID.randomUUID().toString();JsonObject active=active(),request=new JsonObject();
        request.addProperty("schema",1);request.addProperty("request_id",id);request.addProperty("token",token);
        request.addProperty("operation",operation);request.addProperty("world",world.toString());
        request.addProperty("checkpoint_id",active==null?"":active.get("checkpoint_id").getAsString());
        if(death!=null)request.add("death",death);
        AtomicJson.write(control.resolve("request.json"),request);pendingId=id;return id;
    }
    public synchronized void stoppedNormally()throws IOException {
        if(pendingId==null)return;
        JsonObject marker=new JsonObject();marker.addProperty("request_id",pendingId);marker.addProperty("token",token);
        AtomicJson.write(control.resolve("clean_stop.json"),marker);
    }
}
