package dev.rbd.client;
import com.mojang.serialization.Dynamic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
public final class ExistingWorldRules {
    public static void open(Screen parent,LevelStorageSource.LevelStorageAccess access){
        var mc=Minecraft.getInstance();
        try{
            Path world=access.getLevelPath(LevelResource.ROOT),data=world.resolve("level.dat");
            CompoundTag root=NbtIo.readCompressed(data,NbtAccounter.unlimitedHeap());
            GameRules rules=new GameRules(new Dynamic<>(NbtOps.INSTANCE,root.getCompound("Data").getCompound("GameRules")));
            mc.setScreen(new EditGameRulesScreen(rules,result->{
                if(result.isEmpty()){mc.setScreen(parent);return;}
                try{
                    // Keep every unrelated vanilla/mod tag and a recovery copy of the original.
                    root.getCompound("Data").put("GameRules",result.get().createTag());
                    Path returnedRules=dev.rbd.io.SnapshotStore.controlFor(world).resolve("returned_rules.json");
                    if(Files.exists(returnedRules)){
                        var receipt=dev.rbd.io.AtomicJson.read(returnedRules);receipt.add("values",dev.rbd.rules.WorldRules.export(result.get()));
                        dev.rbd.io.AtomicJson.write(returnedRules,receipt);
                    }
                    Path stage=Files.createTempFile(world,"rbd-rules-",".dat");
                    NbtIo.writeCompressed(root,stage);
                    try(FileChannel channel=FileChannel.open(stage,StandardOpenOption.WRITE)){channel.force(true);}
                    Files.copy(data,world.resolve("level.dat_old"),StandardCopyOption.REPLACE_EXISTING);
                    Files.move(stage,data,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                    mc.setScreen(parent);
                }catch(Exception error){error(parent,error);}
            }));
        }catch(Exception error){error(parent,error);}
    }
    private static void error(Screen parent,Exception error){Minecraft.getInstance().setScreen(new AlertScreen(()->Minecraft.getInstance().setScreen(parent),Component.translatable("screen.rbd.rules_error"),Component.literal(error.toString())));}
    private ExistingWorldRules(){}
}
