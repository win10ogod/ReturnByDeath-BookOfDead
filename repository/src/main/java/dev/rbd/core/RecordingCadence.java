package dev.rbd.core;

/** A world-time schedule; old worlds receive the new recording rule through normal rule defaults. */
public final class RecordingCadence {
    private RecordingCadence(){}
    public static boolean due(long tick,int interval){
        if(interval<1)throw new IllegalArgumentException("Positive memory recording interval required");
        return Math.floorMod(tick,interval)==0;
    }
    public static boolean imageDue(long tick,long previous,int interval){
        return previous==Long.MIN_VALUE||tick<previous||tick-previous>=interval;
    }
}
