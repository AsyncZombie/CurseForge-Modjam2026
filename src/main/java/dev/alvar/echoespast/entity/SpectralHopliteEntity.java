package dev.alvar.echoespast.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A server-driven, non-persistent hoplite used only by one phalanx row. */
public final class SpectralHopliteEntity extends Monster implements GeoEntity {
    private static final int DOWNWARD_FLOOR_SEARCH = 12;
    private static final double FLOOR_EPSILON = 0.04D;
    private static final EntityDataAccessor<Float> DIRECTION_X = SynchedEntityData.defineId(
            SpectralHopliteEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIRECTION_Z = SynchedEntityData.defineId(
            SpectralHopliteEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(
            SpectralHopliteEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ROW = SynchedEntityData.defineId(
            SpectralHopliteEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> END_TICK = SynchedEntityData.defineId(
            SpectralHopliteEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DISTANCE_REMAINING = SynchedEntityData.defineId(
            SpectralHopliteEntity.class,
            EntityDataSerializers.FLOAT);

    private static final RawAnimation MARCH =
            RawAnimation.begin().thenLoop("combat.greek.phalanx_march");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private boolean followsFormationGround;
    private double formationCeilingY;

    public SpectralHopliteEntity(
            EntityType<? extends SpectralHopliteEntity> type,
            Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        noPhysics = true;
        setInvulnerable(true);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.8D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DIRECTION_X, 0.0F);
        builder.define(DIRECTION_Z, 1.0F);
        builder.define(SPEED, 0.7F);
        builder.define(ROW, 0);
        builder.define(END_TICK, 0);
        builder.define(DISTANCE_REMAINING, 0.0F);
    }

    public void configure(
            Vec3 direction,
            Vec3 destination,
            double speed,
            int row,
            long endGameTick,
            boolean followsFormationGround) {
        Vec3 flat = new Vec3(direction.x, 0.0D, direction.z).normalize();
        entityData.set(DIRECTION_X, (float) flat.x);
        entityData.set(DIRECTION_Z, (float) flat.z);
        entityData.set(SPEED, (float) speed);
        entityData.set(ROW, row);
        entityData.set(END_TICK, (int) endGameTick);
        entityData.set(DISTANCE_REMAINING, (float) Math.sqrt(
                horizontalDistanceSqr(position(), destination)));
        this.followsFormationGround = followsFormationGround;
        this.formationCeilingY = getY();
        if (followsFormationGround) {
            double alignedY = formationSurfaceAt(getX(), getZ(), getY());
            setPos(getX(), alignedY, getZ());
        }
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
        float yaw = (float) Math.toDegrees(Math.atan2(-flat.x, flat.z));
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
    }

    /**
     * The formation is a memory projection, not a physical mob: it crosses authored
     * geometry on its locked line and remains dangerous for the full corridor.
     */
    public boolean beginMarch() {
        return entityData.get(DISTANCE_REMAINING) > 0.0F;
    }

    public int row() {
        return entityData.get(ROW);
    }

    public float fadeAlpha(float partialTick) {
        float remaining = entityData.get(END_TICK) - (level().getGameTime() + partialTick);
        return Math.clamp(remaining / 5.0F, 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            if (level().getGameTime() >= entityData.get(END_TICK)) {
                discard();
                return;
            }
            float remaining = entityData.get(DISTANCE_REMAINING);
            if (remaining <= 0.001F) {
                entityData.set(END_TICK, (int) Math.min(
                        entityData.get(END_TICK),
                        level().getGameTime() + 5L));
                return;
            }
            double step = Math.min(entityData.get(SPEED), remaining);
            double nextX = getX() + entityData.get(DIRECTION_X) * step;
            double nextZ = getZ() + entityData.get(DIRECTION_Z) * step;
            setPos(
                    nextX,
                    followsFormationGround
                            ? formationSurfaceAt(nextX, nextZ, getY())
                            : formationCeilingY,
                    nextZ);
            entityData.set(DISTANCE_REMAINING, (float) Math.max(0.0D, remaining - step));
        }
    }

    /**
     * Finds the highest unobstructed floor at or below the formation ceiling.
     * The fixed ceiling prevents walls, rubble and steps from making the row
     * appear to climb; when a clear floor returns after a dip, the row can align
     * with it again instead of remaining permanently underground.
     */
    private double formationSurfaceAt(double x, double z, double fallbackY) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int highestSupportY = (int) Math.floor(formationCeilingY - FLOOR_EPSILON);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int blockY = highestSupportY;
                blockY >= highestSupportY - DOWNWARD_FLOOR_SEARCH;
                blockY--) {
            cursor.set(blockX, blockY, blockZ);
            var shape = level().getBlockState(cursor).getCollisionShape(level(), cursor);
            if (shape.isEmpty()) {
                continue;
            }
            double surfaceY = blockY + shape.max(Direction.Axis.Y);
            if (surfaceY > formationCeilingY + FLOOR_EPSILON) {
                continue;
            }
            AABB candidate = getBoundingBox().move(
                    x - getX(),
                    surfaceY - getY(),
                    z - getZ());
            if (level().noCollision(this, candidate)) {
                return surfaceY;
            }
        }
        return fallbackY;
    }

    private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return dx * dx + dz * dz;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        // Phalanx props must never leave gear on the arena floor.
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                "march",
                0,
                state -> state.setAndContinue(MARCH)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        // Transient by contract: shouldBeSaved() is false.
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        // Transient by contract: no persisted state exists.
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
