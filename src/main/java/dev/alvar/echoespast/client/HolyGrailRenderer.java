package dev.alvar.echoespast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * The released blessing has one world-space gesture: a shallow sheet of
 * sanctified water. Broad translucent material and a narrow reflected edge
 * are deliberately submitted to different passes instead of drawing every
 * primitive twice.
 */
public final class HolyGrailRenderer {
    private static final int DEEP_WATER = 0x3B9FBE;
    private static final int PALE_WATER = 0xA7EFF0;
    private static final int SACRED_GOLD = 0xFFE39A;

    public static void submit(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        long now = System.nanoTime();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        float time = minecraft.level.getGameTime();
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        for (AbstractClientPlayer owner : minecraft.level.players()) {
            ClientHolyGrailVision.GrailVisual visual =
                    ClientHolyGrailVision.visualFor(owner, now);
            if (visual.release() <= 0.001F && visual.aura() <= 0.001F) {
                continue;
            }

            Vec3 floor = owner.position()
                    .subtract(camera)
                    .add(0.0, 0.038, 0.0);
            collector.submitCustomGeometry(
                    poseStack,
                    EchoRenderTypes.HOLY_GRAIL_RITUAL,
                    (pose, consumer) -> waterSheet(
                            pose,
                            consumer,
                            floor,
                            visual,
                            time));
            collector.submitCustomGeometry(
                    poseStack,
                    EchoRenderTypes.HOLY_GRAIL_GLOW,
                    (pose, consumer) -> reflectedEdge(
                            pose,
                            consumer,
                            floor.add(0.0, 0.009, 0.0),
                            visual,
                            time));
        }
    }

    private static void waterSheet(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 floor,
            ClientHolyGrailVision.GrailVisual visual,
            float time) {
        float release = visual.release();
        if (release > 0.001F && release < 0.999F) {
            float arrival = smooth(Math.min(1.0F, release * 3.4F));
            float fade = 1.0F - smooth(Math.max(
                    0.0F,
                    (release - 0.48F) / 0.52F));
            float radius = 0.18F + smooth(release) * 7.15F;
            disc(
                    consumer,
                    pose,
                    floor,
                    radius,
                    color(arrival * fade * 0.22F, PALE_WATER));
            annulus(
                    consumer,
                    pose,
                    floor.add(0.0, 0.003, 0.0),
                    radius * 0.58F,
                    Math.max(0.18F, radius * 0.22F),
                    color(arrival * fade * 0.035F, SACRED_GOLD));
        }

        float aura = visual.aura();
        if (aura > 0.001F) {
            float cycle = fract(time * 0.032F);
            float opening = smooth(Math.min(1.0F, cycle * 5.0F));
            float closing = 1.0F - smooth(Math.max(
                    0.0F,
                    (cycle - 0.30F) / 0.70F));
            float radius = 1.15F + cycle * 5.65F;
            annulus(
                    consumer,
                    pose,
                    floor,
                    radius,
                    0.36F + cycle * 0.38F,
                    color(
                            aura * opening * closing * 0.062F,
                            DEEP_WATER));
        }
    }

    private static void reflectedEdge(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 floor,
            ClientHolyGrailVision.GrailVisual visual,
            float time) {
        float release = visual.release();
        if (release > 0.001F && release < 0.999F) {
            float enter = smooth(Math.min(1.0F, release * 4.8F));
            float fade = 1.0F - smooth(Math.max(
                    0.0F,
                    (release - 0.58F) / 0.42F));
            float radius = 0.18F + smooth(release) * 7.15F;
            rippleAnnulus(
                    consumer,
                    pose,
                    floor,
                    radius,
                    0.030F + release * 0.035F,
                    0.055F,
                    time * 0.045F,
                    color(enter * fade * 0.31F, SACRED_GOLD));
        }

        float aura = visual.aura();
        if (aura > 0.001F) {
            float cycle = fract(time * 0.032F);
            float opening = smooth(Math.min(1.0F, cycle * 5.0F));
            float closing = 1.0F - smooth(Math.max(
                    0.0F,
                    (cycle - 0.30F) / 0.70F));
            float radius = 1.15F + cycle * 5.65F;
            rippleAnnulus(
                    consumer,
                    pose,
                    floor,
                    radius,
                    0.014F + cycle * 0.020F,
                    0.035F,
                    time * 0.032F,
                    color(
                            aura * opening * closing * 0.10F,
                            SACRED_GOLD));
        }
    }

