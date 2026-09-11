package dev.rbd.testing;
import dev.rbd.RbdConfig;
import dev.rbd.io.AtomicJson;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.GameRules;
import com.google.gson.JsonObject;
import java.util.*;

/** Exercises the real native screen and its validation responders in the disposable Xvfb fixture. */
public final class RulesUiFixture {
    private static int phase,frames;
    private static EditGameRulesScreen screen;
    private static GameRules rules;
    public static boolean tick(){
        if(!CompatScenario.allowed()||!CompatScenario.connected()||!System.getProperty("rbd.testRole","").equals("RbdA")||phase==4)return false;
        var mc=Minecraft.getInstance();
        if(phase==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
            rules=new GameRules();screen=new EditGameRulesScreen(rules,result->{
                if(result.isEmpty())throw new IllegalStateException("Native rule edit was cancelled");
                var tag=result.get().createTag();
                if(!tag.getString(RbdConfig.MAX_HOLDERS.key().getId()).equals("2147483647")||!tag.getString(RbdConfig.DEATH_DWELL.key().getId()).equals("0.125")||!tag.getString(RbdConfig.MILESTONES.key().getId()).isEmpty())throw new IllegalStateException("Native game-rule editor lost configured values");
                try{var report=new JsonObject();report.addProperty("result","PASS");report.addProperty("nativeScreen",screen.getClass().getName());report.addProperty("rules",dev.rbd.rules.WorldRules.ALL.size());report.addProperty("maxHolders",2147483647);report.addProperty("fractionalDwell","0.125");report.addProperty("emptyMilestones",true);report.addProperty("invalidValueDisablesDone",true);AtomicJson.write(CompatScenario.root().resolve("gamerule-ui.json"),report);}catch(Exception e){throw new RuntimeException(e);}
                phase=4;mc.setScreen(new TitleScreen());
            });mc.setScreen(screen);phase=1;return true;
        }
        if(mc.screen!=screen)return phase>0&&phase<4;
        if(phase==1){
            input(RbdConfig.MAX_HOLDERS.key().getId()).setValue("-1");
            if(done().active)throw new IllegalStateException("Native Done button accepts invalid holder limit");
            input(RbdConfig.MAX_HOLDERS.key().getId()).setValue("2147483647");
            input(RbdConfig.DEATH_DWELL.key().getId()).setValue("0.125");
            input(RbdConfig.MILESTONES.key().getId()).setValue("minecraft:story/enter_the_nether,twilightforest:progress_naga");
            phase=2;return true;
        }
        if(phase==3){input(RbdConfig.MILESTONES.key().getId()).setValue("");if(!done().active)throw new IllegalStateException("Valid custom rules disabled native Done");done().onPress();}
        return true;
    }
    private static Button done(){return done(screen);}
    public static Button done(EditGameRulesScreen screen){return screen.children().stream().filter(x->x instanceof Button).map(x->(Button)x).filter(x->x.getMessage().getContents() instanceof TranslatableContents t&&t.getKey().equals("gui.done")).findFirst().orElseThrow();}
    private static EditBox input(String rule){
        return input(screen,rule);
    }
    public static EditBox input(EditGameRulesScreen screen,String rule){
        for(var child:screen.children())if(child instanceof AbstractSelectionList<?> list){
            int index=0;
            for(Object entry:(Iterable<?>)list.children()){
                if(entry instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)for(var widget:container.children())
                    if(widget instanceof EditBox box&&box.getMessage().getContents() instanceof TranslatableContents t&&t.getKey().equals("gamerule."+rule)){list.setScrollAmount(index*24);return box;}
                index++;
            }
        }
        throw new IllegalStateException("Native rule field missing: "+rule);
    }
    public static void render(){if(phase==2&&++frames>=3){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"rbd-native-game-rules.png",mc.getMainRenderTarget(),m->{});phase=3;}}
    private RulesUiFixture(){}
}
