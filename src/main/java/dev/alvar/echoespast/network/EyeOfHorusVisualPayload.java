package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EyeOfHorusVisualPayload(int durationTicks) implements CustomPacketPayload {
    public static final Type<EyeOfHorusVisualPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "eye_of_horus_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EyeOfHorusVisualPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EyeOfHorusVisualPayload::durationTicks,
                    EyeOfHorusVisualPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
