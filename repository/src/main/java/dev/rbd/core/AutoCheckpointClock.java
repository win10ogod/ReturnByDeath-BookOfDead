package dev.rbd.core;

/** Online ticks only; a due checkpoint waits for a continuous safe window. */
public final class AutoCheckpointClock {
    private long elapsed,safe;
    public AutoCheckpointClock(long elapsed){this.elapsed=Math.max(0,elapsed);}
    public long elapsed(){return elapsed;}
    public void reset(){elapsed=0;safe=0;}
    public boolean tick(boolean enabled,boolean occupied,boolean safeNow,int interval,int quietTicks){
        if(!enabled){reset();return false;}
        if(!occupied){safe=0;return false;}
        elapsed=Math.min((long)Integer.MAX_VALUE,elapsed+1);
        safe=safeNow?Math.min((long)Integer.MAX_VALUE,safe+1):0;
        return safeNow&&elapsed>=interval&&safe>=quietTicks;
    }
}
