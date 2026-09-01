package dev.alvar.echoespast.item;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.resonance.EchoSiteType;
import dev.alvar.echoespast.resonance.ResonanceColor;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A crystallized past. Alone it only stores; a Past Echo vessel projects it.
 */
public final class PastFragmentItem extends Item {
    public PastFragmentItem(Properties properties) {
        super(properties);
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
