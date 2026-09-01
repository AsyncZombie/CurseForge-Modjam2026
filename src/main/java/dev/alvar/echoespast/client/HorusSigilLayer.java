package dev.alvar.echoespast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * A restrained third-eye manifestation of the relic. It is transformed by
 * the actual PlayerModel head bone, then built from a dark gold backing, a
 * crisp glyph, and one controlled additive halo.
 */
final class HorusSigilLayer
        extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final int WHITE = 0xFFFFFF;
    private static final int DEEP_GOLD = 0xA85B12;
    private static final int PALE_GOLD = 0xFFE49A;

    HorusSigilLayer(
            RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            AvatarRenderState state,
            float yRot,
            float xRot) {
        Float strengthValue = state.getRenderData(HorusRenderState.AURA_STRENGTH);
        if (strengthValue == null || strengthValue <= 0.002F) {
            return;
        }

        float strength = strengthValue;
        float pulse = 1.0F
                + (float) Math.sin(state.ageInTicks * 0.16F) * 0.025F;
        float bob = (float) Math.sin(state.ageInTicks * 0.11F) * 0.012F;

        poseStack.pushPose();
        PlayerModel model = this.getParentModel();
        model.root().translateAndRotate(poseStack);
        model.translateToHead(poseStack);
        poseStack.translate(0.0F, -0.69F + bob, -0.292F);

        collector.submitCustomGeometry(
                poseStack,
                EchoRenderTypes.HORUS_SIGIL_GLOW,
                (pose, consumer) -> quad(
                        pose,
                        consumer,
                        0.86F * pulse,
                        -0.004F,
                        color(strength * 0.18F, PALE_GOLD)));
        collector.submitCustomGeometry(
                poseStack,
                EchoRenderTypes.HORUS_SIGIL,
                (pose, consumer) -> {
                    quad(
                            pose,
                            consumer,
                            0.72F * pulse,
                            0.012F,
                            color(strength * 0.58F, DEEP_GOLD));
                    quad(
                            pose,
                            consumer,
                            0.665F * pulse,
                            0.020F,
                            color(strength * 0.96F, WHITE));
                });
        collector.submitCustomGeometry(
                poseStack,
                EchoRenderTypes.HORUS_SIGIL_GLOW,
                (pose, consumer) -> quad(
                        pose,
                        consumer,
                        0.625F * pulse,
                        0.024F,
                        color(strength * 0.26F, PALE_GOLD)));
        poseStack.popPose();
    }

    private static void quad(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            float size,
            float depth,
            int color) {
        float half = size * 0.5F;
        // LivingEntityRenderer mirrors model X. Reversing U here keeps the
        // asymmetric Horus curl readable instead of displaying its mirror.
        consumer.addVertex(pose, -half, -half, depth)
                .setUv(1.0F, 0.0F)
                .setColor(color);
        consumer.addVertex(pose, half, -half, depth)
                .setUv(0.0F, 0.0F)
                .setColor(color);
        consumer.addVertex(pose, half, half, depth)
                .setUv(0.0F, 1.0F)
                .setColor(color);
        consumer.addVertex(pose, -half, half, depth)
                .setUv(1.0F, 1.0F)
                .setColor(color);
    }

    private static int color(float alpha, int rgb) {
        return (Math.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | rgb;
    }
}
