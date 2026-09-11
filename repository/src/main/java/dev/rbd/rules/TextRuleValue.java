package dev.rbd.rules;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import java.util.function.Predicate;

/** Text editor adapter for vanilla's numeric game-rule visitor; persisted without rounding. */
public final class TextRuleValue extends GameRules.IntegerValue {
    private String text;
    private final Predicate<String> valid;
    public TextRuleValue(GameRules.Type<GameRules.IntegerValue> type,String initial,Predicate<String> valid){super(type,0);this.text=initial;this.valid=valid;}
    @Override public String serialize(){return text;}
    @Override public boolean tryDeserialize(String value){if(!valid.test(value))return false;text=value;return true;}
    @Override protected void deserialize(String value){if(!tryDeserialize(value))throw new IllegalArgumentException("Invalid RBD game rule: "+value);}
    @Override protected void updateFromArgument(CommandContext<CommandSourceStack> context,String name){
        if(!tryDeserialize(StringArgumentType.getString(context,name)))throw new IllegalArgumentException("Invalid RBD rule: check the allowed range or advancement IDs");
    }
    @Override protected GameRules.IntegerValue copy(){return new TextRuleValue(type,text,valid);}
    @Override public void setFrom(GameRules.IntegerValue value,MinecraftServer server){deserialize(value.serialize());onChanged(server);}
    @Override public int getCommandResult(){return 1;}
}
