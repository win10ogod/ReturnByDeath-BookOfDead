package dev.rbd.testing;
import dev.rbd.RbdConfig;
import dev.rbd.io.AtomicJson;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.*;
import com.google.gson.JsonObject;
import java.nio.file.*;
import java.util.UUID;

/** Edits a private copy of the fixture's real world metadata through the existing-world menu. */
public final class ExistingRulesUiFixture {
    private static int phase,frames;
    private static LevelStorageSource.LevelStorageAccess access;
    private static EditWorldScreen parent;
    private static CompoundTag original;
    public static boolean tick() throws Exception {
        if(!CompatScenario.allowed()||!CompatScenario.connected()||!System.getProperty("rbd.testRole","").equals("RbdA")||phase==5)return false;
        var mc=Minecraft.getInstance();
        if(phase==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
            Path source=CompatScenario.root().resolve("server/world/level.dat");if(!Files.exists(source))return true;
            access=mc.getLevelSource().createAccess("RbdEditRulesFixture-"+UUID.randomUUID());
            original=NbtIo.readCompressed(source,NbtAccounter.unlimitedHeap());original.putString("rbd_fixture_preserved_mod_tag","存檔內容保持完整");
            NbtIo.writeCompressed(original,access.getLevelPath(LevelResource.ROOT).resolve("level.dat"));
            parent=EditWorldScreen.create(mc,access,changed->{try{access.close();}catch(Exception e){throw new RuntimeException(e);}phase=5;mc.setScreen(new TitleScreen());});
            mc.setScreen(parent);phase=1;return true;
        }
        if(phase==1){
            parent.children().stream().filter(x->x instanceof Button).map(x->(Button)x).filter(x->x.getMessage().equals(Component.translatable("editGamerule.title"))).findFirst().orElseThrow(()->new IllegalStateException("Existing world has no native game-rule entry point")).onPress();
            if(!(mc.screen instanceof EditGameRulesScreen))throw new IllegalStateException("Existing-world rule entry did not open the native editor");phase=2;
        }
        if(phase==2&&mc.screen instanceof EditGameRulesScreen screen){RulesUiFixture.input(screen,RbdConfig.MAX_HOLDERS.key().getId()).setValue("3");RulesUiFixture.input(screen,RbdConfig.DEATH_DWELL.key().getId()).setValue("0.375");phase=3;return true;}
        if(phase==4){
            if(mc.screen instanceof EditGameRulesScreen screen){RulesUiFixture.done(screen).onPress();return true;}
            if(mc.screen!=parent)throw new IllegalStateException("Existing-world rules failed to save");
            Path world=access.getLevelPath(LevelResource.ROOT);var actual=NbtIo.readCompressed(world.resolve("level.dat"),NbtAccounter.unlimitedHeap());
            var expected=original.copy();var rules=expected.getCompound("Data").getCompound("GameRules");rules.putString(RbdConfig.MAX_HOLDERS.key().getId(),"3");rules.putString(RbdConfig.DEATH_DWELL.key().getId(),"0.375");
            if(!actual.equals(expected)||!NbtIo.readCompressed(world.resolve("level.dat_old"),NbtAccounter.unlimitedHeap()).equals(original))throw new IllegalStateException("Editing game rules changed unrelated world/mod data or lost the backup");
            var report=new JsonObject();report.addProperty("result","PASS");report.addProperty("nativeExistingWorldEntry",true);report.addProperty("maxHolders",3);report.addProperty("deathDwell",0.375);report.addProperty("allOtherWorldAndModTagsPreserved",true);report.addProperty("originalMetadataBackup",true);AtomicJson.write(CompatScenario.root().resolve("existing-world-rules-ui.json"),report);parent.onClose();
        }
        return phase>0&&phase<5;
    }
    public static void render(){if(phase==3&&++frames>=3){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"rbd-existing-world-game-rules.png",mc.getMainRenderTarget(),m->{});phase=4;}}
    private ExistingRulesUiFixture(){}
}
