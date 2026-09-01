package dev.alvar.echoespast.visual;

import dev.alvar.echoespast.snapshot.EchoSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Acoustic domain for one Past Echo. The ambient pulse remains a sphere around
 * the listener, while an authored memory contributes only its exact aligned
 * bounds. The path solver, rather than the bounds, decides whether pressure
 * can actually enter a room.
 */
public final class EchoWaveVolume {
    private static final double CREST_MARGIN = 0.90;
    private static final int AUTHORED_AIR_MARGIN = 1;

    private final Vec3 center;
    private final double radius;
    private final double ambientRadius;
    private final BlockPos minimum;
    private final BlockPos maximum;
    private final boolean hasAuthoredBounds;
    private final BlockPos authoredMinimum;
    private final BlockPos authoredMaximum;

    private EchoWaveVolume(
            Vec3 center,
            double radius,
            double ambientRadius,
            BlockPos minimum,
            BlockPos maximum,
            boolean hasAuthoredBounds,
            BlockPos authoredMinimum,
            BlockPos authoredMaximum) {
        this.center = center;
        this.radius = Math.max(1.0, radius);
        this.ambientRadius = Math.max(
                1.0,
                ambientRadius);
        this.minimum = minimum.immutable();
        this.maximum = maximum.immutable();
        this.hasAuthoredBounds = hasAuthoredBounds;
        this.authoredMinimum =
                authoredMinimum.immutable();
        this.authoredMaximum =
                authoredMaximum.immutable();
    }

    public static EchoWaveVolume aroundPlayer(
            EchoSnapshot snapshot,
            Vec3 playerOrigin) {
        return aroundPlayer(
                snapshot,
                playerOrigin,
                snapshot.radius(),
                true);
    }

    /**
     * A large authored template can be rendered section-by-section, but its
     * acoustic solver must remain a small current-space window. Otherwise a
     * 120-block island becomes an invalid 3D pathfinding grid on activation.
     */
    public static EchoWaveVolume aroundPlayer(
            EchoSnapshot snapshot,
            Vec3 playerOrigin,
            int configuredAmbientRadius,
            boolean includeAuthoredBounds) {
        double ambient = Math.max(1, configuredAmbientRadius)
                + CREST_MARGIN;
        BlockPos ambientMinimum = BlockPos.containing(
                playerOrigin.x - ambient - 1.0,
                playerOrigin.y - ambient - 1.0,
                playerOrigin.z - ambient - 1.0);
        BlockPos ambientMaximum = BlockPos.containing(
                playerOrigin.x + ambient + 1.0,
                playerOrigin.y + ambient + 1.0,
                playerOrigin.z + ambient + 1.0);

        if (!includeAuthoredBounds
                || snapshot.boundsMin().isEmpty()
                || snapshot.boundsMax().isEmpty()) {
            return new EchoWaveVolume(
                    playerOrigin,
                    ambient,
                    ambient,
                    ambientMinimum,
                    ambientMaximum,
                    false,
                    BlockPos.ZERO,
                    BlockPos.ZERO);
        }

        BlockPos authoredMinimum = snapshot.origin()
                .offset(snapshot.boundsMin().orElseThrow())
                .offset(
                        -AUTHORED_AIR_MARGIN,
                        -AUTHORED_AIR_MARGIN,
                        -AUTHORED_AIR_MARGIN);
        BlockPos authoredMaximum = snapshot.origin()
                .offset(snapshot.boundsMax().orElseThrow())
                .offset(
                        AUTHORED_AIR_MARGIN,
                        AUTHORED_AIR_MARGIN,
                        AUTHORED_AIR_MARGIN);
        BlockPos domainMinimum = new BlockPos(
                Math.min(
                        ambientMinimum.getX(),
                        authoredMinimum.getX()),
                Math.min(
                        ambientMinimum.getY(),
                        authoredMinimum.getY()),
                Math.min(
                        ambientMinimum.getZ(),
                        authoredMinimum.getZ()));
        BlockPos domainMaximum = new BlockPos(
                Math.max(
                        ambientMaximum.getX(),
                        authoredMaximum.getX()),
                Math.max(
                        ambientMaximum.getY(),
                        authoredMaximum.getY()),
                Math.max(
                        ambientMaximum.getZ(),
                        authoredMaximum.getZ()));
        double maximumTravel = ambient;
        for (int x : new int[] {
                authoredMinimum.getX(),
                authoredMaximum.getX() + 1
        }) {
            for (int y : new int[] {
                    authoredMinimum.getY(),
                    authoredMaximum.getY() + 1
            }) {
                for (int z : new int[] {
                        authoredMinimum.getZ(),
                        authoredMaximum.getZ() + 1
                }) {
                    maximumTravel = Math.max(
                            maximumTravel,
                            new Vec3(x, y, z)
                                    .distanceTo(
                                            playerOrigin));
                }
            }
        }
        return new EchoWaveVolume(
                playerOrigin,
                maximumTravel + CREST_MARGIN,
                ambient,
                domainMinimum,
                domainMaximum,
                true,
                authoredMinimum,
                authoredMaximum);
    }

    public Vec3 center() {
        return center;
    }

    public double radius() {
        return radius;
    }

    public double ambientRadius() {
        return ambientRadius;
    }

    public boolean contains(Vec3 position) {
        if (position.distanceToSqr(center)
                <= ambientRadius * ambientRadius) {
            return true;
        }
        return hasAuthoredBounds
                && position.x >= authoredMinimum.getX()
                && position.y >= authoredMinimum.getY()
                && position.z >= authoredMinimum.getZ()
                && position.x < authoredMaximum.getX() + 1.0
                && position.y < authoredMaximum.getY() + 1.0
                && position.z < authoredMaximum.getZ() + 1.0;
    }

    public BlockPos minBlock() {
        return minimum;
    }

    public BlockPos maxBlock() {
        return maximum;
    }

    public long boundingCellCount() {
        long sizeX = maximum.getX()
                - minimum.getX()
                + 1L;
        long sizeY = maximum.getY()
                - minimum.getY()
                + 1L;
        long sizeZ = maximum.getZ()
                - minimum.getZ()
                + 1L;
        return sizeX * sizeY * sizeZ;
    }
}
