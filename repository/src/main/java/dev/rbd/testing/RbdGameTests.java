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
