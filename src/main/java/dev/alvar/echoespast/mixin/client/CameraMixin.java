package dev.alvar.echoespast.mixin.client;

import dev.alvar.echoespast.client.ClientUnknownEnterCinematic;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot, float roll);

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void echoesShowThePast$enterCinematic(float partialTick, CallbackInfo callbackInfo) {
        var shot = ClientUnknownEnterCinematic.overrideShot(
                (Camera) (Object) this,
                partialTick);
        if (shot != null) {
            setRotation(shot.yaw(), shot.pitch(), shot.roll());
            setPosition(shot.position());
        }
    }
}
