package dev.alvar.echoespast.mixin.client;

import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Minecraft's immediate-buffer stack drops the RenderType name when a GPU
 * validation error escapes from a batch. Preserve that boundary in the crash
 * report so shader-pack compatibility failures identify the actual material.
 */
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class BufferSourceDiagnosticsMixin {
    @Redirect(
            method = "endBatch(Lnet/minecraft/client/renderer/rendertype/RenderType;"
                    + "Lcom/mojang/blaze3d/vertex/BufferBuilder;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/rendertype/RenderType;"
                            + "draw(Lcom/mojang/blaze3d/vertex/MeshData;)V"))
    private void echoesShowThePast$identifyFailedBatch(
            RenderType renderType,
            MeshData mesh) {
        try {
            renderType.draw(mesh);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Render batch failed for "
                            + renderType
                            + " using pipeline "
                            + renderType.pipeline().getLocation(),
                    exception);
        }
    }
}
