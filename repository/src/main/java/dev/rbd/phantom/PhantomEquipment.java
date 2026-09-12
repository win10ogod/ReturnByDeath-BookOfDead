package dev.rbd.phantom;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.LoggerFactory;
import java.lang.reflect.*;
import java.util.*;

/** Optional adapters invoke the installed mod's own implementation; no third-party classes in the jar. */
public final class PhantomEquipment {
    private static final Set<String> REPORTED=new HashSet<>();
    private static Object invoke(Object receiver,String method,Object... args) throws ReflectiveOperationException {
        Class<?> type=receiver instanceof Class<?> c?c:receiver.getClass();
        outer:for(Method m:type.getMethods())if(m.getName().equals(method)&&m.getParameterCount()==args.length){
            Class<?>[] p=m.getParameterTypes();for(int i=0;i<p.length;i++)if(args[i]!=null&&!box(p[i]).isInstance(args[i]))continue outer;
            return m.invoke(receiver instanceof Class<?>?null:receiver,args);
        }throw new NoSuchMethodException(type.getName()+"."+method);
    }
    private static Class<?> box(Class<?> c){return c==int.class?Integer.class:c==boolean.class?Boolean.class:c==double.class?Double.class:c;}
    private static void failure(String component,Exception failure){if(REPORTED.add(component))LoggerFactory.getLogger("rbd").error("Phantom compatibility failed: {}. The affected native ability could not run.",component,failure);}
    public static Map slotTypes(boolean client){
        try{return (Map)invoke(Class.forName("top.theillusivec4.curios.api.CuriosApi"),"getSlots",client);}
        catch(ReflectiveOperationException ex){failure("Curios slot definitions",ex);return Map.of();}
    }
    private static Object handler(LivingEntity entity) throws ReflectiveOperationException {
        return ((Optional<?>)invoke(Class.forName("top.theillusivec4.curios.api.CuriosApi"),"getCuriosInventory",entity)).orElse(null);
    }
    public static CompoundTag curiosSnapshot(LivingEntity wearer){
        CompoundTag result=new CompoundTag();if(!ModList.get().isLoaded("curios"))return result;
        try{Object h=handler(wearer);if(h!=null)for(var entry:((Map<?,?>)invoke(h,"getCurios")).entrySet())result.put(entry.getKey().toString(),((CompoundTag)invoke(entry.getValue(),"serializeNBT")).copy());}
        catch(ReflectiveOperationException ex){failure("Curios snapshot",ex);}return result;
    }
    public static void restoreCurios(LivingEntity wearer,CompoundTag snapshot){
        if(!ModList.get().isLoaded("curios"))return;
        try{
            Object h=handler(wearer);if(h==null)return;
            var contract=Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Class<?> dropType=Class.forName("top.theillusivec4.curios.api.type.capability.ICurio$DropRule");
            var constructor=Class.forName("top.theillusivec4.curios.common.inventory.CurioStacksHandler").getConstructor(contract,String.class,int.class,boolean.class,boolean.class,boolean.class,dropType);
            Map<String,Object> slots=new LinkedHashMap<>();
            for(String key:snapshot.getAllKeys()){
                CompoundTag data=snapshot.getCompound(key).copy();
                // Curios 9.x does not resize its previous-stack arrays in deserializeNBT.
                // Construct at the saved effective size, including expanded two-hand/ring slots.
                int size=data.getCompound("Stacks").getInt("Size");
                Object drop=Enum.valueOf((Class)dropType,data.getString("DropRule").isEmpty()?"DEFAULT":data.getString("DropRule"));
                Object slot=constructor.newInstance(h,key,size,data.getBoolean("Visible"),data.getBoolean("HasCosmetic"),data.getBoolean("RenderToggle"),drop);
                invoke(slot,"deserializeNBT",data);slots.put(key,slot);
            }
            invoke(h,"setCurios",slots);
        }catch(ReflectiveOperationException ex){failure("Curios restore",ex);}
    }
    public static void clearFunctionalCurios(LivingEntity wearer){
        if(!ModList.get().isLoaded("curios"))return;
        try{Object h=handler(wearer);if(h!=null)for(Object slot:((Map<?,?>)invoke(h,"getCurios")).values()){
            Object stacks=invoke(slot,"getStacks");int count=(Integer)invoke(stacks,"getSlots");
            for(int i=0;i<count;i++)invoke(stacks,"setStackInSlot",i,ItemStack.EMPTY);
        }}catch(ReflectiveOperationException ex){failure("Curios disable",ex);}
    }
    public static void tickBelt(DespairPhantomEntity phantom){
        ItemStack belt=phantom.getItemBySlot(EquipmentSlot.FEET);
        if(belt.isEmpty()||!ModList.get().isLoaded("kamenridercraft"))return;
        try{
            Class<?> driver=Class.forName("com.kelco.kamenridercraft.item.base_items.RiderDriverItem");
            if(!driver.isInstance(belt.getItem()))return;
            belt.inventoryTick(phantom.level(),phantom,36,false);
            if(!(Boolean)invoke(belt.getItem(),"isTransformed",phantom))return;
            int count=driver.getField("numBaseFormItems").getInt(belt.getItem());
            for(int i=1;i<=count;i++){
                Object form=invoke(driver,"getFormItem",belt,i);
                for(Object raw:(List<?>)invoke(form,"getPotionEffectList"))phantom.addEffect(new MobEffectInstance((MobEffectInstance)raw));
            }
        }catch(ReflectiveOperationException ex){failure("Kamen Rider Craft transformation",ex);}
    }
    public static final class Actor extends FakePlayer {
        private final DespairPhantomEntity owner;
        Actor(ServerLevel level,DespairPhantomEntity owner){super(level,new GameProfile(UUID.randomUUID(),"RBD_Phantom"));this.owner=owner;}
        @Override public boolean canHarmPlayer(Player other){return !other.getUUID().equals(owner.getUUID());}
        @Override public boolean addItem(ItemStack stack){return getInventory().add(stack);}
        @Override public ItemEntity drop(ItemStack stack,boolean random,boolean trace){return null;}
    }
    public static Actor actor(DespairPhantomEntity boss){
        Actor proxy=new Actor((ServerLevel)boss.level(),boss);
        for(int i=0;i<boss.inventoryCopy.size()&&i<proxy.getInventory().getContainerSize();i++)proxy.getInventory().setItem(i,boss.inventoryCopy.get(i).copy());
        proxy.getInventory().selected=boss.selectedSlot;
        for(EquipmentSlot slot:EquipmentSlot.values())proxy.setItemSlot(slot,boss.getItemBySlot(slot).copy());
        if(PhantomRules.CURIOS.get())restoreCurios(proxy,curiosSnapshot(boss));
        align(proxy,boss);return proxy;
    }
    private static void align(Actor proxy,DespairPhantomEntity boss){
        proxy.moveTo(boss.getX(),boss.getY(),boss.getZ(),boss.getYRot(),boss.getXRot());
        proxy.setYHeadRot(boss.getYHeadRot());proxy.setDeltaMovement(boss.getDeltaMovement());proxy.setSprinting(boss.isSprinting());proxy.setOnGround(boss.onGround());
        for(var e:boss.getActiveEffects())proxy.addEffect(new MobEffectInstance(e));
    }
    /** Use a real copied weapon with a player-compatible context, preserving its native projectile/damage logic. */
    public static boolean useWeapon(DespairPhantomEntity boss,ServerPlayer target){
        if(!PhantomRules.ITEM_USE.get()||boss.getMainHandItem().isEmpty())return false;
        ItemStack stack=boss.getMainHandItem();Item item=stack.getItem();
        // Consumables and blocks are retained in the full snapshot, but aren't combat actions.
        if(item instanceof BlockItem||stack.getFoodProperties(boss)!=null||item instanceof BucketItem)return false;
        Actor proxy=actor(boss);Vec3 aim=boss.combat().aim(target).subtract(proxy.getEyePosition());
        if(item instanceof BowItem||item instanceof CrossbowItem||item instanceof TridentItem){
            double speed=item instanceof CrossbowItem?3.15:item instanceof TridentItem?2.5:Math.max(0.1,BowItem.getPowerForTime(PhantomRules.CHARGE_TICKS.get())*3);
            double flight=aim.horizontalDistance()/speed;aim=aim.add(0,0.025*flight*flight,0);
        }
        proxy.setYRot((float)(Math.toDegrees(Math.atan2(aim.z,aim.x))-90));proxy.setXRot((float)-Math.toDegrees(Math.atan2(aim.y,aim.horizontalDistance())));
        Set<UUID> before=new HashSet<>();ServerLevel level=(ServerLevel)boss.level();for(Entity e:level.getAllEntities())before.add(e.getUUID());
        var result=item.use(level,proxy,InteractionHand.MAIN_HAND);proxy.setItemInHand(InteractionHand.MAIN_HAND,result.getObject());
        if(proxy.isUsingItem()){
            ItemStack active=proxy.getUseItem();int duration=active.getUseDuration(proxy);
            active.releaseUsing(level,proxy,Math.max(0,duration-PhantomRules.CHARGE_TICKS.get()));proxy.stopUsingItem();
        }
        if(item instanceof CrossbowItem&&CrossbowItem.isCharged(proxy.getMainHandItem())){
            var shot=item.use(level,proxy,InteractionHand.MAIN_HAND);proxy.setItemInHand(InteractionHand.MAIN_HAND,shot.getObject());
        }
        boss.setItemSlot(EquipmentSlot.MAINHAND,proxy.getMainHandItem().copy());
        for(int i=0;i<boss.inventoryCopy.size();i++)boss.inventoryCopy.set(i,proxy.getInventory().getItem(i).copy());
        for(Entity e:level.getAllEntities())if(!before.contains(e.getUUID())){
            if(e instanceof net.minecraft.world.entity.projectile.Projectile projectile&&projectile.getOwner()==proxy){projectile.setOwner(boss);boss.rememberRangedWeapon(stack);}
            if(e instanceof AbstractArrow arrow)arrow.pickup=AbstractArrow.Pickup.DISALLOWED;
        }
        return result.getResult().consumesAction();
    }
    public static boolean doubleJump(DespairPhantomEntity boss){
        if(!PhantomRules.CURIOS.get()||!ModList.get().isLoaded("artifacts"))return false;
        try{
            Object holder=Class.forName("artifacts.registry.ModDataComponents").getField("DOUBLE_JUMP").get(null);
            Object component=invoke(holder,"get");
            if(!(Boolean)invoke(Class.forName("artifacts.equipment.EquipmentHelper"),"hasAbilityActive",component,boss,true))return false;
            Actor proxy=actor(boss);invoke(Class.forName("artifacts.component.ability.DoubleJump"),"jump",proxy);
            boss.setDeltaMovement(proxy.getDeltaMovement());boss.fallDistance=proxy.fallDistance;boss.hasImpulse=true;return true;
        }catch(ReflectiveOperationException ex){failure("Artifacts double jump",ex);return false;}
    }
    private PhantomEquipment(){}
}
