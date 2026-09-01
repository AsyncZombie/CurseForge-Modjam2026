package dev.alvar.echoespast.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.network.PhilosophersStoneVisualPayload;
import dev.alvar.echoespast.network.PhilosophersStoneVisualProgressPayload;
import dev.alvar.echoespast.visual.EchoProjectionStyle;
import dev.alvar.echoespast.visual.PhilosophersStoneVisualTiming;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

/**
 * Owns the transition in which a remembered volume is exchanged with the
 * present and the restrained grade which distinguishes physical history while
 * that transaction remains active.
 */
public final class ClientPhilosophersStoneVision {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long SERVER_PROGRESS_BLEND_NANOS = 50_000_000L;
    private static final Identifier POST_EFFECT =
            Identifier.fromNamespaceAndPath(
                    EchoesShowThePast.MOD_ID,
                    "philosophers_stone");

    private static volatile Transition transition;
    private static Blueprint blueprint = Blueprint.EMPTY;
    private static ViewFrame viewFrame = ViewFrame.EMPTY;
    private static ActiveVolume activeVolume =
            ActiveVolume.EMPTY;
    private static volatile boolean materializedPast;
    private static long materializedStartNanos;
    private static boolean postEffectOwned;
    private static boolean failureReported;

    public static boolean isActive() {
        return materializedPast || transition != null;
    }

    public static void receive(PhilosophersStoneVisualPayload payload) {
        long now = System.nanoTime();
        if (payload.phase() == PhilosophersStoneVisualPayload.CLEAR) {
            clearImmediately();
            return;
        }
        if (payload.phase()
                == PhilosophersStoneVisualPayload.STABLE_PAST) {
            transition = null;
            blueprint = Blueprint.EMPTY;
            activeVolume = new ActiveVolume(
                    payload.center(),
                    payload.halfExtents(),
                    payload.direction());
            materializedPast = true;
            materializedStartNanos = now;
            return;
        }
        boolean restoring = payload.phase()
                == PhilosophersStoneVisualPayload.RESTORE_PRESENT;
        boolean waveOnly = payload.phase()
                == PhilosophersStoneVisualPayload.WAVE_ONLY;
        if (!restoring || blueprint.isEmpty()) {
            Blueprint captured = captureBlueprint();
            if (!captured.isEmpty()) {
                blueprint = captured;
            }
        }
        if (!restoring) {
            materializedPast = false;
        }
        activeVolume = new ActiveVolume(
                payload.center(),
                payload.halfExtents(),
                payload.direction());
        transition = new Transition(
                payload.center(),
                payload.halfExtents(),
                payload.direction(),
                restoring,
                waveOnly,
                now,
                now + payload.durationTicks() * 50_000_000L,
                0.0F,
                0.0F,
                now,
                false);
    }

    public static void receiveProgress(
            PhilosophersStoneVisualProgressPayload payload) {
        Transition active = transition;
        if (active == null) {
            return;
        }
        long now = System.nanoTime();
        float displayedProgress = transitionProgress(active, now);
        transition = new Transition(
                active.center(),
                active.halfExtents(),
                active.direction(),
                active.restoring(),
                active.waveOnly(),
                active.startNanos(),
                active.endNanos(),
                displayedProgress,
                Math.max(displayedProgress, payload.progress()),
                now,
                true);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clearImmediately();
            return;
        }
        Transition active = transition;
        long now = System.nanoTime();
        if (active != null && transitionFinished(active, now)) {
            boolean restoring = active.restoring();
            boolean waveOnly = active.waveOnly();
            transition = null;
            viewFrame = ViewFrame.EMPTY;
            if (restoring || waveOnly) {
                materializedPast = false;
                blueprint = Blueprint.EMPTY;
                activeVolume = ActiveVolume.EMPTY;
                if (restoring) {
                    ClientEchoState.onStoneControlReleased();
                }
            } else {
                materializedPast = true;
                materializedStartNanos =
                        System.nanoTime();
            }
        }
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        long now = System.nanoTime();
        Transition active = transition;
        ActiveVolume renderedVolume;
        if (active != null && !transitionFinished(active, now)) {
            renderedVolume = new ActiveVolume(
                    active.center(),
                    active.halfExtents(),
                    active.direction());
        } else if (materializedPast
                && !activeVolume.isEmpty()) {
            renderedVolume = activeVolume;
        } else {
            viewFrame = ViewFrame.EMPTY;
            return;
        }

        Vec3 camera = event.getCamera().position();
        var cameraState = event.getRenderState().cameraRenderState;
        Matrix4f viewRotation =
                new Matrix4f(cameraState.viewRotationMatrix);

