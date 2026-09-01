package dev.alvar.echoespast.mixin.client;

import dev.alvar.echoespast.client.ClientUnknownEnterCinematic;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class LocalPlayerLookMixin {
    @Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true)
    private void echoesShowThePast$lockEnterCinematicLook(
            double yRot,
            double xRot,
            CallbackInfo callbackInfo) {
        if ((Object) this instanceof LocalPlayer && ClientUnknownEnterCinematic.isControlling()) {
            callbackInfo.cancel();
        }
    }
}
