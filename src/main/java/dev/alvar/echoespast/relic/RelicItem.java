package dev.alvar.echoespast.relic;

import dev.alvar.echoespast.EchoesShowThePast;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public abstract class RelicItem extends Item {
    protected RelicItem(Properties properties) {
        super(properties);
    }

    protected RelicState state(ItemStack stack, ServerPlayer user, int maximumCharges) {
        RelicState current = stack.getOrDefault(
                EchoesShowThePast.RELIC_STATE.get(),
                RelicState.EMPTY);
        if (current.originalOwner().isEmpty()) {
            current = new RelicState(
                    Optional.of(user.getUUID()),
                    maximumCharges,
                    current.lastRechargeDay(),
                    current.cooldownUntil());
            stack.set(EchoesShowThePast.RELIC_STATE.get(), current);
        }
        return current;
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide()
                && entity.getY() < entity.level().getMinY() - 24) {
            RelicReturnManager.returnRelic(entity);
            return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> builder,
            TooltipFlag flag) {
        String key = this instanceof EyeOfHorusItem
                ? "lore.echoes_show_the_past.eye_of_horus"
                : this instanceof PetrifiedMedusaHeadItem
                        ? "lore.echoes_show_the_past.medusa_petrified"
                        : this instanceof MedusaHeadItem
                                ? "lore.echoes_show_the_past.medusa"
                                : this instanceof HolyGrailItem
                                        ? "lore.echoes_show_the_past.grail"
                                        : "lore.echoes_show_the_past.philosophers_stone";
        builder.accept(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY));
        if (this instanceof EyeOfHorusItem) {
            builder.accept(Component.translatable(
                            "tooltip.echoes_show_the_past.eye_of_horus_recharge")
                    .withStyle(ChatFormatting.GOLD));
        } else if (this instanceof PetrifiedMedusaHeadItem) {
            builder.accept(Component.translatable(
                            "tooltip.echoes_show_the_past.medusa_petrified_gaze")
                    .withStyle(ChatFormatting.RED));
            builder.accept(Component.translatable(
                            "tooltip.echoes_show_the_past.medusa_petrified_uses")
                    .withStyle(ChatFormatting.GOLD));
            builder.accept(Component.translatable(
                            "tooltip.echoes_show_the_past.medusa_pumpkin")
                    .withStyle(ChatFormatting.GOLD));
        } else if (this instanceof MedusaHeadItem) {
            builder.accept(Component.translatable(
                            "tooltip.echoes_show_the_past.medusa_gaze")
                    .withStyle(ChatFormatting.RED));
            builder.accept(Component.translatable(
                            "tooltip.echoes_show_the_past.medusa_pumpkin")
                    .withStyle(ChatFormatting.GOLD));
        } else if (this instanceof HolyGrailItem) {
            builder.accept(Component.translatable(
                            "tooltip.echoes_show_the_past.holy_grail_effect")
                    .withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable(
                    "tooltip.echoes_show_the_past.holy_grail_recharge")
                    .withStyle(ChatFormatting.AQUA));
        } else if (this instanceof PhilosophersStoneItem) {
            builder.accept(Component.translatable(
                            "tooltip.echoes_show_the_past.philosophers_stone_revisit")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (this instanceof EyeOfHorusItem
                || this instanceof HolyGrailItem
                || this instanceof PhilosophersStoneItem) {
            builder.accept(Component.translatable(
                            "tooltip.echoes_show_the_past.relic_cancel")
                    .withStyle(ChatFormatting.GRAY));
        }
        RelicState relic = stack.getOrDefault(EchoesShowThePast.RELIC_STATE.get(), RelicState.EMPTY);
        if (relic.charges() > 0) {
            builder.accept(Component.translatable(
                            "tooltip.echoes_show_the_past.relic_charges",
                            relic.charges())
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
