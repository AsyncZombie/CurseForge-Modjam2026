package dev.alvar.echoespast.entity.ai;

import dev.alvar.echoespast.entity.MedusaEntity;
import dev.alvar.echoespast.entity.combat.MedusaBossMath;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;

/**
 * Close-range snake strike plus a held petrify pose when the target is staring.
 * Hits and poison are server-authoritative; GeckoLib only mirrors the pose.
 */
public final class MedusaCombatGoal extends Goal {
    private static final double MELEE_RANGE = 3.15D;
    private static final double PETRIFY_RANGE = MedusaBossMath.GAZE_RANGE;

    private final MedusaEntity medusa;
    private int snakeTicks = -1;
    private int petrifyTicks = -1;
    private int snakeCooldown;

    public MedusaCombatGoal(MedusaEntity medusa) {
        this.medusa = medusa;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = medusa.getTarget();
        return target != null && target.isAlive() && !medusa.isDeadOrDying();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        snakeTicks = -1;
        petrifyTicks = -1;
    }

    @Override
    public void stop() {
        snakeTicks = -1;
        petrifyTicks = -1;
        medusa.setCombatMove(MedusaEntity.MOVE_NEUTRAL);
        medusa.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = medusa.getTarget();
        if (target == null || !(medusa.level() instanceof ServerLevel)) {
            return;
        }
        if (snakeCooldown > 0) {
            snakeCooldown--;
        }
        medusa.getLookControl().setLookAt(target, 40.0F, 40.0F);
        if (snakeTicks >= 0) {
            tickSnake(target);
            return;
        }
        if (petrifyTicks >= 0) {
            tickPetrify(target);
            return;
        }

        double distanceSqr = medusa.distanceToSqr(target);
        boolean caught = medusa.isCaughtByGaze(target);
        if (caught && distanceSqr <= PETRIFY_RANGE * PETRIFY_RANGE) {
            beginPetrify();
            return;
        }
        if (snakeCooldown == 0 && distanceSqr <= MELEE_RANGE * MELEE_RANGE) {
            beginSnake();
            return;
        }
        medusa.setCombatMove(MedusaEntity.MOVE_NEUTRAL);
        PathNavigation navigation = medusa.getNavigation();
        if (navigation.getPath() == null || navigation.isDone()) {
            navigation.moveTo(target, 1.0D);
        }
    }

    private void beginSnake() {
        snakeTicks = 0;
        petrifyTicks = -1;
        medusa.getNavigation().stop();
        medusa.setCombatMove(MedusaEntity.MOVE_SNAKE);
        medusa.triggerCombatAnimation("snake_attack");
    }

    private void tickSnake(LivingEntity target) {
        snakeTicks++;
        medusa.getNavigation().stop();
        if (MedusaBossMath.isSnakeHitTick(snakeTicks)) {
            Vec3 look = medusa.getLookAngle();
            medusa.setDeltaMovement(look.x * 0.22D, 0.04D, look.z * 0.22D);
            medusa.trySnakeStrike(target);
        }
        if (snakeTicks >= MedusaBossMath.SNAKE_ATTACK_TICKS) {
            snakeTicks = -1;
            snakeCooldown = MedusaBossMath.SNAKE_RECOVERY_TICKS;
            medusa.setCombatMove(MedusaEntity.MOVE_NEUTRAL);
        }
    }

    private void beginPetrify() {
        petrifyTicks = 0;
        snakeTicks = -1;
        medusa.getNavigation().stop();
        medusa.setCombatMove(MedusaEntity.MOVE_PETRIFY);
        medusa.triggerCombatAnimation("petrify_attack");
    }

    private void tickPetrify(LivingEntity target) {
        petrifyTicks++;
        medusa.getNavigation().stop();
        boolean caught = medusa.isCaughtByGaze(target);
        boolean hold = caught && petrifyTicks >= MedusaBossMath.PETRIFY_ATTACK_MIN_TICKS;
        if (!caught && petrifyTicks >= MedusaBossMath.PETRIFY_ATTACK_MIN_TICKS) {
            petrifyTicks = -1;
            medusa.setCombatMove(MedusaEntity.MOVE_NEUTRAL);
            medusa.stopCombatAnimation("petrify_attack");
        } else if (hold && petrifyTicks % MedusaBossMath.PETRIFY_ATTACK_MIN_TICKS == 0) {
            medusa.triggerCombatAnimation("petrify_attack");
        }
    }
}
