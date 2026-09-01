package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.relic.EyeHazardType;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EyeHazardSignalsPayload(List<EyeHazardSignal> signals)
        implements CustomPacketPayload {
    private static final int MAX_SIGNALS = 128;
    private static final EyeHazardType[] TYPES = EyeHazardType.values();

    public static final Type<EyeHazardSignalsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "eye_hazard_signals"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EyeHazardSignalsPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public EyeHazardSignalsPayload decode(RegistryFriendlyByteBuf buffer) {
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_SIGNALS) {
                        throw new DecoderException("Invalid Eye of Horus hazard count: " + count);
                    }
                    List<EyeHazardSignal> signals = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        BlockPos position = BlockPos.STREAM_CODEC.decode(buffer);
                        int type = buffer.readVarInt();
                        if (type < 0 || type >= TYPES.length) {
                            throw new DecoderException("Invalid Eye of Horus hazard type: " + type);
                        }
                        Direction direction = Direction.from3DDataValue(buffer.readVarInt());
                        signals.add(new EyeHazardSignal(position, TYPES[type], direction));
                    }
                    return new EyeHazardSignalsPayload(List.copyOf(signals));
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        EyeHazardSignalsPayload payload) {
                    if (payload.signals.size() > MAX_SIGNALS) {
                        throw new IllegalArgumentException(
                                "Eye of Horus hazard batch exceeds " + MAX_SIGNALS);
                    }
                    buffer.writeVarInt(payload.signals.size());
                    for (EyeHazardSignal signal : payload.signals) {
                        BlockPos.STREAM_CODEC.encode(buffer, signal.position());
                        buffer.writeVarInt(signal.type().ordinal());
                        buffer.writeVarInt(signal.direction().get3DDataValue());
                    }
                }
            };

    public EyeHazardSignalsPayload {
        signals = List.copyOf(signals);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
