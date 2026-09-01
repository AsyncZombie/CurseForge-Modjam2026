package dev.alvar.echoespast.snapshot;

import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.relic.PetrifiedPose;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * One serialization path shared by echo capture, client previews and physical
 * temporal materialization. Keeping it centralized prevents the real entity
 * from losing a pose that the ghost representation retained.
 */
public final class SnapshotEntityIO {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static Optional<SnapshotEntity> capture(
            Entity root,
            BlockPos origin) {
        if (root.isPassenger()) {
            return Optional.empty();
        }
        try (ProblemReporter.ScopedCollector reporter =
                new ProblemReporter.ScopedCollector(
                        root.problemPath(),
                        LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(
                    reporter,
                    root.registryAccess());
            if (!root.save(output)) {
                return Optional.empty();
            }
            List<Entity> hierarchy =
                    root.getSelfAndPassengers().toList();
            SnapshotEntityFrame rootFrame =
                    SnapshotEntityFrame.capture(root);
            List<SnapshotEntityFrame> passengerFrames =
                    hierarchy.stream()
                            .skip(1)
                            .map(SnapshotEntityFrame::capture)
                            .toList();
            return Optional.of(new SnapshotEntity(
                    root.position().subtract(
                            Vec3.atLowerCornerOf(origin)),
                    output.buildResult(),
                    rootFrame.pose(),
                    rootFrame.ageInTicks(),
                    rootFrame.yRot(),
                    rootFrame.xRot(),
                    rootFrame.bodyYRot(),
                    rootFrame.headYRot(),
                    rootFrame.animation(),
                    passengerFrames));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not capture entity {} in echo snapshot",
                    root.getType(),
                    exception);
            return Optional.empty();
        }
    }

    public static Optional<Entity> load(
            SnapshotEntity remembered,
            Level level,
            BlockPos origin,
            boolean avoidUuidCollisions) {
        try {
            Entity root = EntityType.loadEntityRecursive(
                    remembered.data().copy(),
                    level,
                    EntitySpawnReason.LOAD,
                    entity -> entity);
            if (root == null) {
                return Optional.empty();
            }
            List<Entity> hierarchy =
                    new ArrayList<>(
                            root.getSelfAndPassengers().toList());
            Vec3 destination = Vec3.atLowerCornerOf(origin)
                    .add(remembered.offset());
            Vec3 translation =
                    destination.subtract(root.position());
            for (Entity entity : hierarchy) {
                Vec3 translated =
                        entity.position().add(translation);
                entity.snapTo(
                        translated.x,
                        translated.y,
                        translated.z,
                        entity.getYRot(),
                        entity.getXRot());
            }
            applyFrame(root, remembered.rootFrame());
            for (int index = 1;
                    index < hierarchy.size();
                    index++) {
                int frameIndex = index - 1;
                if (frameIndex
                        >= remembered.passengerFrames().size()) {
                    break;
                }
                applyFrame(
                        hierarchy.get(index),
                        remembered.passengerFrames()
                                .get(frameIndex));
            }
            if (avoidUuidCollisions
                    && level instanceof ServerLevel serverLevel) {
                for (Entity entity : hierarchy) {
                    if (serverLevel.getEntityInAnyDimension(
                                    entity.getUUID())
                            != null) {
                        entity.setUUID(UUID.randomUUID());
                    }
                }
            }
            return Optional.of(root);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not materialize echo entity",
                    exception);
            return Optional.empty();
        }
    }

    private static void applyFrame(
            Entity entity,
            SnapshotEntityFrame frame) {
        entity.snapTo(
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                frame.yRot(),
                frame.xRot());
        entity.setPose(frame.pose());
        entity.tickCount = frame.ageInTicks();
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        PetrifiedPose petrified = living.getExistingDataOrNull(
                EchoesShowThePast.PETRIFIED_POSE.get());
        if (petrified != null) {
            petrified.clearTransientVisualState(living);
        }
        SnapshotAnimation animation = frame.animation();
        living.yBodyRot = living.yBodyRotO =
                frame.bodyYRot();
        living.yHeadRot = living.yHeadRotO =
                frame.headYRot();
        living.oAttackAnim = living.attackAnim =
                animation.attack();
        living.swinging = animation.swinging();
        living.swingingArm = animation.swingingOffHand()
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        living.swingTime = animation.swingTime();
        living.walkAnimation.setSpeed(animation.walkSpeed());
        living.walkAnimation.update(
                animation.walkSpeed(),
                1.0F,
                1.0F);
        if (animation.usingItem()) {
            living.startUsingItem(
                    animation.usingItemOffHand()
                            ? InteractionHand.OFF_HAND
                            : InteractionHand.MAIN_HAND);
        }
        if (living instanceof Mob mob) {
            mob.setAggressive(animation.aggressive());
        }
    }

    private SnapshotEntityIO() {
    }
}
