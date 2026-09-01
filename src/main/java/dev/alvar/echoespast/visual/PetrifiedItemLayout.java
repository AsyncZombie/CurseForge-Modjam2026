package dev.alvar.echoespast.visual;

/**
 * Pure layout math shared by the client renderer and dedicated-server tests.
 */
public final class PetrifiedItemLayout {
    private static final float TARGET_EXTENT = 0.82F;
    private static final float WIDTH_MARGIN = 1.32F;
    private static final float HEIGHT_MARGIN = 1.06F;
    private static final float MAX_SCALE = 1.5F;

    public static float fitScale(float width, float height) {
        float safeWidth = finitePositive(width) ? width : 1.0F;
        float safeHeight = finitePositive(height) ? height : 1.0F;
        float requiredExtent = Math.max(
                safeWidth * WIDTH_MARGIN,
                safeHeight * HEIGHT_MARGIN);
        return Math.min(MAX_SCALE, TARGET_EXTENT / requiredExtent);
    }

    public static float baseY(float height, float scale) {
        float safeHeight = finitePositive(height) ? height : 1.0F;
        float safeScale = finitePositive(scale) ? scale : 1.0F;
        return 0.5F - safeHeight * safeScale * 0.5F;
    }

    private static boolean finitePositive(float value) {
        return Float.isFinite(value) && value > 1.0E-4F;
    }

    private PetrifiedItemLayout() {
    }
}
