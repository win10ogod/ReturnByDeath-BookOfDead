package dev.rbd.mixin;
import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.function.*;
@Mixin(GameRules.Type.class)
public interface GameRuleTypeAccess {
    @Mutable @Accessor("argument") void rbd$argument(Supplier<ArgumentType<?>> value);
    @Mutable @Accessor("constructor") void rbd$constructor(Function<GameRules.Type<GameRules.IntegerValue>,GameRules.IntegerValue> value);
}
