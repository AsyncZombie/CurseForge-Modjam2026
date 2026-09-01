package dev.alvar.echoespast.snapshot;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compact network representation for a Past Echo memory.
 *
 * <p>The persistent codec deliberately remains human-readable and backwards
 * compatible. Item synchronization cannot use that representation: a dense
 * radius-12 capture expands to more than vanilla's two MiB NBT accounting
 * limit even though most entries are only a packed position and palette
 * index. This codec keeps those entries binary and isolates the few optional
 * NBT values so a large valid memory cannot disconnect its owner.
 */
public final class EchoSnapshotStreamCodec {
    private static final int MAX_PALETTE_STATES = 16_384;
    private static final int MAX_BLOCKS =
            EchoProjectionBudget.MAX_NETWORK_BLOCKS;
    private static final int MAX_ENTITIES = 256;
    private static final int MAX_IDENTIFIER_LENGTH = 512;

    private static final StreamCodec<RegistryFriendlyByteBuf, CompoundTag>
            COMPOUND_TAG_CODEC =
                    ByteBufCodecs.fromCodecWithRegistries(CompoundTag.CODEC);
    private static final StreamCodec<RegistryFriendlyByteBuf, SnapshotEntity>
            ENTITY_CODEC =
                    ByteBufCodecs.fromCodecWithRegistries(
                            SnapshotEntity.CODEC);

    public static final StreamCodec<RegistryFriendlyByteBuf, EchoSnapshot>
            STREAM_CODEC = StreamCodec.of(
                    EchoSnapshotStreamCodec::encode,
                    EchoSnapshotStreamCodec::decode);

    private static void encode(
            RegistryFriendlyByteBuf buffer,
            EchoSnapshot snapshot) {
        validateHeader(snapshot);
        buffer.writeVarInt(snapshot.version());
        ResourceKey.streamCodec(Registries.DIMENSION)
                .encode(buffer, snapshot.dimension());
        buffer.writeBlockPos(snapshot.origin());
        buffer.writeVarInt(snapshot.radius());
        buffer.writeBoolean(snapshot.sealed());

        writeCount(
                buffer,
                snapshot.palette().size(),
                MAX_PALETTE_STATES,
                "palette states");
        for (BlockState state : snapshot.palette()) {
            buffer.writeVarInt(Block.getId(state));
        }

        writeCount(
                buffer,
                snapshot.blocks().size(),
                MAX_BLOCKS,
                "snapshot blocks");
        for (SnapshotBlock block : snapshot.blocks()) {
            if (block.paletteIndex() < 0
                    || block.paletteIndex()
                            >= snapshot.palette().size()) {
                throw new EncoderException(
                        "Echo snapshot contains invalid palette index "
                                + block.paletteIndex());
            }
            buffer.writeVarInt(block.packedOffset());
            buffer.writeVarInt(block.paletteIndex());
            buffer.writeBoolean(block.blockEntityData().isPresent());
            block.blockEntityData().ifPresent(tag ->
                    COMPOUND_TAG_CODEC.encode(buffer, tag));
        }

        writeCount(
                buffer,
                snapshot.entities().size(),
                MAX_ENTITIES,
                "snapshot entities");
        for (SnapshotEntity entity : snapshot.entities()) {
            ENTITY_CODEC.encode(buffer, entity);
        }

        writeOptionalIdentifier(buffer, snapshot.template());
        writeOptionalBlockPos(buffer, snapshot.boundsMin());
        writeOptionalBlockPos(buffer, snapshot.boundsMax());
        writeOptionalIdentifier(buffer, snapshot.site());
        buffer.writeBoolean(snapshot.entitiesRevised());
        writeCount(
                buffer,
                snapshot.revisionCells().size(),
                MAX_BLOCKS,
                "revision cells");
        for (EchoRevisionCell cell : snapshot.revisionCells()) {
            if (cell.paletteIndex() < 0
                    || cell.paletteIndex()
                            >= snapshot.palette().size()) {
                throw new EncoderException(
                        "Echo snapshot contains invalid revision palette index "
                                + cell.paletteIndex());
            }
            buffer.writeBlockPos(cell.offset());
            buffer.writeVarInt(cell.paletteIndex());
            buffer.writeBoolean(cell.blockEntityData().isPresent());
            cell.blockEntityData().ifPresent(tag ->
                    COMPOUND_TAG_CODEC.encode(buffer, tag));
        }
    }

