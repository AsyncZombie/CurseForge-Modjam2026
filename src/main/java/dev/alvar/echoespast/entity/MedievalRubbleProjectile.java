package dev.alvar.echoespast.entity;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.entity.combat.UnknownGreekCombatMath;
import dev.alvar.echoespast.server.UnknownFightManager;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Single-impact, server-authoritative projectile kicked from authored rubble. */
public final class MedievalRubbleProjectile extends ThrowableItemProjectile {
    public static final float SPEED = 0.55F;
    public static final float DAMAGE = 7.0F;
    public static final float BLOCKED_DAMAGE = 3.0F;
    public static final int MAX_LIFETIME_TICKS = 60;
    public static final double KNOCKBACK = 0.82D;

    private boolean impacted;

    public MedievalRubbleProjectile(
            EntityType<? extends MedievalRubbleProjectile> type,
            Level level) {
        super(type, level);
    }

    public MedievalRubbleProjectile(
            ServerLevel level,
            UnknownEntity owner,
            Vec3 origin,
            Vec3 direction) {
        super(
                EchoesShowThePast.MEDIEVAL_RUBBLE_PROJECTILE.get(),
                origin.x,
                origin.y,
                origin.z,
                level,
                new ItemStack(Items.COBBLESTONE));
        setOwner(owner);
        Vec3 flight = direction.lengthSqr() <= 1.0E-8D
                ? owner.getLookAngle()
                : direction.normalize();
        setDeltaMovement(flight.scale(SPEED));
    }

    @Override
    protected Item getDefaultItem() {
        return Items.COBBLESTONE;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.012D;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount >= MAX_LIFETIME_TICKS) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity)
                || !(entity instanceof ServerPlayer player)
                || !(getOwner() instanceof UnknownEntity boss)) {
            return false;
        }
        return UnknownFightManager.isMedievalRuinsTarget(boss, player);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        if (impacted
                || !(level() instanceof ServerLevel level)
                || !(hitResult.getEntity() instanceof ServerPlayer target)
                || !(getOwner() instanceof UnknownEntity boss)) {
            return;
        }
        impacted = true;
        Vec3 flight = getDeltaMovement().horizontalDistanceSqr() > 1.0E-8D
                ? getDeltaMovement().normalize()
                : target.position().subtract(position()).normalize();
        boolean blocked = target.isBlocking()
                && UnknownGreekCombatMath.isInsideFrontArc(
                        target.getLookAngle(), flight.reverse(), 180.0D);
        if (blocked) {
            // This attack has authored chip damage. Lowering the active use for
            // this impact prevents vanilla's second shield reduction from
            // turning the explicit three damage into zero.
            target.stopUsingItem();
        }
        float damage = damageFor(blocked);
        boolean damaged = target.hurtServer(
                level,
                damageSources().thrown(this, boss),
                damage);
        Vec3 push = new Vec3(flight.x, 0.0D, flight.z);
        if (push.lengthSqr() > 1.0E-8D) {
            push = push.normalize();
            target.push(push.x * KNOCKBACK, 0.14D, push.z * KNOCKBACK);
            target.hurtMarked = true;
        }
        boss.showCombatFx(
                blocked ? UnknownEntity.COMBAT_FX_PLAYER_BLOCK : UnknownEntity.COMBAT_FX_HIT,
                target.position().add(0.0D, target.getBbHeight() * 0.45D, 0.0D),
                level.getGameTime());
        level.playSound(
                null,
                target.blockPosition(),
                blocked ? SoundEvents.SHIELD_BLOCK.value() : SoundEvents.STONE_BREAK,
                SoundSource.HOSTILE,
                blocked ? 1.15F : 0.95F,
                damaged ? 0.72F : 0.9F);
    }

    /** Deliberately does not forward to BlockState#onProjectileHit. */
    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        // Ruins rubble is visual combat matter: it never presses, breaks or alters blocks.
    }

    @Override
    protected void onHit(HitResult hitResult) {
        if (impacted) {
            broadcastAndDiscard();
            return;
        }
        super.onHit(hitResult);
        impacted = true;
        broadcastAndDiscard();
    }

    private void broadcastAndDiscard() {
        if (!level().isClientSide()) {
            level().broadcastEntityEvent(this, (byte) 3);
            discard();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id != 3) {
            super.handleEntityEvent(id);
            return;
        }
        ParticleOptions particle = new ItemParticleOption(
                ParticleTypes.ITEM,
                ItemStackTemplate.fromNonEmptyStack(getItem()));
        for (int index = 0; index < 10; index++) {
            level().addParticle(
                    particle,
                    getX(),
                    getY(),
                    getZ(),
                    random.nextGaussian() * 0.055D,
                    random.nextDouble() * 0.08D,
                    random.nextGaussian() * 0.055D);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Impacted", impacted);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        impacted = input.getBooleanOr("Impacted", false);
    }

    public static float damageFor(boolean blocked) {
        return blocked ? BLOCKED_DAMAGE : DAMAGE;
    }

    public boolean hasImpacted() {
        return impacted;
    }
}
