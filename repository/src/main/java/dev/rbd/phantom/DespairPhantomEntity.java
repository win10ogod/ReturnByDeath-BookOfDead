package dev.rbd.phantom;

import com.google.gson.JsonObject;
import dev.rbd.runtime.GameSession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.*;
import java.util.*;

public final class DespairPhantomEntity extends PathfinderMob implements Enemy,net.minecraft.world.entity.monster.RangedAttackMob {
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(DespairPhantomEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> BOON=SynchedEntityData.defineId(DespairPhantomEntity.class,EntityDataSerializers.STRING);
    private static final ResourceLocation BOON_MOD=ResourceLocation.fromNamespaceAndPath("rbd","phantom_boon");
    public static final List<String> AVAILABLE_BOONS=List.of("rbd:brutality","rbd:unyielding","rbd:regeneration","rbd:pursuit","rbd:vampirism");
    private final ServerBossEvent bar=new ServerBossEvent(Component.translatable("entity.rbd.despair_phantom"),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.PROGRESS);
    public final List<ItemStack> inventoryCopy=new ArrayList<>();
    public int selectedSlot;
    private UUID quarry;
    private String quarryName="";
    private int absentTicks,auraDelay,attackCooldown,skillCooldown,stuckTicks;
    private boolean airJumped,curiosPending;
    private boolean appliedCurios=true;
    private CompoundTag savedCurios=new CompoundTag();
    private Vec3 lastPosition=Vec3.ZERO;
    private JsonObject history=new JsonObject();
    private String tactic="pursuit";

    public DespairPhantomEntity(EntityType<? extends DespairPhantomEntity> type,Level level){
        super(type,level);xpReward=0;setPersistenceRequired();setCanPickUpLoot(false);
        for(EquipmentSlot slot:EquipmentSlot.values())setDropChance(slot,0);
        bar.setDarkenScreen(true);getNavigation().setCanFloat(true);
    }
    public static AttributeSupplier.Builder attributes(){
        var builder=Mob.createMobAttributes();
        // Optional armor/render/ability implementations expect their registered attributes on the wearer.
        for(var attr:BuiltInRegistries.ATTRIBUTE.holders().toList())builder.add(attr);
        return builder.add(Attributes.MAX_HEALTH,200).add(Attributes.MOVEMENT_SPEED,0.3).add(Attributes.ATTACK_DAMAGE,6).add(Attributes.FOLLOW_RANGE,128).add(Attributes.KNOCKBACK_RESISTANCE,0.5);
    }
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(8,new LookAtPlayerGoal(this,net.minecraft.world.entity.player.Player.class,64));}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(PHASE,1);builder.define(BOON,"");}
    public UUID quarryId(){return quarry;}
    public int phase(){return entityData.get(PHASE);}
    public String boon(){return entityData.get(BOON);}
    public String tactic(){return tactic;}
    public int learningTier(){return PhantomProfile.tier(history);}
    public int auraDelay(){return auraDelay;}
    public ServerPlayer quarry(){return quarry==null||level().isClientSide?null:((ServerLevel)level()).getServer().getPlayerList().getPlayer(quarry);}
    public void mirror(ServerPlayer player){
        quarry=player.getUUID();quarryName=player.getGameProfile().getName();
        inventoryCopy.clear();for(int i=0;i<player.getInventory().getContainerSize();i++)inventoryCopy.add(player.getInventory().getItem(i).copy());selectedSlot=player.getInventory().selected;
        for(EquipmentSlot slot:EquipmentSlot.values())setItemSlot(slot,player.getItemBySlot(slot).copy());
        savedCurios=PhantomEquipment.curiosSnapshot(player);
        appliedCurios=PhantomRules.CURIOS.get();if(appliedCurios)PhantomEquipment.restoreCurios(this,savedCurios);
        PhantomProfile.observe(player);history=PhantomProfile.of(player).deepCopy();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(PhantomRules.BASE_HEALTH.get()+player.getMaxHealth()*PhantomRules.HEALTH_SCALE.get());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(PhantomRules.BASE_DAMAGE.get()+player.getAttributeValue(Attributes.ATTACK_DAMAGE)*PhantomRules.DAMAGE_SCALE.get());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(PhantomRules.SPEED.get());
        setHealth(getMaxHealth());setTarget(player);updateBar();
    }
    @Override public void startSeenByPlayer(ServerPlayer player){super.startSeenByPlayer(player);bar.addPlayer(player);}
    @Override public void stopSeenByPlayer(ServerPlayer player){super.stopSeenByPlayer(player);bar.removePlayer(player);}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean canAttackType(EntityType<?> type){return true;}
    @Override public boolean canAttack(LivingEntity victim){return victim.getUUID().equals(quarry)&&victim.isAlive();}
    @Override public void performRangedAttack(LivingEntity victim,float strength){if(victim instanceof ServerPlayer player&&player.getUUID().equals(quarry))PhantomEquipment.useWeapon(this,player);}
    @Override public net.minecraft.world.entity.item.ItemEntity spawnAtLocation(ItemStack stack,float offset){return null;}
    @Override public void tick(){
        if(!level().isClientSide){
            if(!PhantomRules.ENABLED.get()){PhantomEncounters.depart(this,false);discard();return;}
            boolean enabled=PhantomRules.CURIOS.get();
            if(curiosPending){PhantomEquipment.restoreCurios(this,savedCurios);if(!enabled)PhantomEquipment.clearFunctionalCurios(this);appliedCurios=enabled;curiosPending=false;}
            else if(enabled!=appliedCurios){
                if(enabled)PhantomEquipment.restoreCurios(this,savedCurios);
                else{savedCurios=PhantomEquipment.curiosSnapshot(this);PhantomEquipment.clearFunctionalCurios(this);}
                appliedCurios=enabled;
            }
        }
        super.tick();
        var game=GameSession.current;
        if(!level().isClientSide&&!isRemoved()&&(game==null||(!game.transitioning&&!game.returnPending())))PhantomEquipment.tickBelt(this);
    }
    @Override protected void customServerAiStep(){
        super.customServerAiStep();
        var game=GameSession.current;if(game!=null&&(game.transitioning||game.returnPending())){getNavigation().stop();return;}
        ServerPlayer target=quarry();
        if(target==null||!target.isAlive()||target.isSpectator()){
            getNavigation().stop();if(++absentTicks>=PhantomRules.ABSENT_TICKS.get()){PhantomEncounters.depart(this,false);discard();}return;
        }
        absentTicks=0;
        if(target.level()!=level()){
            if(PhantomRules.PURSUIT.get()&&tickCount%20==0)PhantomEncounters.followDimension(this,target);
            return;
        }
        setTarget(target);
        if(tickCount%20==0){
            PhantomProfile.observe(target);PhantomProfile.learn(target,target.isBlocking()?"blocking":target.getDeltaMovement().horizontalDistanceSqr()>0.02?"evasion":"melee",0.1);
            history=PhantomProfile.of(target).deepCopy();PhantomEncounters.remember(this);
            if(phase()==2&&boon().equals("rbd:regeneration"))heal((float)(PhantomRules.REGEN.get()*(1+PhantomRules.BOON_STRENGTH.get())));
        }
        if(phase()==1&&getHealth()<=getMaxHealth()*PhantomRules.PHASE_THRESHOLD.get())enterSecondPhase();
        if(phase()==2){
            if(auraDelay>0)auraDelay--;
            else if(tickCount%PhantomRules.AURA_TICKS.get()==0)pulseAura();
            if(tickCount%10==0)showAura();
        }
        updateBar();fight(target);
    }
    private void fight(ServerPlayer target){
        int tier=learningTier();double d=distanceToSqr(target);getLookControl().setLookAt(target,360,360);
        if(attackCooldown>0)attackCooldown--;if(skillCooldown>0)skillCooldown--;
        if(onGround())airJumped=false;
        double speed=1+tier*PhantomRules.LEARN_SPEED.get();
        boolean ranged=PhantomProfile.prefers(history,"ranged","melee");
        boolean blocks=PhantomProfile.number(history,"blocking")>0&&tier>0;
        boolean evasive=PhantomProfile.prefers(history,"evasion","melee")&&tier>1;
        tactic=blocks&&target.isBlocking()?"flank":ranged&&tier>0?"strafe":evasive?"intercept":"pursuit";
        Vec3 destination=target.position();
        if(tactic.equals("intercept"))destination=destination.add(target.getDeltaMovement().scale(8+tier*2));
        else if(tactic.equals("flank")){Vec3 side=target.getLookAngle().cross(new Vec3(0,1,0)).normalize().scale(2);destination=destination.add(side);}
        else if(tactic.equals("strafe")&&d<100&&d>16){Vec3 side=target.position().subtract(position()).cross(new Vec3(0,1,0)).normalize().scale((tickCount/40%2==0?1:-1)*3);destination=position().add(side).add(target.position().subtract(position()).normalize());}
        setSprinting(tier>0);getNavigation().moveTo(destination.x,destination.y,destination.z,speed);
        if(horizontalCollision&&onGround())getJumpControl().jump();
        if(tier>=2&&!onGround()&&!airJumped&&horizontalCollision&&getDeltaMovement().y<0.15){airJumped=PhantomEquipment.doubleJump(this);}
        if(tickCount%20==0){
            if(position().distanceToSqr(lastPosition)<0.25&&d>16)stuckTicks+=20;else stuckTicks=0;lastPosition=position();
            if(PhantomRules.PURSUIT.get()&&(d>PhantomRules.CHASE_DISTANCE.get()*PhantomRules.CHASE_DISTANCE.get()||stuckTicks>PhantomRules.SKILL_TICKS.get()*2)){
                PhantomEncounters.relocateNear(this,target,Math.min(PhantomRules.SPAWN_DISTANCE.get(),8));stuckTicks=0;
            }
        }
        if(d<=Math.pow(getBbWidth()+target.getBbWidth()+1.4,2)&&hasLineOfSight(target)&&attackCooldown==0){
            swing(InteractionHand.MAIN_HAND);float before=target.getHealth();doHurtTarget(target);
            if(phase()==2&&boon().equals("rbd:vampirism"))heal((float)(Math.max(0,before-target.getHealth())*PhantomRules.LIFESTEAL.get()*(1+PhantomRules.BOON_STRENGTH.get())));
            if(blocks&&target.isBlocking())target.disableShield();
            attackCooldown=Math.max(1,(int)(PhantomRules.ATTACK_TICKS.get()/(1+tier*PhantomRules.LEARN_ATTACK.get())));
        }
        if(skillCooldown==0&&hasLineOfSight(target)&&d>9){PhantomEquipment.useWeapon(this,target);skillCooldown=PhantomRules.SKILL_TICKS.get();}
    }
    public void enterSecondPhase(){
        if(phase()!=1||level().isClientSide)return;
        entityData.set(PHASE,2);auraDelay=PhantomRules.PHASE_WARNING.get();
        List<String> pool=PhantomRules.BOONS.get().stream().filter(AVAILABLE_BOONS::contains).map(String::valueOf).toList();
        if(!pool.isEmpty())entityData.set(BOON,pool.get(random.nextInt(pool.size())));
        applyBoon();setHealth((float)(getMaxHealth()*PhantomRules.PHASE_HEAL.get()));
        playSound(SoundEvents.WITHER_SPAWN,1,0.7f);
        for(ServerPlayer p:((ServerLevel)level()).players())if(distanceToSqr(p)<=Math.pow(Math.max(64,PhantomRules.AURA_RANGE.get()+16),2))
            p.sendSystemMessage(Component.translatable("message.rbd.phantom.phase_two",quarryName,PhantomRules.AURA_RANGE.get(),auraDelay/20.0));
        updateBar();
    }
    private void applyBoon(){
        for(var attribute:List.of(Attributes.ATTACK_DAMAGE,Attributes.MOVEMENT_SPEED,Attributes.MAX_HEALTH))getAttribute(attribute).removeModifier(BOON_MOD);
        var attr=switch(boon()){case "rbd:brutality"->Attributes.ATTACK_DAMAGE;case "rbd:unyielding"->Attributes.MAX_HEALTH;case "rbd:pursuit"->Attributes.MOVEMENT_SPEED;default->null;};
        if(attr!=null)getAttribute(attr).addTransientModifier(new AttributeModifier(BOON_MOD,PhantomRules.BOON_STRENGTH.get(),AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    public int pulseAura(){
        if(level().isClientSide||phase()!=2||!PhantomRules.AURA.get()||quarry==null||auraDelay>0)return 0;
        double range=PhantomRules.AURA_RANGE.get(),squared=range*range;int count=0;
        // Iterate loaded entities once: this also supports arbitrarily configured radii without a huge chunk-grid scan.
        for(Entity entity:com.google.common.collect.Lists.newArrayList(((ServerLevel)level()).getAllEntities()))if(entity instanceof LivingEntity living&&living!=this&&!living.getUUID().equals(quarry)&&!living.isRemoved()&&living.isAlive()&&distanceToSqr(living)<=squared){PhantomExecution.kill(living);count++;}
        return count;
    }
    private void showAura(){
        if(!PhantomRules.AURA.get())return;ServerLevel level=(ServerLevel)level();double r=PhantomRules.AURA_RANGE.get();
        for(ServerPlayer observer:level.players())if(distanceToSqr(observer)<=Math.pow(r+64,2))
            for(int i=0;i<64;i++){double angle=i*Math.PI/32;level.sendParticles(observer,auraDelay>0?ParticleTypes.SOUL:ParticleTypes.SCULK_SOUL,true,getX()+Math.cos(angle)*r,getY()+0.2,getZ()+Math.sin(angle)*r,1,0,0.2,0,0);}
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,getX(),getY()+1,getZ(),6,0.4,0.6,0.4,0.01);
    }
    private void updateBar(){
        Component name=Component.translatable("entity.rbd.despair_phantom").append(Component.translatable("message.rbd.phantom.bar",quarryName,phase()));
        if(!boon().isEmpty())name=name.copy().append(" · ").append(Component.translatable("boon.rbd."+boon().substring(4)));
        bar.setName(name);bar.setColor(phase()==2?BossEvent.BossBarColor.RED:BossEvent.BossBarColor.PURPLE);bar.setProgress(Math.max(0,Math.min(1,getHealth()/getMaxHealth())));
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(source.getEntity() instanceof ServerPlayer player&&player.getUUID().equals(quarry))PhantomProfile.learn(player,source.getDirectEntity()!=player?"ranged":"melee",1);
        boolean result=super.hurt(source,amount);
        if(!level().isClientSide&&isAlive()&&phase()==1&&getHealth()<=getMaxHealth()*PhantomRules.PHASE_THRESHOLD.get())enterSecondPhase();return result;
    }
    @Override public void die(DamageSource source){
        if(!level().isClientSide&&phase()==1&&!PhantomExecution.executing(this)){enterSecondPhase();return;}
        super.die(source);if(!level().isClientSide&&dead){PhantomEncounters.depart(this,true);bar.removeAllPlayers();}
    }
    @Override protected void dropAllDeathLoot(ServerLevel level,DamageSource source){} // Mirrored possessions never become duplicate loot.
    @Override public void remove(RemovalReason reason){bar.removeAllPlayers();super.remove(reason);}
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);if(quarry!=null)tag.putUUID("Quarry",quarry);tag.putString("QuarryName",quarryName);
        tag.putInt("Phase",phase());tag.putString("Boon",boon());tag.putInt("AuraDelay",auraDelay);tag.putInt("AbsentTicks",absentTicks);tag.putInt("Selected",selectedSlot);
        ListTag inventory=new ListTag();for(ItemStack stack:inventoryCopy)inventory.add(stack.saveOptional(registryAccess()));tag.put("MirrorInventory",inventory);
        if(!curiosPending&&appliedCurios)savedCurios=PhantomEquipment.curiosSnapshot(this);
        tag.put("MirrorCurios",savedCurios.copy());tag.putString("Learned",history.toString());tag.putInt("AttackCooldown",attackCooldown);tag.putInt("SkillCooldown",skillCooldown);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);quarry=tag.hasUUID("Quarry")?tag.getUUID("Quarry"):null;quarryName=tag.getString("QuarryName");
        entityData.set(PHASE,Math.max(1,Math.min(2,tag.getInt("Phase"))));entityData.set(BOON,AVAILABLE_BOONS.contains(tag.getString("Boon"))?tag.getString("Boon"):"");
        auraDelay=Math.max(0,tag.getInt("AuraDelay"));absentTicks=Math.max(0,tag.getInt("AbsentTicks"));selectedSlot=Math.max(0,Math.min(8,tag.getInt("Selected")));
        inventoryCopy.clear();for(Tag item:tag.getList("MirrorInventory",Tag.TAG_COMPOUND))inventoryCopy.add(ItemStack.parseOptional(registryAccess(),(CompoundTag)item));
        savedCurios=tag.getCompound("MirrorCurios").copy();curiosPending=true;
        if(tag.contains("Learned"))history=com.google.gson.JsonParser.parseString(tag.getString("Learned")).getAsJsonObject();
        attackCooldown=Math.max(0,tag.getInt("AttackCooldown"));skillCooldown=Math.max(0,tag.getInt("SkillCooldown"));applyBoon();updateBar();
    }
}
