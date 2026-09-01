package dev.alvar.echoespast.snapshot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public record SnapshotBlock(int packedOffset, int paletteIndex, Optional<CompoundTag> blockEntityData) {
    private static final int AXIS_BITS = 6;
    private static final int AXIS_MASK = (1 << AXIS_BITS) - 1;
    private static final int BIAS = 32;

    public static final Codec<SnapshotBlock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("offset").forGetter(SnapshotBlock::packedOffset),
            Codec.INT.fieldOf("state").forGetter(SnapshotBlock::paletteIndex),
            CompoundTag.CODEC.optionalFieldOf("block_entity").forGetter(SnapshotBlock::blockEntityData)
    ).apply(instance, SnapshotBlock::new));

    public SnapshotBlock {
        blockEntityData = blockEntityData == null
                ? Optional.empty()
                : blockEntityData.map(CompoundTag::copy);
    }

    public SnapshotBlock(int packedOffset, int paletteIndex) {
        this(packedOffset, paletteIndex, Optional.empty());
    }

    public static SnapshotBlock of(int dx, int dy, int dz, int paletteIndex) {
        if (dx < -BIAS || dx >= BIAS || dy < -BIAS || dy >= BIAS || dz < -BIAS || dz >= BIAS) {
            throw new IllegalArgumentException("Snapshot offset outside compact range");
        }
        int packed = (dx + BIAS)
                | ((dy + BIAS) << AXIS_BITS)
                | ((dz + BIAS) << (AXIS_BITS * 2));
        return new SnapshotBlock(packed, paletteIndex);
    }

    public static SnapshotBlock of(
            int dx,
            int dy,
            int dz,
            int paletteIndex,
            CompoundTag blockEntityData) {
        SnapshotBlock block = of(dx, dy, dz, paletteIndex);
        return new SnapshotBlock(
                block.packedOffset,
                block.paletteIndex,
                Optional.ofNullable(blockEntityData));
    }

    public BlockPos offset() {
        int dx = (packedOffset & AXIS_MASK) - BIAS;
        int dy = ((packedOffset >> AXIS_BITS) & AXIS_MASK) - BIAS;
        int dz = ((packedOffset >> (AXIS_BITS * 2)) & AXIS_MASK) - BIAS;
        return new BlockPos(dx, dy, dz);
    }
}
