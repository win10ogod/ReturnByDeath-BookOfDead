package dev.rbd.testing;
import dev.rbd.client.MemoryScreen;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
@EventBusSubscriber(modid="rbd",value=Dist.CLIENT)
public final class LiveClient {
    private static boolean started,shot,attemptedMove,libraryShot,endingShot,silenceShot,reducedShot,returnShot,earlyAck,toggledReduced,restoredEffects;
    private static int doneTicks,memoryFrames,libraryFrames;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!LiveScenario.ENABLED)return;Minecraft mc=Minecraft.getInstance();
        mc.options.pauseOnLostFocus=false;
        if(started&&mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen screen){
            // Only our explicitly created disposable fixture can reach this branch.
            screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button)
                .map(w->(net.minecraft.client.gui.components.Button)w).filter(b->b.getMessage().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinSkipButton")))
                .findFirst().ifPresent(net.minecraft.client.gui.components.Button::onPress);
        }
        if(!started&&mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.screen.onClose();return;}
        if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
            dev.rbd.ImmersionConfig.REDUCED.set(false);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            started=true;String name="RbdLiveTest-"+System.currentTimeMillis();
            mc.createWorldOpenFlows().createFreshLevel(name,new LevelSettings(name,GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(8675309L,true,false),WorldPresets::createNormalWorldDimensions,new TitleScreen());
        }
        if(mc.screen instanceof MemoryScreen&&!attemptedMove&&mc.player!=null){
            attemptedMove=true;mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(mc.player.getX()+16,mc.player.getY(),mc.player.getZ(),true));
        }
        if(mc.screen instanceof MemoryScreen memory&&memory.experiencingDeath()){
            if(!earlyAck){
                earlyAck=true;var ack=dev.rbd.network.RbdNetwork.message("experienced");ack.addProperty("session",memory.session);ack.addProperty("sequence",memory.sequence());dev.rbd.client.RbdClient.send(ack);
            }
            if(memory.deathElapsed()>1&&!toggledReduced){toggledReduced=true;memory.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F8,0,0);}
            if(memory.deathElapsed()>2&&toggledReduced&&!restoredEffects){restoredEffects=true;memory.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F8,0,0);}
        }
        if((LiveScenario.completed||LiveScenario.failure!=null)&&doneTicks++>40){if(mc.level!=null)mc.level.disconnect();mc.disconnect();mc.stop();}
    }
    @SubscribeEvent public static void rendered(RenderFrameEvent.Post e){
        if(!LiveScenario.ENABLED)return;var mc=Minecraft.getInstance();
        if(dev.rbd.client.ImmersionOverlay.isBlack()&&!returnShot){Screenshot.grab(mc.gameDirectory,"rbd-return-silence.png",mc.getMainRenderTarget(),message->{});returnShot=true;LiveScenario.returnBlackPresented=true;}
        if(mc.screen instanceof MemoryScreen memory&&memory.experiencingDeath()){
            if(memory.deathElapsed()>0.4&&!endingShot){Screenshot.grab(mc.gameDirectory,"rbd-memory-ending.png",mc.getMainRenderTarget(),message->{});endingShot=true;LiveScenario.endingPresented=true;}
            if(memory.deathElapsed()>1.3&&dev.rbd.ImmersionConfig.REDUCED.get()&&!reducedShot){Screenshot.grab(mc.gameDirectory,"rbd-memory-reduced.png",mc.getMainRenderTarget(),message->{});reducedShot=true;LiveScenario.reducedPresented=true;}
            if(memory.deathIsSilent()&&!silenceShot){Screenshot.grab(mc.gameDirectory,"rbd-memory-silence.png",mc.getMainRenderTarget(),message->{});silenceShot=true;LiveScenario.silencePresented=true;}
        }
        if(mc.screen instanceof dev.rbd.client.LibraryScreen&&!libraryShot&&libraryFrames++>12){Screenshot.grab(mc.gameDirectory,"rbd-library-live.png",mc.getMainRenderTarget(),message->{});libraryShot=true;LiveScenario.libraryPresented=true;}
        if(shot)return;
        if(mc.screen instanceof MemoryScreen memory&&memory.hasClientImage()&&memoryFrames++>12){Screenshot.grab(mc.gameDirectory,"rbd-memory-live.png",mc.getMainRenderTarget(),message->{});shot=true;LiveScenario.imagePresented=true;}
    }
}
