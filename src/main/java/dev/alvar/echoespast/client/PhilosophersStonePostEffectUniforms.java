package dev.alvar.echoespast.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.mixin.client.PostChainAccessor;
import dev.alvar.echoespast.mixin.client.PostPassAccessor;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

final class PhilosophersStonePostEffectUniforms {
    private static final String CONFIG = "PhilosophersStoneConfig";
    private static final int CONFIG_SIZE = 208;
    private static final int DYNAMIC_USAGE =
            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;

    static void prepare(Minecraft minecraft, Identifier effect) {
        update(
                minecraft,
                effect,
                ClientPhilosophersStoneVision.Visual.NONE,
                ClientPhilosophersStoneVision.ViewFrame.EMPTY,
                0.0F,
                false,
                0.0F,
                ClientMedusaVision.Composite.NONE,
                0.0F,
                0.0F);
    }

    static void update(
            Minecraft minecraft,
            Identifier effect,
            ClientPhilosophersStoneVision.Visual visual,
            ClientPhilosophersStoneVision.ViewFrame frame,
            float echoDarkening,
            boolean surfaceWaveActive,
            float horusStrength,
            ClientMedusaVision.Composite medusa,
            float grailStrength,
            float grailRelease) {
        RenderSystem.assertOnRenderThread();
        PostChain chain = minecraft.getShaderManager()
                .getPostChain(effect, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            return;
        }
        for (PostPass pass :
                ((PostChainAccessor) chain).echoesShowThePast$getPasses()) {
            EchoWorldDepthCapture.attachTo(
                    pass,
                    minecraft.getMainRenderTarget().width,
                    minecraft.getMainRenderTarget().height);
            Map<String, GpuBuffer> uniforms =
                    ((PostPassAccessor) pass)
                            .echoesShowThePast$getCustomUniforms();
            GpuBuffer buffer = uniforms.get(CONFIG);
            if (buffer == null) {
                continue;
            }
            if ((buffer.usage() & GpuBuffer.USAGE_COPY_DST) == 0
                    || buffer.size() != CONFIG_SIZE) {
                GpuBuffer dynamic = createBuffer(
                        visual,
                        frame,
                        echoDarkening,
                        surfaceWaveActive,
                        horusStrength,
                        medusa,
                        grailStrength,
                        grailRelease);
                uniforms.put(CONFIG, dynamic);
                buffer.close();
                buffer = dynamic;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Std140Builder builder =
                        Std140Builder.onStack(stack, CONFIG_SIZE);
                write(
                        builder,
                        visual,
                        frame,
                        echoDarkening,
                        surfaceWaveActive,
                        horusStrength,
                        medusa,
                        grailStrength,
                        grailRelease);
                RenderSystem.getDevice()
                        .createCommandEncoder()
                        .writeToBuffer(buffer.slice(), builder.get());
            }
        }
    }

    private static GpuBuffer createBuffer(
            ClientPhilosophersStoneVision.Visual visual,
            ClientPhilosophersStoneVision.ViewFrame frame,
            float echoDarkening,
            boolean surfaceWaveActive,
            float horusStrength,
            ClientMedusaVision.Composite medusa,
            float grailStrength,
            float grailRelease) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder =
                    Std140Builder.onStack(stack, CONFIG_SIZE);
            write(
                    builder,
                    visual,
                    frame,
                    echoDarkening,
                    surfaceWaveActive,
                    horusStrength,
                    medusa,
                    grailStrength,
                    grailRelease);
            return RenderSystem.getDevice().createBuffer(
                    () -> "Philosopher's Stone temporal transition",
                    DYNAMIC_USAGE,
                    builder.get());
        }
    }

    private static void write(
            Std140Builder builder,
            ClientPhilosophersStoneVision.Visual visual,
            ClientPhilosophersStoneVision.ViewFrame frame,
            float echoDarkening,
            boolean surfaceWaveActive,
            float horusStrength,
            ClientMedusaVision.Composite medusa,
            float grailStrength,
            float grailRelease) {
        builder.putVec4(
                Math.clamp(visual.strength(), 0.0F, 1.0F),
                visual.front(),
                visual.stablePast()
                        ? 0.0F
                        : visual.restoring()
                                ? -1.0F
                                : 1.0F,
                visual.elapsedSeconds());
        builder.putVec4(
                frame.center().x,
                frame.center().y,
                frame.center().z,
                frame.halfSpan());
        builder.putVec4(
                frame.axisX().x,
                frame.axisX().y,
                frame.axisX().z,
                frame.halfExtents().x);
        builder.putVec4(
                frame.axisY().x,
                frame.axisY().y,
                frame.axisY().z,
                frame.halfExtents().y);
        builder.putVec4(
                frame.axisZ().x,
                frame.axisZ().y,
                frame.axisZ().z,
                frame.halfExtents().z);
        builder.putVec4(
                frame.sweep().x,
                frame.sweep().y,
                frame.sweep().z,
                frame.depthZeroToOne() ? 1.0F : 0.0F);
        builder.putVec4(
                Math.clamp(echoDarkening, 0.0F, 1.0F),
                surfaceWaveActive ? 1.0F : 0.0F,
                Math.clamp(horusStrength, 0.0F, 1.0F),
                Math.clamp(medusa.strength(), 0.0F, 1.0F));
        builder.putVec4(
                Math.clamp(grailStrength, 0.0F, 1.0F),
                Math.clamp(grailRelease, 0.0F, 1.0F),
                Math.clamp(visual.progress(), 0.0F, 1.0F),
                Math.clamp(
                        EchoesConfig.DISTORTION.get().floatValue(),
                        0.0F,
                        2.0F));
        builder.putVec4(
                Math.clamp(medusa.channelProgress(), 0.0F, 1.0F),
                Math.clamp(medusa.impactProgress(), 0.0F, 1.0F),
                Math.clamp(medusa.cancelProgress(), 0.0F, 1.0F),
                medusa.elapsedSeconds());
        builder.putMat4f(frame.inverseProjection());
    }

    private PhilosophersStonePostEffectUniforms() {
    }
}
