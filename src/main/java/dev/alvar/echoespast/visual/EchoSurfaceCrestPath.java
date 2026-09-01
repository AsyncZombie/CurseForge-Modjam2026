package dev.alvar.echoespast.visual;

import net.minecraft.world.phys.Vec3;

/**
 * Keeps the old sub-block radial crest while shifting each surface onto the
 * distance reached through traversable space.
 */
public final class EchoSurfaceCrestPath {
    /**
     * Bilinear routed distances at the four vertices of one rendered quad.
     * Neighboring faces reuse the same world-space vertex samples, which
     * makes the crest meet exactly at block boundaries.
     */
    public record FaceDistances(double a, double b, double c, double d) {
        public double at(double u, double v) {
            double near = a + (b - a) * u;
            double far = d + (c - d) * u;
            return near + (far - near) * v;
        }

        public double minimum() {
            return Math.min(Math.min(a, b), Math.min(c, d));
        }

        public double maximum() {
            return Math.max(Math.max(a, b), Math.max(c, d));
        }

        public boolean finite() {
            return Double.isFinite(a)
                    && Double.isFinite(b)
                    && Double.isFinite(c)
                    && Double.isFinite(d);
        }
    }

    public static double distanceAtPoint(
            double routedFaceDistance,
            double faceCenterDistance,
            double pointDistance) {
        if (!Double.isFinite(routedFaceDistance)
                || !Double.isFinite(faceCenterDistance)
                || !Double.isFinite(pointDistance)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(
                0.0,
                routedFaceDistance
                        + pointDistance
                        - faceCenterDistance);
    }

    public static double distanceAtPoint(
            double routedFaceDistance,
            Vec3 faceCenter,
            Vec3 point,
            Vec3 travelGradient) {
        if (!Double.isFinite(routedFaceDistance)
                || !isFinite(faceCenter)
                || !isFinite(point)
                || !isFinite(travelGradient)) {
            return Double.POSITIVE_INFINITY;
        }
        double offsetX =
                point.x - faceCenter.x;
        double offsetY =
                point.y - faceCenter.y;
        double offsetZ =
                point.z - faceCenter.z;
        return Math.max(
                0.0,
                routedFaceDistance
                        + offsetX
                                * travelGradient.x
                        + offsetY
                                * travelGradient.y
                        + offsetZ
                                * travelGradient.z);
    }

    /**
     * Uses the routed pressure direction while retaining the subtle curvature
     * of the original 0.1 spherical crest inside each tessellated face.
     */
    public static double distanceAtPoint(
            double routedFaceDistance,
            Vec3 faceCenter,
            Vec3 point,
            Vec3 travelGradient,
            Vec3 origin) {
        double linearDistance = distanceAtPoint(
                routedFaceDistance,
                faceCenter,
                point,
                travelGradient);
        if (!Double.isFinite(linearDistance)
                || !isFinite(origin)) {
            return Double.POSITIVE_INFINITY;
        }
        double offsetX =
                point.x - faceCenter.x;
        double offsetY =
                point.y - faceCenter.y;
        double offsetZ =
                point.z - faceCenter.z;
        double radialX =
                faceCenter.x - origin.x;
        double radialY =
                faceCenter.y - origin.y;
        double radialZ =
                faceCenter.z - origin.z;
        double radialLength = Math.sqrt(
                radialX * radialX
                        + radialY * radialY
                        + radialZ * radialZ);
        if (radialLength <= 1.0E-6) {
            return linearDistance;
        }
        double pointX = point.x - origin.x;
        double pointY = point.y - origin.y;
        double pointZ = point.z - origin.z;
        double exactRadialDelta = Math.sqrt(
                        pointX * pointX
                                + pointY * pointY
                                + pointZ * pointZ)
                - radialLength;
        double linearRadialDelta =
                (radialX * offsetX
                                + radialY * offsetY
                                + radialZ * offsetZ)
                        / radialLength;
        return Math.max(
                0.0,
                linearDistance
                        + exactRadialDelta
                        - linearRadialDelta);
    }

    /**
     * Samples a continuous routed phase inside a tessellated face. The small
     * radial correction preserves the rounded 0.1 crest, but is calculated
     * from shared corner values so it cannot open a seam at a neighboring
     * face or block.
     */
    public static double distanceAtPoint(
            FaceDistances distances,
            double u,
            double v,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            Vec3 point,
            Vec3 origin) {
        if (distances == null
                || !distances.finite()
                || !isFinite(a)
                || !isFinite(b)
                || !isFinite(c)
                || !isFinite(d)
                || !isFinite(point)
                || !isFinite(origin)) {
            return Double.POSITIVE_INFINITY;
        }
        double routed = distances.at(u, v);
        double radialCorners = bilinear(
                a.distanceTo(origin),
                b.distanceTo(origin),
                c.distanceTo(origin),
                d.distanceTo(origin),
                u,
                v);
        return Math.max(
                0.0,
                routed
                        + point.distanceTo(origin)
                        - radialCorners);
    }

    private static double bilinear(
            double a,
            double b,
            double c,
            double d,
            double u,
            double v) {
        double near = a + (b - a) * u;
        double far = d + (c - d) * u;
        return near + (far - near) * v;
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private EchoSurfaceCrestPath() {
    }
}
