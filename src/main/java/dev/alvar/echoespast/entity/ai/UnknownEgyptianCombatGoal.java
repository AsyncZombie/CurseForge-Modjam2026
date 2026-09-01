package dev.alvar.echoespast.entity.ai;

import dev.alvar.echoespast.entity.UnknownEntity;
import dev.alvar.echoespast.entity.combat.UnknownBossMovementSafety;
import dev.alvar.echoespast.entity.combat.UnknownCombatState;
import dev.alvar.echoespast.entity.combat.UnknownEgyptianCombatMath;
import dev.alvar.echoespast.entity.combat.UnknownGreekCombatMath;
import dev.alvar.echoespast.entity.combat.TemporaryDuatWall;
import dev.alvar.echoespast.server.UnknownFightManager;
import java.util.Arrays;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Authoritative Egyptian footwork, real Duat architecture and Sekhmet pressure. */
public final class UnknownEgyptianCombatGoal extends Goal {
    public static final float EGYPT_RUINS_ANIMATION_SPEED = 1.15F;
    public static final int KHOPESH_ANIMATION_TICKS = 72;
    private static final int[] KHOPESH_CUT_STARTS = {18, 29, 39, 49, 59};
    private static final int[] KHOPESH_CUT_ENDS = {23, 34, 44, 54, 64};
    public static final int WINDUP_PAST = KHOPESH_CUT_STARTS[0];
    public static final int WINDUP_RUINS = scaledAnimationTick(true, WINDUP_PAST);
    public static final int LOCK_PAST = 14;
    public static final int LOCK_RUINS = scaledAnimationTick(true, LOCK_PAST);
    public static final int CUT_ACTIVE_PAST = 5;
    public static final int CUT_ACTIVE_RUINS = scaledDuration(true, CUT_ACTIVE_PAST);
    public static final int BETWEEN_CUTS_PAST = 5;
    public static final int BETWEEN_CUTS_RUINS = scaledDuration(true, BETWEEN_CUTS_PAST);
    public static final int RECOVERY_PAST = 8;
    public static final int RECOVERY_RUINS = 7;
    public static final double FIRST_START_ANGLE = -105.0D;
    public static final double FIRST_END_ANGLE = 72.0D;
    public static final double SECOND_START_ANGLE = 92.0D;
    public static final double SECOND_END_ANGLE = -112.0D;
    public static final double INNER_RADIUS = 0.35D;
    public static final double OUTER_RADIUS = 5.35D;
    public static final float FIRST_DAMAGE = 14.0F;
    public static final float SECOND_DAMAGE = 16.0F;
    private static final int GATE_FIRST_PANEL_AUTHORED_TICK = 28;
    private static final int GATE_PANEL_INTERVAL_AUTHORED_TICKS = 5;
    public static final int THRESHOLD_ACTIVE_PAST = 110;
    public static final int THRESHOLD_ACTIVE_RUINS = 96;
    public static final int GATE_COOLDOWN_PAST = 84;
    public static final int GATE_COOLDOWN_RUINS = 62;
    public static final int WALL_WIDTH = 9;
    public static final int WALL_HEIGHT_BLOCKS = 4;
    public static final int WALL_BUILD_TICKS = WALL_HEIGHT_BLOCKS;
    /** @deprecated Renderer compatibility while rejected field art is deleted. */
    @Deprecated public static final int GATE_COLLISION_RISE_TICKS = WALL_BUILD_TICKS;
    public static final int MAX_ACTIVE_GATES_PAST = 1;
    public static final int MAX_ACTIVE_GATES_RUINS = 1;
    public static final int GATE_PANEL_COUNT = 1;
    public static final double SEAL_PANEL_HALF_WIDTH = UnknownEgyptianCombatMath.DUAT_REAR_HALF_SPAN;
    public static final double SEAL_PANEL_HALF_LENGTH = UnknownEgyptianCombatMath.WALL_HALF_THICK;
    public static final double THRESHOLD_ALONG = UnknownEgyptianCombatMath.DUAT_REAR_DISTANCE;
    public static final double THRESHOLD_HEIGHT = UnknownEgyptianCombatMath.WALL_HEIGHT;
    public static final int JUDGMENT_WARNING_PAST = 34;
    public static final int JUDGMENT_WARNING_RUINS = 29;
    public static final int JUDGMENT_LOCK_PAST = 18;
    public static final int JUDGMENT_LOCK_RUINS = 15;
    public static final int JUDGMENT_ACTIVE_PAST = 8;
    public static final int JUDGMENT_ACTIVE_RUINS = 7;
    public static final int JUDGMENT_RECOVERY_PAST = 12;
    public static final int JUDGMENT_RECOVERY_RUINS = 10;
    public static final int JUDGMENT_COOLDOWN_PAST = 78;
    public static final int JUDGMENT_COOLDOWN_RUINS = 62;
    public static final double JUDGMENT_LENGTH = 28.0D;
    public static final double JUDGMENT_WAVE_START = 0.72D;
    public static final double JUDGMENT_HALF_WIDTH_PAST = 1.45D;
    public static final double JUDGMENT_HALF_WIDTH_RUINS = 1.75D;
    public static final double JUDGMENT_VERTICAL_REACH = 16.0D;
    public static final double JUDGMENT_MAX_VISUAL_SURFACE_STEP = 1.25D;
    public static final float JUDGMENT_DAMAGE = 44.0F;
    public static final float JUDGMENT_BLOCK_MULTIPLIER = 0.35F;
    public static final int HUNT_BEATS_PAST = 2;
    public static final int HUNT_MAX_BEATS_PAST = 4;
    public static final int HUNT_BEATS_RUINS = 4;
    public static final int HUNT_MAX_BEATS_RUINS = 6;
    public static final int HUNT_STRIDE_PAST = 19;
    public static final int HUNT_STRIDE_RUINS = 17;
    public static final int HUNT_LOCK_PAST = 4;
    public static final int HUNT_LOCK_RUINS = 3;
    public static final int HUNT_WINDUP_PAST = 7;
    public static final int HUNT_WINDUP_RUINS = 5;
    public static final int HUNT_DASH_PAST = 6;
    public static final int HUNT_DASH_RUINS = 5;
    public static final int HUNT_ACTIVE_TICKS = 4;
    public static final int HUNT_RECOVERY_PAST = 8;
    public static final int HUNT_RECOVERY_RUINS = 7;
    public static final int HUNT_COOLDOWN_PAST = 46;
    public static final int HUNT_COOLDOWN_RUINS = 34;
    public static final int HUNT_HIT_GRACE_TICKS = 9;
    public static final double HUNT_FLANK_DISTANCE = 1.6D;
    public static final double HUNT_DASH_STEP_PAST = 0.72D;
    public static final double HUNT_DASH_STEP_RUINS = 0.86D;
    public static final double HUNT_MAX_DASH_STEP_PAST = 1.65D;
    public static final double HUNT_MAX_DASH_STEP_RUINS = 1.9D;
    public static final double HUNT_SLASH_RADIUS = 4.4D;
    public static final float HUNT_DAMAGE_PAST = 12.0F;
    public static final float HUNT_DAMAGE_RUINS = 14.0F;
    public static final int SHIELD_BREAK_TICKS = 100;
    // Kept only for dormant legacy geometry helpers that are no longer dispatched.
    @Deprecated public static final double HORUS_CURVE = 0.04D;
    @Deprecated public static final double HORUS_LIFT = 0.08D;
    @Deprecated public static final int MAAT_ACTIVE_TICKS = 5;

