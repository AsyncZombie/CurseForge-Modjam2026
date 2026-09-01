package dev.alvar.echoespast.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;
import dev.alvar.echoespast.entity.ai.UnknownGreekCombatGoal;
import dev.alvar.echoespast.entity.ai.UnknownEgyptianCombatGoal;
import dev.alvar.echoespast.entity.ai.UnknownMedievalCombatGoal;
import dev.alvar.echoespast.entity.ai.UnknownMedievalRuinsCombatGoal;
import dev.alvar.echoespast.entity.ai.UnknownSeekPedestalGoal;
import dev.alvar.echoespast.entity.combat.UnknownCombatState;
import dev.alvar.echoespast.server.UnknownFightManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Working name {@code ???} / Unknown. Featureless black silhouette; no eyes
 * (Medusa cannot petrify it). Dummy mode is for hub review before the fight loop.
 *
 * <p>This entity owns the synchronized Greek combat state while its combat
 * goal executes server-authoritative movement and hit geometry.
 */
public final class UnknownEntity extends Monster implements GeoEntity {
    public static final byte ERA_VOID = 0;
    public static final byte ERA_GREEK = 1;
    public static final byte ERA_EGYPTIAN = 2;
    public static final byte ERA_MEDIEVAL = 3;
    public static final byte COMBAT_FX_NONE = 0;
    public static final byte COMBAT_FX_HIT = 1;
    public static final byte COMBAT_FX_PLAYER_BLOCK = 2;
    public static final byte COMBAT_FX_ASPIS_BLOCK = 3;
    public static final byte COMBAT_FX_WALL_SLAM = 4;
    public static final byte COMBAT_FX_MINE_BLAST = 5;
    public static final byte COMBAT_FX_MINE_TETHER = 6;
    public static final byte COMBAT_FX_MINE_WEAKNESS = 7;
    public static final byte COMBAT_FX_MINE_LAUNCH = 8;
    public static final byte COMBAT_FX_MEDIEVAL_BLOCK = 9;
    /** Appended FX ids: all earlier wire values remain unchanged. */
    public static final byte COMBAT_FX_MEDIEVAL_CUT_HIT = 10;
    public static final byte COMBAT_FX_MEDIEVAL_CUT_BLOCK = 11;
    public static final byte COMBAT_VARIANT_OPENING = 0;
    public static final byte COMBAT_VARIANT_MEDIEVAL_SWEEP = 1;
    public static final byte COMBAT_VARIANT_MEDIEVAL_CHASE = 2;
    public static final int EXPERIENCE_REWARD = 500;

    private static final EntityDataAccessor<Byte> ERA = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> ARMORED = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DUMMY = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RITUAL_OFFERING = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RITUAL_CHANNELING = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> COMBAT_STATE = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> COMBAT_START_TICK = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LOCKED_DIRECTION_X = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LOCKED_DIRECTION_Z = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> COMBAT_ANCHOR_X = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> COMBAT_ANCHOR_Y = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> COMBAT_ANCHOR_Z = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> COMBAT_GAP_OFFSET = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> COMBAT_CORRIDOR_LENGTH = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> RUINS_COMBAT_VARIANT = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> COMBAT_VARIANT = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> COMBAT_FX_KIND = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> COMBAT_FX_TICK = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> COMBAT_FX_X = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> COMBAT_FX_Y = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> COMBAT_FX_Z = SynchedEntityData.defineId(
            UnknownEntity.class,
            EntityDataSerializers.FLOAT);

