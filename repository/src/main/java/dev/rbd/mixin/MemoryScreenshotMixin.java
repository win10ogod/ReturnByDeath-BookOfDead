package dev.rbd.mixin;

import dev.rbd.client.ClientRecorder;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Ordinary screenshots are unchanged; memory captures finish their CPU work on the encoder. */
@Mixin(Screenshot.class)
public abstract class MemoryScreenshotMixin {
    @ModifyArg(method="takeScreenshot",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/NativeImage;downloadTexture(IZ)V"),index=1)
    private static boolean rbd$deferOpaqueAlpha(boolean opaque){return opaque&&!ClientRecorder.defersOpaqueAlpha();}
    @Redirect(method="takeScreenshot",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/NativeImage;flipY()V"))
    private static void rbd$deferVerticalFlip(com.mojang.blaze3d.platform.NativeImage image){
        if(!ClientRecorder.defersOpaqueAlpha())image.flipY();
    }
}
