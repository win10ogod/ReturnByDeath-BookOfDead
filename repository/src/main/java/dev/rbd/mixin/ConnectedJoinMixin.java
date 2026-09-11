package dev.rbd.mixin;
import dev.rbd.runtime.ConnectedReturn;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ConnectedJoinMixin {
    @Unique private boolean rbd$queued;
    @Inject(method="startNextTask",at=@At("HEAD"),cancellable=true)
    private void rbd$waitBeforeFinishingConfiguration(CallbackInfo ci){
        var access=(ConfigurationConnectionAccess)this;
        if(ConnectedReturn.paused()&&access.rbd$currentTask()==null){
            if(!rbd$queued){rbd$queued=true;ConnectedReturn.defer(()->{rbd$queued=false;access.rbd$startNextTask();});}
            ci.cancel();
        }
    }
}
