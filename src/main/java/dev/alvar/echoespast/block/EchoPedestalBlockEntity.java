package dev.alvar.echoespast.block;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.item.PastEchoMemory;
import dev.alvar.echoespast.world.EchoPedestalIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.resources.Identifier;

public final class EchoPedestalBlockEntity extends BlockEntity {
    private static final String ECHO_TAG = "echo";
    private static final String STONE_TAG = "stone";
    private static final String SITE_TAG = "site";
    private ItemStack echo = ItemStack.EMPTY;
    private ItemStack stone = ItemStack.EMPTY;
    private Identifier site;

    public EchoPedestalBlockEntity(BlockPos position, BlockState state) {
        super(EchoesShowThePast.ECHO_PEDESTAL_BLOCK_ENTITY.get(), position, state);
    }

    public ItemStack echo() {
        return echo;
    }

    public boolean hasEcho() {
        return !echo.isEmpty();
    }

    public ItemStack stone() {
        return stone;
    }

    public boolean hasStone() {
        return !stone.isEmpty();
    }

    public Identifier site() {
        return site;
    }

    public void setSite(Identifier site) {
        this.site = site;
        synchronize();
    }

    public ItemStack removeEcho() {
        ItemStack removed = echo;
        echo = ItemStack.EMPTY;
        synchronize();
        return removed;
    }

    public void setEcho(ItemStack stack) {
        echo = normalizeFragment(stack);
        synchronize();
    }

    public boolean tryInsertStone(ItemStack stack) {
        if (hasStone()
                || stack == null
                || !stack.is(EchoesShowThePast.PHILOSOPHERS_STONE.get())) {
            return false;
        }
        stone = stack.copyWithCount(1);
        synchronize();
        return true;
    }

    public ItemStack removeStone() {
        ItemStack removed = stone;
        stone = ItemStack.EMPTY;
        if (!removed.isEmpty()) {
            synchronize();
        }
        return removed;
    }

    private static ItemStack normalizeFragment(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (stack.is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get())) {
            return stack.copyWithCount(1);
        }
        if (stack.is(EchoesShowThePast.PAST_ECHO.get())) {
            ItemStack migratedVessel = stack.copyWithCount(1);
            ItemStack fragment = PastEchoMemory.getFragment(migratedVessel);
            return fragment.isEmpty() ? ItemStack.EMPTY : fragment.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    private void synchronize() {
        setChanged();
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        boolean spent = echo.isEmpty();
        if (state.hasProperty(EchoPedestalBlock.SPENT)
                && state.getValue(EchoPedestalBlock.SPENT) != spent) {
            level.setBlock(worldPosition, state.setValue(EchoPedestalBlock.SPENT, spent), Block.UPDATE_ALL);
        } else {
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            EchoPedestalIndex.register(serverLevel, worldPosition);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        echo = normalizeFragment(input.read(ECHO_TAG, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        stone = input.read(STONE_TAG, ItemStack.OPTIONAL_CODEC)
                .filter(stack -> stack.is(EchoesShowThePast.PHILOSOPHERS_STONE.get()))
                .map(stack -> stack.copyWithCount(1))
                .orElse(ItemStack.EMPTY);
        site = input.read(SITE_TAG, Identifier.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!echo.isEmpty()) {
            output.store(ECHO_TAG, ItemStack.OPTIONAL_CODEC, echo);
        }
        if (!stone.isEmpty()) {
            output.store(STONE_TAG, ItemStack.OPTIONAL_CODEC, stone);
        }
        if (site != null) {
            output.store(SITE_TAG, Identifier.CODEC, site);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public void preRemoveSideEffects(BlockPos position, BlockState state) {
        if (level != null && !level.isClientSide()) {
            if (!echo.isEmpty()) {
                Block.popResource(level, position.above(), echo);
                echo = ItemStack.EMPTY;
            }
            if (!stone.isEmpty()) {
                Block.popResource(level, position.above(), stone);
                stone = ItemStack.EMPTY;
            }
        }
        super.preRemoveSideEffects(position, state);
    }
}
