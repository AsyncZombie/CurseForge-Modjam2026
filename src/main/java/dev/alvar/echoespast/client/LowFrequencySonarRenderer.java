package dev.alvar.echoespast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.server.LowFrequencySonarMath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

/**
 * The long-range lookup remains server-authoritative. Its outbound front is
 * rendered wherever the crest intersects the current camera visibility sphere,
 * so chasing a pulse past the origin's render distance keeps the same wave.
 */
public final class LowFrequencySonarRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BEAM_RGB = 0x83DED0;
    private static final int BEAM_CORE_RGB = 0xE4FFF9;
    private static final double RETURN_PREWARM_DISTANCE = 48.0;
    private static List<EchoSurfaceWaveRenderer.Layer> waveLayers = List.of();
    private static List<ClientLowFrequencySonarState.WaveFront> activeWaves = List.of();
    private static ScreenWaveFrame screenWaveFrame = ScreenWaveFrame.EMPTY;
    private static boolean diagnosticsLogged;
    static final int MAX_SCREEN_WAVES = 16;

    public static void extract(ExtractLevelRenderStateEvent event) {
        long now = System.nanoTime();
        if (!ClientLowFrequencySonarState.isVisualActive(now)) {
            waveLayers = List.of();
            activeWaves = List.of();
            screenWaveFrame = ScreenWaveFrame.EMPTY;
            diagnosticsLogged = false;
            return;
        }

        Vec3 camera = event.getCamera().position();
        // Refresh every extract so render-distance / camera motion mid-pulse
        // never keeps a stale visibility sphere from pulse start.
        ClientLowFrequencySonarState.refreshVisualRange();
        boolean screenSpace = EchoesConfig.POST_PROCESSING.getAsBoolean()
                && ClientEchoState.isPostEffectOperational();
        float intensity = EchoesConfig.INTENSITY.get().floatValue();
        double widthScale = LowFrequencySonarMath.surfaceWidthScale(
                ClientLowFrequencySonarState.speed());
        double visibleRange = ClientLowFrequencySonarState.visualRange();
        double support = widthScale * 1.90 + 2.0;
        float visualEnvelope = ClientLowFrequencySonarState.visualEnvelope(now);
        List<ClientLowFrequencySonarState.PedestalResponse> responses =
                ClientLowFrequencySonarState.responses();
        List<EchoSurfaceWaveRenderer.Layer> nextLayers = new ArrayList<>(responses.size() + 1);
        List<ClientLowFrequencySonarState.WaveFront> nextActiveWaves =
                new ArrayList<>(responses.size() + 1);
        List<WorldScreenWave> nextScreenWaves = new ArrayList<>(responses.size() + 1);

        if (LowFrequencySonarMath.rangeEdgeFade(
                                ClientLowFrequencySonarState.outboundTravelDistance(now),
                                ClientLowFrequencySonarState.range())
                        > 0.001F
                && waveIntersectsVisibleRange(
                        ClientLowFrequencySonarState.origin(),
                        ClientLowFrequencySonarState.outboundRadius(now),
                        camera,
                        visibleRange,
                        support + RETURN_PREWARM_DISTANCE)) {
            double front = ClientLowFrequencySonarState.outboundRadius(now);
            float edgeFade = LowFrequencySonarMath.rangeEdgeFade(
                    front,
                    ClientLowFrequencySonarState.range());
            ClientLowFrequencySonarState.WaveFront outbound =
                    new ClientLowFrequencySonarState.WaveFront(
                            ClientLowFrequencySonarState.origin(),
                            front,
                            widthScale,
                            false);
            nextActiveWaves.add(outbound);
            if (edgeFade > 0.001F
                    && waveIntersectsVisibleRange(
                            outbound.origin(),
                            front,
                            camera,
                            visibleRange,
                            support)) {
                float cleanEntrance = (float) Math.clamp(
                        (front - 2.25) / 2.75,
                        0.0,
                        1.0);
                float waveIntensity = intensity
                        * 0.64F
                        * cleanEntrance
                        * visualEnvelope
                        * edgeFade
                        * LowFrequencySonarMath.signalAttenuation(
                                front,
                                ClientLowFrequencySonarState.range());
                nextScreenWaves.add(new WorldScreenWave(
                        outbound.origin(),
                        outbound.radius(),
                        outbound.widthScale(),
                        false,
                        waveIntensity,
                        0x55E6F2,
                        0.0F));
                if (!screenSpace) {
                    EchoSurfaceWaveRenderer.addLayer(
                            nextLayers,
                            ClientLowFrequencySonarState.facesForWave(outbound),
                            outbound.origin(),
                            outbound.radius(),
                            false,
                            false,
                            waveIntensity,
                            widthScale,
                            event.getFrustum(),
                            camera);
                }
            }
        }

        for (ClientLowFrequencySonarState.PedestalResponse response : responses) {
            Vec3 returnOrigin = response.position().getCenter();
            double front = ClientLowFrequencySonarState.returnRadius(now, response);
            float edgeFade = LowFrequencySonarMath.rangeEdgeFade(
                    front,
                    ClientLowFrequencySonarState.range());
            if (edgeFade > 0.001F
                    && waveIntersectsVisibleRange(
                            returnOrigin,
                            front,
                            camera,
                            visibleRange,
                            support + RETURN_PREWARM_DISTANCE)) {
                ClientLowFrequencySonarState.WaveFront returning =
                        new ClientLowFrequencySonarState.WaveFront(
                                returnOrigin,
                                front,
                                widthScale,
                                true);
                nextActiveWaves.add(returning);
                if (waveIntersectsVisibleRange(
                        returnOrigin,
                        front,
                        camera,
                        visibleRange,
                        support)) {
                    float waveIntensity = intensity
                            * 0.82F
                            * visualEnvelope
                            * edgeFade
                            * LowFrequencySonarMath.signalAttenuation(
                                    front,
                                    ClientLowFrequencySonarState.range());
                    nextScreenWaves.add(new WorldScreenWave(
                            returning.origin(),
                            returning.radius(),
                            returning.widthScale(),
                            true,
                            waveIntensity,
                            response.rgb(),
                            0.0F));
                    if (!screenSpace) {
                        EchoSurfaceWaveRenderer.addLayer(
                                nextLayers,
                                ClientLowFrequencySonarState.facesForWave(returning),
                                returning.origin(),
                                returning.radius(),
                                false,
                                true,
                                waveIntensity,
                                widthScale,
                                event.getFrustum(),
                                camera);
                    }
                }
            }
        }
        waveLayers = screenSpace ? List.of() : List.copyOf(nextLayers);
        activeWaves = screenSpace ? List.of() : List.copyOf(nextActiveWaves);
        screenWaveFrame = screenSpace
                ? createScreenWaveFrame(event, camera, nextScreenWaves)
                : ScreenWaveFrame.EMPTY;
        if (!diagnosticsLogged
                && ClientLowFrequencySonarState.elapsedSeconds(now) >= 0.35) {
            diagnosticsLogged = true;
            LOGGER.info(
                    "Low-frequency wave frame: screenSpace={}, postOperational={},"
                            + " candidates={}, uploaded={}, radius={}, intensity={}",
                    screenSpace,
                    ClientEchoState.isPostEffectOperational(),
                    nextScreenWaves.size(),
                    screenWaveFrame.waves().size(),
                    ClientLowFrequencySonarState.outboundRadius(now),
                    nextScreenWaves.isEmpty()
                            ? 0.0F
                            : nextScreenWaves.getFirst().intensity());
        }
    }

    private static ScreenWaveFrame createScreenWaveFrame(
            ExtractLevelRenderStateEvent event,
            Vec3 camera,
            List<WorldScreenWave> waves) {
        var cameraState = event.getRenderState().cameraRenderState;
        Matrix4f inverseProjection = new Matrix4f(cameraState.projectionMatrix).invert();
        List<ScreenWave> viewWaves = new ArrayList<>(Math.min(MAX_SCREEN_WAVES, waves.size()));
        for (WorldScreenWave wave : waves) {
            if (viewWaves.size() >= MAX_SCREEN_WAVES) {
                break;
            }
            Vec3 relative = wave.origin().subtract(camera);
            Vector3f viewOrigin = new Vector3f(
                    (float) relative.x,
                    (float) relative.y,
                    (float) relative.z);
            cameraState.viewRotationMatrix.transformPosition(viewOrigin);
            viewWaves.add(new ScreenWave(
                    viewOrigin,
                    (float) wave.radius(),
                    (float) wave.widthScale(),
                    wave.returning(),
                    wave.intensity(),
                    new Vector3f(
                            ((wave.rgb() >> 16) & 0xFF) / 255.0F,
                            ((wave.rgb() >> 8) & 0xFF) / 255.0F,
                            (wave.rgb() & 0xFF) / 255.0F),
                    wave.handoffStart()));
        }
        Vector3f aimView = new Vector3f(0.0F, 0.0F, -1.0F);
        float aimCosHalf = 2.0F;
        if (ClientLowFrequencySonarState.isDirectional()) {
            Vec3 worldAim = ClientLowFrequencySonarState.direction();
            aimView.set((float) worldAim.x, (float) worldAim.y, (float) worldAim.z);
            cameraState.viewRotationMatrix.transformDirection(aimView);
            if (aimView.lengthSquared() > 1.0E-6F) {
                aimView.normalize();
            } else {
                aimView.set(0.0F, 0.0F, -1.0F);
            }
            aimCosHalf = LowFrequencySonarMath.coneCosHalfAngle(
                    ClientLowFrequencySonarState.coneDegrees());
        }
        return new ScreenWaveFrame(
                inverseProjection,
                RenderSystem.getDevice().isZZeroToOne(),
                List.copyOf(viewWaves),
                aimView,
                aimCosHalf);
    }

    static ScreenWaveFrame createScreenWaveFrame(
            ExtractLevelRenderStateEvent event,
            Vec3 camera,
            Vec3 origin,
            double radius,
            double widthScale,
            boolean returning,
            float intensity,
            int rgb,
            float handoffStart) {
        return createScreenWaveFrame(
                event,
                camera,
                List.of(new WorldScreenWave(
                        origin,
                        radius,
                        widthScale,
                        returning,
                        intensity,
                        rgb,
                        handoffStart)));
    }

    private static boolean waveIntersectsVisibleRange(
            Vec3 origin,
            double front,
            Vec3 camera,
            double visibleRange,
            double support) {
        return LowFrequencySonarMath.waveIntersectsVisibleRange(
                origin.distanceTo(camera),
                front,
                visibleRange,
                support);
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        long now = System.nanoTime();
        if (!ClientLowFrequencySonarState.isActive(now)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (!EchoesConfig.POST_PROCESSING.getAsBoolean()
                || !ClientEchoState.isPostEffectOperational()) {
            ClientLowFrequencySonarState.updateSurfaceCache(
                    event.getRenderableSections(),
                    activeWaves);
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        EchoSurfaceWaveRenderer.submitLayers(collector, poseStack, camera, waveLayers);

        for (ClientLowFrequencySonarState.PedestalResponse response
                : ClientLowFrequencySonarState.responses()) {
            float hintEnvelope =
                    ClientLowFrequencySonarState.pedestalHintEnvelope(now, response);
            if (hintEnvelope <= 0.001F) {
                continue;
            }
            submitBeam(
                    collector,
                    poseStack,
                    camera,
                    response.position().getCenter().add(0.0, 0.62, 0.0),
                    hintEnvelope,
                    now,
                    response.rgb());
        }
    }

    private static void submitBeam(
            SubmitNodeCollector collector,
            PoseStack poseStack,
            Vec3 camera,
            Vec3 target,
            float fade,
            long now,
            int rgb) {
        Vec3 offset = target.subtract(camera);
        double actualDistance = offset.length();
        if (actualDistance < 0.001) {
            return;
        }
        double renderDistance =
                Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0;
        double displayDistance =
                LowFrequencySonarMath.beamDisplayDistance(actualDistance, renderDistance);
        if (displayDistance <= 0.0) {
            return;
        }
        Vec3 displayOffset = displayDistance < actualDistance
                ? offset.scale(displayDistance / actualDistance)
                : offset;
        float width = (float) Math.clamp(
                0.12 + actualDistance * 0.00048,
                0.12,
                0.42);
        double phase = target.x * 0.17 + target.y * 0.11 + target.z * 0.13;
        float pulse = 0.92F + 0.08F * (float) Math.sin(now / 240_000_000.0 + phase);
        float alpha = 0.82F * fade * pulse;
        float halfHeight = 96.0F
                + 6.0F * (float) Math.sin(now / 410_000_000.0 + phase * 0.7);

        poseStack.pushPose();
        poseStack.translate(
                displayOffset.x,
                displayOffset.y,
                displayOffset.z);
        collector.submitCustomGeometry(
                poseStack,
                EchoRenderTypes.LOW_FREQUENCY_BEAM,
                (pose, consumer) ->
                        beamGeometry(pose, consumer, width, halfHeight, alpha, rgb));
        poseStack.popPose();
    }

    private static void beamGeometry(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            float width,
            float halfHeight,
            float alpha,
            int rgb) {
        int coreRgb = brighten(rgb, 0.72F);
        taperedCross(
                consumer,
                pose,
                width * 2.4F,
                halfHeight,
                alpha * 0.18F,
                rgb);
        taperedCross(
                consumer,
                pose,
                width,
                halfHeight * 0.94F,
                alpha * 0.68F,
                rgb);
        taperedCross(
                consumer,
                pose,
                width * 0.28F,
                halfHeight * 0.78F,
                Math.min(1.0F, alpha * 1.55F),
                coreRgb);
    }

    private static int brighten(int rgb, float whiteMix) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        red = Math.round(red + (255 - red) * whiteMix);
        green = Math.round(green + (255 - green) * whiteMix);
        blue = Math.round(blue + (255 - blue) * whiteMix);
        return (red << 16) | (green << 8) | blue;
    }

    private static void taperedCross(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float width,
            float halfHeight,
            float alpha,
            int rgb) {
        int tip = color(0.0F, rgb);
        int center = color(alpha, rgb);
        consumer.addVertex(pose, 0.0F, -halfHeight, 0.0F).setColor(tip);
        consumer.addVertex(pose, width, 0.0F, 0.0F).setColor(center);
        consumer.addVertex(pose, 0.0F, halfHeight, 0.0F).setColor(tip);
        consumer.addVertex(pose, -width, 0.0F, 0.0F).setColor(center);

        consumer.addVertex(pose, 0.0F, -halfHeight, 0.0F).setColor(tip);
        consumer.addVertex(pose, 0.0F, 0.0F, width).setColor(center);
        consumer.addVertex(pose, 0.0F, halfHeight, 0.0F).setColor(tip);
        consumer.addVertex(pose, 0.0F, 0.0F, -width).setColor(center);
    }

    private static int color(float alpha, int rgb) {
        int clampedAlpha = Math.clamp(Math.round(alpha * 255.0F), 0, 255);
        return (clampedAlpha << 24) | rgb;
    }

    static ScreenWaveFrame screenWaveFrame() {
        return screenWaveFrame;
    }

    record ScreenWaveFrame(
            Matrix4f inverseProjection,
            boolean depthZeroToOne,
            List<ScreenWave> waves,
            Vector3f aimView,
            float aimCosHalf) {
        private static final ScreenWaveFrame EMPTY =
                new ScreenWaveFrame(new Matrix4f(), false, List.of(), new Vector3f(0.0F, 0.0F, -1.0F), 2.0F);

        static ScreenWaveFrame empty() {
            return EMPTY;
        }

        boolean directional() {
            return aimCosHalf <= 1.0F;
        }
    }

    record ScreenWave(
            Vector3f viewOrigin,
            float radius,
            float widthScale,
            boolean returning,
            float intensity,
            Vector3f color,
            float handoffStart) {
    }

    private record WorldScreenWave(
            Vec3 origin,
            double radius,
            double widthScale,
            boolean returning,
            float intensity,
            int rgb,
            float handoffStart) {
    }

    private LowFrequencySonarRenderer() {
    }
}
