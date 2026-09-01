package dev.alvar.echoespast.snapshot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Sparse Stone revision cell with unbounded local offsets. Unlike
 * {@link SnapshotBlock}, this can address Medusa-scale authored footprints.
 */
public record EchoRevisionCell(
        BlockPos offset,
        int paletteIndex,
        Optional<CompoundTag> blockEntityData) {

    public static final Codec<EchoRevisionCell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("offset").forGetter(EchoRevisionCell::offset),
            Codec.INT.fieldOf("state").forGetter(EchoRevisionCell::paletteIndex),
            CompoundTag.CODEC.optionalFieldOf("block_entity").forGetter(EchoRevisionCell::blockEntityData)
    ).apply(instance, EchoRevisionCell::new));

    public EchoRevisionCell {
        offset = offset.immutable();
        blockEntityData = blockEntityData == null
                ? Optional.empty()
                : blockEntityData.map(CompoundTag::copy);
    }

    public static EchoRevisionCell of(
            int dx,
            int dy,
            int dz,
            int paletteIndex,
            CompoundTag blockEntityData) {
        return new EchoRevisionCell(
                new BlockPos(dx, dy, dz),
                paletteIndex,
                Optional.ofNullable(blockEntityData));
    }
}
