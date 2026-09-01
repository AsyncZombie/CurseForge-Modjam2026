package dev.alvar.echoespast.visual;

import net.minecraft.core.BlockPos;

/**
 * When the camera enters remembered hollow / historical-air volume, nearby
 * {@code MISSING} and {@code ADDED} ghosts yield so the player sees the shell
 * instead of a stack of back-lit translucent cubes. {@code REPLACED} cells keep
 * full opacity: solid volume still exists behind the projection.
 *
 * <p>Entering one cell of a 3×3 ghost slab fades that cell and its Chebyshev
 * neighbours (the 2×2 / 3×3 neighbourhood), then restores them with a short
 * fade-in when the camera leaves.</p>
 */
public final class EchoGhostOccupancy {
    /** Chebyshev radius that fully yields around the camera block. */
    public static final int NEIGHBORHOOD = 1;
    /** Seconds to travel between fully visible and fully cleared. */
    public static final float FADE_SECONDS = 0.55F;

    /**
     * {@code 0} clears the ghost at this cell; {@code 1} leaves its normal
     * projection opacity untouched.
     */
    public static float targetVisibility(BlockPos ghost, BlockPos cameraBlock) {
        int dx = Math.abs(ghost.getX() - cameraBlock.getX());
        int dy = Math.abs(ghost.getY() - cameraBlock.getY());
        int dz = Math.abs(ghost.getZ() - cameraBlock.getZ());
        int chebyshev = Math.max(dx, Math.max(dy, dz));
        return chebyshev <= NEIGHBORHOOD ? 0.0F : 1.0F;
    }

    public static float approach(float current, float target, float deltaSeconds) {
        if (Float.compare(current, target) == 0) {
            return target;
        }
        if (deltaSeconds <= 0.0F || FADE_SECONDS <= 0.0F) {
            return target;
        }
        // Exponential ease keeps the occupancy dissolve continuous instead of a
        // hard step when the frame hitch jumps the linear budget.
        float blend = 1.0F - (float) Math.exp(-deltaSeconds / (FADE_SECONDS * 0.33F));
        blend = Math.clamp(blend, 0.0F, 1.0F);
        return current + (target - current) * blend;
    }

    /**
     * How strongly a shared interior face should draw as its neighbour fades.
     * {@code 0} while the neighbour is solid; {@code 1} once it has cleared.
     */
    public static float sharedFaceReveal(float neighborVisibility) {
        float cleared = 1.0F - Math.clamp(neighborVisibility, 0.0F, 1.0F);
        return cleared * cleared * (3.0F - 2.0F * cleared);
    }

    /**
     * Shared faces stay culled only while the neighbour is still fully solid.
     */
    public static boolean occludesSharedFace(float neighborVisibility) {
        return sharedFaceReveal(neighborVisibility) <= 0.005F;
    }

    /**
     * Added present blocks and replaced historical blocks represent stable
     * solid volume. Only missing historical shells may yield around the
     * camera.
     */
    public static boolean isFadeImmune(EchoBlockChange.Kind change) {
        return change == EchoBlockChange.Kind.ADDED
                || change == EchoBlockChange.Kind.REPLACED;
    }

    private EchoGhostOccupancy() {
    }
}
