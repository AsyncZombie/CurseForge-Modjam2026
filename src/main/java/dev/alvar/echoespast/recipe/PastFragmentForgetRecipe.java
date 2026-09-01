package dev.alvar.echoespast.recipe;

import com.mojang.serialization.MapCodec;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.item.PastEchoMemory;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Ritual of forgetting: a lone non-sealed Past Fragment placed in a crafting
 * grid yields the same vessel emptied of memory. Sealed dungeon fragments
 * never match.
 */
public final class PastFragmentForgetRecipe extends CustomRecipe {
    public static final PastFragmentForgetRecipe INSTANCE = new PastFragmentForgetRecipe();
    public static final MapCodec<PastFragmentForgetRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, PastFragmentForgetRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<PastFragmentForgetRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private PastFragmentForgetRecipe() {
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findPurgeable(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack source = findPurgeable(input);
        if (source == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = source.copyWithCount(1);
        PastEchoMemory.purgeFragmentMemory(result);
        return result;
    }

    @Override
    public RecipeSerializer<PastFragmentForgetRecipe> getSerializer() {
        return SERIALIZER;
    }

    private static ItemStack findPurgeable(CraftingInput input) {
        ItemStack found = null;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get())) {
                return null;
            }
            EchoSnapshot snapshot = stack.get(EchoesShowThePast.ECHO_SNAPSHOT.get());
            if (snapshot == null || !snapshot.canErase()) {
                return null;
            }
            if (found != null) {
                return null;
            }
            found = stack;
        }
        return found;
    }
}
