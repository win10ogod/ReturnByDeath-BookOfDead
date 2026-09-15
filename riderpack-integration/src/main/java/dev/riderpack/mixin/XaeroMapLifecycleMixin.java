package dev.riderpack.mixin;

import dev.riderpack.MapReturnGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.MapProcessor;
import xaero.map.file.MapSaveLoad;
import xaero.map.file.worldsave.WorldDataHandler;
import xaero.map.region.LeveledRegion;

@Pseudo
@Mixin(targets = "xaero.map.MapProcessor", remap = false)
public abstract class XaeroMapLifecycleMixin {
    @Shadow private ClientLevel world;
    @Shadow private WorldDataHandler worldDataHandler;
    @Shadow private MapSaveLoad mapSaveLoad;

    @Inject(method = "updateWorldSynced", at = @At("RETURN"))
    private void riderpack$rebindWorldSave(CallbackInfo ci) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (world == null || server == null || worldDataHandler.getWorldServer() == null) return;
        if (!MapReturnGate.enter()) return;
        try {
            var cached = worldDataHandler.getWorldServer();
            if (cached != server.getLevel(cached.dimension())) {
                worldDataHandler.prepareSingleplayer(world, (MapProcessor)(Object)this);
            }
        } finally { MapReturnGate.leave(); }
    }

    @Inject(method = "removeMapRegion", at = @At("TAIL"))
    private void riderpack$releaseCancelledLoad(LeveledRegion<?> region, CallbackInfo ci) {
        // A cancelled world-save read removes its region but can leave the writer waiting
        // for that detached region forever. Let Xaero select the next valid region.
        if (mapSaveLoad.getNextToLoadByViewing() == region) {
            mapSaveLoad.setNextToLoadByViewing(null);
        }
    }
}
