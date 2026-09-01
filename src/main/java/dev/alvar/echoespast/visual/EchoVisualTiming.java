package dev.alvar.echoespast.visual;

public final class EchoVisualTiming {
    public static final float MAX_SCREEN_DARKENING = 2.0F;
    public static final double OUTBOUND_START_SECONDS = 0.12;
    public static final double OUTBOUND_END_SECONDS = 1.94;
    public static final double DARKEN_START_SECONDS = 0.0;
    public static final double DARKEN_END_SECONDS = 1.0;
    public static final double RETURN_START_SECONDS = 2.30;
    public static final double RECOVERY_START_SECONDS = 3.15;
    public static final double EFFECT_END_SECONDS = 4.30;
    /**
     * Compatibility alias for the current render pipeline. In the 0.1 pulse,
     * the returning crest and the complete effect end at the same instant.
     */
    public static final double RETURN_END_SECONDS = EFFECT_END_SECONDS;

    public static double shadowEnvelope(double elapsedSeconds) {
        if (elapsedSeconds <= 0.0 || elapsedSeconds >= EFFECT_END_SECONDS) {
            return 0.0;
        }
        if (elapsedSeconds <= DARKEN_START_SECONDS) {
            return 0.0;
        }
        if (elapsedSeconds < DARKEN_END_SECONDS) {
            double entrance = Math.clamp(
                    (elapsedSeconds - DARKEN_START_SECONDS)
                            / (DARKEN_END_SECONDS - DARKEN_START_SECONDS),
                    0.0,
                    1.0);
            return smootherStep(entrance);
        }
        if (elapsedSeconds <= RECOVERY_START_SECONDS) {
            return 1.0;
        }
        double recovery = Math.clamp(
                (elapsedSeconds - RECOVERY_START_SECONDS)
                        / (EFFECT_END_SECONDS - RECOVERY_START_SECONDS),
                0.0,
                1.0);
        return 1.0 - smootherStep(recovery);
    }

    public static float configuredShadowStrength(
            double elapsedSeconds,
            float fade,
            float screenDarkening) {
        return Math.clamp(screenDarkening / MAX_SCREEN_DARKENING, 0.0F, 1.0F)
                * (float) shadowEnvelope(elapsedSeconds)
                * Math.clamp(fade, 0.0F, 1.0F);
    }

    /** Dynamic variant used by the Past Echo's per-activation clock. */
    public static float configuredShadowStrength(
            double elapsedSeconds,
            float fade,
            float screenDarkening,
            EchoPulseTiming timing) {
        return Math.clamp(screenDarkening / MAX_SCREEN_DARKENING, 0.0F, 1.0F)
                * (float) shadowEnvelope(elapsedSeconds, timing)
                * Math.clamp(fade, 0.0F, 1.0F);
    }

    public static double shadowEnvelope(
            double elapsedSeconds,
            EchoPulseTiming timing) {
        if (elapsedSeconds <= 0.0
                || elapsedSeconds >= timing.effectEndSeconds()) {
            return 0.0;
        }
        if (elapsedSeconds < DARKEN_END_SECONDS) {
            return smootherStep(Math.clamp(
                    (elapsedSeconds - DARKEN_START_SECONDS)
                            / (DARKEN_END_SECONDS - DARKEN_START_SECONDS),
                    0.0,
                    1.0));
        }
        if (elapsedSeconds <= timing.recoveryStartSeconds()) {
            return 1.0;
        }
        double recovery = Math.clamp(
                (elapsedSeconds - timing.recoveryStartSeconds())
                        / (timing.effectEndSeconds()
                                - timing.recoveryStartSeconds()),
                0.0,
                1.0);
        return 1.0 - smootherStep(recovery);
    }

    public static float presentOccluderOpacity(float reveal, float transparentOpacity) {
        float amount = Math.clamp((reveal - 0.12F) / 0.88F, 0.0F, 1.0F);
        amount = (float) smootherStep(amount);
        return 1.0F + (transparentOpacity - 1.0F) * amount;
    }

    public static int presentOccluderMinimumLight(float reveal) {
        return 0;
    }

    public static float presentOccluderReveal(double elapsed, double distance, double maximumRadius) {
        return returnCrossing(elapsed, distance, maximumRadius, 0.0, 0.72);
    }

    public static float presentOccluderReveal(
            double elapsed,
            double distance,
            EchoPulseTiming timing) {
        return returnCrossing(elapsed, distance, timing, 0.0, 0.72);
    }

    /**
     * The remembered material follows just behind the returning crest. Keeping this
     * separate from the present-block dissolve avoids two full textures appearing
     * at the same instant.
     */
    public static float rememberedReveal(double elapsed, double distance, double maximumRadius) {
        return returnCrossing(elapsed, distance, maximumRadius, 0.12, 0.88);
    }

    public static float rememberedReveal(
            double elapsed,
            double distance,
            EchoPulseTiming timing) {
        return returnCrossing(elapsed, distance, timing, 0.12, 0.88);
    }

    private static float returnCrossing(
            double elapsed,
            double distance,
            double maximumRadius,
            double delayBehindFront,
            double transitionWidth) {
        if (elapsed < RETURN_START_SECONDS) {
            return 0.0F;
        }
        if (elapsed >= EFFECT_END_SECONDS) {
            return 1.0F;
        }
        double front = returnRadius(elapsed, maximumRadius);
        double distanceBehindFront = distance - front;
        double crossed = Math.clamp(
                (distanceBehindFront - delayBehindFront) / transitionWidth,
                0.0,
                1.0);
        return (float) smootherStep(crossed);
    }

