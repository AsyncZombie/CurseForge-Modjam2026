package dev.alvar.echoespast.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.snapshot.SnapshotEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class MaterializedEchoSavedData extends SavedData {
    public record PresentBlock(long position, BlockState state, Optional<CompoundTag> blockEntityData) {
        public static final Codec<PresentBlock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("position").forGetter(PresentBlock::position),
                BlockState.CODEC.fieldOf("state").forGetter(PresentBlock::state),
                CompoundTag.CODEC.optionalFieldOf("block_entity").forGetter(PresentBlock::blockEntityData)
        ).apply(instance, PresentBlock::new));

        public PresentBlock {
            blockEntityData = blockEntityData == null
                    ? Optional.empty()
                    : blockEntityData.map(CompoundTag::copy);
        }
    }

    public record Journal(
            UUID owner,
            List<PresentBlock> present,
            BlockPos origin,
            List<SnapshotEntity> presentEntities) {
        public static final Codec<Journal> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Journal::owner),
                PresentBlock.CODEC.listOf().fieldOf("present").forGetter(Journal::present),
                BlockPos.CODEC.optionalFieldOf("origin", BlockPos.ZERO)
                        .forGetter(Journal::origin),
                SnapshotEntity.CODEC.listOf()
                        .optionalFieldOf("present_entities", List.of())
                        .forGetter(Journal::presentEntities)
        ).apply(instance, Journal::new));

        public Journal {
            present = List.copyOf(present);
            origin = origin.immutable();
            presentEntities = List.copyOf(presentEntities);
        }

        public Journal(
                UUID owner,
                List<PresentBlock> present) {
            this(owner, present, BlockPos.ZERO, List.of());
        }
    }

    private static final Codec<MaterializedEchoSavedData> CODEC = Journal.CODEC.listOf().xmap(
            MaterializedEchoSavedData::new,
            MaterializedEchoSavedData::journals);
    public static final SavedDataType<MaterializedEchoSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "materialized_echo_journal"),
            MaterializedEchoSavedData::new,
            CODEC);

    private final Map<UUID, Journal> journals = new HashMap<>();

    public MaterializedEchoSavedData() {
    }

    private MaterializedEchoSavedData(List<Journal> journals) {
        for (Journal journal : journals) {
            this.journals.put(journal.owner(), journal);
        }
    }

    public void put(Journal journal) {
        journals.put(journal.owner(), journal);
        setDirty();
    }

    public void remove(UUID owner) {
        if (journals.remove(owner) != null) {
            setDirty();
        }
    }

    public List<Journal> journals() {
        return new ArrayList<>(journals.values());
    }
}
