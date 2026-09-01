package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MedusaPetrifyPayload(int entityId, int durationTicks)
        implements CustomPacketPayload {
    public static final Type<MedusaPetrifyPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "medusa_petrify"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MedusaPetrifyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    MedusaPetrifyPayload::entityId,
                    ByteBufCodecs.VAR_INT,
                    MedusaPetrifyPayload::durationTicks,
                    MedusaPetrifyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
