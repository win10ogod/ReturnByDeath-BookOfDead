package dev.rbd.client;
import dev.rbd.io.SnapshotStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.io.IOException;
public final class ReturnLifecycle {
    private static volatile String readyWorld,error;
    private static boolean waiting;
    public static void begin(com.google.gson.JsonObject message){
        if(message.has("connected")&&message.get("connected").getAsBoolean()){
            ConnectedClientReturn.begin();
            if(message.has("ending"))ImmersionOverlay.departing(dev.rbd.memory.MemoryArchive.GSON.fromJson(message.get("ending"),dev.rbd.memory.SomaticState.class),true);
            return;
        }
        waiting=Minecraft.getInstance().hasSingleplayerServer();
        if(message.get("operation").getAsString().equals("RESTORE")&&message.has("ending"))ImmersionOverlay.departing(dev.rbd.memory.MemoryArchive.GSON.fromJson(message.get("ending"),dev.rbd.memory.SomaticState.class),waiting);
    }
    public static void finished(String world,String failure){error=failure;readyWorld=world;}
    public static void tick(){
        Minecraft mc=Minecraft.getInstance();String world=readyWorld;
        if(world==null)return;readyWorld=null;waiting=false;
        mc.disconnect(new GenericMessageScreen(Component.translatable("message.rbd.returning")));
        if(error!=null){showError(error);return;}
        mc.createWorldOpenFlows().openWorld(world,()->mc.setScreen(new TitleScreen()));
    }
    public static boolean beforeOpen(String world){
        Minecraft mc=Minecraft.getInstance();var path=mc.getLevelSource().getLevelPath(world);var control=SnapshotStore.controlFor(path);
        if(!java.nio.file.Files.isDirectory(control))return true;
        try{
            if(java.nio.file.Files.exists(control.resolve("fault.json")))throw new IOException("RBD stopped after an archive error. Inspect "+control.resolve("fault.json"));
            var store=new SnapshotStore(path,control);store.complete();return true;
        }catch(IOException e){showError(e.getMessage());return false;}
    }
    private static void showError(String message){ImmersionOverlay.cancel();Minecraft.getInstance().setScreen(new AlertScreen(()->Minecraft.getInstance().setScreen(new TitleScreen()),Component.translatable("screen.rbd.recovery"),Component.literal(message)));}
    private ReturnLifecycle(){}
}
