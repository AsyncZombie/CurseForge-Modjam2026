package dev.alvar.echoespast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.visual.EchoFaceVisibility;
import dev.alvar.echoespast.visual.EchoRadialWindow;
import dev.alvar.echoespast.visual.EchoSurfaceCrestPath;
import dev.alvar.echoespast.visual.EchoVisualTiming;
import dev.alvar.echoespast.visual.EchoWaveTessellation;
import dev.alvar.echoespast.visual.PhilosophersStoneVisualTiming;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The single implementation of the textured, surface-following sonar crest.
 * Both devices feed their own timing and surface cache into this renderer, so
 * improvements to the pulse cannot visually drift between items.
 */
final class EchoSurfaceWaveRenderer {
    private static final double WAVE_SUPPORT_AHEAD = 0.92;
    private static final double WAVE_SUPPORT_BEHIND = 1.82;
    private static final double ROUTED_FACE_MARGIN = 1.12;
    private static final double CONDENSATION_PULSE_SCALE = 6.4;

    /**
     * Fast path for Past Echo caches, which are sorted once by their distance
     * from the emission point. Only the narrow radial interval around the
     * crest reaches camera and frustum checks.
     */
    static void addRadialLayer(
            List<Layer> layers,
            List<ClientEchoState.ScanFace> faces,
            Vec3 origin,
            double front,
            boolean returning,
            float intensity,
            Frustum frustum,
            Vec3 camera) {
        if (front <= 0.02 || intensity <= 0.001F || faces.isEmpty()) {
            return;
        }
        EchoRadialWindow window = EchoRadialWindow.forPulse(
                front,
                returning,
                1.0,
                WAVE_SUPPORT_AHEAD,
                WAVE_SUPPORT_BEHIND,
                ROUTED_FACE_MARGIN);
        int first = lowerBound(faces, window.minimumDistance());
        int last = upperBound(faces, window.maximumDistance());
        if (first >= last) {
            return;
        }

        List<ClientEchoState.ScanFace> visibleFaces =
                new ArrayList<>(last - first);
        for (int index = first; index < last; index++) {
            ClientEchoState.ScanFace face = faces.get(index);
            if (!face.twoSided()
                    && !EchoFaceVisibility.facePointsTowardCamera(
                            camera,
                            face.center(),
                            face.normal())) {
                continue;
            }
            double centerDelta = travelDelta(
                    face.distance() - front,
                    returning);
            if (centerDelta
                            > WAVE_SUPPORT_AHEAD
                                    + ROUTED_FACE_MARGIN
                    || centerDelta
                            < -WAVE_SUPPORT_BEHIND
                                    - ROUTED_FACE_MARGIN
                    || !frustum.isVisible(
                            new AABB(face.position()).inflate(0.01))) {
                continue;
            }
            visibleFaces.add(face);
        }
        if (!visibleFaces.isEmpty()) {
            layers.add(new Layer(
                    List.copyOf(visibleFaces),
                    origin,
                    front,
                    returning,
                    returning,
                    intensity,
                    1.0,
                    false,
                    0.0,
                    1.0,
                    Vec3.ZERO,
                    true));
        }
    }

