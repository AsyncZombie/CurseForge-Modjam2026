package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Authoritative Unknown boss-bar presentation state for the cinematic HUD.
 *
 * <p>{@code active=false} clears the client overlay. Era/phase/threshold drive
 * theme art and the six epoch seals; health is still lerped from the vanilla
 * {@code ServerBossEvent} so drain stays smooth.
 */
public record UnknownBossBarPayload(
        boolean active,
        UUID bossId,
        byte era,
        byte phase,
        int thresholdIndex) implements CustomPacketPayload {
    public static final byte ERA_VOID = 0;
    public static final byte ERA_GREEK = 1;
    public static final byte ERA_EGYPTIAN = 2;
    public static final byte ERA_MEDIEVAL = 3;

    public static final byte PHASE_IDLE = 0;
    public static final byte PHASE_CINEMATIC_WALK = 1;
    public static final byte PHASE_RECONSTRUCTING = 2;
    public static final byte PHASE_PAST = 3;
    public static final byte PHASE_RUINS = 4;
    public static final byte PHASE_VOID_VULNERABLE = 5;
    public static final byte PHASE_DEAD = 6;
    /** Appended so older phase ids remain wire-compatible. */
    public static final byte PHASE_EXECUTION = 7;

    public static final Type<UnknownBossBarPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "unknown_boss_bar"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnknownBossBarPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    UnknownBossBarPayload::active,
                    UUIDUtil.STREAM_CODEC,
                    UnknownBossBarPayload::bossId,
                    ByteBufCodecs.BYTE,
                    UnknownBossBarPayload::era,
                    ByteBufCodecs.BYTE,
                    UnknownBossBarPayload::phase,
                    ByteBufCodecs.VAR_INT,
                    UnknownBossBarPayload::thresholdIndex,
                    UnknownBossBarPayload::new);

    public static UnknownBossBarPayload inactive() {
        return new UnknownBossBarPayload(false, new UUID(0L, 0L), ERA_VOID, PHASE_IDLE, 0);
    }

    public UnknownBossBarPayload {
        era = (byte) Math.clamp(era, ERA_VOID, ERA_MEDIEVAL);
        phase = (byte) Math.clamp(phase, PHASE_IDLE, PHASE_EXECUTION);
        thresholdIndex = Math.clamp(thresholdIndex, 0, 6);
        if (!active) {
            bossId = new UUID(0L, 0L);
            era = ERA_VOID;
            phase = PHASE_IDLE;
            thresholdIndex = 0;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
