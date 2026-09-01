package dev.alvar.echoespast.server;

import dev.alvar.echoespast.snapshot.EchoSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class EchoProjectionAccess {
    public static Result validate(ResourceKey<Level> dimension, Vec3 playerPosition, EchoSnapshot snapshot, double range) {
        if (!dimension.equals(snapshot.dimension())) {
            return Result.WRONG_DIMENSION;
        }
        if (distanceToMemorySquared(
                        playerPosition,
                        snapshot)
                > range * range) {
            return Result.TOO_FAR;
        }
        return Result.ALLOWED;
    }

    private static double distanceToMemorySquared(
            Vec3 position,
            EchoSnapshot snapshot) {
        if (snapshot.boundsMin().isEmpty()
                || snapshot.boundsMax().isEmpty()) {
            return position.distanceToSqr(
                    snapshot.origin().getCenter());
        }
        BlockPos minimum = snapshot.origin().offset(
                snapshot.boundsMin().orElseThrow());
        BlockPos maximum = snapshot.origin().offset(
                snapshot.boundsMax().orElseThrow());
        double closestX = Math.clamp(
                position.x,
                minimum.getX(),
                maximum.getX() + 1.0);
        double closestY = Math.clamp(
                position.y,
                minimum.getY(),
                maximum.getY() + 1.0);
        double closestZ = Math.clamp(
                position.z,
                minimum.getZ(),
                maximum.getZ() + 1.0);
        return position.distanceToSqr(
                closestX,
                closestY,
                closestZ);
    }

    public enum Result {
        ALLOWED,
        WRONG_DIMENSION,
        TOO_FAR
    }

    private EchoProjectionAccess() {
    }
}