    private static int lowerBound(
            List<ClientEchoState.ScanFace> faces,
            double distance) {
        int low = 0;
        int high = faces.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (faces.get(middle).distance() < distance) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static int upperBound(
            List<ClientEchoState.ScanFace> faces,
            double distance) {
        int low = 0;
        int high = faces.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (faces.get(middle).distance() <= distance) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    /**
     * Checks the full rectangular surface, not only its centre. Template
     * patches are intentionally a few blocks wide; centre-only culling made
     * the crest disappear while it crossed their outer edges.
     */
    static boolean pulseIntersectsFace(
            ClientEchoState.ScanFace face,
            Vec3 origin,
            double front,
            boolean returning) {
        return pulseIntersectsFace(face, origin, front, returning, 1.0);
    }

    private static boolean pulseIntersectsFace(
            ClientEchoState.ScanFace face,
            Vec3 origin,
            double front,
            boolean returning,
            double widthScale) {
        double minimum = minimumDistanceToBounds(face, origin);
        double maximum = maximumVertexDistance(face, origin);
        double safeWidth = Math.max(1.0, widthScale);
        double lower = returning
                ? front - (WAVE_SUPPORT_AHEAD + 1.05) * safeWidth
                : front - (WAVE_SUPPORT_BEHIND + 1.05) * safeWidth;
        double upper = returning
                ? front + (WAVE_SUPPORT_BEHIND + 1.05) * safeWidth
                : front + (WAVE_SUPPORT_AHEAD + 1.05) * safeWidth;
        return maximum >= lower && minimum <= upper;
    }

    static AABB faceBounds(ClientEchoState.ScanFace face) {
        double minimumX = Math.min(Math.min(face.a().x, face.b().x),
                Math.min(face.c().x, face.d().x));
        double minimumY = Math.min(Math.min(face.a().y, face.b().y),
                Math.min(face.c().y, face.d().y));
        double minimumZ = Math.min(Math.min(face.a().z, face.b().z),
                Math.min(face.c().z, face.d().z));
        double maximumX = Math.max(Math.max(face.a().x, face.b().x),
                Math.max(face.c().x, face.d().x));
        double maximumY = Math.max(Math.max(face.a().y, face.b().y),
                Math.max(face.c().y, face.d().y));
        double maximumZ = Math.max(Math.max(face.a().z, face.b().z),
                Math.max(face.c().z, face.d().z));
        return new AABB(
                minimumX,
                minimumY,
                minimumZ,
                maximumX,
                maximumY,
                maximumZ).inflate(0.01);
    }

    private static double minimumDistanceToBounds(
            ClientEchoState.ScanFace face,
            Vec3 origin) {
        AABB bounds = faceBounds(face);
        double closestX = Math.clamp(origin.x, bounds.minX, bounds.maxX);
        double closestY = Math.clamp(origin.y, bounds.minY, bounds.maxY);
        double closestZ = Math.clamp(origin.z, bounds.minZ, bounds.maxZ);
        return origin.distanceToSqr(closestX, closestY, closestZ) <= 0.0
                ? 0.0
                : origin.distanceTo(new Vec3(closestX, closestY, closestZ));
    }

    private static double maximumVertexDistance(
            ClientEchoState.ScanFace face,
            Vec3 origin) {
        return Math.max(
                Math.max(face.a().distanceTo(origin), face.b().distanceTo(origin)),
                Math.max(face.c().distanceTo(origin), face.d().distanceTo(origin)));
    }

    static void addLayer(
            List<Layer> layers,
            List<ClientEchoState.ScanFace> faces,
            Vec3 origin,
            double front,
            boolean returning,
            float intensity,
            Frustum frustum,
            Vec3 camera) {
        addLayer(
                layers,
                faces,
                origin,
                front,
                returning,
                returning,
                intensity,
                1.0,
                frustum,
                camera);
    }

    static void addLayer(
            List<Layer> layers,
            List<ClientEchoState.ScanFace> faces,
            Vec3 origin,
            double front,
            boolean returning,
            float intensity,
            double widthScale,
            Frustum frustum,
            Vec3 camera) {
        addLayer(
                layers,
                faces,
                origin,
                front,
                returning,
                returning,
                intensity,
                widthScale,
                frustum,
                camera);
    }

    static void addLayer(
            List<Layer> layers,
            List<ClientEchoState.ScanFace> faces,
            Vec3 origin,
            double front,
            boolean returning,
            boolean returnPalette,
            float intensity,
            double widthScale,
            Frustum frustum,
            Vec3 camera) {
        if (front <= 0.02 || intensity <= 0.001F) {
            return;
        }

        double safeWidthScale = Math.max(1.0, widthScale);
        List<ClientEchoState.ScanFace> visibleFaces = new ArrayList<>();
        for (ClientEchoState.ScanFace face : faces) {
            if (!face.twoSided()
                    && !EchoFaceVisibility.facePointsTowardCamera(camera, face.center(), face.normal())) {
                continue;
            }
            if (!pulseIntersectsFace(
                    face,
                    origin,
                    front,
                    returning,
                    safeWidthScale)) {
                continue;
            }
            if (!frustum.isVisible(faceBounds(face))) {
                continue;
            }
            visibleFaces.add(face);
        }
        if (!visibleFaces.isEmpty()) {
            layers.add(new Layer(
                    List.copyOf(visibleFaces),
                    origin,
                    front,
                    returning,
                    returnPalette,
                    intensity,
                    safeWidthScale,
                    false,
                    0.0,
                    1.0,
                    Vec3.ZERO,
                    false));
        }
    }

    /**
     * Stone-specific entry point. Its front is already an authoritative
     * normalized volume coordinate, so no spherical radius or independent
     * surface warp is allowed to move this crest away from the ghost swap.
     */
    static void addCondensationLayer(
            List<Layer> layers,
            List<ClientEchoState.ScanFace> faces,
            Vec3 origin,
            Vec3 halfExtents,
            double front,
            boolean returning,
            double elapsed,
            float intensity,
            Frustum frustum,
            Vec3 camera) {
        if (intensity <= 0.001F) {
            return;
        }
        List<ClientEchoState.ScanFace> visibleFaces =
                new ArrayList<>();
        for (ClientEchoState.ScanFace face : faces) {
            if (!face.twoSided()
                    && !EchoFaceVisibility.facePointsTowardCamera(
                            camera,
                            face.center(),
                            face.normal())) {
                continue;
            }
            double coordinate =
                    PhilosophersStoneVisualTiming.normalizedCoordinate(
                            face.position().getCenter(),
                            origin,
                            halfExtents);
            double delta = travelDelta(
                    coordinate - front,
                    returning) * CONDENSATION_PULSE_SCALE;
            if (delta > WAVE_SUPPORT_AHEAD + 0.9
                    || delta < -WAVE_SUPPORT_BEHIND - 0.9
                    || !frustum.isVisible(
                            new AABB(face.position()).inflate(0.01))) {
                continue;
            }
            visibleFaces.add(face);
        }
        if (!visibleFaces.isEmpty()) {
            layers.add(new Layer(
                    List.copyOf(visibleFaces),
                    origin,
                    front,
                    returning,
                    false,
                    intensity,
                    1.0,
                    false,
                    elapsed,
                    1.0,
                    halfExtents,
                    false));
        }
    }

    static void addResponseLayer(
            List<Layer> layers,
            List<ClientEchoState.ScanFace> faces,
            Vec3 origin,
            double elapsed,
            double maximumRadius,
            boolean remembered,
            float intensity,
            Frustum frustum,
            Vec3 camera) {
        if (intensity <= 0.001F) {
            return;
        }
        List<ClientEchoState.ScanFace> visibleFaces = new ArrayList<>();
        for (ClientEchoState.ScanFace face : faces) {
            if (!face.twoSided()
                    && !EchoFaceVisibility.facePointsTowardCamera(camera, face.center(), face.normal())) {
                continue;
            }
            float envelope = EchoVisualTiming.surfaceResponseEnvelope(
                    elapsed,
                    face.distance(),
                    maximumRadius,
                    face.response().delaySeconds(),
                    face.response().widthScale());
            if (envelope * face.response().reflectivity() <= 0.004F
                    || !frustum.isVisible(new AABB(face.position()).inflate(0.01))) {
                continue;
            }
            visibleFaces.add(face);
        }
        if (!visibleFaces.isEmpty()) {
            layers.add(new Layer(
                    List.copyOf(visibleFaces),
                    origin,
                    0.0,
                    true,
                    remembered,
                    intensity,
                    1.0,
                    true,
                    elapsed,
                    Math.max(1.0, maximumRadius),
                    Vec3.ZERO,
                    false));
        }
    }

    static void submitLayers(
            SubmitNodeCollector collector,
            PoseStack poseStack,
            Vec3 camera,
            List<Layer> layers) {
        for (Layer layer : layers) {
            if (layer.faces().isEmpty() || layer.intensity() <= 0.001F) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            collector.submitCustomGeometry(
                    poseStack,
                    EchoRenderTypes.WAVE_COLOR,
                    (pose, consumer) -> submitGeometry(pose, consumer, layer));
            collector.submitCustomGeometry(
                    poseStack,
                    EchoRenderTypes.WAVE_MASK,
                    (pose, consumer) -> submitGeometry(pose, consumer, layer));
            poseStack.popPose();
        }
    }

    private static void submitGeometry(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Layer layer) {
        for (ClientEchoState.ScanFace face : layer.faces()) {
            if (!faceCouldContainWave(face, layer)) {
                continue;
            }
            EchoWaveTessellation.Grid subdivisions = EchoWaveTessellation.grid(
                    layer.widthScale(),
                    face.center().distanceTo(layer.origin()),
                    face.a().distanceTo(face.b()),
                    face.a().distanceTo(face.d()));
            for (int u = 0; u < subdivisions.u(); u++) {
                for (int v = 0; v < subdivisions.v(); v++) {
                    double u0 = (double) u / subdivisions.u();
                    double u1 = (double) (u + 1) / subdivisions.u();
                    double v0 = (double) v / subdivisions.v();
                    double v1 = (double) (v + 1) / subdivisions.v();
                    Vec3 a = quadPoint(face, u0, v0);
                    Vec3 b = quadPoint(face, u1, v0);
                    Vec3 c = quadPoint(face, u1, v1);
                    Vec3 d = quadPoint(face, u0, v1);
                    PulseSample pulseA = sampleWaveAt(a, face, u0, v0, layer);
                    PulseSample pulseB = sampleWaveAt(b, face, u1, v0, layer);
                    PulseSample pulseC = sampleWaveAt(c, face, u1, v1, layer);
                    PulseSample pulseD = sampleWaveAt(d, face, u0, v1, layer);
                    float maximumAlpha = Math.max(
                            Math.max(pulseA.alpha(), pulseB.alpha()),
                            Math.max(pulseC.alpha(), pulseD.alpha()));
                    if (maximumAlpha <= 0.002F) {
                        continue;
                    }
                    putWaveVertex(pose, consumer, face, u0, v0, a, pulseA);
                    putWaveVertex(pose, consumer, face, u1, v0, b, pulseB);
                    putWaveVertex(pose, consumer, face, u1, v1, c, pulseC);
                    putWaveVertex(pose, consumer, face, u0, v1, d, pulseD);
                }
            }
        }
    }

    private static boolean faceCouldContainWave(
            ClientEchoState.ScanFace face,
            Layer layer) {
        if (layer.localResponse()) {
            return true;
        }
        if (isNormalizedVolume(layer)) {
            double coordinate =
                    PhilosophersStoneVisualTiming.normalizedCoordinate(
                            face.position().getCenter(),
                            layer.origin(),
                            layer.normalizedHalfExtents());
            double delta = travelDelta(
                    coordinate - layer.front(),
                    layer.returning()) * CONDENSATION_PULSE_SCALE;
            return delta <= WAVE_SUPPORT_AHEAD + 1.05
                    && delta >= -WAVE_SUPPORT_BEHIND - 1.05;
        }
        if (layer.routed() && face.waveDistances().finite()) {
            double minimum = face.waveDistances().minimum();
            double maximum = face.waveDistances().maximum();
            double minimumDelta = layer.returning()
                    ? (layer.front() - maximum) / layer.widthScale()
                    : (minimum - layer.front()) / layer.widthScale();
            double maximumDelta = layer.returning()
                    ? (layer.front() - minimum) / layer.widthScale()
                    : (maximum - layer.front()) / layer.widthScale();
            double margin = 0.32;
            return minimumDelta <= WAVE_SUPPORT_AHEAD + margin
                    && maximumDelta >= -WAVE_SUPPORT_BEHIND - margin;
        }
        return pulseIntersectsFace(
                face,
                layer.origin(),
                layer.front(),
                layer.returning(),
                layer.widthScale());
    }

    private static PulseSample sampleWaveAt(
            Vec3 position,
            ClientEchoState.ScanFace face,
            double faceU,
            double faceV,
            Layer layer) {
        if (layer.localResponse()) {
            return sampleLocalResponse(position, face, layer);
        }
        if (isNormalizedVolume(layer)) {
            double coordinate =
                    PhilosophersStoneVisualTiming.normalizedCoordinate(
                            position,
                            layer.origin(),
                            layer.normalizedHalfExtents());
            double delta = travelDelta(
                    coordinate - layer.front(),
                    layer.returning()) * CONDENSATION_PULSE_SCALE;
            PulseSample pulse = sampleStoneTransmutation(
                    delta,
                    layer.intensity(),
                    position,
                    layer.elapsed());
            float materialWeight =
                    0.46F + face.response().reflectivity() * 0.54F;
            return new PulseSample(
                    pulse.alpha() * materialWeight,
                    pulse.rgb());
        }
        double surfaceDistance = layer.routed()
                ? EchoSurfaceCrestPath.distanceAtPoint(
                        face.waveDistances(),
                        faceU,
                        faceV,
                        face.a(),
                        face.b(),
                        face.c(),
                        face.d(),
                        position,
                        layer.origin())
                : position.distanceTo(layer.origin());
        double warpedDistance = surfaceDistance
                + surfaceWarp(position) * 0.14 * EchoesConfig.DISTORTION.get();
        double delta = travelDelta(
                        warpedDistance - layer.front(),
                        layer.returning())
                / layer.widthScale();
        return samplePulse(
                delta,
                layer.intensity(),
                layer.returnPalette());
    }

    private static PulseSample sampleLocalResponse(
            Vec3 position,
            ClientEchoState.ScanFace face,
            Layer layer) {
        float envelope = EchoVisualTiming.surfaceResponseEnvelope(
                layer.elapsed(),
                face.distance(),
                layer.maximumRadius(),
                face.response().delaySeconds(),
                face.response().widthScale());
        double arrival = EchoVisualTiming.surfaceReturnArrival(
                face.distance(),
                layer.maximumRadius(),
                face.response().delaySeconds());
        double age = layer.elapsed() - arrival;
        double ribbon = 0.72 + 0.28 * Math.cos(
                position.x * 2.15 + position.y * 1.37 + position.z * 1.83 - age * 13.0);
        double soft = Math.clamp(ribbon, 0.25, 1.0);
        int rgb = mixRgb(layer.returnPalette() ? 0x73D8EB : 0x61C4DF, 0xD9FCFF, (float) soft * 0.42F);
        float alpha = (float) soft
                * layer.intensity()
                * envelope
                * face.response().reflectivity();
        return new PulseSample(alpha, rgb);
    }

    private static void putWaveVertex(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            ClientEchoState.ScanFace face,
            double faceU,
            double faceV,
            Vec3 position,
            PulseSample pulse) {
        consumer.addVertex(pose, (float) position.x, (float) position.y, (float) position.z)
                .setUv(
                        quadTextureCoordinate(face, faceU, faceV, true),
                        quadTextureCoordinate(face, faceU, faceV, false))
                .setColor(color(pulse.alpha(), pulse.rgb()));
    }

    private static double travelDelta(double radialDelta, boolean returning) {
        return returning ? -radialDelta : radialDelta;
    }

    private static boolean isNormalizedVolume(Layer layer) {
        return layer.normalizedHalfExtents().lengthSqr() > 0.0;
    }

    private static PulseSample samplePulse(double delta, float intensity, boolean returning) {
        if (delta > WAVE_SUPPORT_AHEAD || delta < -WAVE_SUPPORT_BEHIND) {
            return PulseSample.NONE;
        }

        double precursor = gaussian(delta - 0.38, 0.30) * (returning ? 0.045 : 0.060);
        double corona = gaussian(delta - 0.12, 0.19) * (returning ? 0.18 : 0.14);
        double crest = gaussian(delta, returning ? 0.12 : 0.135) * (returning ? 0.70 : 0.62);
        double filament = gaussian(
                delta + (returning ? 0.075 : 0.095),
                returning ? 0.042 : 0.050) * (returning ? 0.34 : 0.28);
        double echo = gaussian(
                delta + (returning ? 0.34 : 0.42),
                returning ? 0.15 : 0.18) * (returning ? 0.34 : 0.22);
        double wakeEnvelope = delta < -0.18
                ? Math.exp((delta + 0.18) * 1.55)
                * EchoVisualTiming.smoothStep(Math.clamp((-delta - 0.18) / 0.34, 0.0, 1.0))
                : 0.0;
        double wake = wakeEnvelope * (returning ? 0.055 : 0.035);
        double alpha = Math.clamp(
                precursor + corona + crest + filament + echo + wake,
                0.0,
                0.92);
        float whiteHeat = (float) Math.clamp(
                crest * 0.92 + filament * 1.45 + corona * 0.20,
                0.0,
                1.0);
        int rgb = mixRgb(returning ? 0x76D5EC : 0x68C8E6, 0xD8FDFF, whiteHeat);
        return new PulseSample((float) alpha * intensity, rgb);
    }

    /**
     * The Stone is not another sonar pulse. Its visible surface carries a
     * warm alchemical precursor, a white-hot fixation crest, a carmine inner
     * harmonic and finally the cold colour of retained memory.
     */
    private static PulseSample sampleStoneTransmutation(
            double delta,
            float intensity,
            Vec3 position,
            double elapsed) {
        if (delta > WAVE_SUPPORT_AHEAD
                || delta < -WAVE_SUPPORT_BEHIND) {
            return PulseSample.NONE;
        }

        double spatialFlow =
                position.x * 2.37
                        + position.y * 1.61
                        + position.z * 1.93;
        double filament = Math.pow(
                0.5
                        + 0.5
                                * Math.sin(
                                        spatialFlow
                                                - elapsed * 4.6
                                                + Math.sin(
                                                                position.x
                                                                        * 0.71
                                                                        - position.z
                                                                                * 0.83)
                                                        * 1.2),
                7.0);
        double glintSeed = Math.sin(
                        Math.floor(position.x * 3.0) * 12.9898
                                + Math.floor(position.y * 3.0) * 78.233
                                + Math.floor(position.z * 3.0) * 37.719)
                * 43758.5453;
        double glint = Math.pow(
                        0.5
                                + 0.5
                                        * Math.sin(
                                                elapsed * 13.0
                                                        + (glintSeed
                                                                - Math.floor(
                                                                        glintSeed))
                                                                * Math.PI
                                                                * 2.0),
                        18.0)
                * gaussian(delta + 0.30, 0.31);

        double precursor = gaussian(delta - 0.48, 0.28) * 0.11;
        double goldRibbon = gaussian(delta - 0.19, 0.095)
                * (0.24 + filament * 0.14);
        double fixation = gaussian(delta, 0.070) * 0.91;
        double carmine = gaussian(delta + 0.125, 0.085)
                * (0.31 + filament * 0.12);
        double memoryWake = gaussian(delta + 0.46, 0.27)
                * (0.075 + filament * 0.085);
        double spark = glint * 0.46;
        double alpha = Math.clamp(
                precursor
                        + goldRibbon
                        + fixation
                        + carmine
                        + memoryWake
                        + spark,
                0.0,
                0.96);

        float heat = (float) Math.clamp(
                fixation * 1.18
                        + goldRibbon * 0.32
                        + spark,
                0.0,
                1.0);
        float remembered = (float) Math.clamp(
                memoryWake * 3.4,
                0.0,
                0.72);
        int alchemical = mixRgb(
                0xA82E55,
                0xFFF1C9,
                heat);
        int rgb = mixRgb(
                alchemical,
                0x69D4E7,
                remembered);
        return new PulseSample(
                (float) alpha * intensity,
                rgb);
    }

    private static double gaussian(double value, double width) {
        double normalized = value / width;
        return Math.exp(-0.5 * normalized * normalized);
    }

    private static double surfaceWarp(Vec3 position) {
        double broad = Math.sin(position.x * 0.72 + position.y * 0.41 + position.z * 0.58);
        double detail = Math.sin(position.x * 1.63 - position.y * 0.91 + position.z * 1.17);
        return broad * 0.62 + detail * 0.38;
    }

    private static Vec3 quadPoint(ClientEchoState.ScanFace face, double u, double v) {
        Vec3 near = face.a().lerp(face.b(), u);
        Vec3 far = face.d().lerp(face.c(), u);
        return near.lerp(far, v);
    }

    private static float quadTextureCoordinate(
            ClientEchoState.ScanFace face,
            double u,
            double v,
            boolean horizontal) {
        ClientEchoState.WaveUv texture = face.waveUv();
        float a = horizontal ? UVPair.unpackU(texture.a()) : UVPair.unpackV(texture.a());
        float b = horizontal ? UVPair.unpackU(texture.b()) : UVPair.unpackV(texture.b());
        float c = horizontal ? UVPair.unpackU(texture.c()) : UVPair.unpackV(texture.c());
        float d = horizontal ? UVPair.unpackU(texture.d()) : UVPair.unpackV(texture.d());
        double near = a + (b - a) * u;
        double far = d + (c - d) * u;
        return (float) (near + (far - near) * v);
    }

    private static int mixRgb(int from, int to, float amount) {
        float clamped = Math.clamp(amount, 0.0F, 1.0F);
        int red = Math.round(
                ((from >> 16) & 0xFF)
                        + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * clamped);
        int green = Math.round(
                ((from >> 8) & 0xFF)
                        + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * clamped);
        int blue = Math.round(
                (from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * clamped);
        return (red << 16) | (green << 8) | blue;
    }

    private static int color(float alpha, int rgb) {
        int clampedAlpha = Math.clamp(Math.round(alpha * 255.0F), 0, 255);
        return (clampedAlpha << 24) | rgb;
    }

    record Layer(
            List<ClientEchoState.ScanFace> faces,
            Vec3 origin,
            double front,
            boolean returning,
            boolean returnPalette,
            float intensity,
            double widthScale,
            boolean localResponse,
            double elapsed,
            double maximumRadius,
            Vec3 normalizedHalfExtents,
            boolean routed) {
    }

    private record PulseSample(float alpha, int rgb) {
        private static final PulseSample NONE = new PulseSample(0.0F, 0);
    }

    private EchoSurfaceWaveRenderer() {
    }
}
