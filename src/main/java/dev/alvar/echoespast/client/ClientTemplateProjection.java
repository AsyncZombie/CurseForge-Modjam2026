package dev.alvar.echoespast.client;

import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.mixin.server.StructureTemplateAccessor;
import dev.alvar.echoespast.resonance.EchoSiteType;
import dev.alvar.echoespast.snapshot.EchoMemoryRevision;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import dev.alvar.echoespast.snapshot.EchoProjectionBudget;
import dev.alvar.echoespast.snapshot.EchoSiteAdditions;
import dev.alvar.echoespast.snapshot.EchoTemplateProjectionIndex;
import dev.alvar.echoespast.snapshot.EchoTemplateResourceLoader;
import dev.alvar.echoespast.snapshot.EchoTemplateResolver;
import dev.alvar.echoespast.snapshot.SnapshotEntity;
import dev.alvar.echoespast.visual.EchoBlockChange;
import dev.alvar.echoespast.visual.EchoOccluderDistances;
import dev.alvar.echoespast.visual.EchoTemplateWaveMesher;
import dev.alvar.echoespast.visual.EchoWaveVolume;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Client-only projection of an authored template that is too large for the
 * compact snapshot packet. Blocks remain indexed by 16-cube sections; only
 * frustum-visible sections receive baked ghost models and each frame spends a
 * fixed amount of work advancing one section build.
 */
final class ClientTemplateProjection {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BLOCKS_PER_RENDER_FRAME = 256;
    private static final int PRESENT_CELLS_PER_RENDER_FRAME = 2_048;
    private static final int PRESENT_MODELS_PER_RENDER_FRAME = 96;
    private static final int WAVE_TILE_EDGE_BLOCKS = 4;
    private static final Map<net.minecraft.resources.Identifier, CachedTemplate>
            CACHE = new HashMap<>();
    private static boolean staticTemplatesPreloaded;

    private final EchoSnapshot source;
    private final EchoTemplateProjectionIndex index;
    /** Null when this site ships no set, which keeps the coarser reading. */
    private final @Nullable EchoSiteAdditions additions;
    private final BlockPos authoredMinimum;
    private final BlockPos authoredWorldMinimum;
    private final BlockPos authoredWorldMaximum;
    private final List<Long> authoredWorldSections;
    private final Vec3 waveOrigin;
    private List<SnapshotEntity> entities = List.of();
    private boolean entitiesCaptured;
    private EntityCaptureBuild entityCaptureBuild;
    private final Map<Long, SectionBuild> building = new HashMap<>();
    private final LinkedHashMap<Long, List<ClientEchoState.GhostModel>>
            models = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<Long, PresentSectionBuild> presentBuilding = new HashMap<>();
    private final LinkedHashMap<Long, PresentSection> presentSections =
            new LinkedHashMap<>(16, 0.75F, true);
    /**
     * Pulse-lifetime hide distances. Packaged fade-seed cells are recorded in
     * {@link #fadeSeedCells} and never removed; section scans may only refine
     * with a nearer value via {@link EchoOccluderDistances#mergeMin}.
     */
    private final Map<Long, Double> presentOccluderDistances = new HashMap<>();
    private final Set<Long> fadeSeedCells = new HashSet<>();
    /**
     * The pulse has its own small, immutable surface representation. It must
     * not share the detailed-model cache: a crest crosses the entire authored
     * site in under two seconds, while detailed models deliberately stream in
     * at a much gentler per-frame rate.
     */
    private final Map<Long, List<EchoTemplateWaveMesher.Patch>> waveSurfaces;
    private Map<Long, List<TemplateWaveTile>> templateWaveTiles = Map.of();
    private WaveTileBuild waveTileBuild;
    private int cachedModelCount;
    private int cachedPresentModelCount;
    private int presentOccluderRevision;

    static Optional<ClientTemplateProjection> load(
            Minecraft minecraft,
            EchoSnapshot source,
            Vec3 waveOrigin) {
        if (!source.isTemplateReference() || minecraft.level == null) {
            return Optional.empty();
        }
        net.minecraft.resources.Identifier templateId =
                source.template().orElseThrow();
        CachedTemplate cached = CACHE.get(templateId);
        if (cached == null) {
            return Optional.empty();
        }
        return Optional.of(new ClientTemplateProjection(
                source,
                cached.index(),
                cached.waveSurfaces(),
                cached.additions(),
                waveOrigin));
    }

    /**
     * Decodes and indexes authored memories while the world is already behind
     * a loading screen. The first use then only binds immutable cached data to
     * the activation origin instead of inflating a giant NBT on the render
     * frame that received the click.
     */
    static void preloadStaticTemplates(Minecraft minecraft) {
        if (staticTemplatesPreloaded || minecraft.level == null) {
            return;
        }
        long started = System.nanoTime();
        int loaded = 0;
        for (EchoSiteType site : EchoSiteType.generatedSites()) {
            if (cachedTemplate(minecraft, site.intactTemplate()).isPresent()) {
                loaded++;
            }
        }
        staticTemplatesPreloaded = true;
        if (loaded > 0) {
            LOGGER.info(
                    "Preloaded {} authored Past Echo templates in {} ms",
                    loaded,
                    (System.nanoTime() - started) / 1_000_000.0);
        }
    }

    static void clearCache() {
        CACHE.clear();
        staticTemplatesPreloaded = false;
    }

    /**
     * The cells this memory's site adds on top of it, or null when the site
     * ships no such set and callers must keep their coarser reading.
     */
    static @Nullable EchoSiteAdditions additionsFor(
            Minecraft minecraft,
            net.minecraft.resources.Identifier templateId) {
        return cachedTemplate(minecraft, templateId)
                .map(CachedTemplate::additions)
                .orElse(null);
    }

