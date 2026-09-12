package dev.rbd.testing;
import dev.rbd.memory.MemoryFrame;
import dev.rbd.RbdConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.*;
import java.util.*;

/** NPC perception uses first-hit rays. Occluded entities and container contents never enter a frame. */
final class PerceptionReference {
    public static boolean recorded(LivingEntity e){return (dev.rbd.runtime.GameSession.current!=null&&dev.rbd.runtime.GameSession.current.isHolder(e.getUUID()))||(e instanceof Player?RbdConfig.RECORD_PLAYERS.get():e instanceof Villager?RbdConfig.RECORD_VILLAGERS.get():e.hasCustomName()?RbdConfig.RECORD_NAMED.get():RbdConfig.RECORD_CREATURES.get());}
    public static String name(LivingEntity e){
        if(e instanceof Player||e.hasCustomName())return e.getName().getString();
        return e.getName().getString()+" · "+e.getUUID().toString().substring(0,4);
    }
    public static List<MemoryFrame.Contact> contacts(LivingEntity subject){
        Vec3 eye=subject.getEyePosition(),look=subject.getLookAngle();List<MemoryFrame.Contact> out=new ArrayList<>();
        for(LivingEntity other:subject.level().getEntitiesOfClass(LivingEntity.class,subject.getBoundingBox().inflate(RbdConfig.CONTACT_RANGE.get()),e->e!=subject&&(e instanceof Player||e.hasCustomName())&&!e.isInvisible())){
            Vec3 delta=other.getEyePosition().subtract(eye);
            if(delta.normalize().dot(look)>RbdConfig.CONTACT_DOT.get()&&subject.hasLineOfSight(other))out.add(new MemoryFrame.Contact(other.getUUID(),name(other),other.getType().toString()));
        }return out;
    }
    public static int[] raster(LivingEntity subject,int width,int height){
        ServerLevel level=(ServerLevel)subject.level();Vec3 eye=subject.getEyePosition(),forward=subject.getLookAngle();
        Vec3 right=forward.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<0.01)right=new Vec3(1,0,0);
        Vec3 up=right.cross(forward).normalize();int distance=RbdConfig.PERCEPTION_DISTANCE.get();int[] image=new int[width*height];
        List<LivingEntity> entities=level.getEntitiesOfClass(LivingEntity.class,subject.getBoundingBox().inflate(distance),e->e!=subject&&!e.isInvisible());
        var cache=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            Vec3 direction=forward.add(right.scale((2.0*(x+0.5)/width-1)*RbdConfig.RASTER_FOV.get()*width/height)).add(up.scale((1-2.0*(y+0.5)/height)*RbdConfig.RASTER_FOV.get())).normalize();
            Vec3 end=eye.add(direction.scale(distance));
            var hit=level.clip(new ClipContext(eye,end,ClipContext.Block.VISUAL,ClipContext.Fluid.ANY,subject));
            double nearest=hit.getType()==HitResult.Type.MISS?distance:eye.distanceTo(hit.getLocation());
            int color=level.isDay()?0x829ABD:0x151829;
            if(hit.getType()!=HitResult.Type.MISS){
                var pos=hit.getBlockPos();var state=cache.computeIfAbsent(pos,level::getBlockState);
                color=state.getMapColor(level,pos).col;if(color==0)color=0x655F6B;
                float light=0.25F+0.75F*level.getMaxLocalRawBrightness(pos.relative(hit.getDirection()))/15F;
                double shade=light*(0.5+0.5*Math.max(0,hit.getDirection().getStepY()))*(1-0.35*nearest/distance);
                color=shade(color,shade);
            }
            for(LivingEntity entity:entities){
                var p=entity.getBoundingBox().clip(eye,end);
                if(p.isPresent()&&eye.distanceTo(p.get())<nearest){nearest=eye.distanceTo(p.get());color=entity instanceof Player?0x8270AA:entity instanceof Villager?0x947153:0x69765A;}
            }
            image[y*width+x]=0xFF000000|color;
        }return image;
    }
    private static int shade(int rgb,double factor){return ((int)(((rgb>>16)&255)*factor)<<16)|((int)(((rgb>>8)&255)*factor)<<8)|(int)((rgb&255)*factor);}
    private PerceptionReference(){}
}
