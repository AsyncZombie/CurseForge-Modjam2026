package dev.alvar.echoespast.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alvar.echoespast.EchoesShowThePast;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record LowFrequencyPulseResultPayload(
        long pulseId,
        Optional<BlockPos> pedestal,
        int rgb,
        Optional<Identifier> knownSite) implements CustomPacketPayload {
    public static final Type<LowFrequencyPulseResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "low_frequency_pulse_result"));

    public static final Codec<LowFrequencyPulseResultPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("pulse_id").forGetter(LowFrequencyPulseResultPayload::pulseId),
            BlockPos.CODEC.optionalFieldOf("pedestal").forGetter(LowFrequencyPulseResultPayload::pedestal),
            Codec.INT.optionalFieldOf("rgb", 0x55E6F2).forGetter(LowFrequencyPulseResultPayload::rgb),
            Identifier.CODEC.optionalFieldOf("known_site").forGetter(LowFrequencyPulseResultPayload::knownSite)
    ).apply(instance, LowFrequencyPulseResultPayload::new));

    public LowFrequencyPulseResultPayload(long pulseId, Optional<BlockPos> pedestal) {
        this(pulseId, pedestal, 0x55E6F2, Optional.empty());
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, LowFrequencyPulseResultPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static LowFrequencyPulseResultPayload found(long pulseId, BlockPos pedestal) {
        return new LowFrequencyPulseResultPayload(
                pulseId,
                Optional.of(pedestal.immutable()),
                0x55E6F2,
                Optional.empty());
    }

    public static LowFrequencyPulseResultPayload found(
            long pulseId,
            BlockPos pedestal,
            int rgb,
            Optional<Identifier> knownSite) {
        return new LowFrequencyPulseResultPayload(
                pulseId,
                Optional.of(pedestal.immutable()),
                rgb,
                knownSite);
    }

    public static LowFrequencyPulseResultPayload none(long pulseId) {
        return new LowFrequencyPulseResultPayload(
                pulseId,
                Optional.empty(),
                0x55E6F2,
                Optional.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
