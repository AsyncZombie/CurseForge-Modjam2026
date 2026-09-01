package dev.alvar.echoespast.entity.combat;

import dev.alvar.echoespast.entity.UnknownEntity;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Collision, footing and environmental safety shared by direct boss movement. */
public final class UnknownBossMovementSafety {
    private static final double DASH_SAMPLE_DISTANCE = 0.2D;
    private static final double LIVE_STEP_DISTANCE = 0.28D;
    private static final double HAZARD_MARGIN = 0.16D;
    private static final double CONTACT_CACTUS_MARGIN = 0.24D;
    private static final int MAX_CACTUS_COLUMN_HEIGHT = 16;
    private static final double EPSILON = 1.0E-5D;
    private static final double[] STANDING_HEIGHT_OFFSETS = {
        0.0D, 0.5D, 1.0D, -0.5D, -1.0D
    };

    public static boolean isDangerousBlock(
            BlockGetter level,
            BlockPos pos,
            BlockState state) {
        return state.is(Blocks.CACTUS)
                || state.is(BlockTags.FIRE)
                || CampfireBlock.isLitCampfire(state)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.getFluidState().is(FluidTags.LAVA);
    }

    /** True only when the boss is already intersecting danger, not merely beside it. */
    public static boolean isEntityInDanger(ServerLevel level, UnknownEntity boss) {
        return containsDanger(level, boss.getBoundingBox().deflate(0.02D));
    }

    /**
     * Resolves the complete straight dash, including one-block climbs and the
     * final standing height. Empty means the telegraphed movement is impossible.
     */
    public static Optional<Vec3> resolveStraightDashAnchor(
            ServerLevel level,
            UnknownEntity boss,
            Vec3 start,
            Vec3 desiredAnchor) {
        Vec3 horizontal = new Vec3(
                desiredAnchor.x - start.x,
                0.0D,
                desiredAnchor.z - start.z);
        double distance = horizontal.length();
        if (distance <= EPSILON) {
            return safeStandingY(level, boss, start.x, start.z, start.y)
                    .map(y -> new Vec3(start.x, y, start.z));
        }
        int samples = Math.max(1, (int) Math.ceil(distance / DASH_SAMPLE_DISTANCE));
        double standingY = start.y;
        for (int sample = 1; sample <= samples; sample++) {
            double progress = sample / (double) samples;
            double x = start.x + horizontal.x * progress;
            double z = start.z + horizontal.z * progress;
            Optional<Double> nextY = safeStandingY(level, boss, x, z, standingY);
            if (nextY.isEmpty()) {
                return Optional.empty();
            }
            standingY = nextY.orElseThrow();
        }
        return Optional.of(new Vec3(desiredAnchor.x, standingY, desiredAnchor.z));
    }

    public static boolean isStraightDashSafe(
            ServerLevel level,
            UnknownEntity boss,
            Vec3 start,
            Vec3 desiredAnchor) {
        return resolveStraightDashAnchor(level, boss, start, desiredAnchor).isPresent();
    }

    /** Revalidates a live dash step in case the arena changed after direction lock. */
    public static Optional<Vec3> resolveDashStep(
            ServerLevel level,
            UnknownEntity boss,
            Vec3 horizontalStep) {
        Vec3 start = boss.position();
        Vec3 desired = start.add(horizontalStep.x, 0.0D, horizontalStep.z);
        return resolveStraightDashAnchor(level, boss, start, desired)
                .filter(anchor -> Math.abs(anchor.y - start.y) <= 1.0D + EPSILON)
                // Vanilla step resolution needs a horizontal request. Supplying
                // the planned rise here bypasses its full-block/slab candidates.
                .map(anchor -> new Vec3(horizontalStep.x, 0.0D, horizontalStep.z));
    }

