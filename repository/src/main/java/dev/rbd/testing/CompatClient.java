package dev.rbd.testing;

import com.google.gson.JsonObject;
import dev.rbd.client.MemoryScreen;
import dev.rbd.io.AtomicJson;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.*;

@EventBusSubscriber(modid="rbd",value=Dist.CLIENT)
public final class CompatClient {
    private static String session="",readySession="",readSession="";
    private static int joins,done,renderTicks;
    private static long nextConnect;
    private static boolean imageShot;
    private static boolean enabled(){return CompatScenario.allowed()&&System.getProperty("rbd.testRole","").matches("Rbd[ABC]");}
    public static void receive(JsonObject message){if(enabled()){session=message.get("session").getAsString();joins++;renderTicks=0;}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled())return;var mc=Minecraft.getInstance();String role=System.getProperty("rbd.testRole");
        try {
            mc.options.pauseOnLostFocus=false;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            if(Files.exists(CompatScenario.root().resolve("report.json"))){
                if(done++>30){var report=new JsonObject();report.addProperty("joins",joins);report.addProperty("role",role);report.addProperty("session",session);AtomicJson.write(CompatScenario.root().resolve(role+"-done.json"),report);if(mc.level!=null)mc.level.disconnect();mc.disconnect();mc.stop();}return;
            }
            if(mc.screen instanceof AccessibilityOnboardingScreen){mc.screen.onClose();return;}
            if(mc.screen instanceof DeathScreen&&mc.player!=null){mc.player.respawn();mc.setScreen(null);return;}
            if((mc.screen instanceof TitleScreen||mc.screen instanceof DisconnectedScreen)&&mc.getOverlay()==null&&System.nanoTime()>nextConnect){
                nextConnect=System.nanoTime()+2_000_000_000L;
                String address=System.getProperty("rbd.testAddress");
                if(address==null||!address.startsWith("127.0.0.1:"))throw new IllegalStateException("Fixture connects only to loopback");
                ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("RBD compatibility fixture",address,ServerData.Type.OTHER),false,null);
            }
        }catch(Exception e){throw new RuntimeException("Compatibility client fixture",e);}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){
        if(!enabled())return;var mc=Minecraft.getInstance();String role=System.getProperty("rbd.testRole");
        try {
            if(mc.level!=null&&mc.player!=null&&mc.screen==null&&mc.getOverlay()==null&&!session.isEmpty()&&!readySession.equals(session)&&renderTicks++>20){
                var report=new JsonObject();report.addProperty("session",session);report.addProperty("role",role);report.addProperty("dimension",mc.level.dimension().location().toString());report.addProperty("joins",joins);
                report.addProperty("helmet",BuiltInRegistries.ITEM.getKey(mc.player.getItemBySlot(EquipmentSlot.HEAD).getItem()).toString());
                AtomicJson.write(CompatScenario.root().resolve(role+"-ready.json"),report);readySession=session;
                Screenshot.grab(mc.gameDirectory,"compat-"+role+"-join-"+joins+".png",mc.getMainRenderTarget(),m->{});
            }
            if(mc.screen instanceof MemoryScreen memory){
                if(memory.hasClientImage()&&!imageShot){imageShot=true;Screenshot.grab(mc.gameDirectory,"compat-"+role+"-memory.png",mc.getMainRenderTarget(),m->{});}
                if(memory.experiencingDeath()&&memory.deathIsSilent()&&!readSession.equals(session)){
                    Screenshot.grab(mc.gameDirectory,"compat-"+role+"-ending.png",mc.getMainRenderTarget(),m->{});readSession=session;
                    var report=new JsonObject();report.addProperty("session",session);report.addProperty("nativeImagePresented",imageShot);report.addProperty("terminalSilence",true);
                    AtomicJson.write(CompatScenario.root().resolve(role+"-read.json"),report);
                }
            }
        }catch(Exception e){throw new RuntimeException(e);}
    }
}