    private static Optional<CachedTemplate> cachedTemplate(
            Minecraft minecraft,
            net.minecraft.resources.Identifier templateId) {
        CachedTemplate cached = CACHE.get(templateId);
        if (cached != null) {
            return Optional.of(cached);
        }
        if (minecraft.level == null) {
            return Optional.empty();
        }
        Optional<StructureTemplate> decoded = EchoTemplateResourceLoader.load(
                minecraft.getResourceManager(),
                minecraft.level.registryAccess().lookupOrThrow(Registries.BLOCK),
                templateId);
        if (decoded.isEmpty()) {
            return Optional.empty();
        }
        StructureTemplate template = decoded.orElseThrow();
        List<StructureTemplate.Palette> palettes =
                ((StructureTemplateAccessor) (Object) template).echoes$getPalettes();
        List<StructureTemplate.StructureBlockInfo> blocks = palettes.isEmpty()
                ? List.of()
                : palettes.getFirst().blocks();
        EchoTemplateProjectionIndex index = EchoTemplateProjectionIndex.build(
                blocks,
                ((StructureTemplateAccessor) (Object) template)
                        .echoes$getEntityInfoList());
        cached = new CachedTemplate(
                index,
                buildWaveSurfaces(index),
                EchoSiteAdditions.load(
                                minecraft.getResourceManager(),
                                EchoSiteAdditions.resourceFor(templateId))
                        .orElse(null));
        CACHE.put(templateId, cached);
        return Optional.of(cached);
    }

    private ClientTemplateProjection(
            EchoSnapshot source,
            EchoTemplateProjectionIndex index,
            Map<Long, List<EchoTemplateWaveMesher.Patch>> waveSurfaces,
            @Nullable EchoSiteAdditions additions,
            Vec3 waveOrigin) {
        this.source = source;
        this.index = index;
        this.additions = additions;
        this.authoredMinimum = source.boundsMin().orElseThrow();
        this.authoredWorldMinimum = source.origin().offset(authoredMinimum);
        this.authoredWorldMaximum = source.origin().offset(
                source.boundsMax().orElseThrow());
        this.authoredWorldSections = authoredWorldSections(
                authoredWorldMinimum,
                authoredWorldMaximum);
        this.waveOrigin = waveOrigin;
        this.waveSurfaces = waveSurfaces;
        this.waveTileBuild = new WaveTileBuild();
        if (EchoMemoryRevision.hasEntityOverlay(source)) {
            this.entities = source.entities();
            this.entitiesCaptured = true;
            this.entityCaptureBuild = null;
        } else {
            this.entityCaptureBuild = new EntityCaptureBuild();
        }
        seedFadeOccluders();
    }

    /**
     * Loads the packaged fade seed into the pulse-lifetime occluder map once.
     * Section scans may refine distances later but cannot drop these cells.
     */
    private void seedFadeOccluders() {
        if (additions == null) {
            return;
        }
        additions.forEachCell((x, y, z) -> {
            BlockPos world = authoredWorldMinimum.offset(x, y, z);
            long key = world.asLong();
            fadeSeedCells.add(key);
            presentOccluderDistances.put(
                    key,
                    world.getCenter().distanceTo(waveOrigin));
        });
        if (!fadeSeedCells.isEmpty()) {
            presentOccluderRevision++;
        }
    }

    List<SnapshotEntity> entities() {
        return entities;
    }

    /**
     * Advances authored-entity decoding until the absolute nanosecond
     * deadline. Each loop iteration owns exactly one root entity, so a caller
     * can spread a large statue set over rendered frames without ever
     * publishing a partial list.
     *
     * @return {@code true} once the complete immutable list is available
     */
    boolean advanceEntityCapture(
            Minecraft minecraft,
            long deadlineNanos) {
        if (entitiesCaptured) {
            return true;
        }
        EntityCaptureBuild build = entityCaptureBuild;
        if (build == null || minecraft.level == null) {
            return false;
        }
        if (!build.advance(minecraft, deadlineNanos)) {
            return false;
        }
        entities = List.copyOf(build.captured);
        entitiesCaptured = true;
        entityCaptureBuild = null;
        return true;
    }

    /**
     * Compatibility bridge for callers that have not yet adopted the phased
     * API. New activation code should use {@link #advanceEntityCapture} with a
     * real frame deadline.
     */
    void captureEntities(Minecraft minecraft) {
        advanceEntityCapture(minecraft, Long.MAX_VALUE);
    }

    /** Advances world-space wave-face creation under a strict render budget. */
    boolean advanceWaveTilePreparation(
            Minecraft minecraft,
            long budgetNanos) {
        WaveTileBuild build = waveTileBuild;
        if (build == null) {
            return true;
        }
        if (minecraft.level == null) {
            return false;
        }
        if (!build.advance(minecraft, Math.max(1L, budgetNanos))) {
            return false;
        }
        templateWaveTiles = build.freeze();
        waveTileBuild = null;
        return true;
    }

    int authoredBlockCount() {
        return index.indexedBlockCount();
    }

