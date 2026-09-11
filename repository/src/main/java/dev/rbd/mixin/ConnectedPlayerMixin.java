package dev.rbd.mixin;
import dev.rbd.runtime.ConnectedReturn;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.PlayerChunkSender;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ConnectedPlayerMixin {
    @Inject(method="onDisconnect",at=@At("HEAD"),cancellable=true)
    private void rbd$deferLogout(net.minecraft.network.DisconnectionDetails details,CallbackInfo ci){
        if(ConnectedReturn.defer(()->((ServerGamePacketListenerImpl)(Object)this).onDisconnect(details)))ci.cancel();
    }
    @Inject(method="handleCustomPayload",at=@At("HEAD"),cancellable=true)
    private void rbd$discardOldBranchPayload(CallbackInfo ci){if(ConnectedReturn.paused())ci.cancel();}
    @Inject(method="handleChat",at=@At("HEAD"),cancellable=true)
    private void rbd$deferChat(net.minecraft.network.protocol.game.ServerboundChatPacket packet,CallbackInfo ci){
        if(ConnectedReturn.defer(()->((ServerGamePacketListenerImpl)(Object)this).handleChat(packet)))ci.cancel();
    }
    @Inject(method="handleChatCommand",at=@At("HEAD"),cancellable=true)
    private void rbd$deferCommand(net.minecraft.network.protocol.game.ServerboundChatCommandPacket packet,CallbackInfo ci){
        if(ConnectedReturn.defer(()->((ServerGamePacketListenerImpl)(Object)this).handleChatCommand(packet)))ci.cancel();
    }
    @Inject(method="handleSignedChatCommand",at=@At("HEAD"),cancellable=true)
    private void rbd$deferSignedCommand(net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket packet,CallbackInfo ci){
        if(ConnectedReturn.defer(()->((ServerGamePacketListenerImpl)(Object)this).handleSignedChatCommand(packet)))ci.cancel();
    }
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void rbd$keepSocket(CallbackInfo ci){
        if(ConnectedReturn.paused()){
            ((CommonConnectionAccess)this).rbd$keepAlive();ci.cancel();
        }
    }
    @Inject(method={"handlePlayerInput","handleMoveVehicle","handleAcceptTeleportPacket","handleRecipeBookSeenRecipePacket","handleRecipeBookChangeSettingsPacket","handleSeenAdvancements","handleCustomCommandSuggestions","handleSetCommandBlock","handleSetCommandMinecart","handlePickItem","handleRenameItem","handleSetBeaconPacket","handleSetStructureBlock","handleSetJigsawBlock","handleJigsawGenerate","handleSelectTrade","handleEditBook","handleEntityTagQuery","handleContainerSlotStateChanged","handleBlockEntityTagQuery","handleMovePlayer","handlePlayerAction","handleUseItemOn","handleUseItem","handleTeleportToEntityPacket","handlePaddleBoat","handleSetCarriedItem","handleAnimate","handlePlayerCommand","handleInteract","handleClientCommand","handleContainerClose","handleContainerClick","handlePlaceRecipe","handleContainerButtonClick","handleSetCreativeModeSlot","handleSignUpdate","handlePlayerAbilities","handleClientInformation","handleChangeDifficulty","handleLockDifficulty","handleChunkBatchReceived","handleDebugSampleSubscription"},at=@At("HEAD"),cancellable=true)
    private void rbd$freezeInputs(CallbackInfo ci){if(ConnectedReturn.paused())ci.cancel();}
}
