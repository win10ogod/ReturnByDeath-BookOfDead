package dev.rbd.mixin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.saveddata.maps.*;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ClientLevel.class)
public interface ClientMapAccess {
    @Accessor("mapData") Map<MapId,MapItemSavedData> rbd$maps();
}
