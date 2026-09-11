import dev.rbd.core.MortalExperience;
import dev.rbd.core.CheckpointPolicy;
import dev.rbd.memory.*;
import java.util.List;

public final class MortalitySelfTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var calm=new SomaticState(20,300,300,false,false,0,0,0,"",false);
        var submerged=new SomaticState(20,10,300,true,false,0,0,0,"",false);
        var drowned=new SomaticState(20,-10,300,true,false,0,0,2,"drown",true);
        check(MortalExperience.profile(20,calm).stress()==0,"healthy body does not invent distress");
        check(MortalExperience.profile(20,submerged).breathlessness()>0.9F,"recorded air loss drives breathlessness before death");
        check(MortalExperience.profile(0,drowned).kind()==MortalExperience.Kind.DROWNING,"actual drowning stays distinct");
        check(MortalExperience.profile(0,new SomaticState(20,300,300,false,true,0,0,4,"lava",true)).kind()==MortalExperience.Kind.BURNING,"actual fire death stays distinct");
        check(MortalExperience.profile(0,new SomaticState(20,300,300,false,false,0,30,20,"fall",true)).kind()==MortalExperience.Kind.FALL,"actual fall stays distinct");
        check(MortalExperience.profile(0,new SomaticState(20,300,300,false,false,140,0,2,"freeze",true)).kind()==MortalExperience.Kind.FREEZING,"freezing stays distinct");
        var remembered=drowned.asExperience();
        check(!remembered.terminal()&&remembered.rememberedEnding(),"witnessing death cannot end the reader's own life");
        check(!remembered.asExperience().terminal()&&remembered.asExperience().rememberedEnding(),"nested memories preserve ending without creating actual death");
        check(MortalExperience.profile(0,remembered).terminal(),"nested death still presents its experienced ending");
        var before=MortalExperience.ending(0,4,false);var silence=MortalExperience.ending(3,4,false);var end=MortalExperience.ending(4,4,false);
        check(before.darkness()==0&&!before.silent()&&!before.complete(),"a death does not skip its last perception");
        check(silence.darkness()==1&&silence.audible()==0&&silence.silent()&&!silence.complete(),"silence exists before return to the body");
        check(end.complete(),"ending completes only after its configured dwell");
        var reduced=MortalExperience.ending(3,4,true);
        check(reduced.closing()==0&&reduced.silent()==silence.silent()&&reduced.complete()==silence.complete(),"reduced effects preserve duration and ending");
        check(MortalExperience.ending(4,4,true).complete(),"reduced effects do not trap the reader");
        var old=new MemoryFrame(1,"minecraft:overworld",0,0,0,0,0,20,"",List.of(),List.of(),"TEST",1,1,new int[]{0xFF000000},"");
        var legacy=MemoryArchive.GSON.fromJson(MemoryArchive.GSON.toJson(old),MemoryFrame.class);
        check(legacy.body()==null&&!MortalExperience.profile(legacy.health(),legacy.body()).terminal(),"legacy frames remain readable without invented death evidence");
        var modern=new MemoryFrame(1,"minecraft:overworld",0,0,0,0,0,0,"",List.of(),List.of(),"TEST",1,1,new int[]{0xFF000000},"",drowned);
        check(MemoryArchive.GSON.fromJson(MemoryArchive.GSON.toJson(modern),MemoryFrame.class).body().equals(drowned),"raw body evidence survives archive JSON");
        double twoLethalHits=(double)Float.MAX_VALUE+Float.MAX_VALUE;
        var large=new SomaticState(20,300,300,false,false,0,0,twoLethalHits,"genericKill",true);
        check(MemoryArchive.GSON.fromJson(MemoryArchive.GSON.toJson(large),SomaticState.class).damage()==twoLethalHits,"successive maximum-float hits remain finite and untruncated");
        var policy=new CheckpointPolicy.Authored();
        check(policy.mayAdvance(new CheckpointPolicy.Context("trapped",true,true,true,false,CheckpointPolicy.Connection.CONNECTED)),"an authored point need not promise comfort or rescue");
        check(!policy.mayAdvance(new CheckpointPolicy.Context("bed",false,true,true,false,CheckpointPolicy.Connection.CONNECTED)),"ordinary actions cannot choose a return point");
        check(!policy.mayAdvance(new CheckpointPolicy.Context("lost",true,true,true,false,CheckpointPolicy.Connection.LOST)),"lost connection cannot silently advance an authored point");
        System.out.println("MortalitySelfTest: "+checks+" checks passed");
    }
}
