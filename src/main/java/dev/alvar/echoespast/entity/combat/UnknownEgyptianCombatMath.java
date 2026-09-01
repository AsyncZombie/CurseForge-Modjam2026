package dev.alvar.echoespast.entity.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Pure polar geometry shared by the Egyptian khopesh hitbox and its renderer. */
public final class UnknownEgyptianCombatMath {
    private static final double EPSILON = 1.0E-8D;

    private UnknownEgyptianCombatMath() {
    }

    public static Vec3 rotateHorizontal(Vec3 forward, double degrees) {
        Vec3 flat = new Vec3(forward.x, 0.0D, forward.z);
        if (flat.lengthSqr() <= EPSILON) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            flat = flat.normalize();
        }
        double radians = Math.toRadians(degrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Vec3(
                flat.x * cosine - flat.z * sine,
                0.0D,
                flat.x * sine + flat.z * cosine);
    }

    /**
     * Continuous annular-sector test for one server tick of a blade sweep.
     * Angles are signed around {@code forward}; either sweep direction is valid.
     */
    public static boolean sweptArcContains(
            Vec3 origin,
            Vec3 forward,
            double startDegrees,
            double endDegrees,
            Vec3 targetCenter,
            double innerRadius,
            double outerRadius,
            double verticalAllowance) {
        Vec3 offset = targetCenter.subtract(origin);
        if (Math.abs(offset.y) > Math.max(0.0D, verticalAllowance)) {
            return false;
        }
        double radius = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        if (radius + EPSILON < Math.max(0.0D, innerRadius)
                || radius - EPSILON > Math.max(innerRadius, outerRadius)) {
            return false;
        }
        Vec3 flatForward = new Vec3(forward.x, 0.0D, forward.z);
        if (flatForward.lengthSqr() <= EPSILON || radius <= EPSILON) {
            return radius <= Math.max(innerRadius, outerRadius);
        }
        flatForward = flatForward.normalize();
        Vec3 side = new Vec3(-flatForward.z, 0.0D, flatForward.x);
        double targetAngle = Math.toDegrees(Math.atan2(
                offset.x * side.x + offset.z * side.z,
                offset.x * flatForward.x + offset.z * flatForward.z));
        double minimum = Math.min(startDegrees, endDegrees) - 0.001D;
        double maximum = Math.max(startDegrees, endDegrees) + 0.001D;
        return targetAngle >= minimum && targetAngle <= maximum;
    }

    /** Stable triangular formation used by both the warning renderer and server impact. */
    public static Vec3 solarSealCenter(
            Vec3 anchor,
            Vec3 forward,
            int sealIndex,
            double lateralOffset,
            double forwardOffset) {
        Vec3 flatForward = new Vec3(forward.x, 0.0D, forward.z);
        if (flatForward.lengthSqr() <= EPSILON) {
            flatForward = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            flatForward = flatForward.normalize();
        }
        if (sealIndex <= 0) {
            return anchor;
        }
        Vec3 side = new Vec3(-flatForward.z, 0.0D, flatForward.x);
        double sign = sealIndex == 1 ? 1.0D : -1.0D;
        return anchor
                .add(flatForward.scale(forwardOffset))
                .add(side.scale(lateralOffset * sign));
    }

    public static boolean sealContains(
            Vec3 center,
            Vec3 targetCenter,
            double radius,
            double verticalAllowance) {
        double x = targetCenter.x - center.x;
        double z = targetCenter.z - center.z;
        return x * x + z * z <= radius * radius + EPSILON
                && Math.abs(targetCenter.y - center.y) <= verticalAllowance;
    }

    /** Wide enough to interrupt one escape line while leaving both ends reachable. */
    public static final double WALL_HALF_SPAN = 5.25D;
    /** A real architectural slab, not a paper-thin luminous plane. */
    public static final double WALL_HALF_THICK = 0.52D;
    public static final double WALL_HEIGHT = 4.65D;
    /** Extra standoff so the player cannot sink into the sheet between ticks. */
    public static final double WALL_PUSH_MARGIN = 0.08D;
    public static final double DUAT_REAR_DISTANCE = 4.8D;
    public static final double DUAT_SIDE_DISTANCE = 0.0D;
    public static final double DUAT_SIDE_CENTER_FORWARD = 0.0D;
    public static final double DUAT_REAR_HALF_SPAN = WALL_HALF_SPAN;
    public static final double DUAT_SIDE_HALF_SPAN = WALL_HALF_SPAN;
    public static final double CHARIOT_CORNER_RADIUS = 3.25D;

