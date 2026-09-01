package dev.alvar.echoespast.client;

import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.network.UnknownCombatImpactPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Short additive camera recoil. It never changes input, FOV or player rotation. */
public final class ClientUnknownCombatImpact {
    private static double startGameTime = Double.NEGATIVE_INFINITY;
    private static int durationTicks;
    private static float strengthDegrees;
    private static float rollSign = 1.0F;

    private ClientUnknownCombatImpact() {
    }

    public static void receive(UnknownCombatImpactPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || EchoesConfig.BOSS_IMPACT_CAMERA_STRENGTH.get() <= 0.0D) {
            clear();
            return;
        }
        boolean finisher = payload.beat() == UnknownCombatImpactPayload.FINISHER;
        durationTicks = finisher ? 6 : 4;
        strengthDegrees = finisher ? 1.0F : 0.65F;
        if (payload.blocked()) {
            strengthDegrees *= 0.5F;
        }
        rollSign = finisher ? -1.0F : 1.0F;
        startGameTime = minecraft.level.getGameTime();
    }

    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || durationTicks <= 0 || ClientUnknownEnterCinematic.isControlling()) {
            return;
        }
        double age = minecraft.level.getGameTime()
                + event.getPartialTick()
                - startGameTime;
        if (age < 0.0D || age >= durationTicks) {
            clear();
            return;
        }
        float configured = EchoesConfig.BOSS_IMPACT_CAMERA_STRENGTH.get().floatValue();
        float progress = (float) (age / durationTicks);
        float envelope = (1.0F - progress) * (1.0F - progress);
        float recoil = Mth.sin(progress * Mth.PI * 1.35F) * strengthDegrees * configured * envelope;
        event.setPitch(event.getPitch() - recoil);
        event.setRoll(event.getRoll() + recoil * 0.42F * rollSign);
    }

    public static void clear() {
        startGameTime = Double.NEGATIVE_INFINITY;
        durationTicks = 0;
        strengthDegrees = 0.0F;
    }
}
