package dev.alvar.echoespast.entity.ai;

import dev.alvar.echoespast.entity.MedievalRubbleProjectile;
import dev.alvar.echoespast.entity.UnknownEntity;
import dev.alvar.echoespast.entity.combat.UnknownBossMovementSafety;
import dev.alvar.echoespast.entity.combat.UnknownCombatState;
import dev.alvar.echoespast.entity.combat.UnknownGreekCombatMath;
import dev.alvar.echoespast.entity.combat.UnknownMedievalCombatMath;
import dev.alvar.echoespast.server.UnknownFightManager;
import dev.alvar.echoespast.server.UnknownMedievalRuinsArena;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Aggressive two-handed Medieval Ruins controller. */
public final class UnknownMedievalRuinsCombatGoal extends Goal {
    public static final float ANIMATION_SPEED = 1.12F;

    public static final int COMBO_FIRST_HIT_TICK = 12;
    public static final int COMBO_SECOND_HIT_TICK = 23;
    public static final int COMBO_THIRD_HIT_TICK = 38;
    public static final int COMBO_RECOVERY_TICKS = 16;
    public static final float COMBO_FIRST_DAMAGE = 6.0F;
    public static final float COMBO_SECOND_DAMAGE = 7.0F;
    public static final float COMBO_THIRD_DAMAGE = 8.0F;
    public static final double SWORD_REACH = 3.2D;
    public static final double SWORD_ARC_DEGREES = 116.0D;

    public static final int OVERHEAD_LOCK_TICK = 12;
    public static final int OVERHEAD_HIT_TICK = 20;
    public static final int OVERHEAD_RECOVERY_TICKS = 18;
    public static final float OVERHEAD_DAMAGE = 10.0F;
    public static final double OVERHEAD_LANE_RADIUS = 0.76D;

    public static final int SHOULDER_RUSH_WINDUP_TICKS = 14;
    public static final int SHOULDER_RUSH_DASH_TICKS = 7;
    public static final int SHOULDER_RUSH_RECOVERY_TICKS = 14;
    public static final int SHOULDER_RUSH_COOLDOWN_TICKS = 45;
    public static final double SHOULDER_RUSH_MAX_DISTANCE = 4.5D;
    public static final float SHOULDER_RUSH_DAMAGE = 6.0F;
    public static final double SHOULDER_RUSH_KNOCKBACK = 1.18D;

    public static final double RUBBLE_MIN_TARGET_DISTANCE = 5.0D;
    public static final double RUBBLE_MAX_TARGET_DISTANCE = 12.0D;
    public static final double RUBBLE_MAX_BOSS_DISTANCE = 3.4D;
    public static final int RUBBLE_KICK_LOCK_TICK = 12;
    public static final int RUBBLE_KICK_WINDUP_TICKS = 18;
    public static final int RUBBLE_KICK_RECOVERY_TICKS = 14;
    public static final int RUBBLE_KICK_COOLDOWN_TICKS = 70;

    public static final int INITIAL_DELAY_TICKS = 8;
    public static final int NEUTRAL_DELAY_TICKS = 8;
    public static final int FORCED_BREATHER_TICKS = 14;
    public static final int MAX_OFFENSIVE_CHAIN = 2;

    private static final double APPROACH_STOP_RANGE = 3.05D;
    private static final double MELEE_SELECTION_RANGE = 3.65D;
    private static final double RUSH_MIN_DISTANCE = 3.35D;
    private static final double RUSH_MAX_TARGET_DISTANCE = 7.25D;
    private static final double MOVE_SPEED = 1.03D;

    private final UnknownEntity boss;
    private int attackDelay;
    private int rushCooldown;
    private int rubbleCooldown;
    private int offensiveChain;
    private int actionSerial;
    private boolean firstHit;
    private boolean secondHit;
    private boolean thirdHit;
    private boolean actionHit;
    private boolean projectileSpawned;
    private Attack currentAttack = Attack.NONE;
    private Attack lastAttack = Attack.NONE;

