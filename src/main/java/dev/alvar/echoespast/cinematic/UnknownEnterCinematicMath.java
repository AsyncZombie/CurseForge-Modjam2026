package dev.alvar.echoespast.cinematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Shot geometry for Unknown cinematics. Kept side-agnostic so the camera can
 * be tested without a client, and so the server can face the player toward
 * the same altar the lens will later occupy.
 */
public final class UnknownEnterCinematicMath {
    public static final byte MODE_APPROACH = 0;
    public static final byte MODE_DEPOSIT = 1;
    public static final byte MODE_ERA_RISE = 2;
    public static final byte MODE_ERA_FALL = 3;
    public static final byte MODE_SHIELD_BREAK = 4;
    public static final byte MODE_EXECUTION = 5;
    public static final byte MODE_GRAB_DIVE = 6;

    public static final float INTRO_SECONDS = 2.15F;
    public static final float OUTRO_SECONDS = 1.55F;

    private UnknownEnterCinematicMath() {
    }

    /** World-space focus of the levitating offerings above the 2×2 altar. */
    public static Vec3 altarFocus(BlockPos origin) {
        return new Vec3(
                origin.getX() + 0.0D,
                origin.getY() + 1.62D,
                origin.getZ() + 1.0D);
    }

    public static Vec3 bossFocus(Vec3 bossFeet) {
        return bossFeet.add(0.0D, 1.38D, 0.0D);
    }

