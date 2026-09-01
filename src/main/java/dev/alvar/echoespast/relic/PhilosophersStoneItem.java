package dev.alvar.echoespast.relic;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.block.EchoPedestalBlockEntity;
import dev.alvar.echoespast.server.MaterializedEchoManager;
import dev.alvar.echoespast.server.UnknownAdvancements;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class PhilosophersStoneItem extends RelicItem {
    public PhilosophersStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        RelicCooldownManager.synchronizeStack(serverPlayer, stack);
        player.sendOverlayMessage(Component.translatable(
                "message.echoes_show_the_past.stone_requires_echo"));
        return InteractionResult.CONSUME;
    }

    /**
     * Activates the Stone from the memory physically seated in a pedestal.
     * The pedestal is only a holder; materialization uses the snapshot origin.
     */
    public InteractionResult useOnMemoryPedestal(
            ServerPlayer player,
            ItemStack stack,
            EchoPedestalBlockEntity pedestal,
            BlockPos pedestalPosition,
            EchoSnapshot memory) {
        if (memory == null
                || !memory.sealed()
                || !memory.dimension().equals(player.level().dimension())) {
            player.sendOverlayMessage(Component.translatable(
                    "message.echoes_show_the_past.stone_requires_echo"));
            return InteractionResult.CONSUME;
        }

        RelicState relicState = state(stack, player, 0);
        long now = player.level().getGameTime();
        if (RelicCooldownManager.synchronizeStack(player, stack)
                || player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.CONSUME;
        }
        if (pedestal.hasStone()
                || !MaterializedEchoManager.start(
                        player,
                        memory,
                        pedestalPosition)) {
            return InteractionResult.CONSUME;
        }

        stack.set(
                EchoesShowThePast.RELIC_STATE.get(),
                relicState.withCooldown(now + 120 * 20L));
        player.getCooldowns().addCooldown(stack, 120 * 20);
        if (!pedestal.tryInsertStone(stack)) {
            MaterializedEchoManager.abort(player);
            return InteractionResult.CONSUME;
        }
        // The Stone is the physical catalyst. It leaves every inventory,
        // including Creative, until the same pedestal is used again.
        stack.shrink(1);
        Identifier siteId = memory.site().orElse(pedestal.site());
        UnknownAdvancements.awardRevisit(player, siteId);
        return InteractionResult.SUCCESS_SERVER;
    }
}
