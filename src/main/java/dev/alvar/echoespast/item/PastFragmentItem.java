package dev.alvar.echoespast.item;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.resonance.EchoSiteType;
import dev.alvar.echoespast.resonance.ResonanceColor;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A crystallized past. Alone it only stores; a Past Echo vessel projects it.
 */
public final class PastFragmentItem extends Item {
    private static final int MESSAGE_COOLDOWN_TICKS = 10;

    public PastFragmentItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide()) {
            player.getCooldowns().addCooldown(stack, MESSAGE_COOLDOWN_TICKS);
            player.sendSystemMessage(Component.translatable(
                            "message.echoes_show_the_past.fragment_needs_vessel")
                    .withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> builder,
            TooltipFlag flag) {
        EchoSnapshot snapshot = stack.get(EchoesShowThePast.ECHO_SNAPSHOT.get());
        if (snapshot == null) {
            builder.accept(Component.translatable("tooltip.echoes_show_the_past.fragment_empty")
                    .withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable("tooltip.echoes_show_the_past.fragment_insert")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        if (snapshot.sealed()) {
            builder.accept(Component.translatable("tooltip.echoes_show_the_past.sealed_fragment")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            int count = snapshot.isTemplateReference()
                    ? 0
                    : snapshot.blocks().size();
            builder.accept(Component.translatable(
                            "tooltip.echoes_show_the_past.fragment_memory",
                            count)
                    .withStyle(ChatFormatting.AQUA));
        }

        var origin = snapshot.origin();
        builder.accept(Component.translatable(
                        "tooltip.echoes_show_the_past.fragment_center",
                        origin.getX(),
                        origin.getY(),
                        origin.getZ())
                .withStyle(ChatFormatting.GRAY));

        snapshot.site().ifPresent(siteId -> {
            EchoSiteType site = EchoSiteType.byId(siteId);
            Component label = site == null
                    ? Component.literal(siteId.toString())
                    : Component.translatable("site.echoes_show_the_past." + siteId.getPath());
            builder.accept(Component.translatable(
                            "tooltip.echoes_show_the_past.fragment_site",
                            label)
                    .withStyle(ChatFormatting.DARK_AQUA));
        });

        ResonanceColor color = PastEchoMemory.resolveColor(stack);
        builder.accept(Component.translatable(
                        "tooltip.echoes_show_the_past.fragment_color",
                        Component.translatable("resonance.echoes_show_the_past.color." + color.getSerializedName()))
                .withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.echoes_show_the_past.fragment_insert")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (snapshot.canErase()) {
            builder.accept(Component.translatable("tooltip.echoes_show_the_past.fragment_forget")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
