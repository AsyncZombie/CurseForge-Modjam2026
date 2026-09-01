package dev.alvar.echoespast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

public final class MedusaGazeRenderer {
    private static final double RANGE = 16.0;
    private static final double CONE_TANGENT = 0.70;
    private static final int VENOM_RGB = 0x55A866;
    private static final int STONE_RGB = 0xC1AD78;

    public static void submit(SubmitCustomGeometryEvent event) {
        long now = System.nanoTime();
        if (!ClientMedusaVision.gazeActive(now)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Vec3 origin = minecraft.player.getEyePosition();
        Vec3 direction = minecraft.player.getLookAngle().normalize();
        Vec3 requestedEnd = origin.add(direction.scale(RANGE));
        HitResult hit = minecraft.level.clip(new ClipContext(
                origin,
                requestedEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                minecraft.player));
        double obstructionDistance = hit.getType() == HitResult.Type.MISS
                ? RANGE
                : Math.max(0.8, origin.distanceTo(hit.getLocation()) - 0.06);
        float channel = ClientMedusaVision.channelProgress(now);
        float contact = ClientMedusaVision.contactProgress(now);
        double visibleReach = obstructionDistance
                * (0.10 + 0.90 * channel);
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        Vec3 localOrigin = origin.subtract(camera);
        Vec3 right = perpendicular(direction);
        Vec3 up = right.cross(direction).normalize();
        float time = now / 1_000_000_000.0F;

        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        collector.submitCustomGeometry(
                poseStack,
                EchoRenderTypes.MEDUSA_GAZE,
                (pose, consumer) -> gazeGeometry(
                        pose,
                        consumer,
                        localOrigin,
                        direction,
                        right,
                        up,
                        visibleReach,
                        channel,
                        contact,
                        time));
    }

    private static void gazeGeometry(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 origin,
            Vec3 direction,
            Vec3 right,
            Vec3 up,
            double reach,
            float channel,
            float contact,
            float time) {
        float opening = smooth(Math.min(1.0F, channel * 2.5F));
        // The gaze is a moving field rather than one laser. Short serpentine
        // currents repeatedly travel through the whole forward volume without
        // drawing a hard cone boundary or a line from the crosshair.
        for (int strand = 0; strand < 11; strand++) {
            double phase = Math.PI * 2.0 * strand / 11.0;
            double travel = (time * (0.19 + strand * 0.003) + strand * 0.083) % 1.0;
            double centerFraction = 0.10 + travel * 0.82;
            double angularRadius = reach * centerFraction * CONE_TANGENT * 0.52;
            double radialScale = 0.20 + 0.80 * ((strand * 7) % 11) / 10.0;
            Vec3 radial = right.scale(Math.cos(phase) * angularRadius * radialScale)
                    .add(up.scale(Math.sin(phase) * angularRadius * radialScale));
            int segments = 6;
            Vec3 previous = null;
            for (int segment = 0; segment <= segments; segment++) {
                double local = (segment - segments * 0.5) / segments;
                double fraction = Math.clamp(
                        centerFraction + local * 0.12,
                        0.04,
                        0.98);
                double distance = reach * fraction;
                double coil = phase + local * Math.PI * 2.2 + time * 1.25;
                Vec3 curl = right.scale(Math.cos(coil) * (0.035 + fraction * 0.11))
                        .add(up.scale(Math.sin(coil) * (0.035 + fraction * 0.11)));
                Vec3 next = origin
                        .add(direction.scale(distance))
                        .add(radial.scale(fraction / Math.max(centerFraction, 0.04)))
                        .add(curl);
                if (previous != null) {
                    float segmentEnvelope = (float) Math.sin(
                            Math.PI * segment / (segments + 1.0));
                    float distanceFade = (float) (1.0 - fraction * 0.46);
                    ribbonLine(
                            consumer,
                            pose,
                            previous,
                            next,
                            0.007F + 0.005F * opening,
                            color(
                                    0.13F
                                            * opening
                                            * segmentEnvelope
                                            * distanceFade,
                                    strand % 4 == 0 ? STONE_RGB : VENOM_RGB));
                }
                previous = next;
            }
        }

        // Soft irises make contact feel like the whole gaze focusing, not a
        // projectile striking along a beam.
        if (contact > 0.001F) {
            for (int iris = 0; iris < 3; iris++) {
                double distance = reach * (0.34 + iris * 0.22);
                double radius = distance * CONE_TANGENT * (0.20 + contact * 0.12);
                Vec3 center = origin.add(direction.scale(distance));
                int segments = 28;
                Vec3 previous = center.add(right.scale(radius));
                for (int index = 1; index <= segments; index++) {
                    double angle = Math.PI * 2.0 * index / segments;
                    Vec3 next = center
                            .add(right.scale(Math.cos(angle) * radius))
                            .add(up.scale(Math.sin(angle) * radius));
                    float stagger = 1.0F - iris * 0.18F;
                    ribbonLine(
                            consumer,
                            pose,
                            previous,
                            next,
                            0.010F,
                            color(contact * 0.24F * stagger, STONE_RGB));
                    previous = next;
                }
            }
        }
    }

    private static void ribbonLine(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            float width,
            int color) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-8) {
            return;
        }
        direction = direction.normalize();
        Vec3 sideA = perpendicular(direction).scale(width);
        Vec3 sideB = direction.cross(sideA).normalize().scale(width);
        quad(
                consumer,
                pose,
                start.add(sideA),
                start.subtract(sideA),
                end.subtract(sideA),
                end.add(sideA),
                color);
        quad(
                consumer,
                pose,
                start.add(sideB),
                start.subtract(sideB),
                end.subtract(sideB),
                end.add(sideB),
                color);
    }

    private static Vec3 perpendicular(Vec3 direction) {
        Vec3 reference = Math.abs(direction.y) < 0.90
                ? new Vec3(0.0, 1.0, 0.0)
                : new Vec3(1.0, 0.0, 0.0);
        return direction.cross(reference).normalize();
    }

    private static void quad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            int color) {
        consumer.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(color);
        consumer.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(color);
        consumer.addVertex(pose, (float) c.x, (float) c.y, (float) c.z).setColor(color);
        consumer.addVertex(pose, (float) d.x, (float) d.y, (float) d.z).setColor(color);
    }

    private static float smooth(float value) {
        float clamped = Math.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static int color(float alpha, int rgb) {
        return (Math.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | rgb;
    }

    private MedusaGazeRenderer() {
    }
}