        Vec3 relative =
                renderedVolume.center()
                        .subtract(camera);
        Vector3f viewCenter = new Vector3f(
                (float) relative.x,
                (float) relative.y,
                (float) relative.z);
        viewRotation.transformPosition(viewCenter);

        Vector3f axisX = viewRotation.transformDirection(
                new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f axisY = viewRotation.transformDirection(
                new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f axisZ = viewRotation.transformDirection(
                new Vector3f(0.0F, 0.0F, 1.0F));
        Vector3f sweep = viewRotation.transformDirection(new Vector3f(
                (float) renderedVolume.direction().x,
                (float) renderedVolume.direction().y,
                (float) renderedVolume.direction().z));
        sweep.normalize();

        Vector4f projected = new Vector4f(
                viewCenter.x,
                viewCenter.y,
                viewCenter.z,
                1.0F);
        new Matrix4f(cameraState.projectionMatrix).transform(projected);
        float inverseW = Math.abs(projected.w) > 1.0E-5F
                ? 1.0F / projected.w
                : 0.0F;
        float screenX = Math.clamp(
                projected.x * inverseW * 0.5F + 0.5F,
                -1.0F,
                2.0F);
        float screenY = Math.clamp(
                projected.y * inverseW * 0.5F + 0.5F,
                -1.0F,
                2.0F);

        Vec3 extent =
                renderedVolume.halfExtents();
        float halfSpan = (float) Math.max(
                0.5,
                Math.abs(renderedVolume.direction().x)
                                * extent.x
                        + Math.abs(
                                        renderedVolume
                                                .direction()
                                                .y)
                                * extent.y
                        + Math.abs(
                                        renderedVolume
                                                .direction()
                                                .z)
                                * extent.z);
        viewFrame = new ViewFrame(
                new Matrix4f(cameraState.projectionMatrix).invert(),
                RenderSystem.getDevice().isZZeroToOne(),
                viewCenter,
                axisX,
                axisY,
                axisZ,
                sweep,
                new Vector3f(
                        (float) extent.x,
                        (float) extent.y,
                        (float) extent.z),
                halfSpan,
                screenX,
                screenY);
    }

    public static boolean hasPostEffectPriority() {
        return visual(System.nanoTime()).strength() > 0.001F
                && EchoesConfig.POST_PROCESSING.getAsBoolean();
    }

    public static void renderFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        Visual visual = visual(now);
        if (visual.strength() <= 0.001F) {
            releasePostEffect(minecraft);
            return;
        }
        if (!EchoesConfig.POST_PROCESSING.getAsBoolean()) {
            releasePostEffect(minecraft);
            return;
        }

        Identifier current = minecraft.gameRenderer.currentPostEffect();
        if (!POST_EFFECT.equals(current)) {
            try {
                minecraft.gameRenderer.setPostEffect(POST_EFFECT);
                postEffectOwned = true;
            } catch (RuntimeException exception) {
                reportFailure("activate", exception);
                return;
            }
        } else {
            postEffectOwned = true;
        }

        try {
            PhilosophersStonePostEffectUniforms.update(
                    minecraft,
                    POST_EFFECT,
                    visual,
                    viewFrame,
                    ClientEchoState.combinedShadowStrength(now),
                    ClientEchoState.isSurfaceWaveActive(now),
                    ClientHorusVision.visualStrength(now),
                    ClientMedusaVision.composite(now),
                    ClientHolyGrailVision.localStrength(now),
                    ClientHolyGrailVision.localRelease(now));
        } catch (RuntimeException exception) {
            reportFailure("update", exception);
            releasePostEffect(minecraft);
        }
    }

    public static void preparePostEffect() {
        if (!EchoesConfig.POST_PROCESSING.getAsBoolean()) {
            return;
        }
        try {
            PhilosophersStonePostEffectUniforms.prepare(
                    Minecraft.getInstance(),
                    POST_EFFECT);
        } catch (RuntimeException exception) {
            reportFailure("prepare", exception);
        }
    }

    public static boolean isPostEffectOperational() {
        return postEffectOwned
                && POST_EFFECT.equals(
                        Minecraft.getInstance()
                                .gameRenderer
                                .currentPostEffect());
    }

    public static Visual visual(long now) {
        Transition active = transition;
        if (active == null || transitionFinished(active, now)) {
            if (!materializedPast
                    || activeVolume.isEmpty()) {
                return Visual.NONE;
            }
            return new Visual(
                    0.58F,
                    1.0F,
                    PhilosophersStoneVisualTiming
                            .SEAM_END,
                    false,
                    true,
                    Math.max(
                                    0L,
                                    now
                                            - materializedStartNanos)
                            / 1_000_000_000.0F,
                    activeVolume.center(),
                    activeVolume.halfExtents(),
                    activeVolume.direction());
        }
        float progress = transitionProgress(active, now);
        return new Visual(
                PhilosophersStoneVisualTiming.strength(progress),
                progress,
                PhilosophersStoneVisualTiming.front(
                        progress,
                        active.restoring()),
                active.restoring(),
                false,
                (now - active.startNanos()) / 1_000_000_000.0F,
                active.center(),
                active.halfExtents(),
                active.direction());
    }