    public UnknownMedievalRuinsCombatGoal(UnknownEntity boss) {
        this.boss = boss;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return UnknownFightManager.medievalRuinsCombatTarget(boss) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        attackDelay = INITIAL_DELAY_TICKS;
        rushCooldown = 0;
        rubbleCooldown = 0;
        offensiveChain = 0;
        actionSerial = 0;
        currentAttack = Attack.NONE;
        lastAttack = Attack.NONE;
        resetHitFlags();
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
        ServerPlayer target = UnknownFightManager.medievalRuinsCombatTarget(boss);
        if (target == null) {
            stop();
            return;
        }
        boss.setTarget(target);
        rushCooldown = Math.max(0, rushCooldown - 1);
        rubbleCooldown = Math.max(0, rubbleCooldown - 1);

        switch (boss.getCombatState()) {
            case NEUTRAL -> tickNeutral(level, target);
            case MEDIEVAL_COMBO -> tickCombo(level, target);
            case MEDIEVAL_OVERHEAD -> tickOverhead(level, target);
            case MEDIEVAL_SHOULDER_RUSH -> tickShoulderRush(level, target);
            case MEDIEVAL_RUBBLE_KICK -> tickRubbleKick(level, target);
            default -> boss.resetGreekCombat();
        }
    }

    private void tickNeutral(ServerLevel level, ServerPlayer target) {
        double distance = horizontalDistance(boss.position(), target.position());
        face(target.position());
        Optional<BlockPos> rubbleMarker = Optional.empty();
        if (rubbleCooldown == 0
                && distance >= RUBBLE_MIN_TARGET_DISTANCE
                && distance <= RUBBLE_MAX_TARGET_DISTANCE) {
            rubbleMarker = UnknownMedievalRuinsArena.selectRubbleMarker(
                    level,
                    boss,
                    target,
                    RUBBLE_MAX_BOSS_DISTANCE);
        }
        Optional<Vec3> rushAnchor = Optional.empty();
        if (rushCooldown == 0
                && distance >= RUSH_MIN_DISTANCE
                && distance <= RUSH_MAX_TARGET_DISTANCE) {
            rushAnchor = resolveRushAnchor(level, target);
        }

        if (distance > APPROACH_STOP_RANGE && rubbleMarker.isEmpty()) {
            boss.getNavigation().moveTo(target, MOVE_SPEED);
        } else {
            boss.getNavigation().stop();
        }
        if (attackDelay-- > 0) {
            return;
        }

        Attack selected = chooseAttack(
                distance,
                rubbleMarker.isPresent(),
                rubbleCooldown,
                rushAnchor.isPresent(),
                rushCooldown,
                actionSerial,
                lastAttack);
        switch (selected) {
            case COMBO -> beginCombo(level, target);
            case OVERHEAD -> beginOverhead(level, target);
            case SHOULDER_RUSH -> beginShoulderRush(level, target, rushAnchor.orElseThrow());
            case RUBBLE_KICK -> beginRubbleKick(
                    level,
                    target,
                    rubbleMarker.orElseThrow());
            case NONE -> attackDelay = 3;
        }
    }

    private void beginCombo(ServerLevel level, ServerPlayer target) {
        beginAction(level, Attack.COMBO, UnknownCombatState.MEDIEVAL_COMBO);
        resetHitFlags();
        boss.setLockedCombatDirection(directionTo(target));
        boss.triggerGreekAnimation("medieval_combo_ruins");
        level.playSound(null, boss.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                SoundSource.HOSTILE, 0.82F, 1.12F);
    }

    private void tickCombo(ServerLevel level, ServerPlayer target) {
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        holdPosition();
        if (elapsed < COMBO_FIRST_HIT_TICK - 2
                || (elapsed > COMBO_FIRST_HIT_TICK + 2
                        && elapsed < COMBO_SECOND_HIT_TICK - 2)
                || (elapsed > COMBO_SECOND_HIT_TICK + 2
                        && elapsed < COMBO_THIRD_HIT_TICK - 4)) {
            face(target.position());
            boss.setLockedCombatDirection(directionTo(target));
        } else {
            faceDirection(boss.getLockedCombatDirection());
        }
        if (elapsed == COMBO_FIRST_HIT_TICK && !firstHit) {
            firstHit = true;
            trySwordArcHit(level, target, COMBO_FIRST_DAMAGE);
        }
        if (elapsed == COMBO_SECOND_HIT_TICK && !secondHit) {
            secondHit = true;
            trySwordArcHit(level, target, COMBO_SECOND_DAMAGE);
        }
        if (elapsed == COMBO_THIRD_HIT_TICK && !thirdHit) {
            thirdHit = true;
            trySwordArcHit(level, target, COMBO_THIRD_DAMAGE);
        }
        if (elapsed >= COMBO_THIRD_HIT_TICK + COMBO_RECOVERY_TICKS) {
            completeAction();
        }
    }

