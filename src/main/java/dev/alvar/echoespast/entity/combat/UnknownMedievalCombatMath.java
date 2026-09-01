package dev.alvar.echoespast.entity.combat;

import net.minecraft.world.phys.Vec3;

/** Pure, testable hit geometry for the restrained Medieval Past duel. */
public final class UnknownMedievalCombatMath {
    private static final double EPSILON = 1.0E-8D;

    private UnknownMedievalCombatMath() {
    }

    public static boolean meleeArcContains(
            Vec3 origin,
            Vec3 forward,
            Vec3 targetCenter,
            double reach,
            double targetRadius,
            double arcDegrees,
            double verticalAllowance) {
        if (Math.abs(targetCenter.y - origin.y) > Math.max(0.0D, verticalAllowance)) {
            return false;
        }
        Vec3 toTarget = new Vec3(
                targetCenter.x - origin.x,
                0.0D,
                targetCenter.z - origin.z);
        double maximumDistance = Math.max(0.0D, reach) + Math.max(0.0D, targetRadius);
        if (toTarget.lengthSqr() > maximumDistance * maximumDistance + EPSILON) {
            return false;
        }
        if (toTarget.lengthSqr() <= EPSILON) {
            return true;
        }
        return UnknownGreekCombatMath.isInsideFrontArc(forward, toTarget, arcDegrees);
    }

    /** Selects the readable close or pursuit finisher exactly once at the branch beat. */
    public static byte selectComboVariant(double horizontalDistance, double sweepThreshold) {
        return horizontalDistance <= sweepThreshold
                ? (byte) 1
                : (byte) 2;
    }

    /**
     * Turns {@code current} toward {@code desired} without ever exceeding the
     * authored horizontal cone. Both inputs may be non-normalized.
     */
    public static Vec3 limitedHorizontalTurn(
            Vec3 current,
            Vec3 desired,
            double maximumDegrees) {
        Vec3 forward = horizontalUnit(current, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 target = horizontalUnit(desired, forward);
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        double signedDegrees = Math.toDegrees(Math.atan2(
                target.dot(side),
                target.dot(forward)));
        double limited = Math.clamp(
                signedDegrees,
                -Math.max(0.0D, maximumDegrees),
                Math.max(0.0D, maximumDegrees));
        double radians = Math.toRadians(limited);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Vec3(
                forward.x * cosine - forward.z * sine,
                0.0D,
                forward.x * sine + forward.z * cosine);
    }

    /** Normalized endpoints for one tick inside an active attack window. */
    public static double windowProgress(int elapsed, int activeStart, int activeTicks) {
        if (activeTicks <= 0) {
            return elapsed < activeStart ? 0.0D : 1.0D;
        }
        return Math.clamp((elapsed - activeStart) / (double) activeTicks, 0.0D, 1.0D);
    }

    /** One authoritative cut can resolve at most once across its four samples. */
    public static boolean mayApplyCutHit(boolean alreadyHit, boolean intersectsThisSample) {
        return !alreadyHit && intersectsThisSample;
    }

    /**
     * Continuous annular-sector collision for one tick of a sword path. The
     * player's horizontal radius expands both the blade reach and angular
     * interval, preventing gaps between the four authoritative samples.
     */
    public static boolean sweptSwordPathContains(
            Vec3 origin,
            Vec3 forward,
            Vec3 targetCenter,
            double innerRadius,
            double outerRadius,
            double targetRadius,
            double startDegrees,
            double endDegrees,
            double verticalAllowance) {
        Vec3 offset = targetCenter.subtract(origin);
        if (Math.abs(offset.y) > Math.max(0.0D, verticalAllowance)) {
            return false;
        }
        double radius = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        double expandedTarget = Math.max(0.0D, targetRadius);
        double minimumRadius = Math.max(0.0D, innerRadius - expandedTarget);
        double maximumRadius = Math.max(minimumRadius, outerRadius + expandedTarget);
        if (radius + EPSILON < minimumRadius || radius - EPSILON > maximumRadius) {
            return false;
        }
        if (radius <= EPSILON) {
            return minimumRadius <= EPSILON;
        }
        Vec3 flatForward = horizontalUnit(forward, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 side = new Vec3(-flatForward.z, 0.0D, flatForward.x);
        double targetAngle = Math.toDegrees(Math.atan2(
                offset.x * side.x + offset.z * side.z,
                offset.x * flatForward.x + offset.z * flatForward.z));
        double angularExpansion = Math.toDegrees(Math.asin(Math.clamp(
                expandedTarget / Math.max(radius, expandedTarget),
                0.0D,
                1.0D)));
        double minimumAngle = Math.min(startDegrees, endDegrees) - angularExpansion - 0.001D;
        double maximumAngle = Math.max(startDegrees, endDegrees) + angularExpansion + 0.001D;
        return targetAngle >= minimumAngle && targetAngle <= maximumAngle;
    }

    public static boolean overheadLaneContains(
            Vec3 origin,
            Vec3 forward,
            Vec3 targetCenter,
            double reach,
            double radius,
            double verticalAllowance) {
        Vec3 direction = UnknownGreekCombatMath.horizontalDirection(
                Vec3.ZERO,
                forward,
                new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 start = origin.add(direction.scale(0.35D));
        Vec3 end = origin.add(direction.scale(Math.max(0.35D, reach)));
        return UnknownGreekCombatMath.capsuleContains(
                start,
                end,
                targetCenter,
                Math.max(0.0D, radius),
                Math.max(0.0D, verticalAllowance));
    }

    private static Vec3 horizontalUnit(Vec3 direction, Vec3 fallback) {
        Vec3 flat = new Vec3(direction.x, 0.0D, direction.z);
        if (flat.lengthSqr() <= EPSILON) {
            flat = new Vec3(fallback.x, 0.0D, fallback.z);
        }
        return flat.lengthSqr() <= EPSILON
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : flat.normalize();
    }
}
