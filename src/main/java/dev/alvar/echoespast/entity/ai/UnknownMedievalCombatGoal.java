package dev.alvar.echoespast.entity.ai;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.entity.UnknownEntity;
import dev.alvar.echoespast.entity.combat.UnknownBossMovementSafety;
import dev.alvar.echoespast.entity.combat.UnknownCombatState;
import dev.alvar.echoespast.entity.combat.UnknownGreekCombatMath;
import dev.alvar.echoespast.entity.combat.UnknownMedievalCombatMath;
import dev.alvar.echoespast.network.UnknownCombatImpactPayload;
import dev.alvar.echoespast.server.UnknownFightManager;
import dev.alvar.echoespast.server.UnknownMedievalVanguard;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative Medieval Past duel.
 *
 * <p>The kit is deliberately smaller and slower than the Greek controller:
 * two sword attacks, one positional shield response and a short guard window.
 * Every offensive action returns to a neutral beat, and a forced breather is
 * inserted after two consecutive attacks.</p>
 */
public final class UnknownMedievalCombatGoal extends Goal {
    public static final int COMBO_FIRST_LOCK_TICK = 9;
    public static final int COMBO_FIRST_STEP_START_TICK = 10;
    public static final int COMBO_FIRST_STEP_END_TICK = 13;
    public static final int COMBO_FIRST_ACTIVE_START_TICK = 13;
    public static final int COMBO_ACTIVE_TICKS = 4;
    public static final int COMBO_BRANCH_LOCK_TICK = 20;
    public static final int COMBO_BRANCH_READ_TICKS = 6;
    public static final int COMBO_SWEEP_ACTIVE_START_TICK = 26;
    public static final int COMBO_CHASE_STEP_START_TICK = 23;
    public static final int COMBO_CHASE_STEP_END_TICK = 28;
    public static final int COMBO_CHASE_ACTIVE_START_TICK = 29;
    public static final int COMBO_SWEEP_END_TICK = 46;
    public static final int COMBO_CHASE_END_TICK = 51;
    public static final float COMBO_FIRST_DAMAGE = 6.0F;
    public static final float COMBO_SECOND_DAMAGE = 7.0F;
    public static final float COMBO_FIRST_BLOCKED_DAMAGE = 1.0F;
    public static final float COMBO_SECOND_BLOCKED_DAMAGE = 2.0F;
    public static final int COMBO_FIRST_BLOCK_DURABILITY = 2;
    public static final int COMBO_SECOND_BLOCK_DURABILITY = 3;
    public static final int COMBO_FIRST_BLOCK_FLINCH_TICKS = 4;
    public static final int COMBO_SECOND_BLOCK_FLINCH_TICKS = 6;
    public static final double COMBO_FIRST_BLOCK_KNOCKBACK = 0.18D;
    public static final double COMBO_SECOND_BLOCK_KNOCKBACK = 0.32D;
    public static final double SWORD_REACH = 3.15D;
    public static final double COMBO_INNER_RADIUS = 0.55D;
    public static final double COMBO_BRANCH_DISTANCE = 2.85D;
    public static final double COMBO_FIRST_START_DEGREES = -78.0D;
    public static final double COMBO_FIRST_END_DEGREES = 62.0D;
    public static final double COMBO_SWEEP_START_DEGREES = 62.0D;
    public static final double COMBO_SWEEP_END_DEGREES = -70.0D;
    public static final double COMBO_CHASE_START_DEGREES = -28.0D;
    public static final double COMBO_CHASE_END_DEGREES = 32.0D;
    public static final double COMBO_SWEEP_MAX_TURN_DEGREES = 20.0D;
    public static final double COMBO_CHASE_MAX_TURN_DEGREES = 35.0D;
    public static final double COMBO_FIRST_STEP_DISTANCE = 0.60D;
    public static final double COMBO_CHASE_STEP_DISTANCE = 1.08D;
    /** Approximate first-cut frontal arc used by geometry GameTests. */
    public static final double COMBO_ARC_DEGREES =
            Math.abs(COMBO_FIRST_END_DEGREES - COMBO_FIRST_START_DEGREES);

