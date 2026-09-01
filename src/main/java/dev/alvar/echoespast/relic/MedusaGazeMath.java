package dev.alvar.echoespast.relic;

import net.minecraft.world.phys.Vec3;

public final class MedusaGazeMath {
    public static boolean contains(
            Vec3 origin,
            Vec3 look,
            Vec3 target,
            double range,
            double minimumDot) {
        Vec3 offset = target.subtract(origin);
        double distanceSqr = offset.lengthSqr();
        if (distanceSqr <= 1.0E-8 || distanceSqr > range * range) {
            return false;
        }
        return look.normalize().dot(offset.normalize()) >= minimumDot;
    }

    private MedusaGazeMath() {
    }
}
