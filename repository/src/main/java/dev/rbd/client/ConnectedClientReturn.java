package dev.rbd.client;
import dev.rbd.mixin.StatsAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.stats.RecipeBook;
public final class ConnectedClientReturn {
    public static boolean paused,resetWorld;
    public static int completedResets;
    public static void begin(){paused=true;}
    public static void reset(){
        var mc=Minecraft.getInstance();resetWorld=true;
        if(mc.level!=null)((dev.rbd.mixin.ClientMapAccess)mc.level).rbd$maps().clear();
        if(mc.player!=null){
            ((StatsAccess)mc.player.getStats()).rbd$stats().clear();
            mc.player.getRecipeBook().copyOverData(new RecipeBook());
        }
        mc.getSoundManager().stop();
        completedResets++;
    }
    public static void complete(){paused=false;ImmersionOverlay.connectedReady();}
    public static void clear(){paused=false;resetWorld=false;}
    private ConnectedClientReturn(){}
}
