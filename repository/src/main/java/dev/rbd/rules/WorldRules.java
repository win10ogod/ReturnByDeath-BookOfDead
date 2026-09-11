package dev.rbd.rules;
import com.google.gson.*;
import com.mojang.brigadier.arguments.*;
import dev.rbd.mixin.GameRuleTypeAccess;
import dev.rbd.network.RbdNetwork;
import dev.rbd.runtime.GameSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import java.util.*;
import java.util.function.*;

/** Registered native world rules, legacy defaults and client synchronization share one catalogue. */
public final class WorldRules {
    public static final List<Setting<?>> ALL=new ArrayList<>();
    private static volatile Map<String,String> client=Map.of();
    public static boolean initialized;
    public record Setting<T>(GameRules.Key<?> key,Supplier<T> initial,Function<String,T> parse,Function<T,String> print) implements Supplier<T> {
        @Override public T get(){
            var game=GameSession.current;
            if(game!=null&&game.server.isSameThread())return parse.apply(value(game.server.getWorldData().getGameRules(),key).serialize());
            String synchronizedValue=client.get(key.getId());return synchronizedValue==null?initial.get():parse.apply(synchronizedValue);
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static GameRules.Value<?> value(GameRules rules,GameRules.Key<?> key){return rules.getRule((GameRules.Key)key);}
    private static <T> Setting<T> add(GameRules.Key<?> key,Supplier<T> initial,Function<String,T> parse,Function<T,String> print){var setting=new Setting<>(key,initial,parse,print);ALL.add(setting);return setting;}
    public static Setting<Boolean> bool(String id,Supplier<Boolean> initial){
        var key=GameRules.register(id,GameRules.Category.MISC,GameRules.BooleanValue.create(initial.get(),(server,value)->sync(server)));
        return add(key,initial,Boolean::parseBoolean,Object::toString);
    }
    public static Setting<Integer> integer(String id,Supplier<Integer> initial,int min,int max){
        var type=GameRules.IntegerValue.create(initial.get(),(server,value)->sync(server));
        ((GameRuleTypeAccess)type).rbd$argument(()->IntegerArgumentType.integer(min,max));
        return add(GameRules.register(id,GameRules.Category.MISC,type),initial,Integer::parseInt,Object::toString);
    }
    public static Setting<Double> decimal(String id,Supplier<Double> initial,double min,double max){
        return text(id,initial,Double::parseDouble,Object::toString,s->{try{double value=Double.parseDouble(s);return Double.isFinite(value)&&value>=min&&value<=max;}catch(NumberFormatException e){return false;}});
    }
    public static Setting<List<? extends String>> list(String id,Supplier<List<? extends String>> initial){
        return text(id,initial,s->s.isBlank()?List.of():Arrays.stream(s.split(",",-1)).map(String::trim).toList(),s->String.join(",",s),s->s.isBlank()||Arrays.stream(s.split(",",-1)).allMatch(x->net.minecraft.resources.ResourceLocation.tryParse(x.trim())!=null));
    }
    private static <T> Setting<T> text(String id,Supplier<T> initial,Function<String,T> parse,Function<T,String> print,Predicate<String> valid){
        var type=GameRules.IntegerValue.create(0,(server,value)->sync(server));
        var access=(GameRuleTypeAccess)type;access.rbd$argument(StringArgumentType::string);
        access.rbd$constructor(t->new TextRuleValue(t,print.apply(initial.get()),valid));
        return add(GameRules.register(id,GameRules.Category.MISC,type),initial,parse,print);
    }
    public static void seed(GameRules rules){if(initialized)for(Setting<?> setting:ALL)seedOne(rules,setting);}
    private static <T> void seedOne(GameRules rules,Setting<T> setting){
        var rule=value(rules,setting.key);String initial=setting.print.apply(setting.initial.get());
        if(rule instanceof GameRules.BooleanValue b)b.set(Boolean.parseBoolean(initial),null);
        else if(rule instanceof GameRules.IntegerValue i&&!i.tryDeserialize(initial))throw new IllegalArgumentException("Invalid legacy value for "+setting.key.getId());
    }
    public static void send(ServerPlayer player){
        var msg=RbdNetwork.message("world_rules");msg.add("values",export(player.server.getWorldData().getGameRules()));RbdNetwork.sendLarge(player,msg);
    }
    public static JsonObject export(GameRules rules){var values=new JsonObject();for(var setting:ALL)values.addProperty(setting.key.getId(),value(rules,setting.key).serialize());return values;}
    public static void apply(GameRules rules,JsonObject values){
        for(var setting:ALL){String id=setting.key.getId();if(!values.has(id))continue;var rule=value(rules,setting.key);String text=values.get(id).getAsString();
            if(rule instanceof GameRules.BooleanValue b)b.set(Boolean.parseBoolean(text),null);
            else if(rule instanceof GameRules.IntegerValue i&&!i.tryDeserialize(text))throw new IllegalArgumentException("Invalid saved world rule: "+id);
        }
    }
    public static void sync(MinecraftServer server){if(server.getPlayerList()!=null)for(var player:server.getPlayerList().getPlayers())send(player);}
    public static void receive(JsonObject values){var incoming=new HashMap<String,String>();values.entrySet().forEach(e->incoming.put(e.getKey(),e.getValue().getAsString()));client=Map.copyOf(incoming);}
    public static void clearClient(){client=Map.of();}
    private WorldRules(){}
}