    public static final int OVERHEAD_WINDUP_TICKS = 22;
    public static final int OVERHEAD_LOCK_TICK = 14;
    public static final int OVERHEAD_RECOVERY_TICKS = 24;
    public static final int OVERHEAD_SHIELD_DISABLE_TICKS = 40;
    public static final float OVERHEAD_DAMAGE = 9.0F;
    public static final float OVERHEAD_BLOCKED_DAMAGE = 4.0F;
    public static final double OVERHEAD_HIT_RADIUS = 0.72D;

    public static final int SHIELD_BASH_WINDUP_TICKS = 10;
    public static final int SHIELD_BASH_LOCK_TICK = 7;
    public static final int SHIELD_BASH_RECOVERY_TICKS = 18;
    public static final int SHIELD_BASH_COOLDOWN_TICKS = 50;
    public static final float SHIELD_BASH_DAMAGE = 4.0F;
    public static final double SHIELD_BASH_REACH = 2.85D;
    public static final double SHIELD_BASH_KNOCKBACK = 1.45D;

    public static final int GUARD_WINDUP_TICKS = 4;
    public static final int GUARD_ACTIVE_TICKS = 12;
    public static final int GUARD_RECOVERY_TICKS = 8;
    public static final int GUARD_COOLDOWN_TICKS = 60;
    public static final double GUARD_ARC_DEGREES = 115.0D;
    public static final int RIPOSTE_TELEGRAPH_TICKS = 8;
    public static final int RIPOSTE_RECOVERY_TICKS = 16;
    public static final float RIPOSTE_DAMAGE = 6.0F;
    public static final float RIPOSTE_BLOCKED_DAMAGE = 3.0F;

    public static final int INITIAL_ATTACK_DELAY_TICKS = 14;
    public static final int NEUTRAL_DELAY_TICKS = 10;
    public static final int FORCED_BREATHER_TICKS = 22;
    public static final int MAX_OFFENSIVE_CHAIN = 2;

    private static final double APPROACH_STOP_RANGE = 3.0D;
    private static final double ATTACK_SELECTION_RANGE = 3.55D;
    private static final double GUARD_SELECTION_RANGE = 4.2D;
    private static final double BASH_PRIORITY_RANGE = 2.35D;
    private static final double MOVE_SPEED = 0.84D;

    private final UnknownEntity boss;
    private int attackDelay;
    private int shieldBashCooldown;
    private int guardCooldown;
    private int offensiveChain;
    private int actionSerial;
    private boolean firstComboHit;
    private boolean secondComboHit;
    private boolean firstComboStepBlocked;
    private boolean finisherStepBlocked;
    private boolean actionHit;
    private Attack currentAttack = Attack.NONE;
    private Attack lastAttack = Attack.NONE;

    public UnknownMedievalCombatGoal(UnknownEntity boss) {
        this.boss = boss;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return UnknownFightManager.medievalCombatTarget(boss) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        attackDelay = INITIAL_ATTACK_DELAY_TICKS;
        shieldBashCooldown = 0;
        guardCooldown = 0;
        offensiveChain = 0;
        actionSerial = 0;
        firstComboHit = false;
        secondComboHit = false;
        firstComboStepBlocked = false;
        finisherStepBlocked = false;
        actionHit = false;
        currentAttack = Attack.NONE;
        lastAttack = Attack.NONE;
        boss.resetGreekCombat();
    }

    @Override
    public void stop() {
        boss.getNavigation().stop();
        boss.setTarget(null);
        boss.resetGreekCombat();
        currentAttack = Attack.NONE;
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
        ServerPlayer target = UnknownFightManager.medievalCombatTarget(boss);
        if (target == null) {
            stop();
            return;
        }
        boss.setTarget(target);
        shieldBashCooldown = Math.max(0, shieldBashCooldown - 1);
        guardCooldown = Math.max(0, guardCooldown - 1);

        switch (boss.getCombatState()) {
            case NEUTRAL -> tickNeutral(level, target);
            case MEDIEVAL_COMBO -> tickCombo(level, target);
            case MEDIEVAL_OVERHEAD -> tickOverhead(level, target);
            case MEDIEVAL_SHIELD_BASH -> tickShieldBash(level, target);
            case MEDIEVAL_GUARD -> tickGuard(level, target);
            case MEDIEVAL_RIPOSTE -> tickRiposte(level, target);
            default -> boss.resetGreekCombat();
        }
    }

