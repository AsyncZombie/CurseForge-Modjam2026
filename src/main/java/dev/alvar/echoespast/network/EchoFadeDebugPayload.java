package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server asks the client to dump Past Echo present-fade diagnostics for the
 * block under the crosshair.
 */
public record EchoFadeDebugPayload() implements CustomPacketPayload {
    public static final EchoFadeDebugPayload INSTANCE = new EchoFadeDebugPayload();

    public static final Type<EchoFadeDebugPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(
                    EchoesShowThePast.MOD_ID,
                    "fade_debug"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EchoFadeDebugPayload>
            STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
