package dev.alvar.echoespast.world;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.resonance.EchoSiteType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Builds and unlocks the authored Unknown crypt entrance.
 *
 * <p>The crypt itself owns the doorway through an {@code entry} data marker.
 * The generated shaft is therefore allowed to change without ever baking a
 * coordinate from the current template into Java.</p>
 *
 * <p>The chamber also authors four wall openings as barrier carve masks. Those
 * become {@code crypt_seal} until the Harmonic Key answers, so the well's 3×3
 * door is not a hole you can walk around.</p>
 */
public final class CryptAccessGate {
    public static final String ENTRY_MARKER = "entry";
    public static final String LOCKED_MESSAGE = "message.echoes_show_the_past.crypt_locked";
    private static final int SHAFT_DISTANCE = 4;
    private static final int CORRIDOR_HEIGHT = 3;
    private static final String HINT_TICK_TAG = "EchoesCryptLockedHintTick";
    private static final int HINT_COOLDOWN_TICKS = 20;

    private CryptAccessGate() {
    }

    public static List<BlockPos> entryMarkers(
            StructureTemplate template,
            BlockPos templateOrigin,
            StructurePlaceSettings settings) {
        List<BlockPos> entries = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo marker : template.filterBlocks(
                templateOrigin,
                settings,
                Blocks.STRUCTURE_BLOCK)) {
            CompoundTag data = marker.nbt();
            if (data != null && ENTRY_MARKER.equals(data.getStringOr("metadata", ""))) {
                entries.add(marker.pos().immutable());
            }
        }
        return List.copyOf(entries);
    }

    public static Direction outwardDirection(BlockPos anchor, BlockPos entry) {
        int dx = entry.getX() - anchor.getX();
        int dz = entry.getZ() - anchor.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx < 0 ? Direction.WEST : Direction.EAST;
        }
        return dz < 0 ? Direction.NORTH : Direction.SOUTH;
    }

    /** The complete, three-wide by three-high collision seal. */
    public static List<BlockPos> gateCells(BlockPos anchor, BlockPos entry) {
        Direction outward = outwardDirection(anchor, entry);
        Direction across = outward.getClockWise();
        List<BlockPos> result = new ArrayList<>(9);
        for (int y = 0; y < CORRIDOR_HEIGHT; y++) {
            for (int lateral = -1; lateral <= 1; lateral++) {
                result.add(entry.relative(across, lateral).above(y).immutable());
            }
        }
        return List.copyOf(result);
    }

    /**
     * Every authored opening on the four vertical hull faces. Barriers there
     * are the real crypt doors; the well's 3×3 is only the lowest slice of the
     * north window.
     */
    public static List<BlockPos> hullSealCells(
            StructureTemplate template,
            BlockPos templateOrigin,
            StructurePlaceSettings settings) {
        Set<BlockPos> result = new LinkedHashSet<>();
        int sizeX = template.getSize().getX();
        int sizeZ = template.getSize().getZ();
        for (StructureTemplate.StructureBlockInfo barrier : template.filterBlocks(
                templateOrigin,
                settings,
                Blocks.BARRIER)) {
            BlockPos local = barrier.pos().subtract(templateOrigin);
            if (local.getX() == 0
                    || local.getX() == sizeX - 1
                    || local.getZ() == 0
                    || local.getZ() == sizeZ - 1) {
                result.add(barrier.pos().immutable());
            }
        }
        for (BlockPos entry : entryMarkers(template, templateOrigin, settings)) {
            BlockPos local = entry.subtract(templateOrigin);
            if (local.getX() == 0
                    || local.getX() == sizeX - 1
                    || local.getZ() == 0
                    || local.getZ() == sizeZ - 1) {
                result.add(entry.immutable());
            }
        }
        return List.copyOf(result);
    }

    /** Walkable centre of each of the four hull openings. */
    public static List<BlockPos> doorSentinels(EchoSiteType site, BlockPos anchor) {
        BoundingBox box = site.memoryBounds(anchor);
        int midX = Math.floorDiv(box.minX() + box.maxX(), 2);
        int midZ = Math.floorDiv(box.minZ() + box.maxZ(), 2);
        int doorY = box.minY() + 2;
        return List.of(
                new BlockPos(midX, doorY, box.minZ()),
                new BlockPos(midX, doorY, box.maxZ()),
                new BlockPos(box.minX(), doorY, midZ),
                new BlockPos(box.maxX(), doorY, midZ));
    }

    public static boolean isSealed(ServerLevel level, EchoSiteType site, BlockPos anchor) {
        boolean anyLoaded = false;
        for (BlockPos door : doorSentinels(site, anchor)) {
            if (!level.hasChunkAt(door)) {
                continue;
            }
            anyLoaded = true;
            if (level.getBlockState(door).is(EchoesShowThePast.CRYPT_SEAL.get())) {
                return true;
            }
        }
        return !anyLoaded;
    }

    public static BoundingBox accessBounds(BlockPos anchor, BlockPos entry, int surfaceY) {
        Direction outward = outwardDirection(anchor, entry);
        Direction across = outward.getClockWise();
        BlockPos shaft = entry.relative(outward, SHAFT_DISTANCE);
        int minX = Math.min(entry.relative(across, -2).getX(), shaft.offset(-1, 0, -1).getX());
        int minZ = Math.min(entry.relative(across, -2).getZ(), shaft.offset(-1, 0, -1).getZ());
        int maxX = Math.max(entry.relative(across, 2).getX(), shaft.offset(1, 0, 1).getX());
        int maxZ = Math.max(entry.relative(across, 2).getZ(), shaft.offset(1, 0, 1).getZ());
        return new BoundingBox(
                minX,
                entry.getY() - 1,
                minZ,
                maxX,
                Math.max(entry.getY() + CORRIDOR_HEIGHT, surfaceY + 1),
                maxZ);
    }

    /** Compact surface wellhead, used to paint biome and clear trees without a full shaft column. */
    public static BoundingBox surfaceRimBounds(BlockPos anchor, BlockPos entry, int surfaceY) {
        Direction outward = outwardDirection(anchor, entry);
        Direction across = outward.getClockWise();
        BlockPos shaft = entry.relative(outward, SHAFT_DISTANCE);
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int forward = -1; forward <= 1; forward++) {
            for (int lateral = -1; lateral <= 1; lateral++) {
                BlockPos cell = shaft.relative(outward, forward).relative(across, lateral);
                minX = Math.min(minX, cell.getX());
                minZ = Math.min(minZ, cell.getZ());
                maxX = Math.max(maxX, cell.getX());
                maxZ = Math.max(maxZ, cell.getZ());
            }
        }
        return new BoundingBox(minX, surfaceY - 1, minZ, maxX, surfaceY + 2, maxZ);
    }

    /** Called once per intersecting structure chunk; every write respects its writable box. */
    public static void build(
            ServerLevelAccessor level,
            BlockPos anchor,
            BlockPos entry,
            int surfaceY,
            BoundingBox writable) {
        Direction outward = outwardDirection(anchor, entry);
        Direction inward = outward.getOpposite();
        Direction across = outward.getClockWise();
        BlockPos shaft = entry.relative(outward, SHAFT_DISTANCE);

        // A short dressed tunnel connects the authored doorway to the shaft.
        for (int distance = 1; distance < SHAFT_DISTANCE; distance++) {
            BlockPos center = entry.relative(outward, distance);
            for (int y = -1; y <= CORRIDOR_HEIGHT; y++) {
                for (int lateral = -2; lateral <= 2; lateral++) {
                    BlockPos cell = center.relative(across, lateral).above(y);
                    boolean shell = y == -1
                            || y == CORRIDOR_HEIGHT
                            || Math.abs(lateral) == 2;
                    set(level, writable, cell, shell
                            ? masonry(cell)
                            : Blocks.AIR.defaultBlockState());
                }
            }
        }

        // Three-by-three lined shaft. The wall towards the tunnel is opened at
        // its foot and the opposite wall supports the ladder.
        for (int y = entry.getY(); y < surfaceY; y++) {
            for (int forward = -1; forward <= 1; forward++) {
                for (int lateral = -1; lateral <= 1; lateral++) {
                    BlockPos cell = shaft.relative(outward, forward)
                            .relative(across, lateral)
                            .atY(y);
                    boolean center = forward == 0 && lateral == 0;
                    boolean lowerTunnelOpening = forward == -1
                            && lateral == 0
                            && y < entry.getY() + CORRIDOR_HEIGHT;
                    if (center || lowerTunnelOpening) {
                        set(level, writable, cell, Blocks.AIR.defaultBlockState());
                    } else {
                        set(level, writable, cell, masonry(cell));
                    }
                }
            }
            set(level, writable, shaft.atY(y), Blocks.LADDER.defaultBlockState()
                    .setValue(LadderBlock.FACING, inward));
        }
        set(level, writable, shaft.below().atY(entry.getY() - 1),
                Blocks.CHISELED_DEEPSLATE.defaultBlockState());

        // Low surface rim: recognisable as an old sealed well, but compact
        // enough not to flatten or overwrite the surrounding terrain.
        for (int forward = -1; forward <= 1; forward++) {
            for (int lateral = -1; lateral <= 1; lateral++) {
                BlockPos cell = shaft.relative(outward, forward)
                        .relative(across, lateral)
                        .atY(surfaceY);
                if (forward == 0 && lateral == 0) {
                    set(level, writable, cell, Blocks.LADDER.defaultBlockState()
                            .setValue(LadderBlock.FACING, inward));
                } else if (forward == 1 && lateral == 0) {
                    set(level, writable, cell, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
                } else {
                    set(level, writable, cell, Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState());
                }
            }
        }

        // Write the well door last so no neighbouring corridor pass can carve it.
        for (BlockPos cell : gateCells(anchor, entry)) {
            set(level, writable, cell, EchoesShowThePast.CRYPT_SEAL.get().defaultBlockState());
        }
    }

    /**
     * Fills the four authored wall openings after the structure processor has
     * turned their barriers into air.
     */
    public static void sealHullOpenings(
            ServerLevelAccessor level,
            StructureTemplate template,
            BlockPos templateOrigin,
            StructurePlaceSettings settings,
            BoundingBox writable) {
        for (BlockPos cell : hullSealCells(template, templateOrigin, settings)) {
            if (level instanceof ServerLevel serverLevel
                    && !serverLevel.getEntitiesOfClass(Player.class, new AABB(cell)).isEmpty()) {
                continue;
            }
            set(level, writable, cell, EchoesShowThePast.CRYPT_SEAL.get().defaultBlockState());
        }
    }

    /**
     * Opens a loaded crypt without forcing generation or chunk loads. The air
     * left behind is the persistent, multiplayer-safe unlocked state.
     */
    public static boolean unlock(ServerLevel level, EchoSiteType site, BlockPos anchor) {
        if (!site.id().equals(EchoSiteType.UNKNOWN_CRYPT.id()) || !level.hasChunkAt(anchor)) {
            return false;
        }
        StructureTemplate template = level.getStructureManager()
                .get(site.presentTemplate())
                .orElse(null);
        if (template == null) {
            return false;
        }
        BlockPos origin = anchor.offset(site.memoryMin());
        StructurePlaceSettings settings = new StructurePlaceSettings();
        List<BlockPos> entries = entryMarkers(template, origin, settings);
        Set<BlockPos> seals = new LinkedHashSet<>(hullSealCells(template, origin, settings));
        for (BlockPos entry : entries) {
            seals.addAll(gateCells(anchor, entry));
        }
        if (seals.isEmpty()) {
            return false;
        }
        for (BlockPos entry : entries) {
            for (BlockPos cell : gateCells(anchor, entry)) {
                if (!level.hasChunkAt(cell)) {
                    return false;
                }
            }
        }
        int removed = removeSealCells(
                level,
                seals.stream().filter(level::hasChunkAt).toList());
        if (removed == 0) {
            return false;
        }
        for (BlockPos door : doorSentinels(site, anchor)) {
            if (!level.hasChunkAt(door)) {
                continue;
            }
            level.playSound(
                    null,
                    door,
                    SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS,
                    1.1F,
                    0.72F);
            level.playSound(
                    null,
                    door,
                    SoundEvents.AMETHYST_BLOCK_BREAK,
                    SoundSource.BLOCKS,
                    0.9F,
                    0.82F);
            level.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    door.getX() + 0.5,
                    door.getY() + 1.5,
                    door.getZ() + 0.5,
                    56,
                    1.15,
                    1.15,
                    0.28,
                    0.055);
        }
        return true;
    }

    /** Public deterministic seam for the gate lifecycle GameTest. */
    public static int removeSealCells(ServerLevel level, List<BlockPos> cells) {
        int removed = 0;
        for (BlockPos cell : cells) {
            if (level.getBlockState(cell).is(EchoesShowThePast.CRYPT_SEAL.get())) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                removed++;
            }
        }
        return removed;
    }

    public static void hintLocked(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        CompoundTag data = serverPlayer.getPersistentData();
        int now = serverPlayer.tickCount;
        if (now - data.getIntOr(HINT_TICK_TAG, Integer.MIN_VALUE / 2) < HINT_COOLDOWN_TICKS) {
            return;
        }
        data.putInt(HINT_TICK_TAG, now);
        serverPlayer.sendOverlayMessage(Component.translatable(LOCKED_MESSAGE));
    }

    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        return sealedCrypt(level, pos) != null;
    }

    /**
     * Closes authored wall openings that worldgen left as air, including crypts
     * generated before the hull seal existed.
     */
    public static void maintain(ServerLevel level, BlockPos pos) {
        EchoSiteType site = EchoSiteType.byId(EchoSiteType.UNKNOWN_CRYPT.id());
        EchoSitePiece piece = cryptPiece(level, pos);
        if (site == null || piece == null) {
            return;
        }
        BlockPos anchor = piece.cryptAnchor();
        BoundingBox chamber = site.memoryBounds(anchor);
        if (!isSealed(level, site, anchor)) {
            return;
        }
        boolean anyGap = false;
        for (BlockPos door : doorSentinels(site, anchor)) {
            if (level.hasChunkAt(door) && level.getBlockState(door).isAir()) {
                anyGap = true;
                break;
            }
        }
        if (!anyGap) {
            return;
        }
        StructureTemplate template = level.getStructureManager()
                .get(site.presentTemplate())
                .orElse(null);
        if (template == null) {
            return;
        }
        sealHullOpenings(
                level,
                template,
                anchor.offset(site.memoryMin()),
                new StructurePlaceSettings(),
                chamber);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % 20 != 0
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        maintain(level, player.blockPosition());
    }

    private static EchoSitePiece cryptPiece(ServerLevel level, BlockPos pos) {
        EchoSiteType site = EchoSiteType.byId(EchoSiteType.UNKNOWN_CRYPT.id());
        if (site == null) {
            return null;
        }
        Structure structure = level.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .getValue(site.structure());
        if (structure == null) {
            return null;
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, structure);
        if (!start.isValid()) {
            return null;
        }
        for (var piece : start.getPieces()) {
            if (piece instanceof EchoSitePiece echo && site.id().equals(echo.site().id())) {
                return echo;
            }
        }
        return null;
    }

    private static SealedCrypt sealedCrypt(ServerLevel level, BlockPos pos) {
        EchoSiteType site = EchoSiteType.byId(EchoSiteType.UNKNOWN_CRYPT.id());
        EchoSitePiece piece = cryptPiece(level, pos);
        if (site == null || piece == null || !site.requiresHarmonicKey()) {
            return null;
        }
        BlockPos anchor = piece.cryptAnchor();
        return isSealed(level, site, anchor) ? new SealedCrypt(site, anchor) : null;
    }

    private record SealedCrypt(EchoSiteType site, BlockPos anchor) {
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !isProtected(level, event.getPos())) {
            return;
        }
        hintLocked(event.getEntity());
        if (!event.getEntity().getAbilities().instabuild) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !isProtected(level, event.getPos())) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null && player.getAbilities().instabuild) {
            return;
        }
        hintLocked(player);
        event.setNotifyClient(true);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !isProtected(level, event.getPos())) {
            return;
        }
        if (event.getEntity() instanceof Player player && player.getAbilities().instabuild) {
            return;
        }
        event.setCanceled(true);
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

    private static net.minecraft.world.level.block.state.BlockState masonry(BlockPos position) {
        return Math.floorMod(position.getX() * 31 + position.getY() * 17 + position.getZ(), 7) == 0
                ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState()
                : Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    }

    private static void set(
            ServerLevelAccessor level,
            BoundingBox writable,
            BlockPos position,
            net.minecraft.world.level.block.state.BlockState state) {
        if (writable.isInside(position)) {
            level.setBlock(position, state, Block.UPDATE_CLIENTS);
        }
    }
}
