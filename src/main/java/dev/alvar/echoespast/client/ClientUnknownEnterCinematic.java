package dev.alvar.echoespast.client;

import dev.alvar.echoespast.cinematic.UnknownEnterCinematicMath;
import dev.alvar.echoespast.mixin.client.ClientInputAccessor;
import dev.alvar.echoespast.network.UnknownEnterCinematicPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client lens for Unknown cinematics. Positions are critically damped so the
 * shot never inherits pathfinding jitter from the boss, and look/FOV share the
 * same clock so a cut is impossible.
 */
public final class ClientUnknownEnterCinematic {
    private static boolean active;
    private static boolean outro;
    private static int bossId;
    private static BlockPos altarOrigin = BlockPos.ZERO;
    private static byte mode = UnknownEnterCinematicMath.MODE_APPROACH;
    private static int depositStep = -1;

    private static boolean initialized;
    private static Vec3 cameraPos = Vec3.ZERO;
    private static float yaw;
    private static float pitch;
    private static float roll;
    private static float fov = UnknownEnterCinematicMath.approachFov();
    private static double lastGameTime = Double.NaN;
    private static double introSeconds;
    private static double outroSeconds;
    private static double punchSeconds = 100.0D;
    private static float frozenYaw;
    private static float frozenPitch;
    private static Frame frame;

    private ClientUnknownEnterCinematic() {
    }

    public static void receive(UnknownEnterCinematicPayload payload) {
        if (!payload.active()) {
            if (active && initialized) {
                outro = true;
                outroSeconds = 0.0D;
            } else {
                clear();
            }
            active = false;
            return;
        }
        boolean wasActive = active;
        int previousStep = depositStep;
        byte previousMode = mode;
        active = true;
        outro = false;
        outroSeconds = 0.0D;
        bossId = payload.bossId();
        altarOrigin = payload.altarOrigin();
        mode = payload.mode();
        depositStep = payload.depositStep();
        if (!wasActive) {
            initialized = false;
            introSeconds = 0.0D;
            punchSeconds = 100.0D;
            lastGameTime = Double.NaN;
            captureFrozenLook();
        } else if (mode != previousMode) {
            introSeconds = 0.0D;
            roll = 0.0F;
        } else if (depositStep > previousStep && previousStep >= 0) {
            punchSeconds = 0.0D;
        }
    }

    public static void clear() {
        active = false;
        outro = false;
        initialized = false;
        bossId = 0;
        altarOrigin = BlockPos.ZERO;
        mode = UnknownEnterCinematicMath.MODE_APPROACH;
        depositStep = -1;
        lastGameTime = Double.NaN;
        introSeconds = 0.0D;
        outroSeconds = 0.0D;
        punchSeconds = 100.0D;
        roll = 0.0F;
        frame = null;
    }

    public static boolean isControlling() {
        return active || outro;
    }

