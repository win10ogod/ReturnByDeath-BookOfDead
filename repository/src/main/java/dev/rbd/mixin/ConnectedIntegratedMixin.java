package dev.rbd.mixin;
import dev.rbd.runtime.ConnectedReturn;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(IntegratedServer.class)
public abstract class ConnectedIntegratedMixin {
    @Inject(method="tickServer",at=@At("HEAD"),cancellable=true)
    private void rbd$pauseMenuCannotSaveClosedWorld(CallbackInfo ci){if(ConnectedReturn.tick((MinecraftServer)(Object)this))ci.cancel();}
}
