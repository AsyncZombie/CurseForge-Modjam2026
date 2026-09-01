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

public record LowFrequencyPulseStartPayload(
        long pulseId,
        Vec3 origin,
        int range,
        double speed,
        int cooldownTicks,
        Vec3 direction,
        float coneDegrees) implements CustomPacketPayload {
    public static final Type<LowFrequencyPulseStartPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "low_frequency_pulse_start"));

    public static final Codec<LowFrequencyPulseStartPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("pulse_id").forGetter(LowFrequencyPulseStartPayload::pulseId),
            Vec3.CODEC.fieldOf("origin").forGetter(LowFrequencyPulseStartPayload::origin),
            Codec.INT.fieldOf("range").forGetter(LowFrequencyPulseStartPayload::range),
            Codec.DOUBLE.fieldOf("speed").forGetter(LowFrequencyPulseStartPayload::speed),
            Codec.INT.fieldOf("cooldown_ticks").forGetter(LowFrequencyPulseStartPayload::cooldownTicks),
            Vec3.CODEC.optionalFieldOf("direction", new Vec3(0.0, 0.0, 1.0))
                    .forGetter(LowFrequencyPulseStartPayload::direction),
            Codec.FLOAT.optionalFieldOf("cone_degrees", 360.0F)
                    .forGetter(LowFrequencyPulseStartPayload::coneDegrees)
    ).apply(instance, LowFrequencyPulseStartPayload::new));

    public LowFrequencyPulseStartPayload(
            long pulseId,
            Vec3 origin,
            int range,
            double speed,
            int cooldownTicks) {
        this(
                pulseId,
                origin,
                range,
                speed,
                cooldownTicks,
                new Vec3(0.0, 0.0, 1.0),
                360.0F);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, LowFrequencyPulseStartPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
