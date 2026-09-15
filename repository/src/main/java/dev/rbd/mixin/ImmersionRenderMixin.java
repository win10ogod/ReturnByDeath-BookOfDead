package dev.rbd.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.rbd.client.ImmersionOverlay;
import dev.rbd.client.CheckpointNotice;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ImmersionRenderMixin {
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;flush()V"))
    private void rbd$lastPerception(DeltaTracker delta,boolean renderLevel,CallbackInfo ci,@Local GuiGraphics graphics){
        graphics.flush();graphics.pose().pushPose();graphics.pose().translate(0,0,4000);
        ImmersionOverlay.render(graphics);CheckpointNotice.render(graphics);graphics.flush();graphics.pose().popPose();
    }
}
