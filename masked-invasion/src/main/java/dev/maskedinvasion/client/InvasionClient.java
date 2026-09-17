package dev.maskedinvasion.client;
import dev.maskedinvasion.MaskedInvasion;
import com.kelco.kamenridercraft.client.renderer.BasicEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
@EventBusSubscriber(modid=MaskedInvasion.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class InvasionClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(MaskedInvasion.INVADER.get(),BasicEntityRenderer::new);}
}
