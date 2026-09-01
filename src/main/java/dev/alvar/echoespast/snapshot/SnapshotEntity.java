package dev.alvar.echoespast.snapshot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

/**
 * A frozen entity. Complete NBT preserves type-specific data such as
 * armor-stand limbs, paintings, item frames, equipment, and modded persistent
 * data. Explicit pose fields preserve common transient renderer inputs that
 * vanilla NBT does not save.
 */
public record SnapshotEntity(
        Vec3 offset,
        CompoundTag data,
        Pose pose,
        int ageInTicks,
        float yRot,
        float xRot,
        float bodyYRot,
        float headYRot,
        SnapshotAnimation animation,
        List<SnapshotEntityFrame> passengerFrames) {

    public static final Codec<SnapshotEntity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Vec3.CODEC.fieldOf("offset").forGetter(SnapshotEntity::offset),
            CompoundTag.CODEC.fieldOf("data").forGetter(SnapshotEntity::data),
            Pose.CODEC.optionalFieldOf("pose", Pose.STANDING).forGetter(SnapshotEntity::pose),
            Codec.INT.optionalFieldOf("age_in_ticks", 0).forGetter(SnapshotEntity::ageInTicks),
            Codec.FLOAT.optionalFieldOf("y_rot", 0.0F).forGetter(SnapshotEntity::yRot),
            Codec.FLOAT.optionalFieldOf("x_rot", 0.0F).forGetter(SnapshotEntity::xRot),
            Codec.FLOAT.optionalFieldOf("body_y_rot", 0.0F).forGetter(SnapshotEntity::bodyYRot),
            Codec.FLOAT.optionalFieldOf("head_y_rot", 0.0F).forGetter(SnapshotEntity::headYRot),
            SnapshotAnimation.CODEC.optionalFieldOf("animation", SnapshotAnimation.NONE)
                    .forGetter(SnapshotEntity::animation),
            SnapshotEntityFrame.CODEC.listOf()
                    .optionalFieldOf("passenger_frames", List.of())
                    .forGetter(SnapshotEntity::passengerFrames)
    ).apply(instance, SnapshotEntity::new));

    public SnapshotEntity {
        data = data.copy();
        passengerFrames = List.copyOf(passengerFrames);
    }

    public SnapshotEntity(
            Vec3 offset,
            CompoundTag data,
            Pose pose,
            int ageInTicks,
            float yRot,
            float xRot,
            float bodyYRot,
            float headYRot,
            SnapshotAnimation animation) {
        this(
                offset,
                data,
                pose,
                ageInTicks,
                yRot,
                xRot,
                bodyYRot,
                headYRot,
                animation,
                List.of());
    }

    public SnapshotEntityFrame rootFrame() {
        return new SnapshotEntityFrame(
                pose,
                ageInTicks,
                yRot,
                xRot,
                bodyYRot,
                headYRot,
                animation);
    }
}
