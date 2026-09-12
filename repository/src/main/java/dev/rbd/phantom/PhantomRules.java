package dev.rbd.phantom;

import dev.rbd.rules.WorldRules;
import dev.rbd.rules.WorldRules.Setting;
import java.util.List;

/** Native Create/Edit World rules; no separate supervisor or configuration file. */
public final class PhantomRules {
    public static final Setting<Boolean> ENABLED=WorldRules.bool("rbdPhantomEnabled",()->true);
    public static final Setting<Double> THRESHOLD=WorldRules.decimal("rbdPhantomMiasmaThreshold",()->10.0,0,Double.MAX_VALUE);
    public static final Setting<Integer> CHECK_TICKS=WorldRules.integer("rbdPhantomCheckTicks",()->20,1,Integer.MAX_VALUE);
    public static final Setting<Integer> WARNING_TICKS=WorldRules.integer("rbdPhantomWarningTicks",()->200,0,Integer.MAX_VALUE);
    public static final Setting<Integer> COOLDOWN_TICKS=WorldRules.integer("rbdPhantomEncounterCooldownTicks",()->12000,0,Integer.MAX_VALUE);
    public static final Setting<Double> SPAWN_DISTANCE=WorldRules.decimal("rbdPhantomSpawnDistance",()->20.0,2,256);
    public static final Setting<Integer> ABSENT_TICKS=WorldRules.integer("rbdPhantomAbsentTargetTicks",()->1200,20,Integer.MAX_VALUE);
    public static final Setting<Double> BASE_HEALTH=WorldRules.decimal("rbdPhantomBaseHealth",()->160.0,1,1000000);
    public static final Setting<Double> HEALTH_SCALE=WorldRules.decimal("rbdPhantomPlayerHealthScale",()->2.0,0,1000000);
    public static final Setting<Double> BASE_DAMAGE=WorldRules.decimal("rbdPhantomBaseDamage",()->6.0,0,1000000);
    public static final Setting<Double> DAMAGE_SCALE=WorldRules.decimal("rbdPhantomPlayerDamageScale",()->0.5,0,1000000);
    public static final Setting<Double> SPEED=WorldRules.decimal("rbdPhantomMovementSpeed",()->0.30,0,1024);
    public static final Setting<Double> PHASE_THRESHOLD=WorldRules.decimal("rbdPhantomSecondPhaseHealthRatio",()->0.5,0.01,1);
    public static final Setting<Double> PHASE_HEAL=WorldRules.decimal("rbdPhantomSecondPhaseHealRatio",()->0.75,0.01,1);
    public static final Setting<Integer> PHASE_WARNING=WorldRules.integer("rbdPhantomAuraWarningTicks",()->60,0,Integer.MAX_VALUE);
    public static final Setting<Boolean> AURA=WorldRules.bool("rbdPhantomSlaughterAura",()->true);
    public static final Setting<Double> AURA_RANGE=WorldRules.decimal("rbdPhantomAuraRange",()->32.0,0,60000000);
    public static final Setting<Integer> AURA_TICKS=WorldRules.integer("rbdPhantomAuraIntervalTicks",()->20,1,Integer.MAX_VALUE);
    public static final Setting<List<? extends String>> BOONS=WorldRules.list("rbdPhantomSecondPhaseBoons",()->List.of("rbd:brutality","rbd:unyielding","rbd:regeneration","rbd:pursuit","rbd:vampirism"));
    public static final Setting<Double> BOON_STRENGTH=WorldRules.decimal("rbdPhantomBoonStrength",()->0.5,0,1000);
    public static final Setting<Double> RELIEF=WorldRules.decimal("rbdPhantomDefeatMiasmaRelief",()->5.0,0,1000000);
    public static final Setting<Boolean> LEARNING=WorldRules.bool("rbdPhantomAdaptiveAI",()->true);
    public static final Setting<Double> LEARNING_RATE=WorldRules.decimal("rbdPhantomLearningRate",()->1.0,0,1000000);
    public static final Setting<Integer> ATTACK_TICKS=WorldRules.integer("rbdPhantomAttackIntervalTicks",()->24,1,Integer.MAX_VALUE);
    public static final Setting<Integer> SKILL_TICKS=WorldRules.integer("rbdPhantomSkillIntervalTicks",()->100,1,Integer.MAX_VALUE);
    public static final Setting<Double> CHASE_DISTANCE=WorldRules.decimal("rbdPhantomPursuitDistance",()->48.0,4,1024);
    public static final Setting<Boolean> ITEM_USE=WorldRules.bool("rbdPhantomUseCopiedItems",()->true);
    public static final Setting<Boolean> CURIOS=WorldRules.bool("rbdPhantomUseCopiedCurios",()->true);
    public static final Setting<Integer> CHARGE_TICKS=WorldRules.integer("rbdPhantomWeaponChargeTicks",()->25,1,72000);
    public static final Setting<Double> LEARN_SPEED=WorldRules.decimal("rbdPhantomLearningSpeedPerTier",()->0.08,0,100);
    public static final Setting<Double> LEARN_ATTACK=WorldRules.decimal("rbdPhantomLearningAttackPerTier",()->0.12,0,100);
    public static final Setting<Double> REGEN=WorldRules.decimal("rbdPhantomRegenerationPerSecond",()->2.0,0,1000000);
    public static final Setting<Double> LIFESTEAL=WorldRules.decimal("rbdPhantomLifestealRatio",()->0.25,0,1000);
    public static final Setting<Boolean> PURSUIT=WorldRules.bool("rbdPhantomCanTeleport",()->true);
    public static final Setting<Boolean> TACTICAL=WorldRules.bool("rbdPhantomTacticalMovement",()->true);
    public static final Setting<Double> RANGED_MIN=WorldRules.decimal("rbdPhantomRangedMinDistance",()->6.0,0,1024);
    public static final Setting<Double> RANGED_MAX=WorldRules.decimal("rbdPhantomRangedMaxDistance",()->14.0,0,1024);
    public static final Setting<Boolean> DODGE=WorldRules.bool("rbdPhantomDodgeProjectiles",()->true);
    public static final Setting<Integer> DODGE_LOOKAHEAD=WorldRules.integer("rbdPhantomDodgeLookaheadTicks",()->12,1,100);
    public static final Setting<Integer> DODGE_COOLDOWN=WorldRules.integer("rbdPhantomDodgeCooldownTicks",()->30,1,Integer.MAX_VALUE);
    public static final Setting<Double> DODGE_DISTANCE=WorldRules.decimal("rbdPhantomDodgeDistance",()->3.0,0,128);
    public static final Setting<Double> DODGE_SPEED=WorldRules.decimal("rbdPhantomDodgeSpeed",()->1.6,0.01,100);
    public static final Setting<Integer> REACTION_TICKS=WorldRules.integer("rbdPhantomReactionTicks",()->4,1,Integer.MAX_VALUE);
    public static final Setting<Integer> PATH_TICKS=WorldRules.integer("rbdPhantomPathIntervalTicks",()->8,1,Integer.MAX_VALUE);
    public static final Setting<Double> FLANK_DISTANCE=WorldRules.decimal("rbdPhantomFlankDistance",()->2.75,0.1,128);
    public static final Setting<Integer> INTERCEPT_TICKS=WorldRules.integer("rbdPhantomInterceptTicks",()->8,0,100);
    public static final Setting<Integer> AIM_LEAD_TICKS=WorldRules.integer("rbdPhantomAimLeadTicks",()->6,0,100);
    public static void initialize(){}
    private PhantomRules(){}
}