    private void beginOverhead(ServerLevel level, ServerPlayer target) {
        beginAction(level, Attack.OVERHEAD, UnknownCombatState.MEDIEVAL_OVERHEAD);
        actionHit = false;
        boss.setLockedCombatDirection(directionTo(target));
        boss.triggerGreekAnimation("medieval_overhead");
        level.playSound(null, boss.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                SoundSource.HOSTILE, 0.92F, 0.76F);
    }

    private void tickOverhead(ServerLevel level, ServerPlayer target) {
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        holdPosition();
        if (elapsed < OVERHEAD_LOCK_TICK) {
            face(target.position());
        } else if (elapsed == OVERHEAD_LOCK_TICK) {
            boss.setLockedCombatDirection(directionTo(target));
        }
        faceDirection(boss.getLockedCombatDirection());
        if (elapsed == OVERHEAD_HIT_TICK && !actionHit) {
            actionHit = true;
            tryOverheadHit(level, target);
            spawnOverheadDebris(level);
        }
        if (elapsed >= OVERHEAD_HIT_TICK + OVERHEAD_RECOVERY_TICKS) {
            completeAction();
        }
    }

    private void beginShoulderRush(
            ServerLevel level,
            ServerPlayer target,
            Vec3 anchor) {
        beginAction(level, Attack.SHOULDER_RUSH, UnknownCombatState.MEDIEVAL_SHOULDER_RUSH);
        actionHit = false;
        rushCooldown = SHOULDER_RUSH_COOLDOWN_TICKS;
        boss.setLockedCombatDirection(directionTo(target));
        boss.setCombatAnchor(anchor, 0.0D, SHOULDER_RUSH_MAX_DISTANCE);
        boss.triggerGreekAnimation("medieval_shoulder_rush");
        level.playSound(null, boss.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                SoundSource.HOSTILE, 1.0F, 0.62F);
    }

    private void tickShoulderRush(ServerLevel level, ServerPlayer target) {
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        boss.getNavigation().stop();
        faceDirection(boss.getLockedCombatDirection());
        if (elapsed < SHOULDER_RUSH_WINDUP_TICKS) {
            holdPosition();
            if (elapsed < 10) {
                face(target.position());
            }
            return;
        }
        int dashEnd = SHOULDER_RUSH_WINDUP_TICKS + SHOULDER_RUSH_DASH_TICKS;
        if (elapsed < dashEnd) {
            Vec3 remaining = boss.getCombatAnchor().subtract(boss.position());
            Vec3 flat = new Vec3(remaining.x, 0.0D, remaining.z);
            int remainingTicks = Math.max(1, dashEnd - elapsed);
            double stepLength = Math.min(0.76D, flat.horizontalDistance() / remainingTicks);
            if (stepLength > 1.0E-5D) {
                UnknownBossMovementSafety.moveDashStep(
                        level,
                        boss,
                        flat.normalize().scale(stepLength));
            }
            tryShoulderHit(level, target);
            return;
        }
        holdPosition();
        if (elapsed >= dashEnd + SHOULDER_RUSH_RECOVERY_TICKS) {
            completeAction();
        }
    }

    private void beginRubbleKick(
            ServerLevel level,
            ServerPlayer target,
            BlockPos marker) {
        beginAction(level, Attack.RUBBLE_KICK, UnknownCombatState.MEDIEVAL_RUBBLE_KICK);
        projectileSpawned = false;
        rubbleCooldown = RUBBLE_KICK_COOLDOWN_TICKS;
        Vec3 origin = UnknownMedievalRuinsArena.projectileOrigin(marker);
        boss.setCombatAnchor(origin, 0.0D, 0.0D);
        boss.setLockedCombatDirection(projectileDirection(origin, target));
        boss.triggerGreekAnimation("medieval_rubble_kick");
        level.playSound(null, marker, SoundEvents.STONE_HIT,
                SoundSource.HOSTILE, 0.9F, 0.72F);
    }

