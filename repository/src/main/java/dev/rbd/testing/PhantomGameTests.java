package dev.rbd.testing;
import dev.rbd.ModContent;
import dev.rbd.phantom.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@GameTestHolder("rbd") @PrefixGameTestTemplate(false)
public final class PhantomGameTests {
    @GameTest(template="empty",batch="phantom",timeoutTicks=100)
    public static void phantomMirrorPreservesComponentsAndBothPhases(GameTestHelper h){
        var player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PhantomTest"));player.getInventory().selected=4;
        var weapon=new ItemStack(Items.DIAMOND_SWORD);weapon.setDamageValue(37);weapon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Original blade"));
        player.setItemSlot(EquipmentSlot.MAINHAND,weapon);player.getInventory().setItem(19,new ItemStack(Items.DIAMOND,17));
        var boss=h.spawnWithNoFreeWill(ModContent.PHANTOM.get(),2,1,2);boss.mirror(player);
        h.assertTrue(boss.inventoryCopy.size()==player.getInventory().getContainerSize()&&boss.inventoryCopy.get(19).getCount()==17,"complete inventory copied including inactive slots");
        h.assertTrue(ItemStack.matches(weapon,boss.getMainHandItem())&&weapon!=boss.getMainHandItem(),"copied weapon retains name and damage without aliasing");
        boss.getMainHandItem().setDamageValue(93);h.assertTrue(weapon.getDamageValue()==37,"phantom cannot mutate original equipment");
        boss.hurt(h.getLevel().damageSources().genericKill(),1000000);
        h.assertTrue(boss.isAlive()&&boss.phase()==2&&boss.getHealth()>0,"one-shot first phase enters the second phase");
        h.assertTrue(DespairPhantomEntity.AVAILABLE_BOONS.contains(boss.boon()),"exactly one configured native boon selected");
        var saved=new CompoundTag();boss.saveWithoutId(saved);var restored=ModContent.PHANTOM.get().create(h.getLevel());restored.load(saved);
        h.assertTrue(restored.quarryId().equals(player.getUUID())&&restored.phase()==2&&restored.boon().equals(boss.boon()),"target, phase and random boon survive reload");
        h.assertTrue(restored.inventoryCopy.get(19).getCount()==17&&restored.getMainHandItem().getDamageValue()==93,"full gear snapshot survives reload");
        PhantomExecution.kill(boss);h.assertTrue(boss.isRemoved(),"explicit aura execution does not restart another phantom's first phase");h.succeed();
    }
    @GameTest(template="empty",batch="phantom",timeoutTicks=100)
    public static void phantomExecutionBypassesTotemsAndCancelledDeath(GameTestHelper h){
        var victim=h.spawnWithNoFreeWill(EntityType.ZOMBIE,2,1,2);victim.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.TOTEM_OF_UNDYING));victim.setInvulnerable(true);
        java.util.function.Consumer<LivingDeathEvent> cancel=e->{if(e.getEntity()==victim){e.setCanceled(true);victim.setHealth(victim.getMaxHealth());}};
        NeoForge.EVENT_BUS.addListener(cancel);
        try{
            PhantomExecution.kill(victim);h.assertTrue(victim.isRemoved()&&!victim.isAlive(),"death-cancelling mod listener, invulnerability and totem cannot prevent aura execution");
            h.assertTrue(victim.getOffhandItem().is(Items.TOTEM_OF_UNDYING),"totem is not consumed as an ordinary damage save");
        }finally{NeoForge.EVENT_BUS.unregister(cancel);}
        var normal=h.spawnWithNoFreeWill(EntityType.ZOMBIE,4,1,2);normal.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.TOTEM_OF_UNDYING));normal.hurt(h.getLevel().damageSources().magic(),1000);
        h.assertTrue(normal.isAlive()&&normal.getOffhandItem().isEmpty(),"ordinary damage still respects a totem outside the aura scope");h.succeed();
    }
    @GameTest(template="empty",batch="phantom",timeoutTicks=120)
    public static void phantomAuraUsesSphereAndExemptsOnlyItsTarget(GameTestHelper h){
        var target=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PhantomTest"));var boss=h.spawnWithNoFreeWill(ModContent.PHANTOM.get(),2,1,2);boss.mirror(target);target.setPos(boss.position());
        // The aura extends beyond the GameTest template. A synchronous getChunk call alone
        // does not keep those chunks visible in the entity index on a fresh CI world.
        var forced=new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        for(var pos:java.util.List.of(
                net.minecraft.core.BlockPos.containing(boss.getX()+31.9,boss.getY(),boss.getZ()),
                net.minecraft.core.BlockPos.containing(boss.getX()+32.1,boss.getY(),boss.getZ()),
                net.minecraft.core.BlockPos.containing(boss.getX()+24,boss.getY(),boss.getZ()+24))){
            var chunk=new net.minecraft.world.level.ChunkPos(pos);
            if(!h.getLevel().getForcedChunks().contains(chunk.toLong())){
                h.getLevel().setChunkForced(chunk.x,chunk.z,true);forced.add(chunk);
            }
            h.getLevel().getChunk(pos);
        }
        Runnable cleanup=()->forced.forEach(chunk->h.getLevel().setChunkForced(chunk.x,chunk.z,false));
        h.runAtTickTime(119,cleanup);
        var inside=h.spawnWithNoFreeWill(EntityType.PIG,3,1,2);inside.setNoGravity(true);inside.setPos(boss.getX()+31.9,boss.getY(),boss.getZ());
        var outside=h.spawnWithNoFreeWill(EntityType.PIG,4,1,2);outside.setNoGravity(true);outside.setPos(boss.getX()+32.1,boss.getY(),boss.getZ());
        var diagonal=h.spawnWithNoFreeWill(EntityType.COW,5,1,2);diagonal.setNoGravity(true);diagonal.setPos(boss.getX()+24,boss.getY(),boss.getZ()+24);
        h.startSequence().thenWaitUntil(()->{
            var loaded=com.google.common.collect.Lists.newArrayList(h.getLevel().getAllEntities());
            h.assertTrue(loaded.containsAll(java.util.List.of(inside,outside,diagonal)),"all radius fixtures are present in the loaded entity index");
        }).thenExecute(()->{
            try{
                boss.enterSecondPhase();var tag=new CompoundTag();boss.saveWithoutId(tag);tag.putInt("AuraDelay",0);boss.load(tag);
                boss.pulseAura();h.assertTrue(inside.isRemoved(),"within default 32 blocks is killed");
                h.assertTrue(outside.isAlive()&&diagonal.isAlive(),"outside radius and box-only diagonal excluded");h.assertTrue(target.isAlive()&&boss.isAlive(),"locked target and executing phantom exempt");
            }finally{outside.discard();diagonal.discard();boss.discard();cleanup.run();}
        }).thenSucceed();
    }

    @GameTest(template="empty",batch="phantom",timeoutTicks=100)
    public static void phantomLearningAndRulesPersist(GameTestHelper h){
        var history=new com.google.gson.JsonObject();h.assertTrue(PhantomProfile.tier(history)==0,"unobserved novice starts without advanced tactics");history.addProperty("level",60);history.addProperty("ranged",100);
        h.assertTrue(PhantomProfile.tier(history)==3&&PhantomProfile.prefers(history,"ranged","melee"),"growth and observed combat unlock advanced ranged counters");
        var rules=new net.minecraft.world.level.GameRules();var radius=(net.minecraft.world.level.GameRules.IntegerValue)rules.getRule((net.minecraft.world.level.GameRules.Key)PhantomRules.AURA_RANGE.key());
        h.assertTrue(radius.tryDeserialize("64.25"),"native world-rule editor accepts fractional aura radius");
        var restored=new net.minecraft.world.level.GameRules(new com.mojang.serialization.Dynamic<>(NbtOps.INSTANCE,rules.createTag()));
        h.assertTrue(restored.createTag().getString(PhantomRules.AURA_RANGE.key().getId()).equals("64.25"),"configured radius persists without rounding");h.succeed();
    }
}
