package dev.alvar.echoespast.item;

import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.menu.PastEchoMenu;
import dev.alvar.echoespast.server.EchoProjectionAccess;
import dev.alvar.echoespast.server.EchoProjectionManager;
import dev.alvar.echoespast.snapshot.EchoCapture;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class PastEchoItem extends Item {
    /**
     * Vanilla repeats use while the button is held (~every 4 ticks). Projection
     * is a discrete toggle, so a short cooldown stops on/off chatter.
     */
    private static final int TOGGLE_COOLDOWN_TICKS = 12;

    public PastEchoItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return activate(level, player, player.getItemInHand(hand), hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        return activate(context.getLevel(), player, context.getItemInHand(), context.getHand());
    }

    private InteractionResult activate(
            Level level,
            Player player,
            ItemStack stack,
            InteractionHand hand) {
        PastEchoMemory.ensureMigrated(stack);
        if (player.isShiftKeyDown()) {
            return openSocket(level, player, stack, hand);
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.CONSUME;
        }

        EchoSnapshot snapshot = PastEchoMemory.getSnapshot(stack);
        if (snapshot == null) {
            return capture(serverLevel, serverPlayer, stack);
        }

        double maxDistance = EchoesConfig.PROJECTION_RANGE.getAsInt();
        EchoProjectionAccess.Result access = EchoProjectionAccess.validate(
                serverLevel.dimension(), player.position(), snapshot, maxDistance);
        if (access == EchoProjectionAccess.Result.WRONG_DIMENSION) {
            player.sendSystemMessage(Component.translatable("message.echoes_show_the_past.wrong_dimension")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }
        if (access == EchoProjectionAccess.Result.TOO_FAR) {
            player.sendSystemMessage(Component.translatable("message.echoes_show_the_past.too_far")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }

        boolean active = EchoProjectionManager.toggle(serverPlayer, snapshot);
        player.getCooldowns().addCooldown(stack, TOGGLE_COOLDOWN_TICKS);
        player.sendSystemMessage(Component.translatable(active
                ? "message.echoes_show_the_past.projecting"
                : "message.echoes_show_the_past.stopped"));
        if (active) {
            serverLevel.playSound(
                    null,
                    player.blockPosition(),
                    EchoesShowThePast.ECHO_IMPULSE.get(),
                    SoundSource.PLAYERS,
                    0.9F,
                    1.0F);
            serverLevel.playSound(
                    null,
                    player.blockPosition(),
                    EchoesShowThePast.ECHO_SWEEP.get(),
                    SoundSource.PLAYERS,
                    0.7F,
                    1.0F);
            serverLevel.playSound(
                    null,
                    player.blockPosition(),
                    EchoesShowThePast.ECHO_RETURN.get(),
                    SoundSource.PLAYERS,
                    0.72F,
                    1.0F);
        } else {
            serverLevel.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.COPPER_BULB_TURN_OFF,
                    SoundSource.PLAYERS,
                    0.6F,
                    0.9F);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private InteractionResult openSocket(
            Level level,
            Player player,
            ItemStack stack,
            InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer)) {
            return InteractionResult.PASS;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) ->
                        new PastEchoMenu(containerId, inventory, stack),
                Component.translatable("gui.echoes_show_the_past.past_echo")));
        return InteractionResult.SUCCESS_SERVER;
    }

    private InteractionResult capture(ServerLevel level, ServerPlayer player, ItemStack stack) {
        BlockPos origin = player.blockPosition();
        Optional<EchoSnapshot> captured = EchoCapture.capture(
                level,
                origin,
                EchoesConfig.CAPTURE_RADIUS.getAsInt(),
                EchoesConfig.MAX_CAPTURED_BLOCKS.getAsInt());
        if (captured.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.echoes_show_the_past.too_large")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }

        ItemStack fragment = PastEchoMemory.getFragment(stack);
        if (fragment.isEmpty()) {
            PastEchoMemory.setFragment(
                    stack,
                    PastEchoMemory.createFragment(
                            captured.get(),
                            Optional.of(PastEchoMemory.DEFAULT_PERSONAL_COLOR)));
        } else if (fragment.get(EchoesShowThePast.ECHO_SNAPSHOT.get()) == null) {
            PastEchoMemory.setSnapshot(stack, captured.get());
        } else {
            PastEchoMemory.setSnapshot(stack, captured.get());
        }

        player.getCooldowns().addCooldown(stack, TOGGLE_COOLDOWN_TICKS);
        player.sendSystemMessage(Component.translatable(
                "message.echoes_show_the_past.captured",
                captured.get().blocks().size()));
        level.playSound(
                null,
                origin,
                EchoesShowThePast.ECHO_IMPULSE.get(),
                SoundSource.PLAYERS,
                0.85F,
                1.15F);
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
        PastEchoMemory.ensureMigrated(stack);
        EchoSnapshot snapshot = PastEchoMemory.getSnapshot(stack);
        if (snapshot == null) {
            boolean hasEmptyFragment = PastEchoMemory.hasFragment(stack);
            builder.accept(Component.translatable(
                            hasEmptyFragment
                                    ? "tooltip.echoes_show_the_past.vessel_empty_fragment"
                                    : "tooltip.echoes_show_the_past.empty")
                    .withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable("tooltip.echoes_show_the_past.capture")
                    .withStyle(ChatFormatting.DARK_GRAY));
            builder.accept(Component.translatable("tooltip.echoes_show_the_past.open_socket")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        builder.accept(Component.translatable(
                        snapshot.sealed()
                                ? "tooltip.echoes_show_the_past.sealed"
                                : "tooltip.echoes_show_the_past.memory",
                        snapshot.isTemplateReference() ? 0 : snapshot.blocks().size())
                .withStyle(snapshot.sealed() ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        builder.accept(Component.literal(snapshot.dimension().identifier().toString())
                .withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.echoes_show_the_past.project")
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.echoes_show_the_past.open_socket")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
