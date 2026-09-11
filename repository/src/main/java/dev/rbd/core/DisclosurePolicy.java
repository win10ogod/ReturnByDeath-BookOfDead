package dev.rbd.core;
import java.util.Objects;
/** Intent comes from authored dialogue/actions. This is deliberately NOT a keyword classifier. */
public final class DisclosurePolicy {
    public enum Intent { REVEAL_AUTHORITY, REVEAL_LOOP_PROVENANCE, WARN_ABOUT_DANGER, QUOTE_FICTION, UNKNOWN }
    public enum Context { ORDINARY, AUTHORED_EXCEPTION, PASSIVE_MEMORY_DISCOVERY }
    public enum Reaction { NONE, HEART_RESTRAINT, SCRIPTED_WITCH_INTERVENTION }
    public record Decision(boolean suppressDelivery, Reaction reaction) {}
    public Decision evaluate(boolean isHolder, Intent intent, Context context, boolean escalatedScene) {
        Objects.requireNonNull(intent); Objects.requireNonNull(context);
        if (!isHolder || context != Context.ORDINARY) return new Decision(false,Reaction.NONE);
        boolean disclosure=intent==Intent.REVEAL_AUTHORITY || intent==Intent.REVEAL_LOOP_PROVENANCE;
        if(!disclosure)return new Decision(false,Reaction.NONE);
        return new Decision(true,escalatedScene?Reaction.SCRIPTED_WITCH_INTERVENTION:Reaction.HEART_RESTRAINT);
    }
}
