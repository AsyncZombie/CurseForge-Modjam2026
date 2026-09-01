package dev.alvar.echoespast.resonance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alvar.echoespast.EchoesShowThePast;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * A site is authored through {@code data/<namespace>/echo_sites/<id>.json}.
 *
 * <p>Built-in entries remain as a safe fallback for relic and knowledge IDs.
 * Sites that should no longer generate are marked {@code generated=false}
 * (or {@code enabled: false} in datapack JSON) so they stay out of worldgen.</p>
 */
public record EchoSiteType(
        Identifier id,
        Identifier structure,
        Identifier structureSet,
        Identifier presentTemplate,
        Identifier intactTemplate,
        ResonanceColor defaultColor,
        Family family,
        AnchorHeight anchorHeight,
        int anchorYOffset,
        boolean requiresOpenOcean,
        boolean requiresElevatedTerrain,
        BlockPos memoryMin,
        BlockPos memoryMax,
        boolean generated,
        boolean requiresHarmonicKey,
        Optional<ResourceKey<Biome>> biome,
        List<LootPlacement> loot,
        BlockPos harmonicSource) {
    /**
     * Deepslate-layer Y for buried sites such as the Unknown crypt. The
     * authored room sits here unless the local surface is too low to keep it
     * covered, in which case it rises just enough to stay underground.
     */
    public static final int DEEP_CRYPT_ANCHOR_Y = -40;
    private static final int BURIED_COVER = 8;

    private static final Map<Identifier, EchoSiteType> BUILT_INS = new LinkedHashMap<>();
    private static volatile Map<Identifier, EchoSiteType> BY_ID = Map.of();

    public static final ResourceKey<Biome> MEDUSA_ISLAND_BIOME = ResourceKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "medusa_island"));
    public static final ResourceKey<Biome> WATCHTOWER_GROUNDS_BIOME = ResourceKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "watchtower_grounds"));
    public static final ResourceKey<Biome> COLISEUM_GROUNDS_BIOME = ResourceKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "coliseum_grounds"));

    /** Retired test site; kept only so relic knowledge IDs stay stable. */
    public static final EchoSiteType LEGACY_RUIN = registerBuiltIn(
            "echo_ruin",
            ResonanceColor.CYAN,
            Family.LEGACY,
            AnchorHeight.WORLD_SURFACE,
            0,
            false,
            false,
            new BlockPos(-4, -1, -7),
            new BlockPos(4, 4, 4),
            false,
            false);
    /** Retired test site; kept only so Eye of Horus knowledge IDs stay stable. */
    public static final EchoSiteType HORUS = registerBuiltIn(
            "tomb_of_horus",
            ResonanceColor.AMBER,
            Family.DESERT,
            AnchorHeight.WORLD_SURFACE,
            0,
            false,
            false,
            new BlockPos(-11, -3, -15),
            new BlockPos(11, 8, 15),
            false,
            false);
    public static final EchoSiteType MEDUSA = registerBuiltIn(
            "sanctuary_of_medusa",
            ResonanceColor.JADE,
            Family.STONY,
            AnchorHeight.SEA_LEVEL,
            -15,
            true,
            false,
            new BlockPos(-71, -27, -47),
            new BlockPos(48, 55, 76),
            true,
            false,
            Optional.of(MEDUSA_ISLAND_BIOME));
    /** Retired test site; kept only so Holy Grail knowledge IDs stay stable. */
    public static final EchoSiteType GRAIL = registerBuiltIn(
            "abbey_of_the_grail",
            ResonanceColor.PALE_BLUE,
            Family.DARK_FOREST,
            AnchorHeight.WORLD_SURFACE,
            0,
            false,
            false,
            new BlockPos(-14, -6, -17),
            new BlockPos(14, 9, 17),
            false,
            false);
    /** Retired test site; kept only so Philosopher's Stone key gating stays stable. */
    public static final EchoSiteType ENCLAVE = registerBuiltIn(
            "alchemical_enclave",
            ResonanceColor.VIOLET,
            Family.MOUNTAIN,
            AnchorHeight.WORLD_SURFACE,
            0,
            false,
            false,
            new BlockPos(-9, -4, -9),
            new BlockPos(9, 9, 9),
            false,
            true);
    public static final EchoSiteType MEDIEVAL_WATCHTOWER = registerBuiltIn(
            "medieval_watchtower",
            ResonanceColor.PEWTER,
            Family.LEGACY,
            AnchorHeight.OCEAN_FLOOR,
            0,
            false,
            true,
            new BlockPos(-13, -15, -22),
            new BlockPos(12, 24, 21),
            true,
            false,
            Optional.of(WATCHTOWER_GROUNDS_BIOME));
    public static final EchoSiteType COLISEUM = registerBuiltIn(
            "coliseum",
            ResonanceColor.GOLD,
            Family.DESERT,
            AnchorHeight.OCEAN_FLOOR,
            0,
            false,
            true,
            new BlockPos(-23, -1, -19),
            new BlockPos(22, 23, 19),
            true,
            false,
            Optional.of(COLISEUM_GROUNDS_BIOME));
    public static final ResourceKey<Biome> ERECHTHEION_GROUNDS_BIOME = ResourceKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "erechtheion_grounds"));
    public static final EchoSiteType ERECHTHEION = registerBuiltIn(
            "erechtheion",
            ResonanceColor.WHITE,
            Family.STONY,
            AnchorHeight.OCEAN_FLOOR,
            0,
            false,
            true,
            new BlockPos(-22, -7, -19),
            new BlockPos(21, 16, 18),
            true,
            false,
            Optional.of(ERECHTHEION_GROUNDS_BIOME));
    public static final ResourceKey<Biome> MINE_GROUNDS_BIOME = ResourceKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "mine_grounds"));
    public static final EchoSiteType ABANDONED_MINE = registerBuiltIn(
            "abandoned_mine",
            ResonanceColor.AMBER,
            Family.MOUNTAIN,
            AnchorHeight.OCEAN_FLOOR,
            0,
            false,
            false,
            new BlockPos(-14, -23, -25),
            new BlockPos(13, 14, 24),
            true,
            false,
            Optional.of(MINE_GROUNDS_BIOME));
    public static final ResourceKey<Biome> EGYPTIAN_TEMPLE_GROUNDS_BIOME = ResourceKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "egyptian_temple_grounds"));
    public static final EchoSiteType EGYPTIAN_TEMPLE = registerBuiltIn(
            "egyptian_temple",
            ResonanceColor.GOLD,
            Family.DESERT,
            AnchorHeight.OCEAN_FLOOR,
            0,
            false,
            false,
            new BlockPos(-28, -29, -11),
            new BlockPos(27, 12, 11),
            true,
            false,
            Optional.of(EGYPTIAN_TEMPLE_GROUNDS_BIOME));
    public static final ResourceKey<Biome> CRYPT_GROUNDS_BIOME = ResourceKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "crypt_grounds"));
    public static final EchoSiteType UNKNOWN_CRYPT = registerBuiltIn(
            "unknown_crypt",
            ResonanceColor.VIOLET,
            Family.MOUNTAIN,
            AnchorHeight.DEEP_SLATE,
            0,
            false,
            false,
            new BlockPos(-19, -1, -19),
            new BlockPos(19, 29, 19),
            true,
            true,
            Optional.of(CRYPT_GROUNDS_BIOME),
            new BlockPos(0, 3, 0));

    static {
        BY_ID = immutableCopy(BUILT_INS);
    }

    private static EchoSiteType registerBuiltIn(
            String path,
            ResonanceColor color,
            Family family,
            AnchorHeight anchorHeight,
            int anchorYOffset,
            boolean requiresOpenOcean,
            boolean requiresElevatedTerrain,
            BlockPos min,
            BlockPos max,
            boolean generated,
            boolean requiresKey) {
        return registerBuiltIn(
                path,
                color,
                family,
                anchorHeight,
                anchorYOffset,
                requiresOpenOcean,
                requiresElevatedTerrain,
                min,
                max,
                generated,
                requiresKey,
                Optional.empty());
    }

    private static EchoSiteType registerBuiltIn(
            String path,
            ResonanceColor color,
            Family family,
            AnchorHeight anchorHeight,
            int anchorYOffset,
            boolean requiresOpenOcean,
            boolean requiresElevatedTerrain,
            BlockPos min,
            BlockPos max,
            boolean generated,
            boolean requiresKey,
            Optional<ResourceKey<Biome>> biome) {
        return registerBuiltIn(
                path,
                color,
                family,
                anchorHeight,
                anchorYOffset,
                requiresOpenOcean,
                requiresElevatedTerrain,
                min,
                max,
                generated,
                requiresKey,
                biome,
                BlockPos.ZERO);
    }

    private static EchoSiteType registerBuiltIn(
            String path,
            ResonanceColor color,
            Family family,
            AnchorHeight anchorHeight,
            int anchorYOffset,
            boolean requiresOpenOcean,
            boolean requiresElevatedTerrain,
            BlockPos min,
            BlockPos max,
            boolean generated,
            boolean requiresKey,
            Optional<ResourceKey<Biome>> biome,
            BlockPos harmonicSource) {
        Identifier id = Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, path);
        EchoSiteType site = new EchoSiteType(
                id,
                id,
                id,
                Identifier.fromNamespaceAndPath(
                        EchoesShowThePast.MOD_ID,
                        "sites/" + path + "_present"),
                Identifier.fromNamespaceAndPath(
                        EchoesShowThePast.MOD_ID,
                        "sites/" + path + "_intact"),
                color,
                family,
                anchorHeight,
                anchorYOffset,
                requiresOpenOcean,
                requiresElevatedTerrain,
                min.immutable(),
                max.immutable(),
                generated,
                requiresKey,
                biome,
                List.of(),
                harmonicSource.immutable());
        BUILT_INS.put(id, site);
        return site;
    }

    /** Replaces built-in presentation data with validated datapack entries. */
    public static void installDataPackSites(Map<Identifier, Definition> definitions) {
        Map<Identifier, EchoSiteType> next = new LinkedHashMap<>(BUILT_INS);
        definitions.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> next.put(entry.getKey(), entry.getValue().bind(entry.getKey())));
        BY_ID = immutableCopy(next);
    }

    public static EchoSiteType byId(Identifier id) {
        return BY_ID.get(id);
    }

    public static List<EchoSiteType> values() {
        return List.copyOf(BY_ID.values());
    }

    public static List<EchoSiteType> generatedSites() {
        return BY_ID.values().stream().filter(EchoSiteType::generated).toList();
    }

    public String translationKey() {
        return "site." + EchoesShowThePast.MOD_ID + "." + id.getPath();
    }

    /**
     * Land ruins stamp their own pad and then ask nearby terrain to meet
     * that height, instead of sitting in a cut cube. Ocean islands skip this:
     * a dirt or sand collar would bury the shoreline.
     */
    public boolean blendsIntoTerrain() {
        return generated && !requiresOpenOcean && !underground();
    }

    /**
     * A buried site keeps its authored memory under solid ground and may grow
     * a shaft to the real surface. Sea-level islands use a negative offset for
     * a different reason and remain exposed. Deepslate anchors are always
     * underground, even with a zero offset.
     */
    public boolean underground() {
        return generated
                && !requiresOpenOcean
                && anchorHeight != AnchorHeight.SEA_LEVEL
                && (anchorYOffset < 0 || anchorHeight == AnchorHeight.DEEP_SLATE);
    }

    /** Solid-ground height used for shafts, biome checks and {@code /locate}. */
    public int surfaceY(
            ChunkGenerator generator,
            int x,
            int z,
            LevelHeightAccessor heightAccessor,
            RandomState randomState) {
        if (anchorHeight == AnchorHeight.SEA_LEVEL) {
            return generator.getSeaLevel();
        }
        Heightmap.Types heightmap = anchorHeight == AnchorHeight.DEEP_SLATE
                ? Heightmap.Types.OCEAN_FLOOR_WG
                : anchorHeight.heightmap();
        return generator.getFirstOccupiedHeight(
                x,
                z,
                heightmap,
                heightAccessor,
                randomState);
    }

    public int anchorY(
            ChunkGenerator generator,
            int x,
            int z,
            LevelHeightAccessor heightAccessor,
            RandomState randomState) {
        int surface = surfaceY(generator, x, z, heightAccessor, randomState);
        int desired = anchorHeight == AnchorHeight.DEEP_SLATE
                ? DEEP_CRYPT_ANCHOR_Y + anchorYOffset
                : surface + anchorYOffset;
        if (!underground()) {
            return desired;
        }
        int minAnchor = heightAccessor.getMinY() + BURIED_COVER - memoryMin.getY();
        int maxAnchor = surface - BURIED_COVER - memoryMax.getY();
        if (maxAnchor < minAnchor) {
            return minAnchor;
        }
        return Math.clamp(desired, minAnchor, maxAnchor);
    }

    public BoundingBox memoryBounds(BlockPos anchor) {
        BlockPos min = anchor.offset(memoryMin);
        BlockPos max = anchor.offset(memoryMax);
        return new BoundingBox(
                min.getX(),
                min.getY(),
                min.getZ(),
                max.getX(),
                max.getY(),
                max.getZ());
    }

    private static Map<Identifier, EchoSiteType> immutableCopy(
            Map<Identifier, EchoSiteType> sites) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(sites));
    }

    /**
     * Gives one authored container its loot table.
     *
     * <p>{@code offset} is the container's position inside the structure
     * template, the same coordinate the authoring tools report, so a zone can
     * be re-assigned by editing the site manifest alone.</p>
     *
     * <p>{@code phase} selects when the table is sealed: {@code present} at
     * structure generation, {@code past} when the Philosopher's Stone
     * materializes the intact template.</p>
     */
    public record LootPlacement(
            BlockPos offset,
            ResourceKey<LootTable> lootTable,
            LootPhase phase) {
        public static final Codec<LootPlacement> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        BlockPos.CODEC.fieldOf("offset").forGetter(LootPlacement::offset),
                        ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("loot_table")
                                .forGetter(LootPlacement::lootTable),
                        LootPhase.CODEC.optionalFieldOf("phase", LootPhase.PRESENT)
                                .forGetter(LootPlacement::phase)
                ).apply(instance, LootPlacement::new));

        public LootPlacement {
            offset = offset.immutable();
        }
    }

    public enum LootPhase implements StringRepresentable {
        PRESENT("present"),
        PAST("past");

        public static final Codec<LootPhase> CODEC =
                StringRepresentable.fromEnum(LootPhase::values);

        private final String serializedName;

        LootPhase(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    /** Present-world containers sealed during structure generation. */
    public List<LootPlacement> presentLoot() {
        return loot.stream().filter(placement -> placement.phase() == LootPhase.PRESENT).toList();
    }

    /** Intact-template containers sealed when the past is materialized. */
    public List<LootPlacement> pastLoot() {
        return loot.stream().filter(placement -> placement.phase() == LootPhase.PAST).toList();
    }

    /** The content of one {@code echo_sites/*.json} file, before its file ID is bound. */
    public record Definition(
            Identifier structure,
            Identifier structureSet,
            Identifier presentTemplate,
            Identifier intactTemplate,
            ResonanceColor defaultColor,
            Family family,
            AnchorHeight anchorHeight,
            int anchorYOffset,
            boolean requiresOpenOcean,
            boolean requiresElevatedTerrain,
            BlockPos memoryMin,
            BlockPos memoryMax,
            boolean enabled,
            boolean requiresHarmonicKey,
            Optional<ResourceKey<Biome>> biome,
            List<LootPlacement> loot,
            BlockPos harmonicSource) {

        public static final Codec<Definition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("structure").forGetter(Definition::structure),
                Identifier.CODEC.fieldOf("structure_set").forGetter(Definition::structureSet),
                Identifier.CODEC.fieldOf("present_template").forGetter(Definition::presentTemplate),
                Identifier.CODEC.fieldOf("intact_template").forGetter(Definition::intactTemplate),
                ResonanceColor.CODEC.fieldOf("default_color").forGetter(Definition::defaultColor),
                Family.CODEC.fieldOf("sonar_family").forGetter(Definition::family),
                AnchorHeight.CODEC.fieldOf("anchor_heightmap").forGetter(Definition::anchorHeight),
                Codec.INT.optionalFieldOf("anchor_y_offset", 0).forGetter(Definition::anchorYOffset),
                Codec.BOOL.optionalFieldOf("requires_open_ocean", false)
                        .forGetter(Definition::requiresOpenOcean),
                Codec.BOOL.optionalFieldOf("requires_elevated_terrain", false)
                        .forGetter(Definition::requiresElevatedTerrain),
                BlockPos.CODEC.fieldOf("memory_min").forGetter(Definition::memoryMin),
                BlockPos.CODEC.fieldOf("memory_max").forGetter(Definition::memoryMax),
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Definition::enabled),
                HarmonicLock.MAP_CODEC.forGetter(HarmonicLock::of),
                ResourceKey.codec(Registries.BIOME).optionalFieldOf("biome")
                        .forGetter(Definition::biome),
                LootPlacement.CODEC.listOf().optionalFieldOf("loot", List.of())
                        .forGetter(Definition::loot)
        ).apply(instance, Definition::create));

        public static Definition create(
                Identifier structure,
                Identifier structureSet,
                Identifier presentTemplate,
                Identifier intactTemplate,
                ResonanceColor defaultColor,
                Family family,
                AnchorHeight anchorHeight,
                Integer anchorYOffset,
                Boolean requiresOpenOcean,
                Boolean requiresElevatedTerrain,
                BlockPos memoryMin,
                BlockPos memoryMax,
                Boolean enabled,
                HarmonicLock harmonic,
                Optional<ResourceKey<Biome>> biome,
                List<LootPlacement> loot) {
            return new Definition(
                    structure,
                    structureSet,
                    presentTemplate,
                    intactTemplate,
                    defaultColor,
                    family,
                    anchorHeight,
                    anchorYOffset,
                    requiresOpenOcean,
                    requiresElevatedTerrain,
                    memoryMin,
                    memoryMax,
                    enabled,
                    harmonic.requiresKey(),
                    biome,
                    loot,
                    harmonic.source());
        }

        public Definition {
            memoryMin = memoryMin.immutable();
            memoryMax = memoryMax.immutable();
            harmonicSource = harmonicSource.immutable();
            if (memoryMin.getX() > memoryMax.getX()
                    || memoryMin.getY() > memoryMax.getY()
                    || memoryMin.getZ() > memoryMax.getZ()) {
                throw new IllegalArgumentException(
                        "A site memory_min must not exceed its memory_max");
            }
        }

        public EchoSiteType bind(Identifier id) {
            return new EchoSiteType(
                    id,
                    structure,
                    structureSet,
                    presentTemplate,
                    intactTemplate,
                    defaultColor,
                    family,
                    anchorHeight,
                    anchorYOffset,
                    requiresOpenOcean,
                    requiresElevatedTerrain,
                    memoryMin,
                    memoryMax,
                    enabled,
                    requiresHarmonicKey,
                    biome,
                    List.copyOf(loot),
                    harmonicSource);
        }

        /**
         * Flattened onto {@code echo_sites/*.json} so the 16-field record codec
         * can still carry the portal frequency without changing the manifest shape.
         */
        private record HarmonicLock(boolean requiresKey, BlockPos source) {
            static final MapCodec<HarmonicLock> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("requires_harmonic_key", false)
                            .forGetter(HarmonicLock::requiresKey),
                    BlockPos.CODEC.optionalFieldOf("harmonic_source", BlockPos.ZERO)
                            .forGetter(HarmonicLock::source)
            ).apply(instance, HarmonicLock::new));

            HarmonicLock {
                source = source.immutable();
            }

            static HarmonicLock of(Definition definition) {
                return new HarmonicLock(definition.requiresHarmonicKey(), definition.harmonicSource());
            }
        }
    }

    public enum Family implements StringRepresentable {
        LEGACY("legacy"),
        DESERT("desert"),
        STONY("stony"),
        DARK_FOREST("dark_forest"),
        MOUNTAIN("mountain");

        public static final Codec<Family> CODEC =
                StringRepresentable.fromEnum(Family::values);

        private final String serializedName;

        Family(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    public enum AnchorHeight implements StringRepresentable {
        WORLD_SURFACE("world_surface", Heightmap.Types.WORLD_SURFACE_WG),
        OCEAN_FLOOR("ocean_floor", Heightmap.Types.OCEAN_FLOOR_WG),
        DEEP_SLATE("deep_slate", null),
        SEA_LEVEL("sea_level", null);

        public static final Codec<AnchorHeight> CODEC =
                StringRepresentable.fromEnum(AnchorHeight::values);

        private final String serializedName;
        private final Heightmap.Types heightmap;

        AnchorHeight(String serializedName, Heightmap.Types heightmap) {
            this.serializedName = serializedName;
            this.heightmap = heightmap;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        Heightmap.Types heightmap() {
            return heightmap;
        }

        public int anchorY(
                ChunkGenerator generator,
                int x,
                int z,
                LevelHeightAccessor heightAccessor,
                RandomState randomState) {
            if (this == SEA_LEVEL) {
                return generator.getSeaLevel();
            }
            if (this == DEEP_SLATE) {
                return EchoSiteType.DEEP_CRYPT_ANCHOR_Y;
            }
            return generator.getFirstOccupiedHeight(
                    x,
                    z,
                    heightmap,
                    heightAccessor,
                    randomState);
        }
    }
}
