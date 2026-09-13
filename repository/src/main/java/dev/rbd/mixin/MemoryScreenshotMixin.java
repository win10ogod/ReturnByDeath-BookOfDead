package dev.rbd.mixin;

import dev.rbd.client.ClientRecorder;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keep the normal screenshot entry point and hooks; only defer memory capture's alpha pass. */
@Mixin(Screenshot.class)
public abstract class MemoryScreenshotMixin {
    @ModifyArg(method="takeScreenshot",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/NativeImage;downloadTexture(IZ)V"),index=1)
    private static boolean rbd$deferOpaqueAlpha(boolean opaque){return opaque&&!ClientRecorder.defersOpaqueAlpha();}
}
