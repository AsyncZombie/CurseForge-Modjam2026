package dev.alvar.echoespast.relic;

import dev.alvar.echoespast.EchoesShowThePast;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Rebuilds Minecraft's transient cooldown display from the persistent timer
 * stored on relic stacks. Vanilla cooldowns disappear when a player reconnects,
 * while {@link RelicState#cooldownUntil()} intentionally does not.
 */
public final class RelicCooldownManager {
    public static int synchronizePlayer(ServerPlayer player) {
        long now = player.level().getGameTime();
        Map<Identifier, Integer> longestByGroup = new HashMap<>();

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            int remaining = remainingTicks(stack, now);
            if (remaining <= 0) {
                continue;
            }
            Identifier group = player.getCooldowns().getCooldownGroup(stack);
            longestByGroup.merge(group, remaining, Math::max);
        }

        longestByGroup.forEach(player.getCooldowns()::addCooldown);
        return longestByGroup.size();
    }

    public static boolean synchronizeStack(ServerPlayer player, ItemStack stack) {
        int remaining = remainingTicks(stack, player.level().getGameTime());
        if (remaining <= 0) {
            return false;
        }
        if (!player.getCooldowns().isOnCooldown(stack)) {
            player.getCooldowns().addCooldown(stack, remaining);
        }
        return true;
    }

    static int remainingTicks(ItemStack stack, long now) {
        RelicState state = stack.get(EchoesShowThePast.RELIC_STATE.get());
        if (state == null || state.cooldownUntil() <= now) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, state.cooldownUntil() - now);
    }

    private RelicCooldownManager() {
    }
}
