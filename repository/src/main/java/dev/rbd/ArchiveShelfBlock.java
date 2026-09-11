package dev.rbd;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerPlayer;
import dev.rbd.runtime.*;
public final class ArchiveShelfBlock extends Block {
    public static final MapCodec<ArchiveShelfBlock> CODEC=simpleCodec(ArchiveShelfBlock::new);
    public static final IntegerProperty INDEX=IntegerProperty.create("index",0,15);
    public ArchiveShelfBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(INDEX,0));}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(INDEX);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer p&&GameSession.current!=null)try{ArchiveLibrary.browse(GameSession.current,p,pos,state.getValue(INDEX),0);}catch(Exception e){p.displayClientMessage(net.minecraft.network.chat.Component.literal("RBD: "+e.getMessage()),true);}
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
