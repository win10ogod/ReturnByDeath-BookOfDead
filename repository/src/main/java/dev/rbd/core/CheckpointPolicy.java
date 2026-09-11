package dev.rbd.core;
import java.util.Objects;
/** Selection is a replaceable adaptation, not a claimed formula from the novel. */
public interface CheckpointPolicy {
    enum Connection { CONNECTED, UNSTABLE, LOST, SCRIPTED_RECONNECTION }
    record Context(String milestoneId, boolean authoredBoundary, boolean holderAlive,
                   boolean worldQuiescent, boolean transactionPending, Connection connection) {
        public Context { Objects.requireNonNull(milestoneId);Objects.requireNonNull(connection); }
    }
    boolean mayAdvance(Context c);
    final class Authored implements CheckpointPolicy {
        public boolean mayAdvance(Context c) {
            return c.authoredBoundary() && c.holderAlive() && c.worldQuiescent() && !c.transactionPending()
                && (c.connection()==Connection.CONNECTED || c.connection()==Connection.SCRIPTED_RECONNECTION);
        }
    }
}
