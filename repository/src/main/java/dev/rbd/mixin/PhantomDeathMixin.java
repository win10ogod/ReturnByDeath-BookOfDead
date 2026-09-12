package dev.rbd.mixin;

import dev.rbd.phantom.PhantomExecution;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommonHooks.class)
public abstract class PhantomDeathMixin {
    @Inject(method="onLivingDeath",at=@At("RETURN"),cancellable=true)
    private static void rbd$executeAuraVictim(LivingEntity victim,DamageSource source,CallbackInfoReturnable<Boolean> callback){
        if(PhantomExecution.executing(victim))callback.setReturnValue(false);
    }
}
