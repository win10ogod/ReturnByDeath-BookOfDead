package dev.rbd.client;
import com.google.gson.*;
import dev.rbd.memory.*;
import dev.rbd.network.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
@EventBusSubscriber(modid="rbd",value=Dist.CLIENT)
public final class RbdClient {
    private static final Map<String,StringBuilder> chunks=new HashMap<>();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){ReturnLifecycle.tick();ImmersionOverlay.tick();}
    @SubscribeEvent public static void presented(net.neoforged.neoforge.client.event.RenderFrameEvent.Post e){if(Minecraft.getInstance().screen instanceof MemoryScreen memory)memory.presented();}
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){ClientRecorder.shutdown();chunks.clear();ConnectedClientReturn.clear();CheckpointNotice.clear();dev.rbd.rules.WorldRules.clearClient();}
    public static void send(JsonObject message){PacketDistributor.sendToServer(new MessagePayload(message.toString()));}
    public static void receive(JsonObject msg){
        Minecraft mc=Minecraft.getInstance();
        switch(msg.get("kind").getAsString()){
            case "chunk" -> {
                String id=msg.get("id").getAsString();int part=msg.get("part").getAsInt();
                if(part==0)chunks.put(id,new StringBuilder());
                var buffer=chunks.get(id);if(buffer==null)return;buffer.append(msg.get("data").getAsString());
                if(part+1==msg.get("count").getAsInt()){
                    chunks.remove(id);
                    try(var stream=new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(buffer.toString())))){
                        receive(JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject());
                    }catch(IOException e){throw new UncheckedIOException(e);}
                }
            }
            case "compat_test" -> dev.rbd.testing.CompatClient.receive(msg);
            case "checkpoint_saving" -> CheckpointNotice.saving();
            case "transition" -> {ReturnLifecycle.begin(msg);if(mc.screen instanceof MemoryScreen)mc.setScreen(null);}
            case "world_rules" -> dev.rbd.rules.WorldRules.receive(msg.getAsJsonObject("values"));
            case "world_reset" -> ConnectedClientReturn.reset();
            case "transition_complete" -> {ConnectedClientReturn.complete();CheckpointNotice.complete();}
            case "transition_fault" -> {CheckpointNotice.failed();ImmersionOverlay.cancel();mc.setScreen(new net.minecraft.client.gui.screens.GenericMessageScreen(net.minecraft.network.chat.Component.translatable("message.rbd.return_paused")));}
            case "return_imprint" -> ImmersionOverlay.imprint(msg.getAsJsonObject("imprint"));
            case "shelf" -> mc.setScreen(new LibraryScreen(msg));
            case "reading" -> {ClientRecorder.reset();mc.setScreen(new MemoryScreen(msg.get("session").getAsString(),msg.get("title").getAsString()));}
            case "frame" -> {if(mc.screen instanceof MemoryScreen screen&&screen.session.equals(msg.get("session").getAsString()))try{screen.frame(MemoryArchive.GSON.fromJson(msg.get("frame"),MemoryFrame.class),msg.get("sequence").getAsLong(),msg.has("dwell")?msg.get("dwell").getAsDouble():0);}catch(IOException failure){send(RbdNetwork.message("close"));mc.setScreen(null);if(mc.player!=null)mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("RBD: memory image could not be decoded"),true);}}
            case "end" -> {if(mc.screen instanceof MemoryScreen screen&&screen.session.equals(msg.get("session").getAsString())){if(msg.has("completed")&&msg.get("completed").getAsBoolean())screen.completed();else mc.setScreen(null);}}
        }
    }
    private RbdClient(){}
}
