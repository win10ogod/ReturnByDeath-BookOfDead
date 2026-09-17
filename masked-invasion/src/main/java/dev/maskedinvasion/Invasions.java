package dev.maskedinvasion;

import dev.rbd.api.WorldReturnEvent;
import dev.rbd.phantom.PhantomExecution;
import dev.rbd.runtime.GameSession;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;

/** Server-thread state machine. Chunk preparation is asynchronous and no network acknowledgement is awaited. */
@EventBusSubscriber(modid=MaskedInvasion.ID)
public final class Invasions {
    private static final TicketType<UUID> TICKET=TicketType.create("masked_invasion",UUID::compareTo);
    private static final Map<UUID,Prepared> prepared=new HashMap<>();
    private static final Map<UUID,ServerBossEvent> bars=new HashMap<>();
    private static FormCatalog catalog;
    private static boolean replacing;
    private record Prepared(net.minecraft.resources.ResourceKey<Level> dimension,ChunkPos center,List<CompletableFuture<?>> chunks) {
        boolean ready(){return chunks.stream().allMatch(f->f.isDone()&&!f.isCompletedExceptionally()&&!f.isCancelled());}
    }
    public static FormCatalog catalog(){if(catalog==null)throw new IllegalStateException("Form catalogue has not loaded");return catalog;}
    public static boolean returning(MinecraftServer server){var game=GameSession.current;return replacing||(game!=null&&game.server==server&&(game.transitioning||game.returnPending()));}
    public static boolean blocksCheckpoint(MinecraftServer server){return !replacing&&!RaidData.get(server).raids.isEmpty();}
    private static boolean eligible(ServerPlayer p){return !p.isSpectator()&&!p.isCreative();}
    private static List<ServerPlayer> online(MinecraftServer s,RaidData.Raid raid){return raid.defenders.keySet().stream().map(s.getPlayerList()::getPlayer).filter(Objects::nonNull).toList();}
    private static void message(MinecraftServer s,RaidData.Raid r,String key,Object...args){for(var p:online(s,r))p.sendSystemMessage(Component.translatable(key,args));}
    private static long interval(GameRules rules){return InvasionRules.DAYS.get(rules)*24000L;}