    int waveFaceCount() {
        return templateWaveTiles.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    double outerRadius(Vec3 center) {
        BlockPos maximum = source.boundsMax().orElseThrow();
        double farthest = 1.0;
        for (int x : new int[] {authoredMinimum.getX(), maximum.getX()}) {
            for (int y : new int[] {authoredMinimum.getY(), maximum.getY()}) {
                for (int z : new int[] {authoredMinimum.getZ(), maximum.getZ()}) {
                    farthest = Math.max(
                            farthest,
                            source.origin().offset(x, y, z)
                                    .getCenter()
                                    .distanceTo(center));
                }
            }
        }
        return farthest;
    }

    Map<BlockPos, BlockState> localStates(EchoWaveVolume volume) {
        BlockPos rawMinimum = volume.minBlock()
                .subtract(source.origin())
                .subtract(authoredMinimum);
        BlockPos rawMaximum = volume.maxBlock()
                .subtract(source.origin())
                .subtract(authoredMinimum);
        EchoTemplateProjectionIndex.Query query = index.query(
                rawMinimum,
                rawMaximum);
        Map<BlockPos, BlockState> states = new HashMap<>(query.blocks().size());
        for (StructureTemplate.StructureBlockInfo block : query.blocks()) {
            BlockPos world = worldPosition(block.pos());
            if (volume.contains(world.getCenter())) {
                if (EchoMemoryRevision.isOverlayAir(source, world)) {
                    continue;
                }
                states.put(
                        world,
                        EchoMemoryRevision.overlayStateOr(
                                source,
                                world,
                                block.state()));
            }
        }
        // Overlay can also add solids that the template never stored.
        for (Map.Entry<Long, EchoMemoryRevision.OverlayCell> entry :
                EchoMemoryRevision.blockOverlay(source).entrySet()) {
            if (entry.getValue().state().isAir()) {
                continue;
            }
            BlockPos world = BlockPos.of(entry.getKey());
            if (volume.contains(world.getCenter())) {
                states.put(world.immutable(), entry.getValue().state());
            }
        }
        return Map.copyOf(states);
    }

    LocalStateBuild beginLocalStates(EchoWaveVolume volume) {
        return new LocalStateBuild(volume);
    }

    List<ClientEchoState.GhostModel> visibleModels(
            Minecraft minecraft,
            Frustum frustum,
            Vec3 camera,
            int limit,
            Set<BlockPos> locallyRenderedPositions) {
        if (minecraft.level == null || limit <= 0) {
            return List.of();
        }
        List<Long> visibleSections = visibleSections(frustum, camera);
        advanceBuild(minecraft, visibleSections);
        if (visibleSections.isEmpty()) {
            return List.of();
        }
        List<ClientEchoState.GhostModel> visible = new ArrayList<>(
                Math.min(512, limit));
        for (long section : visibleSections) {
            List<ClientEchoState.GhostModel> sectionModels = models.get(section);
            if (sectionModels == null) {
                continue;
            }
            for (ClientEchoState.GhostModel model : sectionModels) {
                if (locallyRenderedPositions.contains(model.position())) {
                    continue;
                }
                if (!frustum.isVisible(model.worldBounds())) {
                    continue;
                }
                visible.add(model);
                if (visible.size() >= limit) {
                    return List.copyOf(visible);
                }
            }
        }
        return List.copyOf(visible);
    }

    /**
     * Historical air is implicit in a structure template, so it cannot be
     * streamed from the remembered block index. Instead, visible loaded world
     * sections are compared lazily against that index. This keeps a giant site
     * complete without turning activation into a synchronous whole-island
     * scan.
     */
    List<ClientEchoState.GhostModel> visiblePresentModels(
            Minecraft minecraft,
            Frustum frustum,
            Vec3 camera,
            int limit,
            Set<BlockPos> locallyRenderedPositions) {
        if (minecraft.level == null || limit <= 0) {
            return List.of();
        }
        List<Long> visibleSections = visiblePresentSections(frustum, camera);
        advancePresentBuild(minecraft, visibleSections);
        if (visibleSections.isEmpty()) {
            return List.of();
        }
        List<ClientEchoState.GhostModel> visible = new ArrayList<>(
                Math.min(512, limit));
        for (long section : visibleSections) {
            PresentSection cached = presentSections.get(section);
            if (cached == null) {
                continue;
            }
            for (ClientEchoState.GhostModel model : cached.models()) {
                if (locallyRenderedPositions.contains(model.position())
                        || !frustum.isVisible(model.worldBounds())) {
                    continue;
                }
                visible.add(model);
                if (visible.size() >= limit) {
                    return List.copyOf(visible);
                }
            }
        }
        return List.copyOf(visible);
    }

    List<ClientEchoState.GhostModel> debugPresentModels() {
        List<ClientEchoState.GhostModel> models = new ArrayList<>();
        for (PresentSection section : presentSections.values()) {
            models.addAll(section.models());
        }
        return List.copyOf(models);
    }

    List<ClientEchoState.GhostModel> debugRememberedModels() {
        List<ClientEchoState.GhostModel> out = new ArrayList<>();
        for (List<ClientEchoState.GhostModel> sectionModels : models.values()) {
            out.addAll(sectionModels);
        }
        return List.copyOf(out);
    }

    Map<Long, Double> presentOccluderDistances() {
        return Collections.unmodifiableMap(presentOccluderDistances);
    }

    int presentOccluderRevision() {
        return presentOccluderRevision;
    }

    void invalidatePresentSection(BlockPos position) {
        if (!source.containsWorldPosition(position)) {
            return;
        }
        long section = SectionPos.asLong(position);
        // World edits may change scan-discovered fades. Seed coverage stays;
        // the rebuild will re-register any non-seed cells that still qualify.
        clearDiscoveredOccludersInSection(section);
        presentBuilding.remove(section);
        removePresentSection(section);
        building.remove(section);
        removeRememberedSection(section);
        presentOccluderRevision++;
    }

    List<ClientEchoState.ScanFace> visibleWaveFaces(
            Frustum frustum,
            Vec3 camera,
            Set<BlockPos> locallyRenderedPositions,
            Vec3 waveOrigin,
            double front,
            boolean returning) {
        List<ClientEchoState.ScanFace> visible = new ArrayList<>();
        for (long section : visibleSections(frustum, camera)) {
            List<TemplateWaveTile> sectionTiles =
                    templateWaveTiles.get(section);
            if (sectionTiles == null) {
                continue;
            }
            for (TemplateWaveTile tile : sectionTiles) {
                ClientEchoState.ScanFace face = tile.face();
                if (tile.fullyCoveredBy(locallyRenderedPositions)
                        || !EchoSurfaceWaveRenderer.pulseIntersectsFace(
                                face,
                                waveOrigin,
                                front,
                                returning)
                        || !frustum.isVisible(
                                EchoSurfaceWaveRenderer.faceBounds(face))) {
                    continue;
                }
                visible.add(face);
            }
        }
        return List.copyOf(visible);
    }

    private List<Long> visibleSections(
            Frustum frustum,
            Vec3 camera) {
        return index.sectionKeys().stream()
                .filter(section -> frustum.isVisible(sectionBounds(section)))
                .sorted(Comparator.comparingDouble(section ->
                        sectionBounds(section).getCenter().distanceToSqr(camera)))
                .toList();
    }

    private List<Long> visiblePresentSections(
            Frustum frustum,
            Vec3 camera) {
        return authoredWorldSections.stream()
                .filter(section -> frustum.isVisible(worldSectionBounds(section)))
                .sorted(Comparator.comparingDouble(section ->
                        worldSectionBounds(section).getCenter().distanceToSqr(camera)))
                .toList();
    }

    private void advanceBuild(
            Minecraft minecraft,
            List<Long> visibleSections) {
        int budget = BLOCKS_PER_RENDER_FRAME;
        Set<Long> protectedSections = new HashSet<>(visibleSections);
        for (long section : visibleSections) {
            if (models.containsKey(section)) {
                continue;
            }
            SectionBuild task = building.computeIfAbsent(
                    section,
                    ignored -> new SectionBuild(
                            index.sectionBlocks(section),
                            environmentFor(section)));
            budget = task.advance(minecraft, budget);
            // Publish REPLACED occluders as soon as cells are scanned, even if
            // the section model bake is still incomplete.
            if (!task.replacedOccluders().isEmpty()) {
                registerReplacedOccluders(
                        task.models(),
                        task.replacedOccluders());
            }
            if (!task.finished()) {
                break;
            }
            List<ClientEchoState.GhostModel> sectionModels = task.models();
            if (!reserveModelCapacity(
                    sectionModels.size(),
                    protectedSections)) {
                // Keeping already-visible sections stable is more important
                // than repeatedly rebuilding them. This finished task waits
                // until the camera exposes an unpinned cache entry.
                break;
            }
            building.remove(section);
            models.put(section, sectionModels);
            cachedModelCount += sectionModels.size();
            registerReplacedOccluders(
                    sectionModels,
                    task.replacedOccluders());
            if (budget <= 0) {
                break;
            }
        }
    }

    private void advancePresentBuild(
            Minecraft minecraft,
            List<Long> visibleSections) {
        int cellBudget = PRESENT_CELLS_PER_RENDER_FRAME;
        int modelBudget = PRESENT_MODELS_PER_RENDER_FRAME;
        Set<Long> protectedSections = new HashSet<>(visibleSections);
        for (long section : visibleSections) {
            if (presentSections.containsKey(section)) {
                continue;
            }
            if (!sectionLoaded(minecraft, section)) {
                presentBuilding.remove(section);
                continue;
            }
            PresentSectionBuild task = presentBuilding.computeIfAbsent(
                    section,
                    this::createPresentSectionBuild);
            PresentBuildBudget remaining = task.advance(
                    minecraft,
                    cellBudget,
                    modelBudget);
            cellBudget = remaining.cells();
            modelBudget = remaining.models();
            // Eager publish: fallen rubble must hide as soon as ADDED/REPLACED
            // cells are classified, not only after the whole 16³ section scan.
            if (publishPresentOccluders(task.occluderDistances())) {
                presentOccluderRevision++;
            }
            if (!task.finished()) {
                break;
            }
            PresentSection completed = task.result();
            if (!reservePresentModelCapacity(
                    completed.models().size(),
                    protectedSections)) {
                break;
            }
            presentBuilding.remove(section);
            presentSections.put(section, completed);
            cachedPresentModelCount += completed.models().size();
            if (publishPresentOccluders(completed.occluderDistances())) {
                presentOccluderRevision++;
            }
            // Keep scanning further sections while cells remain; a spent model
            // budget must not freeze ADDED occluder discovery for the rest of
            // the authored footprint this frame.
            if (cellBudget <= 0) {
                break;
            }
        }
    }

    private boolean reserveModelCapacity(
            int incomingModels,
            Set<Long> protectedSections) {
        while (cachedModelCount + incomingModels
                > EchoProjectionBudget.MAX_CACHED_TEMPLATE_MODELS) {
            Long removable = null;
            for (long section : models.keySet()) {
                if (!protectedSections.contains(section)) {
                    removable = section;
                    break;
                }
            }
            if (removable == null) {
                return false;
            }
            removeRememberedSection(removable);
        }
        return true;
    }

    /**
     * Registers fadeable present cells discovered while walking the intact
     * section. Buried remembered solids never bake a ghost (no historical
     * exterior face), so scanned distances still publish those occluders.
     * Non-solid remembered decoration classifies as ADDED and uses the same
     * hide path. Distances merge into the pulse-lifetime map with nearer-wins.
     */
    private void registerReplacedOccluders(
            List<ClientEchoState.GhostModel> sectionModels,
            Map<Long, Double> scannedReplaced) {
        Map<Long, Double> distances = new HashMap<>(scannedReplaced);
        for (ClientEchoState.GhostModel model : sectionModels) {
            if (model.change() != EchoBlockChange.Kind.REPLACED) {
                continue;
            }
            distances.put(
                    model.position().asLong(),
                    model.position()
                            .getCenter()
                            .distanceTo(waveOrigin));
        }
        if (publishPresentOccluders(distances)) {
            presentOccluderRevision++;
        }
    }

    private boolean publishPresentOccluders(Map<Long, Double> distances) {
        return EchoOccluderDistances.mergeMin(presentOccluderDistances, distances);
    }

    private void removeRememberedSection(long section) {
        List<ClientEchoState.GhostModel> removed = models.remove(section);
        if (removed != null) {
            cachedModelCount -= removed.size();
        }
        // Model cache only — pulse-lifetime occluder distances stay.
    }

    private boolean reservePresentModelCapacity(
            int incomingModels,
            Set<Long> protectedSections) {
        while (cachedPresentModelCount + incomingModels
                > EchoProjectionBudget.MAX_CACHED_TEMPLATE_MODELS) {
            Long removable = null;
            for (long section : presentSections.keySet()) {
                if (!protectedSections.contains(section)) {
                    removable = section;
                    break;
                }
            }
            if (removable == null) {
                return false;
            }
            removePresentSection(removable);
        }
        return true;
    }

    private void removePresentSection(long section) {
        PresentSection removed = presentSections.remove(section);
        if (removed == null) {
            return;
        }
        cachedPresentModelCount -= removed.models().size();
        // Model cache only — pulse-lifetime occluder distances stay.
    }

    /**
     * Drops scan-discovered distances in a section while keeping packaged
     * fade-seed coverage for the rest of the pulse.
     */
    private void clearDiscoveredOccludersInSection(long section) {
        int minX = SectionPos.sectionToBlockCoord(SectionPos.x(section));
        int minY = SectionPos.sectionToBlockCoord(SectionPos.y(section));
        int minZ = SectionPos.sectionToBlockCoord(SectionPos.z(section));
        int maxX = minX + 15;
        int maxY = minY + 15;
        int maxZ = minZ + 15;
        presentOccluderDistances.keySet().removeIf(packed -> {
            if (fadeSeedCells.contains(packed)) {
                return false;
            }
            BlockPos world = BlockPos.of(packed);
            return world.getX() >= minX && world.getX() <= maxX
                    && world.getY() >= minY && world.getY() <= maxY
                    && world.getZ() >= minZ && world.getZ() <= maxZ;
        });
    }

    private PresentSectionBuild createPresentSectionBuild(long section) {
        AABB sectionBounds = worldSectionBounds(section);
        BlockPos minimum = new BlockPos(
                Math.max(authoredWorldMinimum.getX(), (int) sectionBounds.minX),
                Math.max(authoredWorldMinimum.getY(), (int) sectionBounds.minY),
                Math.max(authoredWorldMinimum.getZ(), (int) sectionBounds.minZ));
        BlockPos maximum = new BlockPos(
                Math.min(authoredWorldMaximum.getX(), (int) sectionBounds.maxX - 1),
                Math.min(authoredWorldMaximum.getY(), (int) sectionBounds.maxY - 1),
                Math.min(authoredWorldMaximum.getZ(), (int) sectionBounds.maxZ - 1));
        BlockPos templateMinimum = minimum
                .subtract(source.origin())
                .subtract(authoredMinimum);
        BlockPos templateMaximum = maximum
                .subtract(source.origin())
                .subtract(authoredMinimum);
        EchoTemplateProjectionIndex.Query remembered = index.query(
                templateMinimum,
                templateMaximum);
        Map<Long, BlockState> rememberedStates = new HashMap<>(
                remembered.blocks().size() * 2);
        for (StructureTemplate.StructureBlockInfo block : remembered.blocks()) {
            BlockPos world = worldPosition(block.pos());
            if (EchoMemoryRevision.isOverlayAir(source, world)) {
                continue;
            }
            rememberedStates.put(
                    world.asLong(),
                    EchoMemoryRevision.overlayStateOr(
                            source,
                            world,
                            block.state()));
        }
        for (Map.Entry<Long, EchoMemoryRevision.OverlayCell> entry :
                EchoMemoryRevision.blockOverlay(source).entrySet()) {
            if (entry.getValue().state().isAir()) {
                continue;
            }
            BlockPos world = BlockPos.of(entry.getKey());
            if (world.getX() < minimum.getX()
                    || world.getY() < minimum.getY()
                    || world.getZ() < minimum.getZ()
                    || world.getX() > maximum.getX()
                    || world.getY() > maximum.getY()
                    || world.getZ() > maximum.getZ()) {
                continue;
            }
            rememberedStates.put(entry.getKey(), entry.getValue().state());
        }
        return new PresentSectionBuild(
                minimum,
                maximum,
                Map.copyOf(rememberedStates));
    }

    private static boolean sectionLoaded(Minecraft minecraft, long section) {
        return minecraft.level != null
                && minecraft.level.hasChunkAt(new BlockPos(
                        SectionPos.x(section) << 4,
                        SectionPos.y(section) << 4,
                        SectionPos.z(section) << 4));
    }

    private Map<BlockPos, BlockState> environmentFor(long section) {
        int baseX = SectionPos.x(section) << 4;
        int baseY = SectionPos.y(section) << 4;
        int baseZ = SectionPos.z(section) << 4;
        EchoTemplateProjectionIndex.Query nearby = index.query(
                new BlockPos(baseX - 1, baseY - 1, baseZ - 1),
                new BlockPos(baseX + 16, baseY + 16, baseZ + 16));
        Map<BlockPos, BlockState> environment = new HashMap<>(nearby.blocks().size());
        for (StructureTemplate.StructureBlockInfo block : nearby.blocks()) {
            environment.put(worldPosition(block.pos()), block.state());
        }
        return Map.copyOf(environment);
    }

    private BlockPos worldPosition(BlockPos templatePosition) {
        return source.origin().offset(templatePosition.offset(authoredMinimum));
    }

    private AABB sectionBounds(long section) {
        BlockPos minimum = worldPosition(new BlockPos(
                SectionPos.x(section) << 4,
                SectionPos.y(section) << 4,
                SectionPos.z(section) << 4));
        return new AABB(
                minimum.getX(),
                minimum.getY(),
                minimum.getZ(),
                minimum.getX() + 16,
                minimum.getY() + 16,
                minimum.getZ() + 16);
    }

    private static AABB worldSectionBounds(long section) {
        int minimumX = SectionPos.x(section) << 4;
        int minimumY = SectionPos.y(section) << 4;
        int minimumZ = SectionPos.z(section) << 4;
        return new AABB(
                minimumX,
                minimumY,
                minimumZ,
                minimumX + 16,
                minimumY + 16,
                minimumZ + 16);
    }

    private static List<Long> authoredWorldSections(
            BlockPos minimum,
            BlockPos maximum) {
        int minimumSectionX = SectionPos.blockToSectionCoord(minimum.getX());
        int minimumSectionY = SectionPos.blockToSectionCoord(minimum.getY());
        int minimumSectionZ = SectionPos.blockToSectionCoord(minimum.getZ());
        int maximumSectionX = SectionPos.blockToSectionCoord(maximum.getX());
        int maximumSectionY = SectionPos.blockToSectionCoord(maximum.getY());
        int maximumSectionZ = SectionPos.blockToSectionCoord(maximum.getZ());
        List<Long> sections = new ArrayList<>(
                (maximumSectionX - minimumSectionX + 1)
                        * (maximumSectionY - minimumSectionY + 1)
                        * (maximumSectionZ - minimumSectionZ + 1));
        for (int sectionX = minimumSectionX;
                sectionX <= maximumSectionX;
                sectionX++) {
            for (int sectionY = minimumSectionY;
                    sectionY <= maximumSectionY;
                    sectionY++) {
                for (int sectionZ = minimumSectionZ;
                        sectionZ <= maximumSectionZ;
                        sectionZ++) {
                    sections.add(SectionPos.asLong(
                            sectionX,
                            sectionY,
                            sectionZ));
                }
            }
        }
        return List.copyOf(sections);
    }

    private final class PresentSectionBuild {
        private final BlockPos minimum;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final Map<Long, BlockState> rememberedStates;
        private final List<ClientEchoState.GhostModel> built = new ArrayList<>();
        private final Map<Long, Double> occluderDistances = new HashMap<>();
        private int cursor;

        private PresentSectionBuild(
                BlockPos minimum,
                BlockPos maximum,
                Map<Long, BlockState> rememberedStates) {
            this.minimum = minimum;
            this.sizeX = maximum.getX() - minimum.getX() + 1;
            this.sizeY = maximum.getY() - minimum.getY() + 1;
            this.sizeZ = maximum.getZ() - minimum.getZ() + 1;
            this.rememberedStates = rememberedStates;
        }

        private PresentBuildBudget advance(
                Minecraft minecraft,
                int cellBudget,
                int modelBudget) {
            int cellCount = sizeX * sizeY * sizeZ;
            // Cell scanning must not stall when the model budget is spent:
            // ADDED/REPLACED occluders still have to register so the live rubble
            // mesh yields even before its translucent substitute is baked.
            while (cursor < cellCount && cellBudget > 0) {
                int localX = cursor % sizeX;
                int yz = cursor / sizeX;
                int localY = yz % sizeY;
                int localZ = yz / sizeY;
                cursor++;
                cellBudget--;

                BlockPos position = minimum.offset(localX, localY, localZ);
                BlockState present = minecraft.level.getBlockState(position);
                if (present.isAir()) {
                    continue;
                }
                BlockState remembered = rememberedStates.get(position.asLong());
                boolean authoredBySite = additions != null
                        && additions.contains(position, authoredWorldMinimum);
                // When the companion set is missing, leave undescribed cells to
                // the world. Passing true here made every plains block inside the
                // memory box fade as if the past were empty air.
                EchoBlockChange.Kind change = EchoBlockChange.classify(
                        remembered,
                        present,
                        authoredBySite);
                if (!change.canFadePresentBlock()
                        || !EchoBlockChange.shouldHidePresentGeometry(present)) {
                    continue;
                }
                // Hide live mesh for both ADDED and REPLACED. Fallen rubble that
                // replaced buried past dirt has no remembered exterior faces, so
                // the remembered SectionBuild never baked a substitute — occluders
                // must still register here or the mound stays opaque.
                occluderDistances.put(
                        position.asLong(),
                        position.getCenter().distanceTo(waveOrigin));
                // Only ADDED needs a present-state translucent stand-in; REPLACED
                // keeps its remembered ghost from the intact section stream.
                if (change != EchoBlockChange.Kind.ADDED || modelBudget <= 0) {
                    continue;
                }
                modelBudget--;
                ClientEchoState.GhostModel model = ClientEchoState.buildGhostModel(
                        minecraft,
                        new ClientEchoState.GhostBlock(
                                position,
                                present,
                                change),
                        null);
                if (model != null) {
                    built.add(model);
                }
            }
            return new PresentBuildBudget(cellBudget, modelBudget);
        }

        private boolean finished() {
            return cursor >= sizeX * sizeY * sizeZ;
        }

        private Map<Long, Double> occluderDistances() {
            return Collections.unmodifiableMap(occluderDistances);
        }

        private PresentSection result() {
            return new PresentSection(
                    List.copyOf(built),
                    Map.copyOf(occluderDistances));
        }
    }

    private final class SectionBuild {
        private final List<StructureTemplate.StructureBlockInfo> blocks;
        private final Map<BlockPos, BlockState> environment;
        private final List<ClientEchoState.GhostModel> built = new ArrayList<>();
        private final Map<Long, Double> replacedOccluders = new HashMap<>();
        private int cursor;

        private SectionBuild(
                List<StructureTemplate.StructureBlockInfo> blocks,
                Map<BlockPos, BlockState> environment) {
            this.blocks = blocks;
            this.environment = environment;
        }

        private int advance(Minecraft minecraft, int budget) {
            while (cursor < blocks.size() && budget > 0) {
                StructureTemplate.StructureBlockInfo block = blocks.get(cursor++);
                BlockPos position = worldPosition(block.pos());
                budget--;
                if (!minecraft.level.hasChunkAt(position)) {
                    continue;
                }
                BlockState present = minecraft.level.getBlockState(position);
                BlockState rememberedState =
                        EchoMemoryRevision.overlayStateOr(
                                source,
                                position,
                                block.state());
                if (rememberedState.isAir()) {
                    continue;
                }
                EchoBlockChange.Kind change = EchoBlockChange.classify(
                        rememberedState,
                        present);
                // Buried solids skip ghost baking, but fadeable present
                // geometry (REPLACED rubble, ADDED over plants/barriers) still
                // registers a hide distance here.
                if (change.canFadePresentBlock()
                        && EchoBlockChange.shouldHidePresentGeometry(present)) {
                    replacedOccluders.put(
                            position.asLong(),
                            position.getCenter().distanceTo(waveOrigin));
                }
                if (!change.rendersRememberedBlock()) {
                    continue;
                }
                ClientEchoState.GhostBlock ghost =
                        new ClientEchoState.GhostBlock(
                                position,
                                rememberedState,
                                change);
                ClientEchoState.GhostModel model =
                        ClientEchoState.buildGhostModel(
                                minecraft,
                                ghost,
                                environment);
                if (model != null) {
                    built.add(model);
                }
            }
            return budget;
        }

        private boolean finished() {
            return cursor >= blocks.size();
        }

        private List<ClientEchoState.GhostModel> models() {
            return List.copyOf(built);
        }

        private Map<Long, Double> replacedOccluders() {
            return Map.copyOf(replacedOccluders);
        }

    }

    final class LocalStateBuild {
        private final EchoWaveVolume volume;
        private final BlockPos minimum;
        private final BlockPos maximum;
        private final int minimumSectionX;
        private final int minimumSectionY;
        private final int minimumSectionZ;
        private final int maximumSectionX;
        private final int maximumSectionY;
        private final int maximumSectionZ;
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private int sectionX;
        private int sectionY;
        private int sectionZ;
        private List<StructureTemplate.StructureBlockInfo> candidates = List.of();
        private int cursor;
        private boolean complete;

        private LocalStateBuild(EchoWaveVolume volume) {
            this.volume = volume;
            this.minimum = volume.minBlock()
                    .subtract(source.origin())
                    .subtract(authoredMinimum);
            this.maximum = volume.maxBlock()
                    .subtract(source.origin())
                    .subtract(authoredMinimum);
            this.minimumSectionX = SectionPos.blockToSectionCoord(minimum.getX());
            this.minimumSectionY = SectionPos.blockToSectionCoord(minimum.getY());
            this.minimumSectionZ = SectionPos.blockToSectionCoord(minimum.getZ());
            this.maximumSectionX = SectionPos.blockToSectionCoord(maximum.getX());
            this.maximumSectionY = SectionPos.blockToSectionCoord(maximum.getY());
            this.maximumSectionZ = SectionPos.blockToSectionCoord(maximum.getZ());
            this.sectionX = minimumSectionX;
            this.sectionY = minimumSectionY;
            this.sectionZ = minimumSectionZ;
        }

        boolean advance(long deadlineNanos) {
            int processed = 0;
            while (!complete
                    && (processed == 0 || System.nanoTime() < deadlineNanos)) {
                if (cursor >= candidates.size()) {
                    if (sectionX > maximumSectionX) {
                        complete = true;
                        continue;
                    }
                    candidates = index.sectionBlocks(SectionPos.asLong(
                            sectionX,
                            sectionY,
                            sectionZ));
                    cursor = 0;
                    advanceSectionCursor();
                    if (candidates.isEmpty()) {
                        continue;
                    }
                }
                StructureTemplate.StructureBlockInfo block =
                        candidates.get(cursor++);
                processed++;
                BlockPos position = block.pos();
                if (position.getX() < minimum.getX()
                        || position.getY() < minimum.getY()
                        || position.getZ() < minimum.getZ()
                        || position.getX() > maximum.getX()
                        || position.getY() > maximum.getY()
                        || position.getZ() > maximum.getZ()) {
                    continue;
                }
                BlockPos world = worldPosition(position);
                if (volume.contains(world.getCenter())) {
                    states.put(world, block.state());
                }
            }
            return complete;
        }

        Map<BlockPos, BlockState> result() {
            if (!complete) {
                throw new IllegalStateException(
                        "Template local states requested before completion");
            }
            return Map.copyOf(states);
        }

        private void advanceSectionCursor() {
            sectionZ++;
            if (sectionZ <= maximumSectionZ) {
                return;
            }
            sectionZ = minimumSectionZ;
            sectionY++;
            if (sectionY <= maximumSectionY) {
                return;
            }
            sectionY = minimumSectionY;
            sectionX++;
        }
    }

    /**
     * Extracts the complete exterior shell once from the authored data. This
     * is intentionally data-only: no level lookup or block-model baking
     * happens here, so it remains safe to prepare before the effect starts.
     */
    private static Map<Long, List<EchoTemplateWaveMesher.Patch>> buildWaveSurfaces(
            EchoTemplateProjectionIndex index) {
        Map<Long, BlockState> states = new HashMap<>(
                index.indexedBlockCount());
        for (long section : index.sectionKeys()) {
            for (StructureTemplate.StructureBlockInfo block
                    : index.sectionBlocks(section)) {
                states.put(block.pos().asLong(), block.state());
            }
        }

        Map<Long, List<EchoTemplateWaveMesher.Patch>> surfaces = new HashMap<>();
        for (long section : index.sectionKeys()) {
            List<EchoTemplateWaveMesher.Patch> merged =
                    EchoTemplateWaveMesher.meshSection(
                            index.sectionBlocks(section),
                            states);
            if (!merged.isEmpty()) {
                List<EchoTemplateWaveMesher.Patch> tiles = new ArrayList<>();
                for (EchoTemplateWaveMesher.Patch patch : merged) {
                    tiles.addAll(patch.tiles(WAVE_TILE_EDGE_BLOCKS));
                }
                surfaces.put(section, List.copyOf(tiles));
            }
        }
        return Map.copyOf(surfaces);
    }

    private final class WaveTileBuild {
        private final java.util.Iterator<
                        Map.Entry<Long, List<EchoTemplateWaveMesher.Patch>>>
                sections = waveSurfaces.entrySet().iterator();
        private final Map<Long, List<TemplateWaveTile>> built = new HashMap<>();
        private long section;
        private List<EchoTemplateWaveMesher.Patch> source = List.of();
        private List<TemplateWaveTile> sectionFaces = new ArrayList<>();
        private int cursor;

        private boolean advance(Minecraft minecraft, long budgetNanos) {
            long deadline = System.nanoTime() + budgetNanos;
            int processed = 0;
            while (processed == 0 || System.nanoTime() < deadline) {
                if (cursor >= source.size()) {
                    finishSection();
                    if (!sections.hasNext()) {
                        return true;
                    }
                    Map.Entry<Long, List<EchoTemplateWaveMesher.Patch>> next =
                            sections.next();
                    section = next.getKey();
                    source = next.getValue();
                    sectionFaces = new ArrayList<>(source.size());
                    cursor = 0;
                    continue;
                }

                EchoTemplateWaveMesher.Patch tile = source.get(cursor++);
                processed++;
                BlockPos position = worldPosition(tile.position());
                // A pulse illuminates a remembered surface even where its
                // block is unchanged. Restricting this to changed blocks left
                // long silent gaps between the local current-space cache and
                // the far reaches of a large authored memory.
                sectionFaces.add(new TemplateWaveTile(
                        position,
                        tile.direction(),
                        tile.width(),
                        tile.height(),
                        ClientEchoState.buildTemplateWaveFace(
                                position,
                                tile.direction(),
                                tile.state(),
                                tile.width(),
                                tile.height(),
                                waveOrigin)));
            }
            return false;
        }

        private void finishSection() {
            if (!sectionFaces.isEmpty()) {
                built.put(section, List.copyOf(sectionFaces));
                sectionFaces = new ArrayList<>();
            }
        }

        private Map<Long, List<TemplateWaveTile>> freeze() {
            finishSection();
            return Map.copyOf(built);
        }
    }

    private final class EntityCaptureBuild {
        private final List<StructureTemplate.StructureEntityInfo> sourceEntities =
                index.entities();
        private final List<SnapshotEntity> captured =
                new ArrayList<>(sourceEntities.size());
        private int cursor;

        private boolean advance(
                Minecraft minecraft,
                long deadlineNanos) {
            int processed = 0;
            while (cursor < sourceEntities.size()
                    && (processed == 0
                            || System.nanoTime() < deadlineNanos)) {
                StructureTemplate.StructureEntityInfo info =
                        sourceEntities.get(cursor++);
                processed++;
                EchoTemplateResolver.captureTemplateEntity(
                                minecraft.level,
                                source,
                                info)
                        .ifPresent(captured::add);
            }
            return cursor >= sourceEntities.size();
        }
    }

    private record CachedTemplate(
            EchoTemplateProjectionIndex index,
            Map<Long, List<EchoTemplateWaveMesher.Patch>> waveSurfaces,
            @Nullable EchoSiteAdditions additions) {
    }

    private record PresentBuildBudget(int cells, int models) {
    }

    private record PresentSection(
            List<ClientEchoState.GhostModel> models,
            Map<Long, Double> occluderDistances) {
    }

    private record TemplateWaveTile(
            BlockPos position,
            net.minecraft.core.Direction direction,
            int width,
            int height,
            ClientEchoState.ScanFace face) {
        private boolean fullyCoveredBy(Set<BlockPos> locallyRenderedPositions) {
            for (int v = 0; v < height; v++) {
                for (int u = 0; u < width; u++) {
                    if (!locallyRenderedPositions.contains(
                            position.relative(
                                    EchoTemplateWaveMesher.uAxis(direction),
                                    u)
                                    .relative(
                                            EchoTemplateWaveMesher.vAxis(direction),
                                            v))) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}
