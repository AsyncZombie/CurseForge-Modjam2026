package dev.alvar.echoespast.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.block.EchoPedestalBlockEntity;
import dev.alvar.echoespast.resonance.EchoSiteType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Dimension-local persistent receiver index. It deliberately stores only
 * pedestal positions and six wool-occlusion bits, so a long-range query never
 * has to load a chunk.
 */
public final class EchoPedestalIndex extends SavedData {
    /**
     * Persisted tombstone for a known pedestal/site which cannot currently
     * answer sonar. Keeping the position prevents seed prediction from
     * resurrecting an emptied pedestal after its chunk unloads.
     */
    private static final int SILENT = -1;

    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("position").forGetter(Entry::position),
            Codec.INT.optionalFieldOf("blocked_faces", 0).forGetter(Entry::blockedFaces),
            Identifier.CODEC.optionalFieldOf("site").forGetter(Entry::site)
    ).apply(instance, Entry::new));

    private static final Codec<EchoPedestalIndex> CODEC = ENTRY_CODEC.listOf().xmap(
            EchoPedestalIndex::new,
            EchoPedestalIndex::serializedEntries);

    public static final SavedDataType<EchoPedestalIndex> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "echo_pedestal_index"),
            EchoPedestalIndex::new,
            CODEC);

    private final Map<Long, Integer> pedestals = new HashMap<>();
    private final Map<Long, Identifier> sites = new HashMap<>();

    public EchoPedestalIndex() {
    }

    private EchoPedestalIndex(List<Entry> entries) {
        for (Entry entry : entries) {
            pedestals.put(entry.position(), entry.blockedFaces());
            entry.site().ifPresent(site -> sites.put(entry.position(), site));
        }
    }

    public static EchoPedestalIndex get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void register(ServerLevel level, BlockPos position) {
        EchoPedestalBlockEntity pedestal = level.getBlockEntity(position) instanceof EchoPedestalBlockEntity found
                ? found
                : null;
        Identifier site = pedestal == null ? null : pedestal.site();
        int state = pedestal != null && pedestal.hasEcho()
                ? blockedFaces(level, position)
                : SILENT;
        get(level).put(position, state, site);
    }

    public static void unregister(ServerLevel level, BlockPos position) {
        get(level).remove(position);
    }

    public static void silence(ServerLevel level, BlockPos position) {
        get(level).silence(position);
    }

    public static void refresh(ServerLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        if (state.is(EchoesShowThePast.ECHO_PEDESTAL.get())) {
            register(level, position);
        } else if (isPortalFrequencySource(level, position)) {
            get(level).put(position, 0, EchoSiteType.UNKNOWN_CRYPT.id());
        } else {
            silence(level, position);
        }
    }

    public boolean contains(BlockPos position) {
        Integer state = pedestals.get(position.asLong());
        return state != null && state != SILENT;
    }

    public boolean knows(BlockPos position) {
        return pedestals.containsKey(position.asLong());
    }

    public boolean isBlockedFrom(BlockPos position, Vec3 origin) {
        Integer mask = pedestals.get(position.asLong());
        if (mask == null) {
            return true;
        }
        Direction incomingFace = incomingFace(position, origin);
        return incomingFace != null && (mask & bit(incomingFace)) != 0;
    }

    public Optional<Identifier> siteAt(BlockPos position) {
        return Optional.ofNullable(sites.get(position.asLong()));
    }

    public List<Candidate> candidates(Vec3 origin, double range) {
        return candidates(null, origin, range);
    }

    /**
     * Combines exact, already-seen pedestals with seed-predicted ruin receivers.
     * Base-height sampling is only performed for the handful of region
     * candidates, and never requests or generates their chunks.
     */
    public List<Candidate> candidates(ServerLevel level, Vec3 origin, double range) {
        return candidates(level, origin, range, null);
    }

    public List<Candidate> candidates(
            ServerLevel level,
            Vec3 origin,
            double range,
            BlockPos excludedPosition) {
        double rangeSquared = range * range;
        List<Candidate> result = new ArrayList<>();
        Set<Long> knownChunks = new HashSet<>();
        for (Map.Entry<Long, Integer> entry : pedestals.entrySet()) {
            long packed = entry.getKey();
            BlockPos position = BlockPos.of(packed);
            knownChunks.add(ChunkPos.pack(position));
            if (entry.getValue() == SILENT) {
                continue;
            }
            if (position.equals(excludedPosition)) {
                continue;
            }
            double distanceSquared = position.getCenter().distanceToSqr(origin);
            if (distanceSquared <= rangeSquared) {
                result.add(new Candidate(
                        position,
                        Math.sqrt(distanceSquared),
                        false,
                        siteAt(position)));
            }
        }
        if (level != null && level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            var generator = level.getChunkSource().getGenerator();
            var randomState = level.getChunkSource().randomState();
            for (EchoSiteType site : EchoSiteType.generatedSites()) {
                for (ChunkPos chunk : EchoSitePlacement.candidatesAround(
                        level,
                        site,
                        level.getSeed(),
                        origin.x,
                        origin.z,
                        range)) {
                    long chunkKey = chunk.pack();
                    if (knownChunks.contains(chunkKey)
                            || level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null) {
                        continue;
                    }
                    int x = chunk.getMiddleBlockX();
                    int z = chunk.getMiddleBlockZ();
                    if (!EchoSiteSpawnRules.wouldGenerate(level, site, x, z)) {
                        continue;
                    }
                    int y = site.requiresElevatedTerrain()
                            ? EchoSiteLandFooting.evaluate(
                                            generator,
                                            level,
                                            randomState,
                                            site,
                                            x,
                                            z)
                                    .anchorY()
                            : site.anchorY(
                                    generator,
                                    x,
                                    z,
                                    level,
                                    randomState);
                    BlockPos predicted = new BlockPos(x, y, z).offset(site.harmonicSource());
                    if (predicted.equals(excludedPosition)) {
                        continue;
                    }
                    double distanceSquared = predicted.getCenter().distanceToSqr(origin);
                    if (distanceSquared <= rangeSquared) {
                        result.add(new Candidate(
                                predicted,
                                Math.sqrt(distanceSquared),
                                true,
                                Optional.of(site.id())));
                    }
                }
            }
        }
        return result.stream()
                .sorted(Comparator.comparingDouble(Candidate::distance))
                .toList();
    }

    /**
     * Cheap phase used by the Resonator. Predicted sites contain only X/Z and
     * are resolved against noise height/biome later under a per-tick budget.
     */
    public List<CandidateSeed> candidateSeeds(
            ServerLevel level,
            Vec3 origin,
            double range,
            BlockPos excludedPosition) {
        double rangeSquared = range * range;
        List<CandidateSeed> result = new ArrayList<>();
        Set<Long> knownChunks = new HashSet<>();
        for (Map.Entry<Long, Integer> entry : pedestals.entrySet()) {
            long packed = entry.getKey();
            BlockPos position = BlockPos.of(packed);
            knownChunks.add(ChunkPos.pack(position));
            if (entry.getValue() == SILENT) {
                continue;
            }
            if (position.equals(excludedPosition)) {
                continue;
            }
            double distanceSquared = position.getCenter().distanceToSqr(origin);
            if (distanceSquared <= rangeSquared) {
                result.add(CandidateSeed.exact(
                        position,
                        Math.sqrt(distanceSquared),
                        siteAt(position)));
            }
        }
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            return result.stream()
                    .sorted(Comparator.comparingDouble(CandidateSeed::horizontalDistance))
                    .toList();
        }
        for (EchoSiteType site : EchoSiteType.generatedSites()) {
            for (ChunkPos chunk : EchoSitePlacement.candidatesAround(
                    level,
                    site,
                    level.getSeed(),
                    origin.x,
                    origin.z,
                    range)) {
                if (knownChunks.contains(chunk.pack())
                        || level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null) {
                    continue;
                }
                int x = chunk.getMiddleBlockX();
                int z = chunk.getMiddleBlockZ();
                if (excludedPosition != null
                        && excludedPosition.getX() == x
                        && excludedPosition.getZ() == z) {
                    continue;
                }
                double dx = x + 0.5 - origin.x;
                double dz = z + 0.5 - origin.z;
                double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
                if (horizontalDistance <= range) {
                    result.add(CandidateSeed.predicted(x, z, horizontalDistance, site.id()));
                }
            }
        }
        return result.stream()
                .sorted(Comparator.comparingDouble(CandidateSeed::horizontalDistance))
                .toList();
    }

    public void synchronizeChunk(ServerLevel level, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        Set<Long> previouslyKnown = pedestals.keySet().stream().filter(packed -> {
            BlockPos position = BlockPos.of(packed);
            return new ChunkPos(position.getX() >> 4, position.getZ() >> 4).equals(chunkPos);
        }).collect(java.util.stream.Collectors.toSet());

        boolean changed = false;
        Set<Long> found = new HashSet<>();
        LevelChunkSection[] sections = chunk.getSections();
        int minimumX = chunkPos.getMinBlockX();
        int minimumZ = chunkPos.getMinBlockZ();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(state -> state.is(EchoesShowThePast.ECHO_PEDESTAL.get())
                    || state.is(EchoesShowThePast.TIMELESS_PORTAL.get()))) {
                continue;
            }
            int minimumY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        BlockPos position = new BlockPos(
                                minimumX + localX,
                                minimumY + localY,
                                minimumZ + localZ);
                        if (isPortalFrequencySource(level, chunk, position, state)) {
                            long packed = position.asLong();
                            found.add(packed);
                            Integer previous = pedestals.put(packed, 0);
                            Identifier previousSite = sites.put(
                                    packed,
                                    EchoSiteType.UNKNOWN_CRYPT.id());
                            changed |= previous == null
                                    || previous != 0
                                    || !java.util.Objects.equals(
                                            previousSite, EchoSiteType.UNKNOWN_CRYPT.id());
                            continue;
                        }
                        if (!state.is(EchoesShowThePast.ECHO_PEDESTAL.get())) {
                            continue;
                        }
                        EchoPedestalBlockEntity pedestal = chunk.getBlockEntity(position) instanceof EchoPedestalBlockEntity loaded
                                ? loaded
                                : null;
                        Identifier site = pedestal == null ? null : pedestal.site();
                        int receiverState = pedestal != null && pedestal.hasEcho()
                                ? blockedFaces(level, position)
                                : SILENT;
                        long packed = position.asLong();
                        found.add(packed);
                        Integer previous = pedestals.put(packed, receiverState);
                        changed |= previous == null || previous != receiverState;
                        if (site != null) {
                            Identifier previousSite = sites.put(packed, site);
                            changed |= !java.util.Objects.equals(previousSite, site);
                        } else if (pedestal != null) {
                            changed |= sites.remove(packed) != null;
                        }
                    }
                }
            }
        }

        for (long packed : previouslyKnown) {
            if (found.contains(packed)) {
                continue;
            }
            Integer previous = pedestals.put(packed, SILENT);
            changed |= previous == null || previous != SILENT;
        }
        if (changed) {
            setDirty();
        }
    }

    /**
     * The Harmonic Key locks onto the Timeless Portal's frequency, not every
     * cell of the 3x3 pad and not Void-side portal copies.
     */
    private static boolean isPortalFrequencySource(ServerLevel level, BlockPos position) {
        return isPortalFrequencySource(
                level,
                level.getChunkAt(position),
                position,
                level.getBlockState(position));
    }

    private static boolean isPortalFrequencySource(
            ServerLevel level,
            ChunkAccess chunk,
            BlockPos position,
            BlockState state) {
        if (!level.dimension().equals(Level.OVERWORLD)
                || !state.is(EchoesShowThePast.TIMELESS_PORTAL.get())) {
            return false;
        }
        ChunkPos chunkPos = chunk.getPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos neighbor = position.offset(dx, 0, dz);
                if (ChunkPos.pack(neighbor) != chunkPos.pack()) {
                    return false;
                }
                if (!chunk.getBlockState(neighbor).is(EchoesShowThePast.TIMELESS_PORTAL.get())) {
                    return false;
                }
            }
        }
        return true;
    }

    public static Direction incomingFace(BlockPos pedestal, Vec3 origin) {
        Vec3 towardOrigin = origin.subtract(pedestal.getCenter());
        if (towardOrigin.lengthSqr() < 1.0E-6) {
            return null;
        }
        return Direction.getApproximateNearest(towardOrigin);
    }

    public static int blockedFaces(ServerLevel level, BlockPos pedestal) {
        int mask = 0;
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pedestal.relative(direction))
                    .is(BlockTags.OCCLUDES_VIBRATION_SIGNALS)) {
                mask |= bit(direction);
            }
        }
        return mask;
    }

    public static int bit(Direction direction) {
        return 1 << direction.ordinal();
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            get(level).synchronizeChunk(level, event.getChunk());
        }
    }

    private void put(BlockPos position, int blockedFaces, Identifier site) {
        Integer previous = pedestals.put(position.asLong(), blockedFaces);
        Identifier previousSite = site == null
                ? sites.remove(position.asLong())
                : sites.put(position.asLong(), site);
        if (previous == null || previous != blockedFaces || !java.util.Objects.equals(previousSite, site)) {
            setDirty();
        }
    }

    private void remove(BlockPos position) {
        if (pedestals.remove(position.asLong()) != null) {
            sites.remove(position.asLong());
            setDirty();
        }
    }

    private void silence(BlockPos position) {
        long packed = position.asLong();
        Integer previous = pedestals.put(packed, SILENT);
        if (previous == null || previous != SILENT) {
            setDirty();
        }
    }

    private List<Entry> serializedEntries() {
        return pedestals.entrySet().stream()
                .map(entry -> new Entry(
                        entry.getKey(),
                        entry.getValue(),
                        Optional.ofNullable(sites.get(entry.getKey()))))
                .toList();
    }

    private record Entry(long position, int blockedFaces, Optional<Identifier> site) {
    }

    public record Candidate(
            BlockPos position,
            double distance,
            boolean predicted,
            Optional<Identifier> site) {
        public Candidate {
            position = position.immutable();
            site = site == null ? Optional.empty() : site;
        }

        public Candidate(BlockPos position, double distance) {
            this(position, distance, false, Optional.empty());
        }

        public Candidate(BlockPos position, double distance, boolean predicted) {
            this(position, distance, predicted, Optional.empty());
        }
    }

    public record CandidateSeed(
            Optional<BlockPos> exactPosition,
            int x,
            int z,
            double horizontalDistance,
            boolean predicted,
            Optional<Identifier> site) {
        public CandidateSeed {
            exactPosition = exactPosition == null
                    ? Optional.empty()
                    : exactPosition.map(BlockPos::immutable);
            site = site == null ? Optional.empty() : site;
        }

        public static CandidateSeed exact(
                BlockPos position,
                double distance,
                Optional<Identifier> site) {
            return new CandidateSeed(
                    Optional.of(position),
                    position.getX(),
                    position.getZ(),
                    distance,
                    false,
                    site);
        }

        public static CandidateSeed predicted(
                int x,
                int z,
                double distance,
                Identifier site) {
            return new CandidateSeed(
                    Optional.empty(),
                    x,
                    z,
                    distance,
                    true,
                    Optional.of(site));
        }
    }
}
