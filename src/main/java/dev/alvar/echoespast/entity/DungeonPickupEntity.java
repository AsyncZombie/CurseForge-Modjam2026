package dev.alvar.echoespast.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * One-shot dungeon loot display: holds any {@link ItemStack} (up to that item's
 * max stack), faces the camera on the client, and disappears after a single
 * left- or right-click collect.
 */
public final class DungeonPickupEntity extends Entity {
    public static final float DEFAULT_SCALE = 1.0F;
    public static final float MIN_SCALE = 0.25F;
    public static final float MAX_SCALE = 16.0F;
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(
            DungeonPickupEntity.class,
            EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(
            DungeonPickupEntity.class,
            EntityDataSerializers.FLOAT);

    private boolean collecting;

    public DungeonPickupEntity(EntityType<? extends DungeonPickupEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setInvulnerable(true);
    }

    public static DungeonPickupEntity create(Level level, double x, double y, double z, ItemStack stack) {
        return create(level, x, y, z, stack, DEFAULT_SCALE);
    }

    public static DungeonPickupEntity create(
            Level level, double x, double y, double z, ItemStack stack, float scale) {
        DungeonPickupEntity pickup = new DungeonPickupEntity(
                dev.alvar.echoespast.EchoesShowThePast.DUNGEON_PICKUP.get(),
                level);
        pickup.setPos(x, y, z);
        pickup.setYRot(0.0F);
        pickup.setItem(stack);
        pickup.setDisplayScale(scale);
        return pickup;
    }

    public ItemStack getItem() {
        return getEntityData().get(DATA_ITEM);
    }

    public void setItem(ItemStack stack) {
        if (stack.isEmpty()) {
            getEntityData().set(DATA_ITEM, ItemStack.EMPTY);
            return;
        }
        ItemStack copy = stack.copy();
        copy.setCount(Math.min(copy.getCount(), copy.getMaxStackSize()));
        getEntityData().set(DATA_ITEM, copy);
    }

    public float getDisplayScale() {
        return getEntityData().get(DATA_SCALE);
    }

    public void setDisplayScale(float scale) {
        getEntityData().set(DATA_SCALE, clampScale(scale));
        refreshDimensions();
    }

    public static float clampScale(float scale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale)) {
            return DEFAULT_SCALE;
        }
        return Math.clamp(scale, MIN_SCALE, MAX_SCALE);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(getDisplayScale());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_SCALE.equals(accessor)) {
            refreshDimensions();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
        builder.define(DATA_SCALE, DEFAULT_SCALE);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (!getItem().isEmpty()) {
            output.store("Item", ItemStack.CODEC, getItem());
        }
        output.putFloat("Scale", getDisplayScale());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setItem(input.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        setDisplayScale(input.getFloatOr("Scale", DEFAULT_SCALE));
        if (getItem().isEmpty()) {
            discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide() && getItem().isEmpty() && !collecting) {
            discard();
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        return tryCollect(player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getEntity() instanceof Player player) {
            return tryCollect(player);
        }
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity source) {
        if (source instanceof Player player) {
            tryCollect(player);
            return true;
        }
        return false;
    }

    @Override
    protected Component getTypeName() {
        ItemStack stack = getItem();
        return stack.isEmpty() ? super.getTypeName() : stack.getHoverName();
    }

    /**
     * Gives the held stack once, then removes this entity. Safe to call from
     * interact (right-click) or attack (left-click).
     */
    public boolean tryCollect(Player player) {
        if (!isAlive() || collecting) {
            return false;
        }
        ItemStack held = getItem();
        if (held.isEmpty()) {
            if (!level().isClientSide()) {
                discard();
            }
            return false;
        }
        if (level().isClientSide()) {
            return true;
        }

        collecting = true;
        ItemStack give = held.copy();
        setItem(ItemStack.EMPTY);
        if (!player.addItem(give)) {
            player.drop(give, false);
        } else if (!give.isEmpty()) {
            player.drop(give, false);
        }
        playSound(SoundEvents.ITEM_PICKUP, 0.2F, 1.0F + this.random.nextFloat() * 0.4F);
        discard();
        return true;
    }
}
