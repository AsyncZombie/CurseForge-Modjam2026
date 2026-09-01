package dev.alvar.echoespast.client;

import dev.alvar.echoespast.visual.EchoBlockChange;
import dev.alvar.echoespast.visual.EchoProjectionStyle;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * First-class look-at dump for Past Echo present-fade
 * ({@code /echoes debug fade}).
 */
final class ClientEchoFadeDebug {
    static void dumpLookTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        HitResult hit = minecraft.hitResult;
        if (!(hit instanceof BlockHitResult blockHit)
                || blockHit.getType() != HitResult.Type.BLOCK) {
            chat(player, "[echo fade] mira un bloque sólido");
            return;
        }
        dump(player, blockHit.getBlockPos());
    }

    private static void dump(LocalPlayer player, BlockPos position) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        long now = System.nanoTime();
        BlockState present = minecraft.level.getBlockState(position);
        ClientEchoState.FadeProbe probe = ClientEchoState.probeFade(position, now);

        chat(player, "======== echo fade ========");
        chat(player, String.format(
                Locale.ROOT,
                "pos %s  section %s  site=%s",
                position.toShortString(),
                SectionPos.of(position),
                probe.siteId() == null ? "n/a" : probe.siteId()));
        chat(player, "present=" + describeState(present));
        chat(player, "remembered=" + describeState(probe.remembered()));

        if (probe.snapshotActive()) {
            chat(player, String.format(
                    Locale.ROOT,
                    "echo  t=%.2fs  R=%.1f  end=%.2fs  seed=%s  rev=%s",
                    probe.elapsedSeconds(),
                    probe.scanRadius(),
                    probe.effectEndSeconds(),
                    probe.fadeSeedSize() < 0
                            ? "n/a"
                            : Integer.toString(probe.fadeSeedSize()),
                    probe.occluderRevision()));
        } else {
            chat(player, "echo inactive — activa un Past Echo y repite");
        }

        chat(player, String.format(
                Locale.ROOT,
                "kind=%s  authored=%s  inSeed=%s  canFade=%s",
                probe.kind(),
                probe.authoredBySite(),
                probe.inFadeSeed(),
                probe.kind().canFadePresentBlock()));
        chat(player, String.format(
                Locale.ROOT,
                "hideLive=%s  presentGhost=%s  rememberedGhost=%s  opacity=%.2f",
                probe.shouldHidePresentBlock(),
                probe.presentGhostBaked(),
                probe.rememberedGhostBaked(),
                EchoProjectionStyle.presentTargetOpacity(probe.kind(), 1.0F)));
        chat(player, String.format(
                Locale.ROOT,
                "dist  local=%s  seed=%s  best=%s  reveal=%.3f",
                formatDistance(probe.localTimingDistance()),
                formatDistance(probe.seededDistance()),
                formatDistance(probe.bestDistance()),
                probe.reveal()));
        chat(player, "veredicto: " + verdict(present, probe));
    }

    private static void chat(LocalPlayer player, String line) {
        player.sendSystemMessage(Component.literal(line));
    }

    private static String verdict(BlockState present, ClientEchoState.FadeProbe probe) {
        if (!probe.snapshotActive()) {
            return "sin eco activo";
        }
        if (present.isAir()) {
            return "celda vacía en el presente";
        }
        if (!probe.kind().canFadePresentBlock()) {
            return "UNCHANGED/MISSING — no debe fundirse";
        }
        if (!probe.shouldHidePresentBlock()) {
            if (!Double.isFinite(probe.bestDistance())) {
                return "FALLA: elegible pero sin distancia (seed/scan)";
            }
            if (probe.reveal() <= 0.005F) {
                return "aún no alcanza el frente de retorno — espera";
            }
            return "FALLA: debería ocultar el mesh vivo";
        }
        if (probe.kind() == EchoBlockChange.Kind.ADDED && !probe.presentGhostBaked()) {
            return "hide OK; falta ghost ADDED (~0.26) — sección aún no horneada";
        }
        if (probe.kind() == EchoBlockChange.Kind.REPLACED && !probe.rememberedGhostBaked()) {
            return "hide OK; REPLACED sin ghost recordado (enterrado) — translúcido/hueco, no opaco";
        }
        if (probe.kind() == EchoBlockChange.Kind.ADDED) {
            return "OK ADDED — mesh oculto + ghost ~0.26";
        }
        return "OK REPLACED — mesh oculto; domina el bloque recordado";
    }

    private static String describeState(@Nullable BlockState state) {
        if (state == null) {
            return "null (fuera de índice / no authored)";
        }
        if (state.isAir()) {
            return "air";
        }
        return state.toString();
    }

    private static String formatDistance(double distance) {
        if (!Double.isFinite(distance)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.2f", distance);
    }

    private ClientEchoFadeDebug() {
    }
}
