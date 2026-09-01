package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Owner-only camera impulse for an authoritative Unknown melee impact. */
public record UnknownCombatImpactPayload(byte beat, boolean blocked)
        implements CustomPacketPayload {
    public static final byte FIRST_CUT = 0;
    public static final byte FINISHER = 1;

    public static final Type<UnknownCombatImpactPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(
                    EchoesShowThePast.MOD_ID,
                    "unknown_combat_impact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnknownCombatImpactPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.BYTE,
                    UnknownCombatImpactPayload::beat,
                    ByteBufCodecs.BOOL,
                    UnknownCombatImpactPayload::blocked,
                    UnknownCombatImpactPayload::new);

    public UnknownCombatImpactPayload {
        beat = (byte) Math.clamp(beat, FIRST_CUT, FINISHER);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