    private void tickNeutral(ServerLevel level, ServerPlayer target) {
        double distance = horizontalDistance(boss.position(), target.position());
        face(target.position());
        if (distance > APPROACH_STOP_RANGE) {
            boss.getNavigation().moveTo(target, MOVE_SPEED);
        } else {
            boss.getNavigation().stop();
        }
        if (distance > ATTACK_SELECTION_RANGE || !boss.hasLineOfSight(target)) {
            attackDelay = Math.max(attackDelay, 3);
            return;
        }
        if (attackDelay-- > 0) {
            return;
        }

        Attack choice = chooseAttack(
                distance,
                target.isBlocking(),
                shieldBashCooldown,
                guardCooldown,
                offensiveChain,
                actionSerial,
                lastAttack);
        switch (choice) {
            case COMBO -> beginCombo(level, target);
            case OVERHEAD -> beginOverhead(level, target);
            case SHIELD_BASH -> beginShieldBash(level, target);
            case GUARD -> beginGuard(level, target);
            case NONE -> attackDelay = 4;
        }
    }

    private void beginCombo(ServerLevel level, ServerPlayer target) {
        beginAction(level, Attack.COMBO, UnknownCombatState.MEDIEVAL_COMBO);
        firstComboHit = false;
        secondComboHit = false;
        firstComboStepBlocked = false;
        finisherStepBlocked = false;
        boss.setCombatVariant(UnknownEntity.COMBAT_VARIANT_OPENING);
        boss.setLockedCombatDirection(directionTo(target));
        boss.triggerGreekAnimation("medieval_combo");
        level.playSound(null, boss.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                SoundSource.HOSTILE, 0.72F, 0.78F);
    }

    private void tickCombo(ServerLevel level, ServerPlayer target) {
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        holdPosition();
        if (elapsed < COMBO_FIRST_LOCK_TICK) {
            face(target.position());
        } else if (elapsed == COMBO_FIRST_LOCK_TICK) {
            boss.setLockedCombatDirection(directionTo(target));
            faceDirection(boss.getLockedCombatDirection());
        } else if (elapsed < COMBO_BRANCH_LOCK_TICK) {
            faceDirection(boss.getLockedCombatDirection());
        }

        if (elapsed >= COMBO_FIRST_STEP_START_TICK
                && elapsed <= COMBO_FIRST_STEP_END_TICK
                && !firstComboStepBlocked) {
            firstComboStepBlocked = !tryComboStep(
                    level,
                    boss.getLockedCombatDirection(),
                    COMBO_FIRST_STEP_DISTANCE
                            / (COMBO_FIRST_STEP_END_TICK - COMBO_FIRST_STEP_START_TICK + 1));
        }

        if (elapsed == COMBO_FIRST_ACTIVE_START_TICK) {
            playComboSwing(level, false);
        }
        if (elapsed >= COMBO_FIRST_ACTIVE_START_TICK
                && elapsed < COMBO_FIRST_ACTIVE_START_TICK + COMBO_ACTIVE_TICKS) {
            firstComboHit = trySwordSweepHit(
                    level,
                    target,
                    firstComboHit,
                    UnknownCombatImpactPayload.FIRST_CUT,
                    COMBO_FIRST_DAMAGE,
                    COMBO_FIRST_START_DEGREES,
                    COMBO_FIRST_END_DEGREES,
                    elapsed - COMBO_FIRST_ACTIVE_START_TICK);
        }

        if (elapsed == COMBO_BRANCH_LOCK_TICK) {
            lockComboFinisher(target);
        }
        byte variant = boss.getCombatVariant();
        if (variant != UnknownEntity.COMBAT_VARIANT_OPENING) {
            faceDirection(boss.getLockedCombatDirection());
        }
        if (variant == UnknownEntity.COMBAT_VARIANT_MEDIEVAL_CHASE
                && elapsed >= COMBO_CHASE_STEP_START_TICK
                && elapsed <= COMBO_CHASE_STEP_END_TICK
                && !finisherStepBlocked) {
            finisherStepBlocked = !tryComboStep(
                    level,
                    boss.getLockedCombatDirection(),
                    COMBO_CHASE_STEP_DISTANCE
                            / (COMBO_CHASE_STEP_END_TICK - COMBO_CHASE_STEP_START_TICK + 1));
        }

        int finisherStart = variant == UnknownEntity.COMBAT_VARIANT_MEDIEVAL_CHASE
                ? COMBO_CHASE_ACTIVE_START_TICK
                : COMBO_SWEEP_ACTIVE_START_TICK;
        if (variant != UnknownEntity.COMBAT_VARIANT_OPENING && elapsed == finisherStart) {
            playComboSwing(level, variant == UnknownEntity.COMBAT_VARIANT_MEDIEVAL_CHASE);
        }
        if (variant != UnknownEntity.COMBAT_VARIANT_OPENING
                && elapsed >= finisherStart
                && elapsed < finisherStart + COMBO_ACTIVE_TICKS) {
            boolean chase = variant == UnknownEntity.COMBAT_VARIANT_MEDIEVAL_CHASE;
            secondComboHit = trySwordSweepHit(
                    level,
                    target,
                    secondComboHit,
                    UnknownCombatImpactPayload.FINISHER,
                    COMBO_SECOND_DAMAGE,
                    chase ? COMBO_CHASE_START_DEGREES : COMBO_SWEEP_START_DEGREES,
                    chase ? COMBO_CHASE_END_DEGREES : COMBO_SWEEP_END_DEGREES,
                    elapsed - finisherStart);
        }

        if (variant != UnknownEntity.COMBAT_VARIANT_OPENING
                && elapsed >= comboEndTick(variant)) {
            completeAction(true);
        }
    }

