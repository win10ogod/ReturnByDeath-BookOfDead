package dev.maskedinvasion.test;
import dev.maskedinvasion.*;
import dev.rbd.rules.WorldRules;
import com.google.gson.JsonObject;
import com.kelco.kamenridercraft.item.base_items.RiderDriverItem;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@Mod("masked_invasion_tests")
@GameTestHolder(MaskedInvasion.ID)
@PrefixGameTestTemplate(false)
public final class InvasionTests {
    static GameRules rules(){return new GameRules();}
    static void rule(GameRules rules,String key,int value){var n=new JsonObject();n.addProperty(key,value);WorldRules.apply(rules,n);}
    static RaidData.Raid raid(GameTestHelper h){return new RaidData.Raid(UUID.randomUUID(),new RaidData.Home(h.getLevel().dimension(),h.absolutePos(new BlockPos(2,2,2)),0),1,16,12,1,123,600,18000);}
    static InvaderSummonEntity mob(GameTestHelper h,RaidData.Raid raid,FormCatalog.Form form){
        var m=MaskedInvasion.INVADER.get().create(h.getLevel());m.equip(raid.id,raid.wave,form);m.moveTo(h.absolutePos(new BlockPos(2,2,2)),0,0);return m;
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void firstWaveMatchesPromisedComposition(GameTestHelper h){
        var c=FormCatalog.defaults();var r=rules();
        for(int seed=0;seed<200;seed++){
            var list=WavePlan.create(c,r,1,1,seed);long basic=list.stream().filter(f->f.tier()==FormCatalog.Tier.BASIC).count(),enhanced=list.stream().filter(f->f.tier()==FormCatalog.Tier.ENHANCED).count();
            h.assertTrue(basic==4&&(enhanced==1||enhanced==2)&&list.size()==basic+enhanced,"first wave 4 basic + 1..2 enhanced, seed "+seed);
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void growthAndConfiguredLimits(GameTestHelper h){
        var c=FormCatalog.defaults();var r=rules();
        var fourth=WavePlan.create(c,r,4,1,42);h.assertTrue(fourth.stream().anyMatch(f->f.tier()==FormCatalog.Tier.SUPER),"super wave 4");
        var eighth=WavePlan.create(c,r,8,1,42);h.assertTrue(eighth.stream().anyMatch(f->f.tier()==FormCatalog.Tier.FINAL),"final wave 8");
        h.assertTrue(WavePlan.create(c,r,Integer.MAX_VALUE,100,42).size()==32,"no overflow, configured default 32");
        rule(r,"miMaxRaiders",80);h.assertTrue(WavePlan.create(c,r,100,10,42).size()==80,"cap is configurable above default");
        h.assertTrue(WavePlan.growth(5,15)==1.6,"linear growth");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void deterministicWaveAndMultiplayerScaling(GameTestHelper h){
        var c=FormCatalog.defaults();var r=rules();var a=WavePlan.create(c,r,1,1,42);var b=WavePlan.create(c,r,1,3,42);
        h.assertTrue(b.size()==a.size()+4,"two extra defenders add four invaders");h.assertTrue(a.equals(WavePlan.create(c,r,1,1,42)),"persisted seed reproduces forms");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void everyFormHasRealArmorAndPersists(GameTestHelper h){
        var raid=raid(h);int count=0;
        for(var f:FormCatalog.defaults().all()){
            var m=mob(h,raid,f);var belt=m.getItemBySlot(EquipmentSlot.FEET);
            h.assertTrue(f.belt().isTransformed(m),"native full suit "+f.id());
            h.assertTrue(m.getCustomName().equals(net.minecraft.network.chat.Component.translatable(f.tier().ordinal()>=FormCatalog.Tier.SUPER.ordinal()?"entity.masked_invasion.general":"entity.masked_invasion.soldier")),"soldier/general name by tier "+f.id());
            for(var form:f.forms())h.assertTrue(RiderDriverItem.getFormItem(belt,form.getSlot())==form,"native form slot "+f.id());
            f.belt().beltTick(belt,h.getLevel(),m,36);f.belt().giveEffects(m);
            var tag=new CompoundTag();m.saveWithoutId(tag);var restored=MaskedInvasion.INVADER.get().create(h.getLevel());restored.load(tag);
            h.assertTrue(restored.formId().equals(f.id())&&restored.raidId().equals(raid.id)&&f.belt().isTransformed(restored),"form/raid/equipment persisted "+f.id());
            h.assertTrue(ItemStack.matches(belt,restored.getItemBySlot(EquipmentSlot.FEET)),"belt components roundtrip "+f.id());m.discard();restored.discard();count++;
        }
        h.assertTrue(count>=40,"curated real form catalogue");MaskedInvasion.LOG.info("VERIFIED_INVASION_FORMS {}",count);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void invalidCatalogDoesNotSilentlyDropForms(GameTestHelper h){
        var json=new JsonObject();json.addProperty("schema",1);var rows=new com.google.gson.JsonArray();var row=new JsonObject();row.addProperty("id","missing");row.addProperty("tier","basic");row.addProperty("belt","no_such_driver");row.add("forms",new com.google.gson.JsonArray());rows.add(row);json.add("forms",rows);
        boolean rejected=false;try{FormCatalog.parse(json);}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,"unknown IDs rejected");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void encounterStateRoundTripWithoutHistory(GameTestHelper h){
        var data=new RaidData();data.clock=72000;data.lastDay=94000;var r=raid(h);r.phase=RaidData.Phase.ACTIVE;r.warning=0;r.remaining=981;
        var player=UUID.randomUUID();r.defenders.put(player,r.home);var attacker=new RaidData.Attacker(UUID.randomUUID(),"arcle",r.home.position());attacker.spawned=true;r.attackers.put(attacker.id,attacker);data.raids.put(r.id,r);
        var progress=new RaidData.Progress(96000);progress.wave=7;progress.pendingEmeralds=3;data.players.put(player,progress);
        var n=data.save(new CompoundTag(),h.getLevel().registryAccess());var copy=RaidData.load(n,h.getLevel().registryAccess());var loaded=copy.raids.get(r.id);
        h.assertTrue(loaded.phase==r.phase&&loaded.remaining==981&&loaded.attackers.get(attacker.id).spawned&&loaded.defenders.get(player).equals(r.home),"active encounter persisted");
        h.assertTrue(copy.clock==72000&&copy.players.get(player).wave==7&&copy.players.get(player).pendingEmeralds==3,"schedule/progression/rewards persisted");h.assertTrue(copy.raids.size()==1&&copy.players.size()==1,"bounded current records only");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void arenaChecksHeightAndDimensions(GameTestHelper h){
        var r=raid(h);var p=r.home.position();h.assertTrue(r.contains(r.home.dimension(),p.getX()+0.5,p.getY(),p.getZ()+0.5),"center accepted");
        h.assertTrue(!r.contains(Level.NETHER,p.getX(),p.getY(),p.getZ()),"different dimension rejected");
        h.assertTrue(!r.contains(r.home.dimension(),p.getX()+17,p.getY(),p.getZ()),"outside horizontal boundary");
        h.assertTrue(!r.contains(r.home.dimension(),p.getX(),p.getY()+13,p.getZ()),"above vertical boundary");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void safeSpawnRejectsHazardsAndSolidWalls(GameTestHelper h){
        var pos=new BlockPos(2,2,2);h.setBlock(pos.below(),Blocks.STONE);h.setBlock(pos,Blocks.AIR);h.setBlock(pos.above(),Blocks.AIR);
        h.assertTrue(SafePositions.safe(h.getLevel(),h.absolutePos(pos),null),"indoor floor accepted");h.setBlock(pos.above(),Blocks.STONE);
        h.assertTrue(!SafePositions.safe(h.getLevel(),h.absolutePos(pos),null),"head collision rejected");h.setBlock(pos.above(),Blocks.AIR);h.setBlock(pos.below(),Blocks.MAGMA_BLOCK);
        h.assertTrue(!SafePositions.safe(h.getLevel(),h.absolutePos(pos),null),"magma rejected");h.setBlock(pos.below(),Blocks.AIR);
        h.assertTrue(!SafePositions.safe(h.getLevel(),h.absolutePos(pos),null),"floating spawn rejected");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void invaderCannotBeCapturedAndVillagersRecognizeEnemy(GameTestHelper h){
        var r=raid(h);r.phase=RaidData.Phase.ACTIVE;RaidData.get(h.getLevel().getServer()).raids.put(r.id,r);
        var m=mob(h,r,FormCatalog.defaults().get("arcle"));var v=h.spawn(EntityType.VILLAGER,new BlockPos(3,2,2));
        h.assertTrue(m.canAttack(v),"invader targets village defenders");h.assertTrue(dev.krcvillagers.Companions.hostile(v,m),"village rider recognizes invader");
        for(String type:List.of("aqua","creative","diamond","emerald","golden","hostile")){
            var tag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath("moblassos","forbidden_in_"+type+"_lasso"));h.assertTrue(m.getType().is(tag),"lasso tag blocks "+type);
        }
        h.assertTrue(!m.canBeLeashed()&&!m.removeWhenFarAway(1e9),"persistent encounter entity");m.discard();v.discard();RaidData.get(h.getLevel().getServer()).raids.remove(r.id);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void removingEntityIsNotVictory(GameTestHelper h){
        var d=RaidData.get(h.getLevel().getServer());var r=raid(h);r.phase=RaidData.Phase.ACTIVE;d.raids.put(r.id,r);var m=mob(h,r,FormCatalog.defaults().get("arcle"));
        r.attackers.put(m.getUUID(),new RaidData.Attacker(m.getUUID(),m.formId(),m.blockPosition()));m.discard();h.assertTrue(r.attackers.size()==1,"discard/unload is not a kill");
        Invasions.defeated(h.getLevel(),r.id,m.getUUID());h.assertTrue(r.attackers.isEmpty(),"recorded kill advances encounter");d.raids.remove(r.id);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void checkpointHoldFollowsEncounterLifecycle(GameTestHelper h){
        var s=h.getLevel().getServer();var d=RaidData.get(s);var r=raid(h);d.raids.put(r.id,r);h.assertTrue(Invasions.blocksCheckpoint(s),"active encounter holds safe point");d.raids.remove(r.id);
        h.assertTrue(!d.raids.containsKey(r.id),"ended encounter releases own state");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void lassoPreservesNamedRecruitedRider(GameTestHelper h){
        var v=h.spawn(EntityType.VILLAGER,new BlockPos(2,2,2));var owner=UUID.randomUUID();var data=v.getData(dev.krcvillagers.KrcVillagers.COMPANION);data.owner=owner;data.mode=dev.krcvillagers.VillagerCompanionData.Mode.GUARD;
        v.setCustomName(net.minecraft.network.chat.Component.literal("據點守衛"));data.items.setStackInSlot(0,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("kamenridercraft:arcle"))));
        var player=h.makeMockPlayer(GameType.CREATIVE);var lasso=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("moblassos:creative_lasso")));player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,lasso);
        fuzs.moblassos.world.item.LassoItem.onEntityInteract(player,h.getLevel(),net.minecraft.world.InteractionHand.MAIN_HAND,v);
        var item=(fuzs.moblassos.world.item.LassoItem)lasso.getItem();h.assertTrue(item.hasStoredEntity(lasso),"villager captured");
        var at=h.absolutePos(new BlockPos(4,2,4));item.releaseContents(player,h.getLevel(),lasso,at,at.below());
        h.assertTrue(!item.hasStoredEntity(lasso),"lasso emptied after release");
        var restored=h.getLevel().getEntitiesOfClass(Villager.class,new net.minecraft.world.phys.AABB(at).inflate(3),e->e.isAlive()&&e.hasCustomName()&&e.getName().getString().equals("據點守衛")).stream().findFirst().orElseThrow();
        var rd=restored.getData(dev.krcvillagers.KrcVillagers.COMPANION);h.assertTrue(owner.equals(rd.owner)&&rd.items.getStackInSlot(0).is(data.items.getStackInSlot(0).getItem()),"owner and belt preserved");restored.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void invalidBedDoesNotBecomeAnUnsafeRespawn(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        var missing=new RaidData.Home(h.getLevel().dimension(),pos,0,false);
        h.assertTrue(!SafePositions.validRespawn(h.getLevel(),missing),"destroyed bed rejected");
        h.getLevel().setBlock(pos,Blocks.WHITE_BED.defaultBlockState(),3);
        h.assertTrue(SafePositions.validRespawn(h.getLevel(),missing),"existing overworld bed recognized");
        h.assertTrue(SafePositions.validRespawn(h.getLevel(),new RaidData.Home(h.getLevel().dimension(),pos,0,true)),"forced/admin spawn does not require bed");h.succeed();
    }

}
