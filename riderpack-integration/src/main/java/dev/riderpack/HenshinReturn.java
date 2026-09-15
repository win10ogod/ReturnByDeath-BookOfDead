package dev.riderpack;

import com.example.generichenshin.config.HenshinConfig;
import com.example.generichenshin.addon.ItemWheelData;
import com.example.generichenshin.service.HenshinService;
import com.example.generichenshin.service.WeaponService;
import net.minecraft.server.MinecraftServer;
import java.lang.reflect.Field;
import java.util.Map;

/** Reload GH's world-backed caches after the connected return replaces their files. */
final class HenshinReturn {
    private HenshinReturn() {}
    static void before(MinecraftServer server) {
        TemporalReturn.before(server);
        // Create even empty files, so a checkpoint can represent the absence of later discoveries.
        HenshinConfig.saveRememberedForms();
        HenshinConfig.saveFormWeapons();
        HenshinConfig.flushPendingSaves();
        ItemWheelData.markDirty();
        ItemWheelData.flush();
    }
    static void after(MinecraftServer server) {
        TemporalReturn.after(server);
        // Loading an absent legacy file does not clear these upstream maps on its own.
        for (String name : new String[]{"rememberedForms", "formWeaponB64", "lastForm"}) map(HenshinConfig.class, name).clear();
        for (String name : new String[]{"rememberedFormsDirty", "formWeaponsDirty"}) set(HenshinConfig.class, name, false);
        HenshinConfig.loadRememberedForms();
        HenshinConfig.loadFormWeapons();
        set(ItemWheelData.class, "dirty", false);
        ItemWheelData.load();
        WeaponService.clearAll();
        for (String name : new String[]{"lastArmorWorn", "lastSwitchTick", "lastBeltKey", "lastBeltSerial"}) map(HenshinService.class, name).clear();
        for (var player : server.getPlayerList().getPlayers()) HenshinService.sendFormList(player);
    }
    static Field field(Class<?> owner, String name) {
        try {var f=owner.getDeclaredField(name);f.setAccessible(true);return f;}
        catch (ReflectiveOperationException ex) {throw new IllegalStateException("GH integration field changed: "+owner.getName()+"."+name, ex);}
    }
    @SuppressWarnings("unchecked") static <K,V> Map<K,V> map(Class<?> owner, String name) {
        try {return (Map<K,V>)field(owner,name).get(null);}
        catch (IllegalAccessException ex) {throw new IllegalStateException(ex);}
    }
    static void set(Class<?> owner, String name, Object value) {
        try {field(owner,name).set(null,value);}
        catch (IllegalAccessException ex) {throw new IllegalStateException(ex);}
    }
}
