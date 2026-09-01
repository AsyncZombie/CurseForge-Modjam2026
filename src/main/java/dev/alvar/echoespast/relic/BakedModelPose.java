package dev.alvar.echoespast.relic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * Final model-part transforms captured after vanilla has evaluated every
 * entity-specific animation. This is deliberately renderer-agnostic: named
 * paths can also be applied to compatible clothing, armor and saddle models.
 */
public record BakedModelPose(List<Part> parts) {
    public static final int MAX_PARTS = 512;
    public static final BakedModelPose EMPTY = new BakedModelPose(List.of());
    public static final Codec<BakedModelPose> CODEC = Part.CODEC.listOf()
            .xmap(BakedModelPose::new, BakedModelPose::parts);

    public BakedModelPose {
        parts = List.copyOf(parts);
        if (parts.size() > MAX_PARTS) {
            throw new IllegalArgumentException(
                    "A petrified model pose cannot contain more than "
                            + MAX_PARTS
                            + " parts");
        }
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    public record Part(
            String path,
            float x,
            float y,
            float z,
            float xRot,
            float yRot,
            float zRot,
            float xScale,
            float yScale,
            float zScale,
            boolean visible,
            boolean skipDraw) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("path").forGetter(Part::path),
                Codec.FLOAT.fieldOf("x").forGetter(Part::x),
                Codec.FLOAT.fieldOf("y").forGetter(Part::y),
                Codec.FLOAT.fieldOf("z").forGetter(Part::z),
                Codec.FLOAT.fieldOf("x_rot").forGetter(Part::xRot),
                Codec.FLOAT.fieldOf("y_rot").forGetter(Part::yRot),
                Codec.FLOAT.fieldOf("z_rot").forGetter(Part::zRot),
                Codec.FLOAT.fieldOf("x_scale").forGetter(Part::xScale),
                Codec.FLOAT.fieldOf("y_scale").forGetter(Part::yScale),
                Codec.FLOAT.fieldOf("z_scale").forGetter(Part::zScale),
                Codec.BOOL.fieldOf("visible").forGetter(Part::visible),
                Codec.BOOL.fieldOf("skip_draw").forGetter(Part::skipDraw)
        ).apply(instance, Part::new));

        public Part {
            if (path.isBlank() || path.length() > 256) {
                throw new IllegalArgumentException("Invalid petrified model-part path");
            }
            if (!Float.isFinite(x)
                    || !Float.isFinite(y)
                    || !Float.isFinite(z)
                    || !Float.isFinite(xRot)
                    || !Float.isFinite(yRot)
                    || !Float.isFinite(zRot)
                    || !Float.isFinite(xScale)
                    || !Float.isFinite(yScale)
                    || !Float.isFinite(zScale)) {
                throw new IllegalArgumentException(
                        "Petrified model-part transforms must be finite");
            }
        }
    }
}
