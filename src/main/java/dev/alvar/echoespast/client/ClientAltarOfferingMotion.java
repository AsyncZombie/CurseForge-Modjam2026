package dev.alvar.echoespast.client;

import dev.alvar.echoespast.block.BigEchoPedestalBlockEntity;
import dev.alvar.echoespast.visual.AltarOfferingMotion;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.core.BlockPos;

/**
 * Per-altar occupancy tracker so offerings ease in when they seat and ease
 * out when a fight fragment detonates, without replaying on template restore.
 */
public final class ClientAltarOfferingMotion {
    public static final int STONE_SLOT = BigEchoPedestalBlockEntity.FRAGMENT_SLOTS;
    private static final int SLOTS = STONE_SLOT + 1;
    private static final double RESTORE_GRACE_SECONDS = 0.18D;

    private static final Map<BlockPos, Slot[]> ALTARS = new HashMap<>();

    private ClientAltarOfferingMotion() {
    }

    public static void beginVanish(BlockPos origin, int slot, double gameTime) {
        Slot tracked = slot(origin, slot);
        tracked.seen = true;
        tracked.occupied = false;
        tracked.vanishGameTime = gameTime;
    }

    public static AltarOfferingMotion.Pose extract(
            BlockPos origin,
            int slot,
            boolean occupied,
            ItemClusterRenderState item,
            double gameTime) {
        Slot tracked = slot(origin, slot);
        if (!tracked.seen) {
            tracked.seen = true;
            tracked.occupied = occupied;
            tracked.ghost = occupied ? item : null;
            tracked.appearGameTime = occupied
                    ? gameTime - AltarOfferingMotion.INTRO_SECONDS
                    : Double.NaN;
            return occupied ? AltarOfferingMotion.settled() : AltarOfferingMotion.hidden();
        }
        if (occupied) {
            if (!tracked.occupied) {
                boolean restored = !Double.isNaN(tracked.vanishGameTime)
                        && (gameTime - tracked.vanishGameTime) < RESTORE_GRACE_SECONDS;
                tracked.appearGameTime = restored
                        ? gameTime - AltarOfferingMotion.INTRO_SECONDS
                        : gameTime;
                tracked.vanishGameTime = Double.NaN;
            }
            tracked.occupied = true;
            tracked.ghost = item;
            return AltarOfferingMotion.intro(gameTime - tracked.appearGameTime);
        }
        if (tracked.occupied && Double.isNaN(tracked.vanishGameTime)) {
            tracked.vanishGameTime = gameTime;
        }
        tracked.occupied = false;
        if (Double.isNaN(tracked.vanishGameTime)) {
            tracked.ghost = null;
            return AltarOfferingMotion.hidden();
        }
        AltarOfferingMotion.Pose pose =
                AltarOfferingMotion.outro(gameTime - tracked.vanishGameTime);
        if (!pose.visible()) {
            tracked.vanishGameTime = Double.NaN;
            tracked.ghost = null;
        }
        return pose;
    }

    public static ItemClusterRenderState ghost(BlockPos origin, int slot) {
        Slot[] slots = ALTARS.get(origin);
        if (slots == null) {
            return null;
        }
        int safe = Math.clamp(slot, 0, SLOTS - 1);
        return slots[safe].ghost;
    }

    private static Slot slot(BlockPos origin, int index) {
        Slot[] slots = ALTARS.computeIfAbsent(origin.immutable(), key -> {
            Slot[] created = new Slot[SLOTS];
            for (int i = 0; i < created.length; i++) {
                created[i] = new Slot();
            }
            return created;
        });
        return slots[Math.clamp(index, 0, SLOTS - 1)];
    }

    private static final class Slot {
        boolean seen;
        boolean occupied;
        double appearGameTime = Double.NaN;
        double vanishGameTime = Double.NaN;
        ItemClusterRenderState ghost;
    }
}
