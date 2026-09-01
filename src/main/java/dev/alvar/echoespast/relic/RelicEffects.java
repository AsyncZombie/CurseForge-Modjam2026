package dev.alvar.echoespast.relic;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.entity.MedusaEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class RelicEffects {
    private static final Map<UUID, Long> PETRIFIED_UNTIL = new HashMap<>();
    private static final Map<UUID, GrailAura> GRAIL_AURAS = new HashMap<>();

    public static void petrifyTimed(LivingEntity entity, long untilTick) {
        PETRIFIED_UNTIL.put(entity.getUUID(), untilTick);
        entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    public static boolean isTimedPetrified(LivingEntity entity) {
        Long until = PETRIFIED_UNTIL.get(entity.getUUID());
        return until != null
                && !entity.level().isClientSide()
                && entity.level().getServer().getTickCount() < until;
    }

    public static void petrifyPermanently(LivingEntity entity) {
        applyPermanentPetrify(entity, PetrifiedPose.capture(entity));
    }

    public static void applyPermanentPetrify(
            LivingEntity entity,
            PetrifiedPose pose) {
        PETRIFIED_UNTIL.remove(entity.getUUID());
        entity.removeAllEffects();
        entity.setData(EchoesShowThePast.PETRIFIED_POSE.get(), pose);
        markStatuePersistent(entity);
        pose.freezeCommon(entity);
        if (entity instanceof MedusaEntity medusa) {
            medusa.dismissBossBar();
        }
    }

    public static boolean isPermanentlyPetrified(LivingEntity entity) {
        PetrifiedPose pose = entity.getExistingDataOrNull(EchoesShowThePast.PETRIFIED_POSE.get());
        return pose != null && pose.permanent();
    }

    /**
     * Clears permanent stone so a shattered player-statue can take lethal
     * damage and respawn instead of being discarded mid-connection.
     */
    public static void clearPermanentPetrify(LivingEntity entity) {
        PETRIFIED_UNTIL.remove(entity.getUUID());
        entity.setData(EchoesShowThePast.PETRIFIED_POSE.get(), PetrifiedPose.EMPTY);
        entity.setInvulnerable(false);
    }

    /**
     * Statues remain authored world content. Vanilla still runs
     * {@link Mob#checkDespawn()} even when entity ticks are cancelled, so
     * Peaceful would otherwise discard every hostile statue.
     */
    public static void markStatuePersistent(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
    }

    @SubscribeEvent
    public static void onMobDespawn(MobDespawnEvent event) {
        if (isPermanentlyPetrified(event.getEntity())) {
            event.setResult(MobDespawnEvent.Result.DENY);
        }
    }

    public static void startGrailAura(
            ServerPlayer player,
            long startTick,
            long untilTick) {
        long duration = Math.max(1L, untilTick - startTick);
        long serverStart = player.level().getServer().getTickCount();
        GRAIL_AURAS.put(
                player.getUUID(),
                new GrailAura(serverStart, serverStart + duration));
        player.setData(EchoesShowThePast.GRAIL_AURA_START.get(), startTick);
        player.setData(EchoesShowThePast.GRAIL_AURA_UNTIL.get(), untilTick);
    }

    public static boolean isGrailAuraActive(ServerPlayer player) {
        GrailAura aura = GRAIL_AURAS.get(player.getUUID());
        return aura != null
                && player.level().getServer().getTickCount() < aura.untilTick();
    }

    /**
     * Ends gameplay immediately but leaves a short attachment tail for the
     * client-side visual to close naturally rather than blinking out.
     */
    public static boolean cancelGrailAura(ServerPlayer player) {
        GrailAura aura = GRAIL_AURAS.remove(player.getUUID());
        if (aura == null) {
            return false;
        }
        long now = player.level().getGameTime();
        player.setData(EchoesShowThePast.GRAIL_AURA_UNTIL.get(), now + 10L);
        return true;
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        PetrifiedPose permanent = living.getExistingDataOrNull(EchoesShowThePast.PETRIFIED_POSE.get());
        if (permanent != null && permanent.permanent()) {
            // Permanent statues do not tick, so finite potion durations would
            // otherwise become permanent too (most visibly Horus' GLOWING).
            if (!living.level().isClientSide() && !living.getActiveEffects().isEmpty()) {
                living.removeAllEffects();
            }
            // Heal statues placed before persistence was stamped so Peaceful
            // and distance despawn keep treating them as authored content.
            markStatuePersistent(living);
            permanent.freezeCommon(living);
            event.setCanceled(true);
            return;
        }
        if (living.level().isClientSide()) {
            return;
        }
        Long until = PETRIFIED_UNTIL.get(living.getUUID());
        if (until == null) {
            return;
        }
        long now = living.level().getServer().getTickCount();
        if (now >= until) {
            PETRIFIED_UNTIL.remove(living.getUUID());
            return;
        }
        living.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (isPermanentlyPetrified(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        Long until = PETRIFIED_UNTIL.get(event.getEntity().getUUID());
        if (until == null || event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity().level().getServer().getTickCount() < until) {
            event.setAmount(event.getAmount() * 0.30F);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof LivingEntity living)
                || !isPermanentlyPetrified(living)) {
            return;
        }
        // A statue never receives ordinary combat damage. A pickaxe extracts
        // exactly one serialized statue item instead.
        event.setCanceled(true);
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            PetrifiedMobManager.tickMining(level);
        }
        Iterator<Map.Entry<UUID, GrailAura>> iterator =
                GRAIL_AURAS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, GrailAura> entry = iterator.next();
            GrailAura aura = entry.getValue();
            ServerPlayer owner =
                    server.getPlayerList().getPlayer(entry.getKey());
            if (now >= aura.untilTick() || owner == null || !owner.isAlive()) {
                if (owner != null) {
                    owner.setData(EchoesShowThePast.GRAIL_AURA_START.get(), 0L);
                    owner.setData(EchoesShowThePast.GRAIL_AURA_UNTIL.get(), 0L);
                }
                iterator.remove();
                continue;
            }
            if (!(owner.level() instanceof ServerLevel level)) {
                continue;
            }
            float opening = Math.clamp(
                    (now - aura.startTick()) / 14.0F,
                    0.0F,
                    1.0F);
            double radius = 2.25 + opening * 4.75;
            for (LivingEntity target : level.getEntitiesOfClass(
                    LivingEntity.class,
                    new AABB(owner.blockPosition()).inflate(
                            radius,
                            Math.min(4.0, radius),
                            radius),
                    LivingEntity::isAlive)) {
                if (target.isInvertedHealAndHarm()
                        && !isPermanentlyPetrified(target)) {
                    repelFromGrail(owner, target, radius);
                    if (now % 10L == 0L) {
                        target.hurtServer(
                                level,
                                level.damageSources().magic(),
                                1.0F);
                    }
                } else if (now % 10L == 0L
                        && (target == owner
                                || owner.isAlliedTo(target))) {
                    target.heal(0.5F);
                }
            }
        }
    }

    private static void repelFromGrail(
            ServerPlayer owner,
            LivingEntity undead,
            double radius) {
        Vec3 delta = undead.position().subtract(owner.position());
        double horizontalDistance = Math.sqrt(
                delta.x * delta.x + delta.z * delta.z);
        if (horizontalDistance >= radius) {
            return;
        }

        double directionX;
        double directionZ;
        if (horizontalDistance > 1.0E-4) {
            directionX = delta.x / horizontalDistance;
            directionZ = delta.z / horizontalDistance;
        } else {
            double angle = (undead.getUUID().hashCode() & 1023)
                    * Math.PI * 2.0
                    / 1024.0;
            directionX = Math.cos(angle);
            directionZ = Math.sin(angle);
        }

        double depth = 1.0 - horizontalDistance / radius;
        double barrierStep = 0.035 + depth * 0.055;
        undead.move(
                MoverType.SELF,
                new Vec3(
                        directionX * barrierStep,
                        0.0,
                        directionZ * barrierStep));

        double force = 0.055 + depth * 0.085;
        Vec3 movement = undead.getDeltaMovement();
        undead.push(
                directionX * force - movement.x * 0.20,
                undead.onGround() ? 0.025 : 0.0,
                directionZ * force - movement.z * 0.20);
    }

    private record GrailAura(long startTick, long untilTick) {
    }

    private RelicEffects() {
    }
}
