package dev.rbd.mixin;
import dev.rbd.client.ConnectedClientReturn;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public abstract class ConnectedClientMixin {
    @Shadow @Final @Mutable private Scoreboard scoreboard;
    @ModifyVariable(method="handleRespawn",at=@At("STORE"),ordinal=0)
    private boolean rbd$newWorld(boolean changedDimension){return changedDimension||ConnectedClientReturn.resetWorld;}
    @Inject(method="handleRespawn",at=@At("TAIL"))
    private void rbd$resetDone(CallbackInfo ci){if(ConnectedClientReturn.resetWorld){scoreboard=new Scoreboard();ConnectedClientReturn.resetWorld=false;}}
}
