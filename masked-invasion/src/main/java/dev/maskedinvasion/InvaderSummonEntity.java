package dev.maskedinvasion;

import com.example.generichenshin.service.MobCombatAnimationService;
import com.kelco.kamenridercraft.abilities.AbilityUtil;
import com.kelco.kamenridercraft.attachments.AttachmentTypes;
import com.kelco.kamenridercraft.entity.mobs.foot_soldiers.EnemySummonEntity;
import com.kelco.kamenridercraft.entity.mobs.summons.BaseSummonEntity;
import dev.maskedinvasion.mixin.KickCleanupAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.UUID;

/** Native KRC enemy, so belts, form effects, weapons and Generic Henshin animations retain their own behavior. */
public final class InvaderSummonEntity extends EnemySummonEntity {
    private UUID raidId;
    private String formId="";
    private int wave=1,skillDelay;
    public InvaderSummonEntity(EntityType<? extends InvaderSummonEntity> type,Level level){
        super(type,level);setPersistenceRequired();setCanPickUpLoot(false);getNavigation().setCanFloat(true);xpReward=5;
        for(var slot:EquipmentSlot.values())setDropChance(slot,0);
    }
    public static AttributeSupplier.Builder attributes(){
        var builder=Mob.createMobAttributes();for(var attribute:BuiltInRegistries.ATTRIBUTE.holders().toList())builder.add(attribute);
        return builder.add(Attributes.MAX_HEALTH,40).add(Attributes.ATTACK_DAMAGE,3).add(Attributes.MOVEMENT_SPEED,0.29).add(Attributes.FOLLOW_RANGE,64).add(Attributes.KNOCKBACK_RESISTANCE,0.2);
    }
    @Override protected void addBehaviourGoals(){
        goalSelector.addGoal(7,new WaterAvoidingRandomStrollGoal(this,0.8));
        targetSelector.addGoal(1,new HurtByTargetGoal(this,InvaderSummonEntity.class));
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,10,true,false,p->canAttack(p)));
        targetSelector.addGoal(3,new NearestAttackableTargetGoal<>(this,AbstractVillager.class,10,true,false,v->canAttack(v)));
        targetSelector.addGoal(3,new NearestAttackableTargetGoal<>(this,IronGolem.class,true));
        targetSelector.addGoal(3,new NearestAttackableTargetGoal<>(this,BaseSummonEntity.class,true));
    }
    public UUID raidId(){return raidId;}
    public String formId(){return formId;}
    public int wave(){return wave;}
    public void equip(UUID raidId,int wave,FormCatalog.Form form){
        this.raidId=raidId;this.wave=wave;formId=form.id();
        var rules=level().getGameRules();var belt=form.belt();
        setItemSlot(EquipmentSlot.FEET,form.beltStack());
        setItemSlot(EquipmentSlot.HEAD,new ItemStack(belt.helmet));setItemSlot(EquipmentSlot.CHEST,new ItemStack(belt.chestplate));setItemSlot(EquipmentSlot.LEGS,new ItemStack(belt.leggings));
        setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(form.weapon()));
        for(var slot:EquipmentSlot.values())setDropChance(slot,InvasionRules.EQUIPMENT_DROPS.get(rules)?1:0);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(InvasionRules.HEALTH.get(rules)*form.health()*WavePlan.growth(wave,InvasionRules.HEALTH_GROWTH.get(rules)));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(InvasionRules.DAMAGE.get(rules)*form.damage()*WavePlan.growth(wave,InvasionRules.DAMAGE_GROWTH.get(rules)));
        getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.ABILITY_METER).setBaseValue(getAttributeValue(com.kelco.kamenridercraft.world.attribute.Attributes.MAX_ABILITY_METER));
        getAttribute(com.kelco.kamenridercraft.world.attribute.Attributes.IS_TRANSFORMING).setBaseValue(0);
        setHealth(getMaxHealth());skillDelay=InvasionRules.SKILL_TICKS.get(rules);
        setCustomName(Component.translatable(form.tier().ordinal()>=FormCatalog.Tier.SUPER.ordinal()?"entity.masked_invasion.general":"entity.masked_invasion.soldier"));
    }
    @Override public boolean canAttack(LivingEntity other){
        if(other instanceof InvaderSummonEntity||!other.isAlive()||other.isSpectator())return false;
        if(other instanceof Player player&&player.isCreative())return false;
        if(level() instanceof ServerLevel server&&raidId!=null){
            var raid=RaidData.get(server.getServer()).raids.get(raidId);
            if(raid==null||raid.phase!=RaidData.Phase.ACTIVE||raid.suspended||!raid.contains(other.level().dimension(),other.getX(),other.getY(),other.getZ()))return false;
            if(other instanceof Player&&!raid.defenders.containsKey(other.getUUID()))return false;
        }
        return super.canAttack(other);
    }
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override protected boolean shouldDespawnInPeaceful(){return false;}
    @Override public boolean canBreakDoors(){return false;}
    @Override public boolean canBeLeashed(){return false;}
    @Override public void tick(){
        if(level() instanceof ServerLevel server&&raidId!=null&&tickCount%20==0){var raid=RaidData.get(server.getServer()).raids.get(raidId);if(raid!=null){setNoAi(raid.suspended);if(raid.suspended){setTarget(null);getNavigation().stop();}}}
        super.tick();
        if(!(level() instanceof ServerLevel level)||isRemoved()||!isAlive()||Invasions.returning(level.getServer()))return;
        if(raidId!=null&&tickCount%20==0){
            var raid=RaidData.get(level.getServer()).raids.get(raidId);
            if(raid==null){discard();return;}
            if(!raid.contains(level.dimension(),getX(),getY(),getZ()))Invasions.returnInvader(this,raid);
        }
        if(skillDelay>0)skillDelay--;
        var target=getTarget();
        if(InvasionRules.SKILLS.get(level.getGameRules())&&skillDelay==0&&target!=null&&canAttack(target)&&hasLineOfSight(target)
                &&getData(AttachmentTypes.USED_ABILITY).isEmpty()&&getData(AttachmentTypes.ABILITY_COOLDOWN)==0){
            double distance=distanceToSqr(target);
            if(distance<=100){AbilityUtil.calculateAbility(this,distance>9?"rider_kick":"rider_punch");skillDelay=InvasionRules.SKILL_TICKS.get(level.getGameRules());}
        }
    }
    @Override public void die(DamageSource source){
        super.die(source);
        if(!level().isClientSide&&raidId!=null&&isDeadOrDying())Invasions.defeated((ServerLevel)level(),raidId,getUUID());
    }
    @Override public void remove(RemovalReason reason){
        if(!level().isClientSide){KickCleanupAccess.maskedInvasion$clear(getUUID());MobCombatAnimationService.clear(getUUID());}
        super.remove(reason);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);if(raidId!=null)tag.putUUID("InvasionId",raidId);tag.putString("InvasionForm",formId);tag.putInt("InvasionWave",wave);tag.putInt("InvasionSkillDelay",skillDelay);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);raidId=tag.hasUUID("InvasionId")?tag.getUUID("InvasionId"):null;formId=tag.getString("InvasionForm");wave=tag.getInt("InvasionWave");skillDelay=tag.getInt("InvasionSkillDelay");setPersistenceRequired();
    }
}
