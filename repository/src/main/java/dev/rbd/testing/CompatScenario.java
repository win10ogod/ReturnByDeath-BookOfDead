package dev.rbd.testing;

import com.google.gson.*;
import dev.rbd.*;
import dev.rbd.io.AtomicJson;
import dev.rbd.memory.*;
import dev.rbd.network.RbdNetwork;
import dev.rbd.runtime.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;
import static dev.rbd.testing.CompatChecks.*;

/** Three real clients and the real offline supervisor; enabled only in an explicitly marked disposable root. */
@EventBusSubscriber(modid="rbd")
public final class CompatScenario {
    public static final boolean ENABLED=Boolean.getBoolean("rbd.compatTest");
    private static final String SESSION=UUID.randomUUID().toString();
    private static JsonObject state;
    private static int wait;
    private static boolean failed;
    private static final String TF_ADV="twilightforest:progress_naga";
    public static Path root(){return Path.of(System.getProperty("rbd.testRoot")).toAbsolutePath();}
    public static boolean allowed(){return ENABLED&&System.getProperty("rbd.testRoot")!=null&&Files.isRegularFile(root().resolve(".rbd-test-fixture"));}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!allowed()||!(event.getEntity() instanceof ServerPlayer p)||!p.getGameProfile().getName().matches("Rbd[ABC]"))return;
        var msg=RbdNetwork.message("compat_test");msg.addProperty("session",SESSION);RbdNetwork.send(p,msg);
    }
    private static void save(int stage) throws Exception {state.addProperty("stage",stage);AtomicJson.write(root().resolve("scenario.json"),state);}
    private static boolean ready(String name) throws Exception {
        Path file=root().resolve(name+"-ready.json");return Files.exists(file)&&AtomicJson.read(file).get("session").getAsString().equals(SESSION);
    }
    private static void position(ServerPlayer p,ServerLevel level,int x) {
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)level.setBlockAndUpdate(new BlockPos(x+dx,199,dz),Blocks.STONE.defaultBlockState());
        p.teleportTo(level,x+0.5,200,0.5,Set.of(),0,0);p.setInvulnerable(true);p.setHealth(8);
    }
    private static MemoryFrame.Contact contact(UUID id,String name){return new MemoryFrame.Contact(id,name,"fixture:identity");}
    private static void mutate(GameSession g,ServerPlayer a,ServerPlayer b,ServerPlayer c) throws Exception {
        var tf=g.server.getLevel(TWILIGHT);tf.setBlockAndUpdate(MARKER,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("twilightforest:mazestone")).defaultBlockState());
        g.server.overworld().setBlockAndUpdate(new BlockPos(2,200,0),Blocks.GOLD_BLOCK.defaultBlockState());
        for(ServerPlayer p:List.of(a,b,c)){
            p.getInventory().add(new ItemStack(item("kamenridercraft:rider_pass"),3));p.giveExperienceLevels(9);p.setData(riderAttachment(),false);
        }
        advancement(b,TF_ADV,true);
        for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))b.setItemSlot(slot,ItemStack.EMPTY);
        require(!transformed(b),"KRC form really changed after checkpoint");
        var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)tf.getBlockEntity(MARKER.offset(2,0,0));chest.clearContent();
        UUID mob=UUID.fromString(state.get("boss").getAsString());var boss=tf.getEntity(mob);require(boss instanceof LivingEntity,"Twilight boss exists before branch death");
        ((LivingEntity)boss).hurt(tf.damageSources().genericKill(),Float.MAX_VALUE);
    }
    private static void restored(GameSession g,ServerPlayer a,ServerPlayer b,ServerPlayer c) throws Exception {
        var tf=g.server.getLevel(TWILIGHT);
        require(tf.getBlockState(MARKER).is(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("twilightforest:twilight_oak_log"))),"Twilight mod block restored");
        require(g.server.overworld().getBlockState(new BlockPos(2,200,0)).is(Blocks.DIAMOND_BLOCK),"overworld restored with Twilight");
        require(b.serverLevel()==tf&&a.serverLevel()==g.server.overworld(),"players restored across dimensions");
        require(tf.getEntity(UUID.fromString(state.get("boss").getAsString())) instanceof LivingEntity,"Twilight Naga restored");
        require(!advanced(b,TF_ADV),"Twilight boss progression restored");
        require(transformed(b),"KRC transformed equipment restored");
        var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)tf.getBlockEntity(MARKER.offset(2,0,0));
        require(chest.getItem(0).is(item("kamenridercraft:rider_pass"))&&chest.getItem(0).getCount()==5,"cross-mod container restored");
        for(ServerPlayer p:List.of(a,b,c)){
            require(p.experienceLevel==0,"all players' XP restored");require(p.getInventory().countItem(item("kamenridercraft:rider_pass"))==0,"no cross-loop KRC item gain");
            require(p.getData(riderAttachment()),"KRC serialized attachment restored for every player");
            require(p.getHealth()==8,"injured checkpoint body restored for every player");
        }
        require(g.holders().size()==2&&g.isHolder(a.getUUID())&&g.isHolder(b.getUUID())&&!g.isHolder(c.getUUID()),"configured membership survives whole-world return");
    }
    private static void kill(ServerPlayer p){p.setInvulnerable(false);p.removeAllEffects();p.invulnerableTime=0;p.hurt(p.damageSources().genericKill(),Float.MAX_VALUE);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!allowed()||failed)return;var g=GameSession.current;
        if(g==null||g.transitioning||g.returnPending()||!g.server.isDedicatedServer()||!g.snapshots.world.getParent().equals(root().resolve("server")))return;
        try {
            if(state==null)state=Files.exists(root().resolve("scenario.json"))?AtomicJson.read(root().resolve("scenario.json")):new JsonObject();
            int stage=state.has("stage")?state.get("stage").getAsInt():0;
            if(stage==99){g.server.halt(false);return;}
            var a=g.server.getPlayerList().getPlayerByName("RbdA");var b=g.server.getPlayerList().getPlayerByName("RbdB");var c=g.server.getPlayerList().getPlayerByName("RbdC");
            if(a==null||b==null||c==null||!ready("RbdA")||!ready("RbdB")||!ready("RbdC"))return;
            if(wait++<60)return;wait=0;
            if(stage==0){
                for(String id:List.of("kamenridercraft","twilightforest","geckolib","player_animation_library"))require(ModList.get().isLoaded(id),"required real mod loaded: "+id);
                require(RbdConfig.MAX_HOLDERS.get()==2,"fixture uses actual maxHolders configuration");g.bind(a);g.bind(b);
                boolean limit=false;try{g.bind(c);}catch(IllegalStateException expected){limit=true;}require(limit,"third grant rejected by configured limit");
                var tf=g.server.getLevel(TWILIGHT);require(tf!=null,"Twilight dimension registered");
                for(ServerLevel level:g.server.getAllLevels()){level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,g.server);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,g.server);}
                position(a,g.server.overworld(),0);position(b,tf,0);position(c,g.server.overworld(),10);
                g.server.overworld().setBlockAndUpdate(new BlockPos(2,200,0),Blocks.DIAMOND_BLOCK.defaultBlockState());
                tf.setBlockAndUpdate(MARKER,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("twilightforest:twilight_oak_log")).defaultBlockState());
                // Move the reader away from the marker after making the real dimension fixture.
                b.teleportTo(tf,0.5,201,0.5,Set.of(),0,0);
                tf.setBlockAndUpdate(MARKER.offset(2,0,0),Blocks.CHEST.defaultBlockState());
                ((net.minecraft.world.level.block.entity.ChestBlockEntity)tf.getBlockEntity(MARKER.offset(2,0,0))).setItem(0,new ItemStack(item("kamenridercraft:rider_pass"),5));
                EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("twilightforest:naga"));var naga=(net.minecraft.world.entity.Mob)type.create(tf);
                require(naga!=null,"Naga spawned");naga.moveTo(8,202,8,0,0);naga.setNoAi(true);naga.setNoGravity(true);naga.setPersistenceRequired();naga.setCustomName(Component.literal("Compatibility Naga"));tf.addFreshEntity(naga);state.addProperty("boss",naga.getUUID().toString());
                transform(b);advancement(b,TF_ADV,false);
                for(ServerPlayer p:List.of(a,b,c)){p.setExperienceLevels(0);p.setData(riderAttachment(),true);}
                g.learn(a.getUUID(),contact(b.getUUID(),"RbdB"));g.learn(b.getUUID(),contact(a.getUUID(),"RbdA"));
                state.addProperty("a",a.getUUID().toString());state.addProperty("b",b.getUUID().toString());state.addProperty("c",c.getUUID().toString());save(1);
            }else if(stage==1){
                restored(g,a,b,c);state.addProperty("checkpoint",g.supervisor.active().get("checkpoint_id").getAsString());
                // A further grant and removal do not manufacture a later checkpoint.
                g.unbind(b.getUUID());g.bind(b);require(g.queuedMilestone==null,"regrant creates no checkpoint");
                charm(g,b);b.setHealth(8);b.setInvulnerable(true);
                UUID secret=UUID.randomUUID();state.addProperty("secret",secret.toString());g.learn(a.getUUID(),contact(secret,"A alone remembers"));
                require(!g.recognizes(b.getUUID(),secret),"holders have distinct knowledge");
                g.recorder.caption(a,"SURVIVOR_BRANCH_EXPERIENCE");g.recorder.record(a,false);
                mutate(g,a,b,c);kill(c);require(!g.returnPending()&&!g.transitioning,"ordinary player's death never returns the world");
                save(2);kill(b);require(g.returnPending(),"second holder's death initiates return");
            }else if(stage==2){
                restored(g,a,b,c);require(g.supervisor.active().get("checkpoint_id").getAsString().equals(state.get("checkpoint").getAsString()),"shared checkpoint unchanged");
                var books=g.archive.books().stream().filter(x->x.get("authority").getAsBoolean()).toList();require(books.size()==1,"only B produced an authority death book");
                UUID secret=UUID.fromString(state.get("secret").getAsString());require(g.recognizes(a.getUUID(),secret)&&!g.recognizes(b.getUUID(),secret),"survivor private knowledge persists across return");
                boolean retained=false;try(var reader=g.archive.reader(g.recorder.seal(a.getUUID()))){MemoryFrame f;while((f=reader.next())!=null)if(f.caption().contains("SURVIVOR_BRANCH_EXPERIENCE"))retained=true;}
                require(retained,"surviving holder retains the erased branch's recorded experience");
                require(!g.soul(a.getUUID()).has("lastDeath")&&g.soul(b.getUUID()).has("lastDeath"),"death imprint belongs only to the one who died");
                state.addProperty("book",books.getFirst().get("id").getAsString());
                g.open(a,state.get("book").getAsString());g.open(b,state.get("book").getAsString());save(3);
            }else if(stage==3){
                if(g.reading(a.getUUID())||g.reading(b.getUUID()))return;
                for(String name:List.of("RbdA","RbdB")){Path report=root().resolve(name+"-read.json");require(Files.exists(report)&&AtomicJson.read(report).get("session").getAsString().equals(SESSION),"both clients rendered the book ending: "+name);}
                require(g.archive.books().stream().filter(x->x.get("authority").getAsBoolean()).count()==1,"reading did not kill either holder");
                mutate(g,a,b,c);save(4);kill(a);kill(b);require(g.returnPending(),"both holders may die in one tick");
                require(g.archive.books().stream().filter(x->x.get("authority").getAsBoolean()).count()==3,"both simultaneous deaths sealed before one return");
            }else if(stage==4){
                restored(g,a,b,c);require(g.archive.books().stream().filter(x->x.get("authority").getAsBoolean()).count()==3,"three distinct holder lives survive two returns");
                JsonObject last=AtomicJson.read(g.snapshots.control.resolve("last_return.json"));require(last.getAsJsonArray("deaths").size()==2,"one return transaction contains both simultaneous deaths");
                long returns;try(var files=Files.list(g.snapshots.control.resolve("receipts"))){returns=files.filter(f->f.toString().endsWith(".json")).map(f->{try{return AtomicJson.read(f);}catch(Exception e){throw new RuntimeException(e);}}).filter(x->x.get("operation").getAsString().equals("RESTORE")).count();}
                require(returns==2,"only two world restores for the three holder deaths");
                var report=new JsonObject();report.addProperty("result","PASS");report.addProperty("players",3);report.addProperty("holders",2);report.addProperty("worldReturns",returns);report.addProperty("holderBooks",3);
                report.addProperty("verified","Real three-client dedicated server; actual KRC and Twilight loaded; vanilla totem and life charm cancel death; transformed equipment and KRC attachment restoration; Naga, Twilight blocks, chest and progression restoration; cross-dimension player/XP/item restoration; second holder triggers return; ordinary player death does not; surviving holder remembers erased branch privately; simultaneous book readers render endings; simultaneous holder deaths seal two books in one restore; all clients reconnect after each supervisor restart");
                JsonObject versions=new JsonObject();for(String id:List.of("rbd","neoforge","kamenridercraft","twilightforest","geckolib","player_animation_library"))versions.addProperty(id,ModList.get().getModContainerById(id).orElseThrow().getModInfo().getVersion().toString());report.add("versions",versions);
                AtomicJson.write(root().resolve("report.json"),report);save(99);
            }
        }catch(Exception ex){failed=true;ex.printStackTrace();try{var report=new JsonObject();report.addProperty("result","FAIL");report.addProperty("error",ex.toString());if(state!=null)report.add("state",state);AtomicJson.write(root().resolve("report.json"),report);}catch(Exception ignored){}g.server.halt(false);}
    }
}
