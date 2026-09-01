package dev.alvar.echoespast.visual;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class EchoFaceVisibility {
    /**
     * Geometry helper retained for tests and diagnostics. Past Echo rendering no
     * longer disables outward face culling when this is true; occupancy fade
     * clears the camera neighbourhood instead.
     */
    public static boolean cameraInsideBlock(Vec3 camera, BlockPos position) {
        return camera.x >= position.getX()
                && camera.x <= position.getX() + 1.0
                && camera.y >= position.getY()
                && camera.y <= position.getY() + 1.0
                && camera.z >= position.getZ()
                && camera.z <= position.getZ() + 1.0;
    }

    public static boolean facePointsTowardCamera(Vec3 camera, Vec3 faceCenter, Vec3 outwardNormal) {
        return camera.subtract(faceCenter).dot(outwardNormal) > 1.0E-5;
    }

    private EchoFaceVisibility() {
    }
}
