package dev.rbd.testing;

import dev.rbd.ModContent;
import dev.rbd.phantom.*;
import dev.rbd.rules.WorldRules;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.util.FakePlayer;

@GameTestHolder("rbd") @PrefixGameTestTemplate(false)
public final class PhantomCombatGameTests {
    private static FakePlayer player(GameTestHelper h){return new FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"CombatTest"));}
    @GameTest(template="empty",batch="phantom_combat",timeoutTicks=100)
    public static void projectilePredictionRejectsOutgoingAndMissingShots(GameTestHelper h){
        var boss=h.spawnWithNoFreeWill(ModContent.PHANTOM.get(),2,1,2);boss.setDeltaMovement(Vec3.ZERO);
        var shot=EntityType.ARROW.create(h.getLevel());shot.setPos(boss.position().add(-8,1,0));shot.setDeltaMovement(2,0,0);
        double impact=PhantomCombat.impactTime(boss,shot,6);h.assertTrue(impact>0&&impact<4,"incoming arrow is predicted before impact");
        h.assertTrue(PhantomCombat.impactTime(boss,shot,2)<0,"arrows outside reaction horizon are not immediate threats");
        shot.setDeltaMovement(-2,0,0);h.assertTrue(PhantomCombat.impactTime(boss,shot,12)<0,"outgoing arrow does not cause a dodge");
        shot.setPos(boss.position().add(-8,1,4));shot.setDeltaMovement(2,0,0);h.assertTrue(PhantomCombat.impactTime(boss,shot,12)<0,"nearby shot missing the body does not cause a dodge");
        shot.setDeltaMovement(Vec3.ZERO);h.assertTrue(PhantomCombat.impactTime(boss,shot,12)<0,"stationary projectile is ignored");h.succeed();
    }
    @GameTest(template="empty",batch="phantom_combat",timeoutTicks=100)
    public static void bowAndCrossbowUseRealAmmunitionAndProjectiles(GameTestHelper h){
        var target=player(h);target.setPos(h.absoluteVec(new Vec3(2,1,10)));
        for(Item weapon:new Item[]{Items.BOW,Items.CROSSBOW}){
            target.getInventory().clearContent();target.getInventory().selected=0;target.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(weapon));target.getInventory().setItem(18,new ItemStack(Items.ARROW,8));
            var boss=h.spawnWithNoFreeWill(ModContent.PHANTOM.get(),2,1,2);boss.mirror(target);
            h.assertTrue(boss.hasRangedWeapon(),"copied ammunition enables ranged stance");
            PhantomEquipment.useWeapon(boss,target);
            var shots=h.getLevel().getEntitiesOfClass(Projectile.class,boss.getBoundingBox().inflate(8),p->p.getOwner()==boss);
            h.assertTrue(!shots.isEmpty(),weapon+" emits its native projectile in the firing action");
            h.assertTrue(boss.inventoryCopy.get(18).getCount()==7&&target.getInventory().getItem(18).getCount()==8,"only copied ammunition is consumed");
            h.assertTrue(target.getMainHandItem().getDamageValue()==0,"source player weapon remains unchanged");
            boss.inventoryCopy.set(18,ItemStack.EMPTY);h.assertTrue(!boss.hasRangedWeapon(),"empty weapon falls back to closing instead of kiting forever");
            for(var shot:shots)shot.discard();boss.discard();
        }h.succeed();
    }
    @GameTest(template="empty",batch="phantom_combat",timeoutTicks=100)
    public static void combatTracksWalkingAndReusesNavigation(GameTestHelper h){
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)h.setBlock(x,0,z,Blocks.STONE);
        var target=player(h);target.setPos(h.absoluteVec(new Vec3(5,1,2)));target.setDeltaMovement(Vec3.ZERO);
        var boss=h.spawnWithNoFreeWill(ModContent.PHANTOM.get(),1,1,2);boss.setOnGround(true);boss.mirror(target);
        var brain=boss.combat();brain.observe(target);
        for(int i=0;i<6;i++){target.setPos(target.position().add(0,0,0.2));brain.observe(target);}
        h.assertTrue(brain.observedMotion().z>0.15&&target.getDeltaMovement().lengthSqr()==0,"position samples recognize player walking even without server velocity");
        h.assertTrue(brain.aim(target).z>target.getEyePosition().z,"aim leads actual observed movement");
        for(int i=0;i<80;i++)brain.move(target,1,false,false,false);
        h.assertTrue(brain.pathRequests()>0&&brain.pathRequests()<=22,"navigation is reused instead of restarted every AI tick");h.succeed();
    }
    @GameTest(template="empty",batch="phantom_combat",timeoutTicks=100)
    public static void learnedWeaponAndTacticalSettingsSurviveSave(GameTestHelper h){
        var target=player(h);target.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STICK));
        var boss=h.spawnWithNoFreeWill(ModContent.PHANTOM.get(),2,1,2);boss.mirror(target);boss.rememberRangedWeapon(target.getMainHandItem());
        var tag=new net.minecraft.nbt.CompoundTag();boss.saveWithoutId(tag);var restored=ModContent.PHANTOM.get().create(h.getLevel());restored.load(tag);
        h.assertTrue(restored.hasRangedWeapon(),"observed third-party ranged weapon knowledge survives entity reload");
        var rules=new net.minecraft.world.level.GameRules();var settings=new com.google.gson.JsonObject();settings.addProperty("rbdPhantomDodgeDistance","32.25");settings.addProperty("rbdPhantomPathIntervalTicks","1");settings.addProperty("rbdPhantomDodgeProjectiles","false");WorldRules.apply(rules,settings);
        var saved=rules.createTag();h.assertTrue(saved.getString("rbdPhantomDodgeDistance").equals("32.25")&&saved.getString("rbdPhantomPathIntervalTicks").equals("1")&&saved.getString("rbdPhantomDodgeProjectiles").equals("false"),"tactical settings preserve configured precision, frequency and disable switch");
        var growth=new com.google.gson.JsonObject();growth.addProperty("health",200);growth.addProperty("damage",80);growth.addProperty("armor",30);
        h.assertTrue(PhantomProfile.tier(growth)>=2,"equipment growth contributes without requiring experience levels");h.succeed();
    }
}
