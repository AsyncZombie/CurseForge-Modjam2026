package dev.alvar.echoespast.item;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.relic.PetrifiedMobData;
import dev.alvar.echoespast.relic.PetrifiedMobManager;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

public final class PetrifiedMobItem extends Item {
    public PetrifiedMobItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        PetrifiedMobData data = stack.get(EchoesShowThePast.PETRIFIED_MOB_DATA.get());
        if (data == null) {
            return InteractionResult.PASS;
        }
        Vec3 position = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
        Player player = context.getPlayer();
        float yaw = player == null ? 0.0F : player.getYRot() + 180.0F;
        if (PetrifiedMobManager.place(level, data, position, yaw).isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (player == null || !player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> builder,
            TooltipFlag flag) {
        builder.accept(Component.translatable(
                        "tooltip.echoes_show_the_past.petrified_permanent")
                .withStyle(ChatFormatting.DARK_GRAY));
        PetrifiedMobData data = stack.get(EchoesShowThePast.PETRIFIED_MOB_DATA.get());
        if (data != null && data.headless()) {
            builder.accept(Component.translatable(
                            "tooltip.echoes_show_the_past.petrified_medusa_headless")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
