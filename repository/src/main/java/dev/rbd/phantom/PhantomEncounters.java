package dev.rbd.phantom;

import com.google.gson.JsonObject;
import dev.rbd.ModContent;
import dev.rbd.runtime.GameSession;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.sounds.*;
import java.util.*;

/** Encounter ownership is branch data; learned habits and cooldown survive death with the soul. */
public final class PhantomEncounters {
    private static JsonObject active(){return GameSession.current.branch.object("despairPhantoms");}
    public static void tick(GameSession game){
        if(game.transitioning||game.returnPending()||!PhantomRules.ENABLED.get())return;
        int interval=PhantomRules.CHECK_TICKS.get();if(game.server.getTickCount()%interval!=0)return;
        for(UUID id:game.holders()){
            ServerPlayer player=game.server.getPlayerList().getPlayer(id);if(player==null||!player.isAlive()||player.isSpectator())continue;
            var profile=PhantomProfile.of(player);PhantomProfile.observe(player);
            int cooldown=(int)Math.max(0,PhantomProfile.number(profile,"cooldown")-interval);profile.addProperty("cooldown",cooldown);
            if(active().has(id.toString())){
                JsonObject record=active().getAsJsonObject(id.toString());
                var dimension=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(record.get("dimension").getAsString()));
                ServerLevel level=game.server.getLevel(dimension);
                if(level!=null){
                    level.getChunk(record.get("chunkX").getAsInt(),record.get("chunkZ").getAsInt());
                    Entity entity=level.getEntity(UUID.fromString(record.get("entity").getAsString()));
                    if(entity instanceof DespairPhantomEntity phantom&&!phantom.isRemoved()){
                        record.remove("missing");
                        // The old dimension can have no ticking players after travel or restart.
                        // Its body need not receive an AI tick for pursuit to resume.
                        if(level!=player.serverLevel()&&PhantomRules.PURSUIT.get())followDimension(phantom,player);
                        continue;
                    }
                    // Entity storage loading can complete after the chunk future; allow a full grace period.
                    int missing=record.has("missing")?record.get("missing").getAsInt():0;record.addProperty("missing",missing+interval);
                    if(missing<Math.max(100,interval*2))continue;
                }
                active().remove(id.toString());profile.addProperty("cooldown",PhantomRules.COOLDOWN_TICKS.get());continue;
            }
            var soul=game.soul(id);double miasma=soul.has("miasma")?soul.get("miasma").getAsDouble():0;
            if(cooldown>0||miasma<PhantomRules.THRESHOLD.get()){profile.remove("warning");continue;}
            int warning=(int)PhantomProfile.number(profile,"warning");
            if(warning==0){player.sendSystemMessage(Component.translatable("message.rbd.phantom.warning",PhantomRules.WARNING_TICKS.get()/20.0));player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT,SoundSource.HOSTILE,1,0.65f);}
            warning+=interval;profile.addProperty("warning",warning);
            if(warning<PhantomRules.WARNING_TICKS.get())continue;
            var boss=ModContent.PHANTOM.get().create(player.serverLevel());
            if(boss!=null&&relocateNear(boss,player,PhantomRules.SPAWN_DISTANCE.get())){
                boss.mirror(player);
                if(player.serverLevel().addFreshEntity(boss)){remember(boss);profile.remove("warning");player.sendSystemMessage(Component.translatable("message.rbd.phantom.arrival"));}
            }
        }
        game.branch.setDirty();
    }
    public static void remember(DespairPhantomEntity boss){
        if(GameSession.current==null||boss.quarryId()==null)return;
        JsonObject entry=new JsonObject();entry.addProperty("entity",boss.getUUID().toString());entry.addProperty("dimension",boss.level().dimension().location().toString());entry.addProperty("chunkX",boss.chunkPosition().x);entry.addProperty("chunkZ",boss.chunkPosition().z);
        active().add(boss.quarryId().toString(),entry);GameSession.current.branch.setDirty();
    }
    public static void depart(DespairPhantomEntity boss,boolean defeated){
        var game=GameSession.current;if(game==null||boss.quarryId()==null)return;String key=boss.quarryId().toString();
        // A removed old body must never clear a replacement created for dimension travel.
        if(!active().has(key)||!active().getAsJsonObject(key).get("entity").getAsString().equals(boss.getUUID().toString()))return;
        active().remove(key);game.branch.setDirty();
        if(game.isHolder(boss.quarryId())){
            var soul=game.soul(boss.quarryId());if(!soul.has("phantom"))soul.add("phantom",new JsonObject());
            var profile=soul.getAsJsonObject("phantom");profile.addProperty("cooldown",PhantomRules.COOLDOWN_TICKS.get());profile.remove("warning");
            if(defeated){soul.addProperty("miasma",Math.max(0,(soul.has("miasma")?soul.get("miasma").getAsDouble():0)-PhantomRules.RELIEF.get()));var player=boss.quarry();if(player!=null)player.sendSystemMessage(Component.translatable("message.rbd.phantom.defeated",PhantomRules.RELIEF.get()));}
        }
    }
    public static boolean relocateNear(DespairPhantomEntity boss,ServerPlayer player,double distance){
        ServerLevel level=player.serverLevel();
        for(int attempt=0;attempt<24;attempt++){
            double angle=player.getRandom().nextDouble()*Math.PI*2;int x=(int)Math.floor(player.getX()+Math.cos(angle)*distance),z=(int)Math.floor(player.getZ()+Math.sin(angle)*distance);
            int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            // Search near the player's elevation first, which also works inside caves and Twilight structures.
            for(int offset=0;offset<32;offset++){
                int y=offset==31?surface:player.blockPosition().getY()+(offset%2==0?offset/2:-offset/2);
                BlockPos pos=new BlockPos(x,y,z);if(!level.getWorldBorder().isWithinBounds(pos)||!level.getBlockState(pos.below()).isSolidRender(level,pos.below()))continue;
                boss.moveTo(x+0.5,y,z+0.5,player.getYRot()+180,0);
                if(level.noCollision(boss,boss.getBoundingBox())&&!level.containsAnyLiquid(boss.getBoundingBox()))return true;
            }
        }return false;
    }
    public static void followDimension(DespairPhantomEntity old,ServerPlayer target){
        var replacement=ModContent.PHANTOM.get().create(target.serverLevel());if(replacement==null)return;
        CompoundTag state=new CompoundTag();old.saveWithoutId(state);state.remove("UUID");replacement.load(state);
        if(!relocateNear(replacement,target,Math.min(PhantomRules.SPAWN_DISTANCE.get(),16)))return;
        if(target.serverLevel().addFreshEntity(replacement)){remember(replacement);old.discard();}
    }
    private PhantomEncounters(){}
}
