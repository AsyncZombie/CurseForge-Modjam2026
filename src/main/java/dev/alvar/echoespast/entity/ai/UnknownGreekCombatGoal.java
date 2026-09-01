package dev.alvar.echoespast.entity.ai;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.entity.SpectralHopliteEntity;
import dev.alvar.echoespast.entity.UnknownEntity;
import dev.alvar.echoespast.entity.combat.UnknownCombatState;
import dev.alvar.echoespast.entity.combat.UnknownGreekCombatMath;
import dev.alvar.echoespast.server.UnknownFightManager;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Complete server-authoritative Greek kit: hoplite footwork, impaling stab,
 * charge, phalanx, javelin, shield bash and concentric spear eruption.
 */
public final class UnknownGreekCombatGoal extends Goal {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnknownGreekCombatGoal.class);

    public static final int STAB_WINDUP_TICKS = 16;
    public static final int STAB_LOCK_TICK = 12;
    public static final int STAB_ACTIVE_TICKS = 7;
    public static final int STAB_BETWEEN_CUTS_PAST = 6;
    public static final int STAB_BETWEEN_CUTS_RUINS = 4;
    public static final int STAB_RECOVERY_TICKS = 20;
    public static final int STAB_SEQUENCE_TICKS =
            STAB_WINDUP_TICKS + STAB_ACTIVE_TICKS + STAB_BETWEEN_CUTS_PAST + STAB_ACTIVE_TICKS
                    + STAB_RECOVERY_TICKS;
    public static final double STAB_LUNGE_DISTANCE = 0.0D;
    public static final double STAB_DORY_REACH = 8.4D;
    public static final double STAB_FOLLOWUP_REACH = 5.6D;
    public static final double STAB_EFFECTIVE_REACH = STAB_LUNGE_DISTANCE + STAB_DORY_REACH;
    public static final float STAB_DAMAGE = 8.0F;
    public static final float STAB_FOLLOWUP_DAMAGE = 6.0F;
    public static final float STAB_FOLLOWUP_BLOCKED_DAMAGE = 3.0F;
    public static final float STAB_BLOCKED_DAMAGE = 4.0F;
    public static final int STAB_IMPALE_TICKS_PAST = 48;
    public static final int STAB_IMPALE_TICKS_RUINS = 44;
    public static final int STAB_IMPALE_DAMAGE_INTERVAL = 8;
    public static final float STAB_IMPALE_TICK_DAMAGE = 4.0F;
    public static final double STAB_IMPALE_LIFT = 2.25D;
    public static final double PHALANX_SPRINT_PER_TICK = 0.28D;
    public static final double PHALANX_HIT_INFLATE = 0.28D;
    public static final double GUARD_ARC_DEGREES = 140.0D;

    public static final int CHARGE_WINDUP_PAST = 26;
    public static final int CHARGE_WINDUP_RUINS = 22;
    public static final int CHARGE_LOCK_PAST = 18;
    public static final int CHARGE_LOCK_RUINS = 16;
    public static final int CHARGE_ACTIVE_PAST = 24;
    public static final int CHARGE_ACTIVE_RUINS = 22;
    public static final int CHARGE_RECOVERY_PAST = 22;
    public static final int CHARGE_RECOVERY_RUINS = 20;
    public static final int CRASH_STUN_TICKS = 32;
    public static final double CHARGE_SPEED_PAST = 0.75D;
    public static final double CHARGE_SPEED_RUINS = 0.9D;
    public static final float CHARGE_DAMAGE = 10.0F;
    public static final float CHARGE_BLOCKED_DAMAGE = 5.0F;

    public static final int PHALANX_WARNING_PAST = 28;
    public static final int PHALANX_WARNING_RUINS = 24;
    public static final int PHALANX_SECOND_ROW_DELAY = 16;
    public static final int PHALANX_THIRD_ROW_DELAY = 32;
    public static final int PHALANX_RECOVERY_PAST = 10;
    public static final int PHALANX_RECOVERY_RUINS = 8;
    public static final double PHALANX_SPEED_PAST = 0.7D;
    public static final double PHALANX_SPEED_RUINS = 0.8D;
    public static final double PHALANX_GAP_WIDTH = 4.0D;
    public static final double PHALANX_ROW_WIDTH = 16.0D;
    public static final double PHALANX_CORRIDOR_LENGTH = 20.0D;
    public static final int PHALANX_PATH_ALLOWANCE_TICKS = 6;
    public static final float PHALANX_DAMAGE = 10.0F;
    public static final float PHALANX_BLOCKED_DAMAGE = 5.0F;

    public static final int JAVELIN_WARNING_PAST = 24;
    public static final int JAVELIN_WARNING_RUINS = 20;
    public static final int JAVELIN_LOCK_PAST = 14;
    public static final int JAVELIN_LOCK_RUINS = 12;
    public static final int JAVELIN_RECOVERY_PAST = 12;
    public static final int JAVELIN_RECOVERY_RUINS = 10;
    public static final float JAVELIN_DAMAGE = 10.0F;
    public static final float JAVELIN_BLOCKED_DAMAGE = 5.0F;
    public static final double JAVELIN_IMPACT_RADIUS = 1.8D;
    public static final double JAVELIN_VISUAL_HEIGHT = 9.5D;

    public static final int SPEAR_ERUPTION_WINDUP_PAST = 18;
    public static final int SPEAR_ERUPTION_WINDUP_RUINS = 14;
    public static final int SPEAR_ERUPTION_LOCK_PAST = 10;
    public static final int SPEAR_ERUPTION_LOCK_RUINS = 8;
    public static final int SPEAR_ERUPTION_RING_DELAY_PAST = 2;
    public static final int SPEAR_ERUPTION_RING_DELAY_RUINS = 1;
    public static final int SPEAR_ERUPTION_PERSIST_PAST = 52;
    public static final int SPEAR_ERUPTION_PERSIST_RUINS = 46;
    public static final int SPEAR_ERUPTION_FADE_TICKS = 10;
    public static final int SPEAR_ERUPTION_RECOVERY_PAST = 10;
    public static final int SPEAR_ERUPTION_RECOVERY_RUINS = 8;
    public static final int SPEAR_ERUPTION_COOLDOWN_PAST = 104;
    public static final int SPEAR_ERUPTION_COOLDOWN_RUINS = 76;
    public static final int SPEAR_ERUPTION_RINGS_PAST = 6;
    public static final int SPEAR_ERUPTION_RINGS_RUINS = 8;
    public static final int SPEAR_ERUPTION_BASE_SPEARS = 16;
    public static final int SPEAR_ERUPTION_SPEARS_PER_RING = 6;
    public static final double SPEAR_ERUPTION_FIRST_RADIUS = 1.75D;
    public static final double SPEAR_ERUPTION_RING_SPACING = 1.35D;
    public static final double SPEAR_ERUPTION_HIT_HALF_THICKNESS = 0.72D;
    public static final double SPEAR_ERUPTION_FIELD_MARGIN = 0.48D;
    public static final double SPEAR_ERUPTION_VISUAL_HEIGHT = 2.55D;
    public static final float SPEAR_ERUPTION_DAMAGE = 8.0F;
    public static final int SPEAR_ERUPTION_SLOW_DURATION = 8;
    public static final int SPEAR_ERUPTION_SLOW_AMPLIFIER = 2;

    public static final int SHIELD_BASH_WINDUP_PAST = 12;
    public static final int SHIELD_BASH_WINDUP_RUINS = 10;
    public static final int SHIELD_BASH_LOCK_PAST = 8;
    public static final int SHIELD_BASH_LOCK_RUINS = 6;
    public static final int SHIELD_BASH_ACTIVE_TICKS = 4;
    public static final int SHIELD_BASH_RECOVERY_PAST = 18;
    public static final int SHIELD_BASH_RECOVERY_RUINS = 14;
    public static final int SHIELD_BASH_COOLDOWN_PAST = 44;
    public static final int SHIELD_BASH_COOLDOWN_RUINS = 30;
    public static final int SHIELD_BASH_PRESSURE_PAST = 4;
    public static final int SHIELD_BASH_PRESSURE_RUINS = 2;
    public static final double SHIELD_BASH_REACH = 2.85D;
    public static final float SHIELD_BASH_DAMAGE = 4.0F;
    public static final float SHIELD_BASH_BLOCKED_DAMAGE = 2.0F;
    public static final float SHIELD_BASH_WALL_DAMAGE = 6.0F;
    public static final int SHIELD_BASH_WALL_WINDOW = 10;

    public static final int INITIAL_ATTACK_DELAY_PAST = 10;
    public static final int INITIAL_ATTACK_DELAY_RUINS = 6;
    public static final int CHARGE_COOLDOWN_PAST = 80;
    public static final int CHARGE_COOLDOWN_RUINS = 55;
    public static final int PHALANX_COOLDOWN_PAST = 100;
    public static final int PHALANX_COOLDOWN_RUINS = 75;
    public static final int JAVELIN_COOLDOWN_PAST = 70;
    public static final int JAVELIN_COOLDOWN_RUINS = 52;
    public static final int NEUTRAL_DELAY_PAST = 6;
    public static final int NEUTRAL_DELAY_RUINS = 1;

    private static final double MIN_STAB_RANGE = 2.5D;
    private static final double MAX_STAB_RANGE = 8.5D;
    private static final double MIN_CHARGE_RANGE = 6.0D;
    private static final double MAX_CHARGE_RANGE = 18.0D;
    private static final double MIN_PHALANX_RANGE = 4.0D;
    private static final double MAX_PHALANX_RANGE = 18.0D;
    private static final double MIN_JAVELIN_RANGE = 3.0D;
    private static final double MAX_JAVELIN_RANGE = 24.0D;
    private static final double MIN_SPEAR_ERUPTION_RANGE = 3.25D;
    private static final double MAX_ELEVATED_RANGED_RANGE = 28.0D;
    private static final double APPROACH_RANGE = 8.5D;
    private static final double BACKSTEP_RANGE = 3.0D;
    private static final double BACKSTEP_DISTANCE = 1.8D;
    private static final int BACKSTEP_TICKS = 6;
    private static final double SPEAR_RADIUS = 0.80D;
    private static final double[] PHALANX_LANES = {-8.0D, -6.0D, -4.0D, -2.0D, 0.0D, 2.0D, 4.0D, 6.0D, 8.0D};
    private static final Attack[] PAST_ATTACK_PATTERN = {
        Attack.STAB, Attack.CHARGE, Attack.SPEAR_ERUPTION, Attack.JAVELIN, Attack.PHALANX
    };
    private static final Attack[] RUINS_ATTACK_PATTERN = {
        Attack.STAB,
        Attack.CHARGE,
        Attack.JAVELIN,
        Attack.SPEAR_ERUPTION,
        Attack.STAB,
        Attack.PHALANX,
        Attack.CHARGE,
        Attack.SPEAR_ERUPTION,
        Attack.JAVELIN,
        Attack.STAB,
        Attack.PHALANX,
        Attack.SPEAR_ERUPTION,
        Attack.STAB
    };

    private final UnknownEntity boss;
    private final boolean clockwise;
    private final List<PhalanxRow> phalanxRows = new ArrayList<>();
    private int attackDelay;
    private int chargeCooldown;
    private int phalanxCooldown;
    private int javelinCooldown;
    private int spearEruptionCooldown;
    private int shieldBashCooldown;
    private int closePressureTicks;
    private int backstepCooldown;
    private int backstepTicks;
    private int recoveryEndElapsed;
    private int attackSerial;
    private Vec3 backstepDirection = Vec3.ZERO;
    private boolean attackHit;
    private final boolean[] stabHits = new boolean[4];
    private int stabCutCount;
    private boolean firstPastAttack;
    private UUID impaledVictim;
    private long impaleStartedTick = -1L;
    private Vec3 impaleBase = Vec3.ZERO;
    private Vec3 impaleLastSafe = Vec3.ZERO;
    private Vec3 impaleDirection = Vec3.ZERO;
    private double spearEruptionPhase;
    private UUID wallSlamVictim;
    private int wallSlamTicks;
    private Vec3 wallSlamDirection = Vec3.ZERO;
    private Attack currentAttack = Attack.NONE;
    private Attack lastAttack = Attack.NONE;
    private PhalanxPlan phalanxPlan;

    public UnknownGreekCombatGoal(UnknownEntity boss) {
        this.boss = boss;
        this.clockwise = (boss.getUUID().hashCode() & 1) == 0;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return UnknownFightManager.greekCombatTarget(boss) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        attackDelay = initialAttackDelay(UnknownFightManager.isGreekRuins(boss));
        chargeCooldown = 0;
        phalanxCooldown = 0;
        javelinCooldown = 0;
        spearEruptionCooldown = 0;
        shieldBashCooldown = 0;
        closePressureTicks = 0;
        backstepCooldown = 0;
        backstepTicks = 0;
        attackSerial = 0;
        currentAttack = Attack.NONE;
        clearImpaleTracking();
        clearWallSlam();
        lastAttack = Attack.NONE;
        firstPastAttack = !UnknownFightManager.isGreekRuins(boss);
        clearHoplites();
        boss.resetGreekCombat();
    }

    @Override
    public void stop() {
        if (boss.level() instanceof ServerLevel level) {
            releaseImpaledPlayer(level, true);
        } else {
            clearImpaleTracking();
        }
        boss.getNavigation().stop();
        boss.setTarget(null);
        clearHoplites();
        boss.resetGreekCombat();
        backstepTicks = 0;
        currentAttack = Attack.NONE;
        closePressureTicks = 0;
        clearWallSlam();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (impaledVictim != null && boss.getCombatState() != UnknownCombatState.STAB) {
            releaseImpaledPlayer(level, true);
        }
        ServerPlayer target = UnknownFightManager.greekCombatTarget(boss);
        if (target == null) {
            releaseImpaledPlayer(level, true);
            stop();
            return;
        }
        boss.setTarget(target);
        chargeCooldown = Math.max(0, chargeCooldown - 1);
        phalanxCooldown = Math.max(0, phalanxCooldown - 1);
        javelinCooldown = Math.max(0, javelinCooldown - 1);
        spearEruptionCooldown = Math.max(0, spearEruptionCooldown - 1);
        shieldBashCooldown = Math.max(0, shieldBashCooldown - 1);
        backstepCooldown = Math.max(0, backstepCooldown - 1);
        tickWallSlam(level);

        switch (boss.getCombatState()) {
            case NEUTRAL -> tickNeutral(level, target);
            case STAB -> tickStab(level, target);
            case CHARGE -> tickCharge(level, target);
            case PHALANX -> tickPhalanx(level, target);
            case JAVELIN -> tickJavelin(level, target);
            case SPEAR_ERUPTION -> tickSpearEruption(level, target);
            case SHIELD_BASH -> tickShieldBash(level, target);
            case CRASH_STUN -> tickCrashStun(level);
            case RECOVERY -> tickRecovery(level);
            case BREATHER -> boss.resetGreekCombat();
        }
    }

    private void tickNeutral(ServerLevel level, ServerPlayer target) {
        if (backstepTicks > 0) {
            boss.getNavigation().stop();
            boss.move(MoverType.SELF, backstepDirection.scale(BACKSTEP_DISTANCE / BACKSTEP_TICKS));
            face(target.position());
            backstepTicks--;
            return;
        }

        double distance = horizontalDistance(boss.position(), target.position());
        boolean elevatedUnreachable = distance <= 4.1D && isElevatedUnreachable(target);
        face(target.position());
        if (!elevatedUnreachable && distance <= 3.35D && boss.hasLineOfSight(target)) {
            closePressureTicks++;
        } else if (distance > 4.1D) {
            closePressureTicks = 0;
        } else {
            closePressureTicks = Math.max(0, closePressureTicks - 1);
        }
        boolean ruins = UnknownFightManager.isGreekRuins(boss);
        if (!elevatedUnreachable
                && boss.hasLineOfSight(target)
                && UnknownGreekCombatMath.shouldShieldBash(
                distance,
                closePressureTicks,
                shieldBashPressureTicks(ruins),
                shieldBashCooldown)) {
            beginShieldBash(level, target);
            return;
        }
        if (!elevatedUnreachable
                && distance < BACKSTEP_RANGE
                && backstepCooldown == 0
                && beginSafeBackstep(level, target)) {
            return;
        }
        if (distance > APPROACH_RANGE) {
            boss.getNavigation().moveTo(target, 1.0D);
        } else {
            orbit(target, distance);
        }

        if (attackDelay-- > 0) {
            return;
        }
        AttackChoice choice = chooseAttack(level, target, distance);
        switch (choice.attack) {
            case STAB -> beginStab(level, target);
            case CHARGE -> beginCharge(level, target);
            case PHALANX -> beginPhalanx(level, target, choice.phalanxPlan);
            case JAVELIN -> beginJavelin(level, target);
            case SPEAR_ERUPTION -> beginSpearEruption(level, target);
            case NONE -> attackDelay = 4;
        }
    }

    private AttackChoice chooseAttack(ServerLevel level, ServerPlayer target, double distance) {
        boolean elevatedUnreachable = isElevatedUnreachable(target);
        boolean canJavelin = javelinCooldown == 0
                && lastAttack != Attack.JAVELIN
                && distance <= (elevatedUnreachable ? MAX_ELEVATED_RANGED_RANGE : MAX_JAVELIN_RANGE)
                && (elevatedUnreachable || distance >= MIN_JAVELIN_RANGE);
        boolean canSpearEruption = !elevatedUnreachable
                && spearEruptionCooldown == 0
                && lastAttack != Attack.SPEAR_ERUPTION
                && distance >= MIN_SPEAR_ERUPTION_RANGE
                && distance <= spearEruptionRange(boss.isRuinsCombatVariant()) + 0.8D
                && Math.abs(target.getY() - boss.getY()) <= 2.75D
                && boss.hasLineOfSight(target);
        boolean phalanxWindow = phalanxCooldown == 0
                && lastAttack != Attack.PHALANX
                && distance <= (elevatedUnreachable ? MAX_ELEVATED_RANGED_RANGE : MAX_PHALANX_RANGE)
                && (elevatedUnreachable || distance >= MIN_PHALANX_RANGE);
        PhalanxPlan plan = phalanxWindow
                ? findPhalanxPlan(level, target, elevatedUnreachable)
                : null;
        boolean canPhalanx = plan != null;

        if (elevatedUnreachable) {
            return chooseElevatedAttack(canJavelin, canPhalanx, plan);
        }

        if (firstPastAttack
                && distance >= MIN_STAB_RANGE
                && distance <= MAX_STAB_RANGE
                && boss.hasLineOfSight(target)) {
            return new AttackChoice(Attack.STAB, null);
        }

        boolean canStab = distance >= MIN_STAB_RANGE
                && distance <= MAX_STAB_RANGE
                && boss.hasLineOfSight(target);
        boolean canCharge = chargeCooldown == 0
                && lastAttack != Attack.CHARGE
                && distance >= MIN_CHARGE_RANGE
                && distance <= MAX_CHARGE_RANGE
                && boss.hasLineOfSight(target);
        if (canCharge) {
            Vec3 predicted = UnknownGreekCombatMath.predictHorizontal(
                    target.position(),
                    target.getDeltaMovement(),
                    chargeLockTicks(UnknownFightManager.isGreekRuins(boss)),
                    4.0D);
            Vec3 direction = UnknownGreekCombatMath.horizontalDirection(
                    boss.position(), predicted, boss.getLookAngle());
            canCharge = isChargePathClear(level, direction, Math.min(distance + 2.0D, 18.0D));
        }

        Attack[] pattern = boss.isRuinsCombatVariant()
                ? RUINS_ATTACK_PATTERN
                : PAST_ATTACK_PATTERN;
        Attack preferred = pattern[Math.floorMod(attackSerial, pattern.length)];
        if (preferred == Attack.CHARGE && canCharge) {
            return new AttackChoice(Attack.CHARGE, null);
        }
        if (preferred == Attack.PHALANX && canPhalanx) {
            return new AttackChoice(Attack.PHALANX, plan);
        }
        if (preferred == Attack.JAVELIN && canJavelin) {
            return new AttackChoice(Attack.JAVELIN, null);
        }
        if (preferred == Attack.SPEAR_ERUPTION && canSpearEruption) {
            return new AttackChoice(Attack.SPEAR_ERUPTION, null);
        }
        if (preferred == Attack.STAB && canStab) {
            return new AttackChoice(Attack.STAB, null);
        }
        if (canPhalanx) {
            return new AttackChoice(Attack.PHALANX, plan);
        }
        if (canCharge) {
            return new AttackChoice(Attack.CHARGE, null);
        }
        if (canStab) {
            return new AttackChoice(Attack.STAB, null);
        }
        if (canSpearEruption) {
            return new AttackChoice(Attack.SPEAR_ERUPTION, null);
        }
        if (canJavelin) {
            return new AttackChoice(Attack.JAVELIN, null);
        }
        return new AttackChoice(Attack.NONE, null);
    }

    private AttackChoice chooseElevatedAttack(
            boolean canJavelin,
            boolean canPhalanx,
            PhalanxPlan plan) {
        if (lastAttack == Attack.JAVELIN && canPhalanx) {
            return new AttackChoice(Attack.PHALANX, plan);
        }
        if (lastAttack == Attack.PHALANX && canJavelin) {
            return new AttackChoice(Attack.JAVELIN, null);
        }
        if ((attackSerial & 1) == 0 && canJavelin) {
            return new AttackChoice(Attack.JAVELIN, null);
        }
        if (canPhalanx) {
            return new AttackChoice(Attack.PHALANX, plan);
        }
        if (canJavelin) {
            return new AttackChoice(Attack.JAVELIN, null);
        }
        return new AttackChoice(Attack.NONE, null);
    }

    private void beginStab(ServerLevel level, ServerPlayer target) {
        releaseImpaledPlayer(level, false);
        beginAttack(level, Attack.STAB, UnknownCombatState.STAB);
        firstPastAttack = false;
        attackHit = false;
        stabCutCount = stabCutCount(boss.isRuinsCombatVariant());
        java.util.Arrays.fill(stabHits, false);
        boss.setLockedCombatDirection(Vec3.ZERO);
        boss.setCombatAnchor(boss.position(), 0.0D, stabCutCount);
        boss.triggerGreekAnimation("greek_stab");
        level.playSound(null, boss.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                SoundSource.HOSTILE, 1.0F, 0.78F);
        LOGGER.debug("Unknown begins Greek stab string ({} cuts, ruins={})",
                stabCutCount,
                boss.isRuinsCombatVariant());
    }

    private void tickStab(ServerLevel level, ServerPlayer target) {
        boolean ruins = boss.isRuinsCombatVariant();
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        int lock = stabLockTick(ruins);
        int activeTicks = stabActiveTicks(ruins);
        holdPosition();

        if (impaledVictim != null) {
            faceDirection(impaleDirection);
            if (!tickImpaledPlayer(level)) {
                enterRecovery(elapsed, stabRecoveryTicks(ruins));
            }
            return;
        }

        if (elapsed < lock) {
            face(target.position());
        } else if (elapsed == lock) {
            lockDirection(target.position());
            level.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_RETURN,
                    SoundSource.HOSTILE, 1.1F, 0.68F);
        }
        Vec3 direction = currentTelegraphDirection(target);
        if (elapsed >= lock) {
            faceDirection(direction);
        }

        for (int cut = 0; cut < stabCutCount; cut++) {
            int start = stabCutStartTick(ruins, cut);
            if (cut > 0 && elapsed == start - 1) {
                // Micro re-aim for follow-ups without fully freeing the first lock.
                Vec3 follow = UnknownGreekCombatMath.horizontalDirection(
                        boss.position(), target.position(), direction);
                boss.setLockedCombatDirection(follow);
                direction = follow;
                faceDirection(follow);
                level.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_THROW.value(),
                        SoundSource.HOSTILE, 0.72F, 0.92F + cut * 0.08F);
            }
            if (elapsed >= start && elapsed < start + activeTicks) {
                int activeTick = elapsed - start;
                double maxReach = cut == 0 ? STAB_DORY_REACH : STAB_FOLLOWUP_REACH;
                double reach = maxReach * smoothStep((activeTick + 1.0D) / activeTicks);
                tryStabHit(level, target, direction, boss.position(), reach, cut);
            }
        }
        int lastEnd = stabCutStartTick(ruins, stabCutCount - 1) + activeTicks;
        if (elapsed >= lastEnd) {
            enterRecovery(elapsed, stabRecoveryTicks(ruins));
        }
    }

    private void beginImpale(
            ServerLevel level,
            ServerPlayer target,
            Vec3 direction) {
        impaledVictim = target.getUUID();
        impaleStartedTick = level.getGameTime();
        impaleBase = target.position();
        impaleLastSafe = impaleBase;
        impaleDirection = UnknownGreekCombatMath.horizontalDirection(
                Vec3.ZERO, direction, boss.getLookAngle());
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        boss.setCombatAnchor(
                target.position(),
                elapsed,
                stabImpaleTicks(boss.isRuinsCombatVariant()));
        boss.triggerGreekAnimation("greek_impale");
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0F;
        target.hurtMarked = true;
        level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_HIT,
                SoundSource.HOSTILE, 1.25F, 0.62F);
    }

    /**
     * Holds the victim on a collision-checked vertical path. If a ceiling or a
     * phase transition invalidates that path, the last known clear position is
     * retained and the player is released normally rather than embedded.
     */
    private boolean tickImpaledPlayer(ServerLevel level) {
        if (!(level.getEntity(impaledVictim) instanceof ServerPlayer target)
                || !target.isAlive()
                || target.isSpectator()) {
            releaseImpaledPlayer(level, false);
            return false;
        }
        int duration = stabImpaleTicks(boss.isRuinsCombatVariant());
        int localTick = (int) Math.max(0L, level.getGameTime() - impaleStartedTick);
        if (localTick >= duration) {
            releaseImpaledPlayer(level, true);
            return false;
        }

        double lift = STAB_IMPALE_LIFT
                * UnknownGreekCombatMath.impaleLiftProgress(localTick, duration);
        Vec3 desired = impaleBase.add(0.0D, lift, 0.0D);
        Vec3 safe = safeImpalePosition(level, target, desired);
        if (safe != null) {
            impaleLastSafe = safe;
            target.teleportTo(safe.x, safe.y, safe.z);
        }
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0F;
        target.hurtMarked = true;
        boss.setCombatAnchor(
                target.position(),
                boss.combatElapsedTicks(impaleStartedTick),
                duration);

        if (localTick > 0 && localTick % STAB_IMPALE_DAMAGE_INTERVAL == 0) {
            target.invulnerableTime = 0;
            boolean damaged = target.hurtServer(
                    level,
                    boss.damageSources().mobAttack(boss),
                    STAB_IMPALE_TICK_DAMAGE);
            showPlayerImpact(level, target, false, damaged);
            level.playSound(null, target.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE,
                    SoundSource.HOSTILE, 0.72F, 0.78F + localTick * 0.006F);
        }
        return true;
    }

    private Vec3 safeImpalePosition(
            ServerLevel level,
            ServerPlayer target,
            Vec3 desired) {
        if (canPlaceImpaledPlayer(level, target, desired)) {
            return desired;
        }
        // A low ceiling shortens the lift; it never converts the grab into suffocation.
        for (int step = 1; step <= 8; step++) {
            Vec3 lowered = desired.add(0.0D, -step * 0.25D, 0.0D);
            if (lowered.y + 1.0E-4D < impaleBase.y) {
                break;
            }
            if (canPlaceImpaledPlayer(level, target, lowered)) {
                return lowered;
            }
        }
        return canPlaceImpaledPlayer(level, target, impaleLastSafe)
                ? impaleLastSafe
                : null;
    }

    private boolean canPlaceImpaledPlayer(
            ServerLevel level,
            ServerPlayer target,
            Vec3 position) {
        Vec3 movement = position.subtract(target.position());
        return level.noCollision(target, target.getBoundingBox().move(movement));
    }

    private void releaseImpaledPlayer(ServerLevel level, boolean launch) {
        if (impaledVictim != null
                && level.getEntity(impaledVictim) instanceof ServerPlayer target
                && target.isAlive()) {
            if (canPlaceImpaledPlayer(level, target, impaleLastSafe)) {
                target.teleportTo(
                        impaleLastSafe.x,
                        impaleLastSafe.y,
                        impaleLastSafe.z);
            }
            target.fallDistance = 0.0F;
            target.setDeltaMovement(launch
                    ? new Vec3(impaleDirection.x * 0.42D, 0.28D, impaleDirection.z * 0.42D)
                    : Vec3.ZERO);
            target.hurtMarked = true;
        }
        clearImpaleTracking();
    }

    private void clearImpaleTracking() {
        impaledVictim = null;
        impaleStartedTick = -1L;
        impaleBase = Vec3.ZERO;
        impaleLastSafe = Vec3.ZERO;
        impaleDirection = Vec3.ZERO;
    }

    private void beginShieldBash(ServerLevel level, ServerPlayer target) {
        boolean ruins = UnknownFightManager.isGreekRuins(boss);
        beginAttack(level, Attack.SHIELD_BASH, UnknownCombatState.SHIELD_BASH);
        attackHit = false;
        closePressureTicks = 0;
        shieldBashCooldown = shieldBashCooldownTicks(ruins);
        boss.setLockedCombatDirection(Vec3.ZERO);
        boss.setCombatAnchor(target.position(), 0.0D, SHIELD_BASH_REACH);
        boss.triggerGreekAnimation("greek_shield_bash");
        level.playSound(null, boss.blockPosition(), SoundEvents.SHIELD_BLOCK.value(),
                SoundSource.HOSTILE, 1.3F, 0.58F);
    }

    private void tickShieldBash(ServerLevel level, ServerPlayer target) {
        boolean ruins = boss.isRuinsCombatVariant();
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        int windup = shieldBashWindupTicks(ruins);
        int lock = shieldBashLockTicks(ruins);
        holdPosition();

        if (elapsed < lock) {
            face(target.position());
        } else if (elapsed == lock) {
            lockDirection(target.position());
            boss.setCombatAnchor(target.position(), 0.0D, SHIELD_BASH_REACH);
            level.playSound(null, boss.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                    SoundSource.HOSTILE, 1.0F, 0.72F);
        }
        Vec3 direction = currentTelegraphDirection(target);
        if (elapsed >= lock) {
            faceDirection(direction);
        }
        if (elapsed < windup) {
            return;
        }

        int activeTick = elapsed - windup;
        if (activeTick < SHIELD_BASH_ACTIVE_TICKS) {
            tryShieldBashHit(level, target, direction);
            return;
        }
        enterRecovery(elapsed, shieldBashRecoveryTicks(ruins));
    }

    private void tryShieldBashHit(ServerLevel level, ServerPlayer target, Vec3 direction) {
        if (attackHit || !target.isAlive() || target.isSpectator()) {
            return;
        }
        Vec3 center = boss.position().add(0.0D, boss.getBbHeight() * 0.52D, 0.0D);
        Vec3 start = center.add(direction.scale(0.45D));
        Vec3 end = center.add(direction.scale(SHIELD_BASH_REACH));
        if (!capsuleHitsPlayer(start, end, target, 1.0D)) {
            return;
        }

        attackHit = true;
        boolean blocked = playerBlocks(target, direction.reverse());
        if (blocked) {
            ItemStack blockingItem = target.getUseItem();
            target.stopUsingItem();
            target.getCooldowns().addCooldown(blockingItem, 30);
        }
        float damage = blocked ? SHIELD_BASH_BLOCKED_DAMAGE : SHIELD_BASH_DAMAGE;
        boolean damaged = target.hurtServer(level, boss.damageSources().mobAttack(boss), damage);
        target.push(direction.x * (blocked ? 1.85D : 1.55D), 0.16D,
                direction.z * (blocked ? 1.85D : 1.55D));
        wallSlamVictim = target.getUUID();
        wallSlamDirection = direction;
        wallSlamTicks = SHIELD_BASH_WALL_WINDOW;
        showPlayerImpact(level, target, blocked, damaged);
        level.playSound(null, target.blockPosition(), SoundEvents.SHIELD_BREAK.value(),
                SoundSource.HOSTILE, 0.85F, blocked ? 0.78F : 0.92F);
    }

    private void tickWallSlam(ServerLevel level) {
        if (wallSlamVictim == null || wallSlamTicks <= 0) {
            clearWallSlam();
            return;
        }
        wallSlamTicks--;
        if (!(level.getEntity(wallSlamVictim) instanceof ServerPlayer target)
                || !target.isAlive()) {
            clearWallSlam();
            return;
        }
        // Give knockback one server step before evaluating the wall in front.
        if (wallSlamTicks >= SHIELD_BASH_WALL_WINDOW - 1
                || !hasWallImmediatelyAhead(level, target, wallSlamDirection)) {
            return;
        }
        boolean damaged = target.hurtServer(
                level,
                boss.damageSources().mobAttack(boss),
                SHIELD_BASH_WALL_DAMAGE);
        boss.showCombatFx(
                UnknownEntity.COMBAT_FX_WALL_SLAM,
                target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D),
                level.getGameTime());
        level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.HOSTILE, damaged ? 1.15F : 0.8F, 0.72F);
        clearWallSlam();
    }

    public static boolean hasWallImmediatelyAhead(
            ServerLevel level,
            ServerPlayer target,
            Vec3 direction) {
        AABB box = target.getBoundingBox();
        AABB body = new AABB(
                box.minX + 0.08D,
                box.minY + 0.12D,
                box.minZ + 0.08D,
                box.maxX - 0.08D,
                box.maxY - 0.12D,
                box.maxZ - 0.08D);
        return !level.noCollision(target, body.move(direction.scale(0.34D)));
    }

    private void clearWallSlam() {
        wallSlamVictim = null;
        wallSlamTicks = 0;
        wallSlamDirection = Vec3.ZERO;
    }

    private void beginCharge(ServerLevel level, ServerPlayer target) {
        beginAttack(level, Attack.CHARGE, UnknownCombatState.CHARGE);
        attackHit = false;
        boss.setLockedCombatDirection(Vec3.ZERO);
        boss.triggerGreekAnimation("greek_charge");
        chargeCooldown = chargeCooldownTicks(boss.isRuinsCombatVariant());
        level.playSound(null, boss.blockPosition(), SoundEvents.SHIELD_BLOCK.value(),
                SoundSource.HOSTILE, 1.25F, 0.62F);
        LOGGER.debug("Unknown begins Greek charge at range {} (ruins={})",
                String.format(Locale.ROOT, "%.2f", boss.distanceTo(target)),
                boss.isRuinsCombatVariant());
    }

    private void tickCharge(ServerLevel level, ServerPlayer target) {
        boolean ruins = boss.isRuinsCombatVariant();
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        int windup = chargeWindupTicks(ruins);
        int lock = chargeLockTicks(ruins);
        int activeTicks = chargeActiveTicks(ruins);
        boss.getNavigation().stop();

        if (elapsed < lock) {
            holdPosition();
            face(target.position());
            return;
        }
        if (elapsed == lock) {
            Vec3 predicted = UnknownGreekCombatMath.predictHorizontal(
                    target.position(), target.getDeltaMovement(), 10, 4.0D);
            lockDirection(predicted);
            level.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(),
                    SoundSource.HOSTILE, 0.75F, 1.45F);
        }
        Vec3 direction = currentTelegraphDirection(target);
        faceDirection(direction);
        if (elapsed < windup) {
            holdPosition();
            return;
        }

        int activeTick = elapsed - windup;
        if (activeTick >= activeTicks) {
            // Missed charge: resume immediately. Connected hits keep recovery; walls keep crash stun.
            if (attackHit) {
                enterRecovery(elapsed, chargeRecoveryTicks(ruins));
            } else {
                completeAttack(level);
            }
            return;
        }

        double speed = chargeSpeed(ruins);
        Vec3 step = direction.scale(speed);
        if (!canOccupyChargeStep(level, step)) {
            beginCrashStun(level);
            return;
        }
        Vec3 previous = boss.position();
        boss.move(MoverType.SELF, step);
        Vec3 current = boss.position();
        if (horizontalDistance(previous, current) < speed * 0.55D || boss.horizontalCollision) {
            beginCrashStun(level);
            return;
        }
        tryChargeHit(level, target, direction, previous, current);
        if (attackHit) {
            enterRecovery(elapsed, chargeRecoveryTicks(ruins));
        }
    }

    private void beginJavelin(ServerLevel level, ServerPlayer target) {
        beginAttack(level, Attack.JAVELIN, UnknownCombatState.JAVELIN);
        attackHit = false;
        lockJavelinTarget(target);
        boss.triggerGreekAnimation("greek_javelin");
        javelinCooldown = javelinCooldownTicks(boss.isRuinsCombatVariant());
        level.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_RETURN,
                SoundSource.HOSTILE, 1.05F, 1.34F);
        LOGGER.debug("Unknown begins spectral javelin at range {} (ruins={})",
                String.format(Locale.ROOT, "%.2f", boss.distanceTo(target)),
                boss.isRuinsCombatVariant());
    }

    private void tickJavelin(ServerLevel level, ServerPlayer target) {
        boolean ruins = boss.isRuinsCombatVariant();
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        int lock = javelinLockTicks(ruins);
        int warning = javelinWarningTicks(ruins);
        holdPosition();

        if (elapsed <= lock) {
            lockJavelinTarget(target);
        } else {
            faceDirection(boss.getLockedCombatDirection());
        }

        if (elapsed == lock) {
            level.playSound(null, BlockPos.containing(boss.getCombatAnchor()),
                    SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 0.72F, 1.72F);
        }
        if (elapsed == warning) {
            applyJavelinImpact(level, target);
            level.playSound(null, BlockPos.containing(boss.getCombatAnchor()),
                    SoundEvents.TRIDENT_HIT_GROUND, SoundSource.HOSTILE, 1.3F, 0.72F);
        }
        // Preserve four impact frames before entering the generic recovery state.
        if (elapsed >= warning + 4) {
            enterRecovery(elapsed, javelinRecoveryTicks(ruins));
        }
    }

    private void lockJavelinTarget(ServerPlayer target) {
        Vec3 anchor = target.position();
        Vec3 direction = UnknownGreekCombatMath.horizontalDirection(
                boss.position(), anchor, boss.getLookAngle());
        boss.setLockedCombatDirection(direction);
        boss.setCombatAnchor(anchor, 0.0D, 0.0D);
        face(anchor);
    }

    private void applyJavelinImpact(ServerLevel level, ServerPlayer target) {
        if (attackHit || !target.isAlive() || target.isSpectator()) {
            return;
        }
        attackHit = true;
        Vec3 anchor = boss.getCombatAnchor();
        if (horizontalDistance(anchor, target.position()) > JAVELIN_IMPACT_RADIUS
                || Math.abs(target.getY() - anchor.y) > 3.0D) {
            return;
        }

        Vec3 attackDirection = boss.getLockedCombatDirection();
        boolean blocked = playerBlocks(target, attackDirection.reverse());
        if (blocked) {
            target.stopUsingItem();
        }
        float damage = blocked ? JAVELIN_BLOCKED_DAMAGE : JAVELIN_DAMAGE;
        boolean damaged = target.hurtServer(level, boss.damageSources().mobAttack(boss), damage);
        Vec3 push = UnknownGreekCombatMath.horizontalDirection(
                anchor, target.position(), attackDirection);
        target.push(push.x * (blocked ? 1.25D : 0.9D), blocked ? 0.48D : 0.36D,
                push.z * (blocked ? 1.25D : 0.9D));
        showPlayerImpact(level, target, blocked, damaged);
    }

    private void beginSpearEruption(ServerLevel level, ServerPlayer target) {
        boolean ruins = UnknownFightManager.isGreekRuins(boss);
        beginAttack(level, Attack.SPEAR_ERUPTION, UnknownCombatState.SPEAR_ERUPTION);
        attackHit = false;
        spearEruptionPhase = boss.getRandom().nextDouble() * Math.PI * 2.0D;
        lockSpearEruption(target);
        spearEruptionCooldown = spearEruptionCooldownTicks(ruins);
        boss.triggerGreekAnimation("greek_spear_eruption");
        level.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_RETURN,
                SoundSource.HOSTILE, 1.18F, 0.54F);
        level.playSound(null, boss.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.HOSTILE, 0.62F, 1.52F);
        LOGGER.debug("Unknown begins concentric spectral spear eruption ({} spears, ruins={})",
                spearEruptionTotalSpearCount(ruins),
                ruins);
    }

    private void tickSpearEruption(ServerLevel level, ServerPlayer target) {
        boolean ruins = boss.isRuinsCombatVariant();
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        int lock = spearEruptionLockTicks(ruins);
        int windup = spearEruptionWindupTicks(ruins);
        int ringDelay = spearEruptionRingDelayTicks(ruins);
        int ringCount = spearEruptionRingCount(ruins);
        holdPosition();

        if (elapsed < lock) {
            lockSpearEruption(target);
        } else {
            faceDirection(boss.getLockedCombatDirection());
        }
        if (elapsed == lock) {
            level.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(),
                    SoundSource.HOSTILE, 0.52F, 1.58F);
        }
        for (int ring = 0; ring < ringCount; ring++) {
            if (elapsed == windup + ring * ringDelay) {
                applySpearEruptionRing(level, target, ring);
                Vec3 soundPosition = boss.getCombatAnchor().add(
                        boss.getLockedCombatDirection().scale(spearEruptionRingRadius(ring)));
                level.playSound(
                        null,
                        BlockPos.containing(soundPosition),
                        SoundEvents.TRIDENT_HIT_GROUND,
                        SoundSource.HOSTILE,
                        0.94F,
                        0.58F + ring * 0.055F);
            }
        }
        applySpearEruptionSlow(target, elapsed, windup, ringDelay, ringCount);

        int finalRing = windup + (ringCount - 1) * ringDelay;
        if (elapsed >= finalRing + spearEruptionPersistTicks(ruins)) {
            enterRecovery(elapsed, spearEruptionRecoveryTicks(ruins));
        }
    }

    private void applySpearEruptionSlow(
            ServerPlayer target,
            int elapsed,
            int windup,
            int ringDelay,
            int ringCount) {
        if (elapsed < windup || ringCount <= 0) {
            return;
        }
        int emergedRing = Math.clamp((elapsed - windup) / Math.max(1, ringDelay), 0, ringCount - 1);
        Vec3 targetCenter = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        if (!UnknownGreekCombatMath.spearFieldContains(
                boss.getCombatAnchor(),
                targetCenter,
                spearEruptionRingRadius(emergedRing),
                SPEAR_ERUPTION_FIRST_RADIUS,
                SPEAR_ERUPTION_FIELD_MARGIN,
                3.0D)) {
            return;
        }
        target.addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS,
                SPEAR_ERUPTION_SLOW_DURATION,
                SPEAR_ERUPTION_SLOW_AMPLIFIER,
                false,
                false,
                false));
    }

    private void lockSpearEruption(ServerPlayer target) {
        Vec3 direction = UnknownGreekCombatMath.horizontalDirection(
                boss.position(), target.position(), boss.getLookAngle());
        boss.setLockedCombatDirection(direction);
        boss.setCombatAnchor(
                boss.position(),
                spearEruptionPhase,
                spearEruptionRange(boss.isRuinsCombatVariant()));
        face(target.position());
    }

    private void applySpearEruptionRing(
            ServerLevel level,
            ServerPlayer target,
            int ring) {
        if (attackHit || !target.isAlive() || target.isSpectator()) {
            return;
        }
        Vec3 targetCenter = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        if (!UnknownGreekCombatMath.spearRingContains(
                boss.getCombatAnchor(),
                targetCenter,
                spearEruptionRingRadius(ring),
                SPEAR_ERUPTION_HIT_HALF_THICKNESS,
                3.0D)) {
            return;
        }
        attackHit = true;
        boolean damaged = target.hurtServer(
                level,
                boss.damageSources().mobAttack(boss),
                SPEAR_ERUPTION_DAMAGE);
        Vec3 push = UnknownGreekCombatMath.horizontalDirection(
                boss.getCombatAnchor(), target.position(), boss.getLockedCombatDirection());
        target.push(push.x * 0.42D, 0.44D, push.z * 0.42D);
        target.hurtMarked = true;
        showPlayerImpact(level, target, false, damaged);
    }

    private void beginPhalanx(
            ServerLevel level,
            ServerPlayer target,
            PhalanxPlan plan) {
        if (plan == null) {
            attackDelay = 4;
            return;
        }
        beginAttack(level, Attack.PHALANX, UnknownCombatState.PHALANX);
        phalanxPlan = plan;
        phalanxRows.clear();
        boss.setLockedCombatDirection(plan.direction);
        boss.setCombatAnchor(plan.start, plan.gapOffsets.getFirst(), plan.length);
        faceDirection(plan.direction);
        boss.triggerGreekAnimation("greek_phalanx");
        phalanxCooldown = phalanxCooldownTicks(boss.isRuinsCombatVariant());
        level.playSound(null, boss.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON,
                SoundSource.HOSTILE, 1.15F, 0.72F);
        LOGGER.debug("Unknown begins Greek phalanx ({} safe lanes, ruins={})",
                plan.safeLanes.size(), boss.isRuinsCombatVariant());
    }

    private void tickPhalanx(ServerLevel level, ServerPlayer target) {
        boolean ruins = boss.isRuinsCombatVariant();
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        int warning = phalanxWarningTicks(ruins);
        holdPosition();
        faceDirection(boss.getLockedCombatDirection());

        if (elapsed == warning) {
            spawnPhalanxRow(level, 0, phalanxPlan.gapOffsets.getFirst());
            if (ruins) {
                boss.setCombatAnchor(
                        phalanxPlan.start,
                        phalanxPlan.gapOffsets.get(1),
                        phalanxPlan.length);
            }
        }
        if (ruins && elapsed == warning + PHALANX_SECOND_ROW_DELAY) {
            spawnPhalanxRow(level, 1, phalanxPlan.gapOffsets.get(1));
            boss.setCombatAnchor(
                    phalanxPlan.start,
                    phalanxPlan.gapOffsets.get(2),
                    phalanxPlan.length);
        }
        if (ruins && elapsed == warning + PHALANX_THIRD_ROW_DELAY) {
            spawnPhalanxRow(level, 2, phalanxPlan.gapOffsets.get(2));
        }
        tickPhalanxHits(level, target);

        int lastStart = warning + (ruins ? PHALANX_THIRD_ROW_DELAY : 0);
        int travelTicks = (int) Math.ceil(phalanxPlan.length / phalanxSpeed(ruins))
                + PHALANX_PATH_ALLOWANCE_TICKS;
        if (elapsed >= lastStart + travelTicks) {
            enterRecovery(elapsed, phalanxRecoveryTicks(ruins));
        }
    }

    private void spawnPhalanxRow(ServerLevel level, int rowIndex, double gapOffset) {
        double speed = phalanxSpeed(boss.isRuinsCombatVariant());
        int travelTicks = (int) Math.ceil(phalanxPlan.length / speed)
                + PHALANX_PATH_ALLOWANCE_TICKS;
        PhalanxRow row = new PhalanxRow(rowIndex);
        Vec3 side = sideOf(phalanxPlan.direction);
        for (Lane lane : phalanxPlan.safeLanes) {
            if (Math.abs(lane.offset - gapOffset) < PHALANX_GAP_WIDTH * 0.5D) {
                continue;
            }
            SpectralHopliteEntity hoplite = EchoesShowThePast.SPECTRAL_HOPLITE.get().create(
                    level,
                    EntitySpawnReason.TRIGGERED);
            if (hoplite == null) {
                continue;
            }
            Vec3 spawn = phalanxPlan.start.add(side.scale(lane.offset));
            Vec3 destination = spawn.add(phalanxPlan.direction.scale(phalanxPlan.length));
            hoplite.setPos(spawn.x, spawn.y, spawn.z);
            hoplite.configure(
                    phalanxPlan.direction,
                    destination,
                    speed,
                    rowIndex,
                    level.getGameTime() + travelTicks,
                    !phalanxPlan.airborne);
            if (level.addFreshEntity(hoplite)) {
                row.entities.add(hoplite.getUUID());
                // Route availability during the spawn tick must never decide whether
                // the authored row is visible. The entity retries after it settles.
                hoplite.beginMarch();
            } else {
                hoplite.discard();
            }
        }
        phalanxRows.add(row);
        level.playSound(null, BlockPos.containing(phalanxPlan.start), SoundEvents.TRIDENT_RIPTIDE_1.value(),
                SoundSource.HOSTILE, 1.05F, rowIndex == 0 ? 0.72F : 0.82F);
    }

    private void tickPhalanxHits(ServerLevel level, ServerPlayer target) {
        AABB targetBox = target.getBoundingBox().inflate(0.18D);
        for (PhalanxRow row : phalanxRows) {
            if (row.hit) {
                continue;
            }
            for (UUID entityId : row.entities) {
                if (!(level.getEntity(entityId) instanceof SpectralHopliteEntity hoplite)
                        || !hoplite.isAlive()
                        || !hoplite.getBoundingBox().inflate(PHALANX_HIT_INFLATE).intersects(targetBox)) {
                    continue;
                }
                row.hit = true;
                applyPhalanxHit(level, target, phalanxPlan.direction);
                break;
            }
        }
    }

    private void applyPhalanxHit(ServerLevel level, ServerPlayer target, Vec3 direction) {
        boolean blocked = playerBlocks(target, direction.reverse());
        if (blocked) {
            target.stopUsingItem();
        }
        float damage = blocked ? PHALANX_BLOCKED_DAMAGE : PHALANX_DAMAGE;
        boolean damaged = target.hurtServer(level, boss.damageSources().mobAttack(boss), damage);
        target.push(direction.x * 0.9D, 0.16D, direction.z * 0.9D);
        showPlayerImpact(level, target, blocked, damaged);
    }

    private void beginCrashStun(ServerLevel level) {
        boss.beginGreekCombatState(
                UnknownCombatState.CRASH_STUN,
                level.getGameTime(),
                UnknownFightManager.isGreekRuins(boss));
        boss.setDeltaMovement(Vec3.ZERO);
        boss.triggerGreekAnimation("greek_crash");
        level.playSound(null, boss.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.HOSTILE, 1.0F, 0.62F);
    }

    private void tickCrashStun(ServerLevel level) {
        holdPosition();
        if (boss.combatElapsedTicks(level.getGameTime()) >= CRASH_STUN_TICKS) {
            completeAttack(level);
        }
    }

    private void enterRecovery(int elapsed, int duration) {
        boss.continueGreekCombatState(UnknownCombatState.RECOVERY);
        recoveryEndElapsed = elapsed + duration;
        boss.setDeltaMovement(Vec3.ZERO);
    }

    private void tickRecovery(ServerLevel level) {
        holdPosition();
        if (boss.combatElapsedTicks(level.getGameTime()) >= recoveryEndElapsed) {
            completeAttack(level);
        }
    }

    private void completeAttack(ServerLevel level) {
        if (currentAttack == Attack.PHALANX) {
            clearHoplites();
        }
        lastAttack = currentAttack;
        currentAttack = Attack.NONE;
        attackSerial++;
        boss.resetGreekCombat();
        attackDelay = neutralDelayAfterAttack(UnknownFightManager.isGreekRuins(boss));
    }

    private void beginAttack(ServerLevel level, Attack attack, UnknownCombatState state) {
        boss.getNavigation().stop();
        boss.setDeltaMovement(0.0D, boss.getDeltaMovement().y, 0.0D);
        boss.beginGreekCombatState(state, level.getGameTime(), UnknownFightManager.isGreekRuins(boss));
        currentAttack = attack;
    }

    private void tryStabHit(
            ServerLevel level,
            ServerPlayer target,
            Vec3 direction,
            Vec3 bossPosition,
            double reach,
            int cutIndex) {
        if (stabHits[cutIndex] || !target.isAlive() || target.isSpectator()) {
            return;
        }
        double spearY = boss.getY() + boss.getBbHeight() * 0.58D;
        Vec3 start = new Vec3(
                bossPosition.x + direction.x * 0.45D,
                spearY,
                bossPosition.z + direction.z * 0.45D);
        Vec3 end = new Vec3(
                bossPosition.x + direction.x * reach,
                spearY,
                bossPosition.z + direction.z * reach);
        if (!capsuleHitsPlayer(start, end, target, SPEAR_RADIUS)) {
            return;
        }
        stabHits[cutIndex] = true;
        attackHit = true;
        boolean blocked = playerBlocks(target, direction.reverse());
        float damage;
        if (cutIndex == 0) {
            damage = blocked ? STAB_BLOCKED_DAMAGE : STAB_DAMAGE;
        } else {
            damage = blocked ? STAB_FOLLOWUP_BLOCKED_DAMAGE : STAB_FOLLOWUP_DAMAGE;
        }
        boolean damaged = target.hurtServer(level, boss.damageSources().mobAttack(boss), damage);
        showPlayerImpact(level, target, blocked, damaged);
        if (!blocked && damaged) {
            beginImpale(level, target, direction);
        }
    }

    private boolean tryChargeHit(
            ServerLevel level,
            ServerPlayer target,
            Vec3 direction,
            Vec3 previous,
            Vec3 current) {
        if (attackHit || !capsuleHitsPlayer(
                previous.add(0.0D, boss.getBbHeight() * 0.5D, 0.0D),
                current.add(0.0D, boss.getBbHeight() * 0.5D, 0.0D),
                target,
                1.05D)) {
            return false;
        }
        attackHit = true;
        boolean blocked = playerBlocks(target, direction.reverse());
        ItemStack blockingItem = blocked ? target.getUseItem() : ItemStack.EMPTY;
        if (blocked) {
            target.stopUsingItem();
            target.getCooldowns().addCooldown(blockingItem, 40);
        }
        float damage = blocked ? CHARGE_BLOCKED_DAMAGE : CHARGE_DAMAGE;
        boolean damaged = target.hurtServer(level, boss.damageSources().mobAttack(boss), damage);
        target.push(direction.x * (blocked ? 1.7D : 1.15D), 0.22D,
                direction.z * (blocked ? 1.7D : 1.15D));
        showPlayerImpact(level, target, blocked, damaged);
        return true;
    }

    private boolean capsuleHitsPlayer(Vec3 start, Vec3 end, ServerPlayer target, double radius) {
        AABB box = target.getBoundingBox();
        Vec3 center = new Vec3(target.getX(), (box.minY + box.maxY) * 0.5D, target.getZ());
        return UnknownGreekCombatMath.capsuleContains(
                start,
                end,
                center,
                radius,
                target.getBbHeight() * 0.5D + 0.45D);
    }

    private void showPlayerImpact(
            ServerLevel level,
            ServerPlayer target,
            boolean blocked,
            boolean damaged) {
        boss.showCombatFx(
                blocked ? UnknownEntity.COMBAT_FX_PLAYER_BLOCK : UnknownEntity.COMBAT_FX_HIT,
                target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D),
                level.getGameTime());
        level.playSound(null, target.blockPosition(),
                blocked ? SoundEvents.SHIELD_BLOCK.value() : SoundEvents.TRIDENT_HIT,
                SoundSource.HOSTILE,
                blocked ? 1.25F : 1.05F,
                damaged ? 0.82F : 0.94F);
    }

    private boolean playerBlocks(ServerPlayer target, Vec3 incomingFromTarget) {
        return target.isBlocking()
                && UnknownGreekCombatMath.isInsideFrontArc(
                        target.getLookAngle(), incomingFromTarget, 180.0D);
    }

    private PhalanxPlan findPhalanxPlan(
            ServerLevel level,
            ServerPlayer target,
            boolean airborne) {
        Vec3 desired = UnknownGreekCombatMath.snapToCardinal(UnknownGreekCombatMath.horizontalDirection(
                boss.position(), target.position(), boss.getLookAngle()));
        Vec3 start = target.position().subtract(desired.scale(PHALANX_CORRIDOR_LENGTH * 0.5D));
        List<Lane> lanes = new ArrayList<>(PHALANX_LANES.length);
        for (double laneOffset : PHALANX_LANES) {
            lanes.add(new Lane(laneOffset));
        }
        boolean ruins = UnknownFightManager.isGreekRuins(boss);
        int rowCount = phalanxRowCount(ruins);
        int warning = phalanxWarningTicks(ruins);
        int betweenRows = PHALANX_SECOND_ROW_DELAY;
        for (int attempt = 0; attempt < 24; attempt++) {
            List<Double> gaps = new ArrayList<>(rowCount);
            double first = UnknownGreekCombatMath.initialPhalanxGap(boss.getRandom().nextInt());
            if (!UnknownGreekCombatMath.gapReachable(
                    0.0D, first, PHALANX_SPRINT_PER_TICK, warning)) {
                continue;
            }
            gaps.add(first);
            boolean reachable = true;
            double previous = first;
            double avoid = Double.NaN;
            for (int row = 1; row < rowCount; row++) {
                double next = UnknownGreekCombatMath.nextPhalanxGap(
                        boss.getRandom().nextInt(),
                        previous,
                        avoid);
                if (!UnknownGreekCombatMath.gapReachable(
                        previous, next, PHALANX_SPRINT_PER_TICK, betweenRows)) {
                    reachable = false;
                    break;
                }
                gaps.add(next);
                avoid = previous;
                previous = next;
            }
            if (!reachable || gaps.size() != rowCount) {
                continue;
            }
            return new PhalanxPlan(
                    start,
                    desired,
                    PHALANX_CORRIDOR_LENGTH,
                    List.copyOf(lanes),
                    List.copyOf(gaps),
                    airborne);
        }
        // Guaranteed fair fallback: same street every row.
        double safe = UnknownGreekCombatMath.initialPhalanxGap(boss.getRandom().nextInt());
        List<Double> gaps = new ArrayList<>(rowCount);
        for (int row = 0; row < rowCount; row++) {
            gaps.add(safe);
        }
        return new PhalanxPlan(
                start,
                desired,
                PHALANX_CORRIDOR_LENGTH,
                List.copyOf(lanes),
                List.copyOf(gaps),
                airborne);
    }

    private boolean isElevatedUnreachable(ServerPlayer target) {
        double verticalDifference = target.getY() - boss.getY();
        if (verticalDifference <= UnknownGreekCombatMath.UNREACHABLE_HEIGHT_THRESHOLD) {
            return false;
        }
        Path path = boss.getNavigation().createPath(target, 0);
        return UnknownGreekCombatMath.isElevatedUnreachable(
                verticalDifference,
                path != null && path.canReach());
    }

    private boolean isChargePathClear(ServerLevel level, Vec3 direction, double distance) {
        int steps = Math.max(1, (int) Math.ceil(distance / 0.5D));
        for (int step = 1; step <= steps; step++) {
            Vec3 displacement = direction.scale(distance * step / steps);
            AABB box = boss.getBoundingBox().move(displacement);
            BlockPos support = BlockPos.containing(
                    boss.getX() + displacement.x,
                    box.minY - 0.08D,
                    boss.getZ() + displacement.z);
            if (!level.noCollision(boss, box)
                    || !level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
                return false;
            }
        }
        return true;
    }

    private boolean canOccupyChargeStep(ServerLevel level, Vec3 step) {
        AABB destination = boss.getBoundingBox().move(step);
        BlockPos support = BlockPos.containing(
                boss.getX() + step.x,
                destination.minY - 0.08D,
                boss.getZ() + step.z);
        return level.noCollision(boss, destination)
                && level.getBlockState(support).isFaceSturdy(level, support, Direction.UP);
    }

    private boolean beginSafeBackstep(ServerLevel level, ServerPlayer target) {
        Vec3 away = UnknownGreekCombatMath.horizontalDirection(
                target.position(), boss.position(), boss.getLookAngle().reverse());
        Vec3 displacement = away.scale(BACKSTEP_DISTANCE);
        AABB destination = boss.getBoundingBox().move(displacement);
        BlockPos support = BlockPos.containing(
                boss.getX() + displacement.x,
                destination.minY - 0.08D,
                boss.getZ() + displacement.z);
        if (!level.noCollision(boss, destination)
                || !level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
            backstepCooldown = 14;
            return false;
        }
        backstepDirection = away;
        backstepTicks = BACKSTEP_TICKS;
        backstepCooldown = 40;
        boss.getNavigation().stop();
        boss.triggerGreekAnimation("greek_backstep");
        level.playSound(null, boss.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(),
                SoundSource.HOSTILE, 0.75F, 1.18F);
        return true;
    }

    private void orbit(ServerPlayer target, double distance) {
        Vec3 radial = UnknownGreekCombatMath.horizontalDirection(
                target.position(), boss.position(), boss.getLookAngle());
        Vec3 tangent = clockwise
                ? new Vec3(-radial.z, 0.0D, radial.x)
                : new Vec3(radial.z, 0.0D, -radial.x);
        double radius = distance < 5.0D ? 6.2D : distance > 7.0D ? 6.4D : 6.0D;
        Vec3 orbitPoint = target.position().add(radial.scale(radius)).add(tangent.scale(1.7D));
        if (boss.getNavigation().isDone() || boss.tickCount % 10 == 0) {
            boss.getNavigation().moveTo(
                    orbitPoint.x,
                    target.getY(),
                    orbitPoint.z,
                    distance < 5.0D ? 1.05D : 0.82D);
        }
    }

    private void holdPosition() {
        boss.getNavigation().stop();
        boss.setDeltaMovement(0.0D, boss.getDeltaMovement().y, 0.0D);
    }

    private void lockDirection(Vec3 point) {
        Vec3 locked = UnknownGreekCombatMath.horizontalDirection(
                boss.position(), point, boss.getLookAngle());
        boss.setLockedCombatDirection(locked);
        faceDirection(locked);
    }

    private Vec3 currentTelegraphDirection(ServerPlayer target) {
        Vec3 locked = boss.getLockedCombatDirection();
        return locked.lengthSqr() > 1.0E-6D
                ? locked
                : UnknownGreekCombatMath.horizontalDirection(
                        boss.position(), target.position(), boss.getLookAngle());
    }

    private void face(Vec3 point) {
        faceDirection(UnknownGreekCombatMath.horizontalDirection(
                boss.position(), point, boss.getLookAngle()));
        boss.getLookControl().setLookAt(point.x, point.y + 1.0D, point.z, 35.0F, 30.0F);
    }

    private void faceDirection(Vec3 direction) {
        if (direction.horizontalDistanceSqr() <= 1.0E-8D) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        boss.setYRot(yaw);
        boss.setYHeadRot(yaw);
        boss.yBodyRot = yaw;
    }

    private void clearHoplites() {
        if (boss.level() instanceof ServerLevel level) {
            for (PhalanxRow row : phalanxRows) {
                for (UUID entityId : row.entities) {
                    if (level.getEntity(entityId) instanceof SpectralHopliteEntity hoplite) {
                        hoplite.discard();
                    }
                }
            }
        }
        phalanxRows.clear();
        phalanxPlan = null;
    }

    public static boolean isGuardActive(
            UnknownCombatState state,
            int elapsed,
            boolean ruins) {
        return switch (state) {
            case NEUTRAL, CHARGE, PHALANX, SPEAR_ERUPTION, SHIELD_BASH -> true;
            case STAB -> elapsed < stabWindupTicks(ruins);
            case JAVELIN -> elapsed < javelinWarningTicks(ruins);
            default -> false;
        };
    }

    public static int stabWindupTicks(boolean ruins) {
        return ruins ? 14 : STAB_WINDUP_TICKS;
    }

    public static int stabLockTick(boolean ruins) {
        return ruins ? 10 : STAB_LOCK_TICK;
    }

    public static int stabActiveTicks(boolean ruins) {
        return ruins ? 6 : STAB_ACTIVE_TICKS;
    }

    public static int stabBetweenCutsTicks(boolean ruins) {
        return ruins ? STAB_BETWEEN_CUTS_RUINS : STAB_BETWEEN_CUTS_PAST;
    }

    public static int stabCutCount(boolean ruins) {
        return ruins ? 3 : 2;
    }

    public static int stabCutStartTick(boolean ruins, int cutIndex) {
        return stabWindupTicks(ruins)
                + Math.max(0, cutIndex) * (stabActiveTicks(ruins) + stabBetweenCutsTicks(ruins));
    }

    public static int stabRecoveryTicks(boolean ruins) {
        return ruins ? 18 : STAB_RECOVERY_TICKS;
    }

    public static int stabImpaleTicks(boolean ruins) {
        return ruins ? STAB_IMPALE_TICKS_RUINS : STAB_IMPALE_TICKS_PAST;
    }

    public static int stabSequenceTicks(boolean ruins) {
        int cuts = stabCutCount(ruins);
        return stabCutStartTick(ruins, cuts - 1)
                + stabActiveTicks(ruins)
                + stabRecoveryTicks(ruins);
    }

    public static double stabLungeDistance(boolean ruins) {
        return STAB_LUNGE_DISTANCE;
    }

    public static int chargeWindupTicks(boolean ruins) {
        return ruins ? CHARGE_WINDUP_RUINS : CHARGE_WINDUP_PAST;
    }

    public static int chargeLockTicks(boolean ruins) {
        return ruins ? CHARGE_LOCK_RUINS : CHARGE_LOCK_PAST;
    }

    public static int chargeActiveTicks(boolean ruins) {
        return ruins ? CHARGE_ACTIVE_RUINS : CHARGE_ACTIVE_PAST;
    }

    public static int chargeRecoveryTicks(boolean ruins) {
        return ruins ? CHARGE_RECOVERY_RUINS : CHARGE_RECOVERY_PAST;
    }

    public static double chargeSpeed(boolean ruins) {
        return ruins ? CHARGE_SPEED_RUINS : CHARGE_SPEED_PAST;
    }

    public static int phalanxWarningTicks(boolean ruins) {
        return ruins ? PHALANX_WARNING_RUINS : PHALANX_WARNING_PAST;
    }

    public static int phalanxRecoveryTicks(boolean ruins) {
        return ruins ? PHALANX_RECOVERY_RUINS : PHALANX_RECOVERY_PAST;
    }

    public static double phalanxSpeed(boolean ruins) {
        return ruins ? PHALANX_SPEED_RUINS : PHALANX_SPEED_PAST;
    }

    public static int javelinWarningTicks(boolean ruins) {
        return ruins ? JAVELIN_WARNING_RUINS : JAVELIN_WARNING_PAST;
    }

    public static int javelinLockTicks(boolean ruins) {
        return ruins ? JAVELIN_LOCK_RUINS : JAVELIN_LOCK_PAST;
    }

    public static int javelinRecoveryTicks(boolean ruins) {
        return ruins ? JAVELIN_RECOVERY_RUINS : JAVELIN_RECOVERY_PAST;
    }

    public static int javelinCooldownTicks(boolean ruins) {
        return ruins ? JAVELIN_COOLDOWN_RUINS : JAVELIN_COOLDOWN_PAST;
    }

    public static int spearEruptionWindupTicks(boolean ruins) {
        return ruins ? SPEAR_ERUPTION_WINDUP_RUINS : SPEAR_ERUPTION_WINDUP_PAST;
    }

    public static int spearEruptionLockTicks(boolean ruins) {
        return ruins ? SPEAR_ERUPTION_LOCK_RUINS : SPEAR_ERUPTION_LOCK_PAST;
    }

    public static int spearEruptionRingDelayTicks(boolean ruins) {
        return ruins ? SPEAR_ERUPTION_RING_DELAY_RUINS : SPEAR_ERUPTION_RING_DELAY_PAST;
    }

    public static int spearEruptionRecoveryTicks(boolean ruins) {
        return ruins ? SPEAR_ERUPTION_RECOVERY_RUINS : SPEAR_ERUPTION_RECOVERY_PAST;
    }

    public static int spearEruptionCooldownTicks(boolean ruins) {
        return ruins ? SPEAR_ERUPTION_COOLDOWN_RUINS : SPEAR_ERUPTION_COOLDOWN_PAST;
    }

    public static int spearEruptionRingCount(boolean ruins) {
        return ruins ? SPEAR_ERUPTION_RINGS_RUINS : SPEAR_ERUPTION_RINGS_PAST;
    }

    public static int spearEruptionPersistTicks(boolean ruins) {
        return ruins ? SPEAR_ERUPTION_PERSIST_RUINS : SPEAR_ERUPTION_PERSIST_PAST;
    }

    public static int spearEruptionSpearCount(int ring) {
        return SPEAR_ERUPTION_BASE_SPEARS
                + Math.max(0, ring) * SPEAR_ERUPTION_SPEARS_PER_RING;
    }

    public static int spearEruptionTotalSpearCount(boolean ruins) {
        int total = 0;
        for (int ring = 0; ring < spearEruptionRingCount(ruins); ring++) {
            total += spearEruptionSpearCount(ring);
        }
        return total;
    }

    public static double spearEruptionRingRadius(int ring) {
        return SPEAR_ERUPTION_FIRST_RADIUS
                + Math.max(0, ring) * SPEAR_ERUPTION_RING_SPACING;
    }

    public static double spearEruptionRange(boolean ruins) {
        return spearEruptionRingRadius(spearEruptionRingCount(ruins) - 1);
    }

    public static int shieldBashWindupTicks(boolean ruins) {
        return ruins ? SHIELD_BASH_WINDUP_RUINS : SHIELD_BASH_WINDUP_PAST;
    }

    public static int shieldBashLockTicks(boolean ruins) {
        return ruins ? SHIELD_BASH_LOCK_RUINS : SHIELD_BASH_LOCK_PAST;
    }

    public static int shieldBashRecoveryTicks(boolean ruins) {
        return ruins ? SHIELD_BASH_RECOVERY_RUINS : SHIELD_BASH_RECOVERY_PAST;
    }

    public static int shieldBashCooldownTicks(boolean ruins) {
        return ruins ? SHIELD_BASH_COOLDOWN_RUINS : SHIELD_BASH_COOLDOWN_PAST;
    }

    public static int shieldBashPressureTicks(boolean ruins) {
        return ruins ? SHIELD_BASH_PRESSURE_RUINS : SHIELD_BASH_PRESSURE_PAST;
    }

    public static boolean isElevationRangedState(UnknownCombatState state) {
        return state == UnknownCombatState.JAVELIN || state == UnknownCombatState.PHALANX;
    }

    public static int attackPatternLength(boolean ruins) {
        return ruins ? RUINS_ATTACK_PATTERN.length : PAST_ATTACK_PATTERN.length;
    }

    public static int phalanxRowCount(boolean ruins) {
        return ruins ? 3 : 1;
    }

    public static int initialAttackDelay(boolean ruins) {
        return ruins ? INITIAL_ATTACK_DELAY_RUINS : INITIAL_ATTACK_DELAY_PAST;
    }

    public static int chargeCooldownTicks(boolean ruins) {
        return ruins ? CHARGE_COOLDOWN_RUINS : CHARGE_COOLDOWN_PAST;
    }

    public static int phalanxCooldownTicks(boolean ruins) {
        return ruins ? PHALANX_COOLDOWN_RUINS : PHALANX_COOLDOWN_PAST;
    }

    public static int neutralDelayAfterAttack(boolean ruins) {
        return ruins ? NEUTRAL_DELAY_RUINS : NEUTRAL_DELAY_PAST;
    }

    public static UnknownCombatState stateAtSequenceTick(int elapsed) {
        int activeEnd = stabCutStartTick(false, stabCutCount(false) - 1) + stabActiveTicks(false);
        if (elapsed < activeEnd) {
            return UnknownCombatState.STAB;
        }
        if (elapsed < stabSequenceTicks(false)) {
            return UnknownCombatState.RECOVERY;
        }
        return UnknownCombatState.NEUTRAL;
    }

    private static Vec3 sideOf(Vec3 direction) {
        return new Vec3(-direction.z, 0.0D, direction.x);
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double smoothStep(double value) {
        double clamped = Math.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private enum Attack {
        NONE,
        STAB,
        CHARGE,
        PHALANX,
        JAVELIN,
        SHIELD_BASH,
        SPEAR_ERUPTION
    }

    private record AttackChoice(Attack attack, PhalanxPlan phalanxPlan) {
    }

    private record Lane(double offset) {
    }

    private record PhalanxPlan(
            Vec3 start,
            Vec3 direction,
            double length,
            List<Lane> safeLanes,
            List<Double> gapOffsets,
            boolean airborne) {
    }

    private static final class PhalanxRow {
        private final int index;
        private final List<UUID> entities = new ArrayList<>();
        private boolean hit;

        private PhalanxRow(int index) {
            this.index = index;
        }
    }
}
