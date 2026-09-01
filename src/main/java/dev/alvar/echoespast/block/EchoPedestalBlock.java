package dev.alvar.echoespast.block;

import com.mojang.serialization.MapCodec;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.relic.PhilosophersStoneItem;
import dev.alvar.echoespast.server.LowFrequencySonarManager;
import dev.alvar.echoespast.server.MaterializedEchoManager;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import dev.alvar.echoespast.world.EchoPedestalIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class EchoPedestalBlock extends BaseEntityBlock {
    public static final BooleanProperty SPENT = BooleanProperty.create("spent");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final MapCodec<EchoPedestalBlock> CODEC = simpleCodec(EchoPedestalBlock::new);

    /** Matches the authored Blockbench silhouette (top platform reaches y=19). */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0, 0.0, 1.0, 15.0, 2.0, 15.0),
            Block.box(2.0, 2.0, 2.0, 14.0, 4.0, 14.0),
            Block.box(4.0, 4.0, 4.0, 12.0, 12.0, 12.0),
            Block.box(3.0, 12.0, 3.0, 13.0, 14.0, 13.0),
            Block.box(1.0, 14.0, 1.0, 15.0, 19.0, 15.0));

    public EchoPedestalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(SPENT, true)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SPENT, POWERED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new EchoPedestalBlockEntity(position, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previousState, boolean movedByPiston) {
        super.onPlace(state, level, pos, previousState, movedByPiston);
        if (level instanceof ServerLevel serverLevel && !previousState.is(this)) {
            EchoPedestalIndex.register(serverLevel, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(
            BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        EchoPedestalIndex.silence(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            Orientation orientation,
            boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (level instanceof ServerLevel serverLevel) {
            EchoPedestalIndex.refresh(serverLevel, pos);
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(POWERED)) {
                level.setBlock(
                        pos,
                        state.setValue(POWERED, powered),
                        Block.UPDATE_CLIENTS);
                if (powered
                        && level.getBlockEntity(pos) instanceof EchoPedestalBlockEntity pedestal
                        && pedestal.hasEcho()) {
                    LowFrequencySonarManager.triggerRelay(serverLevel, pos);
                }
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return claim(level, pos, player, ItemStack.EMPTY);
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
        // The resonator must always receive its own use action. In particular,
        // sneaking with it cancels listening instead of accidentally claiming
        // the memory carried by the pedestal.
        if (heldStack.is(EchoesShowThePast.LOW_FREQUENCY_RESONATOR.get())) {
            return InteractionResult.PASS;
        }
        if (heldStack.getItem() instanceof PhilosophersStoneItem stone) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (!(player instanceof ServerPlayer serverPlayer)
                    || !(level.getBlockEntity(pos) instanceof EchoPedestalBlockEntity pedestal)) {
                return InteractionResult.CONSUME;
            }
            if (pedestal.hasStone()) {
                player.sendOverlayMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.echoes_show_the_past.stone_pedestal_active"));
                return InteractionResult.CONSUME;
            }
            return stone.useOnMemoryPedestal(
                    serverPlayer,
                    heldStack,
                    pedestal,
                    pos,
                    resolveSnapshot(pedestal.echo()));
        }
        return claim(level, pos, player, heldStack);
    }

    private InteractionResult claim(
            Level level,
            BlockPos pos,
            Player player,
            ItemStack held) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel)
                || !(level.getBlockEntity(pos) instanceof EchoPedestalBlockEntity pedestal)) {
            return InteractionResult.PASS;
        }

        if (pedestal.hasStone()) {
            if (!held.isEmpty()) {
                player.sendOverlayMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.echoes_show_the_past.stone_pedestal_active"));
                return InteractionResult.CONSUME;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                MaterializedEchoManager.cancelAtPedestal(
                        (ServerLevel) level,
                        pos,
                        serverPlayer);
            }
            ItemStack stone = pedestal.removeStone();
            if (!player.addItem(stone)) {
                player.drop(stone, false);
            }
            level.playSound(
                    null,
                    pos,
                    SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                    SoundSource.BLOCKS,
                    0.9F,
                    1.18F);
            return InteractionResult.SUCCESS_SERVER;
        }

        if (pedestal.hasEcho()) {
            if (isMemoryCarrier(held)) {
                player.sendOverlayMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.echoes_show_the_past.pedestal_occupied"));
                return InteractionResult.CONSUME;
            }
            ItemStack echo = pedestal.removeEcho();
            if (!player.addItem(echo)) {
                player.drop(echo, false);
            }
            level.playSound(
                    null,
                    pos,
                    SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.BLOCKS,
                    1.0F,
                    0.75F);
            EchoPedestalIndex.refresh((ServerLevel) level, pos);
            return InteractionResult.SUCCESS_SERVER;
        }

        if (!isMemoryCarrier(held)) {
            return InteractionResult.PASS;
        }
        if (resolveSnapshot(held) == null) {
            player.sendOverlayMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.echoes_show_the_past.pedestal_empty_memory"));
            return InteractionResult.CONSUME;
        }
        pedestal.setEcho(held.copyWithCount(1));
        if (!player.isCreative()) {
            held.shrink(1);
        }
        level.playSound(
                null,
                pos,
                SoundEvents.RESPAWN_ANCHOR_SET_SPAWN,
                SoundSource.BLOCKS,
                0.85F,
                1.22F);
        EchoPedestalIndex.refresh((ServerLevel) level, pos);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static boolean isMemoryCarrier(ItemStack stack) {
        return stack.is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get());
    }

    private static EchoSnapshot resolveSnapshot(ItemStack stack) {
        if (stack.is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get())) {
            return stack.get(EchoesShowThePast.ECHO_SNAPSHOT.get());
        }
        return null;
    }
}