    private static EchoSnapshot decode(
            RegistryFriendlyByteBuf buffer) {
        int version = buffer.readVarInt();
        ResourceKey<Level> dimension =
                ResourceKey.streamCodec(Registries.DIMENSION)
                        .decode(buffer);
        BlockPos origin = buffer.readBlockPos();
        int radius = buffer.readVarInt();
        boolean sealed = buffer.readBoolean();

        int paletteSize = readCount(
                buffer,
                MAX_PALETTE_STATES,
                "palette states");
        List<BlockState> palette =
                new ArrayList<>(paletteSize);
        for (int index = 0; index < paletteSize; index++) {
            palette.add(Block.stateById(buffer.readVarInt()));
        }

        int blockCount = readCount(
                buffer,
                MAX_BLOCKS,
                "snapshot blocks");
        List<SnapshotBlock> blocks =
                new ArrayList<>(blockCount);
        for (int index = 0; index < blockCount; index++) {
            int packedOffset = buffer.readVarInt();
            int paletteIndex = buffer.readVarInt();
            if (paletteIndex < 0 || paletteIndex >= paletteSize) {
                throw new DecoderException(
                        "Echo snapshot contains invalid palette index "
                                + paletteIndex);
            }
            Optional<CompoundTag> blockEntityData =
                    buffer.readBoolean()
                            ? Optional.of(
                                    COMPOUND_TAG_CODEC.decode(buffer))
                            : Optional.empty();
            blocks.add(new SnapshotBlock(
                    packedOffset,
                    paletteIndex,
                    blockEntityData));
        }

        int entityCount = readCount(
                buffer,
                MAX_ENTITIES,
                "snapshot entities");
        List<SnapshotEntity> entities =
                new ArrayList<>(entityCount);
        for (int index = 0; index < entityCount; index++) {
            entities.add(ENTITY_CODEC.decode(buffer));
        }

        Optional<Identifier> template =
                readOptionalIdentifier(buffer);
        Optional<BlockPos> boundsMin =
                readOptionalBlockPos(buffer);
        Optional<BlockPos> boundsMax =
                readOptionalBlockPos(buffer);
        Optional<Identifier> site =
                version >= 6
                        ? readOptionalIdentifier(buffer)
                        : Optional.empty();
        boolean entitiesRevised =
                version >= 7 && buffer.readBoolean();
        List<EchoRevisionCell> revisionCells = List.of();
        if (version >= 8) {
            int revisionCount = readCount(
                    buffer,
                    MAX_BLOCKS,
                    "revision cells");
            revisionCells = new ArrayList<>(revisionCount);
            for (int index = 0; index < revisionCount; index++) {
                BlockPos offset = buffer.readBlockPos();
                int paletteIndex = buffer.readVarInt();
                if (paletteIndex < 0 || paletteIndex >= paletteSize) {
                    throw new DecoderException(
                            "Echo snapshot contains invalid revision palette index "
                                    + paletteIndex);
                }
                Optional<CompoundTag> blockEntityData =
                        buffer.readBoolean()
                                ? Optional.of(
                                        COMPOUND_TAG_CODEC.decode(buffer))
                                : Optional.empty();
                revisionCells.add(new EchoRevisionCell(
                        offset,
                        paletteIndex,
                        blockEntityData));
            }
        }
        EchoSnapshot snapshot = new EchoSnapshot(
                version,
                dimension,
                origin,
                radius,
                sealed,
                palette,
                blocks,
                entities,
                template,
                boundsMin,
                boundsMax,
                site,
                entitiesRevised,
                revisionCells);
        validateHeader(snapshot);
        return snapshot;
    }

    private static void validateHeader(EchoSnapshot snapshot) {
        if (snapshot.version() < 1
                || snapshot.version()
                        > EchoSnapshot.CURRENT_VERSION) {
            throw new DecoderException(
                    "Unsupported echo snapshot version "
                            + snapshot.version());
        }
        boolean hasMinimum = snapshot.boundsMin().isPresent();
        boolean hasMaximum = snapshot.boundsMax().isPresent();
        if (hasMinimum != hasMaximum) {
            throw new DecoderException(
                    "Echo snapshot bounds must be supplied as a pair");
        }
        boolean authored = snapshot.template().isPresent()
                || snapshot.sealed() && hasMinimum;
        int maximumRadius = authored ? 32 : 16;
        if (snapshot.radius() < 0
                || snapshot.radius() > maximumRadius) {
            throw new DecoderException(
                    "Echo snapshot radius outside 0.."
                            + maximumRadius);
        }
        if (snapshot.template().isPresent() && !hasMinimum) {
            throw new DecoderException(
                    "Template echo memory is missing its bounds");
        }
    }

    private static int readCount(
            RegistryFriendlyByteBuf buffer,
            int maximum,
            String description) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new DecoderException(
                    count
                            + " "
                            + description
                            + " exceeded maximum "
                            + maximum);
        }
        return count;
    }

    private static void writeCount(
            RegistryFriendlyByteBuf buffer,
            int count,
            int maximum,
            String description) {
        if (count < 0 || count > maximum) {
            throw new EncoderException(
                    count
                            + " "
                            + description
                            + " exceeded maximum "
                            + maximum);
        }
        buffer.writeVarInt(count);
    }

    private static void writeOptionalIdentifier(
            RegistryFriendlyByteBuf buffer,
            Optional<Identifier> value) {
        buffer.writeBoolean(value.isPresent());
        value.ifPresent(identifier ->
                buffer.writeUtf(identifier.toString()));
    }

    private static Optional<Identifier> readOptionalIdentifier(
            RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean()
                ? Optional.of(Identifier.parse(
                        buffer.readUtf(MAX_IDENTIFIER_LENGTH)))
                : Optional.empty();
    }

    private static void writeOptionalBlockPos(
            RegistryFriendlyByteBuf buffer,
            Optional<BlockPos> value) {
        buffer.writeBoolean(value.isPresent());
        value.ifPresent(buffer::writeBlockPos);
    }

    private static Optional<BlockPos> readOptionalBlockPos(
            RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean()
                ? Optional.of(buffer.readBlockPos())
                : Optional.empty();
    }

    private EchoSnapshotStreamCodec() {
    }
}
