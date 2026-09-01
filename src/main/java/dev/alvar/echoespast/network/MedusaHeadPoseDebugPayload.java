package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Live authoring overrides for the held Head of Medusa. Disabled payloads
 * restore the compiled rest/active Euler poses.
 */
public record MedusaHeadPoseDebugPayload(
        boolean enabled,
        float restX,
        float restY,
        float restZ,
        float activeX,
        float activeY,
        float activeZ)
        implements CustomPacketPayload {
    public static final Type<MedusaHeadPoseDebugPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(
                    EchoesShowThePast.MOD_ID,
                    "medusa_head_pose_debug"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MedusaHeadPoseDebugPayload>
            STREAM_CODEC = StreamCodec.of(
                    MedusaHeadPoseDebugPayload::write,
                    MedusaHeadPoseDebugPayload::read);

    private static void write(
            RegistryFriendlyByteBuf buffer,
            MedusaHeadPoseDebugPayload payload) {
        buffer.writeBoolean(payload.enabled());
        buffer.writeFloat(payload.restX());
        buffer.writeFloat(payload.restY());
        buffer.writeFloat(payload.restZ());
        buffer.writeFloat(payload.activeX());
        buffer.writeFloat(payload.activeY());
        buffer.writeFloat(payload.activeZ());
    }

    private static MedusaHeadPoseDebugPayload read(RegistryFriendlyByteBuf buffer) {
        return new MedusaHeadPoseDebugPayload(
                buffer.readBoolean(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
