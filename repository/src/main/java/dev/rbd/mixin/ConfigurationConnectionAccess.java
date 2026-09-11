package dev.rbd.mixin;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;
@Mixin(ServerConfigurationPacketListenerImpl.class)
public interface ConfigurationConnectionAccess {
    @Accessor("currentTask") ConfigurationTask rbd$currentTask();
    @Invoker("startNextTask") void rbd$startNextTask();
}
