package dev.alvar.echoespast.relic;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alvar.echoespast.snapshot.SnapshotEntity;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Complete data stored in the mined statue item. Entity NBT preserves
 * type-specific pose data; {@link PetrifiedPose} preserves transient animation
 * fields that ordinary saves omit.
 */
public record PetrifiedMobData(
        SnapshotEntity entity,
        BakedModelPose modelPose,
        boolean headless) {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Codec<PetrifiedMobData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SnapshotEntity.CODEC.fieldOf("entity").forGetter(PetrifiedMobData::entity),
            BakedModelPose.CODEC.optionalFieldOf("model_pose", BakedModelPose.EMPTY)
                    .forGetter(PetrifiedMobData::modelPose),
            Codec.BOOL.optionalFieldOf("headless", false).forGetter(PetrifiedMobData::headless)
    ).apply(instance, PetrifiedMobData::new));

    public PetrifiedMobData {
        CompoundTag detachedData = detachInteractionData(entity.data());
        entity = new SnapshotEntity(
                entity.offset(),
                detachedData,
                entity.pose(),
                entity.ageInTicks(),
                entity.yRot(),
                entity.xRot(),
                entity.bodyYRot(),
                entity.headYRot(),
                entity.animation());
        modelPose = modelPose == null ? BakedModelPose.EMPTY : modelPose;
    }

    public PetrifiedMobData(SnapshotEntity entity, BakedModelPose modelPose) {
        this(entity, modelPose, false);
    }

    public PetrifiedMobData withHeadless(boolean withoutHead) {
        return new PetrifiedMobData(entity, modelPose, withoutHead);
    }

    public static Optional<PetrifiedMobData> capture(LivingEntity living) {
        PetrifiedPose pose = living.getData(dev.alvar.echoespast.EchoesShowThePast.PETRIFIED_POSE.get());
        if (!pose.permanent()) {
            return Optional.empty();
        }
        try (ProblemReporter.ScopedCollector reporter =
                     new ProblemReporter.ScopedCollector(living.problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, living.registryAccess());
            if (!living.save(output)) {
                return Optional.empty();
            }
            return Optional.of(new PetrifiedMobData(
                    new SnapshotEntity(
                            Vec3.ZERO,
                            output.buildResult(),
                            pose.pose(),
                            pose.ageInTicks(),
                            pose.yRot(),
                            pose.xRot(),
                            pose.bodyYRot(),
                            pose.headYRot(),
                            pose.animation()),
                    pose.modelPose(),
                    pose.headless()));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not serialize petrified entity {}", living.getType(), exception);
            return Optional.empty();
        }
    }

    /**
     * Keeps creature identity and model state while removing live world
     * relationships. This also runs from the codec constructor so statue items
     * created before the fix cannot reconnect to an old bed or leash.
     */
    public static CompoundTag detachInteractionData(CompoundTag source) {
        CompoundTag detached = source.copy();
        // Passengers are separate gameplay entities, not part of the statue.
        detached.remove("Passengers");
        // Current and legacy sleeping fields both reposition an entity while loading.
        detached.remove("sleeping_pos");
        detached.remove("SleepingX");
        detached.remove("SleepingY");
        detached.remove("SleepingZ");
        // A restored mob must not reconnect to an entity or fence knot.
        detached.remove("leash");
        detached.remove("Leash");
        // Defensive compatibility with entity data written by older versions.
        detached.remove("RootVehicle");
        return detached;
    }
}
