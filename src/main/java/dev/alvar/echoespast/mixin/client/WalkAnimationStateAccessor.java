package dev.alvar.echoespast.mixin.client;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {
    @Accessor("speedOld")
    void echoesShowThePast$setSpeedOld(float speed);

    @Accessor("speed")
    void echoesShowThePast$setSpeed(float speed);

    @Accessor("position")
    void echoesShowThePast$setPosition(float position);

    @Accessor("positionScale")
    void echoesShowThePast$setPositionScale(float scale);
}