    /**
     * Performs one validated direct-combat step and lets vanilla collision choose
     * the exact full-block or slab height. Cacti in the body's swept volume are
     * crushed first, top-down and without drops.
     */
    public static boolean moveDashStep(
            ServerLevel level,
            UnknownEntity boss,
            Vec3 horizontalStep) {
        Vec3 before = boss.position();
        Vec3 requestedMovement = new Vec3(horizontalStep.x, 0.0D, horizontalStep.z);
        Vec3 desiredAnchor = before.add(requestedMovement);
        // Validate the whole route without applying the one-block limit used by
        // each physical substep. A staircase can legitimately accumulate more
        // than one block of height over a single fast hunt impulse.
        if (resolveStraightDashAnchor(level, boss, before, desiredAnchor).isEmpty()) {
            return false;
        }
        Vec3 movement = requestedMovement;
        double distance = movement.horizontalDistance();
        if (distance <= EPSILON) {
            return true;
        }
        Vec3 direction = new Vec3(movement.x / distance, 0.0D, movement.z / distance);
        double remaining = distance;
        int maximumAttempts = (int) Math.ceil(distance / 0.04D) + 16;
        for (int attempt = 0; attempt < maximumAttempts && remaining > EPSILON; attempt++) {
            double requestedDistance = Math.min(LIVE_STEP_DISTANCE, remaining);
            Vec3 requestedStep = direction.scale(requestedDistance);
            Optional<Vec3> liveStep = resolveDashStep(level, boss, requestedStep);
            if (liveStep.isEmpty()) {
                return false;
            }
            Vec3 step = liveStep.orElseThrow();
            destroyCactiInBox(level, boss, boss.getBoundingBox()
                    .expandTowards(step)
                    .inflate(0.02D));
            Vec3 substepStart = boss.position();
            boss.move(MoverType.SELF, step);
            Vec3 actual = boss.position().subtract(substepStart);
            double forwardProgress = actual.x * direction.x + actual.z * direction.z;
            if (forwardProgress <= EPSILON) {
                return false;
            }
            // Entity.move() temporarily clears onGround after resolving an
            // upward half-step. Every point of this ground-bound dash has just
            // been support-validated, so preserve that contact for the next
            // stair candidate within the same combat tick.
            boss.setOnGround(true);
            // Stair collision may stop exactly at its inner half-block edge.
            // Consume that legitimate partial advance, then resolve the rest
            // from the new height instead of declaring the whole dash blocked.
            remaining -= Math.min(requestedDistance, forwardProgress);
        }
        double travelledSqr = horizontalDistanceSqr(before, boss.position());
        double requestedSqr = movement.horizontalDistanceSqr();
        return remaining <= EPSILON && travelledSqr >= requestedSqr * 0.25D;
    }

    /**
     * Performs a short ground-bound combat step without ever changing a block.
     * Unlike the Egyptian dash, hazards such as cacti are hard stops rather
     * than destructible obstacles.
     */
    public static boolean moveGroundStepNonDestructive(
            ServerLevel level,
            UnknownEntity boss,
            Vec3 horizontalStep) {
        Vec3 requested = new Vec3(horizontalStep.x, 0.0D, horizontalStep.z);
        if (requested.horizontalDistanceSqr() <= EPSILON * EPSILON) {
            return true;
        }
        Vec3 before = boss.position();
        Vec3 desired = before.add(requested);
        if (resolveStraightStepAnchor(level, boss, before, desired, false).isEmpty()) {
            return false;
        }
        boss.move(MoverType.SELF, requested);
        Vec3 actual = boss.position().subtract(before);
        double requestedLength = requested.horizontalDistance();
        double progress = actual.x * requested.x / requestedLength
                + actual.z * requested.z / requestedLength;
        if (progress <= EPSILON) {
            return false;
        }
        boss.setOnGround(true);
        return progress >= requestedLength * 0.75D;
    }

    /** Clears a cactus once normal navigation brings the boss into body contact. */
    public static boolean destroyContactCacti(ServerLevel level, UnknownEntity boss) {
        return destroyCactiInBox(
                level,
                boss,
                boss.getBoundingBox().inflate(CONTACT_CACTUS_MARGIN, 0.06D, CONTACT_CACTUS_MARGIN));
    }

    private static Optional<Double> safeStandingY(
            ServerLevel level,
            UnknownEntity boss,
            double x,
            double z,
            double previousY) {
        return safeStandingY(level, boss, x, z, previousY, true);
    }

    private static Optional<Double> safeStandingY(
            ServerLevel level,
            UnknownEntity boss,
            double x,
            double z,
            double previousY,
            boolean allowDestructibleCacti) {
        for (double offset : STANDING_HEIGHT_OFFSETS) {
            double candidateY = previousY + offset;
            AABB box = boss.getBoundingBox().move(
                    x - boss.getX(),
                    candidateY - boss.getY(),
                    z - boss.getZ());
            if (hasNoBlockingCollision(level, boss, box, allowDestructibleCacti)
                    && hasSafeSupport(level, box)
                    && !containsDanger(
                            level,
                            box.inflate(HAZARD_MARGIN, 0.08D, HAZARD_MARGIN),
                            allowDestructibleCacti)) {
                return Optional.of(candidateY);
            }
        }
        return Optional.empty();
    }

    private static Optional<Vec3> resolveStraightStepAnchor(
            ServerLevel level,
            UnknownEntity boss,
            Vec3 start,
            Vec3 desiredAnchor,
            boolean allowDestructibleCacti) {
        Vec3 horizontal = new Vec3(
                desiredAnchor.x - start.x,
                0.0D,
                desiredAnchor.z - start.z);
        double distance = horizontal.length();
        int samples = Math.max(1, (int) Math.ceil(distance / DASH_SAMPLE_DISTANCE));
        double standingY = start.y;
        for (int sample = 1; sample <= samples; sample++) {
            double progress = sample / (double) samples;
            double x = start.x + horizontal.x * progress;
            double z = start.z + horizontal.z * progress;
            Optional<Double> nextY = safeStandingY(
                    level,
                    boss,
                    x,
                    z,
                    standingY,
                    allowDestructibleCacti);
            if (nextY.isEmpty()) {
                return Optional.empty();
            }
            standingY = nextY.orElseThrow();
        }
        return Optional.of(new Vec3(desiredAnchor.x, standingY, desiredAnchor.z));
    }

