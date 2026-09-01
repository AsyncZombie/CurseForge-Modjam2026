package dev.alvar.echoespast.relic;

import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class EyeHazardClassifier {
    public static Optional<Descriptor> classify(BlockState state) {
        if (state.is(Blocks.DISPENSER) || state.is(Blocks.DROPPER)) {
            return Optional.of(new Descriptor(
                    EyeHazardType.PROJECTILE,
                    state.getValue(DispenserBlock.FACING)));
        }
        if (state.getFluidState().is(FluidTags.LAVA)) {
            return Optional.of(new Descriptor(EyeHazardType.LAVA, Direction.UP));
        }
        if (state.is(Blocks.POINTED_DRIPSTONE)) {
            return Optional.of(new Descriptor(
                    EyeHazardType.SPIKES,
                    state.getValue(PointedDripstoneBlock.TIP_DIRECTION)));
        }
        if (state.is(Blocks.TRIPWIRE)
                || state.is(Blocks.TRIPWIRE_HOOK)
                || state.is(Blocks.TRAPPED_CHEST)
                || state.is(Blocks.STONE_PRESSURE_PLATE)
                || state.is(Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE)
                || state.is(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE)
                || state.is(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE)
                || state.is(Blocks.OAK_PRESSURE_PLATE)
                || state.is(Blocks.SPRUCE_PRESSURE_PLATE)
                || state.is(Blocks.BIRCH_PRESSURE_PLATE)
                || state.is(Blocks.JUNGLE_PRESSURE_PLATE)
                || state.is(Blocks.ACACIA_PRESSURE_PLATE)
                || state.is(Blocks.DARK_OAK_PRESSURE_PLATE)
                || state.is(Blocks.MANGROVE_PRESSURE_PLATE)
                || state.is(Blocks.CHERRY_PRESSURE_PLATE)
                || state.is(Blocks.PALE_OAK_PRESSURE_PLATE)
                || state.is(Blocks.BAMBOO_PRESSURE_PLATE)
                || state.is(Blocks.CRIMSON_PRESSURE_PLATE)
                || state.is(Blocks.WARPED_PRESSURE_PLATE)) {
            return Optional.of(new Descriptor(EyeHazardType.TRIGGER, Direction.UP));
        }
        if (state.is(Blocks.TNT) || state.is(Blocks.RESPAWN_ANCHOR)) {
            return Optional.of(new Descriptor(EyeHazardType.EXPLOSIVE, Direction.UP));
        }
        if (state.is(Blocks.CACTUS)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.POWDER_SNOW)) {
            return Optional.of(new Descriptor(EyeHazardType.CONTACT, Direction.UP));
        }
        if (state.is(EyeRevealManager.TRAPS)) {
            return Optional.of(new Descriptor(EyeHazardType.TRIGGER, Direction.UP));
        }
        if (state.is(EyeRevealManager.GLYPHS)) {
            // Empty by default. Datapacks may opt in authored marks; vanilla
            // chiseled blocks are decoration and must not look like traps.
            return Optional.of(new Descriptor(EyeHazardType.GLYPH, Direction.UP));
        }
        return Optional.empty();
    }

    public record Descriptor(EyeHazardType type, Direction direction) {
    }

    private EyeHazardClassifier() {
    }
}
