package dev.rbd.memory;
import java.util.*;
/** Immutable, subjective visual/audio frame. No writable world or inventory is sent to the reader. */
public record MemoryFrame(long tick, String dimension, double x,double y,double z,float yaw,float pitch,
                          float health,String caption,List<Contact> contacts,List<Sound> sounds,
                          String visualSource,int width,int height,int[] pixels,String png,SomaticState body) {
    /** Read/write compatibility for pre-0.3 recordings and existing callers. */
    public MemoryFrame(long tick,String dimension,double x,double y,double z,float yaw,float pitch,float health,
                       String caption,List<Contact> contacts,List<Sound> sounds,String visualSource,int width,int height,int[] pixels,String png){
        this(tick,dimension,x,y,z,yaw,pitch,health,caption,contacts,sounds,visualSource,width,height,pixels,png,null);
    }
    public record Contact(UUID soul,String name,String appearance) {}
    public record Sound(String id,float volume,float pitch) {}
}
