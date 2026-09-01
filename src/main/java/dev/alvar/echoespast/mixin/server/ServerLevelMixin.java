package dev.alvar.echoespast.mixin.server;

import dev.alvar.echoespast.server.MaterializedEchoManager;
import dev.alvar.echoespast.world.TimelessFireRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "tickBlock", at = @At("HEAD"), cancellable = true)
    private void echoesShowThePast$deferScheduledBlock(
            BlockPos position,
            Block block,
            CallbackInfo callback) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (TimelessFireRules.freezesFireTick(level.dimension(), block)) {
            callback.cancel();
            return;
        }
        if (MaterializedEchoManager.deferBlockTick(
                level,
                position,
                block)) {
            callback.cancel();
        }
    }

    @Inject(method = "tickFluid", at = @At("HEAD"), cancellable = true)
    private void echoesShowThePast$deferScheduledFluid(
            BlockPos position,
            Fluid fluid,
            CallbackInfo callback) {
        if (MaterializedEchoManager.deferFluidTick(
                (ServerLevel) (Object) this,
                position,
                fluid)) {
            callback.cancel();
        }
    }

    @Redirect(
            method = "tickChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void echoesShowThePast$skipRandomBlockTick(
            BlockState state,
            ServerLevel level,
            BlockPos position,
            RandomSource random) {
        if (!MaterializedEchoManager.isProtected(level, position)) {
            state.randomTick(level, position, random);
        }
    }

    @Redirect(
            method = "tickChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void echoesShowThePast$skipRandomFluidTick(
            FluidState state,
            ServerLevel level,
            BlockPos position,
            RandomSource random) {
        if (!MaterializedEchoManager.isProtected(level, position)
                && !TimelessFireRules.suppressesLavaIgnition(level.dimension(), state)) {
            state.randomTick(level, position, random);
        }
    }
}
