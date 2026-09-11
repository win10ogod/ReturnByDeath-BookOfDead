package dev.rbd.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.*;
import java.util.Map;

@Mixin(MinecraftServer.class)
public interface ServerWorldAccess {
    @Accessor("storageSource") LevelStorageSource.LevelStorageAccess rbd$storage();
    @Mutable @Accessor("storageSource") void rbd$storage(LevelStorageSource.LevelStorageAccess value);
    @Mutable @Accessor("playerDataStorage") void rbd$playerStorage(PlayerDataStorage value);
    @Mutable @Accessor("worldData") void rbd$worldData(WorldData value);
    @Accessor("levels") Map<ResourceKey<Level>,ServerLevel> rbd$levels();
    @Mutable @Accessor("scoreboard") void rbd$scoreboard(ServerScoreboard value);
    @Mutable @Accessor("customBossEvents") void rbd$bossEvents(CustomBossEvents value);
    @Mutable @Accessor("structureTemplateManager") void rbd$structures(StructureTemplateManager value);
    @Accessor("resources") void rbd$resources(MinecraftServer.ReloadableResources value);
    @Invoker("loadLevel") void rbd$loadLevel();
}
