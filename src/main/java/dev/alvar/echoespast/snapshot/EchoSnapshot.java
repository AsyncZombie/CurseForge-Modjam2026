package dev.alvar.echoespast.snapshot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public record EchoSnapshot(
        int version,
        ResourceKey<Level> dimension,
        BlockPos origin,
        int radius,
        boolean sealed,
        List<BlockState> palette,
        List<SnapshotBlock> blocks,
        List<SnapshotEntity> entities,
        Optional<Identifier> template,
        Optional<BlockPos> boundsMin,
        Optional<BlockPos> boundsMax,
        Optional<Identifier> site,
        boolean entitiesRevised,
        List<EchoRevisionCell> revisionCells) {

    public static final int CURRENT_VERSION = 8;

    private static final Codec<EchoSnapshot> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("version", CURRENT_VERSION).forGetter(EchoSnapshot::version),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(EchoSnapshot::dimension),
            BlockPos.CODEC.fieldOf("origin").forGetter(EchoSnapshot::origin),
            Codec.INT.fieldOf("radius").forGetter(EchoSnapshot::radius),
            Codec.BOOL.optionalFieldOf("sealed", false).forGetter(EchoSnapshot::sealed),
            BlockState.CODEC.listOf().fieldOf("palette").forGetter(EchoSnapshot::palette),
            SnapshotBlock.CODEC.listOf().fieldOf("blocks").forGetter(EchoSnapshot::blocks),
            SnapshotEntity.CODEC.listOf().optionalFieldOf("entities", List.of()).forGetter(EchoSnapshot::entities),
            Identifier.CODEC.optionalFieldOf("template").forGetter(EchoSnapshot::template),
            BlockPos.CODEC.optionalFieldOf("bounds_min").forGetter(EchoSnapshot::boundsMin),
            BlockPos.CODEC.optionalFieldOf("bounds_max").forGetter(EchoSnapshot::boundsMax),
            Identifier.CODEC.optionalFieldOf("site").forGetter(EchoSnapshot::site),
            Codec.BOOL.optionalFieldOf("entities_revised", false).forGetter(EchoSnapshot::entitiesRevised),
            EchoRevisionCell.CODEC.listOf().optionalFieldOf("revision_cells", List.of()).forGetter(EchoSnapshot::revisionCells)
    ).apply(instance, EchoSnapshot::new));

    public static final Codec<EchoSnapshot> CODEC = RAW_CODEC.validate(EchoSnapshot::validate);

    public EchoSnapshot {
        origin = origin.immutable();
        palette = List.copyOf(palette);
        blocks = List.copyOf(blocks);
        entities = List.copyOf(entities);
        revisionCells = revisionCells == null ? List.of() : List.copyOf(revisionCells);
        template = template == null ? Optional.empty() : template;
        boundsMin = boundsMin == null ? Optional.empty() : boundsMin.map(BlockPos::immutable);
        boundsMax = boundsMax == null ? Optional.empty() : boundsMax.map(BlockPos::immutable);
        site = site == null ? Optional.empty() : site;
    }

    public EchoSnapshot(
            int version,
            ResourceKey<Level> dimension,
            BlockPos origin,
            int radius,
            boolean sealed,
            List<BlockState> palette,
            List<SnapshotBlock> blocks,
            List<SnapshotEntity> entities) {
        this(
                version,
                dimension,
                origin,
                radius,
                sealed,
                palette,
                blocks,
                entities,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of());
    }

    public EchoSnapshot(
            int version,
            ResourceKey<Level> dimension,
            BlockPos origin,
            int radius,
            boolean sealed,
            List<BlockState> palette,
            List<SnapshotBlock> blocks,
            List<SnapshotEntity> entities,
            Optional<Identifier> template,
            Optional<BlockPos> boundsMin,
            Optional<BlockPos> boundsMax,
            Optional<Identifier> site) {
        this(
                version,
                dimension,
                origin,
                radius,
                sealed,
                palette,
                blocks,
                entities,
                template,
                boundsMin,
                boundsMax,
                site,
                false,
                List.of());
    }

    public EchoSnapshot(
            int version,
            ResourceKey<Level> dimension,
            BlockPos origin,
            int radius,
            boolean sealed,
            List<BlockState> palette,
            List<SnapshotBlock> blocks,
            List<SnapshotEntity> entities,
            Optional<Identifier> template,
            Optional<BlockPos> boundsMin,
            Optional<BlockPos> boundsMax,
            Optional<Identifier> site,
            boolean entitiesRevised) {
        this(
                version,
                dimension,
                origin,
                radius,
                sealed,
                palette,
                blocks,
                entities,
                template,
                boundsMin,
                boundsMax,
                site,
                entitiesRevised,
                List.of());
    }

    private static DataResult<EchoSnapshot> validate(EchoSnapshot snapshot) {
        if (snapshot.version < 1 || snapshot.version > CURRENT_VERSION) {
            return DataResult.error(() -> "Unsupported echo snapshot version " + snapshot.version);
        }
        boolean hasBoundsMin = snapshot.boundsMin.isPresent();
        boolean hasBoundsMax = snapshot.boundsMax.isPresent();
        if (hasBoundsMin != hasBoundsMax) {
            return DataResult.error(() -> "Echo snapshot authored bounds must be supplied as a pair");
        }
        boolean authoredMemory = snapshot.template.isPresent()
                || (snapshot.sealed && hasBoundsMin);
        int maximumRadius = authoredMemory ? 32 : 16;
        if (snapshot.radius < 0 || snapshot.radius > maximumRadius) {
            return DataResult.error(() -> "Echo snapshot radius outside 0.." + maximumRadius);
        }
        if (snapshot.template.isPresent()
                && (snapshot.boundsMin.isEmpty() || snapshot.boundsMax.isEmpty())) {
            return DataResult.error(() -> "A template memory requires explicit aligned bounds");
        }
        for (SnapshotBlock block : snapshot.blocks) {
            if (block.paletteIndex() < 0 || block.paletteIndex() >= snapshot.palette.size()) {
                return DataResult.error(() -> "Echo snapshot contains an invalid palette index");
            }
        }
        for (EchoRevisionCell cell : snapshot.revisionCells) {
            if (cell.paletteIndex() < 0 || cell.paletteIndex() >= snapshot.palette.size()) {
                return DataResult.error(() -> "Echo snapshot contains an invalid revision palette index");
            }
        }
        return DataResult.success(snapshot);
    }

    public BlockState state(SnapshotBlock block) {
        return palette.get(block.paletteIndex());
    }

    public BlockState state(EchoRevisionCell cell) {
        return palette.get(cell.paletteIndex());
    }

    public BlockPos worldPosition(SnapshotBlock block) {
        return origin.offset(block.offset());
    }

    public BlockPos worldPosition(EchoRevisionCell cell) {
        return origin.offset(cell.offset());
    }

    /**
     * Whether a world position belongs to the stored historical volume. A
     * missing block inside this volume is meaningful historical air; a block
     * outside it is simply unknown and must remain present-world geometry.
     */
    public boolean containsWorldPosition(BlockPos position) {
        if (boundsMin.isPresent() && boundsMax.isPresent()) {
            BlockPos minimum = origin.offset(boundsMin.orElseThrow());
            BlockPos maximum = origin.offset(boundsMax.orElseThrow());
            return position.getX() >= minimum.getX()
                    && position.getX() <= maximum.getX()
                    && position.getY() >= minimum.getY()
                    && position.getY() <= maximum.getY()
                    && position.getZ() >= minimum.getZ()
                    && position.getZ() <= maximum.getZ();
        }
        return Math.abs(position.getX() - origin.getX()) <= radius
                && Math.abs(position.getY() - origin.getY()) <= radius
                && Math.abs(position.getZ() - origin.getZ()) <= radius;
    }

    public EchoSnapshot asSealed() {
        return sealed
                ? this
                : new EchoSnapshot(
                        version,
                        dimension,
                        origin,
                        radius,
                        true,
                        palette,
                        blocks,
                        entities,
                        template,
                        boundsMin,
                        boundsMax,
                        site,
                        entitiesRevised,
                        revisionCells);
    }

    public EchoSnapshot withSite(Identifier siteId) {
        return new EchoSnapshot(
                version,
                dimension,
                origin,
                radius,
                sealed,
                palette,
                blocks,
                entities,
                template,
                boundsMin,
                boundsMax,
                Optional.of(siteId),
                entitiesRevised,
                revisionCells);
    }

    public boolean canErase() {
        return !sealed;
    }

    public boolean isTemplateReference() {
        return template.isPresent();
    }

    public static EchoSnapshot templateReference(
            ResourceKey<Level> dimension,
            BlockPos origin,
            Identifier template,
            BlockPos boundsMin,
            BlockPos boundsMax) {
        return templateReference(dimension, origin, template, boundsMin, boundsMax, Optional.empty());
    }

    public static EchoSnapshot templateReference(
            ResourceKey<Level> dimension,
            BlockPos origin,
            Identifier template,
            BlockPos boundsMin,
            BlockPos boundsMax,
            Optional<Identifier> site) {
        int radius = Math.max(
                Math.max(Math.abs(boundsMin.getX()), Math.abs(boundsMax.getX())),
                Math.max(
                        Math.max(Math.abs(boundsMin.getY()), Math.abs(boundsMax.getY())),
                        Math.max(Math.abs(boundsMin.getZ()), Math.abs(boundsMax.getZ()))));
        return new EchoSnapshot(
                CURRENT_VERSION,
                dimension,
                origin,
                Math.min(32, radius),
                true,
                List.of(),
                List.of(),
                List.of(),
                Optional.of(template),
                Optional.of(boundsMin),
                Optional.of(boundsMax),
                site,
                false,
                List.of());
    }

    public EchoSnapshot resolved(
            List<BlockState> resolvedPalette,
            List<SnapshotBlock> resolvedBlocks,
            List<SnapshotEntity> resolvedEntities) {
        return new EchoSnapshot(
                CURRENT_VERSION,
                dimension,
                origin,
                radius,
                sealed,
                resolvedPalette,
                resolvedBlocks,
                resolvedEntities,
                Optional.empty(),
                boundsMin,
                boundsMax,
                site,
                false,
                List.of());
    }

    public EchoSnapshot withRevision(
            List<BlockState> revisionPalette,
            List<EchoRevisionCell> revisionCells,
            List<SnapshotEntity> revisionEntities,
            boolean entitiesRevised) {
        return new EchoSnapshot(
                CURRENT_VERSION,
                dimension,
                origin,
                radius,
                sealed,
                revisionPalette,
                List.of(),
                revisionEntities,
                template,
                boundsMin,
                boundsMax,
                site,
                entitiesRevised,
                revisionCells);
    }
}