    static boolean usesCondensationGeometry(long now) {
        return isTransitionActive(now)
                && !blueprint.isEmpty();
    }

    static boolean isTransitionActive(long now) {
        Transition active = transition;
        return active != null && !transitionFinished(active, now);
    }

    static boolean isPastMaterialized() {
        return materializedPast;
    }

    static boolean controlsEchoCaches(long now) {
        return materializedPast || isTransitionActive(now);
    }

    /**
     * Once the materialization crest has reached a position, the synchronized
     * vanilla block is the only authoritative rendering for it. This prevents
     * the normal past-view occluder from hiding that final mesh until the echo
     * cache is rebuilt at the end of the transition.
     */
    static boolean usesNativePastState(
            BlockPos position,
            long now) {
        Transition active = transition;
        if (active == null || transitionFinished(active, now)) {
            return materializedPast;
        }
        float progress = transitionProgress(active, now);
        float coordinate =
                PhilosophersStoneVisualTiming.normalizedCoordinate(
                        position.getCenter(),
                        active.center(),
                        active.halfExtents());
        return PhilosophersStoneVisualTiming
                .nativeWorldOwnsBlock(
                        coordinate,
                        progress,
                        active.restoring(),
                        materializedPast);
    }

    static List<ClientEchoState.GhostModel> rememberedModels() {
        return blueprint.remembered();
    }

    static List<ClientEchoState.GhostModel> presentModels() {
        return blueprint.present();
    }

    static List<ClientEchoState.ScanFace> surfaceFaces() {
        return blueprint.surfaces();
    }

    /**
     * The server sends an edited historical branch before starting the return
     * crest. Replace only its exchange meshes now; retaining the surface field
     * keeps the travelling shader and geometric fallback continuous.
     */
    static void refreshBlueprint(
            List<ClientEchoState.GhostModel> remembered,
            List<ClientEchoState.GhostModel> present) {
        blueprint = new Blueprint(
                List.copyOf(remembered),
                List.copyOf(present),
                blueprint.surfaces(),
                blueprint.entities());
    }

    /**
     * Returns how completely this remembered block has crossed from echo to
     * physical reality. The same normalized coordinate is consumed by the
     * post shader and geometric fallback, avoiding independent visual clocks.
     */
    static float condensation(
            ClientEchoState.GhostModel ghost,
            long now) {
        Visual current = visual(now);
        float coordinate =
                PhilosophersStoneVisualTiming.normalizedCoordinate(
                        ghost.position().getCenter(),
                        current.center(),
                        current.halfExtents());
        return PhilosophersStoneVisualTiming.condensation(
                coordinate,
                current.front());
    }

    static float ghostPresence(
            ClientEchoState.GhostModel ghost,
            long now) {
        return 1.0F - condensation(ghost, now);
    }

    static float ghostPresence(
            Vec3 position,
            long now) {
        Visual current = visual(now);
        float coordinate =
                PhilosophersStoneVisualTiming
                        .normalizedCoordinate(
                                position,
                                current.center(),
                                current.halfExtents());
        return PhilosophersStoneVisualTiming
                .ghostPresence(
                        coordinate,
                        current.front());
    }

    static float rememberedOpacity(
            ClientEchoState.GhostModel ghost,
            float intensity,
            float echoFade,
            long now) {
        float echo = EchoProjectionStyle.rememberedBaseOpacity(
                ghost.change(),
                intensity) * echoFade * ghostPresence(ghost, now);
        return Math.clamp(echo, 0.0F, 0.92F);
    }

    static float presentOpacity(
            ClientEchoState.GhostModel ghost,
            float intensity,
            float echoFade,
            long now) {
        float echo = EchoProjectionStyle.presentTargetOpacity(
                ghost.change(),
                intensity) * echoFade * ghostPresence(ghost, now);
        return Math.clamp(echo, 0.0F, 0.92F);
    }

    public static void clearImmediately() {
        releasePostEffect(Minecraft.getInstance());
        transition = null;
        materializedPast = false;
        blueprint = Blueprint.EMPTY;
        activeVolume = ActiveVolume.EMPTY;
        materializedStartNanos = 0L;
        viewFrame = ViewFrame.EMPTY;
    }

