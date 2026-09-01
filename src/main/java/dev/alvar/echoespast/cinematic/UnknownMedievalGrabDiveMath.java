package dev.alvar.echoespast.cinematic;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative trajectory and grip geometry for the Medieval tower dive. */
public final class UnknownMedievalGrabDiveMath {
    /** Pull the owner visibly into the shield-side arm before committing. */
    public static final int GRAB_TICKS = 10;
    /** Two-tick crouch/readability beat after the grab closes. */
    public static final int LAUNCH_TICK = 12;
    /** Twenty-eight airborne ticks over the authored lower plaza. */
    public static final int IMPACT_TICK = 40;
    /** Hold the impact for dust/readability before the reconstruction starts. */
    public static final int TOTAL_TICKS = 48;
    public static final double ARC_HEIGHT = 10.0D;

    private UnknownMedievalGrabDiveMath() {
    }

    public static Vec3 travelDirection(Vec3 start, Vec3 landing) {
        Vec3 horizontal = new Vec3(landing.x - start.x, 0.0D, landing.z - start.z);
        return horizontal.lengthSqr() < 1.0E-8D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : horizontal.normalize();
    }

    public static Vec3 bossPosition(Vec3 start, Vec3 landing, int tick) {
        if (tick <= LAUNCH_TICK) {
            return start;
        }
        if (tick >= IMPACT_TICK) {
            return landing;
        }
        double progress = Mth.clamp(
                (tick - LAUNCH_TICK) / (double) (IMPACT_TICK - LAUNCH_TICK),
                0.0D,
                1.0D);
        Vec3 base = start.lerp(landing, progress);
        double arc = Math.sin(progress * Math.PI) * ARC_HEIGHT;
        return base.add(0.0D, arc, 0.0D);
    }

    public static Vec3 gripPosition(Vec3 bossFeet, Vec3 travelDirection) {
        Vec3 side = new Vec3(-travelDirection.z, 0.0D, travelDirection.x);
        return bossFeet
                .add(travelDirection.scale(0.42D))
                .add(side.scale(-0.24D))
                .add(0.0D, 0.46D, 0.0D);
    }

    public static Vec3 playerPosition(
            Vec3 playerStart,
            Vec3 bossFeet,
            Vec3 travelDirection,
            int tick) {
        Vec3 grip = gripPosition(bossFeet, travelDirection);
        if (tick >= GRAB_TICKS) {
            return grip;
        }
        float blend = smootherstep(tick / (float) GRAB_TICKS);
        return playerStart.lerp(grip, blend);
    }

    private static float smootherstep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * clamped
                * (clamped * (clamped * 6.0F - 15.0F) + 10.0F);
    }
}
