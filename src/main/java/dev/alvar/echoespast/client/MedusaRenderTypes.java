package dev.alvar.echoespast.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import java.util.HashMap;
import java.util.Map;
import dev.alvar.echoespast.mixin.client.RenderSetupAccessor;
import dev.alvar.echoespast.mixin.client.RenderTypeAccessor;
import dev.alvar.echoespast.mixin.client.TextureBindingAccessor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class MedusaRenderTypes {
    private static final Identifier STONE_TEXTURE =
            Identifier.withDefaultNamespace("textures/block/stone.png");
    static final RenderPipeline STONE_PIPELINE =
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(
                            "echoes_show_the_past",
                            "medusa_stone"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(
                            "echoes_show_the_past",
                            "core/medusa_stone"))
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withShaderDefine("NO_OVERLAY")
                    .withSampler("StoneSampler")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(
                            CompareOp.LESS_THAN_OR_EQUAL,
                            false,
                            -1.0F,
                            -1.0F))
                    .withCull(false)
                    .build();
    static final RenderPipeline CRACK_PIPELINE =
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(
                            "echoes_show_the_past",
                            "medusa_statue_cracks"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(
                            "echoes_show_the_past",
                            "core/medusa_crack"))
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withShaderDefine("NO_OVERLAY")
                    .withSampler("CrackSampler")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(
                            CompareOp.LESS_THAN_OR_EQUAL,
                            false,
                            -1.0F,
                            -1.0F))
                    .withCull(false)
                    .build();

    private static final Map<Identifier, RenderType> FALLBACK_STONE_TYPES =
            new HashMap<>();
    private static final Map<CrackKey, RenderType> CRACK_TYPES = new HashMap<>();

    public static RenderType stone(Identifier texture, boolean translucent) {
        Identifier materialTexture = PetrifiedTextureCache.getOrCreate(texture);
        if (materialTexture != null) {
            return translucent
                    ? RenderTypes.entityTranslucent(materialTexture, false)
                    : RenderTypes.entityCutout(materialTexture, false);
        }
        return FALLBACK_STONE_TYPES.computeIfAbsent(
                texture,
                key -> RenderType.create(
                        "medusa_stone_" + key.toDebugFileName(),
                        RenderSetup.builder(STONE_PIPELINE)
                                .withTexture("Sampler0", key)
                                .withTexture("StoneSampler", STONE_TEXTURE)
                                .useLightmap()
                                .setOutline(RenderSetup.OutlineProperty.NONE)
                                .createRenderSetup()));
    }

    public static RenderType crack(Identifier sourceTexture, int stage) {
        CrackKey key = new CrackKey(sourceTexture, Math.clamp(stage, 0, 9));
        return CRACK_TYPES.computeIfAbsent(
                key,
                value -> RenderType.create(
                        "medusa_crack_"
                                + value.sourceTexture.toDebugFileName()
                                + "_"
                                + value.stage,
                        RenderSetup.builder(CRACK_PIPELINE)
                                .withTexture("Sampler0", value.sourceTexture)
                                .withTexture(
                                        "CrackSampler",
                                        Identifier.withDefaultNamespace(
                                                "textures/block/destroy_stage_"
                                                        + value.stage
                                                        + ".png"))
                                .useLightmap()
                                .setOutline(RenderSetup.OutlineProperty.NONE)
                                .createRenderSetup()));
    }

    public static @Nullable Identifier sourceTexture(RenderType renderType) {
        RenderSetup setup = ((RenderTypeAccessor) (Object) renderType)
                .echoesShowThePast$getState();
        Object binding = ((RenderSetupAccessor) (Object) setup)
                .echoesShowThePast$getTextureBindings()
                .get("Sampler0");
        return binding instanceof TextureBindingAccessor accessor
                ? accessor.echoesShowThePast$getLocation()
                : null;
    }

    /**
     * Eyes and other emissive feature layers are light sources belonging to
     * the living creature, not geometry that should survive petrification.
     */
    public static boolean isEmissiveLayer(RenderType renderType) {
        return renderType.pipeline() == RenderPipelines.EYES
                || renderType.pipeline()
                        == RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE;
    }

    private record CrackKey(Identifier sourceTexture, int stage) {
    }

    private MedusaRenderTypes() {
    }
}
