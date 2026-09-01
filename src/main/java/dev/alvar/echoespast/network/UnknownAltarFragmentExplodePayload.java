package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client burst when an altar fight-fragment detonates at a phase gate. */
public record UnknownAltarFragmentExplodePayload(BlockPos altarOrigin, int slot)
        implements CustomPacketPayload {
    public static final Type<UnknownAltarFragmentExplodePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "unknown_altar_fragment_explode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnknownAltarFragmentExplodePayload>
            STREAM_CODEC = StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    UnknownAltarFragmentExplodePayload::altarOrigin,
                    ByteBufCodecs.VAR_INT,
                    UnknownAltarFragmentExplodePayload::slot,
                    UnknownAltarFragmentExplodePayload::new);

    public UnknownAltarFragmentExplodePayload {
        slot = Math.clamp(slot, 0, 5);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
