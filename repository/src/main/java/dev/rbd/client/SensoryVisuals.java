package dev.rbd.client;

import dev.rbd.ImmersionConfig;
import dev.rbd.core.MortalExperience;
import net.minecraft.client.gui.GuiGraphics;

/** Continuous peripheral shading. No strobe, camera displacement, fabricated attacker or HUD meter. */
public final class SensoryVisuals {
    public static void body(GuiGraphics g,int w,int h,MortalExperience.Profile p,double seconds,float closing){
        if(ImmersionConfig.REDUCED.get())return;
        float strength=ImmersionConfig.VISUAL.get().floatValue();
        float pulse=(float)((1-Math.cos(seconds*(3.2+p.stress()*4)))*0.5)*0.055F*p.stress();
        float edge=(0.12F+p.stress()*0.20F+closing*0.50F+pulse)*strength;
        int tint=switch(p.kind()){
            case DROWNING,SUFFOCATION -> 0x09151E;
            case FREEZING -> 0xA4BCC6;
            case BURNING -> 0x401811;
            default -> 0x170D15;
        };
        for(int i=0;i<24;i++){
            int x=w*i/100,y=h*i/100;
            int nextX=Math.max(x+1,w*(i+1)/100),nextY=Math.max(y+1,h*(i+1)/100);
            float fade=1-i/24F;int color=((int)(255*edge*fade*fade)<<24)|tint;
            g.fill(x,y,w-x,nextY,color);g.fill(x,h-nextY,w-x,h-y,color);
            g.fill(x,nextY,nextX,h-nextY,color);g.fill(w-nextX,nextY,w-x,h-nextY,color);
        }
        if(closing>0){
            int lid=(int)(h*0.5*MortalExperience.smooth(closing)*strength);
            g.fill(0,0,w,lid,0xFF030306);g.fill(0,h-lid,w,h,0xFF030306);
        }
    }
    public static void darkness(GuiGraphics g,int w,int h,float amount){
        int alpha=(int)(255*MortalExperience.unit(amount));if(alpha>0)g.fill(0,0,w,h,(alpha<<24)|0x020204);
    }
    private SensoryVisuals(){}
}