    private void lockComboFinisher(ServerPlayer target) {
        byte variant = UnknownMedievalCombatMath.selectComboVariant(
                horizontalDistance(boss.position(), target.position()),
                COMBO_BRANCH_DISTANCE);
        double maximumTurn = variant == UnknownEntity.COMBAT_VARIANT_MEDIEVAL_CHASE
                ? COMBO_CHASE_MAX_TURN_DEGREES
                : COMBO_SWEEP_MAX_TURN_DEGREES;
        Vec3 locked = UnknownMedievalCombatMath.limitedHorizontalTurn(
                boss.getLockedCombatDirection(),
                directionTo(target),
                maximumTurn);
        boss.setLockedCombatDirection(locked);
        boss.setCombatVariant(variant);
        faceDirection(locked);
        boss.stopCombatAnimation("medieval_combo");
        boss.triggerGreekAnimation(variant == UnknownEntity.COMBAT_VARIANT_MEDIEVAL_CHASE
                ? "medieval_combo_chase"
                : "medieval_combo_sweep");
    }

    private void beginOverhead(ServerLevel level, ServerPlayer target) {
        beginAction(level, Attack.OVERHEAD, UnknownCombatState.MEDIEVAL_OVERHEAD);
        actionHit = false;
        boss.setLockedCombatDirection(directionTo(target));
        boss.triggerGreekAnimation("medieval_overhead");
        level.playSound(null, boss.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                SoundSource.HOSTILE, 0.9F, 0.72F);
    }

    private void tickOverhead(ServerLevel level, ServerPlayer target) {
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        holdPosition();
        if (elapsed < OVERHEAD_LOCK_TICK) {
            face(target.position());
        } else if (elapsed == OVERHEAD_LOCK_TICK) {
            boss.setLockedCombatDirection(directionTo(target));
            faceDirection(boss.getLockedCombatDirection());
        } else {
            faceDirection(boss.getLockedCombatDirection());
        }
        if (elapsed == OVERHEAD_WINDUP_TICKS && !actionHit) {
            actionHit = true;
            tryOverheadHit(level, target);
        }
        if (elapsed >= OVERHEAD_WINDUP_TICKS + OVERHEAD_RECOVERY_TICKS) {
            completeAction(true);
        }
    }

    private void beginShieldBash(ServerLevel level, ServerPlayer target) {
        beginAction(level, Attack.SHIELD_BASH, UnknownCombatState.MEDIEVAL_SHIELD_BASH);
        actionHit = false;
        shieldBashCooldown = SHIELD_BASH_COOLDOWN_TICKS;
        boss.setLockedCombatDirection(directionTo(target));
        boss.triggerGreekAnimation("medieval_shield_bash");
        level.playSound(null, boss.blockPosition(), SoundEvents.SHIELD_BLOCK.value(),
                SoundSource.HOSTILE, 1.0F, 0.68F);
    }