    private static final double ATTACK_RANGE = 6.4D;
    private static final double SEAL_MAX_RANGE = 16.0D;
    private static final double JUDGMENT_MAX_RANGE = 28.0D;
    private static final double HUNT_MAX_RANGE = 12.5D;
    private static final double COMBO_IDEAL_RANGE = 3.35D;
    private static final double CLOSE_PRESSURE_RANGE = 3.0D;
    private static final double ORBIT_RADIUS = 4.5D;

    private final UnknownEntity boss;
    private final boolean clockwise;
    private int attackDelay;
    private int recoveryEndElapsed;
    private int gateCooldown;
    private int judgmentCooldown;
    private int huntCooldown;
    private final boolean[] comboHits = new boolean[KHOPESH_CUT_STARTS.length];
    private TemporaryDuatWall activeWall;
    private TemporaryDuatWall pendingWall;
    private int comboCutCount;
    private int shieldBlocksInCombo;
    private int huntBeat;
    private int huntPlannedBeats;
    private boolean huntBeatHit;
    private int shieldBlocksInHunt;
    private long lastHuntHitTick = -1L;
    private boolean judgmentHit;
    private int closePressureTicks;
    private boolean firstAttack;
    private boolean forceHunt;
    private boolean forceCombo;
    private Attack currentAttack = Attack.NONE;
    private Attack lastAttack = Attack.NONE;
    private Attack previousAttack = Attack.NONE;

    public UnknownEgyptianCombatGoal(UnknownEntity boss) {
        this.boss = boss;
        this.clockwise = (boss.getUUID().hashCode() & 1) == 0;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return UnknownFightManager.egyptianCombatTarget(boss) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        if (activeWall != null && boss.level() instanceof ServerLevel level) {
            activeWall.restore(level);
        }
        attackDelay = initialAttackDelayTicks(UnknownFightManager.isEgyptianRuins(boss));
        recoveryEndElapsed = 0;
        gateCooldown = 0;
        judgmentCooldown = 0;
        huntCooldown = 0;
        Arrays.fill(comboHits, false);
        activeWall = null;
        pendingWall = null;
        huntBeat = 0;
        huntPlannedBeats = huntMinimumBeatCount(UnknownFightManager.isEgyptianRuins(boss));
        huntBeatHit = false;
        shieldBlocksInHunt = 0;
        lastHuntHitTick = -1L;
        judgmentHit = false;
        closePressureTicks = 0;
        firstAttack = true;
        currentAttack = Attack.NONE;
        lastAttack = Attack.NONE;
        previousAttack = Attack.NONE;
        forceHunt = false;
        forceCombo = false;
        boss.resetGreekCombat();
    }

    @Override
    public void stop() {
        boss.getNavigation().stop();
        boss.setTarget(null);
        if (boss.level() instanceof ServerLevel level) {
            restoreWall(level);
        }
        boss.resetGreekCombat();
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
        ServerPlayer target = UnknownFightManager.egyptianCombatTarget(boss);
        if (target == null) {
            stop();
            return;
        }
        gateCooldown = Math.max(0, gateCooldown - 1);
        judgmentCooldown = Math.max(0, judgmentCooldown - 1);
        huntCooldown = Math.max(0, huntCooldown - 1);
        tickPersistentWall(level, target);
        boss.setTarget(target);
        // Cactus is arena dressing, never an Egyptian-phase prison. Clearing it
        // before hazard escape also prevents contact from cancelling an attack.
        UnknownBossMovementSafety.destroyContactCacti(level, boss);
        if (escapeImmediateHazard(level)) {
            return;
        }
        switch (boss.getCombatState()) {
            case NEUTRAL -> tickNeutral(target);
            case KHOPESH_COMBO -> tickCombo(level, target);
            case DUAT_GATE -> tickDuatGate(level, target);
            case SOLAR_JUDGMENT -> tickSolarJudgment(level, target);
            case SEKHMET_HUNT -> tickSekhmetHunt(level, target);
            case RECOVERY -> tickRecovery(level);
            default -> boss.resetGreekCombat();
        }
    }

    private void tickNeutral(ServerPlayer target) {
        boolean ruins = UnknownFightManager.isEgyptianRuins(boss);
        double distance = horizontalDistance(boss.position(), target.position());
        face(target.position());
        closePressureTicks = distance <= CLOSE_PRESSURE_RANGE
                ? Math.min(80, closePressureTicks + 1)
                : Math.max(0, closePressureTicks - 2);
        if (distance > ATTACK_RANGE) {
            boss.getNavigation().moveTo(target, ruins ? 1.34D : 1.22D);
        } else {
            orbit(target, distance);
        }
        if (attackDelay > 0) {
            attackDelay--;
            return;
        }
        boolean elevatedUnreachable = isElevatedUnreachable(target);
        boolean canJudgment = canSelectJudgment(
                elevatedUnreachable,
                firstAttack,
                lastAttack == Attack.JUDGMENT,
                judgmentCooldown,
                distance);
        if (elevatedUnreachable) {
            if (canJudgment) {
                beginSolarJudgment((ServerLevel) boss.level(), target);
            } else {
                attackDelay = interAttackDelayTicks(ruins);
            }
            return;
        }
        if (firstAttack && distance <= ATTACK_RANGE) {
            beginCombo((ServerLevel) boss.level());
            return;
        }
        boolean canCombo = distance <= ATTACK_RANGE;
        boolean escaping = UnknownEgyptianCombatMath.isEscaping(
                boss.position(), target.position(), target.getDeltaMovement());
        boolean canGate = gateCooldown == 0
                && activeWall == null
                && distance >= 4.25D
                && distance <= SEAL_MAX_RANGE
                && escaping;
        boolean canHunt = huntCooldown == 0 && distance <= HUNT_MAX_RANGE;
        if (forceHunt && canHunt) {
            forceHunt = false;
            beginSekhmetHunt((ServerLevel) boss.level(), target);
            return;
        }
        if (forceCombo && canCombo) {
            forceCombo = false;
            beginCombo((ServerLevel) boss.level());
            return;
        }
        boolean pressured = closePressureTicks >= 10;
        Attack selected = chooseAttack(canCombo, canGate, canJudgment, canHunt, pressured, distance);
        switch (selected) {
            case COMBO -> beginCombo((ServerLevel) boss.level());
            case GATE -> beginDuatGate((ServerLevel) boss.level(), target);
            case JUDGMENT -> beginSolarJudgment((ServerLevel) boss.level(), target);
            case HUNT -> beginSekhmetHunt((ServerLevel) boss.level(), target);
            default -> attackDelay = interAttackDelayTicks(ruins);
        }
    }

