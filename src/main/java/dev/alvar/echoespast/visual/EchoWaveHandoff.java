package dev.alvar.echoespast.visual;

/**
 * Chooses one visual carrier for the same physical crest. While the Echo post
 * chain is operational, depth reconstruction owns the complete radius; the
 * custom local pipelines remain available only as the no-post fallback. This
 * avoids both Iris pipeline overrides and a visible renderer handoff.
 */
public final class EchoWaveHandoff {
    public static double screenStart(double localRadius) {
        return 0.0;
    }

    public static double screenWeight(double front, double localRadius) {
        return 1.0;
    }

    public static double localWeight(double front, double localRadius) {
        return 0.0;
    }

    private EchoWaveHandoff() {
    }
}
