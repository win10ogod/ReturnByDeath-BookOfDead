package dev.riderpack.mixin;

import dev.riderpack.BatchingMeshLayer;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.raphimc.immediatelyfast.feature.batching.BatchingBuffers$WrappedRenderLayer", remap = false)
public abstract class ImmediatelyFastMeshLayerMixin implements BatchingMeshLayer {
    @Unique private RenderType riderpack$meshLayer;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void riderpack$rememberGeometry(RenderType original, Runnable start, Runnable end, CallbackInfo ci) {
        riderpack$meshLayer = original;
    }

    @Override
    public RenderType riderpack$meshLayer() {
        return riderpack$meshLayer;
    }
}
