package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.cinematic.UnknownEnterCinematicMath;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Starts, updates and ends Unknown cinematics: the plaza approach, fragment
 * seating, era reconstruction/collapse crane, tower grab dive, shield break
 * and final execution.
 */
public record UnknownEnterCinematicPayload(
        boolean active,
        int bossId,
        BlockPos altarOrigin,
        byte mode,
        int depositStep) implements CustomPacketPayload {
    public static final Type<UnknownEnterCinematicPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "unknown_enter_cinematic"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnknownEnterCinematicPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    UnknownEnterCinematicPayload::active,
                    ByteBufCodecs.VAR_INT,
                    UnknownEnterCinematicPayload::bossId,
                    BlockPos.STREAM_CODEC,
                    UnknownEnterCinematicPayload::altarOrigin,
                    ByteBufCodecs.BYTE,
                    UnknownEnterCinematicPayload::mode,
                    ByteBufCodecs.VAR_INT,
                    UnknownEnterCinematicPayload::depositStep,
                    UnknownEnterCinematicPayload::new);

    public static UnknownEnterCinematicPayload inactive() {
        return new UnknownEnterCinematicPayload(false, 0, BlockPos.ZERO, (byte) 0, -1);
    }

    public UnknownEnterCinematicPayload {
        mode = (byte) Math.clamp(
                mode,
                UnknownEnterCinematicMath.MODE_APPROACH,
                UnknownEnterCinematicMath.MODE_GRAB_DIVE);
        depositStep = Math.clamp(depositStep, -1, 7);
        if (!active) {
            bossId = 0;
            altarOrigin = BlockPos.ZERO;
            mode = UnknownEnterCinematicMath.MODE_APPROACH;
            depositStep = -1;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
