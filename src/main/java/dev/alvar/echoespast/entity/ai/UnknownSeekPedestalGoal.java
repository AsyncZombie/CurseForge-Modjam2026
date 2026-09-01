package dev.alvar.echoespast.entity.ai;

import dev.alvar.echoespast.entity.UnknownEntity;
import dev.alvar.echoespast.server.UnknownFightManager;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

public final class UnknownSeekPedestalGoal extends Goal {
    private final UnknownEntity mob;
    private BlockPos targetPedestal;
    private int repathTicks;

    public UnknownSeekPedestalGoal(UnknownEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.isDummy() || !mob.isVulnerableVoid()) {
            return false;
        }
        targetPedestal = UnknownFightManager.nextPedestalFor(mob);
        return targetPedestal != null;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.isVulnerableVoid()
                && targetPedestal != null
                && UnknownFightManager.wantsPedestalMaterialize(mob);
    }

    @Override
    public void start() {
        if (targetPedestal != null) {
            repathTicks = 0;
            UnknownFightManager.repathToPedestal(mob, targetPedestal, 1.15D);
        }
    }

    @Override
    public void tick() {
        if (targetPedestal == null) {
            return;
        }
        mob.getLookControl().setLookAt(
                targetPedestal.getX() + 0.5D,
                targetPedestal.getY() + 1.0D,
                targetPedestal.getZ() + 0.5D);
        if (UnknownFightManager.isWithinPedestalChannelRange(mob, targetPedestal)) {
            UnknownFightManager.materializeAtPedestal(mob, targetPedestal);
        } else if (--repathTicks <= 0 || mob.getNavigation().isDone()) {
            UnknownFightManager.repathToPedestal(mob, targetPedestal, 1.15D);
            repathTicks = 10;
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
