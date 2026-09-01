package dev.alvar.echoespast.server;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.block.BigEchoPedestalBlock;
import dev.alvar.echoespast.block.BigEchoPedestalBlockEntity;
import dev.alvar.echoespast.boss.UnknownEraSequence;
import dev.alvar.echoespast.entity.UnknownEntity;
import dev.alvar.echoespast.entity.SpectralHopliteEntity;
import dev.alvar.echoespast.entity.MedievalRubbleProjectile;
import dev.alvar.echoespast.entity.ai.UnknownGreekCombatGoal;
import dev.alvar.echoespast.entity.ai.UnknownMedievalCombatGoal;
import dev.alvar.echoespast.entity.combat.UnknownCombatState;
import dev.alvar.echoespast.entity.combat.UnknownGreekCombatMath;
import dev.alvar.echoespast.cinematic.UnknownEnterCinematicMath;
import dev.alvar.echoespast.network.UnknownAltarFragmentExplodePayload;
import dev.alvar.echoespast.network.UnknownBossBarPayload;
import dev.alvar.echoespast.network.UnknownEnterCinematicPayload;
import dev.alvar.echoespast.relic.RelicState;
import dev.alvar.echoespast.mixin.server.StructureTemplateAccessor;
import dev.alvar.echoespast.world.EchoPedestalIndex;
import dev.alvar.echoespast.world.TimelessDimensions;
import dev.alvar.echoespast.world.UnknownMedievalArenaProcessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UnknownFightManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnknownFightManager.class);
    private static final UnknownBossTrackingGrace BOSS_TRACKING_GRACE =
            new UnknownBossTrackingGrace();
    /**
     * Arena wipes must never scatter chests, pots, altar sockets or block loot
     * into the timeless void when eras collapse or the fight resets.
     */
    private static final int ARENA_MUTATION_FLAGS = Block.UPDATE_CLIENTS
            | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS
            | Block.UPDATE_SKIP_ON_PLACE;
    public static final float BOSS_MAX_HEALTH = 600.0F;
    public static final int RITUAL_OFFER_CYCLE_TICKS = 28;
    public static final int RITUAL_OFFER_PLACE_TICK = 14;
    /** Last era crane: rise writes the past, fall collapses it. One fight at a time. */
    private static boolean eraLensRising = true;
    /**
     * Vanilla kicks a survival player after ~80 airborne ticks. The plaza
     * audience stands inside the arena volume, so reconstruction can delete
     * the floor while the cinematic holds them still.
     */
    private static final Identifier CINEMATIC_FLIGHT_ID =
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "cinematic_flight");
    private static final AttributeModifier CINEMATIC_FLIGHT = new AttributeModifier(
            CINEMATIC_FLIGHT_ID,
            1.0D,
            AttributeModifier.Operation.ADD_VALUE);
    public static final int ASPIS_BLOCK_GOLD_SPARKS = 8;
    public static final int ASPIS_BLOCK_WHITE_SPARKS = 4;
    public static final double ASPIS_BLOCK_RECOIL = 0.38D;
    private static final DustParticleOptions ASPIS_GOLD_DUST = new DustParticleOptions(
            0xFFD447,
            0.82F);
    private static final float HEALTH_SEGMENT = 90.0F;
    private static final float INTERMEDIATE_VOID_DAMAGE = 45.0F;
    public static final int MEDIEVAL_SHIELD_BREAK_IMPACT_TICK = 18;
    public static final int MEDIEVAL_SHIELD_BREAK_TICKS = 40;
    public static final float EXECUTION_HEALTH = 1.0F;
    /** Five seconds: collapse settles, silhouette kneels, then owner gets the fatal hit. */
    public static final int VOID_EXECUTION_FATAL_TICK = 100;

    public enum Era {
        VOID,
        GREEK,
        EGYPTIAN,
        MEDIEVAL
    }

    public enum Phase {
        IDLE,
        CINEMATIC_WALK,
        /** Stone crest placing/clearing blocks; boss invulnerable, no attacks. */
        RECONSTRUCTING,
        PAST,
        RUINS,
        VOID_VULNERABLE,
        EXECUTION,
        DEAD
    }

    public enum Action {
        WAITING,
        APPROACHING_PEDESTAL,
        /** Walking complete; depositing six fragments then the stone onto the altar. */
        DEPOSITING_OFFERING,
        RECONSTRUCTING,
        /** Short invulnerable beat between the collapsing tower and the Ruins duel. */
        SHIELD_BREAK,
        COMBAT,
        SEEKING_PEDESTAL,
        EXECUTION,
        DEAD
    }

    /** Debug jump targets for `/echoes unknown stage`. */
    public enum CombatStage {
        CINEMATIC("cinematic", 0, 0, Era.VOID, Phase.CINEMATIC_WALK),
        MEDIEVAL_PAST("medieval", UnknownEraSequence.MEDIEVAL, Phase.PAST),
        MEDIEVAL_RUINS("medieval_ruins", UnknownEraSequence.MEDIEVAL, Phase.RUINS),
        GREEK_PAST("greek", UnknownEraSequence.GREEK, Phase.PAST),
        GREEK_RUINS("greek_ruins", UnknownEraSequence.GREEK, Phase.RUINS),
        EGYPTIAN_PAST("egyptian", UnknownEraSequence.EGYPTIAN, Phase.PAST),
        EGYPTIAN_RUINS("egyptian_ruins", UnknownEraSequence.EGYPTIAN, Phase.RUINS),
        VOID(
                "void",
                UnknownEraSequence.ERA_COUNT,
                UnknownEraSequence.STAGE_COUNT,
                Era.VOID,
                Phase.EXECUTION);

        private final String id;
        private final int eraIndex;
        private final int threshold;
        private final Era era;
        private final Phase phase;

        CombatStage(String id, int eraIndex, int threshold, Era era, Phase phase) {
            this.id = id;
            this.eraIndex = eraIndex;
            this.threshold = threshold;
            this.era = era;
            this.phase = phase;
        }

        CombatStage(String id, UnknownEraSequence definition, Phase phase) {
            this(
                    id,
                    definition.eraIndex(),
                    definition.threshold(phase == Phase.RUINS),
                    Era.valueOf(definition.name()),
                    phase);
        }

        public String id() {
            return id;
        }

        public int eraIndex() {
            return eraIndex;
        }

        public int threshold() {
            return threshold;
        }

        public Era era() {
            return era;
        }

        public Phase phase() {
            return phase;
        }

        public float health() {
            if (this == VOID) {
                return EXECUTION_HEALTH;
            }
            return threshold <= 0 ? BOSS_MAX_HEALTH : healthFloorForThreshold(threshold - 1);
        }

        public int minimumReviewEras() {
            if (this == VOID) {
                return UnknownEraSequence.ERA_COUNT;
            }
            return Math.max(1, eraIndex + 1);
        }

        public byte entityEra() {
            return era == Era.VOID
                    ? UnknownEntity.ERA_VOID
                    : UnknownEraSequence.forKey(era.name()).entityEra();
        }

        public Identifier arenaTemplate() {
            return era == Era.VOID
                    ? null
                    : UnknownEraSequence.forKey(era.name()).template(phase == Phase.RUINS);
        }

        public static CombatStage parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String key = raw.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
            return switch (key) {
                case "cinematic", "intro", "enter" -> CINEMATIC;
                case "greek", "greek_past" -> GREEK_PAST;
                case "greek_ruins" -> GREEK_RUINS;
                case "egyptian", "egyptian_past" -> EGYPTIAN_PAST;
                case "egyptian_ruins" -> EGYPTIAN_RUINS;
                case "medieval", "medieval_past" -> MEDIEVAL_PAST;
                case "medieval_ruins" -> MEDIEVAL_RUINS;
                case "void", "final", "execution" -> VOID;
                default -> null;
            };
        }
    }

    static final Identifier MEDIEVAL_PAST = UnknownEraSequence.MEDIEVAL.pastTemplate();
    static final Identifier MEDIEVAL_RUINS = UnknownEraSequence.MEDIEVAL.ruinsTemplate();
    private static final Identifier VOID_HUB = id("boss/void_hub");
    private static final java.util.List<Identifier> ARENA_TEMPLATES =
            java.util.stream.Stream.concat(
                            UnknownEraSequence.ordered().stream()
                                    .flatMap(era -> java.util.stream.Stream.of(
                                            era.pastTemplate(), era.ruinsTemplate())),
                            java.util.stream.Stream.of(VOID_HUB))
                    .toList();
    private static final BlockPos LEGACY_ARENA_MIN = new BlockPos(-40, 57, -25);
    private static final BlockPos LEGACY_ARENA_MAX = new BlockPos(34, 80, 21);
    /**
     * Shaderpacks do not have to honour DimensionType ambient_light.  These
     * invisible, collisionless light cells provide real lightmap data over the
     * shared plaza, which BSL and other packs can shade consistently.
     */
    private static final int ARENA_LIGHT_LEVEL = 15;
    private static final int ARENA_LIGHT_Y = TimelessDimensions.FLOOR_Y + 3;
    private static final BlockPos[] ARENA_LIGHTS = buildArenaLightGrid();

    /** Runtime union of every arena template plus permanent pedestal stations. */
    public record ArenaBounds(BlockPos origin, Vec3i size) {
        public boolean contains(BlockPos position) {
            return position.getX() >= origin.getX()
                    && position.getY() >= origin.getY()
                    && position.getZ() >= origin.getZ()
                    && position.getX() < origin.getX() + size.getX()
                    && position.getY() < origin.getY() + size.getY()
                    && position.getZ() < origin.getZ() + size.getZ();
        }
    }

    private UnknownFightManager() {
    }

    public static boolean enterFromOverworld(ServerPlayer player) {
        return startFight(player);
    }

    /**
     * Debug / hub review: floor + pedestals only (no fight, no dummy).
     */
    public static void visitHub(ServerPlayer player) {
        storeReturn(player);
        ServerLevel timeless = player.level().getServer().getLevel(TimelessDimensions.TIMELESS_VOID);
        if (timeless == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.echoes_show_the_past.unknown_dimension_missing"));
            return;
        }
        resetSession(player.level().getServer());
        discardUnknowns(timeless);
        prepareHub(timeless, true);
        teleport(player, timeless, TimelessDimensions.HUB_SPAWN);
        player.sendSystemMessage(Component.translatable(
                "message.echoes_show_the_past.unknown_hub_visit"));
    }

    /**
     * Debug / entity review: hub + a passive Unknown dummy.
     */
    public static boolean spawnDummy(ServerPlayer player) {
        visitHub(player);
        ServerLevel timeless = player.level().getServer().getLevel(TimelessDimensions.TIMELESS_VOID);
        if (timeless == null) {
            return false;
        }
        placeReviewDummy(player, timeless);
        return !timeless
                .getEntitiesOfClass(
                        UnknownEntity.class,
                        player.getBoundingBox().inflate(64.0D))
                .isEmpty();
    }

    private static void placeReviewDummy(ServerPlayer player, ServerLevel timeless) {
        discardUnknowns(timeless);
        BlockPos spawnPos = TimelessDimensions.HUB_SPAWN.offset(0, 0, 4);
        timeless.getChunkAt(spawnPos);
        UnknownEntity boss = createUnknown(timeless, spawnPos, 180.0F);
        if (boss == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.echoes_show_the_past.unknown_dummy_failed"));
            LOGGER.error("Failed to addFreshEntity Unknown dummy at {}", spawnPos);
            return;
        }
        boss.setDummy(true);
        boss.setPersistenceRequired();
        boss.setCustomName(Component.translatable("entity.echoes_show_the_past.unknown"));
        boss.setCustomNameVisible(true);
        boss.setGlowingTag(true);
        LOGGER.info(
                "Spawned Unknown dummy id={} at {},{},{}",
                boss.getId(),
                boss.getX(),
                boss.getY(),
                boss.getZ());
        player.sendSystemMessage(Component.translatable(
                "message.echoes_show_the_past.unknown_dummy_spawn",
                String.format("%.1f %.1f %.1f", boss.getX(), boss.getY(), boss.getZ())));
    }

    /** Bypass biome/monster spawn rules — EntityType.spawn often returns null in the_void. */
    private static UnknownEntity createUnknown(ServerLevel level, BlockPos pos, float yaw) {
        // Load + force the chunk so the boss enters the tracked entity map immediately.
        // Otherwise getEntity(UUID) stays null for a tick and reconcile ejects the player.
        level.getChunkAt(pos);
        level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(pos), 2);
        UnknownEntity entity = EchoesShowThePast.UNKNOWN.get().create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            entity = new UnknownEntity(EchoesShowThePast.UNKNOWN.get(), level);
        }
        double x = pos.getX() + 0.5D;
        double y = TimelessDimensions.FLOOR_Y + 1.0D;
        double z = pos.getZ() + 0.5D;
        entity.snapTo(x, y, z, yaw, 0.0F);
        entity.setYBodyRot(yaw);
        entity.setYHeadRot(yaw);
        entity.setPersistenceRequired();
        if (!level.addFreshEntity(entity)) {
            return null;
        }
        return entity;
    }

    private static void discardUnknowns(ServerLevel timeless) {
        ArenaBounds bounds = arenaBounds(timeless);
        BlockPos origin = bounds.origin();
        var box = new net.minecraft.world.phys.AABB(
                origin.getX() - 8,
                0,
                origin.getZ() - 8,
                origin.getX() + bounds.size().getX() + 8,
                128,
                origin.getZ() + bounds.size().getZ() + 8);
        for (var entity : timeless.getEntitiesOfClass(UnknownEntity.class, box)) {
            entity.discard();
        }
    }

    public static void exitToReturn(ServerPlayer player) {
        resetSession(player.level().getServer());
        player.setData(EchoesShowThePast.TIMELESS_DEATH_RETURN.get(), false);
        returnPlayer(player);
    }

    private static void returnPlayer(ServerPlayer player) {
        GlobalPos ret = player.getExistingDataOrNull(EchoesShowThePast.TIMELESS_RETURN.get());
        ServerLevel overworld = player.level().getServer().overworld();
        if (overworld == null) {
            return;
        }
        ServerLevel target = overworld;
        BlockPos pos = overworld.getRespawnData().pos();
        if (ret != null && !ret.dimension().equals(TimelessDimensions.TIMELESS_VOID)) {
            ServerLevel stored = player.level().getServer().getLevel(ret.dimension());
            if (stored != null) {
                target = stored;
                pos = ret.pos();
            }
        }
        // Never drop the player back onto the entry pad — that instantly re-opens the fight.
        teleport(player, target, safeLandingBesidePortal(target, pos));
        restoreOverworldPortal(player);
        player.setPortalCooldown();
    }

    /** Prefer a neighbouring non-portal tile when the stored return is the entry pad itself. */
    private static BlockPos safeLandingBesidePortal(ServerLevel level, BlockPos pos) {
        if (!isTimelessPortalCell(level, pos) && !isTimelessPortalCell(level, pos.below())) {
            return pos;
        }
        BlockPos[] candidates = {
            pos.north(),
            pos.south(),
            pos.east(),
            pos.west(),
            pos.north().east(),
            pos.north().west(),
            pos.south().east(),
            pos.south().west(),
            pos.offset(2, 0, 0),
            pos.offset(-2, 0, 0),
            pos.offset(0, 0, 2),
            pos.offset(0, 0, -2)
        };
        for (BlockPos candidate : candidates) {
            if (isTimelessPortalCell(level, candidate) || isTimelessPortalCell(level, candidate.below())) {
                continue;
            }
            BlockState ground = level.getBlockState(candidate.below());
            BlockState body = level.getBlockState(candidate);
            BlockState head = level.getBlockState(candidate.above());
            if (ground.isFaceSturdy(level, candidate.below(), net.minecraft.core.Direction.UP)
                    && body.getCollisionShape(level, candidate).isEmpty()
                    && head.getCollisionShape(level, candidate.above()).isEmpty()) {
                return candidate;
            }
        }
        return pos.north(2);
    }

    private static boolean isTimelessPortalCell(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(EchoesShowThePast.TIMELESS_PORTAL.get());
    }

    public static boolean startFight(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel timeless = server.getLevel(TimelessDimensions.TIMELESS_VOID);
        if (timeless == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.echoes_show_the_past.unknown_dimension_missing"));
            return false;
        }
        UnknownEncounterSavedData encounter = encounter(timeless);
        reconcileEncounter(server, encounter);
        if (encounter.isActive() && !encounter.owns(player.getUUID())) {
            player.sendSystemMessage(Component.translatable(
                    "message.echoes_show_the_past.unknown_fight_occupied"));
            return false;
        }
        captureOverworldPortal(player);
        player.setData(EchoesShowThePast.TIMELESS_DEATH_RETURN.get(), false);
        resetSession(server);
        discardUnknowns(timeless);
        prepareHub(timeless, false);
        teleportFacingAltar(player, timeless);

        UnknownEntity boss = createUnknown(
                timeless,
                TimelessDimensions.BOSS_SPAWN,
                0.0F);
        if (boss == null) {
            restoreOverworldPortal(player);
            player.sendSystemMessage(Component.translatable(
                    "message.echoes_show_the_past.unknown_dummy_failed"));
            return false;
        }
        boss.setDummy(false);
        boss.setEra(UnknownEntity.ERA_VOID);
        boss.setArmored(false);
        boss.setPersistenceRequired();
        boss.setCustomName(Component.translatable("entity.echoes_show_the_past.unknown"));
        boss.setCustomNameVisible(false);
        boss.setGlowingTag(false);
        boss.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        boss.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        boss.resetGreekCombat();
        Objects.requireNonNull(boss.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(BOSS_MAX_HEALTH);
        boss.setHealth(BOSS_MAX_HEALTH);

        // The normal encounter always follows the complete canonical chronology.
        encounter.begin(boss.getUUID(), player.getUUID(), UnknownEraSequence.ERA_COUNT);
        BOSS_TRACKING_GRACE.begin(boss.getUUID(), server.getTickCount());
        updateBossBar(encounter, boss, player);
        syncEnterCinematic(player, boss, encounter);
        return true;
    }

    public static void resetSession(MinecraftServer server) {
        ArenaReconstructionWave.cancel();
        BOSS_TRACKING_GRACE.clear();
        ServerLevel timeless = server.getLevel(TimelessDimensions.TIMELESS_VOID);
        if (timeless == null) {
            return;
        }
        UnknownEncounterSavedData encounter = encounter(timeless);
        UnknownMedievalVanguard.clear(timeless, encounter);
        UnknownMedievalRuinsArena.clear();
        clearMedievalRubbleProjectiles(timeless);
        ServerPlayer owner = owner(timeless, encounter);
        if (owner != null) {
            endEnterCinematic(owner);
            revokeCinematicFlight(owner, true);
            PacketDistributor.sendToPlayer(owner, UnknownBossBarPayload.inactive());
        }
        if (encounter.bossId() != null) {
            var entity = timeless.getEntity(encounter.bossId());
            if (entity != null) {
                entity.discard();
            }
        }
        encounter.reset();
        discardUnknowns(timeless);
        prepareHub(timeless, false);
    }

    public static boolean wantsPedestalMaterialize(UnknownEntity boss) {
        UnknownEncounterSavedData encounter = encounter(boss);
        return encounter != null
                && encounter.controls(boss.getUUID())
                && encounter.phase() == Phase.VOID_VULNERABLE
                && encounter.action() == Action.SEEKING_PEDESTAL
                && encounter.nextEraIndex() < UnknownEraSequence.ERA_COUNT;
    }

    /** True while a pedestal crest is rewriting the arena — freeze combat AI. */
    public static boolean isArenaLocked(UnknownEntity boss) {
        UnknownEncounterSavedData encounter = encounter(boss);
        return encounter != null
                && encounter.controls(boss.getUUID())
                && (encounter.phase() == Phase.RECONSTRUCTING
                        || encounter.phase() == Phase.CINEMATIC_WALK
                        || encounter.phase() == Phase.EXECUTION
                        || encounter.action() == Action.DEPOSITING_OFFERING
                        || encounter.eraStunTicks() > 0);
    }

    /** True only while this boss owns the live Greek combat session. */
    public static boolean isGreekCombatActive(UnknownEntity boss) {
        UnknownEncounterSavedData encounter = encounter(boss);
        return encounter != null
                && encounter.controls(boss.getUUID())
                && encounter.era() == Era.GREEK
                && encounter.action() == Action.COMBAT
                && (encounter.phase() == Phase.PAST || encounter.phase() == Phase.RUINS)
                && boss.getEra() == UnknownEntity.ERA_GREEK
                && !boss.isDummy()
                && !boss.isInvulnerable();
    }

    /** The encounter owner is the sole legal target for this single-player duel. */
    public static ServerPlayer greekCombatTarget(UnknownEntity boss) {
        if (!isGreekCombatActive(boss) || !(boss.level() instanceof ServerLevel level)) {
            return null;
        }
        ServerPlayer target = owner(level, encounter(level));
        return target != null
                        && target.isAlive()
                        && !target.isSpectator()
                        && target.level() == level
                ? target
                : null;
    }

    public static boolean isGreekRuins(UnknownEntity boss) {
        UnknownEncounterSavedData encounter = encounter(boss);
        return encounter != null
                && encounter.controls(boss.getUUID())
                && encounter.era() == Era.GREEK
                && encounter.phase() == Phase.RUINS;
    }

    public static boolean isEgyptianCombatActive(UnknownEntity boss) {
        UnknownEncounterSavedData encounter = encounter(boss);
        return encounter != null
                && encounter.controls(boss.getUUID())
                && encounter.era() == Era.EGYPTIAN
                && encounter.action() == Action.COMBAT
                && (encounter.phase() == Phase.PAST || encounter.phase() == Phase.RUINS)
                && boss.getEra() == UnknownEntity.ERA_EGYPTIAN
                && !boss.isDummy()
                && !boss.isInvulnerable();
    }

    public static ServerPlayer egyptianCombatTarget(UnknownEntity boss) {
        if (!isEgyptianCombatActive(boss) || !(boss.level() instanceof ServerLevel level)) {
            return null;
        }
        ServerPlayer target = owner(level, encounter(level));
        return target != null
                        && target.isAlive()
                        && !target.isSpectator()
                        && target.level() == level
                ? target
                : null;
    }

    public static boolean isEgyptianRuins(UnknownEntity boss) {
        UnknownEncounterSavedData encounter = encounter(boss);
        return encounter != null
                && encounter.controls(boss.getUUID())
                && encounter.era() == Era.EGYPTIAN
                && encounter.phase() == Phase.RUINS;
    }

    public static boolean isMedievalCombatActive(UnknownEntity boss) {
        UnknownEncounterSavedData encounter = encounter(boss);
        return encounter != null
                && encounter.controls(boss.getUUID())
                && encounter.era() == Era.MEDIEVAL
                && encounter.phase() == Phase.PAST
                && encounter.action() == Action.COMBAT
                && boss.getEra() == UnknownEntity.ERA_MEDIEVAL
                && !boss.isDummy()
                && !boss.isInvulnerable();
    }

    public static ServerPlayer medievalCombatTarget(UnknownEntity boss) {
        if (!isMedievalCombatActive(boss) || !(boss.level() instanceof ServerLevel level)) {
            return null;
        }
        ServerPlayer target = owner(level, encounter(level));
        return target != null
                        && target.isAlive()
                        && !target.isSpectator()
                        && target.level() == level
                ? target
                : null;
    }

    public static boolean isMedievalRuinsCombatActive(UnknownEntity boss) {
        UnknownEncounterSavedData encounter = encounter(boss);
        return encounter != null
                && encounter.controls(boss.getUUID())
                && encounter.era() == Era.MEDIEVAL
                && encounter.phase() == Phase.RUINS
                && encounter.action() == Action.COMBAT
                && boss.getEra() == UnknownEntity.ERA_MEDIEVAL
                && !boss.isDummy()
                && !boss.isInvulnerable();
    }

    public static ServerPlayer medievalRuinsCombatTarget(UnknownEntity boss) {
        if (!isMedievalRuinsCombatActive(boss)
                || !(boss.level() instanceof ServerLevel level)) {
            return null;
        }
        ServerPlayer target = owner(level, encounter(level));
        return target != null
                        && target.isAlive()
                        && !target.isSpectator()
                        && target.level() == level
                ? target
                : null;
    }

    public static boolean isMedievalRuinsTarget(
            UnknownEntity boss,
            ServerPlayer candidate) {
        return candidate != null && candidate == medievalRuinsCombatTarget(boss);
    }

    /** Prevents generic target goals from competing with any owner-bound controller. */
    public static boolean isScriptedCombatActive(UnknownEntity boss) {
        return isGreekCombatActive(boss)
                || isEgyptianCombatActive(boss)
                || isMedievalCombatActive(boss)
                || isMedievalRuinsCombatActive(boss);
    }

    public static ArenaBounds arenaBounds(ServerLevel level) {
        BlockPos canonicalOrigin = TimelessDimensions.ARENA_ORIGIN;
        int minimumWorldX = canonicalOrigin.getX();
        int minimumWorldY = canonicalOrigin.getY();
        int minimumWorldZ = canonicalOrigin.getZ();
        int maximumWorldX = canonicalOrigin.getX() + TimelessDimensions.ARENA_VOLUME.getX() - 1;
        int maximumWorldY = canonicalOrigin.getY() + TimelessDimensions.ARENA_VOLUME.getY() - 1;
        int maximumWorldZ = canonicalOrigin.getZ() + TimelessDimensions.ARENA_VOLUME.getZ() - 1;
        for (Identifier templateId : ARENA_TEMPLATES) {
            Optional<StructureTemplate> template = level.getStructureManager().get(templateId);
            if (template.isEmpty()) {
                continue;
            }
            BlockPos templateOrigin = arenaTemplateOrigin(templateId);
            Vec3i size = template.get().getSize();
            minimumWorldX = Math.min(minimumWorldX, templateOrigin.getX());
            minimumWorldY = Math.min(minimumWorldY, templateOrigin.getY());
            minimumWorldZ = Math.min(minimumWorldZ, templateOrigin.getZ());
            maximumWorldX = Math.max(maximumWorldX, templateOrigin.getX() + size.getX() - 1);
            maximumWorldY = Math.max(maximumWorldY, templateOrigin.getY() + size.getY() - 1);
            maximumWorldZ = Math.max(maximumWorldZ, templateOrigin.getZ() + size.getZ() - 1);
        }
        for (UnknownEraSequence era : UnknownEraSequence.ordered()) {
            BlockPos pedestal = era.pedestal();
            BlockPos approach = pedestalApproachFor(pedestal);
            minimumWorldX = Math.min(minimumWorldX, Math.min(pedestal.getX(), approach.getX()) - 1);
            minimumWorldZ = Math.min(minimumWorldZ, Math.min(pedestal.getZ(), approach.getZ()) - 1);
            maximumWorldX = Math.max(maximumWorldX, Math.max(pedestal.getX(), approach.getX()) + 1);
            maximumWorldZ = Math.max(maximumWorldZ, Math.max(pedestal.getZ(), approach.getZ()) + 1);
            minimumWorldY = Math.min(minimumWorldY, pedestal.getY() - 1);
            maximumWorldY = Math.max(maximumWorldY, pedestal.getY() + 2);
        }
        BlockPos origin = new BlockPos(minimumWorldX, minimumWorldY, minimumWorldZ);
        return new ArenaBounds(
                origin,
                new Vec3i(
                        maximumWorldX - minimumWorldX + 1,
                        maximumWorldY - minimumWorldY + 1,
                        maximumWorldZ - minimumWorldZ + 1));
    }

    public static boolean isInsideArenaVolume(ServerLevel level, BlockPos position) {
        return arenaBounds(level).contains(position);
    }

    /** World origin for a template; only Medieval retains deeper authored layers. */
    public static BlockPos arenaTemplateOrigin(Identifier templateId) {
        return templateId.equals(MEDIEVAL_PAST) || templateId.equals(MEDIEVAL_RUINS)
                ? TimelessDimensions.MEDIEVAL_ARENA_ORIGIN
                : TimelessDimensions.ARENA_ORIGIN;
    }

    /** Immediate cleanup for phase changes, death, logout and combat resets. */
    public static void clearGreekCombatArtifacts(UnknownEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        ArenaBounds bounds = arenaBounds(level);
        BlockPos origin = bounds.origin();
        Vec3i size = bounds.size();
        AABB cleanupVolume = arenaCleanupVolume(origin, size, 8.0D);
        level.getEntitiesOfClass(SpectralHopliteEntity.class, cleanupVolume)
                .forEach(Entity::discard);
        discardArenaItemDrops(level, cleanupVolume);
    }

    /** Ruins projectiles outlive their kick animation, but never a phase/session boundary. */
    public static void clearMedievalRubbleProjectiles(ServerLevel level) {
        ArenaBounds bounds = arenaBounds(level);
        BlockPos origin = bounds.origin();
        Vec3i size = bounds.size();
        AABB cleanupVolume = arenaCleanupVolume(origin, size, 8.0D);
        level.getEntitiesOfClass(MedievalRubbleProjectile.class, cleanupVolume)
                .forEach(Entity::discard);
    }

    /** Protection is derived from encounter state, so reset/death clears it immediately. */
    public static boolean isArenaProtected(ServerLevel level, BlockPos position) {
        if (!level.dimension().equals(TimelessDimensions.TIMELESS_VOID)
                || !isInsideArenaVolume(level, position)) {
            return false;
        }
        UnknownEncounterSavedData encounter = encounter(level);
        return encounter.isActive()
                && encounter.phase() != Phase.IDLE
                && encounter.phase() != Phase.DEAD;
    }

    public static BlockPos nextPedestalFor(UnknownEntity boss) {
        if (!wantsPedestalMaterialize(boss)) {
            return null;
        }
        return UnknownEraSequence.forIndex(
                        Objects.requireNonNull(encounter(boss)).nextEraIndex())
                .pedestal();
    }

    /** Walkable tile immediately inside the arena, facing a solid pedestal. */
    public static BlockPos pedestalApproachFor(BlockPos pedestal) {
        // Root is the north-east cell of the 2x2 altar. East is the plaza-side
        // approach and never overlaps one of its three companion cells.
        return pedestal.east();
    }

    /**
     * Chooses the shortest currently reachable side of the pedestal. This deliberately
     * runs from the boss' live position, so knockback never leaves it committed to a
     * stale route around the arena.
     */
    public static BlockPos bestPedestalApproach(UnknownEntity boss, BlockPos pedestal) {
        BlockPos authored = pedestalApproachFor(pedestal);
        BlockPos best = null;
        int bestNodes = Integer.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        BlockPos[] candidates = {
            authored,
            pedestal.north(),
            pedestal.south(),
            pedestal.west(),
            pedestal.east()
        };
        for (BlockPos candidate : candidates) {
            if (!isWalkablePedestalApproach(boss, candidate)) {
                continue;
            }
            Path path = boss.getNavigation().createPath(candidate, 0);
            if (path == null || !path.canReach()) {
                continue;
            }
            int nodes = path.getNodeCount();
            double distance = boss.distanceToSqr(
                    candidate.getX() + 0.5D,
                    candidate.getY(),
                    candidate.getZ() + 0.5D);
            if (nodes < bestNodes || (nodes == bestNodes && distance < bestDistance)) {
                best = candidate;
                bestNodes = nodes;
                bestDistance = distance;
            }
        }
        return best != null ? best : authored;
    }

    public static boolean repathToPedestal(UnknownEntity boss, BlockPos pedestal, double speed) {
        BlockPos approach = bestPedestalApproach(boss, pedestal);
        Path path = boss.getNavigation().createPath(approach, 0);
        return path != null && path.canReach() && boss.getNavigation().moveTo(path, speed);
    }

    /** Horizontal channel range is independent from navigator arrival tolerance and pedestal height. */
    public static boolean isWithinPedestalChannelRange(UnknownEntity boss, BlockPos pedestal) {
        Vec3 center = pedestalCenterFor(pedestal);
        double dx = boss.getX() - center.x;
        double dz = boss.getZ() - center.z;
        return dx * dx + dz * dz <= 2.25D * 2.25D
                && Math.abs(boss.getY() - pedestal.getY()) <= 2.0D;
    }

    public static Vec3 pedestalCenterFor(BlockPos pedestal) {
        return new Vec3(pedestal.getX(), pedestal.getY() + 0.5D, pedestal.getZ() + 1.0D);
    }

    private static boolean isWalkablePedestalApproach(UnknownEntity boss, BlockPos position) {
        return boss.level().getBlockState(position.below())
                        .isFaceSturdy(boss.level(), position.below(), net.minecraft.core.Direction.UP)
                && boss.level().getBlockState(position).getCollisionShape(boss.level(), position).isEmpty()
                && boss.level().getBlockState(position.above())
                        .getCollisionShape(boss.level(), position.above()).isEmpty();
    }

    public static void materializeAtPedestal(UnknownEntity boss, BlockPos pedestal) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        UnknownEncounterSavedData encounter = encounter(level);
        if (!encounter.controls(boss.getUUID())) {
            return;
        }
        if (ArenaReconstructionWave.isBusy() || encounter.phase() == Phase.RECONSTRUCTING) {
            return;
        }
        boss.setRitualOffering(false);
        int eraIndex = encounter.nextEraIndex();
        UnknownEraSequence era = UnknownEraSequence.forIndex(eraIndex);
        placePedestals(level);
        boss.setEra(era.entityEra());
        boss.setArmored(true);
        // Stone stays on the altar — boss never channels it from the hand.
        clearHeldItem(boss);
        boss.getNavigation().stop();
        boss.setTarget(null);
        encounter.setEra(Era.valueOf(era.name()));
        encounter.setState(Phase.RECONSTRUCTING, Action.RECONSTRUCTING);
        boss.setInvulnerable(true);
        BigEchoPedestalBlockEntity altar = altarEntity(level);
        if (altar != null) {
            altar.setOrbitEraIndex(eraIndex);
            altar.setLocked(true);
        }
        updateBossBar(encounter, boss, owner(level, encounter));

        Identifier past = era.pastTemplate();
        if (era == UnknownEraSequence.MEDIEVAL) {
            Optional<StructureTemplate> medieval = level.getStructureManager().get(past);
            if (medieval.isEmpty()) {
                abortMissingArenaTemplate(level, past);
                return;
            }
            UnknownMedievalVanguard.Validation validation = UnknownMedievalVanguard.validate(
                    medieval.get(), arenaTemplateOrigin(past));
            if (!validation.valid()) {
                abortInvalidArenaTemplate(level, past, validation.describe());
                return;
            }
            UnknownMedievalVanguard.clear(level, encounter);
        }
        java.util.ArrayList<ArenaReconstructionWave.PlannedBlock> blocks =
                new java.util.ArrayList<>();
        if (!ArenaReconstructionWave.collectTemplateDelta(
                level,
                past,
                arenaTemplateOrigin(past),
                pedestalFootprint(),
                blocks::add)) {
            abortMissingArenaTemplate(level, past);
            return;
        }
        beginEraCinematic(level, boss, true);
        ArenaReconstructionWave.start(
                level,
                pedestalCenterFor(pedestal),
                arenaWaveExtents(level, pedestal),
                blocks,
                false,
                () -> finishArenaMaterialize(level, boss, past, pedestal));
    }

    private static void finishArenaMaterialize(
            ServerLevel level,
            UnknownEntity boss,
            Identifier template,
            BlockPos pedestal) {
        UnknownEncounterSavedData encounter = encounter(level);
        if (!encounter.controls(boss.getUUID())) {
            return;
        }
        // The wave mutates states; vanilla placement restores authored barrels,
        // shelves and every other block-entity payload at the same coordinates.
        placeTemplate(level, template, TimelessDimensions.ARENA_ORIGIN);
        placePedestals(level);
        rescueArenaOccupants(level, boss, pedestal);
        finishEraMaterialize(level, boss);
        if (template.equals(MEDIEVAL_PAST)) {
            reconcileMedievalPastRedstone(level);
        }
    }

    private static void finishEraMaterialize(ServerLevel level, UnknownEntity boss) {
        UnknownEncounterSavedData encounter = encounter(level);
        if (!encounter.controls(boss.getUUID())) {
            return;
        }
        ensureArenaLighting(level);
        releaseEraPresentation(level, boss);
        equipEraWeapon(boss);
        boolean medieval = encounter.era() == Era.MEDIEVAL;
        encounter.setState(Phase.PAST, actionForMaterializedEra(encounter.era()));
        updateBossBar(encounter, boss, owner(level, encounter));
        if (medieval) {
            if (!UnknownMedievalVanguard.initialize(level, boss, encounter)) {
                abortInvalidArenaTemplate(level, MEDIEVAL_PAST, "runtime initialization failed");
                return;
            }
            ServerPlayer fightOwner = owner(level, encounter);
            boss.setInvulnerable(false);
            if (fightOwner != null) {
                boss.setTarget(fightOwner);
            }
        } else {
            boss.setInvulnerable(false);
        }
    }

    /** Combat weapon by era. Stone is only the pedestal channel prop. */
    public static void equipEraWeapon(UnknownEntity boss) {
        ItemStack weapon = switch (boss.getEra()) {
            case UnknownEntity.ERA_GREEK -> new ItemStack(EchoesShowThePast.DORY.get());
            case UnknownEntity.ERA_EGYPTIAN -> new ItemStack(EchoesShowThePast.KHOPESH.get());
            case UnknownEntity.ERA_MEDIEVAL ->
                    new ItemStack(EchoesShowThePast.UNKNOWN_MEDIEVAL_SWORD.get());
            default -> ItemStack.EMPTY;
        };
        boss.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        UnknownEncounterSavedData activeEncounter = encounter(boss);
        boolean shieldBreakInProgress = activeEncounter != null
                && activeEncounter.controls(boss.getUUID())
                && activeEncounter.action() == Action.SHIELD_BREAK;
        boolean medievalRuins = activeEncounter != null
                && activeEncounter.controls(boss.getUUID())
                && activeEncounter.phase() == Phase.RUINS;
        boss.setItemSlot(
                EquipmentSlot.OFFHAND,
                boss.getEra() == UnknownEntity.ERA_MEDIEVAL
                                && (!medievalRuins || shieldBreakInProgress)
                        ? new ItemStack(EchoesShowThePast.UNKNOWN_MEDIEVAL_SHIELD.get())
                        : ItemStack.EMPTY);
        equipEraArmor(boss);
    }

    private static void equipMedievalRuinsWeapon(UnknownEntity boss) {
        boss.setItemSlot(
                EquipmentSlot.MAINHAND,
                new ItemStack(EchoesShowThePast.UNKNOWN_MEDIEVAL_SWORD.get()));
        boss.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        equipEraArmor(boss);
    }

    private static void equipEraArmor(UnknownEntity boss) {
        boolean medieval = boss.getEra() == UnknownEntity.ERA_MEDIEVAL;
        boss.setItemSlot(
                EquipmentSlot.HEAD,
                medieval
                        ? new ItemStack(EchoesShowThePast.UNKNOWN_MEDIEVAL_HELMET.get())
                        : ItemStack.EMPTY);
        boss.setItemSlot(
                EquipmentSlot.CHEST,
                medieval
                        ? new ItemStack(EchoesShowThePast.UNKNOWN_MEDIEVAL_CHESTPLATE.get())
                        : ItemStack.EMPTY);
        boss.setItemSlot(
                EquipmentSlot.LEGS,
                medieval
                        ? new ItemStack(EchoesShowThePast.UNKNOWN_MEDIEVAL_LEGGINGS.get())
                        : ItemStack.EMPTY);
        boss.setItemSlot(
                EquipmentSlot.FEET,
                medieval
                        ? new ItemStack(EchoesShowThePast.UNKNOWN_MEDIEVAL_BOOTS.get())
                        : ItemStack.EMPTY);
    }

    public static void clearHeldItem(UnknownEntity boss) {
        boss.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        boss.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        boss.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        boss.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        boss.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        boss.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        boss.resetGreekCombat();
    }

    private static Action actionForMaterializedEra(Era era) {
        return era == Era.VOID ? Action.WAITING : Action.COMBAT;
    }

    public static void onBossDefeated(UnknownEntity boss) {
        UnknownEncounterSavedData encounter = encounter(boss);
        if (encounter == null || !encounter.controls(boss.getUUID())) {
            return;
        }
        encounter.setState(Phase.DEAD, Action.DEAD);
        boss.setNoGravity(false);
        boss.resetGreekCombat();
        encounter.setThresholdIndex(UnknownEraSequence.STAGE_COUNT);
        ServerPlayer owner = boss.level() instanceof ServerLevel level
                ? owner(level, encounter)
                : null;
        encounter.clearRuntimeBar();
        if (owner != null) {
            endEnterCinematic(owner);
            revokeCinematicFlight(owner, true);
            PacketDistributor.sendToPlayer(owner, UnknownBossBarPayload.inactive());
        }
        if (boss.level() instanceof ServerLevel level) {
            UnknownMedievalVanguard.clear(level, encounter);
            UnknownMedievalRuinsArena.clear();
            clearMedievalRubbleProjectiles(level);
            BigEchoPedestalBlockEntity altar = altarEntity(level);
            if (altar != null) {
                altar.clearAll();
            }
            placeExitPortal(level);
            clearGreekCombatArtifacts(boss);
        }
    }

    public static void grantStone(ServerPlayer player) {
        ServerLevel overworld = player.level().getServer().overworld();
        if (overworld == null) {
            return;
        }
        UnknownFightSavedData saved =
                overworld.getDataStorage().computeIfAbsent(UnknownFightSavedData.TYPE);
        if (saved.hasGrantedStone(player.getUUID())) {
            return;
        }
        ItemStack stone = new ItemStack(EchoesShowThePast.PHILOSOPHERS_STONE.get());
        long day = player.level().getOverworldClockTime() / 24_000L;
        stone.set(
                EchoesShowThePast.RELIC_STATE.get(),
                RelicState.EMPTY.ownedBy(player.getUUID(), 0, day));
        if (!player.addItem(stone)) {
            player.drop(stone, false);
        }
        saved.markStoneGranted(player.getUUID());
        UnknownAdvancements.awardDefeatAndStone(player);
        player.sendOverlayMessage(Component.translatable(
                "message.echoes_show_the_past.unknown_stone_granted"));
    }

    public static boolean placeTemplate(ServerLevel level, Identifier templateId, BlockPos origin) {
        // Authored templates may overwrite the altar cells; keep offerings for
        // the subsequent placePedestals restore.
        if (retainedAltarSnapshot == null) {
            retainedAltarSnapshot = captureAndSilenceAltar(level);
        }
        Optional<StructureTemplate> template = level.getStructureManager().get(templateId);
        if (template.isEmpty()) {
            return false;
        }
        BlockPos placementOrigin = origin.equals(TimelessDimensions.ARENA_ORIGIN)
                ? arenaTemplateOrigin(templateId)
                : origin;
        boolean medievalPast = templateId.equals(MEDIEVAL_PAST);
        boolean medievalTemplate = medievalPast || templateId.equals(MEDIEVAL_RUINS);
        if (medievalPast) {
            UnknownMedievalVanguard.Validation validation =
                    UnknownMedievalVanguard.validate(template.get(), placementOrigin);
            if (!validation.valid()) {
                LOGGER.error(
                        "Refusing incomplete Medieval Past authored template {}: {}",
                        templateId,
                        validation.describe());
                return false;
            }
        }
        if (templateId.equals(MEDIEVAL_RUINS)) {
            UnknownMedievalRuinsArena.Validation validation =
                    UnknownMedievalRuinsArena.validate(template.get(), placementOrigin);
            if (!validation.valid()) {
                LOGGER.error(
                        "Refusing incomplete Medieval Ruins authored template {}: {}",
                        templateId,
                        validation.describe());
                return false;
            }
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(!medievalTemplate);
        if (medievalTemplate) {
            settings.addProcessor(UnknownMedievalArenaProcessor.INSTANCE);
        }
        template.get().placeInWorld(
                level,
                placementOrigin,
                placementOrigin,
                settings,
                RandomSource.create(level.getSeed() ^ templateId.hashCode()),
                ARENA_MUTATION_FLAGS);
        return true;
    }

    public static void clearArena(ServerLevel level, BlockPos origin, Vec3i size) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        // Temporary stand-in. Next art pass: invisible/barrier floor + void feel (no blackstone).
        BlockState floor = Blocks.BLACKSTONE.defaultBlockState();
        for (int x = 0; x < size.getX(); x++) {
            for (int z = 0; z < size.getZ(); z++) {
                for (int y = 0; y < size.getY(); y++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    clearArenaBlock(level, cursor, air);
                }
                cursor.set(origin.getX() + x, TimelessDimensions.FLOOR_Y, origin.getZ() + z);
                clearArenaBlock(level, cursor, floor);
            }
        }
        discardArenaItemDrops(level, arenaCleanupVolume(origin, size, 1.0D));
    }

    private static void clearArena(ServerLevel level) {
        // Silence inventory before the volume wipe destroys the ORIGIN BE.
        retainedAltarSnapshot = captureAndSilenceAltar(level);
        clearLegacyArenaResidue(level);
        ArenaBounds bounds = arenaBounds(level);
        clearArena(level, bounds.origin(), bounds.size());
    }

    private static void clearArenaBlock(ServerLevel level, BlockPos pos, BlockState state) {
        // Removing the BE first matches ArenaReconstructionWave and stops chest /
        // pot / altar sockets from scattering before SUPPRESS_DROPS can apply.
        if (level.getBlockEntity(pos) != null) {
            level.removeBlockEntity(pos);
        }
        level.setBlock(pos, state, ARENA_MUTATION_FLAGS);
    }

    private static AABB arenaCleanupVolume(BlockPos origin, Vec3i size, double padding) {
        return new AABB(
                origin.getX() - padding,
                origin.getY() - padding,
                origin.getZ() - padding,
                origin.getX() + size.getX() + padding,
                origin.getY() + size.getY() + padding,
                origin.getZ() + size.getZ() + padding);
    }

    /** Timeless void combat debris must never litter the floor after a wipe. */
    public static void discardArenaItemDrops(ServerLevel level, AABB volume) {
        level.getEntitiesOfClass(ItemEntity.class, volume).forEach(ItemEntity::discard);
    }

    /** Held across clearArena → placePedestals so fight offerings survive rebuilds. */
    private static AltarSnapshot retainedAltarSnapshot;

    private static void placePedestals(ServerLevel level) {
        removeLegacyPedestals(level);
        AltarSnapshot snapshot = retainedAltarSnapshot != null
                ? retainedAltarSnapshot
                : captureAndSilenceAltar(level);
        retainedAltarSnapshot = null;
        BlockState pedestal = EchoesShowThePast.BIG_ECHO_PEDESTAL.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos origin = TimelessDimensions.BOSS_PEDESTAL_ORIGIN;
        for (BigEchoPedestalBlock.Part part : BigEchoPedestalBlock.Part.values()) {
            BlockPos pos = part.positionFrom(origin);
            clearArenaBlock(level, pos.above(), air);
            clearArenaBlock(level, pos.above(2), air);
            clearArenaBlock(level, pos, pedestal.setValue(BigEchoPedestalBlock.PART, part));
        }
        BlockPos approach = pedestalApproachFor(origin);
        clearArenaBlock(level, approach, air);
        clearArenaBlock(level, approach.above(), air);
        restoreAltar(level, snapshot);
    }

    private record AltarSnapshot(
            ItemStack[] fragments,
            ItemStack stone,
            boolean locked,
            int orbitEraIndex) {
    }

    private static AltarSnapshot captureAndSilenceAltar(ServerLevel level) {
        AltarSnapshot snapshot = captureAltar(level);
        BigEchoPedestalBlockEntity altar = altarEntity(level);
        if (altar != null) {
            altar.discardContentsSilently();
        }
        return snapshot;
    }

    private static AltarSnapshot captureAltar(ServerLevel level) {
        BigEchoPedestalBlockEntity altar = altarEntity(level);
        if (altar == null) {
            return null;
        }
        ItemStack[] copies = new ItemStack[BigEchoPedestalBlockEntity.FRAGMENT_SLOTS];
        for (int i = 0; i < copies.length; i++) {
            copies[i] = altar.fragment(i).copy();
        }
        return new AltarSnapshot(
                copies,
                altar.stone().copy(),
                altar.isLocked(),
                altar.orbitEraIndex());
    }

    private static void restoreAltar(ServerLevel level, AltarSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        BigEchoPedestalBlockEntity altar = altarEntity(level);
        if (altar == null) {
            return;
        }
        for (int i = 0; i < snapshot.fragments().length; i++) {
            altar.forceSetFragment(i, snapshot.fragments()[i]);
        }
        altar.forceSetStone(snapshot.stone());
        altar.setLocked(snapshot.locked());
        altar.setOrbitEraIndex(snapshot.orbitEraIndex());
    }

    @SubscribeEvent
    public static void onBossDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof UnknownEntity boss)) {
            return;
        }
        UnknownEncounterSavedData encounter = encounter(boss);
        if (encounter == null || !encounter.controls(boss.getUUID())) {
            return;
        }
        updateBossBar(encounter, boss, owner((ServerLevel) boss.level(), encounter));
        if (encounter.phase() != Phase.PAST && encounter.phase() != Phase.RUINS) {
            return;
        }
        int threshold = encounter.thresholdIndex();
        if (threshold < UnknownEraSequence.STAGE_COUNT
                && boss.getHealth() <= healthFloorForThreshold(threshold) + 0.001F) {
            encounter.setThresholdIndex(threshold + 1);
            applyThreshold(boss, encounter, threshold + 1);
        }
    }

    @SubscribeEvent
    public static void onBossDamageIncoming(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel level) {
            UnknownEncounterSavedData current = encounter(level);
        }
        Entity attackingEntity = event.getSource().getEntity();
        Entity directEntity = event.getSource().getDirectEntity();
        if (UnknownMedievalVanguard.isVanguard(attackingEntity)
                || UnknownMedievalVanguard.isVanguard(directEntity)) {
            if (!(event.getEntity().level() instanceof ServerLevel level)
                    || !isLegalVanguardDamageTarget(
                            event.getEntity().getUUID(),
                            encounter(level).ownerId())) {
                event.setCanceled(true);
            }
            return;
        }
        if (!(event.getEntity() instanceof UnknownEntity boss)) {
            return;
        }
        UnknownEncounterSavedData encounter = encounter(boss);
        if (encounter == null || !encounter.controls(boss.getUUID())) {
            return;
        }
        if (tryBlockWithMedievalShield(event, boss, encounter)) {
            return;
        }
        tryBlockWithGreekAspis(event, boss, encounter);
    }

    /** Shared by the event hook and regression tests; null never grants damage. */
    public static boolean isLegalVanguardDamageTarget(UUID targetId, UUID ownerId) {
        return targetId != null && targetId.equals(ownerId);
    }

    @SubscribeEvent
    public static void onMedievalVanguardDrops(LivingDropsEvent event) {
        if (UnknownMedievalVanguard.isVanguard(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMedievalVanguardExperience(LivingExperienceDropEvent event) {
        if (UnknownMedievalVanguard.isVanguard(event.getEntity())) {
            event.setDroppedExperience(0);
        }
    }

    @SubscribeEvent
    public static void onBossDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof UnknownEntity boss)) {
            return;
        }
        UnknownEncounterSavedData encounter = encounter(boss);
        if (encounter == null || !encounter.controls(boss.getUUID())) {
            return;
        }
        if (encounter.phase() == Phase.RECONSTRUCTING
                || encounter.phase() == Phase.CINEMATIC_WALK
                || (encounter.phase() == Phase.EXECUTION
                        && !encounter.executionResolved())
                || isReviewEndpoint(encounter)) {
            event.setNewDamage(0.0F);
            return;
        }
        event.setNewDamage(clampDamageToCurrentGate(
                boss.getHealth(),
                event.getNewDamage(),
                encounter.thresholdIndex(),
                encounter.phase() == Phase.VOID_VULNERABLE));
    }

    private static boolean isReviewEndpoint(UnknownEncounterSavedData encounter) {
        return encounter.reviewEraCount() < UnknownEraSequence.ERA_COUNT
                && encounter.phase() == Phase.VOID_VULNERABLE
                && encounter.action() == Action.WAITING
                && encounter.thresholdIndex() >= encounter.reviewEraCount() * 2;
    }

    private static boolean tryBlockWithMedievalShield(
            LivingIncomingDamageEvent event,
            UnknownEntity boss,
            UnknownEncounterSavedData encounter) {
        if (!(boss.level() instanceof ServerLevel level)
                || encounter.era() != Era.MEDIEVAL
                || encounter.phase() != Phase.PAST
                || event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        boolean activeGuard = encounter.action() == Action.COMBAT
                && boss.isMedievalGuardActive(level.getGameTime());
        if (!activeGuard) {
            return false;
        }

        Entity directThreat = event.getSource().getDirectEntity();
        Entity attacker = event.getSource().getEntity();
        Entity threat = directThreat != null ? directThreat : attacker;
        if (threat == null || threat == boss) {
            return false;
        }
        Vec3 forward = boss.getLockedCombatDirection();
        if (forward.lengthSqr() <= 1.0E-6D) {
            forward = boss.getLookAngle();
        }
        Vec3 toThreat = threat instanceof Projectile projectile
                        && projectile.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6D
                ? projectile.getDeltaMovement().reverse()
                : threat.position().subtract(boss.position());
        if (activeGuard
                && !UnknownGreekCombatMath.isInsideFrontArc(
                        forward,
                        toThreat,
                        UnknownMedievalCombatGoal.GUARD_ARC_DEGREES)) {
            return false;
        }

        event.setCanceled(true);
        Vec3 shieldForward = UnknownGreekCombatMath.horizontalDirection(
                Vec3.ZERO,
                forward,
                boss.getLookAngle());
        Vec3 shieldSide = new Vec3(-shieldForward.z, 0.0D, shieldForward.x);
        Vec3 impactDirection = UnknownGreekCombatMath.horizontalDirection(
                Vec3.ZERO,
                toThreat,
                shieldForward);
        double lateralImpact = Math.clamp(
                impactDirection.dot(shieldSide) * 0.38D,
                -0.38D,
                0.38D);
        Vec3 impact = boss.position()
                .add(shieldForward.scale(0.52D))
                .add(shieldSide.scale(lateralImpact))
                .add(0.0D, boss.getBbHeight() * 0.60D, 0.0D);
        playMedievalShieldFeedback(level, boss, threat, shieldForward, impact);
        boss.showCombatFx(
                UnknownEntity.COMBAT_FX_MEDIEVAL_BLOCK,
                impact,
                level.getGameTime());

        boolean projectile = directThreat instanceof Projectile
                || event.getSource().is(DamageTypeTags.IS_PROJECTILE);
        if (attacker instanceof ServerPlayer player
                && UnknownMedievalCombatGoal.shouldTriggerRiposte(
                        activeGuard,
                        projectile,
                        player.getUUID().equals(encounter.ownerId()),
                        player.distanceTo(boss))) {
            UnknownMedievalCombatGoal.beginRiposte(boss, level, player);
        }
        return true;
    }

    private static void playMedievalShieldFeedback(
            ServerLevel level,
            UnknownEntity boss,
            Entity threat,
            Vec3 shieldForward,
            Vec3 impact) {
        BlockPos soundPos = BlockPos.containing(impact);
        level.playSound(
                null,
                soundPos,
                SoundEvents.SHIELD_BLOCK.value(),
                SoundSource.HOSTILE,
                1.2F,
                0.88F + level.getRandom().nextFloat() * 0.08F);
        level.playSound(
                null,
                soundPos,
                SoundEvents.ANVIL_HIT,
                SoundSource.HOSTILE,
                0.16F,
                1.38F);
        level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                impact.x,
                impact.y,
                impact.z,
                5,
                0.12D,
                0.15D,
                0.12D,
                0.08D);
        if (threat instanceof Projectile projectile) {
            projectile.setDeltaMovement(
                    projectile.getDeltaMovement().scale(-0.16D).add(0.0D, 0.04D, 0.0D));
            return;
        }
        Vec3 recoil = UnknownGreekCombatMath.horizontalDirection(
                boss.position(),
                threat.position(),
                shieldForward);
        threat.push(recoil.x * 0.22D, 0.04D, recoil.z * 0.22D);
    }

    private static boolean tryBlockWithGreekAspis(
            LivingIncomingDamageEvent event,
            UnknownEntity boss,
            UnknownEncounterSavedData encounter) {
        if (!(boss.level() instanceof ServerLevel level)
                || encounter.era() != Era.GREEK
                || encounter.action() != Action.COMBAT
                || (encounter.phase() != Phase.PAST && encounter.phase() != Phase.RUINS)
                || event.getSource().is(DamageTypeTags.IS_EXPLOSION)
                || !boss.isGreekGuardActive(level.getGameTime())) {
            return false;
        }
        Entity threat = event.getSource().getDirectEntity();
        if (threat == null) {
            threat = event.getSource().getEntity();
        }
        if (threat == null || threat == boss) {
            return false;
        }
        Vec3 forward = boss.getLockedCombatDirection();
        if (forward.lengthSqr() <= 1.0E-6D) {
            forward = boss.getLookAngle();
        }
        Vec3 toThreat = threat instanceof Projectile projectile
                        && projectile.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6D
                ? projectile.getDeltaMovement().reverse()
                : threat.position().subtract(boss.position());
        if (!UnknownGreekCombatMath.isInsideFrontArc(
                forward,
                toThreat,
                UnknownGreekCombatGoal.GUARD_ARC_DEGREES)) {
            return false;
        }

        // Cancel before vanilla assigns hurtTime or broadcasts a red damage flash.
        event.setCanceled(true);
        Vec3 shieldForward = UnknownGreekCombatMath.horizontalDirection(
                Vec3.ZERO,
                forward,
                boss.getLookAngle());
        Vec3 shieldSide = new Vec3(-shieldForward.z, 0.0D, shieldForward.x);
        Vec3 impactDirection = UnknownGreekCombatMath.horizontalDirection(
                Vec3.ZERO,
                toThreat,
                shieldForward);
        double lateralImpact = Math.clamp(
                impactDirection.dot(shieldSide) * 0.42D,
                -0.42D,
                0.42D);
        Vec3 impact = boss.position()
                .add(shieldForward.scale(0.58D))
                .add(shieldSide.scale(lateralImpact))
                .add(0.0D, boss.getBbHeight() * 0.62D, 0.0D);
        playGreekAspisBlockFeedback(level, boss, threat, shieldForward, impact);
        boss.showCombatFx(
                UnknownEntity.COMBAT_FX_ASPIS_BLOCK,
                impact,
                level.getGameTime());
        return true;
    }

    private static void playGreekAspisBlockFeedback(
            ServerLevel level,
            UnknownEntity boss,
            Entity threat,
            Vec3 shieldForward,
            Vec3 impact) {
        BlockPos soundPos = BlockPos.containing(impact);
        level.playSound(
                null,
                soundPos,
                SoundEvents.SHIELD_BLOCK.value(),
                SoundSource.HOSTILE,
                1.35F,
                0.74F + level.getRandom().nextFloat() * 0.10F);
        level.playSound(
                null,
                soundPos,
                SoundEvents.ANVIL_HIT,
                SoundSource.HOSTILE,
                0.22F,
                1.65F + level.getRandom().nextFloat() * 0.12F);
        level.sendParticles(
                ASPIS_GOLD_DUST,
                impact.x,
                impact.y,
                impact.z,
                ASPIS_BLOCK_GOLD_SPARKS,
                0.14D,
                0.18D,
                0.14D,
                0.10D);
        level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                impact.x,
                impact.y,
                impact.z,
                ASPIS_BLOCK_WHITE_SPARKS,
                0.10D,
                0.14D,
                0.10D,
                0.12D);

        if (threat instanceof Projectile projectile) {
            Vec3 velocity = projectile.getDeltaMovement();
            projectile.setDeltaMovement(velocity.scale(-0.18D).add(0.0D, 0.05D, 0.0D));
            return;
        }
        Vec3 recoil = UnknownGreekCombatMath.horizontalDirection(
                boss.position(),
                threat.position(),
                shieldForward);
        threat.push(
                recoil.x * ASPIS_BLOCK_RECOIL,
                0.06D,
                recoil.z * ASPIS_BLOCK_RECOIL);
    }

    @SubscribeEvent
    public static void onArenaRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getItemStack().getItem() instanceof BlockItem)
                && !(event.getItemStack().getItem() instanceof BucketItem)) {
            return;
        }
        BlockPos adjacent = event.getPos().relative(event.getFace());
        if (isArenaProtected(level, event.getPos())
                || isArenaProtected(level, adjacent)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onArenaLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level
                && isArenaProtected(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onArenaBreak(BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && isArenaProtected(level, event.getPos())) {
            event.setNotifyClient(true);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onArenaPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && isArenaProtected(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onArenaFluid(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && (isArenaProtected(level, event.getPos())
                        || isArenaProtected(level, event.getLiquidPos()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onArenaPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        var resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return;
        }
        Direction pushDirection = resolver.getPushDirection();
        boolean touchesProtectedArena = isArenaProtected(level, event.getPos())
                || isArenaProtected(level, event.getFaceOffsetPos())
                || resolver.getToPush().stream().anyMatch(pos ->
                        isArenaProtected(level, pos)
                                || isArenaProtected(level, pos.relative(pushDirection)))
                || resolver.getToDestroy().stream().anyMatch(pos ->
                        isArenaProtected(level, pos));
        if (!touchesProtectedArena) {
            return;
        }

        UnknownEncounterSavedData encounter = encounter(level);
        boolean medievalPast = encounter.isActive()
                && encounter.era() == Era.MEDIEVAL
                && encounter.phase() == Phase.PAST;
        if (!medievalPast || !isContainedArenaPistonMovement(
                arenaBounds(level),
                pedestalFootprint(),
                event.getPos(),
                event.getFaceOffsetPos(),
                pushDirection,
                resolver.getToPush(),
                resolver.getToDestroy())) {
            event.setCanceled(true);
        }
    }

    /**
     * Keeps an authored piston entirely inside the disposable arena and away
     * from the persistent altar. The destination of every pushed block is
     * checked too, closing the boundary case where the source is protected but
     * the piston would move it outside the resettable volume.
     */
    public static boolean isContainedArenaPistonMovement(
            ArenaBounds bounds,
            Set<BlockPos> immutablePositions,
            BlockPos piston,
            BlockPos pistonFace,
            Direction pushDirection,
            List<BlockPos> toPush,
            List<BlockPos> toDestroy) {
        if (!isMovableArenaCell(bounds, immutablePositions, piston)
                || !isMovableArenaCell(bounds, immutablePositions, pistonFace)) {
            return false;
        }
        for (BlockPos position : toPush) {
            if (!isMovableArenaCell(bounds, immutablePositions, position)
                    || !isMovableArenaCell(
                            bounds,
                            immutablePositions,
                            position.relative(pushDirection))) {
                return false;
            }
        }
        for (BlockPos position : toDestroy) {
            if (!isMovableArenaCell(bounds, immutablePositions, position)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isMovableArenaCell(
            ArenaBounds bounds,
            Set<BlockPos> immutablePositions,
            BlockPos position) {
        return bounds.contains(position) && !immutablePositions.contains(position);
    }

    /**
     * Replays one neighbour change directly on authored circuit blocks after
     * passive structure placement. This wakes wires, diodes, torches and
     * pistons without applying shape/physics updates to the complete castle.
     * Observers deliberately wait for the first real world change, avoiding a
     * synthetic pulse merely because the arena appeared.
     */
    public static int reconcileRedstonePositions(
            ServerLevel level,
            Iterable<BlockPos> positions) {
        int reconciled = 0;
        for (BlockPos position : positions) {
            BlockState state = level.getBlockState(position);
            if (!isMedievalRedstoneComponent(state) || state.is(Blocks.OBSERVER)) {
                continue;
            }
            level.neighborChanged(
                    state,
                    position,
                    state.getBlock(),
                    null,
                    false);
            reconciled++;
        }
        return reconciled;
    }

    private static void reconcileMedievalPastRedstone(ServerLevel level) {
        UnknownEncounterSavedData current = encounter(level);
        if (!current.isActive()
                || current.era() != Era.MEDIEVAL
                || current.phase() != Phase.PAST) {
            return;
        }
        Optional<StructureTemplate> loaded = level.getStructureManager().get(MEDIEVAL_PAST);
        if (loaded.isEmpty()) {
            return;
        }
        List<StructureTemplate.Palette> palettes =
                ((StructureTemplateAccessor) (Object) loaded.get()).echoes$getPalettes();
        if (palettes.isEmpty()) {
            return;
        }
        BlockPos origin = arenaTemplateOrigin(MEDIEVAL_PAST);
        List<BlockPos> circuit = palettes.getFirst().blocks().stream()
                .filter(info -> isMedievalRedstoneComponent(info.state()))
                .map(info -> origin.offset(info.pos()))
                .toList();
        reconcileRedstonePositions(level, circuit);
    }

    private static boolean isMedievalRedstoneComponent(BlockState state) {
        Block block = state.getBlock();
        return block instanceof PistonBaseBlock
                || block == Blocks.REDSTONE_WIRE
                || block == Blocks.REDSTONE_TORCH
                || block == Blocks.REDSTONE_WALL_TORCH
                || block == Blocks.REPEATER
                || block == Blocks.COMPARATOR
                || block == Blocks.OBSERVER
                || block == Blocks.TRIPWIRE
                || block == Blocks.TRIPWIRE_HOOK
                || block == Blocks.DISPENSER
                || block == Blocks.DROPPER
                || block == Blocks.HOPPER
                || state.isSignalSource();
    }

    @SubscribeEvent
    public static void onArenaExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().removeIf(pos -> isArenaProtected(level, pos));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel timeless = server.getLevel(TimelessDimensions.TIMELESS_VOID);
        if (timeless == null) {
            return;
        }
        UnknownEncounterSavedData encounter = encounter(timeless);
        if (!encounter.isActive()) {
            return;
        }
        if (timeless.getGameTime() % 20L == 0L) {
            ensureArenaLighting(timeless);
        }
        if (!reconcileEncounter(server, encounter)) {
            return;
        }
        UnknownEntity boss = findEncounterBoss(timeless, encounter);
        if (boss == null) {
            return;
        }
        ServerPlayer owner = owner(timeless, encounter);
        updateBossBar(encounter, boss, owner);
        UnknownMedievalVanguard.tick(timeless, boss, encounter, owner);
        if (owner != null) {
            if (encounter.phase() == Phase.CINEMATIC_WALK || wantsEraLens(encounter)) {
                holdCinematicAudience(owner);
            } else {
                revokeCinematicFlight(owner, false);
            }
        }

        if (encounter.eraStunTicks() > 0) {
            boss.setInvulnerable(true);
            boss.getNavigation().stop();
            boss.setTarget(null);
            gazeAtAltar(boss, true);
            keepEraCinematicAlive(owner, boss, encounter);
            if (encounter.tickEraStun() == 0) {
                encounter.setNextEraIndex(encounter.nextEraIndex());
                materializeAtPedestal(boss, TimelessDimensions.BOSS_PEDESTAL_ORIGIN);
            }
            return;
        }

        if (encounter.action() == Action.DEPOSITING_OFFERING) {
            tickDepositOffering(timeless, boss, encounter);
            return;
        }

        if (encounter.phase() == Phase.RECONSTRUCTING) {
            gazeAtAltar(boss, true);
            keepEraCinematicAlive(owner, boss, encounter);
            return;
        }

        if (encounter.action() == Action.SHIELD_BREAK) {
            tickMedievalShieldBreak(timeless, boss, encounter, owner);
            return;
        }

        if (encounter.action() == Action.EXECUTION) {
            tickVoidExecution(timeless, boss, encounter, owner);
            return;
        }

        if (encounter.phase() != Phase.CINEMATIC_WALK) {
            return;
        }
        int cinematicTicks = encounter.advanceCinematicTick();
        if (cinematicTicks == 1 || cinematicTicks % 20 == 0) {
            syncEnterCinematic(owner, boss, encounter);
        }
        gazeAtAltar(boss, false);
        BlockPos pedestal = UnknownEraSequence.forIndex(0).pedestal();
        if ((cinematicTicks == 1 || cinematicTicks % 10 == 0 || boss.getNavigation().isDone())
                && !repathToPedestal(boss, pedestal, 0.95D)
                && (cinematicTicks == 1 || cinematicTicks % 40 == 0)) {
            LOGGER.warn(
                    "Unknown could not path to Greek pedestal from {}",
                    boss.blockPosition());
        }
        if (isWithinPedestalChannelRange(boss, pedestal)) {
            beginDepositOffering(timeless, boss, encounter);
        }
    }

    private static void beginDepositOffering(
            ServerLevel level,
            UnknownEntity boss,
            UnknownEncounterSavedData encounter) {
        encounter.setState(Phase.CINEMATIC_WALK, Action.DEPOSITING_OFFERING);
        encounter.setDepositStep(0);
        encounter.resetCinematicTicks();
        boss.getNavigation().stop();
        boss.setInvulnerable(true);
        BigEchoPedestalBlockEntity altar = altarEntity(level);
        if (altar != null) {
            altar.clearAll();
            altar.setLocked(false);
        }
        // Hold the first fragment so the player sees each offering before it seats.
        boss.setItemSlot(
                EquipmentSlot.MAINHAND,
                BigEchoPedestalBlockEntity.createFightFragment(level, 0));
        triggerRitualOffer(boss);
        gazeAtAltar(boss, true);
        updateBossBar(encounter, boss, owner(level, encounter));
        syncEnterCinematic(owner(level, encounter), boss, encounter);
    }

    private static void tickDepositOffering(
            ServerLevel level,
            UnknownEntity boss,
            UnknownEncounterSavedData encounter) {
        gazeAtAltar(boss, true);
        boss.getNavigation().stop();
        boss.setDeltaMovement(Vec3.ZERO);
        int ticks = encounter.advanceCinematicTick();
        int cycle = Math.floorMod(ticks, RITUAL_OFFER_CYCLE_TICKS);
        if (cycle == 1) {
            triggerRitualOffer(boss);
        }
        if (cycle != RITUAL_OFFER_PLACE_TICK) {
            return;
        }
        BigEchoPedestalBlockEntity altar = altarEntity(level);
        if (altar == null) {
            placePedestals(level);
            altar = altarEntity(level);
            if (altar == null) {
                return;
            }
        }
        int step = encounter.depositStep();
        BlockPos origin = TimelessDimensions.BOSS_PEDESTAL_ORIGIN;
        if (step >= 0 && step < UnknownEraSequence.STAGE_COUNT) {
            ItemStack fragment = BigEchoPedestalBlockEntity.createFightFragment(level, step);
            altar.forceSetFragment(step, fragment);
            level.playSound(
                    null,
                    origin,
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS,
                    0.95F,
                    0.85F + step * 0.06F);
            int next = step + 1;
            encounter.setDepositStep(next);
            if (next < UnknownEraSequence.STAGE_COUNT) {
                boss.setItemSlot(
                        EquipmentSlot.MAINHAND,
                        BigEchoPedestalBlockEntity.createFightFragment(level, next));
            } else {
                boss.setItemSlot(
                        EquipmentSlot.MAINHAND,
                        new ItemStack(EchoesShowThePast.PHILOSOPHERS_STONE.get()));
            }
            syncEnterCinematic(owner(level, encounter), boss, encounter);
            return;
        }
        if (step == 6) {
            altar.forceSetStone(new ItemStack(EchoesShowThePast.PHILOSOPHERS_STONE.get()));
            altar.setLocked(true);
            altar.setOrbitEraIndex(0);
            level.playSound(
                    null,
                    origin,
                    SoundEvents.BEACON_ACTIVATE,
                    SoundSource.BLOCKS,
                    0.85F,
                    1.1F);
            encounter.setDepositStep(7);
            clearHeldItem(boss);
            syncEnterCinematic(owner(level, encounter), boss, encounter);
            return;
        }
        if (step >= 7) {
            encounter.setNextEraIndex(0);
            encounter.setDepositStep(-1);
            boss.setRitualOffering(false);
            materializeAtPedestal(boss, TimelessDimensions.BOSS_PEDESTAL_ORIGIN);
        }
    }

    public static void gazeAtAltar(UnknownEntity boss, boolean lockBody) {
        Vec3 altar = UnknownEnterCinematicMath.altarFocus(TimelessDimensions.BOSS_PEDESTAL_ORIGIN);
        Vec3 from = new Vec3(boss.getX(), boss.getY() + boss.getEyeHeight(), boss.getZ());
        boss.getLookControl().setLookAt(altar.x, altar.y, altar.z, 120.0F, 85.0F);
        if (!lockBody) {
            return;
        }
        float yaw = UnknownEnterCinematicMath.yawToward(from, altar);
        float pitch = UnknownEnterCinematicMath.pitchToward(from, altar);
        boss.setYRot(yaw);
        boss.setYHeadRot(yaw);
        boss.setYBodyRot(yaw);
        boss.setXRot(Math.clamp(pitch, -45.0F, 45.0F));
    }

    private static void triggerRitualOffer(UnknownEntity boss) {
        boss.setRitualOffering(true);
        boss.triggerRitualAnimation("offer");
    }

    private static void triggerRitualChannel(UnknownEntity boss) {
        boss.setRitualChanneling(true);
        boss.triggerRitualAnimation("channel");
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.level().dimension().equals(TimelessDimensions.TIMELESS_VOID)) {
            return;
        }
        player.setData(EchoesShowThePast.TIMELESS_DEATH_RETURN.get(), true);
        resetSession(player.level().getServer());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.getData(EchoesShowThePast.TIMELESS_DEATH_RETURN.get())) {
            player.setData(EchoesShowThePast.TIMELESS_DEATH_RETURN.get(), false);
            returnPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel timeless = player.level().getServer().getLevel(TimelessDimensions.TIMELESS_VOID);
        if (timeless != null && encounter(timeless).owns(player.getUUID())) {
            restoreOverworldPortal(player);
            resetSession(player.level().getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Only reset when leaving the void. Entering used to race with startFight when
        // player.level() still lagged behind getTo().
        if (!event.getFrom().equals(TimelessDimensions.TIMELESS_VOID)
                || event.getTo().equals(TimelessDimensions.TIMELESS_VOID)) {
            return;
        }
        ServerLevel timeless = player.level().getServer().getLevel(TimelessDimensions.TIMELESS_VOID);
        if (timeless != null && encounter(timeless).owns(player.getUUID())) {
            resetSession(player.level().getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel timeless = event.getServer().getLevel(TimelessDimensions.TIMELESS_VOID);
        if (timeless == null) {
            return;
        }
        UnknownEncounterSavedData encounter = encounter(timeless);
        if (encounter.isActive()) {
            LOGGER.warn("Resetting an unfinished Unknown encounter recovered from disk");
            resetSession(event.getServer());
            discardUnknowns(timeless);
            prepareHub(timeless, false);
        }
    }

    private static void applyThreshold(
            UnknownEntity boss,
            UnknownEncounterSavedData encounter,
            int threshold) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (encounter.phase() == Phase.RECONSTRUCTING || ArenaReconstructionWave.isBusy()) {
            return;
        }
        if (threshold == 1 || threshold == 3 || threshold == 5) {
            explodeFightFragment(level, threshold - 1);
            beginRuinsWave(boss, level);
            return;
        }
        if (threshold == 2 || threshold == 4) {
            explodeFightFragment(level, threshold - 1);
            int completedEraCount = threshold / 2;
            if (completedEraCount >= encounter.reviewEraCount()) {
                beginVoidCollapse(boss, level, encounter, true);
                return;
            }
            encounter.setNextEraIndex(completedEraCount);
            beginVoidCollapse(boss, level, encounter, false);
            return;
        }
        if (threshold >= UnknownEraSequence.STAGE_COUNT) {
            explodeFightFragment(level, UnknownEraSequence.STAGE_COUNT - 1);
            beginVoidCollapse(boss, level, encounter, false);
            // Final void state applied in collapse completion when eraIndex is already maxed.
            encounter.setNextEraIndex(UnknownEraSequence.ERA_COUNT);
        }
    }

    private static void explodeFightFragment(ServerLevel level, int slot) {
        BigEchoPedestalBlockEntity altar = altarEntity(level);
        if (altar == null) {
            return;
        }
        ItemStack removed = altar.clearFragmentSlot(slot);
        if (removed.isEmpty()) {
            return;
        }
        // After a ruins fragment detonates, stop orbiting until the next era rises.
        if (slot % 2 == 1) {
            altar.setOrbitEraIndex(-1);
        }
        BlockPos origin = TimelessDimensions.BOSS_PEDESTAL_ORIGIN;
        level.playSound(
                null,
                origin,
                SoundEvents.GLASS_BREAK,
                SoundSource.BLOCKS,
                1.0F,
                0.7F + slot * 0.05F);
        UnknownAltarFragmentExplodePayload payload =
                new UnknownAltarFragmentExplodePayload(origin, slot);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static void beginRuinsWave(UnknownEntity boss, ServerLevel level) {
        UnknownEncounterSavedData encounter = encounter(level);
        UnknownMedievalVanguard.clear(level, encounter);
        clearMedievalRubbleProjectiles(level);
        encounter.setState(Phase.RECONSTRUCTING, Action.RECONSTRUCTING);
        boss.resetGreekCombat();
        boss.setInvulnerable(true);
        boss.getNavigation().stop();
        boss.setTarget(null);
        updateBossBar(encounter, boss, owner(level, encounter));

        UnknownEraSequence era = UnknownEraSequence.forIndex(encounter.nextEraIndex());
        Identifier ruins = era.ruinsTemplate();
        BlockPos pedestal = era.pedestal();
        BlockPos templateOrigin = arenaTemplateOrigin(ruins);
        if (era == UnknownEraSequence.MEDIEVAL) {
            Optional<StructureTemplate> loaded = level.getStructureManager().get(ruins);
            if (loaded.isEmpty()) {
                abortMissingArenaTemplate(level, ruins);
                return;
            }
            UnknownMedievalRuinsArena.Validation validation =
                    UnknownMedievalRuinsArena.validate(loaded.get(), templateOrigin);
            if (!validation.valid()) {
                abortInvalidArenaTemplate(level, ruins, validation.describe());
                return;
            }
        }

        java.util.ArrayList<ArenaReconstructionWave.PlannedBlock> blocks =
                new java.util.ArrayList<>();
        if (!ArenaReconstructionWave.collectTemplateDelta(
                level,
                ruins,
                templateOrigin,
                pedestalFootprint(),
                blocks::add)) {
            abortMissingArenaTemplate(level, ruins);
            return;
        }
        beginEraCinematic(level, boss, false);
        ArenaReconstructionWave.start(
                level,
                pedestalCenterFor(pedestal),
                arenaWaveExtents(level, pedestal),
                blocks,
                true,
                () -> finishRuinsWave(boss, level, ruins, pedestal));
    }

    private static void finishRuinsWave(
            UnknownEntity boss,
            ServerLevel level,
            Identifier ruins,
            BlockPos pedestal) {
        UnknownEncounterSavedData encounter = encounter(level);
        if (!encounter.controls(boss.getUUID())) {
            return;
        }
        placeTemplate(level, ruins, TimelessDimensions.ARENA_ORIGIN);
        placePedestals(level);
        ensureArenaLighting(level);
        rescueArenaOccupants(level, boss, pedestal);
        if (ruins.equals(MEDIEVAL_RUINS)) {
            ServerPlayer fightOwner = owner(level, encounter);
            if (fightOwner == null
                    || !UnknownMedievalRuinsArena.initialize(level, boss, fightOwner)) {
                abortInvalidArenaTemplate(
                        level,
                        ruins,
                        fightOwner == null
                                ? "the owner is unavailable for Medieval Ruins"
                                : "runtime initialization failed");
                return;
            }
            UnknownMedievalVanguard.trackTemporaryEntities(level, encounter);
            beginMedievalShieldBreak(level, boss, encounter);
            return;
        }
        releaseEraPresentation(level, boss);
        encounter.setState(Phase.RUINS, actionForMaterializedEra(encounter.era()));
        boss.setInvulnerable(false);
        equipEraWeapon(boss);
        updateBossBar(encounter, boss, owner(level, encounter));
    }

    private static void beginMedievalShieldBreak(
            ServerLevel level,
            UnknownEntity boss,
            UnknownEncounterSavedData encounter) {
        if (!encounter.controls(boss.getUUID())) {
            return;
        }
        encounter.setState(Phase.RUINS, Action.SHIELD_BREAK);
        encounter.resetCinematicTicks();
        boss.resetGreekCombat();
        boss.setInvulnerable(true);
        boss.getNavigation().stop();
        boss.setTarget(null);
        equipEraWeapon(boss);
        boss.triggerGreekAnimation("medieval_shield_break");
        syncEnterCinematic(owner(level, encounter), boss, encounter);
        updateBossBar(encounter, boss, owner(level, encounter));
    }

    private static void beginVoidCollapse(
            UnknownEntity boss,
            ServerLevel level,
            UnknownEncounterSavedData encounter,
            boolean reviewDone) {
        UnknownMedievalVanguard.clear(level, encounter);
        clearMedievalRubbleProjectiles(level);
        encounter.setState(Phase.RECONSTRUCTING, Action.RECONSTRUCTING);
        boss.resetGreekCombat();
        boss.setInvulnerable(true);
        boss.getNavigation().stop();
        boss.setTarget(null);
        updateBossBar(encounter, boss, owner(level, encounter));
        java.util.ArrayList<ArenaReconstructionWave.PlannedBlock> blocks =
                new java.util.ArrayList<>();
        collectVoidPlatformDelta(level, blocks);
        BlockPos pedestal = encounter.era() == Era.VOID
                ? UnknownEraSequence.clamped(encounter.nextEraIndex()).pedestal()
                : UnknownEraSequence.forKey(encounter.era().name()).pedestal();
        beginEraCinematic(level, boss, false);
        ArenaReconstructionWave.start(
                level,
                pedestalCenterFor(pedestal),
                arenaWaveExtents(level, pedestal),
                blocks,
                true,
                () -> finishVoidCollapse(boss, level, reviewDone));
    }

    private static Vec3 arenaWaveExtents(ServerLevel level, BlockPos pedestal) {
        ArenaBounds bounds = arenaBounds(level);
        return ArenaReconstructionWave.volumeHalfExtents(
                bounds.origin(),
                bounds.size(),
                pedestalCenterFor(pedestal));
    }

    private static void collectVoidPlatformDelta(
            ServerLevel level,
            java.util.List<ArenaReconstructionWave.PlannedBlock> blocks) {
        ArenaBounds bounds = arenaBounds(level);
        BlockPos origin = bounds.origin();
        Vec3i size = bounds.size();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState floor = Blocks.BLACKSTONE.defaultBlockState();
        java.util.Set<BlockPos> pedestals = pedestalFootprint();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = 0; x < size.getX(); x++) {
            for (int z = 0; z < size.getZ(); z++) {
                int worldX = origin.getX() + x;
                int worldZ = origin.getZ() + z;
                for (int y = 0; y < size.getY(); y++) {
                    cursor.set(worldX, origin.getY() + y, worldZ);
                    BlockPos world = cursor.immutable();
                    if (pedestals.contains(world)) {
                        continue;
                    }
                    boolean podium = world.getY() == TimelessDimensions.FLOOR_Y;
                    BlockState desired = podium ? floor : air;
                    if (!level.getBlockState(world).equals(desired)) {
                        blocks.add(new ArenaReconstructionWave.PlannedBlock(world, desired));
                    }
                }
            }
        }
    }

    public static java.util.Set<BlockPos> pedestalFootprint() {
        BlockPos origin = TimelessDimensions.BOSS_PEDESTAL_ORIGIN;
        return java.util.Set.of(
                origin,
                origin.west(),
                origin.south(),
                origin.west().south());
    }

    /** Only moves an occupant when authored geometry actually intersects it. */
    private static void rescueArenaOccupants(
            ServerLevel level,
            UnknownEntity boss,
            BlockPos pedestal) {
        ArenaBounds bounds = arenaBounds(level);
        BlockPos origin = bounds.origin();
        Vec3i size = bounds.size();
        AABB collisionBounds = new AABB(
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                origin.getX() + size.getX(),
                origin.getY() + size.getY(),
                origin.getZ() + size.getZ());
        ArenaReconstructionWave.rescueCollidingEntity(level, boss, collisionBounds);
        ArenaReconstructionWave.rescueCollidingPlayers(level, collisionBounds);
        ServerPlayer player = owner(level, encounter(level));
        if (player != null) {
            ArenaReconstructionWave.rescueCollidingEntity(level, player, collisionBounds);
        }
    }

    private static void abortMissingArenaTemplate(ServerLevel level, Identifier templateId) {
        LOGGER.error("Missing or empty authored Unknown arena template {}", templateId);
        UnknownEncounterSavedData encounter = encounter(level);
        ServerPlayer player = owner(level, encounter);
        resetSession(level.getServer());
        if (player != null) {
            player.sendSystemMessage(Component.literal(
                    "Unknown arena data is incomplete: " + templateId));
            returnPlayer(player);
        }
    }

    private static void abortInvalidArenaTemplate(
            ServerLevel level,
            Identifier templateId,
            String details) {
        LOGGER.error("Invalid authored Unknown arena template {}: {}", templateId, details);
        UnknownEncounterSavedData encounter = encounter(level);
        ServerPlayer player = owner(level, encounter);
        resetSession(level.getServer());
        if (player != null) {
            player.sendSystemMessage(Component.literal(
                    "Unknown arena authoring is invalid: " + details));
            returnPlayer(player);
        }
    }

    private static void finishVoidCollapse(
            UnknownEntity boss,
            ServerLevel level,
            boolean reviewDone) {
        UnknownEncounterSavedData encounter = encounter(level);
        if (!encounter.controls(boss.getUUID())) {
            return;
        }
        placePedestals(level);
        boss.setEra(UnknownEntity.ERA_VOID);
        boss.setArmored(false);
        clearHeldItem(boss);
        boss.setInvulnerable(false);
        encounter.setEra(Era.VOID);
        BigEchoPedestalBlockEntity altar = altarEntity(level);
        if (altar != null) {
            altar.setOrbitEraIndex(-1);
        }
        if (reviewDone) {
            // Development review endpoint: never strand the tester while later eras are disabled.
            releaseEraPresentation(level, boss);
            placeExitPortal(level);
            encounter.setNextEraIndex(UnknownEraSequence.ERA_COUNT);
            encounter.setState(Phase.VOID_VULNERABLE, Action.WAITING);
            updateBossBar(encounter, boss, owner(level, encounter));
            return;
        }
        if (encounter.nextEraIndex() >= UnknownEraSequence.ERA_COUNT) {
            beginVoidExecution(level, boss, encounter);
            return;
        }
        // Auto next era: brief stun, no pedestal run.
        encounter.setState(Phase.VOID_VULNERABLE, Action.WAITING);
        encounter.setEraStunTicks(45);
        boss.setInvulnerable(true);
        boss.getNavigation().stop();
        boss.setTarget(null);
        updateBossBar(encounter, boss, owner(level, encounter));
    }

    /**
     * Converts the last collapse into a server-owned ending. There is no
     * seventh combat phase: the Unknown is disarmed, held at one health and
     * cannot receive damage until the cinematic resolves it exactly once.
     */
    private static void beginVoidExecution(
            ServerLevel level,
            UnknownEntity boss,
            UnknownEncounterSavedData encounter) {
        if (!encounter.controls(boss.getUUID()) || encounter.executionResolved()) {
            return;
        }
        encounter.beginExecution();
        // Keep the fall camera active and switch modes in-place; sending an
        // inactive packet here would briefly cut back to first person.
        boss.setRitualChanneling(false);
        clearExitPortals(level);
        UnknownMedievalVanguard.clear(level, encounter);
        UnknownMedievalRuinsArena.clear();
        clearMedievalRubbleProjectiles(level);
        clearGreekCombatArtifacts(boss);
        clearHeldItem(boss);
        encounter.setEra(Era.VOID);
        encounter.setNextEraIndex(UnknownEraSequence.ERA_COUNT);
        encounter.setThresholdIndex(UnknownEraSequence.STAGE_COUNT);
        boss.setEra(UnknownEntity.ERA_VOID);
        boss.setArmored(false);
        boss.setHealth(EXECUTION_HEALTH);
        boss.setInvulnerable(true);
        boss.getNavigation().stop();
        boss.setTarget(null);
        boss.setDeltaMovement(Vec3.ZERO);
        boss.beginGreekCombatState(UnknownCombatState.EXECUTION, level.getGameTime(), false);
        boss.triggerGreekAnimation("void_execution");

        BigEchoPedestalBlockEntity altar = altarEntity(level);
        if (altar != null) {
            altar.setOrbitEraIndex(-1);
        }
        ServerPlayer owner = owner(level, encounter);
        faceExecutionAudience(boss, owner);
        holdCinematicAudience(owner);
        syncEnterCinematic(owner, boss, encounter);
        updateBossBar(encounter, boss, owner);
    }

    private static void tickVoidExecution(
            ServerLevel level,
            UnknownEntity boss,
            UnknownEncounterSavedData encounter,
            ServerPlayer owner) {
        boss.getNavigation().stop();
        boss.setTarget(null);
        boss.setDeltaMovement(Vec3.ZERO);
        faceExecutionAudience(boss, owner);

        if (encounter.executionResolved()) {
            return;
        }
        boss.setInvulnerable(true);
        boss.setHealth(EXECUTION_HEALTH);
        if (boss.getCombatState() != UnknownCombatState.EXECUTION) {
            boss.beginGreekCombatState(UnknownCombatState.EXECUTION, level.getGameTime(), false);
            boss.triggerGreekAnimation("void_execution");
        }

        int ticks = encounter.advanceExecutionTick();
        if (ticks == 1 || ticks % 20 == 0) {
            syncEnterCinematic(owner, boss, encounter);
        }
        if (ticks < VOID_EXECUTION_FATAL_TICK || !encounter.tryResolveExecution()) {
            return;
        }

        Vec3 center = boss.position().add(0.0D, boss.getBbHeight() * 0.55D, 0.0D);
        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                center.x,
                center.y,
                center.z,
                48,
                0.55D,
                0.75D,
                0.55D,
                0.12D);
        level.playSound(
                null,
                boss.blockPosition(),
                SoundEvents.BEACON_DEACTIVATE,
                SoundSource.HOSTILE,
                1.4F,
                0.62F);
        boss.setInvulnerable(false);
        boolean fatal = owner != null && boss.hurtServer(
                level,
                level.damageSources().playerAttack(owner),
                Math.max(4.0F, boss.getHealth() + 1.0F));
        if (fatal && !boss.isAlive()) {
            return;
        }

        // A modded damage hook may have canceled the fatal blow. Restore the
        // protected state and retry on the next tick without duplicating loot.
        LOGGER.warn("Unknown Void execution fatal hit was canceled; retrying next tick");
        boss.setHealth(EXECUTION_HEALTH);
        boss.setInvulnerable(true);
        encounter.retryExecutionResolution();
    }

    private static void faceExecutionAudience(UnknownEntity boss, ServerPlayer owner) {
        if (owner == null) {
            return;
        }
        Vec3 from = new Vec3(boss.getX(), boss.getY() + boss.getEyeHeight(), boss.getZ());
        Vec3 audience = owner.getEyePosition();
        float yaw = UnknownEnterCinematicMath.yawToward(from, audience);
        float pitch = UnknownEnterCinematicMath.pitchToward(from, audience);
        boss.getLookControl().setLookAt(audience.x, audience.y, audience.z, 120.0F, 85.0F);
        boss.setYRot(yaw);
        boss.setYHeadRot(yaw);
        boss.setYBodyRot(yaw);
        boss.setXRot(Math.clamp(pitch, -35.0F, 35.0F));
    }

    public static void sendDebugStatus(ServerPlayer player) {
        ServerLevel timeless = player.level().getServer().getLevel(TimelessDimensions.TIMELESS_VOID);
        if (timeless == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.echoes_show_the_past.unknown_dimension_missing"));
            return;
        }
        UnknownEncounterSavedData encounter = encounter(timeless);
        UnknownEntity liveBoss = findEncounterBoss(timeless, encounter);
        float health = liveBoss != null ? liveBoss.getHealth() : 0.0F;
        player.sendSystemMessage(Component.translatable(
                "commands.echoes_show_the_past.unknown_status",
                encounter.phase().name(),
                encounter.action().name(),
                encounter.era().name(),
                encounter.thresholdIndex(),
                String.format(java.util.Locale.ROOT, "%.1f", health)));
    }

    public static boolean debugAdvanceThreshold(ServerPlayer player) {
        ServerLevel timeless = player.level().getServer().getLevel(TimelessDimensions.TIMELESS_VOID);
        if (timeless == null) {
            return false;
        }
        UnknownEncounterSavedData encounter = encounter(timeless);
        UnknownEntity boss = findEncounterBoss(timeless, encounter);
        if (boss == null
                || (encounter.phase() != Phase.PAST && encounter.phase() != Phase.RUINS)
                || encounter.action() == Action.SHIELD_BREAK
                || encounter.thresholdIndex() >= UnknownEraSequence.STAGE_COUNT
                || ArenaReconstructionWave.isBusy()) {
            return false;
        }
        int nextThreshold = encounter.thresholdIndex() + 1;
        boss.setHealth(healthFloorForThreshold(nextThreshold - 1));
        encounter.setThresholdIndex(nextThreshold);
        updateBossBar(encounter, boss, owner(timeless, encounter));
        applyThreshold(boss, encounter, nextThreshold);
        return true;
    }

    public static boolean debugSetStage(ServerPlayer player, CombatStage stage) {
        if (stage == CombatStage.CINEMATIC) {
            return startFight(player);
        }
        if (!ensureActiveFight(player)) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        ServerLevel timeless = server.getLevel(TimelessDimensions.TIMELESS_VOID);
        if (timeless == null) {
            return false;
        }
        UnknownEncounterSavedData encounter = encounter(timeless);
        UnknownEntity boss = findEncounterBoss(timeless, encounter);
        if (boss == null) {
            return false;
        }
        Identifier template = stage.arenaTemplate();
        if (template != null && timeless.getStructureManager().get(template).isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "Unknown arena data is incomplete: " + template));
            return false;
        }
        if (stage == CombatStage.MEDIEVAL_PAST) {
            StructureTemplate medieval = timeless.getStructureManager().get(template).orElseThrow();
            UnknownMedievalVanguard.Validation validation = UnknownMedievalVanguard.validate(
                    medieval, arenaTemplateOrigin(template));
            if (!validation.valid()) {
                player.sendSystemMessage(Component.literal(
                        "Medieval Past authoring is incomplete: " + validation.describe()));
                return false;
            }
        } else if (stage == CombatStage.MEDIEVAL_RUINS) {
            StructureTemplate medieval = timeless.getStructureManager().get(template).orElseThrow();
            UnknownMedievalRuinsArena.Validation validation =
                    UnknownMedievalRuinsArena.validate(
                            medieval,
                            arenaTemplateOrigin(template));
            if (!validation.valid()) {
                player.sendSystemMessage(Component.literal(
                        "Medieval Ruins authoring is incomplete: " + validation.describe()));
                return false;
            }
        }

        ArenaReconstructionWave.cancel();
        boss.setNoGravity(false);
        endEnterCinematic(owner(timeless, encounter));
        UnknownMedievalVanguard.clear(timeless, encounter);
        UnknownMedievalRuinsArena.clear();
        clearMedievalRubbleProjectiles(timeless);
        boss.resetGreekCombat();
        clearGreekCombatArtifacts(boss);
        boss.getNavigation().stop();
        boss.setTarget(null);
        encounter.setEraStunTicks(0);
        encounter.resetCinematicTicks();
        encounter.resetExecution();
        encounter.setDepositStep(-1);
        encounter.setThresholdIndex(stage.threshold());
        encounter.setNextEraIndex(stage.eraIndex());
        encounter.setReviewEraCount(Math.max(
                encounter.reviewEraCount(),
                stage.minimumReviewEras()));
        encounter.setEra(stage.era());
        boss.setEra(stage.entityEra());
        boss.setArmored(stage.era() != Era.VOID);
        Objects.requireNonNull(boss.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(BOSS_MAX_HEALTH);
        boss.setHealth(stage.health());
        boss.setInvulnerable(false);

        BlockPos pedestal = UnknownEraSequence.clamped(stage.eraIndex()).pedestal();
        if (template == null) {
            prepareHub(timeless, true);
        } else {
            clearArena(timeless);
            if (!placeTemplate(timeless, template, TimelessDimensions.ARENA_ORIGIN)) {
                player.sendSystemMessage(Component.literal(
                        "Unknown arena data could not be placed: " + template));
                return false;
            }
            placePedestals(timeless);
            ensureArenaLighting(timeless);
        }
        seedFightAltar(timeless, stage);
        if (stage.era() == Era.VOID) {
            clearHeldItem(boss);
        } else {
            encounter.setState(
                    stage.phase(),
                    stage == CombatStage.MEDIEVAL_RUINS
                            ? Action.SHIELD_BREAK
                            : actionForMaterializedEra(stage.era()));
            equipEraWeapon(boss);
        }
        placeBossOnArenaSurface(timeless, boss);
        rescueArenaOccupants(timeless, boss, pedestal);
        if (stage == CombatStage.MEDIEVAL_PAST) {
            if (!UnknownMedievalVanguard.initialize(timeless, boss, encounter)) {
                player.sendSystemMessage(Component.literal(
                        "Medieval Past could not initialize its authored vanguard."));
                return false;
            }
            reconcileMedievalPastRedstone(timeless);
            boss.setInvulnerable(false);
            boss.setTarget(player);
            player.sendSystemMessage(Component.literal(
                    "Medieval Past: plaza duel and EasyNPC vanguard enabled."));
        } else if (stage == CombatStage.MEDIEVAL_RUINS) {
            if (!UnknownMedievalRuinsArena.initialize(timeless, boss, player)) {
                player.sendSystemMessage(Component.literal(
                        "Medieval Ruins could not initialize its authored markers."));
                return false;
            }
            UnknownMedievalVanguard.trackTemporaryEntities(timeless, encounter);
            beginMedievalShieldBreak(timeless, boss, encounter);
            player.sendSystemMessage(Component.literal(
                    "Medieval Ruins: definitive arena, lower-plaza anchors and combat enabled."));
        } else if (stage == CombatStage.VOID) {
            beginVoidExecution(timeless, boss, encounter);
        }
        updateBossBar(encounter, boss, owner(timeless, encounter));
        return true;
    }

    private static void placeBossOnArenaSurface(ServerLevel level, UnknownEntity boss) {
        BlockPos spawn = TimelessDimensions.BOSS_SPAWN;
        level.getChunkAt(spawn);
        ArenaBounds bounds = arenaBounds(level);
        Vec3 standing = highestStandingPos(
                level,
                spawn.getX(),
                spawn.getZ(),
                bounds.origin().getY() + 1,
                bounds.origin().getY() + bounds.size().getY() - 2);
        boss.snapTo(standing.x, standing.y, standing.z, boss.getYRot(), boss.getXRot());
        boss.setDeltaMovement(Vec3.ZERO);
        boss.fallDistance = 0.0F;
    }

    /**
     * Highest two-block-clear footing in the column. Top-down so authored hills
     * cannot swallow a debug stage jump at the hub Y.
     */
    public static Vec3 highestStandingPos(
            Level level,
            int blockX,
            int blockZ,
            int minY,
            int maxY) {
        int top = Math.max(minY, maxY);
        int bottom = Math.min(minY, maxY);
        Vec3 column = standingPosInColumn(level, blockX, blockZ, bottom, top);
        if (column != null) {
            return column;
        }
        int[][] offsets = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}
        };
        for (int[] offset : offsets) {
            Vec3 nearby = standingPosInColumn(
                    level,
                    blockX + offset[0],
                    blockZ + offset[1],
                    bottom,
                    top);
            if (nearby != null) {
                return nearby;
            }
        }
        return new Vec3(
                blockX + 0.5D,
                TimelessDimensions.FLOOR_Y + 1.0D,
                blockZ + 0.5D);
    }

    private static Vec3 standingPosInColumn(
            Level level,
            int blockX,
            int blockZ,
            int minY,
            int maxY) {
        for (int y = maxY; y >= minY; y--) {
            BlockPos feet = new BlockPos(blockX, y, blockZ);
            BlockPos ground = feet.below();
            if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) {
                continue;
            }
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                continue;
            }
            return new Vec3(blockX + 0.5D, y, blockZ + 0.5D);
        }
        return null;
    }

    private static boolean ensureActiveFight(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel timeless = server.getLevel(TimelessDimensions.TIMELESS_VOID);
        if (timeless == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.echoes_show_the_past.unknown_dimension_missing"));
            return false;
        }
        UnknownEncounterSavedData encounter = encounter(timeless);
        reconcileEncounter(server, encounter);
        if (encounter.isActive() && !encounter.owns(player.getUUID())) {
            player.sendSystemMessage(Component.translatable(
                    "message.echoes_show_the_past.unknown_fight_occupied"));
            return false;
        }
        if (encounter.isActive() && findEncounterBoss(timeless, encounter) != null) {
            if (!player.level().dimension().equals(TimelessDimensions.TIMELESS_VOID)) {
                teleportFacingAltar(player, timeless);
            }
            return true;
        }
        return startFight(player);
    }

    private static void seedFightAltar(ServerLevel level, CombatStage stage) {
        BigEchoPedestalBlockEntity altar = altarEntity(level);
        if (altar == null) {
            placePedestals(level);
            altar = altarEntity(level);
            if (altar == null) {
                return;
            }
        }
        altar.clearAll();
        if (stage == CombatStage.CINEMATIC || stage == CombatStage.VOID) {
            altar.setLocked(false);
            altar.setOrbitEraIndex(-1);
            return;
        }
        for (int slot = stage.threshold(); slot < UnknownEraSequence.STAGE_COUNT; slot++) {
            altar.forceSetFragment(slot, BigEchoPedestalBlockEntity.createFightFragment(level, slot));
        }
        altar.forceSetStone(new ItemStack(EchoesShowThePast.PHILOSOPHERS_STONE.get()));
        altar.setLocked(true);
        altar.setOrbitEraIndex(stage.phase() == Phase.RUINS ? -1 : stage.eraIndex());
    }

    /** Six equal 90 HP combat segments, leaving the final 60 HP void confrontation. */
    public static float healthFloorForThreshold(int thresholdIndex) {
        if (thresholdIndex >= UnknownEraSequence.STAGE_COUNT) {
            return 0.0F;
        }
        int safeIndex = Math.clamp(
                thresholdIndex,
                0,
                UnknownEraSequence.STAGE_COUNT - 1);
        return BOSS_MAX_HEALTH - (safeIndex + 1) * HEALTH_SEGMENT;
    }

    /**
     * Clamps one hit to the next authoritative boundary. During an intermediate
     * void only half of the following 90 HP segment can be pre-damaged.
     */
    public static float clampDamageToCurrentGate(
            float health,
            float requestedDamage,
            int thresholdIndex,
            boolean intermediateVoid) {
        if (requestedDamage <= 0.0F
                || thresholdIndex >= UnknownEraSequence.STAGE_COUNT) {
            return Math.max(0.0F, requestedDamage);
        }
        float floor = healthFloorForThreshold(thresholdIndex);
        if (intermediateVoid) {
            float currentBoundary = thresholdIndex == 0
                    ? BOSS_MAX_HEALTH
                    : healthFloorForThreshold(thresholdIndex - 1);
            floor = currentBoundary - INTERMEDIATE_VOID_DAMAGE;
        }
        return Math.min(requestedDamage, Math.max(0.0F, health - floor));
    }

    private static UnknownEncounterSavedData encounter(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(UnknownEncounterSavedData.TYPE);
    }

    private static UnknownEncounterSavedData encounter(UnknownEntity boss) {
        return boss.level() instanceof ServerLevel level ? encounter(level) : null;
    }

    private static ServerPlayer owner(ServerLevel level, UnknownEncounterSavedData encounter) {
        return encounter.ownerId() == null
                ? null
                : level.getServer().getPlayerList().getPlayer(encounter.ownerId());
    }

    /** Returns false after cleaning an invalid encounter. */
    private static boolean reconcileEncounter(
            MinecraftServer server,
            UnknownEncounterSavedData encounter) {
        if (!encounter.isActive()) {
            return false;
        }
        ServerLevel timeless = server.getLevel(TimelessDimensions.TIMELESS_VOID);
        if (timeless == null) {
            encounter.reset();
            return false;
        }
        ServerPlayer owner = owner(timeless, encounter);
        boolean ownerValid = owner != null
                && owner.level().dimension().equals(TimelessDimensions.TIMELESS_VOID);
        // UUID lookup only sees tracked entities. A boss spawned into a chunk that is
        // not yet player-accessible is invisible to getEntity(UUID) for a tick and
        // used to eject the fighter immediately after /echoes unknown enter.
        UnknownEntity trackedBoss = encounter.phase() == Phase.DEAD
                ? null
                : findEncounterBoss(timeless, encounter);
        boolean bossValid = encounter.phase() == Phase.DEAD || trackedBoss != null;
        if (ownerValid && bossValid) {
            if (trackedBoss != null) {
                BOSS_TRACKING_GRACE.clear();
            }
            return true;
        }
        if (ownerValid
                && BOSS_TRACKING_GRACE.allows(encounter.bossId(), server.getTickCount())) {
            LOGGER.debug(
                    "Waiting for freshly spawned Unknown {} to enter the tracked entity index",
                    encounter.bossId());
            return true;
        }
        LOGGER.warn(
                "Cleaning invalid Unknown encounter (ownerValid={}, bossValid={}, phase={})",
                ownerValid,
                bossValid,
                encounter.phase());
        resetSession(server);
        if (ownerValid) {
            returnPlayer(owner);
        }
        return false;
    }

    /**
     * Resolves the live boss even when its chunk is not yet in the tracked
     * entity map (AABB section scan still sees it).
     */
    private static UnknownEntity findEncounterBoss(
            ServerLevel timeless,
            UnknownEncounterSavedData encounter) {
        if (encounter.bossId() != null
                && timeless.getEntity(encounter.bossId()) instanceof UnknownEntity tracked) {
            return tracked;
        }
        ArenaBounds bounds = arenaBounds(timeless);
        BlockPos origin = bounds.origin();
        Vec3i size = bounds.size();
        AABB search = new AABB(
                origin.getX() - 8.0D,
                Math.max(0, origin.getY() - 8),
                origin.getZ() - 8.0D,
                origin.getX() + size.getX() + 8.0D,
                origin.getY() + size.getY() + 8.0D,
                origin.getZ() + size.getZ() + 8.0D);
        for (UnknownEntity candidate : timeless.getEntitiesOfClass(UnknownEntity.class, search)) {
            if (candidate.isDummy()) {
                continue;
            }
            if (encounter.bossId() == null || candidate.getUUID().equals(encounter.bossId())) {
                return candidate;
            }
        }
        return null;
    }

    private static void updateBossBar(
            UnknownEncounterSavedData encounter,
            UnknownEntity boss,
            ServerPlayer owner) {
        ServerBossEvent bar = encounter.bossBar();
        if (bar == null) {
            return;
        }
        if (owner != null) {
            bar.addPlayer(owner);
        }
        float maxHealth = Math.max(1.0F, boss.getMaxHealth());
        bar.setProgress(Math.clamp(boss.getHealth() / maxHealth, 0.0F, 1.0F));
        bar.setOverlay(BossEvent.BossBarOverlay.NOTCHED_6);
        if (encounter.phase() == Phase.RECONSTRUCTING) {
            bar.setName(Component.translatable("bossbar.echoes_show_the_past.unknown.transition"));
            bar.setColor(BossEvent.BossBarColor.WHITE);
        } else {
            bar.setName(Component.translatable(bossBarKey(encounter)));
            bar.setColor(encounter.era() == Era.VOID
                    ? BossEvent.BossBarColor.PURPLE
                    : UnknownEraSequence.forKey(encounter.era().name()).bossBarColor());
        }
        boolean visible = encounter.phase() != Phase.DEAD;
        bar.setVisible(visible);
        syncBossBarHud(encounter, owner, visible);
    }

    private static void syncBossBarHud(
            UnknownEncounterSavedData encounter,
            ServerPlayer owner,
            boolean visible) {
        if (owner == null) {
            return;
        }
        if (!visible || encounter.bossId() == null) {
            PacketDistributor.sendToPlayer(owner, UnknownBossBarPayload.inactive());
            return;
        }
        PacketDistributor.sendToPlayer(
                owner,
                new UnknownBossBarPayload(
                        true,
                        encounter.bossId(),
                        eraByte(encounter.era()),
                        phaseByte(encounter.phase()),
                        encounter.thresholdIndex()));
    }

    private static byte eraByte(Era era) {
        return era == Era.VOID
                ? UnknownBossBarPayload.ERA_VOID
                : UnknownEraSequence.forKey(era.name()).bossBarEra();
    }

    private static byte phaseByte(Phase phase) {
        return switch (phase) {
            case IDLE -> UnknownBossBarPayload.PHASE_IDLE;
            case CINEMATIC_WALK -> UnknownBossBarPayload.PHASE_CINEMATIC_WALK;
            case RECONSTRUCTING -> UnknownBossBarPayload.PHASE_RECONSTRUCTING;
            case PAST -> UnknownBossBarPayload.PHASE_PAST;
            case RUINS -> UnknownBossBarPayload.PHASE_RUINS;
            case VOID_VULNERABLE -> UnknownBossBarPayload.PHASE_VOID_VULNERABLE;
            case EXECUTION -> UnknownBossBarPayload.PHASE_EXECUTION;
            case DEAD -> UnknownBossBarPayload.PHASE_DEAD;
        };
    }

    private static String bossBarKey(UnknownEncounterSavedData encounter) {
        if (encounter.era() == Era.VOID) {
            return encounter.phase() == Phase.EXECUTION
                    ? "bossbar.echoes_show_the_past.unknown.execution"
                    : "bossbar.echoes_show_the_past.unknown.void";
        }
        String era = encounter.era().name().toLowerCase(java.util.Locale.ROOT);
        String state = encounter.phase() == Phase.RUINS ? "ruins" : "past";
        return "bossbar.echoes_show_the_past.unknown." + era + "." + state;
    }

    private static void prepareHub(ServerLevel level, boolean exitAvailable) {
        clearArena(level);
        // Skip VOID_HUB template until an authored .bp replaces the placeholder NBT.
        placePedestals(level);
        ensureArenaLighting(level);
        clearExitPortals(level);
        if (exitAvailable) {
            placeExitPortal(level);
        }
        ArenaBounds bounds = arenaBounds(level);
        discardArenaItemDrops(level, arenaCleanupVolume(bounds.origin(), bounds.size(), 2.0D));
    }

    private static BlockPos[] buildArenaLightGrid() {
        java.util.ArrayList<BlockPos> positions = new java.util.ArrayList<>();
        int minimumX = TimelessDimensions.ARENA_ORIGIN.getX() + 3;
        int maximumX = TimelessDimensions.ARENA_ORIGIN.getX()
                + TimelessDimensions.ARENA_VOLUME.getX() - 3;
        int minimumZ = TimelessDimensions.ARENA_ORIGIN.getZ() + 3;
        int maximumZ = TimelessDimensions.ARENA_ORIGIN.getZ()
                + TimelessDimensions.ARENA_VOLUME.getZ() - 3;
        for (int x = minimumX; x <= maximumX; x += 6) {
            for (int z = minimumZ; z <= maximumZ; z += 6) {
                positions.add(new BlockPos(x, ARENA_LIGHT_Y, z));
            }
        }
        return positions.toArray(BlockPos[]::new);
    }

    /**
     * Adds only into authored air, never replacing walls, roofs or decoration.
     * Reasserting the grid also repairs cells cleared by a Past/Ruins template
     * transition without making light blocks part of the arena blueprints.
     */
    public static int ensureArenaLighting(ServerLevel level) {
        BlockState desired = Blocks.LIGHT.defaultBlockState()
                .setValue(LightBlock.LEVEL, ARENA_LIGHT_LEVEL);
        int changed = 0;
        for (BlockPos position : ARENA_LIGHTS) {
            BlockState current = level.getBlockState(position);
            if ((!current.isAir() && !current.is(Blocks.LIGHT))
                    || current.equals(desired)) {
                continue;
            }
            level.setBlock(position, desired, Block.UPDATE_CLIENTS);
            changed++;
        }
        return changed;
    }

    public static BigEchoPedestalBlockEntity altarEntity(ServerLevel level) {
        return BigEchoPedestalBlock.altarAt(level, TimelessDimensions.BOSS_PEDESTAL_ORIGIN);
    }

    /** Removes single-block pedestals left by previous hub layouts or old saves. */
    private static void removeLegacyPedestals(ServerLevel level) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = LEGACY_ARENA_MIN.getX(); x <= LEGACY_ARENA_MAX.getX(); x++) {
            for (int z = LEGACY_ARENA_MIN.getZ(); z <= LEGACY_ARENA_MAX.getZ(); z++) {
                for (int y = LEGACY_ARENA_MIN.getY(); y <= LEGACY_ARENA_MAX.getY(); y++) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).is(EchoesShowThePast.ECHO_PEDESTAL.get())) {
                        clearArenaBlock(level, cursor, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    /**
     * Migrates the old 75x47 void pad. Only known generated residue outside
     * the canonical 70x37 arena is touched, so authored arena blocks are safe.
     */
    public static int clearLegacyArenaResidue(ServerLevel level) {
        int cleared = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = LEGACY_ARENA_MIN.getX(); x <= LEGACY_ARENA_MAX.getX(); x++) {
            for (int z = LEGACY_ARENA_MIN.getZ(); z <= LEGACY_ARENA_MAX.getZ(); z++) {
                if (!isOutsideCanonicalArenaFootprint(x, z)) {
                    continue;
                }
                for (int y = LEGACY_ARENA_MIN.getY(); y <= LEGACY_ARENA_MAX.getY(); y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(Blocks.BLACKSTONE)
                            || state.is(Blocks.POLISHED_BLACKSTONE_BRICKS)
                            || state.is(EchoesShowThePast.ECHO_PEDESTAL.get())
                            || state.is(EchoesShowThePast.BIG_ECHO_PEDESTAL.get())) {
                        clearArenaBlock(level, cursor, Blocks.AIR.defaultBlockState());
                        cleared++;
                    }
                }
            }
        }
        return cleared;
    }

    public static boolean isOutsideCanonicalArenaFootprint(int x, int z) {
        int minimumX = TimelessDimensions.ARENA_ORIGIN.getX();
        int maximumX = minimumX + TimelessDimensions.ARENA_VOLUME.getX() - 1;
        int minimumZ = TimelessDimensions.ARENA_ORIGIN.getZ();
        int maximumZ = minimumZ + TimelessDimensions.ARENA_VOLUME.getZ() - 1;
        return x < minimumX || x > maximumX || z < minimumZ || z > maximumZ;
    }

    /** Single walk-in pad on the hub floor — not a fake portal frame wall. */
    private static void placeExitPortal(ServerLevel level) {
        clearExitPortals(level);
        BlockPos pad = TimelessDimensions.EXIT_PORTAL;
        BlockPos floor = pad.below();
        if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
            level.setBlock(floor, Blocks.BLACKSTONE.defaultBlockState(), 3);
        }
        level.setBlock(pad, EchoesShowThePast.TIMELESS_PORTAL.get().defaultBlockState(), 3);
        nudgePlayersOffPortal(level, pad);
    }

    /** Removes current and legacy exit pads so stale plaza portals cannot eject re-entries. */
    private static void clearExitPortals(ServerLevel level) {
        BlockPos[] pads = {
            TimelessDimensions.EXIT_PORTAL,
            // Previous floating pad (hub spawn Y − 1) from before the floor sit.
            TimelessDimensions.HUB_SPAWN.offset(0, -1, -3),
            // Legacy plaza pad from the first hub layout (HUB_SPAWN + (0,-1,12)).
            TimelessDimensions.HUB_SPAWN.offset(0, -1, 12)
        };
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pad : pads) {
            if (level.getBlockState(pad).is(EchoesShowThePast.TIMELESS_PORTAL.get())) {
                level.setBlock(pad, air, 3);
            }
        }
    }

    private static void nudgePlayersOffPortal(ServerLevel level, BlockPos pad) {
        AABB volume = new AABB(pad).inflate(0.35D, 1.25D, 0.35D);
        BlockPos safe = TimelessDimensions.HUB_SPAWN;
        for (ServerPlayer occupant : level.getEntitiesOfClass(ServerPlayer.class, volume)) {
            occupant.setPortalCooldown();
            occupant.teleportTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D);
        }
    }

    private static void storeReturn(ServerPlayer player) {
        captureOverworldPortal(player);
    }

    /**
     * Stores a safe crypt landing and removes the Overworld pad so a defeat
     * return cannot fall back through the still-active portal.
     */
    public static void captureOverworldPortal(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)
                || level.dimension().equals(TimelessDimensions.TIMELESS_VOID)) {
            player.setData(
                    EchoesShowThePast.TIMELESS_RETURN.get(),
                    GlobalPos.of(player.level().dimension(), player.blockPosition().immutable()));
            return;
        }
        List<BlockPos> pad = collectOverworldPad(level, player.blockPosition());
        BlockPos landing = pad.isEmpty()
                ? player.blockPosition().immutable()
                : safeReturnBesidePad(level, pad);
        player.setData(
                EchoesShowThePast.TIMELESS_RETURN.get(),
                GlobalPos.of(level.dimension(), landing));
        if (!pad.isEmpty()) {
            consumeOverworldPad(level, pad);
            player.setData(
                    EchoesShowThePast.TIMELESS_CONSUMED_PORTAL.get(),
                    pad.stream()
                            .map(cell -> GlobalPos.of(level.dimension(), cell))
                            .toList());
        }
    }

    public static List<BlockPos> collectOverworldPad(ServerLevel level, BlockPos origin) {
        BlockPos start = origin;
        if (!level.getBlockState(start).is(EchoesShowThePast.TIMELESS_PORTAL.get())) {
            start = origin.below();
        }
        if (!level.getBlockState(start).is(EchoesShowThePast.TIMELESS_PORTAL.get())) {
            return List.of();
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<BlockPos> found = new HashSet<>();
        queue.add(start.immutable());
        found.add(start.immutable());
        while (!queue.isEmpty() && found.size() < 9) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(direction);
                if (next.getY() != start.getY()
                        || found.contains(next)
                        || !level.getBlockState(next).is(EchoesShowThePast.TIMELESS_PORTAL.get())) {
                    continue;
                }
                BlockPos frozen = next.immutable();
                found.add(frozen);
                queue.add(frozen);
            }
        }
        return List.copyOf(found);
    }

    public static BlockPos safeReturnBesidePad(ServerLevel level, List<BlockPos> pad) {
        BlockPos sample = pad.getFirst();
        int minX = pad.stream().mapToInt(BlockPos::getX).min().orElse(sample.getX());
        int maxX = pad.stream().mapToInt(BlockPos::getX).max().orElse(sample.getX());
        int minZ = pad.stream().mapToInt(BlockPos::getZ).min().orElse(sample.getZ());
        int maxZ = pad.stream().mapToInt(BlockPos::getZ).max().orElse(sample.getZ());
        int y = sample.getY();
        int midX = Math.floorDiv(minX + maxX, 2);
        int midZ = Math.floorDiv(minZ + maxZ, 2);
        BlockPos[] candidates = {
            new BlockPos(midX, y, minZ - 3),
            new BlockPos(midX, y, minZ - 2),
            new BlockPos(midX, y, maxZ + 3),
            new BlockPos(midX - 3, y, midZ),
            new BlockPos(midX + 3, y, midZ)
        };
        for (BlockPos candidate : candidates) {
            BlockState ground = level.getBlockState(candidate.below());
            BlockState body = level.getBlockState(candidate);
            BlockState head = level.getBlockState(candidate.above());
            if (!ground.getCollisionShape(level, candidate.below()).isEmpty()
                    && body.getCollisionShape(level, candidate).isEmpty()
                    && head.getCollisionShape(level, candidate.above()).isEmpty()
                    && !body.liquid()) {
                return candidate;
            }
        }
        return new BlockPos(midX, y, minZ - 3);
    }

    public static void consumeOverworldPad(ServerLevel level, List<BlockPos> pad) {
        for (BlockPos cell : pad) {
            if (level.getBlockState(cell).is(EchoesShowThePast.TIMELESS_PORTAL.get())) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                EchoPedestalIndex.refresh(level, cell);
            }
        }
    }

    public static void restoreOverworldPortal(ServerPlayer player) {
        List<GlobalPos> cells = player.getData(EchoesShowThePast.TIMELESS_CONSUMED_PORTAL.get());
        if (cells == null || cells.isEmpty()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        var portal = EchoesShowThePast.TIMELESS_PORTAL.get().defaultBlockState();
        for (GlobalPos cell : cells) {
            ServerLevel level = server.getLevel(cell.dimension());
            if (level == null || !level.hasChunkAt(cell.pos())) {
                continue;
            }
            if (level.getBlockState(cell.pos()).isAir() || level.getBlockState(cell.pos()).liquid()) {
                level.setBlock(cell.pos(), portal, Block.UPDATE_ALL);
                EchoPedestalIndex.refresh(level, cell.pos());
            }
        }
        player.setData(EchoesShowThePast.TIMELESS_CONSUMED_PORTAL.get(), List.of());
    }

    private static void teleportFacingAltar(ServerPlayer player, ServerLevel timeless) {
        Vec3 spawn = TimelessDimensions.BOSS_ENTRANCE_SPAWN;
        Vec3 from = spawn.add(0.0D, 1.62D, 0.0D);
        Vec3 altar = UnknownEnterCinematicMath.altarFocus(TimelessDimensions.BOSS_PEDESTAL_ORIGIN);
        player.teleport(new TeleportTransition(
                timeless,
                spawn,
                Vec3.ZERO,
                UnknownEnterCinematicMath.yawToward(from, altar),
                UnknownEnterCinematicMath.pitchToward(from, altar),
                TeleportTransition.PLAY_PORTAL_SOUND));
        player.setPortalCooldown();
    }

    private static void tickMedievalShieldBreak(
            ServerLevel level,
            UnknownEntity boss,
            UnknownEncounterSavedData encounter,
            ServerPlayer owner) {
        boss.setInvulnerable(true);
        boss.getNavigation().stop();
        boss.setTarget(null);
        boss.setDeltaMovement(Vec3.ZERO);
        int ticks = keepEraCinematicAlive(owner, boss, encounter);
        if (ticks >= MEDIEVAL_SHIELD_BREAK_IMPACT_TICK
                && !boss.getOffhandItem().isEmpty()) {
            boss.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            level.playSound(
                    null,
                    boss.blockPosition(),
                    SoundEvents.SHIELD_BREAK.value(),
                    SoundSource.HOSTILE,
                    1.45F,
                    0.72F);
            Vec3 center = boss.position().add(0.0D, boss.getBbHeight() * 0.58D, 0.0D);
            level.sendParticles(
                    ParticleTypes.CRIT,
                    center.x,
                    center.y,
                    center.z,
                    22,
                    0.42D,
                    0.52D,
                    0.42D,
                    0.16D);
            level.sendParticles(
                    ParticleTypes.POOF,
                    center.x,
                    center.y,
                    center.z,
                    10,
                    0.32D,
                    0.35D,
                    0.32D,
                    0.055D);
        }
        if (ticks < MEDIEVAL_SHIELD_BREAK_TICKS) {
            return;
        }
        boss.stopCombatAnimation("medieval_shield_break");
        equipMedievalRuinsWeapon(boss);
        boss.resetGreekCombat();
        encounter.setState(Phase.RUINS, Action.COMBAT);
        encounter.resetCinematicTicks();
        boss.setInvulnerable(false);
        releaseEraPresentation(level, boss);
        updateBossBar(encounter, boss, owner);
    }

    private static void syncEnterCinematic(
            ServerPlayer owner,
            UnknownEntity boss,
            UnknownEncounterSavedData encounter) {
        if (owner == null) {
            return;
        }
        UnknownEnterCinematicPayload payload;
        if (boss != null && encounter.phase() == Phase.CINEMATIC_WALK) {
            payload = new UnknownEnterCinematicPayload(
                    true,
                    boss.getId(),
                    TimelessDimensions.BOSS_PEDESTAL_ORIGIN,
                    encounter.action() == Action.DEPOSITING_OFFERING
                            ? UnknownEnterCinematicMath.MODE_DEPOSIT
                            : UnknownEnterCinematicMath.MODE_APPROACH,
                    encounter.depositStep());
        } else if (boss != null && encounter.action() == Action.SHIELD_BREAK) {
            payload = new UnknownEnterCinematicPayload(
                    true,
                    boss.getId(),
                    TimelessDimensions.BOSS_PEDESTAL_ORIGIN,
                    UnknownEnterCinematicMath.MODE_SHIELD_BREAK,
                    -1);
        } else if (boss != null && encounter.action() == Action.EXECUTION) {
            payload = new UnknownEnterCinematicPayload(
                    true,
                    boss.getId(),
                    TimelessDimensions.BOSS_PEDESTAL_ORIGIN,
                    UnknownEnterCinematicMath.MODE_EXECUTION,
                    -1);
        } else if (boss != null && wantsEraLens(encounter)) {
            ArenaBounds bounds = boss.level() instanceof ServerLevel level
                    ? arenaBounds(level)
                    : new ArenaBounds(
                            TimelessDimensions.ARENA_ORIGIN,
                            TimelessDimensions.ARENA_VOLUME);
            payload = new UnknownEnterCinematicPayload(
                    true,
                    boss.getId(),
                    TimelessDimensions.BOSS_PEDESTAL_ORIGIN,
                    eraLensRising
                            ? UnknownEnterCinematicMath.MODE_ERA_RISE
                            : UnknownEnterCinematicMath.MODE_ERA_FALL,
                    -1,
                    bounds.origin(),
                    bounds.size());
        } else {
            payload = UnknownEnterCinematicPayload.inactive();
        }
        if (owner.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(owner, payload);
        }
    }

    private static void endEnterCinematic(ServerPlayer owner) {
        if (owner == null) {
            return;
        }
        UnknownEnterCinematicPayload payload = UnknownEnterCinematicPayload.inactive();
        if (owner.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(owner, payload);
        }
    }

    private static boolean wantsEraLens(UnknownEncounterSavedData encounter) {
        return encounter.phase() == Phase.RECONSTRUCTING
                || encounter.eraStunTicks() > 0
                || encounter.action() == Action.SHIELD_BREAK
                || encounter.action() == Action.EXECUTION;
    }

    private static void beginEraCinematic(
            ServerLevel level,
            UnknownEntity boss,
            boolean rising) {
        eraLensRising = rising;
        boss.setRitualOffering(false);
        triggerRitualChannel(boss);
        gazeAtAltar(boss, true);
        UnknownEncounterSavedData encounter = encounter(level);
        ServerPlayer owner = owner(level, encounter);
        holdCinematicAudience(owner);
        syncEnterCinematic(owner, boss, encounter);
    }

    private static void releaseEraPresentation(ServerLevel level, UnknownEntity boss) {
        boss.setRitualChanneling(false);
        endEnterCinematic(owner(level, encounter(level)));
    }

    private static void holdCinematicAudience(ServerPlayer owner) {
        if (owner == null) {
            return;
        }
        owner.setDeltaMovement(Vec3.ZERO);
        owner.setSprinting(false);
        owner.resetFallDistance();
        grantCinematicFlight(owner);
    }

    private static void grantCinematicFlight(ServerPlayer owner) {
        var flight = owner.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight == null || flight.getModifier(CINEMATIC_FLIGHT_ID) != null) {
            return;
        }
        flight.addTransientModifier(CINEMATIC_FLIGHT);
    }

    private static void revokeCinematicFlight(ServerPlayer owner, boolean force) {
        if (owner == null) {
            return;
        }
        var flight = owner.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight == null || flight.getModifier(CINEMATIC_FLIGHT_ID) == null) {
            return;
        }
        owner.resetFallDistance();
        if (!force && !owner.onGround() && !owner.isInWater() && !owner.onClimbable()) {
            return;
        }
        flight.removeModifier(CINEMATIC_FLIGHT_ID);
    }

    private static int keepEraCinematicAlive(
            ServerPlayer owner,
            UnknownEntity boss,
            UnknownEncounterSavedData encounter) {
        int ticks = encounter.advanceCinematicTick();
        if (ticks == 1 || ticks % 20 == 0) {
            syncEnterCinematic(owner, boss, encounter);
        }
        return ticks;
    }

    private static void teleport(ServerPlayer player, ServerLevel target, BlockPos pos) {
        player.teleport(new TeleportTransition(
                target,
                Vec3.atBottomCenterOf(pos),
                Vec3.ZERO,
                player.getYRot(),
                player.getXRot(),
                TeleportTransition.PLAY_PORTAL_SOUND));
        player.setPortalCooldown();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, path);
    }

}
