package dev.alvar.echoespast.client;

import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.network.LowFrequencyPulseResultPayload;
import dev.alvar.echoespast.network.LowFrequencyPulseStartPayload;
import dev.alvar.echoespast.network.LowFrequencyPulseCancelPayload;
import dev.alvar.echoespast.server.LowFrequencySonarMath;
import dev.alvar.echoespast.visual.EchoVisualTiming;
import dev.alvar.echoespast.resonance.EchoSiteType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.IRenderableSection;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A personal, one-shot locator state. It is intentionally independent from
 * ClientEchoState so a low-frequency pulse never captures or projects memory.
 */
public final class ClientLowFrequencySonarState {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SECTION_BUILD_BUDGET_PER_FRAME = 12;

    private static long pulseId = -1L;
    private static Vec3 origin = Vec3.ZERO;
    private static int range;
    private static double speed;
    private static Vec3 direction = new Vec3(0.0, 0.0, 1.0);
    private static float coneDegrees = 360.0F;
    private static int cooldownTicks;
    private static long startedNanos;
    private static long cancelStartedNanos = Long.MAX_VALUE;
    private static boolean cancelRequestPending;
    private static @Nullable ResourceKey<Level> dimension;
    private static double visualRange;
    private static final Map<Long, PedestalResponse> responses = new LinkedHashMap<>();
    private static final Map<Long, CachedSurfaceSection> surfaceSections = new HashMap<>();
    private static final Set<Long> dirtySurfaceSections = new HashSet<>();
    private static float appliedAudioFocus;

    public static void receive(LowFrequencyPulseStartPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        pulseId = payload.pulseId();
        origin = payload.origin();
        range = payload.range();
        speed = payload.speed();
        direction = payload.direction().lengthSqr() < 1.0E-6
                ? new Vec3(0.0, 0.0, 1.0)
                : payload.direction().normalize();
        coneDegrees = payload.coneDegrees();
        cooldownTicks = payload.cooldownTicks();
        startedNanos = System.nanoTime();
        cancelStartedNanos = Long.MAX_VALUE;
        cancelRequestPending = false;
        dimension = minecraft.level == null ? null : minecraft.level.dimension();
        responses.clear();
        visualRange = visibleRange(minecraft);
        surfaceSections.clear();
        dirtySurfaceSections.clear();
        LOGGER.info(
                "Low-frequency pulse received: id={}, origin={}, range={}, speed={},"
                        + " postProcessing={}",
                pulseId,
                origin,
                range,
                speed,
                EchoesConfig.POST_PROCESSING.getAsBoolean());
    }

    public static void receive(LowFrequencyPulseCancelPayload payload) {
        long now = System.nanoTime();
        if (!LowFrequencySonarMath.isCurrentResult(pulseId, payload.pulseId())
                || !isActive(now)
                || cancelStartedNanos != Long.MAX_VALUE) {
            return;
        }
        cancelRequestPending = false;
        cancelStartedNanos = now;
    }

    public static long requestCancellation(long now) {
        if (!isActive(now)
                || cancelStartedNanos != Long.MAX_VALUE
                || cancelRequestPending) {
            return -1L;
        }
        cancelRequestPending = true;
        return pulseId;
    }

