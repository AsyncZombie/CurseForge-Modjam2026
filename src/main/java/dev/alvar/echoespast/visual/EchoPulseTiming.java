package dev.alvar.echoespast.visual;

/**
 * Immutable clock for one Past Echo activation. The fronts travel at the
 * exact same number of blocks per second regardless of memory size; a larger
 * remembered place therefore takes longer instead of making the crest race
 * across it.
 */
public record EchoPulseTiming(double radius) {
    public static final double BLOCKS_PER_SECOND = 12.0;
    public static final double OUTBOUND_START_SECONDS = 0.12;
    public static final double PERIMETER_HOLD_SECONDS = 0.36;
    /**
     * The inward ghost crest must regain strength almost immediately after the
     * perimeter dissolve, so the handoff reads as one pulse reversing rather
     * than a second wave being born.
     */
    public static final double RETURN_ATTACK_SECONDS = 0.055;
    private static final double RECOVERY_RETURN_FRACTION = 0.425;

    public EchoPulseTiming {
        radius = Math.max(1.0, radius);
    }

    public static EchoPulseTiming forRadius(double radius) {
        return new EchoPulseTiming(radius);
    }

    public double outboundStartSeconds() {
        return OUTBOUND_START_SECONDS;
    }

    public double travelSeconds() {
        return radius / BLOCKS_PER_SECOND;
    }

    public double outboundEndSeconds() {
        return outboundStartSeconds() + travelSeconds();
    }

    public double returnStartSeconds() {
        return outboundEndSeconds() + PERIMETER_HOLD_SECONDS;
    }

    public double effectEndSeconds() {
        return returnStartSeconds() + travelSeconds();
    }

    public double recoveryStartSeconds() {
        return returnStartSeconds()
                + travelSeconds() * RECOVERY_RETURN_FRACTION;
    }

    public boolean isOutbound(double elapsedSeconds) {
        return elapsedSeconds >= outboundStartSeconds()
                && elapsedSeconds < outboundEndSeconds();
    }

    public boolean isPerimeterHold(double elapsedSeconds) {
        return elapsedSeconds >= outboundEndSeconds()
                && elapsedSeconds < returnStartSeconds();
    }

    public boolean isReturning(double elapsedSeconds) {
        return elapsedSeconds >= returnStartSeconds()
                && elapsedSeconds < effectEndSeconds();
    }

    /**
     * Intensity of the surface crest only. Full during the outbound travel,
     * dissolves across the perimeter hold, then snaps back as the inward
     * front begins. Geometry and pulse shape stay untouched.
     */
    public float crestEnvelope(double elapsedSeconds) {
        if (isOutbound(elapsedSeconds)) {
            return 1.0F;
        }
        if (isPerimeterHold(elapsedSeconds)) {
            double progress = Math.clamp(
                    (elapsedSeconds - outboundEndSeconds())
                            / PERIMETER_HOLD_SECONDS,
                    0.0,
                    1.0);
            return (float) (1.0 - smootherStep(progress));
        }
        if (isReturning(elapsedSeconds)) {
            double age = elapsedSeconds - returnStartSeconds();
            return (float) smootherStep(Math.clamp(
                    age / RETURN_ATTACK_SECONDS,
                    0.0,
                    1.0));
        }
        return 0.0F;
    }

    private static double smootherStep(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    public double outboundRadius(double elapsedSeconds) {
        return Math.clamp(
                (elapsedSeconds - outboundStartSeconds())
                        * BLOCKS_PER_SECOND,
                0.0,
                radius);
    }

    public double returnRadius(double elapsedSeconds) {
        return Math.clamp(
                radius - (elapsedSeconds - returnStartSeconds())
                        * BLOCKS_PER_SECOND,
                0.0,
                radius);
    }
}
