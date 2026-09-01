package dev.alvar.echoespast.world;

import dev.alvar.echoespast.server.UnknownMedievalVanguard;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * Placement-time sanitation for the authored medieval boss arena.
 *
 * <p>Axiom preserves complete Easy NPC data, which is exactly what the fight
 * needs for skins, equipment and authored objectives. Identity and navigation
 * coordinates are world-instance data, however, so they must not survive a
 * blueprint export.</p>
 */
public final class UnknownMedievalArenaProcessor extends StructureProcessor {
    public static final UnknownMedievalArenaProcessor INSTANCE =
            new UnknownMedievalArenaProcessor();

    private UnknownMedievalArenaProcessor() {
    }

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo processBlock(
            LevelReader level,
            BlockPos targetPosition,
            BlockPos referencePos,
            StructureTemplate.StructureBlockInfo originalBlockInfo,
            StructureTemplate.StructureBlockInfo processedBlockInfo,
            StructurePlaceSettings settings) {
        CompoundTag data = processedBlockInfo.nbt();
        if (!processedBlockInfo.state().is(Blocks.STRUCTURE_BLOCK)
                || data == null
                || !UnknownMedievalVanguard.isFightMarker(
                        data.getStringOr("metadata", ""))) {
            return processedBlockInfo;
        }
        return new StructureTemplate.StructureBlockInfo(
                processedBlockInfo.pos(),
                Blocks.AIR.defaultBlockState(),
                null);
    }

    @Override
    public StructureTemplate.StructureEntityInfo processEntity(
            LevelReader level,
            BlockPos seedPosition,
            StructureTemplate.StructureEntityInfo originalEntityInfo,
            StructureTemplate.StructureEntityInfo processedEntityInfo,
            StructurePlaceSettings settings,
            StructureTemplate template) {
        if (processedEntityInfo.nbt == null) {
            return processedEntityInfo;
        }
        CompoundTag sanitized = processedEntityInfo.nbt.copy();
        sanitizeIdentityAndHome(
                sanitized,
                BlockPos.containing(processedEntityInfo.pos));
        return new StructureTemplate.StructureEntityInfo(
                processedEntityInfo.pos,
                processedEntityInfo.blockPos,
                sanitized);
    }

    private static void sanitizeIdentityAndHome(CompoundTag data, BlockPos home) {
        data.remove("UUID");
        data.remove("Owner");
        data.remove("OwnerUUID");
        data.remove("PresetUUID");
        data.remove("AngerTime");
        data.remove("AngryAt");
        data.remove("AttackTarget");
        data.remove("DeathLootTable");
        data.remove("DeathLootTableSeed");
        data.remove("HurtByTimestamp");
        data.remove("HurtTime");
        data.remove("DeathTime");
        data.remove("Motion");
        data.remove("Air");
        data.remove("OnGround");
        data.remove("FallDistance");
        data.remove("Fire");
        data.remove("PortalCooldown");
        data.remove("Leash");

        if (data.getStringOr("id", "").startsWith("easy_npc:")) {
            CompoundTag navigation = data.getCompoundOrEmpty("Navigation").copy();
            CompoundTag homeTag = new CompoundTag();
            homeTag.putInt("X", home.getX());
            homeTag.putInt("Y", home.getY());
            homeTag.putInt("Z", home.getZ());
            navigation.put("Home", homeTag);
            data.put("Navigation", navigation);
            data.putBoolean("PersistenceRequired", true);
            data.putBoolean("CanPickUpLoot", false);
        }

        ListTag passengers = data.getListOrEmpty("Passengers");
        for (Tag passenger : passengers) {
            if (passenger instanceof CompoundTag passengerData) {
                sanitizeIdentityAndHome(passengerData, home);
            }
        }
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.NOP;
    }
}