    public static void receive(LowFrequencyPulseResultPayload payload) {
        if (!LowFrequencySonarMath.isCurrentResult(pulseId, payload.pulseId())
                || !isActive(System.nanoTime())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos pedestal = payload.pedestal().orElse(null);
        if (pedestal == null) {
            if (minecraft.player != null) {
                minecraft.player.sendOverlayMessage(
                        Component.translatable("message.echoes_show_the_past.no_low_frequency_response"));
            }
            return;
        }
        long key = pedestal.asLong();
        if (minecraft.level == null || responses.containsKey(key)) {
            return;
        }
        long impactNanos = System.nanoTime();
        Vec3 center = pedestal.getCenter();
        responses.put(
                key,
                new PedestalResponse(
                        pedestal.immutable(),
                        impactNanos,
                        false,
                        payload.rgb(),
                        payload.knownSite()));
        payload.knownSite()
                .map(EchoSiteType::byId)
                .filter(java.util.Objects::nonNull)
                .ifPresent(site -> minecraft.player.sendOverlayMessage(
                        Component.translatable(
                                "message.echoes_show_the_past.known_response",
                                Component.translatable(site.translationKey()))));
        minecraft.level.playLocalSound(
                center.x,
                center.y,
                center.z,
                EchoesShowThePast.PEDESTAL_PING.get(),
                SoundSource.BLOCKS,
                1.0F,
                1.0F,
                false);
    }

    public static void tick() {
        long now = System.nanoTime();
        if (!isActive(now)) {
            if (pulseId >= 0L) {
                clear();
            }
            return;
        }
        refreshAudioFocus(now);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || dimension == null || !minecraft.level.dimension().equals(dimension)) {
            clear();
            return;
        }
        visualRange = visibleRange(minecraft);
        if (responses.isEmpty() || minecraft.player == null) {
            return;
        }
        for (Map.Entry<Long, PedestalResponse> entry : responses.entrySet()) {
            PedestalResponse response = entry.getValue();
            if (response.returnHeard()) {
                continue;
            }
            double radius = returnRadius(now, response);
            double playerDistance =
                    response.position().getCenter().distanceTo(minecraft.player.getEyePosition());
            if (radius < playerDistance) {
                continue;
            }
            minecraft.level.playLocalSound(
                    minecraft.player,
                    EchoesShowThePast.LOW_FREQUENCY_RETURN.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    0.88F);
            entry.setValue(response.withReturnHeard());
        }
    }

    public static boolean isActive(long now) {
        if (pulseId < 0L || startedNanos <= 0L) {
            return false;
        }
        if (elapsedSeconds(now) >= listeningDurationSeconds()) {
            return false;
        }
        return cancelStartedNanos == Long.MAX_VALUE
                || cancellationElapsedSeconds(now)
                        < LowFrequencySonarMath.CANCELLATION_FADE_SECONDS;
    }

    public static double elapsedSeconds(long now) {
        return Math.max(0.0, (now - startedNanos) / 1_000_000_000.0);
    }

    public static double outboundRadius(long now) {
        return LowFrequencySonarMath.expandingRadius(elapsedSeconds(now), speed);
    }

    public static double outboundTravelDistance(long now) {
        return outboundRadius(now);
    }

    public static double returnRadius(long now, PedestalResponse response) {
        return LowFrequencySonarMath.expandingRadius(
                Math.max(0.0, (now - response.impactNanos()) / 1_000_000_000.0),
                speed);
    }

    public static double returnTravelDistance(long now, PedestalResponse response) {
        return returnRadius(now, response);
    }

    public static boolean isVisualActive(long now) {
        return isActive(now);
    }

    public static Vec3 direction() {
        return direction;
    }

    public static float coneDegrees() {
        return coneDegrees;
    }

    public static boolean isDirectional() {
        return coneDegrees < 359.5F;
    }

    public static double visualRange() {
        return visualRange;
    }

