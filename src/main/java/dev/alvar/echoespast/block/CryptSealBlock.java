package dev.alvar.echoespast.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

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