    private static boolean hasSafeSupport(ServerLevel level, AABB box) {
        // A thin collision probe recognises the actual upper surface of slabs,
        // stairs and full blocks. isFaceSturdy(UP) incorrectly rejects slabs.
        AABB supportProbe = new AABB(
                box.minX + EPSILON,
                box.minY - 0.075D,
                box.minZ + EPSILON,
                box.maxX - EPSILON,
                box.minY + EPSILON,
                box.maxZ - EPSILON);
        return containsSafeBlockCollision(level, supportProbe);
    }

    private static boolean containsDanger(ServerLevel level, AABB box) {
        return containsDanger(level, box, false);
    }

    private static boolean containsDanger(
            ServerLevel level,
            AABB box,
            boolean ignoreDestructibleCacti) {
        int minX = (int) Math.floor(box.minX + EPSILON);
        int maxX = (int) Math.floor(box.maxX - EPSILON);
        int minY = (int) Math.floor(box.minY + EPSILON);
        int maxY = (int) Math.floor(box.maxY - EPSILON);
        int minZ = (int) Math.floor(box.minZ + EPSILON);
        int maxZ = (int) Math.floor(box.maxZ - EPSILON);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (isDangerousBlock(level, cursor, state)
                            && !(ignoreDestructibleCacti && state.is(Blocks.CACTUS))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasNoBlockingCollision(
            ServerLevel level,
            UnknownEntity boss,
            AABB box) {
        return hasNoBlockingCollision(level, boss, box, true);
    }

    private static boolean hasNoBlockingCollision(
            ServerLevel level,
            UnknownEntity boss,
            AABB box,
            boolean ignoreCacti) {
        if (!level.noEntityCollision(boss, box) || !level.noBorderCollision(boss, box)) {
            return false;
        }
        return !containsBlockCollision(level, boss, box, ignoreCacti, false);
    }

    private static boolean containsSafeBlockCollision(ServerLevel level, AABB box) {
        return containsBlockCollision(level, null, box, false, true);
    }

    private static boolean containsBlockCollision(
            ServerLevel level,
            UnknownEntity boss,
            AABB box,
            boolean ignoreCacti,
            boolean requireSafeBlock) {
        int minX = (int) Math.floor(box.minX + EPSILON);
        int maxX = (int) Math.floor(box.maxX - EPSILON);
        int minY = (int) Math.floor(box.minY + EPSILON);
        int maxY = (int) Math.floor(box.maxY - EPSILON);
        int minZ = (int) Math.floor(box.minZ + EPSILON);
        int maxZ = (int) Math.floor(box.maxZ - EPSILON);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        VoxelShape testShape = Shapes.create(box);
        CollisionContext context = boss == null
                ? CollisionContext.empty()
                : CollisionContext.of(boss);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if ((ignoreCacti && state.is(Blocks.CACTUS))
                            || (requireSafeBlock && isDangerousBlock(level, cursor, state))) {
                        continue;
                    }
                    VoxelShape collision = state.getCollisionShape(level, cursor, context);
                    if (!collision.isEmpty()
                            && Shapes.joinIsNotEmpty(
                                    collision.move(x, y, z),
                                    testShape,
                                    BooleanOp.AND)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean destroyCactiInBox(
            ServerLevel level,
            UnknownEntity boss,
            AABB contactBox) {
        Set<Long> contactedColumns = new HashSet<>();
        int minX = (int) Math.floor(contactBox.minX);
        int maxX = (int) Math.floor(contactBox.maxX);
        int minY = (int) Math.floor(contactBox.minY);
        int maxY = (int) Math.floor(contactBox.maxY);
        int minZ = (int) Math.floor(contactBox.minZ);
        int maxZ = (int) Math.floor(contactBox.maxZ);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).is(Blocks.CACTUS)) {
                        contactedColumns.add(BlockPos.asLong(x, 0, z));
                    }
                }
            }
        }
        boolean destroyed = false;
        for (long packedColumn : contactedColumns) {
            BlockPos packed = BlockPos.of(packedColumn);
            int baseY = minY;
            int searched = 0;
            while (searched++ < MAX_CACTUS_COLUMN_HEIGHT
                    && level.getBlockState(new BlockPos(packed.getX(), baseY - 1, packed.getZ()))
                            .is(Blocks.CACTUS)) {
                baseY--;
            }
            int topY = baseY;
            searched = 0;
            while (searched++ < MAX_CACTUS_COLUMN_HEIGHT
                    && level.getBlockState(new BlockPos(packed.getX(), topY + 1, packed.getZ()))
                            .is(Blocks.CACTUS)) {
                topY++;
            }
            for (int y = topY; y >= baseY; y--) {
                BlockPos cactusPos = new BlockPos(packed.getX(), y, packed.getZ());
                if (level.getBlockState(cactusPos).is(Blocks.CACTUS)) {
                    destroyed |= level.destroyBlock(cactusPos, false, boss);
                }
            }
        }
        return destroyed;
    }

    private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return x * x + z * z;
    }

    private UnknownBossMovementSafety() {
    }
}
