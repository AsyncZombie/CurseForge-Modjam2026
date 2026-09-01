package dev.alvar.echoespast.relic;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.util.ClientUtil;
import com.geckolib.util.GeckoLibUtil;
import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.entity.UnknownEntity;
import dev.alvar.echoespast.client.MedusaHeadRenderProvider;
import dev.alvar.echoespast.network.MedusaGazeVisualPayload;
import dev.alvar.echoespast.network.MedusaPetrifyPayload;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Head of Medusa — channel a chaotic gaze that permanently petrifies living
 * targets in a look cone. Subclasses adjust boss/player rules and charge wear.
 */
public class MedusaHeadItem extends RelicItem implements GeoItem {
    public static final int WARMUP_TICKS = 10;
    public static final int MAX_CHANNEL_TICKS = 80;
    private static final int SCAN_INTERVAL_TICKS = 2;
    private static final int COOLDOWN_TICKS = 45 * 20;

    public static final DataTicket<Boolean> RENDER_ACTIVE = DataTicket.create(
            "echoes_show_the_past.medusa_head_active",
            Boolean.class);
    public static final DataTicket<Float> RENDER_POSE_BLEND = DataTicket.create(
            "echoes_show_the_past.medusa_head_pose_blend",
            Float.class);

    private static final RawAnimation IDLE =
            RawAnimation.begin().thenLoop("animation.model.idle");
    private static final RawAnimation ACTIVE =
            RawAnimation.begin().thenLoop("animation.model.active");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public MedusaHeadItem(Properties properties) {
        super(properties);
    }

    public static boolean isMedusaHead(ItemStack stack) {
        return stack.is(EchoesShowThePast.MEDUSA_HEAD.get())
                || stack.is(EchoesShowThePast.MEDUSA_PETRIFIED_HEAD.get());
    }

    /**
     * Renderer-safe active-pose check. GeckoLib's special item perspective is
     * not a reliable description of the owner's configured main arm, so the
     * pose follows the actual use stack instead of trying to reconstruct which
     * physical arm is currently being submitted.
     */
    public static boolean rendersActivePose(
            LivingEntity owner,
            ItemStack renderedStack) {
        return owner != null
                && owner.isUsingItem()
                && isMedusaHead(owner.getUseItem())
                && isMedusaHead(renderedStack);
    }

