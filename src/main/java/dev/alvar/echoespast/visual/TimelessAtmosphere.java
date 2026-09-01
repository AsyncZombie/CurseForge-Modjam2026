package dev.alvar.echoespast.visual;

import dev.alvar.echoespast.network.UnknownBossBarPayload;

/**
 * Pure presentation state for the Timeless Void. Keeping the phase mapping
 * outside client rendering makes the palette deterministic and testable.
 */
public final class TimelessAtmosphere {
    public static Profile target(boolean encounterActive, byte era, byte phase) {
        if (!encounterActive || phase == UnknownBossBarPayload.PHASE_IDLE) {
            return hub();
        }
        if (phase == UnknownBossBarPayload.PHASE_DEAD) {
            return new Profile(
                    0.34F, 0.37F, 0.52F,
                    0.018F, 0.020F, 0.042F,
                    0.54F, 0.18F,
                    0.34F, 0.24F, 0.42F,
                    122.0F);
        }

        boolean ruins = phase == UnknownBossBarPayload.PHASE_RUINS;
        boolean transition = phase == UnknownBossBarPayload.PHASE_RECONSTRUCTING;
        boolean voidBeat = phase == UnknownBossBarPayload.PHASE_VOID_VULNERABLE
                || phase == UnknownBossBarPayload.PHASE_CINEMATIC_WALK;
        if (voidBeat || era == UnknownBossBarPayload.ERA_VOID) {
            return new Profile(
                    0.39F, 0.43F, 0.66F,
                    0.014F, 0.017F, 0.041F,
                    0.66F, 0.44F,
                    0.58F, 0.40F, 0.54F,
                    110.0F);
        }

        float skyR;
        float skyG;
        float skyB;
        float fogR;
        float fogG;
        float fogB;
        float gold;
        float eraVeil;
        switch (era) {
            case UnknownBossBarPayload.ERA_EGYPTIAN -> {
                skyR = ruins ? 0.64F : 0.58F;
                skyG = ruins ? 0.45F : 0.51F;
                skyB = ruins ? 0.39F : 0.55F;
                fogR = ruins ? 0.072F : 0.052F;
                fogG = ruins ? 0.043F : 0.044F;
                fogB = ruins ? 0.032F : 0.058F;
                gold = ruins ? 1.0F : 0.88F;
                eraVeil = ruins ? 0.88F : 0.64F;
            }
            case UnknownBossBarPayload.ERA_MEDIEVAL -> {
                skyR = ruins ? 0.49F : 0.46F;
                skyG = ruins ? 0.47F : 0.52F;
                skyB = ruins ? 0.60F : 0.70F;
                fogR = ruins ? 0.043F : 0.032F;
                fogG = ruins ? 0.040F : 0.039F;
                fogB = ruins ? 0.052F : 0.068F;
                gold = ruins ? 0.75F : 0.61F;
                eraVeil = ruins ? 0.76F : 0.54F;
            }
            default -> {
                // Greek memory is intentionally white/gold, never cyan.
                skyR = ruins ? 0.57F : 0.52F;
                skyG = ruins ? 0.53F : 0.57F;
                skyB = ruins ? 0.62F : 0.74F;
                fogR = ruins ? 0.049F : 0.035F;
                fogG = ruins ? 0.042F : 0.043F;
                fogB = ruins ? 0.052F : 0.073F;
                gold = ruins ? 0.92F : 0.76F;
                eraVeil = ruins ? 0.82F : 0.58F;
            }
        }
        float instability = transition ? 0.70F : ruins ? 1.0F : 0.16F;
        float veil = transition ? 0.92F : eraVeil;
        float horizonGlow = transition ? 0.58F : ruins ? 0.52F : 0.38F;
        float starBrightness = transition ? 0.72F : ruins ? 0.82F : 0.64F;
        float horizon = ruins ? 104.0F : 126.0F;
        return new Profile(
                skyR, skyG, skyB,
                fogR, fogG, fogB,
                gold, instability,
                veil, horizonGlow, starBrightness,
                horizon);
    }

    private static Profile hub() {
        return new Profile(
                0.38F, 0.43F, 0.68F,
                0.013F, 0.016F, 0.040F,
                0.62F, 0.24F,
                0.52F, 0.34F, 0.56F,
                116.0F);
    }

    public record Profile(
            float skyR,
            float skyG,
            float skyB,
            float fogR,
            float fogG,
            float fogB,
            float gold,
            float instability,
            float veilStrength,
            float horizonGlow,
            float starBrightness,
            float horizonDistance) {
        public Profile lerp(Profile target, float amount) {
            return new Profile(
                    mix(skyR, target.skyR, amount),
                    mix(skyG, target.skyG, amount),
                    mix(skyB, target.skyB, amount),
                    mix(fogR, target.fogR, amount),
                    mix(fogG, target.fogG, amount),
                    mix(fogB, target.fogB, amount),
                    mix(gold, target.gold, amount),
                    mix(instability, target.instability, amount),
                    mix(veilStrength, target.veilStrength, amount),
                    mix(horizonGlow, target.horizonGlow, amount),
                    mix(starBrightness, target.starBrightness, amount),
                    mix(horizonDistance, target.horizonDistance, amount));
        }

        private static float mix(float start, float end, float amount) {
            return start + (end - start) * amount;
        }
    }

    private TimelessAtmosphere() {
    }
}