    public static float yawToward(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) (Mth.atan2(-dx, dz) * (180.0D / Math.PI));
    }

    public static float pitchToward(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) (-(Mth.atan2(dy, horizontal) * (180.0D / Math.PI)));
    }

    public static float smoothstep(float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    /** Quintic smoothstep — zero first and second derivatives at the ends. */
    public static float smootherstep(float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        return clamped * clamped * clamped * (clamped * (clamped * 6.0F - 15.0F) + 10.0F);
    }

    public static double damp(double current, double target, double omega, double dt) {
        double clampedDt = Math.max(0.0D, dt);
        double blend = 1.0D - Math.exp(-omega * clampedDt);
        return current + (target - current) * blend;
    }

    public static Vec3 damp(Vec3 current, Vec3 target, double omega, double dt) {
        return new Vec3(
                damp(current.x, target.x, omega, dt),
                damp(current.y, target.y, omega, dt),
                damp(current.z, target.z, omega, dt));
    }

    public static float dampAngle(float current, float target, double omega, double dt) {
        return (float) damp(current, current + Mth.wrapDegrees(target - current), omega, dt);
    }

    /**
     * Follow rig: south-east of the silhouette, altar kept in the left of
     * frame so the walk reads as a pilgrimage rather than a chase.
     */
    public static Vec3 approachCamera(Vec3 bossFeet, Vec3 altar, Vec3 audience) {
        Vec3 focus = bossFocus(bossFeet).lerp(altar, 0.16D);
        Vec3 along = horizontal(altar.subtract(bossFeet));
        if (along.lengthSqr() < 1.0E-6D) {
            along = new Vec3(-1.0D, 0.0D, 0.0D);
        }
        Vec3 towardAudience = horizontal(audience.subtract(bossFeet));
        if (towardAudience.lengthSqr() < 1.0E-6D) {
            towardAudience = new Vec3(0.0D, 0.0D, -1.0D);
        }
        Vec3 side = towardAudience.subtract(along.scale(towardAudience.dot(along)));
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(-along.z, 0.0D, along.x);
        }
        side = side.normalize();
        return focus
                .subtract(along.scale(6.35D))
                .add(side.scale(3.55D))
                .add(0.0D, 1.72D, 0.0D);
    }

    public static Vec3 approachLook(Vec3 bossFeet, Vec3 altar) {
        return bossFocus(bossFeet).lerp(altar, 0.22D);
    }

    /**
     * Ritual rig: slow orbit around the altar, tightening as each fragment
     * seats, always keeping both the silhouette and the offerings in frame.
     */
    public static Vec3 depositCamera(Vec3 bossFeet, Vec3 altar, int depositStep, double seconds) {
        int step = Math.clamp(depositStep, 0, 7);
        double orbit = 0.42D + step * 0.11D + seconds * 0.055D;
        double radius = 5.45D - step * 0.16D;
        Vec3 pivot = altar.lerp(bossFocus(bossFeet), 0.34D);
        return pivot.add(
                Math.cos(orbit) * radius,
                1.55D + step * 0.05D,
                Math.sin(orbit) * radius - 0.35D);
    }

    public static Vec3 depositLook(Vec3 bossFeet, Vec3 altar, int depositStep) {
        int step = Math.clamp(depositStep, 0, 7);
        double towardAltar = step >= 6 ? 0.78D : 0.48D + step * 0.04D;
        return bossFocus(bossFeet).lerp(altar, towardAltar).add(0.0D, 0.08D, 0.0D);
    }

    public static float approachFov() {
        return 58.0F;
    }

    public static float depositFov(int depositStep) {
        int step = Math.clamp(depositStep, 0, 7);
        return 51.0F - step * 0.7F;
    }

    public static float punchFov(double secondsSinceSeat) {
        double t = Math.max(0.0D, secondsSinceSeat);
        return (float) (-2.15D * Math.exp(-5.4D * t) * Math.cos(t * 7.5D));
    }

    public static Vec3 punchOffset(Vec3 camera, Vec3 altar, double secondsSinceSeat) {
        double t = Math.max(0.0D, secondsSinceSeat);
        double strength = 0.11D * Math.exp(-6.0D * t);
        Vec3 toward = altar.subtract(camera);
        if (toward.lengthSqr() < 1.0E-6D) {
            return Vec3.ZERO;
        }
        return toward.normalize().scale(strength);
    }

    public static double followOmega(boolean depositing, float introBlend) {
        double settled = depositing ? 1.45D : 2.35D;
        return Mth.lerp(introBlend, 1.05D, settled);
    }

    public static double lookOmega(boolean depositing) {
        return depositing ? 2.6D : 3.4D;
    }

    public static boolean isEraMode(byte mode) {
        return mode == MODE_ERA_RISE || mode == MODE_ERA_FALL;
    }

    public static boolean isShieldBreakMode(byte mode) {
        return mode == MODE_SHIELD_BREAK;
    }

    public static boolean isExecutionMode(byte mode) {
        return mode == MODE_EXECUTION;
    }

    public static boolean isGrabDiveMode(byte mode) {
        return mode == MODE_GRAB_DIVE;
    }

    /** Fast lateral pursuit shot that keeps the seized player and tower drop readable. */
    public static Vec3 grabDiveCamera(
            Vec3 bossFeet,
            Vec3 altar,
            double seconds) {
        Vec3 focus = bossFocus(bossFeet);
        Vec3 travel = horizontal(altar.subtract(bossFeet));
        if (travel.lengthSqr() < 1.0E-6D) {
            travel = new Vec3(-1.0D, 0.0D, 0.0D);
        }
        Vec3 side = new Vec3(-travel.z, 0.0D, travel.x);
        float launch = smootherstep((float) ((seconds - 0.50D) / 0.30D));
        float impact = smootherstep((float) ((seconds - 1.92D) / 0.22D));
        double behind = Mth.lerp(launch, 3.65D, 4.65D);
        behind = Mth.lerp(impact, behind, 3.25D);
        double lateral = Mth.lerp(launch, 5.25D, 6.75D);
        lateral = Mth.lerp(impact, lateral, 5.15D);
        double height = Mth.lerp(launch, 2.35D, 3.65D);
        height = Mth.lerp(impact, height, 2.05D);
        return focus
                .subtract(travel.scale(behind))
                .add(side.scale(lateral))
                .add(0.0D, height, 0.0D);
    }

    public static Vec3 grabDiveLook(Vec3 bossFeet, Vec3 altar, double seconds) {
        Vec3 travel = horizontal(altar.subtract(bossFeet));
        if (travel.lengthSqr() < 1.0E-6D) {
            travel = new Vec3(-1.0D, 0.0D, 0.0D);
        }
        float impact = smootherstep((float) ((seconds - 1.92D) / 0.22D));
        return bossFocus(bossFeet)
                .add(travel.scale(Mth.lerp(impact, 0.42D, 0.08D)))
                .add(0.0D, Mth.lerp(impact, 0.12D, -0.36D), 0.0D);
    }

    public static float grabDiveFov(double seconds) {
        float launch = smootherstep((float) ((seconds - 0.50D) / 0.35D));
        float impact = smootherstep((float) ((seconds - 1.92D) / 0.22D));
        return Mth.lerp(impact, Mth.lerp(launch, 54.0F, 64.0F), 49.0F);
    }

    public static float grabDiveRoll(double seconds) {
        double airborne = Mth.clamp((seconds - 0.60D) / 1.35D, 0.0D, 1.0D);
        double impactTime = Math.max(0.0D, seconds - 1.98D);
        double banking = -4.5D * Math.sin(airborne * Math.PI);
        double impactShake = 3.2D * Math.exp(-8.0D * impactTime)
                * Math.sin(impactTime * 34.0D);
        return (float) (banking + impactShake);
    }

    /** Close three-quarter shot that keeps the shield arm and sword in frame. */
    public static Vec3 shieldBreakCamera(
            Vec3 bossFeet,
            Vec3 audience,
            double seconds) {
        Vec3 focus = bossFocus(bossFeet);
        Vec3 towardAudience = horizontal(audience.subtract(bossFeet));
        if (towardAudience.lengthSqr() < 1.0E-6D) {
            towardAudience = new Vec3(0.0D, 0.0D, -1.0D);
        }
        Vec3 side = new Vec3(-towardAudience.z, 0.0D, towardAudience.x);
        float settle = smootherstep((float) (Math.min(seconds, 1.4D) / 1.4D));
        return focus
                .add(towardAudience.scale(Mth.lerp(settle, 4.25D, 3.55D)))
                .add(side.scale(Mth.lerp(settle, 1.45D, 0.92D)))
                .add(0.0D, Mth.lerp(settle, 1.05D, 0.62D), 0.0D);
    }

    public static Vec3 shieldBreakLook(Vec3 bossFeet, double seconds) {
        double drop = smootherstep((float) (Math.min(seconds, 1.2D) / 1.2D)) * 0.34D;
        return bossFocus(bossFeet).add(0.0D, -drop, 0.0D);
    }

    public static float shieldBreakFov() {
        return 50.0F;
    }

    /**
     * Four-beat execution lens: establish the standing silhouette, descend
     * with the stagger, hold close while the Unknown looks back at the owner,
     * then widen and arc aside so the final forward collapse stays in frame.
     */
    public static Vec3 executionCamera(Vec3 bossFeet, Vec3 audience, double seconds) {
        Vec3 focus = bossFocus(bossFeet);
        Vec3 towardAudience = horizontal(audience.subtract(bossFeet));
        if (towardAudience.lengthSqr() < 1.0E-6D) {
            towardAudience = new Vec3(0.0D, 0.0D, -1.0D);
        }
        Vec3 side = new Vec3(-towardAudience.z, 0.0D, towardAudience.x);

        float recoil = executionPhase(seconds, 0.0D, 0.70D);
        float descent = executionPhase(seconds, 0.70D, 1.60D);
        float defiance = executionPhase(seconds, 2.30D, 1.60D);
        float collapse = executionPhase(seconds, 4.15D, 0.85D);

        double distance = Mth.lerp(recoil, 5.15D, 4.70D);
        distance = Mth.lerp(descent, distance, 3.65D);
        distance = Mth.lerp(defiance, distance, 3.15D);
        distance = Mth.lerp(collapse, distance, 4.05D);

        double lateral = Mth.lerp(recoil, 1.55D, 1.20D);
        lateral = Mth.lerp(descent, lateral, 0.72D);
        lateral = Mth.lerp(defiance, lateral, 0.42D);
        lateral = Mth.lerp(collapse, lateral, 1.05D);

        double height = Mth.lerp(recoil, 1.35D, 1.18D);
        height = Mth.lerp(descent, height, 0.62D);
        height = Mth.lerp(defiance, height, 0.50D);
        height = Mth.lerp(collapse, height, 0.38D);

        return focus
                .add(towardAudience.scale(distance))
                .add(side.scale(lateral))
                .add(0.0D, height, 0.0D);
    }

    public static Vec3 executionLook(Vec3 bossFeet, double seconds) {
        float recoil = executionPhase(seconds, 0.0D, 0.70D);
        float descent = executionPhase(seconds, 0.70D, 1.60D);
        float defiance = executionPhase(seconds, 2.30D, 1.60D);
        float collapse = executionPhase(seconds, 4.15D, 0.85D);

        double height = Mth.lerp(recoil, 0.08D, 0.03D);
        height = Mth.lerp(descent, height, -0.38D);
        height = Mth.lerp(defiance, height, -0.28D);
        height = Mth.lerp(collapse, height, -0.52D);
        return bossFocus(bossFeet).add(0.0D, height, 0.0D);
    }

    public static float executionFov(double seconds) {
        float descent = executionPhase(seconds, 0.70D, 1.60D);
        float defiance = executionPhase(seconds, 2.30D, 1.60D);
        float collapse = executionPhase(seconds, 4.15D, 0.85D);
        float fov = Mth.lerp(descent, 52.0F, 47.0F);
        fov = Mth.lerp(defiance, fov, 44.0F);
        return Mth.lerp(collapse, fov, 50.0F);
    }

    public static double executionFollowOmega() {
        return 2.20D;
    }

    public static double executionLookOmega() {
        return 3.55D;
    }

    private static float executionPhase(double seconds, double start, double duration) {
        return smootherstep((float) ((seconds - start) / duration));
    }

    /** Center of the complete authored volume, biased slightly below mid-height. */
    public static Vec3 eraArenaFocus(BlockPos arenaOrigin, Vec3i arenaSize) {
        double sizeX = Math.max(1, arenaSize.getX());
        double sizeY = Math.max(1, arenaSize.getY());
        double sizeZ = Math.max(1, arenaSize.getZ());
        return new Vec3(
                arenaOrigin.getX() + sizeX * 0.5D,
                arenaOrigin.getY() + sizeY * 0.43D,
                arenaOrigin.getZ() + sizeZ * 0.5D);
    }

    /**
     * Full-arena aerial establishing shot. It reaches the overview inside the
     * shortest reconstruction instead of spending the whole transition near
     * the player's eye line while already aiming downward.
     */
    public static Vec3 eraCamera(
            BlockPos arenaOrigin,
            Vec3i arenaSize,
            double seconds,
            boolean rising) {
        Vec3 focus = eraArenaFocus(arenaOrigin, arenaSize);
        double halfX = Math.max(1.0D, arenaSize.getX() * 0.5D);
        double halfZ = Math.max(1.0D, arenaSize.getZ() * 0.5D);
        double horizontalRadius = Math.sqrt(halfX * halfX + halfZ * halfZ);
        float establish = smootherstep((float) (seconds / 0.90D));

        Vec3 overviewSide = horizontal(new Vec3(-1.0D, 0.0D, -0.52D));
        double arcDegrees = Mth.lerp(establish, rising ? -3.5D : 3.5D, rising ? 3.5D : -2.0D);
        overviewSide = rotateHorizontal(overviewSide, Math.toRadians(arcDegrees));

        double finalDistance = horizontalRadius * (rising ? 1.32D : 1.24D);
        double distance = Mth.lerp(establish, finalDistance * 0.84D, finalDistance);
        double arenaTop = arenaOrigin.getY() + Math.max(1, arenaSize.getY());
        double height = Mth.lerp(
                establish,
                arenaTop + (rising ? 9.0D : 8.0D),
                arenaTop + (rising ? 17.0D : 15.0D));
        return new Vec3(
                focus.x + overviewSide.x * distance,
                height,
                focus.z + overviewSide.z * distance);
    }

    /** Always aims through the arena volume, never at the altar floor. */
    public static Vec3 eraLook(
            BlockPos arenaOrigin,
            Vec3i arenaSize,
            double seconds,
            boolean rising) {
        Vec3 focus = eraArenaFocus(arenaOrigin, arenaSize);
        float settle = smootherstep((float) (seconds / 0.90D));
        double lift = Mth.lerp(settle, rising ? 1.8D : 1.2D, rising ? 0.8D : 0.45D);
        return focus.add(0.0D, lift, 0.0D);
    }

    public static float eraFov(boolean rising) {
        return rising ? 68.0F : 65.0F;
    }

    public static double eraFollowOmega(float introBlend) {
        return Mth.lerp(introBlend, 4.35D, 5.40D);
    }

    public static double eraLookOmega() {
        return 6.20D;
    }

    private static Vec3 rotateHorizontal(Vec3 vector, double radians) {
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Vec3(
                vector.x * cosine - vector.z * sine,
                0.0D,
                vector.x * sine + vector.z * cosine);
    }

    private static Vec3 horizontal(Vec3 vector) {
        Vec3 flat = new Vec3(vector.x, 0.0D, vector.z);
        double length = flat.length();
        if (length < 1.0E-6D) {
            return Vec3.ZERO;
        }
        return flat.scale(1.0D / length);
    }
}
