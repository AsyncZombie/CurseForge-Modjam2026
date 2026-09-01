package dev.alvar.echoespast;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class EchoesConfig {
    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue CAPTURE_RADIUS = COMMON_BUILDER
            .comment("Radius captured by an empty Past Echo.")
            .defineInRange("captureRadius", 12, 1, 16);
    public static final ModConfigSpec.IntValue PROJECTION_RANGE = COMMON_BUILDER
            .comment("Maximum distance from a snapshot origin while projecting it.")
            .defineInRange("projectionRange", 32, 4, 128);
    public static final ModConfigSpec.IntValue MAX_CAPTURED_BLOCKS = COMMON_BUILDER
            .comment("Safety cap for non-air blocks stored in one snapshot.")
            .defineInRange("maxCapturedBlocks", 16_384, 64, 65_536);
    public static final ModConfigSpec.IntValue LOW_FREQUENCY_RANGE = COMMON_BUILDER
            .comment(
                    "Fallback / relay range cap for low-frequency pulses, in blocks.",
                    "Player resonator reach comes from ResonatorLoadout modules instead.")
            .defineInRange("lowFrequencyRange", 1_024, 128, 8_192);
    public static final ModConfigSpec.DoubleValue LOW_FREQUENCY_SPEED = COMMON_BUILDER
            .comment(
                    "Base travel speed, in blocks per second, of low-frequency pulses.",
                    "Cycle regulators add to this. Listening and item cooldown are distance/speed.")
            .defineInRange("lowFrequencySpeed", 96.0, 32.0, 2_048.0);
    public static final ModConfigSpec.IntValue LOW_FREQUENCY_COOLDOWN_TICKS = COMMON_BUILDER
            .comment(
                    "Legacy fallback cooldown ticks. The resonator item derives cooldown from",
                    "its listening window (2 * range / speed + linger) instead.")
            .defineInRange("lowFrequencyCooldownTicks", 1_200, 20, 2_400);

    static {
        COMMON_BUILDER.push("medusa");
    }

    public static final ModConfigSpec.BooleanValue MEDUSA_AFFECTS_PLAYERS = COMMON_BUILDER
            .comment(
                    "When true, the Head of Medusa permanently petrifies (effectively insta-kills) other players.",
                    "A carved pumpkin on the head is the only protection. Disable for calmer multiplayer.")
            .define("affectsPlayers", true);
    public static final ModConfigSpec.BooleanValue MEDUSA_AFFECTS_BOSSES = COMMON_BUILDER
            .comment(
                    "When true, the Head of Medusa can permanently petrify boss entities (ender dragon, wither, and anything tagged as a boss).",
                    "Disable if you want bosses to remain immune.")
            .define("affectsBosses", true);

    static {
        COMMON_BUILDER.pop();
    }

    public static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue POST_PROCESSING = CLIENT_BUILDER
            .comment("Use the cinematic screen-space echo effect when available.")
            .define("postProcessing", true);
    public static final ModConfigSpec.DoubleValue INTENSITY = CLIENT_BUILDER
            .comment("Strength of the sonar wave and projected memory. Independent from screen darkening.")
            .defineInRange("intensity", 1.0, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue SCREEN_DARKENING = CLIENT_BUILDER
            .comment("How strongly the screen darkens during a sonar pulse. 0 disables darkening; 1 is medium; 2 reaches black.")
            .defineInRange("screenDarkening", 1.5, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue DISTORTION = CLIENT_BUILDER
            .defineInRange("distortion", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue FLASHES = CLIENT_BUILDER
            .comment("Allow brief flashes at the outward impact and return.")
            .define("flashes", true);
    public static final ModConfigSpec.DoubleValue BOSS_IMPACT_CAMERA_STRENGTH = CLIENT_BUILDER
            .comment(
                    "Strength of short boss-hit camera impulses.",
                    "0 disables them completely; this never changes input or FOV.")
            .defineInRange("bossImpactCameraStrength", 1.0D, 0.0D, 1.0D);

    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

    private EchoesConfig() {
    }
}