    private Attack chooseAttack(
            boolean canCombo,
            boolean canGate,
            boolean canJudgment,
            boolean canHunt,
            boolean pressured,
            double distance) {
        if (pressured && canCombo && lastAttack != Attack.COMBO) {
            return Attack.COMBO;
        }
        if (canGate && lastAttack != Attack.GATE) {
            return Attack.GATE;
        }
        if (canHunt && distance > COMBO_IDEAL_RANGE && lastAttack != Attack.HUNT) {
            return Attack.HUNT;
        }
        Attack[] attacks = {Attack.COMBO, Attack.GATE, Attack.JUDGMENT, Attack.HUNT};
        boolean routePressureActive = activeWall != null;
        int[] weights = {
            canCombo ? (lastAttack == Attack.COMBO ? 1 : (routePressureActive ? 6 : 4)) : 0,
            canGate && lastAttack != Attack.GATE ? 6 : 0,
            canJudgment ? 3 : 0,
            canHunt && lastAttack != Attack.HUNT ? 7 : 0
        };
        int total = 0;
        for (int i = 0; i < weights.length; i++) {
            if (attacks[i] == previousAttack && attacks[i] == lastAttack) {
                weights[i] = 0;
            }
            total += weights[i];
        }
        if (total <= 0) {
            return canCombo ? Attack.COMBO : Attack.NONE;
        }
        int roll = boss.getRandom().nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return attacks[i];
            }
        }
        return Attack.NONE;
    }

    private void beginCombo(ServerLevel level) {
        boss.getNavigation().stop();
        boss.setDeltaMovement(0.0D, boss.getDeltaMovement().y, 0.0D);
        boss.setLockedCombatDirection(Vec3.ZERO);
        boolean pressured = closePressureTicks >= 10;
        boolean ruins = UnknownFightManager.isEgyptianRuins(boss);
        comboCutCount = comboCutCount(ruins, pressured);
        boss.setCombatAnchor(boss.position(), 0.0D, comboCutCount);
        boss.beginGreekCombatState(
                UnknownCombatState.KHOPESH_COMBO,
                level.getGameTime(),
                UnknownFightManager.isEgyptianRuins(boss));
        boss.triggerGreekAnimation("egypt_khopesh_combo");
        currentAttack = Attack.COMBO;
        firstAttack = false;
        Arrays.fill(comboHits, false);
        shieldBlocksInCombo = 0;
        level.playSound(null, boss.blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD.value(),
                SoundSource.HOSTILE, 1.15F, 0.72F);
        level.playSound(null, boss.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE, 0.55F, 0.55F);
    }

    private void beginDuatGate(ServerLevel level, ServerPlayer target) {
        pendingWall = planDuatWall(level, target);
        if (pendingWall == null) {
            beginSekhmetHunt(level, target);
            return;
        }
        boss.getNavigation().stop();
        boss.setDeltaMovement(0.0D, boss.getDeltaMovement().y, 0.0D);
        boss.beginGreekCombatState(
                UnknownCombatState.DUAT_GATE,
                level.getGameTime(),
                UnknownFightManager.isEgyptianRuins(boss));
        synchronizePendingWall();
        boss.triggerGreekAnimation("egypt_duat_gate");
        currentAttack = Attack.GATE;
        firstAttack = false;
        gateCooldown = sealCooldownTicks(boss.isRuinsCombatVariant());
        level.playSound(null, boss.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.HOSTILE, 1.15F, 0.58F);
        level.playSound(null, boss.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.HOSTILE, 0.95F, 0.72F);
    }

    private void tickDuatGate(ServerLevel level, ServerPlayer target) {
        boolean ruins = boss.isRuinsCombatVariant();
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        int lock = sealLockTick(ruins);
        if (elapsed < lock) {
            TemporaryDuatWall replanned = planDuatWall(level, target);
            if (replanned != null) {
                pendingWall = replanned;
                synchronizePendingWall();
            }
            face(target.position());
            double distance = horizontalDistance(boss.position(), target.position());
            if (distance > COMBO_IDEAL_RANGE) {
                boss.getNavigation().moveTo(target, ruins ? 1.22D : 1.12D);
            }
        } else {
            faceDirection(boss.getLockedCombatDirection());
            boss.getNavigation().stop();
        }
        if (elapsed == lock) {
            level.playSound(null, boss.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.HOSTILE, 1.05F, 1.42F);
        }
        if (elapsed == gatePanelSpawnTick(ruins, 0)) {
            TemporaryDuatWall nextWall = pendingWall;
            if (activeWall != null) {
                activeWall.restore(level);
            }
            activeWall = nextWall;
            pendingWall = null;
            if (activeWall != null && !activeWall.tick(level, boss, target)) {
                activeWall = null;
            }
            level.playSound(
                    null,
                    BlockPos.containing(boss.getCombatAnchor()),
                    SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                    SoundSource.HOSTILE,
                    1.45F,
                    0.62F);
        }
        if (elapsed >= gatePanelSpawnTick(ruins, 0) + WALL_BUILD_TICKS) {
            previousAttack = lastAttack;
            lastAttack = currentAttack;
            currentAttack = Attack.NONE;
            boss.resetGreekCombat();
            forceHunt = true;
            attackDelay = interAttackDelayTicks(ruins);
        }
    }

    private TemporaryDuatWall planDuatWall(ServerLevel level, ServerPlayer target) {
        Vec3 away = UnknownGreekCombatMath.horizontalDirection(
                boss.position(), target.position(), boss.getLookAngle());
        Vec3 motion = new Vec3(
                target.getDeltaMovement().x, 0.0D, target.getDeltaMovement().z);
        Vec3 escape = motion.lengthSqr() >= 0.0064D && motion.dot(away) > 0.0D
                ? motion.normalize()
                : away;
        Direction cardinal = UnknownEgyptianCombatMath.cardinalEscapeDirection(escape);
        long attackStart = boss.getCombatState() == UnknownCombatState.DUAT_GATE
                ? level.getGameTime() - boss.combatElapsedTicks(level.getGameTime())
                : level.getGameTime();
        long buildStart = attackStart + sealWarningTicks(boss.isRuinsCombatVariant());
        return TemporaryDuatWall.plan(
                level,
                boss,
                target,
                cardinal,
                WALL_WIDTH,
                WALL_HEIGHT_BLOCKS,
                buildStart,
                buildStart + thresholdActiveTicks(boss.isRuinsCombatVariant()));
    }

    private void synchronizePendingWall() {
        if (pendingWall == null) {
            return;
        }
        Direction direction = pendingWall.escapeDirection();
        Vec3 facing = new Vec3(direction.getStepX(), 0.0D, direction.getStepZ());
        boss.setLockedCombatDirection(facing);
        boss.setCombatAnchor(
                Vec3.atLowerCornerOf(pendingWall.center()).add(0.5D, 0.0D, 0.5D),
                0.0D,
                WALL_WIDTH);
    }

    private void beginSolarJudgment(ServerLevel level, ServerPlayer target) {
        boss.getNavigation().stop();
        boss.setDeltaMovement(0.0D, boss.getDeltaMovement().y, 0.0D);
        boss.beginGreekCombatState(
                UnknownCombatState.SOLAR_JUDGMENT,
                level.getGameTime(),
                UnknownFightManager.isEgyptianRuins(boss));
        updateJudgmentLock(target);
        boss.triggerGreekAnimation("egypt_solar_judgment");
        currentAttack = Attack.JUDGMENT;
        firstAttack = false;
        judgmentHit = false;
        judgmentCooldown = judgmentCooldownTicks(boss.isRuinsCombatVariant());
        level.playSound(null, boss.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.HOSTILE, 1.45F, 0.48F);
        level.playSound(null, boss.blockPosition(), SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER,
                SoundSource.HOSTILE, 1.0F, 0.62F);
    }

    private void tickSolarJudgment(ServerLevel level, ServerPlayer target) {
        boolean ruins = boss.isRuinsCombatVariant();
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        int lock = judgmentLockTick(ruins);
        int warning = judgmentWarningTicks(ruins);
        int active = judgmentActiveTicks(ruins);
        holdPosition();
        if (elapsed < lock) {
            updateJudgmentLock(target);
        } else {
            faceDirection(boss.getLockedCombatDirection());
        }
        if (elapsed == lock) {
            level.playSound(null, boss.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.HOSTILE, 1.2F, 1.72F);
        }
        if (elapsed == warning) {
            level.playSound(null, boss.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                    SoundSource.HOSTILE, 1.7F, 0.58F);
            level.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(),
                    SoundSource.HOSTILE, 1.35F, 0.72F);
        }
        if (elapsed >= warning && elapsed < warning + active) {
            tryJudgmentHit(level, target, elapsed - warning);
        }
        if (elapsed >= warning + active) {
            recoveryEndElapsed = elapsed + judgmentRecoveryTicks(ruins);
            boss.continueGreekCombatState(UnknownCombatState.RECOVERY);
        }
    }

    private void updateJudgmentLock(ServerPlayer target) {
        Vec3 predicted = UnknownGreekCombatMath.predictHorizontal(
                target.position(), target.getDeltaMovement(), 10, 4.5D);
        Vec3 direction = UnknownGreekCombatMath.horizontalDirection(
                boss.position(), predicted, boss.getLookAngle());
        boss.setLockedCombatDirection(direction);
        boss.setCombatAnchor(predicted, 0.0D, JUDGMENT_LENGTH);
        face(predicted);
    }

    private void tryJudgmentHit(ServerLevel level, ServerPlayer target, int activeElapsed) {
        if (judgmentHit) {
            return;
        }
        Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        double width = judgmentHalfWidth(boss.isRuinsCombatVariant())
                + target.getBbWidth() * 0.5D;
        double previousDistance = judgmentWaveDistance(
                boss.isRuinsCombatVariant(), activeElapsed - 1.0D);
        double currentDistance = judgmentWaveDistance(
                boss.isRuinsCombatVariant(), activeElapsed);
        if (!UnknownEgyptianCombatMath.judgmentWavefrontContains(
                boss.position(),
                boss.getLockedCombatDirection(),
                JUDGMENT_LENGTH,
                width,
                center,
                JUDGMENT_VERTICAL_REACH,
                previousDistance,
                currentDistance,
                target.getBbWidth() * 0.5D)) {
            return;
        }
        judgmentHit = true;
        Vec3 incoming = boss.getLockedCombatDirection().reverse();
        boolean blocked = target.isBlocking()
                && UnknownGreekCombatMath.isInsideFrontArc(target.getLookAngle(), incoming, 180.0D);
        if (blocked) {
            breakPlayerShield(target);
        }
        boolean damaged = target.hurtServer(
                level,
                boss.damageSources().mobAttack(boss),
                judgmentDamage(blocked));
        boss.showCombatFx(
                blocked ? UnknownEntity.COMBAT_FX_PLAYER_BLOCK : UnknownEntity.COMBAT_FX_HIT,
                center,
                level.getGameTime());
        if (damaged) {
            Vec3 push = boss.getLockedCombatDirection();
            target.push(push.x * 0.72D, 0.24D, push.z * 0.72D);
        }
        level.playSound(null, target.blockPosition(),
                blocked ? SoundEvents.SHIELD_BLOCK.value() : SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.HOSTILE, blocked ? 1.3F : 1.15F, blocked ? 0.72F : 1.34F);
    }

    private void breakPlayerShield(ServerPlayer target) {
        ItemStack blockingItem = target.getUseItem();
        if (blockingItem.isEmpty()) {
            return;
        }
        target.stopUsingItem();
        target.getCooldowns().addCooldown(blockingItem, SHIELD_BREAK_TICKS);
    }

    public static float judgmentDamage(boolean blocked) {
        return blocked ? JUDGMENT_DAMAGE * JUDGMENT_BLOCK_MULTIPLIER : JUDGMENT_DAMAGE;
    }

    private void beginSekhmetHunt(ServerLevel level, ServerPlayer target) {
        boolean ruins = UnknownFightManager.isEgyptianRuins(boss);
        Vec3 targetMotion = target.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(
                targetMotion.x * targetMotion.x + targetMotion.z * targetMotion.z);
        float healthRatio = boss.getMaxHealth() <= 0.0F
                ? 1.0F
                : boss.getHealth() / boss.getMaxHealth();
        huntPlannedBeats = selectHuntBeatCount(
                ruins,
                horizontalDistance(boss.position(), target.position()),
                horizontalSpeed,
                target.isBlocking(),
                activeWall != null,
                healthRatio,
                closePressureTicks,
                boss.getRandom().nextInt(3));
        boss.getNavigation().stop();
        boss.setDeltaMovement(0.0D, boss.getDeltaMovement().y, 0.0D);
        boss.beginGreekCombatState(
                UnknownCombatState.SEKHMET_HUNT,
                level.getGameTime(),
                ruins);
        huntBeat = 0;
        huntBeatHit = false;
        shieldBlocksInHunt = 0;
        updateHuntLock(level, target, 0);
        boss.triggerGreekAnimation("egypt_sekhmet_hunt");
        currentAttack = Attack.HUNT;
        firstAttack = false;
        huntCooldown = huntCooldownTicks(boss.isRuinsCombatVariant());
        level.playSound(null, boss.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.HOSTILE, 1.05F, 0.9F);
        level.playSound(null, boss.blockPosition(), SoundEvents.RAVAGER_ROAR,
                SoundSource.HOSTILE, 0.62F, 1.45F);
    }

    private void tickSekhmetHunt(ServerLevel level, ServerPlayer target) {
        boolean ruins = boss.isRuinsCombatVariant();
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        int beats = Math.clamp(
                huntPlannedBeats,
                huntMinimumBeatCount(ruins),
                huntMaximumBeatCount(ruins));
        int stride = huntStrideTicks(ruins);
        int beat = Math.min(beats - 1, elapsed / stride);
        int beatAge = elapsed - beat * stride;
        if (beat != huntBeat) {
            huntBeat = beat;
            huntBeatHit = false;
            updateHuntLock(level, target, beat);
            boss.triggerGreekAnimation("egypt_sekhmet_hunt");
            level.playSound(null, boss.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.HOSTILE, 0.85F, 0.72F + beat * 0.09F);
        }
        if (beatAge < huntLockTick(ruins)) {
            updateHuntLock(level, target, beat);
            face(target.position());
        } else {
            faceDirection(boss.getLockedCombatDirection());
        }
        if (beatAge == huntLockTick(ruins)) {
            level.playSound(null, boss.blockPosition(), SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER,
                    SoundSource.HOSTILE, 0.92F, 1.58F);
        }
        int dashStart = huntWindupTicks(ruins);
        int slashStart = dashStart + huntDashTicks(ruins);
        if (beatAge >= dashStart && beatAge < slashStart) {
            dashTowardHuntAnchor(level, ruins);
        } else {
            holdPosition();
        }
        if (beatAge >= slashStart && beatAge < slashStart + HUNT_ACTIVE_TICKS) {
            tryHuntHit(level, target, beatAge - slashStart, beat);
        }
        if (elapsed >= beats * stride) {
            recoveryEndElapsed = huntSequenceTicks(ruins, beats);
            boss.stopCombatAnimation("egypt_sekhmet_hunt");
            boss.triggerGreekAnimation("egypt_sekhmet_recovery");
            boss.continueGreekCombatState(UnknownCombatState.RECOVERY);
        }
    }

    private void updateHuntLock(ServerLevel level, ServerPlayer target, int beat) {
        boolean ruins = boss.isRuinsCombatVariant();
        Vec3 preferred = UnknownEgyptianCombatMath.sekhmetFlankTarget(
                boss.position(),
                target.position(),
                target.getDeltaMovement(),
                beat,
                HUNT_FLANK_DISTANCE);
        Vec3 velocity = target.getDeltaMovement();
        Vec3 predicted = target.position().add(
                Math.clamp(velocity.x * 5.0D, -2.2D, 2.2D),
                0.0D,
                Math.clamp(velocity.z * 5.0D, -2.2D, 2.2D));
        Vec3 approach = UnknownGreekCombatMath.horizontalDirection(
                boss.position(), predicted, boss.getLookAngle());
        Vec3 side = new Vec3(-approach.z, 0.0D, approach.x);
        double sign = (beat & 1) == 0 ? 1.0D : -1.0D;
        Vec3[] candidates = {
            preferred,
            predicted.add(approach.scale(0.7D)).add(side.scale(-HUNT_FLANK_DISTANCE * sign)),
            predicted.add(approach.scale(0.35D)).add(side.scale(1.15D * sign)),
            predicted.add(approach.scale(0.35D)).add(side.scale(-1.15D * sign)),
            predicted.subtract(approach.scale(0.8D)).add(side.scale(1.2D * sign)),
            predicted.subtract(approach.scale(0.8D)).add(side.scale(-1.2D * sign)),
            boss.position().lerp(preferred, 0.72D),
            boss.position().lerp(preferred, 0.48D)
        };
        Vec3 flank = null;
        double maximumTravel = huntMaximumTravel(ruins);
        for (Vec3 candidate : candidates) {
            Vec3 reachableCandidate = UnknownEgyptianCombatMath.clampHuntAnchorToTravel(
                    boss.position(), candidate, maximumTravel);
            var safe = UnknownBossMovementSafety.resolveStraightDashAnchor(
                    level, boss, boss.position(), reachableCandidate);
            if (safe.isPresent()
                    && horizontalDistance(safe.orElseThrow(), predicted)
                            <= HUNT_SLASH_RADIUS - 0.15D) {
                flank = safe.orElseThrow();
                break;
            }
        }
        if (flank == null) {
            // No fake promise: a fully blocked beat telegraphs in place and can
            // still slash if the player elects to remain in weapon range.
            flank = boss.position();
        }
        Vec3 direction = UnknownEgyptianCombatMath.sekhmetStrikeDirection(
                flank, predicted, boss.getLookAngle());
        boss.setLockedCombatDirection(direction);
        boss.setCombatAnchor(flank, beat, huntPlannedBeats);
    }

    private void dashTowardHuntAnchor(ServerLevel level, boolean ruins) {
        Vec3 toAnchor = boss.getCombatAnchor().subtract(boss.position());
        Vec3 flat = new Vec3(toAnchor.x, 0.0D, toAnchor.z);
        if (flat.lengthSqr() <= 0.04D) {
            holdPosition();
            return;
        }
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        int beatAge = elapsed % huntStrideTicks(ruins);
        int remaining = Math.max(
                1,
                huntWindupTicks(ruins) + huntDashTicks(ruins) - beatAge);
        double baseStep = ruins ? HUNT_DASH_STEP_RUINS : HUNT_DASH_STEP_PAST;
        double maximumStep = ruins ? HUNT_MAX_DASH_STEP_RUINS : HUNT_MAX_DASH_STEP_PAST;
        double stepLength = huntDashStepLength(
                flat.length(), remaining, baseStep, maximumStep);
        Vec3 step = flat.normalize().scale(stepLength);
        boss.getNavigation().stop();
        boss.setDeltaMovement(Vec3.ZERO);
        if (!UnknownBossMovementSafety.moveDashStep(level, boss, step)) {
            holdPosition();
            return;
        }
        faceDirection(boss.getLockedCombatDirection());
    }

    private void tryHuntHit(
            ServerLevel level,
            ServerPlayer target,
            int activeTick,
            int beat) {
        if (huntBeatHit
                || !boss.hasLineOfSight(target)
                || !UnknownEgyptianCombatMath.huntHitAllowed(
                        lastHuntHitTick, level.getGameTime(), HUNT_HIT_GRACE_TICKS)) {
            return;
        }
        boolean leftToRight = (beat & 1) == 0;
        double start = leftToRight ? -72.0D : 72.0D;
        double end = -start;
        double previous = lerp(start, end, activeTick / (double) HUNT_ACTIVE_TICKS);
        double current = lerp(start, end, (activeTick + 1.0D) / HUNT_ACTIVE_TICKS);
        AABB targetBox = target.getBoundingBox();
        Vec3 targetCenter = targetBox.getCenter();
        Vec3 origin = boss.position().add(0.0D, boss.getBbHeight() * 0.52D, 0.0D);
        if (!UnknownEgyptianCombatMath.sweptArcContains(
                origin,
                lockedDirection(target),
                previous,
                current,
                targetCenter,
                0.25D,
                HUNT_SLASH_RADIUS + target.getBbWidth() * 0.5D,
                target.getBbHeight() * 0.7D)) {
            return;
        }
        huntBeatHit = true;
        lastHuntHitTick = level.getGameTime();
        Vec3 incoming = lockedDirection(target).reverse();
        boolean blocked = target.isBlocking()
                && UnknownGreekCombatMath.isInsideFrontArc(target.getLookAngle(), incoming, 180.0D);
        if (blocked) {
            shieldBlocksInHunt++;
            if (boss.isRuinsCombatVariant()
                    && (beat == huntPlannedBeats - 1 || shieldBlocksInHunt >= 3)) {
                breakPlayerShield(target);
            }
        }
        float damage = boss.isRuinsCombatVariant() ? HUNT_DAMAGE_RUINS : HUNT_DAMAGE_PAST;
        boolean damaged = target.hurtServer(
                level,
                boss.damageSources().mobAttack(boss),
                blocked ? damage * 0.5F : damage);
        boss.showCombatFx(
                blocked ? UnknownEntity.COMBAT_FX_PLAYER_BLOCK : UnknownEntity.COMBAT_FX_HIT,
                targetCenter,
                level.getGameTime());
        if (damaged) {
            Vec3 push = lockedDirection(target);
            target.push(push.x * 0.42D, 0.1D, push.z * 0.42D);
        }
        level.playSound(null, target.blockPosition(),
                blocked ? SoundEvents.SHIELD_BLOCK.value() : SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.HOSTILE, blocked ? 1.25F : 1.0F, blocked ? 0.68F : 0.82F);
    }

    private void tickPersistentWall(ServerLevel level, ServerPlayer target) {
        if (activeWall != null && !activeWall.tick(level, boss, target)) {
            activeWall = null;
        }
    }

    private void restoreWall(ServerLevel level) {
        if (activeWall != null) {
            activeWall.restore(level);
            activeWall = null;
        }
        pendingWall = null;
    }

    private void tickCombo(ServerLevel level, ServerPlayer target) {
        boolean ruins = boss.isRuinsCombatVariant();
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        int windup = windupTicks(ruins);
        int lock = lockTick(ruins);
        int active = activeTicks(ruins);
        if (elapsed < lock) {
            face(target.position());
            double distance = horizontalDistance(boss.position(), target.position());
            if (distance > COMBO_IDEAL_RANGE) {
                boss.getNavigation().moveTo(target, ruins ? 1.34D : 1.24D);
            } else {
                boss.getNavigation().stop();
            }
        } else if (elapsed == lock) {
            holdPosition();
            Vec3 direction = UnknownGreekCombatMath.horizontalDirection(
                    boss.position(), target.position(), boss.getLookAngle());
            boss.setLockedCombatDirection(direction);
            faceDirection(direction);
            level.playSound(null, boss.blockPosition(), SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER,
                    SoundSource.HOSTILE, 0.8F, 1.42F);
        } else {
            holdPosition();
            faceDirection(lockedDirection(target));
        }

        for (int cut = 0; cut < comboCutCount; cut++) {
            int start = comboCutStartTick(ruins, cut);
            if (cut > 0 && elapsed == start - betweenCutsTicks(ruins) / 2) {
                // Small lateral step between cuts so the chain feels like footwork, not a fan.
                Vec3 side = new Vec3(
                        -boss.getLockedCombatDirection().z,
                        0.0D,
                        boss.getLockedCombatDirection().x);
                if ((cut & 1) != 0) {
                    side = side.scale(-1.0D);
                }
                Vec3 step = side.scale(0.35D);
                UnknownBossMovementSafety.moveDashStep(level, boss, step);
                level.playSound(null, boss.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.HOSTILE, 0.7F, 0.62F + cut * 0.08F);
            }
            if (cut > 0
                    && elapsed == start - 1
                    && target.isBlocking()
                    && horizontalDistance(boss.position(), target.position()) <= 4.75D) {
                Vec3 direction = UnknownGreekCombatMath.horizontalDirection(
                        boss.position(), target.position(), boss.getLockedCombatDirection());
                boss.setLockedCombatDirection(direction);
                faceDirection(direction);
            }
            if (elapsed >= start && elapsed < start + active) {
                tryCutHit(level, target, elapsed - start, active, cut);
            }
        }
        int lastEnd = comboCutStartTick(ruins, comboCutCount - 1) + active;
        if (elapsed >= lastEnd) {
            recoveryEndElapsed = comboRecoveryEndTick(ruins, comboCutCount);
            boss.stopCombatAnimation("egypt_khopesh_combo");
            boss.triggerGreekAnimation(comboCutCount > 2
                    ? "egypt_khopesh_recovery_late"
                    : "egypt_khopesh_recovery");
            boss.continueGreekCombatState(UnknownCombatState.RECOVERY);
        }
    }

    private void tryCutHit(
            ServerLevel level,
            ServerPlayer target,
            int activeTick,
            int activeTicks,
            int cutIndex) {
        if (comboHits[cutIndex]) {
            return;
        }
        boolean leftToRight = (cutIndex & 1) == 0;
        double start = leftToRight ? FIRST_START_ANGLE : SECOND_START_ANGLE;
        double end = leftToRight ? FIRST_END_ANGLE : SECOND_END_ANGLE;
        double previous = lerp(start, end, activeTick / (double) activeTicks);
        double current = lerp(start, end, (activeTick + 1.0D) / activeTicks);
        AABB box = target.getBoundingBox();
        Vec3 center = new Vec3(target.getX(), (box.minY + box.maxY) * 0.5D, target.getZ());
        Vec3 sweepOrigin = boss.position().add(0.0D, boss.getBbHeight() * 0.52D, 0.0D);
        if (!UnknownEgyptianCombatMath.sweptArcContains(
                sweepOrigin,
                lockedDirection(target),
                previous,
                current,
                center,
                INNER_RADIUS,
                OUTER_RADIUS + target.getBbWidth() * 0.5D,
                target.getBbHeight() * 0.65D)) {
            return;
        }
        comboHits[cutIndex] = true;
        Vec3 incoming = lockedDirection(target).reverse();
        boolean blocked = target.isBlocking()
                && UnknownGreekCombatMath.isInsideFrontArc(target.getLookAngle(), incoming, 180.0D);
        boolean shieldBroken = false;
        if (blocked) {
            shieldBlocksInCombo++;
            if (shieldBlocksInCombo >= shieldBreakBlockCount(boss.isRuinsCombatVariant())) {
                breakPlayerShield(target);
                shieldBroken = true;
                comboCutCount = Math.min(comboCutCount, cutIndex + 2);
            } else {
                comboCutCount = Math.min(
                        defensiveComboMaxCuts(boss.isRuinsCombatVariant()),
                        comboCutCount + 1);
            }
            boss.setCombatAnchor(
                    boss.getCombatAnchor(),
                    boss.getCombatGapOffset(),
                    comboCutCount);
        }
        float damage = cutIndex == 0
                ? FIRST_DAMAGE
                : (blocked ? SECOND_DAMAGE * 0.5F : SECOND_DAMAGE);
        boolean damaged = target.hurtServer(level, boss.damageSources().mobAttack(boss), damage);
        if (cutIndex == comboCutCount - 1) {
            Vec3 push = lockedDirection(target);
            target.push(push.x * 0.72D, 0.12D, push.z * 0.72D);
        }
        boss.showCombatFx(
                blocked ? UnknownEntity.COMBAT_FX_PLAYER_BLOCK : UnknownEntity.COMBAT_FX_HIT,
                center,
                level.getGameTime());
        level.playSound(null, target.blockPosition(),
                shieldBroken
                        ? SoundEvents.SHIELD_BREAK.value()
                        : (blocked ? SoundEvents.SHIELD_BLOCK.value() : SoundEvents.PLAYER_ATTACK_SWEEP),
                SoundSource.HOSTILE,
                blocked ? 1.25F : 1.05F,
                damaged ? (leftToRight ? 0.72F : 0.58F) : 0.9F);
    }

    private void tickRecovery(ServerLevel level) {
        holdPosition();
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        if (elapsed < recoveryEndElapsed) {
            return;
        }
        boolean huntFinished = currentAttack == Attack.HUNT;
        previousAttack = lastAttack;
        lastAttack = currentAttack;
        currentAttack = Attack.NONE;
        boss.resetGreekCombat();
        boolean ruins = UnknownFightManager.isEgyptianRuins(boss);
        if (huntFinished && ruins) {
            forceCombo = true;
        }
        attackDelay = interAttackDelayTicks(ruins);
    }

    private void orbit(ServerPlayer target, double distance) {
        Vec3 radial = UnknownGreekCombatMath.horizontalDirection(
                target.position(), boss.position(), boss.getLookAngle());
        Vec3 tangent = clockwise
                ? new Vec3(-radial.z, 0.0D, radial.x)
                : new Vec3(radial.z, 0.0D, -radial.x);
        Vec3 point = target.position()
                .add(radial.scale(distance < 3.3D ? 3.25D : ORBIT_RADIUS))
                .add(tangent.scale(1.2D));
        if (boss.getNavigation().isDone() || boss.tickCount % 9 == 0) {
            boss.getNavigation().moveTo(point.x, target.getY(), point.z,
                    UnknownFightManager.isEgyptianRuins(boss) ? 1.2D : 1.08D);
        }
    }

    /** Leaves contact hazards before any attack is allowed to hold the boss still. */
    private boolean escapeImmediateHazard(ServerLevel level) {
        if (!UnknownBossMovementSafety.isEntityInDanger(level, boss)) {
            return false;
        }
        if (boss.getCombatState() != UnknownCombatState.NEUTRAL) {
            currentAttack = Attack.NONE;
            boss.resetGreekCombat();
            attackDelay = 6;
        }
        boss.setDeltaMovement(0.0D, boss.getDeltaMovement().y, 0.0D);
        if (!boss.getNavigation().isDone() && boss.tickCount % 4 != 0) {
            return true;
        }
        double angularOffset = Math.floorMod(boss.getUUID().hashCode(), 8) * (Math.PI / 4.0D);
        for (int radius = 2; radius <= 5; radius++) {
            for (int direction = 0; direction < 8; direction++) {
                double angle = angularOffset + direction * (Math.PI / 4.0D);
                BlockPos candidate = BlockPos.containing(
                        boss.getX() + Math.cos(angle) * radius,
                        boss.getY(),
                        boss.getZ() + Math.sin(angle) * radius);
                Path path = boss.getNavigation().createPath(candidate, 0);
                if (path != null && path.canReach()
                        && boss.getNavigation().moveTo(path, 1.35D)) {
                    return true;
                }
            }
        }
        boss.getNavigation().stop();
        return true;
    }

    private Vec3 lockedDirection(ServerPlayer target) {
        Vec3 locked = boss.getLockedCombatDirection();
        return locked.horizontalDistanceSqr() > 1.0E-6D
                ? locked
                : UnknownGreekCombatMath.horizontalDirection(
                        boss.position(), target.position(), boss.getLookAngle());
    }

    private boolean isElevatedUnreachable(ServerPlayer target) {
        double verticalDifference = target.getY() - boss.getY();
        if (verticalDifference <= UnknownGreekCombatMath.UNREACHABLE_HEIGHT_THRESHOLD) {
            return false;
        }
        Path path = boss.getNavigation().createPath(target, 0);
        return isElevatedUnreachable(verticalDifference, path != null && path.canReach());
    }

    private void holdPosition() {
        boss.getNavigation().stop();
        boss.setDeltaMovement(0.0D, boss.getDeltaMovement().y, 0.0D);
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

    public static int windupTicks(boolean ruins) {
        return ruins ? WINDUP_RUINS : WINDUP_PAST;
    }

    public static int lockTick(boolean ruins) {
        return ruins ? LOCK_RUINS : LOCK_PAST;
    }

    public static int activeTicks(boolean ruins) {
        return ruins ? CUT_ACTIVE_RUINS : CUT_ACTIVE_PAST;
    }

    public static int betweenCutsTicks(boolean ruins) {
        return ruins ? BETWEEN_CUTS_RUINS : BETWEEN_CUTS_PAST;
    }

    public static int recoveryTicks(boolean ruins) {
        return ruins ? RECOVERY_RUINS : RECOVERY_PAST;
    }

    public static int secondCutStartTick(boolean ruins) {
        return comboCutStartTick(ruins, 1);
    }

    public static int comboCutStartTick(boolean ruins, int cutIndex) {
        int index = Math.clamp(cutIndex, 0, KHOPESH_CUT_STARTS.length - 1);
        return scaledAnimationTick(ruins, KHOPESH_CUT_STARTS[index]);
    }

    public static int comboCutCount(boolean ruins, boolean closePressure) {
        return ruins && closePressure ? 3 : 2;
    }

    public static int defensiveComboMaxCuts(boolean ruins) {
        return KHOPESH_CUT_STARTS.length;
    }

    public static int shieldBreakBlockCount(boolean ruins) {
        return KHOPESH_CUT_STARTS.length;
    }

    public static int sequenceTicks(boolean ruins) {
        return scaledAnimationTick(ruins, KHOPESH_ANIMATION_TICKS);
    }

    public static int comboVisualStopTick(boolean ruins, int cutCount) {
        return comboRecoveryEndTick(ruins, cutCount);
    }

    public static int comboRecoveryEndTick(boolean ruins, int cutCount) {
        int clamped = Math.clamp(cutCount, 2, KHOPESH_CUT_STARTS.length);
        int lastCutEnd = comboCutStartTick(ruins, clamped - 1) + activeTicks(ruins);
        return lastCutEnd + recoveryTicks(ruins);
    }

    public static int initialAttackDelayTicks(boolean ruins) {
        return ruins ? 0 : 1;
    }

    public static int interAttackDelayTicks(boolean ruins) {
        return 0;
    }

    public static int sealWarningTicks(boolean ruins) {
        return gatePanelSpawnTick(ruins, 0);
    }

    public static int sealLockTick(boolean ruins) {
        return scaledAnimationTick(ruins, 18);
    }

    public static int sealIntervalTicks(boolean ruins) {
        return scaledDuration(ruins, GATE_PANEL_INTERVAL_AUTHORED_TICKS);
    }

    public static int sealCooldownTicks(boolean ruins) {
        return ruins ? GATE_COOLDOWN_RUINS : GATE_COOLDOWN_PAST;
    }

    public static int sealSequenceTicks(boolean ruins) {
        return gatePanelSpawnTick(ruins, GATE_PANEL_COUNT - 1) + WALL_BUILD_TICKS;
    }

    public static int gatePanelSpawnTick(boolean ruins, int panel) {
        int authored = GATE_FIRST_PANEL_AUTHORED_TICK
                + Math.clamp(panel, 0, GATE_PANEL_COUNT - 1)
                        * GATE_PANEL_INTERVAL_AUTHORED_TICKS;
        return scaledAnimationTick(ruins, authored);
    }

    public static int thresholdActiveTicks(boolean ruins) {
        return ruins ? THRESHOLD_ACTIVE_RUINS : THRESHOLD_ACTIVE_PAST;
    }

    public static int mineCount(boolean ruins) {
        return GATE_PANEL_COUNT;
    }

    public static int mineRecoveryTicks(boolean ruins) {
        return 3;
    }

    public static double minefieldRadius(boolean ruins) {
        return UnknownEgyptianCombatMath.DUAT_SIDE_DISTANCE;
    }

    public static int mineImpactTick(boolean ruins, int mineIndex, int seed) {
        return gatePanelSpawnTick(ruins, mineIndex);
    }

    public static int judgmentWarningTicks(boolean ruins) {
        return ruins ? JUDGMENT_WARNING_RUINS : JUDGMENT_WARNING_PAST;
    }

    public static int judgmentLockTick(boolean ruins) {
        return ruins ? JUDGMENT_LOCK_RUINS : JUDGMENT_LOCK_PAST;
    }

    public static int judgmentActiveTicks(boolean ruins) {
        return ruins ? JUDGMENT_ACTIVE_RUINS : JUDGMENT_ACTIVE_PAST;
    }

    public static int judgmentRecoveryTicks(boolean ruins) {
        return ruins ? JUDGMENT_RECOVERY_RUINS : JUDGMENT_RECOVERY_PAST;
    }

    public static int judgmentCooldownTicks(boolean ruins) {
        return ruins ? JUDGMENT_COOLDOWN_RUINS : JUDGMENT_COOLDOWN_PAST;
    }

    public static boolean isElevatedUnreachable(
            double verticalDifference,
            boolean navigationCanReach) {
        return UnknownGreekCombatMath.isElevatedUnreachable(
                verticalDifference,
                navigationCanReach);
    }

    public static boolean canSelectJudgment(
            boolean elevatedUnreachable,
            boolean firstAttack,
            boolean lastWasJudgment,
            int cooldown,
            double distance) {
        return (elevatedUnreachable || !firstAttack)
                && !lastWasJudgment
                && cooldown <= 0
                && distance <= JUDGMENT_MAX_RANGE;
    }

    public static double judgmentHalfWidth(boolean ruins) {
        return ruins ? JUDGMENT_HALF_WIDTH_RUINS : JUDGMENT_HALF_WIDTH_PAST;
    }

    public static int judgmentSequenceTicks(boolean ruins) {
        return judgmentWarningTicks(ruins)
                + judgmentActiveTicks(ruins)
                + judgmentRecoveryTicks(ruins);
    }

    /** Exact eased front used by both the authoritative hit and the renderer. */
    public static double judgmentWaveDistance(boolean ruins, double activeAge) {
        double duration = Math.max(1.0D, judgmentActiveTicks(ruins) - 1.0D);
        double progress = Math.clamp(activeAge / duration, 0.0D, 1.0D);
        double eased = progress * progress * (3.0D - 2.0D * progress);
        return JUDGMENT_WAVE_START
                + (JUDGMENT_LENGTH - JUDGMENT_WAVE_START) * eased;
    }

    public static int huntMinimumBeatCount(boolean ruins) {
        return ruins ? HUNT_BEATS_RUINS : HUNT_BEATS_PAST;
    }

    public static int huntMaximumBeatCount(boolean ruins) {
        return ruins ? HUNT_MAX_BEATS_RUINS : HUNT_MAX_BEATS_PAST;
    }

    /**
     * Chooses the hunt length once, before the first beat. The renderer receives
     * the result through the combat anchor, so its remaining-cut indicators can
     * never disagree with the authoritative damage sequence.
     */
    public static int selectHuntBeatCount(
            boolean ruins,
            double distance,
            double horizontalSpeed,
            boolean targetBlocking,
            boolean routeAlreadyCut,
            float bossHealthRatio,
            int closePressureTicks,
            int tacticalRoll) {
        int pressure = Math.clamp(tacticalRoll, 0, 2);
        if (targetBlocking) {
            pressure += 2;
        }
        if (horizontalSpeed >= 0.18D) {
            pressure++;
        }
        if (distance >= 7.0D) {
            pressure++;
        }
        if (routeAlreadyCut) {
            pressure++;
        }
        if (bossHealthRatio <= 0.45F) {
            pressure += 2;
        }
        if (closePressureTicks >= 10) {
            pressure++;
        }
        int extraBeats = pressure >= 3 ? 1 : 0;
        if (pressure >= 7) {
            extraBeats++;
        }
        return Math.clamp(
                huntMinimumBeatCount(ruins) + extraBeats,
                huntMinimumBeatCount(ruins),
                huntMaximumBeatCount(ruins));
    }

    /** Kept as the minimum authored length for callers without encounter context. */
    public static int huntBeatCount(boolean ruins) {
        return huntMinimumBeatCount(ruins);
    }

    public static int huntStrideTicks(boolean ruins) {
        return ruins ? HUNT_STRIDE_RUINS : HUNT_STRIDE_PAST;
    }

    public static int huntLockTick(boolean ruins) {
        return ruins ? HUNT_LOCK_RUINS : HUNT_LOCK_PAST;
    }

    public static int huntWindupTicks(boolean ruins) {
        return ruins ? HUNT_WINDUP_RUINS : HUNT_WINDUP_PAST;
    }

    public static int huntDashTicks(boolean ruins) {
        return ruins ? HUNT_DASH_RUINS : HUNT_DASH_PAST;
    }

    public static int huntRecoveryTicks(boolean ruins) {
        return ruins ? HUNT_RECOVERY_RUINS : HUNT_RECOVERY_PAST;
    }

    public static int huntCooldownTicks(boolean ruins) {
        return ruins ? HUNT_COOLDOWN_RUINS : HUNT_COOLDOWN_PAST;
    }

    public static double huntMaximumTravel(boolean ruins) {
        double maximumStep = ruins ? HUNT_MAX_DASH_STEP_RUINS : HUNT_MAX_DASH_STEP_PAST;
        return huntDashTicks(ruins) * maximumStep;
    }

    public static double huntDashStepLength(
            double remainingDistance,
            int remainingTicks,
            double baseStep,
            double maximumStep) {
        double distance = Math.max(0.0D, remainingDistance);
        double maximum = Math.max(0.0D, maximumStep);
        double minimum = Math.min(maximum, Math.max(0.0D, baseStep));
        double required = distance / Math.max(1, remainingTicks);
        return Math.min(distance, Math.min(maximum, Math.max(minimum, required)));
    }

    public static int huntSequenceTicks(boolean ruins) {
        return huntSequenceTicks(ruins, huntMinimumBeatCount(ruins));
    }

    public static int huntSequenceTicks(boolean ruins, int beats) {
        int safeBeats = Math.clamp(
                beats,
                huntMinimumBeatCount(ruins),
                huntMaximumBeatCount(ruins));
        return safeBeats * huntStrideTicks(ruins) + huntRecoveryTicks(ruins);
    }

    /** @deprecated Rejected chariots are never dispatched; returns no actors. */
    @Deprecated
    public static int chariotCount(boolean ruins) {
        return 0;
    }

    /** @deprecated Rejected chariots are never dispatched. */
    @Deprecated
    public static int chariotTravelTicks(boolean ruins) {
        return 1;
    }

    /** @deprecated Rejected chariots are never dispatched. */
    @Deprecated
    public static int chariotWarningTicks(boolean ruins) {
        return huntWindupTicks(ruins);
    }

    /** @deprecated Old Ma'at renderer is not part of the active attack graph. */
    @Deprecated
    public static int maatWarningTicks(boolean ruins) {
        return huntWindupTicks(ruins);
    }

    /** @deprecated Old Ma'at renderer is not part of the active attack graph. */
    @Deprecated
    public static double maatRadius(boolean ruins) {
        return minefieldRadius(ruins);
    }

    private static int scaledAnimationTick(boolean ruins, int authoredTick) {
        return ruins
                ? Math.round(authoredTick / EGYPT_RUINS_ANIMATION_SPEED)
                : authoredTick;
    }

    private static int scaledDuration(boolean ruins, int authoredTicks) {
        return Math.max(1, scaledAnimationTick(ruins, authoredTicks));
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return Math.sqrt(x * x + z * z);
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * Math.clamp(progress, 0.0D, 1.0D);
    }

    private enum Attack {
        NONE,
        COMBO,
        GATE,
        JUDGMENT,
        HUNT
    }
}