    private static float returnCrossing(
            double elapsed,
            double distance,
            EchoPulseTiming timing,
            double delayBehindFront,
            double transitionWidth) {
        if (elapsed < timing.returnStartSeconds()) {
            return 0.0F;
        }
        if (elapsed >= timing.effectEndSeconds()) {
            return 1.0F;
        }
        double distanceBehindFront = distance - timing.returnRadius(elapsed);
        double crossed = Math.clamp(
                (distanceBehindFront - delayBehindFront) / transitionWidth,
                0.0,
                1.0);
        return (float) smootherStep(crossed);
    }

    public static float surfaceReveal(
            double elapsed,
            double distance,
            double maximumRadius,
            float materialDelay,
            float materialWidth,
            double delayBehindFront,
            double transitionWidth) {
        double arrival = surfaceReturnArrival(
                distance,
                maximumRadius,
                materialDelay) + delayBehindFront;
        if (elapsed <= arrival) {
            return 0.0F;
        }
        if (elapsed >= EFFECT_END_SECONDS) {
            return 1.0F;
        }
        double width = Math.max(0.16, transitionWidth * Math.max(0.65F, materialWidth));
        double crossed = Math.clamp(
                (elapsed - arrival) / width,
                0.0,
                1.0);
        return (float) smootherStep(crossed);
    }

    public static float surfaceReveal(
            double elapsed,
            double distance,
            double maximumRadius,
            float materialDelay,
            float materialWidth,
            double delayBehindResponse) {
        return surfaceReveal(
                elapsed,
                distance,
                maximumRadius,
                materialDelay,
                materialWidth,
                delayBehindResponse,
                0.24);
    }

    public static double surfaceReturnArrival(
            double distance,
            double maximumRadius,
            float materialDelay) {
        double normalized = Math.clamp(
                distance / Math.max(1.0, maximumRadius),
                0.0,
                1.0);
        return RETURN_START_SECONDS
                + (1.0 - smoothStep(normalized))
                        * (RETURN_END_SECONDS - RETURN_START_SECONDS)
                + Math.max(0.0F, materialDelay);
    }

    public static float surfaceResponseEnvelope(
            double elapsed,
            double distance,
            double maximumRadius,
            float materialDelay,
            float materialWidth) {
        double arrival = surfaceReturnArrival(distance, maximumRadius, materialDelay);
        double age = elapsed - arrival;
        if (age < -0.10 || age > 0.72 * materialWidth) {
            return 0.0F;
        }
        double attack = smoothStep(Math.clamp((age + 0.10) / 0.14, 0.0, 1.0));
        double decay = 1.0 - smootherStep(Math.clamp(
                age / Math.max(0.20, 0.72 * materialWidth),
                0.0,
                1.0));
        return (float) (attack * decay);
    }

    public static double returnRadius(double elapsed, double maximumRadius) {
        double progress = Math.clamp(
                (elapsed - RETURN_START_SECONDS)
                        / (EFFECT_END_SECONDS - RETURN_START_SECONDS),
                0.0,
                1.0);
        return Math.max(1.0, maximumRadius) * (1.0 - smoothStep(progress));
    }

    /**
     * Dissolves the last outbound crest while it sits on the perimeter, so the
     * hold is a fade rather than a frozen ring. The inward attack lives on
     * {@link EchoPulseTiming#crestEnvelope(double)}.
     */
    public static float turnaroundEnvelope(double elapsed) {
        if (elapsed < OUTBOUND_END_SECONDS || elapsed >= RETURN_START_SECONDS) {
            return 0.0F;
        }
        double progress = Math.clamp(
                (elapsed - OUTBOUND_END_SECONDS)
                        / (RETURN_START_SECONDS - OUTBOUND_END_SECONDS),
                0.0,
                1.0);
        return (float) (1.0 - smootherStep(progress));
    }

    public static double outboundRadius(double elapsed, double maximumRadius) {
        double progress = Math.clamp(
                (elapsed - OUTBOUND_START_SECONDS)
                        / (OUTBOUND_END_SECONDS - OUTBOUND_START_SECONDS),
                0.0,
                1.0);
        return Math.max(1.0, maximumRadius) * smoothStep(progress);
    }

    /**
     * Seven item frames mirror the world pulse: 0→6 outbound, a short hold at
     * the perimeter, then 6→0 as the echo returns.
     */
    public static float itemAnimationFrame(double elapsed) {
        if (elapsed < OUTBOUND_START_SECONDS || elapsed >= EFFECT_END_SECONDS) {
            return 0.0F;
        }
        if (elapsed < OUTBOUND_END_SECONDS) {
            double progress = Math.clamp(
                    (elapsed - OUTBOUND_START_SECONDS)
                            / (OUTBOUND_END_SECONDS - OUTBOUND_START_SECONDS),
                    0.0,
                    1.0);
            return (float) (6.0 * smoothStep(progress));
        }
        if (elapsed < RETURN_START_SECONDS) {
            return 6.0F;
        }
        double returnProgress = Math.clamp(
                (elapsed - RETURN_START_SECONDS)
                        / (EFFECT_END_SECONDS - RETURN_START_SECONDS),
                0.0,
                1.0);
        return (float) (6.0 * (1.0 - smoothStep(returnProgress)));
    }

    public static float itemAnimationFrame(
            double elapsed,
            EchoPulseTiming timing) {
        if (elapsed < timing.outboundStartSeconds()
                || elapsed >= timing.effectEndSeconds()) {
            return 0.0F;
        }
        if (elapsed < timing.outboundEndSeconds()) {
            return (float) (6.0 * timing.outboundRadius(elapsed)
                    / timing.radius());
        }
        if (elapsed < timing.returnStartSeconds()) {
            return 6.0F;
        }
        return (float) (6.0 * timing.returnRadius(elapsed)
                / timing.radius());
    }

    public static double smoothStep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double smootherStep(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    private EchoVisualTiming() {
    }
}
