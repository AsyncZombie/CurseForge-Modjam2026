package dev.alvar.echoespast.server;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * Code-authored Greek village stand-in (matches tools/GenerateGreekBossBlueprints).
 * Replace with authored {@code boss_greek_*.bp} when ready.
 */
public final class GreekArenaLayout {
    public static final int PODIUM_HALF = 18;
    public static final int VILLAGE_HALF = PODIUM_HALF;
    public static final int CLEAR_HEIGHT = 14;
    public static final int TEMPLE_COLUMN_TOP = 7;

    private GreekArenaLayout() {
    }

    public static List<BlockPos> pastColumnBases(BlockPos center, int floorY) {
        List<BlockPos> bases = new ArrayList<>();
        int[] xs = {5, 8, 11, 14, 15};
        int[] zs = {-5, -2, 2, 5};
        for (int xi = 0; xi < xs.length; xi++) {
            for (int zi = 0; zi < zs.length; zi++) {
                if (!(xi == 0 || xi == xs.length - 1 || zi == 0 || zi == zs.length - 1)) {
                    continue;
                }
                bases.add(new BlockPos(
                        center.getX() + xs[xi],
                        floorY + 2,
                        center.getZ() + zs[zi]));
            }
        }
        return bases;
    }

    public static void collectPast(BlockPos center, int floorY, Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        build(center, floorY, false, out);
    }

    public static void collectRuins(BlockPos center, int floorY, Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        build(center, floorY, true, out);
    }

