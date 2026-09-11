package dev.rbd.testing;
import com.google.gson.*;
import dev.rbd.io.AtomicJson;
import dev.rbd.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;
/** Opt-in end-to-end fixture, constrained to its own newly created RbdLiveTest world. */
@EventBusSubscriber(modid="rbd")
public final class LiveScenario {
    public static final boolean ENABLED=Boolean.getBoolean("rbd.liveTest");
    private static int stage,wait,returns;
    private static BlockPos anchor;
    private static final BlockPos NETHER_POS=new BlockPos(0,100,0);
    private static UUID npc,priorLoss;
    public static volatile boolean completed,imagePresented,libraryPresented,endingPresented,silencePresented,reducedPresented,returnBlackPresented;
    private static long ordinaryAtPoint;
    private static GameSession dyingSession;
    private static int dyingTick;
    private static net.minecraft.world.phys.Vec3 readPosition;
    public static volatile String failure;
    public static volatile int clientLogins;
    public static volatile int clientResets;
    public static volatile boolean sameClientConnection=true;
    public static volatile boolean pauseMenuReturn;
    public static volatile boolean pauseMenuObserved;
    private static net.minecraft.server.MinecraftServer originalServer;
    private static Object originalPlayerConnection;
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(!ENABLED||completed||failure!=null)return;
        var game=GameSession.current;if(game==null||game.transitioning||!game.snapshots.world.getFileName().toString().startsWith("RbdLiveTest-"))return;
        if(game.holders().isEmpty())return; // The integrated server ticks before its first player joins.
        var p=game.server.getPlayerList().getPlayer(game.onlyHolder());if(p==null)return;
        try{
            if(originalServer==null){originalServer=game.server;originalPlayerConnection=p.connection;}
            require(game.server==originalServer&&p.connection==originalPlayerConnection,"singleplayer return retains its server and play connection");
            if(stage==5){
                if(game!=dyingSession)stage=2;
                else {require(game.server.getTickCount()-dyingTick<240,"environmental death must cause an actual world return");return;}
            }
            if(game.snapshots.active()==null)return;
            if(wait++<60)return;wait=0;
            if(stage==0){
                anchor=p.blockPosition().offset(3,0,0);p.getInventory().clearContent();p.setExperienceLevels(0);
                game.server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION).set(false,game.server);
                p.setHealth(8);
                p.serverLevel().setBlockAndUpdate(anchor,Blocks.DIAMOND_BLOCK.defaultBlockState());
                p.serverLevel().setBlockAndUpdate(anchor.offset(2,0,0),Blocks.CHEST.defaultBlockState());
                ((net.minecraft.world.level.block.entity.ChestBlockEntity)p.serverLevel().getBlockEntity(anchor.offset(2,0,0))).setItem(0,new ItemStack(Items.DIAMOND,5));
                game.server.getLevel(Level.NETHER).setBlockAndUpdate(NETHER_POS,Blocks.EMERALD_BLOCK.defaultBlockState());
                var actor=EntityType.VILLAGER.create(p.serverLevel());actor.setCustomName(Component.literal("Live fixture witness"));actor.setNoAi(true);actor.moveTo(p.getX(),p.getY(),p.getZ()+4);p.serverLevel().addFreshEntity(actor);npc=actor.getUUID();
                var lost=EntityType.VILLAGER.create(p.serverLevel());lost.setCustomName(Component.literal("Already lost before this point"));lost.setNoAi(true);lost.moveTo(p.getX()+5,p.getY(),p.getZ());p.serverLevel().addFreshEntity(lost);priorLoss=lost.getUUID();lost.hurt(p.damageSources().genericKill(),Float.MAX_VALUE);
                ordinaryAtPoint=game.archive.books().stream().filter(b->!b.get("authority").getAsBoolean()&&game.visible(b)).count();
                p.setYRot(0);p.setXRot(0);stage=1;game.milestone("rbd:live_fixture");
            }else if(stage==1){
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,new ItemStack(Items.TOTEM_OF_UNDYING));
                p.hurt(p.damageSources().generic(),Float.MAX_VALUE);
                require(p.isAlive()&&!game.returnPending()&&p.getOffhandItem().isEmpty(),"totem prevents actual death and return");
                p.serverLevel().setBlockAndUpdate(anchor,Blocks.GOLD_BLOCK.defaultBlockState());game.server.getLevel(Level.NETHER).setBlockAndUpdate(NETHER_POS,Blocks.AIR.defaultBlockState());
                ((net.minecraft.world.level.block.entity.ChestBlockEntity)p.serverLevel().getBlockEntity(anchor.offset(2,0,0))).clearContent();
                p.getInventory().add(new ItemStack(Items.NETHERITE_INGOT,7));p.giveExperienceLevels(12);
                var actor=p.serverLevel().getEntity(npc);if(actor!=null)actor.hurt(p.damageSources().genericKill(),Float.MAX_VALUE);
                // After verifying the totem, exercise ordinary environmental death processing.
                p.removeAllEffects();p.invulnerableTime=0;p.setHealth(1);stage=5;dyingSession=game;dyingTick=game.server.getTickCount();
                if(returns==0){
                    BlockPos cell=p.blockPosition();
                    for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=0;dy<=2;dy++)
                        p.serverLevel().setBlockAndUpdate(cell.offset(dx,dy,dz),(dx==0&&dz==0?Blocks.WATER:Blocks.GLASS).defaultBlockState());
                    p.setAirSupply(40);
                }else if(returns==1){
                    p.serverLevel().setBlockAndUpdate(p.blockPosition().below(),Blocks.MAGMA_BLOCK.defaultBlockState());
                    p.setRemainingFireTicks(100);
                }else p.connection.teleport(p.getX(),p.getY()+18,p.getZ(),p.getYRot(),p.getXRot());
            }else if(stage==2){
                require(p.serverLevel().getBlockState(anchor).is(Blocks.DIAMOND_BLOCK),"overworld block restored");
                require(game.server.getLevel(Level.NETHER).getBlockState(NETHER_POS).is(Blocks.EMERALD_BLOCK),"Nether restored");
                require(p.getInventory().countItem(Items.NETHERITE_INGOT)==0,"inventory restored");require(p.experienceLevel==0,"experience restored");
                require(((net.minecraft.world.level.block.entity.ChestBlockEntity)p.serverLevel().getBlockEntity(anchor.offset(2,0,0))).getItem(0).getCount()==5,"container contents restored");
                require(p.serverLevel().getEntity(npc)!=null,"dead NPC restored");
                require(p.serverLevel().getEntity(priorLoss)==null,"return cannot undo a loss already committed at the current point");
                require(p.getHealth()==8,"return restores the injured checkpoint body without free healing");
                returns++;
                if(returns<3){stage=1;return;}
                var books=game.archive.books().stream().filter(b->b.get("authority").getAsBoolean()).toList();require(books.size()==3,"three holder deaths create three books");
                var causes=books.stream().map(b->b.getAsJsonObject("ending").get("damageType").getAsString()).collect(java.util.stream.Collectors.toSet());
                require(causes.contains("drown")&&causes.contains("fall")&&(causes.contains("onFire")||causes.contains("hotFloor")),"actual drowning, heat and fall are distinct recorded deaths");
                long visibleOrdinary=game.archive.books().stream().filter(b->!b.get("authority").getAsBoolean()&&game.visible(b)).count();require(visibleOrdinary==ordinaryAtPoint,"failed ordinary books hidden, prior losses remain committed");
                readPosition=p.position();game.open(p,books.getFirst().get("id").getAsString());stage=3;
            }else if(stage==3){
                if(!imagePresented){require(game.reading(p.getUUID()),"client must display a recorded first-person image before playback ends");return;}
                if(game.reading(p.getUUID()))return;
                require(endingPresented&&silencePresented&&reducedPresented,"whole death ending and reduced-effects presentation must occur before completion");
                require(returnBlackPresented,"actual death return must present its isolated sensory interruption");
                require(game.archive.books().stream().filter(b->b.get("authority").getAsBoolean()).count()==3,"reading death never creates another actual death or return");
                require(p.getHealth()==8,"reading does not transfer remembered lethal injury to the body");
                require(p.position().distanceToSqr(readPosition)<0.1,"forged movement is rejected during reading");require(p.serverLevel().getBlockState(anchor).is(Blocks.DIAMOND_BLOCK),"reading preserves world");
                String head=game.recorder.seal(p.getUUID());boolean rememberedReading=false;
                try(var history=game.archive.reader(head)){dev.rbd.memory.MemoryFrame frame;while((frame=history.next())!=null)if(frame.visualSource().startsWith("EXPERIENCED_MEMORY/"))rememberedReading=true;}
                require(rememberedReading,"presented book experiences become part of the reader's life");
                var book=game.archive.books().stream().filter(b->b.get("authority").getAsBoolean()).findFirst().orElseThrow();
                int index=Integer.parseInt(book.get("id").getAsString().substring(0,1),16);var library=game.branch.json.getAsJsonObject("library");
                int bx=library.get("x").getAsInt(),by=library.get("y").getAsInt(),bz=library.get("z").getAsInt();
                BlockPos shelf=new BlockPos(bx+(index<8?-7:7),by+1,bz-7+(index%8)*2);
                p.connection.teleport(bx+(index<8?-5:5)+0.5,by+1,shelf.getZ()+0.5,index<8?90:-90,0);
                ArchiveLibrary.browse(game,p,shelf,index,0);stage=4;
            }else if(stage==4){
                if(!libraryPresented)return;
                require(clientLogins==1&&sameClientConnection,"integrated client never disconnects or logs in again during returns");
                require(pauseMenuReturn&&pauseMenuObserved,"opening the pause menu does not save closed worlds or deadlock return");
                try(var files=Files.list(game.snapshots.control.resolve("receipts"))){require(clientResets==files.filter(path->path.toString().endsWith(".json")).count(),"every world transaction completed its client cache reset");}
                var report=new JsonObject();report.addProperty("result","PASS");report.addProperty("returns",returns);report.addProperty("books",3);report.addProperty("world",game.snapshots.world.toString());
                report.addProperty("verified","3 environmental deaths (drowning, heat, falling) and integrated returns; overworld/Nether/inventory/containers/XP/NPC restoration; totem; irreversible pre-checkpoint loss; injured checkpoint; distinct lives; branch visibility; native first-person playback; full death ending/silence; reduced effects; premature ack cannot skip ending; reading death is not actual death; nested memory; rejected movement; physical library UI");
                AtomicJson.write(Path.of("rbd-live-report.json").toAbsolutePath(),report);completed=true;
            }
        }catch(Exception ex){failure=ex.toString();try{var report=new JsonObject();report.addProperty("result","FAIL");report.addProperty("stage",stage);report.addProperty("error",failure);AtomicJson.write(Path.of("rbd-live-report.json").toAbsolutePath(),report);}catch(Exception ignored){};ex.printStackTrace();}
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("Live test: "+message);}
}
