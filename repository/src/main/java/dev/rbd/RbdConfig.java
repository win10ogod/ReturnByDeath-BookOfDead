package dev.rbd;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;
/** Explicit Minecraft adaptations. No death count, lifetime duration, or archive retention limit. */
public final class RbdConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue AUTO_BIND, AUTO_BIND_JOIN, RECORD_CREATURES;
    public static final ModConfigSpec.IntValue MAX_HOLDERS;
    public static final ModConfigSpec.IntValue RASTER_WIDTH, RASTER_HEIGHT, VISUAL_INTERVAL, PERCEPTION_DISTANCE, CLIENT_WIDTH;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MILESTONES;
    public static final ModConfigSpec.DoubleValue MIASMA_RANGE;
    public static final ModConfigSpec.DoubleValue DEATH_DWELL;
    static {
        var b = new ModConfigSpec.Builder();
        AUTO_BIND=b.comment("Automatically grant the first singleplayer character the authority.").define("autoBindSingleplayer",true);
        MAX_HOLDERS=b.comment("Maximum active Return by Death holders. 0 allows any number. Lowering this prevents new grants but never silently revokes existing holders; use the console unbind command first. All holders share the same world checkpoint.").defineInRange("maxHolders",1,0,Integer.MAX_VALUE);
        AUTO_BIND_JOIN=b.comment("Grant joining players Return by Death until maxHolders is reached. Dedicated servers require the offline supervisor. False keeps grants under console control.").define("autoBindOnJoin",false);
        RECORD_CREATURES=b.comment("Also record unnamed non-villager creatures. Players, villagers and named characters are always recorded.").define("recordUnnamedCreatures",false);
        CLIENT_WIDTH=b.comment("Width of client-rendered first-person memory images; height follows the viewport aspect ratio. Zero preserves the native viewport resolution.").defineInRange("clientMemoryWidth",0,0,3840);
        RASTER_WIDTH=b.comment("Server-rendered subjective memory image width. Player clients can supply their rendered first-person view.").defineInRange("memoryRasterWidth",96,16,1920);
        RASTER_HEIGHT=b.defineInRange("memoryRasterHeight",54,9,1080);
        VISUAL_INTERVAL=b.comment("Ticks between perception raster images. Pose, contacts, actions and sounds are recorded each tick.").defineInRange("memoryImageIntervalTicks",1,1,1200);
        PERCEPTION_DISTANCE=b.comment("NPC visual range in blocks; this is a Minecraft adaptation, not a novel formula.").defineInRange("perceptionDistance",32,4,256);
        MIASMA_RANGE=b.defineInRange("miasmaSensingRange",32.0,1,256);
        DEATH_DWELL=b.comment("Seconds spent at a recorded death's sensory ending. Presentation only, not a respawn delay or a canonical duration. Zero disables the extra dwell.").defineInRange("memoryDeathDwellSeconds",4.0,0,30);
        MILESTONES=b.comment("Authored one-way checkpoint milestones. Beds and ordinary logout never create checkpoints.").defineListAllowEmpty("checkpointAdvancements",List.of("minecraft:story/enter_the_nether","minecraft:end/kill_dragon"),()->"",v->v instanceof String);
        SPEC=b.build();
    }
    private RbdConfig(){}
}
