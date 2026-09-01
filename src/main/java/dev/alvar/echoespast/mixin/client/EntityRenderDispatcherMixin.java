package dev.alvar.echoespast.mixin.client;

import dev.alvar.echoespast.client.ClientEchoState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes live decorative entities from the reconstructed timeline. Captured
 * counterparts are submitted separately by the ghost renderer.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(
            method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void echoesShowThePast$hidePresentDecoration(
            Entity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ,
            CallbackInfoReturnable<Boolean> callback) {
        if (ClientEchoState.shouldHidePresentEntity(entity)) {
            callback.setReturnValue(false);
        }
    }
}