    private void tickShieldBash(ServerLevel level, ServerPlayer target) {
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        holdPosition();
        if (elapsed < SHIELD_BASH_LOCK_TICK) {
            face(target.position());
        } else if (elapsed == SHIELD_BASH_LOCK_TICK) {
            boss.setLockedCombatDirection(directionTo(target));
        }
        faceDirection(boss.getLockedCombatDirection());
        if (elapsed == SHIELD_BASH_WINDUP_TICKS && !actionHit) {
            actionHit = true;
            tryShieldBashHit(level, target);
        }
        if (elapsed >= SHIELD_BASH_WINDUP_TICKS + SHIELD_BASH_RECOVERY_TICKS) {
            completeAction(true);
        }
    }

    private void beginGuard(ServerLevel level, ServerPlayer target) {
        beginAction(level, Attack.GUARD, UnknownCombatState.MEDIEVAL_GUARD);
        actionHit = false;
        guardCooldown = GUARD_COOLDOWN_TICKS;
        boss.setLockedCombatDirection(directionTo(target));
        boss.triggerGreekAnimation("medieval_guard");
        level.playSound(null, boss.blockPosition(), SoundEvents.SHIELD_BLOCK.value(),
                SoundSource.HOSTILE, 0.68F, 1.28F);
    }

    private void tickGuard(ServerLevel level, ServerPlayer target) {
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        holdPosition();
        if (elapsed < GUARD_WINDUP_TICKS) {
            face(target.position());
            boss.setLockedCombatDirection(directionTo(target));
        } else {
            faceDirection(boss.getLockedCombatDirection());
        }
        if (elapsed >= GUARD_WINDUP_TICKS + GUARD_ACTIVE_TICKS + GUARD_RECOVERY_TICKS) {
            completeAction(false);
        }
    }

    private void tickRiposte(ServerLevel level, ServerPlayer target) {
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        holdPosition();
        faceDirection(boss.getLockedCombatDirection());
        if (elapsed == RIPOSTE_TELEGRAPH_TICKS && !actionHit) {
            actionHit = true;
            tryRiposteHit(level, target);
        }
        if (elapsed >= RIPOSTE_TELEGRAPH_TICKS + RIPOSTE_RECOVERY_TICKS) {
            completeAction(true);
        }
    }

    private boolean trySwordSweepHit(
            ServerLevel level,
            ServerPlayer target,
            boolean alreadyHit,
            byte beat,
            float fullDamage,
            double sweepStartDegrees,
            double sweepEndDegrees,
            int activeTick) {
        Vec3 targetCenter = targetCenter(target);
        Vec3 origin = boss.position().add(0.0D, boss.getBbHeight() * 0.52D, 0.0D);
        double previousProgress = UnknownMedievalCombatMath.windowProgress(
                activeTick,
                0,
                COMBO_ACTIVE_TICKS);
        double currentProgress = UnknownMedievalCombatMath.windowProgress(
                activeTick + 1,
                0,
                COMBO_ACTIVE_TICKS);
        double previousAngle = lerp(sweepStartDegrees, sweepEndDegrees, previousProgress);
        double currentAngle = lerp(sweepStartDegrees, sweepEndDegrees, currentProgress);
        boolean intersects = UnknownMedievalCombatMath.sweptSwordPathContains(
                origin,
                boss.getLockedCombatDirection(),
                targetCenter,
                COMBO_INNER_RADIUS,
                SWORD_REACH,
                target.getBbWidth() * 0.5D,
                previousAngle,
                currentAngle,
                target.getBbHeight() * 0.75D);
        if (!UnknownMedievalCombatMath.mayApplyCutHit(alreadyHit, intersects)) {
            return alreadyHit;
        }
        Vec3 incoming = UnknownGreekCombatMath.horizontalDirection(
                target.position(),
                boss.position(),
                boss.getLockedCombatDirection().reverse());
        boolean blocked = playerBlocks(target, incoming);
        float damage = blocked
                ? (beat == UnknownCombatImpactPayload.FIRST_CUT
                        ? COMBO_FIRST_BLOCKED_DAMAGE
                        : COMBO_SECOND_BLOCKED_DAMAGE)
                : fullDamage;
        if (blocked) {
            applyComboBlockCost(target, beat);
        }
        boolean damaged = target.hurtServer(level, boss.damageSources().mobAttack(boss), damage);
        if (blocked) {
            double knockback = beat == UnknownCombatImpactPayload.FIRST_CUT
                    ? COMBO_FIRST_BLOCK_KNOCKBACK
                    : COMBO_SECOND_BLOCK_KNOCKBACK;
            Vec3 push = UnknownGreekCombatMath.horizontalDirection(
                    boss.position(), target.position(), boss.getLockedCombatDirection());
            target.push(push.x * knockback, 0.04D, push.z * knockback);
            target.hurtMarked = true;
        }
        showComboImpact(level, target, beat, blocked, damaged);
        return true;
    }

