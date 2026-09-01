package dev.alvar.echoespast.relic;

import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.network.MedusaGazeVisualPayload;
import dev.alvar.echoespast.network.MedusaPetrifyPayload;
import dev.alvar.echoespast.network.PetrifiedMobMiningVisualPayload;
import dev.alvar.echoespast.entity.MedusaEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

public final class PetrifiedMobManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int RESET_AFTER_TICKS = 7;
    private static final Map<UUID, MiningSession> MINING = new HashMap<>();

    /**
     * Atomically turns one live statue into one item. The entity is only
     * discarded after its complete item data has been produced.
     */
    public static Optional<ItemStack> extract(LivingEntity living) {
        return extract(living, true);
    }

    public static Optional<ItemStack> extract(LivingEntity living, boolean keepHead) {
        if (!(living.level() instanceof ServerLevel level)
                || living.isRemoved()
                || !RelicEffects.isPermanentlyPetrified(living)) {
            return Optional.empty();
        }
        // Player statues cannot be discarded like mobs: clear the stone, then
        // kill so the victim gets a normal death/respawn instead of a voided session.
        if (living instanceof ServerPlayer player) {
            RelicEffects.clearPermanentPetrify(player);
            level.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.STONE_BREAK,
                    SoundSource.PLAYERS,
                    0.9F,
                    0.82F);
            player.hurtServer(
                    level,
                    level.damageSources().genericKill(),
                    Float.MAX_VALUE);
            return Optional.empty();
        }
        Optional<PetrifiedMobData> captured = PetrifiedMobData.capture(living);
        if (captured.isEmpty()) {
            return Optional.empty();
        }
        PetrifiedMobData data = captured.get();
        if (living instanceof MedusaEntity && !keepHead) {
            data = data.withHeadless(true);
        }
        ItemStack statue = new ItemStack(EchoesShowThePast.PETRIFIED_MOB.get());
        statue.set(EchoesShowThePast.PETRIFIED_MOB_DATA.get(), data);
        statue.set(
                DataComponents.CUSTOM_NAME,
                Component.translatable(
                        "item.echoes_show_the_past.petrified_mob.named",
                        living.getName()));
        living.discard();
        level.playSound(
                null,
                living.blockPosition(),
                SoundEvents.STONE_BREAK,
                SoundSource.PLAYERS,
                0.9F,
                0.82F);
        return Optional.of(statue);
    }

    public static void mine(ServerPlayer player, int entityId) {
        if (!(player.level().getEntity(entityId) instanceof LivingEntity living)
                || !RelicEffects.isPermanentlyPetrified(living)
                || player.distanceToSqr(living) > 36.0) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        boolean creative = player.isCreative();
        if (!creative && !tool.is(net.minecraft.tags.ItemTags.PICKAXES)) {
            return;
        }
        if (creative) {
            // Creative matches vanilla instant block break: one authorised hit
            // yields the statue item with no progressive chisel stage.
            MINING.remove(living.getUUID());
            dropStatue(player, living);
            sendMiningVisual(player, living, 0.0F, false);
            return;
        }
        long now = player.level().getGameTime();
        MiningSession previous = MINING.get(living.getUUID());
        if (previous != null && previous.lastTick() == now) {
            return;
        }
        float progress = previous == null || now - previous.lastTick() > RESET_AFTER_TICKS
                ? 0.0F
                : previous.progress();
        progress = Math.min(1.0F, progress + miningIncrement(tool));
        int stage = Math.min(9, Math.max(0, (int) (progress * 10.0F)));
        boolean visualImpact = previous == null
                || stage != previous.stage()
                || now % 4L == 0L;
        MINING.put(living.getUUID(), new MiningSession(progress, stage, now, living.getId()));
        if (visualImpact) {
            sendMiningVisual(player, living, progress, true);
            emitMiningImpact((ServerLevel) player.level(), living, progress);
        }
        if (progress < 1.0F) {
            return;
        }
        MINING.remove(living.getUUID());
        if (dropStatue(player, living).isPresent()) {
            tool.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
    }

    public static Optional<ItemEntity> dropStatue(
            ServerPlayer miner,
            LivingEntity living) {
        boolean medusa = living instanceof MedusaEntity;
        boolean alreadyHeadless = medusa
                && RelicEffects.isPermanentlyPetrified(living)
                && living.getData(EchoesShowThePast.PETRIFIED_POSE.get()).headless();
        boolean silk = hasSilkTouch(miner.level(), miner.getMainHandItem());
        boolean dropSeveredHead = medusa && !silk && !alreadyHeadless;
        boolean keepHead = !medusa || silk || alreadyHeadless;
        double x = living.getX();
        double y = living.getY() + living.getBbHeight() * 0.45;
        double z = living.getZ();
        Optional<ItemStack> extracted = extract(living, keepHead);
        if (extracted.isEmpty()) {
            return Optional.empty();
        }
        Optional<ItemEntity> statueDrop = spawnDroppedItem(
                miner,
                living.level(),
                x,
                y,
                z,
                extracted.get());
        if (dropSeveredHead) {
            spawnDroppedItem(
                    miner,
                    living.level(),
                    x,
                    y + 0.35,
                    z,
                    new ItemStack(EchoesShowThePast.MEDUSA_PETRIFIED_HEAD.get()));
        }
        return statueDrop;
    }

    public static boolean hasSilkTouch(net.minecraft.world.level.Level level, ItemStack tool) {
        if (tool.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        Optional<Holder.Reference<Enchantment>> silk = serverLevel.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(Enchantments.SILK_TOUCH);
        return silk.isPresent() && tool.getEnchantments().getLevel(silk.get()) > 0;
    }

    private static Optional<ItemEntity> spawnDroppedItem(
            ServerPlayer miner,
            net.minecraft.world.level.Level level,
            double x,
            double y,
            double z,
            ItemStack stack) {
        ItemEntity dropped = new ItemEntity(level, x, y, z, stack);
        dropped.setDefaultPickUpDelay();
        dropped.setDeltaMovement(
                (miner.getRandom().nextDouble() - 0.5) * 0.08,
                0.17,
                (miner.getRandom().nextDouble() - 0.5) * 0.08);
        if (!level.addFreshEntity(dropped)) {
            return Optional.empty();
        }
        return Optional.of(dropped);
    }

    public static float miningIncrement(ItemStack pickaxe) {
        float speed = Math.max(
                1.0F,
                pickaxe.getDestroySpeed(Blocks.STONE.defaultBlockState()));
        return Math.clamp(speed / 120.0F, 0.0125F, 0.10F);
    }

    public static void tickMining(ServerLevel level) {
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, MiningSession>> iterator = MINING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MiningSession> entry = iterator.next();
            MiningSession session = entry.getValue();
            if (now - session.lastTick() <= RESET_AFTER_TICKS) {
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (entity instanceof LivingEntity living && living.getId() == session.entityId()) {
                sendMiningVisual(living, 0.0F, false);
            }
            iterator.remove();
        }
    }

    private static void sendMiningVisual(
            LivingEntity living,
            float progress,
            boolean impact) {
        if (!(living.level() instanceof ServerLevel level)) {
            return;
        }
        PetrifiedMobMiningVisualPayload visual =
                new PetrifiedMobMiningVisualPayload(living.getId(), progress, impact);
        for (ServerPlayer viewer : level.players()) {
            sendMiningVisual(viewer, visual);
        }
    }

    private static void sendMiningVisual(
            ServerPlayer miner,
            LivingEntity living,
            float progress,
            boolean impact) {
        sendMiningVisual(living, progress, impact);
        if (miner.level() == living.level()) {
            return;
        }
        sendMiningVisual(
                miner,
                new PetrifiedMobMiningVisualPayload(living.getId(), progress, impact));
    }

    private static void sendMiningVisual(
            ServerPlayer viewer,
            PetrifiedMobMiningVisualPayload visual) {
        if (!viewer.connection.hasChannel(visual)) {
            return;
        }
        try {
            PacketDistributor.sendToPlayer(viewer, visual);
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // Headless GameTest connections reject play-to-client payloads.
        }
    }

    private static void emitMiningImpact(
            ServerLevel level,
            LivingEntity living,
            float progress) {
        level.playSound(
                null,
                living.blockPosition(),
                SoundEvents.STONE_HIT,
                SoundSource.PLAYERS,
                0.38F + progress * 0.22F,
                0.82F + progress * 0.16F);
        level.sendParticles(
                new BlockParticleOption(
                        ParticleTypes.BLOCK,
                        Blocks.STONE.defaultBlockState()),
                living.getX(),
                living.getY() + living.getBbHeight() * 0.55,
                living.getZ(),
                3,
                living.getBbWidth() * 0.24,
                living.getBbHeight() * 0.18,
                living.getBbWidth() * 0.24,
                0.035);
    }

    /**
     * Turns a gazed-at player into a left-behind stone memorial, then kills
     * them so inventory follows vanilla death rules. Worn gear moves onto the
     * statue so it is not duplicated by the death drop.
     */
    public static boolean leavePlayerStatueAndKill(
            ServerLevel level,
            ServerPlayer attacker,
            ServerPlayer victim) {
        return leavePlayerStatueAndKill(level, (LivingEntity) attacker, victim);
    }

    public static boolean leavePlayerStatueAndKill(
            ServerLevel level,
            LivingEntity attacker,
            ServerPlayer victim) {
        if (victim.isRemoved() || RelicEffects.isPermanentlyPetrified(victim)) {
            return false;
        }
        PetrifiedPose pose = PetrifiedPose.capture(victim);
        Mannequin memorial = EntityType.MANNEQUIN.create(
                level,
                EntitySpawnReason.TRIGGERED);
        if (memorial == null) {
            killPetrifiedVictim(level, attacker, victim);
            return true;
        }
        memorial.snapTo(
                victim.getX(),
                victim.getY(),
                victim.getZ(),
                victim.getYRot(),
                victim.getXRot());
        memorial.setYBodyRot(victim.yBodyRot);
        memorial.setYHeadRot(victim.yHeadRot);
        memorial.setMainArm(victim.getMainArm());
        memorial.setComponent(DataComponents.PROFILE, victim.getProfile());
        memorial.setCustomName(victim.getDisplayName());
        memorial.setCustomNameVisible(true);
        memorial.setSilent(true);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack gear = victim.getItemBySlot(slot);
            if (gear.isEmpty()) {
                continue;
            }
            memorial.setItemSlot(slot, gear.copy());
            victim.setItemSlot(slot, ItemStack.EMPTY);
        }
        if (!level.addFreshEntity(memorial)) {
            // Restore gear if the memorial failed to enter the world.
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack gear = memorial.getItemBySlot(slot);
                if (!gear.isEmpty()) {
                    victim.setItemSlot(slot, gear.copy());
                    memorial.setItemSlot(slot, ItemStack.EMPTY);
                }
            }
            killPetrifiedVictim(level, attacker, victim);
            return true;
        }
        RelicEffects.applyPermanentPetrify(memorial, pose);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                memorial,
                new MedusaPetrifyPayload(memorial.getId(), -1));
        killPetrifiedVictim(level, attacker, victim);
        return true;
    }

    private static void killPetrifiedVictim(
            ServerLevel level,
            LivingEntity attacker,
            ServerPlayer victim) {
        victim.hurtServer(
                level,
                level.damageSources().mobAttack(attacker),
                Float.MAX_VALUE);
        if (victim.isAlive() && !victim.isCreative() && !victim.isSpectator()) {
            victim.hurtServer(
                    level,
                    level.damageSources().genericKill(),
                    Float.MAX_VALUE);
        }
        sendDeathVisualReset(victim);
    }

    private static void sendDeathVisualReset(ServerPlayer victim) {
        MedusaGazeVisualPayload cancel = new MedusaGazeVisualPayload(
                MedusaGazeVisualPayload.CANCEL,
                1);
        MedusaPetrifyPayload clear = new MedusaPetrifyPayload(victim.getId(), 0);
        try {
            if (victim.connection.hasChannel(cancel)) {
                PacketDistributor.sendToPlayer(victim, cancel);
            }
            if (victim.connection.hasChannel(clear)) {
                PacketDistributor.sendToPlayer(victim, clear);
            }
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // Headless GameTest connections reject play-to-client payloads.
        }
    }

    /**
     * Recreates a statue without reusing its old UUID, then reapplies the
     * permanent pose attachment before the entity becomes visible.
     */
    public static Optional<LivingEntity> place(
            ServerLevel level,
            PetrifiedMobData statue,
            Vec3 position,
            float fallbackYaw) {
        try {
            Entity root = EntityType.loadEntityRecursive(
                    PetrifiedMobData.detachInteractionData(statue.entity().data()),
                    level,
                    EntitySpawnReason.LOAD,
                    entity -> entity);
            if (!(root instanceof LivingEntity living)) {
                return Optional.empty();
            }
            root.getSelfAndPassengers().forEach(entity -> entity.setUUID(UUID.randomUUID()));
            float capturedYaw = Float.isFinite(statue.entity().yRot())
                    ? statue.entity().yRot()
                    : 0.0F;
            float yaw = Float.isFinite(fallbackYaw)
                    ? Mth.wrapDegrees(fallbackYaw)
                    : capturedYaw;
            float facingDelta = Mth.wrapDegrees(yaw - capturedYaw);
            living.snapTo(position.x, position.y, position.z, yaw, statue.entity().xRot());
            PetrifiedPose pose = new PetrifiedPose(
                    true,
                    statue.entity().pose(),
                    statue.entity().ageInTicks(),
                    yaw,
                    statue.entity().xRot(),
                    statue.entity().bodyYRot() + facingDelta,
                    statue.entity().headYRot() + facingDelta,
                    statue.entity().animation(),
                    statue.modelPose(),
                    statue.headless());
            living.setData(EchoesShowThePast.PETRIFIED_POSE.get(), pose);
            RelicEffects.markStatuePersistent(living);
            pose.freezeCommon(living);
            if (!level.noCollision(living)) {
                return Optional.empty();
            }
            if (!level.addFreshEntity(living)) {
                return Optional.empty();
            }
            level.playSound(
                    null,
                    living.blockPosition(),
                    SoundEvents.STONE_PLACE,
                    SoundSource.PLAYERS,
                    0.85F,
                    0.9F);
            return Optional.of(living);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not restore petrified mob statue", exception);
            return Optional.empty();
        }
    }

    /**
     * Accepts the final client-evaluated bone pose once. The server still
     * decides which entity is a statue and rejects remote or oversized data.
     */
    public static void acceptModelPose(
            ServerPlayer reporter,
            int entityId,
            BakedModelPose modelPose) {
        if (modelPose.isEmpty()
                || modelPose.parts().size() > BakedModelPose.MAX_PARTS
                || !(reporter.level().getEntity(entityId) instanceof LivingEntity living)
                || reporter.distanceToSqr(living) > 48.0 * 48.0) {
            return;
        }
        PetrifiedPose pose = living.getExistingDataOrNull(
                EchoesShowThePast.PETRIFIED_POSE.get());
        if (pose == null || !pose.permanent() || !pose.modelPose().isEmpty()) {
            return;
        }
        living.setData(
                EchoesShowThePast.PETRIFIED_POSE.get(),
                pose.withModelPose(modelPose));
    }

    private PetrifiedMobManager() {
    }

    private record MiningSession(
            float progress,
            int stage,
            long lastTick,
            int entityId) {
    }
}
