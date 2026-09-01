package dev.alvar.echoespast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import dev.alvar.echoespast.visual.EchoFaceVisibility;
import dev.alvar.echoespast.visual.EchoGhostOccupancy;
import dev.alvar.echoespast.visual.EchoBlockChange;
import dev.alvar.echoespast.visual.EchoPulseTiming;
import dev.alvar.echoespast.visual.EchoProjectionStyle;
import dev.alvar.echoespast.visual.EchoVisualTiming;
import dev.alvar.echoespast.visual.EchoWaveHandoff;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.slf4j.Logger;

public final class ClientEchoRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float GHOST_ENTITY_OPACITY = 0.72F;
    private static List<EchoSurfaceWaveRenderer.Layer> waveLayers = List.of();
    private static LowFrequencySonarRenderer.ScreenWaveFrame screenWaveFrame =
            LowFrequencySonarRenderer.ScreenWaveFrame.empty();
    private static List<GhostEntityState> visibleEntityStates = List.of();
    private static boolean diagnosticsLogged;
    private static boolean projectionSubmitDiagnosticsLogged;

    public static void extract(ExtractLevelRenderStateEvent event) {
        EchoSnapshot snapshot = ClientEchoState.snapshot();
        if (snapshot == null
                || !event.getLevel().dimension().equals(snapshot.dimension())) {
            ClientEchoState.setVisibleGhostModels(List.of());
            ClientEchoState.setVisiblePresentGhostModels(List.of());
            waveLayers = List.of();
            screenWaveFrame = LowFrequencySonarRenderer.ScreenWaveFrame.empty();
            visibleEntityStates = List.of();
            diagnosticsLogged = false;
            projectionSubmitDiagnosticsLogged = false;
            return;
        }
        boolean preparing = ClientEchoState.isPreparationPending();
        if (preparing && ClientEchoState.presentFaces().isEmpty()) {
            ClientEchoState.setVisibleGhostModels(List.of());
            ClientEchoState.setVisiblePresentGhostModels(List.of());
            waveLayers = List.of();
            screenWaveFrame = LowFrequencySonarRenderer.ScreenWaveFrame.empty();
            visibleEntityStates = List.of();
            diagnosticsLogged = false;
            projectionSubmitDiagnosticsLogged = false;
            return;
        }

        long now = System.nanoTime();
        long extractStarted = now;
        long candidateSelectionNanos = 0L;
        long occupancyNanos = 0L;
        long entityExtractionNanos = 0L;
        int rememberedModelCount = 0;
        int presentModelCount = 0;
        int visibleEntityCount = 0;
        double elapsed = ClientEchoState.elapsedSeconds(now);
        float fade = ClientEchoState.fadeAlpha(now);
        float intensity = EchoesConfig.INTENSITY.get().floatValue();
        Vec3 center = ClientEchoState.sonarOrigin();
        EchoPulseTiming pulseTiming = ClientEchoState.pulseTiming();
        boolean stoneTransition =
                !preparing
                        && ClientPhilosophersStoneVision.usesCondensationGeometry(now);
        boolean physicalPast =
                !preparing && ClientPhilosophersStoneVision.isPastMaterialized();
        boolean restoringStone =
                stoneTransition
                        && ClientPhilosophersStoneVision
                                .visual(now)
                                .restoring();
        Vec3 camera = event.getCamera().position();
        if (preparing) {
            // Reconstruction stays unpublished until the staging package is
            // coherent; the outbound crest can still ride present surfaces.
            ClientEchoState.setVisibleGhostModels(List.of());
            ClientEchoState.setVisiblePresentGhostModels(List.of());
            visibleEntityStates = List.of();
        } else {
        long candidateSelectionStarted = System.nanoTime();
        List<ClientEchoState.GhostModel> rememberedSource =
                stoneTransition
                        ? ClientPhilosophersStoneVision.rememberedModels()
                        : physicalPast
                                ? List.of()
                                : ClientEchoState
                                        .visibleRememberedCandidates(
                                                event.getFrustum(),
                                                camera);

        List<ClientEchoState.GhostModel> visibleModels = new ArrayList<>();
        for (ClientEchoState.GhostModel ghost : rememberedSource) {
            if (stoneTransition
                    && ClientPhilosophersStoneVision.ghostPresence(
                                    ghost,
                                    now)
                            <= 0.005F) {
                continue;
            }
            double distance = ghost.travelDistance();
            if ((!ghost.preparedQuads().isEmpty() || !ghost.fallbackBoxes().isEmpty())
                    && (stoneTransition
                            || ghostRevealAlpha(
                                    elapsed,
                                    distance,
                                    pulseTiming) > 0.005F)
                    && event.getFrustum().isVisible(ghost.worldBounds())) {
                visibleModels.add(ghost);
            }
        }
        ClientEchoState.setVisibleGhostModels(visibleModels);

        List<ClientEchoState.GhostModel> visiblePresentModels = new ArrayList<>();
        List<ClientEchoState.GhostModel> presentSource =
                stoneTransition
                        ? ClientPhilosophersStoneVision.presentModels()
                        : physicalPast
                                ? List.of()
                                : ClientEchoState
                                        .visiblePresentCandidates(
                                                event.getFrustum(),
                                                camera);
        for (ClientEchoState.GhostModel ghost : presentSource) {
            boolean visibleDuringStone = stoneTransition
                    && ClientPhilosophersStoneVision.ghostPresence(
                                    ghost,
                                    now)
                            > 0.005F;
            if ((visibleDuringStone
                            || ClientEchoState.shouldHidePresentBlock(
                                    ghost.position()))
                    && (!ghost.preparedQuads().isEmpty() || !ghost.fallbackBoxes().isEmpty())
                    && event.getFrustum().isVisible(ghost.worldBounds())) {
                visiblePresentModels.add(ghost);
            }
        }
        ClientEchoState.setVisiblePresentGhostModels(visiblePresentModels);
        rememberedModelCount = visibleModels.size();
        presentModelCount = visiblePresentModels.size();
        candidateSelectionNanos = System.nanoTime() - candidateSelectionStarted;
        long occupancyStarted = System.nanoTime();
        ClientEchoState.updateGhostOccupancy(
                camera,
                visibleModels,
                visiblePresentModels,
                now);
        occupancyNanos = System.nanoTime() - occupancyStarted;

        long entityExtractionStarted = System.nanoTime();
        List<GhostEntityState> nextEntityStates = new ArrayList<>();
        for (net.minecraft.world.entity.Entity entity :
                stoneTransition
                        ? ClientEchoState
                                .ghostEntitiesForStone(
                                        restoringStone)
                        : physicalPast
                                ? List.<net.minecraft.world.entity.Entity>of()
                                : ClientEchoState.ghostEntities()) {
            double distance = ClientEchoState.travelDistanceTo(
                    BlockPos.containing(entity.position()));
            float reveal = ghostRevealAlpha(
                    elapsed,
                    distance,
                    pulseTiming);
            if (stoneTransition) {
                reveal *= ClientPhilosophersStoneVision
                        .ghostPresence(
                                entity.position(),
                                now);
            }
            if (reveal <= 0.005F
                    || !event.getFrustum().isVisible(entity.getBoundingBox().inflate(0.25))) {
                continue;
            }
            try {
                EntityRenderState state = Minecraft.getInstance()
                        .getEntityRenderDispatcher()
                        .extractEntity(entity, 0.0F);
                state.nameTag = null;
                state.scoreText = null;
                state.displayFireAnimation = false;
                state.outlineColor = 0;
                state.shadowRadius = 0.0F;
                state.shadowPieces.clear();
                nextEntityStates.add(new GhostEntityState(state, reveal));
            } catch (RuntimeException ignored) {
                // One unsupported modded renderer must not suppress the rest of the echo.
            }
        }
        visibleEntityStates = List.copyOf(nextEntityStates);
        visibleEntityCount = visibleEntityStates.size();
        entityExtractionNanos = System.nanoTime() - entityExtractionStarted;
        }

        long waveBuildStarted = System.nanoTime();
        List<EchoSurfaceWaveRenderer.Layer> nextWaveLayers = new ArrayList<>(4);
        ClientEchoState.ensureSurfaceGeometryFallback();
        boolean screenSpaceWave = !stoneTransition
                && !physicalPast
                && EchoesConfig.POST_PROCESSING.getAsBoolean()
                && ClientEchoState.isPostEffectOperational();
        double localWaveRadius = ClientEchoState.localWaveRadius();
        screenWaveFrame = LowFrequencySonarRenderer.ScreenWaveFrame.empty();
        float crestEnvelope = pulseTiming.crestEnvelope(elapsed);
        if (pulseTiming.isOutbound(elapsed) || pulseTiming.isPerimeterHold(elapsed)) {
            double front = pulseTiming.isPerimeterHold(elapsed)
                    ? pulseTiming.radius()
                    : pulseTiming.outboundRadius(elapsed);
            float cleanEntrance = (float) EchoVisualTiming.smoothStep(
                    Math.clamp((front - 2.25) / 2.75, 0.0, 1.0));
            float waveIntensity = fade * intensity * cleanEntrance * crestEnvelope;
            float localIntensity = waveIntensity * (screenSpaceWave
                    ? (float) EchoWaveHandoff.localWeight(front, localWaveRadius)
                    : 1.0F);
            EchoSurfaceWaveRenderer.addRadialLayer(
                    nextWaveLayers,
                    ClientEchoState.presentFaces(),
                    center,
                    front,
                    false,
                    localIntensity,
                    event.getFrustum(),
                    camera);
            if (screenSpaceWave) {
                screenWaveFrame = LowFrequencySonarRenderer.createScreenWaveFrame(
                        event,
                        camera,
                        center,
                        front,
                        1.0,
                        false,
                        waveIntensity,
                        0x68C8E6,
                        (float) EchoWaveHandoff.screenStart(localWaveRadius));
            } else if (!stoneTransition && !physicalPast) {
                EchoSurfaceWaveRenderer.addLayer(
                        nextWaveLayers,
                        ClientEchoState.visibleTemplateWaveFaces(
                                event.getFrustum(),
                                camera,
                                center,
                                front,
                                false),
                        center,
                        front,
                        false,
                        waveIntensity,
                        event.getFrustum(),
                        camera);
            }
        } else if (pulseTiming.isReturning(elapsed)) {
            double front = pulseTiming.returnRadius(elapsed);
            float localWeight = screenSpaceWave
                    ? (float) EchoWaveHandoff.localWeight(front, localWaveRadius)
                    : 1.0F;
            EchoSurfaceWaveRenderer.addRadialLayer(
                    nextWaveLayers,
                    ClientEchoState.returnCarrierFaces(),
                    center,
                    front,
                    true,
                    fade * intensity * 0.54F * localWeight * crestEnvelope,
                    event.getFrustum(),
                    camera);
            EchoSurfaceWaveRenderer.addRadialLayer(
                    nextWaveLayers,
                    ClientEchoState.memoryEchoFaces(),
                    center,
                    front,
                    true,
                    fade * intensity * 0.96F * localWeight * crestEnvelope,
                    event.getFrustum(),
                    camera);
            if (screenSpaceWave) {
                screenWaveFrame = LowFrequencySonarRenderer.createScreenWaveFrame(
                        event,
                        camera,
                        center,
                        front,
                        1.0,
                        true,
                        fade * intensity * 0.96F * crestEnvelope,
                        0x76D5EC,
                        (float) EchoWaveHandoff.screenStart(localWaveRadius));
            } else if (!stoneTransition && !physicalPast) {
                EchoSurfaceWaveRenderer.addLayer(
                        nextWaveLayers,
                        ClientEchoState.visibleTemplateWaveFaces(
                                event.getFrustum(),
                                camera,
                                center,
                                front,
                                true),
                        center,
                        front,
                        true,
                        fade * intensity * 0.96F * crestEnvelope,
                        event.getFrustum(),
                        camera);
            }
        }
        if (stoneTransition) {
            ClientPhilosophersStoneVision.Visual stone =
                    ClientPhilosophersStoneVision.visual(now);
            if (!ClientPhilosophersStoneVision
                    .isPostEffectOperational()) {
                EchoSurfaceWaveRenderer.addCondensationLayer(
                        nextWaveLayers,
                        ClientPhilosophersStoneVision.surfaceFaces(),
                        stone.center(),
                        stone.halfExtents(),
                        stone.front(),
                        stone.restoring(),
                        stone.elapsedSeconds(),
                        stone.strength() * intensity * 0.92F,
                        event.getFrustum(),
                        camera);
            }
        }
        waveLayers = List.copyOf(nextWaveLayers);
        int waveFaceCount = 0;
        for (EchoSurfaceWaveRenderer.Layer layer : waveLayers) {
            waveFaceCount += layer.faces().size();
        }
        long waveBuildNanos = System.nanoTime() - waveBuildStarted;
        PastEchoRenderProfiler.recordExtract(
                System.nanoTime() - extractStarted,
                candidateSelectionNanos,
                occupancyNanos,
                entityExtractionNanos,
                waveBuildNanos,
                rememberedModelCount,
                presentModelCount,
                visibleEntityCount,
                waveFaceCount);
        PastEchoRenderProfiler.logIfReady(LOGGER, now);
        if (!diagnosticsLogged && elapsed >= 0.35) {
            diagnosticsLogged = true;
            LOGGER.info(
                    "Past Echo wave frame: path={}, layers={}, cachedFaces={},"
                            + " routedCells={}, radius={}, localRadius={}, postOperational={}",
                    screenSpaceWave ? "continuous-depth" : "routed-geometry-fallback",
                    waveLayers.size(),
                    ClientEchoState.presentFaces().size(),
                    ClientEchoState.arrivalField().reachedCells(),
                    pulseTiming.outboundRadius(elapsed),
                    localWaveRadius,
                    ClientEchoState.isPostEffectOperational());
        }
    }

    static LowFrequencySonarRenderer.ScreenWaveFrame screenWaveFrame() {
        return screenWaveFrame;
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        EchoSnapshot snapshot = ClientEchoState.snapshot();
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot == null || minecraft.level == null
                || !minecraft.level.dimension().equals(snapshot.dimension())) {
            return;
        }

        List<ClientEchoState.GhostModel> models = ClientEchoState.visibleGhostModels();
        List<ClientEchoState.GhostModel> presentModels = ClientEchoState.visiblePresentGhostModels();
        boolean shadowPass = EchoShaderCompatibility.isShadowPass();
        // ADDED stand-ins are historical air: never write them into the shader
        // shadow map or they cast solid sun shadows despite the light fix.
        if (shadowPass) {
            presentModels = List.of();
        }
        if (models.isEmpty()
                && presentModels.isEmpty()
                && waveLayers.isEmpty()
                && visibleEntityStates.isEmpty()) {
            return;
        }

        long now = System.nanoTime();
        double elapsed = ClientEchoState.elapsedSeconds(now);
        float fade = ClientEchoState.fadeAlpha(now);
        float intensity = EchoesConfig.INTENSITY.get().floatValue();
        EchoPulseTiming pulseTiming = ClientEchoState.pulseTiming();
        boolean stoneTransition =
                ClientPhilosophersStoneVision.usesCondensationGeometry(now);
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        submitGhostBatch(
                collector,
                poseStack,
                models,
                presentModels,
                camera,
                now,
                elapsed,
                fade,
                intensity,
                pulseTiming,
                stoneTransition,
                shadowPass);

        EchoSurfaceWaveRenderer.submitLayers(collector, poseStack, camera, waveLayers);
        long entitySubmitStarted = System.nanoTime();
        submitGhostEntities(event, poseStack, camera, minecraft, fade);
        PastEchoRenderProfiler.recordEntitySubmit(
                shadowPass,
                System.nanoTime() - entitySubmitStarted);
    }

    private static void submitGhostBatch(
            SubmitNodeCollector collector,
            PoseStack poseStack,
            List<ClientEchoState.GhostModel> rememberedModels,
            List<ClientEchoState.GhostModel> presentModels,
            Vec3 camera,
            long now,
            double elapsed,
            float fade,
            float intensity,
            EchoPulseTiming pulseTiming,
            boolean stoneTransition,
            boolean shadowPass) {
        if (rememberedModels.isEmpty() && presentModels.isEmpty()) {
            return;
        }
        submitGhostGeometry(
                collector,
                poseStack,
                rememberedModels,
                presentModels,
                EchoRenderTypes.REMEMBERED_SURFACE,
                camera,
                now,
                elapsed,
                fade,
                intensity,
                pulseTiming,
                stoneTransition,
                shadowPass,
                true);
    }

    private static void submitGhostGeometry(
            SubmitNodeCollector collector,
            PoseStack poseStack,
            List<ClientEchoState.GhostModel> rememberedModels,
            List<ClientEchoState.GhostModel> presentModels,
            net.minecraft.client.renderer.rendertype.RenderType renderType,
            Vec3 camera,
            long now,
            double elapsed,
            float fade,
            float intensity,
            EchoPulseTiming pulseTiming,
            boolean stoneTransition,
            boolean shadowPass,
            boolean profile) {
        int inputModels = rememberedModels.size() + presentModels.size();
        int inputQuads = preparedQuadCount(rememberedModels)
                + preparedQuadCount(presentModels);
        int inputFallbackBoxes = fallbackBoxCount(rememberedModels)
                + fallbackBoxCount(presentModels);
        collector.submitCustomGeometry(
                poseStack,
                renderType,
                (basePose, consumer) -> {
                    boolean profileSubmission = profile
                            && !projectionSubmitDiagnosticsLogged
                            && elapsed >= pulseTiming.outboundEndSeconds();
                    long submissionStarted = System.nanoTime();
                    PoseStack batchPose = new PoseStack();
                    batchPose.mulPose(basePose.pose());
                    QuadInstance quadInstance = new QuadInstance();
                    double translatedX = 0.0;
                    double translatedY = 0.0;
                    double translatedZ = 0.0;

                    for (ClientEchoState.GhostModel ghost : rememberedModels) {
                        float visibleAlpha;
                        if (stoneTransition) {
                            visibleAlpha = ClientPhilosophersStoneVision.rememberedOpacity(
                                    ghost,
                                    intensity,
                                    fade,
                                    now);
                        } else {
                            float reveal = ghostRevealAlpha(
                                    elapsed,
                                    ghost.travelDistance(),
                                    pulseTiming);
                            if (reveal <= 0.005F) {
                                continue;
                            }
                            visibleAlpha = Math.clamp(
                                    EchoProjectionStyle.rememberedBaseOpacity(
                                                    ghost.change(),
                                                    intensity)
                                            * fade
                                            * reveal,
                                    0.0F,
                                    0.92F);
                        }
                        // Occupancy dissolve is only for hollow MISSING shells.
                        // ADDED/REPLACED keep stable opacity (approach fade looked
                        // like flickering on rubble stand-ins).
                        if (ghost.change() == EchoBlockChange.Kind.MISSING) {
                            visibleAlpha *= ClientEchoState.ghostOccupancyVisibility(
                                    ghost.position());
                        }
                        if (visibleAlpha <= 0.005F) {
                            continue;
                        }
                        double targetX = ghost.position().getX()
                                + ghost.modelOffset().x
                                - camera.x;
                        double targetY = ghost.position().getY()
                                + ghost.modelOffset().y
                                - camera.y;
                        double targetZ = ghost.position().getZ()
                                + ghost.modelOffset().z
                                - camera.z;
                        batchPose.translate(
                                targetX - translatedX,
                                targetY - translatedY,
                                targetZ - translatedZ);
                        translatedX = targetX;
                        translatedY = targetY;
                        translatedZ = targetZ;
                        // Occupancy clears the camera neighbourhood; keep only
                        // outward face culling so the hollow shell reads as solid
                        // faces instead of a transparent wash of back faces.
                        submitQuads(
                                batchPose.last(),
                                consumer,
                                ghost,
                                packedWhite(visibleAlpha),
                                camera,
                                quadInstance);
                    }

                    for (ClientEchoState.GhostModel ghost : presentModels) {
                        float visibleAlpha = stoneTransition
                                ? ClientPhilosophersStoneVision.presentOpacity(
                                        ghost,
                                        intensity,
                                        fade,
                                        now)
                                : EchoProjectionStyle.presentTargetOpacity(
                                                ghost.change(),
                                                intensity)
                                        * fade;
                        // Present ADDED stand-ins must not occupancy-fade: that
                        // read as flickering on rubble while the live mesh remeshes.
                        if (visibleAlpha <= 0.005F) {
                            continue;
                        }
                        double targetX = ghost.position().getX()
                                + ghost.modelOffset().x
                                - camera.x;
                        double targetY = ghost.position().getY()
                                + ghost.modelOffset().y
                                - camera.y;
                        double targetZ = ghost.position().getZ()
                                + ghost.modelOffset().z
                                - camera.z;
                        // Shrink camera-relative offset slightly so ADDED stand-ins
                        // win depth against the live solid until remesh hides it.
                        if (ghost.change() == EchoBlockChange.Kind.ADDED) {
                            double length = Math.sqrt(
                                    targetX * targetX + targetY * targetY + targetZ * targetZ);
                            if (length > 1.0E-4) {
                                double scale = Math.max(0.0, 1.0 - 0.0025 / length);
                                targetX *= scale;
                                targetY *= scale;
                                targetZ *= scale;
                            }
                        }
                        batchPose.translate(
                                targetX - translatedX,
                                targetY - translatedY,
                                targetZ - translatedZ);
                        translatedX = targetX;
                        translatedY = targetY;
                        translatedZ = targetZ;
                        submitQuads(
                                batchPose.last(),
                                consumer,
                                ghost,
                                packedWhite(visibleAlpha),
                                camera,
                                quadInstance);
                    }
                    long submissionNanos = System.nanoTime() - submissionStarted;
                    PastEchoRenderProfiler.recordGeometry(
                            shadowPass,
                            submissionNanos,
                            inputModels,
                            inputQuads,
                            inputFallbackBoxes);
                    if (profileSubmission) {
                        projectionSubmitDiagnosticsLogged = true;
                        LOGGER.info(
                                "Past Echo projection submit profile: rememberedModels={}, presentModels={}, preparedQuads={}, fallbackBoxes={}, blockBatches=1, geometryMs={}",
                                rememberedModels.size(),
                                presentModels.size(),
                                inputQuads,
                                inputFallbackBoxes,
                                submissionNanos / 1_000_000.0);
                    }
                });
    }

    private static int preparedQuadCount(
            List<ClientEchoState.GhostModel> models) {
        int count = 0;
        for (ClientEchoState.GhostModel model : models) {
            count += model.preparedQuads().size();
        }
        return count;
    }

    private static int fallbackBoxCount(
            List<ClientEchoState.GhostModel> models) {
        int count = 0;
        for (ClientEchoState.GhostModel model : models) {
            count += model.fallbackBoxes().size();
        }
        return count;
    }

    private static int packedWhite(float alpha) {
        return ARGB.color(
                Math.clamp(Math.round(alpha * 255.0F), 0, 255),
                255,
                255,
                255);
    }

    private static void submitQuads(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            ClientEchoState.GhostModel ghost,
            int packedColor,
            Vec3 camera,
            QuadInstance quadInstance) {
        if (ghost.preparedQuads().isEmpty()) {
            submitFallbackBoxes(
                    pose,
                    consumer,
                    ghost,
                    ghost.lightCoords(),
                    packedColor,
                    camera);
            return;
        }
        for (ClientEchoState.PreparedGhostQuad preparedQuad : ghost.preparedQuads()) {
            float faceReveal = 1.0F;
            if (preparedQuad.occupancyNeighborFace() != null
                    && ghost.change() != EchoBlockChange.Kind.ADDED) {
                faceReveal = EchoGhostOccupancy.sharedFaceReveal(
                        ClientEchoState.ghostOccupancyVisibility(
                                BlockPos.offset(
                                        ghost.position().asLong(),
                                        preparedQuad.occupancyNeighborFace())));
                if (faceReveal <= 0.005F) {
                    continue;
                }
            }
            if (!ghost.twoSided()
                    && ghost.change() != EchoBlockChange.Kind.ADDED
                    && !quadPointsTowardCamera(
                    preparedQuad,
                    ghost,
                    camera)) {
                continue;
            }
            int faceColor = faceReveal >= 0.999F
                    ? packedColor
                    : ARGB.color(
                            Math.clamp(
                                    Math.round(ARGB.alpha(packedColor) * faceReveal),
                                    0,
                                    255),
                            ARGB.red(packedColor),
                            ARGB.green(packedColor),
                            ARGB.blue(packedColor));
            quadInstance.setColor(ARGB.multiply(faceColor, preparedQuad.tint()));
            quadInstance.setLightCoords(preparedQuad.lightCoords());
            quadInstance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
            consumer.putBakedQuad(pose, preparedQuad.quad(), quadInstance);
        }
    }

    private static boolean quadPointsTowardCamera(
            ClientEchoState.PreparedGhostQuad quad,
            ClientEchoState.GhostModel ghost,
            Vec3 camera) {
        double centerX = ghost.position().getX()
                + ghost.modelOffset().x
                + quad.centerX();
        double centerY = ghost.position().getY()
                + ghost.modelOffset().y
                + quad.centerY();
        double centerZ = ghost.position().getZ()
                + ghost.modelOffset().z
                + quad.centerZ();
        return (camera.x - centerX) * quad.normalX()
                        + (camera.y - centerY) * quad.normalY()
                        + (camera.z - centerZ) * quad.normalZ()
                > 1.0E-5;
    }

    private static void submitGhostEntities(
            SubmitCustomGeometryEvent event,
            PoseStack poseStack,
            Vec3 camera,
            Minecraft minecraft,
            float fade) {
        for (GhostEntityState ghost : visibleEntityStates) {
            try {
                EntityRenderState state = ghost.state();
                float opacity = GHOST_ENTITY_OPACITY * fade * ghost.reveal();
                if (opacity <= 0.005F) {
                    continue;
                }
                minecraft.getEntityRenderDispatcher().submit(
                        state,
                        event.getLevelRenderState().cameraRenderState,
                        state.x - camera.x,
                        state.y - camera.y,
                        state.z - camera.z,
                        poseStack,
                        new GhostSubmitNodeCollector(event.getSubmitNodeCollector(), opacity));
            } catch (RuntimeException ignored) {
                // Keep rendering the remaining memory if one custom entity renderer rejects its frozen state.
            }
        }
    }

    private static void submitFallbackBoxes(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            ClientEchoState.GhostModel ghost,
            ClientEchoState.DirectionalLight lightCoords,
            int packedColor,
            Vec3 camera) {
        TextureAtlasSprite sprite = ghost.fallbackSprite();
        if (sprite == null) {
            return;
        }
        for (AABB box : ghost.fallbackBoxes()) {
            submitFallbackBox(
                    pose,
                    consumer,
                    ghost,
                    box,
                    sprite,
                    lightCoords,
                    packedColor,
                    camera);
        }
    }

    private static void submitFallbackBox(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            ClientEchoState.GhostModel ghost,
            AABB box,
            TextureAtlasSprite sprite,
            ClientEchoState.DirectionalLight lightCoords,
            int packedColor,
            Vec3 camera) {
        double x0 = box.minX;
        double y0 = box.minY;
        double z0 = box.minZ;
        double x1 = box.maxX;
        double y1 = box.maxY;
        double z1 = box.maxZ;
        submitTexturedFace(pose, consumer, ghost, sprite, packedColor,
                lightCoords.at(Direction.DOWN), camera, Direction.DOWN,
                new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1),
                new Vec3(0.0, -1.0, 0.0));
        submitTexturedFace(pose, consumer, ghost, sprite, packedColor,
                lightCoords.at(Direction.UP), camera, Direction.UP,
                new Vec3(x0, y1, z0), new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0),
                new Vec3(0.0, 1.0, 0.0));
        submitTexturedFace(pose, consumer, ghost, sprite, packedColor,
                lightCoords.at(Direction.NORTH), camera, Direction.NORTH,
                new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y0, z0),
                new Vec3(0.0, 0.0, -1.0));
        submitTexturedFace(pose, consumer, ghost, sprite, packedColor,
                lightCoords.at(Direction.SOUTH), camera, Direction.SOUTH,
                new Vec3(x0, y0, z1), new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1),
                new Vec3(0.0, 0.0, 1.0));
        submitTexturedFace(pose, consumer, ghost, sprite, packedColor,
                lightCoords.at(Direction.WEST), camera, Direction.WEST,
                new Vec3(x0, y0, z0), new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), new Vec3(x0, y1, z0),
                new Vec3(-1.0, 0.0, 0.0));
        submitTexturedFace(pose, consumer, ghost, sprite, packedColor,
                lightCoords.at(Direction.EAST), camera, Direction.EAST,
                new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x1, y0, z1),
                new Vec3(1.0, 0.0, 0.0));
    }

    private static void submitTexturedFace(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            ClientEchoState.GhostModel ghost,
            TextureAtlasSprite sprite,
            int packedColor,
            int lightCoords,
            Vec3 camera,
            Direction face,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            Vec3 normal) {
        if (ghost.change() != EchoBlockChange.Kind.ADDED
                && !ClientEchoState.shouldDrawFallbackFace(ghost.position(), face)) {
            return;
        }
        Vec3 localCenter = a.add(b).add(c).add(d).scale(0.25);
        Vec3 worldCenter = new Vec3(
                ghost.position().getX(),
                ghost.position().getY(),
                ghost.position().getZ())
                .add(ghost.modelOffset())
                .add(localCenter);
        if (ghost.change() != EchoBlockChange.Kind.ADDED
                && !EchoFaceVisibility.facePointsTowardCamera(camera, worldCenter, normal)) {
            return;
        }
        putTexturedVertex(pose, consumer, a, sprite.getU0(), sprite.getV1(), packedColor, lightCoords, normal);
        putTexturedVertex(pose, consumer, b, sprite.getU1(), sprite.getV1(), packedColor, lightCoords, normal);
        putTexturedVertex(pose, consumer, c, sprite.getU1(), sprite.getV0(), packedColor, lightCoords, normal);
        putTexturedVertex(pose, consumer, d, sprite.getU0(), sprite.getV0(), packedColor, lightCoords, normal);
    }

    private static void putTexturedVertex(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 position,
            float u,
            float v,
            int packedColor,
            int lightCoords,
            Vec3 normal) {
        consumer.addVertex(pose, (float) position.x, (float) position.y, (float) position.z)
                .setColor(packedColor)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static float ghostRevealAlpha(
            double elapsed,
            double distance,
            EchoPulseTiming timing) {
        if (!Double.isFinite(distance)) {
            return 0.0F;
        }
        return EchoVisualTiming.rememberedReveal(
                elapsed,
                distance,
                timing);
    }

    private record GhostEntityState(EntityRenderState state, float reveal) {
    }

    private ClientEchoRenderer() {
    }
}
