package dev.alvar.echoespast.visual;

import java.util.HashMap;
import java.util.Map;

/**
 * Ownership rules for present-fade occluder distances.
 *
 * <p>Packaged fade-seed distances are pulse-lifetime. Local section scans and
 * routed timings may refine a cell to a nearer value, but must never erase
 * seed coverage or replace a nearer distance with a worse one.</p>
 */
public final class EchoOccluderDistances {
    private EchoOccluderDistances() {
    }

    /**
     * Union of local and remote maps; for shared keys the nearer finite
     * distance wins.
     */
    public static Map<Long, Double> best(
            Map<Long, Double> localDistances,
            Map<Long, Double> remoteDistances) {
        Map<Long, Double> best = new HashMap<>(
                Math.max(16, localDistances.size() + remoteDistances.size()) * 2);
        mergeMin(best, localDistances);
        mergeMin(best, remoteDistances);
        return best;
    }

    /**
     * Publishes {@code incoming} into {@code target} with nearer-wins policy.
     *
     * @return {@code true} when any key was inserted or improved
     */
    public static boolean mergeMin(
            Map<Long, Double> target,
            Map<Long, Double> incoming) {
        boolean changed = false;
        for (Map.Entry<Long, Double> entry : incoming.entrySet()) {
            double distance = entry.getValue();
            if (!Double.isFinite(distance)) {
                continue;
            }
            Double previous = target.get(entry.getKey());
            if (previous == null || distance < previous) {
                target.put(entry.getKey(), distance);
                changed = true;
            }
        }
        return changed;
    }
}
