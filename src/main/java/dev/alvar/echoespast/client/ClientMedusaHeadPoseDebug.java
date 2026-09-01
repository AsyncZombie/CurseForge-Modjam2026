package dev.alvar.echoespast.client;

import dev.alvar.echoespast.network.MedusaHeadPoseDebugPayload;
import dev.alvar.echoespast.relic.MedusaHeadAimMath;

/**
 * Client copy of the live Head of Medusa pose overrides. Empty until a
 * gamemaster command enables authoring.
 */
public final class ClientMedusaHeadPoseDebug {
    private static boolean enabled;
    private static MedusaHeadAimMath.PoseEuler rest = MedusaHeadAimMath.REST;
    private static MedusaHeadAimMath.PoseEuler active = MedusaHeadAimMath.ACTIVE;

    public static void receive(MedusaHeadPoseDebugPayload payload) {
        enabled = payload.enabled();
        rest = new MedusaHeadAimMath.PoseEuler(
                payload.restX(),
                payload.restY(),
                payload.restZ());
        active = new MedusaHeadAimMath.PoseEuler(
                payload.activeX(),
                payload.activeY(),
                payload.activeZ());
    }

    public static MedusaHeadAimMath.PoseEuler rest() {
        return enabled ? rest : MedusaHeadAimMath.REST;
    }

    public static MedusaHeadAimMath.PoseEuler active() {
        return enabled ? active : MedusaHeadAimMath.ACTIVE;
    }

    public static MedusaHeadAimMath.PoseEuler rotation(float poseBlend) {
        return rest().lerp(active(), poseBlend);
    }

    private ClientMedusaHeadPoseDebug() {
    }
}