    private static void disc(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 center,
            float radius,
            int color) {
        int segments = 72;
        for (int segment = 0; segment < segments; segment++) {
            double a0 = Math.PI * 2.0 * segment / segments;
            double a1 = Math.PI * 2.0 * (segment + 1) / segments;
            Vec3 edge0 = center.add(
                    Math.cos(a0) * radius,
                    0.0,
                    Math.sin(a0) * radius);
            Vec3 edge1 = center.add(
                    Math.cos(a1) * radius,
                    0.0,
                    Math.sin(a1) * radius);
            quad(
                    consumer,
                    pose,
                    center,
                    edge0,
                    edge1,
                    center,
                    color);
        }
    }

    private static void annulus(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 center,
            float radius,
            float halfWidth,
            int color) {
        int segments = 72;
        float innerRadius = Math.max(0.0F, radius - halfWidth);
        float outerRadius = radius + halfWidth;
        for (int segment = 0; segment < segments; segment++) {
            double a0 = Math.PI * 2.0 * segment / segments;
            double a1 = Math.PI * 2.0 * (segment + 1) / segments;
            Vec3 outer0 = center.add(
                    Math.cos(a0) * outerRadius,
                    0.0,
                    Math.sin(a0) * outerRadius);
            Vec3 inner0 = center.add(
                    Math.cos(a0) * innerRadius,
                    0.0,
                    Math.sin(a0) * innerRadius);
            Vec3 inner1 = center.add(
                    Math.cos(a1) * innerRadius,
                    0.0,
                    Math.sin(a1) * innerRadius);
            Vec3 outer1 = center.add(
                    Math.cos(a1) * outerRadius,
                    0.0,
                    Math.sin(a1) * outerRadius);
            quad(
                    consumer,
                    pose,
                    outer0,
                    inner0,
                    inner1,
                    outer1,
                    color);
        }
    }

    private static void rippleAnnulus(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 center,
            float radius,
            float halfWidth,
            float ripple,
            float phase,
            int color) {
        int segments = 96;
        for (int segment = 0; segment < segments; segment++) {
            double a0 = Math.PI * 2.0 * segment / segments;
            double a1 = Math.PI * 2.0 * (segment + 1) / segments;
            float offset0 = (float) Math.sin(a0 * 5.0 + phase) * ripple;
            float offset1 = (float) Math.sin(a1 * 5.0 + phase) * ripple;
            float inner0Radius = Math.max(
                    0.0F,
                    radius + offset0 - halfWidth);
            float outer0Radius = radius + offset0 + halfWidth;
            float inner1Radius = Math.max(
                    0.0F,
                    radius + offset1 - halfWidth);
            float outer1Radius = radius + offset1 + halfWidth;
            Vec3 outer0 = center.add(
                    Math.cos(a0) * outer0Radius,
                    0.0,
                    Math.sin(a0) * outer0Radius);
            Vec3 inner0 = center.add(
                    Math.cos(a0) * inner0Radius,
                    0.0,
                    Math.sin(a0) * inner0Radius);
            Vec3 inner1 = center.add(
                    Math.cos(a1) * inner1Radius,
                    0.0,
                    Math.sin(a1) * inner1Radius);
            Vec3 outer1 = center.add(
                    Math.cos(a1) * outer1Radius,
                    0.0,
                    Math.sin(a1) * outer1Radius);
            quad(
                    consumer,
                    pose,
                    outer0,
                    inner0,
                    inner1,
                    outer1,
                    color);
        }
    }

    private static void quad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            int color) {
        vertex(consumer, pose, a, color);
        vertex(consumer, pose, b, color);
        vertex(consumer, pose, c, color);
        vertex(consumer, pose, d, color);
    }

    private static void vertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 point,
            int color) {
        consumer.addVertex(
                        pose,
                        (float) point.x,
                        (float) point.y,
                        (float) point.z)
                .setColor(color);
    }

    private static float smooth(float value) {
        float clamped = Math.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static int color(float alpha, int rgb) {
        return (Math.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | rgb;
    }

    private HolyGrailRenderer() {
    }
}
