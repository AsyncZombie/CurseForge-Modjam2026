package dev.alvar.echoespast.item;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.resonance.EchoSiteType;
import dev.alvar.echoespast.resonance.ResonanceColor;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Past Echo is a vessel; Past Fragment is the memory medium.
 * All gameplay reads and writes go through this helper so legacy
 * {@code echo_snapshot} components on the vessel migrate transparently.
 */
public final class PastEchoMemory {
    public static final ResonanceColor DEFAULT_PERSONAL_COLOR = ResonanceColor.ICE;

    private PastEchoMemory() {
    }

    public static ItemStack ensureMigrated(ItemStack vessel) {
        if (!vessel.is(EchoesShowThePast.PAST_ECHO.get())) {
            return vessel;
        }
        EchoSnapshot legacy = vessel.get(EchoesShowThePast.ECHO_SNAPSHOT.get());
        if (legacy == null) {
            return vessel;
        }
        ItemContainerContents existing = vessel.get(EchoesShowThePast.PAST_FRAGMENT.get());
        boolean alreadySocketed = existing != null && !existing.copyOne().isEmpty();
        if (!alreadySocketed) {
            ItemStack fragment = createFragment(legacy, Optional.empty());
            vessel.set(
                    EchoesShowThePast.PAST_FRAGMENT.get(),
                    ItemContainerContents.fromItems(List.of(fragment.copyWithCount(1))));
        }
        vessel.remove(EchoesShowThePast.ECHO_SNAPSHOT.get());
        return vessel;
    }

    public static boolean hasFragment(ItemStack vessel) {
        ensureMigrated(vessel);
        ItemContainerContents contents = vessel.get(EchoesShowThePast.PAST_FRAGMENT.get());
        return contents != null && !contents.copyOne().isEmpty();
    }

    public static ItemStack getFragment(ItemStack vessel) {
        ensureMigrated(vessel);
        ItemContainerContents contents = vessel.get(EchoesShowThePast.PAST_FRAGMENT.get());
        return contents == null ? ItemStack.EMPTY : contents.copyOne();
    }

