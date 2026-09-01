package dev.alvar.echoespast.mixin.client;

import dev.alvar.echoespast.client.EchoBlockParticleTracker;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void echoesShowThePast$restoreTrackedParticleAlpha(CallbackInfo callback) {
        EchoBlockParticleTracker.beforeParticleTick();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void echoesShowThePast$fadeTrackedParticleAlpha(CallbackInfo callback) {
        EchoBlockParticleTracker.afterParticleTick();
    }
}