    private static Blueprint captureBlueprint() {
        List<ClientEchoState.GhostModel> remembered =
                List.copyOf(ClientEchoState.ghostModels());
        List<ClientEchoState.GhostModel> present =
                List.copyOf(ClientEchoState.presentGhostModels());
        List<ClientEchoState.ScanFace> surfaces =
                new ArrayList<>(ClientEchoState.memoryEchoFaces());
        surfaces.addAll(ClientEchoState.presentFaces());
        return new Blueprint(
                remembered,
                present,
                List.copyOf(surfaces),
                !ClientEchoState.ghostEntities()
                        .isEmpty());
    }

    private static void releasePostEffect(Minecraft minecraft) {
        if (postEffectOwned
                && POST_EFFECT.equals(
                        minecraft.gameRenderer.currentPostEffect())) {
            minecraft.gameRenderer.clearPostEffect();
        }
        postEffectOwned = false;
    }

    private static void reportFailure(
            String operation,
            RuntimeException exception) {
        if (!failureReported) {
            failureReported = true;
            LOGGER.error(
                    "Philosopher's Stone post effect failed during {}",
                    operation,
                    exception);
        }
    }

    private static float transitionProgress(
            Transition active,
            long now) {
        if (!active.serverDriven()) {
            long duration = Math.max(
                    1L,
                    active.endNanos() - active.startNanos());
            return PhilosophersStoneVisualTiming.progress(
                    now - active.startNanos(),
                    duration);
        }
        float blend = PhilosophersStoneVisualTiming.progress(
                now - active.progressSampleNanos(),
                SERVER_PROGRESS_BLEND_NANOS);
        return active.progressFrom()
                + (active.progressTo() - active.progressFrom()) * blend;
    }

    private static boolean transitionFinished(
            Transition active,
            long now) {
        if (!active.serverDriven()) {
            return now >= active.endNanos();
        }
        return active.progressTo() >= 1.0F
                && transitionProgress(active, now) >= 1.0F;
    }

    public record Visual(
            float strength,
            float progress,
            float front,
            boolean restoring,
            boolean stablePast,
            float elapsedSeconds,
            Vec3 center,
            Vec3 halfExtents,
            Vec3 direction) {
        static final Visual NONE = new Visual(
                0.0F,
                0.0F,
                0.0F,
                false,
                false,
                0.0F,
                Vec3.ZERO,
                new Vec3(0.5, 0.5, 0.5),
                new Vec3(0.62, 0.18, 0.76).normalize());
    }

    record ViewFrame(
            Matrix4f inverseProjection,
            boolean depthZeroToOne,
            Vector3f center,
            Vector3f axisX,
            Vector3f axisY,
            Vector3f axisZ,
            Vector3f sweep,
            Vector3f halfExtents,
            float halfSpan,
            float screenX,
            float screenY) {
        static final ViewFrame EMPTY = new ViewFrame(
                new Matrix4f(),
                false,
                new Vector3f(),
                new Vector3f(1.0F, 0.0F, 0.0F),
                new Vector3f(0.0F, 1.0F, 0.0F),
                new Vector3f(0.0F, 0.0F, 1.0F),
                new Vector3f(0.62F, 0.18F, 0.76F).normalize(),
                new Vector3f(0.5F),
                0.5F,
                0.5F,
                0.5F);
    }

    private record Transition(
            Vec3 center,
            Vec3 halfExtents,
            Vec3 direction,
            boolean restoring,
            boolean waveOnly,
            long startNanos,
            long endNanos,
            float progressFrom,
            float progressTo,
            long progressSampleNanos,
            boolean serverDriven) {
    }

    private record ActiveVolume(
            Vec3 center,
            Vec3 halfExtents,
            Vec3 direction) {
        private static final ActiveVolume EMPTY =
                new ActiveVolume(
                        Vec3.ZERO,
                        Vec3.ZERO,
                        new Vec3(
                                        0.62,
                                        0.18,
                                        0.76)
                                .normalize());

        boolean isEmpty() {
            return halfExtents.lengthSqr()
                    < 1.0E-6;
        }
    }

    private record Blueprint(
            List<ClientEchoState.GhostModel> remembered,
            List<ClientEchoState.GhostModel> present,
            List<ClientEchoState.ScanFace> surfaces,
            boolean entities) {
        static final Blueprint EMPTY =
                new Blueprint(
                        List.of(),
                        List.of(),
                        List.of(),
                        false);

        boolean isEmpty() {
            return remembered.isEmpty()
                    && present.isEmpty()
                    && surfaces.isEmpty()
                    && !entities;
        }
    }

    private ClientPhilosophersStoneVision() {
    }
}
