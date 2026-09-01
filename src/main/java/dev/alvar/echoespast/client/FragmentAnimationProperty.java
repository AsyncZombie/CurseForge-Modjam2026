package dev.alvar.echoespast.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;

/**
 * Idle swirl for Past Fragment spheres — always alive in inventory, stronger
 * when the fragment still holds a memory.
 */
public final class FragmentAnimationProperty implements RangeSelectItemModelProperty {
    public static final FragmentAnimationProperty INSTANCE = new FragmentAnimationProperty();
    public static final MapCodec<FragmentAnimationProperty> MAP_CODEC = MapCodec.unit(INSTANCE);
    private static final int FRAME_COUNT = 12;
    private static final float TICKS_PER_FRAME = 2.0F;

    @Override
    public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
        long ticks = level == null ? System.currentTimeMillis() / 50L : level.getGameTime();
        return (ticks / (long) TICKS_PER_FRAME) % FRAME_COUNT;
    }

    @Override
    public MapCodec<? extends RangeSelectItemModelProperty> type() {
        return MAP_CODEC;
    }

    private FragmentAnimationProperty() {
    }
}
