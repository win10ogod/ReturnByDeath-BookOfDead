package dev.rbd.runtime;
import com.google.gson.*;
import dev.rbd.*;
import dev.rbd.io.*;
import dev.rbd.memory.*;
import dev.rbd.network.*;
import dev.rbd.core.CheckpointPolicy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.network.chat.Component;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class GameSession implements AutoCloseable {
    public static volatile GameSession current;
    public final MinecraftServer server;
    public final SnapshotStore snapshots;
    public final MemoryArchive archive;
    public final MemoryRecorder recorder;
    public final BranchData branch;
    public final SupervisorBridge supervisor;
    public final SaveFailureGuard saveGuard=new SaveFailureGuard();
    public final AuthorityRoster authorities;
    private final Map<UUID,JsonObject> pendingDeaths=new LinkedHashMap<>();
    public boolean transitioning;
    public String queuedMilestone;
    private final Map<UUID,dev.rbd.network.ImageUpload> uploads=new HashMap<>();
    public final Map<UUID,Reading> readings=new java.util.concurrent.ConcurrentHashMap<>();
    public static final class Reading implements AutoCloseable {
        final String id=UUID.randomUUID().toString();final JsonObject book;final MemoryArchive.ReaderFrames reader;
        MemoryFrame last;long sequence,ackAfterNanos;boolean awaiting;
        Reading(JsonObject book,MemoryArchive.ReaderFrames reader){this.book=book;this.reader=reader;}
        public void close() throws IOException {reader.close();}
    }
    public GameSession(MinecraftServer server) throws IOException {
        this.server=server;Path world=server.getWorldPath(LevelResource.ROOT).toRealPath();
        supervisor=Boolean.getBoolean("rbd.legacySupervisor")&&server.isDedicatedServer()&&System.getenv("RBD_CONTROL_DIR")!=null?new SupervisorBridge(server):null;
        snapshots=new SnapshotStore(world,supervisor!=null?supervisor.control():SnapshotStore.controlFor(world));
        if(supervisor==null&&snapshots.pending())throw new IOException("Pending return must be recovered before opening the world");
        if(Files.exists(snapshots.control.resolve("fault.json")))throw new IOException("RBD archive has an unresolved fault: "+snapshots.control.resolve("fault.json"));
        branch=BranchData.get(server);
        Path rulesFile=snapshots.control.resolve("returned_rules.json");
        if(Files.exists(rulesFile)){
            var restoredRules=AtomicJson.read(rulesFile);String id=restoredRules.get("id").getAsString();
            if(!branch.json.has("rulesTransaction")||!branch.json.get("rulesTransaction").getAsString().equals(id)){
                dev.rbd.rules.WorldRules.apply(server.getWorldData().getGameRules(),restoredRules.getAsJsonObject("values"));
                branch.json.addProperty("rulesTransaction",id);branch.setDirty();
            }
        }
        archive=new MemoryArchive(snapshots.control.resolve("memories"));recorder=new MemoryRecorder(this);
        Path authority=snapshots.control.resolve("authority.json");
        authorities=new AuthorityRoster(Files.exists(authority)?AtomicJson.read(authority):new JsonObject());
        restoreContinuations();
        saveGuard.attach();
    }
    public boolean isHolder(UUID id){return authorities.contains(id);}
    public JsonObject soul(UUID id){JsonObject person=authorities.person(id);if(person==null)throw new IllegalArgumentException("Unknown authority holder: "+id);return person;}
    public List<UUID> holders(){return authorities.activeIds();}
    public boolean returnPending(){return !pendingDeaths.isEmpty();}
    public UUID onlyHolder(){if(holders().size()!=1)throw new IllegalStateException("Specify a player when there are zero or multiple holders");return holders().getFirst();}
    public void saveSoul() throws IOException {AtomicJson.write(snapshots.control.resolve("authority.json"),authorities.json());}
    public void bind(ServerPlayer p) throws IOException {
        if(transitioning||returnPending())throw new IOException("Cannot grant authority during a return");
        JsonObject person=authorities.bind(p.getUUID(),p.getGameProfile().getName(),RbdConfig.MAX_HOLDERS.get());
        JsonObject known=branch.object("knowledge");
        if(!person.has("knowledge")&&known.has(p.getUUID().toString()))person.add("knowledge",known.get(p.getUUID().toString()).deepCopy());
        saveSoul();
        // Joining the roster must not provide a new player-selected checkpoint.
        if((supervisor==null?snapshots.active():supervisor.active())==null)queuedMilestone="awakening";
    }
    public void unbind(UUID id) throws IOException {
        if(transitioning||returnPending())throw new IOException("Cannot revoke authority during a return");
        JsonObject person=soul(id);
        if(person.has("knowledge"))branch.object("knowledge").add(id.toString(),person.get("knowledge").deepCopy());
        authorities.unbind(id);saveSoul();
    }
    private void restoreContinuations() throws IOException {
        Path last=snapshots.control.resolve("last_return.json");if(!Files.exists(last))return;
        JsonObject returned=AtomicJson.read(last);if(!returned.has("id"))return;String id=returned.get("id").getAsString();
        for(UUID who:holders()){
            JsonObject person=soul(who);if(!person.has("continuation"))continue;JsonObject c=person.getAsJsonObject("continuation");
            JsonObject applied=branch.object("appliedContinuations");
            if(c.get("return").getAsString().equals(id)&&(!applied.has(who.toString())||!applied.get(who.toString()).getAsString().equals(id))){
                branch.object("heads").add(who.toString(),c.get("head"));applied.addProperty(who.toString(),id);
            }
        }
    }
    private JsonObject knowledge(UUID who){
        if(isHolder(who)){JsonObject soul=soul(who);if(!soul.has("knowledge"))soul.add("knowledge",new JsonObject());return soul.getAsJsonObject("knowledge");}
        JsonObject all=branch.object("knowledge");if(!all.has(who.toString()))all.add(who.toString(),new JsonObject());return all.getAsJsonObject(who.toString());
    }
    public void learn(UUID who,MemoryFrame.Contact contact){if(!contact.name().isBlank())knowledge(who).add(contact.soul().toString(),MemoryArchive.GSON.toJsonTree(contact));}
    public void learnExperience(UUID who,MemoryFrame.Contact contact,String book,long sequence){
        if(contact.name().isBlank())return;
        var evidence=MemoryArchive.GSON.toJsonTree(contact).getAsJsonObject();evidence.addProperty("source","EXPERIENCED_MEMORY");evidence.addProperty("book",book);evidence.addProperty("sequence",sequence);knowledge(who).add(contact.soul().toString(),evidence);
    }
    public void login(ServerPlayer p){
        dev.rbd.rules.WorldRules.send(p);
        if(isHolder(p.getUUID())&&soul(p.getUUID()).has("lastDeath")){var msg=RbdNetwork.message("return_imprint");msg.add("imprint",soul(p.getUUID()).get("lastDeath"));RbdNetwork.send(p,msg);}
    }
    public boolean recognizes(UUID reader,UUID target){if(isHolder(reader)&&flag(reader,"memorySealed"))return reader.equals(target);
        if(isHolder(target)&&flag(target,"nameSealed"))return reader.equals(target);
        return !RbdConfig.REQUIRE_KNOWN.get()||reader.equals(target)||knowledge(reader).has(target.toString());}
    public boolean visible(JsonObject book){return book.get("authority").getAsBoolean()||branch.object("visibleBooks").has(book.get("id").getAsString());}
    public boolean flag(UUID id,String key){return isHolder(id)&&soul(id).has(key)&&soul(id).get(key).getAsBoolean();}
    public void milestone(String id){
        for(UUID who:holders()){ServerPlayer p=server.getPlayerList().getPlayer(who);if(p!=null&&p.isAlive()&&connection(who)!=CheckpointPolicy.Connection.LOST){milestone(id,who);return;}}
    }
    public void milestone(String id,UUID who){
        if(!isHolder(who)||transitioning||returnPending()||branch.object("milestones").has(id))return;
        var p=server.getPlayerList().getPlayer(who);if(p==null)return;
        var context=new CheckpointPolicy.Context(id,true,p.isAlive(),true,false,connection(who));
        if(new CheckpointPolicy.Authored().mayAdvance(context))queuedMilestone=id;
    }
    public CheckpointPolicy.Connection connection(UUID id){JsonObject soul=soul(id);return soul.has("connection")?CheckpointPolicy.Connection.valueOf(soul.get("connection").getAsString()):CheckpointPolicy.Connection.CONNECTED;}
    public void transition(String operation,JsonObject death) throws IOException {
        if(transitioning)throw new IOException("Return already in progress");
        saveGuard.check();
        recorder.close();
        if(death!=null){
            String returnId=death.get("id").getAsString();
            for(UUID who:holders()){
                JsonObject person=soul(who);person.remove("continuation");
                // A living holder remembers an erased branch as part of the same continuous life.
                if(!pendingDeaths.containsKey(who)&&branch.object("heads").has(who.toString())){
                    JsonObject c=new JsonObject();c.addProperty("return",returnId);c.add("head",branch.object("heads").get(who.toString()));person.add("continuation",c);
                }
            }
        }
        saveSoul();
        if(supervisor!=null)supervisor.request(operation,death);else snapshots.prepare(operation,death,dev.rbd.rules.WorldRules.export(server.getWorldData().getGameRules()));
        transitioning=true;
        for(ServerPlayer p:server.getPlayerList().getPlayers()){
            endReading(p);var msg=RbdNetwork.message("transition");msg.addProperty("operation",operation);
            msg.addProperty("connected",supervisor==null);
            JsonObject ownDeath=pendingDeaths.get(p.getUUID());
            if(ownDeath!=null)msg.add("ending",ownDeath.get("ending"));
            RbdNetwork.send(p,msg);
        }
        if(supervisor==null)ConnectedReturn.begin(this);
    }
    public void tick() throws IOException {
        if(transitioning){if(supervisor!=null)server.halt(false);return;}
        if(returnPending()){
            JsonObject death=pendingDeaths.values().iterator().next().deepCopy();JsonArray all=new JsonArray();pendingDeaths.values().forEach(b->all.add(b.deepCopy()));death.add("deaths",all);
            transition("RESTORE",death);return;
        }
        recorder.tick();
        if(server.getTickCount()%RbdConfig.SOUL_SAVE_TICKS.get()==0)saveSoul();
        for(var entry:List.copyOf(readings.entrySet())){
            ServerPlayer p=server.getPlayerList().getPlayer(entry.getKey());
            if(p==null||!p.isAlive()){if(p!=null)endReading(p);else {entry.getValue().close();readings.remove(entry.getKey());}continue;}
            Reading reading=entry.getValue();if(reading.awaiting)continue;MemoryFrame frame=reading.reader.next();
            if(frame==null){
                if(isHolder(p.getUUID())&&reading.book.get("soul").getAsString().equals(p.getUUID().toString())&&flag(p.getUUID(),"recoverFromSelfBook")){
                    soul(p.getUUID()).addProperty("memorySealed",false);soul(p.getUUID()).addProperty("recoverFromSelfBook",false);saveSoul();
                }
                endReading(p,true);continue;}
            reading.last=frame;reading.awaiting=true;reading.sequence++;
            double dwell=frame.body()!=null&&(frame.body().terminal()||frame.body().rememberedEnding())?RbdConfig.DEATH_DWELL.get():0;
            reading.ackAfterNanos=System.nanoTime()+(long)(dwell*1_000_000_000L);
            var msg=RbdNetwork.message("frame");msg.addProperty("session",reading.id);msg.addProperty("sequence",reading.sequence);msg.addProperty("dwell",dwell);msg.add("frame",MemoryArchive.GSON.toJsonTree(frame));RbdNetwork.sendLarge(p,msg);
        }
        if(queuedMilestone!=null){String milestone=queuedMilestone;queuedMilestone=null;branch.object("milestones").addProperty(milestone,true);transition("CAPTURE",null);}
    }
    public void death(LivingEntity actor,net.minecraft.world.damagesource.DamageSource cause) throws IOException {
        if(pendingDeaths.containsKey(actor.getUUID()))return;
        if(actor instanceof ServerPlayer p)endReading(p);
        JsonObject book=recorder.death(actor,cause);
        if(isHolder(actor.getUUID())){
            JsonObject soul=soul(actor.getUUID());
            double miasma=soul.has("miasma")?soul.get("miasma").getAsDouble():0;soul.addProperty("miasma",miasma+RbdConfig.DEATH_MIASMA.get());
            // Supervisor v1 needs the death fields alongside the complete memory-book reference.
            book.addProperty("soul_id",actor.getUUID().toString());book.addProperty("original_name",Perception.name(actor));
            book.addProperty("dimension",actor.level().dimension().location().toString());book.addProperty("world_tick",actor.level().getGameTime());
            book.addProperty("x",actor.getX());book.addProperty("y",actor.getY());book.addProperty("z",actor.getZ());
            var imprint=new JsonObject();imprint.addProperty("book",book.get("id").getAsString());imprint.addProperty("dimension",actor.level().dimension().location().toString());
            imprint.addProperty("x",actor.getX());imprint.addProperty("y",actor.getY());imprint.addProperty("z",actor.getZ());imprint.add("body",book.get("ending"));soul.add("lastDeath",imprint);
            pendingDeaths.put(actor.getUUID(),book);saveSoul();
        }
    }
    public void open(ServerPlayer p,String id) throws IOException {
        JsonObject book=archive.book(id);
        if(!RbdConfig.SELF_READING.get()&&book.get("soul").getAsString().equals(p.getUUID().toString()))return;
        if(!visible(book)||!recognizes(p.getUUID(),UUID.fromString(book.get("soul").getAsString()))){p.displayClientMessage(Component.translatable("message.rbd.unfamiliar"),true);return;}
        if(transitioning||readings.containsKey(p.getUUID()))return;
        Reading reading=new Reading(book,archive.reader(book.get("head").getAsString()));readings.put(p.getUUID(),reading);recorder.clearImage(p.getUUID());
        var msg=RbdNetwork.message("reading");msg.addProperty("session",reading.id);msg.addProperty("title",book.get("name").getAsString());msg.addProperty("coverage",book.get("coverage").getAsString());RbdNetwork.send(p,msg);
    }
    public void endReading(ServerPlayer p) throws IOException {
        endReading(p,false);
    }
    private void endReading(ServerPlayer p,boolean completed) throws IOException {
        Reading r=readings.remove(p.getUUID());if(r!=null){r.close();var msg=RbdNetwork.message("end");msg.addProperty("session",r.id);msg.addProperty("completed",completed);RbdNetwork.send(p,msg);}
    }
    public boolean reading(UUID who){return readings.containsKey(who);}
    public void message(ServerPlayer p,JsonObject msg) throws IOException {
        if(transitioning)return;
        String kind=msg.get("kind").getAsString();
        if(kind.equals("image_chunk")){
            if(transitioning||reading(p.getUUID()))return;
            var upload=uploads.computeIfAbsent(p.getUUID(),id->new dev.rbd.network.ImageUpload());
            JsonObject view=upload.accept(msg);if(view!=null)recorder.image(p,view.get("width").getAsInt(),view.get("height").getAsInt(),view.get("png").getAsString());return;
        }
        if(kind.equals("experienced")){
            Reading r=readings.get(p.getUUID());
            if(r!=null&&r.awaiting&&System.nanoTime()>=r.ackAfterNanos&&r.id.equals(msg.get("session").getAsString())&&r.sequence==msg.get("sequence").getAsLong()){
                for(var contact:r.last.contacts())learnExperience(p.getUUID(),contact,r.book.get("id").getAsString(),r.sequence);recorder.experienced(p,r.last);r.awaiting=false;
            }return;
        }
        if(kind.equals("close")){endReading(p);return;}
        if(reading(p.getUUID()))return;
        if(kind.equals("page")){ArchiveLibrary.page(this,p,msg.get("direction").getAsInt());return;}
        if(kind.equals("select_book"))ArchiveLibrary.select(this,p,msg.get("id").getAsString());
    }
    public void disconnected(ServerPlayer player) throws IOException {endReading(player);recorder.seal(player.getUUID());uploads.remove(player.getUUID());}
    public void close() throws IOException {uploads.clear();for(var reading:readings.values())reading.close();readings.clear();recorder.close();saveSoul();}
}
