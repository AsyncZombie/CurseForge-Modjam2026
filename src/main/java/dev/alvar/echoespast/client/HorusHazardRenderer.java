package dev.alvar.echoespast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alvar.echoespast.network.EyeHazardSignal;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * A small visual language rather than generic glowing boxes: the silhouette
 * identifies the family, while motion and direction communicate how the
 * hazard acts.
 */
public final class HorusHazardRenderer {
    private static final double MAX_RENDER_DISTANCE_SQR = 40.0 * 40.0;
    private static final int PROJECTILE_RGB = 0xFFC95A;
    private static final int LAVA_RGB = 0xFF582E;
    private static final int SPIKES_RGB = 0xF04B45;
    private static final int TRIGGER_RGB = 0xFFD879;
    private static final int EXPLOSIVE_RGB = 0xFF2838;
    private static final int CONTACT_RGB = 0xEE843F;
    private static final int GLYPH_RGB = 0xE8C77A;

    public static void submit(SubmitCustomGeometryEvent event) {
        List<EyeHazardSignal> signals = ClientHorusHazards.signals();
        if (signals.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            return;
        }

        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        long now = System.nanoTime();
        for (EyeHazardSignal signal : signals) {
            Vec3 center = signal.position().getCenter();
            double distanceSqr = center.distanceToSqr(camera);
            if (distanceSqr > MAX_RENDER_DISTANCE_SQR
                    || !level.hasChunkAt(signal.position())) {
                continue;
            }
            float distanceFade = (float) Math.clamp(
                    1.15 - Math.sqrt(distanceSqr) / 42.0,
                    0.18,
                    1.0);
            double phase = now / 300_000_000.0
                    + signal.position().getX() * 0.41
                    + signal.position().getY() * 0.29
                    + signal.position().getZ() * 0.37;
            float pulse = 0.86F + 0.14F * (float) Math.sin(phase);

            poseStack.pushPose();
            poseStack.translate(
                    signal.position().getX() - camera.x,
                    signal.position().getY() - camera.y,
                    signal.position().getZ() - camera.z);
            submitPass(
                    collector,
                    poseStack,
                    level,
                    signal,
                    pulse,
                    0.22F * distanceFade,
                    true);
            submitPass(
                    collector,
                    poseStack,
                    level,
                    signal,
                    pulse,
                    0.82F * distanceFade,
                    false);
            poseStack.popPose();
        }
    }

    private static void submitPass(
            SubmitNodeCollector collector,
            PoseStack poseStack,
            Level level,
            EyeHazardSignal signal,
            float pulse,
            float alpha,
            boolean occluded) {
        collector.submitCustomGeometry(
                poseStack,
                occluded
                        ? EchoRenderTypes.HORUS_HAZARD_OCCLUDED
                        : EchoRenderTypes.HORUS_HAZARD_VISIBLE,
                (pose, consumer) -> geometry(
                        level,
                        signal,
                        pose,
                        consumer,
                        pulse,
                        alpha,
                        occluded));
    }

    private static void geometry(
            Level level,
            EyeHazardSignal signal,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            float pulse,
            float alpha,
            boolean occluded) {
        int rgb = colorFor(signal);
        float line = occluded ? 0.012F : 0.020F;
        switch (signal.type()) {
            case PROJECTILE -> {
                cornerCage(consumer, pose, line, color(alpha * pulse, rgb));
                projectilePath(
                        level,
                        signal.position(),
                        signal.direction(),
                        consumer,
                        pose,
                        line,
                        color(alpha, rgb));
            }
            case LAVA -> lavaSurface(
                    consumer,
                    pose,
                    line,
                    pulse,
                    color(alpha * pulse, rgb));
            case SPIKES -> {
                cornerCage(consumer, pose, line, color(alpha * 0.70F, rgb));
                directionGlyph(
                        consumer,
                        pose,
                        signal.direction(),
                        0.50F,
                        0.46F,
                        line * 1.35F,
                        color(alpha * pulse, rgb));
            }
            case TRIGGER -> triggerGlyph(
                    consumer,
                    pose,
                    line,
                    pulse,
                    color(alpha * pulse, rgb));
            case EXPLOSIVE -> explosiveGlyph(
                    consumer,
                    pose,
                    line,
                    pulse,
                    color(alpha * pulse, rgb));
            case CONTACT -> {
                float inset = 0.05F + (1.0F - pulse) * 0.035F;
                frameBox(
                        consumer,
                        pose,
                        inset,
                        inset,
                        inset,
                        1.0F - inset,
                        1.0F - inset,
                        1.0F - inset,
                        line,
                        color(alpha * pulse, rgb));
            }
            case GLYPH -> glyph(
                    consumer,
                    pose,
                    line,
                    pulse,
                    color(alpha * pulse, rgb));
        }
    }

