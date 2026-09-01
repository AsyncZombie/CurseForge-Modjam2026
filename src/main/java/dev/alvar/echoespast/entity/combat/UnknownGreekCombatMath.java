package dev.alvar.echoespast.entity.combat;

import net.minecraft.world.phys.Vec3;

/** Pure geometry used by the server combat controller and focused GameTests. */
public final class UnknownGreekCombatMath {
    private static final double EPSILON = 1.0E-8D;
    private static final double[] PHALANX_GAPS = {-4.0D, -2.0D, 0.0D, 2.0D, 4.0D};
    public static final double UNREACHABLE_HEIGHT_THRESHOLD = 2.25D;

    private UnknownGreekCombatMath() {
    }

    public static Vec3 horizontalDirection(Vec3 from, Vec3 to, Vec3 fallback) {
        Vec3 delta = new Vec3(to.x - from.x, 0.0D, to.z - from.z);
        if (delta.lengthSqr() > EPSILON) {
            return delta.normalize();
        }
        Vec3 horizontalFallback = new Vec3(fallback.x, 0.0D, fallback.z);
        return horizontalFallback.lengthSqr() > EPSILON
                ? horizontalFallback.normalize()
                : new Vec3(0.0D, 0.0D, 1.0D);
    }

    /** Predicts horizontal movement while bounding evasive lead to a readable distance. */
    public static Vec3 predictHorizontal(
            Vec3 position,
            Vec3 velocity,
            int ticks,
            double maximumLead) {
        Vec3 lead = new Vec3(velocity.x, 0.0D, velocity.z).scale(Math.max(0, ticks));
        if (lead.lengthSqr() > maximumLead * maximumLead) {
            lead = lead.normalize().scale(maximumLead);
        }
        return position.add(lead);
    }

    /** Keeps a phalanx row aligned to a stable world axis instead of wobbling. */
    public static Vec3 snapToCardinal(Vec3 direction) {
        if (Math.abs(direction.x) >= Math.abs(direction.z)) {
            return new Vec3(Math.copySign(1.0D, direction.x == 0.0D ? 1.0D : direction.x), 0.0D, 0.0D);
        }
        return new Vec3(0.0D, 0.0D, Math.copySign(1.0D, direction.z));
    }

    public static boolean gapReachable(
            double currentLateral,
            double gapLateral,
            double movementPerTick,
            int availableTicks) {
        return Math.abs(gapLateral - currentLateral)
                <= Math.max(0.0D, movementPerTick) * Math.max(0, availableTicks) + EPSILON;
    }

    /**
     * Elevation alone is not enough to force ranged attacks: stairs and arena
     * routes remain valid counters. The restricted pattern starts only after
     * navigation has also confirmed that the target cannot be reached.
     */
    public static boolean isElevatedUnreachable(
            double verticalDifference,
            boolean navigationCanReach) {
        return verticalDifference > UNREACHABLE_HEIGHT_THRESHOLD && !navigationCanReach;
    }

    /** A readable world-space arc shared by the spectral javelin trail and projectile. */
    public static Vec3 javelinArcPoint(
            Vec3 start,
            Vec3 end,
            double progress,
            double lift) {
        double clamped = Math.clamp(progress, 0.0D, 1.0D);
        Vec3 linear = start.lerp(end, clamped);
        double arc = Math.max(0.0D, lift) * 4.0D * clamped * (1.0D - clamped);
        return linear.add(0.0D, arc, 0.0D);
    }

    /**
     * Produces a restrained ballistic apex: long throws rise more than short
     * ones, but never turn into the old near-vertical mortar shot.
     */
    public static double javelinArcLift(Vec3 start, Vec3 end) {
        double dx = end.x - start.x;
        double dz = end.z - start.z;
        double horizontalRange = Math.sqrt(dx * dx + dz * dz);
        double verticalRange = Math.abs(end.y - start.y);
        return Math.clamp(1.35D + horizontalRange * 0.13D + verticalRange * 0.08D, 2.0D, 5.2D);
    }

