package dev.rbd.core;
import java.util.*;
/** Read-only replay protocol. Rendering and compressed full-state recording are P1, not implemented here. */
public final class ReplaySession {
    public enum Phase { ENTERING, EXPERIENCING, RETURNED, ABORTED }
    private final UUID reader;
    private final DeathBook book;
    private final IdentityKnowledge knowledge;
    private Phase phase=Phase.ENTERING;
    private int cursor=-1;
    public ReplaySession(UUID reader,DeathBook book,IdentityKnowledge knowledge) {
        this.reader=Objects.requireNonNull(reader);this.book=Objects.requireNonNull(book);
        this.knowledge=Objects.requireNonNull(knowledge);
        if(!book.mayExperience(reader,knowledge))throw new SecurityException("identity not recognized");
    }
    public Optional<DeathBook.MemoryScene> next() {
        if(phase==Phase.RETURNED||phase==Phase.ABORTED)return Optional.empty();
        if(++cursor>=book.scenes().size()){phase=Phase.RETURNED;return Optional.empty();}
        phase=Phase.EXPERIENCING;var scene=book.scenes().get(cursor);
        knowledge.learnFromExperiencedScene(scene.actuallySeenContacts());return Optional.of(scene);
    }
    public void mutateWorld(){throw new UnsupportedOperationException("memories are not time travel");}
    public void seek(int ignored){throw new UnsupportedOperationException("canonical reader cannot select past moments");}
    public void abort(){phase=Phase.ABORTED;}
    public Phase phase(){return phase;}
}
