package dev.rbd.runtime;

import com.google.gson.JsonObject;
import dev.rbd.RbdConfig;
import dev.rbd.memory.MemoryFrame;
import dev.rbd.network.RbdNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import java.util.UUID;

/** Player-initiated edits only. No automatic names, entity cache or per-tick scan. */
public final class VillagerNaming {
    private final String session=UUID.randomUUID().toString();

    public static boolean validName(String text,int limit){
        return !text.isBlank()&&text.codePointCount(0,text.length())<=limit
                &&text.codePoints().noneMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT||c==0xA7);
    }
    private static Villager target(GameSession game,ServerPlayer player,UUID id){
        if(game.transitioning||game.returnPending()||game.reading(player.getUUID())||!player.isAlive()||player.isSpectator()
                ||!RbdConfig.PLAYER_NAME_VILLAGERS.get(player.serverLevel().getGameRules()))return null;
        var entity=player.serverLevel().getEntity(id);
        return entity instanceof Villager villager&&villager.isAlive()&&player.canInteractWithEntity(villager,0.0)
                &&player.hasLineOfSight(villager)?villager:null;
    }
    private static String customName(Villager villager){return villager.hasCustomName()?villager.getCustomName().getString():"";}
    private static JsonObject result(UUID id,String edit,boolean ok,String message){
        var reply=RbdNetwork.message("villager_name_result");reply.addProperty("entity",id.toString());reply.addProperty("edit",edit);
        reply.addProperty("ok",ok);reply.addProperty("message",message);return reply;
    }
    public JsonObject open(GameSession game,ServerPlayer player,UUID id){
        var villager=target(game,player,id);
        if(villager==null)return result(id,"",false,"message.rbd.name.unavailable");
        var reply=RbdNetwork.message("villager_name_opened");reply.addProperty("entity",id.toString());
        reply.addProperty("edit",UUID.randomUUID().toString());reply.addProperty("session",session);
        reply.addProperty("original",customName(villager));reply.addProperty("limit",RbdConfig.VILLAGER_NAME_LENGTH.get(player.serverLevel().getGameRules()));return reply;
    }
    public JsonObject rename(GameSession game,ServerPlayer player,JsonObject request){
        UUID id=UUID.fromString(request.get("entity").getAsString());String edit=request.get("edit").getAsString();
        var villager=target(game,player,id);
        if(!session.equals(request.get("session").getAsString())||villager==null)return result(id,edit,false,"message.rbd.name.unavailable");
        if(!customName(villager).equals(request.get("original").getAsString()))return result(id,edit,false,"message.rbd.name.changed");
        String name=request.get("name").getAsString().strip();
        if(!validName(name,RbdConfig.VILLAGER_NAME_LENGTH.get(player.serverLevel().getGameRules())))return result(id,edit,false,"message.rbd.name.invalid");
        villager.setCustomName(Component.literal(name));
        game.learn(player.getUUID(),new MemoryFrame.Contact(id,name,villager.getType().toString()));
        return result(id,edit,true,"message.rbd.name.saved");
    }
}
