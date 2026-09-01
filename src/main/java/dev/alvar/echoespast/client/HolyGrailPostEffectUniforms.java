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

final class HolyGrailPostEffectUniforms {
    private static final String CONFIG = "GrailConfig";
    private static final int CONFIG_SIZE = 80;
    private static final int DYNAMIC_USAGE =
            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;

    static void prepare(Minecraft minecraft, Identifier effect) {
        update(
                minecraft,
                effect,
                ClientHolyGrailVision.GrailVisual.NONE,
                0.0F,
                false,
                0.0F);
    }

    static void update(
            Minecraft minecraft,
            Identifier effect,
            ClientHolyGrailVision.GrailVisual visual,
            float echoDarkening,
            boolean surfaceWaveActive,
            float horusStrength) {
        RenderSystem.assertOnRenderThread();
        PostChain chain = minecraft.getShaderManager()
                .getPostChain(effect, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            return;
        }
        for (PostPass pass :
                ((PostChainAccessor) chain).echoesShowThePast$getPasses()) {
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
                        echoDarkening,
                        surfaceWaveActive,
                        horusStrength);
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
                        echoDarkening,
                        surfaceWaveActive,
                        horusStrength);
                RenderSystem.getDevice()
                        .createCommandEncoder()
                        .writeToBuffer(buffer.slice(), builder.get());
            }
        }
    }

    private static GpuBuffer createBuffer(
            ClientHolyGrailVision.GrailVisual visual,
            float echoDarkening,
            boolean surfaceWaveActive,
            float horusStrength) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder =
                    Std140Builder.onStack(stack, CONFIG_SIZE);
            write(
                    builder,
                    visual,
                    echoDarkening,
                    surfaceWaveActive,
                    horusStrength);
            return RenderSystem.getDevice().createBuffer(
                    () -> "Holy Grail dynamic vision grade",
                    DYNAMIC_USAGE,
                    builder.get());
        }
    }

    private static void write(
            Std140Builder builder,
            ClientHolyGrailVision.GrailVisual visual,
            float echoDarkening,
            boolean surfaceWaveActive,
            float horusStrength) {
        builder.putVec4(
                Math.clamp(visual.strength(), 0.0F, 1.0F),
                Math.clamp(visual.channel(), 0.0F, 1.0F),
                Math.clamp(visual.release(), 0.0F, 1.0F),
                Math.clamp(visual.aura(), 0.0F, 1.0F));
        builder.putVec4(
                0.29F,
                0.78F,
                0.91F,
                visual.elapsedSeconds());
        builder.putVec4(1.00F, 0.86F, 0.49F, visual.recharge());
        builder.putVec4(
                Math.clamp(echoDarkening, 0.0F, 1.0F),
                surfaceWaveActive ? 1.0F : 0.0F,
                Math.clamp(horusStrength, 0.0F, 1.0F),
                0.0F);
        builder.putVec4(0.56F, 0.34F, 0.20F, 0.0F);
    }

    private HolyGrailPostEffectUniforms() {
    }
}
