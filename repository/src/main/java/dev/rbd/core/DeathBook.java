package dev.rbd.core;
import java.util.*;
public record DeathBook(UUID bookId, UUID soulId, UUID lifeId, UUID branchId,
                        String originalName, boolean authorityLife, List<MemoryScene> scenes) {
    public DeathBook {
        Objects.requireNonNull(bookId); Objects.requireNonNull(soulId); Objects.requireNonNull(lifeId);
        Objects.requireNonNull(branchId); Objects.requireNonNull(originalName);
        scenes=List.copyOf(scenes);
    }
    public record MemoryScene(long subjectTick, String dimension, double x, double y, double z,
                              String observedEvent, Set<IdentityKnowledge.Evidence> actuallySeenContacts) {
        public MemoryScene {
            if(subjectTick<0 || !Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))
                throw new IllegalArgumentException("invalid frame");
            Objects.requireNonNull(dimension); Objects.requireNonNull(observedEvent);
            actuallySeenContacts=Set.copyOf(actuallySeenContacts);
        }
    }
    public boolean mayExperience(UUID reader, IdentityKnowledge knowledge) {
        return reader.equals(soulId) || knowledge.recognizes(soulId);
    }
}
