package dev.alvar.echoespast.mixin.client;

import dev.alvar.echoespast.client.ClientMedusaVision;
import dev.alvar.echoespast.client.ClientPetrifiedMining;
import dev.alvar.echoespast.client.MedusaRenderState;
import dev.alvar.echoespast.client.MedusaRenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Every vanilla entity feature eventually submits a model here. A finished
 * statue replaces the normal pass instead of layering translucent stone over
 * it, so shaderpacks cannot reorder the creature texture above the material.
 * During the short petrification transition both passes are retained.
 */
@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin {
    @Redirect(
            method = "submitModel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$Storage;add("
                            + "Lnet/minecraft/client/renderer/rendertype/RenderType;"
                            + "Lnet/minecraft/client/renderer/SubmitNodeStorage$ModelSubmit;)V"))
    private void echoesShowThePast$submitPetrifiedMaterial(
            ModelFeatureRenderer.Storage storage,
            RenderType renderType,
            SubmitNodeStorage.ModelSubmit<?> originalSubmit) {
        Object state = originalSubmit.state();
        if (!(state instanceof LivingEntityRenderState livingState)) {
            storage.add(renderType, originalSubmit);
            return;
        }
        Integer entityId = livingState.getRenderData(MedusaRenderState.ENTITY_ID);
        if (entityId == null) {
            storage.add(renderType, originalSubmit);
            return;
        }
        boolean permanent = Boolean.TRUE.equals(
                livingState.getRenderData(MedusaRenderState.PERMANENT));
        float petrification = ClientMedusaVision.petrificationProgress(
                entityId,
                permanent);
        if (petrification <= 0.002F) {
            storage.add(renderType, originalSubmit);
            return;
        }
        boolean fullyPetrified = petrification >= 0.998F;
        if (fullyPetrified && MedusaRenderTypes.isEmissiveLayer(renderType)) {
            return;
        }
        Identifier sourceTexture = MedusaRenderTypes.sourceTexture(renderType);
        if (sourceTexture == null) {
            storage.add(renderType, originalSubmit);
            return;
        }

        int stoneAlpha = Math.clamp(
                Math.round(petrification * 255.0F),
                0,
                255);
        if (!fullyPetrified) {
            storage.add(renderType, originalSubmit);
        }
        storage.add(
                MedusaRenderTypes.stone(sourceTexture, !fullyPetrified),
                withMaterialTint(originalSubmit, (stoneAlpha << 24) | 0xFFFFFF));

        float mining = permanent
                ? ClientPetrifiedMining.progress(entityId)
                : 0.0F;
        if (mining <= 0.001F) {
            return;
        }
        int stage = Math.clamp((int) (mining * 10.0F), 0, 9);
        float pulse = ClientPetrifiedMining.impact(entityId);
        int crackAlpha = Math.clamp(
                Math.round((0.68F + pulse * 0.24F) * 255.0F),
                0,
                255);
        storage.add(
                MedusaRenderTypes.crack(sourceTexture, stage),
                withMaterialTint(originalSubmit, (crackAlpha << 24) | 0xFFFFFF));
    }

    private static <S> SubmitNodeStorage.ModelSubmit<S> withMaterialTint(
            SubmitNodeStorage.ModelSubmit<S> submit,
            int tintedColor) {
        return new SubmitNodeStorage.ModelSubmit<>(
                submit.pose(),
                submit.model(),
                submit.state(),
                submit.lightCoords(),
                OverlayTexture.NO_OVERLAY,
                tintedColor,
                submit.sprite(),
                submit.outlineColor(),
                null);
    }
}
