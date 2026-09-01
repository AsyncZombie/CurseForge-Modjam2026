package dev.alvar.echoespast.block;

import com.mojang.serialization.MapCodec;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.world.CryptAccessGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A solid, worldgen-only harmonic seal with restrained ambient leakage. */
public final class CryptSealBlock extends Block {
    public static final MapCodec<CryptSealBlock> CODEC = simpleCodec(CryptSealBlock::new);

    public CryptSealBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            CryptAccessGate.hintLocked(player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack heldStack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult) {
        if (heldStack.is(EchoesShowThePast.LOW_FREQUENCY_RESONATOR.get())) {
            return InteractionResult.PASS;
        }
        return useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide()) {
            CryptAccessGate.hintLocked(player);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos position, RandomSource random) {
        if (random.nextInt(4) != 0) {
            return;
        }
        Direction face = Direction.getRandom(random);
        double x = position.getX() + 0.5 + face.getStepX() * 0.56;
        double y = position.getY() + 0.5 + face.getStepY() * 0.56;
        double z = position.getZ() + 0.5 + face.getStepZ() * 0.56;
        level.addParticle(
                ParticleTypes.REVERSE_PORTAL,
                x,
                y,
                z,
                face.getStepX() * 0.018,
                0.008 + face.getStepY() * 0.018,
                face.getStepZ() * 0.018);
    }
}
