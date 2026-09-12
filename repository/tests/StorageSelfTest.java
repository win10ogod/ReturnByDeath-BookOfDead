import dev.rbd.io.*;
import dev.rbd.memory.*;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
import java.io.*;

public final class StorageSelfTest {
    private static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static void fails(Throwing action,String why)throws Exception{checks++;try{action.run();throw new AssertionError(why);}catch(IOException expected){}}
    interface Throwing{void run()throws Exception;}
    static Path root;
    static Path write(Path world,String relative,String text)throws IOException{Path p=world.resolve(relative);Files.createDirectories(p.getParent());Files.writeString(p,text);return p;}
    static SnapshotStore fresh(String name)throws IOException{
        Path world=root.resolve(name).resolve("world");Files.createDirectories(world);write(world,"level.dat","before");write(world,"dimensions/test/moon/region/r.0.0.mca","moon");
        write(world,"playerdata/player.dat","inventory");write(world,"data/scoreboard.dat","score");Files.createDirectories(world.resolve("empty"));return new SnapshotStore(world,world.resolveSibling("control"));
    }
    static void capture(SnapshotStore s)throws Exception{s.prepare("CAPTURE",null);s.markClosed();s.complete();}
    static void restore(SnapshotStore s)throws Exception{var death=new JsonObject();death.addProperty("cause","fall");s.prepare("RESTORE",death);s.markClosed();s.complete();}
    static MemoryFrame frame(long tick,String caption,UUID seen){return new MemoryFrame(tick,"minecraft:overworld",1,2,3,0,0,20,caption,seen==null?List.of():List.of(new MemoryFrame.Contact(seen,"Witness","face")),List.of(),"TEST",2,1,new int[]{0xFFABCDEF,0xFF010203},"");}
    public static void main(String[] args)throws Exception{
        if(args.length==3&&args[0].equals("--restore-migrated")){restore(new SnapshotStore(Path.of(args[1]),Path.of(args[2])));System.out.println("Migrated checkpoint restored with native Java storage");return;}
        root=Files.createTempDirectory("rbd-storage-test-");
        try{
            SnapshotStore s=fresh("roundtrip");JsonObject before=SnapshotStore.inventory(s.world);capture(s);String first=s.active().get("id").getAsString();
            write(s.world,"level.dat","after");write(s.world,"region/new.mca","new");Files.delete(s.world.resolve("data/scoreboard.dat"));restore(s);
            check(SnapshotStore.inventory(s.world).equals(before),"all dimensions, additions, deletions and player data restore");
            var reordered=before.deepCopy();var dirs=new JsonArray();for(int i=before.getAsJsonArray("directories").size()-1;i>=0;i--)dirs.add(before.getAsJsonArray("directories").get(i));reordered.add("directories",dirs);
            SnapshotStore.verify(s.world,reordered);check(true,"directory ordering is independent of snapshot writer/platform");
            dirs.add(dirs.get(0));fails(()->SnapshotStore.verify(s.world,reordered),"duplicate directory metadata rejected");
            check(Files.list(s.control.resolve("failed")).findAny().isPresent(),"failed branch retained");
            check(Files.list(s.control.resolve("returns")).count()==1,"death receipt exactly once");s.complete();check(Files.list(s.control.resolve("returns")).count()==1,"retry does not duplicate death");
            write(s.world,"level.dat","second checkpoint");capture(s);check(s.active().get("ordinal").getAsLong()==2,"monotonic checkpoint");restore(s);check(Files.readString(s.world.resolve("level.dat")).equals("second checkpoint"),"restore newest checkpoint only");
            Path firstTree=s.control.resolve("snapshots").resolve(first).resolve("tree");
            Path secondTree=s.control.resolve("snapshots").resolve(s.active().get("id").getAsString()).resolve("tree");
            check(Files.isSameFile(firstTree.resolve("data/scoreboard.dat"),secondTree.resolve("data/scoreboard.dat")),"unchanged immutable snapshot files reused");
            check(!Files.isSameFile(s.world.resolve("data/scoreboard.dat"),secondTree.resolve("data/scoreboard.dat")),"restored live world never shares snapshot inodes");
            write(s.world,"data/scoreboard.dat","live mutation");check(Files.readString(secondTree.resolve("data/scoreboard.dat")).equals("score"),"live writes cannot mutate historical checkpoints");
            check(Files.readString(firstTree.resolve("level.dat")).equals("before"),"changed file keeps original checkpoint bytes");
            var independent=fresh("independent");var noReuse=new SnapshotStore(independent.world,independent.control,false);capture(noReuse);String oldId=noReuse.active().get("id").getAsString();capture(noReuse);
            check(!Files.isSameFile(noReuse.control.resolve("snapshots/"+oldId+"/tree/level.dat"),noReuse.control.resolve("snapshots/"+noReuse.active().get("id").getAsString()+"/tree/level.dat")),"file reuse can be disabled without changing snapshot data");
            var corrupt=fresh("corrupt");capture(corrupt);write(corrupt.control.resolve("snapshots").resolve(corrupt.active().get("id").getAsString()).resolve("tree"),"level.dat","corrupt");
            var configured=fresh("configured");capture(configured);var rules=new JsonObject();rules.addProperty("rbdMaxHolders","3");rules.addProperty("rbdMemoryDeathDwellSeconds","0.123456789");
            configured.prepare("RESTORE",null,rules);configured.markClosed();configured.complete();
            check(AtomicJson.read(configured.control.resolve("returned_rules.json")).getAsJsonObject("values").equals(rules),"configuration survives restore without rounding");
            configured.complete();check(AtomicJson.read(configured.control.resolve("returned_rules.json")).getAsJsonObject("values").equals(rules),"configuration receipt survives recovery retry");
            fails(()->restore(corrupt),"corrupt snapshot must be refused");check(corrupt.pending(),"failed transaction retained");check(Files.readString(corrupt.world.resolve("level.dat")).equals("before"),"live world untouched on bad checksum");
            var unclosed=fresh("unclosed");unclosed.prepare("CAPTURE",null);fails(unclosed::complete,"no normal close proof must refuse");
            var locked=fresh("locked");locked.prepare("CAPTURE",null);locked.markClosed();
            try(var channel=FileChannel.open(locked.world.resolve("session.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()) {fails(locked::complete,"running world cannot be snapshotted");}
            locked.complete();check(locked.active()!=null,"interrupted staging recovers after writer exits");
            for(String phase:List.of("STAGED","OLD_MOVED","INSTALLED")){
                var r=fresh("recover-"+phase);capture(r);JsonObject original=SnapshotStore.inventory(r.world);write(r.world,"level.dat","failed");r.prepare("RESTORE",new JsonObject());r.markClosed();
                JsonObject tx=AtomicJson.read(r.control.resolve("transaction.json"));String id=tx.get("id").getAsString();Path stage=r.control.resolve("staging").resolve(id),failed=r.control.resolve("failed").resolve(id);Files.createDirectories(failed.getParent());
                SnapshotStore.copyVerified(r.control.resolve("snapshots").resolve(r.active().get("id").getAsString()).resolve("tree"),stage);
                if(!phase.equals("STAGED"))Files.move(r.world,failed);if(phase.equals("INSTALLED"))Files.move(stage,r.world);
                tx.addProperty("phase",phase);AtomicJson.write(r.control.resolve("transaction.json"),tx);r.complete();
                check(SnapshotStore.inventory(r.world).equals(original),"recover "+phase);check(!r.pending(),"finish "+phase);
            }
            var archive=new MemoryArchive(root.resolve("archive"));UUID contact=UUID.randomUUID();var prefix=archive.begin("");prefix.append(frame(1,"shared",null));prefix.close();
            var one=archive.begin(prefix.id);one.append(frame(2,"first life",contact));one.close();var two=archive.begin(prefix.id);two.append(frame(2,"second life",null));two.close();
            UUID soul=UUID.randomUUID();var b1=archive.sealBook(soul,"Name",one.id,"branch",true,"test","fall");var b2=archive.sealBook(soul,"Name",two.id,"branch",true,"test","fire");
            check(!b1.get("id").equals(b2.get("id")),"distinct book for each death");check(!b1.get("life").equals(b2.get("life")),"distinct life for each death");
            try(var reader=archive.reader(one.id)){check(reader.next().contacts().isEmpty(),"opening a book reveals no future contacts");check(reader.next().contacts().getFirst().soul().equals(contact),"contact appears at actual encounter");check(reader.next()==null,"chronological reader ends");}
            try(var reader=archive.reader(two.id)){check(reader.next().caption().equals("shared"),"shared prefix preserved");check(reader.next().caption().equals("second life"),"branch stays distinct");}
            check(Files.list(archive.root().resolve("segments")).filter(p->p.toString().endsWith("gz")).count()==3,"common life stored once");
            write(archive.root(),"segments/"+one.id+".jsonl.gz","broken");fails(()->{try(var reader=archive.reader(one.id)){while(reader.next()!=null){}}},"memory corruption cannot masquerade as valid life");
            System.out.println("StorageSelfTest: "+checks+" checks passed");
        }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
