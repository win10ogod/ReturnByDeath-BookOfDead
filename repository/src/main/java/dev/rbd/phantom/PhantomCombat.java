package dev.rbd.phantom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Stateful movement decisions; weapons still execute their own native item behavior. */
public final class PhantomCombat {
    private final DespairPhantomEntity boss;
    private Vec3 lastTarget, targetMotion=Vec3.ZERO, lastGoal, dodgeGoal;
    private int repathDelay, senseDelay, dodgeCooldown, dodgeTicks, side=1;
    private long pathRequests, dodges;
    private String previousTactic="";

    public PhantomCombat(DespairPhantomEntity boss){this.boss=boss;}
    public long pathRequests(){return pathRequests;}
    public long dodges(){return dodges;}
    public Vec3 observedMotion(){return targetMotion;}

    public void observe(ServerPlayer target){
        Vec3 now=target.position();
        // Client-driven walking need not appear in ServerPlayer.getDeltaMovement().
        if(lastTarget!=null&&lastTarget.distanceToSqr(now)<64){
            Vec3 step=now.subtract(lastTarget);targetMotion=targetMotion.scale(0.55).add(step.scale(0.45));
        }else targetMotion=Vec3.ZERO;
        lastTarget=now;
    }

    public Vec3 aim(ServerPlayer target){
        double ticks=Math.min(PhantomRules.AIM_LEAD_TICKS.get(),boss.distanceTo(target)/3.0);
        return target.getEyePosition().add(targetMotion.scale(ticks));
    }

    /** Returns a future collision time, or -1 for outgoing, stationary or missing shots. */
    public static double impactTime(LivingEntity victim,Projectile projectile,int horizon){
        Vec3 velocity=projectile.getDeltaMovement().subtract(victim.getDeltaMovement());
        double speed=velocity.lengthSqr();if(speed<0.0001)return -1;
        Vec3 from=projectile.position(),center=victim.getBoundingBox().getCenter();
        if(center.subtract(from).dot(velocity)<=0)return -1;
        var hit=victim.getBoundingBox().inflate(0.35+projectile.getBbWidth()/2.0).clip(from,from.add(velocity.scale(horizon)));
        return hit.map(p->Math.sqrt(p.distanceToSqr(from)/speed)).orElse(-1.0);
    }

