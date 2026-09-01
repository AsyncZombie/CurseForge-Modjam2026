package dev.alvar.echoespast.mixin.client;

import dev.alvar.echoespast.client.ClientEchoState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mirrors {@link RenderSectionRegionMixin} for Sodium's private chunk snapshot.
 *
 * <p>The string target and {@link Pseudo} keep Sodium strictly optional. When
 * it is installed, its mesher reads this integer overload directly and would
 * otherwise bake the live opaque block behind our translucent replacement.
 */
@Pseudo
@Mixin(
        targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice",
        remap = false)
public abstract class SodiumLevelSliceMixin {
    @Inject(
            method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void echoesShowThePast$replaceRememberedAir(
            int x,
            int y,
            int z,
            CallbackInfoReturnable<BlockState> callback) {
        if (ClientEchoState.shouldHidePresentBlock(
                x,
                y,
                z)) {
            callback.setReturnValue(
                    Blocks.AIR.defaultBlockState());
        }
    }

    /**
     * Sodium snapshots Minecraft's raw light arrays before meshing and never
     * reaches RenderSectionRegionMixin#getBrightness. Recombine that cached
     * value with the same remembered light field used by vanilla so a block
     * occupying remembered air cannot leave an impossible light shadow.
     */
    @Inject(
            method = "getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false)
    private void echoesShowThePast$useRememberedLight(
            LightLayer layer,
            BlockPos position,
            CallbackInfoReturnable<Integer> callback) {
        int present = callback.getReturnValue();
        int combined = switch (layer) {
            case BLOCK -> ClientEchoState.combinedBlockLight(
                    position.getX(),
                    position.getY(),
                    position.getZ(),
                    present);
            case SKY -> ClientEchoState.combinedSkyLight(
                    position.getX(),
                    position.getY(),
                    position.getZ(),
                    present);
        };
        callback.setReturnValue(combined);
    }
}
