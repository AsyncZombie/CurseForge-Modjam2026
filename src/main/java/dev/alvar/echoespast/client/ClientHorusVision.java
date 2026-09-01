package dev.alvar.echoespast.client;

import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.network.EyeOfHorusVisualPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public final class ClientHorusVision {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier POST_EFFECT = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "horus_vision");
    private static final long OPEN_NANOS = 650_000_000L;
    private static final long CLOSE_NANOS = 700_000_000L;

    private static long startNanos;
    private static long endNanos;
    private static boolean active;
    private static boolean cancelling;
    private static float cancellationHeldStrength;
    private static boolean postEffectOwned;
    private static boolean failureReported;

    public static void receive(EyeOfHorusVisualPayload payload) {
        long now = System.nanoTime();
        if (payload.durationTicks() <= 0) {
            if (!active) {
                clearImmediately();
                return;
            }
            cancellationHeldStrength = visualStrength(now);
            startNanos = now;
            endNanos = now + CLOSE_NANOS;
            cancelling = true;
            ClientHorusHazards.clear();
            return;
        }
        int durationTicks = Math.clamp(payload.durationTicks(), 1, 20 * 60);
        startNanos = now;
        endNanos = startNanos + durationTicks * 50_000_000L;
        active = true;
        cancelling = false;
        cancellationHeldStrength = 0.0F;
        ClientHorusHazards.start(durationTicks);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        if (active
                && (minecraft.level == null
                        || minecraft.player == null
                        || now >= endNanos)) {
            clearImmediately();
        }
    }

    public static boolean hasPostEffectPriority() {
        return visualStrength(System.nanoTime()) > 0.0F
                && EchoesConfig.POST_PROCESSING.getAsBoolean();
    }

    public static boolean isActive() {
        // The close fade is presentation only. Treating it as active would
        // re-send dismiss packets and swallow the next legitimate use.
        return active && !cancelling && System.nanoTime() < endNanos;
    }

    public static float visualStrength(long now) {
        if (!active || now >= endNanos || endNanos <= startNanos) {
            return 0.0F;
        }
        if (cancelling) {
            return cancellationHeldStrength
                    * (1.0F - smooth((float) (now - startNanos) / CLOSE_NANOS));
        }
        float opening = smooth((float) (now - startNanos) / OPEN_NANOS);
        float closing = smooth((float) (endNanos - now) / CLOSE_NANOS);
        return Math.min(opening, closing);
    }

    public static void renderFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        if (!active || now >= endNanos) {
            clearImmediately();
            return;
        }
        if (!EchoesConfig.POST_PROCESSING.getAsBoolean()) {
            releasePostEffect(minecraft);
            return;
        }

        Identifier current = minecraft.gameRenderer.currentPostEffect();
        if (!POST_EFFECT.equals(current)) {
            try {
                minecraft.gameRenderer.setPostEffect(POST_EFFECT);
                postEffectOwned = true;
            } catch (RuntimeException exception) {
                reportFailure("activate", exception);
                active = false;
                return;
            }
        } else {
            postEffectOwned = true;
        }

        float opening = smooth((float) (now - startNanos) / OPEN_NANOS);
        float closing = smooth((float) (endNanos - now) / CLOSE_NANOS);
        float strength = visualStrength(now);
        float elapsedSeconds = (now - startNanos) / 1_000_000_000.0F;
        try {
            HorusPostEffectUniforms.update(
                    minecraft,
                    POST_EFFECT,
                    strength,
                    opening,
                    closing,
                    elapsedSeconds);
        } catch (RuntimeException exception) {
            reportFailure("update", exception);
            clearImmediately();
        }
    }

    public static void preparePostEffect() {
        if (!EchoesConfig.POST_PROCESSING.getAsBoolean()) {
            return;
        }
        try {
            HorusPostEffectUniforms.prepare(Minecraft.getInstance(), POST_EFFECT);
        } catch (RuntimeException exception) {
            reportFailure("prepare", exception);
        }
    }

    public static void clearImmediately() {
        Minecraft minecraft = Minecraft.getInstance();
        releasePostEffect(minecraft);
        active = false;
        cancelling = false;
        cancellationHeldStrength = 0.0F;
        ClientHorusHazards.clear();
        postEffectOwned = false;
        startNanos = 0L;
        endNanos = 0L;
    }

    private static void releasePostEffect(Minecraft minecraft) {
        if (postEffectOwned && POST_EFFECT.equals(minecraft.gameRenderer.currentPostEffect())) {
            minecraft.gameRenderer.clearPostEffect();
        }
        postEffectOwned = false;
    }

    private static float smooth(float value) {
        float clamped = Math.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static void reportFailure(String operation, RuntimeException exception) {
        if (!failureReported) {
            failureReported = true;
            LOGGER.error("Eye of Horus post effect failed during {}", operation, exception);
        }
    }

    private ClientHorusVision() {
    }
}
