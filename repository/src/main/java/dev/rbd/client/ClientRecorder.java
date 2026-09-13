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
    private static final java.util.concurrent.atomic.AtomicReference<dev.rbd.io.SpoolingImageSender> upload=new java.util.concurrent.atomic.AtomicReference<>();
    private static final ThreadLocal<Boolean> deferredAlpha=ThreadLocal.withInitial(()->false);
    /** Only this thread's memory capture defers CPU image processing to its ordered worker. */
    public static boolean defersOpaqueAlpha(){return deferredAlpha.get();}
    private static NativeImage takeImage(com.mojang.blaze3d.pipeline.RenderTarget target){
        boolean previous=deferredAlpha.get();deferredAlpha.set(true);
        try{return Screenshot.takeScreenshot(target);}
        finally{if(previous)deferredAlpha.set(true);else deferredAlpha.remove();}
    }
    /** Owns only CPU pixels: never invokes OpenGL or reads the current world's mutable settings. */
    private static byte[] encodeImage(NativeImage image,int width,int height) throws java.io.IOException {
        image.flipY();
        int[] pixels;
        if(width==image.getWidth())pixels=image.getPixelsRGBA();
        else try(NativeImage scaled=new NativeImage(width,height,false)){
            image.resizeSubRectTo(0,0,image.getWidth(),image.getHeight(),scaled);pixels=scaled.getPixelsRGBA();
        }
        return dev.rbd.io.LosslessPng.encodeOpaque(width,height,pixels);
    }
    public static void reset(){generation.incrementAndGet();lastTick=Long.MIN_VALUE;var old=upload.getAndSet(null);if(old!=null)old.close();}
    public static void acknowledge(dev.rbd.network.ImageAckPayload ack){var sender=upload.get();if(sender!=null)sender.acknowledge(ack.id(),ack.part(),ack.accepted());}
    public static long pendingUploadBytes(){var sender=upload.get();return sender==null?0:sender.pendingBytes();}
    public static void shutdown(){
        reset();if(encoders!=null){encoders.closeAsync().whenComplete((done,error)->{if(error!=null)org.slf4j.LoggerFactory.getLogger("rbd").error("Memory image encoder stopped after an error",error);});encoders=null;}
    }
    @SubscribeEvent public static void rendered(RenderFrameEvent.Post e){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||!mc.options.getCameraType().isFirstPerson()||mc.screen instanceof MemoryScreen)return;
        if(ImmersionOverlay.isSeparated()||ConnectedClientReturn.paused)return;
        if(mc.getOverlay()!=null||mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen||mc.screen instanceof net.minecraft.client.gui.screens.ProgressScreen||mc.screen instanceof net.minecraft.client.gui.screens.GenericMessageScreen)return;
        long tick=mc.level.getGameTime();if(tick==lastTick||!dev.rbd.core.RecordingCadence.due(tick,RbdConfig.captureInterval()))return;lastTick=tick;
        NativeImage captured=null;
        try{
            NativeImage image=takeImage(mc.getMainRenderTarget());captured=image;
            int configured=RbdConfig.CLIENT_WIDTH.get();int width=configured==0?image.getWidth():configured;
            int height=Math.max(1,(int)((long)width*image.getHeight()/image.getWidth()));
            int budget=RbdConfig.IMAGE_QUEUE_MIB.get();
            if(encoders==null){queueMiB=budget;encoders=new dev.rbd.io.OrderedIo("RBD client images",budget*1048576L);}
            else if(queueMiB!=budget){queueMiB=budget;encoders.budget(budget*1048576L);}
            long epoch=generation.get();var connection=mc.getConnection();
            var staging=mc.gameDirectory.toPath().resolve(".rbd-upload");
            // Account for the full native screenshot retained by the queue, including scaled captures.
            encoders.submit(512L+(long)image.getWidth()*image.getHeight()*4L,()->{
                if(epoch!=generation.get()||connection==null)return;
                byte[] png=encodeImage(image,width,height);
                if(epoch!=generation.get())return;
                var sender=upload.get();
                if(sender==null){
                    var created=new dev.rbd.io.SpoolingImageSender(staging,chunk->{
                        if(epoch!=generation.get()||!connection.getConnection().isConnected())throw new java.io.IOException("Memory upload connection ended");
                        var msg=RbdNetwork.message("image_chunk");msg.addProperty("id",chunk.id());msg.addProperty("part",chunk.part());msg.addProperty("count",chunk.count());
                        if(chunk.part()==0){msg.addProperty("encoding","png");msg.addProperty("width",chunk.width());msg.addProperty("height",chunk.height());}
                        msg.addProperty("data",chunk.data());
                        connection.getConnection().send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(new dev.rbd.network.MessagePayload(msg.toString())));
                    });
                    if(epoch!=generation.get()){created.close();return;}
                    if(upload.compareAndSet(null,created)){
                        if(epoch!=generation.get()){upload.compareAndSet(created,null);created.close();return;}
                        sender=created;
                    }else{created.close();sender=upload.get();}
                }
                if(epoch==generation.get()&&sender!=null)sender.enqueue(width,height,png);
            }).whenComplete((done,error)->image.close());
            captured=null; // The completion also releases images for cancelled generations or failed queues.
        }catch(Exception ex){mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("RBD memory image: "+ex.getMessage()),true);}
        finally{if(captured!=null)captured.close();}
    }
}
