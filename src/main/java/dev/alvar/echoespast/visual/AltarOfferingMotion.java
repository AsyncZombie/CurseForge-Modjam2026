package dev.alvar.echoespast.visual;

import dev.alvar.echoespast.cinematic.UnknownEnterCinematicMath;

/**
 * Seat and vanish curves for altar offerings. Side-agnostic so the boss
 * deposit beat can be tested without a client renderer.
 */
public final class AltarOfferingMotion {
    public static final float INTRO_SECONDS = 0.62F;
    public static final float OUTRO_SECONDS = 0.52F;
    public static final float INTRO_LIFT = -0.42F;
    public static final float OUTRO_LIFT = 0.34F;

    private AltarOfferingMotion() {
    }

    public static Pose intro(double seconds) {
        float progress = UnknownEnterCinematicMath.smootherstep(
                (float) (seconds / INTRO_SECONDS));
        return new Pose(progress, (1.0F - progress) * INTRO_LIFT, progress > 0.012F);
    }

    public static Pose outro(double seconds) {
        float progress = UnknownEnterCinematicMath.smootherstep(
                (float) (seconds / OUTRO_SECONDS));
        return new Pose(1.0F - progress, progress * OUTRO_LIFT, progress < 0.985F);
    }

    public static Pose settled() {
        return new Pose(1.0F, 0.0F, true);
    }

    public static Pose hidden() {
        return new Pose(0.0F, 0.0F, false);
    }

    public record Pose(float scale, float heightBias, boolean visible) {
    }
}
