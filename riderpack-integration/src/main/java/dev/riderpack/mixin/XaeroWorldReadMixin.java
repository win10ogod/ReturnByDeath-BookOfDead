package dev.riderpack.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.riderpack.MapReturnGate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import xaero.map.file.worldsave.WorldDataHandler;
import xaero.map.region.MapRegion;

@Pseudo
@Mixin(targets = "xaero.map.file.worldsave.WorldDataHandler", remap = false)
public abstract class XaeroWorldReadMixin {
    @Shadow private ServerLevel worldServer;
    @WrapMethod(method = "buildRegion")
    private WorldDataHandler.Result riderpack$finishBeforeUnload(MapRegion region,
            HolderLookup<Block> blocks, Registry<Block> blockRegistry, Registry<Fluid> fluids,
            boolean loading, int[] chunks, Operation<WorldDataHandler.Result> original) {
        if (!MapReturnGate.enter()) return WorldDataHandler.Result.CANCEL;
        try {
            // Same-dimension client replacement does not call Xaero's prepareSingleplayer.
            // Resolve the live level while the gate protects it against a concurrent unload.
            if (worldServer != null) {
                ServerLevel current = worldServer.getServer().getLevel(worldServer.dimension());
                if (current == null) return WorldDataHandler.Result.CANCEL;
                worldServer = current;
            }
            return original.call(region, blocks, blockRegistry, fluids, loading, chunks);
        }
        finally { MapReturnGate.leave(); }
    }
}
