package dev.alvar.echoespast.visual;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A deliberately small acoustic model. It does not try to simulate sound;
 * it gives the visual echo enough material character to feel physically
 * grounded without turning the pulse into visual noise.
 */
public final class EchoMaterialResponse {
    public static final Profile STONE = new Profile(0.86F, 0.00F, 0.92F);
    public static final Profile METAL = new Profile(1.00F, -0.05F, 0.72F);
    public static final Profile SOFT = new Profile(0.28F, 0.18F, 1.55F);
    public static final Profile GLASS = new Profile(0.34F, 0.11F, 1.28F);
    public static final Profile LIQUID = new Profile(0.17F, 0.20F, 1.70F);
    public static final Profile GENERAL = new Profile(0.68F, 0.04F, 1.00F);

    public static Profile forState(BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return LIQUID;
        }
        if (state.is(BlockTags.WOOL) || state.is(BlockTags.LEAVES)) {
            return SOFT;
        }

        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (containsAny(path, "copper", "iron", "gold", "metal", "anvil", "chain")) {
            return METAL;
        }
        if (!state.canOcclude()) {
            if (containsAny(path, "grass", "fern", "flower", "vine", "moss", "crop", "leaves")) {
                return SOFT;
            }
            return GLASS;
        }
        if (containsAny(path, "stone", "slate", "brick", "tuff", "basalt", "quartz")) {
            return STONE;
        }
        return GENERAL;
    }

    private static boolean containsAny(String path, String... fragments) {
        for (String fragment : fragments) {
            if (path.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    public record Profile(float reflectivity, float delaySeconds, float widthScale) {
    }

    private EchoMaterialResponse() {
    }
}