    private static final RawAnimation IDLE =
            RawAnimation.begin().thenLoop("misc.idle");
    private static final RawAnimation WALK =
            RawAnimation.begin().thenLoop("move.walk");
    private static final RawAnimation RUN =
            RawAnimation.begin().thenLoop("move.run");
    private static final RawAnimation HURT =
            RawAnimation.begin().thenPlay("misc.hurt");
    private static final RawAnimation DIE =
            RawAnimation.begin().thenPlayAndHold("misc.die");
    private static final RawAnimation GREEK_GUARD =
            RawAnimation.begin().thenLoop("combat.greek.guard");
    private static final RawAnimation GREEK_GUARD_WALK =
            RawAnimation.begin().thenLoop("combat.greek.guard_walk");
    private static final RawAnimation GREEK_STAB =
            RawAnimation.begin().thenPlayAndHold("combat.greek.stab");
    private static final RawAnimation GREEK_IMPALE =
            RawAnimation.begin().thenPlayAndHold("combat.greek.impale");
    private static final RawAnimation GREEK_BACKSTEP =
            RawAnimation.begin().thenPlay("combat.greek.backstep");
    private static final RawAnimation GREEK_CHARGE =
            RawAnimation.begin().thenPlayAndHold("combat.greek.charge");
    private static final RawAnimation GREEK_CRASH =
            RawAnimation.begin().thenPlayAndHold("combat.greek.crash");
    private static final RawAnimation GREEK_PHALANX =
            RawAnimation.begin().thenPlayAndHold("combat.greek.phalanx_summon");
    private static final RawAnimation GREEK_JAVELIN =
            RawAnimation.begin().thenPlayAndHold("combat.greek.javelin");
    private static final RawAnimation GREEK_SPEAR_ERUPTION =
            RawAnimation.begin().thenPlayAndHold("combat.greek.spear_eruption");
    private static final RawAnimation GREEK_SHIELD_BASH =
            RawAnimation.begin().thenPlayAndHold("combat.greek.shield_bash");
    private static final RawAnimation EGYPT_KHOPESH_COMBO =
            RawAnimation.begin().thenPlayAndHold("combat.egypt.khopesh_combo");
    private static final RawAnimation EGYPT_KHOPESH_RECOVERY =
            RawAnimation.begin().thenPlayAndHold("combat.egypt.khopesh_recovery");
    private static final RawAnimation EGYPT_KHOPESH_RECOVERY_LATE =
            RawAnimation.begin().thenPlayAndHold("combat.egypt.khopesh_recovery_late");
    private static final RawAnimation EGYPT_DUAT_GATE =
            RawAnimation.begin().thenPlayAndHold("combat.egypt.duat_gate");
    private static final RawAnimation EGYPT_SOLAR_JUDGMENT =
            RawAnimation.begin().thenPlayAndHold("combat.egypt.solar_judgment");
    private static final RawAnimation EGYPT_SEKHMET_HUNT =
            RawAnimation.begin().thenPlayAndHold("combat.egypt.sekhmet_hunt");
    private static final RawAnimation EGYPT_SEKHMET_RECOVERY =
            RawAnimation.begin().thenPlayAndHold("combat.egypt.sekhmet_recovery");
    private static final RawAnimation MEDIEVAL_COMBO =
            RawAnimation.begin().thenPlayAndHold("combat.medieval.combo");
    private static final RawAnimation MEDIEVAL_COMBO_SWEEP =
            RawAnimation.begin().thenPlayAndHold("combat.medieval.combo_sweep");
    private static final RawAnimation MEDIEVAL_COMBO_CHASE =
            RawAnimation.begin().thenPlayAndHold("combat.medieval.combo_chase");
    private static final RawAnimation MEDIEVAL_COMBO_RUINS =
            RawAnimation.begin().thenPlayAndHold("combat.medieval.combo_ruins");
    private static final RawAnimation MEDIEVAL_OVERHEAD =
            RawAnimation.begin().thenPlayAndHold("combat.medieval.overhead");
    private static final RawAnimation MEDIEVAL_SHIELD_BASH =
            RawAnimation.begin().thenPlayAndHold("combat.medieval.shield_bash");
    private static final RawAnimation MEDIEVAL_GUARD =
            RawAnimation.begin().thenPlayAndHold("combat.medieval.guard");
    private static final RawAnimation MEDIEVAL_RIPOSTE =
            RawAnimation.begin().thenPlayAndHold("combat.medieval.riposte");
    private static final RawAnimation MEDIEVAL_GRAB_DIVE =
            RawAnimation.begin().thenPlayAndHold("combat.medieval.grab_dive");
    private static final RawAnimation MEDIEVAL_SHIELD_BREAK =
            RawAnimation.begin().thenPlayAndHold("combat.medieval.shield_break");
    private static final RawAnimation MEDIEVAL_SHOULDER_RUSH =
            RawAnimation.begin().thenPlayAndHold("combat.medieval.shoulder_rush");
    private static final RawAnimation MEDIEVAL_RUBBLE_KICK =
            RawAnimation.begin().thenPlayAndHold("combat.medieval.rubble_kick");
    private static final RawAnimation VOID_EXECUTION =
            RawAnimation.begin().thenPlayAndHold("combat.void.execution");
    private static final RawAnimation RITUAL_OFFER =
            RawAnimation.begin().thenPlayAndHold("ritual.offer");
    private static final RawAnimation RITUAL_CHANNEL =
            RawAnimation.begin().thenLoop("ritual.channel");

