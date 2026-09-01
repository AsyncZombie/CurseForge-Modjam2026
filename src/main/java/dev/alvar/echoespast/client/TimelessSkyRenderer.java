package dev.alvar.echoespast.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.visual.TimelessAtmosphere;
import dev.alvar.echoespast.world.TimelessDimensions;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Layered, phase-aware sky for the final-boss dimension. Every layer is a
 * retained GPU buffer: the sky does no per-frame geometry allocation and uses
 * no particles.
 */
final class TimelessSkyRenderer implements CustomSkyboxRenderer {
    static final TimelessSkyRenderer INSTANCE = new TimelessSkyRenderer();
    static final Identifier ENVIRONMENT_ID = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "timeless_void");

    private static final Identifier NEBULA_TEXTURE = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "textures/environment/timeless_nebula.png");
    private static final int FAR_STAR_COUNT = 300;
    private static final int NEAR_STAR_COUNT = 96;
    private static final RenderSystem.AutoStorageIndexBuffer QUAD_INDICES =
            RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);

    private @Nullable SkyMesh background;
    private @Nullable SkyMesh farVeil;
    private @Nullable SkyMesh farVeilFallback;
    private @Nullable SkyMesh nearVeil;
    private @Nullable SkyMesh nearVeilFallback;
    private @Nullable SkyMesh farStars;
    private @Nullable SkyMesh nearStars;
    private @Nullable SkyMesh eclipseCorona;
    private @Nullable SkyMesh eclipseCoronaFallback;
    private @Nullable SkyMesh eclipseDisc;
    private @Nullable SkyMesh eclipseDiscFallback;
    private @Nullable AbstractTexture nebulaTexture;
    private TimelessAtmosphere.Profile profile = TimelessAtmosphere.target(
            false,
            (byte) 0,
            (byte) 0);
    private boolean profileReady;

    private TimelessSkyRenderer() {
    }

    void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !minecraft.level.dimension().equals(TimelessDimensions.TIMELESS_VOID)) {
            profileReady = false;
            return;
        }
        TimelessAtmosphere.Profile target = TimelessAtmosphere.target(
                ClientUnknownBossBar.isActive(),
                ClientUnknownBossBar.era(),
                ClientUnknownBossBar.phase());
        if (!profileReady) {
            profile = target;
            profileReady = true;
        } else {
            // About 0.7 s for most of the transition: legible but never abrupt.
            profile = profile.lerp(target, 0.105F);
        }
    }

    TimelessAtmosphere.Profile profile() {
        return profile;
    }

    @Override
    public boolean renderSky(
            LevelRenderState levelRenderState,
            SkyRenderState skyRenderState,
            Matrix4fc modelViewMatrix,
            Runnable setupFog) {
        if (EchoShaderCompatibility.isShadowPass()) {
            return true;
        }
        if (!profileReady) {
            tick();
        }
        boolean shaderPack = EchoShaderCompatibility.isShaderPackActive();
        if (shaderPack) {
            // Iris composites its deferred world after this callback and can
            // overwrite direct writes to the main target.  The shaderpack
            // fallback is therefore submitted later as ordinary depth-tested
            // world geometry (see submitShaderpackGeometry).
            setupFog.run();
            return true;
        }
        ensureResources();
        setupFog.run();

        float seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0F;
        var cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        float shaderExposure = 1.0F;
        Matrix4f parameters = new Matrix4f()
                .identity()
                // Subtle camera-relative offsets give the two veil shells real parallax.
                .m00((float) cameraPosition.x * 0.00045F)
                .m11((float) cameraPosition.y * 0.00030F)
                .m22((float) cameraPosition.z * 0.00045F)
                .m30(seconds)
                .m31(profile.instability())
                .m32(profile.gold())
                .m03(profile.veilStrength())
                .m13(profile.horizonGlow())
                .m23(profile.starBrightness())
                // Shader packs generally add their own HDR exposure and bloom.
                .m33(shaderExposure);
        Matrix4f view = new Matrix4f(modelViewMatrix);
        Matrix4f farStarView = new Matrix4f(view).rotateY(seconds * 0.000018F);
        Matrix4f farVeilView = new Matrix4f(view)
                .rotateY(seconds * 0.000055F)
                .rotateX(0.006F);
        Matrix4f nearStarView = new Matrix4f(view)
                .rotateY(-seconds * 0.000044F)
                .rotateZ(0.004F);
        Matrix4f nearVeilView = new Matrix4f(view)
                .rotateY(-seconds * 0.00011F)
                .rotateZ(Mth.sin(seconds * 0.0032F) * 0.008F);

        draw(
                "Timeless nebula",
                background,
                TimelessSkyRenderTypes.BACKGROUND,
                view,
                new Vector4f(profile.skyR(), profile.skyG(), profile.skyB(), 1.0F),
                parameters,
                nebulaTexture);
        draw(
                "Timeless deep stars",
                farStars,
                TimelessSkyRenderTypes.STARS,
                farStarView,
                new Vector4f(0.90F, 0.93F, 1.0F, 0.58F),
                new Matrix4f(parameters).m01(0.0F),
                null);
        draw(
                "Timeless distant memory veil",
                farVeil,
                TimelessSkyRenderTypes.VEILS,
                farVeilView,
                new Vector4f(
                        0.72F + profile.skyR() * 0.32F,
                        0.74F + profile.skyG() * 0.28F,
                        0.82F + profile.skyB() * 0.22F,
                        0.46F),
                new Matrix4f(parameters).m01(0.0F),
                null);
        draw(
                "Timeless foreground stars",
                nearStars,
                TimelessSkyRenderTypes.STARS,
                nearStarView,
                new Vector4f(1.0F, 0.86F, 0.58F, 0.46F),
                new Matrix4f(parameters).m01(1.0F),
                null);

        Vector4f eclipseColor = new Vector4f(
                1.0F,
                0.76F + profile.gold() * 0.20F,
                0.30F + profile.gold() * 0.28F,
                1.0F);
        draw(
                "Timeless eclipse outer corona",
                eclipseCorona,
                TimelessSkyRenderTypes.ECLIPSE_CORONA,
                view,
                new Vector4f(
                        eclipseColor.x,
                        eclipseColor.y,
                        eclipseColor.z,
                        0.72F + profile.gold() * 0.16F),
                parameters,
                null);
        draw(
                "Timeless foreground memory veil",
                nearVeil,
                TimelessSkyRenderTypes.VEILS,
                nearVeilView,
                new Vector4f(
                        0.90F + profile.skyR() * 0.16F,
                        0.84F + profile.skyG() * 0.16F,
                        0.67F + profile.skyB() * 0.18F,
                        0.30F),
                new Matrix4f(parameters).m01(1.0F),
                null);
        draw(
                "Timeless eclipse occulting disc",
                eclipseDisc,
                TimelessSkyRenderTypes.ECLIPSE,
                view,
                eclipseColor,
                parameters,
                null);
        return true;
    }

    /**
     * Shaderpack-safe sky path.  These are real, camera-centred, depth-tested
     * layers submitted through the level renderer, so Iris/BSL includes them
     * in its gbuffer and composite stages instead of erasing a direct sky pass.
     * The fallback intentionally uses normal alpha blending: shaderpack HDR
     * and bloom provide the glow without additive white clipping.
     */
    void submitShaderpackGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!EchoShaderCompatibility.isShaderPackActive()
                || EchoShaderCompatibility.isShadowPass()
                || minecraft.level == null
                || !minecraft.level.dimension().equals(TimelessDimensions.TIMELESS_VOID)) {
            return;
        }
        if (!profileReady) {
            tick();
        }
        float seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0F;
        TimelessAtmosphere.Profile frameProfile = profile;
        event.getSubmitNodeCollector().submitCustomGeometry(
                event.getPoseStack(),
                EchoRenderTypes.SHADERPACK_COLOR,
                (pose, consumer) -> emitShaderpackSky(
                        pose,
                        consumer,
                        frameProfile,
                        seconds));
    }

    private static void emitShaderpackSky(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            TimelessAtmosphere.Profile atmosphere,
            float seconds) {
        emitShaderpackBackdrop(pose, consumer, atmosphere);
        emitShaderpackVeils(pose, consumer, atmosphere, seconds, 104.0F, false);
        emitShaderpackStars(pose, consumer, atmosphere, seconds, 0x4543484F4CL, 190, 101.0F, false);
        emitShaderpackVeils(pose, consumer, atmosphere, -seconds * 0.81F, 94.0F, true);
        emitShaderpackStars(pose, consumer, atmosphere, -seconds, 0x54494D454CL, 72, 91.0F, true);
        emitShaderpackEclipse(pose, consumer, atmosphere);
    }

    private static void emitShaderpackBackdrop(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            TimelessAtmosphere.Profile atmosphere) {
        float radius = 110.0F;
        int zenith = rgba(
                1.0F,
                0.010F + atmosphere.skyR() * 0.035F,
                0.012F + atmosphere.skyG() * 0.038F,
                0.030F + atmosphere.skyB() * 0.070F);
        int horizon = rgba(
                1.0F,
                0.008F + atmosphere.skyR() * 0.055F,
                0.009F + atmosphere.skyG() * 0.047F,
                0.022F + atmosphere.skyB() * 0.065F);
        int nadir = rgba(1.0F, 0.002F, 0.0025F, 0.008F);

        // Four gradient walls plus top and bottom.  Cull is disabled because
        // the camera observes the cube from inside.
        shaderpackQuad(consumer, pose,
                new Vector3f(-radius, -radius, -radius), nadir,
                new Vector3f(radius, -radius, -radius), nadir,
                new Vector3f(radius, radius, -radius), zenith,
                new Vector3f(-radius, radius, -radius), zenith);
        shaderpackQuad(consumer, pose,
                new Vector3f(radius, -radius, radius), nadir,
                new Vector3f(-radius, -radius, radius), nadir,
                new Vector3f(-radius, radius, radius), zenith,
                new Vector3f(radius, radius, radius), zenith);
        shaderpackQuad(consumer, pose,
                new Vector3f(-radius, -radius, radius), nadir,
                new Vector3f(-radius, -radius, -radius), nadir,
                new Vector3f(-radius, radius, -radius), horizon,
                new Vector3f(-radius, radius, radius), horizon);
        shaderpackQuad(consumer, pose,
                new Vector3f(radius, -radius, -radius), nadir,
                new Vector3f(radius, -radius, radius), nadir,
                new Vector3f(radius, radius, radius), horizon,
                new Vector3f(radius, radius, -radius), horizon);
        shaderpackQuad(consumer, pose,
                new Vector3f(-radius, radius, -radius), zenith,
                new Vector3f(radius, radius, -radius), zenith,
                new Vector3f(radius, radius, radius), zenith,
                new Vector3f(-radius, radius, radius), zenith);
        shaderpackQuad(consumer, pose,
                new Vector3f(-radius, -radius, radius), nadir,
                new Vector3f(radius, -radius, radius), nadir,
                new Vector3f(radius, -radius, -radius), nadir,
                new Vector3f(-radius, -radius, -radius), nadir);
    }

    private static void emitShaderpackVeils(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            TimelessAtmosphere.Profile atmosphere,
            float seconds,
            float radius,
            boolean foreground) {
        int bands = foreground ? 3 : 4;
        int segments = 80;
        Random random = new Random(foreground
                ? 0x5645494C4E454152L
                : 0x5645494C464152L);
        float drift = seconds * (foreground ? -0.00011F : 0.000055F);
        for (int band = 0; band < bands; band++) {
            float baseLatitude = -0.56F + band * (foreground ? 0.38F : 0.31F)
                    + (random.nextFloat() - 0.5F) * 0.12F;
            float amplitude = 0.085F + random.nextFloat() * 0.075F;
            float width = (foreground ? 0.026F : 0.038F)
                    + random.nextFloat() * 0.022F;
            float frequency = 1.25F + random.nextFloat() * 1.15F;
            float phase = random.nextFloat() * Mth.TWO_PI;
            float alpha = atmosphere.veilStrength()
                    * (foreground ? 0.070F : 0.055F);
            boolean goldBand = band % 3 == 0;
            int core = goldBand
                    ? rgba(alpha, 0.74F, 0.40F, 0.075F)
                    : rgba(alpha * 0.82F, 0.48F, 0.52F, 0.68F);
            int edge = goldBand
                    ? rgba(alpha * 0.07F, 0.52F, 0.25F, 0.035F)
                    : rgba(alpha * 0.06F, 0.30F, 0.34F, 0.52F);
            for (int segment = 0; segment < segments; segment++) {
                float longitude0 = -Mth.PI
                        + Mth.TWO_PI * segment / segments
                        + drift;
                float longitude1 = -Mth.PI
                        + Mth.TWO_PI * (segment + 1) / segments
                        + drift;
                float latitude0 = veilLatitude(
                        baseLatitude,
                        amplitude,
                        frequency,
                        phase,
                        longitude0);
                float latitude1 = veilLatitude(
                        baseLatitude,
                        amplitude,
                        frequency,
                        phase,
                        longitude1);
                shaderpackQuad(consumer, pose,
                        skyPoint(radius, longitude0, latitude0 - width), edge,
                        skyPoint(radius, longitude0, latitude0 + width), core,
                        skyPoint(radius, longitude1, latitude1 + width), core,
                        skyPoint(radius, longitude1, latitude1 - width), edge);
            }
        }
    }

    private static void emitShaderpackStars(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            TimelessAtmosphere.Profile atmosphere,
            float seconds,
            long seed,
            int count,
            float radius,
            boolean foreground) {
        Random random = new Random(seed);
        float rotation = seconds * (foreground ? -0.000044F : 0.000018F);
        for (int i = 0; i < count; i++) {
            Vector3f direction;
            do {
                direction = new Vector3f(
                        random.nextFloat() * 2.0F - 1.0F,
                        random.nextFloat() * 2.0F - 1.0F,
                        random.nextFloat() * 2.0F - 1.0F);
            } while (direction.lengthSquared() < 0.05F
                    || direction.lengthSquared() > 1.0F);
            Vector3f center = direction.normalize(radius).rotateY(rotation);
            float size = foreground
                    ? 0.10F + random.nextFloat() * 0.18F
                    : 0.06F + random.nextFloat() * 0.12F;
            Matrix3f starRotation = new Matrix3f()
                    .rotateTowards(
                            new Vector3f(center).negate(),
                            new Vector3f(0.0F, 1.0F, 0.0F))
                    .rotateZ((float) (-random.nextDouble() * Math.PI * 2.0));
            float alpha = atmosphere.starBrightness()
                    * (foreground ? 0.32F : 0.24F);
            int color = foreground && i % 7 == 0
                    ? rgba(alpha, 0.72F, 0.43F, 0.12F)
                    : rgba(alpha, 0.58F, 0.61F, 0.72F);
            shaderpackStarVertex(consumer, pose, center, starRotation, size, -1.0F, -1.0F, color);
            shaderpackStarVertex(consumer, pose, center, starRotation, size, -1.0F, 1.0F, color);
            shaderpackStarVertex(consumer, pose, center, starRotation, size, 1.0F, 1.0F, color);
            shaderpackStarVertex(consumer, pose, center, starRotation, size, 1.0F, -1.0F, color);
        }
    }

    private static void emitShaderpackEclipse(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            TimelessAtmosphere.Profile atmosphere) {
        EclipseBasis basis = eclipseBasis(82.0F);
        int segments = 72;
        int innerGold = rgba(
                0.22F + atmosphere.gold() * 0.08F,
                0.78F,
                0.43F,
                0.075F);
        int outerGold = rgba(0.012F, 0.42F, 0.18F, 0.025F);
        for (int segment = 0; segment < segments; segment++) {
            float angle0 = Mth.TWO_PI * segment / segments;
            float angle1 = Mth.TWO_PI * (segment + 1) / segments;
            float outer0 = 4.35F + Mth.sin(angle0 * 7.0F) * 0.22F;
            float outer1 = 4.35F + Mth.sin(angle1 * 7.0F) * 0.22F;
            shaderpackQuad(consumer, pose,
                    eclipsePolarPoint(basis, angle0, 3.18F), innerGold,
                    eclipsePolarPoint(basis, angle0, outer0), outerGold,
                    eclipsePolarPoint(basis, angle1, outer1), outerGold,
                    eclipsePolarPoint(basis, angle1, 3.18F), innerGold);
        }
        Random random = new Random(0x45434C49505345L);
        for (int ray = 0; ray < 34; ray++) {
            float angle = Mth.TWO_PI * ray / 34.0F
                    + (random.nextFloat() - 0.5F) * 0.075F;
            float innerRadius = 3.65F + random.nextFloat() * 0.25F;
            float outerRadius = 4.8F + random.nextFloat() * 4.0F;
            float halfWidth = 0.045F + random.nextFloat() * 0.055F;
            float directionX = Mth.cos(angle);
            float directionY = Mth.sin(angle);
            float sideX = -directionY;
            float sideY = directionX;
            int root = rgba(0.13F, 0.70F, 0.35F, 0.055F);
            int tip = rgba(0.0F, 0.40F, 0.16F, 0.015F);
            shaderpackQuad(consumer, pose,
                    eclipsePoint(basis,
                            directionX * innerRadius - sideX * halfWidth,
                            directionY * innerRadius - sideY * halfWidth), root,
                    eclipsePoint(basis,
                            directionX * outerRadius - sideX * halfWidth * 0.1F,
                            directionY * outerRadius - sideY * halfWidth * 0.1F), tip,
                    eclipsePoint(basis,
                            directionX * outerRadius + sideX * halfWidth * 0.1F,
                            directionY * outerRadius + sideY * halfWidth * 0.1F), tip,
                    eclipsePoint(basis,
                            directionX * innerRadius + sideX * halfWidth,
                            directionY * innerRadius + sideY * halfWidth), root);
        }

        int black = rgba(0.995F, 0.001F, 0.001F, 0.003F);
        for (int segment = 0; segment < segments; segment++) {
            float angle0 = Mth.TWO_PI * segment / segments;
            float angle1 = Mth.TWO_PI * (segment + 1) / segments;
            shaderpackQuad(consumer, pose,
                    basis.center(), black,
                    eclipsePolarPoint(basis, angle0, 3.20F), black,
                    eclipsePolarPoint(basis, angle1, 3.20F), black,
                    basis.center(), black);
        }
    }

    private static void shaderpackStarVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vector3f center,
            Matrix3f rotation,
            float size,
            float x,
            float y,
            int color) {
        Vector3f point = new Vector3f(x * size, y * size, 0.0F)
                .mul(rotation)
                .add(center);
        consumer.addVertex(pose, point.x, point.y, point.z).setColor(color);
    }

    private static void shaderpackQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vector3f a,
            int colorA,
            Vector3f b,
            int colorB,
            Vector3f c,
            int colorC,
            Vector3f d,
            int colorD) {
        consumer.addVertex(pose, a.x, a.y, a.z).setColor(colorA);
        consumer.addVertex(pose, b.x, b.y, b.z).setColor(colorB);
        consumer.addVertex(pose, c.x, c.y, c.z).setColor(colorC);
        consumer.addVertex(pose, d.x, d.y, d.z).setColor(colorD);
    }

    private static void draw(
            String label,
            @Nullable SkyMesh mesh,
            RenderPipeline pipeline,
            Matrix4f view,
            Vector4f color,
            Matrix4f textureParameters,
            @Nullable AbstractTexture texture) {
        if (mesh == null || mesh.buffer().isClosed() || color.w <= 0.001F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                view,
                color,
                new Vector3f(),
                textureParameters);
        GpuTextureView colorTarget = minecraft.getMainRenderTarget().getColorTextureView();
        GpuTextureView depthTarget = minecraft.getMainRenderTarget().getDepthTextureView();
        GpuBuffer indices = QUAD_INDICES.getBuffer(mesh.indexCount());
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> label,
                        colorTarget,
                        OptionalInt.empty(),
                        depthTarget,
                        OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            if (texture != null) {
                pass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
            }
            pass.setVertexBuffer(0, mesh.buffer());
            pass.setIndexBuffer(indices, QUAD_INDICES.type());
            pass.drawIndexed(0, 0, mesh.indexCount(), 1);
        }
    }

    private void ensureResources() {
        if (background != null && !background.buffer().isClosed()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        nebulaTexture = minecraft.getTextureManager().getTexture(NEBULA_TEXTURE);
        background = buildSkyCube("Timeless deep nebula", 108.0F);
        farVeil = buildSkyCube("Timeless distant veil shell", 98.0F);
        farVeilFallback = buildVeilBands(
                "Timeless distant veil fallback",
                98.0F,
                0x5645494C464152L,
                false);
        nearVeil = buildSkyCube("Timeless foreground veil shell", 87.0F);
        nearVeilFallback = buildVeilBands(
                "Timeless foreground veil fallback",
                87.0F,
                0x5645494C4E454152L,
                true);
        farStars = buildStars("Timeless deep stars", FAR_STAR_COUNT, 102.0F, 0x4543484F4CL, false);
        nearStars = buildStars("Timeless foreground stars", NEAR_STAR_COUNT, 91.0F, 0x54494D454CL, true);
        eclipseCorona = buildEclipse("Timeless eclipse corona", 80.0F, 18.0F);
        eclipseCoronaFallback = buildEclipseFallbackCorona(
                "Timeless eclipse corona fallback",
                80.0F);
        eclipseDisc = buildEclipse("Timeless eclipse disc", 78.0F, 7.2F);
        eclipseDiscFallback = buildEclipseFallbackDisc(
                "Timeless eclipse disc fallback",
                78.0F);
    }

    void clearResources() {
        close(background);
        close(farVeil);
        close(farVeilFallback);
        close(nearVeil);
        close(nearVeilFallback);
        close(farStars);
        close(nearStars);
        close(eclipseCorona);
        close(eclipseCoronaFallback);
        close(eclipseDisc);
        close(eclipseDiscFallback);
        background = null;
        farVeil = null;
        farVeilFallback = null;
        nearVeil = null;
        nearVeilFallback = null;
        farStars = null;
        nearStars = null;
        eclipseCorona = null;
        eclipseCoronaFallback = null;
        eclipseDisc = null;
        eclipseDiscFallback = null;
        nebulaTexture = null;
        profileReady = false;
    }

    private static void close(@Nullable SkyMesh mesh) {
        if (mesh != null && !mesh.buffer().isClosed()) {
            mesh.buffer().close();
        }
    }

    private static SkyMesh buildSkyCube(String label, float radius) {
        return buildMesh(
                label,
                24,
                DefaultVertexFormat.POSITION_TEX_COLOR,
                builder -> {
                    for (int face = 0; face < 6; face++) {
                        Matrix4f pose = new Matrix4f();
                        switch (face) {
                            case 1 -> pose.rotationX((float) (Math.PI / 2.0));
                            case 2 -> pose.rotationX((float) (-Math.PI / 2.0));
                            case 3 -> pose.rotationX((float) Math.PI);
                            case 4 -> pose.rotationZ((float) (Math.PI / 2.0));
                            case 5 -> pose.rotationZ((float) (-Math.PI / 2.0));
                            default -> {
                            }
                        }
                        builder.addVertex(pose, -radius, -radius, -radius)
                                .setUv(0.0F, 0.0F)
                                .setColor(0xFFFFFFFF);
                        builder.addVertex(pose, -radius, -radius, radius)
                                .setUv(0.0F, 1.0F)
                                .setColor(0xFFFFFFFF);
                        builder.addVertex(pose, radius, -radius, radius)
                                .setUv(1.0F, 1.0F)
                                .setColor(0xFFFFFFFF);
                        builder.addVertex(pose, radius, -radius, -radius)
                                .setUv(1.0F, 0.0F)
                                .setColor(0xFFFFFFFF);
                    }
                });
    }

    /**
     * Shader packs cannot execute the procedural full-screen veil shader. This
     * fallback encodes the veil silhouette into real ribbons on the sky sphere,
     * so Iris may replace the program without turning a cube into a solid fog
     * box around the camera.
     */
    private static SkyMesh buildVeilBands(
            String label,
            float radius,
            long seed,
            boolean foreground) {
        int bands = foreground ? 3 : 4;
        int segments = 96;
        return buildMesh(
                label,
                bands * segments * 4,
                DefaultVertexFormat.POSITION_TEX_COLOR,
                builder -> {
                    Random random = new Random(seed);
                    for (int band = 0; band < bands; band++) {
                        float baseLatitude = -0.56F + band * (foreground ? 0.38F : 0.31F)
                                + (random.nextFloat() - 0.5F) * 0.12F;
                        float amplitude = 0.085F + random.nextFloat() * 0.075F;
                        float width = (foreground ? 0.030F : 0.042F)
                                + random.nextFloat() * 0.028F;
                        float frequency = 1.25F + random.nextFloat() * 1.15F;
                        float phase = random.nextFloat() * Mth.TWO_PI;
                        int inner = band % 3 == 0
                                ? rgba(foreground ? 0.17F : 0.14F, 1.0F, 0.75F, 0.30F)
                                : rgba(foreground ? 0.14F : 0.11F, 0.91F, 0.94F, 1.0F);
                        int edge = band % 3 == 0
                                ? rgba(0.015F, 1.0F, 0.68F, 0.20F)
                                : rgba(0.012F, 0.82F, 0.88F, 1.0F);
                        for (int segment = 0; segment < segments; segment++) {
                            float u0 = segment / (float) segments;
                            float u1 = (segment + 1) / (float) segments;
                            float longitude0 = -Mth.PI + u0 * Mth.TWO_PI;
                            float longitude1 = -Mth.PI + u1 * Mth.TWO_PI;
                            float latitude0 = veilLatitude(
                                    baseLatitude,
                                    amplitude,
                                    frequency,
                                    phase,
                                    longitude0);
                            float latitude1 = veilLatitude(
                                    baseLatitude,
                                    amplitude,
                                    frequency,
                                    phase,
                                    longitude1);
                            veilVertex(builder, skyPoint(radius, longitude0, latitude0 - width), u0, 0.0F, edge);
                            veilVertex(builder, skyPoint(radius, longitude0, latitude0 + width), u0, 1.0F, inner);
                            veilVertex(builder, skyPoint(radius, longitude1, latitude1 + width), u1, 1.0F, inner);
                            veilVertex(builder, skyPoint(radius, longitude1, latitude1 - width), u1, 0.0F, edge);
                        }
                    }
                });
    }

    private static float veilLatitude(
            float base,
            float amplitude,
            float frequency,
            float phase,
            float longitude) {
        return base
                + Mth.sin(longitude * frequency + phase) * amplitude
                + Mth.sin(longitude * (frequency * 2.17F) - phase * 0.63F) * amplitude * 0.34F;
    }

    private static Vector3f skyPoint(float radius, float longitude, float latitude) {
        float horizontal = Mth.cos(latitude) * radius;
        return new Vector3f(
                Mth.cos(longitude) * horizontal,
                Mth.sin(latitude) * radius,
                Mth.sin(longitude) * horizontal);
    }

    private static void veilVertex(
            VertexConsumer builder,
            Vector3f point,
            float u,
            float v,
            int color) {
        builder.addVertex(point).setUv(u, v).setColor(color);
    }

    private static SkyMesh buildStars(
            String label,
            int count,
            float radius,
            long seed,
            boolean foreground) {
        return buildMesh(
                label,
                count * 4,
                DefaultVertexFormat.POSITION_TEX_COLOR,
                builder -> {
                    Random random = new Random(seed);
                    for (int i = 0; i < count; i++) {
                        Vector3f direction;
                        do {
                            direction = new Vector3f(
                                    random.nextFloat() * 2.0F - 1.0F,
                                    random.nextFloat() * 2.0F - 1.0F,
                                    random.nextFloat() * 2.0F - 1.0F);
                        } while (direction.lengthSquared() < 0.05F
                                || direction.lengthSquared() > 1.0F);
                        Vector3f center = direction.normalize(radius);
                        float size = foreground
                                ? 0.12F + random.nextFloat() * 0.22F
                                : 0.07F + random.nextFloat() * 0.17F;
                        if (i % 47 == 0) {
                            size *= 1.8F;
                        }
                        Matrix3f rotation = new Matrix3f()
                                .rotateTowards(
                                        new Vector3f(center).negate(),
                                        new Vector3f(0.0F, 1.0F, 0.0F))
                                .rotateZ((float) (-random.nextDouble() * Math.PI * 2.0));
                        int color = foreground && i % 5 == 0
                                ? rgba(0.78F, 1.0F, 0.70F, 0.24F)
                                : rgba(foreground ? 0.64F : 0.74F, 0.96F, 0.95F, 0.91F);
                        starVertex(builder, center, rotation, size, -1.0F, -1.0F, 0.0F, 1.0F, color);
                        starVertex(builder, center, rotation, size, -1.0F, 1.0F, 0.0F, 0.0F, color);
                        starVertex(builder, center, rotation, size, 1.0F, 1.0F, 1.0F, 0.0F, color);
                        starVertex(builder, center, rotation, size, 1.0F, -1.0F, 1.0F, 1.0F, color);
                    }
                });
    }

    private static void starVertex(
            VertexConsumer builder,
            Vector3f center,
            Matrix3f rotation,
            float size,
            float x,
            float y,
            float u,
            float v,
            int color) {
        Vector3f position = new Vector3f(x * size, y * size, 0.0F)
                .mul(rotation)
                .add(center);
        builder.addVertex(position).setUv(u, v).setColor(color);
    }

    private static SkyMesh buildEclipse(String label, float radius, float size) {
        return buildMesh(
                label,
                4,
                DefaultVertexFormat.POSITION_TEX_COLOR,
                builder -> {
                    Vector3f center = new Vector3f(-0.57F, 0.56F, -0.60F).normalize(radius);
                    Vector3f normal = new Vector3f(center).normalize();
                    Vector3f right = new Vector3f(0.0F, 1.0F, 0.0F)
                            .cross(normal)
                            .normalize();
                    Vector3f up = new Vector3f(normal).cross(right).normalize();
                    eclipseVertex(builder, center, right, up, -size, -size, 0.0F, 1.0F);
                    eclipseVertex(builder, center, right, up, -size, size, 0.0F, 0.0F);
                    eclipseVertex(builder, center, right, up, size, size, 1.0F, 0.0F);
                    eclipseVertex(builder, center, right, up, size, -size, 1.0F, 1.0F);
                });
    }

    /** Circular occulting geometry used when Iris replaces the custom mask. */
    private static SkyMesh buildEclipseFallbackDisc(String label, float radius) {
        int segments = 72;
        return buildMesh(
                label,
                segments * 4,
                DefaultVertexFormat.POSITION_TEX_COLOR,
                builder -> {
                    EclipseBasis basis = eclipseBasis(radius);
                    int black = rgba(0.995F, 0.002F, 0.002F, 0.003F);
                    float discRadius = 3.18F;
                    for (int segment = 0; segment < segments; segment++) {
                        float angle0 = Mth.TWO_PI * segment / segments;
                        float angle1 = Mth.TWO_PI * (segment + 1) / segments;
                        Vector3f outer0 = eclipsePoint(basis, Mth.cos(angle0) * discRadius, Mth.sin(angle0) * discRadius);
                        Vector3f outer1 = eclipsePoint(basis, Mth.cos(angle1) * discRadius, Mth.sin(angle1) * discRadius);
                        // The fourth vertex repeats the centre, producing one
                        // valid triangle while retaining the QUADS index mode.
                        fallbackEclipseVertex(builder, basis.center(), 0.5F, 0.5F, black);
                        fallbackEclipseVertex(builder, outer0, 0.0F, 0.0F, black);
                        fallbackEclipseVertex(builder, outer1, 1.0F, 0.0F, black);
                        fallbackEclipseVertex(builder, basis.center(), 0.5F, 0.5F, black);
                    }
                });
    }

    /**
     * A true annulus plus irregular tapered rays. Its silhouette survives any
     * shader-pack replacement and never exposes the old square eclipse quad.
     */
    private static SkyMesh buildEclipseFallbackCorona(String label, float radius) {
        int ringSegments = 72;
        int rayCount = 40;
        return buildMesh(
                label,
                (ringSegments + rayCount) * 4,
                DefaultVertexFormat.POSITION_TEX_COLOR,
                builder -> {
                    EclipseBasis basis = eclipseBasis(radius);
                    Random random = new Random(0x45434C49505345L);
                    int innerGold = rgba(0.58F, 1.0F, 0.69F, 0.16F);
                    int outerGold = rgba(0.065F, 1.0F, 0.52F, 0.055F);
                    for (int segment = 0; segment < ringSegments; segment++) {
                        float angle0 = Mth.TWO_PI * segment / ringSegments;
                        float angle1 = Mth.TWO_PI * (segment + 1) / ringSegments;
                        float outer0 = 4.15F + Mth.sin(angle0 * 7.0F) * 0.24F;
                        float outer1 = 4.15F + Mth.sin(angle1 * 7.0F) * 0.24F;
                        fallbackEclipseVertex(builder, eclipsePolarPoint(basis, angle0, 3.15F), 0.0F, 0.0F, innerGold);
                        fallbackEclipseVertex(builder, eclipsePolarPoint(basis, angle0, outer0), 0.0F, 1.0F, outerGold);
                        fallbackEclipseVertex(builder, eclipsePolarPoint(basis, angle1, outer1), 1.0F, 1.0F, outerGold);
                        fallbackEclipseVertex(builder, eclipsePolarPoint(basis, angle1, 3.15F), 1.0F, 0.0F, innerGold);
                    }
                    for (int ray = 0; ray < rayCount; ray++) {
                        float angle = Mth.TWO_PI * ray / rayCount
                                + (random.nextFloat() - 0.5F) * 0.075F;
                        float innerRadius = 3.52F + random.nextFloat() * 0.34F;
                        float outerRadius = 5.0F + random.nextFloat() * 5.9F;
                        float innerHalfWidth = 0.060F + random.nextFloat() * 0.075F;
                        float outerHalfWidth = innerHalfWidth * 0.12F;
                        float sideX = -Mth.sin(angle);
                        float sideY = Mth.cos(angle);
                        float directionX = Mth.cos(angle);
                        float directionY = Mth.sin(angle);
                        int rayRoot = rgba(0.32F, 1.0F, 0.74F, 0.25F);
                        int rayTip = rgba(0.0F, 1.0F, 0.55F, 0.08F);
                        fallbackEclipseVertex(
                                builder,
                                eclipsePoint(
                                        basis,
                                        directionX * innerRadius - sideX * innerHalfWidth,
                                        directionY * innerRadius - sideY * innerHalfWidth),
                                0.0F,
                                0.0F,
                                rayRoot);
                        fallbackEclipseVertex(
                                builder,
                                eclipsePoint(
                                        basis,
                                        directionX * outerRadius - sideX * outerHalfWidth,
                                        directionY * outerRadius - sideY * outerHalfWidth),
                                0.0F,
                                1.0F,
                                rayTip);
                        fallbackEclipseVertex(
                                builder,
                                eclipsePoint(
                                        basis,
                                        directionX * outerRadius + sideX * outerHalfWidth,
                                        directionY * outerRadius + sideY * outerHalfWidth),
                                1.0F,
                                1.0F,
                                rayTip);
                        fallbackEclipseVertex(
                                builder,
                                eclipsePoint(
                                        basis,
                                        directionX * innerRadius + sideX * innerHalfWidth,
                                        directionY * innerRadius + sideY * innerHalfWidth),
                                1.0F,
                                0.0F,
                                rayRoot);
                    }
                });
    }

    private static EclipseBasis eclipseBasis(float radius) {
        Vector3f center = new Vector3f(-0.57F, 0.56F, -0.60F).normalize(radius);
        Vector3f normal = new Vector3f(center).normalize();
        Vector3f right = new Vector3f(0.0F, 1.0F, 0.0F).cross(normal).normalize();
        Vector3f up = new Vector3f(normal).cross(right).normalize();
        return new EclipseBasis(center, right, up);
    }

    private static Vector3f eclipsePolarPoint(EclipseBasis basis, float angle, float radius) {
        return eclipsePoint(basis, Mth.cos(angle) * radius, Mth.sin(angle) * radius);
    }

    private static Vector3f eclipsePoint(EclipseBasis basis, float x, float y) {
        return new Vector3f(basis.center()).fma(x, basis.right()).fma(y, basis.up());
    }

    private static void fallbackEclipseVertex(
            VertexConsumer builder,
            Vector3f point,
            float u,
            float v,
            int color) {
        builder.addVertex(point).setUv(u, v).setColor(color);
    }

    private static void eclipseVertex(
            VertexConsumer builder,
            Vector3f center,
            Vector3f right,
            Vector3f up,
            float x,
            float y,
            float u,
            float v) {
        builder.addVertex(new Vector3f(center)
                        .fma(x, right)
                        .fma(y, up))
                .setUv(u, v)
                .setColor(0xFFFFFFFF);
    }

    private static SkyMesh buildGreekArchitecture() {
        return buildMesh(
                "Timeless Greek horizon",
                1800,
                DefaultVertexFormat.POSITION_COLOR,
                builder -> {
                    HorizonBuilder scene = new HorizonBuilder(builder, 0.52F, 76.0F);
                    int stone = rgba(0.92F, 0.17F, 0.19F, 0.24F);
                    int shade = rgba(0.92F, 0.085F, 0.095F, 0.14F);
                    int gold = rgba(0.76F, 0.92F, 0.66F, 0.18F);
                    scene.box(-17.5F, -20.0F, -1.5F, 17.5F, -18.5F, 1.5F, shade);
                    scene.box(-16.0F, -18.5F, -1.1F, 16.0F, -17.2F, 1.1F, stone);
                    for (int i = -6; i <= 6; i += 2) {
                        scene.box(i - 0.58F, -17.2F, -0.62F, i + 0.58F, -4.2F, 0.62F, stone);
                        scene.box(i - 0.86F, -17.2F, -0.83F, i + 0.86F, -16.3F, 0.83F, gold);
                        scene.box(i - 0.88F, -4.2F, -0.82F, i + 0.88F, -3.25F, 0.82F, gold);
                    }
                    scene.box(-15.0F, -3.25F, -1.15F, 15.0F, -1.35F, 1.15F, stone);
                    scene.box(-14.0F, -1.35F, -0.95F, 14.0F, -0.72F, 0.95F, gold);
                    for (int tier = 0; tier < 6; tier++) {
                        float inset = tier * 2.15F;
                        scene.box(
                                -13.0F + inset,
                                -0.72F + tier * 1.08F,
                                -0.72F,
                                13.0F - inset,
                                0.22F + tier * 1.08F,
                                0.72F,
                                tier % 2 == 0 ? stone : shade);
                    }
                    // A second, dim colonnade supplies overlap and apparent depth.
                    HorizonBuilder rear = new HorizonBuilder(builder, 0.68F, 89.0F);
                    for (int i = -5; i <= 5; i += 2) {
                        rear.box(i - 0.40F, -19.0F, -0.45F, i + 0.40F, -8.0F, 0.45F, shade);
                    }
                    rear.box(-7.0F, -8.0F, -0.65F, 7.0F, -6.8F, 0.65F, shade);
                });
    }

    private static SkyMesh buildEgyptianArchitecture() {
        return buildMesh(
                "Timeless Egyptian horizon",
                1900,
                DefaultVertexFormat.POSITION_COLOR,
                builder -> {
                    HorizonBuilder scene = new HorizonBuilder(builder, 2.76F, 75.0F);
                    int lapis = rgba(0.94F, 0.045F, 0.07F, 0.16F);
                    int black = rgba(0.95F, 0.035F, 0.028F, 0.045F);
                    int gold = rgba(0.84F, 1.0F, 0.58F, 0.10F);
                    scene.box(-18.0F, -20.0F, -1.8F, 18.0F, -18.2F, 1.8F, black);
                    scene.box(-14.0F, -18.2F, -1.45F, -6.0F, 1.5F, 1.45F, lapis);
                    scene.box(6.0F, -18.2F, -1.45F, 14.0F, 1.5F, 1.45F, lapis);
                    scene.box(-15.0F, 1.5F, -1.7F, -5.0F, 3.2F, 1.7F, gold);
                    scene.box(5.0F, 1.5F, -1.7F, 15.0F, 3.2F, 1.7F, gold);
                    scene.box(-6.2F, -1.1F, -1.0F, 6.2F, 1.2F, 1.0F, black);
                    scene.box(-5.3F, 1.2F, -0.85F, 5.3F, 2.1F, 0.85F, gold);
                    for (int side : new int[]{-1, 1}) {
                        float x = side * 19.0F;
                        scene.box(x - 0.75F, -18.2F, -0.75F, x + 0.75F, -3.5F, 0.75F, lapis);
                        scene.box(x - 0.58F, -3.5F, -0.58F, x + 0.58F, 2.8F, 0.58F, gold);
                        scene.box(x - 0.28F, 2.8F, -0.28F, x + 0.28F, 6.2F, 0.28F, gold);
                    }
                    for (int band = 0; band < 4; band++) {
                        float y = -14.0F + band * 4.0F;
                        scene.box(-13.9F, y, -1.62F, -6.1F, y + 0.24F, 1.62F, gold);
                        scene.box(6.1F, y, -1.62F, 13.9F, y + 0.24F, 1.62F, gold);
                    }
                    HorizonBuilder rear = new HorizonBuilder(builder, 2.60F, 90.0F);
                    rear.box(-11.0F, -19.0F, -1.0F, -7.0F, -1.0F, 1.0F, black);
                    rear.box(7.0F, -19.0F, -1.0F, 11.0F, -1.0F, 1.0F, black);
                    rear.box(-7.2F, -4.0F, -0.8F, 7.2F, -2.2F, 0.8F, lapis);
                });
    }

    private static SkyMesh buildMedievalArchitecture() {
        return buildMesh(
                "Timeless Medieval horizon",
                2100,
                DefaultVertexFormat.POSITION_COLOR,
                builder -> {
                    HorizonBuilder scene = new HorizonBuilder(builder, 4.82F, 77.0F);
                    int slate = rgba(0.92F, 0.09F, 0.11F, 0.17F);
                    int shadow = rgba(0.96F, 0.030F, 0.036F, 0.055F);
                    int ivory = rgba(0.69F, 0.76F, 0.75F, 0.66F);
                    scene.box(-16.0F, -20.0F, -1.6F, 16.0F, -18.2F, 1.6F, shadow);
                    for (int side : new int[]{-1, 1}) {
                        float x = side * 9.0F;
                        scene.box(x - 3.1F, -18.2F, -1.35F, x + 3.1F, 1.7F, 1.35F, slate);
                        for (int battlement = -2; battlement <= 2; battlement += 2) {
                            scene.box(
                                    x + battlement - 0.65F,
                                    1.7F,
                                    -1.45F,
                                    x + battlement + 0.65F,
                                    4.1F,
                                    1.45F,
                                    shadow);
                        }
                        scene.box(x - 0.32F, -7.4F, -1.52F, x + 0.32F, -3.2F, 1.52F, ivory);
                    }
                    scene.box(-5.9F, -18.2F, -1.05F, -3.3F, -2.8F, 1.05F, slate);
                    scene.box(3.3F, -18.2F, -1.05F, 5.9F, -2.8F, 1.05F, slate);
                    scene.box(-4.0F, -2.8F, -1.12F, 4.0F, -0.3F, 1.12F, shadow);
                    for (int tier = 0; tier < 5; tier++) {
                        float inset = tier * 0.85F;
                        scene.box(
                                -4.4F + inset,
                                -0.3F + tier * 1.25F,
                                -0.8F,
                                4.4F - inset,
                                0.85F + tier * 1.25F,
                                0.8F,
                                tier == 4 ? ivory : slate);
                    }
                    HorizonBuilder rear = new HorizonBuilder(builder, 5.02F, 91.0F);
                    rear.box(-13.0F, -19.0F, -0.9F, -9.0F, -3.5F, 0.9F, shadow);
                    rear.box(9.0F, -19.0F, -0.9F, 13.0F, -3.5F, 0.9F, shadow);
                    rear.box(-9.2F, -5.2F, -0.75F, 9.2F, -3.8F, 0.75F, shadow);
                });
    }

    private static SkyMesh buildFractures() {
        return buildMesh(
                "Timeless memory fracture field",
                1024,
                DefaultVertexFormat.POSITION_COLOR,
                builder -> {
                    Random random = new Random(0x5255494E53L);
                    int ivory = rgba(0.88F, 0.96F, 0.93F, 0.82F);
                    int gold = rgba(0.82F, 1.0F, 0.60F, 0.13F);
                    for (int crack = 0; crack < 24; crack++) {
                        Vector3f point = new Vector3f(
                                random.nextFloat() * 2.0F - 1.0F,
                                random.nextFloat() * 1.6F - 0.55F,
                                random.nextFloat() * 2.0F - 1.0F);
                        if (point.lengthSquared() < 0.08F) {
                            crack--;
                            continue;
                        }
                        point.normalize(84.0F);
                        Vector3f tangent = new Vector3f(0.0F, 1.0F, 0.0F)
                                .cross(new Vector3f(point).normalize());
                        if (tangent.lengthSquared() < 0.02F) {
                            tangent.set(1.0F, 0.0F, 0.0F);
                        }
                        tangent.normalize();
                        Vector3f surfaceNormal = new Vector3f(point).normalize();
                        tangent.rotateAxis(
                                random.nextFloat() * (float) Math.PI,
                                surfaceNormal.x,
                                surfaceNormal.y,
                                surfaceNormal.z);
                        for (int segment = 0; segment < 3 + random.nextInt(3); segment++) {
                            float length = 2.4F + random.nextFloat() * 4.2F;
                            Vector3f next = new Vector3f(point)
                                    .fma(length, tangent)
                                    .normalize(84.0F);
                            Vector3f direction = new Vector3f(next).sub(point).normalize();
                            Vector3f side = new Vector3f(point)
                                    .normalize()
                                    .cross(direction)
                                    .normalize(0.055F + random.nextFloat() * 0.075F);
                            int color = (crack + segment) % 3 == 0 ? gold : ivory;
                            builder.addVertex(new Vector3f(point).sub(side)).setColor(color);
                            builder.addVertex(new Vector3f(point).add(side)).setColor(color);
                            builder.addVertex(new Vector3f(next).add(side)).setColor(color);
                            builder.addVertex(new Vector3f(next).sub(side)).setColor(color);
                            point = next;
                            surfaceNormal.set(point).normalize();
                            tangent.rotateAxis(
                                    (random.nextFloat() - 0.5F) * 0.38F,
                                    surfaceNormal.x,
                                    surfaceNormal.y,
                                    surfaceNormal.z);
                            tangent.fma(-tangent.dot(surfaceNormal), surfaceNormal).normalize();
                        }
                    }
                });
    }

    private static SkyMesh buildMesh(
            String label,
            int maximumVertices,
            VertexFormat format,
            Consumer<BufferBuilder> writer) {
        try (ByteBufferBuilder memory = ByteBufferBuilder.exactlySized(
                maximumVertices * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(
                    memory,
                    VertexFormat.Mode.QUADS,
                    format);
            writer.accept(builder);
            try (MeshData mesh = builder.buildOrThrow()) {
                GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                        () -> label,
                        GpuBuffer.USAGE_VERTEX,
                        mesh.vertexBuffer());
                return new SkyMesh(buffer, mesh.drawState().indexCount());
            }
        }
    }

    private static int rgba(float alpha, float red, float green, float blue) {
        int a = Math.clamp(Math.round(alpha * 255.0F), 0, 255);
        int r = Math.clamp(Math.round(red * 255.0F), 0, 255);
        int g = Math.clamp(Math.round(green * 255.0F), 0, 255);
        int b = Math.clamp(Math.round(blue * 255.0F), 0, 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private record SkyMesh(GpuBuffer buffer, int indexCount) {
    }

    private record EclipseBasis(Vector3f center, Vector3f right, Vector3f up) {
    }

    /** Local facade converted to a vertical plane tangent to the sky sphere. */
    private static final class HorizonBuilder {
        private final VertexConsumer builder;
        private final Vector3f radial;
        private final Vector3f tangent;
        private final float radius;

        private HorizonBuilder(VertexConsumer builder, float angle, float radius) {
            this.builder = builder;
            this.radial = new Vector3f(Mth.cos(angle), 0.0F, Mth.sin(angle));
            this.tangent = new Vector3f(-Mth.sin(angle), 0.0F, Mth.cos(angle));
            this.radius = radius;
        }

        private void box(
                float minX,
                float minY,
                float minZ,
                float maxX,
                float maxY,
                float maxZ,
                int color) {
            Vector3f p000 = point(minX, minY, minZ);
            Vector3f p001 = point(minX, minY, maxZ);
            Vector3f p010 = point(minX, maxY, minZ);
            Vector3f p011 = point(minX, maxY, maxZ);
            Vector3f p100 = point(maxX, minY, minZ);
            Vector3f p101 = point(maxX, minY, maxZ);
            Vector3f p110 = point(maxX, maxY, minZ);
            Vector3f p111 = point(maxX, maxY, maxZ);
            quad(p000, p100, p110, p010, color);
            quad(p101, p001, p011, p111, color);
            quad(p001, p000, p010, p011, color);
            quad(p100, p101, p111, p110, color);
            quad(p010, p110, p111, p011, color);
            quad(p001, p101, p100, p000, color);
        }

        private Vector3f point(float x, float y, float z) {
            return new Vector3f(radial)
                    .mul(radius + z)
                    .fma(x, tangent)
                    .add(0.0F, y, 0.0F);
        }

        private void quad(
                Vector3f a,
                Vector3f b,
                Vector3f c,
                Vector3f d,
                int color) {
            builder.addVertex(a).setColor(color);
            builder.addVertex(b).setColor(color);
            builder.addVertex(c).setColor(color);
            builder.addVertex(d).setColor(color);
        }
    }
}
