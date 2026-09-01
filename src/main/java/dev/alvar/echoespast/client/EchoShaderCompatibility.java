package dev.alvar.echoespast.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Optional Iris / Oculus hooks. Resolved by reflection so shader packs remain
 * soft dependencies. Used to keep historical-air (ADDED) solids from writing
 * into the shader shadow map while leaving their translucent entity look
 * unchanged in the main pass.
 */
public final class EchoShaderCompatibility {
    private static final MethodHandle SHADER_PACK_ACTIVE = resolveIrisApiBoolean("isShaderPackInUse");
    private static final MethodHandle API_SHADOW_PASS = resolveIrisApiBoolean("isRenderingShadowPass");
    private static final MethodHandle SHADOW_PASS = resolveShadowPass();
    private static final @org.jspecify.annotations.Nullable IrisPipelineApi IRIS_PIPELINES =
            resolveIrisPipelines();
    private static final Set<RenderPipeline> ASSIGNED_PIPELINES = Collections.newSetFromMap(
            new IdentityHashMap<>());

    private static MethodHandle resolveIrisApiBoolean(String methodName) {
        String[] owners = {
                "net.irisshaders.iris.api.v0.IrisApi",
                "net.coderbot.iris.api.v0.IrisApi"
        };
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        for (String owner : owners) {
            try {
                Class<?> type = Class.forName(owner);
                Object instance = type.getMethod("getInstance").invoke(null);
                return lookup.unreflect(type.getMethod(methodName)).bindTo(instance);
            } catch (ReflectiveOperationException ignored) {
                // Iris/Oculus is optional, or this API revision predates the hook.
            }
        }
        return null;
    }

    private static MethodHandle resolveShadowPass() {
        String[] owners = {
                "net.irisshaders.iris.shadows.ShadowRenderingState",
                "net.coderbot.iris.shadows.ShadowRenderingState"
        };
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        for (String owner : owners) {
            try {
                Class<?> type = Class.forName(owner);
                Method method = type.getMethod("areShadowsCurrentlyBeingRendered");
                return lookup.findStatic(
                        type,
                        method.getName(),
                        MethodType.methodType(boolean.class));
            } catch (ReflectiveOperationException ignored) {
                // Shader mod absent or API drifted; keep probing.
            }
        }
        return null;
    }

    private static IrisPipelineApi resolveIrisPipelines() {
        String[] roots = {
                "net.irisshaders.iris.api.v0",
                "net.coderbot.iris.api.v0"
        };
        for (String root : roots) {
            try {
                Class<?> apiType = Class.forName(root + ".IrisApi");
                Class<?> programType = Class.forName(root + ".IrisProgram");
                Object instance = apiType.getMethod("getInstance").invoke(null);
                Method assign = apiType.getMethod("assignPipeline", RenderPipeline.class, programType);
                return new IrisPipelineApi(instance, assign, programType);
            } catch (ReflectiveOperationException ignored) {
                // Iris/Oculus is optional, or this API revision predates pipeline assignment.
            }
        }
        return null;
    }

    /**
     * {@code true} while Iris/Oculus is building the sun/moon shadow map.
     * Cheap no-op when no shader mod is present.
     */
    public static boolean isShadowPass() {
        if (invokeBoolean(API_SHADOW_PASS)) {
            return true;
        }
        return invokeBoolean(SHADOW_PASS);
    }

    /**
     * Whether Iris/Oculus currently owns the world pipeline. Sky effects use
     * this to leave headroom for a shader pack's own exposure and bloom.
     */
    public static boolean isShaderPackActive() {
        return invokeBoolean(SHADER_PACK_ACTIVE);
    }

    /**
     * Packs an ARGB vertex colour while reserving luminance and alpha headroom
     * for a shaderpack's exposure and bloom. The vanilla renderer keeps the
     * authored colour byte-for-byte; only an active Iris/Oculus pipeline is
     * attenuated.
     */
    static int shaderPackExposureColor(
            float alpha,
            int rgb,
            float brightestChannel,
            float alphaScale) {
        float adjustedAlpha = alpha;
        int adjustedRgb = rgb & 0xFFFFFF;
        if (isShaderPackActive()) {
            adjustedAlpha *= Math.clamp(alphaScale, 0.0F, 1.0F);
            int red = (adjustedRgb >>> 16) & 0xFF;
            int green = (adjustedRgb >>> 8) & 0xFF;
            int blue = adjustedRgb & 0xFF;
            int peak = Math.max(red, Math.max(green, blue));
            int ceiling = Math.clamp(Math.round(brightestChannel * 255.0F), 0, 255);
            if (peak > ceiling && peak > 0) {
                float scale = ceiling / (float) peak;
                red = Math.round(red * scale);
                green = Math.round(green * scale);
                blue = Math.round(blue * scale);
                adjustedRgb = red << 16 | green << 8 | blue;
            }
        }
        int packedAlpha = Math.clamp(Math.round(adjustedAlpha * 255.0F), 0, 255);
        return packedAlpha << 24 | adjustedRgb;
    }

    /**
     * Gives Iris a vanilla semantic for one of our custom pipelines. A shader
     * pack can then replace that semantic without needing to know this mod's
     * program IDs. Assignment is deliberately idempotent because Iris rejects
     * duplicate mappings during resource reloads.
     */
    public static boolean assignPipeline(RenderPipeline pipeline, String irisProgramName) {
        IrisPipelineApi api = IRIS_PIPELINES;
        if (api == null) {
            return false;
        }
        synchronized (ASSIGNED_PIPELINES) {
            if (ASSIGNED_PIPELINES.contains(pipeline)) {
                return true;
            }
            try {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object program = Enum.valueOf(
                        (Class<? extends Enum>) api.programType().asSubclass(Enum.class),
                        irisProgramName);
                api.assign().invoke(api.instance(), pipeline, program);
                ASSIGNED_PIPELINES.add(pipeline);
                return true;
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                return false;
            }
        }
    }

    private static boolean invokeBoolean(MethodHandle handle) {
        if (handle == null) {
            return false;
        }
        try {
            return (boolean) handle.invokeExact();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private EchoShaderCompatibility() {
    }

    private record IrisPipelineApi(Object instance, Method assign, Class<?> programType) {
    }
}
