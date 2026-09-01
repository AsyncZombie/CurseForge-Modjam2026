package dev.alvar.echoespast.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.mixin.client.PostPassAccessor;
import java.util.List;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Preserves the world's depth buffer before vanilla clears it for first-person
 * hands. Post chains run after that clear, so sampling the main depth texture
 * directly would attach distant waves to the hand and the sky instead.
 */
public final class EchoWorldDepthCapture extends AbstractTexture {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SAMPLER_NAME = "WorldDepth";
    private static final EchoWorldDepthCapture INSTANCE = new EchoWorldDepthCapture();

    private int width;
    private int height;
    private @Nullable TextureFormat format;
    private boolean attached;
    private boolean captureLogged;

    public static void attachTo(PostPass pass, int width, int height) {
        RenderSystem.assertOnRenderThread();
        List<PostPass.Input> inputs =
                ((PostPassAccessor) pass).echoesShowThePast$getInputs();
        for (int index = 0; index < inputs.size(); index++) {
            PostPass.Input input = inputs.get(index);
            if (!SAMPLER_NAME.equals(input.samplerName())) {
                continue;
            }
            INSTANCE.ensureTexture(width, height, TextureFormat.DEPTH32);
            if (!(input instanceof PostPass.TextureInput textureInput)
                    || textureInput.texture() != INSTANCE) {
                inputs.set(
                        index,
                        new PostPass.TextureInput(
                                SAMPLER_NAME,
                                INSTANCE,
                                width,
                                height,
                                false));
            }
            INSTANCE.attached = true;
            return;
        }
    }

    public static boolean shouldCapture() {
        return INSTANCE.attached
                && (ClientEchoState.isPostEffectOperational()
                        || ClientPhilosophersStoneVision
                                .isPostEffectOperational());
    }

    /**
     * Runs in a frame-graph pass after world geometry but before clouds and
     * weather. Those atmospheric layers must not become sonar surfaces.
     */
    public static void captureWorldDepth(RenderTarget source) {
        RenderSystem.assertOnRenderThread();
        if (!shouldCapture()) {
            return;
        }
        GpuTexture sourceDepth = source.getDepthTexture();
        if (sourceDepth == null || source.width <= 0 || source.height <= 0) {
            return;
        }
        INSTANCE.ensureTexture(
                source.width,
                source.height,
                sourceDepth.getFormat());
        if (!INSTANCE.captureLogged) {
            INSTANCE.captureLogged = true;
            LOGGER.info(
                    "Echo world depth captured: {}x{}, format={}",
                    source.width,
                    source.height,
                    sourceDepth.getFormat());
        }
        RenderSystem.getDevice()
                .createCommandEncoder()
                .copyTextureToTexture(
                        sourceDepth,
                        INSTANCE.getTexture(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        source.width,
                        source.height);
    }

    private void ensureTexture(int width, int height, TextureFormat format) {
        if (this.texture != null
                && this.width == width
                && this.height == height
                && this.format == format) {
            return;
        }
        this.close();
        this.width = Math.max(width, 1);
        this.height = Math.max(height, 1);
        this.format = format;
        this.texture = RenderSystem.getDevice().createTexture(
                () -> "Echoes Show the Past preserved world depth",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                format,
                this.width,
                this.height,
                1,
                1);
        this.textureView =
                RenderSystem.getDevice().createTextureView(this.texture);
    }

    private EchoWorldDepthCapture() {
    }
}
