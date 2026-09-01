package dev.alvar.echoespast.resonance;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.util.StringRepresentable;

/**
 * A deliberately small palette keeps distant replies readable against every
 * biome and prevents the decoder from becoming a generic RGB picker.
 */
public enum ResonanceColor implements StringRepresentable {
    CYAN("cyan", 0x55E6F2),
    AMBER("amber", 0xF2AD45),
    JADE("jade", 0x62D394),
    PALE_BLUE("pale_blue", 0xA7D8F5),
    VIOLET("violet", 0xA77BF3),
    CORAL("coral", 0xF27D72),
    GOLD("gold", 0xF3D35C),
    ICE("ice", 0xD7F6FF),
    ROSE("rose", 0xEE8DB7),
    LIME("lime", 0xB8E063),
    INDIGO("indigo", 0x7479E8),
    WHITE("white", 0xE9F1F2),
    /** Weathered metal / medieval stone — watchtower resonance. */
    PEWTER("pewter", 0x9A9084);

    public static final Codec<ResonanceColor> CODEC =
            StringRepresentable.fromEnum(ResonanceColor::values);
    public static final List<ResonanceColor> PALETTE = List.of(values());

    private final String serializedName;
    private final int rgb;

    ResonanceColor(String serializedName, int rgb) {
        this.serializedName = serializedName;
        this.rgb = rgb;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public int rgb() {
        return rgb;
    }

    public float red() {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    public float green() {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    public float blue() {
        return (rgb & 0xFF) / 255.0F;
    }
}
