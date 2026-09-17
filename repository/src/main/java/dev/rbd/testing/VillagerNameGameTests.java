package dev.rbd.testing;

import dev.rbd.RbdConfig;
import dev.rbd.memory.*;
import dev.rbd.rules.WorldRules;
import dev.rbd.runtime.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("rbd") @PrefixGameTestTemplate(false)
public final class VillagerNameGameTests {
    @GameTest(template="empty",batch="villager_names",timeoutTicks=100)
    public static void onlyExactLegacyPlaceholdersAreMigrated(GameTestHelper h){
        var actor=new Villager(EntityType.VILLAGER,h.getLevel());
        var legacy=Component.literal("旅人 · "+actor.getUUID().toString().substring(0,4));
        actor.setCustomName(legacy);h.getLevel().addFreshEntity(actor);
        h.assertTrue(actor.hasCustomName()&&!legacy.equals(actor.getCustomName()),"old RBD UUID placeholder is upgraded on load");
        var upgraded=actor.getCustomName().copy();
        h.assertTrue(!VillagerNames.assign(actor)&&upgraded.equals(actor.getCustomName()),"migration happens once");
        actor.setCustomName(Component.literal("旅人 · 我的名字"));
        h.assertTrue(!VillagerNames.assign(actor),"similar player name is preserved");
        actor.setCustomName(legacy.copy().withStyle(net.minecraft.ChatFormatting.GOLD));
        h.assertTrue(!VillagerNames.assign(actor),"styled custom name is not a generated placeholder");
        h.succeed();
    }
    @GameTest(template="empty",batch="villager_names",timeoutTicks=100)
    public static void loadedVillagerAndBookKeepTheSameName(GameTestHelper h)throws Exception{
        var actor=h.spawnWithNoFreeWill(EntityType.VILLAGER,2,1,2);
        h.assertTrue(actor.hasCustomName(),"join event assigns an individual name before any interaction");
        var name=actor.getCustomName().copy();UUID id=actor.getUUID();
        var saved=new CompoundTag();actor.save(saved);actor.discard();
        var loaded=EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);
        h.assertTrue(loaded instanceof Villager,"normal vanilla entity NBT reload");
        var villager=(Villager)loaded;h.getLevel().addFreshEntity(villager);
        h.assertTrue(villager.getUUID().equals(id)&&name.equals(villager.getCustomName()),"unload/reload retains identity and name");
        // A pre-update checkpoint can contain the same UUID with no CustomName yet.
        saved.remove("CustomName");
        var beforeNaming=EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);
        h.assertTrue(VillagerNames.assign(beforeNaming)&&name.equals(beforeNaming.getCustomName()),"same UUID receives the same name when restored from an unnamed checkpoint");
        var game=GameSession.current;
        var reader=UUID.randomUUID();var contact=new MemoryFrame.Contact(id,Perception.name(villager),villager.getType().toString());
        try{
            h.assertTrue(game.introduce(reader,contact)&&!game.introduce(reader,contact),"first introduction is recorded once per reader");
            h.assertTrue(game.recognizes(reader,id),"introduced villager is recognized by the book reader");
            villager.hurt(h.getLevel().damageSources().genericKill(),Float.MAX_VALUE);
            var book=game.archive.books().stream().filter(b->b.get("soul").getAsString().equals(id.toString())).findFirst().orElseThrow();
            h.assertTrue(book.get("name").getAsString().equals(name.getString()),"actual death book uses the villager's saved name");
        }finally{game.branch.object("knowledge").remove(reader.toString());}
        h.succeed();
    }

    @GameTest(template="empty",batch="villager_names",timeoutTicks=100)
    public static void customNamesProfessionsAndConversionsArePreserved(GameTestHelper h){
        var actor=new Villager(EntityType.VILLAGER,h.getLevel());
        var custom=Component.literal("玩家取的名字").withStyle(net.minecraft.ChatFormatting.GOLD);
        actor.setCustomName(custom);actor.setCustomNameVisible(true);h.getLevel().addFreshEntity(actor);
        h.assertTrue(!VillagerNames.assign(actor)&&custom.equals(actor.getCustomName())&&actor.isCustomNameVisible(),"existing player/mod name and visibility are untouched");
        var generated=h.spawnWithNoFreeWill(EntityType.VILLAGER,2,1,2);var name=generated.getCustomName().copy();
        generated.setVillagerData(generated.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN));
        generated.setAge(-24000);generated.setAge(0);
        h.assertTrue(!VillagerNames.assign(generated)&&name.equals(generated.getCustomName()),"profession and age changes do not reroll names");
        var zombie=generated.convertTo(EntityType.ZOMBIE_VILLAGER,false);
        h.assertTrue(zombie!=null&&name.equals(zombie.getCustomName()),"vanilla zombification conversion copies the name");
        var cured=zombie.convertTo(EntityType.VILLAGER,false);
        h.assertTrue(cured!=null&&name.equals(cured.getCustomName()),"vanilla cure conversion retains the name despite the new UUID");
        cured.setCustomName(custom);VillagerNames.assign(cured);
        h.assertTrue(custom.equals(cured.getCustomName()),"a later name-tag name takes precedence");
        h.succeed();
    }

    @GameTest(template="empty",batch="villager_names",timeoutTicks=100)
    public static void namingRespectsWorldRuleAndDoesNotNameOtherMobs(GameTestHelper h){
        var rules=h.getLevel().getGameRules();boolean old=RbdConfig.AUTO_NAME_VILLAGERS.get(rules);
        var change=new com.google.gson.JsonObject();
        try{
            change.addProperty("rbdAutoNameVillagers","false");WorldRules.apply(rules,change);
            var actor=h.spawnWithNoFreeWill(EntityType.VILLAGER,2,1,2);
            h.assertTrue(!actor.hasCustomName()&&!VillagerNames.assign(actor),"disabled rule is respected by join and interaction fallback");
            change.addProperty("rbdAutoNameVillagers","true");WorldRules.apply(rules,change);
            h.assertTrue(VillagerNames.assign(actor)&&actor.hasCustomName(),"loaded unnamed villager can be named after enabling the rule");
            var pig=h.spawnWithNoFreeWill(EntityType.PIG,4,1,2);
            h.assertTrue(!VillagerNames.assign(pig)&&!pig.hasCustomName(),"unrelated creatures stay unchanged");
            var names=new java.util.HashSet<String>();
            for(int i=0;i<128;i++){
                var sample=new Villager(EntityType.VILLAGER,h.getLevel());sample.setUUID(new UUID(731L,i));VillagerNames.assign(sample);names.add(sample.getName().getString());
            }
            h.assertTrue(names.size()>110,"different identities produce varied personal names");
        }finally{change.addProperty("rbdAutoNameVillagers",Boolean.toString(old));WorldRules.apply(rules,change);}
        h.succeed();
    }
}