    /**
     * Carved pumpkin on the head is the sole gaze ward, matching the mythic
     * mirror/ward fantasy. Anything else in the cone is fair game when rules
     * allow it.
     */
    public static boolean isProtectedByPumpkin(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN);
    }

    public static boolean isBoss(LivingEntity entity) {
        return entity.getType().builtInRegistryHolder().is(Tags.EntityTypes.BOSSES);
    }

    protected boolean canPetrifyPlayers() {
        return EchoesConfig.MEDUSA_AFFECTS_PLAYERS.getAsBoolean();
    }

    protected boolean canPetrifyBosses() {
        return EchoesConfig.MEDUSA_AFFECTS_BOSSES.getAsBoolean();
    }

    protected int maximumCharges() {
        return 0;
    }

    /** Gate for starting a channel (durability, charges, etc.). */
    protected boolean canActivate(ItemStack stack) {
        return true;
    }

    /** Called after a successful channel (past warmup). Default: no wear. */
    protected void onSuccessfulActivation(
            ItemStack stack, ServerLevel level, ServerPlayer user) {}

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            state(stack, serverPlayer, maximumCharges());
            if (!canActivate(stack)) {
                serverPlayer.sendOverlayMessage(
                        Component.translatable("message.echoes_show_the_past.relic_exhausted"));
                return InteractionResult.FAIL;
            }
            if (RelicCooldownManager.synchronizeStack(serverPlayer, stack)) {
                serverPlayer.sendOverlayMessage(
                        Component.translatable("message.echoes_show_the_past.relic_cooling"));
                return InteractionResult.CONSUME;
            }
        }
        if (!canActivate(stack) || player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.CONSUME;
        }
        player.startUsingItem(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new MedusaGazeVisualPayload(
                            MedusaGazeVisualPayload.START,
                            MAX_CHANNEL_TICKS));
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return MAX_CHANNEL_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public void onUseTick(
            Level level,
            LivingEntity living,
            ItemStack stack,
            int remainingUseDuration) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(living instanceof ServerPlayer user)) {
            return;
        }
        int elapsed = MAX_CHANNEL_TICKS - remainingUseDuration;
        if (elapsed < WARMUP_TICKS || elapsed % SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        int contacts = applyGaze(serverLevel, user);
        if (contacts > 0) {
            PacketDistributor.sendToPlayer(
                    user,
                    new MedusaGazeVisualPayload(MedusaGazeVisualPayload.CONTACT, 8));
        }
    }

    private int applyGaze(ServerLevel serverLevel, ServerPlayer user) {
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getLookAngle().normalize();
        AABB area = user.getBoundingBox().inflate(16.0);
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(
                LivingEntity.class,
                area,
                target -> target != user
                        && target.isAlive()
                        && MedusaGazeMath.contains(
                                eye,
                                look,
                                target.getEyePosition(),
                                16.0,
                                0.82)
                        && user.hasLineOfSight(target));
        int contacts = 0;
        for (LivingEntity target : targets) {
            if (!canRelicPetrify(target, user)) {
                continue;
            }
            if (target instanceof ServerPlayer victim) {
                if (PetrifiedMobManager.leavePlayerStatueAndKill(
                        serverLevel,
                        user,
                        victim)) {
                    contacts++;
                }
                continue;
            }
            RelicEffects.petrifyPermanently(target);
            contacts++;
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    target,
                    new MedusaPetrifyPayload(target.getId(), -1));
        }
        return contacts;
    }

    /**
     * Living Head of Medusa can freeze the gorgon herself. The brittle
     * petrified head still cannot freeze bosses, and Unknown stays immune.
     */
    public boolean canRelicPetrify(LivingEntity target, ServerPlayer user) {
        if (target == user || !target.isAlive()) {
            return false;
        }
        if (UnknownEntity.isUnknown(target)) {
            return false;
        }
        if (isProtectedByPumpkin(target)) {
            return false;
        }
        if (RelicEffects.isPermanentlyPetrified(target)) {
            return false;
        }
        if (target instanceof Player player) {
            return canPetrifyPlayers()
                    && !player.isCreative()
                    && !player.isSpectator()
                    && user.canHarmPlayer(player);
        }
        return !isBoss(target) || canPetrifyBosses();
    }

    private void finishChannel(
            ItemStack stack,
            ServerLevel serverLevel,
            ServerPlayer user,
            int elapsedTicks) {
        if (elapsedTicks < WARMUP_TICKS) {
            user.getCooldowns().addCooldown(stack, 10);
            PacketDistributor.sendToPlayer(
                    user,
                    new MedusaGazeVisualPayload(MedusaGazeVisualPayload.CANCEL, 7));
            return;
        }
        RelicState state = state(stack, user, maximumCharges());
        long cooldownNow = serverLevel.getGameTime();
        stack.set(
                EchoesShowThePast.RELIC_STATE.get(),
                state.withCooldown(cooldownNow + COOLDOWN_TICKS));
        user.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        onSuccessfulActivation(stack, serverLevel, user);
        PacketDistributor.sendToPlayer(
                user,
                new MedusaGazeVisualPayload(MedusaGazeVisualPayload.CANCEL, 10));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // Cooldown + gaze cancel run from onStopUsing, which always follows finish/release
        // and also covers hotbar swaps that only call stopUsingItem.
        return stack;
    }

    @Override
    public boolean releaseUsing(
            ItemStack stack,
            Level level,
            LivingEntity entity,
            int timeLeft) {
        // See onStopUsing — releaseUsing alone is skipped when the player changes slots.
        return false;
    }

    /**
     * Hotbar swaps call {@link LivingEntity#stopUsingItem()} without
     * {@link #releaseUsing}, so gaze teardown must live here.
     *
     * @param count remaining use ticks (same meaning as {@code timeLeft} in
     *     {@link #releaseUsing})
     */
    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        if (entity.level() instanceof ServerLevel serverLevel
                && entity instanceof ServerPlayer player) {
            int elapsed = MAX_CHANNEL_TICKS - Math.clamp(count, 0, MAX_CHANNEL_TICKS);
            finishChannel(stack, serverLevel, player, elapsed);
        }
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new MedusaHeadRenderProvider());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("gaze", 5, this::gazeAnimation));
    }

    private PlayState gazeAnimation(AnimationTest<MedusaHeadItem> state) {
        // GeckoLib's MAX_USE_DURATION is the item's max use length, not "is using".
        ItemDisplayContext perspective = state.getData(DataTickets.ITEM_RENDER_PERSPECTIVE);
        if (perspective == ItemDisplayContext.GUI
                || perspective == ItemDisplayContext.GROUND
                || perspective == ItemDisplayContext.FIXED
                || perspective == ItemDisplayContext.NONE) {
            return state.setAndContinue(IDLE);
        }
        Boolean renderedActive = state.getData(RENDER_ACTIVE);
        Player player = ClientUtil.getClientPlayer();
        boolean using = renderedActive != null
                ? renderedActive
                : player != null
                        && player.isUsingItem()
                        && isMedusaHead(player.getUseItem());
        return state.setAndContinue(using ? ACTIVE : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public boolean isPerspectiveAware() {
        return true;
    }
}
