package dev.alvar.echoespast.server;

import net.minecraft.world.phys.Vec3;

/**
 * Shared low-frequency pulse timing. Travel is strictly {@code distance / speed}
 * so a pedestal's reply delay teaches range, and listening (screen darkening)
 * lasts until a worst-case round trip can no longer arrive.
 */
public final class LowFrequencySonarMath {
    public static final double LISTENING_LINGER_SECONDS = 1.0;
    public static final double LISTENING_FADE_SECONDS = 1.0;
    public static final double CANCELLATION_FADE_SECONDS = 0.7;
    public static final double PEDESTAL_HINT_SECONDS = 5.0;
    private static final double PEDESTAL_HINT_FADE_IN_SECONDS = 0.18;

    public static int travelTicks(double distance, double blocksPerSecond) {
        return Math.max(0, (int) Math.ceil(travelSeconds(distance, blocksPerSecond) * 20.0));
    }

    public static int travelTicks(
            double distance,
            double maximumRange,
            double blocksPerSecond) {
        return Math.max(
                0,
                (int) Math.ceil(travelSeconds(distance, maximumRange, blocksPerSecond) * 20.0));
    }

    public static double travelSeconds(double distance, double blocksPerSecond) {
        double speed = Math.max(1.0, blocksPerSecond);
        return Math.max(0.0, distance) / speed;
    }

    public static double travelSeconds(
            double distance,
            double maximumRange,
            double blocksPerSecond) {
        double safeRange = Math.max(0.0, maximumRange);
        double safeDistance = Math.clamp(distance, 0.0, safeRange);
        return travelSeconds(safeDistance, blocksPerSecond);
    }

    public static double radius(double elapsedSeconds, double blocksPerSecond, double maximumRange) {
        double safeRange = Math.max(0.0, maximumRange);
        return Math.min(expandingRadius(elapsedSeconds, blocksPerSecond), safeRange);
    }

    /** Unclamped crest distance so the front keeps moving instead of parking at max range. */
    public static double expandingRadius(double elapsedSeconds, double blocksPerSecond) {
        double speed = Math.max(1.0, blocksPerSecond);
        return Math.max(0.0, elapsedSeconds) * speed;
    }

    /**
     * Softens the crest through the last stretch of device range and reaches
     * zero exactly at the limit, so the wave dissolves instead of freezing.
     */
    public static float rangeEdgeFade(double travelledDistance, double maximumRange) {
        double safeRange = Math.max(0.0, maximumRange);
        if (safeRange <= 0.0 || travelledDistance >= safeRange) {
            return 0.0F;
        }
        if (travelledDistance <= 0.0) {
            return 1.0F;
        }
        double fadeSpan = Math.min(96.0, Math.max(24.0, safeRange * 0.12));
        double fadeStart = safeRange - fadeSpan;
        if (travelledDistance <= fadeStart) {
            return 1.0F;
        }
        double t = (travelledDistance - fadeStart) / fadeSpan;
        return (float) (1.0 - smoothStep(Math.clamp(t, 0.0, 1.0)));
    }

    public static boolean isCurrentResult(long activePulseId, long resultPulseId) {
        return activePulseId >= 0L && activePulseId == resultPulseId;
    }

    public static boolean withinCooldown(double elapsedSeconds, int cooldownTicks) {
        return elapsedSeconds >= 0.0 && elapsedSeconds < Math.max(0, cooldownTicks) / 20.0;
    }

    /**
     * Listening covers the outbound sweep to device range plus the slowest
     * possible return, then a short linger to read the last crest. Item cooldown
     * is derived from this same window so the screen undarkens when no more
     * replies can arrive.
     */
    public static double listeningDurationSeconds(double range, double blocksPerSecond) {
        double travel = travelSeconds(range, range, blocksPerSecond);
        return travel * 2.0 + LISTENING_LINGER_SECONDS;
    }

    /**
     * @deprecated Prefer {@link #listeningDurationSeconds(double, double)}. The
     * cooldown argument is ignored; listening is purely physical.
     */
    @Deprecated
    public static double listeningDurationSeconds(
            double range,
            double blocksPerSecond,
            int cooldownTicks) {
        return listeningDurationSeconds(range, blocksPerSecond);
    }

    public static int listeningTicks(double range, double blocksPerSecond) {
        return Math.max(1, (int) Math.ceil(listeningDurationSeconds(range, blocksPerSecond) * 20.0));
    }

    public static float listeningEnvelope(double elapsedSeconds, double durationSeconds) {
        if (elapsedSeconds <= 0.0 || elapsedSeconds >= durationSeconds) {
            return 0.0F;
        }
        double entrance = Math.clamp(elapsedSeconds / LISTENING_FADE_SECONDS, 0.0, 1.0);
        double exit = Math.clamp(
                (durationSeconds - elapsedSeconds) / LISTENING_FADE_SECONDS,
                0.0,
                1.0);
        return (float) smoothStep(Math.min(entrance, exit));
    }

