package dev.alvar.echoespast.mixin.client;

import dev.alvar.echoespast.client.ClientLowFrequencySonarState;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * During low-frequency listening, ordinary sounds are gently pushed back while
 * the resonator and its answers retain their authored volume.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Redirect(
            method = {
                "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
                "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F"))
    private float echoesShowThePast$focusOnEcho(SoundInstance instance) {
        return instance.getVolume()
                * ClientLowFrequencySonarState.ambientAudioMultiplier(instance.getIdentifier());
    }
}
