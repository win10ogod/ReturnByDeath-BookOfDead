package dev.rbd.testing;

import dev.rbd.*;
import dev.rbd.memory.Perception;
import dev.rbd.runtime.GameSession;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("rbd") @PrefixGameTestTemplate(false)
public final class PerformanceGameTests {
    @GameTest(template="empty",batch="performance",timeoutTicks=100)
    public static void rasterCachePreservesEveryPixel(GameTestHelper h){
        var observer=h.spawnWithNoFreeWill(EntityType.VILLAGER,2,2,1);
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)h.setBlock(x,0,z,Blocks.STONE);
        h.setBlock(1,2,4,Blocks.OAK_FENCE);h.setBlock(2,2,5,Blocks.GLASS);h.setBlock(3,2,4,Blocks.WATER);h.setBlock(4,1,4,Blocks.STONE_SLAB);
        var contact=h.spawnWithNoFreeWill(EntityType.VILLAGER,2,1,6);contact.setCustomName(net.minecraft.network.chat.Component.literal("Visible actor"));
        for(float yaw:new float[]{0,35,90,180,270})for(float pitch:new float[]{-65,0,30,90}){
            observer.setYRot(yaw);observer.setXRot(pitch);
            h.assertTrue(Arrays.equals(PerceptionReference.raster(observer,96,54),Perception.raster(observer,96,54)),"all 5,184 pixels agree at yaw/pitch "+yaw+"/"+pitch);
        }
        observer.setYRot(0);observer.setXRot(15);
        for(int i=0;i<8;i++){PerceptionReference.raster(observer,96,54);Perception.raster(observer,96,54);}
        long reference=0,optimized=0;
        for(int i=0;i<30;i++){
            long start=System.nanoTime();PerceptionReference.raster(observer,96,54);reference+=System.nanoTime()-start;
            start=System.nanoTime();Perception.raster(observer,96,54);optimized+=System.nanoTime()-start;
        }
        org.slf4j.LoggerFactory.getLogger("rbd").info("RBD raster benchmark: 30 images, reference={} ns, optimized={} ns; 103680 exact pixels verified",reference,optimized);
        h.succeed();
    }
    @GameTest(template="empty",batch="performance",timeoutTicks=100)
    public static void rasterRefreshesBlocksLightAndActors(GameTestHelper h){
        var observer=h.spawnWithNoFreeWill(EntityType.VILLAGER,2,2,1);
        var other=h.spawnWithNoFreeWill(EntityType.VILLAGER,2,2,4);
        var random=new Random(711);
        var blocks=new net.minecraft.world.level.block.Block[]{Blocks.STONE,Blocks.AIR,Blocks.WATER,Blocks.LAVA,Blocks.OAK_FENCE,Blocks.OAK_STAIRS,Blocks.GLASS,Blocks.TORCH,Blocks.STONE_SLAB};
        for(int round=0;round<32;round++){
            for(int i=0;i<16;i++)h.setBlock(random.nextInt(8),random.nextInt(5),random.nextInt(8),blocks[random.nextInt(blocks.length)]);
            observer.setYRot(random.nextFloat()*360);observer.setXRot(random.nextFloat()*180-90);
            other.setInvisible(round%2==0);other.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(random.nextInt(8),2,random.nextInt(8))));
            int width=round%2==0?96:127,height=round%2==0?54:71;
            h.getLevel().setDayTime(round%2==0?1000:18000);
            h.assertTrue(Arrays.equals(PerceptionReference.raster(observer,width,height),Perception.raster(observer,width,height)),"fresh raster matches every pixel after block/fluid/light/actor changes, round "+round);
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="performance",timeoutTicks=100)
    public static void autoCheckpointRejectsDangerAndPersistsRules(GameTestHelper h) throws Exception {
        var g=GameSession.current;
        var player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AutoSaveTest"));
        g.authorities.bind(player.getUUID(),"AutoSaveTest",0);
        try{
            player.setOnGround(true);player.setHealth(player.getMaxHealth());
            h.assertTrue(g.safeForCheckpoint(player),"healthy grounded holder is eligible");
            player.setOnGround(false);h.assertTrue(!g.safeForCheckpoint(player),"falling postpones save");player.setOnGround(true);
            player.setHealth(1);h.assertTrue(!g.safeForCheckpoint(player),"low health postpones save");player.setHealth(player.getMaxHealth());
            player.setRemainingFireTicks(40);h.assertTrue(!g.safeForCheckpoint(player),"burning postpones save");player.clearFire();
            g.soul(player.getUUID()).addProperty("connection","LOST");h.assertTrue(!g.safeForCheckpoint(player),"lost authority connection postpones save");g.soul(player.getUUID()).remove("connection");
            g.branch.object("despairPhantoms").addProperty(player.getUUID().toString(),true);h.assertTrue(!g.safeForCheckpoint(player),"active phantom hunt postpones save");g.branch.object("despairPhantoms").remove(player.getUUID().toString());
            var rules=new net.minecraft.world.level.GameRules();var changes=new com.google.gson.JsonObject();changes.addProperty("rbdAutoCheckpoint","false");changes.addProperty("rbdAutoCheckpointIntervalTicks","2147483647");changes.addProperty("rbdAutoCheckpointMinHealthPercent","82.125");
            dev.rbd.rules.WorldRules.apply(rules,changes);
            var loaded=new net.minecraft.world.level.GameRules(new com.mojang.serialization.Dynamic<>(net.minecraft.nbt.NbtOps.INSTANCE,rules.createTag()));
            h.assertTrue(!RbdConfig.AUTO_CHECKPOINT.get(loaded)&&RbdConfig.AUTO_CHECKPOINT_INTERVAL.get(loaded)==Integer.MAX_VALUE&&RbdConfig.AUTO_CHECKPOINT_HEALTH.get(loaded)==82.125,"save rules survive NBT roundtrip without clamping or rounding");
        }finally{g.authorities.unbind(player.getUUID());g.branch.object("despairPhantoms").remove(player.getUUID().toString());}
        h.succeed();
    }
}
