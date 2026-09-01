package dev.alvar.echoespast.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import dev.alvar.echoespast.client.EchoWorldDepthCapture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    @Final
    private LevelTargetBundle targets;

    /**
     * addMainPass is the one world pass Minecraft always creates. Capturing at
     * its return avoids depending on optional cloud or weather passes.
     *
     * Declaring a read/write version is intentional: the texture itself is not
     * changed, but the new frame-graph handle forms a barrier so every later
     * atmospheric pass runs after the depth copy.
     */
    @Inject(
            method = "addMainPass("
                    + "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;"
                    + "Lnet/minecraft/client/renderer/culling/Frustum;"
                    + "Lorg/joml/Matrix4fc;"
                    + "Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
                    + "Z"
                    + "Lnet/minecraft/client/renderer/state/level/LevelRenderState;"
                    + "Lnet/minecraft/client/DeltaTracker;"
                    + "Lnet/minecraft/util/profiling/ProfilerFiller;"
                    + "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V",
            at = @At("RETURN"))
    private void echoesShowThePast$captureAfterMainPass(
            FrameGraphBuilder frame,
            Frustum frustum,
            Matrix4fc modelViewMatrix,
            GpuBufferSlice terrainFog,
            boolean renderOutline,
            LevelRenderState renderState,
            DeltaTracker deltaTracker,
            ProfilerFiller profiler,
            ChunkSectionsToRender sections,
            CallbackInfo callbackInfo) {
        echoesShowThePast$scheduleDepthCapture(frame);
    }

    private void echoesShowThePast$scheduleDepthCapture(FrameGraphBuilder frame) {
        if (!EchoWorldDepthCapture.shouldCapture()) {
            return;
        }
        FramePass capture = frame.addPass("echoes_show_the_past_world_depth");
        ResourceHandle<RenderTarget> worldTarget =
                capture.readsAndWrites(targets.main);
        targets.main = worldTarget;
        capture.executes(() ->
                EchoWorldDepthCapture.captureWorldDepth(worldTarget.get()));
    }
}