    /**
     * A fast initial lift followed by a short hold. Keeping this curve pure lets
     * the server restraint and the world-space spear overlay use the same beat.
     */
    public static double impaleLiftProgress(int elapsedTicks, int durationTicks) {
        if (durationTicks <= 0) {
            return 1.0D;
        }
        // Doubling the restraint extends the threatening hold, not the lift itself.
        double liftTicks = Math.min(durationTicks * 0.55D, 14.0D);
        double normalized = Math.clamp(elapsedTicks / liftTicks, 0.0D, 1.0D);
        return normalized * normalized * (3.0D - 2.0D * normalized);
    }

    /**
     * Places one of the many thin spears on a concentric ring. The jitter is
     * deterministic: the server-synchronised phase produces the same authored
     * disorder on every client without spawning entities or particles.
     */
    public static Vec3 spearRingPoint(
            Vec3 origin,
            int ringIndex,
            int spearIndex,
            int spearCount,
            double firstRadius,
            double ringSpacing,
            double angularPhase) {
        int count = Math.max(1, spearCount);
        double radius = Math.max(0.0D, firstRadius)
                + Math.max(0, ringIndex) * Math.max(0.0D, ringSpacing);
        radius = Math.max(0.25D, radius + spectralSpearNoise(
                ringIndex, spearIndex, angularPhase, 0) * 0.30D);
        double stagger = (ringIndex & 1) == 0 ? 0.0D : Math.PI / count;
        double angularStep = Math.PI * 2.0D / count;
        double angle = angularPhase
                + stagger
                + angularStep * spearIndex
                + angularStep * spectralSpearNoise(
                        ringIndex, spearIndex, angularPhase, 1) * 0.32D;
        return origin.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
    }

    /** A subtly tilted, unit-length shaft direction for the world renderer. */
    public static Vec3 spearVisualTilt(
            int ringIndex,
            int spearIndex,
            double angularPhase) {
        double tiltX = spectralSpearNoise(ringIndex, spearIndex, angularPhase, 2) * 0.17D;
        double tiltZ = spectralSpearNoise(ringIndex, spearIndex, angularPhase, 3) * 0.17D;
        return new Vec3(tiltX, 1.0D, tiltZ).normalize();
    }

    /** Tests the occupied disk after at least one ring has risen. */
    public static boolean spearFieldContains(
            Vec3 origin,
            Vec3 targetCenter,
            double outerRadius,
            double innerRadius,
            double margin,
            double verticalAllowance) {
        if (Math.abs(targetCenter.y - origin.y) > Math.max(0.0D, verticalAllowance)) {
            return false;
        }
        double dx = targetCenter.x - origin.x;
        double dz = targetCenter.z - origin.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double safeCenter = Math.max(0.0D, innerRadius - Math.max(0.0D, margin));
        double occupiedEdge = Math.max(safeCenter, outerRadius + Math.max(0.0D, margin));
        return distance + EPSILON >= safeCenter && distance <= occupiedEdge + EPSILON;
    }

    private static double spectralSpearNoise(
            int ringIndex,
            int spearIndex,
            double angularPhase,
            int channel) {
        long value = Double.doubleToLongBits(angularPhase + 31.0D);
        value ^= (long) (ringIndex + 1) * 0x9E3779B97F4A7C15L;
        value ^= (long) (spearIndex + 1) * 0xC2B2AE3D27D4EB4FL;
        value ^= (long) (channel + 1) * 0x165667B19E3779F9L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        double zeroToOne = (value >>> 11) * 0x1.0p-53;
        return zeroToOne * 2.0D - 1.0D;
    }

    /** Tests the expanding hazardous edge without making the standing spears deal repeated damage. */
    public static boolean spearRingContains(
            Vec3 origin,
            Vec3 targetCenter,
            double ringRadius,
            double halfThickness,
            double verticalAllowance) {
        if (Math.abs(targetCenter.y - origin.y) > Math.max(0.0D, verticalAllowance)) {
            return false;
        }
        double dx = targetCenter.x - origin.x;
        double dz = targetCenter.z - origin.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        return Math.abs(distance - Math.max(0.0D, ringRadius))
                <= Math.max(0.0D, halfThickness) + EPSILON;
    }

