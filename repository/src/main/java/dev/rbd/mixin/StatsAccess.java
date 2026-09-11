package dev.rbd.mixin;
import net.minecraft.stats.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(StatsCounter.class)
public interface StatsAccess {
    @Accessor("stats") Object2IntMap<Stat<?>> rbd$stats();
}
