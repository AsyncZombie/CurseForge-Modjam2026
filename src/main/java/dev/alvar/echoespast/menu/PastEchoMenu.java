package dev.alvar.echoespast.menu;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.item.PastEchoMemory;
import dev.alvar.echoespast.server.EchoProjectionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Single-socket vessel console. The Past Echo remains in-hand; only the
 * nested Past Fragment moves through the ceremonial slot.
 *
 * Layout (256×240 reliquary atlas):
 * <pre>
 *   header  y 5–27
 *   chamber y 32–114  · fragment slot (120,56)
 *   trough  y 94–111  · color button (68,96) 120×14
 *   vault   y 120+    · inventory (47,136) / hotbar +58
 * </pre>
 */
public final class PastEchoMenu extends AbstractContainerMenu {
    public static final int CYCLE_COLOR_BUTTON = 0;

    public static final int FRAGMENT_SLOT_X = 120;
    public static final int FRAGMENT_SLOT_Y = 56;
    public static final int PLAYER_INVENTORY_X = 47;
    public static final int PLAYER_INVENTORY_Y = 136;

    public static final int COLOR_BUTTON_X = 68;
    public static final int COLOR_BUTTON_Y = 96;
    public static final int COLOR_BUTTON_WIDTH = 120;
    public static final int COLOR_BUTTON_HEIGHT = 14;

    private static final int FRAGMENT_SLOTS = 1;
    private static final int INVENTORY_START = FRAGMENT_SLOTS;
    private static final int INVENTORY_END = INVENTORY_START + 36;

    private final Inventory playerInventory;
    private final ItemStack vessel;
    private final FragmentContainer fragmentContainer;
    private boolean loading;

    public PastEchoMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, findVessel(inventory));
    }

    public PastEchoMenu(int containerId, Inventory inventory, ItemStack vessel) {
        super(EchoesShowThePast.PAST_ECHO_MENU.get(), containerId);
        this.playerInventory = inventory;
        this.vessel = vessel;
        this.fragmentContainer = new FragmentContainer(this);
        this.loading = true;
        PastEchoMemory.ensureMigrated(vessel);
        ItemStack fragment = PastEchoMemory.getFragment(vessel);
        if (!fragment.isEmpty()) {
            fragmentContainer.setItem(0, fragment.copy());
        }
        this.loading = false;

        addSlot(new FragmentSlot(fragmentContainer, 0, FRAGMENT_SLOT_X, FRAGMENT_SLOT_Y));
        addStandardInventorySlots(inventory, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);
    }

    public ItemStack vessel() {
        return vessel;
    }

    public ItemStack fragment() {
        return fragmentContainer.getItem(0);
    }

    public boolean canEditColor() {
        return PastEchoMemory.isPersonalColorEditable(fragment());
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        if (!loading && container == fragmentContainer && !playerInventory.player.level().isClientSide()) {
            saveFragment();
        }
    }

    private void saveFragment() {
        if (vessel.isEmpty() || !vessel.is(EchoesShowThePast.PAST_ECHO.get())) {
            return;
        }
        ItemStack fragment = fragmentContainer.getItem(0);
        if (fragment.isEmpty()) {
            PastEchoMemory.clearFragment(vessel);
        } else {
            PastEchoMemory.setFragment(vessel, fragment);
        }
        // Drop an active projection as soon as the vessel no longer holds that memory.
        if (playerInventory.player instanceof ServerPlayer serverPlayer) {
            EchoProjectionManager.stopIfSourceMissing(serverPlayer);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != CYCLE_COLOR_BUTTON || player.level().isClientSide()) {
            return false;
        }
        if (!canEditColor()) {
            return false;
        }
        ItemStack fragment = fragment().copy();
        if (fragment.isEmpty()) {
            return false;
        }
        PastEchoMemory.setFragment(vessel, fragment);
        PastEchoMemory.cyclePersonalColor(vessel);
        loading = true;
        fragmentContainer.setItem(0, PastEchoMemory.getFragment(vessel).copy());
        loading = false;
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (slotIndex < FRAGMENT_SLOTS) {
            if (!moveItemStackTo(source, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (source.is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get())) {
            if (!moveItemStackTo(source, 0, FRAGMENT_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return !vessel.isEmpty()
                && vessel.is(EchoesShowThePast.PAST_ECHO.get())
                && (player.getInventory().contains(vessel)
                        || player.getOffhandItem() == vessel
                        || player.getMainHandItem() == vessel);
    }

    private static ItemStack findVessel(Inventory inventory) {
        ItemStack selected = inventory.getSelectedItem();
        if (selected.is(EchoesShowThePast.PAST_ECHO.get())) {
            return selected;
        }
        ItemStack offhand = inventory.player.getOffhandItem();
        if (offhand.is(EchoesShowThePast.PAST_ECHO.get())) {
            return offhand;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(EchoesShowThePast.PAST_ECHO.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static final class FragmentContainer extends SimpleContainer {
        private final PastEchoMenu menu;

        private FragmentContainer(PastEchoMenu menu) {
            super(FRAGMENT_SLOTS);
            this.menu = menu;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            menu.slotsChanged(this);
        }
    }

    private static final class FragmentSlot extends Slot {
        private FragmentSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
