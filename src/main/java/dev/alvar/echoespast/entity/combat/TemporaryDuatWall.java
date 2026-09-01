package dev.alvar.echoespast.entity.combat;

import dev.alvar.echoespast.entity.UnknownEntity;
import dev.alvar.echoespast.server.UnknownFightManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One real, temporary Duat wall. Vanilla block collision is authoritative;
 * this class only owns safe placement and exact restoration.
 */
public final class TemporaryDuatWall {
    private static final int MUTATION_FLAGS = Block.UPDATE_CLIENTS
            | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS
            | Block.UPDATE_SKIP_ON_PLACE;
    private static final int[] FORWARD_SEARCH = {5, 6, 4, 7};
    private static final int[] LATERAL_SEARCH = {0, -1, 1, -2, 2};

    private final BlockPos center;
    private final Direction escapeDirection;
    private final int width;
    private final int height;
    private final long buildStartTick;
    private final long expireTick;
    private final Map<BlockPos, BlockState> originals;
    private int placedRows;
    private boolean restored;

    private TemporaryDuatWall(
            BlockPos center,
            Direction escapeDirection,
            int width,
            int height,
            long buildStartTick,
            long expireTick,
            Map<BlockPos, BlockState> originals) {
        this.center = center.immutable();
        this.escapeDirection = escapeDirection;
        this.width = width;
        this.height = height;
        this.buildStartTick = buildStartTick;
        this.expireTick = expireTick;
        this.originals = originals;
    }

    public static TemporaryDuatWall plan(
            ServerLevel level,
            UnknownEntity boss,
            ServerPlayer target,
            Direction escapeDirection,
            int width,
            int height,
            long buildStartTick,
            long expireTick) {
        UnknownFightManager.ArenaBounds arena = UnknownFightManager.arenaBounds(level);
        Direction side = escapeDirection.getClockWise();
        BlockPos playerFeet = BlockPos.containing(
                target.getX(), target.getBoundingBox().minY + 0.01D, target.getZ());
        for (int distance : FORWARD_SEARCH) {
            for (int lateral : LATERAL_SEARCH) {
                int x = playerFeet.getX()
                        + escapeDirection.getStepX() * distance
                        + side.getStepX() * lateral;
                int z = playerFeet.getZ()
                        + escapeDirection.getStepZ() * distance
                        + side.getStepZ() * lateral;
                for (int yOffset = 2; yOffset >= -4; yOffset--) {
                    BlockPos candidate = new BlockPos(x, playerFeet.getY() + yOffset, z);
                    Map<BlockPos, BlockState> originals = validate(
                            level, arena, candidate, escapeDirection, width, height);
                    if (originals != null
                            && !intersects(boss, candidate, escapeDirection, width, height)
                            && !intersects(target, candidate, escapeDirection, width, height)) {
                        return new TemporaryDuatWall(
                                candidate,
                                escapeDirection,
                                width,
                                height,
                                buildStartTick,
                                expireTick,
                                originals);
                    }
                }
            }
        }
        return null;
    }

    /** Fixed-position constructor used by deterministic GameTests and scripted encounters. */
    public static TemporaryDuatWall atFixedPosition(
            ServerLevel level,
            BlockPos center,
            Direction escapeDirection,
            int width,
            int height,
            long buildStartTick,
            long expireTick) {
        Map<BlockPos, BlockState> originals = validate(
                level, null, center, escapeDirection, width, height);
        return originals == null
                ? null
                : new TemporaryDuatWall(
                        center,
                        escapeDirection,
                        width,
                        height,
                        buildStartTick,
                        expireTick,
                        originals);
    }

