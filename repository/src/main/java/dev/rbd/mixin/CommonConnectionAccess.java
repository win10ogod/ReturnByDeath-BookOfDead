package dev.rbd.mixin;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;
@Mixin(ServerCommonPacketListenerImpl.class)
public interface CommonConnectionAccess {
    @Invoker("keepConnectionAlive") void rbd$keepAlive();
    @Accessor("connection") Connection rbd$connection();
}
