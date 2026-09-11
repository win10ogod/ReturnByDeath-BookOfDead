package dev.rbd.client;

import dev.rbd.ImmersionConfig;
import dev.rbd.core.MortalExperience;
import dev.rbd.memory.MemoryFrame;
import dev.rbd.network.RbdNetwork;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public final class MemoryScreen extends Screen {
    public final String session;
    private DynamicTexture texture;
    private ResourceLocation location;
    private MemoryFrame frame;
    private String caption="";
    private int captionLife;
    private long sequence,frameStarted;
    private final long entered=System.nanoTime();
    private boolean acknowledge,clientImage;
    private double dwell;
    private MortalExperience.Profile profile=MortalExperience.profile(20,null);

    MemoryScreen(String session,String title){super(Component.literal(title));this.session=session;ExperienceAudio.enterReading();}
    public void frame(MemoryFrame next,long sequence,double dwell) throws java.io.IOException {
        this.sequence=sequence;this.dwell=dwell;frameStarted=System.nanoTime();acknowledge=true;frame=next;
        profile=MortalExperience.profile(next.health(),next.body());
        if(!next.caption().isBlank()){caption=next.caption();captionLife=dev.rbd.RbdConfig.CAPTION_TICKS.get();}
        if(next.pixels().length>0||(next.png()!=null&&!next.png().isEmpty())){
            clientImage=next.png()!=null&&!next.png().isEmpty();NativeImage pixels;
            if(clientImage)pixels=NativeImage.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(next.png())));
            else {
                pixels=new NativeImage(next.width(),next.height(),false);
                for(int y=0;y<next.height();y++)for(int x=0;x<next.width();x++){
                    int argb=next.pixels()[y*next.width()+x];pixels.setPixelRGBA(x,y,(argb&0xFF00FF00)|((argb&0xFF)<<16)|((argb>>16)&0xFF));
                }
            }
            if(texture!=null)Minecraft.getInstance().getTextureManager().release(location);
            texture=new DynamicTexture(pixels);location=Minecraft.getInstance().getTextureManager().register("rbd_memory",texture);
        }
        for(var sound:next.sounds())ExperienceAudio.recorded(sound.id(),sound.volume(),sound.pitch());
    }
    private double elapsed(){return (System.nanoTime()-frameStarted)/1_000_000_000.0;}
    public void presented(){
        if(acknowledge&&frame!=null&&(System.nanoTime()-entered)>=dev.rbd.RbdConfig.READ_FADE.get()*1_000_000_000L&&(!profile.terminal()||elapsed()>=dwell)){
            var ack=RbdNetwork.message("experienced");ack.addProperty("session",session);ack.addProperty("sequence",sequence);RbdClient.send(ack);acknowledge=false;
        }
    }
    public boolean hasClientImage(){return clientImage;}
    public long sequence(){return sequence;}
    public boolean experiencingDeath(){return profile.terminal();}
    public boolean deathIsSilent(){return profile.terminal()&&MortalExperience.ending(elapsed(),dwell,ImmersionConfig.reduced()).silent();}
    public double deathElapsed(){return profile.terminal()?elapsed():0;}
    public void completed(){Minecraft.getInstance().setScreen(null);ImmersionOverlay.afterReading(profile);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){
        if(dev.rbd.RbdConfig.READ_LOCK.get())movementKeys().forEach(key->key.setDown(false));
        if(captionLife>0)captionLife--;
        float audible=profile.terminal()?MortalExperience.ending(elapsed(),dwell,ImmersionConfig.reduced()).audible():1;
        ExperienceAudio.tick(profile,audible);
    }
    @Override public void onClose(){RbdClient.send(RbdNetwork.message("close"));super.onClose();}
    @Override public void removed(){movementKeys().forEach(key->key.setDown(false));if(texture!=null){Minecraft.getInstance().getTextureManager().release(location);texture=null;}ExperienceAudio.leaveReading();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_F8){ImmersionConfig.REDUCED.set(!ImmersionConfig.REDUCED.get());ImmersionConfig.SPEC.save();return true;}
        if(!dev.rbd.RbdConfig.READ_LOCK.get())for(var mapping:movementKeys())if(mapping.matches(key,scan)){mapping.setDown(true);return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean keyReleased(int key,int scan,int modifiers){for(var mapping:movementKeys())if(mapping.matches(key,scan)){mapping.setDown(false);return true;}return super.keyReleased(key,scan,modifiers);}
    private static java.util.List<KeyMapping> movementKeys(){var o=Minecraft.getInstance().options;return java.util.List.of(o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint);}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.fill(0,0,width,height,0xFF030306);
        if(texture!=null){
            int w=texture.getPixels().getWidth(),h=texture.getPixels().getHeight();float scale=Math.min((float)width/w,(float)height/h);
            int dw=(int)(w*scale),dh=(int)(h*scale);g.blit(location,(width-dw)/2,(height-dh)/2,dw,dh,0,0,w,h,w,h);
        }
        double sinceEntry=(System.nanoTime()-entered)/1_000_000_000.0;
        g.flush();g.pose().pushPose();g.pose().translate(0,0,400);
        var ending=profile.terminal()?MortalExperience.ending(elapsed(),dwell,ImmersionConfig.reduced()):new MortalExperience.Ending(0,0,1,false,false);
        SensoryVisuals.body(g,width,height,profile,sinceEntry,ending.closing());
        SensoryVisuals.darkness(g,width,height,Math.max(ending.darkness(),dev.rbd.RbdConfig.READ_FADE.get()==0?0:1-MortalExperience.smooth((float)(sinceEntry/dev.rbd.RbdConfig.READ_FADE.get()))));
        // Identity fades into the lived scene; no permanent video title, death counter or retry prompt.
        if(sinceEntry<dev.rbd.RbdConfig.READ_TITLE.get()&&!profile.terminal()){
            int alpha=(int)(255*MortalExperience.unit((float)(dev.rbd.RbdConfig.READ_TITLE.get()-sinceEntry)));
            if(alpha>4)g.drawString(font,title,12,12,(alpha<<24)|0xE8D6B2,false);
        }
        if(profile.terminal()&&!ending.silent()){
            var sensation=Component.translatable("screen.rbd.ending."+profile.kind().name().toLowerCase(java.util.Locale.ROOT));
            g.drawCenteredString(font,sensation,width/2,height*3/4,0xFFBDB5B3);
        }else if(captionLife>0&&!ending.silent()){
            int y=height-48;for(var line:font.split(Component.literal(caption),Math.max(40,width-40))){g.drawCenteredString(font,line,width/2,y,0xFFD5CEC4);y-=11;}
        }
        // A quiet, always available exit is retained even during silence.
        g.drawString(font,Component.translatable("screen.rbd.readonly"),12,height-15,0xFF827F87,false);
        g.flush();g.pose().popPose();
    }
}