    /** @deprecated Prefer continuous {@link #wallContains}. */
    @Deprecated
    public static final double[] THRESHOLD_LANES = {-5.0D, -2.5D, 0.0D, 2.5D, 5.0D};

    public static int thresholdLaneCount() {
        return THRESHOLD_LANES.length;
    }

    public static int thresholdSafeLaneWidth(boolean ruins) {
        return 0;
    }

    public static int thresholdSafeStart(int seed) {
        return Math.floorMod(seed, THRESHOLD_LANES.length);
    }

    /** Every lane is sealed; escape is around the wall ends, never through. */
    public static boolean isThresholdLaneSealed(int laneIndex, int seed, boolean ruins) {
        return true;
    }

    public static Vec3 thresholdCenter(Vec3 anchor, Vec3 forward, int laneIndex, double along) {
        return wallCenter(anchor, forward, along);
    }

    public static Vec3 wallCenter(Vec3 anchor, Vec3 forward, double along) {
        return anchor.add(horizontalUnit(forward).scale(along));
    }

    /**
     * The Duat attack is one broad slab ahead of the player's escape vector.
     * Keeping the legacy panel parameters makes old saves/payload consumers
     * harmless, but every index resolves to the same non-enclosing wall.
     */
    public static Vec3 duatGatePanelCenter(
            Vec3 anchor,
            Vec3 forward,
            int panelIndex,
            double firstSideSign) {
        return anchor.add(horizontalUnit(forward).scale(DUAT_REAR_DISTANCE));
    }

    public static Vec3 duatGatePanelDirection(
            Vec3 forward,
            int panelIndex,
            double firstSideSign) {
        return horizontalUnit(forward);
    }

    public static double duatGatePanelHalfSpan(int panelIndex) {
        return DUAT_REAR_HALF_SPAN;
    }

    public static boolean duatGateContains(
            Vec3 anchor,
            Vec3 forward,
            double firstSideSign,
            Vec3 targetCenter) {
        Vec3 center = duatGatePanelCenter(anchor, forward, 0, firstSideSign)
                .add(0.0D, WALL_HEIGHT * 0.45D, 0.0D);
        return wallContains(
                center,
                duatGatePanelDirection(forward, 0, firstSideSign),
                targetCenter,
                duatGatePanelHalfSpan(0),
                WALL_HALF_THICK,
                WALL_HEIGHT * 0.55D);
    }

    /** True only when the player's velocity is carrying them farther from Unknown. */
    public static boolean isEscaping(Vec3 bossPosition, Vec3 playerPosition, Vec3 playerVelocity) {
        Vec3 away = horizontalUnit(playerPosition.subtract(bossPosition));
        Vec3 motion = new Vec3(playerVelocity.x, 0.0D, playerVelocity.z);
        return motion.lengthSqr() >= 0.0064D && motion.dot(away) >= 0.055D;
    }

    /** Snaps a retreat vector to the dominant horizontal grid axis. */
    public static Direction cardinalEscapeDirection(Vec3 escapeDirection) {
        double x = escapeDirection.x;
        double z = escapeDirection.z;
        if (Math.abs(x) > Math.abs(z)) {
            return x < 0.0D ? Direction.WEST : Direction.EAST;
        }
        return z < 0.0D ? Direction.NORTH : Direction.SOUTH;
    }

    /**
     * Deterministic one-block-thick wall grid. Width extends perpendicular to
     * the escape direction and rows are returned bottom-to-top.
     */
    public static List<BlockPos> duatWallCells(
            BlockPos center,
            Direction escapeDirection,
            int width,
            int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        int half = safeWidth / 2;
        int sideX = -escapeDirection.getStepZ();
        int sideZ = escapeDirection.getStepX();
        List<BlockPos> cells = new ArrayList<>(safeWidth * safeHeight);
        for (int row = 0; row < safeHeight; row++) {
            for (int column = 0; column < safeWidth; column++) {
                int lateral = column - half;
                cells.add(center.offset(sideX * lateral, row, sideZ * lateral));
            }
        }
        return List.copyOf(cells);
    }

    public static boolean wallIsAhead(
            Vec3 playerPosition,
            Vec3 wallCenter,
            Vec3 escapeDirection) {
        return wallCenter.subtract(playerPosition).dot(horizontalUnit(escapeDirection)) > 0.0D;
    }