    public static boolean hidesHudHands() {
        return isControlling();
    }

    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        Frame evaluated = evaluate(event.getCamera(), (float) event.getPartialTick());
        if (evaluated == null) {
            return;
        }
        event.setYaw(evaluated.yaw);
        event.setPitch(evaluated.pitch);
        event.setRoll(evaluated.roll);
    }

    public static void onFov(ViewportEvent.ComputeFov event) {
        Frame evaluated = frame;
        if (evaluated == null || !isControlling()) {
            return;
        }
        event.setFOV(evaluated.fov);
    }

    public static Frame overrideShot(Camera camera, float partialTick) {
        return evaluate(camera, partialTick);
    }

    public static void freezeInput(MovementInputUpdateEvent event) {
        if (!isControlling()) {
            return;
        }
        var input = event.getInput();
        input.keyPresses = Input.EMPTY;
        ((ClientInputAccessor) input).echoesShowThePast$setMoveVector(Vec2.ZERO);
    }

    public static void hideHands(RenderHandEvent event) {
        if (hidesHudHands()) {
            event.setCanceled(true);
        }
    }

    public static void tick() {
        if (!isControlling()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setSprinting(false);
        player.setYRot(frozenYaw);
        player.setXRot(frozenPitch);
        player.yRotO = frozenYaw;
        player.xRotO = frozenPitch;
        player.yHeadRot = frozenYaw;
        player.yHeadRotO = frozenYaw;
        player.yBodyRot = frozenYaw;
        player.yBodyRotO = frozenYaw;
    }

    private static Frame evaluate(Camera camera, float partialTick) {
        if (!isControlling()) {
            frame = null;
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return null;
        }
        double gameTime = minecraft.level.getGameTime() + partialTick;
        double dt = Double.isNaN(lastGameTime)
                ? 1.0D / 20.0D
                : Mth.clamp(gameTime - lastGameTime, 0.0D, 0.12D);
        lastGameTime = gameTime;

        Vec3 playerEye = new Vec3(
                Mth.lerp(partialTick, player.xo, player.getX()),
                Mth.lerp(partialTick, player.yo, player.getY()) + player.getEyeHeight(),
                Mth.lerp(partialTick, player.zo, player.getZ()));
        if (!initialized) {
            cameraPos = playerEye;
            yaw = player.getViewYRot(partialTick);
            pitch = player.getViewXRot(partialTick);
            roll = 0.0F;
            fov = (float) minecraft.options.fov().get();
            initialized = true;
            captureFrozenLook();
        }
        Vec3 altar = UnknownEnterCinematicMath.altarFocus(altarOrigin);
        Entity boss = minecraft.level.getEntity(bossId);
        Vec3 bossFeet = boss == null
                ? altar.add(8.0D, -1.0D, 0.0D)
                : new Vec3(
                        Mth.lerp(partialTick, boss.xo, boss.getX()),
                        Mth.lerp(partialTick, boss.yo, boss.getY()),
                        Mth.lerp(partialTick, boss.zo, boss.getZ()));

        boolean depositing = mode == UnknownEnterCinematicMath.MODE_DEPOSIT;
        boolean era = UnknownEnterCinematicMath.isEraMode(mode);
        boolean shieldBreak = UnknownEnterCinematicMath.isShieldBreakMode(mode);
        boolean execution = UnknownEnterCinematicMath.isExecutionMode(mode);
        boolean grabDive = UnknownEnterCinematicMath.isGrabDiveMode(mode);
        boolean rising = mode == UnknownEnterCinematicMath.MODE_ERA_RISE;
        Vec3 targetPos;
        Vec3 targetLook;
        float targetFov;
        float targetRoll = 0.0F;
        if (grabDive) {
            targetPos = UnknownEnterCinematicMath.grabDiveCamera(
                    bossFeet, altar, introSeconds);
            targetLook = UnknownEnterCinematicMath.grabDiveLook(
                    bossFeet, altar, introSeconds);
            targetFov = UnknownEnterCinematicMath.grabDiveFov(introSeconds);
            targetRoll = UnknownEnterCinematicMath.grabDiveRoll(introSeconds);
        } else if (execution) {
            targetPos = UnknownEnterCinematicMath.executionCamera(
                    bossFeet, playerEye, introSeconds);
            targetLook = UnknownEnterCinematicMath.executionLook(bossFeet, introSeconds);
            targetFov = UnknownEnterCinematicMath.executionFov(introSeconds);
        } else if (shieldBreak) {
            targetPos = UnknownEnterCinematicMath.shieldBreakCamera(
                    bossFeet, playerEye, introSeconds);
            targetLook = UnknownEnterCinematicMath.shieldBreakLook(bossFeet, introSeconds);
            targetFov = UnknownEnterCinematicMath.shieldBreakFov();
        } else if (era) {
            targetPos = UnknownEnterCinematicMath.eraCamera(
                    bossFeet, altar, playerEye, introSeconds, rising);
            targetLook = UnknownEnterCinematicMath.eraLook(
                    bossFeet, altar, introSeconds, rising);
            targetFov = UnknownEnterCinematicMath.eraFov(rising);
        } else if (depositing) {
            targetPos = UnknownEnterCinematicMath.depositCamera(
                    bossFeet, altar, depositStep, introSeconds);
            targetLook = UnknownEnterCinematicMath.depositLook(bossFeet, altar, depositStep);
            targetFov = UnknownEnterCinematicMath.depositFov(depositStep);
        } else {
            targetPos = UnknownEnterCinematicMath.approachCamera(bossFeet, altar, playerEye);
            targetLook = UnknownEnterCinematicMath.approachLook(bossFeet, altar);
            targetFov = UnknownEnterCinematicMath.approachFov();
        }

        if (active) {
            introSeconds += dt;
            punchSeconds += dt;
            float introBlend = UnknownEnterCinematicMath.smootherstep(
                    (float) (introSeconds / UnknownEnterCinematicMath.INTRO_SECONDS));
            double moveOmega = execution
                    ? UnknownEnterCinematicMath.executionFollowOmega()
                    : (grabDive
                            ? 5.8D
                            : (shieldBreak
                            ? 2.85D
                            : (era
                                    ? UnknownEnterCinematicMath.eraFollowOmega(introBlend)
                                    : UnknownEnterCinematicMath.followOmega(depositing, introBlend))));
            double lookOmega = execution
                    ? UnknownEnterCinematicMath.executionLookOmega()
                    : (grabDive
                            ? 6.4D
                            : (shieldBreak
                            ? 4.2D
                            : (era
                                    ? UnknownEnterCinematicMath.eraLookOmega()
                                    : UnknownEnterCinematicMath.lookOmega(depositing))));
            cameraPos = UnknownEnterCinematicMath.damp(cameraPos, targetPos, moveOmega, dt);
            if (!era && !shieldBreak && !execution && !grabDive) {
                cameraPos = cameraPos.add(
                        UnknownEnterCinematicMath.punchOffset(cameraPos, altar, punchSeconds));
            }
            yaw = UnknownEnterCinematicMath.dampAngle(
                    yaw,
                    UnknownEnterCinematicMath.yawToward(cameraPos, targetLook),
                    lookOmega,
                    dt);
            pitch = UnknownEnterCinematicMath.dampAngle(
                    pitch,
                    UnknownEnterCinematicMath.pitchToward(cameraPos, targetLook),
                    lookOmega,
                    dt);
            roll = UnknownEnterCinematicMath.dampAngle(
                    roll,
                    targetRoll,
                    grabDive ? 8.0D : 5.0D,
                    dt);
            fov = (float) UnknownEnterCinematicMath.damp(fov, targetFov, 2.2D, dt);
            if (!era && !shieldBreak && !execution && !grabDive) {
                fov += UnknownEnterCinematicMath.punchFov(punchSeconds);
            }
        } else {
            outroSeconds += dt;
            float blend = UnknownEnterCinematicMath.smootherstep(
                    (float) (outroSeconds / UnknownEnterCinematicMath.OUTRO_SECONDS));
            cameraPos = cameraPos.lerp(playerEye, blend);
            yaw = rotLerp(yaw, frozenYaw, blend);
            pitch = rotLerp(pitch, frozenPitch, blend);
            roll = Mth.lerp(blend, roll, 0.0F);
            fov = Mth.lerp(blend, fov, (float) minecraft.options.fov().get());
            if (blend >= 0.999F) {
                clear();
                return null;
            }
        }

        frame = new Frame(cameraPos, yaw, pitch, roll, fov);
        return frame;
    }

    private static void captureFrozenLook() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        frozenYaw = player.getYRot();
        frozenPitch = player.getXRot();
    }

    private static float rotLerp(float from, float to, float t) {
        return from + Mth.wrapDegrees(to - from) * t;
    }

    public record Frame(Vec3 position, float yaw, float pitch, float roll, float fov) {
    }
}
