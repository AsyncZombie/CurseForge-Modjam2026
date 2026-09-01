package dev.alvar.echoespast.snapshot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Renderer-driving state that living entities normally do not write to NBT.
 * Keeping it separate also leaves non-living entity snapshots compact.
 */
public record SnapshotAnimation(
        float walkPosition,
        float walkSpeed,
        float attack,
        boolean swinging,
        boolean swingingOffHand,
        int swingTime,
        boolean usingItem,
        boolean usingItemOffHand,
        int useItemRemainingTicks,
        boolean aggressive) {

    public static final SnapshotAnimation NONE =
            new SnapshotAnimation(0.0F, 0.0F, 0.0F, false, false, 0, false, false, 0, false);

    public static final Codec<SnapshotAnimation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("walk_position", 0.0F).forGetter(SnapshotAnimation::walkPosition),
            Codec.FLOAT.optionalFieldOf("walk_speed", 0.0F).forGetter(SnapshotAnimation::walkSpeed),
            Codec.FLOAT.optionalFieldOf("attack", 0.0F).forGetter(SnapshotAnimation::attack),
            Codec.BOOL.optionalFieldOf("swinging", false).forGetter(SnapshotAnimation::swinging),
            Codec.BOOL.optionalFieldOf("swinging_off_hand", false)
                    .forGetter(SnapshotAnimation::swingingOffHand),
            Codec.INT.optionalFieldOf("swing_time", 0).forGetter(SnapshotAnimation::swingTime),
            Codec.BOOL.optionalFieldOf("using_item", false).forGetter(SnapshotAnimation::usingItem),
            Codec.BOOL.optionalFieldOf("using_item_off_hand", false)
                    .forGetter(SnapshotAnimation::usingItemOffHand),
            Codec.INT.optionalFieldOf("use_item_remaining_ticks", 0)
                    .forGetter(SnapshotAnimation::useItemRemainingTicks),
            Codec.BOOL.optionalFieldOf("aggressive", false).forGetter(SnapshotAnimation::aggressive)
    ).apply(instance, SnapshotAnimation::new));
}
