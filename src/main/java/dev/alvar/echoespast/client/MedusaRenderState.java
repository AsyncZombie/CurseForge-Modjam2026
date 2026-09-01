package dev.alvar.echoespast.client;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.relic.BakedModelPose;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;

public final class MedusaRenderState {
    public static final ContextKey<Integer> ENTITY_ID = new ContextKey<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "medusa_entity_id"));
    public static final ContextKey<Boolean> PERMANENT = new ContextKey<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "medusa_permanent"));
    public static final ContextKey<Boolean> HEADLESS = new ContextKey<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "medusa_headless"));
    public static final ContextKey<BakedModelPose> ITEM_PREVIEW_POSE = new ContextKey<>(
            Identifier.fromNamespaceAndPath(
                    EchoesShowThePast.MOD_ID,
                    "medusa_item_preview_pose"));

    private MedusaRenderState() {
    }
}
