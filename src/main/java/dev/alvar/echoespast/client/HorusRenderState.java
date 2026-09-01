package dev.alvar.echoespast.client;

import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;

final class HorusRenderState {
    static final ContextKey<Float> AURA_STRENGTH = new ContextKey<>(
            Identifier.fromNamespaceAndPath(
                    EchoesShowThePast.MOD_ID,
                    "horus_aura_strength"));

    private HorusRenderState() {
    }
}
