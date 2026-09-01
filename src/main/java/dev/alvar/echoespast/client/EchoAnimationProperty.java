package dev.alvar.echoespast.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;

/**
 * Drives the Past Echo's seven texture frames from the exact same monotonic
 * clock as the world-space pulse.
 */
public final class EchoAnimationProperty implements RangeSelectItemModelProperty {
    public static final EchoAnimationProperty INSTANCE = new EchoAnimationProperty();
    public static final MapCodec<EchoAnimationProperty> MAP_CODEC = MapCodec.unit(INSTANCE);

    @Override
    public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
        return ClientEchoState.itemAnimationFrame(stack);
    }

    @Override
    public MapCodec<? extends RangeSelectItemModelProperty> type() {
        return MAP_CODEC;
    }

    private EchoAnimationProperty() {
    }
}
