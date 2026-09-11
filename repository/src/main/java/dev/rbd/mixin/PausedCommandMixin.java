package dev.rbd.mixin;
import dev.rbd.runtime.ConnectedReturn;
import net.minecraft.commands.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Commands.class)
public abstract class PausedCommandMixin {
    @Inject(method="performPrefixedCommand",at=@At("HEAD"),cancellable=true)
    private void rbd$queueWorldCommands(CommandSourceStack source,String command,CallbackInfo ci){
        String normalized=command.startsWith("/")?command.substring(1):command;
        if(normalized.equals("stop")||normalized.equals("rbd status"))return;
        if(ConnectedReturn.defer(()->{
            var entity=source.getEntity();var fresh=entity==null?null:source.getServer().getPlayerList().getPlayer(entity.getUUID());
            ((Commands)(Object)this).performPrefixedCommand(fresh==null?source:fresh.createCommandSourceStack(),command);
        }))ci.cancel();
    }
}
