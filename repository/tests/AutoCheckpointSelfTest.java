import dev.rbd.core.AutoCheckpointClock;
public final class AutoCheckpointSelfTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var clock=new AutoCheckpointClock(0);
        for(int i=0;i<199;i++)check(!clock.tick(true,true,true,200,10),"never saves early");
        check(clock.tick(true,true,true,200,10),"saves after interval and safe window");clock.reset();
        for(int i=0;i<220;i++)check(!clock.tick(true,true,false,200,10),"danger postpones due save");
        for(int i=0;i<9;i++)check(!clock.tick(true,true,true,200,10),"requires a continuous safe window");
        check(clock.tick(true,true,true,200,10),"postponed save executes when safe");
        check(!clock.tick(true,true,false,200,10),"danger cancels readiness immediately");
        long elapsed=clock.elapsed();for(int i=0;i<100;i++)check(!clock.tick(true,false,true,200,10),"empty server cannot save");
        check(clock.elapsed()==elapsed,"empty server consumes no online interval");
        check(!clock.tick(false,true,true,1,0)&&clock.elapsed()==0,"disable cancels countdown");
        clock=new AutoCheckpointClock(11999);check(clock.tick(true,true,true,12000,0),"elapsed online time survives reload");
        clock=new AutoCheckpointClock(Integer.MAX_VALUE-1L);check(clock.tick(true,true,true,Integer.MAX_VALUE,0),"full integer interval remains supported");
        check(clock.tick(true,true,true,Integer.MAX_VALUE,0),"long uptime does not overflow");
        System.out.println("AutoCheckpointSelfTest: interval, disabled, empty-server, danger deferral, safe window, reload and integer boundary PASS");
    }
}