    private void applyComboBlockCost(ServerPlayer target, byte beat) {
        ItemStack blockingItem = target.getUseItem();
        if (blockingItem.isEmpty()) {
            return;
        }
        InteractionHand hand = target.getUsedItemHand();
        int durability = beat == UnknownCombatImpactPayload.FIRST_CUT
                ? COMBO_FIRST_BLOCK_DURABILITY
                : COMBO_SECOND_BLOCK_DURABILITY;
        int flinch = beat == UnknownCombatImpactPayload.FIRST_CUT
                ? COMBO_FIRST_BLOCK_FLINCH_TICKS
                : COMBO_SECOND_BLOCK_FLINCH_TICKS;
        target.stopUsingItem();
        target.getCooldowns().addCooldown(blockingItem, flinch);
        blockingItem.hurtAndBreak(durability, target, hand);
    }

    private boolean tryComboStep(ServerLevel level, Vec3 direction, double distance) {
        Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
        if (horizontal.lengthSqr() <= 1.0E-8D) {
            return false;
        }
        Vec3 step = horizontal.normalize().scale(Math.max(0.0D, distance));
        if (!UnknownMedievalVanguard.futureBossFootprintInsideCombatVolume(level, boss, step)) {
            return false;
        }
        boolean moved = UnknownBossMovementSafety.moveGroundStepNonDestructive(level, boss, step);
        if (moved) {
            spawnStepDust(level);
        }
        return moved;
    }

