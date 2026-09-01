package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PetrifiedMobMinePayload(int entityId) implements CustomPacketPayload {
    public static final Type<PetrifiedMobMinePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "mine_petrified_mob"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PetrifiedMobMinePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    PetrifiedMobMinePayload::entityId,
                    PetrifiedMobMinePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
