package dev.alvar.echoespast.world;

import dev.alvar.echoespast.resonance.EchoSiteType;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Softens the world around an authored land pad so nearby hills do not cliff
 * or bury the ruin.
 *
 * <p>Only the collar outside the structure box is rewritten. Interior rooms
 * and the stamped pad stay as the template placed them. Each column lerps from
 * the pad height at the wall to the original surface a few blocks out, filling
 * or carving natural terrain and plants. Fill follows the column (sand, grass,
 * terracotta) so a plains coliseum does not grow a sand skirt.</p>
 */
public final class EchoSiteTerrainBlend {
    public static final int MARGIN = 6;
    private static final int SCAN_ABOVE_PAD = 24;
    private static final int SCAN_BELOW_PAD = 16;
    private static final Palette DESERT = new Palette(
            Blocks.SAND.defaultBlockState(),
            Blocks.SAND.defaultBlockState());
    private static final Palette RED_DESERT = new Palette(
            Blocks.RED_SAND.defaultBlockState(),
            Blocks.RED_SAND.defaultBlockState());
    private static final Palette GRASSLAND = new Palette(
            Blocks.GRASS_BLOCK.defaultBlockState(),
            Blocks.DIRT.defaultBlockState());
    private static final Palette PATH = new Palette(
            Blocks.COARSE_DIRT.defaultBlockState(),
            Blocks.DIRT.defaultBlockState());

    private EchoSiteTerrainBlend() {
    }

    public static int blendHeight(int padY, int surfaceY, int dist, int margin) {
        if (dist <= 0 || margin <= 0) {
            return padY;
        }
        if (dist >= margin) {
            return surfaceY;
        }
        return padY + Math.round((surfaceY - padY) * (dist / (float) margin));
    }

    public static int distanceOutside(int x, int z, BoundingBox footprint) {
        int dx = 0;
        if (x < footprint.minX()) {
            dx = footprint.minX() - x;
        } else if (x > footprint.maxX()) {
            dx = x - footprint.maxX();
        }
        int dz = 0;
        if (z < footprint.minZ()) {
            dz = footprint.minZ() - z;
        } else if (z > footprint.maxZ()) {
            dz = z - footprint.maxZ();
        }
        return Math.max(dx, dz);
    }

    public static void blend(
            WorldGenLevel level,
            int padY,
            BoundingBox footprint,
            BoundingBox writable) {
        blend(level, padY, footprint, writable, EchoSiteType.Family.DESERT);
    }

