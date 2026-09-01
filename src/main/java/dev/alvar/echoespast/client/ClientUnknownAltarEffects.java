package dev.alvar.echoespast.client;

import dev.alvar.echoespast.block.BigEchoPedestalBlockEntity;
import dev.alvar.echoespast.network.UnknownAltarFragmentExplodePayload;
import dev.alvar.echoespast.resonance.ResonanceColor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.Level;

public final class ClientUnknownAltarEffects {
    private ClientUnknownAltarEffects() {
    }

    public static void receive(UnknownAltarFragmentExplodePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            return;
        }
        ResonanceColor color = BigEchoPedestalBlockEntity.colorForFightSlot(payload.slot());
        DustParticleOptions dust = new DustParticleOptions(color.rgb(), 1.35F);
        double x = payload.altarOrigin().getX() + 0.0D;
        double y = payload.altarOrigin().getY() + 1.95D;
        double z = payload.altarOrigin().getZ() + 1.0D;
        ClientAltarOfferingMotion.beginVanish(
                payload.altarOrigin(),
                payload.slot(),
                level.getGameTime()
                        + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        var random = level.getRandom();
        for (int i = 0; i < 36; i++) {
            double ox = (random.nextDouble() - 0.5D) * 1.4D;
            double oy = random.nextDouble() * 0.9D;
            double oz = (random.nextDouble() - 0.5D) * 1.4D;
            level.addParticle(dust, x + ox, y + oy, z + oz, ox * 0.12D, 0.08D + oy * 0.05D, oz * 0.12D);
        }
    }
}
