package dev.alvar.echoespast.snapshot;

import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.mixin.server.StructureTemplateAccessor;
import dev.alvar.echoespast.resonance.EchoSiteType;
import dev.alvar.echoespast.world.EchoSiteLoot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

public final class EchoTemplateResolver {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<StructureTemplate, EchoTemplateProjectionIndex>
            PROJECTION_INDEXES =
                    Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Full authored footprint for Philosopher's Stone materialization.
     * Unlike {@link #resolveForProjection}, this never clips to the Past Echo
     * visual/network window — every projectable block inside the memory bounds
     * is included so a giant site can become physical as a whole.
     */
    public static MaterializationFootprint resolveForMaterialization(
            ServerLevel level,
            EchoSnapshot source) {
        if (!source.isTemplateReference()) {
            return MaterializationFootprint.fromConcrete(level, source);
        }
        var template = level.getStructureManager()
                .get(source.template().orElseThrow())
                .orElse(null);
        if (template == null) {
            return MaterializationFootprint.fromConcrete(level, source);
        }

        EchoTemplateProjectionIndex index = projectionIndex(template);
        BlockPos authoredMinimum = source.boundsMin().orElse(BlockPos.ZERO);
        BlockPos authoredMaximum = source.boundsMax().orElse(BlockPos.ZERO);
        BlockPos worldMinimum = source.origin().offset(authoredMinimum);
        BlockPos worldMaximum = source.origin().offset(authoredMaximum);
        BlockPos templateMinimum = BlockPos.ZERO;
        BlockPos templateMaximum = authoredMaximum.subtract(authoredMinimum);

        EchoTemplateProjectionIndex.Query query = index.query(
                templateMinimum,
                templateMaximum);
        EchoSiteType site = source.site().map(EchoSiteType::byId).orElse(null);
        Map<Long, EchoMemoryRevision.MaterializationMutable> cells =
                new HashMap<>(query.blocks().size() * 2);
        addHistoricalAirSeed(
                level,
                source,
                worldMinimum,
                worldMaximum,
                cells);
        for (StructureTemplate.StructureBlockInfo entry : query.blocks()) {
            BlockPos relative = entry.pos().offset(authoredMinimum);
            if (relative.getX() < authoredMinimum.getX()
                    || relative.getY() < authoredMinimum.getY()
                    || relative.getZ() < authoredMinimum.getZ()
                    || relative.getX() > authoredMaximum.getX()
                    || relative.getY() > authoredMaximum.getY()
                    || relative.getZ() > authoredMaximum.getZ()) {
                continue;
            }
            BlockState state = entry.state();
            if (state.isAir()
                    || state.is(Blocks.STRUCTURE_BLOCK)
                    || state.is(Blocks.BARRIER)) {
                continue;
            }
            BlockPos world = source.origin().offset(relative);
            Optional<CompoundTag> blockEntity = Optional.ofNullable(entry.nbt())
                    .map(CompoundTag::copy);
            blockEntity = EchoSiteLoot.sealPastBlock(blockEntity, site, entry.pos(), world);
            cells.put(
                    world.asLong(),
                    new EchoMemoryRevision.MaterializationMutable(
                            world,
                            state,
                            blockEntity));
        }
        EchoMemoryRevision.applyBlockOverlay(source, cells);
        List<MaterializationCell> remembered = new ArrayList<>(cells.size());
        for (EchoMemoryRevision.MaterializationMutable cell : cells.values()) {
            remembered.add(new MaterializationCell(
                    cell.position(),
                    cell.state(),
                    cell.blockEntityData()));
        }
        List<SnapshotEntity> entities = EchoMemoryRevision.entitiesForProjection(
                source,
                captureTemplateEntities(level, source, index));
        return new MaterializationFootprint(
                source.origin(),
                worldMinimum,
                worldMaximum,
                List.copyOf(remembered),
                entities,
                true);
    }

    public record MaterializationCell(
            BlockPos position,
            BlockState state,
            Optional<CompoundTag> blockEntityData) {
    }

    public record MaterializationFootprint(
            BlockPos origin,
            BlockPos worldMinimum,
            BlockPos worldMaximum,
            List<MaterializationCell> remembered,
            List<SnapshotEntity> entities,
            boolean authoredTemplate) {

        private static MaterializationFootprint fromConcrete(
                ServerLevel level,
                EchoSnapshot source) {
            BlockPos worldMinimum;
            BlockPos worldMaximum;
            if (source.boundsMin().isPresent() && source.boundsMax().isPresent()) {
                worldMinimum = source.origin().offset(source.boundsMin().orElseThrow());
                worldMaximum = source.origin().offset(source.boundsMax().orElseThrow());
            } else {
                int radius = source.radius();
                worldMinimum = source.origin().offset(-radius, -radius, -radius);
                worldMaximum = source.origin().offset(radius, radius, radius);
            }
            Map<Long, EchoMemoryRevision.MaterializationMutable> remembered =
                    new HashMap<>(Math.max(16, source.blocks().size() * 2));
            if (source.site().isPresent()) {
                addHistoricalAirSeed(
                        level,
                        source,
                        worldMinimum,
                        worldMaximum,
                        remembered);
            }
            for (SnapshotBlock block : source.blocks()) {
                BlockState state = source.state(block);
                BlockPos position = source.worldPosition(block);
                remembered.put(
                        position.asLong(),
                        new EchoMemoryRevision.MaterializationMutable(
                                position,
                                state,
                                block.blockEntityData()));
            }
            List<MaterializationCell> cells = new ArrayList<>(remembered.size());
            for (EchoMemoryRevision.MaterializationMutable cell : remembered.values()) {
                cells.add(new MaterializationCell(
                        cell.position(),
                        cell.state(),
                        cell.blockEntityData()));
            }
            return new MaterializationFootprint(
                    source.origin(),
                    worldMinimum,
                    worldMaximum,
                    List.copyOf(cells),
                    source.entities(),
                    // Site-backed concrete still owns an authored footprint; do
                    // not vacuum oceans inside the memory AABB.
                    source.site().isPresent());
        }
    }

    /**
     * Adds only cells proven to be historical air by the site's packaged
     * present-vs-intact difference. Missing template cells are deliberately
     * not included: they may be terrain, ocean or cave wall belonging to the
     * generated world rather than to the authored structure.
     */
    private static void addHistoricalAirSeed(
            ServerLevel level,
            EchoSnapshot source,
            BlockPos worldMinimum,
            BlockPos worldMaximum,
            Map<Long, EchoMemoryRevision.MaterializationMutable> cells) {
        Identifier templateId = source.template().orElseGet(() -> {
            EchoSiteType site = source.site().map(EchoSiteType::byId).orElse(null);
            return site == null ? null : site.intactTemplate();
        });
        if (templateId == null) {
            return;
        }
        EchoSiteAdditions.load(
                        level.getServer().getResourceManager(),
                        EchoSiteAdditions.resourceFor(templateId))
                .ifPresent(additions -> additions.forEachCell((x, y, z) -> {
                    BlockPos position = worldMinimum.offset(x, y, z);
                    if (!inside(position, worldMinimum, worldMaximum)) {
                        return;
                    }
                    cells.put(
                            position.asLong(),
                            new EchoMemoryRevision.MaterializationMutable(
                                    position,
                                    Blocks.AIR.defaultBlockState(),
                                    Optional.empty()));
                }));
    }

    private static boolean inside(
            BlockPos position,
            BlockPos minimum,
            BlockPos maximum) {
        return position.getX() >= minimum.getX()
                && position.getY() >= minimum.getY()
                && position.getZ() >= minimum.getZ()
                && position.getX() <= maximum.getX()
                && position.getY() <= maximum.getY()
                && position.getZ() <= maximum.getZ();
    }

    public static EchoSnapshot resolve(ServerLevel level, EchoSnapshot snapshot) {
        if (!snapshot.isTemplateReference()) {
            return snapshot;
        }
        if (!fitsCompactSnapshot(snapshot)) {
            // SnapshotBlock deliberately packs only a -32..31 local cube.
            // A giant authored site must therefore use the same bounded view
            // that networking and rendering use, never an invalid full copy.
            return resolveForProjection(
                    level,
                    snapshot,
                    snapshot.origin().getCenter(),
                    snapshot.radius());
        }
        var template = level.getStructureManager()
                .get(snapshot.template().orElseThrow())
                .orElse(null);
        if (template == null) {
            return snapshot;
        }

        CompoundTag root = template.save(new CompoundTag());
        ListTag serializedPalette = root.getListOrEmpty("palette");
        List<BlockState> sourcePalette = new ArrayList<>(serializedPalette.size());
        var blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        for (int index = 0; index < serializedPalette.size(); index++) {
            sourcePalette.add(NbtUtils.readBlockState(
                    blocks,
                    serializedPalette.getCompoundOrEmpty(index)));
        }

        Map<BlockState, Integer> compactIndices = new LinkedHashMap<>();
        List<BlockState> compactPalette = new ArrayList<>();
        List<SnapshotBlock> resolved = new ArrayList<>();
        ListTag serializedBlocks = root.getListOrEmpty("blocks");
        BlockPos min = snapshot.boundsMin().orElse(BlockPos.ZERO);
        BlockPos max = snapshot.boundsMax().orElse(BlockPos.ZERO);
        for (int index = 0; index < serializedBlocks.size(); index++) {
            CompoundTag entry = serializedBlocks.getCompoundOrEmpty(index);
            ListTag position = entry.getListOrEmpty("pos");
            if (position.size() < 3) {
                continue;
            }
            BlockPos relative = new BlockPos(
                    position.getIntOr(0, 0) + min.getX(),
                    position.getIntOr(1, 0) + min.getY(),
                    position.getIntOr(2, 0) + min.getZ());
            if (relative.getX() < min.getX()
                    || relative.getY() < min.getY()
                    || relative.getZ() < min.getZ()
                    || relative.getX() > max.getX()
                    || relative.getY() > max.getY()
                    || relative.getZ() > max.getZ()) {
                continue;
            }
            int stateIndex = entry.getIntOr("state", -1);
            if (stateIndex < 0 || stateIndex >= sourcePalette.size()) {
                continue;
            }
            BlockState state = sourcePalette.get(stateIndex);
            if (state.isAir()
                    || state.is(Blocks.STRUCTURE_BLOCK)
                    || state.is(Blocks.BARRIER)) {
                continue;
            }
            int compactIndex = compactIndices.computeIfAbsent(state, ignored -> {
                compactPalette.add(state);
                return compactPalette.size() - 1;
            });
            resolved.add(SnapshotBlock.of(
                    relative.getX(),
                    relative.getY(),
                    relative.getZ(),
                    compactIndex,
                    entry.getCompound("nbt").map(CompoundTag::copy).orElse(null)));
        }
        return snapshot.resolved(
                compactPalette,
                resolved,
                captureTemplateEntities(level, snapshot, projectionIndex(template)));
    }

    /**
     * Resolves only the spatial window that can contribute to one activation.
     * The item keeps the tiny template reference, so a million-block authored
     * site never becomes a million-block inventory component or client packet.
     */
    public static EchoSnapshot resolveForProjection(
            ServerLevel level,
            EchoSnapshot snapshot,
            Vec3 pulseOrigin,
            int configuredRadius) {
        if (!snapshot.isTemplateReference()) {
            return snapshot;
        }
        var template = level.getStructureManager()
                .get(snapshot.template().orElseThrow())
                .orElse(null);
        if (template == null) {
            return snapshot;
        }

        EchoTemplateProjectionIndex index =
                projectionIndex(template);
        BlockPos viewOrigin =
                BlockPos.containing(pulseOrigin);
        int ambientRadius =
                EchoProjectionBudget.ambientRadius(
                        configuredRadius);
        // Small authored memories are shown whole. A giant site such as
        // Medusa must only rebuild the space reached by this pulse; using its
        // full bounds made it saturate the 65k packet cap in one click.
        int queryRadius = index.indexedBlockCount()
                        > EchoProjectionBudget.LARGE_MEMORY_THRESHOLD
                ? ambientRadius
                : authoredQueryRadius(snapshot, pulseOrigin);
        long projectionStarted = System.nanoTime();
        ProjectionView view = collectProjectionView(
                snapshot,
                index,
                pulseOrigin,
                viewOrigin,
                queryRadius,
                EchoProjectionBudget.MAX_NETWORK_BLOCKS);
        List<SnapshotEntity> entities = projectTemplateEntities(
                captureTemplateEntities(level, snapshot, index),
                snapshot.origin(),
                pulseOrigin,
                viewOrigin,
                queryRadius);

        ViewBounds bounds = clippedBounds(
                snapshot,
                viewOrigin,
                queryRadius);
        double elapsedMs =
                (System.nanoTime() - projectionStarted)
                        / 1_000_000.0;
        if (elapsedMs >= 2.0) {
            LOGGER.info(
                    "Prepared authored Past Echo window: ambientRadius={}, queryRadius={}, visitedIndexedBlocks={}, transferredBlocks={}, timeMs={}",
                    ambientRadius,
                    queryRadius,
                    view.visitedEntries(),
                    view.blocks().size(),
                    elapsedMs);
        }
        return new EchoSnapshot(
                EchoSnapshot.CURRENT_VERSION,
                snapshot.dimension(),
                viewOrigin,
                ambientRadius,
                true,
                view.palette(),
                view.blocks(),
                entities,
                Optional.empty(),
                Optional.of(bounds.minimum()),
                Optional.of(bounds.maximum()),
                snapshot.site());
    }

    private static boolean fitsCompactSnapshot(EchoSnapshot snapshot) {
        BlockPos minimum = snapshot.boundsMin().orElse(BlockPos.ZERO);
        BlockPos maximum = snapshot.boundsMax().orElse(BlockPos.ZERO);
        return minimum.getX() >= -32 && minimum.getY() >= -32 && minimum.getZ() >= -32
                && maximum.getX() <= 31 && maximum.getY() <= 31 && maximum.getZ() <= 31;
    }

    private static int authoredQueryRadius(
            EchoSnapshot snapshot,
            Vec3 pulseOrigin) {
        BlockPos minimum = snapshot.origin().offset(
                snapshot.boundsMin()
                        .orElse(BlockPos.ZERO));
        BlockPos maximum = snapshot.origin().offset(
                snapshot.boundsMax()
                        .orElse(BlockPos.ZERO));
        double farthest = 1.0;
        for (int x : new int[] {
                minimum.getX(),
                maximum.getX()
        }) {
            for (int y : new int[] {
                    minimum.getY(),
                    maximum.getY()
            }) {
                for (int z : new int[] {
                        minimum.getZ(),
                        maximum.getZ()
                }) {
                    farthest = Math.max(
                            farthest,
                            new BlockPos(x, y, z)
                                    .getCenter()
                                    .distanceTo(
                                            pulseOrigin));
                }
            }
        }
        return Math.clamp(
                (int) Math.ceil(farthest),
                1,
                EchoProjectionBudget
                        .MAX_COMPACT_QUERY_RADIUS);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        int loaded = 0;
        long start = System.nanoTime();
        for (EchoSiteType site : EchoSiteType.values()) {
            Optional<StructureTemplate> template =
                    level.getStructureManager()
                            .get(site.intactTemplate());
            if (template.isEmpty()) {
                continue;
            }
            projectionIndex(template.orElseThrow());
            loaded++;
        }
        if (loaded > 0) {
            LOGGER.info(
                    "Prepared {} Past Echo template indices in {} ms",
                    loaded,
                    (System.nanoTime() - start) / 1_000_000L);
        }
    }

    private static EchoTemplateProjectionIndex projectionIndex(
            StructureTemplate template) {
        synchronized (PROJECTION_INDEXES) {
            EchoTemplateProjectionIndex cached =
                    PROJECTION_INDEXES.get(template);
            if (cached != null) {
                return cached;
            }
            long start = System.nanoTime();
            List<StructureTemplate.Palette> palettes =
                    ((StructureTemplateAccessor) (Object) template)
                            .echoes$getPalettes();
            List<StructureTemplate.StructureBlockInfo> blocks =
                    palettes.isEmpty()
                            ? List.of()
                            : palettes.getFirst().blocks();
            List<StructureTemplate.StructureEntityInfo> entities =
                    ((StructureTemplateAccessor) (Object) template)
                            .echoes$getEntityInfoList();
            EchoTemplateProjectionIndex built =
                    EchoTemplateProjectionIndex.build(blocks, entities);
            PROJECTION_INDEXES.put(template, built);
            LOGGER.info(
                    "Indexed Past Echo template: {} authored entries, "
                            + "{} projectable blocks, {} static entities, {} ms",
                    built.sourceBlockCount(),
                    built.indexedBlockCount(),
                    built.entities().size(),
                    (System.nanoTime() - start) / 1_000_000L);
            return built;
        }
    }

    private static List<BlockState> readPalette(
            ServerLevel level,
            ListTag serializedPalette) {
        List<BlockState> sourcePalette =
                new ArrayList<>(
                        serializedPalette.size());
        var blocks = level.registryAccess()
                .lookupOrThrow(Registries.BLOCK);
        for (int index = 0;
                index < serializedPalette.size();
                index++) {
            sourcePalette.add(NbtUtils.readBlockState(
                    blocks,
                    serializedPalette
                            .getCompoundOrEmpty(index)));
        }
        return sourcePalette;
    }

    /**
     * Rehydrates the template's immutable static entities without adding them
     * to the server world. Capturing the decoded entity rather than inventing
     * a generic frame preserves mod attachments such as PetrifiedPose, item
     * frames and armor-stand data exactly as they were authored.
     */
    public static List<SnapshotEntity> captureTemplateEntities(
            Level level,
            EchoSnapshot source,
            EchoTemplateProjectionIndex index) {
        if (index.entities().isEmpty()) {
            return List.of();
        }
        List<SnapshotEntity> captured = new ArrayList<>(index.entities().size());
        for (StructureTemplate.StructureEntityInfo info : index.entities()) {
            captureTemplateEntity(level, source, info).ifPresent(captured::add);
        }
        return List.copyOf(captured);
    }

    public static Optional<SnapshotEntity> captureTemplateEntity(
            Level level,
            EchoSnapshot source,
            StructureTemplate.StructureEntityInfo info) {
        try {
            CompoundTag entityData = info.nbt.copy();
            normalizeBlockAttachedPosition(entityData, info.pos);
            Entity root = EntityType.loadEntityRecursive(
                    entityData,
                    level,
                    EntitySpawnReason.LOAD,
                    entity -> entity);
            if (root == null) {
                return Optional.empty();
            }
            Vec3 templateOrigin = Vec3.atLowerCornerOf(source.origin().offset(
                    source.boundsMin().orElse(BlockPos.ZERO)));
            Vec3 destination = templateOrigin.add(info.pos);
            Vec3 translation = destination.subtract(root.position());
            for (Entity entity : root.getSelfAndPassengers().toList()) {
                Vec3 moved = entity.position().add(translation);
                entity.snapTo(
                        moved.x,
                        moved.y,
                        moved.z,
                        entity.getYRot(),
                        entity.getXRot());
            }
            EchoSiteType site = source.site().map(EchoSiteType::byId).orElse(null);
            EchoSiteLoot.sealPastEntity(
                    root,
                    site,
                    BlockPos.containing(info.pos),
                    BlockPos.containing(destination));
            return SnapshotEntityIO.capture(root, source.origin());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not read an authored Past Echo entity", exception);
            return Optional.empty();
        }
    }

    /**
     * Axiom preserves the source world's TileX/Y/Z for item frames and
     * paintings even after it makes their Pos relative to the blueprint. The
     * vanilla loader rejects that stale attachment before an echo can move it.
     * StructureEntityInfo.pos is the authoritative local position, so derive
     * the attachment from it before decoding the temporary entity.
     */
    private static void normalizeBlockAttachedPosition(
            CompoundTag data,
            Vec3 position) {
        boolean legacyAttachment = data.contains("TileX")
                && data.contains("TileY")
                && data.contains("TileZ");
        boolean modernAttachment = data.contains("block_pos");
        if (!legacyAttachment && !modernAttachment) {
            return;
        }
        BlockPos block = BlockPos.containing(position);
        data.putInt("TileX", block.getX());
        data.putInt("TileY", block.getY());
        data.putInt("TileZ", block.getZ());
        if (modernAttachment) {
            data.putIntArray("block_pos", new int[] {
                    block.getX(),
                    block.getY(),
                    block.getZ()
            });
        }
    }

    private static List<SnapshotEntity> projectTemplateEntities(
            List<SnapshotEntity> source,
            BlockPos templateAnchor,
            Vec3 pulseOrigin,
            BlockPos viewOrigin,
            int radius) {
        if (source.isEmpty()) {
            return List.of();
        }
        List<SnapshotEntity> projected = new ArrayList<>(source.size());
        double radiusSquared = (radius + 0.5) * (radius + 0.5);
        Vec3 viewOriginVector = Vec3.atLowerCornerOf(viewOrigin);
        for (SnapshotEntity entity : source) {
            Vec3 worldPosition = Vec3.atLowerCornerOf(templateAnchor)
                    .add(entity.offset());
            if (worldPosition.distanceToSqr(pulseOrigin) > radiusSquared) {
                continue;
            }
            Vec3 localOffset = worldPosition.subtract(viewOriginVector);
            if (localOffset.x < -32.0 || localOffset.x > 31.999
                    || localOffset.y < -32.0 || localOffset.y > 31.999
                    || localOffset.z < -32.0 || localOffset.z > 31.999) {
                continue;
            }
            projected.add(new SnapshotEntity(
                    localOffset,
                    entity.data(),
                    entity.pose(),
                    entity.ageInTicks(),
                    entity.yRot(),
                    entity.xRot(),
                    entity.bodyYRot(),
                    entity.headYRot(),
                    entity.animation(),
                    entity.passengerFrames()));
        }
        return List.copyOf(projected);
    }

    private static ProjectionView collectProjectionView(
            EchoSnapshot source,
            EchoTemplateProjectionIndex index,
            Vec3 pulseOrigin,
            BlockPos viewOrigin,
            int radius,
            int blockLimit) {
        Map<BlockState, Integer> compactIndices =
                new LinkedHashMap<>();
        List<BlockState> compactPalette =
                new ArrayList<>();
        int axis = radius * 2 + 1;
        int localCellLimit = Math.multiplyExact(
                axis,
                Math.multiplyExact(
                        axis,
                        axis));
        List<SnapshotBlock> resolved =
                new ArrayList<>(
                        Math.min(
                                index.indexedBlockCount(),
                                Math.min(
                                        blockLimit,
                                        localCellLimit)));
        BlockPos authoredMinimum =
                source.boundsMin()
                        .orElse(BlockPos.ZERO);
        BlockPos authoredMaximum =
                source.boundsMax()
                        .orElse(BlockPos.ZERO);
        double radiusSquared =
                (radius + 0.5)
                        * (radius + 0.5);
        BlockPos worldMinimum = viewOrigin.offset(
                -radius,
                -radius,
                -radius);
        BlockPos worldMaximum = viewOrigin.offset(
                radius,
                radius,
                radius);
        BlockPos templateMinimum =
                worldMinimum.subtract(source.origin())
                        .subtract(authoredMinimum);
        BlockPos templateMaximum =
                worldMaximum.subtract(source.origin())
                        .subtract(authoredMinimum);
        EchoTemplateProjectionIndex.Query query =
                index.query(
                        templateMinimum,
                        templateMaximum);

        for (StructureTemplate.StructureBlockInfo entry
                : query.blocks()) {
            BlockPos relative =
                    entry.pos().offset(authoredMinimum);
            if (relative.getX()
                            < authoredMinimum.getX()
                    || relative.getY()
                            < authoredMinimum.getY()
                    || relative.getZ()
                            < authoredMinimum.getZ()
                    || relative.getX()
                            > authoredMaximum.getX()
                    || relative.getY()
                            > authoredMaximum.getY()
                    || relative.getZ()
                            > authoredMaximum.getZ()) {
                continue;
            }
            BlockState state =
                    entry.state();
            BlockPos worldPosition =
                    source.origin().offset(relative);
            if (worldPosition.getCenter()
                            .distanceToSqr(pulseOrigin)
                    > radiusSquared) {
                continue;
            }
            BlockPos local =
                    worldPosition.subtract(viewOrigin);
            if (local.getX() < -32
                    || local.getX() > 31
                    || local.getY() < -32
                    || local.getY() > 31
                    || local.getZ() < -32
                    || local.getZ() > 31) {
                continue;
            }
            int compactIndex =
                    compactIndices.computeIfAbsent(
                            state,
                            ignored -> {
                                compactPalette.add(state);
                                return compactPalette.size()
                                        - 1;
                            });
            resolved.add(SnapshotBlock.of(
                    local.getX(),
                    local.getY(),
                    local.getZ(),
                    compactIndex,
                    entry.nbt() == null
                            ? null
                            : entry.nbt().copy()));
            if (resolved.size() >= blockLimit) {
                break;
            }
        }
        return new ProjectionView(
                List.copyOf(compactPalette),
                List.copyOf(resolved),
                query.visitedEntries());
    }

    private static ViewBounds clippedBounds(
            EchoSnapshot source,
            BlockPos viewOrigin,
            int radius) {
        BlockPos sourceMinimum =
                source.origin().offset(
                        source.boundsMin()
                                .orElse(BlockPos.ZERO));
        BlockPos sourceMaximum =
                source.origin().offset(
                        source.boundsMax()
                                .orElse(BlockPos.ZERO));
        BlockPos worldMinimum = new BlockPos(
                Math.max(
                        sourceMinimum.getX(),
                        viewOrigin.getX() - radius),
                Math.max(
                        sourceMinimum.getY(),
                        viewOrigin.getY() - radius),
                Math.max(
                        sourceMinimum.getZ(),
                        viewOrigin.getZ() - radius));
        BlockPos worldMaximum = new BlockPos(
                Math.min(
                        sourceMaximum.getX(),
                        viewOrigin.getX() + radius),
                Math.min(
                        sourceMaximum.getY(),
                        viewOrigin.getY() + radius),
                Math.min(
                        sourceMaximum.getZ(),
                        viewOrigin.getZ() + radius));
        if (worldMinimum.getX()
                        > worldMaximum.getX()
                || worldMinimum.getY()
                        > worldMaximum.getY()
                || worldMinimum.getZ()
                        > worldMaximum.getZ()) {
            return new ViewBounds(
                    BlockPos.ZERO,
                    BlockPos.ZERO);
        }
        return new ViewBounds(
                worldMinimum.subtract(viewOrigin),
                worldMaximum.subtract(viewOrigin));
    }

    private record ProjectionView(
            List<BlockState> palette,
            List<SnapshotBlock> blocks,
            int visitedEntries) {
    }

    private record ViewBounds(
            BlockPos minimum,
            BlockPos maximum) {
    }

    private EchoTemplateResolver() {
    }
}
