package dev.alvar.echoespast.mixin;

import io.netty.channel.ChannelHandlerContext;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.filters.GenericPacketSplitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores the Forgified Fabric API 1.21.x behaviour that skipped NeoForge's
 * {@link GenericPacketSplitter} for Fabric-registered custom payloads.
 * <p>
 * That mixin was dropped from FFAPI on 26.1, which makes Axiom chunk/block-entity
 * packets arrive truncated ({@code axiom:response_chunk_data} DecoderException).
 */
@Mixin(GenericPacketSplitter.class)
public abstract class GenericPacketSplitterMixin {
    @Unique
    private static final Method ECHOES$GET_FABRIC_CODEC = echoes$findFabricCodecMethod();

    @Inject(
            method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Ljava/util/List;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void echoesShowThePast$skipSplitForFabricPayloads(
            ChannelHandlerContext context,
            Packet<?> packet,
            List<Object> out,
            CallbackInfo callback) {
        CustomPacketPayload payload = echoes$payload(packet);
        if (payload == null) {
            return;
        }

        if (!(context.pipeline().get(HandlerNames.ENCODER) instanceof PacketEncoder<?> encoder)) {
            return;
        }

        Identifier payloadId = payload.type().id();
        ConnectionProtocol protocol = encoder.getProtocolInfo().id();
        PacketFlow flow = encoder.getProtocolInfo().flow();

        if (echoes$isFabricManagedPayload(payloadId, protocol, flow)
                || "axiom".equals(payloadId.getNamespace())) {
            out.add(packet);
            callback.cancel();
        }
    }

    @Unique
    private static CustomPacketPayload echoes$payload(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket clientbound) {
            return clientbound.payload();
        }
        if (packet instanceof ServerboundCustomPayloadPacket serverbound) {
            return serverbound.payload();
        }
        return null;
    }

    @Unique
    private static boolean echoes$isFabricManagedPayload(
            Identifier payloadId,
            ConnectionProtocol protocol,
            PacketFlow flow) {
        if (ECHOES$GET_FABRIC_CODEC == null) {
            return false;
        }
        try {
            return ECHOES$GET_FABRIC_CODEC.invoke(null, payloadId, protocol, flow) != null;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @Unique
    private static Method echoes$findFabricCodecMethod() {
        try {
            Class<?> networkingImpl = Class.forName("net.fabricmc.fabric.impl.networking.NetworkingImpl");
            return networkingImpl.getMethod(
                    "getCodec",
                    Identifier.class,
                    ConnectionProtocol.class,
                    PacketFlow.class);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
