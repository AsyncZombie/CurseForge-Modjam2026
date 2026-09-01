package dev.alvar.echoespast.visual;

/**
 * Chooses CPU tessellation from the world-space width of a crest. Broad
 * low-frequency waves are already smooth when the GPU interpolates one quad;
 * the narrow Past Echo crest keeps its full sub-block geometry across the
 * entire authored memory.
 */
public final class EchoWaveTessellation {
    private static final int MAX_GRID_SUBDIVISIONS = 96;
    public static int subdivisions(double widthScale, double distanceToOrigin) {
        if (widthScale >= 3.0) {
            return 1;
        }
        if (widthScale >= 1.5) {
            return 2;
        }
        // This is a visual wave-radius parameter, not camera distance.
        // Reducing it after twelve blocks made the pulse visibly downgrade
        // exactly as it travelled away from its origin.
        return 9;
    }

    /**
     * Past Echo's narrow crest relies on the original 0.1 sub-block
     * tessellation. Reducing it according to scene density turns the moving
     * line into whole illuminated block faces. Performance is bounded before
     * this point by the sorted radial face window, so density must not alter
     * the shape of the crest that reaches the renderer.
     */
    public static int subdivisions(
            double widthScale,
            double distanceToOrigin,
            int visibleFaceCount) {
        return subdivisions(widthScale, distanceToOrigin);
    }

    public static int verticesPerFace(int subdivisions, int passes) {
        int safeSubdivisions = Math.max(1, subdivisions);
        int safePasses = Math.max(1, passes);
        return safeSubdivisions * safeSubdivisions * 4 * safePasses;
    }

    /**
     * Preserves the per-block sampling density when several adjacent template
     * cells are represented by one rectangular wave tile. A four-block tile
     * therefore receives four times the subdivisions of an ordinary face,
     * avoiding holes in the narrow crest between its sampled vertices.
     */
    public static Grid grid(
            double widthScale,
            double distanceToOrigin,
            double uLength,
            double vLength) {
        int density = subdivisions(widthScale, distanceToOrigin);
        return new Grid(
                subdivisionsForLength(uLength, density),
                subdivisionsForLength(vLength, density));
    }

    private static int subdivisionsForLength(
            double length,
            int density) {
        return Math.clamp(
                (int) Math.ceil(Math.max(1.0, length) * density),
                1,
                MAX_GRID_SUBDIVISIONS);
    }

    public record Grid(int u, int v) {
    }

    private EchoWaveTessellation() {
    }
}
