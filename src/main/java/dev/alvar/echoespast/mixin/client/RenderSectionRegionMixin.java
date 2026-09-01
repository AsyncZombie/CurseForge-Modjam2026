package dev.alvar.echoespast.mixin.client;

import dev.alvar.echoespast.client.ClientEchoState;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes blocks that occupy remembered air only from client chunk meshes.
 * Their real state and collision remain untouched; the echo renderer draws their textured translucent replacement.
 */
@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionMixin {
    @Shadow
    public abstract LevelLightEngine getLightEngine();

    /**
     * Chunk geometry retained by both timelines receives the brighter value
     * from the live and reconstructed light fields.
     */
    public int getBrightness(LightLayer layer, BlockPos position) {
        int present = this.getLightEngine().getLayerListener(layer).getLightValue(position);
        return switch (layer) {
            case BLOCK -> ClientEchoState.combinedBlockLight(position, present);
            case SKY -> ClientEchoState.combinedSkyLight(position, present);
        };
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void echoesShowThePast$replaceRememberedAir(
            BlockPos position,
            CallbackInfoReturnable<BlockState> callback) {
        if (ClientEchoState.shouldHidePresentBlock(position)) {
            callback.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