    private final AnimatableInstanceCache geoCache =
            GeckoLibUtil.createInstanceCache(this);

    public UnknownEntity(EntityType<? extends UnknownEntity> type, Level level) {
        super(type, level);
        this.xpReward = EXPERIENCE_REWARD;
        // Unknown never stands in a damaging node. It may approach one so its
        // Egyptian combat sweep can crush adjacent cacti instead of pathing away.
        setPathfindingMalus(PathType.FIRE, -1.0F);
        setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, -1.0F);
        setPathfindingMalus(PathType.DAMAGING, -1.0F);
        setPathfindingMalus(PathType.DAMAGING_IN_NEIGHBOR, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, UnknownFightManager.BOSS_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    public static boolean isUnknown(LivingEntity entity) {
        return entity instanceof UnknownEntity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ERA, ERA_VOID);
        builder.define(ARMORED, false);
        builder.define(DUMMY, false);
        builder.define(RITUAL_OFFERING, false);
        builder.define(RITUAL_CHANNELING, false);
        builder.define(COMBAT_STATE, UnknownCombatState.NEUTRAL.networkId());
        builder.define(COMBAT_START_TICK, -1);
        builder.define(LOCKED_DIRECTION_X, 0.0F);
        builder.define(LOCKED_DIRECTION_Z, 0.0F);
        builder.define(COMBAT_ANCHOR_X, 0.0F);
        builder.define(COMBAT_ANCHOR_Y, 0.0F);
        builder.define(COMBAT_ANCHOR_Z, 0.0F);
        builder.define(COMBAT_GAP_OFFSET, 0.0F);
        builder.define(COMBAT_CORRIDOR_LENGTH, 0.0F);
        builder.define(RUINS_COMBAT_VARIANT, false);
        builder.define(COMBAT_VARIANT, COMBAT_VARIANT_OPENING);
        builder.define(COMBAT_FX_KIND, COMBAT_FX_NONE);
        builder.define(COMBAT_FX_TICK, -1);
        builder.define(COMBAT_FX_X, 0.0F);
        builder.define(COMBAT_FX_Y, 0.0F);
        builder.define(COMBAT_FX_Z, 0.0F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new UnknownSeekPedestalGoal(this));
        this.goalSelector.addGoal(2, new UnknownGreekCombatGoal(this));
        this.goalSelector.addGoal(2, new UnknownEgyptianCombatGoal(this));
        this.goalSelector.addGoal(2, new UnknownMedievalCombatGoal(this));
        this.goalSelector.addGoal(2, new UnknownMedievalRuinsCombatGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F) {
            @Override
            public boolean canUse() {
                return !UnknownFightManager.isArenaLocked(UnknownEntity.this) && super.canUse();
            }
        });
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return !UnknownFightManager.isArenaLocked(UnknownEntity.this) && super.canUse();
            }
        });
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !isDummy()
                        && !UnknownFightManager.isArenaLocked(UnknownEntity.this)
                        && !UnknownFightManager.isScriptedCombatActive(UnknownEntity.this)
                        && super.canUse();
            }
        });
        this.targetSelector.addGoal(
                2,
                new NearestAttackableTargetGoal<>(this, Player.class, true) {
                    @Override
                    public boolean canUse() {
                        return !isDummy()
                                && !UnknownFightManager.isArenaLocked(UnknownEntity.this)
                                && !UnknownFightManager.isScriptedCombatActive(UnknownEntity.this)
                                && super.canUse();
                    }
                });
    }

    public byte getEra() {
        return this.entityData.get(ERA);
    }

    public void setEra(byte era) {
        this.entityData.set(ERA, era);
    }

    public boolean isArmored() {
        return this.entityData.get(ARMORED);
    }

    public void setArmored(boolean armored) {
        this.entityData.set(ARMORED, armored);
    }

    public boolean isDummy() {
        return this.entityData.get(DUMMY);
    }

    public boolean isRitualOffering() {
        return this.entityData.get(RITUAL_OFFERING);
    }

    public boolean isRitualChanneling() {
        return this.entityData.get(RITUAL_CHANNELING);
    }

    public boolean isRitualBusy() {
        return isRitualOffering() || isRitualChanneling();
    }

    public void setRitualOffering(boolean offering) {
        this.entityData.set(RITUAL_OFFERING, offering);
        if (!offering && !level().isClientSide()) {
            stopTriggeredRitual("offer");
        }
    }

    public void setRitualChanneling(boolean channeling) {
        this.entityData.set(RITUAL_CHANNELING, channeling);
        if (!channeling && !level().isClientSide()) {
            stopTriggeredRitual("channel");
        }
    }

    public void setDummy(boolean dummy) {
        this.entityData.set(DUMMY, dummy);
        if (dummy) {
            setEra(ERA_VOID);
            setArmored(false);
            this.xpReward = 0;
            setNoAi(false);
            getNavigation().stop();
            setTarget(null);
            resetGreekCombat();
        }
    }

    public UnknownCombatState getCombatState() {
        return UnknownCombatState.fromNetwork(this.entityData.get(COMBAT_STATE));
    }

    public void beginGreekCombatState(
            UnknownCombatState state,
            long startGameTick,
            boolean ruinsVariant) {
        this.entityData.set(COMBAT_STATE, state.networkId());
        this.entityData.set(COMBAT_START_TICK, (int) startGameTick);
        this.entityData.set(RUINS_COMBAT_VARIANT, ruinsVariant);
        this.entityData.set(COMBAT_VARIANT, COMBAT_VARIANT_OPENING);
    }

    /** Changes substate without restarting the synchronized attack timeline. */
    public void continueGreekCombatState(UnknownCombatState state) {
        this.entityData.set(COMBAT_STATE, state.networkId());
    }

    public int combatElapsedTicks(long gameTick) {
        int start = this.entityData.get(COMBAT_START_TICK);
        return start < 0 ? 0 : Math.max(0, (int) gameTick - start);
    }

    public void setLockedCombatDirection(net.minecraft.world.phys.Vec3 direction) {
        this.entityData.set(LOCKED_DIRECTION_X, (float) direction.x);
        this.entityData.set(LOCKED_DIRECTION_Z, (float) direction.z);
    }

    public net.minecraft.world.phys.Vec3 getLockedCombatDirection() {
        return new net.minecraft.world.phys.Vec3(
                this.entityData.get(LOCKED_DIRECTION_X),
                0.0D,
                this.entityData.get(LOCKED_DIRECTION_Z));
    }

    public void setCombatAnchor(
            net.minecraft.world.phys.Vec3 position,
            double gapOffset,
            double corridorLength) {
        this.entityData.set(COMBAT_ANCHOR_X, (float) position.x);
        this.entityData.set(COMBAT_ANCHOR_Y, (float) position.y);
        this.entityData.set(COMBAT_ANCHOR_Z, (float) position.z);
        this.entityData.set(COMBAT_GAP_OFFSET, (float) gapOffset);
        this.entityData.set(COMBAT_CORRIDOR_LENGTH, (float) corridorLength);
    }

    public net.minecraft.world.phys.Vec3 getCombatAnchor() {
        return new net.minecraft.world.phys.Vec3(
                this.entityData.get(COMBAT_ANCHOR_X),
                this.entityData.get(COMBAT_ANCHOR_Y),
                this.entityData.get(COMBAT_ANCHOR_Z));
    }

    public float getCombatGapOffset() {
        return this.entityData.get(COMBAT_GAP_OFFSET);
    }

    public float getCombatCorridorLength() {
        return this.entityData.get(COMBAT_CORRIDOR_LENGTH);
    }

    public boolean isRuinsCombatVariant() {
        return this.entityData.get(RUINS_COMBAT_VARIANT);
    }

    public byte getCombatVariant() {
        return this.entityData.get(COMBAT_VARIANT);
    }

    public void setCombatVariant(byte variant) {
        this.entityData.set(
                COMBAT_VARIANT,
                (byte) Math.clamp(
                        variant,
                        COMBAT_VARIANT_OPENING,
                        COMBAT_VARIANT_MEDIEVAL_CHASE));
    }

    public boolean isGreekGuardActive(long gameTick) {
        return getEra() == ERA_GREEK
                && UnknownGreekCombatGoal.isGuardActive(
                getCombatState(),
                combatElapsedTicks(gameTick),
                isRuinsCombatVariant());
    }

    public boolean isMedievalGuardActive(long gameTick) {
        return getEra() == ERA_MEDIEVAL
                && UnknownMedievalCombatGoal.isGuardActive(
                        getCombatState(), combatElapsedTicks(gameTick));
    }

    public void showCombatFx(byte kind, net.minecraft.world.phys.Vec3 position, long gameTick) {
        this.entityData.set(COMBAT_FX_KIND, kind);
        this.entityData.set(COMBAT_FX_TICK, (int) gameTick);
        this.entityData.set(COMBAT_FX_X, (float) position.x);
        this.entityData.set(COMBAT_FX_Y, (float) position.y);
        this.entityData.set(COMBAT_FX_Z, (float) position.z);
    }

    public byte getCombatFxKind() {
        return this.entityData.get(COMBAT_FX_KIND);
    }

    public int getCombatFxTick() {
        return this.entityData.get(COMBAT_FX_TICK);
    }

    public net.minecraft.world.phys.Vec3 getCombatFxPosition() {
        return new net.minecraft.world.phys.Vec3(
                this.entityData.get(COMBAT_FX_X),
                this.entityData.get(COMBAT_FX_Y),
                this.entityData.get(COMBAT_FX_Z));
    }

    public void resetGreekCombat() {
        this.entityData.set(COMBAT_STATE, UnknownCombatState.NEUTRAL.networkId());
        this.entityData.set(COMBAT_START_TICK, -1);
        this.entityData.set(LOCKED_DIRECTION_X, 0.0F);
        this.entityData.set(LOCKED_DIRECTION_Z, 0.0F);
        this.entityData.set(COMBAT_ANCHOR_X, 0.0F);
        this.entityData.set(COMBAT_ANCHOR_Y, 0.0F);
        this.entityData.set(COMBAT_ANCHOR_Z, 0.0F);
        this.entityData.set(COMBAT_GAP_OFFSET, 0.0F);
        this.entityData.set(COMBAT_CORRIDOR_LENGTH, 0.0F);
        this.entityData.set(RUINS_COMBAT_VARIANT, false);
        this.entityData.set(COMBAT_VARIANT, COMBAT_VARIANT_OPENING);
        this.entityData.set(COMBAT_FX_KIND, COMBAT_FX_NONE);
        this.entityData.set(COMBAT_FX_TICK, -1);
        if (!level().isClientSide()) {
            UnknownFightManager.clearGreekCombatArtifacts(this);
            stopGreekAnimation("greek_stab");
            stopGreekAnimation("greek_impale");
            stopGreekAnimation("greek_backstep");
            stopGreekAnimation("greek_charge");
            stopGreekAnimation("greek_crash");
            stopGreekAnimation("greek_phalanx");
            stopGreekAnimation("greek_javelin");
            stopGreekAnimation("greek_spear_eruption");
            stopGreekAnimation("greek_shield_bash");
            stopGreekAnimation("egypt_khopesh_combo");
            stopGreekAnimation("egypt_khopesh_recovery");
            stopGreekAnimation("egypt_khopesh_recovery_late");
            stopGreekAnimation("egypt_duat_gate");
            stopGreekAnimation("egypt_solar_judgment");
            stopGreekAnimation("egypt_sekhmet_hunt");
            stopGreekAnimation("egypt_sekhmet_recovery");
            stopGreekAnimation("medieval_combo");
            stopGreekAnimation("medieval_combo_sweep");
            stopGreekAnimation("medieval_combo_chase");
            stopGreekAnimation("medieval_combo_ruins");
            stopGreekAnimation("medieval_overhead");
            stopGreekAnimation("medieval_shield_bash");
            stopGreekAnimation("medieval_guard");
            stopGreekAnimation("medieval_riposte");
            stopGreekAnimation("medieval_grab_dive");
            stopGreekAnimation("medieval_shield_break");
            stopGreekAnimation("medieval_shoulder_rush");
            stopGreekAnimation("medieval_rubble_kick");
            stopGreekAnimation("void_execution");
            stopRitualAnimation();
            this.entityData.set(RITUAL_OFFERING, false);
            this.entityData.set(RITUAL_CHANNELING, false);
        }
    }

    public void triggerRitualAnimation(String trigger) {
        try {
            triggerAnim("ritual", trigger);
        } catch (UnsupportedOperationException ignored) {
            // Headless GameTest players deliberately reject client animation payloads.
        }
    }

    private void stopRitualAnimation() {
        stopTriggeredRitual("offer");
        stopTriggeredRitual("channel");
    }

    private void stopTriggeredRitual(String trigger) {
        try {
            stopTriggeredAnim("ritual", trigger);
        } catch (UnsupportedOperationException ignored) {
            // State synchronization remains authoritative in headless tests.
        }
    }

    public void triggerGreekAnimation(String trigger) {
        try {
            triggerAnim("combat", trigger);
        } catch (UnsupportedOperationException ignored) {
            // Headless GameTest players deliberately reject client animation payloads.
        }
    }

    public void stopCombatAnimation(String trigger) {
        stopGreekAnimation(trigger);
    }

    private void stopGreekAnimation(String trigger) {
        try {
            stopTriggeredAnim("combat", trigger);
        } catch (UnsupportedOperationException ignored) {
            // State synchronization remains authoritative in headless tests.
        }
    }

    public boolean isVulnerableVoid() {
        return !isArmored();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean damaged = super.hurtServer(level, source, amount);
        if (damaged && isAlive() && getCombatState() == UnknownCombatState.NEUTRAL) {
            try {
                triggerAnim("reaction", "hurt");
            } catch (UnsupportedOperationException ignored) {
                // Headless GameTest players have no GeckoLib client receiver.
            }
        }
        return damaged;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("locomotion", 5, this::locomotionAnimation));
        controllers.add(new AnimationController<UnknownEntity>(
                        "reaction",
                        1,
                        state -> PlayState.STOP)
                .triggerableAnim("hurt", HURT));
        controllers.add(new AnimationController<UnknownEntity>(
                        "combat",
                        0,
                        this::combatAnimation)
                .triggerableAnim("greek_stab", GREEK_STAB)
                .triggerableAnim("greek_impale", GREEK_IMPALE)
                .triggerableAnim("greek_backstep", GREEK_BACKSTEP)
                .triggerableAnim("greek_charge", GREEK_CHARGE)
                .triggerableAnim("greek_crash", GREEK_CRASH)
                .triggerableAnim("greek_phalanx", GREEK_PHALANX)
                .triggerableAnim("greek_javelin", GREEK_JAVELIN)
                .triggerableAnim("greek_spear_eruption", GREEK_SPEAR_ERUPTION)
                .triggerableAnim("greek_shield_bash", GREEK_SHIELD_BASH)
                .triggerableAnim("egypt_khopesh_combo", EGYPT_KHOPESH_COMBO)
                .triggerableAnim("egypt_khopesh_recovery", EGYPT_KHOPESH_RECOVERY)
                .triggerableAnim("egypt_khopesh_recovery_late", EGYPT_KHOPESH_RECOVERY_LATE)
                .triggerableAnim("egypt_duat_gate", EGYPT_DUAT_GATE)
                .triggerableAnim("egypt_solar_judgment", EGYPT_SOLAR_JUDGMENT)
                .triggerableAnim("egypt_sekhmet_hunt", EGYPT_SEKHMET_HUNT)
                .triggerableAnim("egypt_sekhmet_recovery", EGYPT_SEKHMET_RECOVERY)
                .triggerableAnim("medieval_combo", MEDIEVAL_COMBO)
                .triggerableAnim("medieval_combo_sweep", MEDIEVAL_COMBO_SWEEP)
                .triggerableAnim("medieval_combo_chase", MEDIEVAL_COMBO_CHASE)
                .triggerableAnim("medieval_combo_ruins", MEDIEVAL_COMBO_RUINS)
                .triggerableAnim("medieval_overhead", MEDIEVAL_OVERHEAD)
                .triggerableAnim("medieval_shield_bash", MEDIEVAL_SHIELD_BASH)
                .triggerableAnim("medieval_guard", MEDIEVAL_GUARD)
                .triggerableAnim("medieval_riposte", MEDIEVAL_RIPOSTE)
                .triggerableAnim("medieval_grab_dive", MEDIEVAL_GRAB_DIVE)
                .triggerableAnim("medieval_shield_break", MEDIEVAL_SHIELD_BREAK)
                .triggerableAnim("medieval_shoulder_rush", MEDIEVAL_SHOULDER_RUSH)
                .triggerableAnim("medieval_rubble_kick", MEDIEVAL_RUBBLE_KICK)
                .triggerableAnim("void_execution", VOID_EXECUTION));
        controllers.add(new AnimationController<UnknownEntity>(
                        "ritual",
                        0,
                        state -> PlayState.STOP)
                .triggerableAnim("offer", RITUAL_OFFER)
                .triggerableAnim("channel", RITUAL_CHANNEL));
    }

    private PlayState combatAnimation(AnimationTest<UnknownEntity> state) {
        state.setControllerSpeed(isRuinsCombatVariant()
                ? (getEra() == ERA_MEDIEVAL
                        ? UnknownMedievalRuinsCombatGoal.ANIMATION_SPEED
                        : 1.15F)
                : 1.0F);
        return PlayState.STOP;
    }

    private PlayState locomotionAnimation(AnimationTest<UnknownEntity> state) {
        if (isDeadOrDying()) {
            return state.setAndContinue(DIE);
        }
        if (getCombatState() != UnknownCombatState.NEUTRAL) {
            return PlayState.STOP;
        }
        if (isRitualBusy()) {
            return PlayState.STOP;
        }
        boolean greekGuard = getEra() == ERA_GREEK;
        if (state.isMoving()) {
            if (greekGuard) {
                return state.setAndContinue(GREEK_GUARD_WALK);
            }
            return state.setAndContinue(isSprinting() ? RUN : WALK);
        }
        if (greekGuard) {
            return state.setAndContinue(GREEK_GUARD);
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    protected void dropCustomDeathLoot(
            ServerLevel level,
            DamageSource damageSource,
            boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        if (isDummy()) {
            return;
        }
        if (damageSource.getEntity() instanceof ServerPlayer player) {
            UnknownFightManager.grantStone(player);
        } else {
            Player nearest = level.getNearestPlayer(this, 32.0D);
            if (nearest instanceof ServerPlayer serverPlayer) {
                UnknownFightManager.grantStone(serverPlayer);
            }
        }
        UnknownFightManager.onBossDefeated(this);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putByte("Era", getEra());
        output.putBoolean("Armored", isArmored());
        output.putBoolean("Dummy", isDummy());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setEra(input.getByteOr("Era", ERA_VOID));
        setArmored(input.getBooleanOr("Armored", false));
        setDummy(input.getBooleanOr("Dummy", false));
        resetGreekCombat();
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        // Boss weapons/armor are fight props, never timeless-void litter.
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return !isDummy();
    }
}
