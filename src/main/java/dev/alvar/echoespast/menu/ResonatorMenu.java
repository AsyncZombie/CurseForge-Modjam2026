package dev.alvar.echoespast.menu;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.resonance.EchoSiteType;
import dev.alvar.echoespast.resonance.ResonanceColor;
import dev.alvar.echoespast.resonance.ResonanceKnowledge;
import dev.alvar.echoespast.resonance.ResonatorLoadout;
import dev.alvar.echoespast.resonance.ResonatorModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Resonator console. The instrument stays in-hand; only modules move through
 * the three sockets.
 *
 * Layout (256×270, same family as Past Echo):
 * <pre>
 *   header     y 8–28
 *   chamber    y 34–88   · modules (84/120/156, 44)
 *   trough     y 70–84   · mode button when matrix installed
 *   well       y 94–160  · toolbar + scrollable signatures
 *   vault      y 166+    · inventory (47, 176)
 * </pre>
 */
public final class ResonatorMenu extends AbstractContainerMenu {
    public static final int TOGGLE_MODE_BUTTON = 0;
    public static final int MUTE_ALL_BUTTON = 1;
    public static final int COLOR_BUTTON_BASE = 100;
    public static final int IGNORE_BUTTON_BASE = 1_000;

    public static final int MODULE_SLOT_X = 84;
    public static final int MODULE_SLOT_Y = 44;
    public static final int MODULE_SLOT_SPACING = 36;
    public static final int PLAYER_INVENTORY_X = 47;
    public static final int PLAYER_INVENTORY_Y = 176;

    public static final int MODE_BUTTON_X = 68;
    public static final int MODE_BUTTON_Y = 70;
    public static final int MODE_BUTTON_WIDTH = 120;
    public static final int MODE_BUTTON_HEIGHT = 14;

    public static final int MUTE_ALL_BUTTON_X = 148;
    public static final int MUTE_ALL_BUTTON_Y = 98;
    public static final int MUTE_ALL_BUTTON_WIDTH = 72;
    public static final int MUTE_ALL_BUTTON_HEIGHT = 12;

    private static final int MODULE_SLOTS = ResonatorLoadout.SLOT_COUNT;
    private static final int INVENTORY_START = MODULE_SLOTS;
    private static final int INVENTORY_END = INVENTORY_START + 36;

    private final Inventory playerInventory;
    private final ItemStack resonator;
    private final ModuleContainer moduleContainer;
    private boolean loading;

