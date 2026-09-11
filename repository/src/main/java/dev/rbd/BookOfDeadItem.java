package dev.rbd;
import dev.rbd.runtime.GameSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
public final class BookOfDeadItem extends Item {
    public BookOfDeadItem(Properties p){super(p);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer p&&GameSession.current!=null){
            String id=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("rbd_book");
            if(id.isEmpty())p.displayClientMessage(Component.translatable("message.rbd.empty_book"),true);
            else try{GameSession.current.open(p,id);}catch(Exception e){p.displayClientMessage(Component.literal("RBD: "+e.getMessage()),true);}
        }return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
