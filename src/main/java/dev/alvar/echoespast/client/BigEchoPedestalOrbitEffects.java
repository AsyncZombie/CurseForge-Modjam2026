package dev.alvar.echoespast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alvar.echoespast.resonance.ResonanceColor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Selected-era altar FX: clock-face orbit ring, luminous hand spokes, and
 * small camera-facing capsule rings around each fragment tip.
 * Palette matches the Philosopher's Stone post effect
 * ({@code ALCHEMICAL_GOLD} / {@code MEMORY_WHITE} / {@code ECHO_COLD} / {@code STONE_HEART}).
 */
final class BigEchoPedestalOrbitEffects {
    /** philosophers_stone.fsh ALCHEMICAL_GOLD */
    private static final int ALCHEMICAL_GOLD = 0xFF9525;
    /** philosophers_stone.fsh MEMORY_WHITE */
    private static final int MEMORY_WHITE = 0xFFF6D2;
    /** philosophers_stone.fsh ECHO_COLD */
    private static final int ECHO_COLD = 0x59C9EB;
    /** philosophers_stone.fsh STONE_HEART */
    private static final int STONE_HEART = 0xB8133C;

    private BigEchoPedestalOrbitEffects() {
    }

    static void submit(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            float age,
            BlockPos blockOrigin,
            float centerX,
            float centerY,
            float centerZ,
            float stoneY,
            float[] fragX,
            float[] fragY,
            float[] fragZ,
            int fragmentCount,
            ResonanceColor ignoredEraColor) {
        if (EchoShaderCompatibility.isShadowPass() || fragmentCount <= 0) {
            return;
        }

        float pulse = 0.88F + 0.12F * (float) Math.sin(age * 0.19F);
        float scroll = age * 0.055F;
        BillboardBasis[] bases = new BillboardBasis[fragmentCount];
        for (int i = 0; i < fragmentCount; i++) {
            bases[i] = billboardAt(blockOrigin, fragX[i], fragY[i], fragZ[i]);
        }

        collector.submitCustomGeometry(
                poseStack,
                EchoRenderTypes.shaderSafe(EchoRenderTypes.ALTAR_ORBIT),
                (pose, consumer) -> {
                    orbitRing(
                            consumer,
                            pose,
                            centerX,
                            centerY - 0.06F,
                            centerZ,
                            null,
                            0.92F,
                            0.11F,
                            scroll,
                            color(0.26F * pulse, ALCHEMICAL_GOLD));
                    for (int i = 0; i < fragmentCount; i++) {
                        tether(
                                consumer,
                                pose,
                                centerX,
                                stoneY,
                                centerZ,
                                fragX[i],
                                fragY[i],
                                fragZ[i],
                                i == 0 ? 0.038F : 0.028F,
                                scroll * (1.4F + i * 0.8F),
                                color(0.38F * pulse, mixRgb(ALCHEMICAL_GOLD, ECHO_COLD, 0.35F)));
                        capsuleRing(
                                consumer,
                                pose,
                                fragX[i],
                                fragY[i],
                                fragZ[i],
                                bases[i],
                                i == 0 ? 0.38F : 0.34F,
                                0.06F,
                                scroll + i * 0.6F,
                                color(0.42F * pulse, ALCHEMICAL_GOLD));
                    }
                });

        // A shaderpack already supplies bloom and exposure. Drawing the native
        // highlight shell over the base orbit doubles its energy and turns the
        // altar into a white lamp, so shaderpack mode deliberately stays
        // single-pass.
        if (EchoShaderCompatibility.isShaderPackActive()) {
            return;
        }

        collector.submitCustomGeometry(
                poseStack,
                EchoRenderTypes.shaderSafe(EchoRenderTypes.ALTAR_ORBIT_GLOW),
                (pose, consumer) -> {
                    orbitRing(
                            consumer,
                            pose,
                            centerX,
                            centerY - 0.03F,
                            centerZ,
                            null,
                            0.98F,
                            0.032F,
                            scroll * 1.4F,
                            color(0.44F * pulse, MEMORY_WHITE));
                    for (int i = 0; i < fragmentCount; i++) {
                        tether(
                                consumer,
                                pose,
                                centerX,
                                stoneY,
                                centerZ,
                                fragX[i],
                                fragY[i],
                                fragZ[i],
                                i == 0 ? 0.016F : 0.012F,
                                scroll * (2.2F + i),
                                color(0.58F * pulse, mixRgb(ALCHEMICAL_GOLD, MEMORY_WHITE, 0.55F)));
                        capsuleRing(
                                consumer,
                                pose,
                                fragX[i],
                                fragY[i],
                                fragZ[i],
                                bases[i],
                                i == 0 ? 0.38F : 0.34F,
                                0.024F,
                                scroll * 1.5F + i * 0.6F,
                                color(0.62F * pulse, mixRgb(ALCHEMICAL_GOLD, MEMORY_WHITE, 0.4F)));
                        capsuleRing(
                                consumer,
                                pose,
                                fragX[i],
                                fragY[i],
                                fragZ[i],
                                bases[i],
                                i == 0 ? 0.26F : 0.22F,
                                0.014F,
                                scroll * 1.8F + i,
                                color(0.32F * pulse, MEMORY_WHITE));
                    }
                    // Soft carmine heart under the stone, same as the Stone post seam.
                    orbitRing(
                            consumer,
                            pose,
                            centerX,
                            stoneY - 0.02F,
                            centerZ,
                            null,
                            0.22F,
                            0.04F,
                            scroll * 0.7F,
                            color(0.28F * pulse, STONE_HEART));
                });
    }