    public ResonatorMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, findResonator(inventory));
    }

    public ResonatorMenu(int containerId, Inventory inventory, ItemStack resonator) {
        super(EchoesShowThePast.RESONATOR_MENU.get(), containerId);
        this.playerInventory = inventory;
        this.resonator = resonator;
        this.moduleContainer = new ModuleContainer(this);
        this.loading = true;
        ResonatorLoadout loadout = resonator.getOrDefault(
                EchoesShowThePast.RESONATOR_LOADOUT.get(),
                ResonatorLoadout.EMPTY);
        for (int slot = 0; slot < loadout.modules().size(); slot++) {
            moduleContainer.setItem(slot, loadout.modules().get(slot).createStack());
        }
        this.loading = false;

        for (int slot = 0; slot < MODULE_SLOTS; slot++) {
            addSlot(new ModuleSlot(
                    moduleContainer,
                    slot,
                    MODULE_SLOT_X + slot * MODULE_SLOT_SPACING,
                    MODULE_SLOT_Y,
                    this));
        }
        addStandardInventorySlots(inventory, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);
    }

    public ResonatorLoadout loadout() {
        List<ResonatorModule> modules = new ArrayList<>(MODULE_SLOTS);
        for (int slot = 0; slot < MODULE_SLOTS; slot++) {
            ResonatorModule module = ResonatorModule.fromStack(moduleContainer.getItem(slot));
            if (module != null) {
                modules.add(module);
            }
        }
        boolean directional = resonator
                .getOrDefault(EchoesShowThePast.RESONATOR_LOADOUT.get(), ResonatorLoadout.EMPTY)
                .directionalMode();
        return ResonatorLoadout.sanitized(modules, directional);
    }

    public List<Identifier> discoveredSites() {
        ResonanceKnowledge knowledge =
                playerInventory.player.getData(EchoesShowThePast.RESONANCE_KNOWLEDGE.get());
        return knowledge.discovered().stream()
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
    }

    public boolean canToggleMode() {
        return loadout().has(ResonatorModule.DIRECTIONAL_MATRIX);
    }

    public boolean canManageSignatures() {
        return loadout().has(ResonatorModule.HARMONIC_DECODER);
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        if (!loading && container == moduleContainer && !playerInventory.player.level().isClientSide()) {
            ResonatorLoadout previous = resonator.getOrDefault(
                    EchoesShowThePast.RESONATOR_LOADOUT.get(),
                    ResonatorLoadout.EMPTY);
            ResonatorLoadout next = loadout();
            if (next.has(ResonatorModule.DIRECTIONAL_MATRIX)
                    && !previous.has(ResonatorModule.DIRECTIONAL_MATRIX)) {
                next = next.withDirectionalMode(true);
            }
            saveLoadout(next);
        }
    }

    private void saveLoadout(ResonatorLoadout loadout) {
        if (!resonator.isEmpty() && resonator.is(EchoesShowThePast.LOW_FREQUENCY_RESONATOR.get())) {
            resonator.set(EchoesShowThePast.RESONATOR_LOADOUT.get(), loadout);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        ResonatorLoadout loadout = loadout();
        if (buttonId == TOGGLE_MODE_BUTTON) {
            if (!loadout.has(ResonatorModule.DIRECTIONAL_MATRIX)) {
                return false;
            }
            saveLoadout(loadout.withDirectionalMode(!loadout.directionalMode()));
            return true;
        }
        if (!loadout.has(ResonatorModule.HARMONIC_DECODER)) {
            return false;
        }

        List<Identifier> sites = discoveredSites();
        if (buttonId == MUTE_ALL_BUTTON) {
            if (sites.isEmpty()) {
                return false;
            }
            ResonanceKnowledge knowledge =
                    player.getData(EchoesShowThePast.RESONANCE_KNOWLEDGE.get());
            // If anything is still audible, mute everything; otherwise listen to all.
            boolean mute = knowledge.anyListening(sites);
            player.setData(
                    EchoesShowThePast.RESONANCE_KNOWLEDGE.get(),
                    knowledge.setIgnored(sites, mute));
            return true;
        }
        if (buttonId >= IGNORE_BUTTON_BASE) {
            int siteIndex = buttonId - IGNORE_BUTTON_BASE;
            if (siteIndex < 0 || siteIndex >= sites.size()) {
                return false;
            }
            ResonanceKnowledge knowledge =
                    player.getData(EchoesShowThePast.RESONANCE_KNOWLEDGE.get());
            player.setData(
                    EchoesShowThePast.RESONANCE_KNOWLEDGE.get(),
                    knowledge.toggleIgnored(sites.get(siteIndex)));
            return true;
        }
        if (buttonId >= COLOR_BUTTON_BASE) {
            int packed = buttonId - COLOR_BUTTON_BASE;
            int siteIndex = packed / ResonanceColor.PALETTE.size();
            int colorIndex = packed % ResonanceColor.PALETTE.size();
            if (siteIndex < 0 || siteIndex >= sites.size()) {
                return false;
            }
            EchoSiteType site = EchoSiteType.byId(sites.get(siteIndex));
            if (site == null) {
                return false;
            }
            ResonanceKnowledge knowledge =
                    player.getData(EchoesShowThePast.RESONANCE_KNOWLEDGE.get());
            player.setData(
                    EchoesShowThePast.RESONANCE_KNOWLEDGE.get(),
                    knowledge.setColor(site.id(), ResonanceColor.PALETTE.get(colorIndex)));
            return true;
        }
        return false;
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
        if (slotIndex < MODULE_SLOTS) {
            if (!moveItemStackTo(source, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (ResonatorModule.fromStack(source) != null) {
            if (!moveItemStackTo(source, 0, MODULE_SLOTS, false)) {
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
        return !resonator.isEmpty()
                && resonator.is(EchoesShowThePast.LOW_FREQUENCY_RESONATOR.get())
                && (player.getInventory().contains(resonator)
                        || player.getOffhandItem() == resonator
                        || player.getMainHandItem() == resonator);
    }

    private static ItemStack findResonator(Inventory inventory) {
        ItemStack selected = inventory.getSelectedItem();
        if (selected.is(EchoesShowThePast.LOW_FREQUENCY_RESONATOR.get())) {
            return selected;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(EchoesShowThePast.LOW_FREQUENCY_RESONATOR.get())) {
                return stack;
            }
        }
        ItemStack offhand = inventory.player.getOffhandItem();
        return offhand.is(EchoesShowThePast.LOW_FREQUENCY_RESONATOR.get())
                ? offhand
                : ItemStack.EMPTY;
    }

    private static final class ModuleContainer extends SimpleContainer {
        private final ResonatorMenu menu;

        private ModuleContainer(ResonatorMenu menu) {
            super(MODULE_SLOTS);
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

    private static final class ModuleSlot extends Slot {
        private final ResonatorMenu menu;

        private ModuleSlot(SimpleContainer container, int slot, int x, int y, ResonatorMenu menu) {
            super(container, slot, x, y);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            ResonatorModule proposed = ResonatorModule.fromStack(stack);
            if (proposed == null) {
                return false;
            }
            if (proposed.duplicatesAllowed()) {
                return true;
            }
            for (int slot = 0; slot < MODULE_SLOTS; slot++) {
                if (slot != getContainerSlot()
                        && ResonatorModule.fromStack(menu.moduleContainer.getItem(slot)) == proposed) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