    private void tickRubbleKick(ServerLevel level, ServerPlayer target) {
        int elapsed = boss.combatElapsedTicks(level.getGameTime());
        holdPosition();
        if (elapsed < RUBBLE_KICK_LOCK_TICK) {
            face(target.position());
            boss.setLockedCombatDirection(projectileDirection(boss.getCombatAnchor(), target));
        } else {
            faceDirection(boss.getLockedCombatDirection());
        }
        if (elapsed == RUBBLE_KICK_WINDUP_TICKS && !projectileSpawned) {
            projectileSpawned = true;
            MedievalRubbleProjectile rubble = new MedievalRubbleProjectile(
                    level,
                    boss,
                    boss.getCombatAnchor(),
                    boss.getLockedCombatDirection());
            level.addFreshEntity(rubble);
            level.playSound(null, BlockPos.containing(boss.getCombatAnchor()),
                    SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 0.95F, 0.82F);
        }
        if (elapsed >= RUBBLE_KICK_WINDUP_TICKS + RUBBLE_KICK_RECOVERY_TICKS) {
            completeAction();
        }
    }

    private Optional<Vec3> resolveRushAnchor(ServerLevel level, ServerPlayer target) {
        Vec3 direction = directionTo(target);
        double distance = Math.min(
                SHOULDER_RUSH_MAX_DISTANCE,
                Math.max(0.0D, horizontalDistance(boss.position(), target.position()) - 0.7D));
        if (distance < 1.5D) {
            return Optional.empty();
        }
        return UnknownBossMovementSafety.resolveStraightDashAnchor(
                level,
                boss,
                boss.position(),
                boss.position().add(direction.scale(distance)));
    }

    private void trySwordArcHit(ServerLevel level, ServerPlayer target, float damage) {
        Vec3 origin = boss.position().add(0.0D, boss.getBbHeight() * 0.52D, 0.0D);
        if (!UnknownMedievalCombatMath.meleeArcContains(
                origin,
                boss.getLockedCombatDirection(),
                targetCenter(target),
                SWORD_REACH,
                target.getBbWidth() * 0.5D,
                SWORD_ARC_DEGREES,
                target.getBbHeight() * 0.75D)) {
            return;
        }
        boolean blocked = playerBlocks(target, boss.getLockedCombatDirection().reverse());
        boolean damaged = target.hurtServer(level, boss.damageSources().mobAttack(boss), damage);
        showImpact(level, target, blocked, damaged);
    }

    private void tryOverheadHit(ServerLevel level, ServerPlayer target) {
        Vec3 origin = boss.position().add(0.0D, boss.getBbHeight() * 0.5D, 0.0D);
        if (!UnknownMedievalCombatMath.overheadLaneContains(
                origin,
                boss.getLockedCombatDirection(),
                targetCenter(target),
                SWORD_REACH,
                OVERHEAD_LANE_RADIUS + target.getBbWidth() * 0.5D,
                target.getBbHeight() * 0.75D)) {
            return;
        }
        boolean blocked = playerBlocks(target, boss.getLockedCombatDirection().reverse());
        boolean damaged = target.hurtServer(
                level,
                boss.damageSources().mobAttack(boss),
                OVERHEAD_DAMAGE);
        showImpact(level, target, blocked, damaged);
    }

    private void tryShoulderHit(ServerLevel level, ServerPlayer target) {
        if (actionHit || !boss.getBoundingBox().inflate(0.38D, 0.12D, 0.38D)
                .intersects(target.getBoundingBox())) {
            return;
        }
        actionHit = true;
        boolean blocked = playerBlocks(target, boss.getLockedCombatDirection().reverse());
        boolean damaged = target.hurtServer(
                level,
                boss.damageSources().mobAttack(boss),
                SHOULDER_RUSH_DAMAGE);
        Vec3 push = boss.getLockedCombatDirection();
        target.push(
                push.x * SHOULDER_RUSH_KNOCKBACK,
                0.22D,
                push.z * SHOULDER_RUSH_KNOCKBACK);
        target.hurtMarked = true;
        showImpact(level, target, blocked, damaged);
    }

