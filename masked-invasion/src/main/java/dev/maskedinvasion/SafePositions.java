package dev.maskedinvasion;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;

/** Collision checks use already loaded chunks; callers arrange asynchronous chunk preparation. */
public final class SafePositions {
    public static boolean loaded(ServerLevel level,BlockPos pos){return level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4);}
    public static boolean validRespawn(ServerLevel level,RaidData.Home home){
        if(home.forced())return true;
        if(!loaded(level,home.position()))return false;
        var state=level.getBlockState(home.position());
        return state.getBlock() instanceof net.minecraft.world.level.block.BedBlock&&level.dimensionType().bedWorks()
            ||state.is(Blocks.RESPAWN_ANCHOR)&&level.dimensionType().respawnAnchorWorks()&&state.getValue(net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE)>0;
    }
    public static boolean safe(ServerLevel level,BlockPos pos,Entity entity){
        if(pos.getY()<=level.getMinBuildHeight()||pos.getY()+2>=level.getMaxBuildHeight()||!loaded(level,pos)||!level.getWorldBorder().isWithinBounds(pos))return false;
        var below=level.getBlockState(pos.below());
        if(below.getCollisionShape(level,pos.below()).isEmpty()||!level.getFluidState(pos).isEmpty()||!level.getFluidState(pos.above()).isEmpty())return false;
        if(below.is(Blocks.MAGMA_BLOCK)||below.is(Blocks.CACTUS)||below.is(Blocks.CAMPFIRE)||below.is(Blocks.SOUL_CAMPFIRE)||level.getBlockState(pos).is(Blocks.FIRE)||level.getBlockState(pos).is(Blocks.SOUL_FIRE)||level.getBlockState(pos).is(Blocks.POWDER_SNOW))return false;
        AABB box=entity==null?new AABB(pos.getX()+0.2,pos.getY(),pos.getZ()+0.2,pos.getX()+0.8,pos.getY()+1.95,pos.getZ()+0.8):entity.getDimensions(entity.getPose()).makeBoundingBox(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);
        return level.noCollision(entity,box);
    }
    public static Optional<Vec3> home(ServerLevel level,BlockPos origin,Entity entity){
        // Search around the actual bed/anchor height first, including indoor and underground bases.
        for(int radius=0;radius<=5;radius++)for(int dy:new int[]{0,1,-1,2,-2,3,-3,4,-4})
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(radius>0&&Math.abs(dx)!=radius&&Math.abs(dz)!=radius)continue;
                var p=origin.offset(dx,dy,dz);if(safe(level,p,entity))return Optional.of(Vec3.atBottomCenterOf(p));
            }
        return Optional.empty();
    }
    public static Optional<Vec3> spawn(ServerLevel level,RaidData.Raid raid,int min,int max,Random random){
        // Fixed work budget per call, not an area-wide block scan. Failed attempts resume next tick slice.
        int outer=Math.max(1,Math.min(max,raid.radius-2)),inner=Math.min(Math.max(1,min),outer);
        for(int attempt=0;attempt<24;attempt++){
            double angle=random.nextDouble()*Math.PI*2,dist=inner+random.nextDouble()*(outer-inner);
            int x=raid.home.position().getX()+(int)Math.round(Math.cos(angle)*dist),z=raid.home.position().getZ()+(int)Math.round(Math.sin(angle)*dist);
            for(int dy:new int[]{0,1,-1,2,-2,3,-3,4,-4,6,-6,8,-8}){
                var p=new BlockPos(x,raid.home.position().getY()+dy,z);
                if(raid.contains(level.dimension(),x+0.5,p.getY(),z+0.5)&&safe(level,p,null))return Optional.of(Vec3.atBottomCenterOf(p));
            }
        }
        return Optional.empty();
    }
    private SafePositions(){}
}