    @SubscribeEvent public static void started(ServerStartedEvent event){
        replacing=false;clear(event.getServer());
        try{catalog=FormCatalog.load();MaskedInvasion.LOG.info("Loaded {} invasion forms",catalog.all().size());}
        catch(Exception e){throw new IllegalStateException("Invalid config/masked_invasion-forms.json: "+e.getMessage(),e);}
        RaidData.get(event.getServer()).lastDay=event.getServer().overworld().getDayTime();
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e){clear(e.getServer());catalog=null;replacing=false;}
    @SubscribeEvent public static void before(WorldReturnEvent.Before e){replacing=true;clear(e.getServer());}
    @SubscribeEvent public static void after(WorldReturnEvent.After e){replacing=false;RaidData.get(e.getServer()).lastDay=e.getServer().overworld().getDayTime();}
    private static void clear(MinecraftServer s){for(var id:List.copyOf(prepared.keySet()))release(s,id);for(var bar:bars.values())bar.removeAllPlayers();bars.clear();}
    private static void release(MinecraftServer s,UUID id){var p=prepared.remove(id);if(p!=null){var level=s.getLevel(p.dimension());if(level!=null)level.getChunkSource().removeRegionTicket(TICKET,p.center(),3,id);}var bar=bars.remove(id);if(bar!=null)bar.removeAllPlayers();}
    private static Prepared prepare(ServerLevel level,RaidData.Raid raid){
        return prepared.computeIfAbsent(raid.id,id->{
            var center=new ChunkPos(raid.home.position());var cache=level.getChunkSource();
            cache.addRegionTicket(TICKET,center,3,id);
            List<CompletableFuture<?>> futures=new ArrayList<>();
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)futures.add(cache.getChunkFuture(center.x+x,center.z+z,ChunkStatus.FULL,true));
            return new Prepared(level.dimension(),center,futures);
        });
    }
    private static boolean readyForRaid(MinecraftServer s){
        var game=GameSession.current;if(game==null||game.server!=s||returning(s))return false;
        if(game.holders().stream().noneMatch(id->{var p=s.getPlayerList().getPlayer(id);return p!=null&&p.isAlive();}))return false;
        try{return (game.supervisor==null?game.snapshots.active():game.supervisor.active())!=null;}
        catch(IOException e){MaskedInvasion.LOG.warn("Cannot read return checkpoint; invasion remains scheduled",e);return false;}
    }
    public static RaidData.Home home(ServerPlayer p){
        var server=p.server;var dimension=p.getRespawnDimension();var pos=p.getRespawnPosition();
        if(pos==null||server.getLevel(dimension)==null){var level=server.overworld();return new RaidData.Home(Level.OVERWORLD,level.getSharedSpawnPos(),level.getSharedSpawnAngle());}
        var result=new RaidData.Home(dimension,pos.immutable(),p.getRespawnAngle(),p.isRespawnForced());
        if(SafePositions.loaded(server.getLevel(dimension),pos)&&!SafePositions.validRespawn(server.getLevel(dimension),result)){var level=server.overworld();return new RaidData.Home(Level.OVERWORLD,level.getSharedSpawnPos(),level.getSharedSpawnAngle());}
        return result;
    }
    public static RaidData.Raid start(MinecraftServer s,ServerPlayer leader,int forcedWave){
        if(!eligible(leader))throw new IllegalArgumentException("message.masked_invasion.survival_required");
        if(!readyForRaid(s))throw new IllegalArgumentException("message.masked_invasion.checkpoint_required");
        var data=RaidData.get(s);if(data.forPlayer(leader.getUUID())!=null)throw new IllegalArgumentException("message.masked_invasion.already_active");
        var rules=s.getGameRules();var anchor=home(leader);int radius=InvasionRules.RADIUS.get(rules),height=InvasionRules.HEIGHT.get(rules);
        var group=new LinkedHashMap<UUID,RaidData.Home>();int wave=1;
        for(var p:s.getPlayerList().getPlayers()){
            var h=home(p);if(!eligible(p)||data.forPlayer(p.getUUID())!=null||!h.dimension().equals(anchor.dimension())||h.position().distSqr(anchor.position())>radius*(double)radius||Math.abs(h.position().getY()-anchor.position().getY())>height)continue;
            group.put(p.getUUID(),h);var progress=data.players.computeIfAbsent(p.getUUID(),id->new RaidData.Progress(data.clock+interval(rules)));
            wave=Math.max(wave,progress.wave==Integer.MAX_VALUE?Integer.MAX_VALUE:progress.wave+1);
        }
        // Do not create overlapping independent encounters at one base.
        if(data.raids.values().stream().anyMatch(r->r.home.dimension().equals(anchor.dimension())&&r.home.position().distSqr(anchor.position())<Math.pow(r.radius+radius,2)))throw new IllegalArgumentException("message.masked_invasion.nearby_active");
        if(forcedWave>0)wave=forcedWave;
        long seed=s.overworld().random.nextLong();var forms=WavePlan.create(catalog(),rules,wave,group.size(),seed);
        var raid=new RaidData.Raid(UUID.randomUUID(),anchor,wave,radius,height,forms.size(),seed,InvasionRules.WARNING.get(rules),InvasionRules.DURATION.get(rules));raid.defenders.putAll(group);
        for(var form:forms){var a=new RaidData.Attacker(UUID.randomUUID(),form.id(),anchor.position());raid.attackers.put(a.id,a);}
        data.raids.put(raid.id,raid);data.setDirty();
        message(s,raid,"message.masked_invasion.warning",wave,(raid.warning+19)/20,raid.total);return raid;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        var s=event.getServer();if(returning(s)||catalog==null)return;
        var data=RaidData.get(s);var rules=s.getGameRules();long day=s.overworld().getDayTime();
        long delta=data.lastDay==Long.MIN_VALUE?0:Math.max(0,day-data.lastDay);data.lastDay=day;
        boolean occupied=s.getPlayerList().getPlayers().stream().anyMatch(Invasions::eligible);
        if(occupied&&InvasionRules.ENABLED.get(rules))data.clock+=delta;
        if(!InvasionRules.ENABLED.get(rules)){if(!data.raids.isEmpty())stopAll(s);return;}
        if(s.getTickCount()%20==0){
            for(var p:s.getPlayerList().getPlayers()){
                if(!eligible(p))continue;
                var progress=data.players.computeIfAbsent(p.getUUID(),id->new RaidData.Progress(data.clock+interval(rules)));
                if(progress.intervalDays==0)progress.intervalDays=InvasionRules.DAYS.get(rules);
                if(progress.intervalDays!=InvasionRules.DAYS.get(rules)){progress.nextAt+=(InvasionRules.DAYS.get(rules)-(long)progress.intervalDays)*24000L;progress.intervalDays=InvasionRules.DAYS.get(rules);}
                if(progress.pendingEmeralds>0){int amount=progress.pendingEmeralds;progress.pendingEmeralds=0;while(amount>0){int count=Math.min(amount,64);var stack=new ItemStack(Items.EMERALD,count);if(!p.addItem(stack))p.drop(stack,false);amount-=count;}}
                if(data.forPlayer(p.getUUID())==null&&data.clock>=progress.nextAt&&readyForRaid(s)){
                    try{start(s,p,0);}catch(IllegalArgumentException ignored){/* Existing nearby raid retains this due time. */}
                }
            }
            data.setDirty();
        }
        for(var raid:List.copyOf(data.raids.values())){
            if(returning(s))break;
            var players=online(s,raid);raid.suspended=players.isEmpty();if(raid.suspended){release(s,raid.id);continue;}
            if(raid.phase==RaidData.Phase.FAILED){executeFailure(s,raid);continue;}
            var level=s.getLevel(raid.home.dimension());
            if(level==null){if(s.getTickCount()%200==0)message(s,raid,"message.masked_invasion.missing_dimension");continue;}
            if(!prepare(level,raid).ready())continue;
            if(raid.phase==RaidData.Phase.PREPARING){
                // A bed may have been destroyed while its chunk was unloaded. Re-group at valid homes after preparation.
                if(!SafePositions.validRespawn(level,raid.home)){
                    message(s,raid,"message.masked_invasion.invalid_respawn");
                    for(var id:raid.defenders.keySet())data.players.computeIfAbsent(id,k->new RaidData.Progress(0)).nextAt=data.clock;
                    data.raids.remove(raid.id);release(s,raid.id);data.setDirty();continue;
                }
                // Resolve landing before the warning starts. Never alter the player's buildings.
                if(SafePositions.home(level,raid.home.position(),null).isEmpty()){
                    if(s.getTickCount()%200==0)message(s,raid,"message.masked_invasion.no_landing");continue;
                }
                raid.phase=RaidData.Phase.WARNING;data.setDirty();
            }
            if(raid.phase==RaidData.Phase.WARNING){
                if(raid.warning>0)raid.warning--;
                if(raid.warning==0){
                    boolean ready=true;if(InvasionRules.RECALL.get(rules))for(var p:players)if(p.isAlive()&&!recall(s,p,raid))ready=false;
                    if(ready){raid.phase=RaidData.Phase.ACTIVE;message(s,raid,"message.masked_invasion.begin",raid.wave,raid.radius);data.setDirty();}
                }
            }else if(raid.phase==RaidData.Phase.ACTIVE){
                if(s.getTickCount()%5==0&&InvasionRules.CONFINEMENT.get(rules))for(var p:players)if(p.isAlive()&&eligible(p)&&!raid.contains(p.level().dimension(),p.getX(),p.getY(),p.getZ())){if(recall(s,p,raid))p.displayClientMessage(Component.translatable("message.masked_invasion.boundary"),true);}
                if(s.getTickCount()%20==0)spawn(s,level,raid);
                if(raid.remaining>0&&--raid.remaining==0){fail(s,raid,"message.masked_invasion.timeout");continue;}
                if(raid.attackers.isEmpty()){victory(s,raid);continue;}
                if(s.getTickCount()%20==0&&InvasionRules.PARTICLES.get(rules))for(int i=0;i<24;i++){
                    double a=Math.PI*2*i/24;level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,raid.home.position().getX()+0.5+Math.cos(a)*raid.radius,raid.home.position().getY()+1,raid.home.position().getZ()+0.5+Math.sin(a)*raid.radius,1,0,0.3,0,0);
                }
            }
            if(s.getTickCount()%20==0)updateBar(s,raid,players);
        }
    }
    private static void spawn(MinecraftServer s,ServerLevel level,RaidData.Raid raid){
        int budget=InvasionRules.SPAWN_RATE.get(s.getGameRules());var random=new Random(raid.seed+s.getTickCount());
        for(var attacker:raid.attackers.values()){
            if(attacker.spawned||budget--<=0)continue;
            var pos=SafePositions.spawn(level,raid,InvasionRules.SPAWN_MIN.get(s.getGameRules()),InvasionRules.SPAWN_MAX.get(s.getGameRules()),random);
            if(pos.isEmpty()){raid.spawnWait++;if(raid.spawnWait>=60)pos=SafePositions.home(level,raid.home.position(),null);}
            if(pos.isEmpty())continue;
            var mob=MaskedInvasion.INVADER.get().create(level);if(mob==null)throw new IllegalStateException("Cannot create invader");
            mob.setUUID(attacker.id);mob.equip(raid.id,raid.wave,catalog().get(attacker.form));mob.moveTo(pos.get().x,pos.get().y,pos.get().z,random.nextFloat()*360,0);
            if(level.addFreshEntity(mob)){attacker.spawned=true;attacker.position=mob.blockPosition();RaidData.get(s).setDirty();}
        }
    }
    public static boolean recall(MinecraftServer s,ServerPlayer player,RaidData.Raid raid){
        var own=raid.defenders.getOrDefault(player.getUUID(),raid.home);var level=s.getLevel(raid.home.dimension());if(level==null)return false;
        var pos=SafePositions.home(level,own.position(),player).or(()->SafePositions.home(level,raid.home.position(),player));
        if(pos.isEmpty())return false;
        player.stopRiding();player.teleportTo(level,pos.get().x,pos.get().y,pos.get().z,own.angle(),0);player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;return true;
    }
    public static void returnInvader(InvaderSummonEntity mob,RaidData.Raid raid){
        var level=(ServerLevel)mob.level();var pos=SafePositions.spawn(level,raid,2,Math.min(12,raid.radius-2),new Random(mob.getUUID().getLeastSignificantBits()+mob.tickCount));
        pos.ifPresent(v->{mob.getNavigation().stop();mob.teleportTo(v.x,v.y,v.z);mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;});
    }
    private static void updateBar(MinecraftServer s,RaidData.Raid raid,List<ServerPlayer> online){
        var bar=bars.computeIfAbsent(raid.id,id->new ServerBossEvent(Component.empty(),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.PROGRESS));
        for(var p:List.copyOf(bar.getPlayers()))if(!online.contains(p))bar.removePlayer(p);for(var p:online)bar.addPlayer(p);
        if(raid.phase==RaidData.Phase.WARNING){bar.setName(Component.translatable("bar.masked_invasion.warning",raid.wave,(raid.warning+19)/20));bar.setProgress(1);}
        else{bar.setName(Component.translatable("bar.masked_invasion.active",raid.wave,raid.attackers.size(),raid.remaining>0?String.format(Locale.ROOT,"%d:%02d",raid.remaining/1200,raid.remaining/20%60):"∞"));bar.setProgress(Math.min(1,raid.attackers.size()/(float)Math.max(1,raid.total)));}
    }
    public static void defeated(ServerLevel level,UUID raidId,UUID attacker){
        if(returning(level.getServer()))return;var data=RaidData.get(level.getServer());var raid=data.raids.get(raidId);
        if(raid!=null&&raid.phase==RaidData.Phase.ACTIVE&&raid.attackers.remove(attacker)!=null)data.setDirty();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void died(LivingDeathEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p)||replacing||!InvasionRules.DEATH_FAILURE.get(p.server.getGameRules()))return;
        // A cancelled death is a failure only when RBD has accepted that holder's death.
        if(e.isCanceled()&&!returning(p.server))return;
        var raid=RaidData.get(p.server).forPlayer(p.getUUID());if(raid!=null&&raid.phase==RaidData.Phase.ACTIVE)fail(p.server,raid,"message.masked_invasion.death");
    }
    public static void fail(MinecraftServer s,RaidData.Raid raid,String reason){
        if(raid.phase==RaidData.Phase.FAILED)return;raid.phase=RaidData.Phase.FAILED;RaidData.get(s).setDirty();message(s,raid,reason);
    }
    private static void executeFailure(MinecraftServer s,RaidData.Raid raid){
        if(returning(s))return;var game=GameSession.current;if(game==null||game.server!=s)return;
        var holder=online(s,raid).stream().filter(p->game.isHolder(p.getUUID())&&p.isAlive()).findFirst().orElse(null);
        if(holder==null)holder=game.holders().stream().map(s.getPlayerList()::getPlayer).filter(Objects::nonNull).filter(ServerPlayer::isAlive).findFirst().orElse(null);
        if(holder==null){if(s.getTickCount()%200==0)message(s,raid,"message.masked_invasion.wait_holder");return;}
        // Record ordinary team deaths first; the holder then follows RBD's connected-return path.
        for(var p:online(s,raid))if(p.isAlive()&&!game.isHolder(p.getUUID()))PhantomExecution.kill(p);
        PhantomExecution.kill(holder);
    }
    private static void victory(MinecraftServer s,RaidData.Raid raid){
        var data=RaidData.get(s);for(var id:raid.defenders.keySet()){
            var progress=data.players.computeIfAbsent(id,k->new RaidData.Progress(0));progress.wave=Math.max(progress.wave,raid.wave);progress.nextAt=data.clock+interval(s.getGameRules());progress.intervalDays=InvasionRules.DAYS.get(s.getGameRules());progress.pendingEmeralds+=InvasionRules.REWARD.get(s.getGameRules());
        }
        message(s,raid,"message.masked_invasion.victory",raid.wave);data.raids.remove(raid.id);data.setDirty();release(s,raid.id);
    }
    public static int stopAll(MinecraftServer s){
        var data=RaidData.get(s);int count=data.raids.size();
        for(var raid:List.copyOf(data.raids.values())){
            var level=s.getLevel(raid.home.dimension());if(level!=null)for(var a:raid.attackers.values()){var entity=level.getEntity(a.id);if(entity instanceof InvaderSummonEntity)entity.discard();}
            for(var id:raid.defenders.keySet()){var progress=data.players.computeIfAbsent(id,k->new RaidData.Progress(0));progress.nextAt=data.clock+interval(s.getGameRules());progress.intervalDays=InvasionRules.DAYS.get(s.getGameRules());}
            message(s,raid,"message.masked_invasion.stopped");release(s,raid.id);
        }
        data.raids.clear();data.setDirty();return count;
    }
    @SubscribeEvent public static void dimension(EntityTravelToDimensionEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!InvasionRules.CONFINEMENT.get(p.server.getGameRules())||returning(p.server))return;
        var raid=RaidData.get(p.server).forPlayer(p.getUUID());if(raid!=null&&raid.phase==RaidData.Phase.ACTIVE){e.setCanceled(true);p.displayClientMessage(Component.translatable("message.masked_invasion.boundary"),true);}
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("masked_invasion")
            .then(Commands.literal("status").executes(c->{var s=c.getSource();var p=s.getPlayerOrException();var d=RaidData.get(s.getServer());var r=d.forPlayer(p.getUUID());var progress=d.players.get(p.getUUID());s.sendSuccess(()->r!=null?Component.translatable("message.masked_invasion.status_active",r.wave,r.attackers.size(),r.phase.name()):Component.translatable("message.masked_invasion.status_idle",progress==null?0:progress.wave,Math.max(0,(progress==null?interval(s.getServer().getGameRules()):progress.nextAt-d.clock)+1199)/1200),false);return 1;}))
            .then(Commands.literal("start").requires(s->s.hasPermission(2)).executes(c->commandStart(c.getSource(),c.getSource().getPlayerOrException(),0))
                .then(Commands.argument("player",EntityArgument.player()).executes(c->commandStart(c.getSource(),EntityArgument.getPlayer(c,"player"),0))
                    .then(Commands.argument("wave",integer(1)).executes(c->commandStart(c.getSource(),EntityArgument.getPlayer(c,"player"),getInteger(c,"wave"))))))
            .then(Commands.literal("stop").requires(s->s.hasPermission(2)).executes(c->stopAll(c.getSource().getServer())))
            .then(Commands.literal("reload").requires(s->s.hasPermission(2)).executes(c->{
                try{var replacement=FormCatalog.load();for(var r:RaidData.get(c.getSource().getServer()).raids.values())for(var a:r.attackers.values())replacement.get(a.form);catalog=replacement;c.getSource().sendSuccess(()->Component.translatable("message.masked_invasion.reloaded",catalog.all().size()),true);return 1;}
                catch(Exception failure){c.getSource().sendFailure(Component.translatable("message.masked_invasion.reload_failed",failure.getMessage()));return 0;}
            })));
    }
    private static int commandStart(net.minecraft.commands.CommandSourceStack source,ServerPlayer p,int wave){
        try{start(source.getServer(),p,wave);return 1;}catch(IllegalArgumentException failure){source.sendFailure(Component.translatable(failure.getMessage()));return 0;}
    }
    private Invasions(){}
}
