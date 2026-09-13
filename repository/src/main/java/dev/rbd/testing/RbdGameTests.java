package dev.rbd.testing;
import dev.rbd.*;
import dev.rbd.memory.*;
import dev.rbd.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("rbd")
@PrefixGameTestTemplate(false)
public final class RbdGameTests {
    @GameTest(template="empty",batch="cadence",timeoutTicks=100)
    public static void experiencedMemorySamplesRelativeToPresentationAndKeepsEnding(GameTestHelper helper)throws Exception{
        var player=new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ReadingCadence"));
        var game=GameSession.current;
        for(int n=0;n<10;n++)game.recorder.experienced(player,new MemoryFrame(n,"minecraft:overworld",0,0,0,0,0,20,"sample "+n,List.of(),List.of(),"TEST",1,1,new int[]{-1},""));
        var ending=new SomaticState(20,0,300,true,false,0,0,20,"drown",true);
        game.recorder.experienced(player,new MemoryFrame(10,"minecraft:overworld",0,0,0,0,0,0,"ending",List.of(),List.of(),"TEST",1,1,new int[]{-1},"",ending));
        try(var reader=game.archive.reader(game.recorder.seal(player.getUUID()))){
            var first=reader.next();var last=reader.next();
            helper.assertTrue(first!=null&&first.caption().equals("sample 0"),"first presented memory is retained even off the global sampling phase");
            helper.assertTrue(first.sampleTicks()==100,"experienced memory uses the configured recording duration");
            helper.assertTrue(last!=null&&last.body().rememberedEnding(),"ending is retained even before the next sample");
            helper.assertTrue(reader.next()==null,"rapid presentation acknowledgements do not write every frame");
        }helper.succeed();
    }
    @GameTest(template="empty",batch="cadence",timeoutTicks=260)
    public static void memorySamplingWaitsForConfiguredTicksAndPreservesDeath(GameTestHelper helper){
        var game=GameSession.current;
        helper.assertTrue(RbdConfig.RECORD_INTERVAL.get()==100,"new recording interval defaults to 100 ticks");
        var actor=helper.spawnWithNoFreeWill(EntityType.VILLAGER,2,1,2);actor.setNoGravity(true);actor.setCustomName(Component.literal("Cadence witness"));
        helper.runAfterDelay(209,()->{
            try {
                actor.hurt(helper.getLevel().damageSources().genericKill(),Float.MAX_VALUE);
                var book=game.archive.books().stream().filter(b->b.get("soul").getAsString().equals(actor.getUUID().toString())).findFirst().orElseThrow();
                int regular=0,terminal=0;
                try(var reader=game.archive.reader(book.get("head").getAsString())){
                    MemoryFrame frame;while((frame=reader.next())!=null){
                        helper.assertTrue(frame.sampleTicks()==100,"recorded timing survives serialization");
                        if(frame.body()!=null&&frame.body().terminal())terminal++;
                        else {regular++;helper.assertTrue(Math.floorMod(frame.tick(),100)==0,"no per-tick state/continuation records between samples");}
                    }
                }
                helper.assertTrue(regular>=2&&regular<=3,"209 game ticks produce only 2 or 3 regular samples");
                helper.assertTrue(terminal==1,"death outside sampling schedule is still captured once");helper.succeed();
            }catch(Exception failure){helper.fail(failure.toString());}
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void interruptedReturnRecoversBeforeWorldLock(GameTestHelper helper) throws Exception {
        var root=java.nio.file.Files.createTempDirectory("rbd-world-open-");
        try{
            var world=java.nio.file.Files.createDirectory(root.resolve("world"));var data=world.resolve("fixture.dat");java.nio.file.Files.writeString(data,"checkpoint");
            var store=new dev.rbd.io.SnapshotStore(world,dev.rbd.io.SnapshotStore.controlFor(world));store.prepare("CAPTURE",null);store.markClosed();store.complete();
            java.nio.file.Files.writeString(data,"failed branch");store.prepare("RESTORE",null);store.markClosed();
            try(var access=net.minecraft.world.level.storage.LevelStorageSource.createDefault(root).createAccess("world")){
                helper.assertTrue(java.nio.file.Files.readString(data).equals("checkpoint")&&!store.pending(),"normal world opening completes the closed transaction without a supervisor");
            }
            store.prepare("CAPTURE",null);boolean refused=false;
            try(var access=net.minecraft.world.level.storage.LevelStorageSource.createDefault(root).createAccess("world")){}catch(java.io.IOException expected){refused=true;}
            helper.assertTrue(refused,"a transaction without proof of closed writers is rejected before acquiring a world lock");helper.succeed();
        }finally{try(var files=java.nio.file.Files.walk(root)){for(var path:files.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.deleteIfExists(path);}}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void nativeWorldRulesRoundTripWithoutRounding(GameTestHelper helper){
        var rules=new net.minecraft.world.level.GameRules();
        var max=(net.minecraft.world.level.GameRules.IntegerValue)rules.getRule((net.minecraft.world.level.GameRules.Key)RbdConfig.MAX_HOLDERS.key());
        helper.assertTrue(max.tryDeserialize("2147483647"),"unlimited-range holder configuration accepts the complete integer range");
        helper.assertTrue(!max.tryDeserialize("-1"),"negative holder count rejected by the native editor");
        var dwell=(net.minecraft.world.level.GameRules.IntegerValue)rules.getRule((net.minecraft.world.level.GameRules.Key)RbdConfig.DEATH_DWELL.key());
        helper.assertTrue(dwell.tryDeserialize("0.123456789"),"fractional seconds are editable without tick rounding");
        helper.assertTrue(!dwell.tryDeserialize("NaN")&&!dwell.tryDeserialize("31"),"invalid decimal values rejected");
        var milestones=(net.minecraft.world.level.GameRules.IntegerValue)rules.getRule((net.minecraft.world.level.GameRules.Key)RbdConfig.MILESTONES.key());
        helper.assertTrue(milestones.tryDeserialize("minecraft:story/enter_the_nether,twilightforest:progress_naga"),"mod advancement IDs accepted");
        var copy=rules.copy();var loaded=new net.minecraft.world.level.GameRules(new com.mojang.serialization.Dynamic<>(net.minecraft.nbt.NbtOps.INSTANCE,copy.createTag()));
        helper.assertTrue(loaded.createTag().equals(rules.createTag()),"all native/custom values survive copy and NBT reload exactly");
        helper.assertTrue(loaded.createTag().getString(RbdConfig.DEATH_DWELL.key().getId()).equals("0.123456789"),"no fractional precision lost");
        helper.assertTrue(milestones.tryDeserialize(""),"empty advancement list disables advancement checkpoints");
        helper.assertTrue(!loaded.createTag().equals(rules.createTag()),"world rule copies are independent");
        for(var setting:dev.rbd.rules.WorldRules.ALL)helper.assertTrue(loaded.createTag().contains(setting.key().getId()),"configured rule persisted: "+setting.key().getId());
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void drowningKeepsTerminalBodyEvidence(GameTestHelper helper) throws Exception {
        var victim=helper.spawnWithNoFreeWill(EntityType.VILLAGER,2,1,2);victim.setCustomName(Component.literal("Drowning witness"));victim.setAirSupply(-10);
        victim.hurt(helper.getLevel().damageSources().drown(),Float.MAX_VALUE);
        var game=GameSession.current;var book=game.archive.books().stream().filter(b->b.get("soul").getAsString().equals(victim.getUUID().toString())).findFirst().orElseThrow();
        try(var reader=game.archive.reader(book.get("head").getAsString())){
            MemoryFrame frame,last=null;while((frame=reader.next())!=null)last=frame;
            helper.assertTrue(last!=null&&last.body()!=null&&last.body().terminal(),"actual death is an explicit terminal frame");
            helper.assertTrue(last.body().air()==-10&&last.body().damageType().equals("drown"),"raw air and actual damage source persist");
            helper.assertTrue(!last.caption().contains("was killed")&&!last.caption().contains("被殺"),"terminal captions do not leak unseen attacker names");
        }helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void deathDoesNotRevealAnUnseenAttacker(GameTestHelper helper) throws Exception {
        var victim=helper.spawnWithNoFreeWill(EntityType.VILLAGER,2,1,4);victim.setCustomName(Component.literal("Victim"));victim.setYRot(0);victim.setXRot(0);
        var attacker=helper.spawnWithNoFreeWill(EntityType.ZOMBIE,2,1,1);attacker.setCustomName(Component.literal("UNSEEN_ATTACKER_FIXTURE"));attacker.setInvisible(true);
        var game=GameSession.current;game.recorder.record(victim,false);victim.hurt(helper.getLevel().damageSources().mobAttack(attacker),100);
        var book=game.archive.books().stream().filter(b->b.get("soul").getAsString().equals(victim.getUUID().toString())).findFirst().orElseThrow();
        try(var reader=game.archive.reader(book.get("head").getAsString())){
            MemoryFrame frame;while((frame=reader.next())!=null){
                helper.assertTrue(!frame.caption().contains("UNSEEN_ATTACKER_FIXTURE"),"death message cannot introduce an unseen killer's name");
                helper.assertTrue(frame.contacts().stream().noneMatch(c->c.soul().equals(attacker.getUUID())),"invisible attacker supplies no encounter evidence");
            }
        }helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void swallowedSaveFailureBlocksCommit(GameTestHelper helper) throws Exception {
        var guard=new SaveFailureGuard();
        guard.append(org.apache.logging.log4j.core.impl.Log4jLogEvent.newBuilder().setLoggerName("net.minecraft.world.level.storage.PlayerDataStorage")
            .setLevel(org.apache.logging.log4j.Level.WARN).setMessage(new org.apache.logging.log4j.message.SimpleMessage("Failed to save player data"))
            .setThrown(new java.io.IOException("fixture disk failure")).build());
        boolean rejected=false;try{guard.check();}catch(java.io.IOException expected){rejected=true;}
        helper.assertTrue(rejected,"a logged and swallowed vanilla save failure must block a clean-exit commit");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void identityRequiresVisibleEncounter(GameTestHelper helper){
        var observer=helper.spawnWithNoFreeWill(EntityType.VILLAGER,2,1,2);observer.setCustomName(Component.literal("Observer"));observer.setYRot(0);observer.setXRot(0);
        var contact=helper.spawnWithNoFreeWill(EntityType.VILLAGER,2,1,6);contact.setCustomName(Component.literal("Contact"));
        helper.assertTrue(Perception.contacts(observer).stream().anyMatch(c->c.soul().equals(contact.getUUID())),"visible named contact must be recognized");
        for(int x=0;x<=4;x++)for(int y=1;y<=4;y++)helper.setBlock(new BlockPos(x,y,4),Blocks.STONE);
        helper.assertTrue(Perception.contacts(observer).stream().noneMatch(c->c.soul().equals(contact.getUUID())),"wall must block identity evidence");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void ordinaryDeathSealsActualLife(GameTestHelper helper) throws Exception {
        var actor=helper.spawnWithNoFreeWill(EntityType.VILLAGER,2,1,2);actor.setCustomName(Component.literal("Recorded life"));
        var game=GameSession.current;helper.assertTrue(game!=null,"RBD server adapter loaded");game.recorder.caption(actor,"A witnessed beginning");game.recorder.record(actor,false);
        actor.hurt(helper.getLevel().damageSources().genericKill(),Float.MAX_VALUE);
        var book=game.archive.books().stream().filter(b->b.get("soul").getAsString().equals(actor.getUUID().toString())).findFirst().orElseThrow();
        helper.assertTrue(game.visible(book),"ordinary death book must be visible in its live branch");
        try(var reader=game.archive.reader(book.get("head").getAsString())){
            var first=reader.next();helper.assertTrue(first!=null&&first.pixels().length>0,"book contains actual perception imagery");
            helper.assertTrue(first.caption().contains("witnessed"),"life begins before death");helper.assertTrue(reader.next()!=null,"death is a separate ending");
        }helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void shelfPersistsIndex(GameTestHelper helper){
        helper.setBlock(new BlockPos(2,1,2),ModContent.SHELF.get().defaultBlockState().setValue(ArchiveShelfBlock.INDEX,15));
        helper.assertTrue(helper.getBlockState(new BlockPos(2,1,2)).getValue(ArchiveShelfBlock.INDEX)==15,"physical catalog partition survives block placement");helper.succeed();
    }
}
