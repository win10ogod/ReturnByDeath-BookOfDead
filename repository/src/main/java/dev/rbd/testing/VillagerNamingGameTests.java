package dev.rbd.testing;

import dev.rbd.*;
import dev.rbd.memory.Perception;
import dev.rbd.rules.WorldRules;
import dev.rbd.runtime.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import com.google.gson.JsonObject;
import java.util.UUID;

@GameTestHolder("rbd") @PrefixGameTestTemplate(false)
public final class VillagerNamingGameTests {
    private static FakePlayer player(GameTestHelper h){
        var p=new FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"VillagerNamingTest"));
        p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,1,1)));return p;
    }
    @GameTest(template="empty",batch="villager_naming",timeoutTicks=100)
    public static void onlyPlayerConfirmationNamesVillagerAndBook(GameTestHelper h)throws Exception{
        var g=GameSession.current;var v=h.spawnWithNoFreeWill(EntityType.VILLAGER,2,1,3);var p=player(h);var naming=new VillagerNaming();
        h.assertTrue(!v.hasCustomName(),"loading a villager never chooses a name automatically");
        var request=naming.open(g,p,v.getUUID());
        h.assertTrue(request.get("kind").getAsString().equals("villager_name_opened")&&!v.hasCustomName(),"opening and cancelling edit do not modify the villager");
        request.addProperty("name","  玩家命名的阿明  ");
        h.assertTrue(naming.rename(g,p,request).get("ok").getAsBoolean(),"player confirmation is accepted");
        h.assertTrue(Perception.name(v).equals("玩家命名的阿明"),"exact player choice is used with surrounding spaces trimmed");
        h.assertTrue(g.recognizes(p.getUUID(),v.getUUID()),"the player learns the identity they named");
        var nbt=new CompoundTag();v.save(nbt);var id=v.getUUID();v.discard();
        var loaded=EntityType.loadEntityRecursive(nbt,h.getLevel(),e->e);h.getLevel().addFreshEntity(loaded);
        h.assertTrue(loaded.getName().getString().equals("玩家命名的阿明"),"vanilla save/load preserves player name");
        ((net.minecraft.world.entity.LivingEntity)loaded).hurt(h.getLevel().damageSources().genericKill(),Float.MAX_VALUE);
        var book=g.archive.books().stream().filter(b->b.get("soul").getAsString().equals(id.toString())).findFirst().orElseThrow();
        h.assertTrue(book.get("name").getAsString().equals("玩家命名的阿明"),"death book uses the player-given name");
        g.branch.object("knowledge").remove(p.getUUID().toString());h.succeed();
    }
    @GameTest(template="empty",batch="villager_naming",timeoutTicks=100)
    public static void staleOrInvalidEditsNeverOverwriteNames(GameTestHelper h){
        var g=GameSession.current;var v=h.spawnWithNoFreeWill(EntityType.VILLAGER,2,1,3);var p=player(h);var naming=new VillagerNaming();
        var legacy=Component.literal("旅人 · "+v.getUUID().toString().substring(0,4));v.setCustomName(legacy);
        new RbdEvents().entityJoined(new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(v,h.getLevel()));
        h.assertTrue(legacy.equals(v.getCustomName()),"legacy names are not automatically replaced");
        for(String invalid:new String[]{"   ","A".repeat(33),"改\n名","§c偽裝","隱\u200b字"}){
            var request=naming.open(g,p,v.getUUID());request.addProperty("name",invalid);
            h.assertTrue(!naming.rename(g,p,request).get("ok").getAsBoolean()&&legacy.equals(v.getCustomName()),"invalid input cannot modify an existing name");
        }
        var request=naming.open(g,p,v.getUUID());request.addProperty("name","較晚送出的名字");
        v.setCustomName(Component.literal("朋友剛取的名字"));
        h.assertTrue(!naming.rename(g,p,request).get("ok").getAsBoolean()&&v.getName().getString().equals("朋友剛取的名字"),"stale editor cannot overwrite a concurrent player rename");
        request=naming.open(g,p,v.getUUID());request.addProperty("name","舊世界的修改");
        h.assertTrue(!new VillagerNaming().rename(g,p,request).get("ok").getAsBoolean(),"old editor token is rejected after the world session is replaced");
        h.succeed();
    }
    @GameTest(template="empty",batch="villager_naming",timeoutTicks=100)
    public static void serverChecksReachVisibilityRuleAndTransition(GameTestHelper h){
        var g=GameSession.current;var v=h.spawnWithNoFreeWill(EntityType.VILLAGER,2,1,3);var p=player(h);var naming=new VillagerNaming();
        var request=naming.open(g,p,v.getUUID());request.addProperty("name","阿明");
        p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(30,1,1)));
        h.assertTrue(!naming.rename(g,p,request).get("ok").getAsBoolean(),"server rejects an edit after moving out of reach");
        p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,1,1)));
        h.setBlock(2,1,2,Blocks.STONE);h.setBlock(2,2,2,Blocks.STONE);
        h.assertTrue(!naming.rename(g,p,request).get("ok").getAsBoolean(),"server rejects editing through a wall");
        h.setBlock(2,1,2,Blocks.AIR);h.setBlock(2,2,2,Blocks.AIR);
        var rules=h.getLevel().getGameRules();var changes=new JsonObject();boolean old=RbdConfig.PLAYER_NAME_VILLAGERS.get(rules);int limit=RbdConfig.VILLAGER_NAME_LENGTH.get(rules);
        try{
            changes.addProperty("rbdPlayerNameVillagers","false");WorldRules.apply(rules,changes);
            h.assertTrue(!naming.rename(g,p,request).get("ok").getAsBoolean(),"world rule also blocks forged client submissions");
            changes.addProperty("rbdPlayerNameVillagers","true");changes.addProperty("rbdVillagerNameMaxLength","2");WorldRules.apply(rules,changes);
            request.addProperty("name","三個字");h.assertTrue(!naming.rename(g,p,request).get("ok").getAsBoolean(),"server enforces changed length limit");
            request.addProperty("name","阿明");g.transitioning=true;
            h.assertTrue(!naming.rename(g,p,request).get("ok").getAsBoolean(),"transition never accepts an edit");
        }finally{g.transitioning=false;changes.addProperty("rbdPlayerNameVillagers",Boolean.toString(old));changes.addProperty("rbdVillagerNameMaxLength",Integer.toString(limit));WorldRules.apply(rules,changes);}
        h.assertTrue(!v.hasCustomName(),"all rejected edits leave the villager untouched");h.succeed();
    }
}
