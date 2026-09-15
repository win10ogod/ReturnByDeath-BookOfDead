package dev.riderpack;

import com.example.generichenshin.service.MightyCombatService;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/** Persist remaining combo time, never the age of a player object that will be replaced. */
final class MightyCombatReturn {
    private MightyCombatReturn() {}
    static CompoundTag capture(UUID id, long now) {
        var tag = new CompoundTag();
        Object state = HenshinReturn.map(MightyCombatService.class, "COMBOS").get(id);
        if (state == null) return tag;
        try {
            long last = HenshinReturn.field(state.getClass(), "lastAction").getLong(state);
            if (last == Long.MIN_VALUE) return tag;
            tag.putInt("stage", HenshinReturn.field(state.getClass(), "stage").getInt(state));
            tag.putLong("cooldown", Math.max(0, HenshinReturn.field(state.getClass(), "cooldownUntil").getLong(state) - now));
            tag.putLong("age", Math.max(0, now - last));
            return tag;
        } catch (IllegalAccessException ex) { throw new IllegalStateException("Cannot save GH empty-hand combo", ex); }
    }
    static void clear() { HenshinReturn.map(MightyCombatService.class, "COMBOS").clear(); }
    static void restore(UUID id, long now, CompoundTag tag) {
        if (!tag.contains("stage")) return; // Legacy checkpoints start without an old player's combo.
        try {
            Class<?> type = Class.forName(MightyCombatService.class.getName() + "$ComboState");
            var constructor = type.getDeclaredConstructor();constructor.setAccessible(true);
            Object state = constructor.newInstance();
            HenshinReturn.field(type, "stage").setInt(state, tag.getInt("stage"));
            HenshinReturn.field(type, "cooldownUntil").setLong(state, now + tag.getLong("cooldown"));
            HenshinReturn.field(type, "lastAction").setLong(state, now - tag.getLong("age"));
            HenshinReturn.map(MightyCombatService.class, "COMBOS").put(id, state);
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException("Cannot restore GH empty-hand combo", ex); }
    }
}
