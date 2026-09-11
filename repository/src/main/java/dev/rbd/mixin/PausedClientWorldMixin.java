package dev.rbd.mixin;
import dev.rbd.client.ConnectedClientReturn;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientLevel.class)
public abstract class PausedClientWorldMixin {
    @Inject(method={"tick","tickEntities"},at=@At("HEAD"),cancellable=true)
    private void rbd$pauseSimulation(CallbackInfo ci){if(ConnectedClientReturn.paused)ci.cancel();}
}
