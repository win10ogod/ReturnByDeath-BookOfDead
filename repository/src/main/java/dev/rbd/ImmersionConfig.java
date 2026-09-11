package dev.rbd;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local sensory preferences; never changes mortality, history, checkpoint or reading eligibility. */
public final class ImmersionConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue REDUCED,ECHOES;
    public static final ModConfigSpec.DoubleValue VISUAL,AUDIO;
    static {
        var b=new ModConfigSpec.Builder();
        REDUCED=b.comment("Plain fades instead of peripheral constriction and pulses. F8 toggles this while reading.").define("reducedEffects",false);
        ECHOES=b.comment("Subtle remembered body sensations after a return and on encountering the last death site. Never changes health or movement.").define("returnEchoes",true);
        VISUAL=b.defineInRange("visualIntensity",0.8,0,1);
        AUDIO=b.comment("Volume multiplier for original body-sensation cues. Minecraft master/player volume remains effective.").defineInRange("bodySoundIntensity",0.65,0,1);
        SPEC=b.build();
    }
    private ImmersionConfig(){}
}
