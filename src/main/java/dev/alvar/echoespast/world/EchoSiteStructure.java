package dev.alvar.echoespast.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.resonance.EchoSiteType;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public final class EchoSiteStructure extends Structure {
    public static final MapCodec<EchoSiteStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance),
            Identifier.CODEC.fieldOf("site").forGetter(EchoSiteStructure::siteId)
    ).apply(instance, EchoSiteStructure::new));

    private final Identifier siteId;

    public EchoSiteStructure(StructureSettings settings, Identifier siteId) {
        super(settings);
        this.siteId = siteId;
    }

    public Identifier siteId() {
        return siteId;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        EchoSiteType site = EchoSiteType.byId(siteId);
        if (site == null || !site.generated()) {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        // Vanilla only tests biomes after findGenerationPoint returns a stub.
        // Cheap noise-biome rejection must run first or /locate samples every
        // random-spread candidate with a full land-footing height grid.
        int probeY = context.chunkGenerator().getSeaLevel() + 8;
        if (!context.validBiome().test(context.biomeSource().getNoiseBiome(
                QuartPos.fromBlock(x),
                QuartPos.fromBlock(probeY),
                QuartPos.fromBlock(z),
                context.randomState().sampler()))) {
            return Optional.empty();
        }
        if (site.requiresOpenOcean()
                && !EchoSiteSpawnRules.hasOpenOceanClearance(
                        context.chunkGenerator(),
                        context.heightAccessor(),
                        context.randomState(),
                        site,
                        x,
                        z)) {
            return Optional.empty();
        }
        int y;
        if (site.requiresElevatedTerrain()) {
            EchoSiteLandFooting footing = EchoSiteLandFooting.evaluate(
                    context.chunkGenerator(),
                    context.heightAccessor(),
                    context.randomState(),
                    site,
                    x,
                    z);
            if (!footing.acceptable()) {
                return Optional.empty();
            }
            y = footing.anchorY();
        } else if (site.blendsIntoTerrain()
                && !EchoSiteLandFooting.hasGentleRelief(
                        context.chunkGenerator(),
                        context.heightAccessor(),
                        context.randomState(),
                        site,
                        x,
                        z)) {
            return Optional.empty();
        } else {
            y = site.anchorY(
                    context.chunkGenerator(),
                    x,
                    z,
                    context.heightAccessor(),
                    context.randomState());
        }
        int surfaceY = site.surfaceY(
                context.chunkGenerator(),
                x,
                z,
                context.heightAccessor(),
                context.randomState());
        BlockPos pieceAnchor = new BlockPos(x, y, z);
        BlockPos stub = site.underground() ? new BlockPos(x, surfaceY, z) : pieceAnchor;
        return Optional.of(new GenerationStub(
                stub,
                builder -> builder.addPiece(new EchoSitePiece(
                        context.structureTemplateManager(),
                        site,
                        pieceAnchor,
                        surfaceY))));
    }

    @Override
    public StructureType<?> type() {
        return EchoesShowThePast.ECHO_SITE_STRUCTURE_TYPE.get();
    }
}
