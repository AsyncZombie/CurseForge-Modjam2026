package dev.alvar.echoespast.snapshot;

import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pulse-lifetime fade seed for a Past Echo site.
 *
 * <p>A structure template stores only what its author built, so a memory cannot
 * distinguish "the past had air here" from "the past never described this
 * cell". Reading the second as the first treats seabed, ocean and natural
 * terrain inside the memory volume as post-facto additions.</p>
 *
 * <p>The packaged set is the honest seed derived from present and intact
 * templates: structure cells the ruin adds into historical air (including
 * barrier masks), present solids over non-solid remembered decoration, and
 * present blocks that replace a different remembered solid. Soft ground used
 * only to blend a land site into worldgen is omitted when it sits on
 * historical air, so the echo does not fade the collar as false
 * {@code ADDED}. Everything else is left to the world.</p>
 *
 * <h2>NBT format</h2>
 * <ul>
 *   <li>{@code size} — canonical {@code int[3]} array {@code [x, y, z]}.
 *       Readers also accept a list of three ints for older files.</li>
 *   <li>{@code cells} — int array of packed indices
 *       {@code (y * sizeX + x) * sizeZ + z} into that domain.</li>
 * </ul>
 */
public final class EchoSiteAdditions {
    private static final Logger LOGGER = LoggerFactory.getLogger(EchoSiteAdditions.class);
    private static final long MAX_ADDITIONS_NBT_BYTES = 8L * 1024L * 1024L;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final BitSet cells;

    private EchoSiteAdditions(int sizeX, int sizeY, int sizeZ, BitSet cells) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.cells = cells;
    }

    /**
     * The companion resource beside a site's templates.
     * {@code ..._intact} becomes {@code ..._additions}.
     */
    public static Identifier resourceFor(Identifier intactTemplate) {
        String path = intactTemplate.getPath();
        String base = path.endsWith("_intact")
                ? path.substring(0, path.length() - "_intact".length())
                : path;
        return Identifier.fromNamespaceAndPath(
                intactTemplate.getNamespace(),
                base + "_additions");
    }

    /**
     * Loads the fade seed for a site. Missing resource → empty (callers keep
     * the coarser reading). Present but malformed → warn and empty — never a
     * silent zero-cell seed.
     */
    public static Optional<EchoSiteAdditions> load(
            ResourceProvider resources,
            Identifier additions) {
        Identifier resource = additions.withPrefix("structure/").withSuffix(".nbt");
        Optional<Resource> handle = resources.getResource(resource);
        if (handle.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream input = handle.get().open()) {
            CompoundTag root = NbtIo.readCompressed(
                    input,
                    NbtAccounter.create(MAX_ADDITIONS_NBT_BYTES));
            return parse(root, resource.toString());
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn(
                    "Failed to read Past Echo fade seed {}: {}",
                    resource,
                    exception.toString());
            return Optional.empty();
        }
    }

    /**
     * Parses a fade-seed compound. Prefer this in tests; writers must store
     * {@code size} as an int array.
     */
    public static Optional<EchoSiteAdditions> parse(CompoundTag root) {
        return parse(root, "inline");
    }

    static Optional<EchoSiteAdditions> parse(CompoundTag root, String sourceLabel) {
        int[] size = readSize(root);
        if (size.length != 3 || size[0] <= 0 || size[1] <= 0 || size[2] <= 0) {
            LOGGER.warn(
                    "Past Echo fade seed {} has invalid size tag "
                            + "(want int[3] array, or list of 3 positive ints)",
                    sourceLabel);
            return Optional.empty();
        }
        long domain = (long) size[0] * (long) size[1] * (long) size[2];
        if (domain <= 0L || domain > Integer.MAX_VALUE) {
            LOGGER.warn(
                    "Past Echo fade seed {} size domain is unusable: {}x{}x{}",
                    sourceLabel,
                    size[0],
                    size[1],
                    size[2]);
            return Optional.empty();
        }
        Optional<int[]> packedCells = root.getIntArray("cells");
        if (packedCells.isEmpty()) {
            LOGGER.warn(
                    "Past Echo fade seed {} is missing int-array tag 'cells'",
                    sourceLabel);
            return Optional.empty();
        }
        int domainSize = (int) domain;
        BitSet cells = new BitSet(domainSize);
        int outOfRange = 0;
        for (int cell : packedCells.get()) {
            if (cell < 0 || cell >= domainSize) {
                outOfRange++;
                continue;
            }
            cells.set(cell);
        }
        if (outOfRange > 0) {
            LOGGER.warn(
                    "Past Echo fade seed {} dropped {} cell indices outside {}x{}x{} domain",
                    sourceLabel,
                    outOfRange,
                    size[0],
                    size[1],
                    size[2]);
        }
        EchoSiteAdditions loaded = new EchoSiteAdditions(
                size[0],
                size[1],
                size[2],
                cells);
        LOGGER.info(
                "Loaded Past Echo fade seed {} ({} cells, {}×{}×{})",
                sourceLabel,
                loaded.size(),
                loaded.sizeX(),
                loaded.sizeY(),
                loaded.sizeZ());
        return Optional.of(loaded);
    }

    /**
     * Canonical writers store {@code size} as {@code int[3]}. Older converters
     * used a list of three ints — {@link CompoundTag#getIntArray} rejects that
     * tag type, which used to leave every packaged seed empty on the client.
     */
    private static int[] readSize(CompoundTag root) {
        int[] asArray = root.getIntArray("size").orElse(null);
        if (asArray != null && asArray.length == 3) {
            return asArray;
        }
        ListTag list = root.getListOrEmpty("size");
        if (list.size() >= 3) {
            return new int[] {
                    list.getIntOr(0, 0),
                    list.getIntOr(1, 0),
                    list.getIntOr(2, 0)
            };
        }
        return new int[0];
    }

    /**
     * Whether this cell belongs to the packaged fade seed, in template
     * coordinates measured from the world position of the memory's lower corner.
     */
    public boolean contains(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            return false;
        }
        return cells.get((y * sizeX + x) * sizeZ + z);
    }

    public boolean contains(BlockPos world, BlockPos anchor) {
        return contains(
                world.getX() - anchor.getX(),
                world.getY() - anchor.getY(),
                world.getZ() - anchor.getZ());
    }

    public int size() {
        return cells.cardinality();
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    /**
     * Visits every fade-seed cell in template-local coordinates
     * {@code (x, y, z)} relative to the memory lower corner.
     */
    public void forEachCell(CellConsumer consumer) {
        for (int bit = cells.nextSetBit(0); bit >= 0; bit = cells.nextSetBit(bit + 1)) {
            int layer = sizeX * sizeZ;
            int y = bit / layer;
            int rem = bit % layer;
            int x = rem / sizeZ;
            int z = rem % sizeZ;
            consumer.accept(x, y, z);
        }
    }

    @FunctionalInterface
    public interface CellConsumer {
        void accept(int x, int y, int z);
    }
}
