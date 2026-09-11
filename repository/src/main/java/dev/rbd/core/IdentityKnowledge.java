package dev.rbd.core;
import java.util.*;
/** Identity, not display-name equality, is the key. Keep non-holder instances in world data. */
public final class IdentityKnowledge {
    public enum Origin { DIRECT_ENCOUNTER, EXPERIENCED_MEMORY, HEARSAY }
    public record Evidence(UUID soulId, String trueName, boolean recognizesFace, Origin origin) {
        public Evidence { Objects.requireNonNull(soulId); Objects.requireNonNull(trueName);
            Objects.requireNonNull(origin); }
    }
    private final Map<UUID, Evidence> identities = new HashMap<>();
    public void learn(Evidence evidence) {
        if (evidence.trueName().isBlank()) return;
        var old = identities.get(evidence.soulId());
        if (old == null || !qualifies(old) || qualifies(evidence)) identities.put(evidence.soulId(), evidence);
    }
    private static boolean qualifies(Evidence e) {
        return e.recognizesFace() && e.origin() != Origin.HEARSAY && !e.trueName().isBlank();
    }
    public boolean recognizes(UUID soulId) { var e = identities.get(soulId); return e != null && qualifies(e); }
    public Set<UUID> recognizedSouls() {
        Set<UUID> out = new HashSet<>(); identities.forEach((id,e)->{if(qualifies(e))out.add(id);});
        return Set.copyOf(out);
    }
    /** Only contacts actually encountered in the selected memory are learned, not the actor's full contacts. */
    public void learnFromExperiencedScene(Collection<Evidence> seenInScene) {
        for (var e: seenInScene) learn(new Evidence(e.soulId(),e.trueName(),e.recognizesFace(),Origin.EXPERIENCED_MEMORY));
    }
}
