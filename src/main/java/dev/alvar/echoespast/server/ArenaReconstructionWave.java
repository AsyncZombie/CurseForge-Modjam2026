package dev.alvar.echoespast.server;

import dev.alvar.echoespast.mixin.server.StructureTemplateAccessor;
import dev.alvar.echoespast.network.PhilosophersStoneVisualPayload;
import dev.alvar.echoespast.network.PhilosophersStoneVisualProgressPayload;
import dev.alvar.echoespast.visual.PhilosophersStoneVisualTiming;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Pedestal-driven arena rebuild that reuses the Philosopher's Stone crest clock
 * and post-effect without opening a Past Echo materialization session.
 */
public final class ArenaReconstructionWave {
    private static final int MUTATION_FLAGS = Block.UPDATE_CLIENTS
            | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS
            | Block.UPDATE_SKIP_ON_PLACE;
    private static final int SPEED_MULTIPLIER = 3;
    private static final int VERTICAL_RESCUE_RANGE = 24;
    private static final int LATERAL_RESCUE_RANGE = 10;
    private static final int LATERAL_STEP_UP_RANGE = 8;
    private static final Vec3 DEFAULT_DIRECTION = new Vec3(0.62, 0.18, 0.76).normalize();

    private static Wave active;
    private static DropCleanup dropCleanup;

    private ArenaReconstructionWave() {
    }

    public static boolean isBusy() {
        return active != null;
    }

