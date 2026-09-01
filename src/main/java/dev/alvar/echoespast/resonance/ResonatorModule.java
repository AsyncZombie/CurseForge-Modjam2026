package dev.alvar.echoespast.resonance;

import com.mojang.serialization.Codec;
import dev.alvar.echoespast.EchoesShowThePast;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

public enum ResonatorModule implements StringRepresentable {
    RANGE_COIL(true),
    DIRECTIONAL_MATRIX(true),
    CYCLE_REGULATOR(true),
    HARMONIC_DECODER(false),
    HARMONIC_KEY(false);

    public static final Codec<ResonatorModule> CODEC =
            StringRepresentable.fromEnum(ResonatorModule::values);

    private final boolean duplicatesAllowed;

    ResonatorModule(boolean duplicatesAllowed) {
        this.duplicatesAllowed = duplicatesAllowed;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean duplicatesAllowed() {
        return duplicatesAllowed;
    }

    public static ResonatorModule fromStack(ItemStack stack) {
        if (stack.is(EchoesShowThePast.RANGE_COIL.get())) {
            return RANGE_COIL;
        }
        if (stack.is(EchoesShowThePast.DIRECTIONAL_MATRIX.get())) {
            return DIRECTIONAL_MATRIX;
        }
        if (stack.is(EchoesShowThePast.CYCLE_REGULATOR.get())) {
            return CYCLE_REGULATOR;
        }
        if (stack.is(EchoesShowThePast.HARMONIC_DECODER.get())) {
            return HARMONIC_DECODER;
        }
        if (stack.is(EchoesShowThePast.HARMONIC_KEY.get())) {
            return HARMONIC_KEY;
        }
        return null;
    }

    public ItemStack createStack() {
        return new ItemStack(switch (this) {
            case RANGE_COIL -> EchoesShowThePast.RANGE_COIL.get();
            case DIRECTIONAL_MATRIX -> EchoesShowThePast.DIRECTIONAL_MATRIX.get();
            case CYCLE_REGULATOR -> EchoesShowThePast.CYCLE_REGULATOR.get();
            case HARMONIC_DECODER -> EchoesShowThePast.HARMONIC_DECODER.get();
            case HARMONIC_KEY -> EchoesShowThePast.HARMONIC_KEY.get();
        });
    }
}
