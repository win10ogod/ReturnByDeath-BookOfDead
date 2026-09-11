package dev.rbd;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.*;
public final class ModContent {
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(RbdMod.ID);
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(RbdMod.ID);
    public static final DeferredBlock<ArchiveShelfBlock> SHELF=BLOCKS.register("archive_shelf",()->new ArchiveShelfBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_TILES).strength(-1,3600000)));
    public static final DeferredItem<BlockItem> SHELF_ITEM=ITEMS.registerSimpleBlockItem(SHELF);
    public static final DeferredItem<BookOfDeadItem> BOOK=ITEMS.register("book_of_dead",()->new BookOfDeadItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,RbdMod.ID);
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("archive",()->CreativeModeTab.builder().title(net.minecraft.network.chat.Component.translatable("itemGroup.rbd")).icon(()->new ItemStack(BOOK.get())).displayItems((parameters,output)->{output.accept(BOOK);output.accept(SHELF_ITEM);}).build());
    private ModContent(){}
}
