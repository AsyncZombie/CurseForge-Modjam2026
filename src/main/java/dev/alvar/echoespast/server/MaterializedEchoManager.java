package dev.alvar.echoespast.server;

import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.block.EchoPedestalBlockEntity;
import dev.alvar.echoespast.network.PhilosophersStoneVisualPayload;
import dev.alvar.echoespast.resonance.EchoSiteType;
import dev.alvar.echoespast.snapshot.EchoMemoryRevision;
import dev.alvar.echoespast.snapshot.EchoRevisionCell;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import dev.alvar.echoespast.snapshot.EchoTemplateResolver;
import dev.alvar.echoespast.snapshot.SnapshotBlock;
import dev.alvar.echoespast.snapshot.SnapshotEntity;
import dev.alvar.echoespast.snapshot.SnapshotEntityIO;
import dev.alvar.echoespast.visual.PhilosophersStoneVisualTiming;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Temporary world replacement is transactional: the complete present is
 * journalled and flushed before the first mutation, then states and block
 * entities are restored before neighbour updates are allowed again.
 */
public final class MaterializedEchoManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Hard cap for one materialization. Sized for Medusa-scale footprints. */
    private static final int MAX_POSITIONS = 262_144;
    private static final int BATCH_SIZE = 256;
    private static final int ENTITY_BATCH_SIZE = 64;
    private static final int MAX_TEMPORAL_ENTITIES = 256;
    private static final String TEMPORAL_SESSION_KEY =
            "echoes_show_the_past:temporal_session";
    private static final int MUTATION_FLAGS = Block.UPDATE_CLIENTS
            | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS
            | Block.UPDATE_SKIP_ON_PLACE;
    private static final Map<UUID, RuntimeSession> SESSIONS = new LinkedHashMap<>();

    public static boolean start(ServerPlayer player) {
        EchoSnapshot projection = EchoProjectionManager.activeSnapshot(player).orElse(null);
        if (projection == null) {
            player.sendOverlayMessage(Component.translatable(
                    "message.echoes_show_the_past.stone_requires_echo"));
            return false;
        }
        // Prefer the Past Echo item memory (template reference / full capture),
        // never the clipped visual projection window used for ghosts.
        EchoSnapshot source = EchoProjectionManager
                .activeSourceSnapshot(player)
                .orElse(projection);
        return start(player, source);
    }

    /**
     * Server-side transaction entry point. The public Stone path obtains the
     * source memory from EchoProjectionManager; keeping the journal engine
     * separate also lets recovery and GameTests exercise it without a client
     * payload. {@code snapshot} is the authored/source memory — not the Past
     * Echo visual clip.
     */
    public static boolean start(ServerPlayer player, EchoSnapshot snapshot) {
        return start(player, snapshot, null);
    }

    /**
     * Starts a pedestal-bound transmutation. The catalyst position is never
     * replaced by remembered air and remains the sole in-world control used
     * to return the Stone and restore the present.
     */
    public static boolean start(
            ServerPlayer player,
            EchoSnapshot snapshot,
            BlockPos catalystPedestal) {
        if (SESSIONS.containsKey(player.getUUID())) {
            return false;
        }
        ServerLevel level = player.level();
        if (!snapshot.dimension().equals(level.dimension())) {
            return false;
        }
        if (catalystPedestal != null
                && SESSIONS.values().stream().anyMatch(session ->
                        session.level == level
                                && session.isCatalystPedestal(catalystPedestal))) {
            return false;
        }

        EchoSnapshot materializationSource = materializationSource(snapshot);
        EchoTemplateResolver.MaterializationFootprint footprint =
                EchoTemplateResolver.resolveForMaterialization(
                        level,
                        materializationSource);
        Map<Long, TargetBlock> targets = targetsFromFootprint(level, footprint);
        if (catalystPedestal != null) {
            targets.remove(catalystPedestal.asLong());
        }
        if (targets.size() > MAX_POSITIONS) {
            LOGGER.warn(
                    "Philosopher's Stone rejected materialization: {} target cells (cap {})",
                    targets.size(),
                    MAX_POSITIONS);
            player.sendOverlayMessage(Component.translatable(
                    "message.echoes_show_the_past.stone_too_large"));
            return false;
        }
        if (targets.isEmpty()
                && footprint.entities().isEmpty()) {
            return false;
        }
        EffectVolume volume = EffectVolume.fromBounds(
                footprint.worldMinimum(),
                footprint.worldMaximum());
        List<PresentEntity> presentEntities =
                capturePresentEntities(
                        level,
                        footprint.origin(),
                        volume);
        if (presentEntities.size()
                        > MAX_TEMPORAL_ENTITIES
                || footprint.entities().size()
                        > MAX_TEMPORAL_ENTITIES) {
            player.sendOverlayMessage(Component.translatable(
                    "message.echoes_show_the_past.stone_too_large"));
            return false;
        }
        List<MaterializedEchoSavedData.PresentBlock> present = new ArrayList<>();
        List<TargetBlock> mutations = new ArrayList<>();
        Set<Long> editablePositions = new HashSet<>();
        for (TargetBlock target : targets.values()) {
            if (!level.hasChunkAt(target.position())) {
                player.sendOverlayMessage(Component.translatable(
                        "message.echoes_show_the_past.stone_chunks_unloaded"));
                return false;
            }
            BlockState current = level.getBlockState(target.position());
            if (isProtected(level, target.position())) {
                player.sendOverlayMessage(Component.translatable(
                        "message.echoes_show_the_past.stone_overlap"));
                return false;
            }
            Optional<CompoundTag> currentNbt =
                    saveBlockEntity(level.getBlockEntity(
                            target.position()));
            present.add(new MaterializedEchoSavedData.PresentBlock(
                    target.position().asLong(),
                    current,
                    currentNbt));
            if (unsafe(current) || unsafe(target.state())) {
                continue;
            }
            editablePositions.add(target.position().asLong());
            boolean sameNbt =
                    currentNbt.equals(target.blockEntityData());
            if (current == target.state() && sameNbt) {
                continue;
            }
            mutations.add(target);
        }
        if (mutations.isEmpty()
                && presentEntities.isEmpty()
                && footprint.entities().isEmpty()) {
            return false;
        }

        Vec3 direction = transitionDirection(player);
        Comparator<BlockPos> sweepOrder = Comparator
                .<BlockPos>comparingDouble(volume::radialCoordinate)
                .thenComparingLong(BlockPos::asLong);
        mutations.sort(Comparator.comparing(
                TargetBlock::position,
                sweepOrder));
        present.sort(Comparator.comparing(
                block -> BlockPos.of(block.position()),
                sweepOrder));
        presentEntities.sort(Comparator
                .comparingDouble(PresentEntity::coordinate)
                .thenComparing(entity ->
                        entity.rootId().toString()));
        List<SnapshotEntity> historicalEntities =
                new ArrayList<>(footprint.entities());
        historicalEntities.sort(Comparator
                .comparingDouble((SnapshotEntity entity) ->
                        volume.radialCoordinate(
                                Vec3.atLowerCornerOf(
                                                footprint.origin())
                                        .add(entity.offset())))
                .thenComparing(entity ->
                        entity.data().toString()));
        int transitionTicks =
                PhilosophersStoneVisualTiming.transitionTicks(
                        mutations.size()
                                + presentEntities.size()
                                + historicalEntities.size());

        MaterializedEchoSavedData saved =
                level.getDataStorage().computeIfAbsent(MaterializedEchoSavedData.TYPE);
        saved.put(new MaterializedEchoSavedData.Journal(
                player.getUUID(),
                present,
                footprint.origin(),
                presentEntities.stream()
                        .map(PresentEntity::snapshot)
                        .toList()));
        // The journal must reach disk before any inventory or state can be replaced.
        level.getDataStorage().saveAndJoin();
        RuntimeSession session = RuntimeSession.applying(
                player.getUUID(),
                level,
                materializationSource,
                materializationSource,
                present,
                List.copyOf(targets.values()),
                mutations,
                editablePositions,
                presentEntities,
                historicalEntities,
                volume,
                direction,
                transitionTicks,
                catalystPedestal);
        SESSIONS.put(player.getUUID(), session);
        sendVisual(session, PhilosophersStoneVisualPayload.MATERIALIZE_PAST);
        return true;
    }

    public static void abort(ServerPlayer player) {
        restoreNow(player.getUUID(), player);
    }

    public static boolean hasSession(
            ServerPlayer player) {
        return SESSIONS.containsKey(
                player.getUUID());
    }

    /**
     * Manual use returns through the same authoritative crest as the natural
     * timeout. A request made during the outgoing crest is retained until
     * materialization finishes, avoiding a half-applied world transaction.
     */
    public static boolean cancel(
            ServerPlayer player) {
        RuntimeSession session =
                SESSIONS.get(player.getUUID());
        return requestCancel(session, player);
    }

    public static boolean hasSessionAtPedestal(
            ServerLevel level,
            BlockPos pedestal) {
        return sessionAtCatalyst(level, pedestal) != null;
    }

    /** Any player may close a shared pedestal memory; damage/edit ownership
     * remains attributed to the player who originally placed the Stone. */
    public static boolean cancelAtPedestal(
            ServerLevel level,
            BlockPos pedestal,
            ServerPlayer actor) {
        return requestCancel(
                sessionAtCatalyst(level, pedestal),
                actor);
    }

    private static boolean requestCancel(
            RuntimeSession session,
            ServerPlayer actor) {
        if (session == null) {
            return false;
        }
        if (session.phase == Phase.APPLYING) {
            session.cancelRequested = true;
            return true;
        }
        if (session.phase == Phase.ACTIVE) {
            beginRestoring(session, actor);
            return true;
        }
        return session.phase == Phase.RESTORING
                || session.phase == Phase.NEIGHBORS;
    }

    private static Map<Long, TargetBlock> targetsFromFootprint(
            ServerLevel level,
            EchoTemplateResolver.MaterializationFootprint footprint) {
        Map<Long, TargetBlock> result = new LinkedHashMap<>(
                Math.max(16, footprint.remembered().size() * 2));
        for (EchoTemplateResolver.MaterializationCell cell : footprint.remembered()) {
            result.put(
                    cell.position().asLong(),
                    new TargetBlock(
                            cell.position().immutable(),
                            cell.state(),
                            cell.blockEntityData()));
            if (result.size() > MAX_POSITIONS) {
                return result;
            }
        }
        if (footprint.authoredTemplate()) {
            return result;
        }
        /*
         * A personal capture owns its complete cubic volume, so absence there
         * really is remembered air. Authored sites returned above use their
         * explicit additions mask instead: absence in a blueprint may be
         * natural terrain, ocean or cave wall and must never be excavated.
         */
        for (BlockPos cursor : BlockPos.betweenClosed(
                footprint.worldMinimum(),
                footprint.worldMaximum())) {
            long key = cursor.asLong();
            if (result.containsKey(key)) {
                continue;
            }
            if (!level.hasChunkAt(cursor)) {
                // Return one unloaded target so the authoritative validation
                // below rejects the whole operation instead of materializing
                // a silently clipped memory.
                result.clear();
                result.put(
                        key,
                        new TargetBlock(
                                cursor.immutable(),
                                Blocks.AIR.defaultBlockState(),
                                Optional.empty()));
                return result;
            }
            BlockState current = level.getBlockState(cursor);
            if (current.isAir() && level.getBlockEntity(cursor) == null) {
                continue;
            }
            result.put(
                    key,
                    new TargetBlock(
                            cursor.immutable(),
                            Blocks.AIR.defaultBlockState(),
                            Optional.empty()));
            if (result.size() > MAX_POSITIONS) {
                break;
            }
        }
        return result;
    }

    /**
     * Prefers an authored template reference (with any Stone revision overlay).
     * Empty site-tagged concrete clips are rehydrated so ocean-floor sites never
     * sparse-fill their AABB; concrete captures that already store blocks are
     * treated as intentional edited branches and left alone.
     */
    private static EchoSnapshot materializationSource(EchoSnapshot snapshot) {
        if (snapshot.isTemplateReference()) {
            return snapshot;
        }
        if (snapshot.site().isEmpty()
                || !snapshot.blocks().isEmpty()
                || snapshot.entitiesRevised()) {
            return snapshot;
        }
        EchoSiteType site = EchoSiteType.byId(snapshot.site().orElseThrow());
        if (site == null) {
            return snapshot;
        }
        return EchoSnapshot.templateReference(
                snapshot.dimension(),
                snapshot.origin(),
                site.intactTemplate(),
                site.memoryMin(),
                site.memoryMax(),
                snapshot.site());
    }

    private static void beginRestoring(
            RuntimeSession session,
            ServerPlayer player) {
        try {
            session.capturePastBranch(player);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Philosopher's Stone failed to persist a past revision; restoring present without revising the Past Echo",
                    exception);
            session.revisedMemory = null;
            session.revisionOwner = null;
        }
        /*
         * Send the revised population before the restoration visual. The
         * client can then materialize ghosts at their moved positions rather
         * than replacing the original capture on the final frame.
         */
        session.synchronizeRevision();
        session.prepareRestorePlan();
        sendVisual(
                session,
                PhilosophersStoneVisualPayload
                        .RESTORE_PRESENT);
        session.phase = Phase.RESTORING;
        session.index = 0;
        session.phaseTick = 0;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int now = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, RuntimeSession>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, RuntimeSession> entry = iterator.next();
            RuntimeSession session = entry.getValue();
            if (session.phase == Phase.APPLYING) {
                session.applyThroughCrest();
                if (session.phaseTick >= session.transitionTicks) {
                    session.finishApplying();
                    if (session.cancelRequested) {
                        beginRestoring(
                                session,
                                event.getServer()
                                        .getPlayerList()
                                        .getPlayer(
                                                session.owner));
                    } else {
                        session.phase = Phase.ACTIVE;
                        session.index = 0;
                        session.phaseTick = 0;
                        session.synchronizeStableViewers();
                    }
                }
            } else if (session.phase == Phase.ACTIVE) {
                session.trackEscapedEntities();
                if ((now & 15) == 0) {
                    session.synchronizeStableViewers();
                }
            } else if (session.phase == Phase.RESTORING) {
                if (session.visualsEnabled) {
                    session.restoreThroughCrest();
                } else {
                    session.restoreBatch();
                }
                if ((!session.visualsEnabled
                                && session.restoreComplete())
                        || (session.visualsEnabled
                                && session.phaseTick
                                        >= session.transitionTicks)) {
                    session.finishRestoring();
                    session.synchronizeRevision();
                    session.phase = Phase.NEIGHBORS;
                    session.index = 0;
                    session.phaseTick = 0;
                }
            } else if (session.phase == Phase.NEIGHBORS) {
                session.neighborBatch();
                if (session.index
                        >= session.restoreTargets.size()) {
                    finish(session);
                    iterator.remove();
                }
            }
        }
    }

    public static boolean isProtected(ServerLevel level, BlockPos position) {
        for (RuntimeSession session : SESSIONS.values()) {
            if (session.level == level && session.protectedPositions.contains(position.asLong())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPastEditable(
            ServerLevel level,
            BlockPos position) {
        RuntimeSession session = sessionAt(level, position);
        return session != null
                && session.phase == Phase.ACTIVE
                && session.editablePositions.contains(
                        position.asLong())
                && !unsafe(level.getBlockState(position));
    }

    private static boolean isInteractionLocked(
            ServerLevel level,
            BlockPos position) {
        RuntimeSession session = sessionAt(level, position);
        return session != null
                && !session.isCatalystPedestal(position)
                && !isPastEditable(level, position);
    }

    private static boolean isMutationLocked(
            ServerLevel level,
            BlockPos position) {
        return isProtected(level, position)
                && !isPastEditable(level, position);
    }

    public static boolean deferBlockTick(ServerLevel level, BlockPos position, Block block) {
        RuntimeSession session = sessionAt(level, position);
        if (session == null) {
            return false;
        }
        session.deferredBlocks.put(position.asLong(), block);
        return true;
    }

    public static boolean deferFluidTick(ServerLevel level, BlockPos position, Fluid fluid) {
        RuntimeSession session = sessionAt(level, position);
        if (session == null) {
            return false;
        }
        session.deferredFluids.put(position.asLong(), fluid);
        return true;
    }

    private static RuntimeSession sessionAt(ServerLevel level, BlockPos position) {
        for (RuntimeSession session : SESSIONS.values()) {
            if (session.level == level && session.protectedPositions.contains(position.asLong())) {
                return session;
            }
        }
        return null;
    }

    private static RuntimeSession sessionAtCatalyst(
            ServerLevel level,
            BlockPos position) {
        for (RuntimeSession session : SESSIONS.values()) {
            if (session.level == level
                    && session.isCatalystPedestal(position)) {
                return session;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level
                && isInteractionLocked(level, event.getPos())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level
                && isMutationLocked(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && isMutationLocked(level, event.getPos())) {
            event.setNotifyClient(true);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && isMutationLocked(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (isMutationLocked(
                event.getLevel(),
                event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFluid(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && (isMutationLocked(
                                level,
                                event.getPos())
                        || isMutationLocked(
                                level,
                                event.getLiquidPos()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        var resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return;
        }
        if (isProtected(level, event.getPos())
                || resolver.getToPush().stream().anyMatch(pos -> isProtected(level, pos))
                || resolver.getToDestroy().stream().anyMatch(pos -> isProtected(level, pos))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().removeIf(pos -> isProtected(level, pos));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreNow(player.getUUID(), player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreNow(player.getUUID(), player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreNow(player.getUUID(), player);
            sendClearVisual(player);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos unloading = event.getChunk().getPos();
        List<UUID> affected = new ArrayList<>();
        for (RuntimeSession session : SESSIONS.values()) {
            if (session.level != level) {
                continue;
            }
            boolean intersects = session.protectedPositions.stream()
                    .map(BlockPos::of)
                    .anyMatch(position -> new ChunkPos(
                                    position.getX() >> 4,
                                    position.getZ() >> 4)
                            .equals(unloading));
            if (intersects) {
                affected.add(session.owner);
            }
        }
        affected.forEach(owner ->
                restoreNow(
                        owner,
                        level.getServer()
                                .getPlayerList()
                                .getPlayer(owner)));
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            MaterializedEchoSavedData saved =
                    level.getDataStorage().computeIfAbsent(MaterializedEchoSavedData.TYPE);
            for (MaterializedEchoSavedData.Journal journal : saved.journals()) {
                RuntimeSession recovered = RuntimeSession.restoring(
                        journal.owner(),
                        level,
                        journal.present(),
                        journal.origin(),
                        journal.presentEntities());
                SESSIONS.put(journal.owner(), recovered);
            }
        }
    }

    /**
     * A temporal entity which survived a crash may load long after its block
     * journal was recovered. Rejecting an orphan marker here closes that
     * delayed duplication path without touching ordinary entities.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || event.getEntity() instanceof Player) {
            return;
        }
        Optional<UUID> owner =
                temporalOwner(event.getEntity());
        if (owner.isEmpty()) {
            return;
        }
        RuntimeSession session =
                SESSIONS.get(owner.orElseThrow());
        if (session == null || session.level != level) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityTravel(
            EntityTravelToDimensionEvent event) {
        Optional<UUID> owner =
                temporalOwner(event.getEntity());
        if (owner.isEmpty()) {
            return;
        }
        RuntimeSession session =
                SESSIONS.get(owner.orElseThrow());
        if (session != null) {
            session.releaseHistorical(
                    event.getEntity());
        }
    }

    private static void restoreNow(UUID owner) {
        restoreNow(owner, null);
    }

    private static void restoreNow(
            UUID owner,
            ServerPlayer player) {
        RuntimeSession session = SESSIONS.remove(owner);
        if (session == null) {
            return;
        }
        if (session.phase == Phase.ACTIVE) {
            session.capturePastBranch(player);
        }
        session.prepareRestorePlan();
        session.synchronizeRevision();
        if (session.visualsEnabled && session.phase != Phase.RESTORING) {
            sendVisual(
                    session,
                    PhilosophersStoneVisualPayload.RESTORE_PRESENT);
        }
        for (MaterializedEchoSavedData.PresentBlock present :
                session.restoreTargets) {
            restore(session.level, present);
        }
        session.restoreEntitiesNow();
        for (MaterializedEchoSavedData.PresentBlock present :
                session.restoreTargets) {
            BlockPos position = BlockPos.of(present.position());
            session.level.updateNeighborsAt(position, present.state().getBlock());
        }
        finish(session);
    }

    private static void finish(RuntimeSession session) {
        for (Map.Entry<Long, Block> entry : session.deferredBlocks.entrySet()) {
            session.level.scheduleTick(BlockPos.of(entry.getKey()), entry.getValue(), 1);
        }
        for (Map.Entry<Long, Fluid> entry : session.deferredFluids.entrySet()) {
            session.level.scheduleTick(BlockPos.of(entry.getKey()), entry.getValue(), 1);
        }
        MaterializedEchoSavedData saved =
                session.level.getDataStorage().computeIfAbsent(MaterializedEchoSavedData.TYPE);
        saved.remove(session.owner);
    }

    private static Vec3 transitionDirection(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 1.0E-5) {
            double angle = (player.getUUID().hashCode() & 2047)
                    * Math.PI
                    * 2.0
                    / 2048.0;
            horizontal = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
        } else {
            horizontal = horizontal.normalize();
        }
        return new Vec3(horizontal.x, 0.18, horizontal.z).normalize();
    }

    private static void sendVisual(RuntimeSession session, int phase) {
        if (!session.visualsEnabled) {
            return;
        }
        Vec3 center = session.volume.center();
        double range = Math.max(
                64.0,
                session.volume.halfExtents().length() + 48.0);
        PhilosophersStoneVisualPayload payload =
                new PhilosophersStoneVisualPayload(
                        center,
                        session.volume.halfExtents(),
                        session.direction,
                        phase,
                        session.transitionTicks);
        double rangeSquared = range * range;
        for (ServerPlayer viewer : session.level.players()) {
            boolean previouslyTracking =
                    session.visualViewers.contains(viewer.getUUID());
            if ((viewer.distanceToSqr(center) <= rangeSquared
                            || (phase == PhilosophersStoneVisualPayload.RESTORE_PRESENT
                                    && previouslyTracking))
                    && viewer.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(viewer, payload);
                session.visualViewers.add(viewer.getUUID());
            }
        }
    }

    private static void sendStableVisual(
            RuntimeSession session,
            ServerPlayer viewer) {
        PhilosophersStoneVisualPayload payload =
                new PhilosophersStoneVisualPayload(
                        session.volume.center(),
                        session.volume.halfExtents(),
                        session.direction,
                        PhilosophersStoneVisualPayload.STABLE_PAST,
                        1);
        if (viewer.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(viewer, payload);
            session.visualViewers.add(viewer.getUUID());
        }
    }

    private static void sendClearVisual(ServerPlayer viewer) {
        PhilosophersStoneVisualPayload payload =
                new PhilosophersStoneVisualPayload(
                        Vec3.ZERO,
                        new Vec3(0.5, 0.5, 0.5),
                        new Vec3(0.62, 0.18, 0.76),
                        PhilosophersStoneVisualPayload.CLEAR,
                        1);
        if (viewer.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    private static List<PresentEntity> capturePresentEntities(
            ServerLevel level,
            BlockPos origin,
            EffectVolume volume) {
        List<PresentEntity> result = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity.isPassenger()
                    || entity instanceof Player
                    || entity.getSelfAndPassengers()
                            .anyMatch(Player.class::isInstance)
                    || !volume.contains(entity.position())
                    || temporalOwner(entity).isPresent()) {
                continue;
            }
            SnapshotEntityIO.capture(entity, origin)
                    .ifPresent(snapshot -> result.add(
                            new PresentEntity(
                                    entity.getUUID(),
                                    snapshot,
                                    volume.radialCoordinate(
                                            entity.position()))));
        }
        return result;
    }

    private static List<Entity> rootsInside(
            ServerLevel level,
            EffectVolume volume) {
        List<Entity> roots = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!entity.isPassenger()
                    && !(entity instanceof Player)
                    && entity.getSelfAndPassengers()
                            .noneMatch(Player.class::isInstance)
                    && volume.contains(entity.position())) {
                roots.add(entity);
            }
        }
        roots.sort(Comparator.comparing(
                entity -> entity.getUUID().toString()));
        return roots;
    }

    private static Optional<SnapshotEntity> captureTemporalEntity(
            Entity root,
            BlockPos origin) {
        Map<Entity, String> markers = new LinkedHashMap<>();
        root.getSelfAndPassengers().forEach(entity ->
                entity.getPersistentData()
                        .getString(TEMPORAL_SESSION_KEY)
                        .ifPresent(marker -> {
                            markers.put(entity, marker);
                            entity.getPersistentData()
                                    .remove(TEMPORAL_SESSION_KEY);
                        }));
        try {
            return SnapshotEntityIO.capture(root, origin);
        } finally {
            markers.forEach((entity, marker) ->
                    entity.getPersistentData()
                            .putString(
                                    TEMPORAL_SESSION_KEY,
                                    marker));
        }
    }

    private static Optional<Entity> spawnEntity(
            ServerLevel level,
            BlockPos origin,
            SnapshotEntity snapshot,
            UUID temporalOwner) {
        Optional<Entity> loaded = SnapshotEntityIO.load(
                snapshot,
                level,
                origin,
                temporalOwner != null);
        if (loaded.isEmpty()) {
            return Optional.empty();
        }
        Entity root = loaded.orElseThrow();
        if (temporalOwner != null) {
            markTemporal(root, temporalOwner);
        }
        if (!level.tryAddFreshEntityWithPassengers(root)) {
            return Optional.empty();
        }
        return Optional.of(root);
    }

    private static void markTemporal(
            Entity root,
            UUID owner) {
        String marker = owner.toString();
        root.getSelfAndPassengers().forEach(entity ->
                entity.getPersistentData().putString(
                        TEMPORAL_SESSION_KEY,
                        marker));
    }

    private static void clearTemporal(Entity root) {
        root.getSelfAndPassengers().forEach(entity ->
                entity.getPersistentData().remove(
                        TEMPORAL_SESSION_KEY));
    }

    public static boolean isTemporalEntity(Entity entity) {
        return temporalOwner(entity).isPresent();
    }

    private static Optional<UUID> temporalOwner(Entity entity) {
        return entity.getPersistentData()
                .getString(TEMPORAL_SESSION_KEY)
                .flatMap(value -> {
                    try {
                        return Optional.of(
                                UUID.fromString(value));
                    } catch (IllegalArgumentException ignored) {
                        return Optional.empty();
                    }
                });
    }

    private static void discardHierarchy(Entity root) {
        List<Entity> hierarchy =
                root.getSelfAndPassengers().toList();
        for (int index = hierarchy.size() - 1;
                index >= 0;
                index--) {
            hierarchy.get(index).discard();
        }
    }

    private static Entity findEntity(
            ServerLevel level,
            UUID entityId) {
        return level.getEntityInAnyDimension(entityId);
    }

    private static List<TemporalEntity> markedEntities(
            ServerLevel level,
            UUID owner,
            BlockPos origin) {
        List<TemporalEntity> marked = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!entity.isPassenger()
                    && temporalOwner(entity)
                            .filter(owner::equals)
                            .isPresent()) {
                marked.add(new TemporalEntity(
                        entity.getUUID(),
                        entity.position()
                                .distanceTo(
                                        Vec3.atLowerCornerOf(
                                                origin))));
            }
        }
        return marked;
    }

    private static Optional<CompoundTag> saveBlockEntity(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return Optional.empty();
        }
        try (ProblemReporter.ScopedCollector reporter =
                new ProblemReporter.ScopedCollector(blockEntity.problemPath(), LOGGER)) {
            TagValueOutput output =
                    TagValueOutput.createWithContext(reporter, blockEntity.getLevel().registryAccess());
            blockEntity.saveWithId(output);
            return Optional.of(output.buildResult());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not journal block entity at {}", blockEntity.getBlockPos(), exception);
            return Optional.empty();
        }
    }

    private static void apply(ServerLevel level, TargetBlock target) {
        level.setBlock(target.position(), target.state(), MUTATION_FLAGS);
        loadBlockEntity(level, target.position(), target.blockEntityData());
    }

    private static void restore(ServerLevel level, MaterializedEchoSavedData.PresentBlock present) {
        BlockPos position = BlockPos.of(present.position());
        level.setBlock(position, present.state(), MUTATION_FLAGS);
        loadBlockEntity(level, position, present.blockEntityData());
    }

    private static void loadBlockEntity(
            ServerLevel level,
            BlockPos position,
            Optional<CompoundTag> blockEntityData) {
        if (blockEntityData.isEmpty()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity == null) {
            return;
        }
        try (ProblemReporter.ScopedCollector reporter =
                new ProblemReporter.ScopedCollector(blockEntity.problemPath(), LOGGER)) {
            blockEntity.loadWithComponents(TagValueInput.create(
                    reporter,
                    level.registryAccess(),
                    blockEntityData.orElseThrow().copy()));
            blockEntity.setChanged();
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not restore block entity at {}", position, exception);
        }
    }

    private static boolean unsafe(BlockState state) {
        return state.getBlock() instanceof CommandBlock
                || state.is(Blocks.JIGSAW)
                || state.is(Blocks.STRUCTURE_BLOCK)
                || state.is(Blocks.SPAWNER)
                || state.is(Blocks.NETHER_PORTAL)
                || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_GATEWAY);
    }

    private record TargetBlock(
            BlockPos position,
            BlockState state,
            Optional<CompoundTag> blockEntityData) {
        private TargetBlock {
            position = position.immutable();
            blockEntityData = blockEntityData.map(CompoundTag::copy);
        }
    }

    private record PresentEntity(
            UUID rootId,
            SnapshotEntity snapshot,
            double coordinate) {
    }

    private record TemporalEntity(
            UUID rootId,
            double coordinate) {
    }

    private record EffectVolume(Vec3 center, Vec3 halfExtents) {
        private double radialCoordinate(BlockPos position) {
            return PhilosophersStoneVisualTiming.normalizedCoordinate(
                    position.getCenter(),
                    center,
                    halfExtents);
        }

        private double radialCoordinate(Vec3 position) {
            return PhilosophersStoneVisualTiming
                    .normalizedCoordinate(
                            position,
                            center,
                            halfExtents);
        }

        private boolean contains(Vec3 position) {
            Vec3 relative = position.subtract(center);
            return Math.abs(relative.x)
                            <= halfExtents.x
                    && Math.abs(relative.y)
                            <= halfExtents.y
                    && Math.abs(relative.z)
                            <= halfExtents.z;
        }

        private static EffectVolume fromBounds(BlockPos minimum, BlockPos maximum) {
            return new EffectVolume(
                    new Vec3(
                            (minimum.getX() + maximum.getX() + 1.0) * 0.5,
                            (minimum.getY() + maximum.getY() + 1.0) * 0.5,
                            (minimum.getZ() + maximum.getZ() + 1.0) * 0.5),
                    new Vec3(
                            Math.max(0.5, (maximum.getX() - minimum.getX() + 1.0) * 0.5),
                            Math.max(0.5, (maximum.getY() - minimum.getY() + 1.0) * 0.5),
                            Math.max(0.5, (maximum.getZ() - minimum.getZ() + 1.0) * 0.5)));
        }

        private static EffectVolume from(Collection<TargetBlock> targets) {
            if (targets.isEmpty()) {
                return new EffectVolume(
                        Vec3.ZERO,
                        new Vec3(0.5, 0.5, 0.5));
            }
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (TargetBlock target : targets) {
                BlockPos position = target.position();
                minX = Math.min(minX, position.getX());
                minY = Math.min(minY, position.getY());
                minZ = Math.min(minZ, position.getZ());
                maxX = Math.max(maxX, position.getX());
                maxY = Math.max(maxY, position.getY());
                maxZ = Math.max(maxZ, position.getZ());
            }
            return new EffectVolume(
                    new Vec3(
                            (minX + maxX + 1.0) * 0.5,
                            (minY + maxY + 1.0) * 0.5,
                            (minZ + maxZ + 1.0) * 0.5),
                    new Vec3(
                            Math.max(0.5, (maxX - minX + 1.0) * 0.5),
                            Math.max(0.5, (maxY - minY + 1.0) * 0.5),
                            Math.max(0.5, (maxZ - minZ + 1.0) * 0.5)));
        }
    }

    private enum Phase {
        APPLYING,
        ACTIVE,
        RESTORING,
        NEIGHBORS
    }

    private static final class RuntimeSession {
        private final UUID owner;
        private final ServerLevel level;
        private final EchoSnapshot sourceMemory;
        private final EchoSnapshot materializedMemory;
        private final BlockPos memoryOrigin;
        private final List<MaterializedEchoSavedData.PresentBlock> present;
        private final List<PresentEntity> presentEntities;
        private final List<SnapshotEntity> historicalEntities;
        private final List<TargetBlock> memoryVolume;
        private final List<TargetBlock> targets;
        private final Set<Long> editablePositions;
        private final Set<Long> protectedPositions;
        private final EffectVolume volume;
        private final Vec3 direction;
        private final BlockPos catalystPedestal;
        private int transitionTicks;
        private final boolean visualsEnabled;
        private final Set<UUID> visualViewers = new HashSet<>();
        private final Map<Long, Block> deferredBlocks = new HashMap<>();
        private final Map<Long, Fluid> deferredFluids = new HashMap<>();
        private final Set<UUID> historicalRootIds =
                new HashSet<>();
        private Phase phase;
        private List<MaterializedEchoSavedData.PresentBlock>
                restoreTargets = List.of();
        private List<TemporalEntity> pastRestoreEntities =
                List.of();
        private EchoSnapshot revisedMemory;
        private ServerPlayer revisionOwner;
        private int index;
        private int presentApplyIndex;
        private int historicalApplyIndex;
        private int pastRestoreIndex;
        private int presentRestoreIndex;
        private int phaseTick;
        private boolean cancelRequested;

        private RuntimeSession(
                UUID owner,
                ServerLevel level,
                EchoSnapshot sourceMemory,
                EchoSnapshot materializedMemory,
                BlockPos memoryOrigin,
                List<MaterializedEchoSavedData.PresentBlock> present,
                List<TargetBlock> memoryVolume,
                List<TargetBlock> targets,
                Set<Long> editablePositions,
                List<PresentEntity> presentEntities,
                List<SnapshotEntity> historicalEntities,
                Phase phase,
                EffectVolume volume,
                Vec3 direction,
                int transitionTicks,
                boolean visualsEnabled,
                BlockPos catalystPedestal) {
            this.owner = owner;
            this.level = level;
            this.sourceMemory = sourceMemory;
            this.materializedMemory = materializedMemory;
            this.memoryOrigin = memoryOrigin.immutable();
            this.present = List.copyOf(present);
            this.presentEntities =
                    List.copyOf(presentEntities);
            this.historicalEntities =
                    List.copyOf(historicalEntities);
            this.memoryVolume = List.copyOf(memoryVolume);
            this.targets = List.copyOf(targets);
            this.editablePositions =
                    Set.copyOf(editablePositions);
            this.phase = phase;
            this.volume = volume;
            this.direction = direction;
            this.catalystPedestal = catalystPedestal == null
                    ? null
                    : catalystPedestal.immutable();
            this.transitionTicks = transitionTicks;
            this.visualsEnabled = visualsEnabled;
            this.protectedPositions = new HashSet<>();
            if (memoryVolume.isEmpty()) {
                present.forEach(block ->
                        protectedPositions.add(block.position()));
            } else {
                memoryVolume.forEach(block ->
                        protectedPositions.add(
                                block.position().asLong()));
            }
            if (this.catalystPedestal != null) {
                protectedPositions.add(
                        this.catalystPedestal.asLong());
            }
        }

        private boolean isCatalystPedestal(BlockPos position) {
            return catalystPedestal != null
                    && catalystPedestal.equals(position);
        }

        private void synchronizeStableViewers() {
            if (!visualsEnabled || phase != Phase.ACTIVE) {
                return;
            }
            visualViewers.removeIf(viewerId -> {
                ServerPlayer viewer = level.getServer()
                        .getPlayerList()
                        .getPlayer(viewerId);
                return viewer == null || viewer.level() != level;
            });
            double range = Math.max(
                    64.0,
                    volume.halfExtents().length() + 48.0);
            double rangeSquared = range * range;
            for (ServerPlayer viewer : level.players()) {
                if (!visualViewers.contains(viewer.getUUID())
                        && viewer.distanceToSqr(volume.center()) <= rangeSquared) {
                    sendStableVisual(this, viewer);
                }
            }
        }

        private static RuntimeSession applying(
                UUID owner,
                ServerLevel level,
                EchoSnapshot sourceMemory,
                EchoSnapshot materializedMemory,
                List<MaterializedEchoSavedData.PresentBlock> present,
                List<TargetBlock> memoryVolume,
                List<TargetBlock> targets,
                Set<Long> editablePositions,
                List<PresentEntity> presentEntities,
                List<SnapshotEntity> historicalEntities,
                EffectVolume volume,
                Vec3 direction,
                int transitionTicks,
                BlockPos catalystPedestal) {
            return new RuntimeSession(
                    owner,
                    level,
                    sourceMemory,
                    materializedMemory,
                    materializedMemory.origin(),
                    present,
                    memoryVolume,
                    targets,
                    editablePositions,
                    presentEntities,
                    historicalEntities,
                    Phase.APPLYING,
                    volume,
                    direction,
                    transitionTicks,
                    true,
                    catalystPedestal);
        }

        private static RuntimeSession restoring(
                UUID owner,
                ServerLevel level,
                List<MaterializedEchoSavedData.PresentBlock> present,
                BlockPos origin,
                List<SnapshotEntity> presentEntitySnapshots) {
            List<TargetBlock> restoredTargets = present.stream()
                    .map(block -> new TargetBlock(
                            BlockPos.of(block.position()),
                            block.state(),
                            block.blockEntityData()))
                    .toList();
            RuntimeSession session = new RuntimeSession(
                    owner,
                    level,
                    null,
                    null,
                    origin,
                    present,
                    List.of(),
                    List.of(),
                    Set.of(),
                    presentEntitySnapshots.stream()
                            .map(snapshot -> new PresentEntity(
                                    snapshot.data()
                                            .read(
                                                    Entity.TAG_UUID,
                                                    net.minecraft.core.UUIDUtil.CODEC)
                                            .orElse(UUID.randomUUID()),
                                    snapshot,
                                    0.0))
                            .toList(),
                    List.of(),
                    Phase.RESTORING,
                    EffectVolume.from(restoredTargets),
                    new Vec3(0.62, 0.18, 0.76).normalize(),
                    PhilosophersStoneVisualTiming.transitionTicks(present.size()),
                    false,
                    null);
            session.restoreTargets = session.present;
            session.pastRestoreEntities =
                    markedEntities(level, owner, origin);
            return session;
        }

        private void capturePastBranch(ServerPlayer player) {
            if (materializedMemory == null
                    || memoryVolume.isEmpty()) {
                return;
            }
            if (sourceMemory != null && sourceMemory.isTemplateReference()) {
                captureAuthoredPastBranch(player);
                return;
            }
            Map<BlockState, Integer> paletteIndices =
                    new LinkedHashMap<>();
            List<BlockState> palette = new ArrayList<>();
            List<SnapshotBlock> blocks = new ArrayList<>();
            boolean blockChanged = false;
            for (TargetBlock remembered : memoryVolume) {
                BlockState state = remembered.state();
                Optional<CompoundTag> blockEntityData =
                        remembered.blockEntityData();
                if (editablePositions.contains(
                        remembered.position().asLong())) {
                    state = level.getBlockState(
                            remembered.position());
                    blockEntityData = saveBlockEntity(
                            level.getBlockEntity(
                                    remembered.position()));
                    if (state != remembered.state()
                            || !blockEntityData.equals(
                                    remembered.blockEntityData())) {
                        blockChanged = true;
                    }
                }
                if (state.isAir()) {
                    continue;
                }
                BlockState capturedState = state;
                int paletteIndex = paletteIndices.computeIfAbsent(
                        capturedState,
                        ignored -> {
                            palette.add(capturedState);
                            return palette.size() - 1;
                        });
                BlockPos offset = remembered.position()
                        .subtract(materializedMemory.origin());
                blocks.add(SnapshotBlock.of(
                        offset.getX(),
                        offset.getY(),
                        offset.getZ(),
                        paletteIndex,
                        blockEntityData.orElse(null)));
            }
            List<SnapshotEntity> revisedEntities =
                    capturePastPopulation();
            boolean entityChanged = !revisedEntities.equals(
                    historicalEntities);
            if (!blockChanged && !entityChanged) {
                return;
            }

            revisedMemory = new EchoSnapshot(
                    EchoSnapshot.CURRENT_VERSION,
                    materializedMemory.dimension(),
                    materializedMemory.origin(),
                    materializedMemory.radius(),
                    materializedMemory.sealed(),
                    palette,
                    blocks,
                    revisedEntities,
                    Optional.empty(),
                    materializedMemory.boundsMin(),
                    materializedMemory.boundsMax(),
                    materializedMemory.site(),
                    false);
            revisionOwner = player;
            if (player != null) {
                EchoProjectionManager.reviseMemory(
                        player,
                        sourceMemory,
                        revisedMemory,
                        false);
            }
        }

        /**
         * Keeps the authored template on the Past Echo item and stores only the
         * sparse Stone edits (including air tombstones) so the next projection
         * and materialization show the revised past without PHIL-00 truncation.
         */
        private void captureAuthoredPastBranch(ServerPlayer player) {
            Map<Long, EchoMemoryRevision.OverlayCell> overlay =
                    new LinkedHashMap<>(
                            EchoMemoryRevision.blockOverlay(sourceMemory));
            boolean blockChanged = false;
            for (TargetBlock remembered : memoryVolume) {
                BlockState state = remembered.state();
                Optional<CompoundTag> blockEntityData =
                        remembered.blockEntityData();
                if (editablePositions.contains(
                        remembered.position().asLong())) {
                    state = level.getBlockState(
                            remembered.position());
                    blockEntityData = saveBlockEntity(
                            level.getBlockEntity(
                                    remembered.position()));
                }
                if (state == remembered.state()
                        && blockEntityData.equals(
                                remembered.blockEntityData())) {
                    continue;
                }
                blockChanged = true;
                overlay.put(
                        remembered.position().asLong(),
                        new EchoMemoryRevision.OverlayCell(
                                state,
                                blockEntityData));
            }
            List<SnapshotEntity> revisedEntities =
                    capturePastPopulation();
            boolean entityChanged = !revisedEntities.equals(
                    historicalEntities);
            if (!blockChanged && !entityChanged) {
                return;
            }
            List<BlockState> palette = new ArrayList<>();
            List<EchoRevisionCell> revisionCells =
                    EchoMemoryRevision.toRevisionCells(
                            materializedMemory.origin(),
                            overlay,
                            palette);
            boolean entitiesRevised =
                    sourceMemory.entitiesRevised() || entityChanged;
            List<SnapshotEntity> entities = entitiesRevised
                    ? revisedEntities
                    : sourceMemory.entities();
            revisedMemory = sourceMemory.withRevision(
                    palette,
                    revisionCells,
                    entities,
                    entitiesRevised);
            revisionOwner = player;
            if (player != null) {
                EchoProjectionManager.reviseMemory(
                        player,
                        sourceMemory,
                        revisedMemory,
                        false);
            }
        }

        private void prepareRestorePlan() {
            List<MaterializedEchoSavedData.PresentBlock> changed =
                    new ArrayList<>();
            for (MaterializedEchoSavedData.PresentBlock original :
                    present) {
                BlockPos position = BlockPos.of(
                        original.position());
                BlockState current = level.getBlockState(position);
                Optional<CompoundTag> currentNbt =
                        saveBlockEntity(
                                level.getBlockEntity(position));
                if (current != original.state()
                        || !currentNbt.equals(
                                original.blockEntityData())) {
                    changed.add(original);
                }
            }
            restoreTargets = List.copyOf(changed);
            transitionTicks =
                    PhilosophersStoneVisualTiming.transitionTicks(
                            restoreTargets.size()
                                    + pastRestoreEntities.size()
                                    + presentEntities.size());
        }

        private List<SnapshotEntity> capturePastPopulation() {
            List<Entity> inside =
                    rootsInside(level, volume);
            List<PresentEntity> captured =
                    new ArrayList<>();
            Set<UUID> capturedRoots =
                    new HashSet<>();
            for (Entity root : inside) {
                if (captured.size()
                        >= MAX_TEMPORAL_ENTITIES) {
                    clearTemporal(root);
                    continue;
                }
                Optional<UUID> currentOwner =
                        temporalOwner(root);
                if (currentOwner.isPresent()
                        && !currentOwner
                                .orElseThrow()
                                .equals(owner)) {
                    continue;
                }
                if (currentOwner.isEmpty()) {
                    markTemporal(root, owner);
                }
                historicalRootIds.add(
                        root.getUUID());
                Optional<SnapshotEntity> snapshot =
                        captureTemporalEntity(
                                root,
                                memoryOrigin);
                if (snapshot.isEmpty()) {
                    clearTemporal(root);
                    continue;
                }
                double coordinate =
                        volume.radialCoordinate(
                                root.position());
                captured.add(new PresentEntity(
                        root.getUUID(),
                        snapshot.orElseThrow(),
                        coordinate));
                capturedRoots.add(root.getUUID());
            }
            captured.sort(Comparator
                    .comparingDouble(PresentEntity::coordinate)
                    .thenComparing(entity ->
                            entity.rootId().toString()));
            pastRestoreEntities = captured.stream()
                    .map(entity -> new TemporalEntity(
                            entity.rootId(),
                            entity.coordinate()))
                    .toList();

            Iterator<UUID> historical =
                    historicalRootIds.iterator();
            while (historical.hasNext()) {
                UUID rootId = historical.next();
                Entity entity = findEntity(level, rootId);
                if (entity == null) {
                    historical.remove();
                    continue;
                }
                Entity root = entity.getRootVehicle();
                if (!capturedRoots.contains(
                        root.getUUID())) {
                    clearTemporal(root);
                    historical.remove();
                }
            }
            return captured.stream()
                    .map(PresentEntity::snapshot)
                    .toList();
        }

        private void trackEscapedEntities() {
            for (Entity root :
                    rootsInside(level, volume)) {
                Optional<UUID> currentOwner =
                        temporalOwner(root);
                if (currentOwner.isPresent()
                        && !currentOwner
                                .orElseThrow()
                                .equals(owner)) {
                    continue;
                }
                if (currentOwner.isEmpty()) {
                    markTemporal(root, owner);
                }
                historicalRootIds.add(
                        root.getUUID());
            }
            Iterator<UUID> iterator =
                    historicalRootIds.iterator();
            while (iterator.hasNext()) {
                UUID rootId = iterator.next();
                Entity entity = findEntity(level, rootId);
                if (entity == null) {
                    iterator.remove();
                    continue;
                }
                Entity root = entity.getRootVehicle();
                if (!volume.contains(root.position())) {
                    clearTemporal(root);
                    iterator.remove();
                }
            }
        }

        private void releaseHistorical(Entity entity) {
            Entity root = entity.getRootVehicle();
            clearTemporal(root);
            historicalRootIds.remove(root.getUUID());
            historicalRootIds.remove(entity.getUUID());
        }

        private void synchronizeRevision() {
            if (revisedMemory == null) {
                return;
            }
            if (catalystPedestal != null
                    && level.getBlockEntity(catalystPedestal)
                            instanceof EchoPedestalBlockEntity pedestal
                    && pedestal.hasEcho()) {
                ItemStack revisedFragment = pedestal.echo().copyWithCount(1);
                revisedFragment.set(
                        dev.alvar.echoespast.EchoesShowThePast.ECHO_SNAPSHOT.get(),
                        revisedMemory);
                pedestal.setEcho(revisedFragment);
            }
            ServerPlayer player = revisionOwner;
            if (player == null) {
                player = level.getServer()
                        .getPlayerList()
                        .getPlayer(owner);
            }
            if (player != null) {
                EchoProjectionManager.synchronizeRevision(
                        player,
                        revisedMemory);
            }
            revisedMemory = null;
            revisionOwner = null;
        }

        private void applyThroughCrest() {
            phaseTick++;
            float progress = Math.clamp(
                    (float) phaseTick
                            / Math.max(1, transitionTicks),
                    0.0F,
                    1.0F);
            int applied = 0;
            while (index < targets.size()
                    && applied < BATCH_SIZE) {
                TargetBlock target = targets.get(index);
                float coordinate = (float) volume.radialCoordinate(
                        target.position());
                if (!PhilosophersStoneVisualTiming.shouldMutate(
                        coordinate,
                        progress,
                        false)) {
                    break;
                }
                apply(level, target);
                index++;
                applied++;
            }
            applyEntitySwaps(progress);
        }

        private void finishApplying() {
            while (index < targets.size()) {
                apply(level, targets.get(index++));
            }
            while (presentApplyIndex
                    < presentEntities.size()) {
                removePresentEntity(
                        presentEntities.get(
                                presentApplyIndex++));
            }
            while (historicalApplyIndex
                    < historicalEntities.size()) {
                spawnHistoricalEntity(
                        historicalEntities.get(
                                historicalApplyIndex++));
            }
        }

        private void restoreThroughCrest() {
            phaseTick++;
            float progress = Math.clamp(
                    (float) phaseTick
                            / Math.max(1, transitionTicks),
                    0.0F,
                    1.0F);
            int restored = 0;
            while (index < restoreTargets.size()
                    && restored < BATCH_SIZE) {
                MaterializedEchoSavedData.PresentBlock target =
                        restoreTargets.get(
                                restoreTargets.size() - 1 - index);
                float coordinate = (float) volume.radialCoordinate(
                        BlockPos.of(target.position()));
                if (!PhilosophersStoneVisualTiming.shouldMutate(
                        coordinate,
                        progress,
                        true)) {
                    break;
                }
                restore(level, target);
                index++;
                restored++;
            }
            restoreEntitySwaps(progress);
        }

        private void restoreBatch() {
            int end = Math.min(
                    index + BATCH_SIZE,
                    restoreTargets.size());
            while (index < end) {
                restore(
                        level,
                        restoreTargets.get(
                                restoreTargets.size()
                                        - 1
                                        - index++));
            }
            int entityEnd = Math.min(
                    pastRestoreIndex + ENTITY_BATCH_SIZE,
                    pastRestoreEntities.size());
            while (pastRestoreIndex < entityEnd) {
                discardPastEntity(
                        pastRestoreEntities.get(
                                pastRestoreEntities.size()
                                        - 1
                                        - pastRestoreIndex++));
            }
            int presentEnd = Math.min(
                    presentRestoreIndex + ENTITY_BATCH_SIZE,
                    presentEntities.size());
            while (presentRestoreIndex < presentEnd) {
                restorePresentEntity(
                        presentEntities.get(
                                presentEntities.size()
                                        - 1
                                        - presentRestoreIndex++));
            }
        }

        private void finishRestoring() {
            while (index < restoreTargets.size()) {
                restore(
                        level,
                        restoreTargets.get(
                                restoreTargets.size()
                                        - 1
                                        - index++));
            }
            while (pastRestoreIndex
                    < pastRestoreEntities.size()) {
                discardPastEntity(
                        pastRestoreEntities.get(
                                pastRestoreEntities.size()
                                        - 1
                                        - pastRestoreIndex++));
            }
            while (presentRestoreIndex
                    < presentEntities.size()) {
                restorePresentEntity(
                        presentEntities.get(
                                presentEntities.size()
                                        - 1
                                        - presentRestoreIndex++));
            }
        }

        private void applyEntitySwaps(float progress) {
            int changed = 0;
            while (presentApplyIndex
                            < presentEntities.size()
                    && changed < ENTITY_BATCH_SIZE) {
                PresentEntity entity =
                        presentEntities.get(
                                presentApplyIndex);
                if (!PhilosophersStoneVisualTiming
                        .shouldMutate(
                                (float) entity.coordinate(),
                                progress,
                                false)) {
                    break;
                }
                removePresentEntity(entity);
                presentApplyIndex++;
                changed++;
            }
            while (historicalApplyIndex
                            < historicalEntities.size()
                    && changed < ENTITY_BATCH_SIZE) {
                SnapshotEntity entity =
                        historicalEntities.get(
                                historicalApplyIndex);
                float coordinate = (float) volume
                        .radialCoordinate(
                                Vec3.atLowerCornerOf(
                                                memoryOrigin)
                                        .add(entity.offset()));
                if (!PhilosophersStoneVisualTiming
                        .shouldMutate(
                                coordinate,
                                progress,
                                false)) {
                    break;
                }
                spawnHistoricalEntity(entity);
                historicalApplyIndex++;
                changed++;
            }
        }

        private void restoreEntitySwaps(float progress) {
            int changed = 0;
            while (pastRestoreIndex
                            < pastRestoreEntities.size()
                    && changed < ENTITY_BATCH_SIZE) {
                TemporalEntity entity =
                        pastRestoreEntities.get(
                                pastRestoreEntities.size()
                                        - 1
                                        - pastRestoreIndex);
                if (!PhilosophersStoneVisualTiming
                        .shouldMutate(
                                (float) entity.coordinate(),
                                progress,
                                true)) {
                    break;
                }
                discardPastEntity(entity);
                pastRestoreIndex++;
                changed++;
            }
            while (presentRestoreIndex
                            < presentEntities.size()
                    && changed < ENTITY_BATCH_SIZE) {
                PresentEntity entity =
                        presentEntities.get(
                                presentEntities.size()
                                        - 1
                                        - presentRestoreIndex);
                if (!PhilosophersStoneVisualTiming
                        .shouldMutate(
                                (float) entity.coordinate(),
                                progress,
                                true)) {
                    break;
                }
                restorePresentEntity(entity);
                presentRestoreIndex++;
                changed++;
            }
        }

        private void removePresentEntity(
                PresentEntity remembered) {
            Entity current =
                    findEntity(level, remembered.rootId());
            if (current != null) {
                discardHierarchy(
                        current.getRootVehicle());
            }
        }

        private void spawnHistoricalEntity(
                SnapshotEntity remembered) {
            spawnEntity(
                            level,
                            memoryOrigin,
                            remembered,
                            owner)
                    .ifPresent(root ->
                            historicalRootIds.add(
                                    root.getUUID()));
        }

        private void discardPastEntity(
                TemporalEntity remembered) {
            Entity current =
                    findEntity(level, remembered.rootId());
            if (current != null) {
                discardHierarchy(
                        current.getRootVehicle());
            }
            historicalRootIds.remove(
                    remembered.rootId());
        }

        private void restorePresentEntity(
                PresentEntity remembered) {
            spawnEntity(
                    level,
                    memoryOrigin,
                    remembered.snapshot(),
                    null);
        }

        private boolean restoreComplete() {
            return index >= restoreTargets.size()
                    && pastRestoreIndex
                            >= pastRestoreEntities.size()
                    && presentRestoreIndex
                            >= presentEntities.size();
        }

        private void restoreEntitiesNow() {
            while (pastRestoreIndex
                    < pastRestoreEntities.size()) {
                discardPastEntity(
                        pastRestoreEntities.get(
                                pastRestoreEntities.size()
                                        - 1
                                        - pastRestoreIndex++));
            }
            while (presentRestoreIndex
                    < presentEntities.size()) {
                restorePresentEntity(
                        presentEntities.get(
                                presentEntities.size()
                                        - 1
                                        - presentRestoreIndex++));
            }
        }

        private void neighborBatch() {
            int end = Math.min(
                    index + BATCH_SIZE,
                    restoreTargets.size());
            while (index < end) {
                MaterializedEchoSavedData.PresentBlock restored =
                        restoreTargets.get(index++);
                level.updateNeighborsAt(BlockPos.of(restored.position()), restored.state().getBlock());
            }
        }
    }

    private MaterializedEchoManager() {
    }
}
