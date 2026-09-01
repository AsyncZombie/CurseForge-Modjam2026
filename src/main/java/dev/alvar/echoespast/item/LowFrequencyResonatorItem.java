package dev.alvar.echoespast.item;

import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.menu.ResonatorMenu;
import dev.alvar.echoespast.resonance.ResonatorLoadout;
import dev.alvar.echoespast.server.LowFrequencySonarManager;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class LowFrequencyResonatorItem extends Item {
    public LowFrequencyResonatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            if (LowFrequencySonarManager.isActive(serverPlayer)) {
                LowFrequencySonarManager.cancel(serverPlayer);
            } else {
                openConsole(serverPlayer, stack);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.CONSUME;
        }

        ResonatorLoadout loadout = stack.getOrDefault(
                EchoesShowThePast.RESONATOR_LOADOUT.get(),
                ResonatorLoadout.EMPTY);
        int cooldownTicks = loadout.cooldownTicks();
        Vec3 origin = player.getEyePosition();
        LowFrequencySonarManager.start(
                serverPlayer,
                origin,
                player.getLookAngle(),
                loadout,
                EchoesConfig.LOW_FREQUENCY_SPEED.getAsDouble());
        player.getCooldowns().addCooldown(stack, cooldownTicks);
        serverLevel.playSound(
                null,
                player.blockPosition(),
                EchoesShowThePast.LOW_FREQUENCY_IMPULSE.get(),
                SoundSource.PLAYERS,
                loadout.effectiveDirectionalMode() ? 0.82F : 0.75F,
                loadout.effectiveDirectionalMode() ? 0.92F : 0.72F);
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Kept outside {@link #use} because Minecraft suppresses that method while
     * an ItemCooldown is active. The explicit control payload calls this path
     * after validating the held stack on the server.
     */
    public static boolean openConsole(
            ServerPlayer player,
            ItemStack stack) {
        if (!stack.is(EchoesShowThePast.LOW_FREQUENCY_RESONATOR.get())) {
            return false;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) ->
                        new ResonatorMenu(containerId, inventory, stack),
                Component.translatable("gui.echoes_show_the_past.resonator")));
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> builder,
            TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.echoes_show_the_past.low_frequency_resonator")
                .withStyle(ChatFormatting.AQUA));
        builder.accept(Component.translatable("tooltip.echoes_show_the_past.low_frequency_wool")
                .withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.echoes_show_the_past.low_frequency_cancel")
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.echoes_show_the_past.low_frequency_console")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
