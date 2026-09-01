package dev.alvar.echoespast.client;

import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.network.MedusaGazeVisualPayload;
import dev.alvar.echoespast.network.MedusaPetrifyPayload;
import dev.alvar.echoespast.relic.MedusaHeadItem;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public final class ClientMedusaVision {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier POST_EFFECT = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "medusa_gaze");
    private static final long PETRIFY_IN_NANOS = 360_000_000L;
    private static final long PETRIFY_OUT_NANOS = 480_000_000L;
    private static final Map<Integer, Petrification> PETRIFIED = new HashMap<>();

    private static int phase = -1;
    private static long phaseStartNanos;
    private static long phaseEndNanos;
    private static long channelStartNanos;
    private static long channelDurationNanos;
    private static float heldChannelProgress;
    private static long contactStartNanos;
    private static long contactEndNanos;
    private static boolean postEffectOwned;
    private static boolean failureReported;

    public static void receive(MedusaGazeVisualPayload payload) {
        if (localPlayerIsDead(Minecraft.getInstance())) {
            clearImmediately();
            return;
        }
        long now = System.nanoTime();
        int duration = Math.clamp(payload.durationTicks(), 1, 20 * 60);
        if (payload.phase() == MedusaGazeVisualPayload.START) {
            phase = MedusaGazeVisualPayload.START;
            phaseStartNanos = now;
            channelStartNanos = now;
            channelDurationNanos = Math.min(duration, 12) * 50_000_000L;
            phaseEndNanos = now + duration * 50_000_000L + 500_000_000L;
            heldChannelProgress = 0.0F;
        } else if (payload.phase() == MedusaGazeVisualPayload.IMPACT) {
            heldChannelProgress = 1.0F;
            phase = MedusaGazeVisualPayload.IMPACT;
            phaseStartNanos = now;
            phaseEndNanos = now + duration * 50_000_000L;
        } else if (payload.phase() == MedusaGazeVisualPayload.CANCEL) {
            heldChannelProgress = channelProgress(now);
            phase = MedusaGazeVisualPayload.CANCEL;
            phaseStartNanos = now;
            phaseEndNanos = now + duration * 50_000_000L;
        } else if (payload.phase() == MedusaGazeVisualPayload.CONTACT) {
            contactStartNanos = now;
            contactEndNanos = now + duration * 50_000_000L;
        }
    }

    public static void receive(MedusaPetrifyPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean localVictim = minecraft.player != null
                && payload.entityId() == minecraft.player.getId();
        if (payload.durationTicks() == 0 || (localVictim && localPlayerIsDead(minecraft))) {
            if (localVictim) {
                clearImmediately();
            } else {
                PETRIFIED.remove(payload.entityId());
            }
            return;
        }
        if (localVictim && payload.durationTicks() < 0) {
            // The living player never remains a statue; death clears the gaze.
            clearImmediately();
            return;
        }
        long now = System.nanoTime();
        boolean permanent = payload.durationTicks() < 0;
        int duration = Math.clamp(payload.durationTicks(), 1, 20 * 60);
        PETRIFIED.put(
                payload.entityId(),
                new Petrification(
                        now,
                        permanent ? Long.MAX_VALUE : now + duration * 50_000_000L,
                        permanent));
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clearImmediately();
            return;
        }
        if (localPlayerIsDead(minecraft)) {
            clearImmediately();
            return;
        }
        // Hotbar swaps stop the use on the server via stopUsingItem, but the client
        // changes the selected slot before that sync arrives. Fade the gaze locally
        // as soon as the held stack no longer matches the active use item.
        if (phase == MedusaGazeVisualPayload.START && !stillChannelingMedusa(minecraft)) {
            receive(new MedusaGazeVisualPayload(MedusaGazeVisualPayload.CANCEL, 7));
        }
        long now = System.nanoTime();
        Iterator<Petrification> iterator = PETRIFIED.values().iterator();
        while (iterator.hasNext()) {
            Petrification petrification = iterator.next();
            if (!petrification.permanent && now >= petrification.endNanos) {
                iterator.remove();
            }
        }
    }

    private static boolean stillChannelingMedusa(Minecraft minecraft) {
        var player = minecraft.player;
        if (player == null
                || !player.isUsingItem()
                || !MedusaHeadItem.isMedusaHead(player.getUseItem())) {
            return false;
        }
        return ItemStack.isSameItem(
                player.getItemInHand(player.getUsedItemHand()),
                player.getUseItem());
    }

    public static boolean hasPostEffectPriority() {
        return !localPlayerIsDead(Minecraft.getInstance())
                && visualActive(System.nanoTime())
                && EchoesConfig.POST_PROCESSING.getAsBoolean();
    }

    public static void renderFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        if (localPlayerIsDead(minecraft)) {
            clearImmediately();
            return;
        }
        long now = System.nanoTime();
        if (!visualActive(now)) {
            releasePostEffect(minecraft);
            phase = -1;
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
                phase = -1;
                return;
            }
        } else {
            postEffectOwned = true;
        }

        boolean gazeActive = gazeActive(now);
        float victimProgress = localPetrificationProgress();
        Frame frame = gazeActive
                ? frame(now)
                : new Frame(victimProgress * 0.58F, 1.0F, 0.0F, 0.0F);
        float elapsed = gazeActive
                ? (now - channelStartNanos) / 1_000_000_000.0F
                : localPetrificationAge();
        float echoDarkening = ClientEchoState.combinedShadowStrength(now);
        float horusStrength = ClientHorusVision.visualStrength(now);
        float grailStrength = ClientHolyGrailVision.localStrength(now);
        float grailRelease = ClientHolyGrailVision.localRelease(now);
        try {
            MedusaPostEffectUniforms.update(
                    minecraft,
                    POST_EFFECT,
                    frame.strength,
                    frame.channelProgress,
                    frame.impactProgress,
                    frame.cancelProgress,
                    elapsed,
                    contactProgress(now),
                    echoDarkening,
                    ClientEchoState.isSurfaceWaveActive(now),
                    horusStrength,
                    grailStrength,
                    grailRelease,
                    elapsed);
        } catch (RuntimeException exception) {
            reportFailure("update", exception);
            releasePostEffect(minecraft);
            phase = -1;
        }
    }

    public static void preparePostEffect() {
        if (!EchoesConfig.POST_PROCESSING.getAsBoolean()) {
            return;
        }
        try {
            MedusaPostEffectUniforms.prepare(Minecraft.getInstance(), POST_EFFECT);
        } catch (RuntimeException exception) {
            reportFailure("prepare", exception);
        }
    }

    public static boolean visualActive(long now) {
        return gazeActive(now) || localPetrificationProgress() > 0.0F;
    }

    public static boolean gazeActive(long now) {
        return phase >= 0 && now < phaseEndNanos;
    }

    public static float channelProgress(long now) {
        if (phase != MedusaGazeVisualPayload.START) {
            return heldChannelProgress;
        }
        return smooth((float) (now - channelStartNanos)
                / Math.max(1L, channelDurationNanos));
    }

    public static float impactProgress(long now) {
        if (phase != MedusaGazeVisualPayload.IMPACT) {
            return 0.0F;
        }
        return smooth((float) (now - phaseStartNanos)
                / Math.max(1L, phaseEndNanos - phaseStartNanos));
    }

    public static float contactProgress(long now) {
        if (now >= contactEndNanos || contactEndNanos <= contactStartNanos) {
            return 0.0F;
        }
        float progress = (float) (now - contactStartNanos)
                / (contactEndNanos - contactStartNanos);
        return (1.0F - smooth(progress)) * smooth(Math.min(1.0F, progress * 5.0F));
    }

    /**
     * Complete gaze phase used when another relic owns Minecraft's single
     * world post chain. Keeping the temporal channels prevents a composed
     * effect from degrading Medusa into a static colour grade.
     */
    public static Composite composite(long now) {
        if (localPlayerIsDead(Minecraft.getInstance())) {
            return Composite.NONE;
        }
        if (gazeActive(now)) {
            Frame frame = frame(now);
            return new Composite(
                    frame.strength(),
                    frame.channelProgress(),
                    frame.impactProgress(),
                    frame.cancelProgress(),
                    (now - channelStartNanos) / 1_000_000_000.0F);
        }
        float petrification = localPetrificationProgress();
        return petrification <= 0.0F
                ? Composite.NONE
                : new Composite(
                        petrification * 0.58F,
                        1.0F,
                        0.0F,
                        0.0F,
                        localPetrificationAge());
    }

    public static float petrificationProgress(int entityId) {
        return petrificationProgress(entityId, false);
    }

    public static float petrificationProgress(int entityId, boolean permanentEntity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && entityId == minecraft.player.getId()
                && localPlayerIsDead(minecraft)) {
            return 0.0F;
        }
        Petrification petrification = PETRIFIED.get(entityId);
        if (petrification == null) {
            return permanentEntity ? 1.0F : 0.0F;
        }
        long now = System.nanoTime();
        float enter = smooth((float) (now - petrification.startNanos) / PETRIFY_IN_NANOS);
        if (petrification.permanent || permanentEntity) {
            return enter;
        }
        float exit = smooth((float) (petrification.endNanos - now) / PETRIFY_OUT_NANOS);
        return Math.min(enter, exit);
    }

    public static float petrificationAge(int entityId) {
        Petrification petrification = PETRIFIED.get(entityId);
        if (petrification == null) {
            return 0.0F;
        }
        return (System.nanoTime() - petrification.startNanos) / 1_000_000_000.0F;
    }

    private static float localPetrificationProgress() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null
                ? 0.0F
                : petrificationProgress(minecraft.player.getId());
    }

    private static float localPetrificationAge() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null
                ? 0.0F
                : petrificationAge(minecraft.player.getId());
    }

    public static void clearImmediately() {
        releasePostEffect(Minecraft.getInstance());
        PETRIFIED.clear();
        phase = -1;
        phaseStartNanos = 0L;
        phaseEndNanos = 0L;
        channelStartNanos = 0L;
        channelDurationNanos = 0L;
        heldChannelProgress = 0.0F;
        contactStartNanos = 0L;
        contactEndNanos = 0L;
    }

    private static boolean localPlayerIsDead(Minecraft minecraft) {
        return minecraft.player != null
                && (!minecraft.player.isAlive() || minecraft.player.isDeadOrDying());
    }

    private static Frame frame(long now) {
        float channel = channelProgress(now);
        float impact = impactProgress(now);
        float cancel = phase == MedusaGazeVisualPayload.CANCEL
                ? smooth((float) (now - phaseStartNanos)
                        / Math.max(1L, phaseEndNanos - phaseStartNanos))
                : 0.0F;
        float strength;
        if (phase == MedusaGazeVisualPayload.START) {
            strength = smooth(Math.min(1.0F, channel * 2.8F));
        } else if (phase == MedusaGazeVisualPayload.IMPACT) {
            strength = 1.0F - smooth(Math.max(0.0F, (impact - 0.28F) / 0.72F));
        } else {
            strength = 1.0F - cancel;
        }
        return new Frame(strength, channel, impact, cancel);
    }

    private static void releasePostEffect(Minecraft minecraft) {
        if (minecraft.gameRenderer != null
                && POST_EFFECT.equals(minecraft.gameRenderer.currentPostEffect())) {
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
            LOGGER.error("Medusa gaze post effect failed during {}", operation, exception);
        }
    }

    private record Frame(
            float strength,
            float channelProgress,
            float impactProgress,
            float cancelProgress) {
    }

    public record Composite(
            float strength,
            float channelProgress,
            float impactProgress,
            float cancelProgress,
            float elapsedSeconds) {
        public static final Composite NONE = new Composite(
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F);
    }

    private record Petrification(long startNanos, long endNanos, boolean permanent) {
    }

    private ClientMedusaVision() {
    }
}
