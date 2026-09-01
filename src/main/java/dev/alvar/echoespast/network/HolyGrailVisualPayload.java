package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Small event payload for the non-persistent phases of the Grail ritual.
 * The eight-second aura itself is synchronized through player attachments so
 * clients that begin tracking midway still see it.
 */
public record HolyGrailVisualPayload(int entityId, int phase, int durationTicks)
        implements CustomPacketPayload {
    public static final int START = 0;
    public static final int RELEASE = 1;
    public static final int CANCEL = 2;
    public static final int RECHARGE = 3;

    public static final Type<HolyGrailVisualPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(
                    EchoesShowThePast.MOD_ID,
                    "holy_grail_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HolyGrailVisualPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    HolyGrailVisualPayload::entityId,
                    ByteBufCodecs.VAR_INT,
                    HolyGrailVisualPayload::phase,
                    ByteBufCodecs.VAR_INT,
                    HolyGrailVisualPayload::durationTicks,
                    HolyGrailVisualPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
