package dev.rbd.testing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;

@EventBusSubscriber(modid="rbd",value=Dist.CLIENT)
public final class AutoCheckpointLiveClient {
    private static boolean started,published,stopping;
    private static int doneTicks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!AutoCheckpointLive.ENABLED)return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        if(Files.exists(AutoCheckpointLive.report())){if(++doneTicks>60&&!stopping){stopping=true;if(mc.level!=null)mc.level.disconnect();mc.disconnect();mc.stop();}return;}
        if(!started&&mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.screen.onClose();return;}
        if(!Boolean.getBoolean("rbd.autoCheckpointHost"))return;
        if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
            started=true;String name="RbdAutoSaveTest-"+System.currentTimeMillis();
            mc.createWorldOpenFlows().createFreshLevel(name,new LevelSettings(name,GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(8675309L,true,false),WorldPresets::createNormalWorldDimensions,new TitleScreen());
        }
        if(!published&&mc.player!=null&&mc.getSingleplayerServer()!=null&&!dev.rbd.client.ConnectedClientReturn.paused){
            // Disposable localhost fixture accounts only; normal servers never enter this test hook.
            mc.getSingleplayerServer().setLocalIp("127.0.0.1");
            mc.getSingleplayerServer().setUsesAuthentication(false);
            published=mc.getSingleplayerServer().publishServer(GameType.SURVIVAL,false,25588);
            if(published)try{Files.writeString(AutoCheckpointLive.report().resolveSibling("lan-ready"),"25588");}catch(Exception error){throw new RuntimeException(error);}
        }
    }
}
