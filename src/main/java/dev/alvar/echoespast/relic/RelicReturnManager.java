package dev.alvar.echoespast.relic;

import dev.alvar.echoespast.EchoesShowThePast;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class RelicReturnManager {
    public static void returnRelic(ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)
                || entity.getItem().isEmpty()
                || !(entity.getItem().getItem() instanceof RelicItem)) {
            return;
        }
        ItemStack stack = entity.getItem().copyWithCount(1);
        RelicState state = stack.getOrDefault(EchoesShowThePast.RELIC_STATE.get(), RelicState.EMPTY);
        UUID owner = state.originalOwner().orElse(null);
        if (owner == null) {
            return;
        }
        entity.setItem(ItemStack.EMPTY);
        entity.discard();
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null) {
            if (!player.addItem(stack)) {
                player.drop(stack, false);
            }
            return;
        }
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            overworld.getDataStorage()
                    .computeIfAbsent(RelicReturnSavedData.TYPE)
                    .add(owner, stack);
        }
    }

    @SubscribeEvent
    public static void onExpire(ItemExpireEvent event) {
        if (event.getEntity().getItem().getItem() instanceof RelicItem) {
            returnRelic(event.getEntity());
            event.addExtraLife(1);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel overworld = player.level().getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            for (ItemStack stack : overworld.getDataStorage()
                    .computeIfAbsent(RelicReturnSavedData.TYPE)
                    .take(player.getUUID())) {
                if (!player.addItem(stack)) {
                    player.drop(stack, false);
                }
            }
        }
        // The vanilla cooldown manager is session-only. Restore its client bar
        // after pending relics have also been delivered.
        RelicCooldownManager.synchronizePlayer(player);
    }

    private RelicReturnManager() {
    }
}
