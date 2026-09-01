package dev.alvar.echoespast.server;

import dev.alvar.echoespast.EchoesShowThePast;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Awards Unknown-arc advancements. Criteria named {@code granted_by_code} /
 * {@code materialized_echo} use {@code minecraft:impossible} and are granted here.
 */
public final class UnknownAdvancements {
    public static final Identifier DEFEAT_UNKNOWN = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "unknown/defeat_unknown");
    public static final Identifier CLAIM_STONE = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "unknown/claim_philosophers_stone");

    private UnknownAdvancements() {
    }

    public static void awardDefeatAndStone(ServerPlayer player) {
        award(player, DEFEAT_UNKNOWN, "granted_by_code");
        award(player, CLAIM_STONE, "granted_by_code");
    }

    public static void awardRevisit(ServerPlayer player, Identifier siteId) {
        Identifier advancementId = restoreAdvancement(siteId);
        if (advancementId == null) {
            return;
        }
        award(player, advancementId, "materialized_echo");
    }

    private static Identifier restoreAdvancement(Identifier siteId) {
        if (siteId == null
                || !EchoesShowThePast.MOD_ID.equals(siteId.getNamespace())) {
            return null;
        }
        return switch (siteId.getPath()) {
            case "egyptian_temple",
                    "abandoned_mine",
                    "sanctuary_of_medusa",
                    "medieval_watchtower",
                    "coliseum",
                    "erechtheion" -> Identifier.fromNamespaceAndPath(
                            EchoesShowThePast.MOD_ID,
                            "restore/" + siteId.getPath());
            default -> null;
        };
    }

    private static void award(ServerPlayer player, Identifier advancementId, String criterion) {
        var server = player.level().getServer();
        if (server == null) {
            return;
        }
        AdvancementHolder holder = server.getAdvancements().get(advancementId);
        if (holder == null) {
            return;
        }
        player.getAdvancements().award(holder, criterion);
    }
}
