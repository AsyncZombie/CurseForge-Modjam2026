package dev.alvar.echoespast.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record LowFrequencyPulseCancelPayload(long pulseId)
        implements CustomPacketPayload {
    public static final Type<LowFrequencyPulseCancelPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(
                    EchoesShowThePast.MOD_ID,
                    "low_frequency_pulse_cancel"));

    public static final Codec<LowFrequencyPulseCancelPayload> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.LONG.fieldOf("pulse_id")
                            .forGetter(LowFrequencyPulseCancelPayload::pulseId)
            ).apply(instance, LowFrequencyPulseCancelPayload::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LowFrequencyPulseCancelPayload>
            STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
