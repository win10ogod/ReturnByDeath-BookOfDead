package dev.rbd.mixin;
import dev.rbd.runtime.ConnectedReturn;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftServer.class)
public abstract class ConnectedServerMixin {
    @Inject(method="stopServer",at=@At("HEAD"))
    private void rbd$shutdown(CallbackInfo ci){ConnectedReturn.beforeShutdown((MinecraftServer)(Object)this);}
    @Inject(method="tickServer",at=@At("HEAD"),cancellable=true)
    private void rbd$worldPause(CallbackInfo ci){
        if(ConnectedReturn.tick((MinecraftServer)(Object)this))ci.cancel();
    }
}
