package dev.riderpack.mixin;

import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.AcceleratedBufferBuilder;
import dev.riderpack.BatchingMeshLayer;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * AR 1.0.14 keys model meshes by builder equality (render type + vertex layout).
 * ImmediatelyFast's HUD wrappers contain fresh per-draw callbacks, so the same
 * geometry otherwise leaves another builder and callbacks in that cache every frame.
 * Only geometry equality is normalized: drawing still uses the original wrapper,
 * including its color/depth actions, ordering, and current buffer. Layout equality
 * and genuinely different underlying render types remain distinct.
 */
@Pseudo
@Mixin(value = AcceleratedBufferBuilder.class, remap = false)
public abstract class AcceleratedMeshIdentityMixin {
    @Redirect(method = {"equals", "hashCode"}, at = @At(value = "INVOKE",
            target = "Lcom/github/argon4w/acceleratedrendering/core/buffers/accelerated/builders/AcceleratedBufferBuilder;getRenderType()Lnet/minecraft/client/renderer/RenderType;"),
            require = 3, expect = 3, allow = 3)
    private RenderType riderpack$stableGeometryIdentity(AcceleratedBufferBuilder builder) {
        return BatchingMeshLayer.unwrap(builder.getRenderType());
    }
}
