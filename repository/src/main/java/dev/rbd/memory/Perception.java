package dev.rbd.memory;
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
public final class Perception {
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
        double fov=RbdConfig.RASTER_FOV.get();int sky=level.isDay()?0x829ABD:0x151829;
        FrameWorld view=new FrameWorld(level);
        FrameRays rays=new FrameRays(eye,subject,view);
        List<LivingEntity> entities=level.getEntitiesOfClass(LivingEntity.class,subject.getBoundingBox().inflate(distance),e->e!=subject&&!e.isInvisible());
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            Vec3 direction=forward.add(right.scale((2.0*(x+0.5)/width-1)*fov*width/height)).add(up.scale((1-2.0*(y+0.5)/height)*fov)).normalize();
            Vec3 end=eye.add(direction.scale(distance));
            rays.end=end;var hit=view.clip(rays);
            double nearest=hit.getType()==HitResult.Type.MISS?distance:eye.distanceTo(hit.getLocation());
            int color=sky;
            if(hit.getType()!=HitResult.Type.MISS){
                var pos=hit.getBlockPos();var cell=view.cell(pos);
                color=cell.color(level,pos);
                float light=0.25F+0.75F*cell.brightness(level,pos,hit.getDirection())/15F;
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
    /** Cache shape work only within one image; the real level and collision context still reach mod blocks. */
    private static final class FrameRays extends ClipContext {
        Vec3 end;
        private final FrameWorld view;
        FrameRays(Vec3 eye,Entity actor,FrameWorld view){super(eye,eye,Block.VISUAL,Fluid.ANY,actor);end=eye;this.view=view;}
        @Override public Vec3 getTo(){return end;}
        @Override public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos){
            var cell=view.cell(pos);
            if(cell.blockShape==null)cell.blockShape=super.getBlockShape(state,view.level,pos);
            return cell.blockShape;
        }
        @Override public net.minecraft.world.phys.shapes.VoxelShape getFluidShape(net.minecraft.world.level.material.FluidState state,net.minecraft.world.level.BlockGetter level,BlockPos pos){
            var cell=view.cell(pos);
            if(cell.fluidShape==null)cell.fluidShape=super.getFluidShape(state,view.level,pos);
            return cell.fluidShape;
        }
    }
    /** Each raster owns this view. Nothing is reused after blocks, light or the observer can change. */
    private static final class FrameWorld implements net.minecraft.world.level.BlockGetter {
        private static final ClassValue<Boolean> CUSTOM_CLIP=new ClassValue<>(){
            @Override protected Boolean computeValue(Class<?> type){
                try{return type.getMethod("clip",ClipContext.class).getDeclaringClass()!=net.minecraft.world.level.BlockGetter.class;}
                catch(ReflectiveOperationException e){return true;}
            }
        };
        private static final ClassValue<Boolean> CUSTOM_INTERACTION=new ClassValue<>(){
            @Override protected Boolean computeValue(Class<?> type){
                try{return type.getMethod("clipWithInteractionOverride",Vec3.class,Vec3.class,BlockPos.class,net.minecraft.world.phys.shapes.VoxelShape.class,net.minecraft.world.level.block.state.BlockState.class).getDeclaringClass()!=net.minecraft.world.level.BlockGetter.class;}
                catch(ReflectiveOperationException e){return true;}
            }
        };
        final ServerLevel level;
        final boolean customClip,customInteraction;
        final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Cell[]> sections=new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        long lastKey,lastSectionKey;Cell last;Cell[] lastSection;
        FrameWorld(ServerLevel level){this.level=level;customClip=CUSTOM_CLIP.get(level.getClass());customInteraction=CUSTOM_INTERACTION.get(level.getClass());}
        Cell cell(BlockPos pos){
            long key=pos.asLong();if(last!=null&&key==lastKey)return last;
            long sectionKey=net.minecraft.core.SectionPos.asLong(pos.getX()>>4,pos.getY()>>4,pos.getZ()>>4);
            Cell[] section=lastSection;
            if(section==null||sectionKey!=lastSectionKey){
                section=sections.get(sectionKey);
                if(section==null){section=new Cell[4096];sections.put(sectionKey,section);}
                lastSectionKey=sectionKey;lastSection=section;
            }
            int index=((pos.getY()&15)<<8)|((pos.getZ()&15)<<4)|(pos.getX()&15);
            Cell cell=section[index];
            if(cell==null){cell=new Cell(level.getBlockState(pos),level.getFluidState(pos));section[index]=cell;}
            lastKey=key;return last=cell;
        }
        @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos pos){return cell(pos).state;}
        @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos){return cell(pos).fluid;}
        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos){return level.getBlockEntity(pos);}
        @Override public int getHeight(){return level.getHeight();}
        @Override public int getMinBuildHeight(){return level.getMinBuildHeight();}
        @Override public BlockHitResult clip(ClipContext context){
            // Preserve a custom world's ray-tracing override instead of substituting the default algorithm.
            if(customClip)return level.clip(context);
            // Use Minecraft's exact traversal and tie-breaking, but resolve each visited cell once.
            return net.minecraft.world.level.BlockGetter.traverseBlocks(context.getFrom(),context.getTo(),context,this::hit,FrameWorld::miss);
        }
        private BlockHitResult hit(ClipContext context,BlockPos pos){
            var cell=cell(pos);
            if(cell.shapeMask==0){
                if(cell.blockShape==null)cell.blockShape=context.getBlockShape(cell.state,level,pos);
                if(cell.fluidShape==null)cell.fluidShape=context.getFluidShape(cell.fluid,level,pos);
                cell.shapeMask=1|(cell.blockShape.isEmpty()?0:2)|(cell.fluidShape.isEmpty()?0:4);
            }
            if(cell.shapeMask==1&&!customInteraction)return null;
            Vec3 from=context.getFrom(),to=context.getTo();
            var block=(cell.shapeMask&2)==0&&!customInteraction?null:level.clipWithInteractionOverride(from,to,pos,cell.blockShape,cell.state);
            var fluid=(cell.shapeMask&4)==0?null:cell.fluidShape.clip(from,to,pos);
            double blockDistance=block==null?Double.MAX_VALUE:from.distanceToSqr(block.getLocation());
            double fluidDistance=fluid==null?Double.MAX_VALUE:from.distanceToSqr(fluid.getLocation());
            return blockDistance<=fluidDistance?block:fluid;
        }
        private static BlockHitResult miss(ClipContext context){
            Vec3 direction=context.getFrom().subtract(context.getTo());
            return BlockHitResult.miss(context.getTo(),net.minecraft.core.Direction.getNearest(direction.x,direction.y,direction.z),BlockPos.containing(context.getTo()));
        }
        @Override public BlockHitResult clipWithInteractionOverride(Vec3 from,Vec3 to,BlockPos pos,net.minecraft.world.phys.shapes.VoxelShape shape,net.minecraft.world.level.block.state.BlockState state){
            return level.clipWithInteractionOverride(from,to,pos,shape,state);
        }
    }
    private static final class Cell {
        final net.minecraft.world.level.block.state.BlockState state;
        final net.minecraft.world.level.material.FluidState fluid;
        net.minecraft.world.phys.shapes.VoxelShape blockShape,fluidShape;
        int mapColor,shapeMask;boolean colored;int[] light;
        Cell(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.material.FluidState fluid){this.state=state;this.fluid=fluid;}
        int color(ServerLevel level,BlockPos pos){if(!colored){mapColor=state.getMapColor(level,pos).col;if(mapColor==0)mapColor=0x655F6B;colored=true;}return mapColor;}
        int brightness(ServerLevel level,BlockPos pos,net.minecraft.core.Direction face){
            if(light==null){light=new int[6];Arrays.fill(light,-1);}
            int index=face.get3DDataValue();if(light[index]<0)light[index]=level.getMaxLocalRawBrightness(pos.relative(face));return light[index];
        }
    }
    private static int shade(int rgb,double factor){return ((int)(((rgb>>16)&255)*factor)<<16)|((int)(((rgb>>8)&255)*factor)<<8)|(int)((rgb&255)*factor);}
    private Perception(){}
}
