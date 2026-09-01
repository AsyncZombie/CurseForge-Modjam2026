package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.relic.BakedModelPose;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PetrifiedPoseCapturePayload(
        int entityId,
        BakedModelPose modelPose) implements CustomPacketPayload {
    public static final Type<PetrifiedPoseCapturePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(
                    EchoesShowThePast.MOD_ID,
                    "petrified_pose_capture"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PetrifiedPoseCapturePayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    PetrifiedPoseCapturePayload::entityId,
                    ByteBufCodecs.fromCodecWithRegistries(BakedModelPose.CODEC),
                    PetrifiedPoseCapturePayload::modelPose,
                    PetrifiedPoseCapturePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
