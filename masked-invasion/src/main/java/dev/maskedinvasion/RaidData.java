package dev.maskedinvasion;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Only current encounters and one small progress record per player; no per-wave history or entity references. */
public final class RaidData extends SavedData {
    public enum Phase { PREPARING, WARNING, ACTIVE, FAILED }
    public record Home(ResourceKey<Level> dimension,BlockPos position,float angle,boolean forced) {
        public Home(ResourceKey<Level> dimension,BlockPos position,float angle){this(dimension,position,angle,true);}
        CompoundTag save(){var n=new CompoundTag();n.putString("Dimension",dimension.location().toString());n.putLong("Position",position.asLong());n.putFloat("Angle",angle);n.putBoolean("Forced",forced);return n;}
        static Home load(CompoundTag n){return new Home(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(n.getString("Dimension"))),BlockPos.of(n.getLong("Position")),n.getFloat("Angle"),!n.contains("Forced")||n.getBoolean("Forced"));}
    }
    public static final class Progress {
        public int wave,pendingEmeralds,intervalDays;public long nextAt;
        public Progress(long nextAt){this.nextAt=nextAt;}
    }
    public static final class Attacker {
        public final UUID id;public final String form;public boolean spawned;public BlockPos position;
        public Attacker(UUID id,String form,BlockPos position){this.id=id;this.form=form;this.position=position;}
    }
    public static final class Raid {
        public final UUID id;public final Home home;public final int wave,radius,height,total;public final long seed;
        public boolean suspended;
        public Phase phase=Phase.PREPARING;public int warning,remaining,spawnWait;
        public final Map<UUID,Home> defenders=new LinkedHashMap<>();
        public final Map<UUID,Attacker> attackers=new LinkedHashMap<>();
        public Raid(UUID id,Home home,int wave,int radius,int height,int total,long seed,int warning,int remaining){this.id=id;this.home=home;this.wave=wave;this.radius=radius;this.height=height;this.total=total;this.seed=seed;this.warning=warning;this.remaining=remaining;}
        public boolean contains(ResourceKey<Level> dimension,double x,double y,double z){double dx=x-(home.position().getX()+0.5),dz=z-(home.position().getZ()+0.5);return dimension.equals(home.dimension())&&dx*dx+dz*dz<=radius*(double)radius&&Math.abs(y-home.position().getY())<=height;}
        public CompoundTag save(){
            var n=new CompoundTag();n.putUUID("Id",id);n.put("Home",home.save());n.putInt("Wave",wave);n.putInt("Radius",radius);n.putInt("Height",height);n.putInt("Total",total);n.putLong("Seed",seed);n.putString("Phase",phase.name());n.putInt("Warning",warning);n.putInt("Remaining",remaining);n.putInt("SpawnWait",spawnWait);
            var players=new ListTag();defenders.forEach((id,home)->{var p=home.save();p.putUUID("Id",id);players.add(p);});n.put("Defenders",players);
            var mobs=new ListTag();attackers.values().forEach(a->{var m=new CompoundTag();m.putUUID("Id",a.id);m.putString("Form",a.form);m.putBoolean("Spawned",a.spawned);m.putLong("Position",a.position.asLong());mobs.add(m);});n.put("Attackers",mobs);return n;
        }
        static Raid load(CompoundTag n){
            var r=new Raid(n.getUUID("Id"),Home.load(n.getCompound("Home")),n.getInt("Wave"),n.getInt("Radius"),n.getInt("Height"),n.getInt("Total"),n.getLong("Seed"),n.getInt("Warning"),n.getInt("Remaining"));r.phase=Phase.valueOf(n.getString("Phase"));r.spawnWait=n.getInt("SpawnWait");
            for(var v:n.getList("Defenders",Tag.TAG_COMPOUND)){var p=(CompoundTag)v;r.defenders.put(p.getUUID("Id"),Home.load(p));}
            for(var v:n.getList("Attackers",Tag.TAG_COMPOUND)){var m=(CompoundTag)v;var a=new Attacker(m.getUUID("Id"),m.getString("Form"),BlockPos.of(m.getLong("Position")));a.spawned=m.getBoolean("Spawned");r.attackers.put(a.id,a);}return r;
        }
    }
    public long clock,lastDay=Long.MIN_VALUE;
    public final Map<UUID,Progress> players=new LinkedHashMap<>();
    public final Map<UUID,Raid> raids=new LinkedHashMap<>();
    public static RaidData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(RaidData::new,RaidData::load),MaskedInvasion.ID);}
    public Raid forPlayer(UUID id){return raids.values().stream().filter(r->r.defenders.containsKey(id)).findFirst().orElse(null);}
    public static RaidData load(CompoundTag n,HolderLookup.Provider lookup){
        var d=new RaidData();d.clock=n.getLong("Clock");d.lastDay=n.contains("LastDay")?n.getLong("LastDay"):Long.MIN_VALUE;
        for(var raw:n.getList("Players",Tag.TAG_COMPOUND)){var p=(CompoundTag)raw;var progress=new Progress(p.getLong("NextAt"));progress.wave=p.getInt("Wave");progress.intervalDays=p.contains("IntervalDays")?p.getInt("IntervalDays"):3;progress.pendingEmeralds=p.getInt("Emeralds");d.players.put(p.getUUID("Id"),progress);}
        for(var raw:n.getList("Raids",Tag.TAG_COMPOUND)){var r=Raid.load((CompoundTag)raw);d.raids.put(r.id,r);}return d;
    }
    @Override public CompoundTag save(CompoundTag n,HolderLookup.Provider lookup){
        n.putInt("Schema",1);n.putLong("Clock",clock);n.putLong("LastDay",lastDay);var p=new ListTag();players.forEach((id,v)->{var x=new CompoundTag();x.putUUID("Id",id);x.putInt("Wave",v.wave);x.putInt("IntervalDays",v.intervalDays);x.putLong("NextAt",v.nextAt);x.putInt("Emeralds",v.pendingEmeralds);p.add(x);});n.put("Players",p);var r=new ListTag();raids.values().forEach(v->r.add(v.save()));n.put("Raids",r);return n;
    }
}
