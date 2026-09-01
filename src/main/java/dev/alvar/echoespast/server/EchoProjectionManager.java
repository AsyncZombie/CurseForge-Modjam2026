package dev.alvar.echoespast.server;

import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.item.PastEchoMemory;
import dev.alvar.echoespast.network.EchoStatePayload;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import dev.alvar.echoespast.snapshot.EchoTemplateResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class EchoProjectionManager {
    private static final Map<UUID, EchoSnapshot> ACTIVE = new HashMap<>();
    private static final Map<UUID, EchoSnapshot> ACTIVE_SOURCE = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_MEMORY_SLOT = new HashMap<>();

    public static boolean toggle(ServerPlayer player, EchoSnapshot snapshot) {
        EchoSnapshot active = ACTIVE_SOURCE.get(player.getUUID());
        if (snapshot.equals(active)) {
            stop(player);
            return false;
        }
        EchoSnapshot resolved = EchoTemplateResolver.resolveForProjection(
                player.level(),
                snapshot,
                player.getEyePosition(),
                EchoesConfig.CAPTURE_RADIUS.getAsInt());
        if (resolved.isTemplateReference()) {
            return false;
        }
        ACTIVE_SOURCE.put(player.getUUID(), snapshot);
        ACTIVE.put(player.getUUID(), resolved);
        findMemorySlot(player, snapshot, -1)
                .ifPresent(slot -> ACTIVE_MEMORY_SLOT.put(player.getUUID(), slot));
        sendState(player, EchoStatePayload.on(
                clientSnapshot(snapshot, resolved)));
        return true;
    }

    public static void stop(ServerPlayer player) {
        if (ACTIVE.remove(player.getUUID()) != null) {
            ACTIVE_SOURCE.remove(player.getUUID());
            ACTIVE_MEMORY_SLOT.remove(player.getUUID());
            sendState(player, EchoStatePayload.off());
        }
    }

    /** Fades the echo if its Past Echo vessel no longer carries the active memory. */
    public static void stopIfSourceMissing(ServerPlayer player) {
        EchoSnapshot snapshot = ACTIVE_SOURCE.get(player.getUUID());
        if (snapshot == null) {
            return;
        }
        int preferred = ACTIVE_MEMORY_SLOT.getOrDefault(player.getUUID(), -1);
        if (findMemorySlot(player, snapshot, preferred).isEmpty()) {
            stop(player);
        }
    }

    public static Optional<EchoSnapshot> activeSnapshot(ServerPlayer player) {
        return Optional.ofNullable(ACTIVE.get(player.getUUID()));
    }

    public static Optional<EchoSnapshot> activeSourceSnapshot(ServerPlayer player) {
        return Optional.ofNullable(ACTIVE_SOURCE.get(player.getUUID()));
    }

    /**
     * Chooses the representation sent to the client. Authored sites always keep
     * their template reference so the client can load the intact NBT and the
     * companion additions set locally. Without that set, every naturally
     * generated block inside the memory bounds is read as remembered air.
     * The server still keeps the bounded {@code active} window for gameplay.
     */
    public static EchoSnapshot clientSnapshot(
            EchoSnapshot source,
            EchoSnapshot active) {
        if (source.isTemplateReference()) {
            return source;
        }
        return active;
    }

    /**
     * Persists a divergent historical branch on both the active projection
     * and its source item. Synchronization is delayed until the restoration
     * crest has finished so this revision never replays the scanner.
     */
    public static void reviseMemory(
            ServerPlayer player,
            EchoSnapshot expectedSource,
            EchoSnapshot revised,
            boolean synchronizeClient) {
        UUID owner = player.getUUID();
        EchoSnapshot activeSource = ACTIVE_SOURCE.get(owner);
        EchoSnapshot itemSource = activeSource == null ? expectedSource : activeSource;
        int preferred = ACTIVE_MEMORY_SLOT.getOrDefault(owner, -1);
        Optional<Integer> slot = findMemorySlot(player, itemSource, preferred);
        if (slot.isEmpty() && !itemSource.equals(expectedSource)) {
            slot = findMemorySlot(player, expectedSource, preferred);
        }
        slot.ifPresent(index -> {
            ItemStack stack = player.getInventory().getItem(index);
            PastEchoMemory.setSnapshot(stack, revised);
            player.getInventory().setChanged();
            ACTIVE_MEMORY_SLOT.put(owner, index);
        });

        if (ACTIVE.containsKey(owner)) {
            ACTIVE.put(owner, revised);
            ACTIVE_SOURCE.put(owner, revised);
            if (synchronizeClient) {
                sendState(player, EchoStatePayload.revision(revised));
            }
        }
    }

    public static void synchronizeRevision(ServerPlayer player, EchoSnapshot revised) {
        if (revised.equals(ACTIVE.get(player.getUUID()))) {
            sendState(player, EchoStatePayload.revision(revised));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EchoSnapshot snapshot = ACTIVE_SOURCE.get(player.getUUID());
        if (snapshot == null) {
            return;
        }
        if (findMemorySlot(
                        player,
                        snapshot,
                        ACTIVE_MEMORY_SLOT.getOrDefault(player.getUUID(), -1))
                .isEmpty()) {
            stop(player);
            return;
        }
        double maxDistance = EchoesConfig.PROJECTION_RANGE.getAsInt();
        if (EchoProjectionAccess.validate(
                        player.level().dimension(), player.position(), snapshot, maxDistance)
                != EchoProjectionAccess.Result.ALLOWED) {
            stop(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
        ACTIVE_SOURCE.remove(event.getEntity().getUUID());
        ACTIVE_MEMORY_SLOT.remove(event.getEntity().getUUID());
    }

    private static Optional<Integer> findMemorySlot(
            ServerPlayer player, EchoSnapshot expected, int preferred) {
        if (preferred >= 0 && preferred < player.getInventory().getContainerSize()) {
            EchoSnapshot snapshot =
                    PastEchoMemory.getSnapshot(player.getInventory().getItem(preferred));
            if (expected.equals(snapshot)) {
                return Optional.of(preferred);
            }
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            EchoSnapshot snapshot =
                    PastEchoMemory.getSnapshot(player.getInventory().getItem(slot));
            if (expected.equals(snapshot)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    private static void sendState(ServerPlayer player, EchoStatePayload payload) {
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private EchoProjectionManager() {}
}
