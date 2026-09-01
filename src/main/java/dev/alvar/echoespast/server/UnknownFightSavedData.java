package dev.alvar.echoespast.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alvar.echoespast.EchoesShowThePast;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class UnknownFightSavedData extends SavedData {
    private static final Codec<UnknownFightSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.listOf()
                            .optionalFieldOf("stone_granted", List.of())
                            .forGetter(data -> List.copyOf(data.stoneGranted)))
            .apply(instance, UnknownFightSavedData::new));

    public static final SavedDataType<UnknownFightSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "unknown_fight"),
            UnknownFightSavedData::new,
            CODEC);

    private final Set<UUID> stoneGranted = new HashSet<>();

    public UnknownFightSavedData() {
    }

    private UnknownFightSavedData(List<UUID> granted) {
        stoneGranted.addAll(granted);
    }

    public boolean hasGrantedStone(UUID playerId) {
        return stoneGranted.contains(playerId);
    }

    /** Exposed for invariant tests and save diagnostics, never for progression logic. */
    public int grantedStoneCount() {
        return stoneGranted.size();
    }

    public void markStoneGranted(UUID playerId) {
        if (stoneGranted.add(playerId)) {
            setDirty();
        }
    }

    public void clearGranted(UUID playerId) {
        if (stoneGranted.remove(playerId)) {
            setDirty();
        }
    }
}
