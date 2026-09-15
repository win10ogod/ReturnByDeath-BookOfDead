package dev.riderpack.mixin;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** GH 0.1.146 calls Dist.CLIENT.isClient(), which is always true even on a dedicated server. */
@Mixin(targets = "com.example.generichenshin.service.MightyCombatService", remap = false)
public abstract class MightyCombatSideMixin {
    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lnet/neoforged/api/distmarker/Dist;isClient()Z"), require = 1)
    private static boolean riderpack$actualPhysicalSide(Dist ignored) {
        return FMLEnvironment.dist.isClient();
    }
}
