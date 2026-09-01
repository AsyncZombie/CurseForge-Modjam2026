package dev.alvar.echoespast.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

/**
 * Dedicated layers for the remembered surface and the continuously
 * interpolated world-space wave.
 */
final class EchoRenderTypes {
    private static final BlendFunction WAVE_MASK_BLEND = new BlendFunction(
            SourceFactor.ONE,
            DestFactor.ZERO,
            SourceFactor.ONE,
            DestFactor.ZERO);
    private static final Identifier WAVE_SURFACE_SHADER =
            Identifier.fromNamespaceAndPath("echoes_show_the_past", "core/wave_surface");
    private static final Identifier WAVE_MASK_SHADER =
            Identifier.fromNamespaceAndPath("echoes_show_the_past", "core/wave_mask");
    private static final Identifier UNKNOWN_STAB_SHADER =
            Identifier.fromNamespaceAndPath(
                    "echoes_show_the_past",
                    "core/unknown_stab_telegraph");
    private static final Identifier ALTAR_ORBIT_SHADER =
            Identifier.fromNamespaceAndPath(
                    "echoes_show_the_past",
                    "core/altar_orbit");
    private static final Identifier EGYPTIAN_JUDGMENT_SHADER =
            Identifier.fromNamespaceAndPath(
                    "echoes_show_the_past",
                    "core/egyptian_solar_judgment");
    private static final Identifier RA_JUDGMENT_SIGIL_SHADER =
            Identifier.fromNamespaceAndPath(
                    "echoes_show_the_past",
                    "core/ra_judgment_sigil");
    private static final Identifier EGYPTIAN_SEKHMET_SHADER =
            Identifier.fromNamespaceAndPath(
                    "echoes_show_the_past",
                    "core/egyptian_sekhmet_hunt");
    private static final Identifier EGYPTIAN_ARCHITECTURE_SHADER =
            Identifier.fromNamespaceAndPath(
                    "echoes_show_the_past",
                    "core/egyptian_architecture");
    private static final Identifier EGYPTIAN_CHARIOT_SHADER =
            Identifier.fromNamespaceAndPath(
                    "echoes_show_the_past",
                    "core/egyptian_chariot");
    private static final Identifier SPECTRAL_HOPLITE_SHADER =
            Identifier.fromNamespaceAndPath(
                    "echoes_show_the_past",
                    "core/spectral_hoplite");
    private static final Identifier HORUS_SIGIL_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    "echoes_show_the_past",
                    "textures/effect/horus_sigil.png");
    private static final Identifier RA_JUDGMENT_SIGIL_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    "echoes_show_the_past",
                    "textures/effect/ra_judgment_sigil.png");

    static final RenderPipeline WAVE_COLOR_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("echoes_show_the_past", "surface_wave"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader(WAVE_SURFACE_SHADER)
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(
                    Optional.of(BlendFunction.TRANSLUCENT),
                    ColorTargetState.WRITE_COLOR))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F))
            .withCull(false)
            .build();

    /**
     * The technical marker is isolated in an alpha-only pass. This lets the
     * post effect preserve the sonar without forcing screen/additive blending
     * onto its soft outer layers.
     */
    static final RenderPipeline WAVE_MASK_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("echoes_show_the_past", "surface_wave_mask"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader(WAVE_MASK_SHADER)
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(
                    Optional.of(WAVE_MASK_BLEND),
                    ColorTargetState.WRITE_ALPHA))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F))
            .withCull(false)
            .build();
    static final RenderPipeline LOW_FREQUENCY_BEAM_PIPELINE =
            lowFrequencyPipeline("low_frequency_beam", CompareOp.LESS_THAN_OR_EQUAL);
    static final RenderPipeline HORUS_HAZARD_OCCLUDED_PIPELINE =
            lowFrequencyPipeline("horus_hazard_occluded", CompareOp.ALWAYS_PASS);
    static final RenderPipeline HORUS_HAZARD_VISIBLE_PIPELINE =
            lowFrequencyPipeline("horus_hazard_visible", CompareOp.LESS_THAN_OR_EQUAL);
    static final RenderPipeline MEDUSA_GAZE_PIPELINE =
            lowFrequencyPipeline("medusa_gaze_geometry", CompareOp.LESS_THAN_OR_EQUAL);
    static final RenderPipeline HOLY_GRAIL_RITUAL_PIPELINE =
            holyGrailPipeline("holy_grail_ritual", BlendFunction.TRANSLUCENT);
    static final RenderPipeline HOLY_GRAIL_GLOW_PIPELINE =
            holyGrailPipeline("holy_grail_glow", BlendFunction.ADDITIVE);
    static final RenderPipeline ALTAR_ORBIT_PIPELINE =
            altarOrbitPipeline("altar_orbit", BlendFunction.TRANSLUCENT);
    static final RenderPipeline ALTAR_ORBIT_GLOW_PIPELINE =
            altarOrbitPipeline("altar_orbit_glow", BlendFunction.ADDITIVE);
    static final RenderPipeline HORUS_SIGIL_PIPELINE =
            horusSigilPipeline("horus_sigil", BlendFunction.TRANSLUCENT);
    static final RenderPipeline HORUS_SIGIL_GLOW_PIPELINE =
            horusSigilPipeline("horus_sigil_glow", BlendFunction.ADDITIVE);
    static final RenderPipeline RA_JUDGMENT_SIGIL_PIPELINE =
            sigilPipeline(
                    "ra_judgment_sigil",
                    RA_JUDGMENT_SIGIL_SHADER,
                    BlendFunction.TRANSLUCENT);
    static final RenderPipeline RA_JUDGMENT_SIGIL_GLOW_PIPELINE =
            sigilPipeline(
                    "ra_judgment_sigil_glow",
                    RA_JUDGMENT_SIGIL_SHADER,
                    BlendFunction.ADDITIVE);
    static final RenderPipeline UNKNOWN_STAB_PIPELINE =
            unknownStabPipeline();
    static final RenderPipeline EGYPTIAN_JUDGMENT_PIPELINE =
            egyptianJudgmentPipeline("egyptian_solar_judgment", CompareOp.LESS_THAN_OR_EQUAL);
    static final RenderPipeline EGYPTIAN_JUDGMENT_OCCLUDED_PIPELINE =
            egyptianJudgmentPipeline("egyptian_solar_judgment_occluded", CompareOp.ALWAYS_PASS);
    static final RenderPipeline EGYPTIAN_SEKHMET_PIPELINE =
            egyptianSpectralPipeline(
                    "egyptian_sekhmet_hunt",
                    EGYPTIAN_SEKHMET_SHADER,
                    CompareOp.LESS_THAN_OR_EQUAL);
    static final RenderPipeline EGYPTIAN_ARCHITECTURE_PIPELINE =
            egyptianSolidPipeline("egyptian_architecture", EGYPTIAN_ARCHITECTURE_SHADER);
    static final RenderPipeline EGYPTIAN_CHARIOT_PIPELINE =
            egyptianSolidPipeline("egyptian_chariot", EGYPTIAN_CHARIOT_SHADER);
    static final RenderPipeline SPECTRAL_HOPLITE_PIPELINE =
            spectralHoplitePipeline();
    /**
     * Iris selects a replacement program by both semantic and vertex format.
     * Most boss telegraphs carry UVs for their native procedural shader, while
     * the old BASIC mapping only had a POSITION_COLOR match.  BSL therefore
     * received a format-mismatched fallback and lost vertex alpha, turning
     * restrained ground marks into opaque HDR-white geometry.  Shaderpack
     * mode deliberately submits the same silhouettes through this exact
     * POSITION_COLOR contract; BufferBuilder safely ignores the now-unused UV
     * writes while preserving every per-vertex tint and alpha value.
     */
    static final RenderPipeline SHADERPACK_COLOR_PIPELINE =
            shaderpackColorPipeline("shaderpack_color", CompareOp.LESS_THAN_OR_EQUAL);
    static final RenderPipeline SHADERPACK_COLOR_OCCLUDED_PIPELINE =
            shaderpackColorPipeline("shaderpack_color_occluded", CompareOp.ALWAYS_PASS);

    /**
     * All reconstructed block geometry uses the same vanilla entity pipeline.
     * Iris can override it without losing texture alpha, tint, lightmap or
     * two-sided faces, unlike a custom surface program unknown to shaderpacks.
     */
    static final RenderType REMEMBERED_SURFACE =
            RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS, false);
    static final RenderType PRESENT_SURFACE = REMEMBERED_SURFACE;
    static final RenderType WAVE_COLOR = RenderType.create(
            "echo_surface_wave",
            RenderSetup.builder(WAVE_COLOR_PIPELINE)
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType WAVE_MASK = RenderType.create(
            "echo_surface_wave_mask",
            RenderSetup.builder(WAVE_MASK_PIPELINE)
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType LOW_FREQUENCY_BEAM = RenderType.create(
            "low_frequency_beam",
            RenderSetup.builder(LOW_FREQUENCY_BEAM_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType HORUS_HAZARD_OCCLUDED = RenderType.create(
            "horus_hazard_occluded",
            RenderSetup.builder(HORUS_HAZARD_OCCLUDED_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType HORUS_HAZARD_VISIBLE = RenderType.create(
            "horus_hazard_visible",
            RenderSetup.builder(HORUS_HAZARD_VISIBLE_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType MEDUSA_GAZE = RenderType.create(
            "medusa_gaze_geometry",
            RenderSetup.builder(MEDUSA_GAZE_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType HOLY_GRAIL_RITUAL = RenderType.create(
            "holy_grail_ritual",
            RenderSetup.builder(HOLY_GRAIL_RITUAL_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType HOLY_GRAIL_GLOW = RenderType.create(
            "holy_grail_glow",
            RenderSetup.builder(HOLY_GRAIL_GLOW_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType ALTAR_ORBIT = RenderType.create(
            "altar_orbit",
            RenderSetup.builder(ALTAR_ORBIT_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType ALTAR_ORBIT_GLOW = RenderType.create(
            "altar_orbit_glow",
            RenderSetup.builder(ALTAR_ORBIT_GLOW_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType HORUS_SIGIL = RenderType.create(
            "horus_sigil",
            RenderSetup.builder(HORUS_SIGIL_PIPELINE)
                    .withTexture("Sampler0", HORUS_SIGIL_TEXTURE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType HORUS_SIGIL_GLOW = RenderType.create(
            "horus_sigil_glow",
            RenderSetup.builder(HORUS_SIGIL_GLOW_PIPELINE)
                    .withTexture("Sampler0", HORUS_SIGIL_TEXTURE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType RA_JUDGMENT_SIGIL = RenderType.create(
            "ra_judgment_sigil",
            RenderSetup.builder(RA_JUDGMENT_SIGIL_PIPELINE)
                    .withTexture("Sampler0", RA_JUDGMENT_SIGIL_TEXTURE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType RA_JUDGMENT_SIGIL_GLOW = RenderType.create(
            "ra_judgment_sigil_glow",
            RenderSetup.builder(RA_JUDGMENT_SIGIL_GLOW_PIPELINE)
                    .withTexture("Sampler0", RA_JUDGMENT_SIGIL_TEXTURE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType UNKNOWN_STAB = RenderType.create(
            "unknown_stab_telegraph",
            RenderSetup.builder(UNKNOWN_STAB_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType EGYPTIAN_JUDGMENT = RenderType.create(
            "egyptian_solar_judgment",
            RenderSetup.builder(EGYPTIAN_JUDGMENT_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType EGYPTIAN_JUDGMENT_OCCLUDED = RenderType.create(
            "egyptian_solar_judgment_occluded",
            RenderSetup.builder(EGYPTIAN_JUDGMENT_OCCLUDED_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType EGYPTIAN_SEKHMET = RenderType.create(
            "egyptian_sekhmet_hunt",
            RenderSetup.builder(EGYPTIAN_SEKHMET_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType EGYPTIAN_ARCHITECTURE = RenderType.create(
            "egyptian_architecture",
            RenderSetup.builder(EGYPTIAN_ARCHITECTURE_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType EGYPTIAN_CHARIOT = RenderType.create(
            "egyptian_chariot",
            RenderSetup.builder(EGYPTIAN_CHARIOT_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType SPECTRAL_HOPLITE = RenderType.create(
            "spectral_hoplite",
            RenderSetup.builder(SPECTRAL_HOPLITE_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType SHADERPACK_COLOR = RenderType.create(
            "echo_shaderpack_color",
            RenderSetup.builder(SHADERPACK_COLOR_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());
    static final RenderType SHADERPACK_COLOR_OCCLUDED = RenderType.create(
            "echo_shaderpack_color_occluded",
            RenderSetup.builder(SHADERPACK_COLOR_OCCLUDED_PIPELINE)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());

    static RenderType shaderSafe(RenderType nativeType) {
        return EchoShaderCompatibility.isShaderPackActive()
                ? SHADERPACK_COLOR
                : nativeType;
    }

    static RenderType shaderSafeOccluded(RenderType nativeType) {
        return EchoShaderCompatibility.isShaderPackActive()
                ? SHADERPACK_COLOR_OCCLUDED
                : nativeType;
    }

    static RenderType shaderSafeGlow(
            RenderType nativeGlow,
            RenderType translucentFallback) {
        return EchoShaderCompatibility.isShaderPackActive()
                ? translucentFallback
                : nativeGlow;
    }

    private static RenderPipeline shaderpackColorPipeline(
            String name,
            CompareOp depthCompare) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("echoes_show_the_past", name))
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(
                        depthCompare,
                        false,
                        -1.0F,
                        -1.0F))
                .withCull(false)
                .build();
    }

    private static RenderPipeline lowFrequencyPipeline(String name, CompareOp depthCompare) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("echoes_show_the_past", name))
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(depthCompare, false, -1.0F, -1.0F))
                .withCull(false)
                .build();
    }

    private static RenderPipeline unknownStabPipeline() {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(
                        "echoes_show_the_past",
                        "unknown_stab_telegraph"))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader(UNKNOWN_STAB_SHADER)
                .withVertexFormat(
                        DefaultVertexFormat.POSITION_TEX_COLOR,
                        VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(
                        CompareOp.LESS_THAN_OR_EQUAL,
                        false,
                        -1.0F,
                        -1.0F))
                .withCull(false)
                .build();
    }

    private static RenderPipeline altarOrbitPipeline(String name, BlendFunction blendFunction) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("echoes_show_the_past", name))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader(ALTAR_ORBIT_SHADER)
                .withVertexFormat(
                        DefaultVertexFormat.POSITION_TEX_COLOR,
                        VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(blendFunction))
                .withDepthStencilState(new DepthStencilState(
                        CompareOp.LESS_THAN_OR_EQUAL,
                        false,
                        -1.0F,
                        -1.0F))
                .withCull(false)
                .build();
    }

    private static RenderPipeline egyptianJudgmentPipeline(
            String name,
            CompareOp depthCompare) {
        return egyptianSpectralPipeline(name, EGYPTIAN_JUDGMENT_SHADER, depthCompare);
    }

    private static RenderPipeline egyptianSpectralPipeline(
            String name,
            Identifier fragmentShader,
            CompareOp depthCompare) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("echoes_show_the_past", name))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader(fragmentShader)
                .withVertexFormat(
                        DefaultVertexFormat.POSITION_TEX_COLOR,
                        VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(
                        depthCompare,
                        false,
                        -1.0F,
                        -1.0F))
                .withCull(false)
                .build();
    }

    private static RenderPipeline egyptianSolidPipeline(
            String name,
            Identifier fragmentShader) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("echoes_show_the_past", name))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader(fragmentShader)
                .withVertexFormat(
                        DefaultVertexFormat.POSITION_TEX_COLOR,
                        VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(
                        CompareOp.LESS_THAN_OR_EQUAL,
                        true,
                        -1.0F,
                        -1.0F))
                .withCull(false)
                .build();
    }

    private static RenderPipeline spectralHoplitePipeline() {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(
                        "echoes_show_the_past",
                        "spectral_hoplite"))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader(SPECTRAL_HOPLITE_SHADER)
                .withVertexFormat(
                        DefaultVertexFormat.POSITION_TEX_COLOR,
                        VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                // Unlike a flat telegraph, a 3D translucent rig must populate depth.
                // This prevents its far limbs and neighbouring hoplites bleeding
                // through the shield, torso and helmet.
                .withDepthStencilState(new DepthStencilState(
                        CompareOp.LESS_THAN_OR_EQUAL,
                        true,
                        -1.0F,
                        -1.0F))
                .withCull(false)
                .build();
    }

    private static RenderPipeline horusSigilPipeline(
            String name,
            BlendFunction blendFunction) {
        return sigilPipeline(
                name,
                Identifier.fromNamespaceAndPath(
                        "echoes_show_the_past",
                        "core/horus_sigil"),
                blendFunction);
    }

    private static RenderPipeline sigilPipeline(
            String name,
            Identifier fragmentShader,
            BlendFunction blendFunction) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("echoes_show_the_past", name))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader(fragmentShader)
                .withSampler("Sampler0")
                .withVertexFormat(
                        DefaultVertexFormat.POSITION_TEX_COLOR,
                        VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(blendFunction))
                .withDepthStencilState(new DepthStencilState(
                        CompareOp.LESS_THAN_OR_EQUAL,
                        false,
                        -1.0F,
                        -1.0F))
                .withCull(false)
                .build();
    }

    private static RenderPipeline holyGrailPipeline(
            String name,
            BlendFunction blendFunction) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(
                        "echoes_show_the_past",
                        name))
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withVertexFormat(
                        DefaultVertexFormat.POSITION_COLOR,
                        VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(blendFunction))
                .withDepthStencilState(new DepthStencilState(
                        CompareOp.LESS_THAN_OR_EQUAL,
                        false,
                        -1.0F,
                        -1.0F))
                .withCull(false)
                .build();
    }

    private EchoRenderTypes() {
    }
}
