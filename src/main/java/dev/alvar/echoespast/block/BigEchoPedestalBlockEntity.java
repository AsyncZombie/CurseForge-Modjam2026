package dev.alvar.echoespast.block;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.boss.UnknownEraSequence;
import dev.alvar.echoespast.item.PastEchoMemory;
import dev.alvar.echoespast.resonance.ResonanceColor;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import dev.alvar.echoespast.world.TimelessDimensions;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Echo Altar inventory: six Past Fragment sockets plus one Philosopher's Stone.
 * During the Unknown fight the inventory is locked and client orbit/explode
 * presentation is driven by {@link #orbitEraIndex} / cleared fragment slots.
 */
public final class BigEchoPedestalBlockEntity extends BlockEntity {
    public static final int FRAGMENT_SLOTS = UnknownEraSequence.STAGE_COUNT;

    private static final String FRAGMENTS_TAG = "fragments";
    private static final String STONE_TAG = "stone";
    private static final String LOCKED_TAG = "locked";
    private static final String ORBIT_ERA_TAG = "orbit_era";

    private final ItemStack[] fragments = new ItemStack[FRAGMENT_SLOTS];
    private ItemStack stone = ItemStack.EMPTY;
    private boolean locked;
    /** Canonical {@link UnknownEraSequence#eraIndex()}, or -1 when none orbits. */
    private int orbitEraIndex = -1;

    public BigEchoPedestalBlockEntity(BlockPos position, BlockState state) {
        super(EchoesShowThePast.BIG_ECHO_PEDESTAL_BLOCK_ENTITY.get(), position, state);
        Arrays.fill(fragments, ItemStack.EMPTY);
    }

    public ItemStack fragment(int slot) {
        return fragments[clampSlot(slot)];
    }

    public ItemStack[] fragmentsView() {
        return Arrays.copyOf(fragments, FRAGMENT_SLOTS);
    }

    public boolean hasFragment(int slot) {
        return !fragment(slot).isEmpty();
    }

    public int filledFragmentCount() {
        int count = 0;
        for (ItemStack stack : fragments) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public int firstEmptyFragmentSlot() {
        for (int i = 0; i < FRAGMENT_SLOTS; i++) {
            if (fragments[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public ItemStack stone() {
        return stone;
    }

    public boolean hasStone() {
        return !stone.isEmpty();
    }

    public boolean isLocked() {
        return locked;
    }

    public int orbitEraIndex() {
        return orbitEraIndex;
    }

    public void setLocked(boolean value) {
        if (locked != value) {
            locked = value;
            synchronize();
        }
    }

    public void setOrbitEraIndex(int eraIndex) {
        int clamped = eraIndex < 0
                ? -1
                : Math.clamp(eraIndex, 0, UnknownEraSequence.ERA_COUNT - 1);
        if (orbitEraIndex != clamped) {
            orbitEraIndex = clamped;
            synchronize();
        }
    }

    public boolean tryInsertFragment(ItemStack stack) {
        if (locked || !isPastFragment(stack)) {
            return false;
        }
        int slot = firstEmptyFragmentSlot();
        if (slot < 0) {
            return false;
        }
        fragments[slot] = stack.copyWithCount(1);
        synchronize();
        return true;
    }

    public boolean forceSetFragment(int slot, ItemStack stack) {
        int safe = clampSlot(slot);
        fragments[safe] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        synchronize();
        return true;
    }

    public ItemStack removeFragment(int slot) {
        if (locked) {
            return ItemStack.EMPTY;
        }
        int safe = clampSlot(slot);
        ItemStack removed = fragments[safe];
        fragments[safe] = ItemStack.EMPTY;
        if (!removed.isEmpty()) {
            synchronize();
        }
        return removed;
    }

    /** Fight explode: clears even while locked. */
    public ItemStack clearFragmentSlot(int slot) {
        int safe = clampSlot(slot);
        ItemStack removed = fragments[safe];
        fragments[safe] = ItemStack.EMPTY;
        if (!removed.isEmpty()) {
            synchronize();
        }
        return removed;
    }

    public ItemStack removeLastFragment() {
        if (locked) {
            return ItemStack.EMPTY;
        }
        for (int i = FRAGMENT_SLOTS - 1; i >= 0; i--) {
            if (!fragments[i].isEmpty()) {
                return removeFragment(i);
            }
        }
        return ItemStack.EMPTY;
    }

    public boolean tryInsertStone(ItemStack stack) {
        if (locked || !isPhilosophersStone(stack) || hasStone()) {
            return false;
        }
        stone = stack.copyWithCount(1);
        synchronize();
        return true;
    }

    public boolean forceSetStone(ItemStack stack) {
        stone = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        synchronize();
        return true;
    }

    public ItemStack removeStone() {
        if (locked) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = stone;
        stone = ItemStack.EMPTY;
        if (!removed.isEmpty()) {
            synchronize();
        }
        return removed;
    }

    public void clearAll() {
        Arrays.fill(fragments, ItemStack.EMPTY);
        stone = ItemStack.EMPTY;
        locked = false;
        orbitEraIndex = -1;
        synchronize();
    }

    /**
     * Empties sockets without spawning item entities. Used when the fight
     * pipeline replaces the multiblock and will restore inventory afterward.
     */
    public void discardContentsSilently() {
        Arrays.fill(fragments, ItemStack.EMPTY);
        stone = ItemStack.EMPTY;
    }

    public static ResonanceColor colorForFightSlot(int slot) {
        return UnknownEraSequence.forFightSlot(slot).fragmentColor();
    }

    /** Authored arena blueprint carried by each fight-slot fragment. */
    public static Identifier templateForFightSlot(int slot) {
        UnknownEraSequence era = UnknownEraSequence.forFightSlot(slot);
        return era.template(UnknownEraSequence.isRuinsSlot(slot));
    }

    /**
     * Sealed Past Fragment whose memory is the arena blueprint for this fight
     * slot — same template-reference pattern used by dungeon pedestals.
     */
    public static ItemStack createFightFragment(ServerLevel level, int slot) {
        Identifier template = templateForFightSlot(slot);
        Vec3i size = TimelessDimensions.ARENA_VOLUME;
        if (level != null) {
            Optional<StructureTemplate> loaded = level.getStructureManager().get(template);
            if (loaded.isPresent()) {
                size = loaded.get().getSize();
            }
        }
        BlockPos boundsMax = new BlockPos(
                Math.max(0, size.getX() - 1),
                Math.max(0, size.getY() - 1),
                Math.max(0, size.getZ() - 1));
        EchoSnapshot memory = EchoSnapshot.templateReference(
                TimelessDimensions.TIMELESS_VOID,
                TimelessDimensions.ARENA_ORIGIN,
                template,
                BlockPos.ZERO,
                boundsMax);
        return PastEchoMemory.createFragment(memory, Optional.of(colorForFightSlot(slot)));
    }

    public static boolean isPastFragment(ItemStack stack) {
        return stack.is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get());
    }

    public static boolean isPhilosophersStone(ItemStack stack) {
        return stack.is(EchoesShowThePast.PHILOSOPHERS_STONE.get());
    }

    private void synchronize() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private static int clampSlot(int slot) {
        return Math.clamp(slot, 0, FRAGMENT_SLOTS - 1);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Arrays.fill(fragments, ItemStack.EMPTY);
        for (int i = 0; i < FRAGMENT_SLOTS; i++) {
            fragments[i] = input.read(FRAGMENTS_TAG + i, ItemStack.OPTIONAL_CODEC)
                    .orElse(ItemStack.EMPTY);
        }
        stone = input.read(STONE_TAG, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        locked = input.getBooleanOr(LOCKED_TAG, false);
        orbitEraIndex = input.getIntOr(ORBIT_ERA_TAG, -1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < FRAGMENT_SLOTS; i++) {
            if (!fragments[i].isEmpty()) {
                output.store(FRAGMENTS_TAG + i, ItemStack.OPTIONAL_CODEC, fragments[i]);
            }
        }
        if (!stone.isEmpty()) {
            output.store(STONE_TAG, ItemStack.OPTIONAL_CODEC, stone);
        }
        output.putBoolean(LOCKED_TAG, locked);
        output.putInt(ORBIT_ERA_TAG, orbitEraIndex);
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
            for (ItemStack stack : fragments) {
                if (!stack.isEmpty()) {
                    Block.popResource(level, position.above(), stack);
                }
            }
            if (!stone.isEmpty()) {
                Block.popResource(level, position.above(), stone);
            }
            Arrays.fill(fragments, ItemStack.EMPTY);
            stone = ItemStack.EMPTY;
        }
        super.preRemoveSideEffects(position, state);
    }
}
