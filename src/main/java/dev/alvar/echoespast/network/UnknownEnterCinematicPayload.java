package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.cinematic.UnknownEnterCinematicMath;
import dev.alvar.echoespast.world.TimelessDimensions;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
        int depositStep,
        BlockPos arenaOrigin,
        Vec3i arenaSize) implements CustomPacketPayload {
    public static final Type<UnknownEnterCinematicPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "unknown_enter_cinematic"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnknownEnterCinematicPayload> STREAM_CODEC =
            StreamCodec.of(
                    UnknownEnterCinematicPayload::write,
                    UnknownEnterCinematicPayload::read);

    /** Backwards source-compatible constructor for non-era shots and tests. */
    public UnknownEnterCinematicPayload(
            boolean active,
            int bossId,
            BlockPos altarOrigin,
            byte mode,
            int depositStep) {
        this(
                active,
                bossId,
                altarOrigin,
                mode,
                depositStep,
                TimelessDimensions.ARENA_ORIGIN,
                TimelessDimensions.ARENA_VOLUME);
    }

    public static UnknownEnterCinematicPayload inactive() {
        return new UnknownEnterCinematicPayload(
                false,
                0,
                BlockPos.ZERO,
                (byte) 0,
                -1,
                BlockPos.ZERO,
                new Vec3i(0, 0, 0));
    }

    public UnknownEnterCinematicPayload {
        altarOrigin = Objects.requireNonNull(altarOrigin, "altarOrigin");
        arenaOrigin = Objects.requireNonNull(arenaOrigin, "arenaOrigin");
        arenaSize = Objects.requireNonNull(arenaSize, "arenaSize");
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
            arenaOrigin = BlockPos.ZERO;
            arenaSize = new Vec3i(0, 0, 0);
        } else {
            arenaSize = new Vec3i(
                    Math.clamp(arenaSize.getX(), 1, 512),
                    Math.clamp(arenaSize.getY(), 1, 512),
                    Math.clamp(arenaSize.getZ(), 1, 512));
        }
    }

    private static void write(
            RegistryFriendlyByteBuf buffer,
            UnknownEnterCinematicPayload payload) {
        buffer.writeBoolean(payload.active());
        buffer.writeVarInt(payload.bossId());
        buffer.writeBlockPos(payload.altarOrigin());
        buffer.writeByte(payload.mode());
        buffer.writeVarInt(payload.depositStep());
        buffer.writeBlockPos(payload.arenaOrigin());
        buffer.writeVarInt(payload.arenaSize().getX());
        buffer.writeVarInt(payload.arenaSize().getY());
        buffer.writeVarInt(payload.arenaSize().getZ());
    }

    private static UnknownEnterCinematicPayload read(RegistryFriendlyByteBuf buffer) {
        return new UnknownEnterCinematicPayload(
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readBlockPos(),
                buffer.readByte(),
                buffer.readVarInt(),
                buffer.readBlockPos(),
                new Vec3i(
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