    /**
     * Alternating predicted flank for Sekhmet's pursuit. The locked point is
     * intentionally recomputed once per beat and remains fixed afterwards.
     */
    public static Vec3 sekhmetFlankTarget(
            Vec3 bossPosition,
            Vec3 playerPosition,
            Vec3 playerVelocity,
            int beat,
            double flankDistance) {
        Vec3 predicted = playerPosition.add(
                Math.clamp(playerVelocity.x * 5.0D, -2.2D, 2.2D),
                0.0D,
                Math.clamp(playerVelocity.z * 5.0D, -2.2D, 2.2D));
        Vec3 approach = horizontalUnit(predicted.subtract(bossPosition));
        Vec3 side = new Vec3(-approach.z, 0.0D, approach.x);
        double sign = (beat & 1) == 0 ? 1.0D : -1.0D;
        return predicted
                .add(approach.scale(0.7D))
                .add(side.scale(Math.max(0.0D, flankDistance) * sign));
    }

    /** Caps a flank route to the distance the current dash can physically cover. */
    public static Vec3 clampHuntAnchorToTravel(
            Vec3 start,
            Vec3 desiredAnchor,
            double maximumTravel) {
        Vec3 horizontal = new Vec3(
                desiredAnchor.x - start.x,
                0.0D,
                desiredAnchor.z - start.z);
        double distance = horizontal.length();
        double travel = Math.max(0.0D, maximumTravel);
        if (distance <= travel || distance <= EPSILON) {
            return desiredAnchor;
        }
        Vec3 clamped = horizontal.scale(travel / distance);
        return new Vec3(start.x + clamped.x, start.y, start.z + clamped.z);
    }

    /** The dash route and the khopesh aim are separate: the cut crosses the locked target. */
    public static Vec3 sekhmetStrikeDirection(
            Vec3 strikeAnchor,
            Vec3 lockedTarget,
            Vec3 fallbackDirection) {
        Vec3 towardTarget = new Vec3(
                lockedTarget.x - strikeAnchor.x,
                0.0D,
                lockedTarget.z - strikeAnchor.z);
        return towardTarget.lengthSqr() <= EPSILON
                ? horizontalUnit(fallbackDirection)
                : towardTarget.normalize();
    }

    public static boolean huntHitAllowed(long previousHitTick, long gameTick, int graceTicks) {
        return previousHitTick < 0L || gameTick - previousHitTick >= Math.max(0, graceTicks);
    }

    /**
     * Uses the real escape vector when available, then clamps the interception
     * point inside the authored horizontal arena footprint.
     */
    public static Vec3 escapeInterceptAnchor(
            Vec3 bossPosition,
            Vec3 playerPosition,
            Vec3 playerVelocity,
            double distance,
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            double edgeMargin) {
        Vec3 away = horizontalUnit(playerPosition.subtract(bossPosition));
        Vec3 motion = new Vec3(playerVelocity.x, 0.0D, playerVelocity.z);
        Vec3 escape = motion.lengthSqr() >= 0.0064D && motion.dot(away) > 0.0D
                ? motion.normalize()
                : away;
        Vec3 raw = playerPosition.add(escape.scale(Math.max(1.0D, distance)));
        double margin = Math.max(0.0D, edgeMargin);
        double minX = Math.min(minimumX + margin, maximumX - margin);
        double maxX = Math.max(minimumX + margin, maximumX - margin);
        double minZ = Math.min(minimumZ + margin, maximumZ - margin);
        double maxZ = Math.max(minimumZ + margin, maximumZ - margin);
        return new Vec3(
                Math.clamp(raw.x, minX, maxX),
                playerPosition.y,
                Math.clamp(raw.z, minZ, maxZ));
    }

