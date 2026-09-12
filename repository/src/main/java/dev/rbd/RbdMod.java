package dev.rbd;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
@Mod(RbdMod.ID)
public final class RbdMod {
    public static final String ID="rbd";
    public RbdMod(IEventBus bus,ModContainer container){
        RbdConfig.initialize();
        ModContent.BLOCKS.register(bus);ModContent.ITEMS.register(bus);ModContent.TABS.register(bus);ModContent.ENTITIES.register(bus);
        bus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e)->e.put(ModContent.PHANTOM.get(),dev.rbd.phantom.DespairPhantomEntity.attributes().build()));
        container.registerConfig(ModConfig.Type.COMMON,RbdConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT,ImmersionConfig.SPEC);
        bus.addListener(dev.rbd.network.RbdNetwork::register);
        NeoForge.EVENT_BUS.register(new RbdEvents());
    }
}