    /**
     * Ring in a plane. When {@code basis} is null the ring lies on XZ (clock
     * face); otherwise it faces the camera using the given billboard axes.
     */
    private static void orbitRing(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float cx,
            float cy,
            float cz,
            BillboardBasis basis,
            float radius,
            float halfWidth,
            float phase,
            int color) {
        int segments = basis == null ? 72 : 48;
        float inner = Math.max(0.04F, radius - halfWidth);
        float outer = radius + halfWidth;
        for (int segment = 0; segment < segments; segment++) {
            float t0 = (float) segment / segments;
            float t1 = (float) (segment + 1) / segments;
            double a0 = Math.PI * 2.0 * t0 + phase;
            double a1 = Math.PI * 2.0 * t1 + phase;
            float ripple0 = 1.0F + 0.028F * (float) Math.sin(a0 * 6.0 + phase * 2.5);
            float ripple1 = 1.0F + 0.028F * (float) Math.sin(a1 * 6.0 + phase * 2.5);
            float u0 = t0 + phase;
            float u1 = t1 + phase;
            Vec3 outer0 = ringPoint(cx, cy, cz, basis, a0, outer * ripple0);
            Vec3 inner0 = ringPoint(cx, cy, cz, basis, a0, inner * ripple0);
            Vec3 inner1 = ringPoint(cx, cy, cz, basis, a1, inner * ripple1);
            Vec3 outer1 = ringPoint(cx, cy, cz, basis, a1, outer * ripple1);
            // Push glow rings slightly toward the camera for a toroidal read.
            if (basis != null) {
                Vec3 lift = basis.normal.scale(0.012D);
                outer0 = outer0.add(lift);
                inner0 = inner0.add(lift);
                inner1 = inner1.add(lift);
                outer1 = outer1.add(lift);
            }
            texQuad(
                    consumer,
                    pose,
                    outer0,
                    u0,
                    1.0F,
                    inner0,
                    u0,
                    0.0F,
                    inner1,
                    u1,
                    0.0F,
                    outer1,
                    u1,
                    1.0F,
                    color);
        }
    }