    public static void collectVolumeAir(BlockPos center, int floorY, Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -VILLAGE_HALF; dx <= VILLAGE_HALF; dx++) {
            for (int dz = -VILLAGE_HALF; dz <= VILLAGE_HALF; dz++) {
                for (int y = -1; y <= CLEAR_HEIGHT; y++) {
                    put(out, center, floorY, dx, y, dz, air);
                }
            }
        }
    }

    private static void build(
            BlockPos center,
            int floorY,
            boolean ruins,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        placeTerrain(center, floorY, ruins, out);
        placeProcessional(center, floorY, ruins, out);
        placeWestGate(center, floorY, ruins, out);
        placeAgora(center, floorY, ruins, out);
        placeTemple(center, floorY, ruins, out);
        placeHouse(center, floorY, -15, -14, 5, 6, Direction.SOUTH, ruins, out);
        placeHouse(center, floorY, -1, -16, 5, 5, Direction.SOUTH, ruins, out);
        placeHouse(center, floorY, -16, 8, 6, 5, Direction.NORTH, ruins, out);
        placeHouse(center, floorY, -2, 11, 6, 5, Direction.NORTH, ruins, out);
        placeHouse(center, floorY, 10, 10, 5, 5, Direction.WEST, ruins, out);
        placeMarket(center, floorY, ruins, out);
        placeWell(center, floorY, -4, 0, ruins, out);
        placeOlives(center, floorY, ruins, out);
        placeProps(center, floorY, ruins, out);
        if (ruins) {
            placeDebris(center, floorY, out);
        }
        placeLights(center, floorY, ruins, out);
    }

    private static void placeTerrain(
            BlockPos center,
            int floorY,
            boolean ruins,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        for (int x = -VILLAGE_HALF; x <= VILLAGE_HALF; x++) {
            for (int z = -VILLAGE_HALF; z <= VILLAGE_HALF; z++) {
                if (ruins && hole(x, z, 13)) {
                    continue;
                }
                if (inTemplePad(x, z)) {
                    continue;
                }
                BlockState surface;
                BlockState fill;
                if (inAgora(x, z)) {
                    surface = ruins
                            ? mix(x, z,
                                    Blocks.CRACKED_STONE_BRICKS,
                                    Blocks.MOSSY_STONE_BRICKS,
                                    Blocks.ANDESITE,
                                    Blocks.COBBLESTONE)
                            : (((x + z) & 1) == 0
                                    ? Blocks.POLISHED_DIORITE.defaultBlockState()
                                    : Blocks.CALCITE.defaultBlockState());
                    fill = Blocks.STONE.defaultBlockState();
                } else if (onRoad(x, z)) {
                    surface = ruins
                            ? mix(x, z, Blocks.STONE, Blocks.ANDESITE, Blocks.COBBLESTONE, Blocks.GRAVEL)
                            : Blocks.SMOOTH_STONE.defaultBlockState();
                    fill = Blocks.STONE.defaultBlockState();
                } else {
                    surface = ruins
                            ? mix(x, z, Blocks.COARSE_DIRT, Blocks.DIRT, Blocks.ROOTED_DIRT, Blocks.GRAVEL)
                            : (((x * 17 + z * 11) & 7) == 0
                                    ? Blocks.GRASS_BLOCK.defaultBlockState()
                                    : Blocks.DIRT_PATH.defaultBlockState());
                    fill = Blocks.DIRT.defaultBlockState();
                }
                put(out, center, floorY, x, 0, z, surface);
                put(out, center, floorY, x, -1, z, fill);
            }
        }
    }

    private static void placeProcessional(
            BlockPos center,
            int floorY,
            boolean ruins,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        for (int x = -VILLAGE_HALF; x <= 3; x++) {
            if (ruins && hole(x, 4, 5)) {
                continue;
            }
            BlockState curb = ruins
                    ? Blocks.COBBLESTONE.defaultBlockState()
                    : Blocks.STONE_BRICKS.defaultBlockState();
            put(out, center, floorY, x, 0, -4, curb);
            put(out, center, floorY, x, 0, 4, curb);
        }
    }

    private static void placeWestGate(
            BlockPos center,
            int floorY,
            boolean ruins,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        BlockState step = stairs(
                ruins ? Blocks.STONE_BRICK_STAIRS : Blocks.POLISHED_DIORITE_STAIRS,
                Direction.EAST);
        BlockState slab = bottomSlab(ruins ? Blocks.STONE_SLAB : Blocks.POLISHED_DIORITE_SLAB);
        for (int z = -2; z <= 2; z++) {
            put(out, center, floorY, -VILLAGE_HALF, 0, z, step);
            put(out, center, floorY, -VILLAGE_HALF - 1, -1, z, slab);
        }
        column(center, floorY, -16, -4, ruins ? 3 : TEMPLE_COLUMN_TOP, 1, ruins, out);
        column(center, floorY, -16, 4, ruins ? 2 : TEMPLE_COLUMN_TOP, 1, ruins, out);
        if (!ruins) {
            column(center, floorY, -12, -4, TEMPLE_COLUMN_TOP - 1, 1, false, out);
            column(center, floorY, -12, 4, TEMPLE_COLUMN_TOP - 1, 1, false, out);
            for (int z = -4; z <= 4; z++) {
                put(out, center, floorY, -16, TEMPLE_COLUMN_TOP + 1, z, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            }
            for (int z = -2; z <= 2; z++) {
                put(out, center, floorY, -16, TEMPLE_COLUMN_TOP + 2, z, bottomSlab(Blocks.SMOOTH_QUARTZ_SLAB));
            }
        }
    }

    private static void placeAgora(
            BlockPos center,
            int floorY,
            boolean ruins,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        BlockState bench = bottomSlab(ruins ? Blocks.ANDESITE_SLAB : Blocks.POLISHED_DIORITE_SLAB);
        for (int x = -7; x <= 2; x++) {
            if (ruins && ((x + 7) % 3) == 0) {
                continue;
            }
            put(out, center, floorY, x, 1, -6, bench);
            put(out, center, floorY, x, 1, 6, bench);
        }
        if (!ruins) {
            for (int x = -7; x <= 2; x += 2) {
                put(out, center, floorY, x, 1, -7, pillarY());
                put(out, center, floorY, x, 2, -7, pillarY());
                put(out, center, floorY, x, 3, -7, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            }
            for (int x = -6; x <= 1; x++) {
                put(out, center, floorY, x, 3, -7, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            }
            put(out, center, floorY, 0, 1, 0, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            put(out, center, floorY, 0, 2, 0, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            put(out, center, floorY, 0, 3, 0, Blocks.TORCH.defaultBlockState());
        } else {
            put(out, center, floorY, 0, 1, 0, Blocks.ANDESITE.defaultBlockState());
            put(out, center, floorY, -5, 1, -7, pillarY());
        }
    }

    private static void placeTemple(
            BlockPos center,
            int floorY,
            boolean ruins,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        for (int x = 5; x <= 15; x++) {
            for (int z = -5; z <= 5; z++) {
                if (ruins && hole(x, z, 8)) {
                    continue;
                }
                put(out, center, floorY, x, 0, z, Blocks.QUARTZ_BLOCK.defaultBlockState());
                put(out, center, floorY, x, 1, z, ruins
                        ? mix(x, z,
                                Blocks.CRACKED_STONE_BRICKS,
                                Blocks.MOSSY_STONE_BRICKS,
                                Blocks.STONE_BRICKS,
                                Blocks.CALCITE)
                        : (((x + z) & 1) == 0
                                ? Blocks.SMOOTH_QUARTZ.defaultBlockState()
                                : Blocks.CALCITE.defaultBlockState()));
            }
        }
        BlockState approach = stairs(
                ruins ? Blocks.STONE_BRICK_STAIRS : Blocks.SMOOTH_QUARTZ_STAIRS,
                Direction.EAST);
        for (int z = -2; z <= 2; z++) {
            if (ruins && Math.abs(z) == 2) {
                continue;
            }
            put(out, center, floorY, 4, 1, z, approach);
            put(out, center, floorY, 3, 0, z, bottomSlab(ruins ? Blocks.STONE_SLAB : Blocks.SMOOTH_QUARTZ_SLAB));
        }
        int[] xs = {5, 8, 11, 14, 15};
        int[] zs = {-5, -2, 2, 5};
        for (int xi = 0; xi < xs.length; xi++) {
            for (int zi = 0; zi < zs.length; zi++) {
                if (!(xi == 0 || xi == xs.length - 1 || zi == 0 || zi == zs.length - 1)) {
                    continue;
                }
                int height = ruins ? ruinHeight(xi, zi) : TEMPLE_COLUMN_TOP;
                if (height > 0) {
                    column(center, floorY, xs[xi], zs[zi], height, 2, ruins, out);
                }
            }
        }
        if (!ruins) {
            for (int x = 5; x <= 15; x += 2) {
                put(out, center, floorY, x, TEMPLE_COLUMN_TOP + 1, -5, Blocks.SMOOTH_QUARTZ.defaultBlockState());
                put(out, center, floorY, x, TEMPLE_COLUMN_TOP + 1, 5, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            }
            for (int z = -5; z <= 5; z += 2) {
                put(out, center, floorY, 5, TEMPLE_COLUMN_TOP + 1, z, Blocks.SMOOTH_QUARTZ.defaultBlockState());
                put(out, center, floorY, 15, TEMPLE_COLUMN_TOP + 1, z, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            }
            placeCella(center, floorY, false, out);
            placeTempleRoof(center, floorY, out);
            put(out, center, floorY, 13, 2, 0, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            put(out, center, floorY, 13, 3, 0, pillarY());
            put(out, center, floorY, 13, 4, 0, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            put(out, center, floorY, 13, 5, 0, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        } else {
            placeCella(center, floorY, true, out);
            column(center, floorY, 15, -5, 5, 2, true, out);
        }
    }

    private static void placeCella(
            BlockPos center,
            int floorY,
            boolean ruins,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        BlockState wall = ruins
                ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                : Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState trim = ruins
                ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                : Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
        int maxY = ruins ? 4 : 6;
        for (int z = -2; z <= 2; z++) {
            for (int y = 2; y <= maxY; y++) {
                if (ruins && y > 3 && (z & 1) == 0) {
                    continue;
                }
                put(out, center, floorY, 14, y, z, y == maxY ? trim : wall);
            }
        }
        for (int x = 9; x <= 13; x++) {
            for (int y = 2; y <= maxY; y++) {
                if (ruins && ((x + y) % 3) == 0) {
                    continue;
                }
                put(out, center, floorY, x, y, -2, y == maxY ? trim : wall);
                put(out, center, floorY, x, y, 2, y == maxY ? trim : wall);
            }
        }
        for (int z = -2; z <= 2; z++) {
            for (int y = 2; y <= maxY; y++) {
                if (Math.abs(z) <= 1 && y <= 4) {
                    continue;
                }
                if (ruins && y > 3 && z == 0) {
                    continue;
                }
                put(out, center, floorY, 9, y, z, y == maxY ? trim : wall);
            }
        }
        if (!ruins) {
            for (int x = 10; x <= 13; x++) {
                for (int z = -1; z <= 1; z++) {
                    put(out, center, floorY, x, 7, z, bottomSlab(Blocks.SMOOTH_QUARTZ_SLAB));
                }
            }
        }
    }

    private static void placeTempleRoof(
            BlockPos center,
            int floorY,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        for (int z = -5; z <= 5; z++) {
            put(out, center, floorY, 5, TEMPLE_COLUMN_TOP + 2, z, stairs(Blocks.BRICK_STAIRS, Direction.EAST));
            put(out, center, floorY, 6, TEMPLE_COLUMN_TOP + 2, z, Blocks.ORANGE_TERRACOTTA.defaultBlockState());
            put(out, center, floorY, 7, TEMPLE_COLUMN_TOP + 3, z, stairs(Blocks.BRICK_STAIRS, Direction.EAST));
            put(out, center, floorY, 8, TEMPLE_COLUMN_TOP + 3, z, Blocks.ORANGE_TERRACOTTA.defaultBlockState());
            put(out, center, floorY, 9, TEMPLE_COLUMN_TOP + 4, z, Blocks.ORANGE_TERRACOTTA.defaultBlockState());
            put(out, center, floorY, 10, TEMPLE_COLUMN_TOP + 4, z, Blocks.TERRACOTTA.defaultBlockState());
            put(out, center, floorY, 11, TEMPLE_COLUMN_TOP + 5, z, Blocks.ORANGE_TERRACOTTA.defaultBlockState());
            put(out, center, floorY, 12, TEMPLE_COLUMN_TOP + 5, z, Blocks.TERRACOTTA.defaultBlockState());
            put(out, center, floorY, 13, TEMPLE_COLUMN_TOP + 4, z, Blocks.ORANGE_TERRACOTTA.defaultBlockState());
            put(out, center, floorY, 14, TEMPLE_COLUMN_TOP + 3, z, Blocks.ORANGE_TERRACOTTA.defaultBlockState());
            put(out, center, floorY, 15, TEMPLE_COLUMN_TOP + 2, z, stairs(Blocks.BRICK_STAIRS, Direction.WEST));
        }
        put(out, center, floorY, 12, TEMPLE_COLUMN_TOP + 6, 0, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
    }

    private static void placeHouse(
            BlockPos center,
            int floorY,
            int ox,
            int oz,
            int sx,
            int sz,
            Direction door,
            boolean ruins,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        BlockState wall = ruins
                ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                : Blocks.STONE_BRICKS.defaultBlockState();
        BlockState trim = ruins
                ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                : Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        BlockState floor = ruins
                ? Blocks.COBBLESTONE.defaultBlockState()
                : Blocks.STRIPPED_OAK_WOOD.defaultBlockState();
        int wallTop = ruins ? 3 : 4;
        for (int lx = 0; lx < sx; lx++) {
            for (int lz = 0; lz < sz; lz++) {
                int x = ox + lx;
                int z = oz + lz;
                boolean edge = lx == 0 || lz == 0 || lx == sx - 1 || lz == sz - 1;
                if (!ruins || !hole(x, z, 5)) {
                    put(out, center, floorY, x, 0, z, Blocks.COBBLESTONE.defaultBlockState());
                }
                if (!edge) {
                    put(out, center, floorY, x, 1, z, floor);
                    continue;
                }
                for (int y = 1; y <= wallTop; y++) {
                    if (isDoor(lx, lz, sx, sz, door) && y <= 2) {
                        continue;
                    }
                    if (!ruins && isWindow(lx, lz, sx, sz, door) && y == 2) {
                        continue;
                    }
                    if (ruins && y == wallTop && ((lx + lz) & 1) == 0) {
                        continue;
                    }
                    put(out, center, floorY, x, y, z, y == wallTop ? trim : wall);
                }
            }
        }
        if (!ruins) {
            boolean longX = sx >= sz;
            for (int lx = 0; lx < sx; lx++) {
                for (int lz = 0; lz < sz; lz++) {
                    int x = ox + lx;
                    int z = oz + lz;
                    if (longX) {
                        int mid = sz / 2;
                        if (lz < mid) {
                            put(out, center, floorY, x, 5, z, stairs(Blocks.BRICK_STAIRS, Direction.SOUTH));
                        } else if (lz > mid) {
                            put(out, center, floorY, x, 5, z, stairs(Blocks.BRICK_STAIRS, Direction.NORTH));
                        } else {
                            put(out, center, floorY, x, 5, z, Blocks.ORANGE_TERRACOTTA.defaultBlockState());
                            put(out, center, floorY, x, 6, z, Blocks.TERRACOTTA.defaultBlockState());
                        }
                    } else {
                        int mid = sx / 2;
                        if (lx < mid) {
                            put(out, center, floorY, x, 5, z, stairs(Blocks.BRICK_STAIRS, Direction.EAST));
                        } else if (lx > mid) {
                            put(out, center, floorY, x, 5, z, stairs(Blocks.BRICK_STAIRS, Direction.WEST));
                        } else {
                            put(out, center, floorY, x, 5, z, Blocks.ORANGE_TERRACOTTA.defaultBlockState());
                            put(out, center, floorY, x, 6, z, Blocks.TERRACOTTA.defaultBlockState());
                        }
                    }
                }
            }
            put(out, center, floorY, ox + sx / 2, 2, oz + sz / 2, bottomSlab(Blocks.SMOOTH_STONE_SLAB));
        } else {
            put(out, center, floorY, ox + 1, 1, oz + 1, Blocks.ORANGE_TERRACOTTA.defaultBlockState());
            put(out, center, floorY, ox + sx - 2, 1, oz + sz - 2, Blocks.TERRACOTTA.defaultBlockState());
        }
    }

    private static void placeMarket(
            BlockPos center,
            int floorY,
            boolean ruins,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        int[][] posts = {{2, 7}, {4, 7}, {2, 9}, {4, 9}};
        for (int[] p : posts) {
            put(out, center, floorY, p[0], 1, p[1],
                    Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
            if (!ruins) {
                put(out, center, floorY, p[0], 2, p[1],
                        Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
            }
        }
        if (!ruins) {
            for (int x = 2; x <= 4; x++) {
                for (int z = 7; z <= 9; z++) {
                    put(out, center, floorY, x, 3, z, bottomSlab(Blocks.OAK_SLAB));
                }
            }
            put(out, center, floorY, 3, 1, 8, Blocks.BARREL.defaultBlockState());
            put(out, center, floorY, 3, 1, 7, Blocks.DECORATED_POT.defaultBlockState());
        } else {
            put(out, center, floorY, 3, 1, 8, Blocks.OAK_PLANKS.defaultBlockState());
            put(out, center, floorY, 4, 1, 8, Blocks.DECORATED_POT.defaultBlockState());
        }
    }

    private static void placeWell(
            BlockPos center,
            int floorY,
            int x,
            int z,
            boolean ruins,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        BlockState rim = ruins
                ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                : Blocks.STONE_BRICKS.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    put(out, center, floorY, x, 0, z, ruins
                            ? Blocks.COBBLESTONE.defaultBlockState()
                            : Blocks.WATER_CAULDRON.defaultBlockState()
                                    .setValue(LayeredCauldronBlock.LEVEL, 3));
                    continue;
                }
                put(out, center, floorY, x + dx, 1, z + dz, rim);
            }
        }
        if (!ruins) {
            put(out, center, floorY, x, 1, z - 1, Blocks.OAK_FENCE.defaultBlockState());
            put(out, center, floorY, x, 2, z - 1, Blocks.OAK_FENCE.defaultBlockState());
            put(out, center, floorY, x, 3, z - 1, Blocks.OAK_FENCE.defaultBlockState());
            put(out, center, floorY, x, 3, z, bottomSlab(Blocks.OAK_SLAB));
        }
    }

    private static void placeOlives(
            BlockPos center,
            int floorY,
            boolean ruins,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        int[][] trees = {{-17, -16}, {-8, -17}, {5, -17}, {16, -10}, {-17, 14}, {-6, 16}, {8, 16}, {16, 5}};
        for (int[] t : trees) {
            olive(center, floorY, t[0], t[1], ruins, out);
        }
    }

    private static void olive(
            BlockPos center,
            int floorY,
            int x,
            int z,
            boolean ruins,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, true)
                .setValue(LeavesBlock.DISTANCE, 1);
        if (ruins && ((x + z) & 3) == 0) {
            put(out, center, floorY, x, 1, z, pillarY(Blocks.OAK_LOG));
            put(out, center, floorY, x + 1, 1, z, leaves);
            return;
        }
        put(out, center, floorY, x, 1, z, pillarY(Blocks.STRIPPED_OAK_LOG));
        put(out, center, floorY, x, 2, z, pillarY(Blocks.STRIPPED_OAK_LOG));
        put(out, center, floorY, x, 3, z, pillarY(Blocks.OAK_LOG));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 3; dy <= (ruins ? 4 : 5); dy++) {
                    if (dx == 0 && dz == 0 && dy < 5) {
                        continue;
                    }
                    if (Math.abs(dx) + Math.abs(dz) + (dy - 3) > 3) {
                        continue;
                    }
                    put(out, center, floorY, x + dx, dy, z + dz, leaves);
                }
            }
        }
    }

    private static void placeProps(
            BlockPos center,
            int floorY,
            boolean ruins,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        if (!ruins) {
            put(out, center, floorY, -6, 1, -3, Blocks.DECORATED_POT.defaultBlockState());
            put(out, center, floorY, -6, 1, 3, Blocks.DECORATED_POT.defaultBlockState());
            put(out, center, floorY, 2, 1, -5, Blocks.DECORATED_POT.defaultBlockState());
            put(out, center, floorY, 2, 1, -3, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            put(out, center, floorY, 2, 2, -3, Blocks.STONE_BRICK_WALL.defaultBlockState());
            put(out, center, floorY, 2, 1, 3, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            put(out, center, floorY, 2, 2, 3, Blocks.STONE_BRICK_WALL.defaultBlockState());
        } else {
            put(out, center, floorY, -6, 1, -3, Blocks.DECORATED_POT.defaultBlockState());
            put(out, center, floorY, 2, 1, -3, Blocks.COBBLESTONE.defaultBlockState());
            put(out, center, floorY, 1, 1, 4, Blocks.MOSS_CARPET.defaultBlockState());
            put(out, center, floorY, -3, 1, -2, Blocks.MOSS_CARPET.defaultBlockState());
        }
    }

    private static void placeDebris(
            BlockPos center,
            int floorY,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        fallen(center, floorY, 1, -2, 4, true, out);
        fallen(center, floorY, 7, 2, 3, false, out);
        fallen(center, floorY, -7, 1, 3, true, out);
        fallen(center, floorY, 11, -6, 3, false, out);
        put(out, center, floorY, 8, 2, 0, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        put(out, center, floorY, 9, 2, -4, Blocks.ORANGE_TERRACOTTA.defaultBlockState());
    }

    private static void placeLights(
            BlockPos center,
            int floorY,
            boolean ruins,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        if (ruins) {
            put(out, center, floorY, -15, 2, -4, Blocks.TORCH.defaultBlockState());
            return;
        }
        put(out, center, floorY, -16, TEMPLE_COLUMN_TOP + 1, -3, Blocks.TORCH.defaultBlockState());
        put(out, center, floorY, -16, TEMPLE_COLUMN_TOP + 1, 3, Blocks.TORCH.defaultBlockState());
        put(out, center, floorY, 5, TEMPLE_COLUMN_TOP + 1, -4, Blocks.TORCH.defaultBlockState());
        put(out, center, floorY, 5, TEMPLE_COLUMN_TOP + 1, 4, Blocks.TORCH.defaultBlockState());
        put(out, center, floorY, 15, TEMPLE_COLUMN_TOP + 1, 0, Blocks.TORCH.defaultBlockState());
        put(out, center, floorY, -14, 5, -12, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        put(out, center, floorY, -14, 5, 10, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        put(out, center, floorY, 1, 5, -14, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
    }

    private static void column(
            BlockPos center,
            int floorY,
            int x,
            int z,
            int shaftTop,
            int baseY,
            boolean ruins,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        BlockState base = ruins
                ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                : Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
        BlockState shaft = ruins
                ? Blocks.ANDESITE_WALL.defaultBlockState()
                : pillarY();
        put(out, center, floorY, x, baseY, z, base);
        for (int y = baseY + 1; y <= Math.max(baseY, shaftTop); y++) {
            put(out, center, floorY, x, y, z, shaft);
        }
        if (!ruins && shaftTop >= 4) {
            put(out, center, floorY, x, shaftTop + 1, z, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        }
    }

    private static void fallen(
            BlockPos center,
            int floorY,
            int startX,
            int startZ,
            int length,
            boolean alongX,
            Consumer<ArenaReconstructionWave.PlannedBlock> out) {
        BlockState shaft = Blocks.QUARTZ_PILLAR.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
        for (int i = 0; i < length; i++) {
            int x = alongX ? startX + i : startX;
            int z = alongX ? startZ : startZ + i;
            put(out, center, floorY, x, 1, z, shaft);
        }
    }

    private static void put(
            Consumer<ArenaReconstructionWave.PlannedBlock> out,
            BlockPos center,
            int floorY,
            int dx,
            int dy,
            int dz,
            BlockState state) {
        out.accept(new ArenaReconstructionWave.PlannedBlock(
                new BlockPos(center.getX() + dx, floorY + dy, center.getZ() + dz),
                state));
    }

    private static BlockState bottomSlab(net.minecraft.world.level.block.Block slab) {
        return slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState stairs(net.minecraft.world.level.block.Block block, Direction facing) {
        return block.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, Half.BOTTOM)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
    }

    private static BlockState pillarY() {
        return pillarY(Blocks.QUARTZ_PILLAR);
    }

    private static BlockState pillarY(net.minecraft.world.level.block.Block block) {
        return block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    }

    private static BlockState mix(int a, int b, net.minecraft.world.level.block.Block... blocks) {
        return blocks[Math.floorMod(a * 31 + b * 17, blocks.length)].defaultBlockState();
    }

    private static boolean hole(int x, int z, int mod) {
        return Math.floorMod(x * 31 + z * 17, mod) == 0;
    }

    private static boolean inAgora(int x, int z) {
        return x >= -8 && x <= 3 && z >= -6 && z <= 6;
    }

    private static boolean onRoad(int x, int z) {
        return x >= -VILLAGE_HALF && x <= 4 && z >= -3 && z <= 3;
    }

    private static boolean inTemplePad(int x, int z) {
        return x >= 3 && x <= 15 && z >= -5 && z <= 5;
    }

    private static int ruinHeight(int xi, int zi) {
        if (xi == 4 && zi == 0) {
            return 5;
        }
        if (xi == 0 && zi == 3) {
            return 3;
        }
        if (xi == 4 && zi == 3) {
            return 2;
        }
        if (xi == 0 && zi == 0) {
            return 4;
        }
        if (xi == 2 && (zi == 0 || zi == 3)) {
            return 1;
        }
        return 0;
    }

    private static boolean isDoor(int lx, int lz, int sx, int sz, Direction door) {
        int mx = sx / 2;
        int mz = sz / 2;
        return switch (door) {
            case NORTH -> lz == 0 && lx == mx;
            case SOUTH -> lz == sz - 1 && lx == mx;
            case WEST -> lx == 0 && lz == mz;
            case EAST -> lx == sx - 1 && lz == mz;
            default -> false;
        };
    }

    private static boolean isWindow(int lx, int lz, int sx, int sz, Direction door) {
        int mx = sx / 2;
        int mz = sz / 2;
        return switch (door) {
            case NORTH -> lz == sz - 1 && lx == mx;
            case SOUTH -> lz == 0 && lx == mx;
            case WEST -> lx == sx - 1 && lz == mz;
            case EAST -> lx == 0 && lz == mz;
            default -> false;
        };
    }
}
