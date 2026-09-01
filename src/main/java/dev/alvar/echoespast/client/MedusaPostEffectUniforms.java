package dev.alvar.echoespast.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.alvar.echoespast.mixin.client.PostChainAccessor;
import dev.alvar.echoespast.mixin.client.PostPassAccessor;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

final class MedusaPostEffectUniforms {
    private static final String CONFIG = "MedusaConfig";
    private static final int CONFIG_SIZE = 80;
    private static final int DYNAMIC_USAGE =
            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;

    static void prepare(Minecraft minecraft, Identifier effect) {
        update(
                minecraft,
                effect,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                false,
                0.0F,
                0.0F,
                0.0F,
                0.0F);
    }

    static void update(
            Minecraft minecraft,
            Identifier effect,
            float strength,
            float channel,
            float impact,
            float cancel,
            float elapsedSeconds,
            float contact,
            float echoDarkening,
            boolean surfaceWaveActive,
            float horusStrength,
            float grailStrength,
            float grailRelease,
            float grailElapsed) {
        RenderSystem.assertOnRenderThread();
        PostChain chain = minecraft.getShaderManager()
                .getPostChain(effect, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            return;
        }
        for (PostPass pass : ((PostChainAccessor) chain).echoesShowThePast$getPasses()) {
            Map<String, GpuBuffer> uniforms =
                    ((PostPassAccessor) pass).echoesShowThePast$getCustomUniforms();
            GpuBuffer buffer = uniforms.get(CONFIG);
            if (buffer == null) {
                continue;
            }
            if ((buffer.usage() & GpuBuffer.USAGE_COPY_DST) == 0
                    || buffer.size() != CONFIG_SIZE) {
                GpuBuffer dynamic = createBuffer(
                        strength,
                        channel,
                        impact,
                        cancel,
                        elapsedSeconds,
                        contact,
                        echoDarkening,
                        surfaceWaveActive,
                        horusStrength,
                        grailStrength,
                        grailRelease,
                        grailElapsed);
                uniforms.put(CONFIG, dynamic);
                buffer.close();
                buffer = dynamic;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Std140Builder builder = Std140Builder.onStack(stack, CONFIG_SIZE);
                write(
                        builder,
                        strength,
                        channel,
                        impact,
                        cancel,
                        elapsedSeconds,
                        contact,
                        echoDarkening,
                        surfaceWaveActive,
                        horusStrength,
                        grailStrength,
                        grailRelease,
                        grailElapsed);
                RenderSystem.getDevice()
                        .createCommandEncoder()
                        .writeToBuffer(buffer.slice(), builder.get());
            }
        }
    }

    private static GpuBuffer createBuffer(
            float strength,
            float channel,
            float impact,
            float cancel,
            float elapsedSeconds,
            float contact,
            float echoDarkening,
            boolean surfaceWaveActive,
            float horusStrength,
            float grailStrength,
            float grailRelease,
            float grailElapsed) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, CONFIG_SIZE);
            write(
                    builder,
                    strength,
                    channel,
                    impact,
                    cancel,
                    elapsedSeconds,
                    contact,
                    echoDarkening,
                    surfaceWaveActive,
                    horusStrength,
                    grailStrength,
                    grailRelease,
                    grailElapsed);
            return RenderSystem.getDevice().createBuffer(
                    () -> "Medusa gaze dynamic grade",
                    DYNAMIC_USAGE,
                    builder.get());
        }
    }

    private static void write(
            Std140Builder builder,
            float strength,
            float channel,
            float impact,
            float cancel,
            float elapsedSeconds,
            float contact,
            float echoDarkening,
            boolean surfaceWaveActive,
            float horusStrength,
            float grailStrength,
            float grailRelease,
            float grailElapsed) {
        builder.putVec4(
                Math.clamp(strength, 0.0F, 1.0F),
                Math.clamp(channel, 0.0F, 1.0F),
                Math.clamp(impact, 0.0F, 1.0F),
                Math.clamp(cancel, 0.0F, 1.0F));
        builder.putVec4(0.31F, 0.56F, 0.37F, elapsedSeconds);
        builder.putVec4(0.73F, 0.67F, 0.47F, 1.0F);
        builder.putVec4(
                Math.clamp(contact, 0.0F, 1.0F),
                Math.clamp(echoDarkening, 0.0F, 1.0F),
                surfaceWaveActive ? 1.0F : 0.0F,
                Math.clamp(horusStrength, 0.0F, 1.0F));
        builder.putVec4(
                Math.clamp(grailStrength, 0.0F, 1.0F),
                Math.clamp(grailRelease, 0.0F, 1.0F),
                grailElapsed,
                0.0F);
    }

    private MedusaPostEffectUniforms() {
    }
}
