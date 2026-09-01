package dev.alvar.echoespast.client;

import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.network.HolyGrailVisualPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

/**
 * Client clock for both the local post grade and world-space Grail geometry.
 * Short ritual phases use monotonic time; the aura uses synchronized game-time
 * attachments so it survives tracking changes and does not drift.
 */
public final class ClientHolyGrailVision {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier POST_EFFECT = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "holy_grail");
    private static final long RELEASE_NANOS = 1_150_000_000L;
    private static final long CANCEL_NANOS = 450_000_000L;
    private static final Map<Integer, Ritual> RITUALS = new HashMap<>();

    private static boolean postEffectOwned;
    private static boolean failureReported;

    public static void receive(HolyGrailVisualPayload payload) {
        long now = System.nanoTime();
        int duration = Math.clamp(payload.durationTicks(), 1, 20 * 60);
        Ritual previous = RITUALS.get(payload.entityId());
        if (payload.phase() == HolyGrailVisualPayload.START) {
            RITUALS.put(
                    payload.entityId(),
                    new Ritual(
                            HolyGrailVisualPayload.START,
                            now,
                            now + duration * 50_000_000L,
                            0.0F));
        } else if (payload.phase() == HolyGrailVisualPayload.RELEASE) {
            RITUALS.put(
                    payload.entityId(),
                    new Ritual(
                            HolyGrailVisualPayload.RELEASE,
                            now,
                            now + duration * 50_000_000L,
                            1.0F));
        } else if (payload.phase() == HolyGrailVisualPayload.CANCEL) {
            float held = previous == null
                    ? 0.35F
                    : visualFromEvent(previous, now).channel();
            RITUALS.put(
                    payload.entityId(),
                    new Ritual(
                            HolyGrailVisualPayload.CANCEL,
                            now,
                            now + CANCEL_NANOS,
                            held));
        } else if (payload.phase() == HolyGrailVisualPayload.RECHARGE) {
            RITUALS.put(
                    payload.entityId(),
                    new Ritual(
                            HolyGrailVisualPayload.RECHARGE,
                            now,
                            now + duration * 50_000_000L,
                            0.0F));
        }
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clearImmediately();
            return;
        }
        long now = System.nanoTime();
        Iterator<Map.Entry<Integer, Ritual>> iterator =
                RITUALS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Ritual> entry = iterator.next();
            if (now >= entry.getValue().endNanos()
                    || minecraft.level.getEntity(entry.getKey()) == null) {
                iterator.remove();
            }
        }
    }

    public static GrailVisual visualFor(
            LivingEntity entity,
            long nowNanos) {
        GrailVisual event = visualFromEvent(
                RITUALS.get(entity.getId()),
                nowNanos);
        float aura = auraStrength(entity);
        float strength = Math.max(
                event.strength(),
                aura * (0.18F
                        + 0.035F * (float) Math.sin(
                                entity.level().getGameTime() * 0.13F)));
        return new GrailVisual(
                Math.clamp(strength, 0.0F, 1.0F),
                event.channel(),
                event.release(),
                aura,
                event.recharge(),
                event.elapsedSeconds());
    }

    public static boolean hasPostEffectPriority() {
        return localVisual(System.nanoTime()).strength() > 0.001F
                && EchoesConfig.POST_PROCESSING.getAsBoolean();
    }

    public static float localStrength(long nowNanos) {
        return localVisual(nowNanos).strength();
    }

    public static float localRelease(long nowNanos) {
        return localVisual(nowNanos).release();
    }

    public static boolean isLocalAuraActive() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        Ritual ritual = RITUALS.get(minecraft.player.getId());
        if (ritual != null
                && ritual.phase() == HolyGrailVisualPayload.CANCEL
                && System.nanoTime() < ritual.endNanos()) {
            // Manual dismiss has already begun; the attachment tail is only
            // visual and must not keep intercepting use.
            return false;
        }
        long start = minecraft.player.getData(
                EchoesShowThePast.GRAIL_AURA_START.get());
        long until = minecraft.player.getData(
                EchoesShowThePast.GRAIL_AURA_UNTIL.get());
        return until > start
                && minecraft.player.level().getGameTime() < until;
    }

    public static void renderFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        GrailVisual visual = localVisual(System.nanoTime());
        if (visual.strength() <= 0.001F) {
            releasePostEffect(minecraft);
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
                return;
            }
        } else {
            postEffectOwned = true;
        }

        long now = System.nanoTime();
        try {
            HolyGrailPostEffectUniforms.update(
                    minecraft,
                    POST_EFFECT,
                    visual,
                    ClientEchoState.combinedShadowStrength(now),
                    ClientEchoState.isSurfaceWaveActive(now),
                    ClientHorusVision.visualStrength(now));
        } catch (RuntimeException exception) {
            reportFailure("update", exception);
            releasePostEffect(minecraft);
        }
    }

    public static void preparePostEffect() {
        if (!EchoesConfig.POST_PROCESSING.getAsBoolean()) {
            return;
        }
        try {
            HolyGrailPostEffectUniforms.prepare(
                    Minecraft.getInstance(),
                    POST_EFFECT);
        } catch (RuntimeException exception) {
            reportFailure("prepare", exception);
        }
    }

    public static void clearImmediately() {
        releasePostEffect(Minecraft.getInstance());
        RITUALS.clear();
    }

    private static GrailVisual localVisual(long nowNanos) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null
                ? GrailVisual.NONE
                : visualFor(minecraft.player, nowNanos);
    }

    private static float auraStrength(LivingEntity entity) {
        long start = entity.getData(
                EchoesShowThePast.GRAIL_AURA_START.get());
        long until = entity.getData(
                EchoesShowThePast.GRAIL_AURA_UNTIL.get());
        float now = entity.level().getGameTime();
        if (until <= start || now < start || now >= until) {
            return 0.0F;
        }
        float opening = smooth((now - start) / 14.0F);
        float closing = smooth((until - now) / 18.0F);
        return Math.min(opening, closing);
    }

    private static GrailVisual visualFromEvent(
            Ritual ritual,
            long now) {
        if (ritual == null || now >= ritual.endNanos()) {
            return GrailVisual.NONE;
        }
        float elapsed = (now - ritual.startNanos()) / 1_000_000_000.0F;
        float duration = Math.max(
                0.001F,
                (ritual.endNanos() - ritual.startNanos())
                        / 1_000_000_000.0F);
        float progress = Math.clamp(elapsed / duration, 0.0F, 1.0F);
        if (ritual.phase() == HolyGrailVisualPayload.START) {
            float channel = smooth(progress);
            return new GrailVisual(
                    smooth(Math.min(1.0F, channel * 2.2F)) * 0.72F,
                    channel,
                    0.0F,
                    0.0F,
                    0.0F,
                    elapsed);
        }
        if (ritual.phase() == HolyGrailVisualPayload.RELEASE) {
            float release = smooth(Math.min(
                    1.0F,
                    (now - ritual.startNanos()) / (float) RELEASE_NANOS));
            float envelope = 1.0F - smooth(Math.max(
                    0.0F,
                    (release - 0.32F) / 0.68F));
            return new GrailVisual(
                    0.28F + envelope * 0.72F,
                    1.0F,
                    release,
                    0.0F,
                    0.0F,
                    elapsed);
        }
        if (ritual.phase() == HolyGrailVisualPayload.CANCEL) {
            float fade = 1.0F - smooth(progress);
            return new GrailVisual(
                    ritual.heldStrength() * fade * 0.65F,
                    ritual.heldStrength(),
                    0.0F,
                    0.0F,
                    0.0F,
                    elapsed);
        }
        float rechargeEnvelope =
                smooth(Math.min(1.0F, progress * 4.0F))
                        * (1.0F - smooth(Math.max(
                                0.0F,
                                (progress - 0.55F) / 0.45F)));
        return new GrailVisual(
                rechargeEnvelope * 0.62F,
                0.0F,
                0.0F,
                0.0F,
                rechargeEnvelope,
                elapsed);
    }

    private static void releasePostEffect(Minecraft minecraft) {
        if (postEffectOwned
                && POST_EFFECT.equals(
                        minecraft.gameRenderer.currentPostEffect())) {
            minecraft.gameRenderer.clearPostEffect();
        }
        postEffectOwned = false;
    }

    private static float smooth(float value) {
        float clamped = Math.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static void reportFailure(
            String operation,
            RuntimeException exception) {
        if (!failureReported) {
            failureReported = true;
            LOGGER.error(
                    "Holy Grail post effect failed during {}",
                    operation,
                    exception);
        }
    }

    public record GrailVisual(
            float strength,
            float channel,
            float release,
            float aura,
            float recharge,
            float elapsedSeconds) {
        static final GrailVisual NONE =
                new GrailVisual(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

        public boolean active() {
            return strength > 0.001F
                    || channel > 0.001F
                    || release > 0.001F
                    || aura > 0.001F
                    || recharge > 0.001F;
        }
    }

    private record Ritual(
            int phase,
            long startNanos,
            long endNanos,
            float heldStrength) {
    }

    private ClientHolyGrailVision() {
    }
}