    public static float cancellationEnvelope(double elapsedSeconds) {
        if (elapsedSeconds <= 0.0) {
            return 1.0F;
        }
        if (elapsedSeconds >= CANCELLATION_FADE_SECONDS) {
            return 0.0F;
        }
        double remaining = 1.0 - elapsedSeconds / CANCELLATION_FADE_SECONDS;
        return (float) smoothStep(remaining);
    }

    public static float pedestalHintEnvelope(double elapsedSeconds) {
        if (elapsedSeconds < 0.0 || elapsedSeconds >= PEDESTAL_HINT_SECONDS) {
            return 0.0F;
        }
        double entrance = Math.clamp(
                elapsedSeconds / PEDESTAL_HINT_FADE_IN_SECONDS,
                0.0,
                1.0);
        double lifetime = Math.clamp(
                elapsedSeconds / PEDESTAL_HINT_SECONDS,
                0.0,
                1.0);
        return (float) (smoothStep(entrance) * (1.0 - smoothStep(lifetime)));
    }

    /**
     * Real geometry beyond the far plane cannot be rendered. Nearby beams stay
     * on their pedestal; distant responses use a horizon proxy on the exact
     * camera-to-pedestal ray, leaving room for the tall tapered tips.
     */
    public static double beamDisplayDistance(
            double actualDistance,
            double renderDistance) {
        if (actualDistance <= 0.0 || renderDistance <= 0.0) {
            return 0.0;
        }
        return actualDistance <= renderDistance * 0.92
                ? actualDistance
                : Math.min(actualDistance, renderDistance * 0.58);
    }

    /**
     * Long-distance signals retain a readable core, but distance steadily
     * reduces their strength instead of making a whole front vanish at once.
     */
    public static float signalAttenuation(double travelledDistance, double maximumRange) {
        if (maximumRange <= 0.0) {
            return 0.0F;
        }
        double normalized = Math.clamp(
                Math.max(0.0, travelledDistance) / maximumRange,
                0.0,
                1.0);
        return (float) (1.0 - smoothStep(normalized) * 0.78);
    }

    private static double smoothStep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    public static double visibleRange(int deviceRange, int renderDistanceChunks) {
        double renderedBlocks = Math.max(1, renderDistanceChunks) * 16.0;
        return Math.min(Math.max(0, deviceRange), renderedBlocks);
    }

    /**
     * True when a spherical crest around {@code origin} intersects the camera's
     * visible sphere. Used so a player can catch up to a pulse that already
     * travelled past the render distance measured from the pulse origin.
     */
    public static boolean waveIntersectsVisibleRange(
            double originToCameraDistance,
            double front,
            double visibleRange,
            double support) {
        double safeVisible = Math.max(0.0, visibleRange);
        double safeSupport = Math.max(0.0, support);
        double centerDistance = Math.max(0.0, originToCameraDistance);
        double nearestVisibleDistance = Math.max(0.0, centerDistance - safeVisible);
        double farthestVisibleDistance = centerDistance + safeVisible;
        return front + safeSupport >= nearestVisibleDistance
                && front - safeSupport <= farthestVisibleDistance;
    }

    /**
     * Full 3D cone test shared by server selection and client masking. A cone
     * of 360° (or less than a half-degree) is treated as omnidirectional.
     */
    public static boolean withinCone(
            Vec3 origin,
            Vec3 direction,
            Vec3 target,
            float coneDegrees) {
        if (coneDegrees >= 359.5F) {
            return true;
        }
        double dirLenSq = direction.lengthSqr();
        if (dirLenSq < 1.0E-6) {
            return true;
        }
        Vec3 toward = target.subtract(origin);
        if (toward.lengthSqr() < 1.0E-6) {
            return true;
        }
        double minimumDot = Math.cos(Math.toRadians(Math.max(0.5F, coneDegrees) * 0.5));
        return toward.normalize().dot(direction.normalize()) >= minimumDot;
    }

    public static float coneCosHalfAngle(float coneDegrees) {
        if (coneDegrees >= 359.5F) {
            return 2.0F;
        }
        return (float) Math.cos(Math.toRadians(Math.max(0.5F, coneDegrees) * 0.5));
    }

    public static int recordResponse(int responseCount, boolean accessible) {
        return accessible ? Math.max(0, responseCount) + 1 : Math.max(0, responseCount);
    }

    public static boolean shouldSendNoResponse(int responseCount) {
        return responseCount <= 0;
    }

    /**
     * Stretch the shared layered profile enough to remain continuous without
     * turning the low-frequency front into a broad wall of light.
     */
    public static double surfaceWidthScale(double blocksPerSecond) {
        return Math.clamp(Math.max(0.0, blocksPerSecond) / 48.0, 4.0, 24.0);
    }

    private LowFrequencySonarMath() {
    }
}
