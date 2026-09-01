package dev.alvar.echoespast.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A Low-Frequency Resonator socket module. Tooltips spell the exact range,
 * cone, speed or gating change so the player does not have to open the console
 * to learn what the part does.
 */
public final class ResonatorModuleItem extends Item {
    private final String tooltipKey;

    public ResonatorModuleItem(Properties properties, String tooltipKey) {
        super(properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> builder,
            TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.echoes_show_the_past." + tooltipKey)
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.echoes_show_the_past.resonator_module_install")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
