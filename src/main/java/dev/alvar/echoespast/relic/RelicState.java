package dev.alvar.echoespast.relic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

public record RelicState(
        Optional<UUID> originalOwner,
        int charges,
        long lastRechargeDay,
        long cooldownUntil) {
    public static final Codec<RelicState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(RelicState::originalOwner),
            Codec.INT.optionalFieldOf("charges", 0).forGetter(RelicState::charges),
            Codec.LONG.optionalFieldOf("last_recharge_day", -1L).forGetter(RelicState::lastRechargeDay),
            Codec.LONG.optionalFieldOf("cooldown_until", 0L).forGetter(RelicState::cooldownUntil)
    ).apply(instance, RelicState::new));

    public static final RelicState EMPTY = new RelicState(Optional.empty(), 0, -1L, 0L);

    public RelicState ownedBy(UUID owner, int maximumCharges, long day) {
        return new RelicState(
                originalOwner.isPresent() ? originalOwner : Optional.of(owner),
                Math.clamp(charges, 0, maximumCharges),
                lastRechargeDay < 0L ? day : lastRechargeDay,
                cooldownUntil);
    }

    public RelicState withCharges(int value, int maximum) {
        return new RelicState(
                originalOwner,
                Math.clamp(value, 0, maximum),
                lastRechargeDay,
                cooldownUntil);
    }

    public RelicState withRecharge(int value, int maximum, long day) {
        return new RelicState(
                originalOwner,
                Math.clamp(value, 0, maximum),
                day,
                cooldownUntil);
    }

    public RelicState withCooldown(long until) {
        return new RelicState(originalOwner, charges, lastRechargeDay, until);
    }
}
