package dev.alvar.echoespast.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;
import dev.alvar.echoespast.entity.ai.MedusaCombatGoal;
import dev.alvar.echoespast.entity.combat.MedusaBossMath;
import dev.alvar.echoespast.network.MedusaGazeVisualPayload;
import dev.alvar.echoespast.network.MedusaPetrifyPayload;
import dev.alvar.echoespast.relic.MedusaHeadItem;
import dev.alvar.echoespast.relic.PetrifiedMobManager;
import dev.alvar.echoespast.relic.RelicEffects;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Gorgon of the sanctuary. Looking at her face petrifies; her snakes strike
 * with venom. The relic head cannot petrify her, and a carved pumpkin still
 * wards the gaze.
 */
public final class MedusaEntity extends Monster implements GeoEntity {
    public static final byte MOVE_NEUTRAL = 0;
    public static final byte MOVE_SNAKE = 1;
    public static final byte MOVE_PETRIFY = 2;
    public static final int EXPERIENCE_REWARD = 50;
    public static final float MAX_HEALTH = 200.0F;

    private static final EntityDataAccessor<Byte> COMBAT_MOVE = SynchedEntityData.defineId(
            MedusaEntity.class,
            EntityDataSerializers.BYTE);

    private static final RawAnimation IDLE =
            RawAnimation.begin().thenLoop("animation.model.idle");
    private static final RawAnimation MOVING =
            RawAnimation.begin().thenLoop("animation.model.moving");
    private static final RawAnimation SNAKE_ATTACK =
            RawAnimation.begin().thenPlay("animation.model.front_snake_attack");
    private static final RawAnimation PETRIFY_ATTACK =
            RawAnimation.begin().thenLoop("animation.model.petrifiaction_attack");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent;
    private final Map<UUID, GazeLock> gazeLocks = new HashMap<>();

    public MedusaEntity(EntityType<? extends MedusaEntity> type, Level level) {
        super(type, level);
        this.xpReward = EXPERIENCE_REWARD;
        this.bossEvent = new ServerBossEvent(
                getUUID(),
                net.minecraft.network.chat.Component.translatable(
                        "entity.echoes_show_the_past.medusa"),
                BossEvent.BossBarColor.GREEN,
                BossEvent.BossBarOverlay.NOTCHED_10);
        setPersistenceRequired();
        setPathfindingMalus(PathType.WATER, 0.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, MedusaBossMath.SNAKE_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.75D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.45D);
    }

    public static boolean isMedusa(LivingEntity entity) {
        return entity instanceof MedusaEntity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(COMBAT_MOVE, MOVE_NEUTRAL);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MedusaCombatGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, LivingEntity.class, 16.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(
                2,
                new NearestAttackableTargetGoal<>(
                        this,
                        LivingEntity.class,
                        10,
                        true,
                        false,
                        (target, level) -> canHunt(target)));
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return canHunt(target) && super.canAttack(target);
    }

    /**
     * Hunts every living creature in reach. Statues, other gorgons and
     * creative/spectator players are not prey.
     */
    public boolean canHunt(LivingEntity target) {
        if (target == this || !target.isAlive() || isMedusa(target)) {
            return false;
        }
        if (RelicEffects.isPermanentlyPetrified(target)) {
            return false;
        }
        if (target instanceof ArmorStand || target instanceof Mannequin) {
            return false;
        }
        return !(target instanceof Player player)
                || (!player.isCreative() && !player.isSpectator());
    }

    public byte getCombatMove() {
        return this.entityData.get(COMBAT_MOVE);
    }

    public void setCombatMove(byte move) {
        this.entityData.set(COMBAT_MOVE, move);
    }

    public boolean isPetrifyChanneling() {
        return getCombatMove() == MOVE_PETRIFY;
    }

    /**
     * Players petrify by looking at her face. Every other creature petrifies
     * when her own gaze lands on them.
     */
    public boolean isCaughtByGaze(LivingEntity target) {
        if (!canHunt(target)
                || MedusaHeadItem.isProtectedByPumpkin(target)
                || !hasLineOfSight(target)) {
            return false;
        }
        if (target instanceof Player) {
            return MedusaBossMath.isLookingAtFace(
                    target.getEyePosition(),
                    target.getLookAngle(),
                    getEyePosition(),
                    getLookAngle(),
                    MedusaBossMath.GAZE_RANGE);
        }
        return MedusaBossMath.isInGazeBeam(
                getEyePosition(),
                getLookAngle(),
                target.getEyePosition(),
                MedusaBossMath.GAZE_RANGE);
    }

    public void dismissBossBar() {
        bossEvent.removeAllPlayers();
        bossEvent.setVisible(false);
    }

    public void trySnakeStrike(LivingEntity target) {
        if (!(level() instanceof ServerLevel serverLevel)
                || !target.isAlive()
                || RelicEffects.isPermanentlyPetrified(target)) {
            return;
        }
        boolean inReach = MedusaBossMath.snakeStrikeReaches(
                getEyePosition(),
                getLookAngle(),
                target.getEyePosition(),
                MedusaBossMath.SNAKE_REACH)
                || distanceToSqr(target) <= MedusaBossMath.SNAKE_REACH * MedusaBossMath.SNAKE_REACH;
        if (!inReach) {
            return;
        }
        target.hurtServer(
                serverLevel,
                damageSources().mobAttack(this),
                MedusaBossMath.SNAKE_DAMAGE);
        target.addEffect(new MobEffectInstance(
                MobEffects.POISON,
                MedusaBossMath.SNAKE_POISON_TICKS,
                MedusaBossMath.SNAKE_POISON_AMPLIFIER));
        playSound(SoundEvents.SPIDER_HURT, 0.9F, 0.75F);
        playSound(SoundEvents.WITCH_THROW, 0.7F, 1.15F);
    }

