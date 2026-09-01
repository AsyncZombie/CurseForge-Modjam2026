package dev.alvar.echoespast.server;

import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.network.LowFrequencyPulseCancelPayload;
import dev.alvar.echoespast.network.LowFrequencyPulseResultPayload;
import dev.alvar.echoespast.network.LowFrequencyPulseStartPayload;
import dev.alvar.echoespast.resonance.EchoSiteType;
import dev.alvar.echoespast.resonance.ResonanceColor;
import dev.alvar.echoespast.resonance.ResonanceKnowledge;
import dev.alvar.echoespast.resonance.ResonatorLoadout;
import dev.alvar.echoespast.resonance.ResonatorModule;
import dev.alvar.echoespast.world.EchoPedestalIndex;
import dev.alvar.echoespast.world.CryptAccessGate;
import dev.alvar.echoespast.world.EchoSiteLandFooting;
import dev.alvar.echoespast.world.EchoSiteSpawnRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class LowFrequencySonarManager {
    private static final AtomicLong NEXT_PULSE_ID = new AtomicLong();
    private static final Map<UUID, PendingPulse> PENDING = new HashMap<>();
    private static final Map<UUID, Long> ACTIVE_PULSES = new HashMap<>();
    private static final Map<RelayKey, Integer> RELAY_COOLDOWNS = new HashMap<>();
    private static final int CANDIDATE_BUDGET_PER_PULSE_TICK = 4;
    private static final int RELAY_RANGE = 256;
    private static final int RELAY_PLAYER_RANGE = 48;
    private static final int RELAY_COOLDOWN_TICKS = 600;

    public static long start(
            ServerPlayer player,
            Vec3 origin,
            int range,
            double speed,
            int cooldownTicks) {
        return start(
                player,
                origin,
                player.getLookAngle(),
                ResonatorLoadout.EMPTY,
                range,
                speed,
                cooldownTicks,
                null);
    }

    public static long start(
            ServerPlayer player,
            Vec3 origin,
            Vec3 direction,
            ResonatorLoadout loadout,
            double baseSpeed) {
        double speed = loadout.effectiveSpeed(baseSpeed);
        return start(
                player,
                origin,
                direction,
                loadout,
                loadout.effectiveRange(),
                speed,
                loadout.cooldownTicks(),
                null);
    }

    private static long start(
            ServerPlayer player,
            Vec3 origin,
            Vec3 direction,
            ResonatorLoadout loadout,
            int range,
            double speed,
            int cooldownTicks,
            BlockPos excludedPedestal) {
        long pulseId = NEXT_PULSE_ID.incrementAndGet();
        ServerLevel level = player.level();
        Vec3 normalizedDirection = direction.lengthSqr() < 1.0E-6
                ? new Vec3(0.0, 0.0, 1.0)
                : direction.normalize();
        List<CandidateSignal> candidates = classifyCandidates(
                player,
                EchoPedestalIndex.get(level).candidateSeeds(
                        level,
                        origin,
                        range,
                        excludedPedestal),
                origin,
                normalizedDirection,
                loadout);
        ACTIVE_PULSES.put(player.getUUID(), pulseId);
        PENDING.put(player.getUUID(), new PendingPulse(
                pulseId,
                player.getUUID(),
                level.dimension(),
                origin,
                range,
                speed,
                level.getServer().getTickCount(),
                candidates,
                0,
                new ArrayList<>(),
                0,
                false));
        PacketDistributor.sendToPlayer(player, new LowFrequencyPulseStartPayload(
                pulseId,
                origin,
                range,
                speed,
                cooldownTicks,
                normalizedDirection,
                loadout.effectiveDirectionalMode() ? loadout.coneDegrees() : 360.0F));
        return pulseId;
    }

    private static List<CandidateSignal> classifyCandidates(
            ServerPlayer player,
            List<EchoPedestalIndex.CandidateSeed> seeds,
            Vec3 origin,
            Vec3 direction,
            ResonatorLoadout loadout) {
        ResonanceKnowledge knowledge =
                player.getData(EchoesShowThePast.RESONANCE_KNOWLEDGE.get());
        boolean decoder = loadout.has(ResonatorModule.HARMONIC_DECODER);
        boolean directional = loadout.effectiveDirectionalMode();
        float coneDegrees = directional ? loadout.coneDegrees() : 360.0F;
        List<CandidateSignal> result = new ArrayList<>();
        Map<Identifier, CandidateSignal> harmonicSignals = new HashMap<>();
        for (EchoPedestalIndex.CandidateSeed seed : seeds) {
            EchoSiteType site = seed.site().map(EchoSiteType::byId).orElse(null);
            if (site != null
                    && site.requiresHarmonicKey()
                    && !loadout.has(ResonatorModule.HARMONIC_KEY)) {
                continue;
            }
            if (directional) {
                Vec3 target = seed.exactPosition()
                        .map(net.minecraft.core.BlockPos::getCenter)
                        .orElseGet(() -> new Vec3(seed.x() + 0.5, origin.y, seed.z() + 0.5));
                if (!LowFrequencySonarMath.withinCone(origin, direction, target, coneDegrees)) {
                    continue;
                }
            }

            Optional<Identifier> knownSite = Optional.empty();
            ResonanceColor color = ResonanceColor.CYAN;
            if (site != null && decoder && knowledge.discovered().contains(site.id())) {
                if (knowledge.ignored().contains(site.id())) {
                    continue;
                }
                knownSite = Optional.of(site.id());
                color = knowledge.colorFor(site);
            }
            CandidateSignal signal = new CandidateSignal(seed, knownSite, color.rgb());
            if (site != null && site.requiresHarmonicKey()) {
                CandidateSignal previous = harmonicSignals.get(site.id());
                if (previous == null || seed.horizontalDistance() < previous.seed().horizontalDistance()) {
                    harmonicSignals.put(site.id(), signal);
                }
                continue;
            }
            result.add(signal);
        }
        result.addAll(harmonicSignals.values());
        return List.copyOf(result);
    }

    public static boolean cancel(ServerPlayer player) {
        Long pulseId = ACTIVE_PULSES.get(player.getUUID());
        return pulseId != null && cancel(player, pulseId);
    }

    public static boolean isActive(ServerPlayer player) {
        return PENDING.containsKey(player.getUUID());
    }

    public static boolean cancel(ServerPlayer player, long requestedPulseId) {
        Long pulseId = ACTIVE_PULSES.get(player.getUUID());
        if (pulseId == null || pulseId != requestedPulseId) {
            return false;
        }
        ACTIVE_PULSES.remove(player.getUUID());
        PENDING.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new LowFrequencyPulseCancelPayload(pulseId));
        return true;
    }

    /**
     * A powered pedestal remains a deliberately short-range, non-recursive
     * relay. Replies are visual results and never feed this entry point.
     */
    public static boolean triggerRelay(ServerLevel level, BlockPos position) {
        RelayKey key = new RelayKey(level.dimension(), position.asLong());
        int now = level.getServer().getTickCount();
        int readyAt = RELAY_COOLDOWNS.getOrDefault(key, Integer.MIN_VALUE);
        if (now < readyAt) {
            return false;
        }
        List<ServerPlayer> listeners = level.getPlayers(player ->
                player.distanceToSqr(position.getCenter())
                        <= RELAY_PLAYER_RANGE * RELAY_PLAYER_RANGE);
        if (listeners.isEmpty()) {
            return false;
        }

        RELAY_COOLDOWNS.put(key, now + RELAY_COOLDOWN_TICKS);
        int range = Math.min(EchoesConfig.LOW_FREQUENCY_RANGE.getAsInt(), RELAY_RANGE);
        double speed = EchoesConfig.LOW_FREQUENCY_SPEED.getAsDouble();
        int listeningTicks = LowFrequencySonarMath.listeningTicks(range, speed);
        for (ServerPlayer listener : listeners) {
            start(
                    listener,
                    position.getCenter().add(0.0, 0.75, 0.0),
                    listener.getLookAngle(),
                    ResonatorLoadout.EMPTY,
                    range,
                    speed,
                    listeningTicks,
                    position);
        }
        level.playSound(
                null,
                position,
                EchoesShowThePast.LOW_FREQUENCY_IMPULSE.get(),
                SoundSource.BLOCKS,
                0.62F,
                0.58F);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        if ((now & 1023) == 0) {
            RELAY_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
        Iterator<Map.Entry<UUID, PendingPulse>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingPulse> entry = iterator.next();
            PendingPulse pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId);
            ServerLevel level = server.getLevel(pending.dimension);
            if (player == null || level == null || !player.level().dimension().equals(pending.dimension)) {
                iterator.remove();
                ACTIVE_PULSES.remove(pending.playerId, pending.pulseId);
                continue;
            }

            int elapsedTicks = now - pending.startTick;
            EchoPedestalIndex index = EchoPedestalIndex.get(level);
            if (!pending.resolutionFinished) {
                int budget = CANDIDATE_BUDGET_PER_PULSE_TICK;
                while (budget-- > 0 && pending.nextSeed < pending.seeds.size()) {
                    CandidateSignal signal = pending.seeds.get(pending.nextSeed++);
                    ScheduledResponse response = resolve(level, index, pending, signal);
                    if (response != null) {
                        pending.ready.add(response);
                    }
                }
                pending.ready.sort(Comparator.comparingDouble(ScheduledResponse::distance));

                Iterator<ScheduledResponse> responses = pending.ready.iterator();
                while (responses.hasNext()) {
                    ScheduledResponse response = responses.next();
                    int impactTick = LowFrequencySonarMath.travelTicks(
                            response.distance,
                            pending.range,
                            pending.speed);
                    if (impactTick > elapsedTicks) {
                        break;
                    }
                    responses.remove();
                    pending.responseCount++;
                    PacketDistributor.sendToPlayer(
                            player,
                            LowFrequencyPulseResultPayload.found(
                                    pending.pulseId,
                                    response.position,
                                    response.rgb,
                                    response.knownSite));
                    sendPhysicalPing(level, player, response.position);
                    EchoSiteType responseSite = response.site
                            .map(EchoSiteType::byId)
                            .orElse(null);
                    if (responseSite != null
                            && CryptAccessGate.unlock(
                                    level,
                                    responseSite,
                                    response.position.subtract(responseSite.harmonicSource()))) {
                        player.sendOverlayMessage(Component.translatable(
                                "message.echoes_show_the_past.crypt_unsealed"));
                    }
                }
            }

            int maximumTravelTicks = LowFrequencySonarMath.travelTicks(
                    pending.range,
                    pending.range,
                    pending.speed);
            if (!pending.resolutionFinished
                    && elapsedTicks >= maximumTravelTicks
                    && pending.nextSeed >= pending.seeds.size()
                    && pending.ready.isEmpty()) {
                if (LowFrequencySonarMath.shouldSendNoResponse(pending.responseCount)) {
                    PacketDistributor.sendToPlayer(
                            player,
                            LowFrequencyPulseResultPayload.none(pending.pulseId));
                }
                pending.resolutionFinished = true;
            }
            int listeningTicks = (int) Math.ceil(
                    LowFrequencySonarMath.listeningDurationSeconds(
                            pending.range,
                            pending.speed) * 20.0);
            if (pending.resolutionFinished && elapsedTicks >= listeningTicks) {
                iterator.remove();
                ACTIVE_PULSES.remove(pending.playerId, pending.pulseId);
            }
        }
    }

    private static ScheduledResponse resolve(
            ServerLevel level,
            EchoPedestalIndex index,
            PendingPulse pending,
            CandidateSignal signal) {
        EchoPedestalIndex.CandidateSeed seed = signal.seed;
        BlockPos position;
        boolean accessible;
        if (seed.exactPosition().isPresent()) {
            position = seed.exactPosition().orElseThrow();
            if (level.hasChunkAt(position)) {
                EchoPedestalIndex.refresh(level, position);
            }
            accessible = index.contains(position)
                    && !index.isBlockedFrom(position, pending.origin);
        } else {
            EchoSiteType site = seed.site().map(EchoSiteType::byId).orElse(null);
            if (site == null) {
                return null;
            }
            if (!EchoSiteSpawnRules.wouldGenerate(level, site, seed.x(), seed.z())) {
                return null;
            }
            var generator = level.getChunkSource().getGenerator();
            var randomState = level.getChunkSource().randomState();
            int y = site.requiresElevatedTerrain()
                    ? EchoSiteLandFooting.evaluate(
                                    generator,
                                    level,
                                    randomState,
                                    site,
                                    seed.x(),
                                    seed.z())
                            .anchorY()
                    : site.anchorY(
                            generator,
                            seed.x(),
                            seed.z(),
                            level,
                            randomState);
            position = new BlockPos(seed.x(), y, seed.z()).offset(site.harmonicSource());
            if (level.hasChunkAt(position)) {
                EchoPedestalIndex.refresh(level, position);
                accessible = index.contains(position)
                        && !index.isBlockedFrom(position, pending.origin);
            } else {
                accessible = true;
            }
        }
        if (!accessible) {
            return null;
        }
        double distance = position.getCenter().distanceTo(pending.origin);
        if (distance > pending.range) {
            return null;
        }
        return new ScheduledResponse(
                position.immutable(),
                distance,
                signal.seed.site(),
                signal.knownSite,
                signal.rgb);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        ACTIVE_PULSES.remove(event.getEntity().getUUID());
    }

    private static void sendPhysicalPing(ServerLevel level, ServerPlayer owner, BlockPos position) {
        for (ServerPlayer listener : level.players()) {
            if (listener == owner
                    || listener.distanceToSqr(position.getCenter()) > 32.0 * 32.0) {
                continue;
            }
            listener.connection.send(new ClientboundSoundPacket(
                    EchoesShowThePast.PEDESTAL_PING,
                    SoundSource.BLOCKS,
                    position.getX() + 0.5,
                    position.getY() + 0.5,
                    position.getZ() + 0.5,
                    0.85F,
                    1.0F,
                    level.getRandom().nextLong()));
        }
    }

    private record CandidateSignal(
            EchoPedestalIndex.CandidateSeed seed,
            Optional<Identifier> knownSite,
            int rgb) {
    }

    private record ScheduledResponse(
            BlockPos position,
            double distance,
            Optional<Identifier> site,
            Optional<Identifier> knownSite,
            int rgb) {
    }

    private static final class PendingPulse {
        private final long pulseId;
        private final UUID playerId;
        private final ResourceKey<Level> dimension;
        private final Vec3 origin;
        private final int range;
        private final double speed;
        private final int startTick;
        private final List<CandidateSignal> seeds;
        private int nextSeed;
        private final List<ScheduledResponse> ready;
        private int responseCount;
        private boolean resolutionFinished;

        private PendingPulse(
                long pulseId,
                UUID playerId,
                ResourceKey<Level> dimension,
                Vec3 origin,
                int range,
                double speed,
                int startTick,
                List<CandidateSignal> seeds,
                int nextSeed,
                List<ScheduledResponse> ready,
                int responseCount,
                boolean resolutionFinished) {
            this.pulseId = pulseId;
            this.playerId = playerId;
            this.dimension = dimension;
            this.origin = origin;
            this.range = range;
            this.speed = speed;
            this.startTick = startTick;
            this.seeds = seeds;
            this.nextSeed = nextSeed;
            this.ready = ready;
            this.responseCount = responseCount;
            this.resolutionFinished = resolutionFinished;
        }
    }

    private record RelayKey(ResourceKey<Level> dimension, long position) {
    }

    private LowFrequencySonarManager() {
    }
}