    /** Recompute the camera-local visibility sphere for an active pulse. */
    public static void refreshVisualRange() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || pulseId < 0L) {
            return;
        }
        visualRange = visibleRange(minecraft);
    }

    public static double speed() {
        return speed;
    }

    public static List<PedestalResponse> responses() {
        return List.copyOf(responses.values());
    }

    static float visualShadowStrength(long now) {
        if (!isActive(now)) {
            return 0.0F;
        }
        float configuredDarkening = EchoesConfig.SCREEN_DARKENING.get().floatValue();
        return Math.clamp(
                        configuredDarkening / EchoVisualTiming.MAX_SCREEN_DARKENING,
                        0.0F,
                        1.0F)
                * listeningFocus(now);
    }

    public static float ambientAudioMultiplier(Identifier sound) {
        if (isListeningSound(sound)) {
            return 1.0F;
        }
        return 1.0F - appliedAudioFocus * 0.78F;
    }

    private static boolean isListeningSound(Identifier sound) {
        if (!sound.getNamespace().equals(EchoesShowThePast.MOD_ID)) {
            return false;
        }
        String path = sound.getPath();
        return path.equals("low_frequency_impulse")
                || path.equals("low_frequency_return")
                || path.equals("pedestal_ping");
    }

    private static float listeningFocus(long now) {
        return LowFrequencySonarMath.listeningEnvelope(
                        elapsedSeconds(now),
                        listeningDurationSeconds())
                * cancellationFade(now);
    }

    /**
     * Server sends the listening window as cooldown ticks so darkness, item
     * cooldown and pulse lifetime share one number.
     */
    private static double listeningDurationSeconds() {
        if (cooldownTicks > 0) {
            return cooldownTicks / 20.0;
        }
        return LowFrequencySonarMath.listeningDurationSeconds(range, speed);
    }

    public static float visualEnvelope(long now) {
        return cancellationFade(now);
    }

    public static float pedestalHintEnvelope(long now, PedestalResponse response) {
        return LowFrequencySonarMath.pedestalHintEnvelope(
                        Math.max(0.0, (now - response.impactNanos()) / 1_000_000_000.0))
                * cancellationFade(now);
    }

    private static float cancellationFade(long now) {
        return cancelStartedNanos == Long.MAX_VALUE
                ? 1.0F
                : LowFrequencySonarMath.cancellationEnvelope(
                        cancellationElapsedSeconds(now));
    }

    private static double cancellationElapsedSeconds(long now) {
        return cancelStartedNanos == Long.MAX_VALUE
                ? 0.0
                : Math.max(0.0, (now - cancelStartedNanos) / 1_000_000_000.0);
    }

    private static void refreshAudioFocus(long now) {
        float focus = listeningFocus(now);
        if (Math.abs(focus - appliedAudioFocus) < 0.015F) {
            return;
        }
        appliedAudioFocus = focus;
        Minecraft.getInstance().getSoundManager().refreshCategoryVolume(SoundSource.MASTER);
    }

    public static void onBlockChanged(BlockPos position) {
        if (!isActive(System.nanoTime())) {
            return;
        }
        dirtySurfaceSections.add(SectionPos.asLong(position));
        for (Direction direction : Direction.values()) {
            dirtySurfaceSections.add(SectionPos.asLong(position.relative(direction)));
        }
    }

    public static Vec3 origin() {
        return origin;
    }

    public static int range() {
        return range;
    }

    public static void clear() {
        boolean restoreAudio = appliedAudioFocus > 0.001F;
        pulseId = -1L;
        origin = Vec3.ZERO;
        range = 0;
        speed = 0.0;
        direction = new Vec3(0.0, 0.0, 1.0);
        coneDegrees = 360.0F;
        cooldownTicks = 0;
        startedNanos = 0L;
        cancelStartedNanos = Long.MAX_VALUE;
        cancelRequestPending = false;
        dimension = null;
        visualRange = 0.0;
        responses.clear();
        surfaceSections.clear();
        dirtySurfaceSections.clear();
        appliedAudioFocus = 0.0F;
        if (restoreAudio) {
            Minecraft.getInstance().getSoundManager().refreshCategoryVolume(SoundSource.MASTER);
        }
    }

    static List<ClientEchoState.ScanFace> facesForWave(WaveFront wave) {
        if (surfaceSections.isEmpty()) {
            return List.of();
        }
        boolean coneMask = !wave.returning() && isDirectional();
        List<ClientEchoState.ScanFace> faces = new ArrayList<>();
        for (Map.Entry<Long, CachedSurfaceSection> entry : surfaceSections.entrySet()) {
            if (dirtySurfaceSections.contains(entry.getKey())
                    || !intersectsWave(entry.getValue().bounds(), wave, 2.0)) {
                continue;
            }
            for (ClientEchoState.ScanFace face : entry.getValue().faces()) {
                if (coneMask
                        && !LowFrequencySonarMath.withinCone(
                                wave.origin(),
                                direction,
                                face.center(),
                                coneDegrees)) {
                    continue;
                }
                faces.add(face);
            }
        }
        return List.copyOf(faces);
    }

    static void updateSurfaceCache(
            Iterable<? extends IRenderableSection> renderableSections,
            List<WaveFront> activeWaves) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || activeWaves.isEmpty()) {
            surfaceSections.clear();
            return;
        }

        Set<Long> visibleKeys = new HashSet<>();
        List<SectionCandidate> candidates = new ArrayList<>();
        // Prefer sections on the current crest so newly loaded chunks fill in
        // when the player catches a pulse that already outran render distance.
        double crestLookAhead = Math.max(64.0, speed * 0.55);
        for (WaveFront wave : activeWaves) {
            crestLookAhead = Math.max(crestLookAhead, wave.widthScale() * 1.90 + 48.0);
        }
        final double lookAhead = crestLookAhead;
        renderableSections.forEach(section -> {
            if (section.isEmpty()) {
                return;
            }
            BlockPos sectionOrigin = section.getRenderOrigin().immutable();
            long key = SectionPos.asLong(sectionOrigin);
            visibleKeys.add(key);
            if (surfaceSections.containsKey(key) && !dirtySurfaceSections.contains(key)) {
                return;
            }
            AABB sourceBounds = section.getBoundingBox();
            AABB bounds = new AABB(
                    sourceBounds.minX,
                    sourceBounds.minY,
                    sourceBounds.minZ,
                    sourceBounds.maxX,
                    sourceBounds.maxY,
                    sourceBounds.maxZ);
            double priority = activeWaves.stream()
                    .mapToDouble(wave -> distanceFromWave(bounds, wave))
                    .min()
                    .orElse(Double.POSITIVE_INFINITY);
            if (priority <= lookAhead) {
                candidates.add(new SectionCandidate(key, sectionOrigin, bounds, priority));
            }
        });

        surfaceSections.keySet().removeIf(key -> !visibleKeys.contains(key));
        dirtySurfaceSections.removeIf(key -> !visibleKeys.contains(key));
        candidates.sort(Comparator.comparingDouble(SectionCandidate::priority));
        int buildCount = Math.min(SECTION_BUILD_BUDGET_PER_FRAME, candidates.size());
        for (int index = 0; index < buildCount; index++) {
            SectionCandidate candidate = candidates.get(index);
            List<ClientEchoState.ScanFace> faces =
                    ClientEchoState.buildPresentFacesInSection(minecraft, candidate.origin());
            surfaceSections.put(
                    candidate.key(),
                    new CachedSurfaceSection(candidate.bounds(), faces));
            dirtySurfaceSections.remove(candidate.key());
        }
    }

    private static double visibleRange(Minecraft minecraft) {
        return LowFrequencySonarMath.visibleRange(
                range,
                minecraft.options.getEffectiveRenderDistance());
    }

    private static boolean intersectsWave(
            AABB bounds,
            WaveFront wave,
            double padding) {
        DistanceRange distances = distances(bounds, wave.origin());
        double support = wave.widthScale() * 1.90 + padding;
        return distances.minimum() <= wave.radius() + support
                && distances.maximum() >= wave.radius() - support;
    }

    private static double distanceFromWave(AABB bounds, WaveFront wave) {
        DistanceRange distances = distances(bounds, wave.origin());
        if (wave.radius() < distances.minimum()) {
            return distances.minimum() - wave.radius();
        }
        if (wave.radius() > distances.maximum()) {
            return wave.radius() - distances.maximum();
        }
        return 0.0;
    }

    private static DistanceRange distances(AABB bounds, Vec3 point) {
        double nearX = point.x < bounds.minX
                ? bounds.minX - point.x
                : point.x > bounds.maxX ? point.x - bounds.maxX : 0.0;
        double nearY = point.y < bounds.minY
                ? bounds.minY - point.y
                : point.y > bounds.maxY ? point.y - bounds.maxY : 0.0;
        double nearZ = point.z < bounds.minZ
                ? bounds.minZ - point.z
                : point.z > bounds.maxZ ? point.z - bounds.maxZ : 0.0;
        double farX = Math.max(Math.abs(point.x - bounds.minX), Math.abs(point.x - bounds.maxX));
        double farY = Math.max(Math.abs(point.y - bounds.minY), Math.abs(point.y - bounds.maxY));
        double farZ = Math.max(Math.abs(point.z - bounds.minZ), Math.abs(point.z - bounds.maxZ));
        return new DistanceRange(
                Math.sqrt(nearX * nearX + nearY * nearY + nearZ * nearZ),
                Math.sqrt(farX * farX + farY * farY + farZ * farZ));
    }

    record WaveFront(Vec3 origin, double radius, double widthScale, boolean returning) {
    }

    record PedestalResponse(
            BlockPos position,
            long impactNanos,
            boolean returnHeard,
            int rgb,
            Optional<Identifier> knownSite) {
        private PedestalResponse withReturnHeard() {
            return new PedestalResponse(position, impactNanos, true, rgb, knownSite);
        }
    }

    private record CachedSurfaceSection(AABB bounds, List<ClientEchoState.ScanFace> faces) {
    }

    private record SectionCandidate(long key, BlockPos origin, AABB bounds, double priority) {
    }

    private record DistanceRange(double minimum, double maximum) {
    }

    private ClientLowFrequencySonarState() {
    }
}
