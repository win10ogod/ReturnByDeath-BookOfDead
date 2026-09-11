package dev.rbd.mixin;
import dev.rbd.runtime.GameSession;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ReadingInputMixin {
    @Shadow public ServerPlayer player;
    @Inject(method={"handleMovePlayer","handleMoveVehicle","handlePlayerInput","handleInteract","handleUseItem","handleUseItemOn","handlePlayerAction","handleContainerClick","handleSetCreativeModeSlot","handlePlayerCommand"},at=@At("HEAD"),cancellable=true)
    private void rbd$readOnly(CallbackInfo ci){var game=GameSession.current;if(game!=null&&(game.transitioning||(dev.rbd.RbdConfig.READ_LOCK.get()&&game.reading(player.getUUID()))))ci.cancel();}
}
