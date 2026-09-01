package dev.alvar.echoespast.mixin.client;

import dev.alvar.echoespast.client.ClientEchoState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The chunk-state mixin removes ordinary block meshes. Block entities travel
 * through a separate renderer, so they must yield at that boundary as well.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(
            method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Lnet/minecraft/client/renderer/culling/Frustum;)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
            at = @At("HEAD"),
            cancellable = true)
    private void echoesShowThePast$hidePresentBlockEntity(
            BlockEntity blockEntity,
            float partialTicks,
            ModelFeatureRenderer.CrumblingOverlay breakProgress,
            Frustum frustum,
            CallbackInfoReturnable<BlockEntityRenderState> callback) {
        if (ClientEchoState.shouldHidePresentBlockEntity(
                blockEntity.getBlockPos())) {
            callback.setReturnValue(null);
        }
    }
}
