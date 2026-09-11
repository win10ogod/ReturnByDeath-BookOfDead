package dev.rbd.core;
import java.util.*;
/** Persistent storage does not imply universal in-game visibility. */
public final class ArchiveVisibility {
    public boolean visible(DeathBook b, UUID activeBranch, Set<UUID> deadSoulsInCurrentWorld) {
        return b.authorityLife() || (b.branchId().equals(activeBranch) && deadSoulsInCurrentWorld.contains(b.soulId()));
    }
}
