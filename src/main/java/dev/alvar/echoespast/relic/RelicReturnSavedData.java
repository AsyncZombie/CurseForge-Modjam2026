package dev.alvar.echoespast.relic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alvar.echoespast.EchoesShowThePast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class RelicReturnSavedData extends SavedData {
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
            ItemStack.CODEC.listOf().fieldOf("items").forGetter(Entry::items)
    ).apply(instance, Entry::new));
    private static final Codec<RelicReturnSavedData> CODEC = ENTRY_CODEC.listOf().xmap(
            RelicReturnSavedData::new,
            RelicReturnSavedData::entries);
    public static final SavedDataType<RelicReturnSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "pending_relic_returns"),
            RelicReturnSavedData::new,
            CODEC);

    private final Map<UUID, List<ItemStack>> pending = new HashMap<>();

    public RelicReturnSavedData() {
    }

    private RelicReturnSavedData(List<Entry> entries) {
        for (Entry entry : entries) {
            pending.put(entry.owner(), new ArrayList<>(entry.items()));
        }
    }

    public void add(UUID owner, ItemStack stack) {
        pending.computeIfAbsent(owner, ignored -> new ArrayList<>())
                .add(stack.copyWithCount(1));
        setDirty();
    }

    public List<ItemStack> take(UUID owner) {
        List<ItemStack> result = pending.remove(owner);
        if (result == null) {
            return List.of();
        }
        setDirty();
        return List.copyOf(result);
    }

    private List<Entry> entries() {
        return pending.entrySet().stream()
                .map(entry -> new Entry(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    private record Entry(UUID owner, List<ItemStack> items) {
    }
}