    public void triggerCombatAnimation(String trigger) {
        try {
            triggerAnim("combat", trigger);
        } catch (UnsupportedOperationException ignored) {
            // Headless GameTest players reject GeckoLib client payloads.
        }
    }

    public void stopCombatAnimation(String trigger) {
        try {
            stopTriggeredAnim("combat", trigger);
        } catch (UnsupportedOperationException ignored) {
            // State synchronization remains authoritative in headless tests.
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel) || isDeadOrDying()) {
            return;
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());
        tickGaze(serverLevel);
    }

    private void tickGaze(ServerLevel level) {
        Iterator<Map.Entry<UUID, GazeLock>> iterator = gazeLocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, GazeLock> entry = iterator.next();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living)
                    || !living.isAlive()
                    || living.level() != level) {
                iterator.remove();
                continue;
            }
            ServerPlayer viewer = living instanceof ServerPlayer player ? player : null;
            boolean looking = isCaughtByGaze(living);
            boolean mobGaze = viewer == null;
            int next = mobGaze
                    ? MedusaBossMath.nextMobGazeLock(entry.getValue().ticks(), looking)
                    : MedusaBossMath.nextGazeLock(
                            entry.getValue().ticks(),
                            looking,
                            isPetrifyChanneling());
            if (next <= 0) {
                if (viewer != null && entry.getValue().visual()) {
                    sendGazeVisual(viewer, MedusaGazeVisualPayload.CANCEL, 8);
                }
                iterator.remove();
                continue;
            }
            boolean visual = entry.getValue().visual();
            if (viewer != null && !visual) {
                sendGazeVisual(
                        viewer,
                        MedusaGazeVisualPayload.START,
                        MedusaBossMath.GAZE_LOCK_TICKS);
                visual = true;
            } else if (viewer != null && looking && next == MedusaBossMath.GAZE_LOCK_TICKS / 2) {
                sendGazeVisual(viewer, MedusaGazeVisualPayload.CONTACT, 8);
            }
            boolean complete = mobGaze
                    ? MedusaBossMath.mobGazeCompletes(next)
                    : MedusaBossMath.gazeCompletes(next);
            if (complete) {
                if (viewer != null && visual) {
                    sendGazeVisual(viewer, MedusaGazeVisualPayload.CANCEL, 6);
                }
                iterator.remove();
                petrifyVictim(level, living);
                continue;
            }
            entry.setValue(new GazeLock(next, visual));
        }
        AABB area = getBoundingBox().inflate(MedusaBossMath.GAZE_RANGE);
        for (LivingEntity living : level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                this::canHunt)) {
            if (gazeLocks.containsKey(living.getUUID()) || !isCaughtByGaze(living)) {
                continue;
            }
            gazeLocks.put(living.getUUID(), new GazeLock(1, false));
        }
    }

    private void petrifyVictim(ServerLevel level, LivingEntity victim) {
        if (victim instanceof ServerPlayer player) {
            PetrifiedMobManager.leavePlayerStatueAndKill(level, this, player);
            return;
        }
        RelicEffects.petrifyPermanently(victim);
        MedusaPetrifyPayload petrify = new MedusaPetrifyPayload(victim.getId(), -1);
        try {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(victim, petrify);
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // Headless GameTest connections reject play-to-client payloads.
        }
        if (getTarget() == victim) {
            setTarget(null);
        }
    }

    private static void sendGazeVisual(ServerPlayer player, int phase, int durationTicks) {
        sendToPlayer(player, new MedusaGazeVisualPayload(phase, durationTicks));
    }

    private static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (!player.connection.hasChannel(payload)) {
            return;
        }
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // Headless GameTest connections reject play-to-client payloads.
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!RelicEffects.isPermanentlyPetrified(this) && bossEvent.isVisible()) {
            bossEvent.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossEvent.removeAllPlayers();
        gazeLocks.clear();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // Authored island boss; Peaceful and distance must not discard her.
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITCH_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GENERIC_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_SKELETON_DEATH;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("locomotion", 5, this::locomotionAnimation));
        controllers.add(new AnimationController<MedusaEntity>(
                        "combat",
                        0,
                        state -> PlayState.STOP)
                .triggerableAnim("snake_attack", SNAKE_ATTACK)
                .triggerableAnim("petrify_attack", PETRIFY_ATTACK));
    }

    private PlayState locomotionAnimation(AnimationTest<MedusaEntity> state) {
        if (isDeadOrDying() || RelicEffects.isPermanentlyPetrified(this)) {
            return PlayState.STOP;
        }
        if (getCombatMove() != MOVE_NEUTRAL) {
            return PlayState.STOP;
        }
        return state.setAndContinue(state.isMoving() ? MOVING : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    private record GazeLock(int ticks, boolean visual) {
    }
}
