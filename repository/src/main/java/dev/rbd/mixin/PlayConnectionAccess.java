package dev.rbd.mixin;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.PlayerChunkSender;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ServerGamePacketListenerImpl.class)
public interface PlayConnectionAccess {
    @Mutable @Accessor("chunkSender") void rbd$chunkSender(PlayerChunkSender value);
    @Accessor("aboveGroundTickCount") void rbd$floatingTicks(int value);
    @Accessor("aboveGroundVehicleTickCount") void rbd$vehicleFloatingTicks(int value);
}
