package dev.alvar.echoespast.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record PhilosophersStoneVisualPayload(
        Vec3 center,
        Vec3 halfExtents,
        Vec3 direction,
        int phase,
        int durationTicks) implements CustomPacketPayload {
    public static final int MATERIALIZE_PAST = 0;
    public static final int RESTORE_PRESENT = 1;
    /** Crest + block wave without leaving the amber "physical past" grade. */
    public static final int WAVE_ONLY = 2;
    /** Late-viewer snapshot: the past is already physical and bounded. */
    public static final int STABLE_PAST = 3;
    /** Client-only teardown when a tracked viewer leaves the dimension. */
    public static final int CLEAR = 4;

    public static final Type<PhilosophersStoneVisualPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    EchoesShowThePast.MOD_ID,
                    "philosophers_stone_visual"));

    public static final Codec<PhilosophersStoneVisualPayload> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Vec3.CODEC.fieldOf("center")
                            .forGetter(PhilosophersStoneVisualPayload::center),
                    Vec3.CODEC.fieldOf("half_extents")
                            .forGetter(PhilosophersStoneVisualPayload::halfExtents),
                    Vec3.CODEC.fieldOf("direction")
                            .forGetter(PhilosophersStoneVisualPayload::direction),
                    Codec.INT.fieldOf("phase")
                            .forGetter(PhilosophersStoneVisualPayload::phase),
                    Codec.INT.fieldOf("duration_ticks")
                            .forGetter(PhilosophersStoneVisualPayload::durationTicks)
            ).apply(instance, PhilosophersStoneVisualPayload::new));

    public static final StreamCodec<
                    RegistryFriendlyByteBuf,
                    PhilosophersStoneVisualPayload>
            STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public PhilosophersStoneVisualPayload {
        halfExtents = new Vec3(
                Math.clamp(Math.abs(halfExtents.x), 0.5, 128.0),
                Math.clamp(Math.abs(halfExtents.y), 0.5, 128.0),
                Math.clamp(Math.abs(halfExtents.z), 0.5, 128.0));
        direction = direction.lengthSqr() < 1.0E-6
                ? new Vec3(0.62, 0.18, 0.76).normalize()
                : direction.normalize();
        phase = switch (phase) {
            case RESTORE_PRESENT -> RESTORE_PRESENT;
            case WAVE_ONLY -> WAVE_ONLY;
            case STABLE_PAST -> STABLE_PAST;
            case CLEAR -> CLEAR;
            default -> MATERIALIZE_PAST;
        };
        durationTicks = Math.clamp(durationTicks, 1, 20 * 10);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
