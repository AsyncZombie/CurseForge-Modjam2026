package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MedusaGazeVisualPayload(int phase, int durationTicks)
        implements CustomPacketPayload {
    public static final int START = 0;
    public static final int IMPACT = 1;
    public static final int CANCEL = 2;
    public static final int CONTACT = 3;

    public static final Type<MedusaGazeVisualPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "medusa_gaze_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MedusaGazeVisualPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    MedusaGazeVisualPayload::phase,
                    ByteBufCodecs.VAR_INT,
                    MedusaGazeVisualPayload::durationTicks,
                    MedusaGazeVisualPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
