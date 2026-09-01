package dev.alvar.echoespast.world;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.block.EchoPedestalBlock;
import dev.alvar.echoespast.block.EchoPedestalBlockEntity;
import dev.alvar.echoespast.item.PastEchoMemory;
import dev.alvar.echoespast.resonance.EchoSiteType;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class EchoSitePiece extends TemplateStructurePiece {
    private static final String SITE_TAG = "EchoSite";
    private static final String SURFACE_Y_TAG = "EchoSurfaceY";
    private final EchoSiteType site;
    private final int surfaceY;

    public EchoSitePiece(
            StructureTemplateManager templates,
            EchoSiteType site,
            BlockPos anchor,
            int surfaceY) {
        super(
                EchoesShowThePast.ECHO_SITE_PIECE_TYPE.get(),
                0,
                templates,
                site.presentTemplate(),
                site.presentTemplate().toString(),
                settings(),
                anchor.offset(site.memoryMin()));
        this.site = site;
        this.surfaceY = surfaceY;
        includeCryptAccess();
    }

    public EchoSitePiece(StructureTemplateManager templates, CompoundTag tag) {
        super(
                EchoesShowThePast.ECHO_SITE_PIECE_TYPE.get(),
                tag,
                templates,
                ignored -> settings());
        Identifier siteId = Identifier.tryParse(tag.getStringOr(SITE_TAG, ""));
        this.site = EchoSiteType.byId(siteId);
        if (this.site == null) {
            throw new IllegalArgumentException("Unknown authored echo site " + siteId);
        }
        this.surfaceY = tag.getIntOr(
                SURFACE_Y_TAG,
                anchor().getY() - site.anchorYOffset());
        includeCryptAccess();
    }

    /**
     * An echo site is authored down to the block state, and its dry volume only
     * survives if placement stays passive.
     *
     * <p>{@code knownShape} keeps vanilla from running
     * {@code updateFromNeighbourShapes} and {@code updateNeighborsAt} over the
     * placement: the first rewrites authored stairs, walls and fences, and the
     * second is what invites the surrounding ocean to flow into a carved
     * chamber. {@code IGNORE_WATERLOGGING} stops the sea the island was dropped
     * into from waterlogging every authored slab and fence it touches.</p>
     */
    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false)
                .setKnownShape(true)
                .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING)
                .addProcessor(BarrierToAirProcessor.INSTANCE);
    }

    public EchoSiteType site() {
        return site;
    }

    public BlockPos cryptAnchor() {
        return anchor();
    }

    private BlockPos anchor() {
        return templatePosition.subtract(site.memoryMin());
    }

    private void includeCryptAccess() {
        if (!site.underground()) {
            return;
        }
        for (BlockPos entry : CryptAccessGate.entryMarkers(
                template,
                templatePosition,
                placeSettings)) {
            boundingBox.encapsulate(CryptAccessGate.accessBounds(anchor(), entry, surfaceY));
        }
    }

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager structures,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox writable,
            ChunkPos chunkPos,
            BlockPos origin) {
        super.postProcess(level, structures, generator, random, writable, chunkPos, origin);
        BoundingBox authored = site.memoryBounds(anchor());
        BoundingBox vegetation;
        if (site.underground()) {
            vegetation = new BoundingBox(
                    authored.minX(),
                    authored.minY(),
                    authored.minZ(),
                    authored.maxX(),
                    authored.maxY(),
                    authored.maxZ());
            for (BlockPos entry : CryptAccessGate.entryMarkers(
                    template,
                    templatePosition,
                    placeSettings)) {
                vegetation.encapsulate(CryptAccessGate.surfaceRimBounds(anchor(), entry, surfaceY));
            }
        } else {
            vegetation = getBoundingBox();
        }
        EchoSiteTreeCleanup.clearIntersecting(level, vegetation, writable);
        if (site.underground()) {
            for (BlockPos entry : CryptAccessGate.entryMarkers(
                    template,
                    templatePosition,
                    placeSettings)) {
                CryptAccessGate.build(level, anchor(), entry, surfaceY, writable);
            }
            CryptAccessGate.sealHullOpenings(
                    level,
                    template,
                    templatePosition,
                    placeSettings,
                    writable);
        }
        if (site.blendsIntoTerrain()) {
            EchoSiteTerrainBlend.blend(
                    level,
                    anchor().getY(),
                    getBoundingBox(),
                    writable,
                    site.family());
        }
        site.biome().ifPresent(biome -> {
            paintSiteBiome(level, chunkPos, writable, biome, authored);
            if (site.underground()) {
                for (BlockPos entry : CryptAccessGate.entryMarkers(
                        template,
                        templatePosition,
                        placeSettings)) {
                    paintSiteBiome(
                            level,
                            chunkPos,
                            writable,
                            biome,
                            CryptAccessGate.surfaceRimBounds(anchor(), entry, surfaceY));
                }
            }
        });
        EchoSiteLoot.assignPresent(level, site, templatePosition, writable);
    }

    /**
     * Paints the site's technical biome over the footprint of this chunk.
     *
     * <p>The biome is deliberately absent from the biome source, so it can only
     * reach the world here and never generates on its own. Structures run in
     * the {@code surface_structures} step, which is early enough that the
     * biome filter of every later decoration step already sees it: an authored
     * island keeps its own ambience and stops growing kelp or trees through its
     * roofs.</p>
     */
    private void paintSiteBiome(
            WorldGenLevel level,
            ChunkPos chunkPos,
            BoundingBox writable,
            ResourceKey<Biome> biome,
            BoundingBox footprint) {
        if (!footprint.intersects(writable)) {
            return;
        }
        BoundingBox region = new BoundingBox(
                Math.max(footprint.minX(), writable.minX()),
                Math.max(footprint.minY(), writable.minY()),
                Math.max(footprint.minZ(), writable.minZ()),
                Math.min(footprint.maxX(), writable.maxX()),
                Math.min(footprint.maxY(), writable.maxY()),
                Math.min(footprint.maxZ(), writable.maxZ()));
        Holder<Biome> holder = level.registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .get(biome)
                .orElse(null);
        if (holder == null) {
            return;
        }
        ChunkAccess chunk = level.getChunk(chunkPos.x(), chunkPos.z());
        chunk.fillBiomesFromNoise(
                (quartX, quartY, quartZ, sampler) -> region.isInside(
                                QuartPos.toBlock(quartX),
                                QuartPos.toBlock(quartY),
                                QuartPos.toBlock(quartZ))
                        ? holder
                        : chunk.getNoiseBiome(quartX, quartY, quartZ),
                level.getLevel().getChunkSource().randomState().sampler());
        chunk.markUnsaved();
    }

    @Override
    protected void addAdditionalSaveData(
            StructurePieceSerializationContext context,
            CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString(SITE_TAG, site.id().toString());
        tag.putInt(SURFACE_Y_TAG, surfaceY);
    }

    @Override
    protected void handleDataMarker(
            String markerId,
            BlockPos position,
            ServerLevelAccessor level,
            RandomSource random,
            BoundingBox bounds) {
        level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if ("pedestal".equals(markerId)) {
            level.setBlock(
                    position,
                    EchoesShowThePast.ECHO_PEDESTAL.get()
                            .defaultBlockState()
                            .setValue(EchoPedestalBlock.SPENT, false),
                    Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(position) instanceof EchoPedestalBlockEntity pedestal) {
                pedestal.setSite(site.id());
                pedestal.setEcho(PastEchoMemory.createFragment(
                        EchoSnapshot.templateReference(
                                level.getLevel().dimension(),
                                anchor(),
                                site.intactTemplate(),
                                site.memoryMin(),
                                site.memoryMax(),
                                Optional.of(site.id())),
                        Optional.empty()));
            }
            return;
        }
        if ("secret_chest".equals(markerId) || "chest".equals(markerId)) {
            level.setBlock(position, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(position) instanceof ChestBlockEntity chest) {
                chest.setItem(4, new ItemStack(EchoesShowThePast.RESONANT_FILAMENT.get(), 2 + random.nextInt(4)));
                chest.setItem(13, new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 2 + random.nextInt(5)));
                if ("secret_chest".equals(markerId)) {
                    chest.setItem(22, new ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE));
                }
                chest.setChanged();
            }
        }
    }
}