    public static void setFragment(ItemStack vessel, ItemStack fragment) {
        ensureMigrated(vessel);
        if (fragment == null || fragment.isEmpty()) {
            vessel.remove(EchoesShowThePast.PAST_FRAGMENT.get());
            return;
        }
        ItemStack stored = fragment.copyWithCount(1);
        if (!stored.is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get())) {
            throw new IllegalArgumentException("Past Echo socket only accepts Past Fragments");
        }
        vessel.set(
                EchoesShowThePast.PAST_FRAGMENT.get(),
                ItemContainerContents.fromItems(List.of(stored)));
        vessel.remove(EchoesShowThePast.ECHO_SNAPSHOT.get());
    }

    public static void clearFragment(ItemStack vessel) {
        ensureMigrated(vessel);
        vessel.remove(EchoesShowThePast.PAST_FRAGMENT.get());
        vessel.remove(EchoesShowThePast.ECHO_SNAPSHOT.get());
    }

    public static EchoSnapshot getSnapshot(ItemStack vessel) {
        ensureMigrated(vessel);
        ItemStack fragment = getFragment(vessel);
        if (fragment.isEmpty()) {
            return null;
        }
        return fragment.get(EchoesShowThePast.ECHO_SNAPSHOT.get());
    }

    public static void setSnapshot(ItemStack vessel, EchoSnapshot snapshot) {
        ensureMigrated(vessel);
        ItemStack fragment = getFragment(vessel);
        if (fragment.isEmpty()) {
            fragment = createFragment(snapshot, Optional.of(DEFAULT_PERSONAL_COLOR));
        } else {
            fragment = fragment.copy();
            fragment.set(EchoesShowThePast.ECHO_SNAPSHOT.get(), snapshot);
            fragment.set(DataComponents.MAX_STACK_SIZE, 1);
            if (snapshot.site().isPresent()) {
                fragment.remove(EchoesShowThePast.RESONANCE_COLOR.get());
            } else if (!fragment.has(EchoesShowThePast.RESONANCE_COLOR.get())) {
                fragment.set(EchoesShowThePast.RESONANCE_COLOR.get(), DEFAULT_PERSONAL_COLOR);
            }
        }
        setFragment(vessel, fragment);
    }

    public static void clearSnapshot(ItemStack vessel) {
        ensureMigrated(vessel);
        ItemStack fragment = getFragment(vessel);
        if (fragment.isEmpty()) {
            vessel.remove(EchoesShowThePast.ECHO_SNAPSHOT.get());
            return;
        }
        fragment = fragment.copy();
        wipeMemory(fragment);
        setFragment(vessel, fragment);
    }

    public static ItemStack createFragment(EchoSnapshot snapshot, Optional<ResonanceColor> personalColor) {
        ItemStack fragment = new ItemStack(EchoesShowThePast.PAST_FRAGMENT_ITEM.get());
        if (snapshot != null) {
            fragment.set(EchoesShowThePast.ECHO_SNAPSHOT.get(), snapshot);
            fragment.set(DataComponents.MAX_STACK_SIZE, 1);
            if (snapshot.site().isEmpty()) {
                fragment.set(
                        EchoesShowThePast.RESONANCE_COLOR.get(),
                        personalColor.orElse(DEFAULT_PERSONAL_COLOR));
            }
        } else {
            personalColor.ifPresent(color ->
                    fragment.set(EchoesShowThePast.RESONANCE_COLOR.get(), color));
        }
        return fragment;
    }

    public static ItemStack createEmptyFragment() {
        // No color component: blank shells stack together.
        return createFragment(null, Optional.empty());
    }

    public static ItemStack createSealedVessel(EchoSnapshot snapshot) {
        ItemStack vessel = new ItemStack(EchoesShowThePast.PAST_ECHO.get());
        setFragment(vessel, createFragment(snapshot, Optional.empty()));
        return vessel;
    }

    public static ResonanceColor resolveColor(ItemStack fragmentOrVessel) {
        ItemStack fragment = fragmentOrVessel.is(EchoesShowThePast.PAST_ECHO.get())
                ? getFragment(fragmentOrVessel)
                : fragmentOrVessel;
        if (fragment.isEmpty()) {
            return DEFAULT_PERSONAL_COLOR;
        }
        EchoSnapshot snapshot = fragment.get(EchoesShowThePast.ECHO_SNAPSHOT.get());
        if (snapshot != null && snapshot.site().isPresent()) {
            EchoSiteType site = EchoSiteType.byId(snapshot.site().orElseThrow());
            if (site != null) {
                return site.defaultColor();
            }
        }
        return fragment.getOrDefault(
                EchoesShowThePast.RESONANCE_COLOR.get(),
                DEFAULT_PERSONAL_COLOR);
    }

    public static boolean isPersonalColorEditable(ItemStack fragment) {
        if (fragment.isEmpty() || !fragment.is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get())) {
            return false;
        }
        EchoSnapshot snapshot = fragment.get(EchoesShowThePast.ECHO_SNAPSHOT.get());
        if (snapshot == null) {
            return true;
        }
        return snapshot.site().isEmpty() && snapshot.canErase();
    }

    public static void cyclePersonalColor(ItemStack vessel) {
        ensureMigrated(vessel);
        ItemStack fragment = getFragment(vessel);
        if (!isPersonalColorEditable(fragment)) {
            return;
        }
        ResonanceColor current = resolveColor(fragment);
        int index = ResonanceColor.PALETTE.indexOf(current);
        ResonanceColor next = ResonanceColor.PALETTE.get(
                (index + 1) % ResonanceColor.PALETTE.size());
        fragment = fragment.copy();
        fragment.set(EchoesShowThePast.RESONANCE_COLOR.get(), next);
        setFragment(vessel, fragment);
    }

    public static void purgeFragmentMemory(ItemStack fragment) {
        if (!fragment.is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get())) {
            return;
        }
        EchoSnapshot snapshot = fragment.get(EchoesShowThePast.ECHO_SNAPSHOT.get());
        if (snapshot == null || !snapshot.canErase()) {
            return;
        }
        wipeMemory(fragment);
    }

    /** Empty shells carry neither memory nor hue, so they can stack. */
    private static void wipeMemory(ItemStack fragment) {
        fragment.remove(EchoesShowThePast.ECHO_SNAPSHOT.get());
        fragment.remove(EchoesShowThePast.RESONANCE_COLOR.get());
        // Removing MAX_STACK_SIZE punches an empty override; the component
        // default is 1, not the item's stacksTo(64) prototype.
        fragment.set(
                DataComponents.MAX_STACK_SIZE,
                EchoesShowThePast.PAST_FRAGMENT_ITEM.get().getDefaultMaxStackSize());
    }
}
