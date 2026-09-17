package dev.rbd.client;

import dev.rbd.network.RbdNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid="rbd",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class VillagerNameKeys {
    public static final KeyMapping NAME=new KeyMapping("key.rbd.name_villager",GLFW.GLFW_KEY_N,"key.categories.rbd");
    @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(NAME);}
    public static void tick(){
        var mc=Minecraft.getInstance();
        while(NAME.consumeClick()){
            if(mc.screen==null&&mc.player!=null&&!mc.player.isSpectator()&&!ConnectedClientReturn.paused
                    &&mc.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof Villager villager){
                var request=RbdNetwork.message("villager_name_open");request.addProperty("entity",villager.getUUID().toString());RbdClient.send(request);
            }
        }
    }
    private VillagerNameKeys(){}
}
