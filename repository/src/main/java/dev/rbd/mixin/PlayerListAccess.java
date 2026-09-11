package dev.rbd.mixin;

import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import java.util.*;

@Mixin(PlayerList.class)
public interface PlayerListAccess {
    @Accessor("players") List<ServerPlayer> rbd$players();
    @Accessor("playersByUUID") Map<UUID,ServerPlayer> rbd$playersById();
    @Accessor("stats") Map<UUID,ServerStatsCounter> rbd$stats();
    @Accessor("advancements") Map<UUID,PlayerAdvancements> rbd$advancements();
    @Mutable @Accessor("playerIo") void rbd$playerStorage(PlayerDataStorage value);
    @Invoker("updateEntireScoreboard") void rbd$sendScoreboard(ServerScoreboard board,ServerPlayer player);
}
