package dev.rbd.network;
import com.google.gson.*;
import dev.rbd.runtime.GameSession;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
public final class RbdNetwork {
    private RbdNetwork(){}
    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("7").executesOn(net.neoforged.neoforge.network.registration.HandlerThread.NETWORK)
            .playToClient(ImageAckPayload.TYPE,ImageAckPayload.CODEC,(payload,context)->ClientReceiver.ack(payload));
        event.registrar("7").executesOn(net.neoforged.neoforge.network.registration.HandlerThread.NETWORK).playBidirectional(MessagePayload.TYPE,MessagePayload.CODEC,(payload,context)->{
            // Login/respawn packets can precede construction of the client player on its main thread.
            if(context.flow()==net.minecraft.network.protocol.PacketFlow.CLIENTBOUND){context.enqueueWork(()->ClientReceiver.receive(payload.json()));return;}
            if(context.player() instanceof ServerPlayer player){
                try{
                    var message=JsonParser.parseString(payload.json()).getAsJsonObject();
                    if("image_chunk".equals(message.get("kind").getAsString())){
                        String id=message.get("id").getAsString();int part=message.get("part").getAsInt();
                        var game=GameSession.current;
                        if(game==null){PacketDistributor.sendToPlayer(player,new ImageAckPayload(id,part,false));return;}
                        game.enqueueImage(player,message,error->{
                            PacketDistributor.sendToPlayer(player,new ImageAckPayload(id,part,error==null));
                            if(error!=null)context.enqueueWork(()->player.displayClientMessage(net.minecraft.network.chat.Component.literal("RBD: "+error.getMessage()),true));
                        });
                    }else context.enqueueWork(()->{
                        // A connected return may replace the player while this control packet is queued.
                        try{if(GameSession.current!=null&&context.player() instanceof ServerPlayer currentPlayer)GameSession.current.message(currentPlayer,message);}
                        catch(Exception error){player.displayClientMessage(net.minecraft.network.chat.Component.literal("RBD: "+error.getMessage()),true);}
                    });
                }catch(Exception error){context.enqueueWork(()->player.displayClientMessage(net.minecraft.network.chat.Component.literal("RBD: "+error.getMessage()),true));}
            }
        });
    }
    /** Class resolution is deferred until an actual clientbound payload is handled. */
    private static final class ClientReceiver {
        static void receive(String json){dev.rbd.client.RbdClient.receive(JsonParser.parseString(json).getAsJsonObject());}
        static void ack(ImageAckPayload payload){dev.rbd.client.ClientRecorder.acknowledge(payload);}
    }
    public static JsonObject message(String kind){var o=new JsonObject();o.addProperty("kind",kind);return o;}
    public static void send(ServerPlayer p,JsonObject message){PacketDistributor.sendToPlayer(p,new MessagePayload(message.toString()));}
    public static void sendLarge(ServerPlayer p,JsonObject message){
        try{
            ByteArrayOutputStream output=new ByteArrayOutputStream();try(GZIPOutputStream zip=new GZIPOutputStream(output)){zip.write(message.toString().getBytes(StandardCharsets.UTF_8));}
            String base64=Base64.getEncoder().encodeToString(output.toByteArray());String id=UUID.randomUUID().toString();
            int count=(base64.length()+23999)/24000;
            for(int part=0;part<count;part++){var chunk=message("chunk");chunk.addProperty("id",id);chunk.addProperty("part",part);chunk.addProperty("count",count);chunk.addProperty("data",base64.substring(part*24000,Math.min(base64.length(),(part+1)*24000)));send(p,chunk);}
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
}
