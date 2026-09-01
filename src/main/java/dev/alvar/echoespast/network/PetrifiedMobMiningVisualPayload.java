package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PetrifiedMobMiningVisualPayload(
        int entityId,
        float progress,
        boolean impact) implements CustomPacketPayload {
    public static final Type<PetrifiedMobMiningVisualPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "petrified_mob_mining"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PetrifiedMobMiningVisualPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    PetrifiedMobMiningVisualPayload::entityId,
                    ByteBufCodecs.FLOAT,
                    PetrifiedMobMiningVisualPayload::progress,
                    ByteBufCodecs.BOOL,
                    PetrifiedMobMiningVisualPayload::impact,
                    PetrifiedMobMiningVisualPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