    /**
     * A closed, rounded-rectangle patrol along the canonical arena border.
     * Indexes are phase-separated, so reinforcements never form a charge line.
     */
    public static Vec3 chariotPerimeterPoint(
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            int chariotIndex,
            int chariotCount,
            int seed,
            double progress) {
        double minX = Math.min(minimumX, maximumX);
        double maxX = Math.max(minimumX, maximumX);
        double minZ = Math.min(minimumZ, maximumZ);
        double maxZ = Math.max(minimumZ, maximumZ);
        double width = Math.max(2.0D, maxX - minX);
        double depth = Math.max(2.0D, maxZ - minZ);
        double radius = Math.min(CHARIOT_CORNER_RADIUS, Math.min(width, depth) * 0.25D);
        double straightX = Math.max(0.0D, width - radius * 2.0D);
        double straightZ = Math.max(0.0D, depth - radius * 2.0D);
        double arc = Math.PI * radius * 0.5D;
        double perimeter = 2.0D * (straightX + straightZ) + 4.0D * arc;
        int count = Math.max(1, chariotCount);
        int index = Math.clamp(chariotIndex, 0, count - 1);
        double seedPhase = Math.floorMod(seed, 997) / 997.0D;
        double phase = progress + index / (double) count + seedPhase;
        if ((seed & 1) != 0) {
            phase = -phase;
        }
        double distance = phase - Math.floor(phase);
        distance *= perimeter;
        if (distance < straightX) {
            return new Vec3(minX + radius + distance, 0.0D, minZ);
        }
        distance -= straightX;
        if (distance < arc) {
            double angle = -Math.PI * 0.5D + distance / radius;
            return new Vec3(
                    maxX - radius + Math.cos(angle) * radius,
                    0.0D,
                    minZ + radius + Math.sin(angle) * radius);
        }
        distance -= arc;
        if (distance < straightZ) {
            return new Vec3(maxX, 0.0D, minZ + radius + distance);
        }
        distance -= straightZ;
        if (distance < arc) {
            double angle = distance / radius;
            return new Vec3(
                    maxX - radius + Math.cos(angle) * radius,
                    0.0D,
                    maxZ - radius + Math.sin(angle) * radius);
        }
        distance -= arc;
        if (distance < straightX) {
            return new Vec3(maxX - radius - distance, 0.0D, maxZ);
        }
        distance -= straightX;
        if (distance < arc) {
            double angle = Math.PI * 0.5D + distance / radius;
            return new Vec3(
                    minX + radius + Math.cos(angle) * radius,
                    0.0D,
                    maxZ - radius + Math.sin(angle) * radius);
        }
        distance -= arc;
        if (distance < straightZ) {
            return new Vec3(minX, 0.0D, maxZ - radius - distance);
        }
        distance -= straightZ;
        double angle = Math.PI + distance / radius;
        return new Vec3(
                minX + radius + Math.cos(angle) * radius,
                0.0D,
                minZ + radius + Math.sin(angle) * radius);
    }

    public static Vec3 chariotPerimeterDirection(
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            int chariotIndex,
            int chariotCount,
            int seed,
            double progress) {
        Vec3 current = chariotPerimeterPoint(
                minimumX, maximumX, minimumZ, maximumZ,
                chariotIndex, chariotCount, seed, progress);
        Vec3 next = chariotPerimeterPoint(
                minimumX, maximumX, minimumZ, maximumZ,
                chariotIndex, chariotCount, seed, progress + 0.001D);
        return horizontalUnit(next.subtract(current));
    }

    /** Shallow, side-curved shot used by both the chariot archer and hit test. */
    public static Vec3 chariotArrowPoint(
            Vec3 start,
            Vec3 end,
            double progress,
            double curveSign) {
        double t = Math.clamp(progress, 0.0D, 1.0D);
        if (t <= 0.0D) {
            return start;
        }
        if (t >= 1.0D) {
            return end;
        }
        Vec3 direction = horizontalUnit(end.subtract(start));
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        double arch = Math.sin(Math.PI * t);
        return start.lerp(end, t)
                .add(side.scale((curveSign < 0.0D ? -1.0D : 1.0D) * 0.45D * arch))
                .add(0.0D, 1.15D * arch, 0.0D);
    }

    public static boolean wallContains(
            Vec3 center,
            Vec3 forward,
            Vec3 targetCenter,
            double halfSpan,
            double halfThick,
            double halfHeight) {
        Vec3 flatForward = horizontalUnit(forward);
        Vec3 side = new Vec3(-flatForward.z, 0.0D, flatForward.x);
        Vec3 offset = targetCenter.subtract(center);
        return Math.abs(offset.y) <= Math.max(0.0D, halfHeight) + EPSILON
                && Math.abs(offset.dot(side)) <= Math.max(0.0D, halfSpan) + EPSILON
                && Math.abs(offset.dot(flatForward)) <= Math.max(0.0D, halfThick) + EPSILON;
    }

    /** Half-extent of an AABB along a horizontal unit axis (for wall depth tests). */
    public static double horizontalExtentAlong(AABB box, Vec3 flatForward) {
        Vec3 axis = horizontalUnit(flatForward);
        return 0.5D * (box.getXsize() * Math.abs(axis.x) + box.getZsize() * Math.abs(axis.z));
    }

