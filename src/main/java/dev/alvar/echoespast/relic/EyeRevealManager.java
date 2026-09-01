package dev.alvar.echoespast.relic;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.network.EyeHazardSignal;
import dev.alvar.echoespast.network.EyeHazardSignalsPayload;
import dev.alvar.echoespast.network.EyeOfHorusVisualPayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Finds only mechanical hazards. Decorative chiseled blocks are architecture,
 * not traps. Detection stays server-authoritative and is spread across the
 * entire vision window.
 */
public final class EyeRevealManager {
    public static final TagKey<net.minecraft.world.level.block.Block> TRAPS = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "eye_revealed_traps"));
    public static final TagKey<net.minecraft.world.level.block.Block> GLYPHS = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "eye_revealed_glyphs"));
    private static final int RADIUS = 32;
    private static final int[] SCAN_OFFSETS = createScanOffsets();
    private static final int BUDGET_PER_TICK = 2_048;
    private static final int MAX_BATCH = 128;
    private static final int MAX_TOTAL_SIGNALS = 256;
    private static final int NIGHT_VISION_BUFFER_TICKS = 15 * 20;
    private static final int NIGHT_VISION_REFRESH_INTERVAL = 20;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static void start(ServerPlayer player, int durationTicks) {
        int now = player.level().getServer().getTickCount();
        Session replaced = SESSIONS.remove(player.getUUID());
        if (replaced != null) {
            finishNightVision(player, replaced, now);
        }

        MobEffectInstance existing = player.getEffect(MobEffects.NIGHT_VISION);
        MobEffectInstance previous = existing == null ? null : new MobEffectInstance(existing);
        boolean ownsNightVision = existing == null
                || (!existing.isInfiniteDuration()
                        && existing.getDuration() < durationTicks + 200);
        Session session = new Session(
                player.blockPosition(),
                player.level().dimension(),
                now,
                now + durationTicks,
                previous,
                ownsNightVision);
        SESSIONS.put(player.getUUID(), session);
        if (ownsNightVision && existing != null) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
        refreshNightVision(player, session, now);
    }

    public static boolean isActive(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null
                && player.level().dimension().equals(session.dimension)
                && player.level().getServer().getTickCount() < session.untilTick;
    }

    /**
     * Applies only the GLOWING effect owned by this vision session. Existing
     * glowing effects are intentionally left untouched so cancellation never
     * strips a status supplied by another mechanic.
     */
    public static void applyVisionGlow(
            ServerPlayer player,
            net.minecraft.world.entity.LivingEntity target,
            int durationTicks) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || target.hasEffect(MobEffects.GLOWING)) {
            return;
        }
        target.addEffect(new MobEffectInstance(
                MobEffects.GLOWING,
                durationTicks,
                0,
                false,
                false));
        session.glowingTargets.add(target.getUUID());
    }

    public static boolean cancel(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return false;
        }
        int now = player.level().getServer().getTickCount();
        finishSession(player, session, now);
        long gameNow = player.level().getGameTime();
        // Keep a short synchronized tail so other clients fade the head sigil
        // instead of seeing it blink out when its owner dismisses the vision.
        player.setData(EchoesShowThePast.HORUS_AURA_UNTIL.get(), gameNow + 14L);
        EyeOfHorusVisualPayload payload = new EyeOfHorusVisualPayload(0);
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int now = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null
                    || now >= session.untilTick
                    || !player.level().dimension().equals(session.dimension)) {
                if (player != null) {
                    finishSession(player, session, now);
                }
                iterator.remove();
                continue;
            }
            refreshNightVision(player, session, now);
            ServerLevel level = player.level();
            int tested = 0;
            List<EyeHazardSignal> batch = new ArrayList<>();
            while (tested++ < BUDGET_PER_TICK
                    && session.cursor < SCAN_OFFSETS.length
                    && batch.size() < MAX_BATCH) {
                int packed = SCAN_OFFSETS[session.cursor++];
                int dx = (packed & 0x7F) - RADIUS;
                int dy = ((packed >> 7) & 0x7F) - RADIUS;
                int dz = ((packed >> 14) & 0x7F) - RADIUS;
                BlockPos position = session.origin.offset(dx, dy, dz);
                if (!level.hasChunkAt(position)) {
                    continue;
                }
                var state = level.getBlockState(position);
                var descriptor = EyeHazardClassifier.classify(state);
                if (descriptor.isEmpty()) {
                    continue;
                }
                EyeHazardClassifier.Descriptor hazard = descriptor.get();
                if (!shouldReveal(level, position, hazard.type())
                        || !session.reserve(hazard.type())) {
                    continue;
                }
                batch.add(new EyeHazardSignal(
                        position.immutable(),
                        hazard.type(),
                        hazard.direction()));
            }
            if (!batch.isEmpty()) {
                EyeHazardSignalsPayload payload = new EyeHazardSignalsPayload(batch);
                if (player.connection.hasChannel(payload)) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Session session = SESSIONS.remove(event.getEntity().getUUID());
        if (session != null && event.getEntity() instanceof ServerPlayer player) {
            finishSession(
                    player,
                    session,
                    player.level().getServer().getTickCount());
        }
    }

    private static boolean shouldReveal(
            ServerLevel level,
            BlockPos position,
            EyeHazardType type) {
        if (type != EyeHazardType.LAVA) {
            return true;
        }
        for (var direction : net.minecraft.core.Direction.values()) {
            BlockPos neighbour = position.relative(direction);
            if (level.hasChunkAt(neighbour)
                    && !level.getBlockState(neighbour).getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }

    private static void refreshNightVision(
            ServerPlayer player,
            Session session,
            int now) {
        if (!session.ownsNightVision
                || now < session.nextNightVisionRefreshTick) {
            return;
        }
        player.addEffect(new MobEffectInstance(
                MobEffects.NIGHT_VISION,
                NIGHT_VISION_BUFFER_TICKS,
                0,
                false,
                false,
                false));
        session.nextNightVisionRefreshTick = now + NIGHT_VISION_REFRESH_INTERVAL;
    }

    private static void finishNightVision(
            ServerPlayer player,
            Session session,
            int now) {
        if (!session.ownsNightVision) {
            return;
        }
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (current != null
                && current.getAmplifier() == 0
                && !current.isVisible()
                && !current.showIcon()
                && current.getDuration() <= NIGHT_VISION_BUFFER_TICKS) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
        MobEffectInstance previous = session.previousNightVision;
        if (previous == null) {
            return;
        }
        int elapsed = Math.max(0, now - session.startTick);
        if (previous.isInfiniteDuration()) {
            player.addEffect(new MobEffectInstance(previous));
            return;
        }
        int remaining = previous.getDuration() - elapsed;
        if (remaining > 0) {
            player.addEffect(new MobEffectInstance(
                    previous.getEffect(),
                    remaining,
                    previous.getAmplifier(),
                    previous.isAmbient(),
                    previous.isVisible(),
                    previous.showIcon()));
        }
    }

    private static void finishSession(
            ServerPlayer player,
            Session session,
            int now) {
        finishNightVision(player, session, now);
        for (UUID targetId : session.glowingTargets) {
            net.minecraft.world.entity.Entity entity = player.level().getEntity(targetId);
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity target)) {
                continue;
            }
            MobEffectInstance glowing = target.getEffect(MobEffects.GLOWING);
            int expectedRemaining = Math.max(0, session.untilTick - now);
            if (glowing != null && glowing.getDuration() <= expectedRemaining + 2) {
                target.removeEffect(MobEffects.GLOWING);
            }
        }
    }

    private static int cap(EyeHazardType type) {
        return switch (type) {
            case LAVA -> 72;
            case CONTACT -> 96;
            case GLYPH -> 64;
            default -> 128;
        };
    }

    /**
     * Chebyshev shells keep the first scan local. A dispenser beside the
     * player is found during the first tick instead of halfway through the
     * eight-second effect.
     */
    private static int[] createScanOffsets() {
        int count = 0;
        int radiusSqr = RADIUS * RADIUS;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    if (x * x + y * y + z * z <= radiusSqr) {
                        count++;
                    }
                }
            }
        }
        int[] offsets = new int[count];
        int cursor = 0;
        for (int shell = 0; shell <= RADIUS; shell++) {
            for (int x = -shell; x <= shell; x++) {
                for (int y = -shell; y <= shell; y++) {
                    for (int z = -shell; z <= shell; z++) {
                        if (Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z)) != shell
                                || x * x + y * y + z * z > radiusSqr) {
                            continue;
                        }
                        offsets[cursor++] = (x + RADIUS)
                                | ((y + RADIUS) << 7)
                                | ((z + RADIUS) << 14);
                    }
                }
            }
        }
        return offsets;
    }

    private static final class Session {
        private final BlockPos origin;
        private final net.minecraft.resources.ResourceKey<Level> dimension;
        private final int startTick;
        private final int untilTick;
        private final MobEffectInstance previousNightVision;
        private final boolean ownsNightVision;
        private final Set<UUID> glowingTargets = new HashSet<>();
        private final EnumMap<EyeHazardType, Integer> counts =
                new EnumMap<>(EyeHazardType.class);
        private int cursor;
        private int totalSignals;
        private int nextNightVisionRefreshTick;

        private Session(
                BlockPos origin,
                net.minecraft.resources.ResourceKey<Level> dimension,
                int startTick,
                int untilTick,
                MobEffectInstance previousNightVision,
                boolean ownsNightVision) {
            this.origin = origin;
            this.dimension = dimension;
            this.startTick = startTick;
            this.untilTick = untilTick;
            this.previousNightVision = previousNightVision;
            this.ownsNightVision = ownsNightVision;
            this.nextNightVisionRefreshTick = startTick;
        }

        private boolean reserve(EyeHazardType type) {
            int count = counts.getOrDefault(type, 0);
            if (totalSignals >= MAX_TOTAL_SIGNALS || count >= cap(type)) {
                return false;
            }
            counts.put(type, count + 1);
            totalSignals++;
            return true;
        }
    }

    private EyeRevealManager() {
    }
}
