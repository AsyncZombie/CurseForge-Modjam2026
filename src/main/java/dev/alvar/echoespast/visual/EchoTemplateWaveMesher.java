package dev.alvar.echoespast.visual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Converts one authored-template section into complete exterior surface
 * patches. The previous point sampling was cheap but left deliberate holes in
 * a travelling crest as soon as a surface had more than the sample budget.
 */
public final class EchoTemplateWaveMesher {
    /**
     * Greedy-meshes coplanar exterior cells that have the same visual material
     * response. Keeping response boundaries intact lets the renderer retain
     * material timing while making broad terrain one coherent surface.
     */
    public static List<Patch> meshSection(
            List<StructureTemplate.StructureBlockInfo> sectionBlocks,
            Map<Long, BlockState> allStates) {
        Map<Direction, Map<Integer, Map<Long, FaceCell>>> faces =
                new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            faces.put(direction, new HashMap<>());
        }

        for (StructureTemplate.StructureBlockInfo block : sectionBlocks) {
            BlockState state = block.state();
            if (!state.isSolidRender()) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockState neighbor = allStates.get(
                        block.pos().relative(direction).asLong());
                if (neighbor != null && neighbor.isSolidRender()) {
                    continue;
                }
                FaceCell cell = FaceCell.of(block.pos(), state, direction);
                faces.get(direction)
                        .computeIfAbsent(cell.plane(), ignored -> new HashMap<>())
                        .putIfAbsent(gridKey(cell.u(), cell.v()), cell);
            }
        }

        List<Patch> result = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            List<Map.Entry<Integer, Map<Long, FaceCell>>> planes =
                    new ArrayList<>(faces.get(direction).entrySet());
            planes.sort(Map.Entry.comparingByKey());
            for (Map.Entry<Integer, Map<Long, FaceCell>> plane : planes) {
                result.addAll(meshPlane(plane.getValue()));
            }
        }
        return List.copyOf(result);
    }

    private static List<Patch> meshPlane(Map<Long, FaceCell> cells) {
        List<FaceCell> ordered = new ArrayList<>(cells.values());
        ordered.sort(Comparator.comparingInt(FaceCell::v)
                .thenComparingInt(FaceCell::u));
        Set<Long> consumed = new HashSet<>();
        List<Patch> patches = new ArrayList<>();
        for (FaceCell first : ordered) {
            long firstKey = gridKey(first.u(), first.v());
            if (consumed.contains(firstKey)) {
                continue;
            }

            int width = 1;
            while (matches(cells, consumed, first, first.u() + width, first.v())) {
                width++;
            }

            int height = 1;
            while (rowMatches(
                    cells,
                    consumed,
                    first,
                    first.u(),
                    first.v() + height,
                    width)) {
                height++;
            }

            for (int v = 0; v < height; v++) {
                for (int u = 0; u < width; u++) {
                    consumed.add(gridKey(first.u() + u, first.v() + v));
                }
            }
            patches.add(new Patch(
                    first.position(),
                    first.state(),
                    first.direction(),
                    width,
                    height));
        }
        return patches;
    }

    private static boolean rowMatches(
            Map<Long, FaceCell> cells,
            Set<Long> consumed,
            FaceCell reference,
            int startU,
            int v,
            int width) {
        for (int u = 0; u < width; u++) {
            if (!matches(cells, consumed, reference, startU + u, v)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(
            Map<Long, FaceCell> cells,
            Set<Long> consumed,
            FaceCell reference,
            int u,
            int v) {
        long key = gridKey(u, v);
        FaceCell candidate = cells.get(key);
        return candidate != null
                && !consumed.contains(key)
                && candidate.response().equals(reference.response());
    }

    private static long gridKey(int u, int v) {
        return ((long) u << 32) ^ (v & 0xFFFF_FFFFL);
    }

    /**
     * A rectangular, outward-facing patch in template-local coordinates.
     * Width and height follow the two in-plane axes for {@link #direction()}.
     */
    public record Patch(
            BlockPos position,
            BlockState state,
            Direction direction,
            int width,
            int height) {
        public Patch {
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException(
                        "A wave patch must cover at least one exterior cell");
            }
        }

        public int area() {
            return width * height;
        }

        /**
         * Splits a patch into adjacent tiles without discarding any cells.
         * Small tiles keep crest tessellation and radial culling proportional
         * to the part of the surface actually crossed by the pulse.
         */
        public List<Patch> tiles(int maximumEdge) {
            if (maximumEdge < 1) {
                throw new IllegalArgumentException("maximumEdge must be positive");
            }
            if (width <= maximumEdge && height <= maximumEdge) {
                return List.of(this);
            }
            List<Patch> tiles = new ArrayList<>();
            for (int v = 0; v < height; v += maximumEdge) {
                for (int u = 0; u < width; u += maximumEdge) {
                    int tileWidth = Math.min(maximumEdge, width - u);
                    int tileHeight = Math.min(maximumEdge, height - v);
                    tiles.add(new Patch(
                            position.relative(uAxis(direction), u)
                                    .relative(vAxis(direction), v),
                            state,
                            direction,
                            tileWidth,
                            tileHeight));
                }
            }
            return List.copyOf(tiles);
        }
    }

    public static Direction uAxis(Direction direction) {
        return switch (direction) {
            case DOWN, UP, NORTH, SOUTH -> Direction.EAST;
            case WEST, EAST -> Direction.SOUTH;
        };
    }

    public static Direction vAxis(Direction direction) {
        return switch (direction) {
            case DOWN, UP -> Direction.SOUTH;
            case NORTH, SOUTH, WEST, EAST -> Direction.UP;
        };
    }

    private record FaceCell(
            BlockPos position,
            BlockState state,
            Direction direction,
            EchoMaterialResponse.Profile response,
            int plane,
            int u,
            int v) {
        static FaceCell of(
                BlockPos position,
                BlockState state,
                Direction direction) {
            int plane = switch (direction) {
                case DOWN -> position.getY();
                case UP -> position.getY() + 1;
                case NORTH -> position.getZ();
                case SOUTH -> position.getZ() + 1;
                case WEST -> position.getX();
                case EAST -> position.getX() + 1;
            };
            int u = switch (direction) {
                case DOWN, UP, NORTH, SOUTH -> position.getX();
                case WEST, EAST -> position.getZ();
            };
            int v = switch (direction) {
                case DOWN, UP -> position.getZ();
                case NORTH, SOUTH, WEST, EAST -> position.getY();
            };
            return new FaceCell(
                    position,
                    state,
                    direction,
                    EchoMaterialResponse.forState(state),
                    plane,
                    u,
                    v);
        }
    }

    private EchoTemplateWaveMesher() {
    }
}
