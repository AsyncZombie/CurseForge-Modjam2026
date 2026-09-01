package dev.alvar.echoespast.client;

import com.mojang.serialization.MapCodec;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.item.PastEchoMemory;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Tints the greyscale Past Fragment sphere with dungeon or personal resonance.
 * Empty shells render dimmer so the vessel still reads as spent energy.
 */
public final class FragmentTintSource implements ItemTintSource {
    public static final FragmentTintSource INSTANCE = new FragmentTintSource();
    public static final MapCodec<FragmentTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);

    private FragmentTintSource() {
    }

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        int rgb = PastEchoMemory.resolveColor(stack).rgb();
        if (stack.get(EchoesShowThePast.ECHO_SNAPSHOT.get()) == null) {
            int r = (((rgb >> 16) & 0xFF) * 90) / 255;
            int g = (((rgb >> 8) & 0xFF) * 90) / 255;
            int b = ((rgb & 0xFF) * 110) / 255;
            rgb = (r << 16) | (g << 8) | b;
        }
        return ARGB.opaque(rgb);
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
