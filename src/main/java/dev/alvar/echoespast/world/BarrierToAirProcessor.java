package dev.alvar.echoespast.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * Turns authoring-only barriers into air while an Echo site is placed.
 *
 * <p>Vanilla templates omit natural air, so barriers are the explicit carve
 * mask for caves, tunnels and entrances. This processor is installed directly
 * by {@link EchoSitePiece}; it is not a serialised datapack processor.</p>
 */
public final class BarrierToAirProcessor extends StructureProcessor {
    public static final BarrierToAirProcessor INSTANCE = new BarrierToAirProcessor();

    private BarrierToAirProcessor() {
    }

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo processBlock(
            LevelReader level,
            BlockPos targetPosition,
            BlockPos referencePos,
            StructureTemplate.StructureBlockInfo originalBlockInfo,
            StructureTemplate.StructureBlockInfo processedBlockInfo,
            StructurePlaceSettings settings) {
        if (!processedBlockInfo.state().is(Blocks.BARRIER)) {
            return processedBlockInfo;
        }
        return new StructureTemplate.StructureBlockInfo(
                processedBlockInfo.pos(),
                Blocks.AIR.defaultBlockState(),
                null);
    }

    /**
     * Entity NBT in an Axiom blueprint can retain an absolute attachment
     * position for item frames and paintings. StructureTemplate relocates Pos
     * itself, but not TileX/Y/Z, so rebase those fields to the placement here.
     */
    @Override
    public StructureTemplate.StructureEntityInfo processEntity(
            LevelReader level,
            BlockPos seedPosition,
            StructureTemplate.StructureEntityInfo originalEntityInfo,
            StructureTemplate.StructureEntityInfo processedEntityInfo,
            StructurePlaceSettings settings,
            StructureTemplate template) {
        CompoundTag data = processedEntityInfo.nbt;
        if (data == null) {
            return processedEntityInfo;
        }
        boolean legacyAttachment = data.contains("TileX")
                && data.contains("TileY")
                && data.contains("TileZ");
        boolean modernAttachment = data.contains("block_pos");
        if (!legacyAttachment && !modernAttachment) {
            return processedEntityInfo;
        }
        CompoundTag rebased = data.copy();
        BlockPos attachment = BlockPos.containing(processedEntityInfo.pos);
        rebased.putInt("TileX", attachment.getX());
        rebased.putInt("TileY", attachment.getY());
        rebased.putInt("TileZ", attachment.getZ());
        if (modernAttachment) {
            rebased.putIntArray("block_pos", new int[] {
                    attachment.getX(),
                    attachment.getY(),
                    attachment.getZ()
            });
        }
        return new StructureTemplate.StructureEntityInfo(
                processedEntityInfo.pos,
                processedEntityInfo.blockPos,
                rebased);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        // This processor is runtime-only and never appears in a processor-list
        // JSON. NOP is sufficient if a caller introspects its type.
        return StructureProcessorType.NOP;
    }
}
