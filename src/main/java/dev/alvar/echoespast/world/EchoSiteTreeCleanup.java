package dev.alvar.echoespast.world;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Removes natural trees that the structure footprint bisected.
 *
 * <p>Surface structures write before decoration in their own chunk, but a
 * neighbour that already finished can leave logs and leaves hanging across the
 * authored box. Placing air inside that box then leaves a half tree on the
 * border. Flood-filling tree vegetation from the outside collar clears the
 * connected crown without touching authored logs, leaves or props that the
 * template just placed inside the footprint.</p>
 *
 * <p>Sites that also paint a technical biome stop later vegetation features from
 * regrowing trees into the pad; this cleanup still catches cross-chunk crowns
 * that already existed when the piece wrote.</p>
 */
public final class EchoSiteTreeCleanup {
    private static final int HORIZONTAL_MARGIN = 10;
    private static final int UPWARD_MARGIN = 24;

    private EchoSiteTreeCleanup() {
    }

    public static void clearIntersecting(
            WorldGenLevel level,
            BoundingBox footprint,
            BoundingBox writable) {
        BoundingBox scan = new BoundingBox(
                footprint.minX() - HORIZONTAL_MARGIN,
                footprint.minY(),
                footprint.minZ() - HORIZONTAL_MARGIN,
                footprint.maxX() + HORIZONTAL_MARGIN,
                footprint.maxY() + UPWARD_MARGIN,
                footprint.maxZ() + HORIZONTAL_MARGIN);
        if (!scan.intersects(writable)) {
            return;
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minX = Math.max(scan.minX(), writable.minX());
        int minY = Math.max(scan.minY(), writable.minY());
        int minZ = Math.max(scan.minZ(), writable.minZ());
        int maxX = Math.min(scan.maxX(), writable.maxX());
        int maxY = Math.min(scan.maxY(), writable.maxY());
        int maxZ = Math.min(scan.maxZ(), writable.maxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    // Never seed inside the authored box: spruce logs and other
                    // decorative wood the template placed would be destroyed.
                    if (footprint.isInside(cursor)) {
                        continue;
                    }
                    if (!isTreeVegetation(level.getBlockState(cursor))) {
                        continue;
                    }
                    if (touchesFootprint(cursor, footprint, 2)) {
                        queue.add(cursor.immutable());
                    }
                }
            }
        }

        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!writable.isInside(pos) || !scan.isInside(pos)) {
                continue;
            }
            if (footprint.isInside(pos)) {
                continue;
            }
            if (!visited.add(pos.asLong())) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!isTreeVegetation(state)) {
                continue;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        neighbour.set(
                                pos.getX() + dx,
                                pos.getY() + dy,
                                pos.getZ() + dz);
                        if (footprint.isInside(neighbour)) {
                            continue;
                        }
                        if (writable.isInside(neighbour)
                                && scan.isInside(neighbour)
                                && isTreeVegetation(
                                        level.getBlockState(neighbour))) {
                            queue.add(neighbour.immutable());
                        }
                    }
                }
            }
        }
    }

    private static boolean touchesFootprint(
            BlockPos pos,
            BoundingBox footprint,
            int margin) {
        return pos.getX() >= footprint.minX() - margin
                && pos.getX() <= footprint.maxX() + margin
                && pos.getY() >= footprint.minY() - margin
                && pos.getY() <= footprint.maxY() + margin
                && pos.getZ() >= footprint.minZ() - margin
                && pos.getZ() <= footprint.maxZ() + margin;
    }

    private static boolean isTreeVegetation(BlockState state) {
        return state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.VINE)
                || state.is(Blocks.PALE_HANGING_MOSS)
                || state.is(Blocks.MANGROVE_ROOTS)
                || state.is(Blocks.MANGROVE_PROPAGULE)
                || state.is(Blocks.MUSHROOM_STEM)
                || state.is(Blocks.BEE_NEST)
                || state.is(Blocks.BEEHIVE);
    }
}