    private void spawnOverheadDebris(ServerLevel level) {
        Vec3 impact = boss.position().add(boss.getLockedCombatDirection().scale(2.15D));
        level.sendParticles(
                new BlockParticleOption(
                        ParticleTypes.BLOCK,
                        Blocks.COBBLESTONE.defaultBlockState()),
                impact.x,
                impact.y + 0.12D,
                impact.z,
                16,
                0.42D,
                0.08D,
                0.42D,
                0.075D);
    }

    private void showImpact(
            ServerLevel level,
            ServerPlayer target,
            boolean blocked,
            boolean damaged) {
        boss.showCombatFx(
                blocked ? UnknownEntity.COMBAT_FX_PLAYER_BLOCK : UnknownEntity.COMBAT_FX_HIT,
                target.position().add(0.0D, target.getBbHeight() * 0.52D, 0.0D),
                level.getGameTime());
        level.playSound(
                null,
                target.blockPosition(),
                blocked ? SoundEvents.SHIELD_BLOCK.value() : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE,
                blocked ? 1.15F : 1.0F,
                damaged ? 0.76F : 0.92F);
    }

    private void beginAction(
            ServerLevel level,
            Attack attack,
            UnknownCombatState state) {
        holdPosition();
        currentAttack = attack;
        boss.beginGreekCombatState(state, level.getGameTime(), true);
        boss.setCombatAnchor(boss.position(), offensiveChain, actionSerial);
    }

    private void completeAction() {
        lastAttack = currentAttack;
        currentAttack = Attack.NONE;
        actionSerial++;
        offensiveChain++;
        if (offensiveChain >= MAX_OFFENSIVE_CHAIN) {
            offensiveChain = 0;
            attackDelay = FORCED_BREATHER_TICKS;
        } else {
            attackDelay = NEUTRAL_DELAY_TICKS;
        }
        boss.resetGreekCombat();
    }

    private void resetHitFlags() {
        firstHit = false;
        secondHit = false;
        thirdHit = false;
        actionHit = false;
        projectileSpawned = false;
    }

    private void holdPosition() {
        boss.getNavigation().stop();
        boss.setDeltaMovement(0.0D, boss.getDeltaMovement().y, 0.0D);
    }

    private Vec3 directionTo(Entity target) {
        return UnknownGreekCombatMath.horizontalDirection(
                boss.position(), target.position(), boss.getLookAngle());
    }

    private static Vec3 projectileDirection(Vec3 origin, ServerPlayer target) {
        Vec3 targetPoint = target.position().add(0.0D, target.getBbHeight() * 0.38D, 0.0D);
        Vec3 direction = targetPoint.subtract(origin);
        return direction.lengthSqr() <= 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
    }

    private void face(Vec3 point) {
        faceDirection(UnknownGreekCombatMath.horizontalDirection(
                boss.position(), point, boss.getLookAngle()));
        boss.getLookControl().setLookAt(point.x, point.y + 1.0D, point.z, 38.0F, 32.0F);
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

    private static boolean playerBlocks(ServerPlayer target, Vec3 incomingFromTarget) {
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

    public static Attack chooseAttack(
            double distance,
            boolean rubbleMarkerAvailable,
            int rubbleCooldown,
            boolean rushPathAvailable,
            int rushCooldown,
            int serial,
            Attack lastAttack) {
        if (rubbleMarkerAvailable
                && rubbleCooldown <= 0
                && distance >= RUBBLE_MIN_TARGET_DISTANCE
                && distance <= RUBBLE_MAX_TARGET_DISTANCE
                && lastAttack != Attack.RUBBLE_KICK) {
            return Attack.RUBBLE_KICK;
        }
        if (rushPathAvailable
                && rushCooldown <= 0
                && distance >= RUSH_MIN_DISTANCE
                && distance <= RUSH_MAX_TARGET_DISTANCE
                && lastAttack != Attack.SHOULDER_RUSH
                && (serial & 1) == 1) {
            return Attack.SHOULDER_RUSH;
        }
        if (distance > MELEE_SELECTION_RANGE) {
            return Attack.NONE;
        }
        if (lastAttack == Attack.COMBO) {
            return Attack.OVERHEAD;
        }
        return Math.floorMod(serial, 3) == 1 ? Attack.OVERHEAD : Attack.COMBO;
    }

    public enum Attack {
        NONE,
        COMBO,
        OVERHEAD,
        SHOULDER_RUSH,
        RUBBLE_KICK
    }
}
