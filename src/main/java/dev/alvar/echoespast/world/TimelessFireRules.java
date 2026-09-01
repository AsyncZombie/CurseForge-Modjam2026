package dev.alvar.echoespast.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FluidState;

/** Keeps authored flames frozen in time without changing their collision or damage. */
public final class TimelessFireRules {
    private TimelessFireRules() {
    }

    public static boolean freezesFireTick(ResourceKey<Level> dimension, Block block) {
        return dimension.equals(TimelessDimensions.TIMELESS_VOID)
                && block instanceof BaseFireBlock;
    }

    public static boolean suppressesLavaIgnition(
            ResourceKey<Level> dimension,
            FluidState fluidState) {
        return dimension.equals(TimelessDimensions.TIMELESS_VOID)
                && fluidState.is(FluidTags.LAVA);
    }
}
