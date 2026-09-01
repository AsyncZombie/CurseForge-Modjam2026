package dev.alvar.echoespast.client;

import dev.alvar.echoespast.boss.UnknownEraSequence;
import dev.alvar.echoespast.network.UnknownBossBarPayload;
import java.util.UUID;
import net.minecraft.util.Mth;

/**
 * Client mirror of the Unknown cinematic boss bar. Theme and epoch seals come
 * from the payload; health width still follows the lerped vanilla boss event.
 */
public final class ClientUnknownBossBar {
    private static boolean active;
    private static UUID bossId = new UUID(0L, 0L);
    private static byte era = UnknownBossBarPayload.ERA_VOID;
    private static byte phase = UnknownBossBarPayload.PHASE_IDLE;
    private static int thresholdIndex;
    private static long themeChangeMillis;
    private static byte previousTheme = -1;

    private ClientUnknownBossBar() {
    }

    public static void receive(UnknownBossBarPayload payload) {
        active = payload.active();
        if (!active) {
            bossId = new UUID(0L, 0L);
            era = UnknownBossBarPayload.ERA_VOID;
            phase = UnknownBossBarPayload.PHASE_IDLE;
            thresholdIndex = 0;
            previousTheme = -1;
            return;
        }
        bossId = payload.bossId();
        era = payload.era();
        phase = payload.phase();
        thresholdIndex = payload.thresholdIndex();
        byte theme = themeIndex();
        if (theme != previousTheme) {
            previousTheme = theme;
            themeChangeMillis = System.currentTimeMillis();
        }
    }

    public static void clear() {
        receive(UnknownBossBarPayload.inactive());
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean matches(UUID id) {
        return active && bossId.equals(id);
    }

    public static byte era() {
        return era;
    }

    public static byte phase() {
        return phase;
    }

    public static int thresholdIndex() {
        return thresholdIndex;
    }

    /** Atlas theme row selected by the canonical era definition. */
    public static byte themeIndex() {
        if (phase == UnknownBossBarPayload.PHASE_RECONSTRUCTING) {
            return 7;
        }
        if (era == UnknownBossBarPayload.ERA_VOID
                || phase == UnknownBossBarPayload.PHASE_VOID_VULNERABLE
                || phase == UnknownBossBarPayload.PHASE_CINEMATIC_WALK
                || phase == UnknownBossBarPayload.PHASE_IDLE) {
            return 6;
        }
        boolean ruins = phase == UnknownBossBarPayload.PHASE_RUINS;
        return UnknownEraSequence.forBossBarEra(era)
                .map(definition -> (byte) definition.atlasStageRow(ruins))
                .orElse((byte) 6);
    }

    /**
     * Seal index 0..5 for the six combat epochs, or {@code -1} when none is
     * currently active (void / reconstruction / cinematic).
     */
    public static int activeSealIndex() {
        if (phase != UnknownBossBarPayload.PHASE_PAST
                && phase != UnknownBossBarPayload.PHASE_RUINS) {
            return -1;
        }
        boolean ruins = phase == UnknownBossBarPayload.PHASE_RUINS;
        return UnknownEraSequence.forBossBarEra(era)
                .map(definition -> definition.threshold(ruins))
                .orElse(-1);
    }

    public static float themeFlash(float partialTick) {
        if (themeChangeMillis <= 0L) {
            return 0.0F;
        }
        float age = (System.currentTimeMillis() - themeChangeMillis) / 1000.0F + partialTick / 20.0F;
        return Mth.clamp(1.0F - age / 0.55F, 0.0F, 1.0F);
    }
}
