package dev.alvar.echoespast.client;

import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.item.PastEchoMemory;
import dev.alvar.echoespast.mixin.client.LivingEntityAccessor;
import dev.alvar.echoespast.mixin.client.WalkAnimationStateAccessor;
import dev.alvar.echoespast.network.EchoStatePayload;
import dev.alvar.echoespast.resonance.EchoSiteType;
import dev.alvar.echoespast.snapshot.EchoMemoryRevision;
import dev.alvar.echoespast.snapshot.EchoSiteAdditions;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import dev.alvar.echoespast.snapshot.EchoProjectionBudget;
import dev.alvar.echoespast.snapshot.SnapshotBlock;
import dev.alvar.echoespast.snapshot.SnapshotEntity;
import dev.alvar.echoespast.snapshot.SnapshotEntityFrame;
import dev.alvar.echoespast.snapshot.SnapshotEntityIO;
import dev.alvar.echoespast.visual.EchoBlockChange;
import dev.alvar.echoespast.visual.EchoCacheHandoff;
import dev.alvar.echoespast.visual.EchoGhostOccupancy;
import dev.alvar.echoespast.visual.EchoMaterialResponse;
import dev.alvar.echoespast.visual.EchoOccluderPropagation;
import dev.alvar.echoespast.visual.EchoOccluderDistances;
import dev.alvar.echoespast.visual.EchoPastLight;
import dev.alvar.echoespast.visual.EchoPostEffects;
import dev.alvar.echoespast.visual.EchoPulseTiming;
import dev.alvar.echoespast.visual.EchoSurfaceCrestPath;
import dev.alvar.echoespast.visual.EchoVisualTiming;
import dev.alvar.echoespast.visual.EchoWaveVolume;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class ClientEchoState {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double SCAN_SURFACE_OFFSET = 0.0035;
    private static final double OCCLUDER_PENETRATION_DISTANCE_PER_LAYER = 0.46;
    private static final WaveUv FULL_BLOCK_WAVE_UV = new WaveUv(
            UVPair.pack(0.0F, 1.0F),
            UVPair.pack(1.0F, 1.0F),
            UVPair.pack(1.0F, 0.0F),
            UVPair.pack(0.0F, 0.0F));
    static final long FADE_DURATION_NANOS = 250_000_000L;
    /**
     * Hard ceiling for activation work on the render thread. One slice always
     * runs so cooperative solvers can make progress; additional slices only
     * continue while this budget remains.
     */
    private static final long PREPARATION_FRAME_BUDGET_NANOS = 2_500_000L;
    static final List<Identifier> POST_EFFECT_STAGES = createPostEffectStages();

    private static @Nullable EchoSnapshot snapshot;
    private static @Nullable ClientTemplateProjection templateProjection;
    private static @Nullable EchoWaveVolume localWaveVolume;
    private static List<GhostBlock> ghosts = List.of();
    private static List<GhostModel> ghostModels = List.of();
    private static Map<Long, List<GhostModel>>
            ghostModelSections = Map.of();
    private static List<GhostModel> visibleGhostModels = List.of();
    private static List<GhostModel> presentGhostModels = List.of();
    private static Map<Long, List<GhostModel>>
            presentGhostModelSections = Map.of();
    private static List<GhostModel> visiblePresentGhostModels = List.of();
    private static List<Entity> ghostEntities = List.of();
    private static List<GhostBlock> presentOccluders = List.of();
    private static List<ScanFace> presentFaces = List.of();
    private static List<ScanFace> returnCarrierFaces = List.of();
    private static List<ScanFace> memoryFaces = List.of();
    private static List<ScanFace> memoryEchoFaces = List.of();
    private static ClientEchoArrivalField arrivalField =
            ClientEchoArrivalField.EMPTY;
    private static volatile Set<Long> presentOccluderPositions = Set.of();
    private static volatile Map<Long, SurfaceTiming> presentOccluderTimings = Map.of();
    /**
     * Per-ghost visibility when the camera occupies remembered solid space.
     * Values approach {@link EchoGhostOccupancy} targets each extract frame.
     */
    private static final Map<Long, Float> ghostOccupancyVisibility = new HashMap<>();
    /**
     * O(1) index for the per-quad occupancy query. The previous implementation
     * scanned every local ghost model for every submitted face, making the
     * stable projection path quadratic on large authored structures.
     */
    private static Set<Long> ghostFadeImmunePositions = Set.of();
    private static long ghostOccupancyNanos;
    private static BlockPos occupancyCameraBlock = BlockPos.ZERO;
    private static Map<BlockPos, BlockState> rememberedStates = Map.of();
    private static Map<BlockPos, BlockState> presentBaselineStates = Map.of();
    private static Map<Long, Integer> sharedBlockLight = Map.of();
    private static Map<Long, Integer> sharedSkyLight = Map.of();
    private static Set<Long> sharedLightSections = Set.of();
    private static Vec3 sonarOrigin = Vec3.ZERO;
    private static EchoPulseTiming pulseTiming =
            EchoPulseTiming.forRadius(1.0);
    private static double scanRadius;
    private static long activationNanos;
    private static long fadeStartNanos = Long.MAX_VALUE;
    private static boolean postEffectOwned;
    private static @Nullable Identifier activePostEffect;
    private static boolean postEffectFailureLogged;
    private static boolean worldStateDirty;
    private static @Nullable EchoSnapshot pendingRevision;
    private static @Nullable List<Entity> pendingGhostEntities;
    private static @Nullable RevisionVisuals pendingRevisionVisuals;
    private static boolean stoneControlReleased;
    private static boolean surfaceGeometryReady;
    private static boolean presentOccluderFilteringSettled;
    private static int appliedTemplatePresentRevision = -1;
    private static long preparationGeneration;
    private static @Nullable PendingPreparation pendingPreparation;
    private static boolean renderRuntimePrepared;

    public static void receive(EchoStatePayload payload) {
        if (!payload.active() || payload.snapshot().isEmpty()) {
            beginFade();
            return;
        }
        if (!payload.replay()) {
            receiveRevision(payload.snapshot().orElseThrow());
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        clearPresentOccluderFiltering(minecraft);
        resetActivationCaches(minecraft);
        snapshot = payload.snapshot().get();
        sonarOrigin = minecraft.player == null
                ? snapshot.origin().getCenter()
                : minecraft.player.getEyePosition();
        appliedTemplatePresentRevision = -1;
        activationNanos = System.nanoTime();
        fadeStartNanos = Long.MAX_VALUE;
        EchoWaveVolume waveVolume = localWaveVolume(snapshot, sonarOrigin);
        localWaveVolume = waveVolume;
        scanRadius = waveVolume.radius();
        pulseTiming = EchoPulseTiming.forRadius(scanRadius);
        pendingPreparation = new PendingPreparation(
                ++preparationGeneration,
                snapshot,
                waveVolume,
                System.nanoTime());
        worldStateDirty = false;
        pendingRevision = null;
        pendingGhostEntities = null;
        pendingRevisionVisuals = null;
        stoneControlReleased = false;
        // Own the post chain immediately so the click darkens the scene while
        // cooperative preparation fills wave and reconstruction caches.
        if (EchoesConfig.POST_PROCESSING.getAsBoolean()) {
            setOwnedPostEffect(minecraft, POST_EFFECT_STAGES.getFirst());
        } else {
            clearOwnedPostEffect(minecraft);
        }
    }

    private static void resetActivationCaches(Minecraft minecraft) {
        Set<Long> previousSharedLightSections = sharedLightSections;
        templateProjection = null;
        ghosts = List.of();
        ghostModels = List.of();
        ghostModelSections = Map.of();
        visibleGhostModels = List.of();
        presentGhostModels = List.of();
        presentGhostModelSections = Map.of();
        visiblePresentGhostModels = List.of();
        ghostOccupancyVisibility.clear();
        ghostFadeImmunePositions = Set.of();
        ghostOccupancyNanos = 0L;
        occupancyCameraBlock = BlockPos.ZERO;
        ghostEntities = List.of();
        presentOccluders = List.of();
        presentOccluderTimings = Map.of();
        presentFaces = List.of();
        returnCarrierFaces = List.of();
        memoryFaces = List.of();
        memoryEchoFaces = List.of();
        arrivalField = ClientEchoArrivalField.EMPTY;
        rememberedStates = Map.of();
        presentBaselineStates = Map.of();
        sharedBlockLight = Map.of();
        sharedSkyLight = Map.of();
        sharedLightSections = Set.of();
        surfaceGeometryReady = false;
        presentOccluderFilteringSettled = false;
        markSectionsDirty(minecraft, previousSharedLightSections);
    }

    /**
     * Refreshes an edited historical branch without replaying the scanner,
     * darkness ramp, sounds or item animation.
     */
    private static void receiveRevision(EchoSnapshot revised) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !minecraft.level.dimension().equals(
                        revised.dimension())) {
            return;
        }
        if (ClientPhilosophersStoneVision.controlsEchoCaches(
                System.nanoTime())) {
            pendingRevision = revised;
            pendingGhostEntities =
                    buildEntityCache(
                            minecraft,
                            revised);
            pendingRevisionVisuals =
                    buildRevisionVisuals(
                            minecraft,
                            revised);
            ClientPhilosophersStoneVision
                    .refreshBlueprint(
                            pendingRevisionVisuals
                                    .rememberedModels(),
                            pendingRevisionVisuals
                                    .presentModels());
            return;
        }
        applySnapshotRevision(minecraft, revised, true);
    }

    private static void applySnapshotRevision(
            Minecraft minecraft,
            EchoSnapshot revised,
            boolean refreshFilteredSections) {
        if (refreshFilteredSections) {
            clearPresentOccluderFiltering(minecraft);
        }
        snapshot = revised;
        templateProjection = ClientTemplateProjection.load(
                        minecraft,
                        revised,
                        sonarOrigin)
                .orElse(null);
        appliedTemplatePresentRevision = -1;
        EchoWaveVolume waveVolume = localWaveVolume(revised, sonarOrigin);
        scanRadius = waveVolume.radius();
        rebuildBlockCaches(minecraft, waveVolume);
        ghostEntities = buildEntityCache(minecraft, revised);
        worldStateDirty = false;
        pendingRevision = null;
        pendingGhostEntities = null;
        pendingRevisionVisuals = null;
        stoneControlReleased = false;
        updatePresentOccluderFiltering(
                minecraft,
                System.nanoTime());
    }

    /**
     * Advances as many activation phases as fit inside
     * {@link #PREPARATION_FRAME_BUDGET_NANOS}. Heavy phases use cooperative
     * solvers so no single render frame monopolizes pathing, lighting, models
     * or surfaces. Live fields stay unpublished until {@link #finishPreparation}
     * so cancellation never reveals a half-built island.
     */
    static void advancePreparation() {
        PendingPreparation preparation = pendingPreparation;
        if (preparation == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (preparation.generation != preparationGeneration
                || snapshot != preparation.source
                || minecraft.level == null
                || !minecraft.level.dimension().equals(preparation.source.dimension())) {
            pendingPreparation = null;
            return;
        }

        long deadlineNanos = System.nanoTime() + PREPARATION_FRAME_BUDGET_NANOS;
        preparation.frames++;
        boolean progressed = false;
        while (preparation.phase != PreparationPhase.COMPLETE) {
            PreparationPhase phase = preparation.phase;
            long phaseStarted = System.nanoTime();
            if (progressed && phaseStarted >= deadlineNanos) {
                break;
            }
            boolean phaseComplete;
            try {
                phaseComplete = advancePreparationPhase(
                        minecraft,
                        preparation,
                        deadlineNanos);
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Past Echo activation preparation failed during {}",
                        phase,
                        exception);
                clearImmediately();
                return;
            }
            long elapsed = System.nanoTime() - phaseStarted;
            preparation.phaseNanos[phase.ordinal()] += elapsed;
            if (elapsed > preparation.longestSliceNanos) {
                preparation.longestSliceNanos = elapsed;
                preparation.longestSlicePhase = phase;
            }
            progressed = true;
            if (!phaseComplete) {
                break;
            }
            if (phase != PreparationPhase.COMPLETE) {
                preparation.phase = phase.next();
            }
        }

        if (preparation.phase == PreparationPhase.COMPLETE) {
            finishPreparation(minecraft, preparation);
        }
    }

    private static boolean advancePreparationPhase(
            Minecraft minecraft,
            PendingPreparation preparation,
            long deadlineNanos) {
        return switch (preparation.phase) {
            case TEMPLATE -> {
                preparation.stagingTemplate = ClientTemplateProjection.load(
                                minecraft,
                                preparation.source,
                                sonarOrigin)
                        .orElse(null);
                if (preparation.stagingTemplate != null) {
                    preparation.stagingScanRadius = Math.max(
                            preparation.waveVolume.radius(),
                            preparation.stagingTemplate.outerRadius(sonarOrigin));
                } else {
                    preparation.stagingScanRadius = preparation.waveVolume.radius();
                }
                preparation.stagingPulseTiming = EchoPulseTiming.forRadius(
                        preparation.stagingScanRadius);
                // Publish timing early so the outbound crest can start with the
                // click while the remaining caches continue cooperatively.
                scanRadius = preparation.stagingScanRadius;
                pulseTiming = preparation.stagingPulseTiming;
                localWaveVolume = preparation.waveVolume;
                yield true;
            }
            case OUTGOING_SURFACES -> advanceOutgoingSurfaces(
                    minecraft,
                    preparation,
                    deadlineNanos);
            case TEMPLATE_WAVE -> preparation.stagingTemplate == null
                    || preparation.stagingTemplate.advanceWaveTilePreparation(
                            minecraft,
                            Math.max(1L, deadlineNanos - System.nanoTime()));
            case REMEMBERED -> {
                preparation.stagingRememberedStates = rememberedStates(
                        preparation.source,
                        preparation.waveVolume,
                        preparation.stagingTemplate);
                yield true;
            }
            case ROUTE -> advanceRoute(minecraft, preparation, deadlineNanos);
            case BASELINE -> advanceBaseline(minecraft, preparation, deadlineNanos);
            case LIGHT -> advanceLighting(minecraft, preparation, deadlineNanos);
            case REMEMBERED_MODELS -> advanceRememberedModels(
                    minecraft,
                    preparation,
                    deadlineNanos);
            case PRESENT_MODELS -> advancePresentModels(
                    minecraft,
                    preparation,
                    deadlineNanos);
            case MEMORY_SURFACES -> advanceMemorySurfaces(
                    minecraft,
                    preparation,
                    deadlineNanos);
            case ENTITIES -> advanceEntities(minecraft, preparation, deadlineNanos);
            case COMPLETE -> true;
        };
    }

    private static boolean advanceOutgoingSurfaces(
            Minecraft minecraft,
            PendingPreparation preparation,
            long deadlineNanos) {
        if (preparation.outgoingFaces == null) {
            preparation.outgoingFaces = new ArrayList<>();
            BlockPos minimum = preparation.waveVolume.minBlock();
            preparation.scanX = minimum.getX();
            preparation.scanY = minimum.getY();
            preparation.scanZ = minimum.getZ();
            preparation.scanMaximumDistanceSquared =
                    (preparation.waveVolume.radius() + 1.0)
                            * (preparation.waveVolume.radius() + 1.0);
        }
        if (!advancePresentFaceScan(
                minecraft,
                preparation.waveVolume,
                preparation.outgoingFaces,
                preparation,
                deadlineNanos)) {
            return false;
        }
        preparation.stagingPresentFaces = freezeFacesByDistance(preparation.outgoingFaces);
        preparation.outgoingFaces = null;
        // Safe to expose for the outbound crest only: reconstruction models are
        // still gated until finishPreparation publishes them atomically.
        presentFaces = preparation.stagingPresentFaces;
        returnCarrierFaces = preparation.stagingPresentFaces;
        surfaceGeometryReady = true;
        return true;
    }

    private static boolean advanceRoute(
            Minecraft minecraft,
            PendingPreparation preparation,
            long deadlineNanos) {
        if (preparation.arrivalPreparation == null) {
            preparation.arrivalPreparation = ClientEchoArrivalField.prepare(
                    minecraft,
                    preparation.waveVolume);
        }
        if (!preparation.arrivalPreparation.advance(deadlineNanos)) {
            return false;
        }
        preparation.stagingArrivalField = preparation.arrivalPreparation.result();
        preparation.arrivalPreparation = null;
        arrivalField = preparation.stagingArrivalField;
        if (!preparation.stagingArrivalField.isEmpty()) {
            preparation.stagingScanRadius = Math.max(
                    preparation.stagingScanRadius,
                    preparation.stagingArrivalField.maximumDistance());
            preparation.stagingPulseTiming = EchoPulseTiming.forRadius(
                    preparation.stagingScanRadius);
            scanRadius = preparation.stagingScanRadius;
            pulseTiming = preparation.stagingPulseTiming;
        }
        return true;
    }

    private static boolean advanceBaseline(
            Minecraft minecraft,
            PendingPreparation preparation,
            long deadlineNanos) {
        if (preparation.stagingBaselineBuilder == null) {
            preparation.stagingBaselineBuilder = new HashMap<>();
            BlockPos minimum = preparation.waveVolume.minBlock();
            preparation.scanX = minimum.getX();
            preparation.scanY = minimum.getY();
            preparation.scanZ = minimum.getZ();
        }
        if (minecraft.level == null) {
            preparation.stagingBaselineStates = Map.of();
            preparation.stagingBaselineBuilder = null;
            return true;
        }
        BlockPos maximum = preparation.waveVolume.maxBlock();
        BlockPos.MutableBlockPos cursor = preparation.scanCursor;
        int processed = 0;
        while (preparation.scanX <= maximum.getX()) {
            if (processed > 0 && System.nanoTime() >= deadlineNanos) {
                return false;
            }
            cursor.set(preparation.scanX, preparation.scanY, preparation.scanZ);
            if (minecraft.level.isInWorldBounds(cursor)
                    && minecraft.level.hasChunkAt(cursor)
                    && preparation.waveVolume.contains(cursor.getCenter())) {
                BlockState state = minecraft.level.getBlockState(cursor);
                if (!state.isAir()) {
                    preparation.stagingBaselineBuilder.put(cursor.immutable(), state);
                }
            }
            processed++;
            if (!advanceScanCursor(preparation, maximum)) {
                preparation.stagingBaselineStates = Map.copyOf(
                        preparation.stagingBaselineBuilder);
                preparation.stagingBaselineBuilder = null;
                return true;
            }
        }
        preparation.stagingBaselineStates = Map.copyOf(preparation.stagingBaselineBuilder);
        preparation.stagingBaselineBuilder = null;
        return true;
    }

    private static boolean advanceLighting(
            Minecraft minecraft,
            PendingPreparation preparation,
            long deadlineNanos) {
        if (!preparation.lightSeedsReady) {
            if (!advanceLightSeeds(minecraft, preparation, deadlineNanos)) {
                return false;
            }
            preparation.lightSeedsReady = true;
            preparation.blockLightPropagation = EchoPastLight.incremental(
                    preparation.stagingRememberedStates,
                    preparation.blockSeeds,
                    preparation.waveVolume.minBlock(),
                    preparation.waveVolume.maxBlock(),
                    false);
            if (!preparation.largeMemory) {
                preparation.skyLightPropagation = EchoPastLight.incremental(
                        preparation.stagingRememberedStates,
                        preparation.skySeeds,
                        preparation.waveVolume.minBlock(),
                        preparation.waveVolume.maxBlock(),
                        true);
            }
        }
        if (preparation.blockLightPropagation != null
                && !preparation.blockLightPropagation.advance(deadlineNanos)) {
            return false;
        }
        if (preparation.skyLightPropagation != null
                && !preparation.skyLightPropagation.advance(deadlineNanos)) {
            return false;
        }
        preparation.stagingBlockLight = preparation.blockLightPropagation == null
                ? Map.of()
                : preparation.blockLightPropagation.result();
        preparation.stagingSkyLight = preparation.skyLightPropagation == null
                ? Map.of()
                : preparation.skyLightPropagation.result();
        preparation.stagingLightSections =
                preparation.stagingBlockLight.isEmpty() && preparation.stagingSkyLight.isEmpty()
                        ? Set.of()
                        : sectionsCovering(
                                preparation.waveVolume.minBlock(),
                                preparation.waveVolume.maxBlock());
        preparation.blockLightPropagation = null;
        preparation.skyLightPropagation = null;
        preparation.blockSeeds = Map.of();
        preparation.skySeeds = Map.of();
        // Models need the light field while baking; chunk rebuilds wait until
        // finishPreparation so mid-prep cancellation cannot dirty the world.
        sharedBlockLight = preparation.stagingBlockLight;
        sharedSkyLight = preparation.stagingSkyLight;
        return true;
    }

    private static boolean advanceLightSeeds(
            Minecraft minecraft,
            PendingPreparation preparation,
            long deadlineNanos) {
        if (preparation.blockSeeds == null) {
            preparation.blockSeeds = new HashMap<>();
            preparation.skySeeds = new HashMap<>();
            preparation.largeMemory = preparation.stagingRememberedStates.size()
                    > EchoProjectionBudget.LARGE_MEMORY_THRESHOLD;
            preparation.seedRememberedIterator =
                    preparation.stagingRememberedStates.entrySet().iterator();
            BlockPos minimum = preparation.waveVolume.minBlock();
            preparation.scanX = minimum.getX();
            preparation.scanY = minimum.getY();
            preparation.scanZ = minimum.getZ();
            preparation.seedWorldScanStarted = false;
        }
        int processed = 0;
        while (preparation.seedRememberedIterator != null
                && preparation.seedRememberedIterator.hasNext()) {
            if (processed > 0 && System.nanoTime() >= deadlineNanos) {
                return false;
            }
            Map.Entry<BlockPos, BlockState> entry = preparation.seedRememberedIterator.next();
            int emission = entry.getValue().getLightEmission();
            if (emission > 0) {
                preparation.blockSeeds.put(entry.getKey(), emission);
            }
            processed++;
        }
        preparation.seedRememberedIterator = null;
        if (minecraft.level == null || preparation.largeMemory) {
            return true;
        }
        preparation.seedWorldScanStarted = true;
        BlockPos maximum = preparation.waveVolume.maxBlock();
        BlockPos.MutableBlockPos cursor = preparation.scanCursor;
        while (preparation.scanX <= maximum.getX()) {
            if (processed > 0 && System.nanoTime() >= deadlineNanos) {
                return false;
            }
            cursor.set(preparation.scanX, preparation.scanY, preparation.scanZ);
            if (minecraft.level.isInWorldBounds(cursor)
                    && minecraft.level.hasChunkAt(cursor)
                    && preparation.waveVolume.contains(cursor.getCenter())) {
                int blockSeed = Math.max(
                        minecraft.level.getBlockState(cursor).getLightEmission(),
                        minecraft.level.getBrightness(LightLayer.BLOCK, cursor));
                int skySeed = minecraft.level.getBrightness(LightLayer.SKY, cursor);
                if (blockSeed > 0 || skySeed > 0) {
                    BlockPos position = cursor.immutable();
                    if (blockSeed > 0) {
                        preparation.blockSeeds.merge(position, blockSeed, Math::max);
                    }
                    if (skySeed > 0) {
                        preparation.skySeeds.put(position, skySeed);
                    }
                }
            }
            processed++;
            if (!advanceScanCursor(preparation, maximum)) {
                return true;
            }
        }
        return true;
    }

    private static boolean advanceRememberedModels(
            Minecraft minecraft,
            PendingPreparation preparation,
            long deadlineNanos) {
        if (preparation.stagingGhosts == null) {
            preparation.stagingGhosts = buildGhostCache(
                    minecraft,
                    preparation.source,
                    preparation.stagingRememberedStates,
                    null);
            preparation.stagingGhostModels = new ArrayList<>(preparation.stagingGhosts.size());
            preparation.modelCursor = 0;
        }
        while (preparation.modelCursor < preparation.stagingGhosts.size()) {
            if (preparation.modelCursor > 0 && System.nanoTime() >= deadlineNanos) {
                return false;
            }
            GhostBlock ghost = preparation.stagingGhosts.get(preparation.modelCursor++);
            GhostModel model = buildGhostModel(
                    minecraft,
                    ghost,
                    preparation.stagingRememberedStates);
            if (model != null) {
                preparation.stagingGhostModels.add(model);
            }
        }
        return true;
    }

    private static boolean advancePresentModels(
            Minecraft minecraft,
            PendingPreparation preparation,
            long deadlineNanos) {
        if (preparation.stagingPresentOccluders == null) {
            preparation.stagingPresentOccluders = buildPresentOccluderCache(
                    minecraft,
                    preparation.source,
                    preparation.stagingRememberedStates,
                    preparation.stagingBaselineStates);
            preparation.stagingPresentOccluderTimings = buildPresentOccluderTimings(
                    preparation.stagingPresentOccluders);
            preparation.stagingAddedBlocks = preparation.stagingPresentOccluders.stream()
                    .filter(block -> block.change() == EchoBlockChange.Kind.ADDED)
                    .toList();
            preparation.stagingPresentModels = new ArrayList<>(
                    preparation.stagingAddedBlocks.size());
            preparation.modelCursor = 0;
        }
        while (preparation.modelCursor < preparation.stagingAddedBlocks.size()) {
            if (preparation.modelCursor > 0 && System.nanoTime() >= deadlineNanos) {
                return false;
            }
            GhostBlock ghost = preparation.stagingAddedBlocks.get(preparation.modelCursor++);
            GhostModel model = buildGhostModel(minecraft, ghost, null);
            if (model != null) {
                preparation.stagingPresentModels.add(model);
            }
        }
        return true;
    }

    private static boolean advanceMemorySurfaces(
            Minecraft minecraft,
            PendingPreparation preparation,
            long deadlineNanos) {
        if (preparation.memoryFacesBuilder == null) {
            Set<BlockPos> reconstructed = new HashSet<>();
            for (GhostBlock ghost : preparation.stagingGhosts) {
                reconstructed.add(ghost.position());
            }
            for (GhostBlock occluder : preparation.stagingPresentOccluders) {
                reconstructed.add(occluder.position());
            }
            preparation.stagingReturnCarrierFaces = preparation.stagingPresentFaces.stream()
                    .filter(face -> !reconstructed.contains(face.position()))
                    .toList();
            preparation.memoryFacesBuilder = new ArrayList<>();
            preparation.memoryGhostCursor = 0;
        }
        while (preparation.memoryGhostCursor < preparation.stagingGhosts.size()) {
            if (preparation.memoryGhostCursor > 0 && System.nanoTime() >= deadlineNanos) {
                return false;
            }
            GhostBlock ghost = preparation.stagingGhosts.get(preparation.memoryGhostCursor++);
            appendMemoryFaces(
                    minecraft,
                    preparation.memoryFacesBuilder,
                    ghost,
                    preparation.stagingRememberedStates);
        }
        List<ScanFace> faces = freezeFacesByDistance(preparation.memoryFacesBuilder);
        preparation.stagingMemoryFaces = withArrivalDistances(faces);
        preparation.memoryFacesBuilder = null;
        return true;
    }

    private static boolean advanceEntities(
            Minecraft minecraft,
            PendingPreparation preparation,
            long deadlineNanos) {
        if (preparation.stagingTemplate != null
                && !preparation.stagingTemplate.advanceEntityCapture(
                        minecraft,
                        deadlineNanos)) {
            return false;
        }
        if (preparation.stagingGhostEntities == null) {
            preparation.stagingGhostEntities = new ArrayList<>();
            preparation.entitySource = entitySources(
                    preparation.source,
                    preparation.stagingTemplate);
            preparation.entityCursor = 0;
        }
        while (preparation.entityCursor < preparation.entitySource.size()) {
            if (preparation.entityCursor > 0 && System.nanoTime() >= deadlineNanos) {
                return false;
            }
            SnapshotEntity remembered = preparation.entitySource.get(preparation.entityCursor++);
            appendLoadedEntity(
                    minecraft,
                    preparation.source,
                    remembered,
                    preparation.stagingGhostEntities);
        }
        return true;
    }

    private static boolean advancePresentFaceScan(
            Minecraft minecraft,
            EchoWaveVolume volume,
            List<ScanFace> faces,
            PendingPreparation preparation,
            long deadlineNanos) {
        if (minecraft.level == null) {
            return true;
        }
        BlockPos maximum = volume.maxBlock();
        BlockPos.MutableBlockPos cursor = preparation.scanCursor;
        int processed = 0;
        while (preparation.scanX <= maximum.getX()) {
            if (processed > 0 && System.nanoTime() >= deadlineNanos) {
                return false;
            }
            cursor.set(preparation.scanX, preparation.scanY, preparation.scanZ);
            if (minecraft.level.isInWorldBounds(cursor)) {
                BlockState state = minecraft.level.getBlockState(cursor);
                if (!state.isAir()) {
                    BlockPos position = cursor.immutable();
                    if (squaredDistanceToCenter(position, volume.center())
                            <= preparation.scanMaximumDistanceSquared
                            && (!state.isSolidRender()
                                    || hasVisibleExteriorFace(minecraft, position, state))) {
                        if (!appendModelFaces(
                                minecraft,
                                faces,
                                position,
                                state,
                                volume,
                                null)) {
                            appendShapeFaces(minecraft, faces, position, state, volume);
                        }
                    }
                }
            }
            processed++;
            if (!advanceScanCursor(preparation, maximum)) {
                return true;
            }
        }
        return true;
    }

    private static boolean advanceScanCursor(
            PendingPreparation preparation,
            BlockPos maximum) {
        preparation.scanZ++;
        if (preparation.scanZ <= maximum.getZ()) {
            return true;
        }
        preparation.scanZ = preparation.waveVolume.minBlock().getZ();
        preparation.scanY++;
        if (preparation.scanY <= maximum.getY()) {
            return true;
        }
        preparation.scanY = preparation.waveVolume.minBlock().getY();
        preparation.scanX++;
        return preparation.scanX <= maximum.getX();
    }

    private static void finishPreparation(
            Minecraft minecraft,
            PendingPreparation preparation) {
        if (pendingPreparation != preparation
                || preparation.generation != preparationGeneration) {
            return;
        }
        // Publish the coherent staging package atomically. Timing already began
        // on the click; do not restart the pulse clock here.
        templateProjection = preparation.stagingTemplate;
        rememberedStates = preparation.stagingRememberedStates;
        presentBaselineStates = preparation.stagingBaselineStates;
        arrivalField = preparation.stagingArrivalField == null
                ? ClientEchoArrivalField.EMPTY
                : preparation.stagingArrivalField;
        ghosts = preparation.stagingGhosts == null
                ? List.of()
                : List.copyOf(preparation.stagingGhosts);
        ghostModels = preparation.stagingGhostModels == null
                ? List.of()
                : List.copyOf(preparation.stagingGhostModels);
        ghostModelSections = indexGhostModels(ghostModels);
        visibleGhostModels = List.of();
        presentOccluders = preparation.stagingPresentOccluders == null
                ? List.of()
                : List.copyOf(preparation.stagingPresentOccluders);
        presentOccluderTimings = preparation.stagingPresentOccluderTimings == null
                ? Map.of()
                : preparation.stagingPresentOccluderTimings;
        presentGhostModels = preparation.stagingPresentModels == null
                ? List.of()
                : List.copyOf(preparation.stagingPresentModels);
        presentGhostModelSections = indexGhostModels(presentGhostModels);
        ghostFadeImmunePositions = indexFadeImmunePositions(
                ghostModels,
                presentGhostModels);
        visiblePresentGhostModels = List.of();
        presentFaces = preparation.stagingPresentFaces == null
                ? List.of()
                : preparation.stagingPresentFaces;
        returnCarrierFaces = preparation.stagingReturnCarrierFaces == null
                ? presentFaces
                : preparation.stagingReturnCarrierFaces;
        memoryFaces = preparation.stagingMemoryFaces == null
                ? List.of()
                : preparation.stagingMemoryFaces;
        memoryEchoFaces = memoryFaces;
        ghostEntities = preparation.stagingGhostEntities == null
                ? List.of()
                : List.copyOf(preparation.stagingGhostEntities);
        sharedBlockLight = preparation.stagingBlockLight == null
                ? Map.of()
                : preparation.stagingBlockLight;
        sharedSkyLight = preparation.stagingSkyLight == null
                ? Map.of()
                : preparation.stagingSkyLight;
        Set<Long> previousSharedLightSections = sharedLightSections;
        sharedLightSections = preparation.stagingLightSections == null
                ? Set.of()
                : preparation.stagingLightSections;
        scanRadius = preparation.stagingScanRadius;
        pulseTiming = preparation.stagingPulseTiming;
        localWaveVolume = preparation.waveVolume;

        pendingPreparation = null;
        fadeStartNanos = Long.MAX_VALUE;
        surfaceGeometryReady = true;
        presentOccluderFilteringSettled = false;
        appliedTemplatePresentRevision = -1;
        worldStateDirty = false;

        Set<Long> dirtySections = new HashSet<>(previousSharedLightSections);
        dirtySections.addAll(sharedLightSections);
        markSectionsDirty(minecraft, dirtySections);

        if (templateProjection != null) {
            LOGGER.info(
                    "Prepared client Past Echo template stream: authoredBlocks={}, staticEntities={}, localRadius={}, waveFaces={}",
                    templateProjection.authoredBlockCount(),
                    templateProjection.entities().size(),
                    preparation.waveVolume.ambientRadius(),
                    templateProjection.waveFaceCount());
        } else if (preparation.source.isTemplateReference()) {
            LOGGER.warn(
                    "Could not load the local authored Past Echo template {}; only the safe local pulse is available",
                    preparation.source.template().orElse(null));
        }

        long workNanos = 0L;
        for (PreparationPhase phase : PreparationPhase.values()) {
            workNanos += preparation.phaseNanos[phase.ordinal()];
        }
        LOGGER.info(
                "Past Echo phased preparation: frames={}, wallMs={}, workMs={}, longestSlice={}, longestSliceMs={}, budgetMs={}, volumeCells={}, localBlocks={}, models={}, faces={}",
                preparation.frames,
                elapsedMs(preparation.startedNanos, System.nanoTime()),
                workNanos / 1_000_000.0,
                preparation.longestSlicePhase,
                preparation.longestSliceNanos / 1_000_000.0,
                PREPARATION_FRAME_BUDGET_NANOS / 1_000_000.0,
                preparation.waveVolume.boundingCellCount(),
                rememberedStates.size(),
                ghostModels.size(),
                presentFaces.size());

        if (EchoesConfig.POST_PROCESSING.getAsBoolean()) {
            setOwnedPostEffect(minecraft, POST_EFFECT_STAGES.getFirst());
        } else {
            clearOwnedPostEffect(minecraft);
        }
    }

    private static void rebuildBlockCaches(Minecraft minecraft, EchoWaveVolume waveVolume) {
        if (snapshot == null) {
            return;
        }
        long preparationStarted = System.nanoTime();
        localWaveVolume = waveVolume;
        rememberedStates = rememberedStates(
                snapshot,
                waveVolume);
        long rememberedReady = System.nanoTime();
        arrivalField = ClientEchoArrivalField.build(
                minecraft,
                waveVolume);
        if (!arrivalField.isEmpty()) {
            // The routed field only governs how the nearby crest bends around
            // walls. It must never shorten the radial pulse itself: a sparse
            // or sealed local room is not the boundary of the stored memory.
            scanRadius = Math.max(
                    scanRadius,
                    arrivalField.maximumDistance());
        }
        if (templateProjection != null) {
            scanRadius = Math.max(
                    scanRadius,
                    templateProjection.outerRadius(sonarOrigin));
        }
        pulseTiming = EchoPulseTiming.forRadius(scanRadius);
        long routingReady = System.nanoTime();
        presentBaselineStates =
                capturePresentBaseline(
                        minecraft,
                        waveVolume);
        long baselineReady = System.nanoTime();
        rebuildSharedLighting(
                minecraft,
                waveVolume);
        long lightingReady = System.nanoTime();
        ghosts = buildGhostCache(
                minecraft,
                snapshot,
                rememberedStates,
                null);
        ghostModels = buildGhostModels(minecraft, ghosts, rememberedStates);
        ghostModelSections = indexGhostModels(
                ghostModels);
        visibleGhostModels = List.of();
        long rememberedModelsReady = System.nanoTime();
        presentOccluders = buildPresentOccluderCache(
                minecraft,
                snapshot,
                rememberedStates,
                presentBaselineStates);
        presentOccluderTimings = buildPresentOccluderTimings(
                presentOccluders);
        List<GhostBlock> addedBlocks = presentOccluders.stream()
                .filter(block -> block.change() == EchoBlockChange.Kind.ADDED)
                .toList();
        presentGhostModels = buildGhostModels(minecraft, addedBlocks, null);
        presentGhostModelSections = indexGhostModels(
                presentGhostModels);
        ghostFadeImmunePositions = indexFadeImmunePositions(
                ghostModels,
                presentGhostModels);
        visiblePresentGhostModels = List.of();
        long presentModelsReady = System.nanoTime();
        surfaceGeometryReady = false;
        presentFaces = List.of();
        returnCarrierFaces = List.of();
        memoryFaces = List.of();
        memoryEchoFaces = List.of();
        // The routed field controls when a face is reached; the cached model
        // geometry controls the narrow line drawn inside that face. Keeping
        // those jobs separate avoids the full-block glow of the depth path.
        buildWaveSurfaceCaches(minecraft, waveVolume);
        long surfacesReady = System.nanoTime();
        presentOccluderFilteringSettled = false;
        appliedTemplatePresentRevision = -1;
        double totalMs = elapsedMs(
                preparationStarted,
                surfacesReady);
        if (snapshot.sealed()
                || totalMs >= 8.0) {
            LOGGER.info(
                    "Past Echo preparation profile: radius={}, volumeCells={}, transferredBlocks={}, localBlocks={}, reachedCells={}, ghosts={}, models={}, faces={}, rememberedMs={}, routeMs={}, baselineMs={}, lightMs={}, rememberedModelsMs={}, presentModelsMs={}, surfacesMs={}, totalMs={}",
                    waveVolume.radius(),
                    waveVolume.boundingCellCount(),
                    snapshot.blocks().size(),
                    rememberedStates.size(),
                    arrivalField.reachedCells(),
                    ghosts.size(),
                    ghostModels.size(),
                    presentFaces.size(),
                    elapsedMs(
                            preparationStarted,
                            rememberedReady),
                    elapsedMs(
                            rememberedReady,
                            routingReady),
                    elapsedMs(
                            routingReady,
                            baselineReady),
                    elapsedMs(
                            baselineReady,
                            lightingReady),
                    elapsedMs(
                            lightingReady,
                            rememberedModelsReady),
                    elapsedMs(
                            rememberedModelsReady,
                            presentModelsReady),
                    elapsedMs(
                            presentModelsReady,
                            surfacesReady),
                    totalMs);
        }
    }

    private static double elapsedMs(
            long started,
            long finished) {
        return (finished - started)
                / 1_000_000.0;
    }

    private static EchoWaveVolume localWaveVolume(
            EchoSnapshot source,
            Vec3 center) {
        if (source.isTemplateReference()) {
            return EchoWaveVolume.aroundPlayer(
                    source,
                    center,
                    EchoProjectionBudget.ambientRadius(
                            EchoesConfig.CAPTURE_RADIUS.getAsInt()),
                    false);
        }
        return EchoWaveVolume.aroundPlayer(source, center);
    }

    private static void buildWaveSurfaceCaches(
            Minecraft minecraft,
            EchoWaveVolume waveVolume) {
        // Sound belongs to the current space, not to the remembered template.
        // Even sealed authored memories therefore illuminate every nearby
        // present surface; only the reconstruction remains template-bounded.
        presentFaces = buildPresentFaces(minecraft, waveVolume);
        Set<BlockPos> reconstructedPositions = new HashSet<>();
        for (GhostBlock ghost : ghosts) {
            reconstructedPositions.add(ghost.position());
        }
        for (GhostBlock occluder : presentOccluders) {
            reconstructedPositions.add(occluder.position());
        }
        returnCarrierFaces = presentFaces.stream()
                .filter(face -> !reconstructedPositions.contains(face.position()))
                .toList();
        memoryFaces = buildMemoryFaces(minecraft, rememberedStates, ghosts);
        memoryFaces = withArrivalDistances(memoryFaces);
        memoryEchoFaces = memoryFaces;
        surfaceGeometryReady = true;
    }

    static void ensureSurfaceGeometryFallback() {
        if (surfaceGeometryReady
                || pendingPreparation != null
                || snapshot == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !minecraft.level.dimension().equals(snapshot.dimension())) {
            return;
        }
        buildWaveSurfaceCaches(
                minecraft,
                localWaveVolume(snapshot, sonarOrigin));
    }

    /**
     * Builds an edited historical branch against the present that existed
     * before the Stone materialized it. Comparing against the live level here
     * would compare the revision with the physical past and classify edited
     * positions as unchanged.
     */
    private static RevisionVisuals buildRevisionVisuals(
            Minecraft minecraft,
            EchoSnapshot revised) {
        EchoWaveVolume revisionVolume =
                EchoWaveVolume.aroundPlayer(
                        revised,
                        sonarOrigin);
        Map<BlockPos, BlockState> revisedStates =
                rememberedStates(
                        revised,
                        revisionVolume);
        List<GhostBlock> revisedGhosts =
                buildGhostCache(
                        minecraft,
                        revised,
                        revisedStates,
                        presentBaselineStates);
        List<GhostModel> revisedModels =
                buildGhostModels(
                        minecraft,
                        revisedGhosts,
                        revisedStates);
        List<GhostBlock> revisedOccluders =
                buildPresentOccluderCache(
                        minecraft,
                        revised,
                        revisedStates,
                        presentBaselineStates);
        List<GhostModel> revisedPresentModels =
                buildGhostModels(
                        minecraft,
                        revisedOccluders.stream()
                                .filter(block ->
                                        block.change()
                                                == EchoBlockChange
                                                        .Kind
                                                        .ADDED)
                                .toList(),
                        null);
        Set<Long> revisedPositions =
                new HashSet<>(
                        revisedOccluders.size());
        Map<Long, SurfaceTiming> revisedTimings =
                buildPresentOccluderTimings(
                        revisedOccluders);
        for (GhostBlock occluder : revisedOccluders) {
            long packed =
                    occluder.position().asLong();
            revisedPositions.add(packed);
        }
        return new RevisionVisuals(
                List.copyOf(revisedModels),
                List.copyOf(revisedPresentModels),
                Set.copyOf(revisedPositions),
                Map.copyOf(revisedTimings));
    }

    private static Map<BlockPos, BlockState> capturePresentBaseline(
            Minecraft minecraft,
            EchoWaveVolume volume) {
        if (minecraft.level == null) {
            return Map.of();
        }
        BlockPos minimum = volume.minBlock();
        BlockPos maximum = volume.maxBlock();
        Map<BlockPos, BlockState> states =
                new HashMap<>();
        BlockPos.MutableBlockPos cursor =
                new BlockPos.MutableBlockPos();
        for (int x = minimum.getX();
                x <= maximum.getX();
                x++) {
            for (int y = minimum.getY();
                    y <= maximum.getY();
                    y++) {
                for (int z = minimum.getZ();
                        z <= maximum.getZ();
                        z++) {
                    cursor.set(x, y, z);
                    if (!minecraft.level
                                    .isInWorldBounds(cursor)
                            || !minecraft.level
                                    .hasChunkAt(cursor)
                            || !volume.contains(
                                    cursor.getCenter())) {
                        continue;
                    }
                    BlockState state =
                            minecraft.level
                                    .getBlockState(cursor);
                    if (!state.isAir()) {
                        states.put(
                                cursor.immutable(),
                                state);
                    }
                }
            }
        }
        return Map.copyOf(states);
    }

    /**
     * Reconstructs block- and sky-light fields shared by both timelines.
     * Remembered geometry controls their travel, while seeds come from both
     * remembered and present lighting. This is deliberately rebuilt with the
     * block caches after a world change.
     */
    private static void rebuildSharedLighting(
            Minecraft minecraft,
            EchoWaveVolume volume) {
        BlockPos minimum = volume.minBlock();
        BlockPos maximum = volume.maxBlock();
        Map<BlockPos, Integer> blockSeeds = new HashMap<>();
        Map<BlockPos, Integer> skySeeds = new HashMap<>();

        for (Map.Entry<BlockPos, BlockState> entry : rememberedStates.entrySet()) {
            int emission = entry.getValue().getLightEmission();
            if (emission > 0) {
                blockSeeds.put(entry.getKey(), emission);
            }
        }

        boolean largeMemory =
                rememberedStates.size()
                        > EchoProjectionBudget
                                .LARGE_MEMORY_THRESHOLD;
        if (minecraft.level != null
                && !largeMemory) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = minimum.getX(); x <= maximum.getX(); x++) {
                for (int y = minimum.getY(); y <= maximum.getY(); y++) {
                    for (int z = minimum.getZ(); z <= maximum.getZ(); z++) {
                        cursor.set(x, y, z);
                        if (!minecraft.level.isInWorldBounds(cursor)
                                || !minecraft.level.hasChunkAt(cursor)
                                || !volume.contains(cursor.getCenter())) {
                            continue;
                        }
                        int blockSeed = Math.max(
                                minecraft.level.getBlockState(cursor).getLightEmission(),
                                minecraft.level.getBrightness(LightLayer.BLOCK, cursor));
                        int skySeed = minecraft.level.getBrightness(LightLayer.SKY, cursor);
                        BlockPos position = cursor.immutable();
                        BlockState presentState = minecraft.level.getBlockState(cursor);
                        // ADDED rubble (present solid on historical air) traps 0 sky
                        // in the live engine. Seed the open-sky column the past had
                        // so light from above passes through those cells into the
                        // shared field used when remeshing neighbours.
                        if (presentState.canOcclude()
                                && !EchoBlockChange.claimsRememberedSolid(
                                        rememberedStates.get(position))) {
                            skySeed = Math.max(
                                    skySeed,
                                    historicalAirSkySeed(
                                            minecraft.level,
                                            rememberedStates,
                                            cursor,
                                            maximum.getY()));
                            for (Direction direction : Direction.values()) {
                                blockSeed = Math.max(
                                        blockSeed,
                                        minecraft.level.getBrightness(
                                                LightLayer.BLOCK,
                                                cursor.relative(direction)));
                            }
                        }
                        if (blockSeed > 0 || skySeed > 0) {
                            if (blockSeed > 0) {
                                blockSeeds.merge(position, blockSeed, Math::max);
                            }
                            if (skySeed > 0) {
                                skySeeds.merge(position, skySeed, Math::max);
                            }
                        }
                    }
                }
            }
        }

        Set<Long> dirtySections = new HashSet<>(sharedLightSections);
        sharedBlockLight = EchoPastLight.propagate(
                rememberedStates,
                blockSeeds,
                minimum,
                maximum);
        sharedSkyLight = largeMemory
                ? Map.of()
                : EchoPastLight.propagateSky(
                        rememberedStates,
                        skySeeds,
                        minimum,
                        maximum);
        sharedLightSections = sharedBlockLight.isEmpty() && sharedSkyLight.isEmpty()
                ? Set.of()
                : sectionsCovering(minimum, maximum);
        dirtySections.addAll(sharedLightSections);
        markSectionsDirty(minecraft, dirtySections);
    }

    /**
     * Sky that would reach this cell if present solids on historical air were
     * absent. Walks upward through non-remembered solids; open sky above the
     * memory, or live open air, yields full skylight so ADDED rubble cannot
     * cast an impossible shadow.
     */
    private static int historicalAirSkySeed(
            net.minecraft.world.level.Level level,
            Map<BlockPos, BlockState> rememberedStates,
            BlockPos.MutableBlockPos cursor,
            int maximumY) {
        int x = cursor.getX();
        int startY = cursor.getY();
        int z = cursor.getZ();
        for (int y = startY; y <= maximumY + 1; y++) {
            cursor.set(x, y, z);
            if (y > maximumY) {
                cursor.set(x, startY, z);
                return 15;
            }
            BlockPos sample = cursor.immutable();
            if (EchoBlockChange.claimsRememberedSolid(
                    rememberedStates.get(sample))) {
                int underPastRoof = level.getBrightness(LightLayer.SKY, cursor);
                cursor.set(x, startY, z);
                return underPastRoof;
            }
            BlockState present = level.getBlockState(cursor);
            int liveSky = level.getBrightness(LightLayer.SKY, cursor);
            if (!present.canOcclude() && liveSky > 0) {
                cursor.set(x, startY, z);
                return liveSky;
            }
        }
        cursor.set(x, startY, z);
        return 15;
    }

    private static Map<BlockPos, BlockState> rememberedStates(
            EchoSnapshot source,
            EchoWaveVolume volume) {
        return rememberedStates(source, volume, templateProjection);
    }

    private static Map<BlockPos, BlockState> rememberedStates(
            EchoSnapshot source,
            EchoWaveVolume volume,
            @Nullable ClientTemplateProjection projection) {
        if (projection != null && source.isTemplateReference()) {
            return projection.localStates(volume);
        }
        Map<BlockPos, BlockState> states = new HashMap<>(
                Math.min(
                        source.blocks().size(),
                        EchoProjectionBudget
                                .MAX_NETWORK_BLOCKS));
        for (SnapshotBlock block : source.blocks()) {
            BlockPos position =
                    source.worldPosition(block);
            if (volume.contains(
                    position.getCenter())) {
                states.put(
                        position.immutable(),
                        source.state(block));
            }
        }
        return states;
    }

    private static List<SnapshotEntity> entitySources(
            EchoSnapshot source,
            @Nullable ClientTemplateProjection projection) {
        if (EchoMemoryRevision.hasEntityOverlay(source)) {
            return source.entities();
        }
        if (projection != null && source.isTemplateReference()) {
            return projection.entities();
        }
        return source.entities();
    }

    private static void appendLoadedEntity(
            Minecraft minecraft,
            EchoSnapshot source,
            SnapshotEntity remembered,
            List<Entity> entities) {
        if (minecraft.level == null) {
            return;
        }
        try {
            Entity root = SnapshotEntityIO.load(
                            remembered,
                            minecraft.level,
                            source.origin(),
                            false)
                    .orElse(null);
            if (root == null) {
                return;
            }
            List<Entity> hierarchy = root.getSelfAndPassengers().toList();
            applyClientFrame(root, remembered.rootFrame());
            for (int index = 1;
                    index < hierarchy.size()
                            && index - 1 < remembered.passengerFrames().size();
                    index++) {
                applyClientFrame(
                        hierarchy.get(index),
                        remembered.passengerFrames().get(index - 1));
            }
            entities.addAll(hierarchy);
        } catch (RuntimeException ignored) {
            // A missing client renderer or disabled modded type must not break the block projection.
        }
    }

    private static void appendMemoryFaces(
            Minecraft minecraft,
            List<ScanFace> faces,
            GhostBlock changed,
            Map<BlockPos, BlockState> historicalStates) {
        if (minecraft.level == null) {
            return;
        }
        if (changed.state().isSolidRender()
                && !hasHistoricalExteriorFace(
                        minecraft,
                        changed.position(),
                        changed.state(),
                        historicalStates)) {
            return;
        }
        if (!appendModelFaces(
                minecraft,
                faces,
                changed.position(),
                changed.state(),
                null,
                historicalStates)) {
            appendShapeFaces(
                    minecraft,
                    faces,
                    changed.position(),
                    changed.state(),
                    null);
        }
    }

    private static List<Entity> buildEntityCache(Minecraft minecraft, EchoSnapshot source) {
        List<SnapshotEntity> sourceEntities = entitySources(source, templateProjection);
        if (minecraft.level == null || sourceEntities.isEmpty()) {
            return List.of();
        }
        List<Entity> entities = new ArrayList<>();
        for (SnapshotEntity remembered : sourceEntities) {
            appendLoadedEntity(minecraft, source, remembered, entities);
        }
        return List.copyOf(entities);
    }

    private static void applyClientFrame(
            Entity entity,
            SnapshotEntityFrame frame) {
        entity.snapTo(
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                frame.yRot(),
                frame.xRot());
        entity.setPose(frame.pose());
        entity.tickCount = frame.ageInTicks();
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        var animation = frame.animation();
        living.yBodyRot = living.yBodyRotO =
                frame.bodyYRot();
        living.yHeadRot = living.yHeadRotO =
                frame.headYRot();
        living.oAttackAnim = living.attackAnim =
                animation.attack();
        living.swinging = animation.swinging();
        living.swingingArm = animation.swingingOffHand()
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        living.swingTime = animation.swingTime();
        WalkAnimationStateAccessor walkAnimation =
                (WalkAnimationStateAccessor)
                        living.walkAnimation;
        walkAnimation.echoesShowThePast$setSpeedOld(
                animation.walkSpeed());
        walkAnimation.echoesShowThePast$setSpeed(
                animation.walkSpeed());
        walkAnimation.echoesShowThePast$setPosition(
                animation.walkPosition()
                        + animation.walkSpeed());
        walkAnimation.echoesShowThePast$setPositionScale(
                1.0F);
        if (animation.usingItem()) {
            living.startUsingItem(
                    animation.usingItemOffHand()
                            ? InteractionHand.OFF_HAND
                            : InteractionHand.MAIN_HAND);
            ((LivingEntityAccessor) living)
                    .echoesShowThePast$setUseItemRemaining(
                            animation
                                    .useItemRemainingTicks());
        }
        if (living instanceof Mob mob) {
            mob.setAggressive(
                    animation.aggressive());
        }
    }

    private static List<GhostBlock> buildGhostCache(
            Minecraft minecraft,
            EchoSnapshot source,
            Map<BlockPos, BlockState> historicalStates,
            @Nullable Map<BlockPos, BlockState> comparisonStates) {
        if (minecraft.level == null || !minecraft.level.dimension().equals(source.dimension())) {
            return List.of();
        }
        List<GhostBlock> changed = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry
                : historicalStates.entrySet()) {
            BlockPos position = entry.getKey();
            BlockState remembered = entry.getValue();
            BlockState present =
                    comparisonStates == null
                            ? minecraft.level
                                    .getBlockState(position)
                            : comparisonStates
                                    .getOrDefault(
                                            position,
                                            Blocks.AIR
                                                    .defaultBlockState());
            EchoBlockChange.Kind change = EchoBlockChange.classify(remembered, present);
            if (change.rendersRememberedBlock()) {
                changed.add(new GhostBlock(position.immutable(), remembered, change));
            }
        }
        return List.copyOf(changed);
    }

    private static List<GhostModel> buildGhostModels(
            Minecraft minecraft,
            List<GhostBlock> source,
            @Nullable Map<BlockPos, BlockState> modelEnvironment) {
        if (minecraft.level == null) {
            return List.of();
        }
        List<GhostModel> models = new ArrayList<>(source.size());
        for (GhostBlock ghost : source) {
            GhostModel model = buildGhostModel(
                    minecraft,
                    ghost,
                    modelEnvironment);
            if (model != null) {
                models.add(model);
            }
        }
        return List.copyOf(models);
    }

    /**
     * One model build is intentionally exposed to the template streamer. It
     * lets a huge authored memory amortize work over render frames while using
     * exactly the same face and fallback rules as ordinary snapshots.
     */
    static @Nullable GhostModel buildGhostModel(
            Minecraft minecraft,
            GhostBlock ghost,
            @Nullable Map<BlockPos, BlockState> modelEnvironment) {
        if (minecraft.level == null) {
            return null;
        }
        try {
            BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(ghost.state());
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(
                    minecraft.level,
                    ghost.position(),
                    ghost.state(),
                    RandomSource.create(ghost.position().asLong()),
                    parts);
            DirectionalLight lightCoords = buildGhostLight(
                    minecraft,
                    ghost.position(),
                    ghost.change());
            List<PreparedGhostQuad> preparedQuads = new ArrayList<>();
            boolean hasUnculledQuads = false;
            for (BlockStateModelPart part : parts) {
                for (Direction direction : Direction.values()) {
                    BlockState neighbor = modelEnvironment == null
                            ? minecraft.level.getBlockState(ghost.position().relative(direction))
                            : modelEnvironment.getOrDefault(
                                    ghost.position().relative(direction),
                                    Blocks.AIR.defaultBlockState());
                    boolean exterior = Block.shouldRenderFace(
                            minecraft.level,
                            ghost.position(),
                            ghost.state(),
                            neighbor,
                            direction);
                    // Remembered solids that share a face are normally culled at
                    // bake time. Keep those quads occupancy-gated so a faded
                    // neighbour reveals the interior shell instead of a hole.
                    // Fully enclosed remembered cells have no exterior face; they
                    // must still bake these gated quads or occupancy fade leaves
                    // an empty transparent wash with no walls behind it.
                    boolean occupancyGated = !exterior
                            && modelEnvironment != null
                            && !neighbor.isAir();
                    if (!exterior && !occupancyGated) {
                        continue;
                    }
                    for (BakedQuad quad : part.getQuads(direction)) {
                        preparedQuads.add(prepareGhostQuad(
                                minecraft,
                                ghost,
                                quad,
                                lightCoords,
                                occupancyGated ? direction : null));
                    }
                }
                List<BakedQuad> unculled = part.getQuads(null);
                if (!unculled.isEmpty()) {
                    hasUnculledQuads = true;
                    for (BakedQuad quad : unculled) {
                        preparedQuads.add(prepareGhostQuad(
                                minecraft,
                                ghost,
                                quad,
                                lightCoords,
                                null));
                    }
                }
            }
            List<AABB> fallbackBoxes = preparedQuads.isEmpty()
                    ? ghost.state().getShape(minecraft.level, ghost.position()).toAabbs()
                    : List.of();
            if (preparedQuads.isEmpty() && fallbackBoxes.isEmpty()) {
                fallbackBoxes = List.of(new AABB(0.125, 0.0, 0.125, 0.875, 0.875, 0.875));
            }
            TextureAtlasSprite fallbackSprite = preparedQuads.isEmpty()
                    ? model.particleMaterial(minecraft.level, ghost.position(), ghost.state()).sprite()
                    : null;
            Vec3 modelOffset = ghost.state().getOffset(ghost.position());
            return new GhostModel(
                    ghost.position(),
                    ghost.state(),
                    List.copyOf(preparedQuads),
                    List.copyOf(fallbackBoxes),
                    fallbackSprite,
                    ghost.change(),
                    modelOffset,
                    hasUnculledQuads || !fallbackBoxes.isEmpty(),
                    hasUnculledQuads,
                    travelDistanceTo(ghost.position()),
                    lightCoords,
                    new AABB(ghost.position()).inflate(0.02));
        } catch (RuntimeException ignored) {
            // Special/block-entity models can be unavailable while resources reload.
            return null;
        }
    }

    private static PreparedGhostQuad prepareGhostQuad(
            Minecraft minecraft,
            GhostBlock ghost,
            BakedQuad quad,
            DirectionalLight lightCoords,
            @Nullable Direction occupancyNeighborFace) {
        var a = quad.position0();
        var b = quad.position1();
        var c = quad.position2();
        var d = quad.position3();
        double centerX = (a.x() + b.x() + c.x() + d.x()) * 0.25;
        double centerY = (a.y() + b.y() + c.y() + d.y()) * 0.25;
        double centerZ = (a.z() + b.z() + c.z() + d.z()) * 0.25;
        double abX = b.x() - a.x();
        double abY = b.y() - a.y();
        double abZ = b.z() - a.z();
        double acX = c.x() - a.x();
        double acY = c.y() - a.y();
        double acZ = c.z() - a.z();
        double normalX = abY * acZ - abZ * acY;
        double normalY = abZ * acX - abX * acZ;
        double normalZ = abX * acY - abY * acX;
        double expectedX = quad.direction().getStepX();
        double expectedY = quad.direction().getStepY();
        double expectedZ = quad.direction().getStepZ();
        double lengthSquared = normalX * normalX
                + normalY * normalY
                + normalZ * normalZ;
        if (lengthSquared < 1.0E-8) {
            normalX = expectedX;
            normalY = expectedY;
            normalZ = expectedZ;
        } else {
            double inverseLength = 1.0 / Math.sqrt(lengthSquared);
            normalX *= inverseLength;
            normalY *= inverseLength;
            normalZ *= inverseLength;
            if (normalX * expectedX
                            + normalY * expectedY
                            + normalZ * expectedZ
                    < 0.0) {
                normalX = -normalX;
                normalY = -normalY;
                normalZ = -normalZ;
            }
        }
        int tint = -1;
        if (quad.materialInfo().isTinted()
                && minecraft.level != null) {
            var tintSource = minecraft.getBlockColors().getTintSource(
                    ghost.state(),
                    quad.materialInfo().tintIndex());
            if (tintSource != null) {
                tint = tintSource.colorInWorld(
                        ghost.state(),
                        minecraft.level,
                        ghost.position());
            }
        }
        return new PreparedGhostQuad(
                quad,
                ARGB.opaque(tint),
                lightCoords.at(quad.direction()),
                centerX,
                centerY,
                centerZ,
                normalX,
                normalY,
                normalZ,
                occupancyNeighborFace);
    }

    /**
     * Whether a ghost face toward {@code neighborFace} should draw under the
     * current occupancy fade. Exterior faces always draw; shared faces toward
     * another remembered solid draw once that neighbour has faded enough.
     */
    static boolean shouldDrawOccupancyFace(
            BlockPos position,
            @Nullable Direction neighborFace) {
        if (neighborFace == null) {
            return true;
        }
        return !EchoGhostOccupancy.occludesSharedFace(
                ghostOccupancyVisibility(position.relative(neighborFace)));
    }

    /**
     * Fallback box faces have no bake-time cull list, so a remembered solid
     * neighbour still occludes until occupancy clears it.
     */
    static boolean shouldDrawFallbackFace(
            BlockPos position,
            Direction face) {
        BlockState neighbor = rememberedStates.get(position.relative(face));
        if (neighbor == null || neighbor.isAir()) {
            return true;
        }
        return !EchoGhostOccupancy.occludesSharedFace(
                ghostOccupancyVisibility(position.relative(face)));
    }

    private static Map<Long, List<GhostModel>>
            indexGhostModels(
                    List<GhostModel> models) {
        if (models.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<GhostModel>> mutable =
                new HashMap<>();
        for (GhostModel model : models) {
            mutable.computeIfAbsent(
                            SectionPos.asLong(
                                    model.position()),
                            ignored ->
                                    new ArrayList<>())
                    .add(model);
        }
        Map<Long, List<GhostModel>> indexed =
                new HashMap<>(mutable.size());
        mutable.forEach((section, entries) ->
                indexed.put(
                        section,
                        List.copyOf(entries)));
        return Map.copyOf(indexed);
    }

    private static Set<Long> indexFadeImmunePositions(
            List<GhostModel> rememberedModels,
            List<GhostModel> presentModels) {
        Set<Long> indexed = new HashSet<>(
                rememberedModels.size() + presentModels.size());
        indexFadeImmunePositions(indexed, rememberedModels);
        indexFadeImmunePositions(indexed, presentModels);
        return Set.copyOf(indexed);
    }

    private static void indexFadeImmunePositions(
            Set<Long> indexed,
            List<GhostModel> models) {
        for (GhostModel model : models) {
            if (EchoGhostOccupancy.isFadeImmune(model.change())) {
                indexed.add(model.position().asLong());
            }
        }
    }

    static List<GhostModel> visibleRememberedCandidates(
            Frustum frustum,
            Vec3 camera) {
        List<GhostModel> local = visibleSectionModels(
                ghostModelSections,
                frustum,
                camera,
                EchoProjectionBudget.MAX_VISIBLE_GHOST_MODELS);
        ClientTemplateProjection projection = templateProjection;
        if (projection == null
                || snapshot == null
                || !snapshot.isTemplateReference()
                || local.size() >= EchoProjectionBudget.MAX_VISIBLE_GHOST_MODELS) {
            return local;
        }
        List<GhostModel> remote = projection.visibleModels(
                Minecraft.getInstance(),
                frustum,
                camera,
                EchoProjectionBudget.MAX_VISIBLE_GHOST_MODELS - local.size(),
                rememberedStates.keySet());
        if (remote.isEmpty()) {
            return local;
        }
        List<GhostModel> combined = new ArrayList<>(local.size() + remote.size());
        combined.addAll(local);
        combined.addAll(remote);
        return List.copyOf(combined);
    }

    static List<GhostModel> visiblePresentCandidates(
            Frustum frustum,
            Vec3 camera) {
        List<GhostModel> local = visibleSectionModels(
                presentGhostModelSections,
                frustum,
                camera,
                EchoProjectionBudget
                        .MAX_VISIBLE_GHOST_MODELS);
        ClientTemplateProjection projection = templateProjection;
        if (projection == null
                || snapshot == null
                || !snapshot.isTemplateReference()
                || local.size() >= EchoProjectionBudget.MAX_VISIBLE_GHOST_MODELS) {
            return local;
        }
        Set<BlockPos> locallyRenderedPositions = new HashSet<>(
                presentGhostModels.size() * 2);
        for (GhostModel model : presentGhostModels) {
            locallyRenderedPositions.add(model.position());
        }
        List<GhostModel> remote = projection.visiblePresentModels(
                Minecraft.getInstance(),
                frustum,
                camera,
                EchoProjectionBudget.MAX_VISIBLE_GHOST_MODELS - local.size(),
                locallyRenderedPositions);
        if (remote.isEmpty()) {
            return local;
        }
        List<GhostModel> combined = new ArrayList<>(local.size() + remote.size());
        combined.addAll(local);
        combined.addAll(remote);
        return List.copyOf(combined);
    }

    /**
     * Remote authored sections use Euclidean wave travel, while the nearby
     * cache keeps the more expensive routed/occluded crest. Both paths share
     * the same renderer and never require a world-sized arrival grid.
     */
    static List<ScanFace> visibleTemplateWaveFaces(
            Frustum frustum,
            Vec3 camera,
            Vec3 waveOrigin,
            double front,
            boolean returning) {
        ClientTemplateProjection projection = templateProjection;
        if (projection == null
                || snapshot == null
                || !snapshot.isTemplateReference()) {
            return List.of();
        }
        return projection.visibleWaveFaces(
                frustum,
                camera,
                rememberedStates.keySet(),
                waveOrigin,
                front,
                returning);
    }

    private static List<GhostModel> visibleSectionModels(
            Map<Long, List<GhostModel>> sections,
            Frustum frustum,
            Vec3 camera,
            int limit) {
        if (sections.isEmpty()
                || limit <= 0) {
            return List.of();
        }
        List<Map.Entry<Long, List<GhostModel>>>
                visibleSections =
                        new ArrayList<>();
        for (Map.Entry<Long, List<GhostModel>> entry
                : sections.entrySet()) {
            if (frustum.isVisible(
                    sectionBounds(entry.getKey()))) {
                visibleSections.add(entry);
            }
        }
        visibleSections.sort(
                Comparator.comparingDouble(entry ->
                        sectionBounds(entry.getKey())
                                .getCenter()
                                .distanceToSqr(
                                        camera)));
        List<GhostModel> result =
                new ArrayList<>(
                        Math.min(limit, 512));
        for (Map.Entry<Long, List<GhostModel>> section
                : visibleSections) {
            for (GhostModel model
                    : section.getValue()) {
                if (frustum.isVisible(
                    model.worldBounds())) {
                    result.add(model);
                    if (result.size() >= limit) {
                        return List.copyOf(result);
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static AABB sectionBounds(
            long section) {
        int minimumX =
                SectionPos.x(section) << 4;
        int minimumY =
                SectionPos.y(section) << 4;
        int minimumZ =
                SectionPos.z(section) << 4;
        return new AABB(
                minimumX,
                minimumY,
                minimumZ,
                minimumX + 16,
                minimumY + 16,
                minimumZ + 16);
    }

    private static DirectionalLight buildGhostLight(
            Minecraft minecraft,
            BlockPos position,
            EchoBlockChange.Kind change) {
        if (minecraft.level == null) {
            return DirectionalLight.DARK;
        }
        int pastBlockLight = EchoPastLight.sample(sharedBlockLight, position);
        int pastSkyLight = EchoPastLight.sample(sharedSkyLight, position);
        return surfaceWorldLight(minecraft, position).map(packedLight ->
                EchoPastLight.ghostPackedLight(
                        change,
                        packedLight,
                        pastBlockLight,
                        pastSkyLight));
    }

    private static DirectionalLight surfaceWorldLight(Minecraft minecraft, BlockPos position) {
        int internalLight = LevelRenderer.getLightCoords(minecraft.level, position);
        return new DirectionalLight(
                faceWorldLight(minecraft, position, internalLight, Direction.DOWN),
                faceWorldLight(minecraft, position, internalLight, Direction.UP),
                faceWorldLight(minecraft, position, internalLight, Direction.NORTH),
                faceWorldLight(minecraft, position, internalLight, Direction.SOUTH),
                faceWorldLight(minecraft, position, internalLight, Direction.WEST),
                faceWorldLight(minecraft, position, internalLight, Direction.EAST));
    }

    private static int faceWorldLight(
            Minecraft minecraft,
            BlockPos position,
            int internalLight,
            Direction direction) {
        return EchoPastLight.translucentFacePackedLight(
                internalLight,
                LevelRenderer.getLightCoords(minecraft.level, position.relative(direction)));
    }

    /**
     * Only a template memory can tell world terrain from remembered air: it is
     * the one whose bounds are the authored volume, so its lower corner anchors
     * the set. A personal capture recorded the world as it stood and needs no
     * such correction.
     *
     * <p>When the client receives a resolved block window instead of a template
     * reference, the site id still points at the intact template whose
     * companion additions set lives in client resources.</p>
     */
    private static @Nullable EchoSiteAdditions siteAdditions(
            Minecraft minecraft,
            EchoSnapshot source) {
        Identifier templateId = source.template().orElse(null);
        if (templateId == null && source.site().isPresent()) {
            EchoSiteType site = EchoSiteType.byId(source.site().orElseThrow());
            if (site != null) {
                templateId = site.intactTemplate();
            }
        }
        if (templateId == null) {
            return null;
        }
        return ClientTemplateProjection.additionsFor(minecraft, templateId);
    }

    /**
     * Authored sites without a packaged additions set must leave world terrain
     * alone (the coarser {@code true} reading eats the biome). Personal captures
     * keep the coarse reading: a new solid where the capture stored nothing is
     * something that appeared after the memory.
     */
    private static boolean authoredBySite(
            EchoSnapshot source,
            @Nullable EchoSiteAdditions additions,
            BlockPos position,
            BlockPos memoryCorner) {
        if (additions != null) {
            return additions.contains(position, memoryCorner);
        }
        return source.site().isEmpty();
    }

    private static List<GhostBlock> buildPresentOccluderCache(
            Minecraft minecraft,
            EchoSnapshot source,
            Map<BlockPos, BlockState> rememberedStates,
            Map<BlockPos, BlockState> comparisonStates) {
        if (minecraft.level == null) {
            return List.of();
        }
        List<GhostBlock> occluders = new ArrayList<>();
        EchoSiteAdditions additions = siteAdditions(minecraft, source);
        BlockPos memoryCorner = source.origin().offset(
                source.boundsMin().orElse(BlockPos.ZERO));
        for (Map.Entry<BlockPos, BlockState> entry
                : comparisonStates.entrySet()) {
            BlockPos position = entry.getKey();
            if (!source.containsWorldPosition(position)) {
                // This position was never captured. Treat it as unknown,
                // not as historical air, even if the player has moved the
                // current acoustic pulse across it.
                continue;
            }
            BlockState current = entry.getValue();
            BlockState remembered =
                    rememberedStates.get(position);
            EchoBlockChange.Kind change =
                    EchoBlockChange.classify(
                            remembered,
                            current,
                            authoredBySite(
                                    source,
                                    additions,
                                    position,
                                    memoryCorner));
            if (!change.canFadePresentBlock()
                    || !EchoBlockChange
                            .shouldHidePresentGeometry(
                                    current)) {
                continue;
            }
            occluders.add(new GhostBlock(
                    position,
                    current,
                    change));
        }
        return List.copyOf(occluders);
    }

    private static Map<Long, SurfaceTiming> buildPresentOccluderTimings(
            List<GhostBlock> occluders) {
        if (occluders.isEmpty()) {
            return Map.of();
        }
        Map<Long, SurfaceTiming> timings = new HashMap<>(
                occluders.size() * 2);
        List<BlockPos> historicalAirVolume = new ArrayList<>();
        Map<Long, Double> surfaceSeeds = new HashMap<>();
        for (GhostBlock occluder : occluders) {
            long packed = occluder.position().asLong();
            EchoMaterialResponse.Profile response =
                    EchoMaterialResponse.forState(
                            occluder.state());
            if (occluder.change() != EchoBlockChange.Kind.ADDED) {
                double euclidean = occluder.position()
                        .getCenter()
                        .distanceTo(sonarOrigin);
                // Prefer the nearer of routed arrival and Euclidean. A long
                // routed path used alone delayed REPLACED hide while the fade
                // seed already knew the shorter radial distance.
                double distance = Math.min(
                        euclidean,
                        travelDistanceTo(occluder.position()));
                timings.put(
                        packed,
                        new SurfaceTiming(
                                distance,
                                response));
                continue;
            }

            historicalAirVolume.add(
                    occluder.position());
            double nearestSurface = Double.POSITIVE_INFINITY;
            if (!arrivalField.isEmpty()) {
                for (Direction direction : Direction.values()) {
                    ClientEchoArrivalField.SurfaceSample sample =
                            arrivalField.surfaceSampleAt(
                                    occluder.position(),
                                    new Vec3(
                                            direction.getStepX(),
                                            direction.getStepY(),
                                            direction.getStepZ()));
                    if (sample.reached()) {
                        nearestSurface = Math.min(
                                nearestSurface,
                                sample.arrival());
                    }
                }
            }
            // A non-empty arrival field that never samples this solid must not
            // leave the cell unseeded: fallen rubble in historical air then
            // kept an infinite timing that blocked the remote euclidean path
            // and the live mesh never yielded.
            if (!Double.isFinite(nearestSurface)) {
                nearestSurface = occluder.position()
                        .getCenter()
                        .distanceTo(sonarOrigin);
            }
            if (Double.isFinite(nearestSurface)) {
                surfaceSeeds.put(
                        packed,
                        nearestSurface);
            }
        }

        Map<Long, Double> propagated =
                EchoOccluderPropagation.propagate(
                        historicalAirVolume,
                        surfaceSeeds,
                        OCCLUDER_PENETRATION_DISTANCE_PER_LAYER);
        for (GhostBlock occluder : occluders) {
            if (occluder.change() != EchoBlockChange.Kind.ADDED) {
                continue;
            }
            double distance = propagated.getOrDefault(
                    occluder.position().asLong(),
                    Double.POSITIVE_INFINITY);
            if (!Double.isFinite(distance)) {
                distance = occluder.position()
                        .getCenter()
                        .distanceTo(sonarOrigin);
            }
            timings.put(
                    occluder.position().asLong(),
                    new SurfaceTiming(
                            distance,
                            EchoMaterialResponse.forState(
                                    occluder.state())));
        }
        return Map.copyOf(timings);
    }

    static List<ScanFace> buildPresentFaces(Minecraft minecraft, EchoWaveVolume volume) {
        return buildPresentFaces(
                minecraft,
                volume.minBlock(),
                volume.maxBlock(),
                volume);
    }

    static List<ScanFace> buildPresentFacesInSection(
            Minecraft minecraft,
            BlockPos sectionOrigin) {
        return buildPresentFaces(
                minecraft,
                sectionOrigin,
                sectionOrigin.offset(15, 15, 15),
                null);
    }

    private static List<ScanFace> buildPresentFaces(
            Minecraft minecraft,
            BlockPos minimum,
            BlockPos maximum,
            @Nullable EchoWaveVolume volume) {
        if (minecraft.level == null) {
            return List.of();
        }
        List<ScanFace> faces = new ArrayList<>();
        double maximumDistanceSquared = volume == null
                ? Double.POSITIVE_INFINITY
                : (volume.radius() + 1.0) * (volume.radius() + 1.0);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minimum.getX(); x <= maximum.getX(); x++) {
            for (int y = minimum.getY(); y <= maximum.getY(); y++) {
                for (int z = minimum.getZ(); z <= maximum.getZ(); z++) {
                    cursor.set(x, y, z);
                    if (!minecraft.level.isInWorldBounds(cursor)) {
                        continue;
                    }
                    BlockState state = minecraft.level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    BlockPos position = cursor.immutable();
                    if (volume != null
                            && squaredDistanceToCenter(position, volume.center())
                                    > maximumDistanceSquared) {
                        continue;
                    }
                    if (state.isSolidRender() && !hasVisibleExteriorFace(minecraft, position, state)) {
                        continue;
                    }
                    if (!appendModelFaces(minecraft, faces, position, state, volume, null)) {
                        appendShapeFaces(minecraft, faces, position, state, volume);
                    }
                }
            }
        }
        return freezeFacesByDistance(faces);
    }

    private static double squaredDistanceToCenter(
            BlockPos position,
            Vec3 center) {
        double x = position.getX() + 0.5 - center.x;
        double y = position.getY() + 0.5 - center.y;
        double z = position.getZ() + 0.5 - center.z;
        return x * x + y * y + z * z;
    }

    private static boolean hasVisibleExteriorFace(
            Minecraft minecraft,
            BlockPos position,
            BlockState state) {
        if (minecraft.level == null) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (Block.shouldRenderFace(
                    minecraft.level,
                    position,
                    state,
                    minecraft.level.getBlockState(position.relative(direction)),
                    direction)) {
                return true;
            }
        }
        return false;
    }

    private static ScanBounds changeDetectionBounds(EchoSnapshot source) {
        if (source.sealed()) {
            return snapshotContentBounds(source);
        }
        BlockPos origin = source.origin();
        int radius = source.radius();
        return new ScanBounds(
                origin.getX() - radius,
                origin.getY() - radius,
                origin.getZ() - radius,
                origin.getX() + radius,
                origin.getY() + radius,
                origin.getZ() + radius);
    }

    private static ScanBounds snapshotContentBounds(EchoSnapshot source) {
        if (source.boundsMin().isPresent() && source.boundsMax().isPresent()) {
            BlockPos minimum = source.origin().offset(source.boundsMin().orElseThrow());
            BlockPos maximum = source.origin().offset(source.boundsMax().orElseThrow());
            return new ScanBounds(
                    minimum.getX(),
                    minimum.getY(),
                    minimum.getZ(),
                    maximum.getX(),
                    maximum.getY(),
                    maximum.getZ());
        }
        if (source.blocks().isEmpty()) {
            int radius = source.radius();
            BlockPos origin = source.origin();
            return new ScanBounds(
                    origin.getX() - radius,
                    origin.getY() - radius,
                    origin.getZ() - radius,
                    origin.getX() + radius,
                    origin.getY() + radius,
                    origin.getZ() + radius);
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (SnapshotBlock block : source.blocks()) {
            BlockPos position = source.worldPosition(block);
            minX = Math.min(minX, position.getX());
            minY = Math.min(minY, position.getY());
            minZ = Math.min(minZ, position.getZ());
            maxX = Math.max(maxX, position.getX());
            maxY = Math.max(maxY, position.getY());
            maxZ = Math.max(maxZ, position.getZ());
        }
        return new ScanBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    static List<ScanFace> buildMemoryFaces(
            Minecraft minecraft,
            Map<BlockPos, BlockState> rememberedStates,
            List<GhostBlock> changedBlocks) {
        if (minecraft.level == null) {
            return List.of();
        }
        List<ScanFace> faces = new ArrayList<>();
        for (GhostBlock changed : changedBlocks) {
            if (changed.state().isSolidRender()
                    && !hasHistoricalExteriorFace(
                            minecraft,
                            changed.position(),
                            changed.state(),
                            rememberedStates)) {
                continue;
            }
            if (!appendModelFaces(
                    minecraft,
                    faces,
                    changed.position(),
                    changed.state(),
                    null,
                    rememberedStates)) {
                appendShapeFaces(minecraft, faces, changed.position(), changed.state(), null);
            }
        }
        return freezeFacesByDistance(faces);
    }

    private static boolean hasHistoricalExteriorFace(
            Minecraft minecraft,
            BlockPos position,
            BlockState state,
            Map<BlockPos, BlockState> historicalStates) {
        if (minecraft.level == null) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            BlockState neighbor =
                    historicalStates.getOrDefault(
                            position.relative(direction),
                            Blocks.AIR
                                    .defaultBlockState());
            if (Block.shouldRenderFace(
                    minecraft.level,
                    position,
                    state,
                    neighbor,
                    direction)) {
                return true;
            }
        }
        return false;
    }

    private static List<ScanFace> freezeFacesByDistance(
            List<ScanFace> faces) {
        faces.sort(Comparator.comparingDouble(ScanFace::distance));
        return List.copyOf(faces);
    }

    private static List<ScanFace> withArrivalDistances(
            List<ScanFace> faces) {
        if (arrivalField.isEmpty()) {
            return faces;
        }
        List<ScanFace> rebased = new ArrayList<>(faces.size());
        for (ScanFace face : faces) {
            ClientEchoArrivalField.SurfaceSample sample =
                    arrivalField.surfaceSampleAt(
                    face.position(),
                    face.normal());
            if (!sample.reached()) {
                continue;
            }
            EchoSurfaceCrestPath.FaceDistances waveDistances =
                    routedFaceDistances(
                            face.position(),
                            face.normal(),
                            face.a(),
                            face.b(),
                            face.c(),
                            face.d(),
                            face.center(),
                            sample);
            rebased.add(new ScanFace(
                    face.position(),
                    face.a(),
                    face.b(),
                    face.c(),
                    face.d(),
                    face.center(),
                    face.normal(),
                    face.twoSided(),
                    face.response(),
                    face.waveUv(),
                    sample.arrival(),
                    sample.gradient(),
                    waveDistances));
        }
        return freezeFacesByDistance(rebased);
    }

    private static boolean appendModelFaces(
            Minecraft minecraft,
            List<ScanFace> faces,
            BlockPos position,
            BlockState state,
            @Nullable EchoWaveVolume volume,
            @Nullable Map<BlockPos, BlockState> rememberedStates) {
        if (minecraft.level == null || state.getRenderShape() != RenderShape.MODEL) {
            return false;
        }
        int initialSize = faces.size();
        try {
            BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(
                    minecraft.level,
                    position,
                    state,
                    RandomSource.create(state.getSeed(position)),
                    parts);
            Vec3 blockOffset = state.getOffset(position);
            for (BlockStateModelPart part : parts) {
                for (Direction direction : Direction.values()) {
                    BlockState neighbor = rememberedStates == null
                            ? minecraft.level.getBlockState(position.relative(direction))
                            : rememberedStates.getOrDefault(
                                    position.relative(direction),
                                    Blocks.AIR.defaultBlockState());
                    if (!Block.shouldRenderFace(
                            minecraft.level,
                            position,
                            state,
                            neighbor,
                            direction)) {
                        continue;
                    }
                    for (BakedQuad quad : part.getQuads(direction)) {
                            appendQuadFace(faces, position, state, blockOffset, quad, false, volume);
                    }
                }
                for (BakedQuad quad : part.getQuads(null)) {
                    appendQuadFace(faces, position, state, blockOffset, quad, true, volume);
                }
            }
        } catch (RuntimeException ignored) {
            // Resource reloads and custom models can briefly make baked geometry unavailable.
        }
        return faces.size() > initialSize;
    }

    private static void appendQuadFace(
            List<ScanFace> faces,
            BlockPos position,
            BlockState state,
            Vec3 blockOffset,
            BakedQuad quad,
            boolean twoSided,
            @Nullable EchoWaveVolume volume) {
        Vec3 normalOffset = new Vec3(
                quad.direction().getStepX() * SCAN_SURFACE_OFFSET,
                quad.direction().getStepY() * SCAN_SURFACE_OFFSET,
                quad.direction().getStepZ() * SCAN_SURFACE_OFFSET);
        Vec3 base = new Vec3(position.getX(), position.getY(), position.getZ())
                .add(blockOffset)
                .add(normalOffset);
        Vec3 a = base.add(quad.position0().x(), quad.position0().y(), quad.position0().z());
        Vec3 b = base.add(quad.position1().x(), quad.position1().y(), quad.position1().z());
        Vec3 c = base.add(quad.position2().x(), quad.position2().y(), quad.position2().z());
        Vec3 d = base.add(quad.position3().x(), quad.position3().y(), quad.position3().z());
        Vec3 normal = new Vec3(
                quad.direction().getStepX(),
                quad.direction().getStepY(),
                quad.direction().getStepZ());
        appendScanFace(
                faces,
                position,
                a,
                b,
                c,
                d,
                normal,
                twoSided,
                EchoMaterialResponse.forState(state),
                new WaveUv(
                        quad.packedUV0(),
                        quad.packedUV1(),
                        quad.packedUV2(),
                        quad.packedUV3()),
                volume);
    }

    private static void appendShapeFaces(
            Minecraft minecraft,
            List<ScanFace> faces,
            BlockPos position,
            BlockState state,
            @Nullable EchoWaveVolume volume) {
        if (minecraft.level == null) {
            return;
        }
        WaveUv waveUv;
        try {
            BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
            TextureAtlasSprite sprite =
                    model.particleMaterial(minecraft.level, position, state).sprite();
            waveUv = WaveUv.fullSprite(sprite);
        } catch (RuntimeException ignored) {
            return;
        }
        Vec3 offset = new Vec3(position.getX(), position.getY(), position.getZ());
        for (net.minecraft.world.phys.AABB box
                : state.getShape(minecraft.level, position).toAabbs()) {
            double minX = box.minX + offset.x;
            double minY = box.minY + offset.y;
            double minZ = box.minZ + offset.z;
            double maxX = box.maxX + offset.x;
            double maxY = box.maxY + offset.y;
            double maxZ = box.maxZ + offset.z;
            appendScanFace(
                    faces, position,
                    new Vec3(minX, minY, minZ), new Vec3(minX, minY, maxZ),
                    new Vec3(maxX, minY, maxZ), new Vec3(maxX, minY, minZ),
                    new Vec3(0.0, -1.0, 0.0), false,
                    EchoMaterialResponse.forState(state),
                    waveUv,
                    volume);
            appendScanFace(
                    faces, position,
                    new Vec3(minX, maxY, minZ), new Vec3(maxX, maxY, minZ),
                    new Vec3(maxX, maxY, maxZ), new Vec3(minX, maxY, maxZ),
                    new Vec3(0.0, 1.0, 0.0), false,
                    EchoMaterialResponse.forState(state),
                    waveUv,
                    volume);
            appendScanFace(
                    faces, position,
                    new Vec3(minX, minY, minZ), new Vec3(maxX, minY, minZ),
                    new Vec3(maxX, maxY, minZ), new Vec3(minX, maxY, minZ),
                    new Vec3(0.0, 0.0, -1.0), false,
                    EchoMaterialResponse.forState(state),
                    waveUv,
                    volume);
            appendScanFace(
                    faces, position,
                    new Vec3(minX, minY, maxZ), new Vec3(minX, maxY, maxZ),
                    new Vec3(maxX, maxY, maxZ), new Vec3(maxX, minY, maxZ),
                    new Vec3(0.0, 0.0, 1.0), false,
                    EchoMaterialResponse.forState(state),
                    waveUv,
                    volume);
            appendScanFace(
                    faces, position,
                    new Vec3(minX, minY, minZ), new Vec3(minX, maxY, minZ),
                    new Vec3(minX, maxY, maxZ), new Vec3(minX, minY, maxZ),
                    new Vec3(-1.0, 0.0, 0.0), false,
                    EchoMaterialResponse.forState(state),
                    waveUv,
                    volume);
            appendScanFace(
                    faces, position,
                    new Vec3(maxX, minY, minZ), new Vec3(maxX, minY, maxZ),
                    new Vec3(maxX, maxY, maxZ), new Vec3(maxX, maxY, minZ),
                    new Vec3(1.0, 0.0, 0.0), false,
                    EchoMaterialResponse.forState(state),
                    waveUv,
                    volume);
        }
    }

    private static void appendScanFace(
            List<ScanFace> faces,
            BlockPos position,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            Vec3 normal,
            boolean twoSided,
            EchoMaterialResponse.Profile response,
            WaveUv waveUv,
            @Nullable EchoWaveVolume volume) {
        Vec3 center = a.add(b).add(c).add(d).scale(0.25);
        if (volume == null || volume.contains(center)) {
            Vec3 distanceOrigin =
                    volume == null
                            ? sonarOrigin
                            : volume.center();
            double distance;
            Vec3 travelGradient;
            EchoSurfaceCrestPath.FaceDistances waveDistances;
            if (volume != null
                    && !arrivalField.isEmpty()) {
                ClientEchoArrivalField.SurfaceSample sample =
                        arrivalField.surfaceSampleAt(
                                position,
                                normal);
                if (!sample.reached()) {
                    return;
                }
                distance = sample.arrival();
                travelGradient =
                        sample.gradient();
                waveDistances = routedFaceDistances(
                        position,
                        normal,
                        a,
                        b,
                        c,
                        d,
                        center,
                        sample);
            } else {
                distance =
                        center.distanceTo(
                                distanceOrigin);
                Vec3 radial =
                        center.subtract(
                                distanceOrigin);
                travelGradient =
                        radial.lengthSqr()
                                        <= 1.0E-8
                                ? Vec3.ZERO
                                : radial.normalize();
                waveDistances = new EchoSurfaceCrestPath.FaceDistances(
                        a.distanceTo(distanceOrigin),
                        b.distanceTo(distanceOrigin),
                        c.distanceTo(distanceOrigin),
                        d.distanceTo(distanceOrigin));
            }
            if (!Double.isFinite(distance)) {
                return;
            }
            faces.add(new ScanFace(
                    position,
                    a,
                    b,
                    c,
                    d,
                    center,
                    normal,
                    twoSided,
                    response,
                    waveUv,
                    distance,
                    travelGradient,
                    waveDistances));
        }
    }

    /**
     * Cheap exterior face used by a distant authored template. The detailed
     * model stream is intentionally independent from the pulse, so the crest
     * never has to wait for model baking before it can cross a large island.
     * Only solid template blocks reach this path. Broad coplanar surfaces are
     * tiled before reaching this method, so every tile remains a continuous
     * part of the authored exterior rather than a sparse point sample.
     */
    static ScanFace buildTemplateWaveFace(
            BlockPos position,
            Direction direction,
            BlockState state,
            int width,
            int height,
            Vec3 waveOrigin) {
        double minX = position.getX();
        double minY = position.getY();
        double minZ = position.getZ();
        Vec3 normal = new Vec3(
                direction.getStepX(),
                direction.getStepY(),
                direction.getStepZ());
        Vec3 offset = normal.scale(SCAN_SURFACE_OFFSET);
        Vec3 a;
        Vec3 b;
        Vec3 c;
        Vec3 d;
        switch (direction) {
            case DOWN -> {
                a = new Vec3(minX, minY, minZ);
                b = new Vec3(minX, minY, minZ + height);
                c = new Vec3(minX + width, minY, minZ + height);
                d = new Vec3(minX + width, minY, minZ);
            }
            case UP -> {
                a = new Vec3(minX, minY + 1.0, minZ);
                b = new Vec3(minX + width, minY + 1.0, minZ);
                c = new Vec3(minX + width, minY + 1.0, minZ + height);
                d = new Vec3(minX, minY + 1.0, minZ + height);
            }
            case NORTH -> {
                a = new Vec3(minX, minY, minZ);
                b = new Vec3(minX + width, minY, minZ);
                c = new Vec3(minX + width, minY + height, minZ);
                d = new Vec3(minX, minY + height, minZ);
            }
            case SOUTH -> {
                a = new Vec3(minX, minY, minZ + 1.0);
                b = new Vec3(minX, minY + height, minZ + 1.0);
                c = new Vec3(minX + width, minY + height, minZ + 1.0);
                d = new Vec3(minX + width, minY, minZ + 1.0);
            }
            case WEST -> {
                a = new Vec3(minX, minY, minZ);
                b = new Vec3(minX, minY + height, minZ);
                c = new Vec3(minX, minY + height, minZ + width);
                d = new Vec3(minX, minY, minZ + width);
            }
            case EAST -> {
                a = new Vec3(minX + 1.0, minY, minZ);
                b = new Vec3(minX + 1.0, minY, minZ + width);
                c = new Vec3(minX + 1.0, minY + height, minZ + width);
                d = new Vec3(minX + 1.0, minY + height, minZ);
            }
            default -> throw new IllegalStateException(
                    "Unexpected direction: " + direction);
        }
        a = a.add(offset);
        b = b.add(offset);
        c = c.add(offset);
        d = d.add(offset);
        Vec3 center = a.add(b).add(c).add(d).scale(0.25);
        Vec3 radial = center.subtract(waveOrigin);
        Vec3 gradient = radial.lengthSqr() <= 1.0E-8
                ? Vec3.ZERO
                : radial.normalize();
        return new ScanFace(
                position,
                a,
                b,
                c,
                d,
                center,
                normal,
                false,
                EchoMaterialResponse.forState(state),
                FULL_BLOCK_WAVE_UV,
                center.distanceTo(waveOrigin),
                gradient,
                new EchoSurfaceCrestPath.FaceDistances(
                        a.distanceTo(waveOrigin),
                        b.distanceTo(waveOrigin),
                        c.distanceTo(waveOrigin),
                        d.distanceTo(waveOrigin)));
    }

    private static EchoSurfaceCrestPath.FaceDistances routedFaceDistances(
            BlockPos position,
            Vec3 normal,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            Vec3 center,
            ClientEchoArrivalField.SurfaceSample centerSample) {
        return new EchoSurfaceCrestPath.FaceDistances(
                routedVertexDistance(
                        position,
                        normal,
                        a,
                        center,
                        centerSample),
                routedVertexDistance(
                        position,
                        normal,
                        b,
                        center,
                        centerSample),
                routedVertexDistance(
                        position,
                        normal,
                        c,
                        center,
                        centerSample),
                routedVertexDistance(
                        position,
                        normal,
                        d,
                        center,
                        centerSample));
    }

    private static double routedVertexDistance(
            BlockPos position,
            Vec3 normal,
            Vec3 point,
            Vec3 center,
            ClientEchoArrivalField.SurfaceSample centerSample) {
        float sampled = arrivalField.surfaceDistanceAt(
                position,
                normal,
                point);
        if (Float.isFinite(sampled)) {
            return sampled;
        }
        return EchoSurfaceCrestPath.distanceAtPoint(
                centerSample.arrival(),
                center,
                point,
                centerSample.gradient(),
                sonarOrigin);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        if (snapshot != null && (minecraft.level == null || !minecraft.level.dimension().equals(snapshot.dimension()))) {
            clearImmediately();
        } else if (snapshot != null
                && pendingPreparation == null
                && fadeStartNanos == Long.MAX_VALUE) {
            boolean stoneControlsWorld =
                    ClientPhilosophersStoneVision.controlsEchoCaches(now);
            EchoCacheHandoff.Action handoff =
                    EchoCacheHandoff.decide(
                            stoneControlsWorld,
                            pendingRevision != null,
                            worldStateDirty,
                            stoneControlReleased);
            if (handoff != EchoCacheHandoff.Action.HOLD
                    && minecraft.level != null) {
                if (handoff
                        == EchoCacheHandoff.Action
                                .REBUILD_AND_REFRESH_SECTIONS) {
                    clearPresentOccluderFiltering(minecraft);
                }
                EchoSnapshot revision = pendingRevision;
                if (revision != null) {
                    snapshot = revision;
                    ghostEntities =
                            pendingGhostEntities == null
                                    ? buildEntityCache(
                                            minecraft,
                                            revision)
                                    : pendingGhostEntities;
                }
                if (snapshot != null) {
                    rebuildBlockCaches(
                            minecraft,
                            localWaveVolume(
                                    snapshot,
                                    sonarOrigin));
                }
                pendingRevision = null;
                pendingGhostEntities = null;
                pendingRevisionVisuals = null;
                worldStateDirty = false;
                stoneControlReleased = false;
            }
            if (!stoneControlsWorld) {
                updatePresentOccluderFiltering(minecraft, now);
            }
        } else if (snapshot != null && fadeStartNanos != Long.MAX_VALUE && now - fadeStartNanos >= FADE_DURATION_NANOS) {
            clearImmediately();
        }
    }

    /**
     * RenderFrame.Pre owns only post-chain lifetime. Wave uniforms cannot be
     * uploaded here: level render-state extraction has not produced this
     * frame's camera matrices or crest radius yet.
     */
    public static void renderFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        float strength = combinedShadowStrength(now);
        if (!EchoesConfig.POST_PROCESSING.getAsBoolean()) {
            clearOwnedPostEffect(minecraft);
        } else if (strength <= 0.0F) {
            clearOwnedPostEffect(minecraft);
        } else if (!postEffectOwned) {
            setOwnedPostEffect(minecraft, postEffectForStrength(strength));
        }
    }

    /**
     * Publishes the camera, local crest and depth crest extracted for the same
     * render frame. Keeping this after both wave renderers removes the one-frame
     * phase error that was visible at their twelve-block handoff.
     */
    static void publishExtractedPostFrame() {
        if (!postEffectOwned || !EchoesConfig.POST_PROCESSING.getAsBoolean()) {
            return;
        }
        long now = System.nanoTime();
        float strength = combinedShadowStrength(now);
        if (strength > 0.0F) {
            updatePostEffect(Minecraft.getInstance(), strength);
        }
    }

    static float combinedShadowStrength(long now) {
        return Math.max(
                shadowStrength(now),
                ClientLowFrequencySonarState.visualShadowStrength(now));
    }

    public static void preparePostEffect() {
        if (!EchoesConfig.POST_PROCESSING.getAsBoolean()) {
            return;
        }
        try {
            EchoPostEffectUniforms.prepare(
                    Minecraft.getInstance(),
                    POST_EFFECT_STAGES.getFirst());
        } catch (RuntimeException exception) {
            reportPostEffectFailure("prepare", exception);
        }
    }

    /**
     * Exercises the real baked-model, tint, collision and face paths while the
     * client is still entering/reloading a world. Synthetic solver warm-up
     * alone cannot compile these Minecraft-side calls, which were the remaining
     * cold portion of the first activation profile.
     */
    static void prepareRenderRuntime(Minecraft minecraft) {
        if (renderRuntimePrepared
                || minecraft.level == null
                || minecraft.player == null) {
            return;
        }
        renderRuntimePrepared = true;
        BlockPos center = minecraft.player.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        List<ScanFace> discardedFaces = new ArrayList<>();
        int preparedModels = 0;
        for (int y = -5; y <= 5 && preparedModels < 128; y++) {
            for (int x = -5; x <= 5 && preparedModels < 128; x++) {
                for (int z = -5; z <= 5 && preparedModels < 128; z++) {
                    cursor.setWithOffset(center, x, y, z);
                    if (!minecraft.level.isInWorldBounds(cursor)
                            || !minecraft.level.hasChunkAt(cursor)) {
                        continue;
                    }
                    BlockState state = minecraft.level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    BlockPos position = cursor.immutable();
                    try {
                        state.getCollisionShape(minecraft.level, position)
                                .toAabbs();
                        if (state.getRenderShape() != RenderShape.MODEL) {
                            continue;
                        }
                        GhostBlock ghost = new GhostBlock(
                                position,
                                state,
                                EchoBlockChange.Kind.MISSING);
                        buildGhostModel(minecraft, ghost, null);
                        if (!appendModelFaces(
                                minecraft,
                                discardedFaces,
                                position,
                                state,
                                null,
                                null)) {
                            appendShapeFaces(
                                    minecraft,
                                    discardedFaces,
                                    position,
                                    state,
                                    null);
                        }
                        preparedModels++;
                    } catch (RuntimeException ignored) {
                        // One custom block must not prevent the remaining
                        // vanilla/model paths from being prepared.
                    }
                }
            }
        }
        LOGGER.info(
                "Prepared {} nearby Past Echo model paths during world loading",
                preparedModels);
    }

    static void invalidateRenderRuntime() {
        renderRuntimePrepared = false;
    }

    private static void beginFade() {
        if (snapshot == null) {
            clearOwnedPostEffect(Minecraft.getInstance());
            return;
        }
        pendingPreparation = null;
        preparationGeneration++;
        clearPresentOccluderFiltering(Minecraft.getInstance());
        fadeStartNanos = System.nanoTime();
    }

    public static void clearImmediately() {
        Minecraft minecraft = Minecraft.getInstance();
        Set<Long> previousSharedLightSections = sharedLightSections;
        clearOwnedPostEffect(minecraft);
        clearPresentOccluderFiltering(minecraft);
        pendingPreparation = null;
        preparationGeneration++;
        snapshot = null;
        templateProjection = null;
        localWaveVolume = null;
        ghosts = List.of();
        ghostModels = List.of();
        ghostModelSections = Map.of();
        visibleGhostModels = List.of();
        presentGhostModels = List.of();
        presentGhostModelSections = Map.of();
        visiblePresentGhostModels = List.of();
        ghostOccupancyVisibility.clear();
        ghostFadeImmunePositions = Set.of();
        ghostOccupancyNanos = 0L;
        occupancyCameraBlock = BlockPos.ZERO;
        ghostEntities = List.of();
        presentOccluders = List.of();
        presentOccluderTimings = Map.of();
        presentFaces = List.of();
        returnCarrierFaces = List.of();
        memoryFaces = List.of();
        memoryEchoFaces = List.of();
        arrivalField = ClientEchoArrivalField.EMPTY;
        surfaceGeometryReady = false;
        presentOccluderFilteringSettled = false;
        appliedTemplatePresentRevision = -1;
        rememberedStates = Map.of();
        presentBaselineStates = Map.of();
        sharedBlockLight = Map.of();
        sharedSkyLight = Map.of();
        sharedLightSections = Set.of();
        markSectionsDirty(minecraft, previousSharedLightSections);
        sonarOrigin = Vec3.ZERO;
        scanRadius = 0.0;
        pulseTiming = EchoPulseTiming.forRadius(1.0);
        activationNanos = 0L;
        fadeStartNanos = Long.MAX_VALUE;
        worldStateDirty = false;
        pendingRevision = null;
        pendingGhostEntities = null;
        pendingRevisionVisuals = null;
        stoneControlReleased = false;
    }

    private static void clearOwnedPostEffect(Minecraft minecraft) {
        if (postEffectOwned && isEchoPostEffect(minecraft.gameRenderer.currentPostEffect())) {
            minecraft.gameRenderer.clearPostEffect();
        }
        postEffectOwned = false;
        activePostEffect = null;
    }

    private static void updatePostEffect(Minecraft minecraft, float strength) {
        Identifier current = minecraft.gameRenderer.currentPostEffect();
        if (!isEchoPostEffect(current)) {
            postEffectOwned = false;
            activePostEffect = null;
            return;
        }
        Identifier desired = postEffectForStrength(strength);
        if (!desired.equals(activePostEffect)) {
            setOwnedPostEffect(minecraft, desired);
        }
        if (postEffectOwned) {
            try {
                EchoPostEffectUniforms.update(minecraft, desired, strength);
            } catch (RuntimeException exception) {
                reportPostEffectFailure("update", exception);
                clearOwnedPostEffect(minecraft);
            }
        }
    }

    private static void setOwnedPostEffect(Minecraft minecraft, Identifier effect) {
        try {
            minecraft.gameRenderer.setPostEffect(effect);
            activePostEffect = effect;
            postEffectOwned = true;
        } catch (RuntimeException exception) {
            reportPostEffectFailure("activate " + effect, exception);
            activePostEffect = null;
            postEffectOwned = false;
        }
    }

    private static boolean isEchoPostEffect(@Nullable Identifier effect) {
        return EchoPostEffects.contains(POST_EFFECT_STAGES, effect);
    }

    private static Identifier effect(String path) {
        return Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, path);
    }

    private static List<Identifier> createPostEffectStages() {
        return List.of(effect("echo_scan"));
    }

    private static void reportPostEffectFailure(String operation, RuntimeException exception) {
        if (postEffectFailureLogged) {
            return;
        }
        postEffectFailureLogged = true;
        LOGGER.error(
                "Echo post-processing failed during {}. Falling back to geometric rendering.",
                operation,
                exception);
    }

    static boolean isPostEffectOperational() {
        Minecraft minecraft = Minecraft.getInstance();
        return postEffectOwned && isEchoPostEffect(minecraft.gameRenderer.currentPostEffect());
    }

    static @Nullable EchoSnapshot snapshot() {
        return snapshot;
    }

    static boolean isPreparationPending() {
        return pendingPreparation != null;
    }

    static List<GhostBlock> ghosts() {
        return ghosts;
    }

    static List<GhostModel> ghostModels() {
        return ghostModels;
    }

    static List<GhostModel> visibleGhostModels() {
        return visibleGhostModels;
    }

    static void setVisibleGhostModels(List<GhostModel> models) {
        visibleGhostModels = List.copyOf(models);
    }

    static List<GhostModel> presentGhostModels() {
        return presentGhostModels;
    }

    static List<GhostModel> visiblePresentGhostModels() {
        return visiblePresentGhostModels;
    }

    static void setVisiblePresentGhostModels(List<GhostModel> models) {
        visiblePresentGhostModels = List.copyOf(models);
    }

    /**
     * Softens ghosts whose cells the camera currently occupies so the player
     * reads the hollow shell instead of a fully transparent wash of back faces.
     */
    static void updateGhostOccupancy(
            Vec3 camera,
            List<GhostModel> remembered,
            List<GhostModel> present,
            long now) {
        float deltaSeconds = ghostOccupancyNanos == 0L
                ? 1.0F / 60.0F
                : (float) Math.min(
                        0.1,
                        (now - ghostOccupancyNanos) / 1_000_000_000.0);
        ghostOccupancyNanos = now;
        BlockPos cameraBlock = BlockPos.containing(camera);
        occupancyCameraBlock = cameraBlock;
        Set<Long> active = new HashSet<>(
                (remembered.size() + present.size()) * 2
                        + 27);
        approachOccupancy(remembered, cameraBlock, deltaSeconds, active);
        approachOccupancy(present, cameraBlock, deltaSeconds, active);
        // Track the whole Chebyshev neighbourhood even when a cell has no baked
        // ghost model yet: shared-face unlocks still need that visibility.
        int radius = EchoGhostOccupancy.NEIGHBORHOOD;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos cell = cameraBlock.offset(dx, dy, dz);
                    long key = cell.asLong();
                    active.add(key);
                    if (isOccupancyImmune(key)) {
                        ghostOccupancyVisibility.put(key, 1.0F);
                        continue;
                    }
                    float target = EchoGhostOccupancy.targetVisibility(
                            cell,
                            cameraBlock);
                    float current = ghostOccupancyVisibility.getOrDefault(key, 1.0F);
                    ghostOccupancyVisibility.put(
                            key,
                            EchoGhostOccupancy.approach(
                                    current,
                                    target,
                                    deltaSeconds));
                }
            }
        }
        ghostOccupancyVisibility.entrySet().removeIf(entry -> {
            if (active.contains(entry.getKey())) {
                return false;
            }
            float next = EchoGhostOccupancy.approach(
                    entry.getValue(),
                    1.0F,
                    deltaSeconds);
            if (next >= 0.999F) {
                return true;
            }
            entry.setValue(next);
            return false;
        });
    }

    static float ghostOccupancyVisibility(BlockPos position) {
        return ghostOccupancyVisibility(position.asLong(), position);
    }

    static float ghostOccupancyVisibility(long packedPosition) {
        return ghostOccupancyVisibility(packedPosition, null);
    }

    private static float ghostOccupancyVisibility(
            long packedPosition,
            @Nullable BlockPos knownPosition) {
        if (isOccupancyImmune(packedPosition)) {
            return 1.0F;
        }
        Float tracked = ghostOccupancyVisibility.get(packedPosition);
        if (tracked != null) {
            return tracked;
        }
        // Untracked cells still follow the live camera neighbourhood so a shell
        // face can unlock against a neighbour that never received a ghost model.
        return EchoGhostOccupancy.targetVisibility(
                knownPosition == null
                        ? BlockPos.of(packedPosition)
                        : knownPosition,
                occupancyCameraBlock);
    }

    private static boolean isOccupancyImmune(long packedPosition) {
        return ghostFadeImmunePositions.contains(packedPosition);
    }

    private static void approachOccupancy(
            List<GhostModel> models,
            BlockPos cameraBlock,
            float deltaSeconds,
            Set<Long> active) {
        for (GhostModel ghost : models) {
            long key = ghost.position().asLong();
            active.add(key);
            // MISSING shells dissolve near the camera; ADDED/REPLACED stay put.
            float target = ghost.change() == EchoBlockChange.Kind.MISSING
                    ? EchoGhostOccupancy.targetVisibility(
                            ghost.position(),
                            cameraBlock)
                    : 1.0F;
            float current = ghostOccupancyVisibility.getOrDefault(key, 1.0F);
            ghostOccupancyVisibility.put(
                    key,
                    EchoGhostOccupancy.approach(current, target, deltaSeconds));
        }
    }

    static List<Entity> ghostEntities() {
        return ghostEntities;
    }

    static List<Entity> ghostEntitiesForStone(
            boolean restoring) {
        if (restoring
                && pendingGhostEntities != null) {
            return pendingGhostEntities;
        }
        return ghostEntities;
    }

    public static boolean shouldHidePresentBlock(BlockPos position) {
        return shouldHidePresentBlock(
                position.asLong(),
                position);
    }

    /**
     * Allocation-free entry used by Sodium's multi-threaded chunk mesher. The
     * packed-position miss is overwhelmingly the common case; a BlockPos is
     * only materialized for one of the few blocks controlled by an Echo.
     */
    public static boolean shouldHidePresentBlock(
            int x,
            int y,
            int z) {
        return shouldHidePresentBlock(
                BlockPos.asLong(x, y, z),
                null);
    }

    private static boolean shouldHidePresentBlock(
            long packedPosition,
            @Nullable BlockPos knownPosition) {
        long now = System.nanoTime();
        RevisionVisuals revision =
                pendingRevisionVisuals;
        Set<Long> occluders =
                revision != null
                                && ClientPhilosophersStoneVision
                                        .controlsEchoCaches(now)
                        ? revision.presentOccluderPositions()
                        : presentOccluderPositions;
        if (!occluders.contains(packedPosition)) {
            return false;
        }
        BlockPos position = knownPosition == null
                ? BlockPos.of(packedPosition)
                : knownPosition;
        return !ClientPhilosophersStoneVision
                .usesNativePastState(
                        position,
                        now);
    }

    public static boolean shouldHidePresentBlockEntity(
            BlockPos position) {
        return shouldHidePresentBlock(position);
    }

    /**
     * Decorative entities are part of the reconstructed scene rather than the
     * live one. Once the returning front reaches them, the present renderer is
     * suppressed: captured entities are then represented by their frozen ghost,
     * while entities added after the capture simply disappear.
     */
    public static boolean shouldHidePresentEntity(Entity entity) {
        EchoSnapshot activeSnapshot = snapshot;
        if (activeSnapshot == null
                || pendingPreparation != null
                || fadeStartNanos != Long.MAX_VALUE
                || !isTemporalDecoration(entity)) {
            return false;
        }

        BlockPos position = entity.blockPosition();
        ScanBounds bounds = changeDetectionBounds(activeSnapshot);
        if (position.getX() < bounds.minX() - 1 || position.getX() > bounds.maxX() + 1
                || position.getY() < bounds.minY() - 1 || position.getY() > bounds.maxY() + 1
                || position.getZ() < bounds.minZ() - 1 || position.getZ() > bounds.maxZ() + 1) {
            return false;
        }

        double distance = travelDistanceTo(
                entity.blockPosition());
        double maximum = Math.max(1.0, scanRadius);
        if (!Double.isFinite(distance)
                || distance > maximum + 1.5) {
            return false;
        }
        return EchoVisualTiming.presentOccluderReveal(
                elapsedSeconds(System.nanoTime()),
                distance,
                pulseTiming) > 0.005F;
    }

    private static boolean isTemporalDecoration(Entity entity) {
        return entity instanceof ItemFrame
                || entity instanceof Painting
                || entity instanceof ArmorStand;
    }

    /**
     * Visibility of particles emitted by the present block at this position.
     * Sampling this probability when particles are created gives a short,
     * progressive fade without mutating unrelated world or entity particles.
     */
    public static float presentBlockParticleVisibility(BlockPos position) {
        if (snapshot == null
                || pendingPreparation != null
                || fadeStartNanos != Long.MAX_VALUE) {
            return 1.0F;
        }
        SurfaceTiming timing =
                activePresentOccluderTimings(
                                System.nanoTime())
                        .get(position.asLong());
        if (timing == null) {
            return 1.0F;
        }
        if (!Double.isFinite(timing.distance())) {
            return 1.0F;
        }
        float reveal = EchoVisualTiming.presentOccluderReveal(
                elapsedSeconds(System.nanoTime()),
                timing.distance(),
                pulseTiming);
        return Math.clamp(1.0F - reveal, 0.0F, 1.0F);
    }

    public static boolean tracksPresentBlockParticles(BlockPos position) {
        return snapshot != null
                && pendingPreparation == null
                && fadeStartNanos == Long.MAX_VALUE
                && activePresentOccluderTimings(
                                System.nanoTime())
                        .containsKey(position.asLong());
    }

    private static Map<Long, SurfaceTiming>
            activePresentOccluderTimings(long now) {
        RevisionVisuals revision =
                pendingRevisionVisuals;
        if (revision != null
                && ClientPhilosophersStoneVision
                        .controlsEchoCaches(now)) {
            return revision
                    .presentOccluderTimings();
        }
        return presentOccluderTimings;
    }

    public static int combinedBlockLight(BlockPos position, int presentBlockLight) {
        return combinedBlockLight(
                position.getX(),
                position.getY(),
                position.getZ(),
                presentBlockLight);
    }

    public static int combinedBlockLight(
            int x,
            int y,
            int z,
            int presentBlockLight) {
        if (snapshot == null || pendingPreparation != null) {
            return presentBlockLight;
        }
        int remembered = EchoPastLight.sample(sharedBlockLight, x, y, z);
        int combined = EchoPastLight.combinedBlockLight(
                presentBlockLight,
                remembered);
        // Hidden ADDED cells are visually air: never keep the live solid's
        // trapped interior darkness when the shared field already lit the cell.
        if (shouldHidePresentBlock(x, y, z) && remembered > presentBlockLight) {
            return remembered;
        }
        return combined;
    }

    public static int combinedSkyLight(BlockPos position, int presentSkyLight) {
        return combinedSkyLight(
                position.getX(),
                position.getY(),
                position.getZ(),
                presentSkyLight);
    }

    public static int combinedSkyLight(
            int x,
            int y,
            int z,
            int presentSkyLight) {
        if (snapshot == null || pendingPreparation != null) {
            return presentSkyLight;
        }
        int remembered = EchoPastLight.sample(sharedSkyLight, x, y, z);
        int combined = Math.max(presentSkyLight, remembered);
        if (shouldHidePresentBlock(x, y, z) && remembered > presentSkyLight) {
            return remembered;
        }
        return combined;
    }

    public static void onBlockChanged(BlockPos position) {
        EchoSnapshot activeSnapshot = snapshot;
        if (activeSnapshot == null || fadeStartNanos != Long.MAX_VALUE) {
            return;
        }
        ClientTemplateProjection projection = templateProjection;
        if (projection != null) {
            projection.invalidatePresentSection(position);
        }
        EchoWaveVolume localVolume = localWaveVolume;
        if (projection != null
                && (localVolume == null
                        || !localVolume.contains(position.getCenter()))) {
            return;
        }
        ScanBounds bounds = changeDetectionBounds(activeSnapshot);
        if (position.getX() >= bounds.minX() && position.getX() <= bounds.maxX()
                && position.getY() >= bounds.minY() && position.getY() <= bounds.maxY()
                && position.getZ() >= bounds.minZ() && position.getZ() <= bounds.maxZ()) {
            worldStateDirty = true;
        }
    }

    static void onStoneControlReleased() {
        if (snapshot != null
                && fadeStartNanos
                        == Long.MAX_VALUE) {
            stoneControlReleased = true;
            worldStateDirty = true;
        }
    }

    static List<ScanFace> presentFaces() {
        return presentFaces;
    }

    static List<ScanFace> returnCarrierFaces() {
        return returnCarrierFaces;
    }

    static List<ScanFace> memoryFaces() {
        return memoryFaces;
    }

    static List<ScanFace> memoryEchoFaces() {
        return memoryEchoFaces;
    }

    static Vec3 sonarOrigin() {
        return sonarOrigin;
    }

    static double scanRadius() {
        return scanRadius;
    }

    static double localWaveRadius() {
        EchoWaveVolume volume = localWaveVolume;
        return volume == null ? 1.0 : volume.radius();
    }

    static EchoPulseTiming pulseTiming() {
        return pulseTiming;
    }

    static ClientEchoArrivalField arrivalField() {
        return arrivalField;
    }

    static double travelDistanceTo(BlockPos position) {
        if (!arrivalField.isEmpty()) {
            float routed = arrivalField.arrivalAt(position);
            if (Float.isFinite(routed)) {
                return routed;
            }
        }
        return position.getCenter().distanceTo(sonarOrigin);
    }

    static double elapsedSeconds(long now) {
        return (now - activationNanos) / 1_000_000_000.0;
    }

    static float fadeAlpha(long now) {
        if (fadeStartNanos == Long.MAX_VALUE) {
            return 1.0F;
        }
        return (float) Math.clamp(1.0 - (double) (now - fadeStartNanos) / FADE_DURATION_NANOS, 0.0, 1.0);
    }

    static boolean isSurfaceWaveActive(long now) {
        if (snapshot == null || fadeAlpha(now) <= 0.005F) {
            return false;
        }
        if (pendingPreparation != null && presentFaces.isEmpty()) {
            return false;
        }
        double elapsed = elapsedSeconds(now);
        return pulseTiming.crestEnvelope(elapsed) > 0.001F;
    }

    static float itemAnimationFrame(net.minecraft.world.item.ItemStack stack) {
        EchoSnapshot activeSnapshot = snapshot;
        if (activeSnapshot == null || fadeStartNanos != Long.MAX_VALUE) {
            return 0.0F;
        }
        EchoSnapshot itemSnapshot = PastEchoMemory.getSnapshot(stack);
        if (itemSnapshot == null
                || !itemSnapshot.dimension().equals(activeSnapshot.dimension())
                || !itemSnapshot.origin().equals(activeSnapshot.origin())) {
            return 0.0F;
        }
        return EchoVisualTiming.itemAnimationFrame(
                elapsedSeconds(System.nanoTime()),
                pulseTiming);
    }

    private static float shadowStrength(long now) {
        if (snapshot == null) {
            return 0.0F;
        }

        double elapsed = elapsedSeconds(now);
        return EchoVisualTiming.configuredShadowStrength(
                elapsed,
                fadeAlpha(now),
                EchoesConfig.SCREEN_DARKENING.get().floatValue(),
                pulseTiming);
    }

    private static Identifier postEffectForStrength(float strength) {
        return POST_EFFECT_STAGES.getFirst();
    }

    private static void updatePresentOccluderFiltering(Minecraft minecraft, long now) {
        double elapsed = elapsedSeconds(now);
        ClientTemplateProjection projection = templateProjection;
        int templateRevision = projection == null
                ? -1
                : projection.presentOccluderRevision();
        if (presentOccluderFilteringSettled
                && templateRevision == appliedTemplatePresentRevision) {
            return;
        }
        Map<Long, Double> remoteDistances = projection == null
                ? Map.of()
                : projection.presentOccluderDistances();
        Map<Long, Double> localDistances = new HashMap<>(presentOccluderTimings.size() * 2);
        for (Map.Entry<Long, SurfaceTiming> entry : presentOccluderTimings.entrySet()) {
            double distance = entry.getValue().distance();
            if (Double.isFinite(distance)) {
                localDistances.put(entry.getKey(), distance);
            }
        }
        Map<Long, Double> bestDistances = EchoOccluderDistances.best(
                localDistances,
                remoteDistances);
        if (elapsed >= pulseTiming.effectEndSeconds()) {
            // Every finite hide distance yields for the rest of the pulse.
            replacePresentOccluderFiltering(
                    minecraft,
                    bestDistances.keySet());
            presentOccluderFilteringSettled = true;
            appliedTemplatePresentRevision = templateRevision;
            return;
        }
        Set<Long> desired = new HashSet<>();
        Set<Long> alreadyHidden = presentOccluderPositions;
        for (Map.Entry<Long, Double> entry : bestDistances.entrySet()) {
            float reveal = EchoVisualTiming.presentOccluderReveal(
                    elapsed,
                    entry.getValue(),
                    pulseTiming);
            // Hysteresis stops wave-front blocks from flickering in/out of the
            // hide set while reveal sits near the threshold.
            if (alreadyHidden.contains(entry.getKey())) {
                if (reveal > 0.001F) {
                    desired.add(entry.getKey());
                }
            } else if (reveal > 0.02F) {
                desired.add(entry.getKey());
            }
        }
        replacePresentOccluderFiltering(minecraft, desired);
        appliedTemplatePresentRevision = templateRevision;
    }

    private static void replacePresentOccluderFiltering(
            Minecraft minecraft,
            Set<Long> desired) {
        Set<Long> previous = presentOccluderPositions;
        if (previous.equals(desired)) {
            return;
        }
        Set<Long> changed = new HashSet<>(previous);
        changed.addAll(desired);
        Set<Long> unchanged = new HashSet<>(previous);
        unchanged.retainAll(desired);
        changed.removeAll(unchanged);
        presentOccluderPositions = Set.copyOf(desired);
        markPositionSectionsDirty(minecraft, changed);
    }

    private static void clearPresentOccluderFiltering(Minecraft minecraft) {
        Set<Long> previous = presentOccluderPositions;
        if (previous.isEmpty()) {
            return;
        }
        presentOccluderPositions = Set.of();
        markPositionSectionsDirty(minecraft, previous);
    }

    private static void markPositionSectionsDirty(
            Minecraft minecraft,
            Set<Long> positions) {
        if (positions.isEmpty() || minecraft.level == null) {
            return;
        }
        Set<Long> dirtySections = new HashSet<>();
        for (long packedPosition : positions) {
            BlockPos position = BlockPos.of(packedPosition);
            dirtySections.add(SectionPos.asLong(position));
            for (Direction direction : Direction.values()) {
                dirtySections.add(SectionPos.asLong(position.relative(direction)));
            }
        }
        markSectionsDirty(minecraft, dirtySections);
    }

    private static Set<Long> sectionsCovering(
            BlockPos minimum,
            BlockPos maximum) {
        int minimumSectionX = SectionPos.blockToSectionCoord(minimum.getX() - 1);
        int minimumSectionY = SectionPos.blockToSectionCoord(minimum.getY() - 1);
        int minimumSectionZ = SectionPos.blockToSectionCoord(minimum.getZ() - 1);
        int maximumSectionX = SectionPos.blockToSectionCoord(maximum.getX() + 1);
        int maximumSectionY = SectionPos.blockToSectionCoord(maximum.getY() + 1);
        int maximumSectionZ = SectionPos.blockToSectionCoord(maximum.getZ() + 1);
        Set<Long> sections = new HashSet<>();
        for (int sectionX = minimumSectionX; sectionX <= maximumSectionX; sectionX++) {
            for (int sectionY = minimumSectionY; sectionY <= maximumSectionY; sectionY++) {
                for (int sectionZ = minimumSectionZ; sectionZ <= maximumSectionZ; sectionZ++) {
                    sections.add(SectionPos.asLong(sectionX, sectionY, sectionZ));
                }
            }
        }
        return Set.copyOf(sections);
    }

    private static void markSectionsDirty(
            Minecraft minecraft,
            Set<Long> dirtySections) {
        if (dirtySections.isEmpty() || minecraft.level == null) {
            return;
        }
        for (long section : dirtySections) {
            minecraft.levelRenderer.setSectionDirty(
                    SectionPos.x(section),
                    SectionPos.y(section),
                    SectionPos.z(section));
        }
    }

    private enum PreparationPhase {
        TEMPLATE,
        OUTGOING_SURFACES,
        TEMPLATE_WAVE,
        REMEMBERED,
        ROUTE,
        BASELINE,
        LIGHT,
        REMEMBERED_MODELS,
        PRESENT_MODELS,
        MEMORY_SURFACES,
        ENTITIES,
        COMPLETE;

        private PreparationPhase next() {
            return switch (this) {
                case TEMPLATE -> OUTGOING_SURFACES;
                case OUTGOING_SURFACES -> TEMPLATE_WAVE;
                case TEMPLATE_WAVE -> REMEMBERED;
                case REMEMBERED -> ROUTE;
                case ROUTE -> BASELINE;
                case BASELINE -> LIGHT;
                case LIGHT -> REMEMBERED_MODELS;
                case REMEMBERED_MODELS -> PRESENT_MODELS;
                case PRESENT_MODELS -> MEMORY_SURFACES;
                case MEMORY_SURFACES -> ENTITIES;
                case ENTITIES, COMPLETE -> COMPLETE;
            };
        }
    }

    private static final class PendingPreparation {
        private final long generation;
        private final EchoSnapshot source;
        private final EchoWaveVolume waveVolume;
        private final long startedNanos;
        private final long[] phaseNanos =
                new long[PreparationPhase.values().length];
        private PreparationPhase phase = PreparationPhase.TEMPLATE;
        private PreparationPhase longestSlicePhase = PreparationPhase.TEMPLATE;
        private long longestSliceNanos;
        private int frames;

        private @Nullable ClientTemplateProjection stagingTemplate;
        private double stagingScanRadius = 1.0;
        private EchoPulseTiming stagingPulseTiming = EchoPulseTiming.forRadius(1.0);
        private Map<BlockPos, BlockState> stagingRememberedStates = Map.of();
        private Map<BlockPos, BlockState> stagingBaselineStates = Map.of();
        private @Nullable Map<BlockPos, BlockState> stagingBaselineBuilder;
        private ClientEchoArrivalField.Preparation arrivalPreparation;
        private @Nullable ClientEchoArrivalField stagingArrivalField;
        private @Nullable Map<BlockPos, Integer> blockSeeds;
        private @Nullable Map<BlockPos, Integer> skySeeds;
        private java.util.Iterator<Map.Entry<BlockPos, BlockState>> seedRememberedIterator;
        private boolean seedWorldScanStarted;
        private boolean lightSeedsReady;
        private boolean largeMemory;
        private EchoPastLight.IncrementalPropagation blockLightPropagation;
        private EchoPastLight.IncrementalPropagation skyLightPropagation;
        private @Nullable Map<Long, Integer> stagingBlockLight;
        private @Nullable Map<Long, Integer> stagingSkyLight;
        private @Nullable Set<Long> stagingLightSections;
        private @Nullable List<GhostBlock> stagingGhosts;
        private @Nullable List<GhostModel> stagingGhostModels;
        private @Nullable List<GhostBlock> stagingPresentOccluders;
        private @Nullable Map<Long, SurfaceTiming> stagingPresentOccluderTimings;
        private @Nullable List<GhostBlock> stagingAddedBlocks;
        private @Nullable List<GhostModel> stagingPresentModels;
        private @Nullable List<ScanFace> outgoingFaces;
        private @Nullable List<ScanFace> stagingPresentFaces;
        private @Nullable List<ScanFace> stagingReturnCarrierFaces;
        private @Nullable List<ScanFace> memoryFacesBuilder;
        private @Nullable List<ScanFace> stagingMemoryFaces;
        private @Nullable List<Entity> stagingGhostEntities;
        private @Nullable List<SnapshotEntity> entitySource;
        private final BlockPos.MutableBlockPos scanCursor = new BlockPos.MutableBlockPos();
        private int scanX;
        private int scanY;
        private int scanZ;
        private double scanMaximumDistanceSquared;
        private int modelCursor;
        private int memoryGhostCursor;
        private int entityCursor;

        private PendingPreparation(
                long generation,
                EchoSnapshot source,
                EchoWaveVolume waveVolume,
                long startedNanos) {
            this.generation = generation;
            this.source = source;
            this.waveVolume = waveVolume;
            this.startedNanos = startedNanos;
            this.stagingScanRadius = waveVolume.radius();
            this.stagingPulseTiming = EchoPulseTiming.forRadius(stagingScanRadius);
        }
    }

    /**
     * Look-at diagnostics for {@code /echoes debug fade}.
     */
    static FadeProbe probeFade(BlockPos position, long now) {
        EchoSnapshot active = snapshot;
        BlockState remembered = rememberedStates.get(position);
        ClientTemplateProjection projection = templateProjection;
        EchoSiteAdditions additions = null;
        BlockPos memoryCorner = BlockPos.ZERO;
        boolean authored = false;
        boolean inSeed = false;
        int seedSize = -1;
        String siteId = null;
        if (active != null) {
            additions = siteAdditions(Minecraft.getInstance(), active);
            memoryCorner = active.origin().offset(
                    active.boundsMin().orElse(BlockPos.ZERO));
            authored = authoredBySite(active, additions, position, memoryCorner);
            inSeed = additions != null && additions.contains(position, memoryCorner);
            seedSize = additions == null ? -1 : additions.size();
            siteId = active.site().map(Object::toString).orElse(null);
            if (siteId == null && active.template().isPresent()) {
                EchoSiteType site = null;
                for (EchoSiteType candidate : EchoSiteType.generatedSites()) {
                    if (candidate.intactTemplate().equals(active.template().orElse(null))) {
                        site = candidate;
                        break;
                    }
                }
                if (site != null) {
                    siteId = site.id().toString();
                }
            }
        }
        EchoBlockChange.Kind kind = active == null
                ? EchoBlockChange.Kind.UNCHANGED
                : EchoBlockChange.classify(
                        remembered,
                        Minecraft.getInstance().level != null
                                ? Minecraft.getInstance().level.getBlockState(position)
                                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        authored);
        SurfaceTiming localTiming = presentOccluderTimings.get(position.asLong());
        Double seeded = projection == null
                ? null
                : projection.presentOccluderDistances().get(position.asLong());
        double localDistance = localTiming == null
                ? Double.NaN
                : localTiming.distance();
        double seededDistance = seeded == null ? Double.NaN : seeded;
        double best = Double.NaN;
        if (Double.isFinite(localDistance)) {
            best = localDistance;
        }
        if (Double.isFinite(seededDistance)) {
            best = Double.isFinite(best) ? Math.min(best, seededDistance) : seededDistance;
        }
        double elapsed = active == null ? 0.0 : elapsedSeconds(now);
        float reveal = !Double.isFinite(best) || active == null
                ? 0.0F
                : EchoVisualTiming.presentOccluderReveal(elapsed, best, pulseTiming);
        boolean presentGhost = false;
        boolean rememberedGhost = false;
        for (GhostModel model : presentGhostModels) {
            if (model.position().equals(position)) {
                presentGhost = true;
                break;
            }
        }
        if (!presentGhost && projection != null) {
            for (GhostModel model : projection.debugPresentModels()) {
                if (model.position().equals(position)) {
                    presentGhost = true;
                    break;
                }
            }
        }
        for (GhostModel model : ghostModels) {
            if (model.position().equals(position)) {
                rememberedGhost = true;
                break;
            }
        }
        if (!rememberedGhost && projection != null) {
            for (GhostModel model : projection.debugRememberedModels()) {
                if (model.position().equals(position)) {
                    rememberedGhost = true;
                    break;
                }
            }
        }
        return new FadeProbe(
                active != null,
                elapsed,
                scanRadius,
                pulseTiming.effectEndSeconds(),
                remembered,
                kind,
                authored,
                inSeed,
                shouldHidePresentBlock(position),
                localDistance,
                seededDistance,
                best,
                reveal,
                presentGhost,
                rememberedGhost,
                projection != null,
                seedSize,
                projection == null ? -1 : projection.presentOccluderRevision(),
                siteId);
    }

    record FadeProbe(
            boolean snapshotActive,
            double elapsedSeconds,
            double scanRadius,
            double effectEndSeconds,
            @Nullable BlockState remembered,
            EchoBlockChange.Kind kind,
            boolean authoredBySite,
            boolean inFadeSeed,
            boolean shouldHidePresentBlock,
            double localTimingDistance,
            double seededDistance,
            double bestDistance,
            float reveal,
            boolean presentGhostBaked,
            boolean rememberedGhostBaked,
            boolean templateProjectionLoaded,
            int fadeSeedSize,
            int occluderRevision,
            @Nullable String siteId) {
    }

    record GhostBlock(BlockPos position, BlockState state, EchoBlockChange.Kind change) {
    }

    private record RevisionVisuals(
            List<GhostModel> rememberedModels,
            List<GhostModel> presentModels,
            Set<Long> presentOccluderPositions,
            Map<Long, SurfaceTiming> presentOccluderTimings) {
    }

    record GhostModel(
            BlockPos position,
            BlockState state,
            List<PreparedGhostQuad> preparedQuads,
            List<AABB> fallbackBoxes,
            @Nullable TextureAtlasSprite fallbackSprite,
            EchoBlockChange.Kind change,
            Vec3 modelOffset,
            boolean twoSided,
            boolean texturedPlane,
            double travelDistance,
            DirectionalLight lightCoords,
            AABB worldBounds) {
    }

    record PreparedGhostQuad(
            BakedQuad quad,
            int tint,
            int lightCoords,
            double centerX,
            double centerY,
            double centerZ,
            double normalX,
            double normalY,
            double normalZ,
            @Nullable Direction occupancyNeighborFace) {
    }

    record DirectionalLight(
            int down,
            int up,
            int north,
            int south,
            int west,
            int east) {
        static final DirectionalLight DARK = new DirectionalLight(0, 0, 0, 0, 0, 0);

        int at(Direction direction) {
            return switch (direction) {
                case DOWN -> down;
                case UP -> up;
                case NORTH -> north;
                case SOUTH -> south;
                case WEST -> west;
                case EAST -> east;
            };
        }

        DirectionalLight map(java.util.function.IntUnaryOperator operation) {
            return new DirectionalLight(
                    operation.applyAsInt(down),
                    operation.applyAsInt(up),
                    operation.applyAsInt(north),
                    operation.applyAsInt(south),
                    operation.applyAsInt(west),
                    operation.applyAsInt(east));
        }
    }

    record ScanFace(
            BlockPos position,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            Vec3 center,
            Vec3 normal,
            boolean twoSided,
            EchoMaterialResponse.Profile response,
            WaveUv waveUv,
            double distance,
            Vec3 travelGradient,
            EchoSurfaceCrestPath.FaceDistances waveDistances) {
    }

    private record SurfaceTiming(
            double distance,
            EchoMaterialResponse.Profile response) {
    }

    record WaveUv(long a, long b, long c, long d) {
        static WaveUv fullSprite(TextureAtlasSprite sprite) {
            return new WaveUv(
                    UVPair.pack(sprite.getU0(), sprite.getV1()),
                    UVPair.pack(sprite.getU1(), sprite.getV1()),
                    UVPair.pack(sprite.getU1(), sprite.getV0()),
                    UVPair.pack(sprite.getU0(), sprite.getV0()));
        }
    }

    private record ScanBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static ScanBounds at(BlockPos position) {
            return new ScanBounds(
                    position.getX(),
                    position.getY(),
                    position.getZ(),
                    position.getX(),
                    position.getY(),
                    position.getZ());
        }

        ScanBounds include(BlockPos position) {
            return new ScanBounds(
                    Math.min(minX, position.getX()),
                    Math.min(minY, position.getY()),
                    Math.min(minZ, position.getZ()),
                    Math.max(maxX, position.getX()),
                    Math.max(maxY, position.getY()),
                    Math.max(maxZ, position.getZ()));
        }

        ScanBounds inflate(int amount) {
            return new ScanBounds(
                    minX - amount,
                    minY - amount,
                    minZ - amount,
                    maxX + amount,
                    maxY + amount,
                    maxZ + amount);
        }
    }

    private ClientEchoState() {
    }
}
