package dev.rbd.testing;

import dev.rbd.runtime.GameSession;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentType;
import java.util.function.Supplier;

/** Optional, real third-party objects; reflection keeps the shipped mod independent of these mods. */
public final class CompatChecks {
    public static final ResourceKey<Level> TWILIGHT=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("twilightforest:twilight_forest"));
    public static final BlockPos MARKER=new BlockPos(0,200,0);
    public static Item item(String id){var key=ResourceLocation.parse(id);if(!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalStateException("Missing fixture item "+id);return BuiltInRegistries.ITEM.get(key);}
    public static void require(boolean b,String why){if(!b)throw new IllegalStateException("Compatibility fixture: "+why);}
    public static void transform(ServerPlayer p) throws Exception {
        var belt=item("kamenridercraft:arcle");p.setItemSlot(EquipmentSlot.FEET,new ItemStack(belt));
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack((Item)belt.getClass().getField("helmet").get(belt)));
        p.setItemSlot(EquipmentSlot.CHEST,new ItemStack((Item)belt.getClass().getField("chestplate").get(belt)));
        p.setItemSlot(EquipmentSlot.LEGS,new ItemStack((Item)belt.getClass().getField("leggings").get(belt)));
        belt.getClass().getMethod("onTransformation",ItemStack.class,LivingEntity.class).invoke(belt,p.getItemBySlot(EquipmentSlot.FEET),p);
        require(transformed(p),"KRC recognizes the transformed armor set");
    }
    public static boolean transformed(ServerPlayer p) throws Exception {
        var belt=p.getItemBySlot(EquipmentSlot.FEET).getItem();
        return belt==item("kamenridercraft:arcle")&&(boolean)belt.getClass().getMethod("isTransformed",LivingEntity.class).invoke(belt,p);
    }
    @SuppressWarnings("unchecked") public static AttachmentType<Boolean> riderAttachment() throws Exception {
        return ((Supplier<AttachmentType<Boolean>>)Class.forName("com.kelco.kamenridercraft.attachments.AttachmentTypes").getField("MOB_TRANSFORMED").get(null)).get();
    }
    public static void charm(GameSession g,ServerPlayer p) throws Exception {
        long before=g.archive.books().size();p.setInvulnerable(false);p.removeAllEffects();p.setHealth(1);p.invulnerableTime=0;
        p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.TOTEM_OF_UNDYING));
        p.hurt(p.damageSources().generic(),Float.MAX_VALUE);
        require(p.isAlive()&&p.getOffhandItem().isEmpty()&&!g.returnPending()&&!g.transitioning,"vanilla totem is consumed and prevents return with both mods installed");
        require(g.archive.books().size()==before,"totem rescue creates no death book");
        p.removeAllEffects();p.setHealth(1);p.invulnerableTime=0;
        p.getInventory().add(new ItemStack(item("twilightforest:charm_of_life_1")));
        p.hurt(p.damageSources().generic(),Float.MAX_VALUE);
        require(p.isAlive()&&p.getHealth()>1&&!g.returnPending()&&!g.transitioning,"Twilight life charm cancels death before RBD");
        require(p.getInventory().countItem(item("twilightforest:charm_of_life_1"))==0,"life charm consumed");
        require(g.archive.books().size()==before,"rescued player has no death book");p.removeAllEffects();p.invulnerableTime=0;
    }
    public static void advancement(ServerPlayer p,String id,boolean grant){
        var holder=p.server.getAdvancements().get(ResourceLocation.parse(id));require(holder!=null,"mod advancement exists: "+id);
        var progress=p.getAdvancements().getOrStartProgress(holder);
        for(String criterion:(grant?progress.getRemainingCriteria():progress.getCompletedCriteria())){
            if(grant)p.getAdvancements().award(holder,criterion);else p.getAdvancements().revoke(holder,criterion);
        }
    }
    public static boolean advanced(ServerPlayer p,String id){var holder=p.server.getAdvancements().get(ResourceLocation.parse(id));require(holder!=null,"advancement loaded");return p.getAdvancements().getOrStartProgress(holder).isDone();}
    private CompatChecks(){}
}
