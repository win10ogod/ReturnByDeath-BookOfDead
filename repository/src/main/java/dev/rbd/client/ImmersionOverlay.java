package dev.rbd.client;

import com.google.gson.JsonObject;
import dev.rbd.ImmersionConfig;
import dev.rbd.core.MortalExperience;
import dev.rbd.memory.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Runs over the normal loading lifecycle; never replaces or prevents vanilla world loading screens. */
public final class ImmersionOverlay {
    private static boolean separated,integrated,awaitingLogin,siteRecalled;
    private static long departed,afterStarted,awakened;
    private static MortalExperience.Profile loss=MortalExperience.profile(0,null),after=MortalExperience.profile(20,null);
    private static DynamicTexture lastView;
    private static ResourceLocation lastLocation;
    private static JsonObject imprint;
    private static String imprintId="";
    public static void departing(SomaticState body,boolean local){
        var mc=Minecraft.getInstance();releaseView();
        // Preserve only what this client was actually seeing. No off-camera killer or reconstructed scene.
        if(mc.level!=null&&mc.player!=null){
            lastView=new DynamicTexture(Screenshot.takeScreenshot(mc.getMainRenderTarget()));
            lastLocation=mc.getTextureManager().register("rbd_last_perception",lastView);
        }
        separated=true;integrated=local;awaitingLogin=local;departed=System.nanoTime();loss=MortalExperience.profile(0,body);
        ExperienceAudio.enterReturn();
    }
    public static void imprint(JsonObject value){
        imprint=value.deepCopy();String id=value.get("book").getAsString();if(!id.equals(imprintId)){imprintId=id;siteRecalled=false;}
        if(separated)awaitingLogin=false;
    }
    public static boolean isSeparated(){return separated;}
    public static boolean isBlack(){return separated&&(System.nanoTime()-departed)>2_700_000_000L;}
    public static void cancel(){separated=false;releaseView();}
    public static void afterReading(MortalExperience.Profile profile){
        after=profile;afterStarted=System.nanoTime();ExperienceAudio.resetBodyClock();ExperienceAudio.cue("body.gasp",0.2F,1);
    }
    public static void tick(){
        var mc=Minecraft.getInstance();long now=System.nanoTime();
        if(separated){
            double age=(now-departed)/1_000_000_000.0;
            // Long disk work or a failed connection must remain diagnosable; this only removes the veil.
            if(age>30&&(mc.screen instanceof DisconnectedScreen||mc.screen instanceof AlertScreen||mc.screen instanceof TitleScreen)){cancel();return;}
            var ending=MortalExperience.ending(age,4,ImmersionConfig.REDUCED.get());ExperienceAudio.tick(loss,ending.audible());
            boolean playable=mc.level!=null&&mc.player!=null&&mc.screen==null&&mc.getOverlay()==null;
            if((integrated&&!awaitingLogin&&playable&&age>=4)||(!integrated&&age>=4)){
                separated=false;releaseView();awakened=now;after=loss;afterStarted=now;
                ExperienceAudio.resetBodyClock();ExperienceAudio.cue("body.gasp",0.32F,1);
            }
            return;
        }
        if(mc.level==null||mc.player==null||mc.screen instanceof MemoryScreen)return;
        if(afterStarted>0&&(now-afterStarted)<5_000_000_000L){
            float remaining=1-(now-afterStarted)/5_000_000_000F;
            ExperienceAudio.tick(new MortalExperience.Profile(after.kind(),after.stress()*remaining,after.breathlessness()*remaining,false),0.6F*remaining);
        }
        if(!ImmersionConfig.ECHOES.get()||imprint==null||siteRecalled||now-awakened<10_000_000_000L)return;
        if(!mc.level.dimension().location().toString().equals(imprint.get("dimension").getAsString()))return;
        double x=imprint.get("x").getAsDouble(),y=imprint.get("y").getAsDouble(),z=imprint.get("z").getAsDouble();
        // Close range and actual line of sight, so this cannot become a through-wall death-site radar.
        var eye=mc.player.getEyePosition();var remembered=new net.minecraft.world.phys.Vec3(x,y+1,z);
        if(eye.distanceToSqr(remembered)>25)return;
        var obstruction=mc.level.clip(new net.minecraft.world.level.ClipContext(eye,remembered,net.minecraft.world.level.ClipContext.Block.VISUAL,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
        if(obstruction.getType()!=net.minecraft.world.phys.HitResult.Type.MISS&&obstruction.getLocation().distanceToSqr(remembered)>1)return;
        siteRecalled=true;after=MortalExperience.profile(0,MemoryArchive.GSON.fromJson(imprint.get("body"),SomaticState.class));afterStarted=now;
    }
    public static void render(GuiGraphics g){
        var mc=Minecraft.getInstance();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        if(separated){
            double age=(System.nanoTime()-departed)/1_000_000_000.0;
            var ending=MortalExperience.ending(age,4,ImmersionConfig.REDUCED.get());
            g.fill(0,0,w,h,0xFF020204);
            if(lastView!=null&&!ending.silent()){
                int iw=lastView.getPixels().getWidth(),ih=lastView.getPixels().getHeight();g.blit(lastLocation,0,0,w,h,0,0,iw,ih,iw,ih);
            }
            SensoryVisuals.body(g,w,h,loss,age,ending.closing());SensoryVisuals.darkness(g,w,h,ending.darkness());
            return;
        }
        if(mc.level==null||mc.screen instanceof MemoryScreen||afterStarted==0)return;
        double age=(System.nanoTime()-afterStarted)/1_000_000_000.0;
        if(age<5&&ImmersionConfig.ECHOES.get()){
            float remaining=(float)(1-age/5);var echo=new MortalExperience.Profile(after.kind(),after.stress()*remaining,after.breathlessness()*remaining,false);
            SensoryVisuals.body(g,w,h,echo,age,0);
        }
    }
    private static void releaseView(){if(lastView!=null){Minecraft.getInstance().getTextureManager().release(lastLocation);lastView=null;}}
    private ImmersionOverlay(){}
}
