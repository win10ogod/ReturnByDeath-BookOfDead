package dev.rbd.client;
import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import dev.rbd.RbdConfig;
import dev.rbd.memory.MemoryArchive;
import dev.rbd.network.RbdNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;
@EventBusSubscriber(modid="rbd",value=Dist.CLIENT)
public final class ClientRecorder {
    private static long lastTick=Long.MIN_VALUE;
    @SubscribeEvent public static void rendered(RenderFrameEvent.Post e){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||!mc.options.getCameraType().isFirstPerson()||mc.screen instanceof MemoryScreen)return;
        if(ImmersionOverlay.isSeparated()||ConnectedClientReturn.paused)return;
        if(mc.getOverlay()!=null||mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen||mc.screen instanceof net.minecraft.client.gui.screens.ProgressScreen||mc.screen instanceof net.minecraft.client.gui.screens.GenericMessageScreen)return;
        long tick=mc.level.getGameTime();if(tick==lastTick||Math.floorMod(tick,RbdConfig.VISUAL_INTERVAL.get())!=0)return;lastTick=tick;
        try(NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            int configured=RbdConfig.CLIENT_WIDTH.get();int width=configured==0?image.getWidth():configured;
            int height=Math.max(1,(int)((long)width*image.getHeight()/image.getWidth()));byte[] png;
            if(width==image.getWidth())png=image.asByteArray();
            else try(NativeImage scaled=new NativeImage(width,height,false)){image.resizeSubRectTo(0,0,image.getWidth(),image.getHeight(),scaled);png=scaled.asByteArray();}
            JsonObject view=new JsonObject();view.addProperty("width",width);view.addProperty("height",height);view.addProperty("png",Base64.getEncoder().encodeToString(png));
            ByteArrayOutputStream output=new ByteArrayOutputStream();try(GZIPOutputStream gzip=new GZIPOutputStream(output)){gzip.write(view.toString().getBytes(StandardCharsets.UTF_8));}
            String data=Base64.getEncoder().encodeToString(output.toByteArray());String id=UUID.randomUUID().toString();int count=(data.length()+23999)/24000;
            for(int part=0;part<count;part++){var msg=RbdNetwork.message("image_chunk");msg.addProperty("id",id);msg.addProperty("part",part);msg.addProperty("count",count);msg.addProperty("data",data.substring(part*24000,Math.min(data.length(),(part+1)*24000)));RbdClient.send(msg);}
        }catch(Exception ex){mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("RBD memory image: "+ex.getMessage()),true);}
    }
}
