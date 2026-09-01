package dev.alvar.echoespast.visual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Extends a reached acoustic surface through a connected volume that was air
 * in the memory. The real pressure field cannot enter the present solid, but
 * once its skin yields the historical void must keep collapsing layer by
 * layer instead of disappearing in one final-frame correction.
 */
public final class EchoOccluderPropagation {
    public static Map<Long, Double> propagate(
            Collection<BlockPos> positions,
            Map<Long, Double> surfaceSeeds,
            double distancePerLayer) {
        Incremental propagation = incremental(
                positions,
                surfaceSeeds,
                distancePerLayer);
        while (!propagation.advance(Long.MAX_VALUE)) {
            // A maximum deadline preserves the original synchronous API.
        }
        return propagation.result();
    }

    public static Incremental incremental(
            Collection<BlockPos> positions,
            Map<Long, Double> surfaceSeeds,
            double distancePerLayer) {
        return new Incremental(positions, surfaceSeeds, distancePerLayer);
    }

    /** Cooperative version used by client activation under a frame deadline. */
    public static final class Incremental {
        private final Iterator<BlockPos> positions;
        private final Iterator<Map.Entry<Long, Double>> seeds;
        private final double safeStep;
        private final List<BlockPos> indexedPositions;
        private final Map<Long, Integer> indexByPosition;
        private double[] distances;
        private IndexedMaxHeap open;
        private Map<Long, Double> propagated;
        private int serializeCursor;
        private Stage stage;
        private Map<Long, Double> result;

        private Incremental(
                Collection<BlockPos> positions,
                Map<Long, Double> surfaceSeeds,
                double distancePerLayer) {
            this.positions = positions.iterator();
            this.seeds = surfaceSeeds.entrySet().iterator();
            this.safeStep = Math.max(0.05, distancePerLayer);
            this.indexedPositions = new ArrayList<>(positions.size());
            this.indexByPosition = new HashMap<>(Math.max(16, positions.size() * 2));
            if (positions.isEmpty() || surfaceSeeds.isEmpty()) {
                stage = Stage.DONE;
                result = Map.of();
            } else {
                stage = Stage.INDEX;
            }
        }

        public boolean advance(long deadlineNanos) {
            int processed = 0;
            while (stage != Stage.DONE
                    && (processed == 0 || System.nanoTime() < deadlineNanos)) {
                switch (stage) {
                    case INDEX -> {
                        if (!positions.hasNext()) {
                            distances = new double[indexedPositions.size()];
                            Arrays.fill(distances, Double.NEGATIVE_INFINITY);
                            open = new IndexedMaxHeap(distances);
                            stage = Stage.SEEDS;
                            continue;
                        }
                        BlockPos position = positions.next();
                        long packed = position.asLong();
                        if (!indexByPosition.containsKey(packed)) {
                            indexByPosition.put(packed, indexedPositions.size());
                            indexedPositions.add(position.immutable());
                        }
                        processed++;
                    }
                    case SEEDS -> {
                        if (!seeds.hasNext()) {
                            stage = Stage.PROPAGATE;
                            continue;
                        }
                        Map.Entry<Long, Double> seed = seeds.next();
                        Integer index = indexByPosition.get(seed.getKey());
                        double distance = seed.getValue();
                        if (index != null
                                && Double.isFinite(distance)
                                && distance >= 0.0
                                && distance > distances[index]) {
                            distances[index] = distance;
                            open.addOrIncrease(index);
                        }
                        processed++;
                    }
                    case PROPAGATE -> {
                        if (open.isEmpty()) {
                            propagated = new HashMap<>(Math.max(
                                    16,
                                    indexedPositions.size() * 2));
                            stage = Stage.SERIALIZE;
                            continue;
                        }
                        int current = open.removeMaximum();
                        double floor = Math.min(0.75, distances[current]);
                        double candidate = Math.max(
                                floor,
                                distances[current] - safeStep);
                        BlockPos position = indexedPositions.get(current);
                        for (Direction direction : Direction.values()) {
                            Integer neighbor = indexByPosition.get(
                                    position.relative(direction).asLong());
                            if (neighbor == null || candidate <= distances[neighbor]) {
                                continue;
                            }
                            distances[neighbor] = candidate;
                            open.addOrIncrease(neighbor);
                        }
                        processed++;
                    }
                    case SERIALIZE -> {
                        if (serializeCursor >= indexedPositions.size()) {
                            result = propagated.isEmpty()
                                    ? Map.of()
                                    : Map.copyOf(propagated);
                            stage = Stage.DONE;
                            continue;
                        }
                        int index = serializeCursor++;
                        if (Double.isFinite(distances[index])) {
                            propagated.put(
                                    indexedPositions.get(index).asLong(),
                                    distances[index]);
                        }
                        processed++;
                    }
                    case DONE -> {
                    }
                }
            }
            return stage == Stage.DONE;
        }

        public Map<Long, Double> result() {
            if (stage != Stage.DONE) {
                throw new IllegalStateException(
                        "Occluder propagation requested before completion");
            }
            return result;
        }

        private enum Stage {
            INDEX,
            SEEDS,
            PROPAGATE,
            SERIALIZE,
            DONE
        }
    }

    private static final class IndexedMaxHeap {
        private final double[] priorities;
        private final int[] positions;
        private int[] heap;
        private int size;

        private IndexedMaxHeap(double[] priorities) {
            this.priorities = priorities;
            this.positions = new int[priorities.length];
            Arrays.fill(positions, -1);
            this.heap = new int[Math.max(16, priorities.length)];
        }

        private boolean isEmpty() {
            return size == 0;
        }

        private void addOrIncrease(int value) {
            int position = positions[value];
            if (position < 0) {
                if (size == heap.length) {
                    heap = Arrays.copyOf(heap, heap.length * 2);
                }
                heap[size] = value;
                positions[value] = size;
                siftUp(size++);
                return;
            }
            siftUp(position);
        }

        private int removeMaximum() {
            int result = heap[0];
            positions[result] = -1;
            size--;
            if (size > 0) {
                heap[0] = heap[size];
                positions[heap[0]] = 0;
                siftDown(0);
            }
            return result;
        }

        private void siftUp(int start) {
            int position = start;
            while (position > 0) {
                int parent = (position - 1) >>> 1;
                if (priorities[heap[parent]] >= priorities[heap[position]]) {
                    break;
                }
                swap(parent, position);
                position = parent;
            }
        }

        private void siftDown(int start) {
            int position = start;
            while (true) {
                int left = position * 2 + 1;
                if (left >= size) {
                    return;
                }
                int right = left + 1;
                int largest = right < size
                                && priorities[heap[right]] > priorities[heap[left]]
                        ? right
                        : left;
                if (priorities[heap[position]] >= priorities[heap[largest]]) {
                    return;
                }
                swap(position, largest);
                position = largest;
            }
        }

        private void swap(int first, int second) {
            int value = heap[first];
            heap[first] = heap[second];
            heap[second] = value;
            positions[heap[first]] = first;
            positions[heap[second]] = second;
        }
    }

    private EchoOccluderPropagation() {
    }
}