    private void spawnStepDust(ServerLevel level) {
        BlockPos support = boss.blockPosition().below();
        BlockState state = level.getBlockState(support);
        if (state.isAir()) {
            return;
        }
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state),
                boss.getX(),
                boss.getY() + 0.08D,
                boss.getZ(),
                4,
                0.24D,
                0.035D,
                0.24D,
                0.025D);
    }

    private void playComboSwing(ServerLevel level, boolean chase) {
        level.playSound(
                null,
                boss.blockPosition(),
                EchoesShowThePast.UNKNOWN_MEDIEVAL_SWORD_ATTACK.get(),
                SoundSource.HOSTILE,
                1.0F,
                chase ? 0.72F : 0.94F);
    }

    private void showComboImpact(
            ServerLevel level,
            ServerPlayer target,
            byte beat,
            boolean blocked,
            boolean damaged) {
        boss.showCombatFx(
                blocked
                        ? UnknownEntity.COMBAT_FX_MEDIEVAL_CUT_BLOCK
                        : UnknownEntity.COMBAT_FX_MEDIEVAL_CUT_HIT,
                target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D),
                level.getGameTime());
        if (blocked) {
            level.playSound(
                    null,
                    target.blockPosition(),
                    EchoesShowThePast.UNKNOWN_MEDIEVAL_SWORD_CLASH.get(),
                    SoundSource.HOSTILE,
                    1.12F,
                    beat == UnknownCombatImpactPayload.FIRST_CUT ? 0.96F : 0.82F);
        } else if (damaged) {
            level.playSound(
                    null,
                    target.blockPosition(),
                    SoundEvents.ARMOR_EQUIP_IRON.value(),
                    SoundSource.HOSTILE,
                    0.68F,
                    beat == UnknownCombatImpactPayload.FIRST_CUT ? 0.78F : 0.66F);
        }
        UnknownCombatImpactPayload payload = new UnknownCombatImpactPayload(beat, blocked);
        if (target.connection.hasChannel(payload)) {
            try {
                PacketDistributor.sendToPlayer(target, payload);
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // Headless GameTest connections deliberately reject client payloads.
            }
        }
    }

    private void tryOverheadHit(ServerLevel level, ServerPlayer target) {
        Vec3 origin = boss.position().add(0.0D, boss.getBbHeight() * 0.5D, 0.0D);
        if (!UnknownMedievalCombatMath.overheadLaneContains(
                origin,
                boss.getLockedCombatDirection(),
                targetCenter(target),
                SWORD_REACH,
                OVERHEAD_HIT_RADIUS + target.getBbWidth() * 0.5D,
                target.getBbHeight() * 0.75D)) {
            return;
        }
        boolean blocked = playerBlocks(target, boss.getLockedCombatDirection().reverse());
        float damage = blocked ? OVERHEAD_BLOCKED_DAMAGE : OVERHEAD_DAMAGE;
        if (blocked) {
            ItemStack blockingItem = target.getUseItem();
            target.stopUsingItem();
            if (!blockingItem.isEmpty()) {
                target.getCooldowns().addCooldown(blockingItem, OVERHEAD_SHIELD_DISABLE_TICKS);
            }
        }
        boolean damaged = target.hurtServer(level, boss.damageSources().mobAttack(boss), damage);
        showImpact(level, target, blocked, damaged);
    }

    private void tryShieldBashHit(ServerLevel level, ServerPlayer target) {
        Vec3 origin = boss.position().add(0.0D, boss.getBbHeight() * 0.52D, 0.0D);
        if (!UnknownMedievalCombatMath.meleeArcContains(
                origin,
                boss.getLockedCombatDirection(),
                targetCenter(target),
                SHIELD_BASH_REACH,
                target.getBbWidth() * 0.5D,
                100.0D,
                target.getBbHeight() * 0.75D)) {
            return;
        }
        boolean blocked = playerBlocks(target, boss.getLockedCombatDirection().reverse());
        if (blocked) {
            target.stopUsingItem();
        }
        boolean damaged = target.hurtServer(
                level,
                boss.damageSources().mobAttack(boss),
                SHIELD_BASH_DAMAGE);
        Vec3 push = UnknownGreekCombatMath.horizontalDirection(
                boss.position(), target.position(), boss.getLockedCombatDirection());
        target.push(
                push.x * SHIELD_BASH_KNOCKBACK,
                0.26D,
                push.z * SHIELD_BASH_KNOCKBACK);
        target.hurtMarked = true;
        showImpact(level, target, blocked, damaged);
    }

    private void tryRiposteHit(ServerLevel level, ServerPlayer target) {
        Vec3 origin = boss.position().add(0.0D, boss.getBbHeight() * 0.52D, 0.0D);
        if (!UnknownMedievalCombatMath.meleeArcContains(
                origin,
                boss.getLockedCombatDirection(),
                targetCenter(target),
                SWORD_REACH,
                target.getBbWidth() * 0.5D,
                82.0D,
                target.getBbHeight() * 0.75D)) {
            return;
        }
        boolean blocked = playerBlocks(target, boss.getLockedCombatDirection().reverse());
        float damage = blocked ? RIPOSTE_BLOCKED_DAMAGE : RIPOSTE_DAMAGE;
        if (blocked) {
            target.stopUsingItem();
        }
        boolean damaged = target.hurtServer(level, boss.damageSources().mobAttack(boss), damage);
        showImpact(level, target, blocked, damaged);
    }

    private void showImpact(
            ServerLevel level,
            ServerPlayer target,
            boolean blocked,
            boolean damaged) {
        boss.showCombatFx(
                blocked ? UnknownEntity.COMBAT_FX_PLAYER_BLOCK : UnknownEntity.COMBAT_FX_HIT,
                target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D),
                level.getGameTime());
        level.playSound(
                null,
                target.blockPosition(),
                blocked ? SoundEvents.SHIELD_BLOCK.value() : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE,
                blocked ? 1.15F : 0.95F,
                damaged ? 0.82F : 0.94F);
    }

    private void beginAction(
            ServerLevel level,
            Attack attack,
            UnknownCombatState state) {
        holdPosition();
        currentAttack = attack;
        boss.beginGreekCombatState(state, level.getGameTime(), false);
        boss.setCombatAnchor(boss.position(), offensiveChain, actionSerial);
    }

    private void completeAction(boolean offensive) {
        lastAttack = currentAttack;
        currentAttack = Attack.NONE;
        actionSerial++;
        if (offensive) {
            offensiveChain++;
            if (offensiveChain >= MAX_OFFENSIVE_CHAIN) {
                attackDelay = FORCED_BREATHER_TICKS;
                offensiveChain = 0;
            } else {
                attackDelay = NEUTRAL_DELAY_TICKS;
            }
        } else {
            offensiveChain = 0;
            attackDelay = NEUTRAL_DELAY_TICKS;
        }
        boss.resetGreekCombat();
    }

    private void holdPosition() {
        boss.getNavigation().stop();
        boss.setDeltaMovement(0.0D, boss.getDeltaMovement().y, 0.0D);
    }

    private static int comboEndTick(byte variant) {
        return variant == UnknownEntity.COMBAT_VARIANT_MEDIEVAL_CHASE
                ? COMBO_CHASE_END_TICK
                : COMBO_SWEEP_END_TICK;
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * Math.clamp(progress, 0.0D, 1.0D);
    }

    private Vec3 directionTo(Entity target) {
        return UnknownGreekCombatMath.horizontalDirection(
                boss.position(), target.position(), boss.getLookAngle());
    }

    private void face(Vec3 point) {
        faceDirection(UnknownGreekCombatMath.horizontalDirection(
                boss.position(), point, boss.getLookAngle()));
        boss.getLookControl().setLookAt(point.x, point.y + 1.0D, point.z, 32.0F, 28.0F);
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

    private boolean playerBlocks(ServerPlayer target, Vec3 incomingFromTarget) {
        return target.isBlocking()
                && UnknownGreekCombatMath.isInsideFrontArc(
                        target.getLookAngle(), incomingFromTarget, 180.0D);
    }

    private static Vec3 targetCenter(ServerPlayer target) {
        AABB bounds = target.getBoundingBox();
        return new Vec3(target.getX(), (bounds.minY + bounds.maxY) * 0.5D, target.getZ());
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return Math.sqrt(x * x + z * z);
    }

    public static boolean isGuardActive(UnknownCombatState state, int elapsed) {
        return state == UnknownCombatState.MEDIEVAL_GUARD
                && elapsed >= GUARD_WINDUP_TICKS
                && elapsed < GUARD_WINDUP_TICKS + GUARD_ACTIVE_TICKS;
    }

    /** Called by the incoming-damage event after a legal melee guard. */
    public static boolean beginRiposte(
            UnknownEntity boss,
            ServerLevel level,
            Entity attacker) {
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        if (!isGuardActive(boss.getCombatState(), elapsed) || attacker == null) {
            return false;
        }
        Vec3 direction = UnknownGreekCombatMath.horizontalDirection(
                boss.position(), attacker.position(), boss.getLookAngle());
        boss.stopCombatAnimation("medieval_guard");
        boss.setLockedCombatDirection(direction);
        boss.setCombatAnchor(boss.position(), 0.0D, 0.0D);
        boss.beginGreekCombatState(
                UnknownCombatState.MEDIEVAL_RIPOSTE,
                level.getGameTime(),
                false);
        boss.triggerGreekAnimation("medieval_riposte");
        return true;
    }

    public static int neutralDelayAfterOffense(int completedOffensiveChain) {
        return completedOffensiveChain >= MAX_OFFENSIVE_CHAIN
                ? FORCED_BREATHER_TICKS
                : NEUTRAL_DELAY_TICKS;
    }

    public static boolean shouldTriggerRiposte(
            boolean activeGuard,
            boolean projectile,
            boolean ownerMeleeAttack,
            double attackerDistance) {
        return activeGuard
                && !projectile
                && ownerMeleeAttack
                && attackerDistance <= 4.25D;
    }

    public static Attack chooseAttack(
            double distance,
            boolean targetBlocking,
            int bashCooldown,
            int defenseCooldown,
            int currentOffensiveChain,
            int serial,
            Attack last) {
        if (currentOffensiveChain >= MAX_OFFENSIVE_CHAIN) {
            return Attack.NONE;
        }
        if (bashCooldown <= 0
                && distance <= SHIELD_BASH_REACH + 0.35D
                && (targetBlocking || distance <= BASH_PRIORITY_RANGE)) {
            return Attack.SHIELD_BASH;
        }
        if (defenseCooldown <= 0
                && currentOffensiveChain > 0
                && distance <= GUARD_SELECTION_RANGE
                && last != Attack.GUARD
                && Math.floorMod(serial, 3) == 1) {
            return Attack.GUARD;
        }
        if (distance > ATTACK_SELECTION_RANGE) {
            return Attack.NONE;
        }
        Attack preferred = (serial & 1) == 0 ? Attack.COMBO : Attack.OVERHEAD;
        if (preferred == last) {
            preferred = preferred == Attack.COMBO ? Attack.OVERHEAD : Attack.COMBO;
        }
        return preferred;
    }

    public enum Attack {
        NONE,
        COMBO,
        OVERHEAD,
        SHIELD_BASH,
        GUARD
    }
}
