package dev.rbd.memory;

/** Observed body state. This never writes damage, effects or attributes to the reader. */
public record SomaticState(float maxHealth, int air, int maxAir, boolean submerged,
                          boolean burning, int frozenTicks, float fallDistance,
                          double damage, String damageType, boolean terminal, boolean rememberedEnding) {
    public SomaticState(float maxHealth,int air,int maxAir,boolean submerged,boolean burning,int frozenTicks,float fallDistance,double damage,String damageType,boolean terminal){
        this(maxHealth,air,maxAir,submerged,burning,frozenTicks,fallDistance,damage,damageType,terminal,false);
    }
    public SomaticState asExperience() {
        // A death inside someone else's memory is not the end of the reader's own life.
        return new SomaticState(maxHealth,air,maxAir,submerged,burning,frozenTicks,fallDistance,damage,damageType,false,terminal||rememberedEnding);
    }
}
