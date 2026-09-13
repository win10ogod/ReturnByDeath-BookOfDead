package dev.rbd.testing;

import com.google.gson.*;
import dev.rbd.io.AtomicJson;
import dev.rbd.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;

/** Explicit disposable-world LAN regression; never active during normal gameplay. */
@EventBusSubscriber(modid="rbd")
public final class AutoCheckpointLive {
    public static final boolean ENABLED=Boolean.getBoolean("rbd.autoCheckpointTest");
    public static Path report(){return Path.of(System.getProperty("rbd.autoCheckpointReport","rbd-auto-checkpoint-report.json"));}
    private static int stage,ticks;
    private static boolean done;
    private static GameSession before;
    private static String checkpoint;
    private static BlockPos marker;
    private static long start;
    private static final Map<UUID,Object> connections=new HashMap<>();
    private static final Map<UUID,Integer> logins=new HashMap<>();
    private static final JsonArray checks=new JsonArray(),pauses=new JsonArray();
    private static long pausedAt;
    private static long tickStart;
    private static final List<Long> steadyTicks=new ArrayList<>();
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void tickStart(ServerTickEvent.Pre e){if(ENABLED)tickStart=System.nanoTime();}
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void tickTime(ServerTickEvent.Post e){if(ENABLED&&stage==2&&ticks>40&&ticks<150)steadyTicks.add(System.nanoTime()-tickStart);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){
        if(ENABLED&&e.getEntity() instanceof ServerPlayer p){connections.putIfAbsent(p.getUUID(),p.connection);logins.merge(p.getUUID(),1,Integer::sum);}
    }
    @SubscribeEvent public static void before(dev.rbd.api.WorldReturnEvent.Before e){if(ENABLED)pausedAt=System.nanoTime();}
    @SubscribeEvent public static void after(dev.rbd.api.WorldReturnEvent.After e){if(ENABLED&&pausedAt!=0){pauses.add((System.nanoTime()-pausedAt)/1e6);pausedAt=0;}}
    private static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);checks.add(text);}
    private static void rule(GameSession g,String name,String value){g.server.getCommands().performPrefixedCommand(g.server.createCommandSourceStack(),"gamerule "+name+" "+value);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!ENABLED||done)return;
        var g=GameSession.current;if(g==null||!g.snapshots.world.getFileName().toString().startsWith("RbdAutoSaveTest-"))return;
        if(g.transitioning||g.returnPending())return;
        var a=g.server.getPlayerList().getPlayerByName("RbdAutoHost");var b=g.server.getPlayerList().getPlayerByName("RbdAutoGuest");
        if(a==null||b==null)return;
        try{
            if(start==0)start=System.nanoTime();
            if(stage==0){
                if(g.snapshots.active()==null)return;
                require(dev.rbd.RbdConfig.AUTO_CHECKPOINT_INTERVAL.get(g.server.getGameRules())==12000,"default automatic interval is 10 minutes at 20 TPS");
                require(dev.rbd.RbdConfig.CHECKPOINT_RETENTION.get(g.server.getGameRules())==1,"default keeps one committed checkpoint");
                require(dev.rbd.RbdConfig.FAILED_WORLD_RETENTION.get(g.server.getGameRules())==1,"default keeps one replaced-world recovery backup");
                rule(g,"rbdMaxHolders","2");if(!g.isHolder(b.getUUID()))g.bind(b);
                rule(g,"rbdAutoCheckpointIntervalTicks","100");rule(g,"rbdAutoCheckpointSafeTicks","20");rule(g,"rbdAutoCheckpoint","false");
                g.server.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,g.server);
                marker=a.blockPosition().offset(3,0,0);
                for(ServerPlayer p:List.of(a,b)){p.getInventory().clearContent();p.setExperienceLevels(0);p.setHealth(1);}
                checkpoint=g.snapshots.active().get("id").getAsString();ticks=0;stage=1;
            }else if(stage==1&&++ticks>=140){
                require(checkpoint.equals(g.snapshots.active().get("id").getAsString()),"disabled automatic checkpoint does not advance");
                rule(g,"rbdAutoCheckpoint","true");ticks=0;stage=2;
            }else if(stage==2&&++ticks>=160){
                require(checkpoint.equals(g.snapshots.active().get("id").getAsString()),"overdue checkpoint stays unchanged while holders have low health");
                for(ServerPlayer p:List.of(a,b)){p.setHealth(p.getMaxHealth());p.getInventory().add(new ItemStack(Items.GOLD_INGOT,5));}
                a.serverLevel().setBlockAndUpdate(marker,Blocks.DIAMOND_BLOCK.defaultBlockState());before=g;ticks=0;stage=3;
            }else if(stage==3){
                if(g==before){if(++ticks>400)throw new AssertionError("automatic safe checkpoint did not run");return;}
                require(!checkpoint.equals(g.snapshots.active().get("id").getAsString()),"safe online interval automatically creates a new shared checkpoint");
                checkpoint=g.snapshots.active().get("id").getAsString();
                for(ServerPlayer p:List.of(a,b))require(connections.get(p.getUUID())==p.connection&&logins.get(p.getUUID())==1,"capture retained original connection: "+p.getGameProfile().getName());
                rule(g,"rbdAutoCheckpoint","false");a.serverLevel().setBlockAndUpdate(marker,Blocks.AIR.defaultBlockState());
                for(ServerPlayer p:List.of(a,b)){p.getInventory().clearContent();p.getInventory().add(new ItemStack(Items.DIAMOND,9));p.setExperienceLevels(12);}
                before=g;stage=4;a.removeAllEffects();a.invulnerableTime=0;a.hurt(a.damageSources().genericKill(),Float.MAX_VALUE);
                require(g.returnPending(),"real holder death triggers restore of the automatic checkpoint");
            }else if(stage==4&&g!=before){
                long snapshots;try(var paths=Files.list(g.snapshots.control.resolve("snapshots"))){snapshots=paths.count();}
                if(snapshots!=1){if(++ticks>600)throw new AssertionError("background checkpoint retirement did not finish");return;}
                require(snapshots==1,"older complete checkpoint removed by background retention");
                try(var paths=Files.list(g.snapshots.control.resolve("failed"))){require(paths.count()==1,"one replaced-world recovery backup retained");}
                require(checkpoint.equals(g.snapshots.active().get("id").getAsString()),"death restores the latest automatic checkpoint without creating another");
                require(a.serverLevel().getBlockState(marker).is(Blocks.DIAMOND_BLOCK),"world block restored from automatic checkpoint");
                for(ServerPlayer p:List.of(a,b)){
                    require(p.getInventory().countItem(Items.GOLD_INGOT)==5&&p.getInventory().countItem(Items.DIAMOND)==0&&p.experienceLevel==0,"inventory and XP restored: "+p.getGameProfile().getName());
                    require(connections.get(p.getUUID())==p.connection&&logins.get(p.getUUID())==1,"death retained original connection without another login: "+p.getGameProfile().getName());
                }
                var book=g.archive.books().stream().filter(x->x.get("authority").getAsBoolean()).findFirst().orElseThrow();
                boolean clientImage=false,terminal=false;long frames=0;
                try(var reader=g.archive.reader(book.get("head").getAsString())){dev.rbd.memory.MemoryFrame f;while((f=reader.next())!=null){frames++;clientImage|=!f.png().isEmpty();terminal|=f.body()!=null&&f.body().terminal();}}
                require(clientImage&&terminal,"async first-person images and terminal memory survived save and return");
                JsonObject result=new JsonObject();result.addProperty("result","PASS");result.addProperty("version",net.neoforged.fml.ModList.get().getModContainerById("rbd").orElseThrow().getModInfo().getVersion().toString());result.add("checks",checks);result.add("pauseMillis",pauses);result.addProperty("recordedFrames",frames);result.addProperty("testIntervalTicks",100);result.addProperty("defaultIntervalTicks",12000);result.addProperty("elapsedSeconds",(System.nanoTime()-start)/1e9);result.add("loginCounts",new Gson().toJsonTree(logins));
                var sorted=steadyTicks.stream().mapToLong(Long::longValue).sorted().toArray();
                if(sorted.length>0){result.addProperty("steadyTicks",sorted.length);result.addProperty("steadyMeanMs",Arrays.stream(sorted).average().orElseThrow()/1e6);result.addProperty("steadyP95Ms",sorted[(int)((sorted.length-1)*0.95)]/1e6);result.addProperty("steadyMaxMs",sorted[sorted.length-1]/1e6);}
                AtomicJson.write(report(),result);done=true;
            }
        }catch(Throwable error){done=true;error.printStackTrace();try{JsonObject result=new JsonObject();result.addProperty("result","FAIL");result.addProperty("stage",stage);result.addProperty("error",error.toString());result.add("checks",checks);AtomicJson.write(report(),result);}catch(Exception ignored){}}
    }
}
