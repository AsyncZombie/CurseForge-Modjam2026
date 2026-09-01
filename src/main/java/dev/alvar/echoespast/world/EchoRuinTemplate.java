package dev.alvar.echoespast.world;

import dev.alvar.echoespast.snapshot.EchoSnapshot;
import dev.alvar.echoespast.snapshot.SnapshotBlock;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.block.EchoPedestalBlock;
import dev.alvar.echoespast.resonance.EchoSiteType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class EchoRuinTemplate {
    public static final int SECRET_Z = -6;
    private static final List<TemplateBlock> BLOCKS = buildTemplate();

    public static List<TemplateBlock> blocks() {
        return BLOCKS;
    }

    public static EchoSnapshot createSnapshot(ResourceKey<Level> dimension, BlockPos pedestal) {
        Map<BlockState, Integer> paletteIndex = new LinkedHashMap<>();
        List<BlockState> palette = new ArrayList<>();
        List<SnapshotBlock> snapshotBlocks = new ArrayList<>();
        for (TemplateBlock block : BLOCKS) {
            int index = paletteIndex.computeIfAbsent(block.state(), state -> {
                palette.add(state);
                return palette.size() - 1;
            });
            snapshotBlocks.add(SnapshotBlock.of(
                    block.offset().getX(),
                    block.offset().getY(),
                    block.offset().getZ(),
                    index));
        }

        // The pedestal is consumed when the sealed memory is claimed, so it must remain in the intact echo.
        BlockState pedestalState = EchoesShowThePast.ECHO_PEDESTAL.get()
                .defaultBlockState()
                .setValue(EchoPedestalBlock.SPENT, false);
        int pedestalIndex = paletteIndex.computeIfAbsent(pedestalState, state -> {
            palette.add(state);
            return palette.size() - 1;
        });
        snapshotBlocks.add(SnapshotBlock.of(0, 0, 0, pedestalIndex));

        return new EchoSnapshot(
                EchoSnapshot.CURRENT_VERSION,
                dimension,
                pedestal,
                9,
                true,
                palette,
                snapshotBlocks,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(EchoSiteType.LEGACY_RUIN.id()));
    }

    private static List<TemplateBlock> buildTemplate() {
        List<TemplateBlock> blocks = new ArrayList<>();
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState copper = Blocks.EXPOSED_COPPER.defaultBlockState();
        BlockState marker = Blocks.AMETHYST_BLOCK.defaultBlockState();

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                add(blocks, x, -1, z, ((x + z) & 3) == 0 ? mossy : brick);
            }
        }
        for (int z = -7; z <= -5; z++) {
            for (int x = -2; x <= 2; x++) {
                add(blocks, x, -1, z, brick);
            }
        }

        for (int y = 0; y <= 3; y++) {
            for (int x = -4; x <= 4; x++) {
                if (!(y < 3 && Math.abs(x) <= 1)) {
                    add(blocks, x, y, 4, brick);
                }
                // The intact ruin had a two-block-high passage into the hidden rear room.
                // Its frame is added below; the present-day feature fills this opening with cracked bricks.
                boolean rearPassageOrFrame = (y < 2 && Math.abs(x) <= 1) || (y == 2 && x == 0);
                if (!rearPassageOrFrame) {
                    add(blocks, x, y, -4, brick);
                }
            }
            for (int z = -3; z <= 3; z++) {
                add(blocks, -4, y, z, brick);
                add(blocks, 4, y, z, brick);
            }
        }

        for (int y = 0; y <= 3; y++) {
            for (int z = -7; z <= -5; z++) {
                add(blocks, -2, y, z, brick);
                add(blocks, 2, y, z, brick);
            }
            for (int x = -2; x <= 2; x++) {
                add(blocks, x, y, -7, brick);
            }
        }

        for (int y = 0; y <= 4; y++) {
            add(blocks, -3, y, -3, copper);
            add(blocks, 3, y, -3, copper);
            add(blocks, -3, y, 3, copper);
            add(blocks, 3, y, 3, copper);
        }
        for (int x = -3; x <= 3; x++) {
            add(blocks, x, 4, -3, copper);
            add(blocks, x, 4, 3, copper);
        }
        for (int z = -2; z <= 2; z++) {
            add(blocks, -3, 4, z, copper);
            add(blocks, 3, 4, z, copper);
        }

        // A bright frame in the intact memory marks the passage that the ruin has lost.
        add(blocks, -1, 0, -4, marker);
        add(blocks, 1, 0, -4, marker);
        add(blocks, -1, 1, -4, marker);
        add(blocks, 1, 1, -4, marker);
        add(blocks, 0, 2, -4, marker);
        return List.copyOf(blocks);
    }

    private static void add(List<TemplateBlock> blocks, int x, int y, int z, BlockState state) {
        blocks.add(new TemplateBlock(new BlockPos(x, y, z), state));
    }

    public record TemplateBlock(BlockPos offset, BlockState state) {
    }

    private EchoRuinTemplate() {
    }
}
