package dev.rbd.mixin;
import dev.rbd.client.ConnectedClientReturn;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class PausedClientInputMixin {
    @Inject(method="handleKeybinds",at=@At("HEAD"),cancellable=true)
    private void rbd$pauseInput(CallbackInfo ci){if(ConnectedClientReturn.paused)ci.cancel();}
}
