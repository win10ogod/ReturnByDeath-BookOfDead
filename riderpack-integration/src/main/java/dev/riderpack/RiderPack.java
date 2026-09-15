package dev.riderpack;

import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import dev.ftb.mods.ftbteams.api.event.PlayerLoggedInAfterTeamEvent;
import dev.rbd.api.WorldReturnEvent;
import dev.rbd.io.AtomicJson;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.LoggerFactory;

/** Integration is server authoritative. World data follows the checkpoint; play sockets stay attached. */
@Mod("riderpack")
public final class RiderPack {
    private String operation = "";
    public RiderPack() {
        ArchiveCompression.register();
        NeoForge.EVENT_BUS.addListener(this::beforeReturn);
        NeoForge.EVENT_BUS.addListener(this::afterReturn);
        NeoForge.EVENT_BUS.addListener(this::login);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> MapReturnGate.after());
    }
    private void beforeReturn(WorldReturnEvent.Before event) {
        MapReturnGate.before(event.getServer());
        try {
            operation = AtomicJson.read(event.getArchivePath().resolve("transaction.json")).get("operation").getAsString();
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException("Unable to identify the return transaction", ex);
        }
        HenshinReturn.before(event.getServer());
        if (ServerQuestFile.INSTANCE != null) ServerQuestFile.INSTANCE.saveNow();
        if (TeamManagerImpl.INSTANCE != null) TeamManagerImpl.INSTANCE.saveNow();
    }
    private void afterReturn(WorldReturnEvent.After event) {
        MapReturnGate.after();
        HenshinReturn.after(event.getServer());
        // Do not unload/save the old caches here: the checkpoint has already replaced their disk files.
        TeamManagerImpl teams = new TeamManagerImpl(event.getServer());
        TeamManagerImpl.INSTANCE = teams;
        teams.load();
        ServerQuestFile quests = new ServerQuestFile(event.getServer());
        ServerQuestFile.INSTANCE = quests;
        quests.load(); // also emits the cache-clear callback used by FTB's task handlers
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            restoreRiderVitality(player);
            // The null player path creates a personal team for a player absent from the checkpoint,
            // without firing login events. Existing party membership comes from the restored files.
            teams.playerLoggedIn(null, player.getUUID(), player.getScoreboardName());
            var personal = teams.getPersonalTeamForPlayerID(player.getUUID());
            teams.syncAllToPlayer(player, personal.getEffectiveTeam());
            // Rebind FTB's menu listener to the replacement ServerPlayer and sync through FTB packets.
            quests.playerLoggedIn(new PlayerLoggedInAfterTeamEvent(personal.getEffectiveTeam(), player));
        }
        LoggerFactory.getLogger("riderpack").info("RIDERPACK_REBOUND operation={} players={} quests={} teams={}",
                operation, event.getServer().getPlayerCount(), quests.getAllChapters().size(), teams.getKnownPlayerTeams().size());
    }
    private static void restoreRiderVitality(ServerPlayer player) {
        // Live checkpoints save the current Health, but vanilla omits KRC Boost's transient
        // maximum-health modifier. Loading first clamps against the unboosted maximum, then
        // BoostEngine would scale that already-loaded health a second time. Rebuild the
        // modifier and restore the original saved Health, including checkpoints made by 1.1.
        // A player offline at the checkpoint has neither power effect (Boost clears on logout),
        // so their saved health is already unboosted and should follow normal BoostEngine scaling.
        Float savedHealth = null;
        if (dev.krcboost.BoostEngine.transformed(player)
                && (player.hasEffect(dev.krcboost.KrcBoost.RIDER_POWER)
                || player.hasEffect(dev.krcboost.KrcBoost.EVIL_RIDER_POWER))) {
            var path = player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                    .resolve(player.getUUID() + ".dat");
            try {
                var saved = net.minecraft.nbt.NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                if (saved.contains("Health", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
                    float value = saved.getFloat("Health");
                    if (Float.isFinite(value) && value >= 0) savedHealth = value;
                }
            } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException("Unable to restore saved rider health", ex);
            }
        }
        dev.krcboost.BoostEngine.update(player, dev.krcboost.KrcBoost.config());
        if (savedHealth != null && player.isAlive()) player.setHealth(Math.min(savedHealth, player.getMaxHealth()));
    }
    private void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.getPersistentData().getBoolean("riderpackGuideGiven")) {
            player.getInventory().placeItemBackInInventory(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ftbquests:book"))));
            player.getPersistentData().putBoolean("riderpackGuideGiven", true);
        }
    }
}
