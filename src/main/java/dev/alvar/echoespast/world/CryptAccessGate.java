package dev.alvar.echoespast.world;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.resonance.EchoSiteType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Builds and unlocks the authored Unknown crypt entrance.
 *
 * <p>The crypt itself owns the doorway through an {@code entry} data marker.
 * The generated shaft is therefore allowed to change without ever baking a
 * coordinate from the current template into Java.</p>
 */
public final class CryptAccessGate {
    public static final String ENTRY_MARKER = "entry";
    private static final int SHAFT_DISTANCE = 4;
    private static final int CORRIDOR_HEIGHT = 3;

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

        // Write the seal last so no neighbouring corridor pass can carve it.
        for (BlockPos cell : gateCells(anchor, entry)) {
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
        List<BlockPos> entries = entryMarkers(template, origin, new StructurePlaceSettings());
        List<BlockPos> seals = entries.stream()
                .flatMap(entry -> gateCells(anchor, entry).stream())
                .toList();
        if (seals.isEmpty() || seals.stream().anyMatch(cell -> !level.hasChunkAt(cell))) {
            return false;
        }
        int removed = removeSealCells(level, seals);
        if (removed == 0) {
            return false;
        }
        for (BlockPos entry : entries) {
            level.playSound(
                    null,
                    entry,
                    SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS,
                    1.1F,
                    0.72F);
            level.playSound(
                    null,
                    entry,
                    SoundEvents.AMETHYST_BLOCK_BREAK,
                    SoundSource.BLOCKS,
                    0.9F,
                    0.82F);
            level.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    entry.getX() + 0.5,
                    entry.getY() + 1.5,
                    entry.getZ() + 0.5,
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
