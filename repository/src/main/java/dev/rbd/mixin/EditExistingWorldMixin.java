package dev.rbd.mixin;
import dev.rbd.client.ExistingWorldRules;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EditWorldScreen.class)
public abstract class EditExistingWorldMixin extends Screen {
    @Shadow @Final private LinearLayout layout;
    @Shadow @Final private LevelStorageSource.LevelStorageAccess levelAccess;
    protected EditExistingWorldMixin(Component title){super(title);}
    @Inject(method="<init>",at=@At("TAIL"))
    private void rbd$editRules(CallbackInfo ci){
        var button=Button.builder(Component.translatable("editGamerule.title"),b->ExistingWorldRules.open(this,levelAccess)).width(200).build();
        layout.spacing(3);layout.addChild(button);addRenderableWidget(button);
    }
}