    /**
     * True when the player volume overlaps the wall slab or their horizontal
     * motion this tick would cross its mid-plane inside the authored span.
     */
    public static boolean wallBlocksPlayer(
            Vec3 center,
            Vec3 forward,
            AABB playerBox,
            Vec3 horizontalMotion,
            double halfSpan,
            double halfThick,
            double halfHeight) {
        Vec3 flatForward = horizontalUnit(forward);
        Vec3 side = new Vec3(-flatForward.z, 0.0D, flatForward.x);
        double playerDepth = horizontalExtentAlong(playerBox, flatForward);
        double thick = Math.max(0.0D, halfThick) + playerDepth;
        Vec3 mid = new Vec3(
                (playerBox.minX + playerBox.maxX) * 0.5D,
                (playerBox.minY + playerBox.maxY) * 0.5D,
                (playerBox.minZ + playerBox.maxZ) * 0.5D);
        if (wallContains(center, flatForward, mid, halfSpan, thick, halfHeight)) {
            return true;
        }
        // Corner samples catch partial overlaps the mid-point can miss.
        Vec3[] samples = {
            new Vec3(playerBox.minX, mid.y, playerBox.minZ),
            new Vec3(playerBox.maxX, mid.y, playerBox.maxZ),
            new Vec3(playerBox.minX, mid.y, playerBox.maxZ),
            new Vec3(playerBox.maxX, mid.y, playerBox.minZ),
            new Vec3(mid.x, playerBox.minY + 0.05D, mid.z),
            new Vec3(mid.x, playerBox.maxY - 0.05D, mid.z)
        };
        for (Vec3 sample : samples) {
            if (wallContains(center, flatForward, sample, halfSpan, thick, halfHeight)) {
                return true;
            }
        }
        Vec3 next = mid.add(horizontalMotion.x, 0.0D, horizontalMotion.z);
        double alongNow = mid.subtract(center).dot(flatForward);
        double alongNext = next.subtract(center).dot(flatForward);
        boolean crossesPlane = alongNow * alongNext <= 0.0D
                || Math.abs(alongNow) <= thick + EPSILON
                || Math.abs(alongNext) <= thick + EPSILON;
        if (!crossesPlane) {
            return false;
        }
        double denom = alongNow - alongNext;
        double t = Math.abs(denom) <= EPSILON ? 0.0D : alongNow / denom;
        t = Math.clamp(t, 0.0D, 1.0D);
        Vec3 hit = mid.add(horizontalMotion.x * t, 0.0D, horizontalMotion.z * t);
        Vec3 offset = hit.subtract(center);
        return Math.abs(offset.y) <= Math.max(0.0D, halfHeight) + EPSILON
                && Math.abs(offset.dot(side)) <= Math.max(0.0D, halfSpan) + EPSILON;
    }

    /**
     * Continuous previous-to-current collision used by virtual architecture.
     * Unlike {@link #wallBlocksPlayer}, this checks the movement that already
     * happened during the player tick, so a sprint cannot tunnel through.
     */
    public static boolean wallBlocksTraversal(
            Vec3 center,
            Vec3 forward,
            Vec3 previousCenter,
            Vec3 currentCenter,
            double halfSpan,
            double halfThick,
            double halfHeight) {
        Vec3 normal = horizontalUnit(forward);
        Vec3 side = new Vec3(-normal.z, 0.0D, normal.x);
        Vec3 previousOffset = previousCenter.subtract(center);
        Vec3 currentOffset = currentCenter.subtract(center);
        if (Math.abs(currentOffset.y) > Math.max(0.0D, halfHeight) + EPSILON) {
            return false;
        }
        double previousDepth = previousOffset.dot(normal);
        double currentDepth = currentOffset.dot(normal);
        double depth = Math.max(0.0D, halfThick);
        if (Math.abs(currentDepth) <= depth + EPSILON
                && Math.abs(currentOffset.dot(side)) <= Math.max(0.0D, halfSpan) + EPSILON) {
            return true;
        }
        if ((previousDepth > depth && currentDepth > depth)
                || (previousDepth < -depth && currentDepth < -depth)) {
            return false;
        }
        double denominator = previousDepth - currentDepth;
        double crossing = Math.abs(denominator) <= EPSILON
                ? 0.0D
                : previousDepth / denominator;
        crossing = Math.clamp(crossing, 0.0D, 1.0D);
        Vec3 contact = previousCenter.lerp(currentCenter, crossing);
        Vec3 contactOffset = contact.subtract(center);
        return Math.abs(contactOffset.dot(side)) <= Math.max(0.0D, halfSpan) + EPSILON;
    }

