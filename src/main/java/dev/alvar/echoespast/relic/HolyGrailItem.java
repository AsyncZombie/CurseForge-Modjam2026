package dev.alvar.echoespast.relic;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.network.HolyGrailVisualPayload;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HolyGrailItem extends RelicItem {
    public static final int MAX_CHARGES = 5;
    public static final int CHANNEL_TICKS = 28;
    public static final int AURA_TICKS = 160;
    private static final int COOLDOWN_TICKS = 50;

    public HolyGrailItem(Properties properties) {
        super(properties);
    }

    /**
     * Pure recharge rule kept separate from interaction handling so daily
     * eligibility and charge capacity can be verified without a client.
     */
    public static RelicState rechargeStateForDay(
            RelicState state,
            long day) {
        if (day == state.lastRechargeDay()
                || state.charges() >= MAX_CHARGES) {
            return state;
        }
        return state.withRecharge(
                MAX_CHARGES,
                MAX_CHARGES,
                day);
    }

    public static Optional<BlockHitResult> targetedWaterSource(
            Level level,
            Player player) {
        BlockHitResult hit = getPlayerPOVHitResult(
                level,
                player,
                ClipContext.Fluid.SOURCE_ONLY);
        return hit.getType() == HitResult.Type.BLOCK
                        && level.getFluidState(hit.getBlockPos())
                                .is(FluidTags.WATER)
                ? Optional.of(hit)
                : Optional.empty();
    }

    @Override
    public InteractionResult use(
            Level level,
            Player player,
            InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Optional<BlockHitResult> waterHit =
                targetedWaterSource(level, player);
        if (waterHit.isPresent()) {
            if (level.isClientSide()) {
                RelicState clientState = stack.getOrDefault(
                        EchoesShowThePast.RELIC_STATE.get(),
                        RelicState.EMPTY);
                long day =
                        level.getOverworldClockTime() / 24_000L;
                if (clientState.originalOwner().isPresent()
                        && !rechargeStateForDay(clientState, day)
                                .equals(clientState)) {
                    return InteractionResult.SUCCESS;
                }
            }
            if (level instanceof ServerLevel serverLevel
                    && player instanceof ServerPlayer serverPlayer) {
                if (rechargeFromWater(
                        serverLevel,
                        serverPlayer,
                        stack,
                        waterHit.get())) {
                    return InteractionResult.SUCCESS_SERVER;
                }
            }
        }
        if (level instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer
                && cancelActiveAura(serverPlayer)) {
            player.sendOverlayMessage(Component.translatable(
                    "message.echoes_show_the_past.grail_cancelled"));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            RelicState state = state(stack, serverPlayer, MAX_CHARGES);
            if (state.charges() <= 0) {
                player.sendOverlayMessage(Component.translatable(
                        "message.echoes_show_the_past.no_relic_charges"));
                return InteractionResult.CONSUME;
            }
            if (player.getCooldowns().isOnCooldown(stack)) {
                return InteractionResult.CONSUME;
            }
        }

        player.startUsingItem(hand);
        if (level instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    serverPlayer,
                    new HolyGrailVisualPayload(
                            serverPlayer.getId(),
                            HolyGrailVisualPayload.START,
                            CHANNEL_TICKS));
            serverLevel.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.BOTTLE_FILL,
                    SoundSource.PLAYERS,
                    0.42F,
                    0.48F);
        }
        return level.isClientSide()
                ? InteractionResult.SUCCESS
                : InteractionResult.CONSUME;
    }

    public static boolean cancelActiveAura(ServerPlayer player) {
        if (!RelicEffects.cancelGrailAura(player)) {
            return false;
        }
        HolyGrailVisualPayload payload = new HolyGrailVisualPayload(
                player.getId(),
                HolyGrailVisualPayload.CANCEL,
                9);
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);
        }
        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS,
                0.48F,
                1.20F);
        return true;
    }

    private static boolean rechargeFromWater(
            ServerLevel level,
            ServerPlayer player,
            ItemStack stack,
            BlockHitResult hit) {
        RelicState state = stateForRecharge(stack, player);
        long day = level.getOverworldClockTime() / 24_000L;
        if (state.charges() >= MAX_CHARGES) {
            return false;
        }

        RelicState recharged = rechargeStateForDay(state, day);
        if (recharged.equals(state)) {
            return false;
        }

        stack.set(
                EchoesShowThePast.RELIC_STATE.get(),
                recharged);
        player.sendOverlayMessage(
                Component.translatable(
                        "message.echoes_show_the_past.grail_recharged",
                        MAX_CHARGES));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new HolyGrailVisualPayload(
                        player.getId(),
                        HolyGrailVisualPayload.RECHARGE,
                        30));
        level.playSound(
                null,
                hit.getBlockPos(),
                SoundEvents.BOTTLE_FILL,
                SoundSource.PLAYERS,
                1.0F,
                0.72F);
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.72F,
                1.35F);
        level.sendParticles(
                ParticleTypes.SPLASH,
                hit.getLocation().x,
                hit.getLocation().y + 0.08,
                hit.getLocation().z,
                28,
                0.34,
                0.10,
                0.34,
                0.14);
        level.sendParticles(
                ParticleTypes.END_ROD,
                hit.getLocation().x,
                hit.getLocation().y + 0.18,
                hit.getLocation().z,
                9,
                0.22,
                0.12,
                0.22,
                0.015);
        return true;
    }

    private static RelicState stateForRecharge(
            ItemStack stack,
            ServerPlayer player) {
        RelicState current = stack.getOrDefault(
                EchoesShowThePast.RELIC_STATE.get(),
                RelicState.EMPTY);
        if (current.originalOwner().isEmpty()) {
            current = new RelicState(
                    java.util.Optional.of(player.getUUID()),
                    MAX_CHARGES,
                    current.lastRechargeDay(),
                    current.cooldownUntil());
            stack.set(EchoesShowThePast.RELIC_STATE.get(), current);
        }
        return current;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return CHANNEL_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.DRINK;
    }

    @Override
    public void onUseTick(
            Level level,
            LivingEntity living,
            ItemStack stack,
            int remainingUseDuration) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(living instanceof ServerPlayer player)) {
            return;
        }
        int elapsed = CHANNEL_TICKS - remainingUseDuration;
        if (elapsed <= 0 || elapsed % 7 != 0) {
            return;
        }
        double angle = elapsed * 0.83;
        serverLevel.sendParticles(
                ParticleTypes.END_ROD,
                player.getX() + Math.cos(angle) * 0.30,
                player.getY() + 0.55 + elapsed / (double) CHANNEL_TICKS,
                player.getZ() + Math.sin(angle) * 0.30,
                1,
                0.015,
                0.025,
                0.015,
                0.0);
    }

    @Override
    public ItemStack finishUsingItem(
            ItemStack stack,
            Level level,
            LivingEntity entity) {
        if (level instanceof ServerLevel serverLevel
                && entity instanceof ServerPlayer player) {
            completeRitual(serverLevel, player, stack);
        }
        return stack;
    }

    @Override
    public boolean releaseUsing(
            ItemStack stack,
            Level level,
            LivingEntity entity,
            int timeLeft) {
        if (level instanceof ServerLevel serverLevel
                && entity instanceof ServerPlayer player
                && timeLeft > 0) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    player,
                    new HolyGrailVisualPayload(
                            player.getId(),
                            HolyGrailVisualPayload.CANCEL,
                            9));
            serverLevel.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.BOTTLE_EMPTY,
                    SoundSource.PLAYERS,
                    0.22F,
                    1.55F);
        }
        return false;
    }

    private static void completeRitual(
            ServerLevel level,
            ServerPlayer player,
            ItemStack stack) {
        if (!applyRitualEffects(level, player, stack)) {
            return;
        }

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new HolyGrailVisualPayload(
                        player.getId(),
                        HolyGrailVisualPayload.RELEASE,
                        AURA_TICKS));

        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS,
                0.72F,
                1.42F);
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                1.0F,
                0.72F);
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.PLAYER_SPLASH_HIGH_SPEED,
                SoundSource.PLAYERS,
                0.48F,
                1.68F);
        level.sendParticles(
                ParticleTypes.END_ROD,
                player.getX(),
                player.getY() + player.getBbHeight() * 0.55,
                player.getZ(),
                30,
                0.75,
                0.85,
                0.75,
                0.035);
        level.sendParticles(
                ParticleTypes.SPLASH,
                player.getX(),
                player.getY() + 0.15,
                player.getZ(),
                24,
                1.15,
                0.10,
                1.15,
                0.18);
    }

    /**
     * Applies the authoritative gameplay transaction independently from its
     * presentation. Keeping this atomic makes it impossible for a visual
     * packet failure to spend a charge without granting the blessing.
     */
    public static boolean applyRitualEffects(
            ServerLevel level,
            ServerPlayer player,
            ItemStack stack) {
        RelicState state = stack.getOrDefault(
                EchoesShowThePast.RELIC_STATE.get(),
                RelicState.EMPTY);
        if (state.charges() <= 0) {
            return false;
        }

        stack.set(
                EchoesShowThePast.RELIC_STATE.get(),
                state.withCharges(state.charges() - 1, MAX_CHARGES));
        player.heal(12.0F);
        for (MobEffectInstance effect :
                new ArrayList<>(player.getActiveEffects())) {
            MobEffect mobEffect = effect.getEffect().value();
            if (!mobEffect.isBeneficial()) {
                player.removeEffect(effect.getEffect());
            }
        }

        long start = level.getGameTime();
        RelicEffects.startGrailAura(player, start, start + AURA_TICKS);
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        return true;
    }
}