    public static void blend(
            WorldGenLevel level,
            int padY,
            BoundingBox footprint,
            BoundingBox writable,
            EchoSiteType.Family family) {
        BoundingBox scan = new BoundingBox(
                footprint.minX() - MARGIN,
                Math.min(footprint.minY(), padY - SCAN_BELOW_PAD),
                footprint.minZ() - MARGIN,
                footprint.maxX() + MARGIN,
                Math.max(footprint.maxY(), padY + SCAN_ABOVE_PAD),
                footprint.maxZ() + MARGIN);
        if (!scan.intersects(writable)) {
            return;
        }
        int minX = Math.max(scan.minX(), writable.minX());
        int minZ = Math.max(scan.minZ(), writable.minZ());
        int maxX = Math.min(scan.maxX(), writable.maxX());
        int maxZ = Math.min(scan.maxZ(), writable.maxZ());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int dist = distanceOutside(x, z, footprint);
                if (dist <= 0 || dist > MARGIN) {
                    continue;
                }
                reshapeColumn(level, cursor, x, z, padY, dist, scan, writable, family);
            }
        }
    }

    private static void reshapeColumn(
            WorldGenLevel level,
            BlockPos.MutableBlockPos cursor,
            int x,
            int z,
            int padY,
            int dist,
            BoundingBox scan,
            BoundingBox writable,
            EchoSiteType.Family family) {
        int minY = Math.max(scan.minY(), writable.minY());
        int maxY = Math.min(scan.maxY(), writable.maxY());
        int surfaceY = findSurface(level, cursor, x, z, minY, maxY);
        if (surfaceY < minY) {
            surfaceY = minY - 1;
        }
        if (touchesWater(level, cursor, x, z, surfaceY, minY)) {
            return;
        }
        int targetY = Math.clamp(blendHeight(padY, surfaceY, dist, MARGIN), minY, maxY);
        Palette palette = paletteAt(level, cursor, x, z, surfaceY, minY, family);
        if (targetY > surfaceY) {
            for (int y = surfaceY + 1; y <= targetY; y++) {
                cursor.set(x, y, z);
                if (!writable.isInside(cursor)) {
                    continue;
                }
                if (canReplace(level.getBlockState(cursor))) {
                    level.setBlock(cursor, palette.at(y, targetY), Block.UPDATE_CLIENTS);
                }
            }
            return;
        }
        for (int y = maxY; y > targetY; y--) {
            cursor.set(x, y, z);
            if (!writable.isInside(cursor)) {
                continue;
            }
            if (canCarve(level.getBlockState(cursor))) {
                level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        if (targetY >= surfaceY) {
            return;
        }
        cursor.set(x, targetY, z);
        if (writable.isInside(cursor) && canCarve(level.getBlockState(cursor))) {
            level.setBlock(cursor, palette.surface(), Block.UPDATE_CLIENTS);
        }
    }

    private static int findSurface(
            WorldGenLevel level,
            BlockPos.MutableBlockPos cursor,
            int x,
            int z,
            int minY,
            int maxY) {
        for (int y = maxY; y >= minY; y--) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (state.isAir() || isPlant(state) || state.is(Blocks.SNOW)) {
                continue;
            }
            return y;
        }
        return minY - 1;
    }

    private static boolean touchesWater(
            WorldGenLevel level,
            BlockPos.MutableBlockPos cursor,
            int x,
            int z,
            int surfaceY,
            int minY) {
        int from = Math.max(minY, surfaceY - 1);
        int to = surfaceY + 1;
        for (int y = from; y <= to; y++) {
            cursor.set(x, y, z);
            if (level.getBlockState(cursor).getFluidState().isEmpty()) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static Palette paletteAt(
            WorldGenLevel level,
            BlockPos.MutableBlockPos cursor,
            int x,
            int z,
            int surfaceY,
            int minY,
            EchoSiteType.Family family) {
        int from = Math.max(surfaceY, minY);
        for (int y = from; y >= minY; y--) {
            cursor.set(x, y, z);
            Palette matched = paletteMatching(level.getBlockState(cursor));
            if (matched != null) {
                return matched;
            }
        }
        return paletteForFamily(family);
    }

    private static Palette paletteMatching(BlockState state) {
        if (state.is(Blocks.RED_SAND) || state.is(Blocks.RED_SANDSTONE)) {
            return RED_DESERT;
        }
        if (state.is(Blocks.SAND)
                || state.is(Blocks.SUSPICIOUS_SAND)
                || state.is(BlockTags.SAND)
                || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.SMOOTH_SANDSTONE)
                || state.is(Blocks.CUT_SANDSTONE)
                || state.is(Blocks.CHISELED_SANDSTONE)) {
            return DESERT;
        }
        if (state.is(BlockTags.TERRACOTTA)) {
            return new Palette(state, state);
        }
        if (state.is(Blocks.DIRT_PATH) || state.is(Blocks.COARSE_DIRT)) {
            return PATH;
        }
        if (state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.FARMLAND)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(BlockTags.DIRT)) {
            return GRASSLAND;
        }
        return null;
    }

    private static Palette paletteForFamily(EchoSiteType.Family family) {
        return switch (family) {
            case DESERT -> DESERT;
            case LEGACY -> PATH;
            default -> GRASSLAND;
        };
    }

    private static boolean canReplace(BlockState state) {
        return state.isAir() || isPlant(state) || state.is(Blocks.SNOW);
    }

    private static boolean canCarve(BlockState state) {
        return state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.SUSPICIOUS_SAND)
                || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.SMOOTH_SANDSTONE)
                || state.is(Blocks.CUT_SANDSTONE)
                || state.is(Blocks.CHISELED_SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.FARMLAND)
                || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.TERRACOTTA)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.LOGS)
                || isPlant(state);
    }

    private static boolean isPlant(BlockState state) {
        return state.is(BlockTags.REPLACEABLE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SHORT_DRY_GRASS)
                || state.is(Blocks.TALL_DRY_GRASS)
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.VINE)
                || state.is(Blocks.MOSS_CARPET)
                || state.is(BlockTags.SMALL_FLOWERS)
                || state.is(BlockTags.SAPLINGS);
    }

    private record Palette(BlockState surface, BlockState subsurface) {
        BlockState at(int y, int targetY) {
            return y >= targetY ? surface : subsurface;
        }
    }
}
