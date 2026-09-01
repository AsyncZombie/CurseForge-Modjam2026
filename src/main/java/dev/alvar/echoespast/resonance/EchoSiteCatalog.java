package dev.alvar.echoespast.resonance;

import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.world.EchoSiteStructure;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/** Loads the author-facing echo-site manifest from datapack resources. */
public final class EchoSiteCatalog
        extends SimpleJsonResourceReloadListener<EchoSiteType.Definition> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIRECTORY = "echo_sites";

    public EchoSiteCatalog() {
        super(EchoSiteType.Definition.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(
            Map<Identifier, EchoSiteType.Definition> resources,
            ResourceManager resourceManager,
            ProfilerFiller profiler) {
        Map<Identifier, EchoSiteType.Definition> definitions = new LinkedHashMap<>();
        definitions.putAll(resources);
        EchoSiteType.installDataPackSites(definitions);
        LOGGER.info("Loaded {} echo-site manifests", definitions.size());
    }

    @SubscribeEvent
    public static void addReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(
                        EchoesShowThePast.MOD_ID,
                        "echo_site_catalog"),
                new EchoSiteCatalog());
    }

    @SubscribeEvent
    public static void validateLoadedSites(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var structureSets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        for (EchoSiteType site : EchoSiteType.values()) {
            if (!site.generated()) {
                continue;
            }
            var structure = structures.getValue(site.structure());
            if (!(structure instanceof EchoSiteStructure echoSite)
                    || !echoSite.siteId().equals(site.id())) {
                LOGGER.error(
                        "Echo site {} must reference an echo_site structure with the same site ID; found {}",
                        site.id(),
                        site.structure());
                continue;
            }
            var present = level.getStructureManager().get(site.presentTemplate());
            if (present.isEmpty()) {
                LOGGER.error(
                        "Echo site {} is missing its present template {}",
                        site.id(),
                        site.presentTemplate());
            }
            var past = level.getStructureManager().get(site.intactTemplate());
            if (past.isEmpty()) {
                LOGGER.error(
                        "Echo site {} is missing its past template {}",
                        site.id(),
                        site.intactTemplate());
            }
            if (present.isPresent() && past.isPresent()) {
                validateTemplateAlignment(site, present.orElseThrow().getSize(), past.orElseThrow().getSize());
            }
            StructureSet structureSet = structureSets.getValue(site.structureSet());
            if (structureSet == null) {
                LOGGER.error(
                        "Echo site {} is missing structure set {}",
                        site.id(),
                        site.structureSet());
                continue;
            }
            boolean containsSite = structureSet.structures().stream()
                    .anyMatch(entry -> entry.structure().value() == structure);
            if (!containsSite) {
                LOGGER.error(
                        "Echo site {} is not referenced by structure set {}",
                        site.id(),
                        site.structureSet());
            }
            if (!(structureSet.placement() instanceof RandomSpreadStructurePlacement)) {
                LOGGER.error(
                        "Echo site {} must use random_spread placement so the Resonator can locate it",
                        site.id());
            }
            site.biome().ifPresent(biome -> {
                if (level.registryAccess()
                        .lookupOrThrow(Registries.BIOME)
                        .get(biome)
                        .isEmpty()) {
                    LOGGER.error(
                            "Echo site {} declares the technical biome {}, which no datapack defines",
                            site.id(),
                            biome.identifier());
                }
            });
        }
    }

    private static void validateTemplateAlignment(
            EchoSiteType site,
            Vec3i presentSize,
            Vec3i pastSize) {
        if (!presentSize.equals(pastSize)) {
            LOGGER.error(
                    "Echo site {} uses differently sized present ({}) and past ({}) templates; save both from one identical selection box",
                    site.id(),
                    presentSize,
                    pastSize);
        }
        BlockPos declaredSize = site.memoryMax().subtract(site.memoryMin()).offset(1, 1, 1);
        if (!declaredSize.equals(presentSize)) {
            LOGGER.error(
                    "Echo site {} declares memory bounds {}..{} (size {}) but its template size is {}; set memory_max = memory_min + size - 1",
                    site.id(),
                    site.memoryMin(),
                    site.memoryMax(),
                    declaredSize,
                    presentSize);
        }
    }
}
