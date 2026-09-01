package dev.alvar.echoespast.snapshot;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public final class EchoCapture {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_CAPTURED_ENTITIES = 256;

    public static Optional<EchoSnapshot> capture(ServerLevel level, BlockPos origin, int radius, int blockLimit) {
        int clampedRadius = Math.clamp(radius, 1, 16);
        Map<BlockState, Integer> paletteIndices = new LinkedHashMap<>();
        List<BlockState> palette = new ArrayList<>();
        List<SnapshotBlock> blocks = new ArrayList<>();

        BlockPos min = origin.offset(-clampedRadius, -clampedRadius, -clampedRadius);
        BlockPos max = origin.offset(clampedRadius, clampedRadius, clampedRadius);
        for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
            if (!level.isInWorldBounds(cursor)) {
                continue;
            }
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }
            if (blocks.size() >= blockLimit) {
                return Optional.empty();
            }
            int paletteIndex = paletteIndices.computeIfAbsent(state, key -> {
                palette.add(key);
                return palette.size() - 1;
            });
            blocks.add(SnapshotBlock.of(
                    cursor.getX() - origin.getX(),
                    cursor.getY() - origin.getY(),
                    cursor.getZ() - origin.getZ(),
                    paletteIndex));
        }
        List<SnapshotEntity> entities = captureEntities(level, origin, min, max);

        return Optional.of(new EchoSnapshot(
                EchoSnapshot.CURRENT_VERSION,
                level.dimension(),
                origin,
                clampedRadius,
                false,
                palette,
                blocks,
                entities));
    }

    private static List<SnapshotEntity> captureEntities(
            ServerLevel level,
            BlockPos origin,
            BlockPos minimum,
            BlockPos maximum) {
        List<SnapshotEntity> captured = new ArrayList<>();
        List<Entity> candidates = level.getEntitiesOfClass(
                Entity.class,
                AABB.encapsulatingFullBlocks(minimum, maximum),
                entity -> !(entity instanceof ServerPlayer) && !entity.isPassenger());
        for (Entity entity : candidates) {
            if (captured.size() >= MAX_CAPTURED_ENTITIES) {
                LOGGER.warn("Echo capture at {} reached the {} entity safety limit", origin, MAX_CAPTURED_ENTITIES);
                break;
            }
            SnapshotEntityIO.capture(entity, origin)
                    .ifPresent(captured::add);
        }
        return List.copyOf(captured);
    }

    private EchoCapture() {
    }
}