    /**
     * Selects the next phalanx opening relative to the previous row: same
     * street, one lane left, or one lane right. {@code avoid} remains for API
     * compatibility but never forces an unreachable jump.
     */
    public static double nextPhalanxGap(int entropy, double previous, double avoid) {
        double[] candidates = new double[3];
        int count = 0;
        for (double delta : new double[] {0.0D, -2.0D, 2.0D}) {
            double gap = previous + delta;
            if (!isPhalanxGap(gap)) {
                continue;
            }
            candidates[count++] = gap;
        }
        if (count == 0) {
            return previous;
        }
        // Soft preference: when several options exist, de-prioritize {@code avoid}.
        if (!Double.isNaN(avoid) && count > 1) {
            int filtered = 0;
            double[] preferred = new double[count];
            for (int i = 0; i < count; i++) {
                if (Math.abs(candidates[i] - avoid) > EPSILON) {
                    preferred[filtered++] = candidates[i];
                }
            }
            if (filtered > 0) {
                return preferred[Math.floorMod(entropy, filtered)];
            }
        }
        return candidates[Math.floorMod(entropy, count)];
    }

    public static boolean isPhalanxGap(double gap) {
        for (double candidate : PHALANX_GAPS) {
            if (Math.abs(candidate - gap) <= EPSILON) {
                return true;
            }
        }
        return false;
    }

    public static double initialPhalanxGap(int entropy) {
        return PHALANX_GAPS[Math.floorMod(entropy, PHALANX_GAPS.length)];
    }

    public static boolean shouldShieldBash(
            double horizontalDistance,
            int pressureTicks,
            int requiredPressureTicks,
            int cooldownTicks) {
        return horizontalDistance <= 3.35D
                && pressureTicks >= requiredPressureTicks
                && cooldownTicks <= 0;
    }

    /**
     * Shared straight-line corridor used by spectral entities and their warning.
     * Its height is deliberately invariant: the formation is a memory projection
     * that crosses architecture, not a physical group walking over terrain.
     */
    public static Vec3 phalanxCorridorPoint(
            Vec3 anchor,
            Vec3 direction,
            double lateralOffset,
            double forwardDistance,
            double visualLift) {
        Vec3 forward = horizontalDirection(Vec3.ZERO, direction, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        return anchor
                .add(side.scale(lateralOffset))
                .add(forward.scale(forwardDistance))
                .add(0.0D, visualLift, 0.0D);
    }

    /**
     * Tests an arc centred on {@code forward}. An arc of 120 degrees therefore
     * accepts attackers up to 60 degrees either side of the shield normal.
     */
    public static boolean isInsideFrontArc(
            Vec3 forward,
            Vec3 directionToThreat,
            double arcDegrees) {
        Vec3 flatForward = new Vec3(forward.x, 0.0D, forward.z);
        Vec3 flatThreat = new Vec3(directionToThreat.x, 0.0D, directionToThreat.z);
        if (flatForward.lengthSqr() <= EPSILON || flatThreat.lengthSqr() <= EPSILON) {
            return false;
        }
        double threshold = Math.cos(Math.toRadians(arcDegrees * 0.5D));
        return flatForward.normalize().dot(flatThreat.normalize()) + EPSILON >= threshold;
    }

    /**
     * Continuous horizontal capsule test. The vertical allowance is explicit so
     * a spear cannot hit a player on a different floor while crossing their X/Z.
     */
    public static boolean capsuleContains(
            Vec3 segmentStart,
            Vec3 segmentEnd,
            Vec3 targetCenter,
            double horizontalRadius,
            double verticalAllowance) {
        if (Math.abs(targetCenter.y - segmentStart.y) > verticalAllowance
                && Math.abs(targetCenter.y - segmentEnd.y) > verticalAllowance) {
            return false;
        }
        double segmentX = segmentEnd.x - segmentStart.x;
        double segmentZ = segmentEnd.z - segmentStart.z;
        double lengthSqr = segmentX * segmentX + segmentZ * segmentZ;
        double t = lengthSqr <= EPSILON
                ? 0.0D
                : ((targetCenter.x - segmentStart.x) * segmentX
                                + (targetCenter.z - segmentStart.z) * segmentZ)
                        / lengthSqr;
        t = Math.clamp(t, 0.0D, 1.0D);
        double closestX = segmentStart.x + segmentX * t;
        double closestZ = segmentStart.z + segmentZ * t;
        double dx = targetCenter.x - closestX;
        double dz = targetCenter.z - closestZ;
        return dx * dx + dz * dz <= horizontalRadius * horizontalRadius + EPSILON;
    }
}
