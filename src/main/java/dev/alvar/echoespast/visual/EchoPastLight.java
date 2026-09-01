package dev.alvar.echoespast.visual;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compact block- and sky-light simulations for the remembered layer.
 * It never writes to Minecraft's real light engine.
 */
public final class EchoPastLight {
    private static final int MAX_DENSE_CELLS = 16 * 1024 * 1024;
    private static boolean runtimePrepared;

    /** Moves first-use allocation and JIT work into the resource-load pause. */
    public static void prepareRuntime() {
        if (runtimePrepared) {
            return;
        }
        runtimePrepared = true;
        BlockPos minimum = new BlockPos(-16, -16, -16);
        BlockPos maximum = new BlockPos(16, 16, 16);
        Map<BlockPos, Integer> seed = Map.of(
                BlockPos.ZERO,
                15,
                new BlockPos(9, 7, -8),
                12,
                new BlockPos(-11, -4, 10),
                9);
        // Two representative passes move dense allocation, queue growth and
        // tiered compilation into resource loading instead of the first Echo.
        for (int pass = 0; pass < 2; pass++) {
            propagate(Map.of(), seed, minimum, maximum);
            propagateSky(Map.of(), seed, minimum, maximum);
        }
    }

    public static Map<Long, Integer> propagate(
            Map<BlockPos, BlockState> rememberedStates,
            BlockPos minimum,
            BlockPos maximum) {
        Map<BlockPos, Integer> rememberedEmitters = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : rememberedStates.entrySet()) {
            int emission = entry.getValue().getLightEmission();
            if (emission > 0) {
                rememberedEmitters.put(entry.getKey(), emission);
            }
        }
        return propagate(rememberedStates, rememberedEmitters, minimum, maximum);
    }

    /**
     * Builds the light visible in the reconstructed timeline. The remembered
     * blocks decide where light may travel, while seeds can come from either
     * timeline. Consequently, a present torch can illuminate the past without
     * a present block that replaced remembered air casting an impossible
     * shadow through it.
     */
    public static Map<Long, Integer> propagate(
            Map<BlockPos, BlockState> rememberedStates,
            Map<BlockPos, Integer> seedLevels,
            BlockPos minimum,
            BlockPos maximum) {
        return propagate(rememberedStates, seedLevels, minimum, maximum, false);
    }

    /**
     * Skylight has Minecraft's characteristic no-loss downward travel through
     * transparent space, while horizontal and upward travel still fades.
     */
    public static Map<Long, Integer> propagateSky(
            Map<BlockPos, BlockState> rememberedStates,
            Map<BlockPos, Integer> seedLevels,
            BlockPos minimum,
            BlockPos maximum) {
        return propagate(rememberedStates, seedLevels, minimum, maximum, true);
    }

    private static Map<Long, Integer> propagate(
            Map<BlockPos, BlockState> rememberedStates,
            Map<BlockPos, Integer> seedLevels,
            BlockPos minimum,
            BlockPos maximum,
            boolean skyLight) {
        long sizeX = (long) maximum.getX() - minimum.getX() + 1L;
        long sizeY = (long) maximum.getY() - minimum.getY() + 1L;
        long sizeZ = (long) maximum.getZ() - minimum.getZ() + 1L;
        if (sizeX <= 0L || sizeY <= 0L || sizeZ <= 0L) {
            return Map.of();
        }
        if (fitsDense(sizeX, sizeY, sizeZ)) {
            return propagateDense(
                    rememberedStates,
                    seedLevels,
                    minimum,
                    (int) sizeX,
                    (int) sizeY,
                    (int) sizeZ,
                    skyLight);
        }
        return propagateSparse(
                rememberedStates,
                seedLevels,
                minimum,
                maximum,
                skyLight);
    }

    private static boolean fitsDense(long sizeX, long sizeY, long sizeZ) {
        if (sizeX > MAX_DENSE_CELLS
                || sizeY > MAX_DENSE_CELLS
                || sizeZ > MAX_DENSE_CELLS
                || sizeX > MAX_DENSE_CELLS / sizeY) {
            return false;
        }
        long plane = sizeX * sizeY;
        return plane <= MAX_DENSE_CELLS / sizeZ;
    }

    private static Map<Long, Integer> propagateDense(
            Map<BlockPos, BlockState> rememberedStates,
            Map<BlockPos, Integer> seedLevels,
            BlockPos minimum,
            int sizeX,
            int sizeY,
            int sizeZ,
            boolean skyLight) {
        int minimumX = minimum.getX();
        int minimumY = minimum.getY();
        int minimumZ = minimum.getZ();
        int strideX = sizeY * sizeZ;
        int cellCount = sizeX * strideX;
        byte[] levels = new byte[cellCount];
        byte[] dampening = new byte[cellCount];

        for (Map.Entry<BlockPos, BlockState> entry : rememberedStates.entrySet()) {
            BlockPos position = entry.getKey();
            int localX = position.getX() - minimumX;
            int localY = position.getY() - minimumY;
            int localZ = position.getZ() - minimumZ;
            if (localX >= 0 && localX < sizeX
                    && localY >= 0 && localY < sizeY
                    && localZ >= 0 && localZ < sizeZ) {
                dampening[(localX * sizeY + localY) * sizeZ + localZ] =
                        (byte) Math.clamp(entry.getValue().getLightDampening(), 0, 15);
            }
        }

        IntArrayFIFOQueue pending = new IntArrayFIFOQueue();
        for (Map.Entry<BlockPos, Integer> entry : seedLevels.entrySet()) {
            BlockPos position = entry.getKey();
            int emission = Math.clamp(entry.getValue(), 0, 15);
            int localX = position.getX() - minimumX;
            int localY = position.getY() - minimumY;
            int localZ = position.getZ() - minimumZ;
            if (emission <= 0
                    || localX < 0 || localX >= sizeX
                    || localY < 0 || localY >= sizeY
                    || localZ < 0 || localZ >= sizeZ) {
                continue;
            }
            int index = (localX * sizeY + localY) * sizeZ + localZ;
            if (emission > Byte.toUnsignedInt(levels[index])) {
                levels[index] = (byte) emission;
                pending.enqueue(index);
            }
        }

        while (!pending.isEmpty()) {
            int index = pending.dequeueInt();
            int level = Byte.toUnsignedInt(levels[index]);
            if (level <= 1) {
                continue;
            }
            int localZ = index % sizeZ;
            int yz = index / sizeZ;
            int localY = yz % sizeY;
            int localX = yz / sizeY;
            if (localY > 0) {
                spreadDense(levels, dampening, pending, index - sizeZ, level, skyLight);
            }
            if (localY + 1 < sizeY) {
                spreadDense(levels, dampening, pending, index + sizeZ, level, false);
            }
            if (localZ > 0) {
                spreadDense(levels, dampening, pending, index - 1, level, false);
            }
            if (localZ + 1 < sizeZ) {
                spreadDense(levels, dampening, pending, index + 1, level, false);
            }
            if (localX > 0) {
                spreadDense(levels, dampening, pending, index - strideX, level, false);
            }
            if (localX + 1 < sizeX) {
                spreadDense(levels, dampening, pending, index + strideX, level, false);
            }
        }

        int litCells = 0;
        for (byte level : levels) {
            if (level != 0) {
                litCells++;
            }
        }
        if (litCells == 0) {
            return Map.of();
        }
        Long2IntOpenHashMap result = new Long2IntOpenHashMap(litCells);
        int index = 0;
        for (int localX = 0; localX < sizeX; localX++) {
            int x = minimumX + localX;
            for (int localY = 0; localY < sizeY; localY++) {
                int y = minimumY + localY;
                for (int localZ = 0; localZ < sizeZ; localZ++, index++) {
                    int level = Byte.toUnsignedInt(levels[index]);
                    if (level != 0) {
                        result.put(
                                BlockPos.asLong(x, y, minimumZ + localZ),
                                level);
                    }
                }
            }
        }
        return Long2IntMaps.unmodifiable(result);
    }

    /**
     * Cooperative dense propagation used by live Echo activation. It produces
     * the same field as {@link #propagate} while bounding capture, queue work
     * and map publication by the caller's frame deadline.
     */
    public static IncrementalPropagation incremental(
            Map<BlockPos, BlockState> rememberedStates,
            Map<BlockPos, Integer> seedLevels,
            BlockPos minimum,
            BlockPos maximum,
            boolean skyLight) {
        return new IncrementalPropagation(
                rememberedStates,
                seedLevels,
                minimum,
                maximum,
                skyLight);
    }

    public static final class IncrementalPropagation {
        private final BlockPos minimum;
        private final BlockPos maximum;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int strideX;
        private final boolean skyLight;
        private final Iterator<Map.Entry<BlockPos, BlockState>> remembered;
        private final Iterator<Map.Entry<BlockPos, Integer>> seeds;
        private final byte[] levels;
        private final byte[] dampening;
        private final IntArrayFIFOQueue pending = new IntArrayFIFOQueue();
        private Stage stage;
        private int serializeCursor;
        private Long2IntOpenHashMap serialized;
        private Map<Long, Integer> result;

        private IncrementalPropagation(
                Map<BlockPos, BlockState> rememberedStates,
                Map<BlockPos, Integer> seedLevels,
                BlockPos minimum,
                BlockPos maximum,
                boolean skyLight) {
            this.minimum = minimum;
            this.maximum = maximum;
            long longSizeX = (long) maximum.getX() - minimum.getX() + 1L;
            long longSizeY = (long) maximum.getY() - minimum.getY() + 1L;
            long longSizeZ = (long) maximum.getZ() - minimum.getZ() + 1L;
            if (!fitsDense(longSizeX, longSizeY, longSizeZ)) {
                this.sizeX = 0;
                this.sizeY = 0;
                this.sizeZ = 0;
                this.strideX = 0;
                this.skyLight = skyLight;
                this.remembered = rememberedStates.entrySet().iterator();
                this.seeds = seedLevels.entrySet().iterator();
                this.levels = new byte[0];
                this.dampening = new byte[0];
                this.stage = Stage.FALLBACK;
                return;
            }
            this.sizeX = (int) longSizeX;
            this.sizeY = (int) longSizeY;
            this.sizeZ = (int) longSizeZ;
            this.strideX = sizeY * sizeZ;
            this.skyLight = skyLight;
            this.remembered = rememberedStates.entrySet().iterator();
            this.seeds = seedLevels.entrySet().iterator();
            int cellCount = sizeX * strideX;
            this.levels = new byte[cellCount];
            this.dampening = new byte[cellCount];
            this.stage = Stage.DAMPENING;
        }

        public boolean advance(long deadlineNanos) {
            int processed = 0;
            while (stage != Stage.DONE
                    && (processed == 0 || System.nanoTime() < deadlineNanos)) {
                switch (stage) {
                    case DAMPENING -> {
                        if (!remembered.hasNext()) {
                            stage = Stage.SEEDS;
                            continue;
                        }
                        Map.Entry<BlockPos, BlockState> entry = remembered.next();
                        BlockPos position = entry.getKey();
                        int localX = position.getX() - minimum.getX();
                        int localY = position.getY() - minimum.getY();
                        int localZ = position.getZ() - minimum.getZ();
                        if (localX >= 0 && localX < sizeX
                                && localY >= 0 && localY < sizeY
                                && localZ >= 0 && localZ < sizeZ) {
                            dampening[(localX * sizeY + localY) * sizeZ + localZ] =
                                    (byte) Math.clamp(
                                            entry.getValue().getLightDampening(),
                                            0,
                                            15);
                        }
                        processed++;
                    }
                    case SEEDS -> {
                        if (!seeds.hasNext()) {
                            stage = Stage.PROPAGATE;
                            continue;
                        }
                        Map.Entry<BlockPos, Integer> entry = seeds.next();
                        BlockPos position = entry.getKey();
                        int emission = Math.clamp(entry.getValue(), 0, 15);
                        int localX = position.getX() - minimum.getX();
                        int localY = position.getY() - minimum.getY();
                        int localZ = position.getZ() - minimum.getZ();
                        if (emission > 0
                                && localX >= 0 && localX < sizeX
                                && localY >= 0 && localY < sizeY
                                && localZ >= 0 && localZ < sizeZ) {
                            int index = (localX * sizeY + localY) * sizeZ + localZ;
                            if (emission > Byte.toUnsignedInt(levels[index])) {
                                levels[index] = (byte) emission;
                                pending.enqueue(index);
                            }
                        }
                        processed++;
                    }
                    case PROPAGATE -> {
                        if (pending.isEmpty()) {
                            serialized = new Long2IntOpenHashMap();
                            stage = Stage.SERIALIZE;
                            continue;
                        }
                        int index = pending.dequeueInt();
                        int level = Byte.toUnsignedInt(levels[index]);
                        if (level > 1) {
                            int localZ = index % sizeZ;
                            int yz = index / sizeZ;
                            int localY = yz % sizeY;
                            int localX = yz / sizeY;
                            if (localY > 0) {
                                spreadDense(
                                        levels,
                                        dampening,
                                        pending,
                                        index - sizeZ,
                                        level,
                                        skyLight);
                            }
                            if (localY + 1 < sizeY) {
                                spreadDense(levels, dampening, pending, index + sizeZ, level, false);
                            }
                            if (localZ > 0) {
                                spreadDense(levels, dampening, pending, index - 1, level, false);
                            }
                            if (localZ + 1 < sizeZ) {
                                spreadDense(levels, dampening, pending, index + 1, level, false);
                            }
                            if (localX > 0) {
                                spreadDense(levels, dampening, pending, index - strideX, level, false);
                            }
                            if (localX + 1 < sizeX) {
                                spreadDense(levels, dampening, pending, index + strideX, level, false);
                            }
                        }
                        processed++;
                    }
                    case SERIALIZE -> {
                        if (serializeCursor >= levels.length) {
                            result = serialized.isEmpty()
                                    ? Map.of()
                                    : Long2IntMaps.unmodifiable(serialized);
                            stage = Stage.DONE;
                            continue;
                        }
                        int index = serializeCursor++;
                        int level = Byte.toUnsignedInt(levels[index]);
                        if (level != 0) {
                            int localZ = index % sizeZ;
                            int yz = index / sizeZ;
                            int localY = yz % sizeY;
                            int localX = yz / sizeY;
                            serialized.put(
                                    BlockPos.asLong(
                                            minimum.getX() + localX,
                                            minimum.getY() + localY,
                                            minimum.getZ() + localZ),
                                    level);
                        }
                        processed++;
                    }
                    case FALLBACK -> {
                        result = propagateSparse(
                                collectRemembered(),
                                collectSeeds(),
                                minimum,
                                maximum,
                                skyLight);
                        stage = Stage.DONE;
                        processed++;
                    }
                    case DONE -> {
                    }
                }
            }
            return stage == Stage.DONE;
        }

        public Map<Long, Integer> result() {
            if (stage != Stage.DONE) {
                throw new IllegalStateException(
                        "Past light requested before incremental propagation completed");
            }
            return result;
        }

        private Map<BlockPos, BlockState> collectRemembered() {
            Map<BlockPos, BlockState> collected = new HashMap<>();
            remembered.forEachRemaining(entry -> collected.put(entry.getKey(), entry.getValue()));
            return collected;
        }

        private Map<BlockPos, Integer> collectSeeds() {
            Map<BlockPos, Integer> collected = new HashMap<>();
            seeds.forEachRemaining(entry -> collected.put(entry.getKey(), entry.getValue()));
            return collected;
        }

        private enum Stage {
            DAMPENING,
            SEEDS,
            PROPAGATE,
            SERIALIZE,
            FALLBACK,
            DONE
        }
    }

    private static void spreadDense(
            byte[] levels,
            byte[] dampening,
            IntArrayFIFOQueue pending,
            int neighbor,
            int sourceLevel,
            boolean noLossWhenClear) {
        int obstruction = Byte.toUnsignedInt(dampening[neighbor]);
        int attenuation = noLossWhenClear && obstruction == 0
                ? 0
                : Math.max(1, obstruction);
        int propagated = sourceLevel - attenuation;
        if (propagated > Byte.toUnsignedInt(levels[neighbor])) {
            levels[neighbor] = (byte) propagated;
            pending.enqueue(neighbor);
        }
    }

    /**
     * Extremely sparse, unusually large authored bounds keep a primitive
     * fallback so they never force a giant dense allocation.
     */
    private static Map<Long, Integer> propagateSparse(
            Map<BlockPos, BlockState> rememberedStates,
            Map<BlockPos, Integer> seedLevels,
            BlockPos minimum,
            BlockPos maximum,
            boolean skyLight) {
        Long2ByteOpenHashMap dampening = new Long2ByteOpenHashMap(rememberedStates.size());
        for (Map.Entry<BlockPos, BlockState> entry : rememberedStates.entrySet()) {
            BlockPos position = entry.getKey();
            if (inside(position.getX(), position.getY(), position.getZ(), minimum, maximum)) {
                dampening.put(
                        position.asLong(),
                        (byte) Math.clamp(entry.getValue().getLightDampening(), 0, 15));
            }
        }
        Long2ByteOpenHashMap levels = new Long2ByteOpenHashMap();
        LongArrayFIFOQueue pending = new LongArrayFIFOQueue();
        for (Map.Entry<BlockPos, Integer> entry : seedLevels.entrySet()) {
            BlockPos position = entry.getKey();
            int emission = Math.clamp(entry.getValue(), 0, 15);
            long packed = position.asLong();
            if (emission > Byte.toUnsignedInt(levels.get(packed))
                    && inside(position.getX(), position.getY(), position.getZ(), minimum, maximum)) {
                levels.put(packed, (byte) emission);
                pending.enqueue(packed);
            }
        }
        while (!pending.isEmpty()) {
            long packed = pending.dequeueLong();
            int level = Byte.toUnsignedInt(levels.get(packed));
            if (level <= 1) {
                continue;
            }
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            spreadSparse(levels, dampening, pending, x, y - 1, z, level, skyLight, minimum, maximum);
            spreadSparse(levels, dampening, pending, x, y + 1, z, level, false, minimum, maximum);
            spreadSparse(levels, dampening, pending, x, y, z - 1, level, false, minimum, maximum);
            spreadSparse(levels, dampening, pending, x, y, z + 1, level, false, minimum, maximum);
            spreadSparse(levels, dampening, pending, x - 1, y, z, level, false, minimum, maximum);
            spreadSparse(levels, dampening, pending, x + 1, y, z, level, false, minimum, maximum);
        }
        if (levels.isEmpty()) {
            return Map.of();
        }
        Long2IntOpenHashMap result = new Long2IntOpenHashMap(levels.size());
        levels.long2ByteEntrySet().fastForEach(entry ->
                result.put(entry.getLongKey(), Byte.toUnsignedInt(entry.getByteValue())));
        return Long2IntMaps.unmodifiable(result);
    }

    private static void spreadSparse(
            Long2ByteOpenHashMap levels,
            Long2ByteOpenHashMap dampening,
            LongArrayFIFOQueue pending,
            int x,
            int y,
            int z,
            int sourceLevel,
            boolean noLossWhenClear,
            BlockPos minimum,
            BlockPos maximum) {
        if (!inside(x, y, z, minimum, maximum)) {
            return;
        }
        long packed = BlockPos.asLong(x, y, z);
        int obstruction = Byte.toUnsignedInt(dampening.get(packed));
        int attenuation = noLossWhenClear && obstruction == 0
                ? 0
                : Math.max(1, obstruction);
        int propagated = sourceLevel - attenuation;
        if (propagated > Byte.toUnsignedInt(levels.get(packed))) {
            levels.put(packed, (byte) propagated);
            pending.enqueue(packed);
        }
    }

    public static int sample(Map<Long, Integer> levels, BlockPos position) {
        return sample(levels, position.getX(), position.getY(), position.getZ());
    }

    /**
     * Allocation-free light sampling for chunk mesh workers. Sodium asks for
     * this value for every AO sample, so creating six neighboring BlockPos
     * instances per query would turn the compatibility path into avoidable
     * rebuild churn.
     */
    public static int sample(
            Map<Long, Integer> levels,
            int x,
            int y,
            int z) {
        int result = levels.getOrDefault(BlockPos.asLong(x, y, z), 0);
        result = Math.max(result, levels.getOrDefault(BlockPos.asLong(x - 1, y, z), 0));
        result = Math.max(result, levels.getOrDefault(BlockPos.asLong(x + 1, y, z), 0));
        result = Math.max(result, levels.getOrDefault(BlockPos.asLong(x, y - 1, z), 0));
        result = Math.max(result, levels.getOrDefault(BlockPos.asLong(x, y + 1, z), 0));
        result = Math.max(result, levels.getOrDefault(BlockPos.asLong(x, y, z - 1), 0));
        return Math.max(result, levels.getOrDefault(BlockPos.asLong(x, y, z + 1), 0));
    }

    /**
     * Block light is stored inside voxels, but a ghost surface can replace a
     * presently opaque voxel whose internal light is zero. Combine channels
     * independently so its visible face receives the same light as adjacent
     * air without inventing colored block light.
     */
    public static int brightestPackedLight(int first, int second) {
        return LightCoordsUtil.pack(
                Math.max(LightCoordsUtil.block(first), LightCoordsUtil.block(second)),
                Math.max(LightCoordsUtil.sky(first), LightCoordsUtil.sky(second)));
    }

    public static int translucentFacePackedLight(
            int internalPackedLight,
            int adjacentPackedLight) {
        return brightestPackedLight(internalPackedLight, adjacentPackedLight);
    }

    public static int projectionPackedLight(
            int presentPackedLight,
            int rememberedBlockLight) {
        return projectionPackedLight(
                presentPackedLight,
                rememberedBlockLight,
                LightCoordsUtil.sky(presentPackedLight));
    }

    public static int projectionPackedLight(
            int presentPackedLight,
            int rememberedBlockLight,
            int sharedSkyLight) {
        return LightCoordsUtil.pack(
                Math.max(LightCoordsUtil.block(presentPackedLight), rememberedBlockLight),
                Math.max(LightCoordsUtil.sky(presentPackedLight), sharedSkyLight));
    }

    /**
     * Present and remembered emitters share one visual light field. Blocks that
     * occupy remembered air are the sole exception: lighting them makes the
     * translucent obstruction read as solid geometry again.
     */
    public static int ghostPackedLight(
            EchoBlockChange.Kind change,
            int presentPackedLight,
            int rememberedBlockLight) {
        return ghostPackedLight(
                change,
                presentPackedLight,
                rememberedBlockLight,
                LightCoordsUtil.sky(presentPackedLight));
    }

    public static int ghostPackedLight(
            EchoBlockChange.Kind change,
            int presentPackedLight,
            int rememberedBlockLight,
            int sharedSkyLight) {
        if (change == EchoBlockChange.Kind.ADDED) {
            /*
             * This surface represents an obstruction that did not exist. Keep
             * it subdued so it reads as removable, but never force it to zero:
             * doing so produced the solid black stains reported around added
             * blocks, even beside a torch or under open sky.
             */
            return LightCoordsUtil.pack(
                    Math.min(8, Math.max(
                            LightCoordsUtil.block(presentPackedLight),
                            rememberedBlockLight)),
                    Math.min(8, Math.max(
                            LightCoordsUtil.sky(presentPackedLight),
                            sharedSkyLight)));
        }
        return projectionPackedLight(
                presentPackedLight,
                rememberedBlockLight,
                sharedSkyLight);
    }

    public static int combinedBlockLight(int presentBlockLight, int rememberedBlockLight) {
        return Math.max(presentBlockLight, rememberedBlockLight);
    }

    private static boolean inside(
            int x,
            int y,
            int z,
            BlockPos minimum,
            BlockPos maximum) {
        return x >= minimum.getX()
                && x <= maximum.getX()
                && y >= minimum.getY()
                && y <= maximum.getY()
                && z >= minimum.getZ()
                && z <= maximum.getZ();
    }

    private EchoPastLight() {
    }
}
