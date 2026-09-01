package dev.alvar.echoespast.server;

import java.util.Objects;
import java.util.UUID;

/**
 * Short, UUID-bound grace period while a freshly spawned boss enters the
 * server's tracked-entity index.
 *
 * <p>This is deliberately runtime-only. Active encounters are reset when the
 * server starts, and a missing boss must still abort once the bounded window
 * expires.</p>
 */
public final class UnknownBossTrackingGrace {
    public static final int DEFAULT_DURATION_TICKS = 40;

    private final int durationTicks;
    private UUID bossId;
    private long expiresAtTick;

    public UnknownBossTrackingGrace() {
        this(DEFAULT_DURATION_TICKS);
    }

    public UnknownBossTrackingGrace(int durationTicks) {
        if (durationTicks < 1) {
            throw new IllegalArgumentException("durationTicks must be positive");
        }
        this.durationTicks = durationTicks;
    }

    public void begin(UUID newBossId, long currentTick) {
        bossId = Objects.requireNonNull(newBossId, "newBossId");
        expiresAtTick = currentTick + durationTicks;
    }

    /** True only for the exact newly spawned boss and before the deadline. */
    public boolean allows(UUID encounterBossId, long currentTick) {
        return bossId != null
                && bossId.equals(encounterBossId)
                && currentTick < expiresAtTick;
    }

    public void clear() {
        bossId = null;
        expiresAtTick = 0L;
    }
}
