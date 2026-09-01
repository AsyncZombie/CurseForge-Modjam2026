package dev.alvar.echoespast.snapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sparse edits made while a Philosopher's Stone materialization is ACTIVE.
 * Authored template memories keep their template id; only divergent cells
 * (including air tombstones) and optional entity replacements are stored.
 */
public final class EchoMemoryRevision {
    private EchoMemoryRevision() {}

    public static boolean hasBlockOverlay(EchoSnapshot source) {
        return source.isTemplateReference()
                && (!source.revisionCells().isEmpty() || !source.blocks().isEmpty());
    }

    public static boolean hasEntityOverlay(EchoSnapshot source) {
        return source.isTemplateReference() && source.entitiesRevised();
    }

    public static Map<Long, OverlayCell> blockOverlay(EchoSnapshot source) {
        if (!hasBlockOverlay(source)) {
            return Map.of();
        }
        Map<Long, OverlayCell> overlay = new HashMap<>(
                Math.max(16, source.revisionCells().size() + source.blocks().size()) * 2);
        for (EchoRevisionCell cell : source.revisionCells()) {
            overlay.put(
                    source.worldPosition(cell).asLong(),
                    new OverlayCell(source.state(cell), cell.blockEntityData()));
        }
        // Legacy compact overlays written before unbounded revision cells.
        for (SnapshotBlock block : source.blocks()) {
            overlay.putIfAbsent(
                    source.worldPosition(block).asLong(),
                    new OverlayCell(source.state(block), block.blockEntityData()));
        }
        return overlay;
    }

    public static void applyBlockOverlay(
            EchoSnapshot source,
            Map<Long, MaterializationMutable> cells) {
        Map<Long, OverlayCell> overlay = blockOverlay(source);
        if (overlay.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, OverlayCell> entry : overlay.entrySet()) {
            OverlayCell cell = entry.getValue();
            if (cell.state().isAir()) {
                BlockPos position = BlockPos.of(entry.getKey());
                cells.put(
                        entry.getKey(),
                        new MaterializationMutable(
                                position,
                                cell.state(),
                                Optional.empty()));
                continue;
            }
            cells.put(
                    entry.getKey(),
                    new MaterializationMutable(
                            BlockPos.of(entry.getKey()),
                            cell.state(),
                            cell.blockEntityData()));
        }
    }

    public static Map<BlockPos, BlockState> applyStateOverlay(
            EchoSnapshot source,
            Map<BlockPos, BlockState> base) {
        Map<Long, OverlayCell> overlay = blockOverlay(source);
        if (overlay.isEmpty()) {
            return base;
        }
        Map<BlockPos, BlockState> revised = new HashMap<>(base);
        for (Map.Entry<Long, OverlayCell> entry : overlay.entrySet()) {
            BlockPos position = BlockPos.of(entry.getKey());
            OverlayCell cell = entry.getValue();
            if (cell.state().isAir()) {
                revised.remove(position);
            } else {
                revised.put(position.immutable(), cell.state());
            }
        }
        return revised;
    }

    public static Map<Long, BlockState> applyPackedStateOverlay(
            EchoSnapshot source,
            Map<Long, BlockState> base) {
        Map<Long, OverlayCell> overlay = blockOverlay(source);
        if (overlay.isEmpty()) {
            return base;
        }
        Map<Long, BlockState> revised = new HashMap<>(base);
        for (Map.Entry<Long, OverlayCell> entry : overlay.entrySet()) {
            OverlayCell cell = entry.getValue();
            if (cell.state().isAir()) {
                revised.remove(entry.getKey());
            } else {
                revised.put(entry.getKey(), cell.state());
            }
        }
        return revised;
    }

    public static BlockState overlayStateOr(
            EchoSnapshot source,
            BlockPos worldPosition,
            BlockState fallback) {
        OverlayCell cell = blockOverlay(source).get(worldPosition.asLong());
        if (cell == null) {
            return fallback;
        }
        return cell.state();
    }

    public static boolean isOverlayAir(EchoSnapshot source, BlockPos worldPosition) {
        OverlayCell cell = blockOverlay(source).get(worldPosition.asLong());
        return cell != null && cell.state().isAir();
    }

    public static List<SnapshotEntity> entitiesForProjection(
            EchoSnapshot source,
            List<SnapshotEntity> templateEntities) {
        return hasEntityOverlay(source) ? source.entities() : templateEntities;
    }

    public static List<EchoRevisionCell> toRevisionCells(
            BlockPos origin,
            Map<Long, OverlayCell> overlay,
            List<BlockState> paletteOut) {
        Map<BlockState, Integer> indices = new HashMap<>();
        List<EchoRevisionCell> cells = new ArrayList<>(overlay.size());
        for (Map.Entry<Long, OverlayCell> entry : overlay.entrySet()) {
            BlockPos world = BlockPos.of(entry.getKey());
            BlockPos offset = world.subtract(origin);
            OverlayCell cell = entry.getValue();
            int paletteIndex = indices.computeIfAbsent(cell.state(), state -> {
                paletteOut.add(state);
                return paletteOut.size() - 1;
            });
            cells.add(EchoRevisionCell.of(
                    offset.getX(),
                    offset.getY(),
                    offset.getZ(),
                    paletteIndex,
                    cell.blockEntityData().orElse(null)));
        }
        return cells;
    }

    public record OverlayCell(
            BlockState state,
            Optional<CompoundTag> blockEntityData) {
    }

    /** Mutable cell used while building a materialization footprint. */
    public record MaterializationMutable(
            BlockPos position,
            BlockState state,
            Optional<CompoundTag> blockEntityData) {
    }
}
