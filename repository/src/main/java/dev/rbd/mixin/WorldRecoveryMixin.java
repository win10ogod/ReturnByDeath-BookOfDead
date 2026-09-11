package dev.rbd.mixin;
import dev.rbd.io.SnapshotStore;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.io.IOException;
import java.nio.file.Files;
@Mixin(LevelStorageSource.class)
public abstract class WorldRecoveryMixin {
    @Inject(method={"createAccess","validateAndCreateAccess"},at=@At("HEAD"))
    private void rbd$recoverBeforeOpening(String name,CallbackInfoReturnable<LevelStorageSource.LevelStorageAccess> ci) throws IOException {
        var world=((LevelStorageSource)(Object)this).getLevelPath(name);
        var control=SnapshotStore.controlFor(world);
        if(Files.exists(control.resolve("transaction.json")))new SnapshotStore(world,control).complete();
    }
}