    private static Map<BlockPos, BlockState> validate(
            ServerLevel level,
            UnknownFightManager.ArenaBounds arena,
            BlockPos center,
            Direction escapeDirection,
            int width,
            int height) {
        List<BlockPos> cells = UnknownEgyptianCombatMath.duatWallCells(
                center, escapeDirection, width, height);
        int minimumX = arena == null ? Integer.MIN_VALUE : arena.origin().getX() + 1;
        int maximumX = arena == null
                ? Integer.MAX_VALUE
                : arena.origin().getX() + arena.size().getX() - 2;
        int minimumY = arena == null ? level.getMinY() : arena.origin().getY();
        int maximumY = arena == null
                ? level.getMaxY() - 1
                : arena.origin().getY() + arena.size().getY() - 1;
        int minimumZ = arena == null ? Integer.MIN_VALUE : arena.origin().getZ() + 1;
        int maximumZ = arena == null
                ? Integer.MAX_VALUE
                : arena.origin().getZ() + arena.size().getZ() - 2;
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>(cells.size());
        for (BlockPos cell : cells) {
            if (cell.getX() < minimumX || cell.getX() > maximumX
                    || cell.getY() < minimumY || cell.getY() > maximumY
                    || cell.getZ() < minimumZ || cell.getZ() > maximumZ
                    || !level.getBlockState(cell).isAir()
                    || level.getBlockEntity(cell) != null) {
                return null;
            }
            originals.put(cell.immutable(), level.getBlockState(cell));
        }
        for (BlockPos cell : cells) {
            if (cell.getY() != center.getY()) {
                continue;
            }
            BlockPos support = cell.below();
            if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
                return null;
            }
        }
        return originals;
    }

    public boolean tick(ServerLevel level, UnknownEntity boss, ServerPlayer target) {
        long now = level.getGameTime();
        if (restored) {
            return false;
        }
        if (now >= expireTick) {
            restore(level);
            return false;
        }
        int desiredRows = now < buildStartTick
                ? 0
                : Math.min(height, (int) (now - buildStartTick) + 1);
        while (placedRows < desiredRows) {
            if (!placeRow(level, boss, target, placedRows)) {
                restore(level);
                return false;
            }
            placedRows++;
        }
        return true;
    }

    private boolean placeRow(
            ServerLevel level,
            UnknownEntity boss,
            ServerPlayer target,
            int row) {
        List<BlockPos> rowCells = cellsForRow(row);
        AABB rowBounds = bounds(rowCells);
        if (!rescueFromRow(level, target, rowBounds)
                || !rescueFromRow(level, boss, rowBounds)) {
            return false;
        }
        for (BlockPos cell : rowCells) {
            BlockState expected = originals.get(cell);
            if (expected == null
                    || !level.getBlockState(cell).equals(expected)
                    || level.getBlockEntity(cell) != null) {
                return false;
            }
        }
        for (BlockPos cell : rowCells) {
            level.setBlock(cell, Blocks.CHISELED_SANDSTONE.defaultBlockState(), MUTATION_FLAGS);
        }
        return true;
    }

    private boolean rescueFromRow(ServerLevel level, LivingEntity entity, AABB rowBounds) {
        if (!entity.isAlive() || !entity.getBoundingBox().intersects(rowBounds)) {
            return true;
        }
        Vec3 normal = new Vec3(
                escapeDirection.getStepX(), 0.0D, escapeDirection.getStepZ());
        Vec3 wallCenter = Vec3.atCenterOf(center);
        Vec3 entityCenter = entity.getBoundingBox().getCenter();
        double occupiedSide = entityCenter.subtract(wallCenter).dot(normal) >= 0.0D
                ? 1.0D
                : -1.0D;
        Vec3 lateral = new Vec3(-normal.z, 0.0D, normal.x);
        for (int distance = 1; distance <= 4; distance++) {
            for (int lateralStep : LATERAL_SEARCH) {
                Vec3 candidate = entity.position()
                        .add(normal.scale(occupiedSide * distance))
                        .add(lateral.scale(lateralStep));
                if (canStandAt(level, entity, candidate)) {
                    entity.stopRiding();
                    if (entity instanceof ServerPlayer player) {
                        player.teleportTo(candidate.x, candidate.y, candidate.z);
                    } else {
                        entity.setPos(candidate.x, candidate.y, candidate.z);
                    }
                    entity.setDeltaMovement(Vec3.ZERO);
                    entity.fallDistance = 0.0F;
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canStandAt(ServerLevel level, LivingEntity entity, Vec3 candidate) {
        Vec3 displacement = candidate.subtract(entity.position());
        AABB destination = entity.getBoundingBox().move(displacement);
        if (!level.noCollision(entity, destination)) {
            return false;
        }
        BlockPos support = BlockPos.containing(
                candidate.x, destination.minY - 0.08D, candidate.z);
        return level.getBlockState(support).isFaceSturdy(level, support, Direction.UP);
    }

    public void restore(ServerLevel level) {
        if (restored) {
            return;
        }
        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(originals.entrySet());
        entries.sort(Comparator.comparingInt(
                (Map.Entry<BlockPos, BlockState> entry) -> entry.getKey().getY()).reversed());
        for (Map.Entry<BlockPos, BlockState> entry : entries) {
            if (level.getBlockState(entry.getKey()).is(Blocks.CHISELED_SANDSTONE)) {
                level.setBlock(entry.getKey(), entry.getValue(), MUTATION_FLAGS);
            }
        }
        restored = true;
        placedRows = 0;
    }

    public BlockPos center() {
        return center;
    }

    public Direction escapeDirection() {
        return escapeDirection;
    }

    public long buildStartTick() {
        return buildStartTick;
    }

    public long expireTick() {
        return expireTick;
    }

    private List<BlockPos> cellsForRow(int row) {
        List<BlockPos> cells = UnknownEgyptianCombatMath.duatWallCells(
                center, escapeDirection, width, height);
        int from = Math.clamp(row, 0, height - 1) * width;
        return cells.subList(from, from + width);
    }

    private static boolean intersects(
            LivingEntity entity,
            BlockPos center,
            Direction escapeDirection,
            int width,
            int height) {
        return entity.getBoundingBox().intersects(bounds(
                UnknownEgyptianCombatMath.duatWallCells(
                        center, escapeDirection, width, height)));
    }

    private static AABB bounds(List<BlockPos> cells) {
        int minX = cells.stream().mapToInt(BlockPos::getX).min().orElse(0);
        int minY = cells.stream().mapToInt(BlockPos::getY).min().orElse(0);
        int minZ = cells.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        int maxX = cells.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int maxY = cells.stream().mapToInt(BlockPos::getY).max().orElse(0);
        int maxZ = cells.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }
}
