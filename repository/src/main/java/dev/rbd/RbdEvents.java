package dev.rbd;
import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.rbd.core.DisclosurePolicy;
import dev.rbd.runtime.*;
import dev.rbd.memory.Perception;
import dev.rbd.network.RbdNetwork;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.sounds.*;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.*;
import java.io.*;
import java.util.*;

public final class RbdEvents {
    private static final Logger LOG=LoggerFactory.getLogger("rbd");
    private static boolean normalStopping;
    private static final TagKey<EntityType<?>> SENSITIVE=TagKey.create(Registries.ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath("rbd","miasma_sensitive"));
    @SubscribeEvent public void started(ServerStartedEvent e){
        normalStopping=false;ArchiveLibrary.clear();
        try{GameSession.current=new GameSession(e.getServer());ArchiveLibrary.generate(GameSession.current);}
        catch(Exception ex){LOG.error("RBD world initialization failed",ex);GameSession.current=null;e.getServer().halt(false);}
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p)||GameSession.current==null)return;
        var game=GameSession.current;
        try{
            if(!game.isHolder(p.getUUID())&&game.authorities.hasRoom(RbdConfig.MAX_HOLDERS.get())&&
                (RbdConfig.AUTO_BIND_JOIN.get()||(game.holders().isEmpty()&&!game.server.isDedicatedServer()&&RbdConfig.AUTO_BIND.get())))game.bind(p);
            game.login(p);
            if(!game.branch.object("greeted").has(p.getUUID().toString())){
                game.branch.object("greeted").addProperty(p.getUUID().toString(),true);
                var library=game.branch.json.getAsJsonObject("library");
                p.sendSystemMessage(Component.translatable("message.rbd.welcome",library.get("x").getAsInt(),library.get("z").getAsInt()));
            }
        }catch(Exception ex){fault(game,ex);}
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){
        if(e.getEntity() instanceof ServerPlayer p&&GameSession.current!=null)try{GameSession.current.disconnected(p);}catch(Exception ex){fault(GameSession.current,ex);}
    }
    @SubscribeEvent public void stopping(ServerStoppingEvent e){
        var game=GameSession.current;if(game==null)return;
        try{game.close();normalStopping=true;}catch(Exception ex){normalStopping=false;fault(game,ex);}
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent e){
        var game=GameSession.current;if(game==null)return;
        try{game.saveGuard.check();}catch(IOException ex){normalStopping=false;fault(game,ex);}
        finally{game.saveGuard.close();}
        if(game.transitioning&&normalStopping){
            if(game.supervisor!=null){try{game.supervisor.stoppedNormally();}catch(IOException ex){LOG.error("Cannot seal supervisor stop",ex);}}
            else {
                // The server-stopped event precedes final thread exit. Join before opening any world file.
                Thread worker=new Thread(()->{
                    try{e.getServer().getRunningThread().join();game.snapshots.markClosed();game.snapshots.complete();dev.rbd.client.ReturnLifecycle.finished(game.snapshots.world.getFileName().toString(),null);}
                    catch(Exception ex){LOG.error("Offline return failed",ex);dev.rbd.client.ReturnLifecycle.finished(game.snapshots.world.getFileName().toString(),ex.getMessage());}
                },"RBD offline return");worker.setDaemon(false);worker.start();
            }
        }
        GameSession.current=null;
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post e){
        var game=GameSession.current;if(game==null)return;
        try{
            game.tick();
            if(!game.transitioning&&!game.returnPending()&&e.getServer().getTickCount()%20==0){
                double range=RbdConfig.MIASMA_RANGE.get();
                for(ServerLevel level:game.server.getAllLevels())for(var entity:level.getAllEntities()){
                    if(!(entity instanceof Mob mob)||!mob.getType().is(SENSITIVE))continue;
                    ServerPlayer nearest=null;double distance=range*range;
                    for(UUID id:game.holders()){
                        ServerPlayer p=game.server.getPlayerList().getPlayer(id);var soul=game.soul(id);
                        if(p==null||p.serverLevel()!=level||!p.isAlive()||!soul.has("miasma")||soul.get("miasma").getAsDouble()<=0)continue;
                        double d=mob.distanceToSqr(p);if(d<=distance){nearest=p;distance=d;}
                    }
                    if(nearest!=null)mob.setTarget(nearest);
                }
            }
        }catch(Exception ex){fault(game,ex);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void died(LivingDeathEvent e){
        if(!(e.getEntity().level() instanceof ServerLevel))return;
        var game=GameSession.current;if(game==null||!Perception.recorded(e.getEntity()))return;
        if(game.transitioning){if(game.isHolder(e.getEntity().getUUID())){e.setCanceled(true);e.getEntity().setHealth(1);}return;}
        try{
            game.death(e.getEntity(),e.getSource());
            if(game.isHolder(e.getEntity().getUUID())){e.setCanceled(true);e.getEntity().setHealth(1);}
        }catch(Exception ex){fault(game,ex);}
    }
    @SubscribeEvent public void advancement(AdvancementEvent.AdvancementEarnEvent e){
        var game=GameSession.current;if(game!=null&&game.isHolder(e.getEntity().getUUID())){
            String id=e.getAdvancement().id().toString();if(RbdConfig.MILESTONES.get().contains(id))game.milestone(id,e.getEntity().getUUID());
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void sound(PlayLevelSoundEvent.AtPosition e){
        var game=GameSession.current;if(game!=null&&e.getLevel() instanceof ServerLevel level&&e.getSound()!=null)
            game.recorder.sound(level,e.getPosition(),e.getSound().value().getLocation().toString(),e.getNewVolume(),e.getNewPitch());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void soundEntity(PlayLevelSoundEvent.AtEntity e){
        var game=GameSession.current;if(game!=null&&e.getLevel() instanceof ServerLevel level&&e.getSound()!=null)
            game.recorder.sound(level,e.getEntity().position(),e.getSound().value().getLocation().toString(),e.getNewVolume(),e.getNewPitch());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void chat(ServerChatEvent e){
        var game=GameSession.current;if(game==null||game.transitioning)return;
        try{for(ServerPlayer p:game.server.getPlayerList().getPlayers())game.recorder.caption(p,e.getUsername()+": "+e.getMessage().getString());}
        catch(Exception ex){fault(game,ex);}
    }
    @SubscribeEvent public void hurt(LivingDamageEvent.Post e){
        if(!(e.getEntity().level() instanceof ServerLevel))return;
        var game=GameSession.current;if(game==null||game.transitioning||!Perception.recorded(e.getEntity()))return;
        try{game.recorder.hurt(e.getEntity(),e.getSource().getMsgId(),e.getNewDamage());}catch(Exception ex){fault(game,ex);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void breakBlock(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent e){
        if(!(e.getPlayer() instanceof ServerPlayer))return;
        var game=GameSession.current;if(game==null||game.transitioning)return;
        try{game.recorder.caption(e.getPlayer(),"挖掘 / Break: "+e.getState().getBlock().getName().getString());}catch(Exception ex){fault(game,ex);}
    }
    @SubscribeEvent public void interact(PlayerInteractEvent.EntityInteract e){
        var game=GameSession.current;
        if(game==null||!(e.getEntity() instanceof ServerPlayer p)||!(e.getTarget() instanceof LivingEntity target))return;
        if(game.reading(p.getUUID())){e.setCanceled(true);return;}
        if(!p.hasLineOfSight(target))return;
        try{
            if(target instanceof net.minecraft.world.entity.npc.Villager&&!target.hasCustomName()){
                target.setCustomName(Component.literal("旅人 · "+target.getUUID().toString().substring(0,4)));
                game.recorder.caption(p,target.getName().getString()+" 向你介紹了自己。");
                game.recorder.caption(target,p.getGameProfile().getName()+" 前來交談。");
            }
            if(target.hasCustomName()||target instanceof ServerPlayer){
                game.learn(p.getUUID(),new dev.rbd.memory.MemoryFrame.Contact(target.getUUID(),Perception.name(target),target.getType().toString()));
                game.learn(target.getUUID(),new dev.rbd.memory.MemoryFrame.Contact(p.getUUID(),p.getGameProfile().getName(),p.getType().toString()));
            }
        }catch(Exception ex){fault(game,ex);}
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("rbd")
            .then(Commands.literal("bind").requires(s->s.hasPermission(4)&&s.getEntity()==null).then(Commands.argument("player",EntityArgument.player()).executes(c->{
                try{required().bind(EntityArgument.getPlayer(c,"player"));return 1;}catch(Exception e){c.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}
            })))
            .then(Commands.literal("unbind").requires(s->s.hasPermission(4)&&s.getEntity()==null).then(Commands.argument("player",EntityArgument.player()).executes(c->{
                try{required().unbind(EntityArgument.getPlayer(c,"player").getUUID());return 1;}catch(Exception e){c.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}
            })))
            .then(Commands.literal("unbind_uuid").requires(s->s.hasPermission(4)&&s.getEntity()==null).then(Commands.argument("uuid",net.minecraft.commands.arguments.UuidArgument.uuid()).executes(c->{
                try{required().unbind(net.minecraft.commands.arguments.UuidArgument.getUuid(c,"uuid"));return 1;}catch(Exception e){c.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}
            })))
            .then(Commands.literal("checkpoint").requires(s->s.hasPermission(4)&&s.getEntity()==null).executes(c->{
                try{required().milestone("authored:"+UUID.randomUUID());return 1;}catch(Exception e){c.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}
            }))
            .then(Commands.literal("confess").executes(c->{try{return confess(c.getSource().getPlayerOrException());}catch(Exception e){c.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}}))
            .then(Commands.literal("scene").requires(s->s.hasPermission(4)&&s.getEntity()==null).then(Commands.argument("scene",StringArgumentType.word()).suggests((c,b)->{for(String v:List.of("ordinary","tea_party","connection_lost","connection_restored","memory_lost","name_lost","recover_self","restore_identity"))b.suggest(v);return b.buildFuture();}).executes(c->{
                try{scene(required(),required().onlyHolder(),StringArgumentType.getString(c,"scene"));return 1;}catch(Exception e){c.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}
            }).then(Commands.argument("player",EntityArgument.player()).executes(c->{
                try{scene(required(),EntityArgument.getPlayer(c,"player").getUUID(),StringArgumentType.getString(c,"scene"));return 1;}catch(Exception e){c.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}
            }))))
            .then(Commands.literal("milestone").requires(s->s.hasPermission(4)&&s.getEntity()==null).then(Commands.argument("id",StringArgumentType.word()).executes(c->{try{required().milestone(StringArgumentType.getString(c,"id"));return 1;}catch(Exception e){c.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}})))
            .then(Commands.literal("intervention").requires(s->s.hasPermission(4)&&s.getEntity()==null).then(Commands.argument("target",EntityArgument.entity()).executes(c->{
                try{var game=required();game.soul(game.onlyHolder()).addProperty("interventionTarget",EntityArgument.getEntity(c,"target").getUUID().toString());game.saveSoul();return 1;}catch(Exception ex){c.getSource().sendFailure(Component.literal(ex.getMessage()));return 0;}
            }).then(Commands.argument("holder",EntityArgument.player()).executes(c->{
                try{var game=required();UUID id=EntityArgument.getPlayer(c,"holder").getUUID();if(!game.isHolder(id))throw new IOException("Not an active holder");game.soul(id).addProperty("interventionTarget",EntityArgument.getEntity(c,"target").getUUID().toString());game.saveSoul();return 1;}catch(Exception ex){c.getSource().sendFailure(Component.literal(ex.getMessage()));return 0;}
            }))))
            .then(Commands.literal("status").requires(s->s.hasPermission(4)&&s.getEntity()==null).executes(c->{var g=GameSession.current;c.getSource().sendSuccess(()->Component.literal(g==null?"RBD inactive":"RBD holders="+g.holders()+" limit="+RbdConfig.MAX_HOLDERS.get()+" transition="+g.transitioning+" archive="+g.archive.root()),false);return 1;})));
    }
    private static GameSession required() throws IOException {if(GameSession.current==null)throw new IOException("RBD world is not ready");return GameSession.current;}
    public static int confess(ServerPlayer p) throws IOException {
        var game=required();var soul=game.isHolder(p.getUUID())?game.soul(p.getUUID()):new JsonObject();boolean exception=soul.has("disclosureContext")&&soul.get("disclosureContext").getAsString().equals("tea_party");
        var decision=new DisclosurePolicy().evaluate(game.isHolder(p.getUUID()),DisclosurePolicy.Intent.REVEAL_AUTHORITY,exception?DisclosurePolicy.Context.AUTHORED_EXCEPTION:DisclosurePolicy.Context.ORDINARY,false);
        if(decision.suppressDelivery()){
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,5,false,false,false));p.addEffect(new MobEffectInstance(MobEffects.DARKNESS,60,0,false,false,false));
            p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT,SoundSource.PLAYERS,0.8F,0.65F);
            p.displayClientMessage(Component.translatable("message.rbd.taboo"),true);
            soul.addProperty("miasma",soul.has("miasma")?soul.get("miasma").getAsDouble()+1:1);
            game.recorder.caption(p,"心臟彷彿被無形的手攥住。話語沒有傳達出去。");
            if(soul.has("interventionTarget")){
                UUID targetId=UUID.fromString(soul.get("interventionTarget").getAsString());soul.remove("interventionTarget");
                for(ServerLevel level:game.server.getAllLevels()){var target=level.getEntity(targetId);if(target instanceof LivingEntity living)living.hurt(level.damageSources().genericKill(),Float.MAX_VALUE);}
            }
            game.saveSoul();return 1;
        }
        p.displayClientMessage(Component.translatable(exception?"message.rbd.exception":"message.rbd.no_authority"),true);return 0;
    }
    private static void scene(GameSession game,UUID who,String scene) throws IOException {
        if(!game.isHolder(who))throw new IOException("Not an active holder");var soul=game.soul(who);
        switch(scene){
            case "ordinary" -> soul.addProperty("disclosureContext","ordinary");
            case "tea_party" -> soul.addProperty("disclosureContext","tea_party");
            case "connection_lost" -> soul.addProperty("connection","LOST");
            case "connection_restored" -> soul.addProperty("connection","SCRIPTED_RECONNECTION");
            case "memory_lost" -> soul.addProperty("memorySealed",true);
            case "name_lost" -> soul.addProperty("nameSealed",true);
            case "recover_self" -> soul.addProperty("recoverFromSelfBook",true);
            case "restore_identity" -> {soul.addProperty("memorySealed",false);soul.addProperty("nameSealed",false);}
            default -> throw new IOException("Unknown authored scene: "+scene);
        }game.saveSoul();
    }
    private static void fault(GameSession game,Exception ex){
        LOG.error("RBD stopped; memory/world transaction could not be completed",ex);
        try{var fault=new JsonObject();fault.addProperty("reason",ex.toString());dev.rbd.io.AtomicJson.write(game.snapshots.control.resolve("fault.json"),fault);}catch(Exception writeError){LOG.error("Could not write fault record",writeError);}
        game.transitioning=false;game.server.halt(false);
    }
}
