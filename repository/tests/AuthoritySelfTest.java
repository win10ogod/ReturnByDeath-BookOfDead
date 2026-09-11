import com.google.gson.*;
import dev.rbd.io.AuthorityRoster;
import dev.rbd.core.LoopStateMachine;
import java.util.*;
public final class AuthoritySelfTest {
    private static int checks;
    static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    static void rejects(Runnable r,String m){checks++;try{r.run();}catch(RuntimeException e){return;}throw new AssertionError(m);}
    public static void main(String[] args){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
        JsonObject legacy=new JsonObject();legacy.addProperty("holder",a.toString());legacy.addProperty("name","A");legacy.addProperty("miasma",7);
        JsonObject known=new JsonObject();known.addProperty(c.toString(),"memory");legacy.add("knowledge",known);legacy.addProperty("customStoryField","preserved");
        AuthorityRoster r=new AuthorityRoster(legacy);
        ok(r.activeIds().equals(List.of(a)),"legacy owner migrated");ok(r.person(a).get("miasma").getAsInt()==7,"legacy miasma retained");
        ok(r.person(a).get("knowledge").equals(known),"legacy knowledge retained");ok(r.person(a).has("customStoryField"),"unknown authored fields retained");
        ok(!legacy.has("holders"),"migration leaves original object intact");
        rejects(()->r.bind(b,"B",1),"configured one-person limit");r.bind(b,"B",2);
        ok(r.activeIds().size()==2,"two holders");rejects(()->r.bind(a,"A",0),"duplicate grant rejected even unlimited");rejects(()->r.bind(c,"C",2),"cap enforced");
        r.person(b).addProperty("miasma",1);ok(r.person(a).get("miasma").getAsInt()==7,"individual miasma");ok(!r.person(b).has("knowledge"),"knowledge not shared");
        r.person(a).addProperty("memorySealed",true);ok(!r.person(b).has("memorySealed"),"individual story state");
        ok(!r.hasRoom(1)&&r.contains(a)&&r.contains(b),"lower cap never silently revokes");
        r.unbind(a);ok(!r.contains(a)&&r.contains(b),"targeted removal");ok(r.person(a).has("knowledge"),"removal preserves memories");
        r.bind(a,"A renamed",2);ok(r.person(a).get("name").getAsString().equals("A renamed")&&r.person(a).has("knowledge"),"regrant preserves identity history");
        rejects(()->r.unbind(c),"cannot remove unknown owner");
        AuthorityRoster reloaded=new AuthorityRoster(JsonParser.parseString(r.json().toString()).getAsJsonObject());
        ok(reloaded.activeIds().size()==2&&reloaded.person(a).equals(r.person(a)),"serialized roster reload");
        r.unbind(b);ok(reloaded.contains(b),"reloaded state isolated");
        rejects(()->r.hasRoom(-1),"negative limit rejected");
        r.bind(c,"C",Integer.MAX_VALUE);ok(r.contains(c),"high configured limit honored");
        AuthorityRoster unlimited=new AuthorityRoster(new JsonObject());for(int i=0;i<2048;i++)unlimited.bind(new UUID(0,i),"P"+i,0);
        ok(unlimited.activeIds().size()==2048,"zero means unlimited without hidden small cap");
        JsonObject future=r.json().deepCopy();future.addProperty("schema",99);rejects(()->new AuthorityRoster(future),"unknown schema not downgraded");
        JsonObject bad=r.json().deepCopy();bad.getAsJsonObject("holders").getAsJsonObject(a.toString()).addProperty("holder",b.toString());rejects(()->new AuthorityRoster(bad),"UUID mismatch rejected");
        var state=new LoopStateMachine(2);state.bind(a);state.acceptCheckpoint(new LoopStateMachine.Checkpoint(UUID.randomUUID(),1,UUID.randomUUID()));state.bind(b);
        ok(state.phase()==LoopStateMachine.Phase.ACTIVE,"second grant does not replace checkpoint");
        var death=state.beginDeath(b,UUID.randomUUID());ok(death.holder().equals(b),"second holder can initiate return");
        rejects(()->state.beginDeath(a,UUID.randomUUID()),"one transaction for shared world");rejects(()->state.bind(c),"binding blocked during return");state.committed(death.requestId());
        ok(state.loops().intValue()==1,"shared return commits once");
        System.out.println("AuthoritySelfTest: "+checks+" checks passed");
    }
}
