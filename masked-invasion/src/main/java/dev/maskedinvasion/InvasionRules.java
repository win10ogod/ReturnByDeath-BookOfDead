package dev.maskedinvasion;

import dev.rbd.rules.WorldRules;
import dev.rbd.rules.WorldRules.Setting;

/** Native world rules also appear in RBD's existing-world rules editor. */
public final class InvasionRules {
    public static final Setting<Boolean> ENABLED=bool("miEnabled",true);
    public static final Setting<Integer> DAYS=num("miIntervalDays",3,1,Integer.MAX_VALUE);
    public static final Setting<Integer> WARNING=num("miWarningTicks",600,0,Integer.MAX_VALUE);
    public static final Setting<Integer> DURATION=num("miDefenseTicks",18000,0,Integer.MAX_VALUE);
    public static final Setting<Integer> RADIUS=num("miArenaRadius",48,8,1024);
    public static final Setting<Integer> HEIGHT=num("miArenaHeight",24,4,512);
    public static final Setting<Boolean> RECALL=bool("miRecallAtStart",true);
    public static final Setting<Boolean> CONFINEMENT=bool("miConfinePlayers",true);
    public static final Setting<Boolean> HOLD_CHECKPOINT=bool("miHoldCheckpointDuringRaid",true);
    public static final Setting<Boolean> DEATH_FAILURE=bool("miPlayerDeathFailsRaid",true);
    public static final Setting<Integer> BASE_COUNT=num("miBaseRaiders",4,1,10000);
    public static final Setting<Integer> EXTRA_MIN=num("miFirstEnhancedMin",1,0,10000);
    public static final Setting<Integer> EXTRA_MAX=num("miFirstEnhancedMax",2,0,10000);
    public static final Setting<Integer> COUNT_GROWTH=num("miRaidersPerWave",2,0,10000);
    public static final Setting<Integer> PER_PLAYER=num("miRaidersPerExtraPlayer",2,0,10000);
    public static final Setting<Integer> MAX_COUNT=num("miMaxRaiders",32,1,10000);
    public static final Setting<Integer> SUPER_WAVE=num("miSuperFormsFromWave",4,1,Integer.MAX_VALUE);
    public static final Setting<Integer> FINAL_WAVE=num("miFinalFormsFromWave",8,1,Integer.MAX_VALUE);
    public static final Setting<Integer> HEALTH=num("miBaseHealth",40,1,1024);
    public static final Setting<Integer> DAMAGE=num("miBaseDamage",3,1,2048);
    public static final Setting<Integer> HEALTH_GROWTH=num("miHealthGrowthPercent",15,0,10000);
    public static final Setting<Integer> DAMAGE_GROWTH=num("miDamageGrowthPercent",10,0,10000);
    public static final Setting<Integer> SPAWN_MIN=num("miSpawnMinDistance",6,1,1024);
    public static final Setting<Integer> SPAWN_MAX=num("miSpawnMaxDistance",20,1,1024);
    public static final Setting<Integer> SPAWN_RATE=num("miSpawnPerSecond",4,1,100);
    public static final Setting<Boolean> SKILLS=bool("miRiderSkills",true);
    public static final Setting<Integer> SKILL_TICKS=num("miSkillIntervalTicks",200,20,Integer.MAX_VALUE);
    public static final Setting<Boolean> EQUIPMENT_DROPS=bool("miEquipmentDrops",false);
    public static final Setting<Integer> REWARD=num("miVictoryEmeralds",3,0,10000);
    public static final Setting<Boolean> PARTICLES=bool("miBoundaryParticles",true);
    private static Setting<Boolean> bool(String name,boolean value){return WorldRules.bool(name,()->value);}
    private static Setting<Integer> num(String name,int value,int min,int max){return WorldRules.integer(name,()->value,min,max);}
    public static void init(){}
    private InvasionRules(){}
}
