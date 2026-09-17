package dev.maskedinvasion;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MaskedInvasion.ID)
public final class MaskedInvasion {
    public static final String ID="masked_invasion";
    public static final Logger LOG=LoggerFactory.getLogger(ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,ID);
    public static final DeferredHolder<EntityType<?>,EntityType<InvaderSummonEntity>> INVADER=ENTITIES.register("invader",()->EntityType.Builder.of(InvaderSummonEntity::new,MobCategory.MONSTER).sized(0.6f,1.95f).clientTrackingRange(10).build(ID+":invader"));
    public MaskedInvasion(IEventBus bus){ENTITIES.register(bus);bus.addListener(MaskedInvasion::attributes);bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e)->e.enqueueWork(InvasionRules::init));}
    private static void attributes(EntityAttributeCreationEvent event){event.put(INVADER.get(),InvaderSummonEntity.attributes().build());}
    public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(ID,path);}
}
