package dev.alvar.echoespast.snapshot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;

/**
 * Transient renderer and pose state for one member of an entity/passenger
 * hierarchy. Vanilla NBT owns durable gameplay data; this frame fills the
 * animation fields which normal entity saving deliberately omits.
 */
public record SnapshotEntityFrame(
        Pose pose,
        int ageInTicks,
        float yRot,
        float xRot,
        float bodyYRot,
        float headYRot,
        SnapshotAnimation animation) {
    public static final Codec<SnapshotEntityFrame> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Pose.CODEC.optionalFieldOf("pose", Pose.STANDING)
                            .forGetter(SnapshotEntityFrame::pose),
                    Codec.INT.optionalFieldOf("age_in_ticks", 0)
                            .forGetter(SnapshotEntityFrame::ageInTicks),
                    Codec.FLOAT.optionalFieldOf("y_rot", 0.0F)
                            .forGetter(SnapshotEntityFrame::yRot),
                    Codec.FLOAT.optionalFieldOf("x_rot", 0.0F)
                            .forGetter(SnapshotEntityFrame::xRot),
                    Codec.FLOAT.optionalFieldOf("body_y_rot", 0.0F)
                            .forGetter(SnapshotEntityFrame::bodyYRot),
                    Codec.FLOAT.optionalFieldOf("head_y_rot", 0.0F)
                            .forGetter(SnapshotEntityFrame::headYRot),
                    SnapshotAnimation.CODEC
                            .optionalFieldOf(
                                    "animation",
                                    SnapshotAnimation.NONE)
                            .forGetter(SnapshotEntityFrame::animation)
            ).apply(instance, SnapshotEntityFrame::new));

    public static SnapshotEntityFrame capture(Entity entity) {
        float bodyYRot = entity.getYRot();
        float headYRot = entity.getYRot();
        float walkAnimationPosition = 0.0F;
        float walkAnimationSpeed = 0.0F;
        float attackAnimation = 0.0F;
        boolean swinging = false;
        boolean swingingOffHand = false;
        int swingTime = 0;
        boolean usingItem = false;
        boolean usingItemOffHand = false;
        int useItemRemainingTicks = 0;
        boolean aggressive = false;
        if (entity instanceof LivingEntity living) {
            bodyYRot = living.yBodyRot;
            headYRot = living.yHeadRot;
            walkAnimationPosition = living.walkAnimation.position();
            walkAnimationSpeed = living.walkAnimation.speed();
            attackAnimation = living.getAttackAnim(1.0F);
            swinging = living.swinging;
            swingingOffHand =
                    living.swingingArm == InteractionHand.OFF_HAND;
            swingTime = living.swingTime;
            usingItem = living.isUsingItem();
            usingItemOffHand = usingItem
                    && living.getUsedItemHand()
                            == InteractionHand.OFF_HAND;
            useItemRemainingTicks = usingItem
                    ? living.getUseItemRemainingTicks()
                    : 0;
        }
        if (entity instanceof Mob mob) {
            aggressive = mob.isAggressive();
        }
        return new SnapshotEntityFrame(
                entity.getPose(),
                entity.tickCount,
                entity.getYRot(),
                entity.getXRot(),
                bodyYRot,
                headYRot,
                new SnapshotAnimation(
                        walkAnimationPosition,
                        walkAnimationSpeed,
                        attackAnimation,
                        swinging,
                        swingingOffHand,
                        swingTime,
                        usingItem,
                        usingItemOffHand,
                        useItemRemainingTicks,
                        aggressive));
    }
}
