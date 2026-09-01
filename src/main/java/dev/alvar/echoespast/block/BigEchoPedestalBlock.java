package dev.alvar.echoespast.block;

import com.mojang.serialization.MapCodec;
import dev.alvar.echoespast.EchoesShowThePast;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * One authored 2x2 altar with a single rendered root. The root is the
 * north-east cell; the other three cells only provide occupancy and collision.
 * Inventory lives on the ORIGIN block entity.
 */
public final class BigEchoPedestalBlock extends BaseEntityBlock {
    public static final MapCodec<BigEchoPedestalBlock> CODEC = simpleCodec(BigEchoPedestalBlock::new);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private static final VoxelShape ORIGIN_SHAPE = Block.box(0.0, 0.0, 1.0, 15.0, 19.0, 16.0);
    private static final VoxelShape WEST_SHAPE = Block.box(1.0, 0.0, 1.0, 16.0, 19.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0.0, 0.0, 0.0, 15.0, 19.0, 15.0);
    private static final VoxelShape SOUTH_WEST_SHAPE = Block.box(1.0, 0.0, 0.0, 16.0, 19.0, 15.0);

    public BigEchoPedestalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.ORIGIN));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == Part.ORIGIN ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context) {
        return shapeFor(state.getValue(PART));
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context) {
        return shapeFor(state.getValue(PART));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return state.getValue(PART) == Part.ORIGIN
                ? new BigEchoPedestalBlockEntity(position, state)
                : null;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos origin = context.getClickedPos();
        for (Part part : Part.values()) {
            BlockPos cell = part.positionFrom(origin);
            if (!cell.equals(origin)
                    && !context.getLevel().getBlockState(cell).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        for (Part part : Part.values()) {
            level.setBlock(part.positionFrom(pos), state.setValue(PART, part), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            boolean movedByPiston) {
        BlockPos origin = state.getValue(PART).originFrom(pos);
        for (Part part : Part.values()) {
            BlockPos cell = part.positionFrom(origin);
            if (level.getBlockState(cell).is(this)) {
                level.setBlock(
                        cell,
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS);
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return interact(level, pos, player, ItemStack.EMPTY);
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
        return interact(level, pos, player, heldStack);
    }

    private InteractionResult interact(Level level, BlockPos pos, Player player, ItemStack held) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BigEchoPedestalBlockEntity altar = altarAt(level, pos);
        if (altar == null) {
            return InteractionResult.PASS;
        }
        if (altar.isLocked()) {
            player.sendOverlayMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.echoes_show_the_past.echo_altar_locked"));
            return InteractionResult.CONSUME;
        }

        if (!held.isEmpty()) {
            if (BigEchoPedestalBlockEntity.isPastFragment(held)) {
                if (!altar.tryInsertFragment(held)) {
                    player.sendOverlayMessage(
                            net.minecraft.network.chat.Component.translatable(
                                    "message.echoes_show_the_past.echo_altar_full"));
                    return InteractionResult.CONSUME;
                }
                if (!player.isCreative()) {
                    held.shrink(1);
                }
                level.playSound(
                        null,
                        pos,
                        SoundEvents.RESPAWN_ANCHOR_SET_SPAWN,
                        SoundSource.BLOCKS,
                        0.7F,
                        1.35F);
                return InteractionResult.SUCCESS_SERVER;
            }
            if (BigEchoPedestalBlockEntity.isPhilosophersStone(held)) {
                if (!altar.tryInsertStone(held)) {
                    player.sendOverlayMessage(
                            net.minecraft.network.chat.Component.translatable(
                                    "message.echoes_show_the_past.echo_altar_stone_occupied"));
                    return InteractionResult.CONSUME;
                }
                if (!player.isCreative()) {
                    held.shrink(1);
                }
                level.playSound(
                        null,
                        pos,
                        SoundEvents.AMETHYST_BLOCK_PLACE,
                        SoundSource.BLOCKS,
                        0.85F,
                        0.85F);
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.PASS;
        }

        if (altar.hasStone()) {
            ItemStack stone = altar.removeStone();
            if (!player.addItem(stone)) {
                player.drop(stone, false);
            }
            level.playSound(
                    null,
                    pos,
                    SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.BLOCKS,
                    0.8F,
                    0.9F);
            return InteractionResult.SUCCESS_SERVER;
        }
        ItemStack fragment = altar.removeLastFragment();
        if (!fragment.isEmpty()) {
            if (!player.addItem(fragment)) {
                player.drop(fragment, false);
            }
            level.playSound(
                    null,
                    pos,
                    SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.BLOCKS,
                    0.75F,
                    1.15F);
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

    public static @Nullable BigEchoPedestalBlockEntity altarAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BigEchoPedestalBlock)) {
            return null;
        }
        BlockPos origin = state.getValue(PART).originFrom(pos);
        return level.getBlockEntity(origin) instanceof BigEchoPedestalBlockEntity altar
                ? altar
                : null;
    }

    private static VoxelShape shapeFor(Part part) {
        return switch (part) {
            case ORIGIN -> ORIGIN_SHAPE;
            case WEST -> WEST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case SOUTH_WEST -> SOUTH_WEST_SHAPE;
        };
    }

    public enum Part implements StringRepresentable {
        ORIGIN(0, 0),
        WEST(-1, 0),
        SOUTH(0, 1),
        SOUTH_WEST(-1, 1);

        private final int offsetX;
        private final int offsetZ;

        Part(int offsetX, int offsetZ) {
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
        }

        public BlockPos positionFrom(BlockPos origin) {
            return origin.offset(offsetX, 0, offsetZ);
        }

        public BlockPos originFrom(BlockPos position) {
            return position.offset(-offsetX, 0, -offsetZ);
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
