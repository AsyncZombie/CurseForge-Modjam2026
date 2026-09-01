package dev.alvar.echoespast.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.entity.DungeonPickupEntity;
import dev.alvar.echoespast.network.EchoFadeDebugPayload;
import dev.alvar.echoespast.relic.MedusaHeadAimMath;
import dev.alvar.echoespast.relic.RelicState;
import dev.alvar.echoespast.server.UnknownFightManager;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class EchoCommands {
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("echoes")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("cooldown")
                                .then(Commands.literal("reset")
                                        .executes(context -> reset(
                                                context.getSource(),
                                                List.of(context.getSource().getPlayerOrException())))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(context -> reset(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(
                                                                context,
                                                                "targets"))))))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("fade")
                                        .executes(context -> requestFadeDebug(
                                                context.getSource())))
                                .then(Commands.literal("medusahead")
                                        .executes(context -> showMedusaHeadPose(
                                                context.getSource()))
                                        .then(Commands.literal("show")
                                                .executes(context -> showMedusaHeadPose(
                                                        context.getSource())))
                                        .then(Commands.literal("reset")
                                                .executes(context -> resetMedusaHeadPose(
                                                        context.getSource())))
                                        .then(Commands.literal("idle")
                                                .then(Commands.argument(
                                                                "x",
                                                                FloatArgumentType.floatArg())
                                                        .then(Commands.argument(
                                                                        "y",
                                                                        FloatArgumentType.floatArg())
                                                                .then(Commands.argument(
                                                                                "z",
                                                                                FloatArgumentType.floatArg())
                                                                        .executes(context -> setMedusaHeadPose(
                                                                                context.getSource(),
                                                                                true,
                                                                                FloatArgumentType.getFloat(
                                                                                        context,
                                                                                        "x"),
                                                                                FloatArgumentType.getFloat(
                                                                                        context,
                                                                                        "y"),
                                                                                FloatArgumentType.getFloat(
                                                                                        context,
                                                                                        "z")))))))
                                        .then(Commands.literal("active")
                                                .then(Commands.argument(
                                                                "x",
                                                                FloatArgumentType.floatArg())
                                                        .then(Commands.argument(
                                                                        "y",
                                                                        FloatArgumentType.floatArg())
                                                                .then(Commands.argument(
                                                                                "z",
                                                                                FloatArgumentType.floatArg())
                                                                        .executes(context -> setMedusaHeadPose(
                                                                                context.getSource(),
                                                                                false,
                                                                                FloatArgumentType.getFloat(
                                                                                        context,
                                                                                        "x"),
                                                                                FloatArgumentType.getFloat(
                                                                                        context,
                                                                                        "y"),
                                                                                FloatArgumentType.getFloat(
                                                                                        context,
                                                                                        "z")))))))
                                        .then(Commands.literal("add")
                                                .then(Commands.argument(
                                                                "pose",
                                                                StringArgumentType.word())
                                                        .suggests((context, builder) ->
                                                                SharedSuggestionProvider.suggest(
                                                                        new String[] {"idle", "active"},
                                                                        builder))
                                                        .then(Commands.argument(
                                                                        "axis",
                                                                        StringArgumentType.word())
                                                                .suggests((context, builder) ->
                                                                        SharedSuggestionProvider.suggest(
                                                                                new String[] {"x", "y", "z"},
                                                                                builder))
                                                                .then(Commands.argument(
                                                                                "degrees",
                                                                                FloatArgumentType.floatArg())
                                                                        .executes(context -> addMedusaHeadPose(
                                                                                context.getSource(),
                                                                                StringArgumentType.getString(
                                                                                        context,
                                                                                        "pose"),
                                                                                StringArgumentType.getString(
                                                                                        context,
                                                                                        "axis"),
                                                                                FloatArgumentType.getFloat(
                                                                                        context,
                                                                                        "degrees")))))))))
                        .then(Commands.literal("pickup")
                                .then(Commands.literal("spawn")
                                        .then(Commands.argument(
                                                        "item",
                                                        ItemArgument.item(event.getBuildContext()))
                                                .executes(context -> spawnPickup(
                                                        context.getSource(),
                                                        ItemArgument.getItem(context, "item"),
                                                        1,
                                                        DungeonPickupEntity.DEFAULT_SCALE))
                                                .then(Commands.argument(
                                                                "count",
                                                                IntegerArgumentType.integer(1, 99))
                                                        .executes(context -> spawnPickup(
                                                                context.getSource(),
                                                                ItemArgument.getItem(
                                                                        context,
                                                                        "item"),
                                                                IntegerArgumentType.getInteger(
                                                                        context,
                                                                        "count"),
                                                                DungeonPickupEntity.DEFAULT_SCALE))
                                                        .then(Commands.argument(
                                                                        "scale",
                                                                        FloatArgumentType.floatArg(
                                                                                DungeonPickupEntity.MIN_SCALE,
                                                                                DungeonPickupEntity.MAX_SCALE))
                                                                .executes(context -> spawnPickup(
                                                                        context.getSource(),
                                                                        ItemArgument.getItem(
                                                                                context,
                                                                                "item"),
                                                                        IntegerArgumentType.getInteger(
                                                                                context,
                                                                                "count"),
                                                                        FloatArgumentType.getFloat(
                                                                                context,
                                                                                "scale")))))))
                                .then(Commands.literal("scale")
                                        .then(Commands.argument(
                                                        "scale",
                                                        FloatArgumentType.floatArg(
                                                                DungeonPickupEntity.MIN_SCALE,
                                                                DungeonPickupEntity.MAX_SCALE))
                                                .executes(context -> scaleNearestPickup(
                                                        context.getSource(),
                                                        FloatArgumentType.getFloat(
                                                                context,
                                                                "scale"))))))
                        .then(Commands.literal("unknown")
                                .then(Commands.literal("visit")
                                        .executes(context -> visitUnknown(
                                                context.getSource())))
                                .then(Commands.literal("spawn")
                                        .executes(context -> spawnUnknownDummy(
                                                context.getSource())))
                                .then(Commands.literal("enter")
                                        .executes(context -> enterUnknown(
                                                context.getSource())))
                                .then(Commands.literal("status")
                                        .executes(context -> unknownStatus(
                                                context.getSource())))
                                .then(Commands.literal("advance")
                                        .executes(context -> advanceUnknown(
                                                context.getSource())))
                                .then(Commands.literal("stage")
                                        .then(Commands.argument("stage", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        Arrays.stream(UnknownFightManager.CombatStage.values())
                                                                .map(UnknownFightManager.CombatStage::id),
                                                        builder))
                                                .executes(context -> setUnknownStage(
                                                        context.getSource(),
                                                        StringArgumentType.getString(
                                                                context,
                                                                "stage")))))
                                .then(Commands.literal("reset")
                                        .executes(context -> resetUnknown(
                                                context.getSource())))));
    }

    private static int spawnPickup(
            CommandSourceStack source, ItemInput item, int count, float scale)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = item.createItemStack(count);
        float displayScale = DungeonPickupEntity.clampScale(scale);
        DungeonPickupEntity pickup = DungeonPickupEntity.create(
                player.level(),
                player.getX(),
                player.getY() + 0.35D,
                player.getZ(),
                stack,
                displayScale);
        if (!player.level().addFreshEntity(pickup)) {
            source.sendFailure(Component.translatable(
                    "commands.echoes_show_the_past.pickup_spawn_failed"));
            return 0;
        }
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.echoes_show_the_past.pickup_spawn",
                        stack.getHoverName(),
                        stack.getCount(),
                        formatPickupScale(displayScale)),
                true);
        return 1;
    }

    private static int scaleNearestPickup(CommandSourceStack source, float scale)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        float displayScale = DungeonPickupEntity.clampScale(scale);
        DungeonPickupEntity pickup = player.level()
                .getEntitiesOfClass(
                        DungeonPickupEntity.class,
                        player.getBoundingBox().inflate(16.0D),
                        DungeonPickupEntity::isAlive)
                .stream()
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
        if (pickup == null) {
            source.sendFailure(Component.translatable(
                    "commands.echoes_show_the_past.pickup_scale_missing"));
            return 0;
        }
        pickup.setDisplayScale(displayScale);
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.echoes_show_the_past.pickup_scale",
                        formatPickupScale(pickup.getDisplayScale())),
                true);
        return 1;
    }

    private static String formatPickupScale(float scale) {
        return String.format("%.2f", scale);
    }

    private static int visitUnknown(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        UnknownFightManager.visitHub(player);
        source.sendSuccess(
                () -> Component.translatable("commands.echoes_show_the_past.unknown_visit"),
                true);
        return 1;
    }

    private static int spawnUnknownDummy(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!UnknownFightManager.spawnDummy(player)) {
            source.sendFailure(Component.translatable(
                    "message.echoes_show_the_past.unknown_dummy_failed"));
            return 0;
        }
        source.sendSuccess(
                () -> Component.translatable("commands.echoes_show_the_past.unknown_spawn"),
                true);
        return 1;
    }

    private static int enterUnknown(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!UnknownFightManager.enterFromOverworld(player)) {
            return 0;
        }
        source.sendSuccess(
                () -> Component.translatable("commands.echoes_show_the_past.unknown_enter"),
                true);
        return 1;
    }

    private static int unknownStatus(CommandSourceStack source)
            throws CommandSyntaxException {
        UnknownFightManager.sendDebugStatus(source.getPlayerOrException());
        return 1;
    }

    private static int advanceUnknown(CommandSourceStack source)
            throws CommandSyntaxException {
        if (!UnknownFightManager.debugAdvanceThreshold(source.getPlayerOrException())) {
            source.sendFailure(Component.translatable(
                    "commands.echoes_show_the_past.unknown_advance_unavailable"));
            return 0;
        }
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.echoes_show_the_past.unknown_advance"),
                true);
        return 1;
    }

    private static int setUnknownStage(CommandSourceStack source, String rawStage)
            throws CommandSyntaxException {
        UnknownFightManager.CombatStage stage = UnknownFightManager.CombatStage.parse(rawStage);
        if (stage == null) {
            source.sendFailure(Component.translatable(
                    "commands.echoes_show_the_past.unknown_stage_invalid",
                    rawStage));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        if (!UnknownFightManager.debugSetStage(player, stage)) {
            source.sendFailure(Component.translatable(
                    "commands.echoes_show_the_past.unknown_stage_unavailable"));
            return 0;
        }
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.echoes_show_the_past.unknown_stage",
                        stage.id()),
                true);
        return 1;
    }

    private static int resetUnknown(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        UnknownFightManager.resetSession(player.level().getServer());
        source.sendSuccess(
                () -> Component.translatable("commands.echoes_show_the_past.unknown_reset"),
                true);
        return 1;
    }

    private static int requestFadeDebug(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PacketDistributor.sendToPlayer(player, EchoFadeDebugPayload.INSTANCE);
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.echoes_show_the_past.fade_debug"),
                true);
        return 1;
    }

    private static int showMedusaHeadPose(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MedusaHeadPoseDebugSessions.Session session = MedusaHeadPoseDebugSessions.of(player);
        MedusaHeadPoseDebugSessions.send(player, session);
        source.sendSuccess(() -> medusaHeadPoseMessage(session), false);
        return 1;
    }

    private static int resetMedusaHeadPose(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MedusaHeadPoseDebugSessions.reset(player);
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.echoes_show_the_past.medusa_head_pose_reset"),
                true);
        return 1;
    }

    private static int setMedusaHeadPose(
            CommandSourceStack source,
            boolean idle,
            float x,
            float y,
            float z)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MedusaHeadPoseDebugSessions.Session current = MedusaHeadPoseDebugSessions.of(player);
        var euler = new MedusaHeadAimMath.PoseEuler(x, y, z);
        MedusaHeadPoseDebugSessions.Session next = idle
                ? current.withRest(euler)
                : current.withActive(euler);
        MedusaHeadPoseDebugSessions.put(player, next);
        source.sendSuccess(() -> medusaHeadPoseMessage(next), false);
        return 1;
    }

    private static int addMedusaHeadPose(
            CommandSourceStack source,
            String pose,
            String axisName,
            float degrees)
            throws CommandSyntaxException {
        boolean idle = pose.equalsIgnoreCase("idle");
        boolean active = pose.equalsIgnoreCase("active");
        if (!idle && !active) {
            source.sendFailure(Component.translatable(
                    "commands.echoes_show_the_past.medusa_head_pose_unknown",
                    pose));
            return 0;
        }
        if (axisName.length() != 1) {
            source.sendFailure(Component.translatable(
                    "commands.echoes_show_the_past.medusa_head_pose_unknown",
                    axisName));
            return 0;
        }
        char axis = Character.toLowerCase(axisName.charAt(0));
        if (axis != 'x' && axis != 'y' && axis != 'z') {
            source.sendFailure(Component.translatable(
                    "commands.echoes_show_the_past.medusa_head_pose_unknown",
                    axisName));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        MedusaHeadPoseDebugSessions.Session current = MedusaHeadPoseDebugSessions.of(player);
        var euler = idle ? current.rest() : current.active();
        MedusaHeadPoseDebugSessions.Session next = idle
                ? current.withRest(euler.addAxis(axis, degrees))
                : current.withActive(euler.addAxis(axis, degrees));
        MedusaHeadPoseDebugSessions.put(player, next);
        source.sendSuccess(() -> medusaHeadPoseMessage(next), false);
        return 1;
    }

    private static Component medusaHeadPoseMessage(
            MedusaHeadPoseDebugSessions.Session session) {
        return Component.translatable(
                "commands.echoes_show_the_past.medusa_head_pose",
                formatEuler(session.rest()),
                formatEuler(session.active()));
    }

    private static String formatEuler(MedusaHeadAimMath.PoseEuler pose) {
        return String.format("x=%.1f y=%.1f z=%.1f", pose.x(), pose.y(), pose.z());
    }

    public static int resetCooldowns(ServerPlayer player) {
        // Data packs may assign a custom USE_COOLDOWN group to an individual
        // stack. Clear those exact groups before touching persistent data.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                player.getCooldowns().removeCooldown(
                        player.getCooldowns().getCooldownGroup(stack));
            }
        }

        // Remove every mod cooldown group, including one whose corresponding
        // item is not currently selected.
        EchoesShowThePast.ITEMS.getEntries().forEach(holder -> {
            ItemStack representative = holder.value().getDefaultInstance();
            player.getCooldowns().removeCooldown(
                    player.getCooldowns().getCooldownGroup(representative));
        });

        int resetRelics = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            RelicState state = stack.get(EchoesShowThePast.RELIC_STATE.get());
            if (state == null || state.cooldownUntil() == 0L) {
                continue;
            }
            stack.set(
                    EchoesShowThePast.RELIC_STATE.get(),
                    state.withCooldown(0L));
            resetRelics++;
        }
        player.getInventory().setChanged();
        return resetRelics;
    }

    private static int reset(
            CommandSourceStack source,
            Collection<ServerPlayer> targets) {
        int relics = 0;
        for (ServerPlayer target : targets) {
            relics += resetCooldowns(target);
        }
        int playerCount = targets.size();
        int persistentRelics = relics;
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.echoes_show_the_past.cooldown_reset",
                        playerCount,
                        persistentRelics),
                true);
        return playerCount;
    }

    private EchoCommands() {
    }
}