    /**
     * Moves players intersecting newly reconstructed arena blocks to safety.
     * Kept public so the collision contract can be exercised by GameTests.
     */
    public static int rescueCollidingPlayers(ServerLevel level, AABB bounds) {
        int rescued = 0;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive()
                    || player.isSpectator()
                    || !player.getBoundingBox().intersects(bounds)
                    || level.noCollision(player)) {
                continue;
            }
            rescued += rescueCollidingEntity(level, player, bounds) ? 1 : 0;
        }
        return rescued;
    }

    /**
     * Applies the same minimal upward/lateral correction to the boss as to the
     * player. This avoids the old end-of-wave snap back to a pedestal.
     */
    public static boolean rescueCollidingEntity(
            ServerLevel level,
            LivingEntity entity,
            AABB bounds) {
        if (!entity.isAlive()
                || !entity.getBoundingBox().intersects(bounds)
                || level.noCollision(entity)) {
            return false;
        }
        Vec3 destination = findSafePosition(level, entity, bounds);
        if (destination == null) {
            return false;
        }
        entity.stopRiding();
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(destination.x, destination.y, destination.z);
        } else {
            entity.setPos(destination.x, destination.y, destination.z);
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
        return true;
    }

    private static int rescueCollidingLivingEntities(ServerLevel level, AABB bounds) {
        int rescued = 0;
        for (LivingEntity entity : level.getEntitiesOfClass(
                LivingEntity.class,
                bounds,
                candidate -> !(candidate instanceof ServerPlayer player) || !player.isSpectator())) {
            rescued += rescueCollidingEntity(level, entity, bounds) ? 1 : 0;
        }
        return rescued;
    }

    private static Vec3 findSafePosition(
            ServerLevel level,
            LivingEntity entity,
            AABB bounds) {
        BlockPos originalFeet = BlockPos.containing(
                entity.getX(),
                entity.getBoundingBox().minY + 0.01D,
                entity.getZ());

        // Going straight up is the least disorienting correction during the
        // crest. It also places the player on top of the block that appeared.
        for (int up = 1; up <= VERTICAL_RESCUE_RANGE; up++) {
            Vec3 candidate = new Vec3(
                    entity.getX(),
                    originalFeet.getY() + up,
                    entity.getZ());
            if (canStandAt(level, entity, candidate, bounds)) {
                return candidate;
            }
        }

        // If a roof or column blocks the vertical route, search compact
        // Manhattan rings so the player moves only as far sideways as needed.
        for (int radius = 1; radius <= LATERAL_RESCUE_RANGE; radius++) {
            for (int up = 0; up <= LATERAL_STEP_UP_RANGE; up++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int dz = radius - Math.abs(dx);
                    Vec3 positive = blockCenterAt(originalFeet, dx, up, dz);
                    if (canStandAt(level, entity, positive, bounds)) {
                        return positive;
                    }
                    if (dz != 0) {
                        Vec3 negative = blockCenterAt(originalFeet, dx, up, -dz);
                        if (canStandAt(level, entity, negative, bounds)) {
                            return negative;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Vec3 blockCenterAt(
            BlockPos origin,
            int offsetX,
            int offsetY,
            int offsetZ) {
        return new Vec3(
                origin.getX() + offsetX + 0.5D,
                origin.getY() + offsetY,
                origin.getZ() + offsetZ + 0.5D);
    }

    private static boolean canStandAt(
            ServerLevel level,
            LivingEntity entity,
            Vec3 candidate,
            AABB bounds) {
        Vec3 displacement = candidate.subtract(entity.position());
        AABB destination = entity.getBoundingBox().move(displacement);
        if (destination.minX < bounds.minX
                || destination.minY < bounds.minY
                || destination.minZ < bounds.minZ
                || destination.maxX > bounds.maxX
                || destination.maxY > bounds.maxY
                || destination.maxZ > bounds.maxZ
                || !level.noCollision(entity, destination)) {
            return false;
        }
        BlockPos support = BlockPos.containing(
                candidate.x,
                destination.minY - 0.08D,
                candidate.z);
        return level.isInWorldBounds(support)
                && level.getBlockState(support).isFaceSturdy(level, support, Direction.UP);
    }

    public static void start(
            ServerLevel level,
            Vec3 center,
            Vec3 halfExtents,
            List<PlannedBlock> blocks,
            boolean restoring,
            Runnable onComplete) {
        if (blocks.isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        List<PlannedBlock> sorted = new ArrayList<>(blocks);
        sorted.sort((left, right) -> PhilosophersStoneVisualTiming.compareMutationOrder(
                PhilosophersStoneVisualTiming.normalizedCoordinate(
                        Vec3.atCenterOf(left.pos()), center, halfExtents),
                PhilosophersStoneVisualTiming.normalizedCoordinate(
                        Vec3.atCenterOf(right.pos()), center, halfExtents),
                restoring));
        int duration = PhilosophersStoneVisualTiming.transitionTicks(
                sorted.size(),
                SPEED_MULTIPLIER);
        int phase = restoring
                ? PhilosophersStoneVisualPayload.RESTORE_PRESENT
                : PhilosophersStoneVisualPayload.WAVE_ONLY;
        PhilosophersStoneVisualPayload payload = new PhilosophersStoneVisualPayload(
                center,
                halfExtents,
                DEFAULT_DIRECTION,
                phase,
                duration);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        active = new Wave(level, center, halfExtents, sorted, restoring, duration, onComplete);
    }

    /**
     * Symmetric visual reach required for a crest emitted from {@code center}
     * to enclose every block cell in the authored volume. The pedestal is not
     * the geometric centre of the Greek arena, so using the raw arena size
     * makes the shader finish at a different time from the block mutation.
     * The extra half block converts the outermost centre into a cell boundary,
     * preserving the shader's block lattice around a block-centred pedestal.
     */
    public static Vec3 volumeHalfExtents(
            BlockPos origin,
            Vec3i size,
            Vec3 center) {
        Vec3 minimum = Vec3.atCenterOf(origin);
        Vec3 maximum = Vec3.atCenterOf(origin.offset(
                Math.max(0, size.getX() - 1),
                Math.max(0, size.getY() - 1),
                Math.max(0, size.getZ() - 1)));
        return new Vec3(
                Math.max(Math.abs(minimum.x - center.x), Math.abs(maximum.x - center.x)) + 0.5,
                Math.max(Math.abs(minimum.y - center.y), Math.abs(maximum.y - center.y)) + 0.5,
                Math.max(Math.abs(minimum.z - center.z), Math.abs(maximum.z - center.z)) + 0.5);
    }

    public static void collectGreekPast(BlockPos center, int floorY, Consumer<PlannedBlock> out) {
        GreekArenaLayout.collectPast(center, floorY, out);
    }

    public static void collectGreekRuins(BlockPos center, int floorY, Consumer<PlannedBlock> out) {
        GreekArenaLayout.collectRuins(center, floorY, out);
    }

    public static void collectGreekVolumeAir(
            BlockPos center,
            int floorY,
            int height,
            Consumer<PlannedBlock> out) {
        // height kept for call-site compatibility; layout owns the clear box.
        GreekArenaLayout.collectVolumeAir(center, floorY, out);
    }

    public static void collectClearToAir(
            BlockPos origin,
            int size,
            int height,
            int floorY,
            BlockState floor,
            Consumer<PlannedBlock> out) {
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                for (int y = 0; y < height; y++) {
                    out.accept(new PlannedBlock(
                            new BlockPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z),
                            air));
                }
                out.accept(new PlannedBlock(
                        new BlockPos(origin.getX() + x, floorY, origin.getZ() + z),
                        floor));
            }
        }
    }

    /**
     * Builds a sparse, block-state delta from the live arena to an authored
     * structure. Missing template cells mean air. A final vanilla template
     * placement should follow the wave so block-entity NBT is restored too.
     */
    public static boolean collectTemplateDelta(
            ServerLevel level,
            Identifier templateId,
            BlockPos origin,
            Set<BlockPos> preservedWorldPositions,
            Consumer<PlannedBlock> out) {
        var template = level.getStructureManager().get(templateId);
        if (template.isEmpty()) {
            return false;
        }
        List<StructureTemplate.Palette> palettes =
                ((StructureTemplateAccessor) (Object) template.get()).echoes$getPalettes();
        if (palettes.isEmpty()) {
            return false;
        }
        Map<Long, BlockState> target = new HashMap<>();
        for (StructureTemplate.StructureBlockInfo block : palettes.getFirst().blocks()) {
            // Data-mode structure blocks are authoring anchors, never visible
            // arena geometry. The final vanilla placement has processors that
            // turn Medieval markers into air, but the progressive wave runs
            // first and previously exposed the impact marker for several
            // ticks. Filter every authoring marker at the delta source too.
            target.put(
                    block.pos().asLong(),
                    visibleTargetState(block.state()));
        }
        Vec3i size = template.get().getSize();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos local = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos world = new BlockPos.MutableBlockPos();
        for (int y = 0; y < size.getY(); y++) {
            for (int x = 0; x < size.getX(); x++) {
                for (int z = 0; z < size.getZ(); z++) {
                    local.set(x, y, z);
                    world.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    BlockPos immutableWorld = world.immutable();
                    if (preservedWorldPositions.contains(immutableWorld)) {
                        continue;
                    }
                    BlockState desired = target.getOrDefault(local.asLong(), air);
                    if (!level.getBlockState(world).equals(desired)) {
                        out.accept(new PlannedBlock(immutableWorld, desired));
                    }
                }
            }
        }
        return true;
    }

    /** State the progressive wave may expose to players. */
    public static BlockState visibleTargetState(BlockState authored) {
        return authored.is(Blocks.STRUCTURE_BLOCK)
                ? Blocks.AIR.defaultBlockState()
                : authored;
    }

    public static void cancel() {
        active = null;
        dropCleanup = null;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (dropCleanup != null) {
            dropCleanup.tick();
            if (dropCleanup.finished()) {
                dropCleanup = null;
            }
        }
        if (active == null) {
            return;
        }
        active.tick();
        if (active.finished) {
            Wave done = active;
            active = null;
            done.cleanupDrops();
            if (done.onComplete != null) {
                done.onComplete.run();
            }
            done.cleanupDrops();
            dropCleanup = new DropCleanup(
                    done.level,
                    done.cleanupBounds,
                    done.preservedItemIds,
                    10);
        }
    }

    public record PlannedBlock(BlockPos pos, BlockState state) {
    }

    private static final class Wave {
        private final ServerLevel level;
        private final Vec3 center;
        private final Vec3 halfExtents;
        private final List<PlannedBlock> blocks;
        private final boolean restoring;
        private final int durationTicks;
        private final Runnable onComplete;
        private final AABB cleanupBounds;
        private final Set<UUID> preservedItemIds;
        private int tick;
        private int cursor;
        private boolean finished;

        private Wave(
                ServerLevel level,
                Vec3 center,
                Vec3 halfExtents,
                List<PlannedBlock> blocks,
                boolean restoring,
                int durationTicks,
                Runnable onComplete) {
            this.level = level;
            this.center = center;
            this.halfExtents = halfExtents;
            this.blocks = blocks;
            this.restoring = restoring;
            this.durationTicks = durationTicks;
            this.onComplete = onComplete;
            this.cleanupBounds = new AABB(
                    center.x - halfExtents.x - 1.0D,
                    center.y - halfExtents.y - 1.0D,
                    center.z - halfExtents.z - 1.0D,
                    center.x + halfExtents.x + 1.0D,
                    center.y + halfExtents.y + 1.0D,
                    center.z + halfExtents.z + 1.0D);
            this.preservedItemIds = new HashSet<>();
            level.getEntitiesOfClass(ItemEntity.class, cleanupBounds)
                    .forEach(item -> preservedItemIds.add(item.getUUID()));
        }

        private void tick() {
            int candidateTick = Math.min(tick + 1, durationTicks);
            float candidateProgress = PhilosophersStoneVisualTiming.progress(
                    candidateTick,
                    durationTicks);
            int applied = 0;
            int mutationBudget = PhilosophersStoneVisualTiming.mutationsPerTick(
                    SPEED_MULTIPLIER);
            while (cursor < blocks.size() && applied < mutationBudget) {
                PlannedBlock next = blocks.get(cursor);
                float coordinate = PhilosophersStoneVisualTiming.normalizedCoordinate(
                        Vec3.atCenterOf(next.pos()),
                        center,
                        halfExtents);
                if (!PhilosophersStoneVisualTiming.shouldMutate(
                        coordinate,
                        candidateProgress,
                        restoring)) {
                    break;
                }
                // Removing the old block entity first prevents container contents from
                // being scattered before the visual wave can replace the block.
                if (level.getBlockEntity(next.pos()) != null) {
                    level.removeBlockEntity(next.pos());
                }
                level.setBlock(next.pos(), next.state(), MUTATION_FLAGS);
                cursor++;
                applied++;
            }
            if (applied > 0) {
                // Block placement and rescue happen in the same server tick, so
                // a crest can never leave a player enclosed until the next tick.
                rescueCollidingLivingEntities(level, cleanupBounds);
            }
            boolean readyBacklog = false;
            if (cursor < blocks.size()) {
                PlannedBlock next = blocks.get(cursor);
                float nextCoordinate = PhilosophersStoneVisualTiming.normalizedCoordinate(
                        Vec3.atCenterOf(next.pos()),
                        center,
                        halfExtents);
                readyBacklog = PhilosophersStoneVisualTiming.shouldMutate(
                        nextCoordinate,
                        candidateProgress,
                        restoring);
            }
            tick = PhilosophersStoneVisualTiming.advanceServerClock(
                    tick,
                    durationTicks,
                    readyBacklog);
            sendProgress(PhilosophersStoneVisualTiming.progress(tick, durationTicks));
            cleanupDrops();
            if (cursor >= blocks.size() && tick >= durationTicks) {
                finished = true;
            }
        }

        private void cleanupDrops() {
            level.getEntitiesOfClass(
                            ItemEntity.class,
                            cleanupBounds,
                            item -> !preservedItemIds.contains(item.getUUID()))
                    .forEach(ItemEntity::discard);
        }

        private void sendProgress(float progress) {
            PhilosophersStoneVisualProgressPayload payload =
                    new PhilosophersStoneVisualProgressPayload(progress);
            for (ServerPlayer player : level.players()) {
                if (player.connection.hasChannel(payload)) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }

    /** Catches neighbor-update drops that materialize a few ticks after the last mutation. */
    private static final class DropCleanup {
        private final ServerLevel level;
        private final AABB bounds;
        private final Set<UUID> preservedItemIds;
        private int remainingTicks;

        private DropCleanup(
                ServerLevel level,
                AABB bounds,
                Set<UUID> preservedItemIds,
                int remainingTicks) {
            this.level = level;
            this.bounds = bounds;
            this.preservedItemIds = Set.copyOf(preservedItemIds);
            this.remainingTicks = remainingTicks;
        }

        private void tick() {
            level.getEntitiesOfClass(
                            ItemEntity.class,
                            bounds,
                            item -> !preservedItemIds.contains(item.getUUID()))
                    .forEach(ItemEntity::discard);
            remainingTicks--;
        }

        private boolean finished() {
            return remainingTicks <= 0;
        }
    }
}
