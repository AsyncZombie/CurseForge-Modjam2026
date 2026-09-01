package dev.alvar.echoespast.resonance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.server.LowFrequencySonarMath;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Module sockets and directional toggle. Range and cycle length are sized so a
 * constant-speed round trip stays playable: the item cooldown is the listening
 * window itself, and cycle regulators shorten that window by raising pulse speed.
 */
public record ResonatorLoadout(List<ResonatorModule> modules, boolean directionalMode) {
    public static final int SLOT_COUNT = 3;
    /** Omnidirectional reach without coils. */
    public static final int BASE_RANGE = 512;
    public static final int RANGE_COIL_BONUS = 512;
    /** Each regulator adds this many blocks/second to the pulse. */
    public static final double REGULATOR_SPEED_BONUS = 24.0;

    private static final Codec<ResonatorLoadout> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResonatorModule.CODEC.listOf()
                    .optionalFieldOf("modules", List.of())
                    .forGetter(ResonatorLoadout::modules),
            // Default true so installing a matrix immediately aims the cone;
            // wide mode remains an explicit console toggle.
            Codec.BOOL.optionalFieldOf("directional", true)
                    .forGetter(ResonatorLoadout::directionalMode)
    ).apply(instance, ResonatorLoadout::new));

    public static final Codec<ResonatorLoadout> CODEC =
            RAW_CODEC.validate(ResonatorLoadout::validate);
    public static final ResonatorLoadout EMPTY = new ResonatorLoadout(List.of(), true);

    public ResonatorLoadout {
        modules = List.copyOf(modules);
    }

    private static DataResult<ResonatorLoadout> validate(ResonatorLoadout loadout) {
        if (loadout.modules.size() > SLOT_COUNT) {
            return DataResult.error(() -> "A resonator has exactly three module slots");
        }
        Set<ResonatorModule> unique = new HashSet<>();
        for (ResonatorModule module : loadout.modules) {
            if (!module.duplicatesAllowed() && !unique.add(module)) {
                return DataResult.error(() -> module.getSerializedName() + " cannot be installed twice");
            }
            unique.add(module);
        }
        return DataResult.success(loadout);
    }

    public int count(ResonatorModule module) {
        return (int) modules.stream().filter(module::equals).count();
    }

    public boolean has(ResonatorModule module) {
        return modules.contains(module);
    }

    public boolean effectiveDirectionalMode() {
        return directionalMode && count(ResonatorModule.DIRECTIONAL_MATRIX) > 0;
    }

    public int effectiveRange() {
        int coils = count(ResonatorModule.RANGE_COIL);
        int matrices = count(ResonatorModule.DIRECTIONAL_MATRIX);
        int base = switch (effectiveDirectionalMode() ? matrices : 0) {
            case 1 -> 2_560;
            case 2 -> 4_608;
            case 3 -> 6_656;
            default -> BASE_RANGE;
        };
        return base + coils * RANGE_COIL_BONUS;
    }

    public double effectiveSpeed() {
        return effectiveSpeed(EchoesConfig.LOW_FREQUENCY_SPEED.getAsDouble());
    }

    public double effectiveSpeed(double baseSpeed) {
        return Math.max(1.0, baseSpeed + count(ResonatorModule.CYCLE_REGULATOR) * REGULATOR_SPEED_BONUS);
    }

    /**
     * Item cooldown equals the listening window: screen darkening ends when no
     * further reply can arrive, and a new pulse becomes available at the same moment.
     */
    public int cooldownTicks() {
        return LowFrequencySonarMath.listeningTicks(effectiveRange(), effectiveSpeed());
    }

    public float coneDegrees() {
        return switch (count(ResonatorModule.DIRECTIONAL_MATRIX)) {
            case 1 -> 48.0F;
            case 2 -> 32.0F;
            case 3 -> 20.0F;
            default -> 360.0F;
        };
    }

    public ResonatorLoadout withDirectionalMode(boolean directional) {
        return new ResonatorLoadout(modules, directional);
    }

    /**
     * Used at every trust boundary, including malformed components supplied by
     * commands. Invalid duplicates are ignored rather than duplicating items.
     */
    public static ResonatorLoadout sanitized(List<ResonatorModule> proposed, boolean directional) {
        List<ResonatorModule> accepted = new ArrayList<>(SLOT_COUNT);
        Set<ResonatorModule> unique = new HashSet<>();
        for (ResonatorModule module : proposed) {
            if (accepted.size() >= SLOT_COUNT) {
                break;
            }
            if (!module.duplicatesAllowed() && !unique.add(module)) {
                continue;
            }
            unique.add(module);
            accepted.add(module);
        }
        return new ResonatorLoadout(accepted, directional);
    }
}
