package dev.rbd.runtime;

import dev.rbd.io.AtomicJson;
import dev.rbd.mixin.*;
import dev.rbd.network.RbdNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.*;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.*;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Closes every world writer while retaining the server and each play-protocol connection. */
public final class ConnectedReturn {
    private static volatile ConnectedReturn current;
    private final GameSession game;
    private final List<ServerPlayer> players;
    private final List<ServerLevel> levels;
    private CompletableFuture<Void> disk;
    private final CompletableFuture<Void> preflight;
    private int phase=-1;
    private boolean loading;
    private Throwable failure;
    private final Queue<Runnable> afterResume=new ConcurrentLinkedQueue<>();
    private final List<io.netty.util.concurrent.ScheduledFuture<?>> heartbeats=new ArrayList<>();
    private ConnectedReturn(GameSession game){
        this.game=game;players=new ArrayList<>(game.server.getPlayerList().getPlayers());
        levels=new ArrayList<>();game.server.getAllLevels().forEach(levels::add);
        var dataConfiguration=game.server.getWorldData().getDataConfiguration();
        preflight=CompletableFuture.runAsync(()->{
            try{
                var tx=AtomicJson.read(game.snapshots.control.resolve("transaction.json"));
                if(!tx.get("operation").getAsString().equals("RESTORE"))return;
                var checkpoint=game.snapshots.control.resolve("snapshots").resolve(tx.get("checkpoint").getAsString());
                dev.rbd.io.SnapshotStore.verify(checkpoint.resolve("tree"),AtomicJson.read(checkpoint.resolve("manifest.json")));
                var tag=NbtIo.readCompressed(checkpoint.resolve("tree/level.dat"),NbtAccounter.unlimitedHeap());
                var savedConfig=LevelStorageSource.readDataConfig(new com.mojang.serialization.Dynamic<>(NbtOps.INSTANCE,tag.getCompound("Data")));
                if(!dataConfiguration.dataPacks().getEnabled().equals(savedConfig.dataPacks().getEnabled())||!dataConfiguration.dataPacks().getDisabled().equals(savedConfig.dataPacks().getDisabled())||!dataConfiguration.enabledFeatures().equals(savedConfig.enabledFeatures())||!packInventory(game.snapshots.world.resolve("datapacks")).equals(packInventory(checkpoint.resolve("tree/datapacks"))))
                    throw new IOException("Checkpoint data packs differ from the running server; world remains paused with connections retained. Restore the matching packs before retrying.");
            }catch(Exception error){throw new CompletionException(error);}
        });
        for(ServerPlayer player:players)heartbeat(player);
    }
    private void heartbeat(ServerPlayer player){
            var connection=((CommonConnectionAccess)player.connection).rbd$connection();
            // Stateless play-protocol ping/pong also protects sockets during a blocking save flush.
            heartbeats.add(connection.channel().eventLoop().scheduleAtFixedRate(()->{
                if(connection.isConnected())connection.send(new net.minecraft.network.protocol.common.ClientboundPingPacket(0x524244));
            },0,5,TimeUnit.SECONDS));
    }
    public static boolean paused(){return current!=null;}
    public static void pauseWithFault(GameSession game,Throwable error){
        game.transitioning=true;if(current==null)current=new ConnectedReturn(game);current.fail(error);
    }
    public static boolean defer(Runnable action){var task=current;if(task==null)return false;task.afterResume.add(action);return true;}
    public static void begin(GameSession game){
        if(current!=null)throw new IllegalStateException("World return already running");
        current=new ConnectedReturn(game);
    }
    public static boolean tick(MinecraftServer server){
        ConnectedReturn task=current;if(task==null||task.game.server!=server)return false;
        // Connection.tick invokes only keepalives while the levels are closed.
        server.getConnection().tick();
        if(task.loading||task.failure!=null)return true;
        try{task.step();}catch(Throwable error){task.fail(error);}
        return true;
    }
    private void step() throws Exception {
        MinecraftServer server=game.server;ServerWorldAccess access=(ServerWorldAccess)server;
        if(phase==-1){if(!preflight.isDone())return;preflight.join();phase=0;return;}
        if(phase==0){
            // A finish-configuration packet already in flight must finish before closing its world.
            var connections=server.getConnection().getConnections();
            synchronized(connections){for(var connection:connections)if(connection.isConnected()&&connection.getPacketListener() instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl configuration&&((ConfigurationConnectionAccess)configuration).rbd$currentTask() instanceof net.minecraft.server.network.config.JoinWorldTask)return;}
            for(ServerPlayer player:server.getPlayerList().getPlayers())if(!players.contains(player)){
                players.add(player);heartbeat(player);var message=RbdNetwork.message("transition");
                message.addProperty("connected",true);message.addProperty("operation",AtomicJson.read(game.snapshots.control.resolve("transaction.json")).get("operation").getAsString());RbdNetwork.send(player,message);
            }
            NeoForge.EVENT_BUS.post(new dev.rbd.api.WorldReturnEvent.Before(server,game.snapshots.control));
            for(ServerPlayer p:players)p.closeContainer();
            server.getPlayerList().saveAll();
            for(ServerPlayer p:players){
                p.getAdvancements().stopListening();
                if(p.isPassenger()){
                    var mount=p.getRootVehicle();
                    if(mount.hasExactlyOnePlayerPassenger()){
                        p.stopRiding();mount.getPassengersAndSelf().forEach(entity->entity.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER));
                    }
                }
                p.unRide();
                p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.UNLOADED_WITH_PLAYER);
            }
            for(ServerLevel level:levels){level.noSave=false;level.getChunkSource().removeTicketsOnClosing();}
            phase=1;return;
        }
        if(phase==1){
            boolean busy=false;
            for(ServerLevel level:levels){
                level.getChunkSource().tick(()->true,false);
                busy|=level.getChunkSource().chunkMap.hasWork();
            }
            if(busy)return;
            server.saveAllChunks(false,true,false);game.saveGuard.check();
            for(ServerLevel level:levels)NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
            disk=CompletableFuture.runAsync(()->{
                try{
                    for(ServerLevel level:levels)level.close();
                    // Zipped world data packs also hold file handles, especially on Windows.
                    server.getServerResources().resourceManager().close();
                    game.saveGuard.check();access.rbd$storage().close();
                    game.snapshots.markClosed();
                    if((dev.rbd.testing.CompatScenario.allowed()&&Boolean.getBoolean("rbd.connectedTest"))||(dev.rbd.testing.LiveScenario.ENABLED&&game.snapshots.world.getFileName().toString().startsWith("RbdLiveTest-")))
                        Thread.sleep(Long.getLong("rbd.connectedDelayMillis",0L));
                    game.snapshots.complete();
                }catch(Exception e){throw new CompletionException(e);}
            });
            phase=2;return;
        }
        if(phase==2&&disk.isDone()){
            disk.join();loading=true;
            reopen(access);loading=false;phase=3;
        }
        if(phase==3){
            game.saveGuard.close();ArchiveLibrary.clear();
            GameSession fresh=new GameSession(server);GameSession.current=fresh;
            ArchiveLibrary.generate(fresh);
            for(ServerPlayer player:server.getPlayerList().getPlayers()){
                fresh.login(player);RbdNetwork.send(player,RbdNetwork.message("transition_complete"));
            }
            LoggerFactory.getLogger("rbd").info("RBD connected return complete: {} retained play connections",server.getPlayerList().getPlayerCount());
            current=null;
            heartbeats.forEach(timer->timer.cancel(false));
            NeoForge.EVENT_BUS.post(new dev.rbd.api.WorldReturnEvent.After(server,game.snapshots.control));
            Runnable action;while((action=afterResume.poll())!=null)action.run();
            fresh.pruneObsoleteCheckpoints();
        }
    }
    private void reopen(ServerWorldAccess access) throws Exception {
        MinecraftServer server=game.server;
        var resourceManager=new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,server.getPackRepository().openAllSelected());
        access.rbd$resources(new MinecraftServer.ReloadableResources(resourceManager,server.getServerResources().managers()));
        var storage=LevelStorageSource.createDefault(game.snapshots.world.getParent()).createAccess(game.snapshots.world.getFileName().toString());
        access.rbd$storage(storage);var playerStorage=storage.createPlayerStorage();access.rbd$playerStorage(playerStorage);
        var dynamic=storage.getDataTag();
        var restored=LevelStorageSource.getLevelDataAndDimensions(dynamic,LevelStorageSource.readDataConfig(dynamic),
            server.registryAccess().registryOrThrow(Registries.LEVEL_STEM),server.registryAccess());
        access.rbd$worldData(restored.worldData());
        var retainedRules=game.snapshots.control.resolve("returned_rules.json");
        if(java.nio.file.Files.exists(retainedRules))dev.rbd.rules.WorldRules.apply(server.getWorldData().getGameRules(),AtomicJson.read(retainedRules).getAsJsonObject("values"));
        access.rbd$levels().clear();server.markWorldsDirty();
        access.rbd$scoreboard(new ServerScoreboard(server));
        for(var boss:server.getCustomBossEvents().getEvents())boss.removeAllPlayers();
        access.rbd$bossEvents(new CustomBossEvents());
        access.rbd$structures(new StructureTemplateManager(server.getResourceManager(),storage,server.getFixerUpper(),server.registryAccess().lookupOrThrow(Registries.BLOCK)));
        var list=server.getPlayerList();PlayerListAccess playerAccess=(PlayerListAccess)list;
        playerAccess.rbd$playerStorage(playerStorage);
        playerAccess.rbd$advancements().values().forEach(PlayerAdvancements::stopListening);
        playerAccess.rbd$advancements().clear();playerAccess.rbd$stats().clear();
        playerAccess.rbd$players().clear();playerAccess.rbd$playersById().clear();
        access.rbd$loadLevel();server.markWorldsDirty();
        for(ServerPlayer old:players){
            ServerPlayer player=new ServerPlayer(server,server.overworld(),old.getGameProfile(),old.clientInformation());
            Optional<CompoundTag> saved=list.load(player);
            ResourceKey<Level> dimension=saved.flatMap(tag->Level.RESOURCE_KEY_CODEC.parse(NbtOps.INSTANCE,tag.get("Dimension")).result()).orElse(Level.OVERWORLD);
            ServerLevel level=server.getLevel(dimension);
            if(level==null)throw new IOException("Checkpoint player dimension is missing: "+dimension.location());
            player.setServerLevel(level);player.loadGameTypes(saved.orElse(null));
            // The authenticated chat session belongs to the retained connection, not the saved life.
            player.setChatSession(old.getChatSession());
            player.connection=old.connection;player.connection.player=player;player.setId(old.getId());
            PlayConnectionAccess connection=(PlayConnectionAccess)player.connection;
            connection.rbd$chunkSender(new PlayerChunkSender(((CommonConnectionAccess)player.connection).rbd$connection().isMemoryConnection()));
            connection.rbd$floatingTicks(0);connection.rbd$vehicleFloatingTicks(0);player.connection.resetPosition();
            RbdNetwork.send(player,RbdNetwork.message("world_reset"));
            player.connection.send(new ClientboundRespawnPacket(player.createCommonSpawnInfo(level),(byte)0));
            player.connection.teleport(player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot());
            playerAccess.rbd$players().add(player);playerAccess.rbd$playersById().put(player.getUUID(),player);
            level.addRespawnedPlayer(player);
            if(saved.isPresent()&&saved.get().contains("RootVehicle",Tag.TAG_COMPOUND))restoreVehicle(player,saved.get().getCompound("RootVehicle"));
            list.sendLevelInfo(player,level);list.sendPlayerPermissionLevel(player);list.sendActivePlayerEffects(player);
            player.onUpdateAbilities();
            player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
            player.connection.send(new ClientboundSetExperiencePacket(player.experienceProgress,player.totalExperience,player.experienceLevel));
            player.connection.send(new ClientboundSetHealthPacket(player.getHealth(),player.getFoodData().getFoodLevel(),player.getFoodData().getSaturationLevel()));
            player.connection.send(new ClientboundUpdateAttributesPacket(player.getId(),player.getAttributes().getSyncableAttributes()));
            var entityData=player.getEntityData().getNonDefaultValues();if(entityData!=null)player.connection.send(new ClientboundSetEntityDataPacket(player.getId(),entityData));
            player.connection.send(new ClientboundUpdateAdvancementsPacket(true,List.of(),Set.of(),Map.of()));
            player.getAdvancements().flushDirty(player);player.getStats().markAllDirty();player.getStats().sendStats(player);
            player.getRecipeBook().sendInitialRecipeBook(player);playerAccess.rbd$sendScoreboard(server.getScoreboard(),player);
            player.initInventoryMenu();player.inventoryMenu.sendAllDataToRemote();
            net.neoforged.neoforge.attachment.AttachmentSync.syncInitialPlayerAttachments(player);
            server.getCustomBossEvents().onPlayerConnect(player);
        }
        list.broadcastAll(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),list.getPlayers()));
    }
    private static void restoreVehicle(ServerPlayer player,CompoundTag tag){
        Entity root=EntityType.loadEntityRecursive(tag.getCompound("Entity"),player.serverLevel(),entity->player.serverLevel().addWithUUID(entity)?entity:null);
        if(root==null)return;
        UUID attach=tag.hasUUID("Attach")?tag.getUUID("Attach"):null;
        if(root.getUUID().equals(attach))player.startRiding(root,true);
        else for(Entity passenger:root.getIndirectPassengers())if(passenger.getUUID().equals(attach)){player.startRiding(passenger,true);break;}
        if(!player.isPassenger()){for(Entity passenger:root.getIndirectPassengers())passenger.discard();root.discard();}
    }
    private void fail(Throwable error){
        current=this;if(GameSession.current!=null)GameSession.current.transitioning=true;
        failure=error;LoggerFactory.getLogger("rbd").error("Connected world return is paused after a failure; connections remain alive",error);
        try{var fault=RbdNetwork.message("transition_fault");fault.addProperty("reason",error.toString());AtomicJson.write(game.snapshots.control.resolve("fault.json"),fault);
            for(ServerPlayer p:players)RbdNetwork.send(p,RbdNetwork.message("transition_fault"));
        }catch(Exception writeError){error.addSuppressed(writeError);}
    }
    private static com.google.gson.JsonObject packInventory(java.nio.file.Path path) throws IOException {
        return java.nio.file.Files.isDirectory(path)?dev.rbd.io.SnapshotStore.inventory(path):new com.google.gson.JsonObject();
    }
    public static void beforeShutdown(MinecraftServer server){
        var task=current;if(task==null||task.game.server!=server)return;
        // An explicit server shutdown must never save detached failed-branch bodies into the checkpoint.
        if(task.disk!=null)try{task.disk.join();}catch(CompletionException ignored){}
        if(task.phase>=2){
            for(ServerLevel level:server.getAllLevels())if(!task.levels.contains(level))try{NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));level.close();}catch(Exception error){LoggerFactory.getLogger("rbd").error("Could not close replacement world during explicit shutdown",error);}
            var access=(PlayerListAccess)server.getPlayerList();access.rbd$players().clear();access.rbd$playersById().clear();
            ((ServerWorldAccess)server).rbd$levels().clear();server.markWorldsDirty();
        }
        task.heartbeats.forEach(timer->timer.cancel(false));current=null;
    }
    private ConnectedReturn(){throw new AssertionError();}
}
