package dev.alvar.echoespast.visual;

import net.minecraft.world.phys.Vec3;

/**
 * Shared timing for the server's batched mutation and the client's
 * surface-bound temporal condensation. The extra settling time hides the last
 * block batch behind a smooth hand-off instead of ending on a mutation tick.
 */
public final class PhilosophersStoneVisualTiming {
    public static final int MIN_TRANSITION_TICKS = 64;
    public static final int MAX_TRANSITION_TICKS = 200;
    public static final int SETTLE_TICKS = 60;
    public static final int MUTATIONS_PER_TICK = 256;
    public static final float SEAM_START = -0.08F;
    public static final float SEAM_END = 1.08F;
    /**
     * Narrow enough that present dissolution and past fixation happen inside
     * the visible refractive seam instead of reading as two separate events.
     */
    public static final float BLOCK_BLEND_WIDTH = 0.018F;
    /**
     * The server changes a block just inside the bright anticipation band.
     * This small lead absorbs packet/chunk rebuild latency while the exact
     * opacity hand-off remains centred on the visual front.
     */
    public static final float SERVER_MUTATION_LEAD = 0.012F;
    /*
     * A short invocation gives the structure time to answer before the
     * transmutation travels. The final interval belongs to the fading
     * surface filaments, not to another block-state change.
     */
    private static final float TRAVEL_START = 0.13F;
    private static final float TRAVEL_END = 0.84F;
    private static final float FADE_START = 0.90F;

    public static int transitionTicks(int changedPositions) {
        return transitionTicks(changedPositions, 1);
    }

    public static int transitionTicks(
            int changedPositions,
            int speedMultiplier) {
        int speed = Math.clamp(speedMultiplier, 1, 4);
        int mutationBudget = mutationsPerTick(speed);
        int batches = Math.max(
                1,
                (Math.max(0, changedPositions)
                                + mutationBudget
                                - 1)
                        / mutationBudget);
        return Math.clamp(
                batches + Math.max(1, SETTLE_TICKS / speed),
                Math.max(1, MIN_TRANSITION_TICKS / speed),
                Math.max(1, MAX_TRANSITION_TICKS / speed));
    }

    public static int mutationsPerTick(int speedMultiplier) {
        return MUTATIONS_PER_TICK * Math.clamp(speedMultiplier, 1, 4);
    }

    public static float progress(long elapsedNanos, long durationNanos) {
        if (durationNanos <= 0L) {
            return 1.0F;
        }
        return Math.clamp(
                (float) elapsedNanos / durationNanos,
                0.0F,
                1.0F);
    }

    public static float strength(float progress) {
        float p = Math.clamp(progress, 0.0F, 1.0F);
        float opening = smoother(Math.min(1.0F, p / 0.10F));
        float closing = 1.0F - smoother(Math.max(
                0.0F,
                (p - FADE_START) / (1.0F - FADE_START)));
        return opening * closing;
    }

    /**
     * The terrain fissure is not a second animation clock. It condenses from
     * the existing transmutation front as that front reaches the authored
     * perimeter, remains while history is physical, and is swallowed by the
     * same front on restoration.
     */
    public static float boundaryEnvelope(
            float progress,
            boolean restoring) {
        float currentFront = front(progress, restoring);
        return smoother(Math.clamp(
                (currentFront - 0.74F) / 0.24F,
                0.0F,
                1.0F));
    }

    public static float front(float progress, boolean restoring) {
        float p = Math.clamp(progress, 0.0F, 1.0F);
        float travel = smoother(Math.clamp(
                (p - TRAVEL_START)
                        / (TRAVEL_END - TRAVEL_START),
                0.0F,
                1.0F));
        if (travel <= 0.0F) {
            return restoring ? SEAM_END : SEAM_START;
        }
        if (travel >= 1.0F) {
            return restoring ? SEAM_START : SEAM_END;
        }
        float materializing =
                SEAM_START + (SEAM_END - SEAM_START) * travel;
        return restoring
                ? SEAM_END + SEAM_START - materializing
                : materializing;
    }

    /**
     * Authoritative spatial coordinate shared by mutation order, ghost
     * opacity and the surface fallback. Zero is the volume centre and the
     * furthest authored corner is one.
     */
    public static float normalizedCoordinate(
            Vec3 position,
            Vec3 center,
            Vec3 halfExtents) {
        Vec3 relative = position.subtract(center);
        double x = relative.x / Math.max(halfExtents.x, 0.5);
        double y = relative.y / Math.max(halfExtents.y, 0.5);
        double z = relative.z / Math.max(halfExtents.z, 0.5);
        return (float) Math.clamp(
                Math.sqrt(x * x + y * y + z * z)
                        / Math.sqrt(3.0),
                0.0,
                1.0);
    }

    public static float condensation(
            float coordinate,
            float front) {
        float x = Math.clamp(
                (front - coordinate + BLOCK_BLEND_WIDTH)
                        / (BLOCK_BLEND_WIDTH * 2.0F),
                0.0F,
                1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    /**
     * Complementary visibility shared by remembered blocks and present
     * occluders while either timeline crosses the physical world.
     */
    public static float ghostPresence(
            float coordinate,
            float front) {
        return 1.0F - condensation(coordinate, front);
    }

    public static boolean shouldMutate(
            float coordinate,
            float progress,
            boolean restoring) {
        float currentFront = front(progress, restoring);
        return restoring
                ? coordinate >= currentFront - SERVER_MUTATION_LEAD
                : coordinate <= currentFront + SERVER_MUTATION_LEAD;
    }

    /**
     * The server consumes a single queue. An outward crest needs inner cells
     * first; a returning crest needs outer cells first or the queue stalls
     * behind a cell that the visual front has not reached yet.
     */
    public static int compareMutationOrder(
            float leftCoordinate,
            float rightCoordinate,
            boolean restoring) {
        return restoring
                ? Float.compare(rightCoordinate, leftCoordinate)
                : Float.compare(leftCoordinate, rightCoordinate);
    }

    /**
     * A dense spatial band may contain more mutations than one server tick can
     * safely publish. Hold the shared clock until that ready backlog is empty
     * so the shader can never run ahead of the physical world transaction.
     */
    public static int advanceServerClock(
            int currentTick,
            int durationTicks,
            boolean readyBacklog) {
        int boundedDuration = Math.max(1, durationTicks);
        if (readyBacklog) {
            return Math.clamp(currentTick, 0, boundedDuration);
        }
        return Math.min(Math.max(0, currentTick) + 1, boundedDuration);
    }

    /**
     * During materialization the vanilla mesh is always authoritative: before
     * the crest it is the untouched present, afterwards it is the physical
     * past. During restoration it yields to the active Past Echo immediately
     * after the returning crest has restored the present block.
     */
    public static boolean nativeWorldOwnsBlock(
            float coordinate,
            float progress,
            boolean restoring,
            boolean materializedPast) {
        if (!restoring) {
            return true;
        }
        return materializedPast
                && !shouldMutate(
                        coordinate,
                        progress,
                        true);
    }

    private static float smoother(float value) {
        float clamped = Math.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * clamped
                * (clamped * (clamped * 6.0F - 15.0F) + 10.0F);
    }

    private PhilosophersStoneVisualTiming() {
    }
}
