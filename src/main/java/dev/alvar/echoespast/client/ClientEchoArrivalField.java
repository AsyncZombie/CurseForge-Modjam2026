package dev.alvar.echoespast.client;

import dev.alvar.echoespast.visual.EchoArrivalSolver;
import dev.alvar.echoespast.visual.EchoWaveVolume;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Compact client-side arrival atlas for one Past Echo activation. Traversable
 * cells are solved once; adjacent solid cells inherit the arrival of the air
 * touching their visible surface, so the post shader needs one texture fetch.
 */
final class ClientEchoArrivalField {
    private static final int MAX_GRID_AXIS = 96;
    private static final double MAX_SURFACE_GRADIENT = 1.45;
    private static boolean runtimePrepared;
    static final ClientEchoArrivalField EMPTY = new ClientEchoArrivalField(
            BlockPos.ZERO,
            1,
            1,
            1,
            1.0F,
            new float[] {Float.POSITIVE_INFINITY},
            new float[] {Float.POSITIVE_INFINITY},
            Vec3.ZERO,
            0);

    /** Moves the solver's first tiered-compilation pass out of gameplay. */
    static void prepareRuntime() {
        if (runtimePrepared) {
            return;
        }
        runtimePrepared = true;
        // Match the maximum 16-block local domain closely enough to cross the
        // JVM's compilation thresholds before a player can click the item.
        // Several materials and sealed cells exercise the same queue branches
        // as a real cave without touching a client level off-thread.
        for (int pass = 0; pass < 3; pass++) {
            int salt = pass;
            EchoArrivalSolver.solve(
                    33,
                    33,
                    33,
                    16,
                    16,
                    16,
                    Double.POSITIVE_INFINITY,
                    index -> ((index + salt * 31) % 29) == 0
                            ? Double.POSITIVE_INFINITY
                            : 1.0 + ((index + salt) & 3) * 0.07);
        }
    }

    private final BlockPos minimum;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final float maximumDistance;
    private final float[] pressureArrivals;
    private final float[] arrivals;
    private final Vec3 origin;
    private final int reachedCells;

