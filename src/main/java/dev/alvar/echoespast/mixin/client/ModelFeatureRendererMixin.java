package dev.alvar.echoespast.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alvar.echoespast.client.ClientPetrifiedPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {
    @Inject(
            method = "renderModel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/Model;renderToBuffer("
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
                    ordinal = 0))
    private <S> void echoesShowThePast$freezeEvaluatedBones(
            SubmitNodeStorage.ModelSubmit<S> submit,
            RenderType renderType,
            VertexConsumer buffer,
            OutlineBufferSource outlineBufferSource,
            MultiBufferSource.BufferSource crumblingBufferSource,
            CallbackInfo callback) {
        ClientPetrifiedPose.applyOrCaptureModel(submit.state(), submit.model());
    }
}
