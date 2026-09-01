package dev.alvar.echoespast.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import dev.alvar.echoespast.resonance.EchoSiteType;

/**
 * Stable land footing for sites whose authored pad (coarse dirt / path) must
 * sit flush with the world surface across a wide memory box. Sites that stamp
 * their own pad still use {@link #hasGentleRelief} so a mountain face or a
 * lake cannot become a cut cube.
 */
public record EchoSiteLandFooting(boolean acceptable, int anchorY) {
    private static final int MAX_FOOTPRINT_SLOPE = 3;
    private static final int MIN_ABOVE_SEA = 2;
    /**
     * Dense 4-block grids on a 46×39 coliseum asked {@code /locate} for ~125
     * noise columns per candidate. Vanilla then checks biomes
     * <em>after</em> {@code findGenerationPoint}, so the watchdog killed the
     * server. A 3×3 pad plus the origin still sees every corner cliff.
     */
    private static final int MAX_AXIS_SAMPLES = 3;

    /**
     * Sites that stamp their own pad still must not cube-cut a mountain.
     * Dunes of about a dozen blocks can blend; a mesa wall cannot.
     */
    public static final int MAX_BLEND_SITE_RELIEF = 12;
    /**
     * {@link Heightmap.Types#WORLD_SURFACE_WG} counts water and dead bushes;
     * {@link Heightmap.Types#OCEAN_FLOOR_WG} does not. One extra block is a
     * bush; two or more is a lake, river or shoreline the pad would cut.
     */
    public static final int MAX_STANDING_WATER = 1;

    public static EchoSiteLandFooting reject() {
        return new EchoSiteLandFooting(false, 0);
    }

    /**
     * Offsets sampled for a site's memory box. Centre and the four corners
     * always come first so a ravine or cliff can reject without filling the
     * rest of the grid. Kept public so GameTests can pin the locate budget.
     */
    public static List<BlockPos> sampleOffsets(EchoSiteType site) {
        int minimumX = site.memoryMin().getX();
        int maximumX = site.memoryMax().getX();
        int minimumZ = site.memoryMin().getZ();
        int maximumZ = site.memoryMax().getZ();
        Set<Long> packed = new LinkedHashSet<>();
        packed.add(BlockPos.asLong(0, 0, 0));
        packed.add(BlockPos.asLong(minimumX, 0, minimumZ));
        packed.add(BlockPos.asLong(maximumX, 0, minimumZ));
        packed.add(BlockPos.asLong(minimumX, 0, maximumZ));
        packed.add(BlockPos.asLong(maximumX, 0, maximumZ));
        int countX = axisSampleCount(maximumX - minimumX);
        int countZ = axisSampleCount(maximumZ - minimumZ);
        for (int indexX = 0; indexX < countX; indexX++) {
            int offsetX = axisSample(minimumX, maximumX, indexX, countX);
            for (int indexZ = 0; indexZ < countZ; indexZ++) {
                packed.add(BlockPos.asLong(
                        offsetX,
                        0,
                        axisSample(minimumZ, maximumZ, indexZ, countZ)));
            }
        }
        List<BlockPos> offsets = new ArrayList<>(packed.size());
        for (long value : packed) {
            offsets.add(BlockPos.of(value));
        }
        return offsets;
    }

    /**
     * Samples solid ground ({@link Heightmap.Types#OCEAN_FLOOR_WG}) across the
     * memory footprint and accepts only gently sloping pads. The returned
     * anchor Y is the median sample so the coarse-dirt line tracks the terrain
     * instead of a single chunk-centre spike or dip.
     */
    public static EchoSiteLandFooting evaluate(
            ChunkGenerator generator,
            LevelHeightAccessor heightAccessor,
            RandomState randomState,
            EchoSiteType site,
            int centerX,
            int centerZ) {
        int seaLevel = generator.getSeaLevel();
        List<Integer> samples = new ArrayList<>();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (BlockPos offset : sampleOffsets(site)) {
            int y = surface(
                    generator,
                    heightAccessor,
                    randomState,
                    centerX + offset.getX(),
                    centerZ + offset.getZ());
            samples.add(y);
            lowest = Math.min(lowest, y);
            highest = Math.max(highest, y);
            if (highest - lowest > MAX_FOOTPRINT_SLOPE) {
                return reject();
            }
        }
        if (samples.isEmpty()) {
            return reject();
        }
        Collections.sort(samples);
        int median = samples.get(samples.size() / 2);
        if (median < seaLevel + MIN_ABOVE_SEA) {
            return reject();
        }
        return new EchoSiteLandFooting(true, median);
    }

    /**
     * Same sparse height grid as {@link #evaluate}, but only rejects cliffs
     * and standing water. The pad still sits on {@code ocean_floor} at the
     * origin so a stamped sand cap does not float to the median of a dune.
     *
     * <p>Ocean-floor anchoring without a dry check would seat the ruin on a
     * lake bed and stamp through the water.</p>
     */
    public static boolean hasGentleRelief(
            ChunkGenerator generator,
            LevelHeightAccessor heightAccessor,
            RandomState randomState,
            EchoSiteType site,
            int centerX,
            int centerZ) {
        int seaLevel = generator.getSeaLevel();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (BlockPos offset : sampleOffsets(site)) {
            int x = centerX + offset.getX();
            int z = centerZ + offset.getZ();
            int floor = height(
                    generator,
                    heightAccessor,
                    randomState,
                    x,
                    z,
                    Heightmap.Types.OCEAN_FLOOR_WG);
            int top = height(
                    generator,
                    heightAccessor,
                    randomState,
                    x,
                    z,
                    Heightmap.Types.WORLD_SURFACE_WG);
            if (floor < seaLevel || !isDryColumn(top, floor)) {
                return false;
            }
            lowest = Math.min(lowest, floor);
            highest = Math.max(highest, floor);
            if (highest - lowest > MAX_BLEND_SITE_RELIEF) {
                return false;
            }
        }
        return lowest != Integer.MAX_VALUE;
    }

    public static boolean isDryColumn(int worldSurface, int oceanFloor) {
        return worldSurface - oceanFloor <= MAX_STANDING_WATER;
    }

    private static int axisSampleCount(int span) {
        if (span <= 0) {
            return 1;
        }
        return Math.min(MAX_AXIS_SAMPLES, Math.max(2, span));
    }

    private static int axisSample(int minimum, int maximum, int index, int count) {
        if (count <= 1 || index <= 0) {
            return minimum;
        }
        if (index >= count - 1) {
            return maximum;
        }
        return minimum + (int) ((long) (maximum - minimum) * index / (count - 1));
    }

    private static int surface(
            ChunkGenerator generator,
            LevelHeightAccessor heightAccessor,
            RandomState randomState,
            int x,
            int z) {
        return height(
                generator,
                heightAccessor,
                randomState,
                x,
                z,
                Heightmap.Types.OCEAN_FLOOR_WG);
    }

    private static int height(
            ChunkGenerator generator,
            LevelHeightAccessor heightAccessor,
            RandomState randomState,
            int x,
            int z,
            Heightmap.Types type) {
        return generator.getFirstOccupiedHeight(
                x,
                z,
                type,
                heightAccessor,
                randomState);
    }
}
