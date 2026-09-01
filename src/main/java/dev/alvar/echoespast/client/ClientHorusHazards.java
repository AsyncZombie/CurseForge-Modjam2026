package dev.alvar.echoespast.client;

import dev.alvar.echoespast.network.EyeHazardSignal;
import dev.alvar.echoespast.network.EyeHazardSignalsPayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientHorusHazards {
    private static final Map<Long, EyeHazardSignal> SIGNALS = new LinkedHashMap<>();
    private static long endNanos;

    public static void start(int durationTicks) {
        SIGNALS.clear();
        endNanos = System.nanoTime() + Math.max(1, durationTicks) * 50_000_000L;
    }

    public static void receive(EyeHazardSignalsPayload payload) {
        if (System.nanoTime() >= endNanos) {
            return;
        }
        for (EyeHazardSignal signal : payload.signals()) {
            SIGNALS.put(signal.position().asLong(), signal);
        }
    }

    public static List<EyeHazardSignal> signals() {
        if (System.nanoTime() >= endNanos) {
            clear();
            return List.of();
        }
        return List.copyOf(SIGNALS.values());
    }

    public static void clear() {
        SIGNALS.clear();
        endNanos = 0L;
    }

    private ClientHorusHazards() {
    }
}