    private static int colorFor(EyeHazardSignal signal) {
        return switch (signal.type()) {
            case PROJECTILE -> PROJECTILE_RGB;
            case LAVA -> LAVA_RGB;
            case SPIKES -> SPIKES_RGB;
            case TRIGGER -> TRIGGER_RGB;
            case EXPLOSIVE -> EXPLOSIVE_RGB;
            case CONTACT -> CONTACT_RGB;
            case GLYPH -> GLYPH_RGB;
        };
    }

    private static void projectilePath(
            Level level,
            BlockPos origin,
            Direction direction,
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float width,
            int color) {
        Vec3 axis = directionVector(direction);
        double reach = 0.72;
        for (int step = 1; step <= 8; step++) {
            BlockPos position = origin.relative(direction, step);
            var state = level.getBlockState(position);
            if (!state.getCollisionShape(level, position).isEmpty()) {
                reach = Math.max(0.72, step - 0.12);
                break;
            }
            reach = step + 0.72;
        }

        Vec3 center = new Vec3(0.5, 0.5, 0.5);
        for (double offset = 0.72; offset < reach - 0.30; offset += 0.62) {
            Vec3 start = center.add(axis.scale(offset));
            Vec3 end = center.add(axis.scale(Math.min(offset + 0.28, reach - 0.18)));
            ribbonLine(consumer, pose, start, end, width, color);
        }
        Vec3 tip = center.add(axis.scale(reach));
        Vec3 base = tip.subtract(axis.scale(0.34));
        Vec3 sideA = perpendicular(axis).scale(0.20);
        Vec3 sideB = axis.cross(sideA).normalize().scale(0.20);
        ribbonLine(consumer, pose, tip, base.add(sideA), width * 1.25F, color);
        ribbonLine(consumer, pose, tip, base.subtract(sideA), width * 1.25F, color);
        ribbonLine(consumer, pose, tip, base.add(sideB), width * 1.25F, color);
        ribbonLine(consumer, pose, tip, base.subtract(sideB), width * 1.25F, color);
    }

    private static void lavaSurface(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float width,
            float pulse,
            int color) {
        double y = 1.025 + (pulse - 0.86F) * 0.05;
        diamond(
                consumer,
                pose,
                new Vec3(0.5, y, 0.5),
                0.43,
                width * 1.4F,
                color);
        diamond(
                consumer,
                pose,
                new Vec3(0.5, y + 0.035, 0.5),
                0.23,
                width,
                color);
        ribbonLine(
                consumer,
                pose,
                new Vec3(0.20, y, 0.5),
                new Vec3(0.80, y, 0.5),
                width * 0.75F,
                color);
        ribbonLine(
                consumer,
                pose,
                new Vec3(0.5, y, 0.20),
                new Vec3(0.5, y, 0.80),
                width * 0.75F,
                color);
    }

    private static void triggerGlyph(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float width,
            float pulse,
            int color) {
        double y = 0.035;
        diamond(consumer, pose, new Vec3(0.5, y, 0.5), 0.43, width, color);
        diamond(
                consumer,
                pose,
                new Vec3(0.5, y + 0.012, 0.5),
                0.19 + pulse * 0.025,
                width * 1.15F,
                color);
        ribbonLine(
                consumer,
                pose,
                new Vec3(0.5, y, 0.17),
                new Vec3(0.5, 0.32, 0.5),
                width,
                color);
        ribbonLine(
                consumer,
                pose,
                new Vec3(0.5, y, 0.83),
                new Vec3(0.5, 0.32, 0.5),
                width,
                color);
    }

    private static void explosiveGlyph(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float width,
            float pulse,
            int color) {
        float inset = 0.07F - (pulse - 0.86F) * 0.10F;
        cornerCage(consumer, pose, width, color);
        Vec3 center = new Vec3(0.5, 0.5, 0.5);
        double arm = 0.25 + pulse * 0.035;
        ribbonLine(
                consumer,
                pose,
                center.add(-arm, -arm, -arm),
                center.add(arm, arm, arm),
                width * 1.3F,
                color);
        ribbonLine(
                consumer,
                pose,
                center.add(-arm, arm, -arm),
                center.add(arm, -arm, arm),
                width * 1.3F,
                color);
        frameBox(
                consumer,
                pose,
                inset,
                inset,
                inset,
                1.0F - inset,
                1.0F - inset,
                1.0F - inset,
                width * 0.55F,
                color);
    }

