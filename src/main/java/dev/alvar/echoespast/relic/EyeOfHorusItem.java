package dev.alvar.echoespast.relic;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.network.EyeOfHorusVisualPayload;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

public final class EyeOfHorusItem extends RelicItem {
    public static final int MAX_CHARGES = 5;

    public EyeOfHorusItem(Properties properties) {
        super(properties);
    }

    public static RelicState rechargeStateForDay(
            RelicState state,
            long day) {
        if (day <= state.lastRechargeDay()) {
            return state;
        }
        return state.withRecharge(
                MAX_CHARGES,
                MAX_CHARGES,
                day);
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            ServerLevel level,
            Entity owner,
            EquipmentSlot slot) {
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }

        RelicState before = state(stack, player, MAX_CHARGES);
        long day = level.getOverworldClockTime() / 24_000L;
        RelicState after = rechargeStateForDay(before, day);
        if (after.equals(before)) {
            return;
        }

        stack.set(EchoesShowThePast.RELIC_STATE.get(), after);
        if (before.charges() >= MAX_CHARGES) {
            return;
        }

        player.sendOverlayMessage(Component.translatable(
                "message.echoes_show_the_past.horus_recharged"));
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.72F,
                1.72F);
        level.sendParticles(
                ParticleTypes.WAX_ON,
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                18,
                0.34,
                0.22,
                0.34,
                0.025);
    }

    @Override
    public InteractionResult use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (cancelActiveVision(serverPlayer)) {
            player.sendOverlayMessage(Component.translatable(
                    "message.echoes_show_the_past.horus_cancelled"));
            return InteractionResult.SUCCESS_SERVER;
        }
        RelicState state = state(stack, serverPlayer, MAX_CHARGES);
        long day = level.getOverworldClockTime() / 24_000L;
        state = rechargeStateForDay(state, day);
        if (state.charges() <= 0) {
            player.sendOverlayMessage(Component.translatable("message.echoes_show_the_past.no_relic_charges"));
            stack.set(EchoesShowThePast.RELIC_STATE.get(), state);
            return InteractionResult.CONSUME;
        }

        stack.set(EchoesShowThePast.RELIC_STATE.get(), state.withCharges(state.charges() - 1, MAX_CHARGES));
        AABB area = player.getBoundingBox().inflate(32.0);
        List<LivingEntity> revealed = serverLevel.getEntitiesOfClass(
                LivingEntity.class,
                area,
                target -> target != player
                        && (target.isInvisible()
                                || target instanceof net.minecraft.world.entity.monster.Monster)
                        && visibleThroughThinCover(serverLevel, player.getEyePosition(), target.getEyePosition()));
        EyeRevealManager.start(serverPlayer, 160);
        for (LivingEntity target : revealed) {
            // A permanent statue cancels its entity tick, so vanilla timed
            // effects cannot count down on it. Its stone silhouette remains
            // visible without leaking an eternal glowing flag.
            if (!RelicEffects.isPermanentlyPetrified(target)) {
                EyeRevealManager.applyVisionGlow(serverPlayer, target, 160);
            }
        }
        long now = serverLevel.getGameTime();
        serverPlayer.setData(EchoesShowThePast.HORUS_AURA_START.get(), now);
        serverPlayer.setData(EchoesShowThePast.HORUS_AURA_UNTIL.get(), now + 160L);
        EyeOfHorusVisualPayload visual = new EyeOfHorusVisualPayload(160);
        if (serverPlayer.connection.hasChannel(visual)) {
            PacketDistributor.sendToPlayer(serverPlayer, visual);
        }
        player.getCooldowns().addCooldown(stack, 160);
        return InteractionResult.SUCCESS_SERVER;
    }

    public static boolean cancelActiveVision(ServerPlayer player) {
        return EyeRevealManager.cancel(player);
    }

    private static boolean visibleThroughThinCover(
            ServerLevel level,
            net.minecraft.world.phys.Vec3 from,
            net.minecraft.world.phys.Vec3 to) {
        net.minecraft.world.phys.Vec3 delta = to.subtract(from);
        int samples = Math.max(1, (int) Math.ceil(delta.length() * 3.0));
        net.minecraft.core.BlockPos previous = null;
        int solidBlocks = 0;
        for (int sample = 1; sample < samples; sample++) {
            net.minecraft.core.BlockPos position =
                    net.minecraft.core.BlockPos.containing(from.add(delta.scale((double) sample / samples)));
            if (position.equals(previous)) {
                continue;
            }
            previous = position;
            var state = level.getBlockState(position);
            if (state.canOcclude() && state.isCollisionShapeFullBlock(level, position)
                    && ++solidBlocks > 2) {
                return false;
            }
        }
        return true;
    }
}
