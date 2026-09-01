package dev.alvar.echoespast.mixin.client;

import dev.alvar.echoespast.client.ClientEchoState;
import dev.alvar.echoespast.client.ClientLowFrequencySonarState;
import dev.alvar.echoespast.client.EchoBlockParticleTracker;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Invalidates the remembered/present comparison after the client accepts an
 * actual block-state change. Rebuild work is coalesced to once per client tick.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Unique
    private static final ThreadLocal<ParticleEmission> echoesShowThePast$particleEmission =
            new ThreadLocal<>();

    @Inject(
            method = "setBlocksDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("TAIL"))
    private void echoesShowThePast$trackEchoBlockChange(
            BlockPos position,
            BlockState oldState,
            BlockState newState,
            CallbackInfo callback) {
        ClientEchoState.onBlockChanged(position);
        ClientLowFrequencySonarState.onBlockChanged(position);
    }

    /**
     * Carries the visibility of the exact block currently producing ambient
     * particles. The block tick itself still runs, so sounds and non-particle
     * behavior remain untouched.
     */
    @Redirect(
            method = "doAnimateTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;animateTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void echoesShowThePast$trackParticleEmitter(
            Block block,
            BlockState state,
            Level level,
            BlockPos position,
            RandomSource random) {
        ParticleEmission previous = echoesShowThePast$particleEmission.get();
        echoesShowThePast$particleEmission.set(new ParticleEmission(
                position.immutable(),
                ClientEchoState.tracksPresentBlockParticles(position),
                ClientEchoState.presentBlockParticleVisibility(position),
                random));
        try {
            block.animateTick(state, level, position, random);
        } finally {
            if (previous == null) {
                echoesShowThePast$particleEmission.remove();
            } else {
                echoesShowThePast$particleEmission.set(previous);
            }
        }
    }

    @Redirect(
            method = "doAddParticle",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleEngine;createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;"))
    private Particle echoesShowThePast$rememberGhostBlockParticle(
            ParticleEngine engine,
            ParticleOptions particle,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ) {
        Particle created = engine.createParticle(
                particle,
                x,
                y,
                z,
                velocityX,
                velocityY,
                velocityZ);
        ParticleEmission emission = echoesShowThePast$particleEmission.get();
        if (created != null && emission != null && emission.temporal()) {
            EchoBlockParticleTracker.track(created, emission.position());
        }
        return created;
    }

    @Inject(method = "doAddParticle", at = @At("HEAD"), cancellable = true)
    private void echoesShowThePast$fadeGhostBlockParticle(
            ParticleOptions particle,
            boolean overrideLimiter,
            boolean alwaysShow,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            CallbackInfo callback) {
        ParticleEmission emission = echoesShowThePast$particleEmission.get();
        if (emission != null
                && emission.temporal()
                && emission.visibility() < 0.999F
                && (emission.visibility() <= 0.001F
                || emission.random().nextFloat() > emission.visibility())) {
            callback.cancel();
        }
    }

    @Unique
    private record ParticleEmission(
            BlockPos position,
            boolean temporal,
            float visibility,
            RandomSource random) {
    }
}
