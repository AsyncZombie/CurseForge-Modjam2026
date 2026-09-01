package dev.alvar.echoespast.mixin.client;

import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SingleQuadParticle.class)
public interface SingleQuadParticleAccessor {
    @Accessor("alpha")
    float echoesShowThePast$getAlpha();

    @Accessor("alpha")
    void echoesShowThePast$setAlpha(float alpha);
}
