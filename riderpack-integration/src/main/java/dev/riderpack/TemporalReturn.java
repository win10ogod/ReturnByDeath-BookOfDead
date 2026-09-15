package dev.riderpack;

import com.example.generichenshin.addon.ItemWheelData;
import com.example.generichenshin.compat.TimeclockCompat;
import com.example.generichenshin.service.KabutoBulletService;
import com.example.generichenshin.service.ZioTimeStopService;
import io.github.suel_ki.timeclock.core.data.TimeData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** GH's static timers use player age, which restarts when a connected return replaces players. */
final class TemporalReturn {
    private static final String KEY = "riderpackTemporalCheckpoint";
    private static final Class<?> ZIO = ZioTimeStopService.class, BULLET = KabutoBulletService.class;
    private TemporalReturn() {}
    static void before(MinecraftServer server) {
        for (var p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            var tag = new CompoundTag();
            tag.put("mightyCombat", MightyCombatReturn.capture(id, p.tickCount));
            tag.putInt("uses", number(ZIO, "STOPS_USED", id));
            tag.putLong("lock", remaining(ZIO, "STOP_LOCK_UNTIL", id, p.tickCount));
            tag.putLong("endSound", remaining(ZIO, "END_SOUND_TICK", id, p.tickCount));
            tag.putBoolean("wasPaused", Boolean.TRUE.equals(HenshinReturn.map(ZIO, "WAS_PAUSED").get(id)));
            tag.putLong("bulletProtect", remaining(BULLET, "BULLET_PROTECT_END", id, p.level().getGameTime()));
            tag.putString("faizForm", HenshinReturn.<UUID,String>map(BULLET, "LAST_FAIZ_AXEL_FORM").getOrDefault(id, ""));
            tag.putBoolean("pendingClear", set(BULLET, "PENDING_COOLDOWN_CLEAR").contains(id));
            var wheel = new CompoundTag();
            String prefix = id + "|";
            HenshinReturn.<String,Long>map(ItemWheelData.class, "COOLDOWNS").forEach((key, end) -> {
                long left = end - p.level().getGameTime();
                if (key.startsWith(prefix) && left > 0) wheel.putLong(key.substring(prefix.length()), left);
            });
            tag.put("wheel", wheel);
            p.getPersistentData().put(KEY, tag);
        }
    }
    static void after(MinecraftServer server) {
        MightyCombatReturn.clear();
        for (String name : new String[]{"STOPS_USED", "STOP_LOCK_UNTIL", "END_SOUND_TICK", "WAS_PAUSED", "LAST_LOGGED_FORM"}) HenshinReturn.map(ZIO, name).clear();
        set(ZIO, "SHADER_SENT").clear();
        for (String name : new String[]{"BULLET_PROTECT_END", "LAST_FAIZ_AXEL_FORM"}) HenshinReturn.map(BULLET, name).clear();
        set(BULLET, "PENDING_COOLDOWN_CLEAR").clear();
        HenshinReturn.map(ItemWheelData.class, "COOLDOWNS").clear();
        for (var p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            var tag = p.getPersistentData().getCompound(KEY);
            MightyCombatReturn.restore(id, p.tickCount, tag.getCompound("mightyCombat"));
            HenshinReturn.map(ZIO, "STOPS_USED").put(id, tag.getInt("uses"));
            deadline(ZIO, "STOP_LOCK_UNTIL", id, p.tickCount, tag.getLong("lock"));
            if (tag.getLong("endSound") > 0) HenshinReturn.map(ZIO, "END_SOUND_TICK").put(id, p.tickCount + (int)tag.getLong("endSound"));
            HenshinReturn.map(ZIO, "WAS_PAUSED").put(id, tag.getBoolean("wasPaused"));
            deadline(BULLET, "BULLET_PROTECT_END", id, p.level().getGameTime(), tag.getLong("bulletProtect"));
            HenshinReturn.map(BULLET, "LAST_FAIZ_AXEL_FORM").put(id, tag.getString("faizForm"));
            if (tag.getBoolean("pendingClear")) set(BULLET, "PENDING_COOLDOWN_CLEAR").add(id);
            var wheel = tag.getCompound("wheel");
            for (String key : wheel.getAllKeys()) HenshinReturn.map(ItemWheelData.class, "COOLDOWNS").put(id + "|" + key, p.level().getGameTime() + wheel.getLong(key));
            TimeData.get(p.level()).ifPresent(data -> data.syncToClient(p, p.level()));
        }
        for (var level : server.getAllLevels()) {
            TimeData.get(level).ifPresent(data -> {
                if (data.isTimePaused()) TimeclockCompat.sendDesaturateShaderStart(level);
            });
        }
    }
    private static int number(Class<?> owner, String field, UUID id) {
        Object value = HenshinReturn.map(owner, field).get(id);
        return value instanceof Number n ? n.intValue() : 0;
    }
    private static long remaining(Class<?> owner, String field, UUID id, long now) {
        Object value = HenshinReturn.map(owner, field).get(id);
        return value instanceof Number n ? Math.max(0, n.longValue() - now) : 0;
    }
    private static void deadline(Class<?> owner, String field, UUID id, long now, long left) {
        if (left > 0) HenshinReturn.map(owner, field).put(id, now + left);
    }
    @SuppressWarnings("unchecked") private static Set<UUID> set(Class<?> owner, String field) {
        try { return (Set<UUID>) HenshinReturn.field(owner, field).get(null); }
        catch (IllegalAccessException ex) { throw new IllegalStateException(ex); }
    }
}
