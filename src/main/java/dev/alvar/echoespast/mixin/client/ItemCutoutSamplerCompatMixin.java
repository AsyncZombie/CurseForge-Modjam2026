package dev.alvar.echoespast.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iris shader packs transform both vanilla item pipelines into entity programs
 * that consume the overlay sampler. Vanilla's RenderSetup deliberately omits
 * that binding, so Connector leaves the transformed program incomplete and the
 * first affected world item aborts the entire render batch.
 *
 * Supplying the real overlay texture only for this exact vanilla pipeline is
 * safe when no shader pack is active (unused bindings are ignored) and avoids
 * changing any Echo or third-party material.
 */
@Mixin(RenderSetup.class)
public abstract class ItemCutoutSamplerCompatMixin {
    private static final Set<Identifier> ECHOES_SHOW_THE_PAST$ITEM_PIPELINES =
            Set.of(
                    Identifier.withDefaultNamespace("pipeline/item_cutout"),
                    Identifier.withDefaultNamespace("pipeline/item_translucent"));

    @Shadow
    @Final
    private RenderPipeline pipeline;

    @Inject(method = "getTextures", at = @At("RETURN"), cancellable = true)
    private void echoesShowThePast$bindItemOverlay(
            CallbackInfoReturnable<Map<String, RenderSetup.TextureAndSampler>> callback) {
        Map<String, RenderSetup.TextureAndSampler> current = callback.getReturnValue();
        if (!ECHOES_SHOW_THE_PAST$ITEM_PIPELINES.contains(this.pipeline.getLocation())
                || current.containsKey("Sampler1")) {
            return;
        }

        Map<String, RenderSetup.TextureAndSampler> compatible = new HashMap<>(current);
        compatible.put(
                "Sampler1",
                new RenderSetup.TextureAndSampler(
                        Minecraft.getInstance()
                                .gameRenderer
                                .overlayTexture()
                                .getTextureView(),
                        RenderSystem.getSamplerCache()
                                .getClampToEdge(FilterMode.LINEAR)));
        callback.setReturnValue(compatible);
    }
}
