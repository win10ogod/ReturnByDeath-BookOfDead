package dev.rbd.phantom;

import dev.rbd.ModContent;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid="rbd",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class PhantomClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(ModContent.PHANTOM.get(),Renderer::new);}
    public static final class Renderer extends HumanoidMobRenderer<DespairPhantomEntity,HumanoidModel<DespairPhantomEntity>> {
        public Renderer(EntityRendererProvider.Context context){
            super(context,new SkeletonModel<>(context.bakeLayer(ModelLayers.WITHER_SKELETON)),0.5f);
            addLayer(new HumanoidArmorLayer<>(this,new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),context.getModelManager()));
            if(net.neoforged.fml.ModList.get().isLoaded("curios"))try{
                var constructor=Class.forName("top.theillusivec4.curios.client.render.CuriosLayer").getConstructor(RenderLayerParent.class);
                addLayer((RenderLayer)constructor.newInstance(this));
            }catch(ReflectiveOperationException ex){org.slf4j.LoggerFactory.getLogger("rbd").error("Could not attach native Curios phantom render layer",ex);}
        }
        @Override public ResourceLocation getTextureLocation(DespairPhantomEntity entity){return ResourceLocation.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png");}
    }
    private PhantomClient(){}
}
