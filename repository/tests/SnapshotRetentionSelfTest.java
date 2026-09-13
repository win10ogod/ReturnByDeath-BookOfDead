import dev.rbd.io.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

public final class SnapshotRetentionSelfTest {
    static int checks;
    static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    static void capture(SnapshotStore s)throws Exception{s.prepare("CAPTURE",null);s.markClosed();s.complete();}
    static void prune(SnapshotStore s,int keep,int failed)throws Exception{SnapshotRetention.prune(s.control,"world",false,keep,failed);}
    static long count(Path p)throws Exception{if(!Files.isDirectory(p))return 0;try(var list=Files.list(p)){return list.count();}}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("rbd-retention-");
        try{
            Path world=root.resolve("world"),control=root.resolve("control");Files.createDirectories(world);
            Files.writeString(world.resolve("level.dat"),"unchanged");Files.writeString(world.resolve("region.mca"),"first");
            Files.createDirectories(control.resolve("memories/books"));Files.writeString(control.resolve("memories/books/life.json"),"keep every memory");
            var s=new SnapshotStore(world,control);capture(s);
            String first=s.active().get("id").getAsString();
            for(int i=0;i<20;i++){
                Files.writeString(world.resolve("region.mca"),"branch "+i);capture(s);prune(s,1,1);
                check(count(control.resolve("snapshots"))==1,"default retains one checkpoint after save "+i);
                SnapshotStore.verify(control.resolve("snapshots/"+s.active().get("id").getAsString()+"/tree"),SnapshotStore.inventory(world));
            }
            check(!Files.exists(control.resolve("snapshots/"+first)),"old checkpoint retired");
            for(int i=0;i<3;i++){capture(s);prune(s,3,1);}
            check(count(control.resolve("snapshots"))==3,"configured retention keeps three checkpoints");
            String active=s.active().get("id").getAsString();
            s.prepare("CAPTURE",null);prune(s,1,0);
            check(count(control.resolve("snapshots"))==3,"pending copy protects existing recovery data");
            s.markClosed();s.complete();prune(s,1,1);
            check(count(control.resolve("snapshots"))==1,"cleanup resumes after successful commit");
            Path incomplete=control.resolve("snapshots/"+UUID.randomUUID()+".partial");Files.createDirectories(incomplete);Files.writeString(incomplete.resolve("evidence"),"uncommitted");
            for(int i=0;i<4;i++){
                Files.writeString(world.resolve("region.mca"),"dead "+i);s.prepare("RESTORE",new JsonObject());s.markClosed();s.complete();prune(s,1,1);
                check(count(control.resolve("failed"))==1,"failed world backups remain bounded");
                check(Files.readString(world.resolve("level.dat")).equals("unchanged"),"shared snapshot bytes survive older inode deletion and restore");
            }
            check(Files.exists(incomplete.resolve("evidence")),"partial checkpoint retained for recovery");
            Files.writeString(control.resolve("fault.json"),"{}");prune(s,1,0);
            check(count(control.resolve("failed"))==1,"fault pauses deletion");Files.delete(control.resolve("fault.json"));prune(s,1,0);
            check(count(control.resolve("failed"))==0,"zero failed-branch retention supported after successful reopen");
            check(Files.readString(control.resolve("memories/books/life.json")).equals("keep every memory"),"books and replay memories are independent of world retention");
            // A linked child must never cause a deletion outside the retired tree.
            Path outside=root.resolve("outside");Files.createDirectories(outside);Files.writeString(outside.resolve("precious"),"untouched");
            Path trash=control.resolve("retired-checkpoints/"+UUID.randomUUID());Files.createDirectories(trash);Files.createSymbolicLink(trash.resolve("link"),outside);
            prune(s,1,1);check(Files.exists(outside.resolve("precious"))&&!Files.exists(trash),"interrupted deletion resumes without following symlinks");
            Path unknown=control.resolve("snapshots/"+UUID.randomUUID());Files.createDirectories(unknown);prune(s,1,1);
            check(Files.exists(unknown),"unreceipted snapshot never guessed obsolete");
            // Concurrent cleanup never retires the copy from which a new capture is reading.
            var errors=new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
            Thread cleaner=new Thread(()->{for(int i=0;i<100;i++)try{prune(s,1,1);}catch(Throwable e){errors.add(e);}});cleaner.start();
            for(int i=0;i<12;i++){Files.writeString(world.resolve("region.mca"),"concurrent "+i);capture(s);}
            cleaner.join();prune(s,1,1);check(errors.isEmpty(),"cleanup/capture race succeeds: "+errors);
            SnapshotStore.verify(control.resolve("snapshots/"+s.active().get("id").getAsString()+"/tree"),SnapshotStore.inventory(world));
            check(count(control.resolve("snapshots"))==3,"one committed checkpoint plus untouched partial and unknown evidence");
            // The optional supervisor uses different pointer, manifest and receipt fields.
            Path legacy=root.resolve("legacy");Files.createDirectories(legacy.resolve("snapshots"));
            for(int i=1;i<=3;i++){
                String id=UUID.randomUUID().toString();Files.createDirectories(legacy.resolve("snapshots/"+id+"/tree"));
                AtomicJson.write(legacy.resolve("snapshots/"+id+"/manifest.json"),SnapshotStore.inventory(legacy.resolve("snapshots/"+id+"/tree")));
                JsonObject receipt=new JsonObject();receipt.addProperty("request_id",id);receipt.addProperty("operation","CAPTURE");receipt.addProperty("ordinal",i);AtomicJson.write(legacy.resolve("receipts/"+id+".json"),receipt);
                JsonObject pointer=new JsonObject();pointer.addProperty("checkpoint_id",id);pointer.addProperty("ordinal",i);AtomicJson.write(legacy.resolve("active_checkpoint.json"),pointer);
            }
            SnapshotRetention.prune(legacy,"world",true,1,1);check(count(legacy.resolve("snapshots"))==1,"legacy supervisor committed checkpoint format supported");
            JsonObject pointer=AtomicJson.read(legacy.resolve("active_checkpoint.json"));pointer.add("id",pointer.get("checkpoint_id"));AtomicJson.write(legacy.resolve("active.json"),pointer);
            AtomicJson.write(legacy.resolve("migration.json"),new JsonObject());
            var converted=new SnapshotStore(world,legacy);capture(converted);prune(converted,1,1);
            check(count(legacy.resolve("snapshots"))==1,"native capture retires a migrated supervisor checkpoint using its preserved receipt");
            for(int i=0;i<2;i++){
                String id=UUID.randomUUID().toString();Files.createDirectories(legacy.resolve("failed/.world.rbd-rejected-"+id));
                JsonObject receipt=new JsonObject();receipt.addProperty("request_id",id);receipt.addProperty("operation","RESTORE");AtomicJson.write(legacy.resolve("receipts/"+id+".json"),receipt);
            }
            prune(converted,1,1);check(count(legacy.resolve("failed"))==1,"migrated failed-world backups also remain bounded");
            prune(converted,1,0);check(count(legacy.resolve("failed"))==0,"migrated failed-world backups respect zero retention");
            System.out.println("SnapshotRetentionSelfTest: "+checks+" checks passed");
        }finally{try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