    static ClientEchoArrivalField build(
            Minecraft minecraft,
            EchoWaveVolume volume) {
        if (minecraft.level == null) {
            return EMPTY;
        }
        BlockPos minimum = volume.minBlock();
        BlockPos maximum = volume.maxBlock();
        int sizeX = maximum.getX() - minimum.getX() + 1;
        int sizeY = maximum.getY() - minimum.getY() + 1;
        int sizeZ = maximum.getZ() - minimum.getZ() + 1;
        if (sizeX <= 0
                || sizeY <= 0
                || sizeZ <= 0
                || sizeX > MAX_GRID_AXIS
                || sizeY > MAX_GRID_AXIS
                || sizeZ > MAX_GRID_AXIS) {
            return EMPTY;
        }

        int cellCount = Math.multiplyExact(
                sizeX,
                Math.multiplyExact(sizeY, sizeZ));
        float[] traversalCosts = new float[cellCount];
        Arrays.fill(traversalCosts, Float.NaN);
        BlockPos start = BlockPos.containing(volume.center());
        int startX = start.getX() - minimum.getX();
        int startY = start.getY() - minimum.getY();
        int startZ = start.getZ() - minimum.getZ();
        // The domain already bounds the work. Do not cap route length by the
        // straight-line radius: a one-block opening or a winding corridor can
        // have a valid acoustic path much longer than its Euclidean distance.
        double maximumDistance = Double.POSITIVE_INFINITY;

        EchoArrivalSolver.Field solved = EchoArrivalSolver.solve(
                sizeX,
                sizeY,
                sizeZ,
                startX,
                startY,
                startZ,
                maximumDistance,
                index -> traversalCost(
                        minecraft,
                        volume,
                        minimum,
                        sizeX,
                        sizeY,
                        index,
                        index == index(
                                startX,
                                startY,
                                startZ,
                                sizeX,
                                sizeY),
                        traversalCosts));
        if (solved.reachedCells() == 0) {
            return EMPTY;
        }

        float[] surfaceArrivals = solved.distances().clone();
        float farthest = solved.farthestDistance();
        for (int z = 0; z < sizeZ; z++) {
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    int index = index(x, y, z, sizeX, sizeY);
                    if (Float.isFinite(surfaceArrivals[index])
                            || Float.isFinite(traversalCosts[index])) {
                        continue;
                    }
                    float nearest = nearestArrival(
                            solved,
                            x,
                            y,
                            z);
                    if (!Float.isFinite(nearest)) {
                        continue;
                    }
                    float arrival = Math.min(
                            solved.farthestDistance()
                                    + 1.0F,
                            nearest + 0.28F);
                    surfaceArrivals[index] = arrival;
                    farthest = Math.max(farthest, arrival);
                }
            }
        }
        return new ClientEchoArrivalField(
                minimum,
                sizeX,
                sizeY,
                sizeZ,
                Math.max(1.0F, farthest),
                solved.distances(),
                surfaceArrivals,
                volume.center(),
                solved.reachedCells());
    }

    static Preparation prepare(
            Minecraft minecraft,
            EchoWaveVolume volume) {
        return new Preparation(minecraft, volume);
    }

    /** Cooperative world capture, path solve and surface-field finalization. */
    static final class Preparation {
        private final Minecraft minecraft;
        private final EchoWaveVolume volume;
        private final BlockPos minimum;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int startX;
        private final int startY;
        private final int startZ;
        private final int startIndex;
        private final float[] traversalCosts;
        private Stage stage;
        private int cursor;
        private EchoArrivalSolver.Incremental solver;
        private EchoArrivalSolver.Field solved;
        private float[] surfaceArrivals;
        private float farthest;
        private ClientEchoArrivalField result;

        private Preparation(
                Minecraft minecraft,
                EchoWaveVolume volume) {
            this.minecraft = minecraft;
            this.volume = volume;
            this.minimum = volume.minBlock();
            BlockPos maximum = volume.maxBlock();
            this.sizeX = maximum.getX() - minimum.getX() + 1;
            this.sizeY = maximum.getY() - minimum.getY() + 1;
            this.sizeZ = maximum.getZ() - minimum.getZ() + 1;
            BlockPos start = BlockPos.containing(volume.center());
            this.startX = start.getX() - minimum.getX();
            this.startY = start.getY() - minimum.getY();
            this.startZ = start.getZ() - minimum.getZ();
            if (minecraft.level == null
                    || sizeX <= 0
                    || sizeY <= 0
                    || sizeZ <= 0
                    || sizeX > MAX_GRID_AXIS
                    || sizeY > MAX_GRID_AXIS
                    || sizeZ > MAX_GRID_AXIS) {
                this.startIndex = 0;
                this.traversalCosts = new float[0];
                this.stage = Stage.DONE;
                this.result = EMPTY;
                return;
            }
            int cellCount = Math.multiplyExact(
                    sizeX,
                    Math.multiplyExact(sizeY, sizeZ));
            this.startIndex = index(
                    startX,
                    startY,
                    startZ,
                    sizeX,
                    sizeY);
            this.traversalCosts = new float[cellCount];
            Arrays.fill(traversalCosts, Float.NaN);
            this.stage = Stage.CAPTURE;
        }

        boolean advance(long deadlineNanos) {
            int processed = 0;
            while (stage != Stage.DONE
                    && (processed == 0 || System.nanoTime() < deadlineNanos)) {
                switch (stage) {
                    case CAPTURE -> {
                        if (cursor >= traversalCosts.length) {
                            solver = EchoArrivalSolver.incremental(
                                    sizeX,
                                    sizeY,
                                    sizeZ,
                                    startX,
                                    startY,
                                    startZ,
                                    Double.POSITIVE_INFINITY,
                                    index -> traversalCosts[index]);
                            stage = Stage.SOLVE;
                            cursor = 0;
                            continue;
                        }
                        traversalCost(
                                minecraft,
                                volume,
                                minimum,
                                sizeX,
                                sizeY,
                                cursor,
                                cursor == startIndex,
                                traversalCosts);
                        cursor++;
                        processed++;
                    }
                    case SOLVE -> {
                        processed++;
                        if (!solver.advance(8)) {
                            continue;
                        }
                        solved = solver.result();
                        if (solved.reachedCells() == 0) {
                            result = EMPTY;
                            stage = Stage.DONE;
                            continue;
                        }
                        surfaceArrivals = solved.distances().clone();
                        farthest = solved.farthestDistance();
                        cursor = 0;
                        stage = Stage.SURFACES;
                    }
                    case SURFACES -> {
                        if (cursor >= surfaceArrivals.length) {
                            result = new ClientEchoArrivalField(
                                    minimum,
                                    sizeX,
                                    sizeY,
                                    sizeZ,
                                    Math.max(1.0F, farthest),
                                    solved.distances(),
                                    surfaceArrivals,
                                    volume.center(),
                                    solved.reachedCells());
                            stage = Stage.DONE;
                            continue;
                        }
                        int current = cursor++;
                        processed++;
                        if (Float.isFinite(surfaceArrivals[current])
                                || Float.isFinite(traversalCosts[current])) {
                            continue;
                        }
                        int x = current % sizeX;
                        int yz = current / sizeX;
                        int y = yz % sizeY;
                        int z = yz / sizeY;
                        float nearest = nearestArrival(solved, x, y, z);
                        if (!Float.isFinite(nearest)) {
                            continue;
                        }
                        float arrival = Math.min(
                                solved.farthestDistance() + 1.0F,
                                nearest + 0.28F);
                        surfaceArrivals[current] = arrival;
                        farthest = Math.max(farthest, arrival);
                    }
                    case DONE -> {
                    }
                }
            }
            return stage == Stage.DONE;
        }

        ClientEchoArrivalField result() {
            if (stage != Stage.DONE) {
                throw new IllegalStateException(
                        "Arrival preparation requested before completion");
            }
            return result;
        }

        private enum Stage {
            CAPTURE,
            SOLVE,
            SURFACES,
            DONE
        }
    }

    private static double traversalCost(
            Minecraft minecraft,
            EchoWaveVolume volume,
            BlockPos minimum,
            int sizeX,
            int sizeY,
            int index,
            boolean source,
            float[] cache) {
        float cached = cache[index];
        if (!Float.isNaN(cached)) {
            return cached;
        }
        int x = index % sizeX;
        int yz = index / sizeX;
        int y = yz % sizeY;
        int z = yz / sizeY;
        BlockPos position = minimum.offset(x, y, z);
        if (!minecraft.level.isInWorldBounds(position)
                || !minecraft.level.hasChunkAt(position)
                || !volume.contains(
                        position.getCenter())) {
            cache[index] = Float.POSITIVE_INFINITY;
            return Double.POSITIVE_INFINITY;
        }
        if (source) {
            cache[index] = 1.0F;
            return 1.0;
        }

        BlockState state = minecraft.level.getBlockState(position);
        VoxelShape shape = state.getCollisionShape(
                minecraft.level,
                position);
        if (shape.isEmpty()) {
            float cost = state.getFluidState().isEmpty()
                    ? 1.0F
                    : 1.22F;
            cache[index] = cost;
            return cost;
        }
        double occupiedVolume = 0.0;
        for (AABB box : shape.toAabbs()) {
            occupiedVolume += Math.max(0.0, box.getXsize())
                    * Math.max(0.0, box.getYsize())
                    * Math.max(0.0, box.getZsize());
        }
        if (occupiedVolume >= 0.985) {
            cache[index] = Float.POSITIVE_INFINITY;
            return Double.POSITIVE_INFINITY;
        }
        float cost = (float) (1.10
                + Math.clamp(occupiedVolume, 0.0, 0.98) * 1.35);
        cache[index] = cost;
        return cost;
    }

    private static float nearestArrival(
            EchoArrivalSolver.Field field,
            int x,
            int y,
            int z) {
        float nearest = Float.POSITIVE_INFINITY;
        nearest = Math.min(nearest, field.distance(x - 1, y, z));
        nearest = Math.min(nearest, field.distance(x + 1, y, z));
        nearest = Math.min(nearest, field.distance(x, y - 1, z));
        nearest = Math.min(nearest, field.distance(x, y + 1, z));
        nearest = Math.min(nearest, field.distance(x, y, z - 1));
        return Math.min(nearest, field.distance(x, y, z + 1));
    }

    float arrivalAt(BlockPos position) {
        if (isEmpty()) {
            return Float.POSITIVE_INFINITY;
        }
        int x = position.getX() - minimum.getX();
        int y = position.getY() - minimum.getY();
        int z = position.getZ() - minimum.getZ();
        if (x < 0
                || y < 0
                || z < 0
                || x >= sizeX
                || y >= sizeY
                || z >= sizeZ) {
            return Float.POSITIVE_INFINITY;
        }
        return arrivals[index(x, y, z, sizeX, sizeY)];
    }

    /**
     * Returns the pressure arrival on the exposed side of a rendered face.
     * Full blocks can border both a reached room and a sealed room; using one
     * value for the whole block would leak the crest through the wall.
     */
    float surfaceArrivalAt(
            BlockPos position,
            Vec3 outwardNormal) {
        return surfaceSampleAt(
                position,
                outwardNormal).arrival();
    }

    SurfaceSample surfaceSampleAt(
            BlockPos position,
            Vec3 outwardNormal) {
        if (isEmpty()) {
            return SurfaceSample.UNREACHED;
        }
        float direct = pressureArrivalAt(position);
        BlockPos pressurePosition = position;
        float arrival = direct;
        if (Float.isFinite(direct)) {
            return new SurfaceSample(
                    direct,
                    pressureGradientAt(
                            pressurePosition));
        }
        int stepX =
                axisStep(outwardNormal.x);
        int stepY =
                axisStep(outwardNormal.y);
        int stepZ =
                axisStep(outwardNormal.z);
        if ((stepX | stepY | stepZ) == 0) {
            return SurfaceSample.UNREACHED;
        }
        pressurePosition =
                position.offset(
                        stepX,
                        stepY,
                        stepZ);
        float exterior =
                pressureArrivalAt(
                        pressurePosition);
        if (!Float.isFinite(exterior)) {
            return SurfaceSample.UNREACHED;
        }
        arrival = Math.min(
                maximumDistance,
                exterior + 0.28F);
        return new SurfaceSample(
                arrival,
                pressureGradientAt(
                        pressurePosition));
    }

    /**
     * Samples the routed pressure on a world-space face vertex. Sampling the
     * pressure lattice instead of extrapolating independently from each face
     * center guarantees that two neighboring quads receive the same phase at
     * their shared edge.
     */
    float surfaceDistanceAt(
            BlockPos position,
            Vec3 outwardNormal,
            Vec3 surfacePoint) {
        if (isEmpty()) {
            return Float.POSITIVE_INFINITY;
        }
        boolean pressureInside = Float.isFinite(
                pressureArrivalAt(position));
        int stepX = axisStep(outwardNormal.x);
        int stepY = axisStep(outwardNormal.y);
        int stepZ = axisStep(outwardNormal.z);
        if (!pressureInside && (stepX | stepY | stepZ) == 0) {
            return Float.POSITIVE_INFINITY;
        }

        Vec3 pressurePoint = surfacePoint.add(
                outwardNormal.scale(
                        pressureInside ? -0.5 : 0.5));
        float interpolated = interpolatedPressureArrivalAt(
                pressurePoint);
        if (Float.isFinite(interpolated)) {
            return Math.min(
                    maximumDistance,
                    interpolated
                            + (pressureInside ? 0.0F : 0.28F));
        }

        SurfaceSample fallback = surfaceSampleAt(
                position,
                outwardNormal);
        if (!fallback.reached()) {
            return Float.POSITIVE_INFINITY;
        }
        Vec3 center = position.getCenter();
        return (float) Math.max(
                0.0,
                fallback.arrival()
                        + surfacePoint.subtract(center)
                                .dot(fallback.gradient()));
    }

    private float interpolatedPressureArrivalAt(
            Vec3 worldPoint) {
        double gridX = Math.clamp(
                worldPoint.x - minimum.getX() - 0.5,
                0.0,
                sizeX - 1.0);
        double gridY = Math.clamp(
                worldPoint.y - minimum.getY() - 0.5,
                0.0,
                sizeY - 1.0);
        double gridZ = Math.clamp(
                worldPoint.z - minimum.getZ() - 0.5,
                0.0,
                sizeZ - 1.0);
        int x0 = (int) Math.floor(gridX);
        int y0 = (int) Math.floor(gridY);
        int z0 = (int) Math.floor(gridZ);
        int x1 = Math.min(sizeX - 1, x0 + 1);
        int y1 = Math.min(sizeY - 1, y0 + 1);
        int z1 = Math.min(sizeZ - 1, z0 + 1);
        double tx = gridX - x0;
        double ty = gridY - y0;
        double tz = gridZ - z0;
        double weighted = 0.0;
        double totalWeight = 0.0;
        for (int ix = 0; ix < 2; ix++) {
            if (ix == 1 && x0 == x1) {
                continue;
            }
            int x = ix == 0 ? x0 : x1;
            double wx = x0 == x1 ? 1.0 : (ix == 0 ? 1.0 - tx : tx);
            for (int iy = 0; iy < 2; iy++) {
                if (iy == 1 && y0 == y1) {
                    continue;
                }
                int y = iy == 0 ? y0 : y1;
                double wy = y0 == y1 ? 1.0 : (iy == 0 ? 1.0 - ty : ty);
                for (int iz = 0; iz < 2; iz++) {
                    if (iz == 1 && z0 == z1) {
                        continue;
                    }
                    int z = iz == 0 ? z0 : z1;
                    double wz = z0 == z1 ? 1.0 : (iz == 0 ? 1.0 - tz : tz);
                    double weight = wx * wy * wz;
                    float sample = pressureArrivals[
                            index(x, y, z, sizeX, sizeY)];
                    if (weight <= 0.0 || !Float.isFinite(sample)) {
                        continue;
                    }
                    weighted += sample * weight;
                    totalWeight += weight;
                }
            }
        }
        return totalWeight <= 1.0E-8
                ? Float.POSITIVE_INFINITY
                : (float) (weighted / totalWeight);
    }

    private Vec3 pressureGradientAt(
            BlockPos position) {
        float center =
                pressureArrivalAt(position);
        if (!Float.isFinite(center)) {
            return Vec3.ZERO;
        }
        Vec3 gradient = new Vec3(
                axisGradient(
                        center,
                        pressureArrivalAt(
                                position.offset(-1, 0, 0)),
                        pressureArrivalAt(
                                position.offset(1, 0, 0))),
                axisGradient(
                        center,
                        pressureArrivalAt(
                                position.offset(0, -1, 0)),
                        pressureArrivalAt(
                                position.offset(0, 1, 0))),
                axisGradient(
                        center,
                        pressureArrivalAt(
                                position.offset(0, 0, -1)),
                        pressureArrivalAt(
                                position.offset(0, 0, 1))));
        double length = gradient.length();
        if (length <= 0.04) {
            Vec3 radial =
                    position.getCenter()
                            .subtract(origin);
            return radial.lengthSqr() <= 1.0E-8
                    ? Vec3.ZERO
                    : radial.normalize();
        }
        if (length > MAX_SURFACE_GRADIENT) {
            return gradient.scale(
                    MAX_SURFACE_GRADIENT
                            / length);
        }
        return gradient;
    }

    private static double axisGradient(
            float center,
            float negative,
            float positive) {
        boolean hasNegative =
                Float.isFinite(negative);
        boolean hasPositive =
                Float.isFinite(positive);
        if (hasNegative && hasPositive) {
            return (positive - negative)
                    * 0.5;
        }
        if (hasPositive) {
            return positive - center;
        }
        if (hasNegative) {
            return center - negative;
        }
        return 0.0;
    }

    private float pressureArrivalAt(BlockPos position) {
        int x = position.getX() - minimum.getX();
        int y = position.getY() - minimum.getY();
        int z = position.getZ() - minimum.getZ();
        if (x < 0
                || y < 0
                || z < 0
                || x >= sizeX
                || y >= sizeY
                || z >= sizeZ) {
            return Float.POSITIVE_INFINITY;
        }
        return pressureArrivals[index(x, y, z, sizeX, sizeY)];
    }

    private static int axisStep(double component) {
        if (component > 0.5) {
            return 1;
        }
        return component < -0.5 ? -1 : 0;
    }

    boolean isEmpty() {
        return this == EMPTY || reachedCells == 0;
    }

    float maximumDistance() {
        return maximumDistance;
    }

    int reachedCells() {
        return reachedCells;
    }

    record SurfaceSample(
            float arrival,
            Vec3 gradient) {
        private static final SurfaceSample UNREACHED =
                new SurfaceSample(
                        Float.POSITIVE_INFINITY,
                        Vec3.ZERO);

        boolean reached() {
            return Float.isFinite(arrival);
        }
    }

    private static int index(
            int x,
            int y,
            int z,
            int sizeX,
            int sizeY) {
        return x + sizeX * (y + sizeY * z);
    }

    private ClientEchoArrivalField(
            BlockPos minimum,
            int sizeX,
            int sizeY,
            int sizeZ,
            float maximumDistance,
            float[] pressureArrivals,
            float[] arrivals,
            Vec3 origin,
            int reachedCells) {
        this.minimum = minimum;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.maximumDistance = maximumDistance;
        this.pressureArrivals = pressureArrivals;
        this.arrivals = arrivals;
        this.origin = origin;
        this.reachedCells = reachedCells;
    }
}
