package dev.alvar.echoespast.block;

import com.mojang.serialization.MapCodec;
import dev.alvar.echoespast.server.UnknownFightManager;
import dev.alvar.echoespast.world.TimelessDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;

public final class TimelessPortalBlock extends Block {
    public static final MapCodec<TimelessPortalBlock> CODEC = simpleCodec(TimelessPortalBlock::new);

    public TimelessPortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(
            BlockState state,
            Level level,
            BlockPos pos,
            Entity entity,
            InsideBlockEffectApplier effectApplier,
            boolean stillInside) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (!player.canUsePortal(false) || player.isPassenger() || player.isVehicle()) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Match nether portals: without this, landing on the paired pad re-fires immediately.
        player.setPortalCooldown();
        if (serverLevel.dimension().equals(TimelessDimensions.TIMELESS_VOID)) {
            UnknownFightManager.exitToReturn(player);
        } else {
            UnknownFightManager.enterFromOverworld(player);
        }
    }
}
