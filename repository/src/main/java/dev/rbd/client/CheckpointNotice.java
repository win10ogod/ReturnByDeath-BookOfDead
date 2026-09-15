package dev.rbd.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A single lightweight notice that remains visible while checkpoint ticks are paused. */
public final class CheckpointNotice {
    private enum State { HIDDEN, SAVING, SAVED, FAILED }
    private static State state=State.HIDDEN;
    private static long expires;
    private static final Component SAVING=Component.translatable("message.rbd.checkpoint_saving");
    private static final Component SAVED=Component.translatable("message.rbd.checkpoint_saved");
    private static final Component FAILED=Component.translatable("message.rbd.checkpoint_failed");

    public static void saving(){state=State.SAVING;expires=0;}
    public static void complete(){
        if(state==State.SAVING){state=State.SAVED;expires=System.nanoTime()+5_000_000_000L;}
    }
    public static void failed(){
        if(state==State.SAVING||state==State.SAVED){state=State.FAILED;expires=System.nanoTime()+8_000_000_000L;}
    }
    public static void clear(){state=State.HIDDEN;expires=0;}
    public static void render(GuiGraphics graphics){
        long now=System.nanoTime();
        if(state!=State.SAVING&&expires!=0&&now>=expires)clear();
        var mc=Minecraft.getInstance();
        if(state==State.HIDDEN||mc.options.hideGui)return;
        Component text=switch(state){case SAVING->SAVING;case SAVED->SAVED;case FAILED->FAILED;default->Component.empty();};
        int color=switch(state){case SAVING->0xFFE1C16E;case SAVED->0xFF9FE3AB;default->0xFFFFA39A;};
        int width=mc.font.width(text)+24,right=graphics.guiWidth()-8,left=Math.max(4,right-width),top=graphics.guiHeight()-64;
        graphics.fill(left,top,right,top+21,0xD0181B23);
        graphics.fill(left,top,left+2,top+21,color);
        int dot=state==State.SAVING?(int)((now/350_000_000L)%3):1;
        graphics.fill(left+8,top+8-dot,left+11,top+11-dot,color);
        graphics.drawString(mc.font,text,left+16,top+7,color,false);
    }
    private CheckpointNotice(){}
}
