package dev.alvar.echoespast.visual;

/**
 * Testable contract shared by the screen grade and the wave marker.
 */
public final class EchoScreenGrade {
    public static final float WAVE_MARKER_BASE = 8.0F / 255.0F;
    public static final float WAVE_MARKER_SCALE = 64.0F / 255.0F;

    public static float sceneMultiplier(float effectAmount, float exposure) {
        return (1.0F - Math.clamp(effectAmount, 0.0F, 1.0F)) * exposure;
    }

    public static float waveMask(float red, float green, float blue, float alpha) {
        float markerMaximum = WAVE_MARKER_BASE + WAVE_MARKER_SCALE;
        if (alpha < WAVE_MARKER_BASE - 0.006F || alpha >= markerMaximum + 0.006F) {
            return 0.0F;
        }
        return Math.clamp(
                (alpha - WAVE_MARKER_BASE) / WAVE_MARKER_SCALE,
                0.0F,
                1.0F);
    }

    public static float encodedWaveAlpha(float waveOpacity) {
        return WAVE_MARKER_BASE
                + Math.clamp(waveOpacity, 0.0F, 1.0F) * WAVE_MARKER_SCALE;
    }

    private EchoScreenGrade() {
    }
}
