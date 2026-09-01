package dev.alvar.echoespast.client;

import dev.alvar.echoespast.mixin.client.SingleQuadParticleAccessor;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;

/**
 * Keeps particles already emitted by a changed block tied to their source.
 * This matters especially for long-lived campfire smoke: thinning only newly
 * spawned particles would otherwise leave old smoke floating in the past view.
 */
public final class EchoBlockParticleTracker {
    private static final Map<Particle, TrackedParticle> TRACKED = new IdentityHashMap<>();

    public static void track(Particle particle, BlockPos source) {
        float baseAlpha = particle instanceof SingleQuadParticleAccessor accessor
                ? accessor.echoesShowThePast$getAlpha()
                : 1.0F;
        TRACKED.put(particle, new TrackedParticle(source.immutable(), baseAlpha));
    }

    /**
     * Restores vanilla alpha before the particle's own tick updates it.
     */
    public static void beforeParticleTick() {
        Iterator<Map.Entry<Particle, TrackedParticle>> iterator = TRACKED.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Particle, TrackedParticle> entry = iterator.next();
            Particle particle = entry.getKey();
            if (!particle.isAlive()) {
                iterator.remove();
                continue;
            }
            if (particle instanceof SingleQuadParticleAccessor accessor) {
                accessor.echoesShowThePast$setAlpha(entry.getValue().baseAlpha());
            }
        }
    }

    /**
     * Applies the current temporal visibility after vanilla has advanced each
     * particle. When the echo ends, alpha is restored and tracking is released.
     */
    public static void afterParticleTick() {
        Iterator<Map.Entry<Particle, TrackedParticle>> iterator = TRACKED.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Particle, TrackedParticle> entry = iterator.next();
            Particle particle = entry.getKey();
            TrackedParticle tracked = entry.getValue();
            if (!particle.isAlive()) {
                iterator.remove();
                continue;
            }

            float baseAlpha = tracked.baseAlpha();
            if (particle instanceof SingleQuadParticleAccessor accessor) {
                baseAlpha = accessor.echoesShowThePast$getAlpha();
                float visibility = ClientEchoState.presentBlockParticleVisibility(tracked.source());
                accessor.echoesShowThePast$setAlpha(baseAlpha * visibility);
            }
            entry.setValue(new TrackedParticle(tracked.source(), baseAlpha));

            if (!ClientEchoState.tracksPresentBlockParticles(tracked.source())) {
                iterator.remove();
            }
        }
    }

    private record TrackedParticle(BlockPos source, float baseAlpha) {
    }

    private EchoBlockParticleTracker() {
    }
}
