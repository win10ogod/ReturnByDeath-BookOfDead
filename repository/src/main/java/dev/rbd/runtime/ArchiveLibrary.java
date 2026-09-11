package dev.rbd.runtime;
import com.google.gson.*;
import dev.rbd.*;
import dev.rbd.network.RbdNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import java.io.*;
import java.util.*;

/** Sixteen physical catalog shelves, with local pages. Unknown identities never receive their name/head. */
public final class ArchiveLibrary {
    private record Visit(BlockPos pos,String dimension,int index,int page,Set<String> offered){}
    private static final Map<UUID,Visit> visits=new HashMap<>();
    public static void clear(){visits.clear();}
    public static void generate(GameSession game){
        if(game.branch.json.has("library")||!RbdConfig.LIBRARY_GENERATE.get())return;
        ServerLevel level=game.server.overworld();BlockPos spawn=level.getSharedSpawnPos();
        int x=spawn.getX()+RbdConfig.LIBRARY_X.get(),z=spawn.getZ()+RbdConfig.LIBRARY_Z.get();
        int y=0;
        for(int attempt=0;attempt<32;attempt++){
            y=level.getSeaLevel()+1;
            for(int dx=-9;dx<=9;dx++)for(int dz=-9;dz<=9;dz++)y=Math.max(y,level.getHeight(Heightmap.Types.WORLD_SURFACE,x+dx,z+dz)+1);
            if(y+9<level.getMaxBuildHeight())break;
            x+=32;
        }
        if(y+9>=level.getMaxBuildHeight())throw new IllegalStateException("No vertical space for the archive near spawn");
        BlockPos base=new BlockPos(x,y,z);
        for(int dx=-9;dx<=9;dx++)for(int dz=-9;dz<=9;dz++)for(int dy=0;dy<=9;dy++){
            boolean wall=Math.abs(dx)==9||Math.abs(dz)==9;
            var state=dy==0?Blocks.DEEPSLATE_TILES.defaultBlockState():dy==9?Blocks.DARK_OAK_PLANKS.defaultBlockState():wall?Blocks.POLISHED_DEEPSLATE.defaultBlockState():Blocks.AIR.defaultBlockState();
            if(wall&&dy>=3&&dy<=6&&(Math.abs(dx)%4==0||Math.abs(dz)%4==0))state=Blocks.TINTED_GLASS.defaultBlockState();
            if(dz==-9&&Math.abs(dx)<=1&&dy>=1&&dy<=3)state=Blocks.AIR.defaultBlockState();
            level.setBlock(base.offset(dx,dy,dz),state,2);
        }
        for(int i=0;i<16;i++){
            int sx=i<8?-7:7,sz=-7+(i%8)*2;
            level.setBlock(base.offset(sx,1,sz),ModContent.SHELF.get().defaultBlockState().setValue(ArchiveShelfBlock.INDEX,i),3);
            level.setBlock(base.offset(sx,2,sz),Blocks.CHISELED_BOOKSHELF.defaultBlockState(),3);
            level.setBlock(base.offset(sx,3,sz),Blocks.CHAIN.defaultBlockState(),3);
            level.setBlock(base.offset(sx,4,sz),Blocks.SOUL_LANTERN.defaultBlockState(),3);
        }
        for(int dx=-4;dx<=4;dx+=4)for(int dz=-4;dz<=4;dz+=4)level.setBlock(base.offset(dx,1,dz),Blocks.PURPLE_CARPET.defaultBlockState(),2);
        // Keep the archive above existing terrain/builds, but give survival players a ground entrance.
        for(int step=0;step<level.getHeight();step++){
            int sy=y-step,sz=z-10-step;if(sy<=level.getMinBuildHeight())break;
            boolean grounded=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,sz)>sy;
            for(int dx=-1;dx<=1;dx++){
                BlockPos stair=new BlockPos(x+dx,sy,sz);
                if(level.getBlockState(stair).canBeReplaced())level.setBlock(stair,Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING,net.minecraft.core.Direction.SOUTH),3);
            }
            if(grounded)break;
        }
        JsonObject location=new JsonObject();location.addProperty("x",x);location.addProperty("y",y);location.addProperty("z",z);game.branch.json.add("library",location);game.branch.setDirty();
    }
    public static void browse(GameSession game,ServerPlayer p,BlockPos pos,int index,int page) throws IOException {
        if(p.blockPosition().distSqr(pos)>Math.pow(RbdConfig.LIBRARY_REACH.get(),2)||!p.level().getBlockState(pos).is(ModContent.SHELF.get()))return;
        var books=game.archive.books().stream().filter(game::visible).filter(b->Integer.parseInt(b.get("id").getAsString().substring(0,1),16)==index).toList();
        int pages=Math.max(1,(books.size()+7)/8);page=Math.max(0,Math.min(page,pages-1));
        var msg=RbdNetwork.message("shelf");msg.addProperty("page",page);msg.addProperty("pages",pages);msg.addProperty("shelf",index+1);
        JsonArray entries=new JsonArray();Set<String> offered=new HashSet<>();
        for(int i=page*8;i<Math.min(books.size(),page*8+8);i++){
            var b=books.get(i);boolean known=game.recognizes(p.getUUID(),UUID.fromString(b.get("soul").getAsString()));
            JsonObject entry=new JsonObject();entry.addProperty("known",known);entry.addProperty("name",known?b.get("name").getAsString():"???");
            if(known){String id=b.get("id").getAsString();entry.addProperty("id",id);offered.add(id);}
            entries.add(entry);
        }
        msg.add("entries",entries);visits.put(p.getUUID(),new Visit(pos.immutable(),p.level().dimension().location().toString(),index,page,offered));RbdNetwork.send(p,msg);
    }
    private static Visit valid(ServerPlayer p){
        var v=visits.get(p.getUUID());return v!=null&&p.blockPosition().distSqr(v.pos)<=Math.pow(RbdConfig.LIBRARY_REACH.get(),2)&&p.level().dimension().location().toString().equals(v.dimension)&&p.level().getBlockState(v.pos).is(ModContent.SHELF.get())?v:null;
    }
    public static void page(GameSession game,ServerPlayer p,int direction) throws IOException {var v=valid(p);if(v!=null)browse(game,p,v.pos,v.index,v.page+Integer.signum(direction));}
    public static void select(GameSession game,ServerPlayer p,String id) throws IOException {
        var v=valid(p);if(v!=null&&v.offered.contains(id)){visits.remove(p.getUUID());game.open(p,id);}
    }
}