    /** Returns a correction that always keeps the player on their previous side. */
    public static Vec3 wallTraversalCorrection(
            Vec3 center,
            Vec3 forward,
            Vec3 previousCenter,
            Vec3 currentCenter,
            double clearance) {
        Vec3 normal = horizontalUnit(forward);
        double previousDepth = previousCenter.subtract(center).dot(normal);
        double currentDepth = currentCenter.subtract(center).dot(normal);
        double sign;
        if (Math.abs(previousDepth) > EPSILON) {
            sign = previousDepth >= 0.0D ? 1.0D : -1.0D;
        } else {
            sign = currentDepth >= 0.0D ? -1.0D : 1.0D;
        }
        return normal.scale(sign * Math.max(0.0D, clearance) - currentDepth);
    }

    /**
     * Returns the horizontal displacement that places the player just outside
     * the wall on the nearest free side.
     */
    public static Vec3 wallPushDelta(
            Vec3 center,
            Vec3 forward,
            AABB playerBox,
            Vec3 preferredOutward,
            double halfThick) {
        Vec3 flatForward = horizontalUnit(forward);
        Vec3 mid = new Vec3(
                (playerBox.minX + playerBox.maxX) * 0.5D,
                (playerBox.minY + playerBox.maxY) * 0.5D,
                (playerBox.minZ + playerBox.maxZ) * 0.5D);
        double along = mid.subtract(center).dot(flatForward);
        double playerDepth = horizontalExtentAlong(playerBox, flatForward);
        double surface = Math.max(0.0D, halfThick) + playerDepth + WALL_PUSH_MARGIN;
        double sign;
        if (Math.abs(along) > 0.02D) {
            sign = along >= 0.0D ? 1.0D : -1.0D;
        } else if (preferredOutward.lengthSqr() > 1.0E-8D) {
            sign = preferredOutward.dot(flatForward) >= 0.0D ? 1.0D : -1.0D;
        } else {
            sign = 1.0D;
        }
        double delta = sign * surface - along;
        return flatForward.scale(delta);
    }

    /** Removes only the velocity component that drives the player into the wall. */
    public static Vec3 wallClampedMotion(Vec3 motion, Vec3 forward, Vec3 pushDelta) {
        Vec3 flatForward = horizontalUnit(forward);
        double outward = pushDelta.dot(flatForward);
        if (Math.abs(outward) <= EPSILON) {
            return motion;
        }
        double sign = outward >= 0.0D ? 1.0D : -1.0D;
        double intoWall = -sign * motion.dot(flatForward);
        if (intoWall <= 0.0D) {
            return motion;
        }
        return motion.add(flatForward.scale(sign * intoWall));
    }

    /**
     * Overhead storm spawn: arrows hang above the player in a spaced line,
     * then dive toward the locked body point.
     */
    public static Vec3 horusOverheadStart(
            Vec3 anchor,
            Vec3 forward,
            int shot,
            int shotCount,
            double height,
            double spacing) {
        Vec3 flatForward = horizontalUnit(forward);
        Vec3 side = new Vec3(-flatForward.z, 0.0D, flatForward.x);
        double centered = shot - (Math.max(1, shotCount) - 1) * 0.5D;
        return anchor
                .add(0.0D, Math.max(2.5D, height), 0.0D)
                .add(side.scale(centered * Math.max(0.55D, spacing)));
    }

    public static Vec3 horusVolleyStart(
            Vec3 origin,
            Vec3 forward,
            int shot,
            int shotCount) {
        return horusOverheadStart(origin, forward, shot, shotCount, 7.2D, 0.95D);
    }

    /** Shared lateral spread used by server hit tests and client arrow rendering. */
    public static Vec3 horusVolleyTarget(
            Vec3 anchor,
            Vec3 forward,
            int shot,
            int shotCount,
            double spacing) {
        // Dive target stays on the predicted body; lateral is only in the overhead starts.
        return anchor;
    }

