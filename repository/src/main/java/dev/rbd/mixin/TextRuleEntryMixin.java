package dev.rbd.mixin;
import dev.rbd.rules.TextRuleValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
@Mixin(EditGameRulesScreen.IntegerRuleEntry.class)
public abstract class TextRuleEntryMixin {
    @Shadow @Final private EditBox input;
    @Unique private Component rbd$label;
    @Inject(method="<init>",at=@At("TAIL"))
    private void rbd$text(EditGameRulesScreen screen,Component label,List<FormattedCharSequence> tooltip,String narration,GameRules.IntegerValue value,CallbackInfo ci){
        if(value instanceof TextRuleValue){rbd$label=Component.literal(label.getString().replaceFirst("^RBD · ",""));input.setMaxLength(Integer.MAX_VALUE);input.setValue(value.serialize());}
    }
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void rbd$renderText(GuiGraphics g,int index,int top,int left,int width,int height,int mouseX,int mouseY,boolean hovering,float partial,CallbackInfo ci){
        if(rbd$label==null)return;
        int inputWidth=Math.min(110,Math.max(70,width/3));input.setWidth(inputWidth);
        var font=Minecraft.getInstance().font;int y=top;for(var line:font.split(rbd$label,width-inputWidth-10)){g.drawString(font,line,left,y,-1,false);y+=10;if(y>top+10)break;}
        input.setX(left+width-inputWidth-1);input.setY(top);input.render(g,mouseX,mouseY,partial);ci.cancel();
    }
}