    private static void glyph(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float width,
            float pulse,
            int color) {
        double rise = 0.68 + pulse * 0.08;
        diamond(
                consumer,
                pose,
                new Vec3(0.5, rise, 0.5),
                0.34,
                width,
                color);
        directionGlyph(
                consumer,
                pose,
                Direction.UP,
                0.50F,
                0.38F,
                width,
                color);
    }

    private static void directionGlyph(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Direction direction,
            float centerOffset,
            float length,
            float width,
            int color) {
        Vec3 axis = directionVector(direction);
        Vec3 center = new Vec3(0.5, 0.5, 0.5).add(axis.scale(centerOffset - 0.5));
        Vec3 tip = center.add(axis.scale(length * 0.5));
        Vec3 base = center.subtract(axis.scale(length * 0.5));
        Vec3 side = perpendicular(axis).scale(0.16);
        ribbonLine(consumer, pose, base, tip, width, color);
        ribbonLine(
                consumer,
                pose,
                tip,
                tip.subtract(axis.scale(0.18)).add(side),
                width,
                color);
        ribbonLine(
                consumer,
                pose,
                tip,
                tip.subtract(axis.scale(0.18)).subtract(side),
                width,
                color);
    }

    private static void diamond(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 center,
            double radius,
            float width,
            int color) {
        Vec3 north = center.add(0.0, 0.0, -radius);
        Vec3 east = center.add(radius, 0.0, 0.0);
        Vec3 south = center.add(0.0, 0.0, radius);
        Vec3 west = center.add(-radius, 0.0, 0.0);
        ribbonLine(consumer, pose, north, east, width, color);
        ribbonLine(consumer, pose, east, south, width, color);
        ribbonLine(consumer, pose, south, west, width, color);
        ribbonLine(consumer, pose, west, north, width, color);
    }

    private static void cornerCage(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float width,
            int color) {
        float inset = 0.045F;
        float end = 1.0F - inset;
        float corner = 0.24F;
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    float px = x == 0 ? inset : end;
                    float py = y == 0 ? inset : end;
                    float pz = z == 0 ? inset : end;
                    float sx = x == 0 ? 1.0F : -1.0F;
                    float sy = y == 0 ? 1.0F : -1.0F;
                    float sz = z == 0 ? 1.0F : -1.0F;
                    ribbonLine(
                            consumer,
                            pose,
                            new Vec3(px, py, pz),
                            new Vec3(px + sx * corner, py, pz),
                            width,
                            color);
                    ribbonLine(
                            consumer,
                            pose,
                            new Vec3(px, py, pz),
                            new Vec3(px, py + sy * corner, pz),
                            width,
                            color);
                    ribbonLine(
                            consumer,
                            pose,
                            new Vec3(px, py, pz),
                            new Vec3(px, py, pz + sz * corner),
                            width,
                            color);
                }
            }
        }
    }

    private static void frameBox(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ,
            float width,
            int color) {
        for (int y = 0; y <= 1; y++) {
            double py = y == 0 ? minY : maxY;
            ribbonLine(
                    consumer,
                    pose,
                    new Vec3(minX, py, minZ),
                    new Vec3(maxX, py, minZ),
                    width,
                    color);
            ribbonLine(
                    consumer,
                    pose,
                    new Vec3(maxX, py, minZ),
                    new Vec3(maxX, py, maxZ),
                    width,
                    color);
            ribbonLine(
                    consumer,
                    pose,
                    new Vec3(maxX, py, maxZ),
                    new Vec3(minX, py, maxZ),
                    width,
                    color);
            ribbonLine(
                    consumer,
                    pose,
                    new Vec3(minX, py, maxZ),
                    new Vec3(minX, py, minZ),
                    width,
                    color);
        }
        ribbonLine(
                consumer,
                pose,
                new Vec3(minX, minY, minZ),
                new Vec3(minX, maxY, minZ),
                width,
                color);
        ribbonLine(
                consumer,
                pose,
                new Vec3(maxX, minY, minZ),
                new Vec3(maxX, maxY, minZ),
                width,
                color);
        ribbonLine(
                consumer,
                pose,
                new Vec3(maxX, minY, maxZ),
                new Vec3(maxX, maxY, maxZ),
                width,
                color);
        ribbonLine(
                consumer,
                pose,
                new Vec3(minX, minY, maxZ),
                new Vec3(minX, maxY, maxZ),
                width,
                color);
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

    private static Vec3 directionVector(Direction direction) {
        return new Vec3(
                direction.getStepX(),
                direction.getStepY(),
                direction.getStepZ());
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

    private static int color(float alpha, int rgb) {
        int clampedAlpha = Math.clamp(Math.round(alpha * 255.0F), 0, 255);
        return (clampedAlpha << 24) | rgb;
    }

    private HorusHazardRenderer() {
    }
}
