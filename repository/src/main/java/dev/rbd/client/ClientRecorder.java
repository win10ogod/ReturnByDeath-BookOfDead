package dev.rbd.client;
import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import dev.rbd.RbdConfig;
import dev.rbd.network.RbdNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.util.*;
@EventBusSubscriber(modid="rbd",value=Dist.CLIENT)
public final class ClientRecorder {
    private static long lastTick=Long.MIN_VALUE;
    private static final java.util.concurrent.atomic.AtomicLong generation=new java.util.concurrent.atomic.AtomicLong();
    private static dev.rbd.io.OrderedIo encoders;
    private static int queueMiB;
    public static void reset(){generation.incrementAndGet();lastTick=Long.MIN_VALUE;}
    public static void shutdown(){
        reset();if(encoders!=null){try{encoders.close();}catch(java.io.IOException error){org.slf4j.LoggerFactory.getLogger("rbd").error("Memory image encoder stopped after an error",error);}finally{encoders=null;}}
    }
    @SubscribeEvent public static void rendered(RenderFrameEvent.Post e){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||!mc.options.getCameraType().isFirstPerson()||mc.screen instanceof MemoryScreen)return;
        if(ImmersionOverlay.isSeparated()||ConnectedClientReturn.paused)return;
        if(mc.getOverlay()!=null||mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen||mc.screen instanceof net.minecraft.client.gui.screens.ProgressScreen||mc.screen instanceof net.minecraft.client.gui.screens.GenericMessageScreen)return;
        long tick=mc.level.getGameTime();if(tick==lastTick||Math.floorMod(tick,RbdConfig.VISUAL_INTERVAL.get())!=0)return;lastTick=tick;
        try(NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            int configured=RbdConfig.CLIENT_WIDTH.get();int width=configured==0?image.getWidth():configured;
            int height=Math.max(1,(int)((long)width*image.getHeight()/image.getWidth()));int[] pixels;
            if(width==image.getWidth())pixels=image.getPixelsRGBA();
            else try(NativeImage scaled=new NativeImage(width,height,false)){image.resizeSubRectTo(0,0,image.getWidth(),image.getHeight(),scaled);pixels=scaled.getPixelsRGBA();}
            int budget=RbdConfig.IMAGE_QUEUE_MIB.get();
            if(encoders==null||queueMiB!=budget){if(encoders!=null)encoders.close();queueMiB=budget;encoders=new dev.rbd.io.OrderedIo("RBD client images",budget*1048576L);}
            long epoch=generation.get();var connection=mc.getConnection();
            encoders.submit(512L+pixels.length*4L,()->{
                if(epoch!=generation.get())return;
                byte[] png=dev.rbd.io.LosslessPng.encode(width,height,pixels);
                String data=Base64.getEncoder().encodeToString(png);String id=UUID.randomUUID().toString();int count=(data.length()+23999)/24000;
                var packets=new ArrayList<dev.rbd.network.MessagePayload>(count);
                for(int part=0;part<count;part++){
                    var msg=RbdNetwork.message("image_chunk");msg.addProperty("id",id);msg.addProperty("part",part);msg.addProperty("count",count);
                    if(part==0){msg.addProperty("encoding","png");msg.addProperty("width",width);msg.addProperty("height",height);}
                    msg.addProperty("data",data.substring(part*24000,Math.min(data.length(),(part+1)*24000)));
                    packets.add(new dev.rbd.network.MessagePayload(msg.toString()));
                }
                mc.execute(()->{
                    if(connection==null||mc.getConnection()!=connection||epoch!=generation.get()||ConnectedClientReturn.paused)return;
                    for(var packet:packets)connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(packet));
                });
            });
        }catch(Exception ex){mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("RBD memory image: "+ex.getMessage()),true);}
    }
}
