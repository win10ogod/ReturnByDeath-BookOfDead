package dev.maskedinvasion.mixin;
import dev.maskedinvasion.*;
import dev.rbd.runtime.GameSession;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=GameSession.class,remap=false)
public abstract class CheckpointSafetyMixin {
    @Inject(method="safeForCheckpoint",at=@At("HEAD"),cancellable=true)
    private void maskedInvasion$defendFirst(ServerPlayer player,CallbackInfoReturnable<Boolean> result){
        if(InvasionRules.HOLD_CHECKPOINT.get(player.server.getGameRules())&&Invasions.blocksCheckpoint(player.server))result.setReturnValue(false);
    }
}
