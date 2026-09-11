package dev.rbd.core;

import dev.rbd.memory.SomaticState;

/** Cross-media presentation rules, not purported biological or canonical numerical formulas. */
public final class MortalExperience {
    public enum Kind { DROWNING, BURNING, FREEZING, FALL, SUFFOCATION, VOID, VIOLENCE, UNKNOWN }
    public record Profile(Kind kind,float stress,float breathlessness,boolean terminal) {}
    public record Ending(float closing,float darkness,float audible,boolean silent,boolean complete) {}
    public static Profile profile(float health,SomaticState s) {
        if(s==null)return new Profile(Kind.UNKNOWN,unit(1-health/20F),0,false);
        String cause=s.damageType()==null?"":s.damageType();
        Kind kind=switch(cause){
            case "drown" -> Kind.DROWNING;
            case "inFire","onFire","lava","hotFloor" -> Kind.BURNING;
            case "freeze" -> Kind.FREEZING;
            case "fall","flyIntoWall" -> Kind.FALL;
            case "inWall","cramming" -> Kind.SUFFOCATION;
            case "outOfWorld" -> Kind.VOID;
            case "mob","player","arrow","trident","mobProjectile","thorns","explosion","playerExplosion",
                 "stalagmite","fallingBlock","fallingAnvil","fallingStalactite","sonic_boom","sting","sweetBerryBush" -> Kind.VIOLENCE;
            default -> Kind.UNKNOWN;
        };
        if(kind==Kind.UNKNOWN){
            if(s.submerged())kind=Kind.DROWNING;
            else if(s.burning())kind=Kind.BURNING;
            else if(s.frozenTicks()>0)kind=Kind.FREEZING;
            else if(s.fallDistance()>3)kind=Kind.FALL;
        }
        float breath=s.submerged()?unit(1-(float)s.air()/Math.max(1,s.maxAir())):0;
        float stress=Math.max(unit(1-health/Math.max(1,s.maxHealth())),breath);
        stress=Math.max(stress,s.burning()?0.7F:0);
        stress=Math.max(stress,unit(s.frozenTicks()/140F)*0.7F);
        stress=Math.max(stress,unit(s.fallDistance()/20F));
        if(s.terminal()||s.rememberedEnding())stress=1;
        return new Profile(kind,stress,breath,s.terminal()||s.rememberedEnding());
    }
    public static Ending ending(double elapsedSeconds,double durationSeconds,boolean reduced) {
        double progress=Math.max(0,elapsedSeconds)/Math.max(0.1,durationSeconds);
        float closing=smooth((float)(progress/0.62));
        float darkness=smooth((float)((progress-0.18)/0.47));
        // Reduced effects retain the same death interval and silence, with a plain fade.
        if(reduced)closing=0;
        return new Ending(closing,darkness,1-smooth((float)((progress-0.1)/0.5)),progress>=0.65,progress>=1);
    }
    public static float unit(float x){return Math.max(0,Math.min(1,x));}
    public static float smooth(float x){x=unit(x);return x*x*(3-2*x);}
    private MortalExperience(){}
}
