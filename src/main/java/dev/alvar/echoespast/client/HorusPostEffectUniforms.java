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

final class HorusPostEffectUniforms {
    private static final String HORUS_CONFIG = "HorusConfig";
    private static final int HORUS_CONFIG_SIZE = 64;
    private static final int DYNAMIC_UNIFORM_USAGE =
            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;

    static void prepare(Minecraft minecraft, Identifier effect) {
        update(minecraft, effect, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    static void update(
            Minecraft minecraft,
            Identifier effect,
            float strength,
            float opening,
            float closing,
            float elapsedSeconds) {
        RenderSystem.assertOnRenderThread();
        PostChain chain = minecraft.getShaderManager()
                .getPostChain(effect, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            return;
        }
        for (PostPass pass : ((PostChainAccessor) chain).echoesShowThePast$getPasses()) {
            Map<String, GpuBuffer> uniforms =
                    ((PostPassAccessor) pass).echoesShowThePast$getCustomUniforms();
            GpuBuffer buffer = uniforms.get(HORUS_CONFIG);
            if (buffer == null) {
                continue;
            }
            if ((buffer.usage() & GpuBuffer.USAGE_COPY_DST) == 0
                    || buffer.size() != HORUS_CONFIG_SIZE) {
                GpuBuffer dynamicBuffer =
                        createBuffer(strength, opening, closing, elapsedSeconds);
                uniforms.put(HORUS_CONFIG, dynamicBuffer);
                buffer.close();
                buffer = dynamicBuffer;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Std140Builder builder = Std140Builder.onStack(stack, HORUS_CONFIG_SIZE);
                writeConfig(builder, strength, opening, closing, elapsedSeconds);
                RenderSystem.getDevice()
                        .createCommandEncoder()
                        .writeToBuffer(buffer.slice(), builder.get());
            }
        }
    }

    private static GpuBuffer createBuffer(
            float strength,
            float opening,
            float closing,
            float elapsedSeconds) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, HORUS_CONFIG_SIZE);
            writeConfig(builder, strength, opening, closing, elapsedSeconds);
            return RenderSystem.getDevice().createBuffer(
                    () -> "Eye of Horus dynamic vision grade",
                    DYNAMIC_UNIFORM_USAGE,
                    builder.get());
        }
    }

    private static void writeConfig(
            Std140Builder builder,
            float strength,
            float opening,
            float closing,
            float elapsedSeconds) {
        builder.putVec4(
                Math.clamp(strength, 0.0F, 1.0F),
                Math.clamp(opening, 0.0F, 1.0F),
                Math.clamp(closing, 0.0F, 1.0F),
                elapsedSeconds);
        builder.putVec4(1.00F, 0.62F, 0.16F, 1.0F);
        builder.putVec4(0.24F, 0.70F, 0.54F, 0.0F);
        builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
    }

    private HorusPostEffectUniforms() {
    }
}