    private static void capsuleRing(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float cx,
            float cy,
            float cz,
            BillboardBasis basis,
            float radius,
            float halfWidth,
            float phase,
            int color) {
        if (basis == null) {
            return;
        }
        orbitRing(consumer, pose, cx, cy, cz, basis, radius, halfWidth, phase, color);
        // Back plate offset away from camera for a thin shell of volume.
        BillboardBasis back = new BillboardBasis(
                basis.right,
                basis.up,
                basis.normal.scale(-1.0D));
        orbitRing(
                consumer,
                pose,
                (float) (cx - basis.normal.x * 0.03D),
                (float) (cy - basis.normal.y * 0.03D),
                (float) (cz - basis.normal.z * 0.03D),
                back,
                radius * 0.96F,
                halfWidth * 0.85F,
                phase + 0.5F,
                color(Math.max(0.08F, ((color >>> 24) & 0xFF) / 255.0F * 0.55F), color & 0xFFFFFF));
    }

    private static Vec3 ringPoint(
            float cx,
            float cy,
            float cz,
            BillboardBasis basis,
            double angle,
            float radius) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        if (basis == null) {
            return new Vec3(cx + cos * radius, cy, cz + sin * radius);
        }
        return new Vec3(cx, cy, cz)
                .add(basis.right.scale(cos * radius))
                .add(basis.up.scale(sin * radius));
    }

    private static void tether(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float ax,
            float ay,
            float az,
            float bx,
            float by,
            float bz,
            float halfWidth,
            float scroll,
            int color) {
        Vec3 start = new Vec3(ax, ay, az);
        Vec3 end = new Vec3(bx, by, bz);
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.001D) {
            return;
        }
        Vec3 dir = delta.scale(1.0D / length);
        Vec3 side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) {
            side = dir.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize().scale(halfWidth);
        Vec3 up = dir.cross(side).normalize().scale(halfWidth * 0.55F);
        ribbonPlane(consumer, pose, start, end, side, scroll, color);
        ribbonPlane(consumer, pose, start, end, up, scroll + 0.33F, color);
    }

    private static void ribbonPlane(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            Vec3 side,
            float scroll,
            int color) {
        Vec3 a0 = start.add(side);
        Vec3 a1 = start.subtract(side);
        Vec3 b0 = end.add(side);
        Vec3 b1 = end.subtract(side);
        texQuad(consumer, pose, a0, scroll, 0.0F, a1, scroll, 1.0F, b1, scroll + 1.0F, 1.0F, b0, scroll + 1.0F, 0.0F, color);
    }

    private static BillboardBasis billboardAt(
            BlockPos blockOrigin,
            float localX,
            float localY,
            float localZ) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 fragWorld = new Vec3(
                blockOrigin.getX() + localX,
                blockOrigin.getY() + localY,
                blockOrigin.getZ() + localZ);
        Vec3 toCamera = camera.position().subtract(fragWorld);
        if (toCamera.lengthSqr() < 1.0E-8D) {
            return new BillboardBasis(new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1));
        }
        Vec3 normal = toCamera.normalize();
        Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = worldUp.cross(normal);
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D).cross(normal);
        }
        right = right.normalize();
        Vec3 up = normal.cross(right).normalize();
        return new BillboardBasis(right, up, normal);
    }

    private static void texQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 a,
            float u0,
            float v0,
            Vec3 b,
            float u1,
            float v1,
            Vec3 c,
            float u2,
            float v2,
            Vec3 d,
            float u3,
            float v3,
            int color) {
        consumer.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setUv(u0, v0).setColor(color);
        consumer.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setUv(u1, v1).setColor(color);
        consumer.addVertex(pose, (float) c.x, (float) c.y, (float) c.z).setUv(u2, v2).setColor(color);
        consumer.addVertex(pose, (float) d.x, (float) d.y, (float) d.z).setUv(u3, v3).setColor(color);
    }

    private static int color(float alpha, int rgb) {
        return EchoShaderCompatibility.shaderPackExposureColor(alpha, rgb, 0.72F, 0.55F);
    }

    private static int mixRgb(int from, int to, float amount) {
        float t = Math.clamp(amount, 0.0F, 1.0F);
        int r = Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
        int g = Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
        int b = Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return (r << 16) | (g << 8) | b;
    }

    private record BillboardBasis(Vec3 right, Vec3 up, Vec3 normal) {
    }
}
