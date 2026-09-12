package dev.rbd.phantom;

import com.google.gson.JsonObject;
import dev.rbd.runtime.GameSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Observed player history lives with the soul, so erased battles can teach the phantom. */
public final class PhantomProfile {
    public static JsonObject of(ServerPlayer player){
        var game=GameSession.current;
        if(game==null)return new JsonObject();
        JsonObject person;
        if(game.isHolder(player.getUUID()))person=game.soul(player.getUUID());
        else {var all=game.branch.object("phantomPlayers");String id=player.getUUID().toString();if(!all.has(id))all.add(id,new JsonObject());person=all.getAsJsonObject(id);}
        if(!person.has("phantom"))person.add("phantom",new JsonObject());
        return person.getAsJsonObject("phantom");
    }
    public static double number(JsonObject data,String key){return data.has(key)?data.get(key).getAsDouble():0;}
    public static void observe(ServerPlayer player){
        var p=of(player);
        peak(p,"level",player.experienceLevel);peak(p,"health",player.getMaxHealth());
        peak(p,"damage",player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        peak(p,"armor",player.getArmorValue());
    }
    private static void peak(JsonObject p,String key,double value){p.addProperty(key,Math.max(number(p,key),value));}
    public static void learn(ServerPlayer player,String habit,double amount){
        if(!PhantomRules.LEARNING.get())return;
        var p=of(player);p.addProperty(habit,number(p,habit)+amount*PhantomRules.LEARNING_RATE.get());
    }
    public static int tier(JsonObject p){
        if(!PhantomRules.LEARNING.get())return 0;
        double growth=number(p,"level")/15+Math.log1p(number(p,"melee")+number(p,"ranged")+number(p,"blocking")+number(p,"evasion"))/2;
        return growth>=5?3:growth>=3?2:growth>=1?1:0;
    }
    public static boolean prefers(JsonObject p,String a,String b){return PhantomRules.LEARNING.get()&&number(p,a)>number(p,b);}
    private PhantomProfile(){}
}
