package dev.alvar.echoespast.server;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.resonance.EchoSiteType;
import dev.alvar.echoespast.resonance.ResonanceKnowledge;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class ResonanceDiscoveryManager {
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % 20 != 0) {
            return;
        }
        ResonanceKnowledge knowledge =
                player.getData(EchoesShowThePast.RESONANCE_KNOWLEDGE.get());
        var registry = player.level().registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (EchoSiteType site : EchoSiteType.generatedSites()) {
            if (knowledge.discovered().contains(site.id())) {
                continue;
            }
            Structure structure = registry.getValue(site.structure());
            if (structure == null
                    || !player.level()
                            .structureManager()
                            .getStructureWithPieceAt(player.blockPosition(), structure)
                            .isValid()) {
                continue;
            }
            knowledge = knowledge.discover(site);
            player.setData(EchoesShowThePast.RESONANCE_KNOWLEDGE.get(), knowledge);
            player.sendSystemMessage(Component.translatable(
                    "message.echoes_show_the_past.signature_learned",
                    Component.translatable(site.translationKey())));
        }
    }

    private ResonanceDiscoveryManager() {
    }
}
