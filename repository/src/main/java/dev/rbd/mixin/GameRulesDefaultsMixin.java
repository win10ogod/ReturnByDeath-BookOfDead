package dev.rbd.mixin;
import dev.rbd.rules.WorldRules;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRules.class)
public abstract class GameRulesDefaultsMixin {
    @Inject(method="<init>()V",at=@At("TAIL"))
    private void rbd$legacyDefaults(CallbackInfo ci){WorldRules.seed((GameRules)(Object)this);}
}
