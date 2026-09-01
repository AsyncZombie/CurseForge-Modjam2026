package dev.alvar.echoespast.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** GPU pipelines used only by the Timeless Void environment. */
final class TimelessSkyRenderTypes {
    private static final Identifier SKY_SHADER = shader("timeless_sky");
    private static final Identifier VEIL_SHADER = shader("timeless_veil");
    private static final Identifier STAR_SHADER = shader("timeless_star");
    private static final Identifier ECLIPSE_SHADER = shader("timeless_eclipse");
    private static final Identifier ECLIPSE_CORONA_SHADER = shader("timeless_eclipse_corona");

    static final RenderPipeline BACKGROUND = textured(
            "timeless_sky_background",
            SKY_SHADER,
            SKY_SHADER,
            BlendFunction.TRANSLUCENT);
    static final RenderPipeline VEILS = untexturedUv(
            "timeless_sky_veils",
            SKY_SHADER,
            VEIL_SHADER,
            BlendFunction.ADDITIVE);
    static final RenderPipeline STARS = untexturedUv(
            "timeless_sky_stars",
            STAR_SHADER,
            STAR_SHADER,
            BlendFunction.ADDITIVE);
    static final RenderPipeline ECLIPSE = untexturedUv(
            "timeless_sky_eclipse",
            "core/position_tex_color",
            ECLIPSE_SHADER,
            BlendFunction.TRANSLUCENT);
    static final RenderPipeline ECLIPSE_CORONA = untexturedUv(
            "timeless_sky_eclipse_corona",
            "core/position_tex_color",
            ECLIPSE_CORONA_SHADER,
            BlendFunction.ADDITIVE);

    private static RenderPipeline textured(
            String name,
            Identifier vertexShader,
            Identifier fragmentShader,
            BlendFunction blend) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(id(name))
                .withVertexShader(vertexShader)
                .withFragmentShader(fragmentShader)
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(blend))
                .withDepthStencilState(new DepthStencilState(
                        CompareOp.ALWAYS_PASS,
                        false,
                        -1.0F,
                        -1.0F))
                .withCull(false)
                .build();
    }

    private static RenderPipeline untexturedUv(
            String name,
            String vertexShader,
            Identifier fragmentShader,
            BlendFunction blend) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(id(name))
                .withVertexShader(vertexShader)
                .withFragmentShader(fragmentShader)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(blend))
                .withDepthStencilState(new DepthStencilState(
                        CompareOp.ALWAYS_PASS,
                        false,
                        -1.0F,
                        -1.0F))
                .withCull(false)
                .build();
    }

    private static RenderPipeline untexturedUv(
            String name,
            Identifier vertexShader,
            Identifier fragmentShader,
            BlendFunction blend) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(id(name))
                .withVertexShader(vertexShader)
                .withFragmentShader(fragmentShader)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(blend))
                .withDepthStencilState(new DepthStencilState(
                        CompareOp.ALWAYS_PASS,
                        false,
                        -1.0F,
                        -1.0F))
                .withCull(false)
                .build();
    }

    private static Identifier shader(String path) {
        return id("core/" + path);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, path);
    }

    private TimelessSkyRenderTypes() {
    }
}
