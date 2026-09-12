package dev.rbd.mixin;

import dev.rbd.phantom.*;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;

/** Every installed slot definition is eligible; custom player slots are not a hardcoded list. */
@Pseudo
@Mixin(targets="top.theillusivec4.curios.api.CuriosApi",remap=false)
public abstract class PhantomCuriosMixin {
    @Inject(method="getEntitySlots(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Map;",at=@At("HEAD"),cancellable=true,remap=false)
    private static void rbd$mirrorSlotTypes(LivingEntity wearer,CallbackInfoReturnable<Map> result){
        if(wearer instanceof DespairPhantomEntity)result.setReturnValue(PhantomEquipment.slotTypes(wearer.level().isClientSide));
    }
}
