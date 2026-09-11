package dev.rbd.mixin;
import dev.rbd.client.ReturnLifecycle;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenMixin {
    @Inject(method="openWorld",at=@At("HEAD"),cancellable=true)
    private void rbd$recover(String world,Runnable onCancel,CallbackInfo ci){if(!ReturnLifecycle.beforeOpen(world))ci.cancel();}
}
