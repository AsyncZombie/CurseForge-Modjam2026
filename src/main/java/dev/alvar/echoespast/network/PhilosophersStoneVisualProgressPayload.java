package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Authoritative server progress for block-mutating Stone transitions. */
public record PhilosophersStoneVisualProgressPayload(float progress)
        implements CustomPacketPayload {
    public static final Type<PhilosophersStoneVisualProgressPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    EchoesShowThePast.MOD_ID,
                    "philosophers_stone_visual_progress"));

    public static final StreamCodec<
                    RegistryFriendlyByteBuf,
                    PhilosophersStoneVisualProgressPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.FLOAT,
                    PhilosophersStoneVisualProgressPayload::progress,
                    PhilosophersStoneVisualProgressPayload::new);

    public PhilosophersStoneVisualProgressPayload {
        progress = Math.clamp(progress, 0.0F, 1.0F);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
