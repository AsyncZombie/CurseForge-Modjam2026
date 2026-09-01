package dev.alvar.echoespast.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import dev.alvar.echoespast.snapshot.EchoSnapshotStreamCodec;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EchoStatePayload(
        boolean active,
        Optional<EchoSnapshot> snapshot,
        boolean replay) implements CustomPacketPayload {
    public static final Type<EchoStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "echo_state"));

    public static final Codec<EchoStatePayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("active").forGetter(EchoStatePayload::active),
            EchoSnapshot.CODEC.optionalFieldOf("snapshot").forGetter(EchoStatePayload::snapshot),
            Codec.BOOL.optionalFieldOf("replay", true).forGetter(EchoStatePayload::replay)
    ).apply(instance, EchoStatePayload::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EchoStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBoolean(payload.active());
                        buffer.writeBoolean(payload.snapshot().isPresent());
                        payload.snapshot().ifPresent(snapshot ->
                                EchoSnapshotStreamCodec.STREAM_CODEC.encode(
                                        buffer,
                                        snapshot));
                        buffer.writeBoolean(payload.replay());
                    },
                    buffer -> {
                        boolean active = buffer.readBoolean();
                        Optional<EchoSnapshot> snapshot =
                                buffer.readBoolean()
                                        ? Optional.of(
                                                EchoSnapshotStreamCodec
                                                        .STREAM_CODEC
                                                        .decode(buffer))
                                        : Optional.empty();
                        return new EchoStatePayload(
                                active,
                                snapshot,
                                buffer.readBoolean());
                    });

    public static EchoStatePayload off() {
        return new EchoStatePayload(false, Optional.empty(), false);
    }

    public static EchoStatePayload on(EchoSnapshot snapshot) {
        return new EchoStatePayload(true, Optional.of(snapshot), true);
    }

    public static EchoStatePayload revision(EchoSnapshot snapshot) {
        return new EchoStatePayload(true, Optional.of(snapshot), false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
