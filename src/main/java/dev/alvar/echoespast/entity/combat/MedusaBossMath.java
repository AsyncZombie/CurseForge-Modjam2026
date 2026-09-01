package dev.alvar.echoespast.entity.combat;

import net.minecraft.world.phys.Vec3;

/**
 * Pure gaze and strike geometry for the Medusa boss. Players petrify by
 * looking at her face; other creatures petrify when her own gaze lands on
 * them. A carved pumpkin still wards the look.
 */
public final class MedusaBossMath {
    public static final double GAZE_RANGE = 32.0D;
    public static final double GAZE_LOOK_DOT = 0.94D;
    public static final double GAZE_FACE_DOT = 0.22D;
    public static final double GAZE_BEAM_DOT = 0.62D;
    public static final int GAZE_LOCK_TICKS = 40;
    public static final int MOB_GAZE_LOCK_TICKS = 8;
    public static final int MOB_GAZE_GAIN = 2;
    public static final int GAZE_DECAY_PER_TICK = 2;
    public static final int GAZE_CHANNEL_GAIN = 2;

    public static final int SNAKE_ATTACK_TICKS = 26;
    public static final int SNAKE_HIT_TICK = 11;
    public static final double SNAKE_REACH = 3.6D;
    public static final float SNAKE_DAMAGE = 8.0F;
    public static final int SNAKE_POISON_TICKS = 120;
    public static final int SNAKE_POISON_AMPLIFIER = 1;
    public static final int SNAKE_RECOVERY_TICKS = 18;

    public static final int PETRIFY_ATTACK_MIN_TICKS = 36;

    private static final double EPSILON = 1.0E-8D;

    private MedusaBossMath() {
    }

    /**
     * True when the viewer is looking at Medusa's face: their look ray hits
     * her eyes, she is facing them, and they are inside range.
     */
    public static boolean isLookingAtFace(
            Vec3 viewerEye,
            Vec3 viewerLook,
            Vec3 medusaEye,
            Vec3 medusaLook,
            double range) {
        Vec3 toMedusa = medusaEye.subtract(viewerEye);
        double distanceSqr = toMedusa.lengthSqr();
        if (distanceSqr <= EPSILON || distanceSqr > range * range) {
            return false;
        }
        Vec3 towardMedusa = toMedusa.normalize();
        if (viewerLook.normalize().dot(towardMedusa) < GAZE_LOOK_DOT) {
            return false;
        }
        Vec3 towardViewer = viewerEye.subtract(medusaEye);
        if (towardViewer.lengthSqr() <= EPSILON) {
            return false;
        }
        return medusaLook.normalize().dot(towardViewer.normalize()) >= GAZE_FACE_DOT;
    }

    /**
     * True when Medusa's own look ray is on the target. Mobs do not have to
     * stare back; her face is the weapon.
     */
    public static boolean isInGazeBeam(
            Vec3 medusaEye,
            Vec3 medusaLook,
            Vec3 targetEye,
            double range) {
        Vec3 toTarget = targetEye.subtract(medusaEye);
        double distanceSqr = toTarget.lengthSqr();
        if (distanceSqr <= EPSILON || distanceSqr > range * range) {
            return false;
        }
        return medusaLook.normalize().dot(toTarget.normalize()) >= GAZE_BEAM_DOT;
    }

    public static int nextGazeLock(int current, boolean looking, boolean petrifyChannel) {
        if (looking) {
            int gain = petrifyChannel ? GAZE_CHANNEL_GAIN : 1;
            return Math.min(GAZE_LOCK_TICKS, Math.max(0, current) + gain);
        }
        return Math.max(0, current - GAZE_DECAY_PER_TICK);
    }

    public static boolean gazeCompletes(int lockTicks) {
        return lockTicks >= GAZE_LOCK_TICKS;
    }

    public static int nextMobGazeLock(int current, boolean looking) {
        if (looking) {
            return Math.min(MOB_GAZE_LOCK_TICKS, Math.max(0, current) + MOB_GAZE_GAIN);
        }
        return Math.max(0, current - GAZE_DECAY_PER_TICK);
    }

    public static boolean mobGazeCompletes(int lockTicks) {
        return lockTicks >= MOB_GAZE_LOCK_TICKS;
    }

    public static boolean isSnakeHitTick(int elapsedTicks) {
        return elapsedTicks == SNAKE_HIT_TICK;
    }

    public static boolean snakeStrikeReaches(
            Vec3 origin,
            Vec3 look,
            Vec3 target,
            double reach) {
        Vec3 offset = target.subtract(origin);
        double distanceSqr = offset.lengthSqr();
        if (distanceSqr <= EPSILON || distanceSqr > reach * reach) {
            return false;
        }
        return look.normalize().dot(offset.normalize()) >= 0.55D;
    }
}
