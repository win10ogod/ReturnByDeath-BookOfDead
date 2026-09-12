package dev.rbd.runtime;
import com.google.gson.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.UUID;
public final class BranchData extends SavedData {
    public JsonObject json=new JsonObject();
    public BranchData(){json.addProperty("branch",UUID.randomUUID().toString());}
    public static BranchData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(BranchData::new,BranchData::load),"rbd_branch");}
    private static BranchData load(CompoundTag tag,HolderLookup.Provider registries){var data=new BranchData();if(tag.contains("json"))data.json=JsonParser.parseString(tag.getString("json")).getAsJsonObject();return data;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        // A persisted head must never refer to a segment still awaiting its durable seal.
        var game=GameSession.current;
        if(game!=null&&game.branch==this)try{game.archive.flush();}catch(java.io.IOException e){throw new java.io.UncheckedIOException("Cannot save branch before memory is durable",e);}
        tag.putString("json",json.toString());return tag;
    }
    public JsonObject object(String name){if(!json.has(name))json.add(name,new JsonObject());setDirty();return json.getAsJsonObject(name);}
}
