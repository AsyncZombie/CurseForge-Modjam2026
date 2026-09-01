package dev.alvar.echoespast.visual;

import java.util.Arrays;
import java.util.function.IntToDoubleFunction;

/**
 * Small bounded Dijkstra field used by Past Echo. It describes when pressure
 * reaches traversable voxels without tracing rays every frame.
 */
public final class EchoArrivalSolver {
    public static Field solve(
            int sizeX,
            int sizeY,
            int sizeZ,
            int startX,
            int startY,
            int startZ,
            double maximumDistance,
            IntToDoubleFunction traversalCost) {
        int volume = Math.multiplyExact(
                sizeX,
                Math.multiplyExact(sizeY, sizeZ));
        float[] distances = new float[volume];
        Arrays.fill(distances, Float.POSITIVE_INFINITY);
        if (!contains(
                        startX,
                        startY,
                        startZ,
                        sizeX,
                        sizeY,
                        sizeZ)
                || maximumDistance <= 0.0) {
            return new Field(
                    sizeX,
                    sizeY,
                    sizeZ,
                    distances,
                    0,
                    0.0F);
        }

        int start = index(
                startX,
                startY,
                startZ,
                sizeX,
                sizeY);
        double startCost = traversalCost.applyAsDouble(start);
        if (!Double.isFinite(startCost)) {
            return new Field(
                    sizeX,
                    sizeY,
                    sizeZ,
                    distances,
                    0,
                    0.0F);
        }

        IndexedMinHeap open =
                new IndexedMinHeap(
                        distances);
        distances[start] = 0.0F;
        open.addOrDecrease(start);
        int reached = 0;
        float farthest = 0.0F;

        while (!open.isEmpty()) {
            int currentIndex =
                    open.removeMinimum();
            float currentDistance =
                    distances[currentIndex];
            reached++;
            farthest = Math.max(
                    farthest,
                    currentDistance);
            int x = currentIndex % sizeX;
            int yz = currentIndex / sizeX;
            int y = yz % sizeY;
            int z = yz / sizeY;
            double currentCost =
                    traversalCost.applyAsDouble(
                            currentIndex);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if ((dx | dy | dz) == 0) {
                            continue;
                        }
                        int nextX = x + dx;
                        int nextY = y + dy;
                        int nextZ = z + dz;
                        if (!contains(
                                nextX,
                                nextY,
                                nextZ,
                                sizeX,
                                sizeY,
                                sizeZ)) {
                            continue;
                        }
                        int next = index(
                                nextX,
                                nextY,
                                nextZ,
                                sizeX,
                                sizeY);
                        double nextCost =
                                traversalCost.applyAsDouble(next);
                        if (!Double.isFinite(nextCost)
                                || cutsSolidCorner(
                                        x,
                                        y,
                                        z,
                                        dx,
                                        dy,
                                        dz,
                                        sizeX,
                                        sizeY,
                                        sizeZ,
                                        traversalCost)) {
                            continue;
                        }
                        double stepLength =
                                Math.sqrt(dx * dx + dy * dy + dz * dz);
                        float candidate = (float) (
                                currentDistance
                                        + stepLength
                                                * (currentCost
                                                        + nextCost)
                                                * 0.5);
                        if (candidate > maximumDistance
                                || candidate >= distances[next]) {
                            continue;
                        }
                        distances[next] = candidate;
                        open.addOrDecrease(next);
                    }
                }
            }
        }
        return new Field(
                sizeX,
                sizeY,
                sizeZ,
                distances,
                reached,
                farthest);
    }

    /**
     * Cooperative form of the same bounded Dijkstra solver. Callers choose a
     * node budget and can therefore keep path finding below a render-frame
     * deadline without changing the resulting field.
     */
    public static Incremental incremental(
            int sizeX,
            int sizeY,
            int sizeZ,
            int startX,
            int startY,
            int startZ,
            double maximumDistance,
            IntToDoubleFunction traversalCost) {
        return new Incremental(
                sizeX,
                sizeY,
                sizeZ,
                startX,
                startY,
                startZ,
                maximumDistance,
                traversalCost);
    }

    public static final class Incremental {
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final double maximumDistance;
        private final IntToDoubleFunction traversalCost;
        private final float[] distances;
        private final IndexedMinHeap open;
        private int reached;
        private float farthest;
        private boolean complete;

        private Incremental(
                int sizeX,
                int sizeY,
                int sizeZ,
                int startX,
                int startY,
                int startZ,
                double maximumDistance,
                IntToDoubleFunction traversalCost) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.maximumDistance = maximumDistance;
            this.traversalCost = traversalCost;
            int volume = Math.multiplyExact(
                    sizeX,
                    Math.multiplyExact(sizeY, sizeZ));
            this.distances = new float[volume];
            Arrays.fill(distances, Float.POSITIVE_INFINITY);
            this.open = new IndexedMinHeap(distances);
            if (!contains(
                            startX,
                            startY,
                            startZ,
                            sizeX,
                            sizeY,
                            sizeZ)
                    || maximumDistance <= 0.0) {
                complete = true;
                return;
            }
            int start = index(
                    startX,
                    startY,
                    startZ,
                    sizeX,
                    sizeY);
            if (!Double.isFinite(traversalCost.applyAsDouble(start))) {
                complete = true;
                return;
            }
            distances[start] = 0.0F;
            open.addOrDecrease(start);
        }

        /** Returns true once the complete field is available. */
        public boolean advance(int nodeBudget) {
            if (complete) {
                return true;
            }
            int remaining = Math.max(1, nodeBudget);
            while (remaining-- > 0 && !open.isEmpty()) {
                int currentIndex = open.removeMinimum();
                float currentDistance = distances[currentIndex];
                reached++;
                farthest = Math.max(farthest, currentDistance);
                int x = currentIndex % sizeX;
                int yz = currentIndex / sizeX;
                int y = yz % sizeY;
                int z = yz / sizeY;
                double currentCost = traversalCost.applyAsDouble(currentIndex);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if ((dx | dy | dz) == 0) {
                                continue;
                            }
                            int nextX = x + dx;
                            int nextY = y + dy;
                            int nextZ = z + dz;
                            if (!contains(
                                    nextX,
                                    nextY,
                                    nextZ,
                                    sizeX,
                                    sizeY,
                                    sizeZ)) {
                                continue;
                            }
                            int next = index(
                                    nextX,
                                    nextY,
                                    nextZ,
                                    sizeX,
                                    sizeY);
                            double nextCost = traversalCost.applyAsDouble(next);
                            if (!Double.isFinite(nextCost)
                                    || cutsSolidCorner(
                                            x,
                                            y,
                                            z,
                                            dx,
                                            dy,
                                            dz,
                                            sizeX,
                                            sizeY,
                                            sizeZ,
                                            traversalCost)) {
                                continue;
                            }
                            double stepLength = Math.sqrt(
                                    dx * dx + dy * dy + dz * dz);
                            float candidate = (float) (currentDistance
                                    + stepLength
                                            * (currentCost + nextCost)
                                            * 0.5);
                            if (candidate > maximumDistance
                                    || candidate >= distances[next]) {
                                continue;
                            }
                            distances[next] = candidate;
                            open.addOrDecrease(next);
                        }
                    }
                }
            }
            if (open.isEmpty()) {
                complete = true;
            }
            return complete;
        }

        public boolean isComplete() {
            return complete;
        }

        public Field result() {
            if (!complete) {
                throw new IllegalStateException(
                        "Arrival field requested before incremental solve completed");
            }
            return new Field(
                    sizeX,
                    sizeY,
                    sizeZ,
                    distances,
                    reached,
                    farthest);
        }
    }

    private static boolean cutsSolidCorner(
            int x,
            int y,
            int z,
            int dx,
            int dy,
            int dz,
            int sizeX,
            int sizeY,
            int sizeZ,
            IntToDoubleFunction traversalCost) {
        int changedAxes = Math.abs(dx)
                + Math.abs(dy)
                + Math.abs(dz);
        if (changedAxes <= 1) {
            return false;
        }
        if (dx != 0
                && !isTraversable(
                        x + dx,
                        y,
                        z,
                        sizeX,
                        sizeY,
                        sizeZ,
                        traversalCost)) {
            return true;
        }
        if (dy != 0
                && !isTraversable(
                        x,
                        y + dy,
                        z,
                        sizeX,
                        sizeY,
                        sizeZ,
                        traversalCost)) {
            return true;
        }
        return dz != 0
                && !isTraversable(
                        x,
                        y,
                        z + dz,
                        sizeX,
                        sizeY,
                        sizeZ,
                        traversalCost);
    }

    private static boolean isTraversable(
            int x,
            int y,
            int z,
            int sizeX,
            int sizeY,
            int sizeZ,
            IntToDoubleFunction traversalCost) {
        return contains(x, y, z, sizeX, sizeY, sizeZ)
                && Double.isFinite(traversalCost.applyAsDouble(
                        index(x, y, z, sizeX, sizeY)));
    }

    private static boolean contains(
            int x,
            int y,
            int z,
            int sizeX,
            int sizeY,
            int sizeZ) {
        return x >= 0
                && y >= 0
                && z >= 0
                && x < sizeX
                && y < sizeY
                && z < sizeZ;
    }

    private static int index(
            int x,
            int y,
            int z,
            int sizeX,
            int sizeY) {
        return x + sizeX * (y + sizeY * z);
    }

    public record Field(
            int sizeX,
            int sizeY,
            int sizeZ,
            float[] distances,
            int reachedCells,
            float farthestDistance) {

        public float distance(int x, int y, int z) {
            if (!contains(
                    x,
                    y,
                    z,
                    sizeX,
                    sizeY,
                    sizeZ)) {
                return Float.POSITIVE_INFINITY;
            }
            return distances[index(x, y, z, sizeX, sizeY)];
        }
    }

    /**
     * Allocation-free decrease-key heap. A normal PriorityQueue needs a new
     * node every time a route improves, causing avoidable garbage collection
     * pressure on the render thread during activation.
     */
    private static final class IndexedMinHeap {
        private final float[] priorities;
        private final int[] heap;
        private final int[] positions;
        private int size;

        private IndexedMinHeap(float[] priorities) {
            this.priorities = priorities;
            this.heap = new int[priorities.length];
            this.positions =
                    new int[priorities.length];
            Arrays.fill(
                    positions,
                    -1);
        }

        private boolean isEmpty() {
            return size == 0;
        }

        private void addOrDecrease(int index) {
            int position = positions[index];
            if (position < 0) {
                position = size++;
                heap[position] = index;
                positions[index] = position;
            }
            siftUp(position);
        }

        private int removeMinimum() {
            int minimum = heap[0];
            positions[minimum] = -1;
            int lastPosition = --size;
            if (lastPosition > 0) {
                int replacement =
                        heap[lastPosition];
                heap[0] = replacement;
                positions[replacement] = 0;
                siftDown(0);
            }
            return minimum;
        }

        private void siftUp(int position) {
            int index = heap[position];
            float priority =
                    priorities[index];
            while (position > 0) {
                int parent =
                        (position - 1) >>> 1;
                int parentIndex =
                        heap[parent];
                if (priorities[parentIndex]
                        <= priority) {
                    break;
                }
                heap[position] =
                        parentIndex;
                positions[parentIndex] =
                        position;
                position = parent;
            }
            heap[position] = index;
            positions[index] = position;
        }

        private void siftDown(int position) {
            int index = heap[position];
            float priority =
                    priorities[index];
            int half = size >>> 1;
            while (position < half) {
                int child =
                        (position << 1) + 1;
                int right = child + 1;
                if (right < size
                        && priorities[heap[right]]
                                < priorities[heap[child]]) {
                    child = right;
                }
                int childIndex =
                        heap[child];
                if (priority
                        <= priorities[childIndex]) {
                    break;
                }
                heap[position] =
                        childIndex;
                positions[childIndex] =
                        position;
                position = child;
            }
            heap[position] = index;
            positions[index] = position;
        }
    }

    private EchoArrivalSolver() {
    }
}
