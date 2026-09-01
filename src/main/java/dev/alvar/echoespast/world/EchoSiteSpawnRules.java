package dev.alvar.echoespast.world;

import dev.alvar.echoespast.resonance.EchoSiteType;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Placement filters shared by worldgen and the Resonator.
 *
 * <p>Structure-set spacing only chooses candidate chunks. Open-ocean clearance,
 * stable land footing and the structure's biome list can still reject that chunk.
 * The Resonator must apply the same rules or it will ping empty valleys and
 * shallow shelves that never receive a pedestal.</p>
 */
public final class EchoSiteSpawnRules {
    private EchoSiteSpawnRules() {
    }

    public static boolean wouldGenerate(ServerLevel level, EchoSiteType site, int x, int z) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        if (!biomeAllowed(level, site, x, generator.getSeaLevel() + 8, z)) {
            return false;
        }
        int y;
        if (site.requiresElevatedTerrain()) {
            EchoSiteLandFooting footing = EchoSiteLandFooting.evaluate(
                    generator,
                    level,
                    randomState,
                    site,
                    x,
                    z);
            if (!footing.acceptable()) {
                return false;
            }
            y = footing.anchorY();
        } else if (site.blendsIntoTerrain()
                && !EchoSiteLandFooting.hasGentleRelief(generator, level, randomState, site, x, z)) {
            return false;
        } else {
            y = site.anchorY(generator, x, z, level, randomState);
        }
        int biomeY = site.underground()
                ? site.surfaceY(generator, x, z, level, randomState)
                : y;
        if (!biomeAllowed(level, site, x, biomeY, z)) {
            return false;
        }
        if (site.requiresOpenOcean()
                && !hasOpenOceanClearance(generator, level, randomState, site, x, z)) {
            return false;
        }
        return true;
    }

    public static boolean biomeAllowed(
            ServerLevel level,
            EchoSiteType site,
            int x,
            int y,
            int z) {
        RandomState randomState = level.getChunkSource().randomState();
        Holder<Biome> biome = level.getChunkSource()
                .getGenerator()
                .getBiomeSource()
                .getNoiseBiome(
                        QuartPos.fromBlock(x),
                        QuartPos.fromBlock(y),
                        QuartPos.fromBlock(z),
                        randomState.sampler());
        Structure structure = level.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .getValue(site.structure());
        return structure != null && structure.biomes().contains(biome);
    }

    public static boolean hasOpenOceanClearance(
            ChunkGenerator generator,
            LevelHeightAccessor heightAccessor,
            RandomState randomState,
            EchoSiteType site,
            int centerX,
            int centerZ) {
        int seaLevel = generator.getSeaLevel();
        int minimumX = site.memoryMin().getX() - 16;
        int maximumX = site.memoryMax().getX() + 16;
        int minimumZ = site.memoryMin().getZ() - 16;
        int maximumZ = site.memoryMax().getZ() + 16;
        for (int offsetX = minimumX; offsetX <= maximumX; offsetX += 16) {
            for (int offsetZ = minimumZ; offsetZ <= maximumZ; offsetZ += 16) {
                int x = centerX + offsetX;
                int z = centerZ + offsetZ;
                int surface = generator.getFirstOccupiedHeight(
                        x,
                        z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        heightAccessor,
                        randomState);
                int oceanFloor = generator.getFirstOccupiedHeight(
                        x,
                        z,
                        Heightmap.Types.OCEAN_FLOOR_WG,
                        heightAccessor,
                        randomState);
                if (surface < seaLevel - 1
                        || surface > seaLevel + 2
                        || surface - oceanFloor < 12) {
                    return false;
                }
            }
        }
        return true;
    }

    @Deprecated
    public static boolean hasElevatedTerrain(
            ChunkGenerator generator,
            LevelHeightAccessor heightAccessor,
            RandomState randomState,
            EchoSiteType site,
            int centerX,
            int centerZ,
            int centerSurface) {
        return EchoSiteLandFooting.evaluate(
                generator,
                heightAccessor,
                randomState,
                site,
                centerX,
                centerZ).acceptable();
    }
}
