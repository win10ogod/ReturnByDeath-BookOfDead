package dev.rbd.core;
import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;
/** Pure-domain state machine. Durable commit belongs to the storage boundary. */
public final class LoopStateMachine {
    public enum Phase { UNBOUND, AWAITING_CHECKPOINT, ACTIVE, SEALING, RECOVERY_REQUIRED }
    public record Checkpoint(UUID id, long ordinal, UUID branch) {
        public Checkpoint { Objects.requireNonNull(id); Objects.requireNonNull(branch);
            if (ordinal < 0) throw new IllegalArgumentException("negative ordinal"); }
    }
    public record DeathIntent(UUID requestId, UUID holder, UUID checkpoint, BigInteger nextLoop) {}
    private Phase phase = Phase.UNBOUND;
    private final java.util.Set<UUID> holders = new java.util.LinkedHashSet<>();
    private final int holderLimit;
    public LoopStateMachine(){this(1);}
    public LoopStateMachine(int holderLimit){if(holderLimit<0)throw new IllegalArgumentException("negative holder limit");this.holderLimit=holderLimit;}
    private Checkpoint checkpoint;
    private BigInteger loops = BigInteger.ZERO;
    private UUID pending;
    public synchronized void bind(UUID id) {
        Objects.requireNonNull(id);
        if (phase==Phase.SEALING||phase==Phase.RECOVERY_REQUIRED) throw new IllegalStateException("binding during transaction");
        if (holders.contains(id)||(holderLimit>0&&holders.size()>=holderLimit)) throw new IllegalStateException("authority limit reached or duplicate holder");
        holders.add(id);if(phase==Phase.UNBOUND)phase = Phase.AWAITING_CHECKPOINT;
    }
    public synchronized void acceptCheckpoint(Checkpoint next) {
        Objects.requireNonNull(next);
        if (phase != Phase.AWAITING_CHECKPOINT && phase != Phase.ACTIVE)
            throw new IllegalStateException("checkpoint during transition");
        if (checkpoint != null && next.ordinal() <= checkpoint.ordinal())
            throw new IllegalArgumentException("checkpoints cannot move backwards");
        checkpoint = next; phase = Phase.ACTIVE;
    }
    public synchronized DeathIntent beginDeath(UUID subject, UUID nonce) {
        if (phase != Phase.ACTIVE || !holders.contains(subject))
            throw new IllegalStateException("not an active authority death");
        pending = Objects.requireNonNull(nonce); phase = Phase.SEALING;
        return new DeathIntent(pending, subject, checkpoint.id(), loops.add(BigInteger.ONE));
    }
    public synchronized void committed(UUID nonce) {
        if (phase != Phase.SEALING || !Objects.equals(nonce, pending))
            throw new IllegalStateException("receipt does not match pending death");
        loops = loops.add(BigInteger.ONE); pending = null; phase = Phase.ACTIVE;
    }
    public synchronized void failClosed() { phase = Phase.RECOVERY_REQUIRED; }
    public synchronized Phase phase() { return phase; }
    public synchronized BigInteger loops() { return loops; }
    public synchronized Checkpoint checkpoint() { return checkpoint; }
}
