package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent from the interaction binding when vanilla's item cooldown would stop
 * the use callback before a player can dismiss an already-active relic or
 * open the Resonator console.
 */
public record RelicControlPayload(boolean offHand)
        implements CustomPacketPayload {
    public static final Type<RelicControlPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(
                    EchoesShowThePast.MOD_ID,
                    "relic_control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RelicControlPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    RelicControlPayload::offHand,
                    RelicControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
