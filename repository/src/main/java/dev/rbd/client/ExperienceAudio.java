package dev.rbd.client;

import dev.rbd.ImmersionConfig;
import dev.rbd.core.MortalExperience;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/** Only marked, recorded sounds and body cues belong to the subjective scene. */
@EventBusSubscriber(modid="rbd",value=Dist.CLIENT)
public final class ExperienceAudio {
    public static final class SubjectiveSound extends SimpleSoundInstance {
        SubjectiveSound(ResourceLocation id,float volume,float pitch){
            super(id,SoundSource.PLAYERS,volume,pitch,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,0,0,0,true);
        }
    }
    private static long heartbeat,breath;
    private static boolean silenced,pausedWorld;
    private static final java.util.List<SubjectiveSound> owned=new java.util.ArrayList<>();
    public static void resetBodyClock(){heartbeat=breath=0;silenced=false;}
    public static void enterReading(){stopOwned();Minecraft.getInstance().getSoundManager().pause();pausedWorld=true;resetBodyClock();}
    public static void leaveReading(){stopOwned();if(pausedWorld)Minecraft.getInstance().getSoundManager().resume();pausedWorld=false;}
    public static void enterReturn(){stopOwned();Minecraft.getInstance().getSoundManager().stop();pausedWorld=false;resetBodyClock();}
    public static void recorded(String id,float volume,float pitch){
        var location=ResourceLocation.tryParse(id);if(location!=null){var sound=new SubjectiveSound(location,volume,pitch);owned.add(sound);Minecraft.getInstance().getSoundManager().play(sound);}
    }
    public static void cue(String id,float volume,float pitch){recorded("rbd:"+id,volume*ImmersionConfig.AUDIO.get().floatValue(),pitch);}
    private static void stopOwned(){for(var sound:owned)Minecraft.getInstance().getSoundManager().stop(sound);owned.clear();}
    public static void silence(){if(!silenced){stopOwned();silenced=true;}}
    public static void tick(MortalExperience.Profile profile,float audible){
        if(audible<=0.005F){silence();return;}
        silenced=false;long now=System.nanoTime();float stress=profile.stress();
        owned.removeIf(sound->!Minecraft.getInstance().getSoundManager().isActive(sound));
        if(stress>0.18F&&now>=heartbeat){
            cue("body.heartbeat",(0.1F+0.23F*stress)*audible,0.85F+0.25F*stress);
            heartbeat=now+(long)((1.1-0.65*stress)*1_000_000_000L);
        }
        if(stress>0.35F&&now>=breath){
            String id=profile.kind()==MortalExperience.Kind.DROWNING||profile.kind()==MortalExperience.Kind.SUFFOCATION?"body.airless":"body.breath";
            cue(id,(0.13F+stress*0.13F)*audible,0.9F+0.15F*stress);
            breath=now+(long)((2.7-1.0*stress)*1_000_000_000L);
        }
    }
    @SubscribeEvent public static void sound(PlaySoundEvent e){
        if((Minecraft.getInstance().screen instanceof MemoryScreen||ImmersionOverlay.isSeparated())&&!(e.getOriginalSound() instanceof SubjectiveSound))e.setSound(null);
    }
    private ExperienceAudio(){}
}
