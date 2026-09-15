package dev.riderpack;

import com.example.generichenshin.service.MightyCombatService;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

public final class MightyCombatReturnSelfTest {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static Object state(UUID id)throws Exception{return HenshinReturn.map(MightyCombatService.class,"COMBOS").get(id);}
    private static long value(Object state,String name)throws Exception{return ((Number)HenshinReturn.field(state.getClass(),name).get(state)).longValue();}
    private static void seed(UUID id,int stage,long last,long end)throws Exception{
        var type=Class.forName(MightyCombatService.class.getName()+"$ComboState");var c=type.getDeclaredConstructor();c.setAccessible(true);var state=c.newInstance();
        HenshinReturn.field(type,"stage").setInt(state,stage);HenshinReturn.field(type,"lastAction").setLong(state,last);HenshinReturn.field(type,"cooldownUntil").setLong(state,end);
        HenshinReturn.map(MightyCombatService.class,"COMBOS").put(id,state);
    }
    public static void main(String[] args)throws Exception{
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        try{
            seed(a,3,12000,12008);seed(b,1,20000,20015);
            var savedA=MightyCombatReturn.capture(a,12003);var savedB=MightyCombatReturn.capture(b,20004);
            check(savedA.getInt("stage")==3,"combo stage captured");check(savedA.getLong("cooldown")==5,"unexpired five-tick cooldown retained");check(savedA.getLong("age")==3,"three-tick combo age retained");
            seed(a,0,25000,25015);MightyCombatReturn.clear();MightyCombatReturn.restore(a,2,savedA);MightyCombatReturn.restore(b,0,savedB);
            check(value(state(a),"stage")==3,"checkpoint stage replaces erased-branch state");check(value(state(a),"cooldownUntil")==7,"cooldown rebased to new player age");check(value(state(a),"lastAction")==-1,"combo age rebased without resetting timeout");
            check(value(state(b),"cooldownUntil")==11,"second player's heavy cooldown retained independently");check(value(state(b),"lastAction")==-4,"second player's combo age retained");
            seed(a,2,1000,1008);var expired=MightyCombatReturn.capture(a,5000);MightyCombatReturn.clear();MightyCombatReturn.restore(a,0,expired);
            check(value(state(a),"cooldownUntil")==0,"expired cooldown permits a new punch");check(-value(state(a),"lastAction")==4000,"expired combo remains expired");
            MightyCombatReturn.clear();MightyCombatReturn.restore(a,0,new CompoundTag());check(state(a)==null,"legacy checkpoint cannot retain stale combo");check(MightyCombatReturn.capture(a,100).isEmpty(),"absent combo is absent in checkpoint");
            seed(a,0,Long.MIN_VALUE,Long.MIN_VALUE);check(MightyCombatReturn.capture(a,100).isEmpty(),"uninitialized sentinel does not overflow combo age");
            System.out.println("MightyCombatReturnSelfTest: "+checks+" checks passed");
        }finally{MightyCombatReturn.clear();}
    }
}
