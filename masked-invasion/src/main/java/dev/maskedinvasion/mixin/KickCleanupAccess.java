package dev.maskedinvasion.mixin;
import com.example.generichenshin.service.KickService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.UUID;
@Mixin(value=KickService.class,remap=false)
public interface KickCleanupAccess {
    @Invoker("clearMobAbility") static void maskedInvasion$clear(UUID id){throw new AssertionError();}
}
