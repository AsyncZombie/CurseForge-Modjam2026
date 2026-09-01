package dev.alvar.echoespast.visual;

import java.util.List;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Null-safe ownership checks for Minecraft's optional current post effect.
 */
public final class EchoPostEffects {
    public static boolean contains(List<Identifier> stages, @Nullable Identifier effect) {
        return effect != null && stages.contains(effect);
    }

    private EchoPostEffects() {
    }
}
