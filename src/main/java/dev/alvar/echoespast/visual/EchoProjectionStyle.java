package dev.alvar.echoespast.visual;

public final class EchoProjectionStyle {
    public static float presentTargetOpacity(EchoBlockChange.Kind change, float intensity) {
        float strength = Math.clamp(intensity, 0.0F, 1.0F);
        float target = switch (change) {
            case REPLACED -> 0.0F;
            case ADDED -> 0.26F;
            default -> 1.0F;
        };
        return 1.0F + (target - 1.0F) * strength;
    }

    public static float rememberedBaseOpacity(EchoBlockChange.Kind change, float intensity) {
        float base = switch (change) {
            case REPLACED -> 0.86F;
            case MISSING -> 0.80F;
            default -> 0.0F;
        };
        return Math.clamp(base * Math.max(0.0F, intensity), 0.0F, 0.92F);
    }

    public static float occludedOpacity(float visibleOpacity) {
        return 0.0F;
    }

    public static float planeGhostCoverage(float paintedCoverage) {
        return paintedCoverage < 0.5F ? 0.0F : 1.0F;
    }

    private EchoProjectionStyle() {
    }
}
