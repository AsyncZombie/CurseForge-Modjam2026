package dev.alvar.echoespast.relic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alvar.echoespast.snapshot.SnapshotAnimation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

/**
 * Persistent renderer-driving state for a Medusa statue.
 *
 * <p>Vanilla entity NBT already stores type-specific state such as armor-stand
 * limb rotations and equipment. These fields cover the transient animation
 * inputs that vanilla normally advances every tick.</p>
 */
public record PetrifiedPose(
        boolean permanent,
        Pose pose,
        int ageInTicks,
        float yRot,
        float xRot,
        float bodyYRot,
        float headYRot,
        SnapshotAnimation animation,
        BakedModelPose modelPose,
        boolean headless) {

    public static final PetrifiedPose EMPTY = new PetrifiedPose(
            false,
            Pose.STANDING,
            0,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            SnapshotAnimation.NONE,
            BakedModelPose.EMPTY,
            false);

    public static final Codec<PetrifiedPose> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("permanent", false).forGetter(PetrifiedPose::permanent),
            Pose.CODEC.optionalFieldOf("pose", Pose.STANDING).forGetter(PetrifiedPose::pose),
            Codec.INT.optionalFieldOf("age_in_ticks", 0).forGetter(PetrifiedPose::ageInTicks),
            Codec.FLOAT.optionalFieldOf("y_rot", 0.0F).forGetter(PetrifiedPose::yRot),
            Codec.FLOAT.optionalFieldOf("x_rot", 0.0F).forGetter(PetrifiedPose::xRot),
            Codec.FLOAT.optionalFieldOf("body_y_rot", 0.0F).forGetter(PetrifiedPose::bodyYRot),
            Codec.FLOAT.optionalFieldOf("head_y_rot", 0.0F).forGetter(PetrifiedPose::headYRot),
            SnapshotAnimation.CODEC.optionalFieldOf("animation", SnapshotAnimation.NONE)
                    .forGetter(PetrifiedPose::animation),
            BakedModelPose.CODEC.optionalFieldOf("model_pose", BakedModelPose.EMPTY)
                    .forGetter(PetrifiedPose::modelPose),
            Codec.BOOL.optionalFieldOf("headless", false).forGetter(PetrifiedPose::headless)
    ).apply(instance, PetrifiedPose::new));

    public static PetrifiedPose capture(LivingEntity living) {
        boolean usingItem = living.isUsingItem();
        SnapshotAnimation animation = new SnapshotAnimation(
                living.walkAnimation.position(),
                living.walkAnimation.speed(),
                living.getAttackAnim(1.0F),
                living.swinging,
                living.swingingArm == InteractionHand.OFF_HAND,
                living.swingTime,
                usingItem,
                usingItem && living.getUsedItemHand() == InteractionHand.OFF_HAND,
                usingItem ? living.getUseItemRemainingTicks() : 0,
                living instanceof Mob mob && mob.isAggressive());
        return new PetrifiedPose(
                true,
                living.getPose(),
                living.tickCount,
                living.getYRot(),
                living.getXRot(),
                living.yBodyRot,
                living.yHeadRot,
                animation,
                BakedModelPose.EMPTY,
                false);
    }

    public PetrifiedPose withModelPose(BakedModelPose bakedPose) {
        return new PetrifiedPose(
                permanent,
                pose,
                ageInTicks,
                yRot,
                xRot,
                bodyYRot,
                headYRot,
                animation,
                bakedPose,
                headless);
    }

    public PetrifiedPose withHeadless(boolean withoutHead) {
        return new PetrifiedPose(
                permanent,
                pose,
                ageInTicks,
                yRot,
                xRot,
                bodyYRot,
                headYRot,
                animation,
                modelPose,
                withoutHead);
    }

    /**
     * Reapplies fields available on both logical sides. The client supplements
     * this with exact private walk-animation fields before render extraction.
     */
    public void freezeCommon(LivingEntity living) {
        if (!permanent) {
            return;
        }
        clearTransientVisualState(living);
        // Vanilla combat targeting rejects invulnerable living entities.
        // A statue already cancels all normal damage; expressing the same
        // invariant here also prevents goal- and brain-based AI from
        // acquiring or retaining it as an attack target.
        living.setInvulnerable(true);
        living.setDeltaMovement(Vec3.ZERO);
        // Sleeping is a visual pose backed by a live bed relationship and a
        // deliberately tiny 0.2 x 0.2 vanilla hitbox. The renderer receives
        // the captured pose separately; physics must remain detached.
        living.clearSleepingPos();
        living.stopRiding();
        living.ejectPassengers();
        if (living instanceof Leashable leashable) {
            leashable.setLeashData(null);
        }
        living.setPose(physicalPose());
        living.setYRot(yRot);
        living.setXRot(xRot);
        living.yRotO = yRot;
        living.xRotO = xRot;
        living.yBodyRot = living.yBodyRotO = bodyYRot;
        living.yHeadRot = living.yHeadRotO = headYRot;
        living.tickCount = ageInTicks;
        living.oAttackAnim = living.attackAnim = animation.attack();
        living.swinging = animation.swinging();
        living.swingingArm = animation.swingingOffHand()
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        living.swingTime = animation.swingTime();
        if (living instanceof Mob mob) {
            mob.setAggressive(animation.aggressive());
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
    }

    /**
     * Damage feedback is not part of an authored pose. Old blueprints may
     * contain a non-zero HurtTime captured on the petrification tick; because
     * statues do not tick, leaving it intact would make that flash permanent.
     */
    public void clearTransientVisualState(LivingEntity living) {
        if (!permanent) {
            return;
        }
        living.hurtTime = 0;
        living.hurtDuration = 0;
    }

    /**
     * Pose used by collision and world interaction. The original pose remains
     * in this record and is injected into the render state.
     */
    public Pose physicalPose() {
        return pose == Pose.SLEEPING ? Pose.STANDING : pose;
    }
}
