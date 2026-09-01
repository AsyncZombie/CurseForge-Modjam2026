package dev.alvar.echoespast.world;

import dev.alvar.echoespast.resonance.EchoSiteType;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

/**
 * Seals authored barrels, chests and chest minecarts with the site manifest.
 *
 * <p>Block containers are sealed at worldgen. Chest minecarts live as template
 * entities, so the same offset is matched against {@link BlockPos#containing}
 * of the entity after placement or materialization.</p>
 */
public final class EchoSiteLoot {
    private EchoSiteLoot() {
    }

    public static void assignPresent(
            WorldGenLevel level,
            EchoSiteType site,
            BlockPos templateOrigin,
            BoundingBox writable) {
        for (EchoSiteType.LootPlacement placement : site.presentLoot()) {
            BlockPos position = templateOrigin.offset(placement.offset());
            if (!writable.isInside(position)) {
                continue;
            }
            sealLive(level, position, placement.lootTable());
        }
    }

    public static Optional<CompoundTag> sealPastBlock(
            Optional<CompoundTag> nbt,
            EchoSiteType site,
            BlockPos templateOffset,
            BlockPos worldPos) {
        if (nbt.isEmpty() || site == null) {
            return nbt;
        }
        return matchingPast(site, templateOffset)
                .map(placement -> Optional.of(sealCompound(
                        nbt.orElseThrow(),
                        placement.lootTable(),
                        Mth.getSeed(worldPos))))
                .orElse(nbt);
    }

    public static void sealPastEntity(
            Entity entity,
            EchoSiteType site,
            BlockPos templateOffset,
            BlockPos worldPos) {
        if (site == null) {
            return;
        }
        matchingPast(site, templateOffset).ifPresent(placement ->
                sealContainer(entity, placement.lootTable(), Mth.getSeed(worldPos)));
    }

    private static Optional<EchoSiteType.LootPlacement> matchingPast(
            EchoSiteType site,
            BlockPos templateOffset) {
        return site.pastLoot().stream()
                .filter(placement -> placement.offset().equals(templateOffset))
                .findFirst();
    }

    private static void sealLive(
            WorldGenLevel level,
            BlockPos position,
            ResourceKey<LootTable> table) {
        long seed = Mth.getSeed(position);
        if (level.getBlockEntity(position) instanceof RandomizableContainer container) {
            sealContainer(container, table, seed);
        }
        AABB box = new AABB(position).inflate(0.25);
        for (Entity entity : level.getEntities((Entity) null, box, ignored -> true)) {
            if (BlockPos.containing(entity.position()).equals(position)) {
                sealContainer(entity, table, seed);
            }
        }
    }

    private static void sealContainer(Object candidate, ResourceKey<LootTable> table, long seed) {
        if (!(candidate instanceof RandomizableContainer container)) {
            return;
        }
        if (candidate instanceof Container items && !items.isEmpty()) {
            return;
        }
        container.setLootTable(table, seed);
    }

    private static CompoundTag sealCompound(
            CompoundTag source,
            ResourceKey<LootTable> table,
            long seed) {
        if (!source.getListOrEmpty("Items").isEmpty()) {
            return source;
        }
        CompoundTag nbt = source.copy();
        String id = table.identifier().toString();
        nbt.putString("LootTable", id);
        nbt.putLong("LootTableSeed", seed);
        CompoundTag components = nbt.getCompoundOrEmpty("components").copy();
        CompoundTag loot = new CompoundTag();
        loot.putString("loot_table", id);
        loot.putLong("seed", seed);
        components.put("minecraft:container_loot", loot);
        nbt.put("components", components);
        return nbt;
    }
}