    private Vec3 horizontal(Vec3 v){return new Vec3(v.x,0,v.z);}
    private Vec3 away(ServerPlayer target){
        Vec3 v=horizontal(boss.position().subtract(target.position())).normalize();
        return v.lengthSqr()>0?v:new Vec3(1,0,0);
    }
    private boolean openStep(Vec3 point){
        Vec3 delta=point.subtract(boss.position());var level=boss.level();
        BlockPos feet=BlockPos.containing(point),floor=feet.below();
        if(!level.getFluidState(feet).isEmpty()||!level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP))return false;
        if(!level.noCollision(boss,boss.getBoundingBox().move(delta)))return false;
        Vec3 from=boss.position().add(0,boss.getBbHeight()/2.0,0),to=point.add(0,boss.getBbHeight()/2.0,0);
        return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss)).getType()==HitResult.Type.MISS;
    }
    private Vec3 openChoice(Vec3 preferred,Vec3 alternate,Vec3 fallback){
        if(openStep(preferred))return preferred;
        if(openStep(alternate)){side=-side;return alternate;}
        return fallback;
    }

    private boolean tryDodge(int tier){
        if(!PhantomRules.DODGE.get()||tier==0||dodgeCooldown>0||!boss.onGround())return false;
        double movement=boss.getAttributeValue(Attributes.MOVEMENT_SPEED),distance=PhantomRules.DODGE_DISTANCE.get();
        if(movement<=0||distance<=0)return false;
        int horizon=PhantomRules.DODGE_LOOKAHEAD.get();Projectile threat=null;double nearest=Double.MAX_VALUE;
        for(Projectile p:boss.level().getEntitiesOfClass(Projectile.class,boss.getBoundingBox().inflate(Math.max(8,horizon*3.0)))){
            if(p.getOwner()==boss||p.isRemoved())continue;
            double t=impactTime(boss,p,horizon);if(t<0||t>=nearest)continue;
            Vec3 end=p.position().add(p.getDeltaMovement().scale(t));
            if(boss.level().clip(new ClipContext(p.position(),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss)).getType()!=HitResult.Type.MISS)continue;
            threat=p;nearest=t;
        }
        if(threat==null)return false;
        Vec3 sideways=horizontal(threat.getDeltaMovement()).cross(new Vec3(0,1,0)).normalize().scale(distance*side);
        if(sideways.lengthSqr()<0.001)return false;
        Vec3 point=boss.position().add(sideways);
        if(!openStep(point)){point=boss.position().subtract(sideways);side=-side;if(!openStep(point))return false;}
        dodgeGoal=point;double speed=PhantomRules.DODGE_SPEED.get();
        dodgeTicks=Math.max(1,(int)Math.ceil(distance/(movement*speed)));
        dodgeCooldown=PhantomRules.DODGE_COOLDOWN.get();dodges++;
        Vec3 step=point.subtract(boss.position()).normalize().scale(movement*speed);
        boss.setDeltaMovement(step.x,boss.getDeltaMovement().y,step.z);boss.hasImpulse=true;
        repathDelay=0;return true;
    }

    private void navigate(Vec3 goal,double speed,String tactic,ServerPlayer target){
        boss.getNavigation().setSpeedModifier(speed);
        if(!tactic.equals(previousTactic)){repathDelay=0;previousTactic=tactic;}
        if(boss.position().distanceToSqr(goal)<0.36){boss.getNavigation().stop();return;}
        if(repathDelay>0)return;
        if(lastGoal!=null&&lastGoal.distanceToSqr(goal)<1&&!boss.getNavigation().isDone())return;
        repathDelay=PhantomRules.PATH_TICKS.get();lastGoal=goal;pathRequests++;
        var path=boss.getNavigation().createPath(BlockPos.containing(goal),0);
        if(path!=null&&path.canReach())boss.getNavigation().moveTo(path,speed);
        else {
            // Keep pursuing through terrain when an orbit/retreat point is unreachable.
            pathRequests++;boss.getNavigation().moveTo(target,speed);
        }
    }

    public String move(ServerPlayer target,int tier,boolean rangedHabit,boolean blocks,boolean evasive){
        if(repathDelay>0)repathDelay--;if(dodgeCooldown>0)dodgeCooldown--;if(senseDelay>0)senseDelay--;
        double d=boss.distanceToSqr(target),speed=1+tier*PhantomRules.LEARN_SPEED.get();
        Vec3 destination=target.position(),out=away(target);String tactic="pursuit";
        boolean tactical=PhantomRules.TACTICAL.get();
        if(tactical&&senseDelay==0){senseDelay=PhantomRules.REACTION_TICKS.get();tryDodge(tier);}
        if(tactical&&dodgeTicks>0&&dodgeGoal!=null){
            dodgeTicks--;navigate(dodgeGoal,speed*PhantomRules.DODGE_SPEED.get(),"dodge",target);return "dodge";
        }
        if(tactical&&PhantomRules.ITEM_USE.get()&&boss.hasRangedWeapon()&&boss.hasLineOfSight(target)){
            double min=Math.min(PhantomRules.RANGED_MIN.get(),PhantomRules.RANGED_MAX.get());
            double max=Math.max(PhantomRules.RANGED_MIN.get(),PhantomRules.RANGED_MAX.get());
            Vec3 lateral=out.cross(new Vec3(0,1,0)).scale(PhantomRules.FLANK_DISTANCE.get()*side);
            if(d<min*min){
                tactic="retreat";Vec3 back=boss.position().add(out.scale(Math.max(1,min-Math.sqrt(d)+1)));
                destination=openChoice(back,back.add(lateral),target.position());
            }else if(d<=max*max){
                tactic="strafe";
                if(boss.tickCount%60==0)side=-side;
                destination=openChoice(boss.position().add(lateral),boss.position().subtract(lateral),boss.position());
            }
        }else if(blocks&&target.isBlocking()){
            tactic="flank";
            Vec3 look=horizontal(target.getLookAngle()).normalize(),lateral=look.cross(new Vec3(0,1,0)).scale(PhantomRules.FLANK_DISTANCE.get()*side);
            Vec3 point=target.position().add(out.dot(look)>0.35?lateral:look.scale(-PhantomRules.FLANK_DISTANCE.get()));
            destination=tactical?openChoice(point,target.position().subtract(lateral),target.position()):target.position().add(lateral);
        }else if(evasive){
            tactic="intercept";destination=destination.add(targetMotion.scale(PhantomRules.INTERCEPT_TICKS.get()+tier*2));
        }else if(rangedHabit&&tier>0){
            tactic="strafe";
            if(d<100&&d>16){
                Vec3 lateral=out.cross(new Vec3(0,1,0)).scale(PhantomRules.FLANK_DISTANCE.get()*side);
                destination=openChoice(boss.position().add(lateral).subtract(out),boss.position().subtract(lateral).subtract(out),target.position());
            }
        }
        boss.setSprinting(tier>0);
        double standOff=boss.getBbWidth()+target.getBbWidth()+1;
        if(tactical&&(tactic.equals("pursuit")||tactic.equals("intercept"))&&d<standOff*standOff&&boss.hasLineOfSight(target))boss.getNavigation().stop();
        else navigate(destination,speed,tactic,target);
        return tactic;
    }
}
