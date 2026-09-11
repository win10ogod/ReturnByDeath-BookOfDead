package dev.rbd.io;

import com.google.gson.*;
import java.util.*;

/** Persistent per-person authority. Membership and memories live outside the shared world timeline. */
public final class AuthorityRoster {
    private final JsonObject root;
    public AuthorityRoster(JsonObject saved) {
        if (saved.has("holders")) {
            if (!saved.has("schema") || saved.get("schema").getAsInt()!=2)
                throw new IllegalArgumentException("Unknown authority roster schema; original data retained");
            root=saved.deepCopy();
        } else {
            root=new JsonObject();root.addProperty("schema",2);root.add("holders",new JsonObject());
            if (saved.has("holder")) {
                UUID id=UUID.fromString(saved.get("holder").getAsString());
                JsonObject person=saved.deepCopy();person.addProperty("active",true);
                entries().add(id.toString(),person);
            } else if (!saved.isEmpty()) root.add("legacy",saved.deepCopy());
        }
        for (var entry:entries().entrySet()) {
            UUID id=UUID.fromString(entry.getKey());JsonObject person=entry.getValue().getAsJsonObject();
            if (!id.toString().equals(person.get("holder").getAsString()))
                throw new IllegalArgumentException("Authority UUID does not match roster entry");
        }
    }
    private JsonObject entries(){return root.getAsJsonObject("holders");}
    public JsonObject json(){return root;}
    public JsonObject person(UUID id){return entries().has(id.toString())?entries().getAsJsonObject(id.toString()):null;}
    public boolean contains(UUID id){JsonObject p=person(id);return p!=null&&(!p.has("active")||p.get("active").getAsBoolean());}
    public List<UUID> activeIds(){return entries().keySet().stream().map(UUID::fromString).filter(this::contains).toList();}
    public boolean hasRoom(int limit){if(limit<0)throw new IllegalArgumentException("Negative holder limit");return limit==0||activeIds().size()<limit;}
    public JsonObject bind(UUID id,String name,int limit){
        if(contains(id))throw new IllegalStateException("Player already holds Return by Death");
        if(!hasRoom(limit))throw new IllegalStateException("Return by Death holder limit reached: "+limit);
        JsonObject p=person(id);if(p==null){p=new JsonObject();p.addProperty("holder",id.toString());p.addProperty("miasma",0);entries().add(id.toString(),p);}
        p.addProperty("name",name);p.addProperty("active",true);return p;
    }
    public void unbind(UUID id){if(!contains(id))throw new IllegalStateException("Player is not an active holder");person(id).addProperty("active",false);}
}
