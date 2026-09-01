package dev.alvar.echoespast.server;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.item.LowFrequencyResonatorItem;
import dev.alvar.echoespast.relic.EyeOfHorusItem;
import dev.alvar.echoespast.relic.HolyGrailItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authoritative controls which must remain available during vanilla's
 * visual item cooldown. Each request is scoped to the item still held in the
 * reported hand; stale or forged requests are inert.
 */
public final class RelicControlManager {
    public static boolean handle(
            ServerPlayer player,
            InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(EchoesShowThePast.LOW_FREQUENCY_RESONATOR.get())) {
            // Match LowFrequencyResonatorItem.use: console and pulse cancel are
            // secondary-use actions, never ordinary right-clicks.
            if (!player.isSecondaryUseActive()) {
                return false;
            }
            if (LowFrequencySonarManager.cancel(player)) {
                return true;
            }
            return LowFrequencyResonatorItem.openConsole(player, stack);
        }
        if (stack.is(EchoesShowThePast.EYE_OF_HORUS.get())) {
            if (!EyeOfHorusItem.cancelActiveVision(player)) {
                return false;
            }
            player.sendOverlayMessage(Component.translatable(
                    "message.echoes_show_the_past.horus_cancelled"));
            return true;
        }
        if (stack.is(EchoesShowThePast.HOLY_GRAIL.get())) {
            if (!HolyGrailItem.cancelActiveAura(player)) {
                return false;
            }
            player.sendOverlayMessage(Component.translatable(
                    "message.echoes_show_the_past.grail_cancelled"));
            return true;
        }
        if (stack.is(EchoesShowThePast.PHILOSOPHERS_STONE.get())
                && MaterializedEchoManager.cancel(player)) {
            player.sendOverlayMessage(Component.translatable(
                    "message.echoes_show_the_past.stone_cancelled"));
            return true;
        }
        return false;
    }

    private RelicControlManager() {
    }
}
