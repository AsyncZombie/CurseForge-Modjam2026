package dev.alvar.echoespast.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.mixin.client.PostChainAccessor;
import dev.alvar.echoespast.mixin.client.PostPassAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

/**
 * Keeps one post chain alive and updates its strength in-place. The previous
 * staged implementation instantiated 24 separate chains during one fade.
 */
final class EchoPostEffectUniforms {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ECHO_CONFIG = "EchoConfig";
    private static final int ECHO_CONFIG_SIZE = 896;
    private static final int DYNAMIC_UNIFORM_USAGE =
            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
    private static boolean nonEmptyWaveUploadLogged;
    private static boolean surfaceWaveGateLogged;

    static void prepare(Minecraft minecraft, Identifier effect) {
        update(minecraft, effect, 0.0F);
    }

    static void update(Minecraft minecraft, Identifier effect, float strength) {
        RenderSystem.assertOnRenderThread();
        PostChain chain = minecraft.getShaderManager()
                .getPostChain(effect, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            return;
        }
        for (PostPass pass : ((PostChainAccessor) chain).echoesShowThePast$getPasses()) {
            EchoWorldDepthCapture.attachTo(
                    pass,
                    minecraft.getMainRenderTarget().width,
                    minecraft.getMainRenderTarget().height);
            Map<String, GpuBuffer> uniforms =
                    ((PostPassAccessor) pass).echoesShowThePast$getCustomUniforms();
            GpuBuffer buffer = uniforms.get(ECHO_CONFIG);
            if (buffer == null) {
                continue;
            }
            if ((buffer.usage() & GpuBuffer.USAGE_COPY_DST) == 0
                    || buffer.size() != ECHO_CONFIG_SIZE) {
                GpuBuffer dynamicBuffer = createBuffer(strength);
                uniforms.put(ECHO_CONFIG, dynamicBuffer);
                buffer.close();
                buffer = dynamicBuffer;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Std140Builder builder = Std140Builder.onStack(stack, ECHO_CONFIG_SIZE);
                writeConfig(builder, strength);
                RenderSystem.getDevice()
                        .createCommandEncoder()
                        .writeToBuffer(buffer.slice(), builder.get());
            }
        }
    }

    private static GpuBuffer createBuffer(float strength) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, ECHO_CONFIG_SIZE);
            writeConfig(builder, strength);
            return RenderSystem.getDevice().createBuffer(
                    () -> "Echoes Show the Past dynamic post grade and distant waves",
                    DYNAMIC_UNIFORM_USAGE,
                    builder.get());
        }
    }

    private static void writeConfig(Std140Builder builder, float strength) {
        float distortion = EchoesConfig.DISTORTION.get().floatValue() * 0.31F;
        long now = System.nanoTime();
        builder.putVec4(0.76F, 0.94F, 1.0F, 0.56F);
        builder.putVec4(
                1.0F,
                0.31F,
                distortion,
                Math.clamp(strength, 0.0F, 1.0F));
        LowFrequencySonarRenderer.ScreenWaveFrame pastFrame =
                ClientEchoRenderer.screenWaveFrame();
        LowFrequencySonarRenderer.ScreenWaveFrame sonarFrame =
                LowFrequencySonarRenderer.screenWaveFrame();
        LowFrequencySonarRenderer.ScreenWaveFrame projectionFrame =
                pastFrame.waves().isEmpty() ? sonarFrame : pastFrame;
        List<LowFrequencySonarRenderer.ScreenWave> waves = new ArrayList<>(
                LowFrequencySonarRenderer.MAX_SCREEN_WAVES);
        appendWaves(waves, pastFrame.waves());
        appendWaves(waves, sonarFrame.waves());
        builder.putMat4f(projectionFrame.inverseProjection());
        int waveCount = Math.min(
                LowFrequencySonarRenderer.MAX_SCREEN_WAVES,
                waves.size());
        if (waveCount > 0 && !nonEmptyWaveUploadLogged) {
            nonEmptyWaveUploadLogged = true;
            LowFrequencySonarRenderer.ScreenWave first = waves.getFirst();
            LOGGER.info(
                    "Echo shader wave upload: count={}, origin={}, radius={}, width={},"
                            + " intensity={}, depthZeroToOne={}",
                    waveCount,
                    first.viewOrigin(),
                    first.radius(),
                    first.widthScale(),
                    first.intensity(),
                    projectionFrame.depthZeroToOne());
        }
        boolean surfaceWaveActive =
                ClientEchoState.isSurfaceWaveActive(now);
        builder.putVec4(
                waveCount,
                projectionFrame.depthZeroToOne() ? 1.0F : 0.0F,
                surfaceWaveActive ? 1.0F : 0.0F,
                0.0F);
        if (surfaceWaveActive && !surfaceWaveGateLogged) {
            surfaceWaveGateLogged = true;
            LOGGER.info(
                    "Echo shader surface marker enabled: strength={}, distantWaveCount={}",
                    strength,
                    waveCount);
        }
        for (int index = 0; index < LowFrequencySonarRenderer.MAX_SCREEN_WAVES; index++) {
            if (index < waveCount) {
                LowFrequencySonarRenderer.ScreenWave wave = waves.get(index);
                builder.putVec4(
                        wave.viewOrigin().x,
                        wave.viewOrigin().y,
                        wave.viewOrigin().z,
                        wave.radius());
            } else {
                builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
        }
        for (int index = 0; index < LowFrequencySonarRenderer.MAX_SCREEN_WAVES; index++) {
            if (index < waveCount) {
                LowFrequencySonarRenderer.ScreenWave wave = waves.get(index);
                builder.putVec4(
                        wave.widthScale(),
                        wave.returning() ? 1.0F : 0.0F,
                        wave.intensity(),
                        1.0F);
            } else {
                builder.putVec4(1.0F, 0.0F, 0.0F, 0.0F);
            }
        }
        for (int index = 0; index < LowFrequencySonarRenderer.MAX_SCREEN_WAVES; index++) {
            if (index < waveCount) {
                LowFrequencySonarRenderer.ScreenWave wave = waves.get(index);
                builder.putVec4(
                        wave.color().x,
                        wave.color().y,
                        wave.color().z,
                        wave.handoffStart());
            } else {
                builder.putVec4(0.33F, 0.90F, 0.95F, 0.0F);
            }
        }
        // Aim is omnidirectional while a Past Echo crest is also uploaded so the
        // shared shader never cones the memory pulse by accident.
        if (pastFrame.waves().isEmpty() && sonarFrame.directional()) {
            Vector3f aim = sonarFrame.aimView();
            builder.putVec4(aim.x, aim.y, aim.z, sonarFrame.aimCosHalf());
        } else {
            builder.putVec4(0.0F, 0.0F, -1.0F, 2.0F);
        }
    }

    private static void appendWaves(
            List<LowFrequencySonarRenderer.ScreenWave> destination,
            List<LowFrequencySonarRenderer.ScreenWave> source) {
        for (LowFrequencySonarRenderer.ScreenWave wave : source) {
            if (destination.size() >= LowFrequencySonarRenderer.MAX_SCREEN_WAVES) {
                return;
            }
            destination.add(wave);
        }
    }

    private EchoPostEffectUniforms() {
    }
}
