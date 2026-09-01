package dev.alvar.echoespast.world;

import dev.alvar.echoespast.resonance.EchoSiteType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

public final class EchoSitePlacement {
    public static Optional<ChunkPos> candidateChunk(
            ServerLevel level,
            EchoSiteType site,
            long worldSeed,
            int regionX,
            int regionZ) {
        return placement(level, site).map(placement -> placement.getPotentialStructureChunk(
                worldSeed,
                regionX * placement.spacing(),
                regionZ * placement.spacing()));
    }

    public static boolean isCandidate(
            ServerLevel level,
            EchoSiteType site,
            long seed,
            int chunkX,
            int chunkZ) {
        return placement(level, site).map(placement -> {
            int regionX = Math.floorDiv(chunkX, placement.spacing());
            int regionZ = Math.floorDiv(chunkZ, placement.spacing());
            return placement.getPotentialStructureChunk(
                            seed,
                            regionX * placement.spacing(),
                            regionZ * placement.spacing())
                    .equals(new ChunkPos(chunkX, chunkZ));
        }).orElse(false);
    }

    public static List<ChunkPos> candidatesAround(
            ServerLevel level,
            EchoSiteType site,
            long worldSeed,
            double blockX,
            double blockZ,
            double range) {
        Optional<RandomSpreadStructurePlacement> resolved = placement(level, site);
        if (resolved.isEmpty()) {
            return List.of();
        }
        RandomSpreadStructurePlacement placement = resolved.orElseThrow();
        int minChunkX = (int) Math.floor((blockX - range) / 16.0);
        int maxChunkX = (int) Math.floor((blockX + range) / 16.0);
        int minChunkZ = (int) Math.floor((blockZ - range) / 16.0);
        int maxChunkZ = (int) Math.floor((blockZ + range) / 16.0);
        int minRegionX = Math.floorDiv(minChunkX, placement.spacing());
        int maxRegionX = Math.floorDiv(maxChunkX, placement.spacing());
        int minRegionZ = Math.floorDiv(minChunkZ, placement.spacing());
        int maxRegionZ = Math.floorDiv(maxChunkZ, placement.spacing());
        double rangeSquared = range * range;
        List<ChunkPos> result = new ArrayList<>();
        for (int rz = minRegionZ; rz <= maxRegionZ; rz++) {
            for (int rx = minRegionX; rx <= maxRegionX; rx++) {
                ChunkPos candidate = placement.getPotentialStructureChunk(
                        worldSeed,
                        rx * placement.spacing(),
                        rz * placement.spacing());
                double x = candidate.getMiddleBlockX() + 0.5;
                double z = candidate.getMiddleBlockZ() + 0.5;
                double dx = x - blockX;
                double dz = z - blockZ;
                if (dx * dx + dz * dz <= rangeSquared) {
                    result.add(candidate);
                }
            }
        }
        return List.copyOf(result);
    }

    public static Optional<RandomSpreadStructurePlacement> placement(
            ServerLevel level,
            EchoSiteType site) {
        StructureSet structureSet = level.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE_SET)
                .getValue(site.structureSet());
        if (structureSet == null
                || !(structureSet.placement() instanceof RandomSpreadStructurePlacement placement)) {
            return Optional.empty();
        }
        return Optional.of(placement);
    }

    private EchoSitePlacement() {
    }
}
