import dev.rbd.core.*;
import java.util.*;
public final class CoreSelfTest {
    private static int count;
    static void ok(boolean b,String name){count++;if(!b)throw new AssertionError(name);}
    static void rejects(Runnable r,String name){count++;try{r.run();}catch(RuntimeException expected){return;}throw new AssertionError(name);}
    public static void main(String[] args){
        UUID owner=UUID.randomUUID(),other=UUID.randomUUID(),branch=UUID.randomUUID(),cp=UUID.randomUUID();
        var e=new LoopStateMachine();ok(e.phase()==LoopStateMachine.Phase.UNBOUND,"unbound");
        rejects(()->e.beginDeath(owner,UUID.randomUUID()),"unbound death");e.bind(owner);
        rejects(()->e.bind(other),"only one holder");
        rejects(()->e.beginDeath(owner,UUID.randomUUID()),"no checkpoint no return");
        e.acceptCheckpoint(new LoopStateMachine.Checkpoint(cp,1,branch));
        rejects(()->e.acceptCheckpoint(new LoopStateMachine.Checkpoint(UUID.randomUUID(),1,branch)),"no backwards checkpoint");
        rejects(()->e.beginDeath(other,UUID.randomUUID()),"non-holder does not return");
        UUID id=UUID.randomUUID();var intent=e.beginDeath(owner,id);ok(intent.checkpoint().equals(cp),"right checkpoint");
        rejects(()->e.beginDeath(owner,UUID.randomUUID()),"duplicate death");
        rejects(()->e.committed(UUID.randomUUID()),"wrong receipt");e.committed(id);
        ok(e.loops().intValueExact()==1,"single increment");rejects(()->e.committed(id),"idempotency guard");
        e.failClosed();rejects(()->e.beginDeath(owner,UUID.randomUUID()),"fail closed");
        var k=new IdentityKnowledge();k.learn(new IdentityKnowledge.Evidence(other,"known",true,IdentityKnowledge.Origin.HEARSAY));
        ok(!k.recognizes(other),"hearsay not acquaintance");
        k.learn(new IdentityKnowledge.Evidence(other,"known",false,IdentityKnowledge.Origin.DIRECT_ENCOUNTER));
        ok(!k.recognizes(other),"name alone insufficient");
        k.learn(new IdentityKnowledge.Evidence(other,"known",true,IdentityKnowledge.Origin.DIRECT_ENCOUNTER));
        ok(k.recognizes(other),"name and face");
        k.learn(new IdentityKnowledge.Evidence(other,"known",false,IdentityKnowledge.Origin.HEARSAY));ok(k.recognizes(other),"no evidence downgrade");
        UUID third=UUID.randomUUID();var contact=new IdentityKnowledge.Evidence(third,"third",true,IdentityKnowledge.Origin.DIRECT_ENCOUNTER);
        var scene=new DeathBook.MemoryScene(1,"minecraft:overworld",0,64,0,"met",Set.of(contact));
        var book=new DeathBook(UUID.randomUUID(),other,UUID.randomUUID(),branch,"known",false,List.of(scene));
        rejects(()->new ReplaySession(owner,book,new IdentityKnowledge()),"unauthorized read");
        var replay=new ReplaySession(owner,book,k);ok(replay.next().isPresent(),"scene available");ok(k.recognizes(third),"arc9 transitive recognition");
        rejects(()->replay.seek(0),"no arbitrary seeking");rejects(replay::mutateWorld,"no world mutation");
        ok(replay.next().isEmpty()&&replay.phase()==ReplaySession.Phase.RETURNED,"memory ends");
        var p=new DisclosurePolicy();
        ok(p.evaluate(true,DisclosurePolicy.Intent.REVEAL_AUTHORITY,DisclosurePolicy.Context.ORDINARY,false).suppressDelivery(),"taboo");
        ok(!p.evaluate(true,DisclosurePolicy.Intent.WARN_ABOUT_DANGER,DisclosurePolicy.Context.ORDINARY,false).suppressDelivery(),"warning not taboo");
        ok(!p.evaluate(true,DisclosurePolicy.Intent.QUOTE_FICTION,DisclosurePolicy.Context.ORDINARY,false).suppressDelivery(),"quotation not taboo");
        ok(!p.evaluate(true,DisclosurePolicy.Intent.REVEAL_AUTHORITY,DisclosurePolicy.Context.PASSIVE_MEMORY_DISCOVERY,false).suppressDelivery(),"discovery not confession");
        ok(!p.evaluate(false,DisclosurePolicy.Intent.REVEAL_AUTHORITY,DisclosurePolicy.Context.ORDINARY,false).suppressDelivery(),"nonholder no taboo");
        var vis=new ArchiveVisibility();
        ok(vis.visible(book,branch,Set.of(other)),"ordinary current dead visible");
        ok(!vis.visible(book,UUID.randomUUID(),Set.of(other)),"ordinary discarded future hidden");
        ok(!vis.visible(book,branch,Set.of()),"ordinary alive not dead");
        var self=new DeathBook(UUID.randomUUID(),owner,UUID.randomUUID(),branch,"owner",true,List.of(scene));
        ok(vis.visible(self,UUID.randomUUID(),Set.of()),"holder failed loop visible");
        var policy=new CheckpointPolicy.Authored();
        ok(policy.mayAdvance(new CheckpointPolicy.Context("end",true,true,true,false,CheckpointPolicy.Connection.CONNECTED)),"authored checkpoint");
        ok(!policy.mayAdvance(new CheckpointPolicy.Context("bed",false,true,true,false,CheckpointPolicy.Connection.CONNECTED)),"bed not save");
        ok(!policy.mayAdvance(new CheckpointPolicy.Context("end",true,true,true,true,CheckpointPolicy.Connection.CONNECTED)),"no capture during transaction");
        ok(!policy.mayAdvance(new CheckpointPolicy.Context("end",true,true,true,false,CheckpointPolicy.Connection.LOST)),"lost link needs authored rule");
        System.out.println("CoreSelfTest: "+count+" assertions passed (pure Java only)");
    }
}
