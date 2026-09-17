package dev.rbd.runtime;

import dev.rbd.RbdConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import java.util.SplittableRandom;

/** Vanilla CustomName owns persistence and synchronization; no entity registry or tick scan. */
public final class VillagerNames {
    private static final String[] GIVEN = {
        "艾文", "莉娜", "諾亞", "米拉", "里昂", "艾拉", "洛伊", "希雅",
        "菲恩", "露娜", "亞倫", "妮雅", "伊恩", "瑟琳", "雷恩", "芙蕾",
        "路卡", "梅莉", "奧文", "莉瑟", "凱爾", "艾莉", "西恩", "諾菈",
        "尤里", "蕾娜", "萊恩", "薇拉", "托爾", "露西", "亞修", "希爾",
        "賽恩", "伊莉", "艾德", "羅莎", "魯恩", "緹娜", "洛恩", "芙蘭",
        "奧利", "米娜", "埃里", "蕾雅", "柯林", "賽拉", "尼爾", "艾瑪"
    };
    private static final String[] FAMILY = {
        "柳溪", "白樺", "灰石", "晨露", "橡木", "松風", "河灣", "麥穗",
        "楓葉", "山嶺", "溪谷", "石橋", "春泉", "落葉", "赤岩", "月泉",
        "青苔", "霜林", "銀葉", "遠丘", "榛木", "藤原", "蒼松", "石井",
        "風鈴", "晚星", "冬青", "穗田", "榆木", "白峰", "苜蓿", "露草",
        "雪杉", "野薔", "澄湖", "鳶尾", "林鐘", "金雀", "岩泉", "晨霧",
        "深谷", "海棠", "溪石", "栗林", "小丘", "長風", "晴川", "望月"
    };

    public static boolean assign(Entity entity) {
        if (!(entity instanceof Villager villager) || !(entity.level() instanceof ServerLevel level)
                || !RbdConfig.AUTO_NAME_VILLAGERS.get(level.getGameRules())) return false;
        // Older RBD versions used this exact plain-text UUID placeholder. Keep every other custom name.
        var legacy = Component.literal("旅人 · " + villager.getUUID().toString().substring(0, 4));
        if (villager.hasCustomName() && !legacy.equals(villager.getCustomName())) return false;
        // UUID-based names survive a return to a checkpoint made before the villager was named.
        var id = villager.getUUID();
        var random = new SplittableRandom(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 23));
        villager.setCustomName(Component.literal(GIVEN[random.nextInt(GIVEN.length)] + "·" + FAMILY[random.nextInt(FAMILY.length)]));
        return true;
    }

    private VillagerNames() {}
}