    /** Curved falcon-feather trajectory; endpoints remain exact for synchronization. */
    public static Vec3 horusVolleyPoint(
            Vec3 start,
            Vec3 end,
            double progress,
            double curveSign,
            double curveAmount,
            double lift) {
        double t = Math.clamp(progress, 0.0D, 1.0D);
        if (t <= 0.0D) {
            return start;
        }
        if (t >= 1.0D) {
            return end;
        }
        Vec3 flat = horizontalUnit(end.subtract(start));
        Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);
        double arch = Math.sin(Math.PI * t);
        return start.lerp(end, t)
                .add(side.scale((curveSign < 0.0D ? -1.0D : 1.0D) * curveAmount * arch))
                .add(0.0D, Math.max(0.0D, lift) * arch, 0.0D);
    }

    /** Coherent 3x3 / 5x3 cartouche mosaic shared by hitboxes and rendering. */
    public static Vec3 mineCenter(
            Vec3 anchor,
            Vec3 forward,
            int mineIndex,
            int mineCount,
            int seed,
            double fieldRadius) {
        int count = Math.max(1, mineCount);
        int columns = count > 9 ? 5 : 3;
        int rows = Math.max(1, (int) Math.ceil(count / (double) columns));
        int index = Math.clamp(mineIndex, 0, count - 1);
        int column = index % columns;
        int row = index / columns;
        Vec3 flatForward = horizontalUnit(forward);
        Vec3 side = new Vec3(-flatForward.z, 0.0D, flatForward.x);
        double maximumExtent = Math.max(1.0D, fieldRadius);
        double lateralSpacing = count > 9 ? maximumExtent / 2.0D : maximumExtent / 1.5D;
        double forwardSpacing = maximumExtent / 1.5D;
        double lateral = (column - (columns - 1) * 0.5D) * lateralSpacing;
        double along = (row - (rows - 1) * 0.5D) * forwardSpacing;
        if ((seed & 1) != 0) {
            lateral = -lateral;
        }
        return anchor
                .add(flatForward.scale(along))
                .add(side.scale(lateral));
    }

    /** Every sufficiently large field contains blast, tether, weakness and launch seals. */
    public static int mineType(int mineIndex, int seed) {
        return Math.floorMod(mineIndex + seed, 4);
    }

    /** Distributes adjacent spatial seals across five musical impact beats. */
    public static int mineWave(int mineIndex, int seed) {
        return mineWave(mineIndex, seed, 9);
    }

    public static int mineWave(int mineIndex, int seed, int mineCount) {
        int columns = mineCount > 9 ? 5 : 3;
        int column = mineIndex % columns;
        int row = mineIndex / columns;
        return Math.floorMod(row + column + seed, 5);
    }

    public static boolean orientedPanelContains(
            Vec3 center,
            Vec3 forward,
            Vec3 targetCenter,
            double halfWidth,
            double halfLength,
            double verticalAllowance) {
        Vec3 flatForward = horizontalUnit(forward);
        Vec3 side = new Vec3(-flatForward.z, 0.0D, flatForward.x);
        Vec3 offset = targetCenter.subtract(center);
        return Math.abs(offset.y) <= Math.max(0.0D, verticalAllowance) + EPSILON
                && Math.abs(offset.dot(side)) <= Math.max(0.0D, halfWidth) + EPSILON
                && Math.abs(offset.dot(flatForward)) <= Math.max(0.0D, halfLength) + EPSILON;
    }

    public static boolean capsuleContains(
            Vec3 start,
            Vec3 end,
            Vec3 targetCenter,
            double radius) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        double t = lengthSqr <= EPSILON
                ? 0.0D
                : Math.clamp(targetCenter.subtract(start).dot(segment) / lengthSqr, 0.0D, 1.0D);
        Vec3 nearest = start.add(segment.scale(t));
        double safeRadius = Math.max(0.0D, radius);
        return nearest.distanceToSqr(targetCenter) <= safeRadius * safeRadius + EPSILON;
    }

    /** Finite horizontal corridor shared by the warning lane and solar beam. */
    public static boolean judgmentBeamContains(
            Vec3 origin,
            Vec3 forward,
            double length,
            double halfWidth,
            Vec3 targetCenter,
            double verticalAllowance) {
        Vec3 flatForward = new Vec3(forward.x, 0.0D, forward.z);
        if (flatForward.lengthSqr() <= EPSILON) {
            flatForward = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            flatForward = flatForward.normalize();
        }
        Vec3 offset = targetCenter.subtract(origin);
        if (Math.abs(offset.y) > Math.max(0.0D, verticalAllowance)) {
            return false;
        }
        double along = offset.x * flatForward.x + offset.z * flatForward.z;
        if (along < -EPSILON || along > Math.max(0.0D, length) + EPSILON) {
            return false;
        }
        Vec3 side = new Vec3(-flatForward.z, 0.0D, flatForward.x);
        double lateral = Math.abs(offset.x * side.x + offset.z * side.z);
        return lateral <= Math.max(0.0D, halfWidth) + EPSILON;
    }

    /**
     * Restricts Judgment damage to the thin solar front that is visibly
     * crossing the arena. A player behind a front they already dodged cannot
     * be struck later by an invisible full-corridor hitbox.
     */
    public static boolean judgmentWavefrontContains(
            Vec3 origin,
            Vec3 forward,
            double corridorLength,
            double halfWidth,
            Vec3 targetCenter,
            double verticalAllowance,
            double previousDistance,
            double currentDistance,
            double targetRadius) {
        if (!judgmentBeamContains(
                origin,
                forward,
                corridorLength,
                halfWidth,
                targetCenter,
                verticalAllowance)) {
            return false;
        }
        Vec3 flatForward = horizontalUnit(forward);
        double along = targetCenter.subtract(origin).dot(flatForward);
        double minimum = Math.min(previousDistance, currentDistance);
        double maximum = Math.max(previousDistance, currentDistance);
        double radius = Math.max(0.0D, targetRadius);
        return along + radius >= minimum - EPSILON
                && along - radius <= maximum + EPSILON;
    }

    /** Prevents terrain-following telegraphs from bridging cliffs as floating quads. */
    public static boolean judgmentSurfaceContinuous(
            Vec3 first,
            Vec3 second,
            double maximumStep) {
        return Double.isFinite(first.y)
                && Double.isFinite(second.y)
                && Math.abs(first.y - second.y) <= Math.max(0.0D, maximumStep) + EPSILON;
    }

    public static double contractingRadius(
            double outerRadius,
            double innerRadius,
            double progress) {
        double clamped = Math.clamp(progress, 0.0D, 1.0D);
        double eased = clamped * clamped * (3.0D - 2.0D * clamped);
        return outerRadius + (innerRadius - outerRadius) * eased;
    }

    /**
     * Collision test for one contracting Duat wall. The angular doorway is
     * intentionally part of the geometry instead of being communicated by color.
     */
    public static boolean duatRingContains(
            Vec3 center,
            Vec3 referenceForward,
            double gateOffsetDegrees,
            double gateHalfAngleDegrees,
            double radius,
            double bandHalfWidth,
            Vec3 targetCenter,
            double verticalAllowance) {
        Vec3 offset = targetCenter.subtract(center);
        if (Math.abs(offset.y) > Math.max(0.0D, verticalAllowance)) {
            return false;
        }
        double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        if (Math.abs(horizontal - Math.max(0.0D, radius))
                > Math.max(0.0D, bandHalfWidth) + EPSILON) {
            return false;
        }
        Vec3 forward = horizontalUnit(referenceForward);
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        double targetAngle = Math.toDegrees(Math.atan2(
                offset.x * side.x + offset.z * side.z,
                offset.x * forward.x + offset.z * forward.z));
        double fromGate = Math.abs(wrapDegrees(targetAngle - gateOffsetDegrees));
        return fromGate > Math.max(0.0D, gateHalfAngleDegrees) + 0.001D;
    }

    public static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped >= 180.0D) {
            wrapped -= 360.0D;
        }
        if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return wrapped;
    }

    private static Vec3 horizontalUnit(Vec3 direction) {
        Vec3 flat = new Vec3(direction.x, 0.0D, direction.z);
        return flat.lengthSqr() <= EPSILON
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : flat.normalize();
    }

    /** Circular arena split used by Ma'at: sign selects the condemned half. */
    public static boolean maatCondemnedHalfContains(
            Vec3 center,
            Vec3 dividerForward,
            double unsafeSign,
            double radius,
            Vec3 targetCenter,
            double verticalAllowance) {
        Vec3 offset = targetCenter.subtract(center);
        if (Math.abs(offset.y) > Math.max(0.0D, verticalAllowance)) {
            return false;
        }
        double horizontalSqr = offset.x * offset.x + offset.z * offset.z;
        if (horizontalSqr > Math.max(0.0D, radius) * Math.max(0.0D, radius) + EPSILON) {
            return false;
        }
        Vec3 flatForward = new Vec3(dividerForward.x, 0.0D, dividerForward.z);
        if (flatForward.lengthSqr() <= EPSILON) {
            flatForward = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            flatForward = flatForward.normalize();
        }
        Vec3 side = new Vec3(-flatForward.z, 0.0D, flatForward.x);
        double signedSide = offset.x * side.x + offset.z * side.z;
        double sign = unsafeSign < 0.0D ? -1.0D : 1.0D;
        return signedSide * sign >= -EPSILON;
    }
}
