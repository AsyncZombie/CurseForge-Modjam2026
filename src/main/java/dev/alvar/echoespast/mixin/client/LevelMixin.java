package dev.alvar.echoespast.mixin.client;

import dev.alvar.echoespast.client.ClientEchoState;
import dev.alvar.echoespast.client.EchoShaderCompatibility;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iris shadow terrain samples {@link Level#getBlockState} (declared on Level,
 * not ClientLevel). During the shader shadow pass only, hidden ADDED cells
 * read as air so historical-air rubble cannot cast sun shadows. Collision and
 * the main pass keep the real solid.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(
            method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true)
    private void echoesShowThePast$hideAddedFromShaderShadows(
            BlockPos position,
            CallbackInfoReturnable<BlockState> callback) {
        Level level = (Level) (Object) this;
        if (!level.isClientSide()
                || !EchoShaderCompatibility.isShadowPass()
                || !ClientEchoState.shouldHidePresentBlock(position)) {
            return;
        }
        callback.setReturnValue(Blocks.AIR.defaultBlockState());
    }
}
