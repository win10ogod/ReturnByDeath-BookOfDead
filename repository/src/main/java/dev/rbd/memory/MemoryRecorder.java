package dev.rbd.memory;
import dev.rbd.RbdConfig;
import dev.rbd.runtime.GameSession;
import com.google.gson.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import java.io.*;
import java.util.*;

public final class MemoryRecorder implements AutoCloseable {
    private final GameSession game;
    private final Map<UUID,Track> tracks=new HashMap<>();
    private final Set<LivingEntity> observed=Collections.newSetFromMap(new IdentityHashMap<>());
    private LivingEntity[] observers=new LivingEntity[0];
    private boolean observersChanged=true;
    private static final class Track {
        LivingEntity actor;MemoryArchive.Segment segment;List<MemoryFrame.Sound> sounds=new ArrayList<>();
        String caption="";int[] pixels=new int[0];String png="";int width,height;long imageTick=Long.MIN_VALUE;
        String damageType="";double damage;
        Track(LivingEntity actor){this.actor=actor;}
    }
    public MemoryRecorder(GameSession game){
        this.game=game;
        for(ServerLevel level:game.server.getAllLevels())for(var entity:level.getAllEntities())if(entity instanceof LivingEntity actor)observed.add(actor);
    }
    public void joined(LivingEntity actor){observersChanged|=observed.add(actor);}
    public void left(LivingEntity actor) throws IOException {
        observersChanged|=observed.remove(actor);Track track=tracks.get(actor.getUUID());
        if(track!=null&&track.actor==actor)seal(actor.getUUID());
    }
    private Track track(LivingEntity e) throws IOException {
        Track t=tracks.get(e.getUUID());if(t!=null){t.actor=e;return t;}
        t=new Track(e);var heads=game.branch.object("heads");String parent=heads.has(e.getUUID().toString())?heads.get(e.getUUID().toString()).getAsString():"";
        t.segment=game.archive.begin(parent);tracks.put(e.getUUID(),t);return t;
    }
    public void tick() throws IOException {
        if(observersChanged){observers=observed.toArray(LivingEntity[]::new);observersChanged=false;}
        for(var e:observers){
            if(e.isRemoved()){observersChanged|=observed.remove(e);continue;}
            if(e.isAlive()&&Perception.recorded(e))record(e,false);
        }
        for(UUID id:List.copyOf(tracks.keySet()))if(tracks.get(id).actor.isRemoved())seal(id);
    }
    public void record(LivingEntity e,boolean last) throws IOException {
        // While immersed, the reader sees only the presented memory, not contacts around their body.
        if(game.reading(e.getUUID())&&!last)return;
        Track t=track(e);int interval=RbdConfig.VISUAL_INTERVAL.get();
        List<MemoryFrame.Contact> contacts=Perception.contacts(e);for(var contact:contacts)game.learn(e.getUUID(),contact);
        boolean image=t.segment.count()==0||game.server.getTickCount()%interval==0||last;
        int[] pixels=new int[0];int w=0,h=0;String source="CONTINUATION",png="";
        if(image){
            if(!t.png.isEmpty()&&game.server.getTickCount()-t.imageTick<=RbdConfig.VISUAL_INTERVAL.get()*2L){w=t.width;h=t.height;png=t.png;source="CLIENT_FIRST_PERSON";}
            else {w=RbdConfig.RASTER_WIDTH.get();h=RbdConfig.RASTER_HEIGHT.get();pixels=Perception.raster(e,w,h);source="SERVER_SUBJECTIVE_RASTER";}
        }
        var body=new SomaticState(e.getMaxHealth(),e.getAirSupply(),e.getMaxAirSupply(),e.isUnderWater(),e.isOnFire(),e.getTicksFrozen(),e.fallDistance,t.damage,t.damageType,last);
        t.segment.append(new MemoryFrame(e.level().getGameTime(),e.level().dimension().location().toString(),e.getX(),e.getEyeY(),e.getZ(),e.getYRot(),e.getXRot(),e.getHealth(),t.caption,List.copyOf(contacts),List.copyOf(t.sounds),source,w,h,pixels,png,body));
        t.damage=0;
        t.caption="";t.sounds.clear();
        if(t.segment.count()>=RbdConfig.SEGMENT_FRAMES.get())rotate(e);
    }
    public void experienced(ServerPlayer reader,MemoryFrame frame) throws IOException {
        Track t=track(reader);
        t.segment.append(new MemoryFrame(reader.level().getGameTime(),frame.dimension(),frame.x(),frame.y(),frame.z(),frame.yaw(),frame.pitch(),frame.health(),frame.caption(),frame.contacts(),frame.sounds(),"EXPERIENCED_MEMORY/"+frame.visualSource(),frame.width(),frame.height(),frame.pixels(),frame.png(),frame.body()==null?null:frame.body().asExperience()));
        t.caption="";t.sounds.clear();
        if(t.segment.count()>=RbdConfig.SEGMENT_FRAMES.get())rotate(reader);
    }
    public void clearImage(UUID id){Track t=tracks.get(id);if(t!=null){t.png="";t.imageTick=Long.MIN_VALUE;}}
    public void image(ServerPlayer actor,int width,int height,String png) throws IOException {
        byte[] data=java.util.Base64.getDecoder().decode(png);
        if(width<1||height<1||(long)width*height>64000000L||data.length<24||java.nio.ByteBuffer.wrap(data).getLong()!=0x89504E470D0A1A0AL||java.nio.ByteBuffer.wrap(data).getInt(16)!=width||java.nio.ByteBuffer.wrap(data).getInt(20)!=height)throw new IOException("Invalid rendered memory image");
        Track t=track(actor);t.width=width;t.height=height;t.png=png;t.imageTick=game.server.getTickCount();
    }
    public void caption(LivingEntity e,String caption) throws IOException {if(!game.reading(e.getUUID()))track(e).caption+=caption+"\n";}
    public void hurt(LivingEntity e,String type,float damage) throws IOException {Track t=track(e);t.damageType=type;t.damage+=damage;}
    public void sound(ServerLevel level,net.minecraft.world.phys.Vec3 pos,String sound,float volume,float pitch){
        if(!RbdConfig.RECORD_SOUNDS.get()||game.transitioning)return;
        for(Track t:tracks.values())if(!game.reading(t.actor.getUUID())&&t.actor.level()==level&&t.actor.position().distanceTo(pos)<=Math.max(RbdConfig.SOUND_RANGE.get(),volume*RbdConfig.SOUND_RANGE.get()))
            t.sounds.add(new MemoryFrame.Sound(sound,(float)Math.max(0,volume*(1-t.actor.position().distanceTo(pos)/Math.max(RbdConfig.SOUND_RANGE.get(),volume*RbdConfig.SOUND_RANGE.get()))),pitch));
    }
    public String seal(UUID id) throws IOException {
        Track t=tracks.remove(id);var heads=game.branch.object("heads");
        if(t!=null){t.segment.close();heads.addProperty(id.toString(),t.segment.id);}
        return heads.has(id.toString())?heads.get(id.toString()).getAsString():"";
    }
    private void rotate(LivingEntity actor) throws IOException {
        Track t=tracks.remove(actor.getUUID());
        t.segment.closeAsync();
        game.branch.object("heads").addProperty(actor.getUUID().toString(),t.segment.id);
        track(actor);
    }
    public JsonObject death(LivingEntity e,net.minecraft.world.damagesource.DamageSource damageSource) throws IOException {
        String cause=damageSource.getMsgId();Track t=track(e);t.damageType=cause;
        SomaticState ending=new SomaticState(e.getMaxHealth(),e.getAirSupply(),e.getMaxAirSupply(),e.isUnderWater(),e.isOnFire(),e.getTicksFrozen(),e.fallDistance,t.damage,cause,true);
        record(e,true);String head=seal(e.getUUID());
        var book=game.archive.sealBook(e.getUUID(),Perception.name(e),head,game.branch.json.get("branch").getAsString(),game.isHolder(e.getUUID()),
            "RECORDED_SINCE_OBSERVATION; POSE_20TPS; SUBJECTIVE_RASTER_EVERY_"+RbdConfig.VISUAL_INTERVAL.get()+"_TICKS; PREINSTALL_AND_UNLOADED_LIFE_NOT_AVAILABLE",cause);
        book.add("ending",MemoryArchive.GSON.toJsonTree(ending));
        dev.rbd.io.AtomicJson.write(game.archive.root().resolve("books").resolve(book.get("id").getAsString()+".json"),book);
        if(!game.isHolder(e.getUUID()))game.branch.object("visibleBooks").addProperty(book.get("id").getAsString(),true);
        return book;
    }
    public void close() throws IOException {for(UUID id:List.copyOf(tracks.keySet()))seal(id);}
}
