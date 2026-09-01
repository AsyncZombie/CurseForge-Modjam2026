package dev.alvar.echoespast.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.markusbordihn.easynpc.data.skin.SkinDataEntry;
import de.markusbordihn.easynpc.data.skin.SkinType;
import io.netty.buffer.Unpooled;
import dev.alvar.echoespast.EchoesConfig;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.block.EchoPedestalBlock;
import dev.alvar.echoespast.block.EchoPedestalBlockEntity;
import dev.alvar.echoespast.block.BigEchoPedestalBlock;
import dev.alvar.echoespast.block.BigEchoPedestalBlockEntity;
import dev.alvar.echoespast.boss.UnknownEraSequence;
import dev.alvar.echoespast.cinematic.UnknownEnterCinematicMath;
import dev.alvar.echoespast.cinematic.UnknownMedievalGrabDiveMath;
import dev.alvar.echoespast.command.EchoCommands;
import dev.alvar.echoespast.entity.ai.UnknownGreekCombatGoal;
import dev.alvar.echoespast.entity.ai.UnknownEgyptianCombatGoal;
import dev.alvar.echoespast.entity.ai.UnknownMedievalCombatGoal;
import dev.alvar.echoespast.entity.ai.UnknownMedievalRuinsCombatGoal;
import dev.alvar.echoespast.entity.combat.MedusaBossMath;
import dev.alvar.echoespast.entity.combat.UnknownCombatState;
import dev.alvar.echoespast.entity.combat.UnknownBossMovementSafety;
import dev.alvar.echoespast.entity.combat.UnknownEgyptianCombatMath;
import dev.alvar.echoespast.entity.combat.UnknownGreekCombatMath;
import dev.alvar.echoespast.entity.combat.UnknownMedievalCombatMath;
import dev.alvar.echoespast.entity.combat.TemporaryDuatWall;
import dev.alvar.echoespast.entity.DungeonPickupEntity;
import dev.alvar.echoespast.entity.MedusaEntity;
import dev.alvar.echoespast.entity.MedievalRubbleProjectile;
import dev.alvar.echoespast.entity.SpectralHopliteEntity;
import dev.alvar.echoespast.entity.UnknownEntity;
import dev.alvar.echoespast.item.UnknownMedievalArmorItem;
import dev.alvar.echoespast.network.EchoStatePayload;
import dev.alvar.echoespast.network.LowFrequencyPulseResultPayload;
import dev.alvar.echoespast.network.LowFrequencyPulseStartPayload;
import dev.alvar.echoespast.network.LowFrequencyPulseCancelPayload;
import dev.alvar.echoespast.network.PhilosophersStoneVisualPayload;
import dev.alvar.echoespast.network.UnknownBossBarPayload;
import dev.alvar.echoespast.network.UnknownCombatImpactPayload;
import dev.alvar.echoespast.network.UnknownEnterCinematicPayload;
import dev.alvar.echoespast.server.LowFrequencySonarMath;
import dev.alvar.echoespast.resonance.EchoSiteType;
import dev.alvar.echoespast.resonance.ResonanceColor;
import dev.alvar.echoespast.resonance.ResonanceKnowledge;
import dev.alvar.echoespast.resonance.ResonatorLoadout;
import dev.alvar.echoespast.resonance.ResonatorModule;
import dev.alvar.echoespast.relic.RelicState;
import dev.alvar.echoespast.relic.RelicReturnSavedData;
import dev.alvar.echoespast.relic.EyeRevealManager;
import dev.alvar.echoespast.relic.EyeHazardClassifier;
import dev.alvar.echoespast.relic.EyeHazardType;
import dev.alvar.echoespast.relic.EyeOfHorusItem;
import dev.alvar.echoespast.relic.MedusaGazeMath;
import dev.alvar.echoespast.relic.MedusaHeadItem;
import dev.alvar.echoespast.relic.MedusaHeadAimMath;
import dev.alvar.echoespast.relic.HolyGrailItem;
import dev.alvar.echoespast.relic.BakedModelPose;
import dev.alvar.echoespast.relic.PetrifiedMobData;
import dev.alvar.echoespast.relic.PetrifiedMobManager;
import dev.alvar.echoespast.relic.PetrifiedPose;
import dev.alvar.echoespast.relic.RelicEffects;
import dev.alvar.echoespast.relic.RelicCooldownManager;
import dev.alvar.echoespast.item.PastEchoMemory;
import dev.alvar.echoespast.mixin.server.StructureTemplateAccessor;
import dev.alvar.echoespast.menu.ResonatorMenu;
import dev.alvar.echoespast.server.MaterializedEchoManager;
import dev.alvar.echoespast.server.MaterializedEchoSavedData;
import dev.alvar.echoespast.server.ArenaReconstructionWave;
import dev.alvar.echoespast.server.RelicControlManager;
import dev.alvar.echoespast.server.UnknownEncounterSavedData;
import dev.alvar.echoespast.server.UnknownFightSavedData;
import dev.alvar.echoespast.server.UnknownFightManager;
import dev.alvar.echoespast.server.UnknownMedievalVanguard;
import dev.alvar.echoespast.server.UnknownMedievalRuinsArena;
import dev.alvar.echoespast.snapshot.EchoCapture;
import dev.alvar.echoespast.snapshot.EchoProjectionBudget;
import dev.alvar.echoespast.snapshot.EchoSiteAdditions;
import dev.alvar.echoespast.snapshot.EchoRevisionCell;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import dev.alvar.echoespast.snapshot.EchoTemplateResolver;
import dev.alvar.echoespast.snapshot.EchoTemplateProjectionIndex;
import dev.alvar.echoespast.snapshot.EchoTemplateResourceLoader;
import dev.alvar.echoespast.snapshot.SnapshotBlock;
import dev.alvar.echoespast.snapshot.SnapshotEntity;
import dev.alvar.echoespast.snapshot.SnapshotEntityIO;
import dev.alvar.echoespast.visual.AltarOfferingMotion;
import dev.alvar.echoespast.visual.EchoBlockChange;
import dev.alvar.echoespast.visual.EchoArrivalSolver;
import dev.alvar.echoespast.visual.EchoCacheHandoff;
import dev.alvar.echoespast.visual.EchoFaceVisibility;
import dev.alvar.echoespast.visual.EchoGhostOccupancy;
import dev.alvar.echoespast.visual.EchoMaterialResponse;
import dev.alvar.echoespast.visual.EchoOccluderPropagation;
import dev.alvar.echoespast.visual.EchoPastLight;
import dev.alvar.echoespast.visual.EchoPostEffects;
import dev.alvar.echoespast.visual.EchoPulseTiming;
import dev.alvar.echoespast.visual.EchoProjectionStyle;
import dev.alvar.echoespast.visual.EchoRadialWindow;
import dev.alvar.echoespast.visual.EchoSurfaceCrestPath;
import dev.alvar.echoespast.visual.EchoScreenGrade;
import dev.alvar.echoespast.visual.EchoTemplateWaveMesher;
import dev.alvar.echoespast.visual.EchoVisualTiming;
import dev.alvar.echoespast.visual.EchoOccluderDistances;
import dev.alvar.echoespast.visual.EchoWaveHandoff;
import dev.alvar.echoespast.visual.EchoWaveTessellation;
import dev.alvar.echoespast.visual.EchoWaveVolume;
import dev.alvar.echoespast.visual.PetrifiedItemLayout;
import dev.alvar.echoespast.visual.PhilosophersStoneVisualTiming;
import dev.alvar.echoespast.visual.TimelessAtmosphere;
import dev.alvar.echoespast.server.EchoProjectionAccess;
import dev.alvar.echoespast.server.EchoProjectionManager;
import dev.alvar.echoespast.world.EchoRuinTemplate;
import dev.alvar.echoespast.world.BarrierToAirProcessor;
import dev.alvar.echoespast.world.CryptAccessGate;
import dev.alvar.echoespast.world.EchoSiteLandFooting;
import dev.alvar.echoespast.world.EchoSitePlacement;
import dev.alvar.echoespast.world.EchoSiteTerrainBlend;
import dev.alvar.echoespast.world.EchoPedestalIndex;
import dev.alvar.echoespast.world.TimelessDimensions;
import dev.alvar.echoespast.world.TimelessFireRules;
import dev.alvar.echoespast.world.UnknownMedievalArenaProcessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;

public final class EchoGameTests {
    public static void register(DeferredRegister<Consumer<GameTestHelper>> functions) {
        functions.register("capture_basic", () -> EchoGameTests::captureBasic);
        functions.register("capture_limit", () -> EchoGameTests::captureLimit);
        functions.register("capture_entities", () -> EchoGameTests::captureEntities);
        functions.register("capture_skeleton_aim", () -> EchoGameTests::captureSkeletonAim);
        functions.register("vertical_bounds", () -> EchoGameTests::verticalBounds);
        functions.register("sealed_memory", () -> EchoGameTests::sealedMemory);
        functions.register("codec_round_trip", () -> EchoGameTests::codecRoundTrip);
        functions.register("projection_guards", () -> EchoGameTests::projectionGuards);
        functions.register("wave_volume", () -> EchoGameTests::waveVolume);
        functions.register("echo_arrival_field", () -> EchoGameTests::echoArrivalField);
        functions.register(
                "echo_arrival_incremental",
                () -> EchoGameTests::echoArrivalIncremental);
        functions.register("block_change_classification", () -> EchoGameTests::blockChangeClassification);
        functions.register("visual_timing", () -> EchoGameTests::visualTiming);
        functions.register("pulse_timing", () -> EchoGameTests::pulseTiming);
        functions.register(
                "template_wave_meshing",
                () -> EchoGameTests::templateWaveMeshing);
        functions.register("post_uniform_layout", () -> EchoGameTests::postUniformLayout);
        functions.register("low_frequency_selection", () -> EchoGameTests::lowFrequencySelection);
        functions.register("low_frequency_wool_faces", () -> EchoGameTests::lowFrequencyWoolFaces);
        functions.register("low_frequency_index_lifecycle", () -> EchoGameTests::lowFrequencyIndexLifecycle);
        functions.register("low_frequency_codecs_timing", () -> EchoGameTests::lowFrequencyCodecsTiming);
        functions.register("progression_codecs", () -> EchoGameTests::progressionCodecs);
        functions.register("authored_sites", () -> EchoGameTests::authoredSites);
        functions.register("unknown_crypt_access", () -> EchoGameTests::unknownCryptAccess);
        functions.register(
                "egyptian_temple_terrain_blend",
                () -> EchoGameTests::egyptianTempleTerrainBlend);
        functions.register(
                "land_site_terrain_blend",
                () -> EchoGameTests::landSiteTerrainBlend);
        functions.register(
                "giant_template_projection_index",
                () -> EchoGameTests::giantTemplateProjectionIndex);
        functions.register("relic_persistence", () -> EchoGameTests::relicPersistence);
        functions.register("eye_hazard_classification", () -> EchoGameTests::eyeHazardClassification);
        functions.register("medusa_gaze_geometry", () -> EchoGameTests::medusaGazeGeometry);
        functions.register("medusa_player_memorial", () -> EchoGameTests::medusaPlayerMemorial);
        functions.register("medusa_statue_round_trip", () -> EchoGameTests::medusaStatueRoundTrip);
        functions.register("medusa_boss_kit", () -> EchoGameTests::medusaBossKit);
        functions.register(
                "petrified_peaceful_persistence",
                () -> EchoGameTests::petrifiedPeacefulPersistence);
        functions.register("holy_grail_ritual", () -> EchoGameTests::holyGrailRitual);
        functions.register("materialized_echo_transaction", () -> EchoGameTests::materializedEchoTransaction);
        functions.register("materialized_echo_entities", () -> EchoGameTests::materializedEchoEntities);
        functions.register(
                "philosophers_stone_pedestal_activation",
                () -> EchoGameTests::philosophersStonePedestalActivation);
        functions.register("philosophers_stone_cancel", () -> EchoGameTests::philosophersStoneCancel);
        functions.register("relic_control_actions", () -> EchoGameTests::relicControlActions);
        functions.register("past_fragment_vessel", () -> EchoGameTests::pastFragmentVessel);
        functions.register("echo_pedestal_reseating", () -> EchoGameTests::echoPedestalReseating);
        functions.register("unknown_damage_gates", () -> EchoGameTests::unknownDamageGates);
        functions.register(
                "unknown_hostile_damage_delivery",
                () -> EchoGameTests::unknownHostileDamageDelivery);
        functions.register("unknown_peaceful_persistence", () -> EchoGameTests::unknownPeacefulPersistence);
        functions.register("unknown_pedestal_approach", () -> EchoGameTests::unknownPedestalApproach);
        functions.register("unknown_greek_arena_assets", () -> EchoGameTests::unknownGreekArenaAssets);
        functions.register("unknown_greek_combat_geometry", () -> EchoGameTests::unknownGreekCombatGeometry);
        functions.register("unknown_egyptian_physical_wall", () -> EchoGameTests::unknownEgyptianPhysicalWall);
        functions.register("unknown_boss_safe_movement", () -> EchoGameTests::unknownBossSafeMovement);
        functions.register("unknown_spectral_phalanx_visibility", () -> EchoGameTests::unknownSpectralPhalanxVisibility);
        functions.register("timeless_fire_rules", () -> EchoGameTests::timelessFireRules);
        functions.register("timeless_atmosphere", () -> EchoGameTests::timelessAtmosphere);
        functions.register("arena_wave_player_rescue", () -> EchoGameTests::arenaWavePlayerRescue);
        functions.register("big_echo_pedestal", () -> EchoGameTests::bigEchoPedestal);
        functions.register("dungeon_pickup_collect", () -> EchoGameTests::dungeonPickupCollect);
        functions.register("unknown_enter_cinematic", () -> EchoGameTests::unknownEnterCinematic);
        functions.register("unknown_combat_stage", () -> EchoGameTests::unknownCombatStage);
        functions.register("unknown_void_execution", () -> EchoGameTests::unknownVoidExecution);
        functions.register("timeless_portal_texture", () -> EchoGameTests::timelessPortalTexture);
        functions.register(
                "unknown_medieval_authoring_contract",
                () -> EchoGameTests::unknownMedievalAuthoringContract);
        functions.register(
                "unknown_medieval_entity_sanitizer",
                () -> EchoGameTests::unknownMedievalEntitySanitizer);
        functions.register(
                "unknown_medieval_dependency_contract",
                () -> EchoGameTests::unknownMedievalDependencyContract);
        functions.register(
                "unknown_medieval_definitive_arena",
                () -> EchoGameTests::unknownMedievalDefinitiveArena);
        functions.register(
                "unknown_medieval_past_combat",
                () -> EchoGameTests::unknownMedievalPastCombat);
        functions.register(
                "unknown_medieval_combo_movement",
                () -> EchoGameTests::unknownMedievalComboMovement);
        functions.register(
                "unknown_medieval_redstone",
                () -> EchoGameTests::unknownMedievalRedstone);
        functions.register(
                "unknown_medieval_ruins_combat",
                () -> EchoGameTests::unknownMedievalRuinsCombat);
        functions.register("unknown_dory_item", () -> EchoGameTests::unknownDoryItem);
        functions.register(
                "unknown_medieval_armor_items", () -> EchoGameTests::unknownMedievalArmorItems);
    }

    private static void egyptianTempleTerrainBlend(GameTestHelper helper) {
        helper.assertValueEqual(
                EchoSiteTerrainBlend.blendHeight(70, 76, 0, EchoSiteTerrainBlend.MARGIN),
                70,
                "the pad itself must not be raised or lowered");
        helper.assertValueEqual(
                EchoSiteTerrainBlend.blendHeight(70, 76, EchoSiteTerrainBlend.MARGIN, EchoSiteTerrainBlend.MARGIN),
                76,
                "the far collar must keep the original dune height");
        helper.assertValueEqual(
                EchoSiteTerrainBlend.blendHeight(70, 76, 3, 6),
                73,
                "a mid-collar column must split the difference toward the pad");
        helper.assertValueEqual(
                EchoSiteTerrainBlend.distanceOutside(15, 4, new BoundingBox(10, 0, 0, 12, 8, 6)),
                3,
                "chebyshev distance must measure the collar from the stamped box");

        BlockPos origin = helper.absolutePos(new BlockPos(8, 4, 8));
        int padY = origin.getY();
        BoundingBox footprint = new BoundingBox(
                origin.getX(),
                padY - 1,
                origin.getZ(),
                origin.getX() + 2,
                padY + 1,
                origin.getZ() + 2);
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.getLevel().setBlock(
                        origin.offset(x, 0, z),
                        Blocks.SAND.defaultBlockState(),
                        3);
            }
        }
        BlockPos dune = origin.offset(5, 0, 1);
        for (int y = 0; y <= 4; y++) {
            helper.getLevel().setBlock(
                    dune.offset(0, y, 0),
                    Blocks.SAND.defaultBlockState(),
                    3);
        }
        helper.getLevel().setBlock(
                dune.above(5),
                Blocks.CACTUS.defaultBlockState(),
                3);
        BlockPos dip = origin.offset(-2, -2, 1);
        helper.getLevel().setBlock(origin.offset(-2, -5, 1), Blocks.SANDSTONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.offset(-2, -4, 1), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.offset(-2, -3, 1), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.offset(-2, -2, 1), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.offset(-2, -1, 1), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.offset(-2, 0, 1), Blocks.AIR.defaultBlockState(), 3);

        BoundingBox writable = new BoundingBox(
                origin.getX() - 8,
                padY - 8,
                origin.getZ() - 8,
                origin.getX() + 8,
                padY + 8,
                origin.getZ() + 8);
        EchoSiteTerrainBlend.blend(helper.getLevel(), padY, footprint, writable);

        helper.assertTrue(
                helper.getLevel().getBlockState(dune.above(5)).isAir(),
                "a cactus on a burying dune must be cleared with the slope");
        helper.assertTrue(
                helper.getLevel().getBlockState(dune.above(4)).isAir(),
                "sand that buried the pad must be carved toward the authored cap");
        helper.assertTrue(
                helper.getLevel().getBlockState(dip).is(Blocks.SAND),
                "a dip beside the pad must fill with sand so the ruin does not float");
        helper.assertTrue(
                helper.getLevel().getBlockState(origin.offset(1, 0, 1)).is(Blocks.SAND),
                "the stamped pad must stay untouched");
        helper.succeed();
    }

    private static void landSiteTerrainBlend(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(8, 8, 8));
        int padY = origin.getY();
        BoundingBox footprint = new BoundingBox(
                origin.getX(),
                padY - 1,
                origin.getZ(),
                origin.getX() + 2,
                padY + 1,
                origin.getZ() + 2);
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.getLevel().setBlock(
                        origin.offset(x, 0, z),
                        Blocks.GRASS_BLOCK.defaultBlockState(),
                        3);
            }
        }
        BlockPos hill = origin.offset(5, 0, 1);
        for (int y = 0; y <= 4; y++) {
            helper.getLevel().setBlock(
                    hill.offset(0, y, 0),
                    Blocks.STONE.defaultBlockState(),
                    3);
        }
        helper.getLevel().setBlock(
                hill.above(5),
                Blocks.SHORT_GRASS.defaultBlockState(),
                3);
        helper.getLevel().setBlock(origin.offset(-2, -5, 1), Blocks.DIRT.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.offset(-2, -4, 1), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.offset(-2, -3, 1), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.offset(-2, -2, 1), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.offset(-2, -1, 1), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.offset(-2, 0, 1), Blocks.AIR.defaultBlockState(), 3);
        BlockPos dip = origin.offset(-2, -2, 1);

        BoundingBox writable = new BoundingBox(
                origin.getX() - 8,
                padY - 8,
                origin.getZ() - 8,
                origin.getX() + 8,
                padY + 8,
                origin.getZ() + 8);
        EchoSiteTerrainBlend.blend(
                helper.getLevel(),
                padY,
                footprint,
                writable,
                EchoSiteType.Family.MOUNTAIN);

        helper.assertTrue(
                helper.getLevel().getBlockState(hill.above(5)).isAir(),
                "grass on a burying hillside must be cleared with the slope");
        helper.assertTrue(
                helper.getLevel().getBlockState(hill.above(4)).isAir(),
                "stone that buried the pad must be carved toward the authored cap");
        helper.assertTrue(
                helper.getLevel().getBlockState(dip).is(Blocks.GRASS_BLOCK),
                "a dip beside a grass pad must fill with grass so the ruin does not float");
        helper.assertTrue(
                helper.getLevel().getBlockState(origin.offset(1, 0, 1)).is(Blocks.GRASS_BLOCK),
                "the stamped pad must stay untouched");
        helper.succeed();
    }

    private static void dungeonPickupCollect(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
        player.setPos(at);
        DungeonPickupEntity pickup = DungeonPickupEntity.create(
                helper.getLevel(),
                at.x,
                at.y,
                at.z,
                new ItemStack(Items.DIAMOND, 8));
        helper.assertTrue(helper.getLevel().addFreshEntity(pickup), "pickup must enter the level");
        helper.assertTrue(
                pickup.tryCollect(player),
                "right-click style collect must succeed once");
        helper.assertFalse(pickup.isAlive(), "pickup must despawn after collect");
        helper.assertTrue(
                player.getInventory().countItem(Items.DIAMOND) == 8,
                "player must receive the full stack");

        DungeonPickupEntity second = DungeonPickupEntity.create(
                helper.getLevel(),
                at.x,
                at.y + 1.0,
                at.z,
                new ItemStack(Items.EMERALD, 3));
        helper.assertTrue(helper.getLevel().addFreshEntity(second), "second pickup must enter");
        helper.assertTrue(second.tryCollect(player), "first collect of second pickup succeeds");
        helper.assertFalse(second.tryCollect(player), "already-collected pickup must fail");
        helper.assertTrue(
                player.getInventory().countItem(Items.EMERALD) == 3,
                "emerald stack must be granted once");
        helper.assertValueEqual(
                DungeonPickupEntity.clampScale(100.0F),
                DungeonPickupEntity.MAX_SCALE,
                "pickup scale must clamp instead of exploding the hitbox");
        DungeonPickupEntity scaled = DungeonPickupEntity.create(
                helper.getLevel(),
                at.x,
                at.y + 2.0,
                at.z,
                new ItemStack(Items.GOLD_INGOT),
                2.5F);
        helper.assertTrue(helper.getLevel().addFreshEntity(scaled), "scaled pickup must enter");
        helper.assertValueEqual(
                scaled.getDisplayScale(),
                2.5F,
                "spawned pickups must keep the authored display scale");
        helper.assertTrue(
                scaled.getBbWidth() > 0.45F,
                "a larger pickup must grow its collect hitbox");
        scaled.discard();
        helper.succeed();
    }

    private static void unknownEnterCinematic(GameTestHelper helper) {
        BlockPos origin = TimelessDimensions.BOSS_PEDESTAL_ORIGIN;
        Vec3 altar = UnknownEnterCinematicMath.altarFocus(origin);
        helper.assertTrue(
                Math.abs(altar.y - (origin.getY() + 1.62D)) < 1.0E-6D,
                "the lens must sit on the levitating offering height, not the floor");
        Vec3 boss = new Vec3(0.5D, 64.0D, 8.5D);
        Vec3 audience = new Vec3(-10.5D, 66.62D, -13.5D);
        Vec3 follow = UnknownEnterCinematicMath.approachCamera(boss, altar, audience);
        helper.assertTrue(
                follow.y > boss.y + 1.0D,
                "the opening crane must look down onto the silhouette");
        helper.assertTrue(
                follow.distanceTo(boss) > 4.5D,
                "the follow rig must not inherit boss pathfinding jitter");
        helper.assertTrue(
                follow.distanceTo(audience) < follow.distanceTo(altar),
                "the walk shot stays on the player's side of the plaza");
        float yaw = UnknownEnterCinematicMath.yawToward(
                follow,
                UnknownEnterCinematicMath.approachLook(boss, altar));
        helper.assertTrue(Float.isFinite(yaw), "look-at yaw must stay finite");
        helper.assertValueEqual(
                UnknownEnterCinematicMath.smoothstep(0.0F),
                0.0F,
                "smoothstep must rest at the start");
        helper.assertValueEqual(
                UnknownEnterCinematicMath.smootherstep(1.0F),
                1.0F,
                "smootherstep must rest at the end");
        double damped = UnknownEnterCinematicMath.damp(0.0D, 10.0D, 2.2D, 0.05D);
        helper.assertTrue(
                damped > 0.0D && damped < 1.5D,
                "critically damped motion must ease in without overshooting");
        Vec3 ritual = UnknownEnterCinematicMath.depositCamera(
                new Vec3(-33.5D, 64.0D, 0.5D),
                altar,
                3,
                1.6D);
        helper.assertTrue(
                ritual.distanceTo(altar) < 7.5D,
                "the ritual orbit must stay framed on the altar");
        helper.assertTrue(
                UnknownEnterCinematicMath.depositFov(6)
                        < UnknownEnterCinematicMath.depositFov(0),
                "the stone beat must tighten the lens");
        helper.assertTrue(
                UnknownEnterCinematicMath.punchFov(0.0D) < 0.0F,
                "seating a fragment must breathe the FOV inward");
        helper.assertTrue(
                Math.abs(UnknownEnterCinematicMath.punchFov(3.0D)) < 0.05F,
                "the fragment punch must decay before the next offering");
        Vec3 approachFeet = new Vec3(-34.5D, 64.0D, 0.5D);
        float altarYaw = UnknownEnterCinematicMath.yawToward(
                approachFeet.add(0.0D, 1.62D, 0.0D),
                altar);
        helper.assertTrue(
                altarYaw > 45.0F && altarYaw < 120.0F,
                "from the plaza approach the boss must face the altar table, not south");
        helper.assertTrue(
                UnknownFightManager.RITUAL_OFFER_PLACE_TICK
                        < UnknownFightManager.RITUAL_OFFER_CYCLE_TICKS,
                "the offering place beat must land inside its animation cycle");
        Vec3 eraAudience = new Vec3(-10.5D, 66.62D, -13.5D);
        Vec3 eraBoss = new Vec3(-33.5D, 64.0D, 0.5D);
        Vec3 eraRise = UnknownEnterCinematicMath.eraCamera(
                eraBoss, altar, eraAudience, 2.5D, true);
        Vec3 eraFall = UnknownEnterCinematicMath.eraCamera(
                eraBoss, altar, eraAudience, 2.5D, false);
        Vec3 deposit = UnknownEnterCinematicMath.depositCamera(eraBoss, altar, 0, 0.0D);
        helper.assertTrue(
                eraRise.y > eraBoss.y + 2.4D,
                "the era crane must look down onto the reconstruction");
        helper.assertTrue(
                eraRise.distanceTo(altar) > deposit.distanceTo(altar),
                "the reconstruction shot must pull back past the fragment orbit");
        Vec3 riseFromAltar = new Vec3(eraRise.x - altar.x, 0.0D, eraRise.z - altar.z);
        Vec3 audienceFromAltar = new Vec3(eraAudience.x - altar.x, 0.0D, eraAudience.z - altar.z);
        helper.assertTrue(
                riseFromAltar.dot(audienceFromAltar) > 0.0D,
                "the era crane stays on the player's side of the plaza");
        helper.assertTrue(
                eraFall.distanceTo(UnknownEnterCinematicMath.bossFocus(eraBoss))
                        < eraRise.distanceTo(UnknownEnterCinematicMath.bossFocus(eraBoss)),
                "collapse keeps the silhouette closer than a rising world");
        helper.assertTrue(
                UnknownEnterCinematicMath.eraFov(true)
                        > UnknownEnterCinematicMath.depositFov(0),
                "a rising era must widen the lens to show the wave");
        helper.assertTrue(
                UnknownEnterCinematicMath.isEraMode(UnknownEnterCinematicMath.MODE_ERA_RISE)
                        && UnknownEnterCinematicMath.isEraMode(
                                UnknownEnterCinematicMath.MODE_ERA_FALL)
                        && !UnknownEnterCinematicMath.isEraMode(
                                UnknownEnterCinematicMath.MODE_DEPOSIT),
                "era modes must not collide with the plaza seating lens");
        AltarOfferingMotion.Pose introStart = AltarOfferingMotion.intro(0.0D);
        AltarOfferingMotion.Pose introEnd = AltarOfferingMotion.intro(
                AltarOfferingMotion.INTRO_SECONDS);
        helper.assertTrue(
                introStart.scale() < 0.08F && introStart.heightBias() < -0.3F,
                "a seated fragment must rise out of the altar instead of popping in");
        helper.assertTrue(
                introEnd.scale() > 0.99F && Math.abs(introEnd.heightBias()) < 0.02F,
                "the seat animation must rest at full size on the orbit");
        helper.assertTrue(
                AltarOfferingMotion.INTRO_SECONDS
                        < UnknownFightManager.RITUAL_OFFER_CYCLE_TICKS / 20.0F,
                "each offering must finish appearing before the next place beat");
        AltarOfferingMotion.Pose outroStart = AltarOfferingMotion.outro(0.0D);
        AltarOfferingMotion.Pose outroEnd = AltarOfferingMotion.outro(
                AltarOfferingMotion.OUTRO_SECONDS);
        helper.assertTrue(
                outroStart.scale() > 0.99F && outroStart.visible(),
                "a detonating fragment must start from its seated size");
        helper.assertTrue(
                !outroEnd.visible() && outroEnd.scale() < 0.05F,
                "a detonating fragment must shrink away instead of vanishing");
        helper.succeed();
    }

    private static void unknownCombatStage(GameTestHelper helper) {
        helper.assertValueEqual(
                UnknownEraSequence.ordered(),
                List.of(
                        UnknownEraSequence.MEDIEVAL,
                        UnknownEraSequence.GREEK,
                        UnknownEraSequence.EGYPTIAN),
                "the canonical fight order must be Medieval, Greek, Egyptian");
        helper.assertValueEqual(
                UnknownFightManager.CombatStage.parse("greek_past"),
                UnknownFightManager.CombatStage.GREEK_PAST,
                "greek_past must alias the hellenic past stage");
        helper.assertValueEqual(
                UnknownFightManager.CombatStage.parse("medieval"),
                UnknownFightManager.CombatStage.MEDIEVAL_PAST,
                "medieval must remain a valid debug alias");
        helper.assertValueEqual(
                UnknownFightManager.CombatStage.parse("egyptian_ruins"),
                UnknownFightManager.CombatStage.EGYPTIAN_RUINS,
                "egyptian_ruins must remain a valid debug alias");
        helper.assertTrue(
                UnknownFightManager.CombatStage.parse("not-a-stage") == null,
                "an unknown stage name must be rejected");
        helper.assertValueEqual(
                UnknownFightManager.CombatStage.MEDIEVAL_PAST.threshold(),
                0,
                "medieval past is the opening combat gate");
        helper.assertValueEqual(
                UnknownFightManager.CombatStage.MEDIEVAL_PAST.health(),
                600.0F,
                "medieval past starts at full health");
        helper.assertValueEqual(
                UnknownFightManager.CombatStage.MEDIEVAL_RUINS.health(),
                UnknownFightManager.healthFloorForThreshold(0),
                "medieval ruins must sit on the first HP boundary");
        helper.assertValueEqual(
                UnknownFightManager.CombatStage.GREEK_PAST.threshold(),
                2,
                "Greek past begins after both medieval stages");
        helper.assertValueEqual(
                UnknownFightManager.CombatStage.EGYPTIAN_PAST.threshold(),
                4,
                "Egyptian past begins after Medieval and Greek");
        helper.assertValueEqual(
                UnknownFightManager.CombatStage.EGYPTIAN_RUINS.threshold(),
                5,
                "Egyptian ruins must own the sixth fragment");
        helper.assertValueEqual(
                UnknownFightManager.CombatStage.GREEK_PAST.health(),
                420.0F,
                "Greek past starts at 420 HP");
        helper.assertValueEqual(
                UnknownFightManager.CombatStage.EGYPTIAN_PAST.health(),
                240.0F,
                "Egyptian past starts at 240 HP");
        helper.assertValueEqual(
                UnknownFightManager.CombatStage.VOID.phase(),
                UnknownFightManager.Phase.EXECUTION,
                "Void must be an automatic execution, not a seventh combat phase");
        helper.assertValueEqual(
                UnknownFightManager.CombatStage.VOID.health(),
                UnknownFightManager.EXECUTION_HEALTH,
                "Void execution must expose exactly one cinematic health point");
        helper.assertTrue(
                UnknownFightManager.CombatStage.CINEMATIC.arenaTemplate() == null,
                "the intro stays on the hub floor");
        helper.assertTrue(
                UnknownFightManager.CombatStage.MEDIEVAL_PAST.minimumReviewEras() == 1,
                "jumping to the opening medieval stage only needs the first era");
        helper.assertTrue(
                UnknownFightManager.CombatStage.EGYPTIAN_PAST.minimumReviewEras() == 3,
                "jumping to Egyptian must retain all three eras");

        ResonanceColor[] expectedColors = {
            ResonanceColor.CORAL,
            ResonanceColor.CORAL,
            ResonanceColor.PALE_BLUE,
            ResonanceColor.PALE_BLUE,
            ResonanceColor.GOLD,
            ResonanceColor.GOLD
        };
        Identifier[] expectedTemplates = {
            UnknownEraSequence.MEDIEVAL.pastTemplate(),
            UnknownEraSequence.MEDIEVAL.ruinsTemplate(),
            UnknownEraSequence.GREEK.pastTemplate(),
            UnknownEraSequence.GREEK.ruinsTemplate(),
            UnknownEraSequence.EGYPTIAN.pastTemplate(),
            UnknownEraSequence.EGYPTIAN.ruinsTemplate()
        };
        for (int slot = 0; slot < UnknownEraSequence.STAGE_COUNT; slot++) {
            helper.assertValueEqual(
                    BigEchoPedestalBlockEntity.colorForFightSlot(slot),
                    expectedColors[slot],
                    "fight slot " + slot + " must use the canonical era color");
            helper.assertValueEqual(
                    BigEchoPedestalBlockEntity.templateForFightSlot(slot),
                    expectedTemplates[slot],
                    "fight slot " + slot + " must carry the canonical arena template");
            UnknownEraSequence era = UnknownEraSequence.forFightSlot(slot);
            helper.assertValueEqual(
                    era.threshold(UnknownEraSequence.isRuinsSlot(slot)),
                    slot,
                    "fight slot " + slot + " must equal its HUD seal and HP threshold");
        }
        BlockPos hill = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(hill, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(hill.above(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(hill.above(2), Blocks.STONE.defaultBlockState(), 3);
        Vec3 standing = UnknownFightManager.highestStandingPos(
                helper.getLevel(),
                hill.getX(),
                hill.getZ(),
                hill.getY(),
                hill.getY() + 6);
        helper.assertTrue(
                standing.y > hill.getY() + 2.5D,
                "a stage jump must stand on the hilltop, not inside authored terrain");
        helper.succeed();
    }

    private static void unknownVoidExecution(GameTestHelper helper) {
        helper.assertValueEqual(
                UnknownFightManager.CombatStage.parse("execution"),
                UnknownFightManager.CombatStage.VOID,
                "execution must be a direct debug alias for the final sequence");
        helper.assertValueEqual(
                UnknownFightManager.VOID_EXECUTION_FATAL_TICK,
                100,
                "execution must resolve on the five-second animation beat");
        helper.assertValueEqual(
                UnknownEntity.EXPERIENCE_REWARD,
                500,
                "the normal death path must retain the authored XP reward");
        helper.assertValueEqual(
                UnknownCombatState.EXECUTION.networkId(),
                (byte) 21,
                "execution must append its combat state without renumbering attacks");

        Vec3 bossFeet = new Vec3(3.5D, 2.0D, 3.5D);
        Vec3 audience = new Vec3(3.5D, 3.62D, 11.5D);
        Vec3 opening = UnknownEnterCinematicMath.executionCamera(bossFeet, audience, 0.0D);
        Vec3 descent = UnknownEnterCinematicMath.executionCamera(bossFeet, audience, 2.3D);
        Vec3 defiance = UnknownEnterCinematicMath.executionCamera(bossFeet, audience, 3.9D);
        Vec3 settled = UnknownEnterCinematicMath.executionCamera(bossFeet, audience, 5.0D);
        Vec3 focus = UnknownEnterCinematicMath.bossFocus(bossFeet);
        helper.assertTrue(
                settled.distanceTo(focus) < opening.distanceTo(focus),
                "the execution lens must still finish tighter than its establishing shot");
        helper.assertTrue(
                defiance.distanceTo(focus) < descent.distanceTo(focus),
                "the execution lens must push in for the defiant look back");
        helper.assertTrue(
                settled.distanceTo(focus) > defiance.distanceTo(focus),
                "the execution lens must widen again to retain the forward collapse");
        helper.assertTrue(
                UnknownEnterCinematicMath.executionLook(bossFeet, 5.0D).y
                        < UnknownEnterCinematicMath.executionLook(bossFeet, 0.0D).y,
                "the execution lens must follow the boss down through the fatal impact");
        helper.assertTrue(
                UnknownEnterCinematicMath.executionLook(bossFeet, 3.9D).y
                        > UnknownEnterCinematicMath.executionLook(bossFeet, 2.3D).y,
                "the look target must lift when the defeated boss looks back at the owner");
        helper.assertTrue(
                UnknownEnterCinematicMath.executionFov(3.9D)
                        < UnknownEnterCinematicMath.executionFov(0.0D),
                "the defiant hold must be more intimate than the establishing shot");
        helper.assertTrue(
                UnknownEnterCinematicMath.executionFov(5.0D)
                        > UnknownEnterCinematicMath.executionFov(3.9D),
                "the fatal fall must widen enough to keep the whole silhouette visible");
        helper.assertTrue(
                UnknownEnterCinematicMath.isExecutionMode(
                                UnknownEnterCinematicMath.MODE_EXECUTION)
                        && !UnknownEnterCinematicMath.isEraMode(
                                UnknownEnterCinematicMath.MODE_EXECUTION)
                        && !UnknownEnterCinematicMath.isShieldBreakMode(
                                UnknownEnterCinematicMath.MODE_EXECUTION),
                "execution must own a distinct camera protocol mode");
        UnknownEnterCinematicPayload cameraPayload = new UnknownEnterCinematicPayload(
                true,
                42,
                BlockPos.ZERO,
                UnknownEnterCinematicMath.MODE_EXECUTION,
                -1);
        helper.assertValueEqual(
                cameraPayload.mode(),
                UnknownEnterCinematicMath.MODE_EXECUTION,
                "the payload codec guard must preserve the new camera mode");
        UnknownBossBarPayload bossBarPayload = new UnknownBossBarPayload(
                true,
                UUID.fromString("226a5e65-1d31-4e0a-bff4-cb62df8ea019"),
                UnknownBossBarPayload.ERA_VOID,
                UnknownBossBarPayload.PHASE_EXECUTION,
                UnknownEraSequence.STAGE_COUNT);
        helper.assertValueEqual(
                bossBarPayload.phase(),
                UnknownBossBarPayload.PHASE_EXECUTION,
                "the HUD payload must preserve execution without clamping it to death");

        UUID bossId = UUID.fromString("07ea7599-8656-4392-afc1-684f57de6a26");
        UUID ownerId = UUID.fromString("65916cc8-e0e2-491c-bc08-2090e5cadc7f");
        UnknownEncounterSavedData state = new UnknownEncounterSavedData();
        state.begin(bossId, ownerId, UnknownEraSequence.ERA_COUNT);
        state.setEra(UnknownFightManager.Era.EGYPTIAN);
        state.setState(UnknownFightManager.Phase.RUINS, UnknownFightManager.Action.COMBAT);
        state.setThresholdIndex(UnknownEraSequence.STAGE_COUNT);
        helper.assertTrue(state.beginExecution(), "the final threshold must start execution once");
        helper.assertFalse(state.beginExecution(), "execution start must be idempotent");
        helper.assertValueEqual(
                state.phase(),
                UnknownFightManager.Phase.EXECUTION,
                "execution state must replace the playable Void phase");
        for (int tick = 0; tick < 37; tick++) {
            state.advanceExecutionTick();
        }
        helper.assertTrue(state.tryResolveExecution(), "the first fatal resolution must be accepted");
        helper.assertFalse(state.tryResolveExecution(), "fatal resolution must not run twice");

        var encoded = UnknownEncounterSavedData.CODEC
                .encodeStart(JsonOps.INSTANCE, state)
                .getOrThrow();
        UnknownEncounterSavedData decoded = UnknownEncounterSavedData.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();
        helper.assertValueEqual(decoded.executionTicks(), 37, "execution ticks save round trip");
        helper.assertTrue(decoded.executionResolved(), "fatal resolution save round trip");
        helper.assertFalse(
                decoded.beginExecution(),
                "a resolved execution must stay closed after loading");

        UnknownFightSavedData rewards = new UnknownFightSavedData();
        rewards.markStoneGranted(ownerId);
        rewards.markStoneGranted(ownerId);
        helper.assertTrue(rewards.hasGrantedStone(ownerId), "the owner must retain the Stone grant");
        helper.assertValueEqual(
                rewards.grantedStoneCount(),
                1,
                "repeated death callbacks must not duplicate the Philosopher's Stone");

        try (InputStream animations = EchoGameTests.class.getResourceAsStream(
                "/assets/echoes_show_the_past/geckolib/animations/entity/unknown.animation.json")) {
            helper.assertTrue(animations != null, "Unknown animations must be packaged");
            JsonObject clips = JsonParser.parseString(
                            new String(animations.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonObject("animations");
            helper.assertTrue(
                    clips.has("combat.void.execution"),
                    "the authored Void execution clip must be packaged");
            JsonObject execution = clips.getAsJsonObject("combat.void.execution");
            helper.assertValueEqual(
                    execution.get("animation_length").getAsFloat(),
                    5.0F,
                    "the animation and server fatal beat must share a five-second duration");
            JsonObject executionBones = execution.getAsJsonObject("bones");
            helper.assertValueEqual(
                    executionBones.size(),
                    8,
                    "the final execution must animate the complete runtime combat rig");
            helper.assertTrue(
                    executionBones.getAsJsonObject("body")
                                    .getAsJsonObject("rotation")
                                    .size()
                            >= 18,
                    "the torso must preserve the authored recoil, descent, defiance, and collapse beats");
            helper.assertValueEqual(
                    executionBones.getAsJsonObject("root")
                            .getAsJsonObject("rotation")
                            .getAsJsonArray("5.0")
                            .get(0)
                            .getAsFloat(),
                    -86.0F,
                    "the tick-fatal pose must finish the forward full-body collapse");
            helper.assertTrue(
                    executionBones.getAsJsonObject("arm_right")
                                    .getAsJsonObject("rotation")
                                    .getAsJsonArray("5.0")
                                    .get(0)
                                    .getAsFloat()
                            != executionBones.getAsJsonObject("arm_left")
                                    .getAsJsonObject("rotation")
                                    .getAsJsonArray("5.0")
                                    .get(0)
                                    .getAsFloat(),
                    "the fatal contact must remain asymmetrical instead of ending in a rigid push-up pose");
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect Void execution assets", exception);
        }
        helper.succeed();
    }

    private static void timelessPortalTexture(GameTestHelper helper) {
        try (InputStream texture = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/textures/block/timeless_portal.png");
                InputStream metadata = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/textures/block/timeless_portal.png.mcmeta")) {
            helper.assertTrue(texture != null, "the Timeless Portal atlas must be packaged");
            helper.assertTrue(metadata != null, "the Timeless Portal animation metadata must be packaged");

            var atlas = ImageIO.read(texture);
            helper.assertValueEqual(atlas.getWidth(), 16, "portal density must remain 16 pixels per block");
            helper.assertValueEqual(atlas.getHeight(), 256, "portal atlas must contain sixteen 16x16 frames");

            Set<Integer> colors = new HashSet<>();
            Set<Integer> frameHashes = new HashSet<>();
            boolean fullyOpaque = true;
            for (int frame = 0; frame < 16; frame++) {
                int hash = 1;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        int argb = atlas.getRGB(x, frame * 16 + y);
                        colors.add(argb);
                        fullyOpaque &= (argb >>> 24) == 255;
                        hash = 31 * hash + argb;
                    }
                }
                frameHashes.add(hash);
            }
            helper.assertTrue(
                    colors.size() >= 8 && colors.size() <= 10,
                    "portal pixel art must keep its restrained 8-10 color palette");
            helper.assertTrue(fullyOpaque, "the full-cube portal texture must not contain transparent pixels");
            helper.assertValueEqual(frameHashes.size(), 16, "every portal frame must be visually distinct");

            JsonObject animation = JsonParser.parseString(
                            new String(metadata.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonObject("animation");
            helper.assertValueEqual(animation.get("frametime").getAsInt(), 3, "portal loop must remain calm");
            helper.assertFalse(
                    animation.get("interpolate").getAsBoolean(),
                    "portal frames must stay pixel-crisp without interpolation");
            JsonArray frames = animation.getAsJsonArray("frames");
            helper.assertValueEqual(frames.size(), 16, "metadata must expose all sixteen authored frames");
            for (int frame = 0; frame < frames.size(); frame++) {
                helper.assertValueEqual(frames.get(frame).getAsInt(), frame, "portal frames must play in loop order");
            }
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect Timeless Portal texture assets", exception);
        }
        helper.succeed();
    }

    private static void unknownMedievalAuthoringContract(GameTestHelper helper) {
        BlockPos origin = new BlockPos(100, -20, 300);
        List<StructureTemplate.StructureBlockInfo> markers = medievalMarkerFixture();
        List<StructureTemplate.StructureEntityInfo> localVanguard =
                medievalVanguardFixture(false);
        UnknownMedievalVanguard.Validation valid =
                UnknownMedievalVanguard.validateAuthoredData(
                        markers,
                        localVanguard,
                        origin);
        helper.assertTrue(valid.valid(), "the complete medieval fixture must validate: " + valid.describe());
        UnknownMedievalVanguard.Layout layout = valid.layout().orElseThrow();
        helper.assertValueEqual(
                layout.bossSpawn(),
                origin.offset(4, 12, 4),
                "boss spawn must be transformed from blueprint to world coordinates");
        helper.assertTrue(
                layout.rooftop().contains(Vec3.atCenterOf(origin.offset(8, 14, 8))),
                "the roof max marker must be included in its trigger volume");
        helper.assertFalse(
                layout.rooftop().contains(Vec3.atCenterOf(origin.offset(9, 14, 8))),
                "the rooftop trigger must stop immediately after its authored max marker");
        helper.assertTrue(
                layout.innerTrigger().contains(Vec3.atCenterOf(origin.offset(2, 1, 2))),
                "the castle trigger must include its authored min marker");
        helper.assertValueEqual(
                layout.transitionLanding(),
                origin.offset(5, 2, 7),
                "the lower-plaza landing must transform from its authored marker");

        UnknownMedievalVanguard.Validation missingMarker =
                UnknownMedievalVanguard.validateAuthoredData(
                        markers.subList(0, markers.size() - 1),
                        localVanguard,
                        origin);
        helper.assertFalse(
                missingMarker.valid(),
                "a blueprint missing one trigger marker must be rejected");
        helper.assertTrue(
                missingMarker.describe().contains(UnknownMedievalVanguard.INNER_MAX_MARKER),
                "the marker diagnostic must name the missing structure block");

        UnknownMedievalVanguard.Validation unsafeSkin =
                UnknownMedievalVanguard.validateAuthoredData(
                        markers,
                        medievalVanguardFixture(true),
                        origin);
        helper.assertFalse(
                unsafeSkin.valid(),
                "an insecure defender skin must be rejected");
        helper.assertTrue(
                unsafeSkin.describe().contains("insecure/player skin"),
                "unsafe-skin rejection must explain the broken authoring contract");
        helper.succeed();
    }

    private static void unknownMedievalDefinitiveArena(GameTestHelper helper) {
        StructureTemplate template = helper.getLevel()
                .getStructureManager()
                .get(Identifier.fromNamespaceAndPath(
                        EchoesShowThePast.MOD_ID,
                        "boss/medieval_past"))
                .orElseThrow();
        helper.assertValueEqual(
                template.getSize(),
                new Vec3i(70, 39, 37),
                "the definitive castle must retain its complete subterranean selection");

        UnknownMedievalVanguard.Validation validation =
                UnknownMedievalVanguard.validate(
                        template,
                        TimelessDimensions.MEDIEVAL_ARENA_ORIGIN);
        helper.assertTrue(
                validation.valid(),
                "the definitive castle must satisfy its complete marker/entity contract: "
                        + validation.describe());
        UnknownMedievalVanguard.Layout layout = validation.layout().orElseThrow();
        helper.assertValueEqual(
                layout.bossSpawn(),
                TimelessDimensions.MEDIEVAL_ARENA_ORIGIN.offset(51, 36, 25),
                "the definitive boss anchor must land on the tower roof");
        helper.assertTrue(
                layout.rooftop().contains(Vec3.atBottomCenterOf(layout.bossSpawn())),
                "the boss anchor must be inside the authored roof trigger");
        helper.assertTrue(
                layout.innerTrigger().contains(Vec3.atCenterOf(
                        TimelessDimensions.MEDIEVAL_ARENA_ORIGIN.offset(57, 17, 26))),
                "the interior trigger must include the castle pedestal area");
        helper.assertValueEqual(
                layout.transitionLanding(),
                TimelessDimensions.MEDIEVAL_ARENA_ORIGIN.offset(12, 11, 18),
                "the dive must target the centre of the lower fighting plaza, not the blueprint centre");

        List<StructureTemplate.StructureBlockInfo> blocks =
                ((StructureTemplateAccessor) (Object) template)
                        .echoes$getPalettes()
                        .getFirst()
                        .blocks();
        long fightMarkers = blocks.stream()
                .filter(block -> block.state().is(Blocks.STRUCTURE_BLOCK)
                        && block.nbt() != null
                        && UnknownMedievalVanguard.isFightMarker(
                                block.nbt().getStringOr("metadata", "")))
                .count();
        helper.assertValueEqual(
                fightMarkers,
                6L,
                "the definitive arena must package each geometry marker exactly once");
        Map<Integer, Long> dirtPathByY = blocks.stream()
                .filter(block -> block.state().is(Blocks.DIRT_PATH))
                .collect(java.util.stream.Collectors.groupingBy(
                        block -> block.pos().getY(),
                        java.util.stream.Collectors.counting()));
        int dominantPathY = dirtPathByY.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow()
                .getKey();
        helper.assertValueEqual(
                dominantPathY,
                10,
                "the grass-path plaza must define the canonical arena floor");
        helper.assertValueEqual(
                TimelessDimensions.MEDIEVAL_ARENA_ORIGIN.getY() + dominantPathY,
                TimelessDimensions.FLOOR_Y,
                "the Medieval walking surface must align with the shared void floor");
        helper.assertTrue(
                blocks.stream().anyMatch(block -> block.pos().getY() > 0
                        && block.pos().getY() < 5
                        && !block.state().isAir()),
                "the authored subterranean trap must survive conversion below the foundation");
        helper.assertTrue(
                ArenaReconstructionWave.visibleTargetState(
                                Blocks.STRUCTURE_BLOCK.defaultBlockState())
                        .isAir(),
                "authoring markers must remain invisible throughout the reconstruction wave");
        helper.assertTrue(
                blocks.size() >= 22_000,
                "the packaged arena must contain the definitive castle rather than the old placeholder");

        List<StructureTemplate.StructureEntityInfo> roots =
                ((StructureTemplateAccessor) (Object) template).echoes$getEntityInfoList();
        helper.assertValueEqual(
                roots.size(),
                27,
                "the definitive arena must retain its infantry, archers and ambience");
        long humanoids = roots.stream()
                .filter(root -> "easy_npc:humanoid".equals(root.nbt.getStringOr("id", "")))
                .count();
        long horses = roots.stream()
                .filter(root -> "minecraft:horse".equals(root.nbt.getStringOr("id", "")))
                .count();
        long armorStands = roots.stream()
                .filter(root -> "minecraft:armor_stand".equals(root.nbt.getStringOr("id", "")))
                .count();
        helper.assertValueEqual(humanoids, 12L, "the arena must retain all 12 EasyNPC defenders as roots");
        helper.assertValueEqual(horses, 5L, "the arena must retain only its 5 ambient horses");
        helper.assertValueEqual(armorStands, 10L, "the arena must retain its 10 authored armor stands");
        helper.assertTrue(
                roots.stream().allMatch(root -> medievalHasTag(
                        root.nbt,
                        UnknownMedievalVanguard.TEMPORARY_TAG)),
                "every authored entity must be temporary so transitions and resets clean it");

        List<CompoundTag> entityTree = new ArrayList<>();
        roots.forEach(root -> collectMedievalEntityTree(root.nbt, entityTree));
        List<CompoundTag> authoredNpcs = entityTree.stream()
                .filter(data -> data.getStringOr("id", "").startsWith("easy_npc:"))
                .toList();
        helper.assertValueEqual(
                authoredNpcs.size(),
                12,
                "all 6 infantry and 6 archers must survive conversion");
        helper.assertTrue(
                authoredNpcs.stream().allMatch(data ->
                        !data.getCompoundOrEmpty("ObjectiveData")
                                .getListOrEmpty("ObjectiveDataSet")
                                .isEmpty()
                                && !data.getCompoundOrEmpty("equipment").isEmpty()),
                "EasyNPC objectives and equipment must remain exactly embedded in the arena NBT");

        helper.assertTrue(
                roots.stream().allMatch(root ->
                        root.nbt.getListOrEmpty("Passengers").isEmpty()),
                "the cavalry-free blueprint must contain no passenger hierarchy");
        helper.assertTrue(
                roots.stream()
                        .filter(root -> "minecraft:horse".equals(
                                root.nbt.getStringOr("id", "")))
                        .noneMatch(root -> medievalHasTag(
                                root.nbt,
                                UnknownMedievalVanguard.VANGUARD_TAG)),
                "ambient horses must never be promoted into the combat vanguard");
        helper.succeed();
    }

    private static void unknownMedievalEntitySanitizer(GameTestHelper helper) {
        CompoundTag markerData = new CompoundTag();
        markerData.putString("metadata", UnknownMedievalVanguard.ROOF_MIN_MARKER);
        StructureTemplate.StructureBlockInfo marker = new StructureTemplate.StructureBlockInfo(
                new BlockPos(3, 4, 5),
                Blocks.STRUCTURE_BLOCK.defaultBlockState(),
                markerData);
        StructureTemplate.StructureBlockInfo removed =
                UnknownMedievalArenaProcessor.INSTANCE.processBlock(
                        helper.getLevel(),
                        marker.pos(),
                        BlockPos.ZERO,
                        marker,
                        marker,
                        new StructurePlaceSettings());
        helper.assertTrue(
                removed != null && removed.state().isAir() && removed.nbt() == null,
                "medieval data markers must become air during placement");

        CompoundTag unrelatedData = new CompoundTag();
        unrelatedData.putString("metadata", "secret_chest");
        StructureTemplate.StructureBlockInfo unrelated = new StructureTemplate.StructureBlockInfo(
                BlockPos.ZERO,
                Blocks.STRUCTURE_BLOCK.defaultBlockState(),
                unrelatedData);
        helper.assertValueEqual(
                UnknownMedievalArenaProcessor.INSTANCE.processBlock(
                                helper.getLevel(),
                                BlockPos.ZERO,
                                BlockPos.ZERO,
                                unrelated,
                                unrelated,
                                new StructurePlaceSettings())
                        .state(),
                Blocks.STRUCTURE_BLOCK.defaultBlockState(),
                "the medieval processor must not erase unrelated authoring data");

        CompoundTag root = medievalNpcData(
                UnknownMedievalVanguard.OUTER_TAG,
                UnknownMedievalVanguard.INFANTRY_TAG,
                false);
        root.putIntArray("UUID", new int[] {1, 2, 3, 4});
        root.putIntArray("Owner", new int[] {5, 6, 7, 8});
        root.putIntArray("PresetUUID", new int[] {9, 10, 11, 12});
        root.putString("DeathLootTable", "minecraft:chests/simple_dungeon");
        ListTag oldMotion = new ListTag();
        oldMotion.add(net.minecraft.nbt.DoubleTag.valueOf(1.0D));
        oldMotion.add(net.minecraft.nbt.DoubleTag.valueOf(2.0D));
        oldMotion.add(net.minecraft.nbt.DoubleTag.valueOf(3.0D));
        root.put("Motion", oldMotion);
        root.putBoolean("OnGround", true);
        CompoundTag oldNavigation = new CompoundTag();
        CompoundTag oldHome = new CompoundTag();
        oldHome.putInt("X", -999);
        oldHome.putInt("Y", -999);
        oldHome.putInt("Z", -999);
        oldNavigation.put("Home", oldHome);
        root.put("Navigation", oldNavigation);
        CompoundTag equipment = new CompoundTag();
        equipment.putString("fixture", "preserved");
        root.put("EquipmentData", equipment);

        Vec3 placedPosition = new Vec3(120.8D, 77.2D, -31.1D);
        StructureTemplate.StructureEntityInfo authored =
                new StructureTemplate.StructureEntityInfo(
                        placedPosition,
                        BlockPos.containing(placedPosition),
                        root);
        StructureTemplate.StructureEntityInfo sanitized =
                UnknownMedievalArenaProcessor.INSTANCE.processEntity(
                        helper.getLevel(),
                        BlockPos.ZERO,
                        authored,
                        authored,
                        new StructurePlaceSettings(),
                        new StructureTemplate());
        BlockPos expectedHome = BlockPos.containing(placedPosition);
        CompoundTag sanitizedHome = sanitized.nbt
                .getCompoundOrEmpty("Navigation")
                .getCompoundOrEmpty("Home");
        helper.assertFalse(sanitized.nbt.contains("UUID"), "root UUID must be regenerated");
        helper.assertFalse(sanitized.nbt.contains("Owner"), "authored owner must not leak");
        helper.assertFalse(sanitized.nbt.contains("PresetUUID"), "preset identity must not leak");
        helper.assertFalse(sanitized.nbt.contains("DeathLootTable"), "authored loot must be disabled");
        helper.assertFalse(
                sanitized.nbt.contains("Motion") || sanitized.nbt.contains("OnGround"),
                "authored transient physics must not leak into a new arena instance");
        helper.assertValueEqual(
                sanitizedHome.getIntOr("X", Integer.MIN_VALUE),
                expectedHome.getX(),
                "EasyNPC home X must use the transformed entity position");
        helper.assertValueEqual(
                sanitizedHome.getIntOr("Y", Integer.MIN_VALUE),
                expectedHome.getY(),
                "EasyNPC home Y must use the transformed entity position");
        helper.assertValueEqual(
                sanitizedHome.getIntOr("Z", Integer.MIN_VALUE),
                expectedHome.getZ(),
                "EasyNPC home Z must use the transformed entity position");
        helper.assertTrue(
                sanitized.nbt.getBooleanOr("PersistenceRequired", false),
                "temporary defenders must remain loaded until explicit encounter cleanup");
        helper.assertFalse(
                sanitized.nbt.getBooleanOr("CanPickUpLoot", true),
                "temporary defenders must never acquire arena drops");
        helper.assertValueEqual(
                sanitized.nbt.getCompoundOrEmpty("EquipmentData").getStringOr("fixture", ""),
                "preserved",
                "equipment configuration must survive instance sanitation");
        helper.assertTrue(
                sanitized.nbt.getCompoundOrEmpty("SkinData").contains("Content"),
                "embedded skin content must survive instance sanitation");

        helper.assertTrue(
                root.contains("UUID")
                        && root.getCompoundOrEmpty("Navigation")
                                .getCompoundOrEmpty("Home")
                                .getIntOr("X", 0) == -999,
                "the processor must copy entity NBT instead of mutating the cached blueprint");
        helper.succeed();
    }

    private static void unknownMedievalDependencyContract(GameTestHelper helper) {
        try (InputStream manifest = EchoGameTests.class.getResourceAsStream(
                "/META-INF/neoforge.mods.toml")) {
            helper.assertTrue(manifest != null, "the processed NeoForge manifest must be packaged");
            String text = new String(manifest.readAllBytes(), StandardCharsets.UTF_8);
            helper.assertTrue(
                    text.contains("modId=\"easy_npc\"")
                            && text.contains("type=\"required\"")
                            && text.contains("versionRange=\"[7.4.2,8)\""),
                    "EasyNPC Core 7.4.2-7.x must be a required runtime dependency");
            helper.assertFalse(
                    text.contains("modId=\"easy_npc_config_ui\""),
                    "EasyNPC Config UI must remain an optional local authoring tool");
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect the processed NeoForge manifest", exception);
        }
        helper.succeed();
    }

    private static void unknownMedievalPastCombat(GameTestHelper helper) {
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.COMBO_FIRST_LOCK_TICK,
                9,
                "the first direction must lock after the complete tracking windup");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.COMBO_FIRST_STEP_END_TICK
                        - UnknownMedievalCombatGoal.COMBO_FIRST_STEP_START_TICK
                        + 1,
                4,
                "the opening step must be distributed over four validated ticks");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.COMBO_FIRST_ACTIVE_START_TICK,
                13,
                "medieval combo first cut window");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.COMBO_ACTIVE_TICKS,
                4,
                "every medieval sword path must remain active for four samples");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.COMBO_SWEEP_ACTIVE_START_TICK,
                26,
                "medieval combo sweep finisher window");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.COMBO_BRANCH_LOCK_TICK,
                20,
                "medieval combo branch lock");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.COMBO_BRANCH_READ_TICKS,
                6,
                "the close/pursuit pose must be readable for six complete ticks");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.COMBO_SWEEP_ACTIVE_START_TICK
                        - UnknownMedievalCombatGoal.COMBO_BRANCH_LOCK_TICK,
                6,
                "the close finisher cannot start before its six-tick read");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.COMBO_CHASE_ACTIVE_START_TICK,
                29,
                "the pursuit diagonal must wait until its validated advance finishes");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.COMBO_SWEEP_END_TICK,
                46,
                "medieval combo sweep end");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.COMBO_CHASE_END_TICK,
                51,
                "medieval combo chase end");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.COMBO_FIRST_DAMAGE,
                6.0F,
                "medieval first cut damage");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.COMBO_SECOND_DAMAGE,
                7.0F,
                "medieval second cut damage");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.SWORD_REACH,
                3.15D,
                "medieval sword reach");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.COMBO_FIRST_STEP_DISTANCE,
                0.60D,
                "the opening step cap must remain exact");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.COMBO_CHASE_STEP_DISTANCE,
                1.08D,
                "the pursuit step cap must remain exact");
        helper.assertTrue(
                UnknownMedievalCombatGoal.COMBO_FIRST_BLOCKED_DAMAGE == 1.0F
                        && UnknownMedievalCombatGoal.COMBO_SECOND_BLOCKED_DAMAGE == 2.0F
                        && UnknownMedievalCombatGoal.COMBO_FIRST_BLOCK_DURABILITY == 2
                        && UnknownMedievalCombatGoal.COMBO_SECOND_BLOCK_DURABILITY == 3
                        && UnknownMedievalCombatGoal.COMBO_FIRST_BLOCK_FLINCH_TICKS == 4
                        && UnknownMedievalCombatGoal.COMBO_SECOND_BLOCK_FLINCH_TICKS == 6,
                "blocking must pay the authored chip, durability and short flinch costs");
        helper.assertTrue(
                Math.abs(UnknownMedievalCombatGoal.COMBO_FIRST_BLOCK_KNOCKBACK - 0.18D) < 1.0E-8D
                        && Math.abs(UnknownMedievalCombatGoal.COMBO_SECOND_BLOCK_KNOCKBACK - 0.32D)
                                < 1.0E-8D,
                "each blocked beat must use its own restrained knockback");

        helper.assertValueEqual(
                UnknownMedievalCombatGoal.OVERHEAD_WINDUP_TICKS,
                22,
                "overhead windup");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.OVERHEAD_LOCK_TICK,
                14,
                "overhead direction lock");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.OVERHEAD_DAMAGE,
                9.0F,
                "overhead damage");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.OVERHEAD_BLOCKED_DAMAGE,
                4.0F,
                "blocked overhead damage");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.OVERHEAD_SHIELD_DISABLE_TICKS,
                40,
                "overhead shield disable");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.OVERHEAD_RECOVERY_TICKS,
                24,
                "overhead recovery");

        helper.assertValueEqual(
                UnknownMedievalCombatGoal.SHIELD_BASH_WINDUP_TICKS,
                10,
                "shield bash windup");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.SHIELD_BASH_DAMAGE,
                4.0F,
                "shield bash damage");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.SHIELD_BASH_COOLDOWN_TICKS,
                50,
                "shield bash cooldown");
        helper.assertTrue(
                UnknownMedievalCombatGoal.SHIELD_BASH_KNOCKBACK >= 1.4D,
                "shield bash must threaten the rooftop edge with strong knockback");

        helper.assertValueEqual(
                UnknownMedievalCombatGoal.GUARD_ARC_DEGREES,
                115.0D,
                "medieval guard arc");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.GUARD_COOLDOWN_TICKS,
                60,
                "medieval defense cooldown");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.RIPOSTE_TELEGRAPH_TICKS,
                8,
                "riposte warning");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.RIPOSTE_DAMAGE,
                6.0F,
                "riposte damage");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.RIPOSTE_BLOCKED_DAMAGE,
                3.0F,
                "blocked riposte damage");
        helper.assertFalse(
                UnknownMedievalCombatGoal.isGuardActive(
                        UnknownCombatState.MEDIEVAL_GUARD,
                        UnknownMedievalCombatGoal.GUARD_WINDUP_TICKS - 1),
                "guard must not block before its authored window");
        helper.assertTrue(
                UnknownMedievalCombatGoal.isGuardActive(
                                UnknownCombatState.MEDIEVAL_GUARD,
                                UnknownMedievalCombatGoal.GUARD_WINDUP_TICKS)
                        && UnknownMedievalCombatGoal.isGuardActive(
                                UnknownCombatState.MEDIEVAL_GUARD,
                                UnknownMedievalCombatGoal.GUARD_WINDUP_TICKS
                                        + UnknownMedievalCombatGoal.GUARD_ACTIVE_TICKS
                                        - 1),
                "guard must cover its complete short active window");
        helper.assertFalse(
                UnknownMedievalCombatGoal.isGuardActive(
                                UnknownCombatState.MEDIEVAL_GUARD,
                                UnknownMedievalCombatGoal.GUARD_WINDUP_TICKS
                                        + UnknownMedievalCombatGoal.GUARD_ACTIVE_TICKS)
                        || UnknownMedievalCombatGoal.isGuardActive(
                                UnknownCombatState.MEDIEVAL_RIPOSTE,
                                0),
                "guard must end before recovery and riposte must remain punishable");
        helper.assertTrue(
                UnknownMedievalCombatGoal.shouldTriggerRiposte(true, false, true, 3.0D),
                "a close owner melee hit must trigger the guarded riposte");
        helper.assertFalse(
                UnknownMedievalCombatGoal.shouldTriggerRiposte(true, true, true, 3.0D)
                        || UnknownMedievalCombatGoal.shouldTriggerRiposte(true, false, false, 3.0D)
                        || UnknownMedievalCombatGoal.shouldTriggerRiposte(true, false, true, 5.0D),
                "projectiles, other attackers and unreachable melee hits must never trigger riposte");
        helper.assertValueEqual(
                Byte.toUnsignedInt(UnknownCombatState.MEDIEVAL_COMBO.networkId()),
                14,
                "new states must append without changing existing network ids");
        helper.assertValueEqual(
                Byte.toUnsignedInt(UnknownCombatState.MEDIEVAL_RIPOSTE.networkId()),
                18,
                "Medieval Past state range must remain contiguous");
        helper.assertValueEqual(
                UnknownMedievalCombatMath.selectComboVariant(
                        2.85D,
                        UnknownMedievalCombatGoal.COMBO_BRANCH_DISTANCE),
                UnknownEntity.COMBAT_VARIANT_MEDIEVAL_SWEEP,
                "the exact threshold must select the close reverse sweep");
        helper.assertValueEqual(
                UnknownMedievalCombatMath.selectComboVariant(
                        Math.nextUp(2.85D),
                        UnknownMedievalCombatGoal.COMBO_BRANCH_DISTANCE),
                UnknownEntity.COMBAT_VARIANT_MEDIEVAL_CHASE,
                "the first representable distance above the threshold must select pursuit");
        Vec3 turnOrigin = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 turnTarget = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 sweepTurn = UnknownMedievalCombatMath.limitedHorizontalTurn(
                turnOrigin,
                turnTarget,
                UnknownMedievalCombatGoal.COMBO_SWEEP_MAX_TURN_DEGREES);
        Vec3 chaseTurn = UnknownMedievalCombatMath.limitedHorizontalTurn(
                turnOrigin,
                turnTarget,
                UnknownMedievalCombatGoal.COMBO_CHASE_MAX_TURN_DEGREES);
        double sweepTurnDegrees = Math.toDegrees(Math.acos(Math.clamp(
                turnOrigin.dot(sweepTurn),
                -1.0D,
                1.0D)));
        double chaseTurnDegrees = Math.toDegrees(Math.acos(Math.clamp(
                turnOrigin.dot(chaseTurn),
                -1.0D,
                1.0D)));
        helper.assertTrue(
                Math.abs(sweepTurnDegrees - 20.0D) < 1.0E-6D
                        && Math.abs(chaseTurnDegrees - 35.0D) < 1.0E-6D,
                "the two branch locks must enforce their exact 20/35-degree cones");
        helper.assertTrue(
                UnknownMedievalCombatMath.mayApplyCutHit(false, true)
                        && !UnknownMedievalCombatMath.mayApplyCutHit(true, true)
                        && !UnknownMedievalCombatMath.mayApplyCutHit(false, false),
                "one cut may resolve once across four samples and never twice");
        helper.assertValueEqual(
                UnknownEntity.COMBAT_FX_MEDIEVAL_BLOCK,
                (byte) 9,
                "the previous final FX id must remain wire-stable");
        helper.assertTrue(
                UnknownEntity.COMBAT_FX_MEDIEVAL_CUT_HIT == 10
                        && UnknownEntity.COMBAT_FX_MEDIEVAL_CUT_BLOCK == 11,
                "new medieval impact FX ids must append at the end");
        helper.assertTrue(
                UnknownCombatImpactPayload.TYPE != null
                        && UnknownCombatImpactPayload.FIRST_CUT == 0
                        && UnknownCombatImpactPayload.FINISHER == 1,
                "the owner-only camera payload must preserve its two stable beat ids");

        helper.assertValueEqual(
                UnknownMedievalCombatGoal.chooseAttack(
                        2.8D,
                        true,
                        0,
                        0,
                        0,
                        0,
                        UnknownMedievalCombatGoal.Attack.NONE),
                UnknownMedievalCombatGoal.Attack.SHIELD_BASH,
                "a raised player shield must prioritize bash");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.chooseAttack(
                        3.0D,
                        false,
                        20,
                        0,
                        1,
                        1,
                        UnknownMedievalCombatGoal.Attack.COMBO),
                UnknownMedievalCombatGoal.Attack.GUARD,
                "defense must be inserted as a short response rather than permanent stance");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.chooseAttack(
                        3.0D,
                        false,
                        20,
                        20,
                        1,
                        1,
                        UnknownMedievalCombatGoal.Attack.COMBO),
                UnknownMedievalCombatGoal.Attack.OVERHEAD,
                "overhead must remain available while defense cools down");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.chooseAttack(
                        3.0D,
                        false,
                        20,
                        20,
                        UnknownMedievalCombatGoal.MAX_OFFENSIVE_CHAIN,
                        2,
                        UnknownMedievalCombatGoal.Attack.OVERHEAD),
                UnknownMedievalCombatGoal.Attack.NONE,
                "a third consecutive offensive action must be rejected");
        helper.assertValueEqual(
                UnknownMedievalCombatGoal.neutralDelayAfterOffense(2),
                UnknownMedievalCombatGoal.FORCED_BREATHER_TICKS,
                "two attacks must force a readable breather");

        Vec3 origin = Vec3.ZERO;
        Vec3 forward = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 insideGuardEdge = new Vec3(
                Math.sin(Math.toRadians(57.0D)),
                0.0D,
                Math.cos(Math.toRadians(57.0D)));
        Vec3 outsideGuardEdge = new Vec3(
                Math.sin(Math.toRadians(58.0D)),
                0.0D,
                Math.cos(Math.toRadians(58.0D)));
        helper.assertTrue(
                UnknownGreekCombatMath.isInsideFrontArc(
                        forward,
                        insideGuardEdge,
                        UnknownMedievalCombatGoal.GUARD_ARC_DEGREES),
                "the 115-degree guard must include threats just inside its authored edge");
        helper.assertFalse(
                UnknownGreekCombatMath.isInsideFrontArc(
                        forward,
                        outsideGuardEdge,
                        UnknownMedievalCombatGoal.GUARD_ARC_DEGREES),
                "the 115-degree guard must expose attacks just outside its edge");
        Vec3 firstPathTarget = UnknownEgyptianCombatMath.rotateHorizontal(
                forward,
                -55.0D).scale(2.7D);
        helper.assertTrue(
                UnknownMedievalCombatMath.sweptSwordPathContains(
                        origin,
                        forward,
                        firstPathTarget,
                        UnknownMedievalCombatGoal.COMBO_INNER_RADIUS,
                        UnknownMedievalCombatGoal.SWORD_REACH,
                        0.3D,
                        -78.0D,
                        -43.0D,
                        1.5D),
                "a valid frontal position must intersect the first authoritative sword sample");
        helper.assertFalse(
                UnknownMedievalCombatMath.sweptSwordPathContains(
                                origin,
                                forward,
                                new Vec3(0.0D, 0.0D, -2.0D),
                                UnknownMedievalCombatGoal.COMBO_INNER_RADIUS,
                                UnknownMedievalCombatGoal.SWORD_REACH,
                                0.3D,
                                UnknownMedievalCombatGoal.COMBO_FIRST_START_DEGREES,
                                UnknownMedievalCombatGoal.COMBO_FIRST_END_DEGREES,
                                1.5D)
                        || UnknownMedievalCombatMath.sweptSwordPathContains(
                                origin,
                                forward,
                                new Vec3(0.0D, 0.0D, 3.8D),
                                UnknownMedievalCombatGoal.COMBO_INNER_RADIUS,
                                UnknownMedievalCombatGoal.SWORD_REACH,
                                0.3D,
                                UnknownMedievalCombatGoal.COMBO_FIRST_START_DEGREES,
                                UnknownMedievalCombatGoal.COMBO_FIRST_END_DEGREES,
                                1.5D)
                        || UnknownMedievalCombatMath.sweptSwordPathContains(
                                origin,
                                forward,
                                new Vec3(0.0D, 2.2D, 2.0D),
                                UnknownMedievalCombatGoal.COMBO_INNER_RADIUS,
                                UnknownMedievalCombatGoal.SWORD_REACH,
                                0.3D,
                                UnknownMedievalCombatGoal.COMBO_FIRST_START_DEGREES,
                                UnknownMedievalCombatGoal.COMBO_FIRST_END_DEGREES,
                                1.5D),
                "rear, out-of-reach and out-of-height targets must evade the swept sword path");
        helper.assertTrue(
                UnknownMedievalCombatMath.windowProgress(0, 0, 4) == 0.0D
                        && UnknownMedievalCombatMath.windowProgress(2, 0, 4) == 0.5D
                        && UnknownMedievalCombatMath.windowProgress(4, 0, 4) == 1.0D,
                "the four active samples must expose stable normalized path progress");
        helper.assertTrue(
                UnknownMedievalCombatMath.overheadLaneContains(
                        origin,
                        forward,
                        new Vec3(0.55D, 0.0D, 2.6D),
                        UnknownMedievalCombatGoal.SWORD_REACH,
                        UnknownMedievalCombatGoal.OVERHEAD_HIT_RADIUS,
                        1.5D),
                "the overhead lane must cover its visible central strike");
        helper.assertFalse(
                UnknownMedievalCombatMath.overheadLaneContains(
                        origin,
                        forward,
                        new Vec3(1.4D, 0.0D, 2.6D),
                        UnknownMedievalCombatGoal.SWORD_REACH,
                        UnknownMedievalCombatGoal.OVERHEAD_HIT_RADIUS,
                        1.5D),
                "a lateral dodge must escape the locked overhead lane");
        AABB rooftop = new AABB(0.0D, 0.0D, 0.0D, 8.0D, 6.0D, 8.0D);
        helper.assertTrue(
                UnknownMedievalVanguard.bossFootprintInside(
                        rooftop,
                        new AABB(3.4D, 1.0D, 3.4D, 4.6D, 3.4D, 4.6D)),
                "the boss footprint must be accepted when fully contained by the rooftop");
        helper.assertFalse(
                UnknownMedievalVanguard.bossFootprintInside(
                        rooftop,
                        new AABB(-0.2D, 1.0D, 3.4D, 1.0D, 3.4D, 4.6D)),
                "a boss whose body crosses the roof edge must be restored to its anchor");

        UnknownEntity boss = EchoesShowThePast.UNKNOWN.get().create(
                helper.getLevel(),
                EntitySpawnReason.TRIGGERED);
        helper.assertTrue(boss != null, "the medieval Unknown fixture must spawn");
        boss.setEra(UnknownEntity.ERA_MEDIEVAL);
        boss.beginGreekCombatState(
                UnknownCombatState.MEDIEVAL_COMBO,
                helper.getLevel().getGameTime(),
                false);
        boss.setCombatVariant(UnknownEntity.COMBAT_VARIANT_MEDIEVAL_CHASE);
        helper.assertValueEqual(
                boss.getCombatVariant(),
                UnknownEntity.COMBAT_VARIANT_MEDIEVAL_CHASE,
                "the branch byte must synchronize independently from the combat-state id");
        boss.resetGreekCombat();
        helper.assertValueEqual(
                boss.getCombatVariant(),
                UnknownEntity.COMBAT_VARIANT_OPENING,
                "ending, falling out of combat or changing stage must reset the branch byte");
        UnknownFightManager.equipEraWeapon(boss);
        helper.assertTrue(
                boss.getMainHandItem().is(EchoesShowThePast.UNKNOWN_MEDIEVAL_SWORD.get())
                        && boss.getOffhandItem().is(EchoesShowThePast.UNKNOWN_MEDIEVAL_SHIELD.get()),
                "Medieval Past must equip its two private weapon props");
        helper.assertTrue(
                boss.getItemBySlot(EquipmentSlot.HEAD)
                                .is(EchoesShowThePast.UNKNOWN_MEDIEVAL_HELMET.get())
                        && boss.getItemBySlot(EquipmentSlot.CHEST)
                                .is(EchoesShowThePast.UNKNOWN_MEDIEVAL_CHESTPLATE.get())
                        && boss.getItemBySlot(EquipmentSlot.LEGS)
                                .is(EchoesShowThePast.UNKNOWN_MEDIEVAL_LEGGINGS.get())
                        && boss.getItemBySlot(EquipmentSlot.FEET)
                                .is(EchoesShowThePast.UNKNOWN_MEDIEVAL_BOOTS.get()),
                "the complete medieval armor loadout must be equipped server-side");

        try (InputStream animations = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/geckolib/animations/entity/unknown.animation.json");
                InputStream swordModel = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/models/item/internal/unknown_medieval_sword.json");
                InputStream shieldModel = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/models/item/internal/unknown_medieval_shield.json");
                InputStream swordTexture = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/textures/item/internal/unknown_medieval_sword.png");
                InputStream shieldTexture = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/textures/item/internal/unknown_medieval_shield.png");
                InputStream sounds = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/sounds.json")) {
            helper.assertTrue(animations != null, "Unknown animations must be packaged");
            helper.assertTrue(
                    swordModel != null && shieldModel != null,
                    "definitive internal sword and heater-shield models must be packaged");
            helper.assertTrue(
                    swordTexture != null && shieldTexture != null,
                    "both private medieval weapons need their own texture atlas");
            helper.assertTrue(sounds != null, "the sound-event manifest must be packaged");
            JsonObject clips = JsonParser.parseString(
                            new String(animations.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonObject("animations");
            helper.assertTrue(
                    clips.has("combat.medieval.combo")
                            && clips.has("combat.medieval.combo_sweep")
                            && clips.has("combat.medieval.combo_chase")
                            && clips.has("combat.medieval.overhead")
                            && clips.has("combat.medieval.shield_bash")
                            && clips.has("combat.medieval.guard")
                            && clips.has("combat.medieval.riposte"),
                    "the three combo clips and unchanged Medieval Past contracts must resolve");
            String[] comboClips = {
                "combat.medieval.combo",
                "combat.medieval.combo_sweep",
                "combat.medieval.combo_chase"
            };
            double[] expectedLengths = {1.0D, 1.3D, 1.55D};
            int[] expectedArmKeyCounts = {7, 8, 9};
            double[] swordArmLimits = {58.0D, 54.0D, 72.0D};
            String[] requiredBones = {
                "root", "pelvis", "body", "chest", "head", "arm_right", "main_hand",
                "arm_left", "off_hand", "leg_right", "leg_left"
            };
            for (int clipIndex = 0; clipIndex < comboClips.length; clipIndex++) {
                JsonObject clip = clips.getAsJsonObject(comboClips[clipIndex]);
                helper.assertTrue(
                        Math.abs(clip.get("animation_length").getAsDouble()
                                        - expectedLengths[clipIndex])
                                < 1.0E-6D,
                        comboClips[clipIndex] + " must align exactly with the server timeline");
                JsonObject animatedBones = clip.getAsJsonObject("bones");
                for (String requiredBone : requiredBones) {
                    helper.assertTrue(
                            animatedBones.has(requiredBone),
                            comboClips[clipIndex] + " must animate " + requiredBone);
                }
                for (String armBone : List.of(
                        "arm_right", "main_hand", "arm_left", "off_hand")) {
                    JsonObject rotation = animatedBones.getAsJsonObject(armBone)
                            .getAsJsonObject("rotation");
                    helper.assertValueEqual(
                            rotation.size(),
                            expectedArmKeyCounts[clipIndex],
                            comboClips[clipIndex] + " must retain every authored "
                                    + armBone + " key");
                    double limit = switch (armBone) {
                        case "arm_right" -> swordArmLimits[clipIndex];
                        case "main_hand" -> 5.0D;
                        case "arm_left" -> 49.0D;
                        case "off_hand" -> 4.0D;
                        default -> throw new IllegalStateException("Unexpected arm bone " + armBone);
                    };
                    for (var key : rotation.entrySet()) {
                        helper.assertTrue(
                                key.getValue().isJsonArray(),
                                comboClips[clipIndex] + "/" + armBone
                                        + " must remain linear to prevent interpolation overshoot");
                        for (var component : key.getValue().getAsJsonArray()) {
                            helper.assertTrue(
                                    Math.abs(component.getAsDouble()) <= limit + 1.0E-6D,
                                    comboClips[clipIndex] + "/" + armBone
                                            + " exceeds its authored anatomical limit");
                        }
                    }
                }
            }
            JsonObject openingBones = clips.getAsJsonObject("combat.medieval.combo")
                    .getAsJsonObject("bones");
            for (String branchName : List.of(
                    "combat.medieval.combo_sweep",
                    "combat.medieval.combo_chase")) {
                JsonObject branchBones = clips.getAsJsonObject(branchName).getAsJsonObject("bones");
                for (String requiredBone : requiredBones) {
                    JsonArray openingPose = openingBones
                            .getAsJsonObject(requiredBone)
                            .getAsJsonObject("rotation")
                            .getAsJsonArray("1.0");
                    JsonArray branchPose = branchBones
                            .getAsJsonObject(requiredBone)
                            .getAsJsonObject("rotation")
                            .getAsJsonArray("0.0");
                    helper.assertValueEqual(
                            branchPose,
                            openingPose,
                            branchName + " must begin on the exact shared pose for " + requiredBone);
                }
            }

            JsonObject sword = JsonParser.parseString(
                    new String(swordModel.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject shield = JsonParser.parseString(
                    new String(shieldModel.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            helper.assertTrue(
                    !sword.has("parent")
                            && !shield.has("parent")
                            && sword.getAsJsonArray("elements").size() >= 8
                            && shield.getAsJsonArray("elements").size() >= 14,
                    "weapons must be deliberate custom 3D models, never vanilla parents");
            for (JsonObject model : List.of(sword, shield)) {
                for (var modelElement : model.getAsJsonArray("elements")) {
                    JsonObject element = modelElement.getAsJsonObject();
                    helper.assertTrue(
                            !element.has("rotation"),
                            "medieval weapons must keep a block-built Minecraft silhouette");
                    for (String corner : List.of("from", "to")) {
                        for (var coordinate : element.getAsJsonArray(corner)) {
                            double doubled = coordinate.getAsDouble() * 2.0D;
                            helper.assertTrue(
                                    Math.abs(doubled - Math.rint(doubled)) < 1.0E-6D,
                                    "medieval weapon geometry must stay on the half-pixel grid");
                        }
                    }
                }
            }
            var swordAtlas = ImageIO.read(swordTexture);
            var shieldAtlas = ImageIO.read(shieldTexture);
            helper.assertTrue(
                    swordAtlas != null
                            && shieldAtlas != null
                            && swordAtlas.getWidth() == 64
                            && swordAtlas.getHeight() == 64
                            && shieldAtlas.getWidth() == 64
                            && shieldAtlas.getHeight() == 64,
                    "both weapon atlases must decode at exactly 64x64");

            JsonObject soundEvents = JsonParser.parseString(
                    new String(sounds.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            helper.assertTrue(
                    soundEvents.getAsJsonObject("unknown_medieval_sword_attack")
                                    .getAsJsonArray("sounds").size()
                            == 10
                            && soundEvents.getAsJsonObject("unknown_medieval_sword_clash")
                                    .getAsJsonArray("sounds").size()
                            == 10,
                    "the two medieval sound events must expose all ten random variants each");
            for (String prefix : List.of("attack_", "clash_")) {
                for (int variant = 1; variant <= 10; variant++) {
                    try (InputStream ogg = EchoGameTests.class.getResourceAsStream(
                            "/assets/echoes_show_the_past/sounds/unknown_medieval/"
                                    + prefix + variant + ".ogg")) {
                        helper.assertTrue(
                                ogg != null && ogg.readNBytes(4).length == 4,
                                prefix + variant + ".ogg must be packaged and non-empty");
                    }
                }
            }

            Path projectRoot = Path.of("").toAbsolutePath().normalize();
            if (!Files.isRegularFile(projectRoot.resolve("gradlew.bat"))
                    && projectRoot.getParent() != null
                    && Files.isRegularFile(projectRoot.getParent().resolve("gradlew.bat"))) {
                projectRoot = projectRoot.getParent();
            }
            Path masterPath = projectRoot.resolve(Path.of(
                    "art", "boss_unknown", "animations", "unknown_medieval_combat.bbmodel"));
            helper.assertTrue(Files.isRegularFile(masterPath), "the canonical combat bbmodel must exist");
            helper.assertTrue(
                    Files.isRegularFile(projectRoot.resolve(Path.of(
                            "art",
                            "boss_unknown",
                            "weapons",
                            "medieval",
                            "unknown_medieval_sword.bbmodel")))
                            && Files.isRegularFile(projectRoot.resolve(Path.of(
                                    "art",
                                    "boss_unknown",
                                    "weapons",
                                    "medieval",
                                    "unknown_medieval_shield.bbmodel"))),
                    "medieval sword and shield must keep Blockbench sources beside the other era weapons");
            JsonObject master = JsonParser.parseString(Files.readString(masterPath))
                    .getAsJsonObject();
            helper.assertValueEqual(
                    master.getAsJsonArray("animations").size(),
                    3,
                    "the canonical Blockbench project must own exactly the three combo clips");
            for (var masterClipElement : master.getAsJsonArray("animations")) {
                JsonObject masterClip = masterClipElement.getAsJsonObject();
                helper.assertTrue(
                        List.of(comboClips).contains(masterClip.get("name").getAsString())
                                && masterClip.getAsJsonObject("animators").size() >= 11,
                        "each master clip must retain the complete animated armor rig");
            }
            helper.assertTrue(
                    Files.notExists(projectRoot.resolve(Path.of(
                                    "src/main/resources/assets/echoes_show_the_past/models/item/internal",
                                    "unknown_medieval_sword_placeholder.json")))
                            && Files.notExists(projectRoot.resolve(Path.of(
                                    "src/main/resources/assets/echoes_show_the_past/models/item/internal",
                                    "unknown_medieval_shield_placeholder.json"))),
                    "vanilla-parent placeholder files must be removed");
            String notices = Files.readString(projectRoot.resolve("THIRD_PARTY_NOTICES.md"));
            helper.assertTrue(
                    notices.contains("StarNinjas")
                            && notices.contains("CC0 1.0 Universal")
                            && notices.contains("20-sword-sound-effects-attacks-and-clashes"),
                    "the original sound pack, author, source and CC0 license must be documented");
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect Medieval Past assets", exception);
        }
        helper.succeed();
    }

    private static void unknownMedievalComboMovement(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
            }
        }
        BlockPos trap = new BlockPos(4, 0, 5);
        helper.setBlock(trap, Blocks.PISTON);

        UnknownEntity boss = EchoesShowThePast.UNKNOWN.get().create(
                helper.getLevel(),
                EntitySpawnReason.TRIGGERED);
        helper.assertTrue(boss != null, "the movement fixture must create an Unknown");
        Vec3 start = helper.absoluteVec(new Vec3(4.5D, 2.0D, 4.5D));
        boss.setPos(start);
        helper.getLevel().addFreshEntity(boss);
        helper.assertTrue(
                UnknownBossMovementSafety.moveGroundStepNonDestructive(
                        helper.getLevel(), boss, new Vec3(0.0D, 0.0D, 0.5D)),
                "a supported unobstructed combo step must move physically");

        boss.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
        BlockPos wall = new BlockPos(4, 2, 5);
        helper.setBlock(wall, Blocks.STONE);
        helper.assertFalse(
                UnknownBossMovementSafety.moveGroundStepNonDestructive(
                        helper.getLevel(), boss, new Vec3(0.0D, 0.0D, 1.0D)),
                "a wall must stop the combo step before movement begins");
        helper.assertBlockPresent(Blocks.STONE, wall);
        helper.setBlock(wall, Blocks.AIR);

        boss.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
        for (int x = 3; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 5), Blocks.AIR);
        }
        helper.assertFalse(
                UnknownBossMovementSafety.moveGroundStepNonDestructive(
                        helper.getLevel(), boss, new Vec3(0.0D, 0.0D, 1.0D)),
                "a complete support gap must stop the combo step");

        boss.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
        for (int x = 3; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 5), Blocks.MAGMA_BLOCK);
        }
        helper.assertFalse(
                UnknownBossMovementSafety.moveGroundStepNonDestructive(
                        helper.getLevel(), boss, new Vec3(0.0D, 0.0D, 1.0D)),
                "a dangerous landing must stop the combo step without clearing it");
        helper.assertBlockPresent(Blocks.MAGMA_BLOCK, new BlockPos(4, 1, 5));
        helper.assertBlockPresent(Blocks.PISTON, trap);
        boss.discard();
        helper.succeed();
    }

    private static void unknownMedievalRedstone(GameTestHelper helper) {
        UnknownFightManager.ArenaBounds fixtureBounds =
                new UnknownFightManager.ArenaBounds(
                        BlockPos.ZERO,
                        new Vec3i(8, 8, 8));
        BlockPos fixturePiston = new BlockPos(2, 2, 2);
        BlockPos fixtureFace = fixturePiston.east();
        BlockPos fixtureCargo = fixtureFace;
        helper.assertTrue(
                UnknownFightManager.isContainedArenaPistonMovement(
                        fixtureBounds,
                        Set.of(),
                        fixturePiston,
                        fixtureFace,
                        Direction.EAST,
                        List.of(fixtureCargo),
                        List.of()),
                "an authored piston and its destination wholly inside the arena must be allowed");
        helper.assertFalse(
                UnknownFightManager.isContainedArenaPistonMovement(
                        fixtureBounds,
                        Set.of(),
                        new BlockPos(6, 2, 2),
                        new BlockPos(7, 2, 2),
                        Direction.EAST,
                        List.of(new BlockPos(7, 2, 2)),
                        List.of()),
                "an internal piston must not push arena geometry beyond the protected volume");
        helper.assertFalse(
                UnknownFightManager.isContainedArenaPistonMovement(
                        fixtureBounds,
                        Set.of(fixtureCargo.east()),
                        fixturePiston,
                        fixtureFace,
                        Direction.EAST,
                        List.of(fixtureCargo),
                        List.of()),
                "an internal piston must not move a block into an immutable altar cell");

        int passiveFlags = Block.UPDATE_CLIENTS
                | Block.UPDATE_KNOWN_SHAPE
                | Block.UPDATE_SUPPRESS_DROPS
                | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS
                | Block.UPDATE_SKIP_ON_PLACE;
        BlockPos piston = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos cargo = piston.east();
        BlockPos destination = cargo.east();
        BlockPos power = piston.north();
        BlockState retracted = Blocks.STICKY_PISTON.defaultBlockState()
                .setValue(PistonBaseBlock.FACING, Direction.EAST)
                .setValue(PistonBaseBlock.EXTENDED, false);
        helper.getLevel().setBlock(cargo, Blocks.GOLD_BLOCK.defaultBlockState(), passiveFlags);
        helper.getLevel().setBlock(power, Blocks.REDSTONE_BLOCK.defaultBlockState(), passiveFlags);
        helper.getLevel().setBlock(piston, retracted, passiveFlags);
        helper.assertFalse(
                helper.getLevel().getBlockState(piston).getValue(PistonBaseBlock.EXTENDED),
                "passive arena placement must not activate the piston before reconciliation");
        helper.assertValueEqual(
                UnknownFightManager.reconcileRedstonePositions(
                        helper.getLevel(),
                        List.of(piston)),
                1,
                "the authored piston must participate in the focused redstone bootstrap");

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(
                    helper.getLevel().getBlockState(piston).is(Blocks.STICKY_PISTON)
                            && helper.getLevel()
                                    .getBlockState(piston)
                                    .getValue(PistonBaseBlock.EXTENDED),
                    "reconciliation must extend a silently placed powered piston");
            helper.assertTrue(
                    helper.getLevel().getBlockState(destination).is(Blocks.GOLD_BLOCK),
                    "the extending piston must move its cargo exactly one block");

            helper.getLevel().setBlock(power, Blocks.AIR.defaultBlockState(), passiveFlags);
            helper.assertValueEqual(
                    UnknownFightManager.reconcileRedstonePositions(
                            helper.getLevel(),
                            List.of(piston)),
                    1,
                    "the same bootstrap must reconcile a silently removed power source");
        });
        helper.runAfterDelay(11, () -> {
            helper.assertTrue(
                    helper.getLevel().getBlockState(piston).is(Blocks.STICKY_PISTON)
                            && !helper.getLevel()
                                    .getBlockState(piston)
                                    .getValue(PistonBaseBlock.EXTENDED),
                    "the authored sticky piston must retract after losing power");
            helper.assertTrue(
                    helper.getLevel().getBlockState(cargo).is(Blocks.GOLD_BLOCK)
                            && helper.getLevel().getBlockState(destination).isAir(),
                    "retraction must pull the cargo back without duplicating or deleting it");
            helper.succeed();
        });
    }

    private static void unknownMedievalRuinsCombat(GameTestHelper helper) {
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.ANIMATION_SPEED,
                1.12F,
                "Ruins must run exactly twelve percent faster");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.COMBO_FIRST_HIT_TICK,
                12,
                "Ruins first cut timing");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.COMBO_SECOND_HIT_TICK,
                23,
                "Ruins second cut timing");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.COMBO_THIRD_HIT_TICK,
                38,
                "the third cut must remain deliberately delayed");
        helper.assertTrue(
                UnknownMedievalRuinsCombatGoal.COMBO_THIRD_HIT_TICK
                        - UnknownMedievalRuinsCombatGoal.COMBO_SECOND_HIT_TICK
                        > UnknownMedievalRuinsCombatGoal.COMBO_SECOND_HIT_TICK
                                - UnknownMedievalRuinsCombatGoal.COMBO_FIRST_HIT_TICK,
                "the third cut delay must punish an early second dodge");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.COMBO_FIRST_DAMAGE,
                6.0F,
                "Ruins combo first damage");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.COMBO_SECOND_DAMAGE,
                7.0F,
                "Ruins combo second damage");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.COMBO_THIRD_DAMAGE,
                8.0F,
                "Ruins combo third damage");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.OVERHEAD_HIT_TICK,
                20,
                "Ruins overhead windup");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.OVERHEAD_RECOVERY_TICKS,
                18,
                "Ruins overhead recovery");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.OVERHEAD_DAMAGE,
                10.0F,
                "Ruins overhead damage");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.SHOULDER_RUSH_WINDUP_TICKS,
                14,
                "shoulder rush warning");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.SHOULDER_RUSH_MAX_DISTANCE,
                4.5D,
                "shoulder rush maximum travel");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.SHOULDER_RUSH_DAMAGE,
                6.0F,
                "shoulder rush damage");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.SHOULDER_RUSH_COOLDOWN_TICKS,
                45,
                "shoulder rush cooldown");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.RUBBLE_KICK_WINDUP_TICKS,
                18,
                "rubble kick warning");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.RUBBLE_KICK_COOLDOWN_TICKS,
                70,
                "rubble kick cooldown");
        helper.assertValueEqual(
                MedievalRubbleProjectile.SPEED,
                0.55F,
                "rubble flight speed");
        helper.assertValueEqual(
                MedievalRubbleProjectile.damageFor(false),
                7.0F,
                "rubble direct damage");
        helper.assertValueEqual(
                MedievalRubbleProjectile.damageFor(true),
                3.0F,
                "rubble blocked chip damage");
        helper.assertValueEqual(
                Byte.toUnsignedInt(UnknownCombatState.MEDIEVAL_SHOULDER_RUSH.networkId()),
                19,
                "Ruins states must append after the stable Past ids");
        helper.assertValueEqual(
                Byte.toUnsignedInt(UnknownCombatState.MEDIEVAL_RUBBLE_KICK.networkId()),
                20,
                "Ruins state ids must remain contiguous");

        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.chooseAttack(
                        7.0D,
                        true,
                        0,
                        true,
                        0,
                        0,
                        UnknownMedievalRuinsCombatGoal.Attack.NONE),
                UnknownMedievalRuinsCombatGoal.Attack.RUBBLE_KICK,
                "a valid 5-12 block rubble lane must take ranged priority");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.chooseAttack(
                        5.0D,
                        false,
                        0,
                        true,
                        0,
                        1,
                        UnknownMedievalRuinsCombatGoal.Attack.OVERHEAD),
                UnknownMedievalRuinsCombatGoal.Attack.SHOULDER_RUSH,
                "a safe middle-distance lane must enable shoulder rush");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.chooseAttack(
                        3.0D,
                        false,
                        0,
                        false,
                        0,
                        0,
                        UnknownMedievalRuinsCombatGoal.Attack.COMBO),
                UnknownMedievalRuinsCombatGoal.Attack.OVERHEAD,
                "the three-cut combo must never repeat immediately");
        helper.assertValueEqual(
                UnknownMedievalRuinsCombatGoal.chooseAttack(
                        3.0D,
                        false,
                        0,
                        false,
                        0,
                        2,
                        UnknownMedievalRuinsCombatGoal.Attack.OVERHEAD),
                UnknownMedievalRuinsCombatGoal.Attack.COMBO,
                "the combo must re-enter only after a different action");

        List<StructureTemplate.StructureBlockInfo> markers = List.of(
                medievalMarker(
                        UnknownMedievalRuinsArena.BOSS_SPAWN_MARKER,
                        new BlockPos(4, 2, 4)),
                medievalMarker(
                        UnknownMedievalRuinsArena.PLAYER_SPAWN_MARKER,
                        new BlockPos(4, 2, 10)),
                medievalMarker(
                        UnknownMedievalRuinsArena.RUBBLE_KICK_MARKER,
                        new BlockPos(3, 2, 5)),
                medievalMarker(
                        UnknownMedievalRuinsArena.RUBBLE_KICK_MARKER,
                        new BlockPos(6, 2, 5)));
        BlockPos authoredOrigin = new BlockPos(20, 40, -10);
        UnknownMedievalRuinsArena.Validation valid =
                UnknownMedievalRuinsArena.validateAuthoredData(markers, authoredOrigin);
        helper.assertTrue(valid.valid(), "complete Ruins markers must validate: " + valid.describe());
        helper.assertValueEqual(
                valid.layout().orElseThrow().bossSpawn(),
                authoredOrigin.offset(4, 2, 4),
                "Ruins boss anchor must transform from the NBT origin");
        helper.assertValueEqual(
                valid.layout().orElseThrow().rubbleKickMarkers().size(),
                2,
                "all authored rubble kick points must survive validation");
        UnknownMedievalRuinsArena.Validation missingRubble =
                UnknownMedievalRuinsArena.validateAuthoredData(markers.subList(0, 2), authoredOrigin);
        helper.assertFalse(
                missingRubble.valid(),
                "normal progression must reject a Ruins arena without rubble markers");

        StructureTemplate definitiveRuins = helper.getLevel()
                .getStructureManager()
                .get(Identifier.fromNamespaceAndPath(
                        EchoesShowThePast.MOD_ID,
                        "boss/medieval_ruins"))
                .orElseThrow();
        helper.assertValueEqual(
                definitiveRuins.getSize(),
                new Vec3i(70, 39, 37),
                "Ruins must be padded to Past height so the tower is completely removed");
        UnknownMedievalRuinsArena.Validation definitiveValidation =
                UnknownMedievalRuinsArena.validate(
                        definitiveRuins,
                        TimelessDimensions.MEDIEVAL_ARENA_ORIGIN);
        helper.assertTrue(
                definitiveValidation.valid(),
                "the definitive Ruins blueprint must satisfy its marker contract: "
                        + definitiveValidation.describe());
        UnknownMedievalRuinsArena.Layout definitiveLayout =
                definitiveValidation.layout().orElseThrow();
        helper.assertValueEqual(
                definitiveLayout.bossSpawn(),
                TimelessDimensions.MEDIEVAL_ARENA_ORIGIN.offset(12, 11, 14),
                "Ruins boss spawn must sit in the lower plaza");
        helper.assertValueEqual(
                definitiveLayout.playerSpawn(),
                TimelessDimensions.MEDIEVAL_ARENA_ORIGIN.offset(12, 11, 22),
                "Ruins player spawn must face the boss across the lower plaza");
        helper.assertValueEqual(
                definitiveLayout.rubbleKickMarkers().size(),
                4,
                "the definitive lower plaza must expose four rubble lanes");
        List<StructureTemplate.StructureEntityInfo> ruinsRoots =
                ((StructureTemplateAccessor) (Object) definitiveRuins)
                        .echoes$getEntityInfoList();
        helper.assertValueEqual(
                ruinsRoots.size(),
                10,
                "Ruins must retain its ten authored armor stands");
        helper.assertTrue(
                ruinsRoots.stream().allMatch(root ->
                        "minecraft:armor_stand".equals(root.nbt.getStringOr("id", ""))
                                && medievalHasTag(
                                        root.nbt,
                                        UnknownMedievalVanguard.TEMPORARY_TAG)),
                "every Ruins armor stand must remain authored but cleanable");

        Vec3 diveStart = Vec3.atBottomCenterOf(
                TimelessDimensions.MEDIEVAL_ARENA_ORIGIN.offset(51, 36, 25));
        Vec3 diveLanding = Vec3.atBottomCenterOf(
                TimelessDimensions.MEDIEVAL_ARENA_ORIGIN.offset(12, 11, 18));
        Vec3 diveDirection = UnknownMedievalGrabDiveMath.travelDirection(
                diveStart,
                diveLanding);
        Vec3 diveMiddle = UnknownMedievalGrabDiveMath.bossPosition(
                diveStart,
                diveLanding,
                (UnknownMedievalGrabDiveMath.LAUNCH_TICK
                        + UnknownMedievalGrabDiveMath.IMPACT_TICK) / 2);
        helper.assertValueEqual(
                UnknownMedievalGrabDiveMath.bossPosition(
                        diveStart,
                        diveLanding,
                        UnknownMedievalGrabDiveMath.IMPACT_TICK),
                diveLanding,
                "the server trajectory must finish exactly on the authored lower-plaza marker");
        helper.assertTrue(
                diveMiddle.y > diveStart.lerp(diveLanding, 0.5D).y + 9.0D,
                "the tower transfer must be a visible leap rather than a linear teleport");
        Vec3 grippedPlayer = UnknownMedievalGrabDiveMath.playerPosition(
                diveStart.add(0.5D, 0.0D, 0.0D),
                diveStart,
                diveDirection,
                UnknownMedievalGrabDiveMath.GRAB_TICKS);
        helper.assertTrue(
                grippedPlayer.distanceTo(diveStart) < 1.0D,
                "the owner must finish the grab attached to the boss torso");
        helper.assertTrue(
                UnknownMedievalGrabDiveMath.TOTAL_TICKS
                        > UnknownMedievalGrabDiveMath.IMPACT_TICK,
                "the impact must hold before reconstruction begins");

        Vec3 bossFeet = new Vec3(3.5D, 2.0D, 3.5D);
        Vec3 audience = new Vec3(3.5D, 2.0D, 10.5D);
        Vec3 shieldCamera = UnknownEnterCinematicMath.shieldBreakCamera(
                bossFeet, audience, 1.0D);
        helper.assertTrue(
                shieldCamera.distanceTo(UnknownEnterCinematicMath.bossFocus(bossFeet)) < 5.5D,
                "shield break must use a close readable shot");
        helper.assertTrue(
                UnknownEnterCinematicMath.isShieldBreakMode(
                        UnknownEnterCinematicMath.MODE_SHIELD_BREAK)
                        && !UnknownEnterCinematicMath.isEraMode(
                                UnknownEnterCinematicMath.MODE_SHIELD_BREAK),
                "shield break must own a distinct network camera mode");
        helper.assertTrue(
                UnknownEnterCinematicMath.isGrabDiveMode(
                        UnknownEnterCinematicMath.MODE_GRAB_DIVE)
                        && !UnknownEnterCinematicMath.isEraMode(
                                UnknownEnterCinematicMath.MODE_GRAB_DIVE),
                "the grab dive must own a distinct network camera mode");
        UnknownEnterCinematicPayload divePayload = new UnknownEnterCinematicPayload(
                true,
                17,
                TimelessDimensions.BOSS_PEDESTAL_ORIGIN,
                UnknownEnterCinematicMath.MODE_GRAB_DIVE,
                -1);
        helper.assertValueEqual(
                divePayload.mode(),
                UnknownEnterCinematicMath.MODE_GRAB_DIVE,
                "the payload guard must preserve the grab-dive camera mode");
        Vec3 diveCamera = UnknownEnterCinematicMath.grabDiveCamera(
                diveStart,
                UnknownEnterCinematicMath.altarFocus(
                        TimelessDimensions.BOSS_PEDESTAL_ORIGIN),
                1.1D);
        helper.assertTrue(
                diveCamera.distanceTo(UnknownEnterCinematicMath.bossFocus(diveStart)) > 6.0D,
                "the leap lens must widen enough to show both seized player and tower drop");
        helper.assertValueEqual(
                UnknownFightManager.MEDIEVAL_SHIELD_BREAK_IMPACT_TICK,
                18,
                "shield removal must land on the animation impact");
        helper.assertTrue(
                UnknownFightManager.MEDIEVAL_SHIELD_BREAK_TICKS
                        > UnknownFightManager.MEDIEVAL_SHIELD_BREAK_IMPACT_TICK,
                "the cinematic must hold after the shield visibly breaks");

        try (InputStream animations = EchoGameTests.class.getResourceAsStream(
                "/assets/echoes_show_the_past/geckolib/animations/entity/unknown.animation.json")) {
            helper.assertTrue(animations != null, "Unknown animations must be packaged");
            JsonObject clips = JsonParser.parseString(
                            new String(animations.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonObject("animations");
            helper.assertTrue(
                    clips.has("combat.medieval.combo_ruins")
                            && clips.has("combat.medieval.grab_dive")
                            && clips.has("combat.medieval.shield_break")
                            && clips.has("combat.medieval.shoulder_rush")
                            && clips.has("combat.medieval.rubble_kick"),
                    "all definitive Medieval transition/Ruins animation contracts must resolve");
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect Medieval Ruins assets", exception);
        }

        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 4; z++) {
                helper.getLevel().setBlock(
                        helper.absolutePos(new BlockPos(x, 1, z)),
                        Blocks.STONE.defaultBlockState(),
                        3);
            }
        }
        BlockPos collisionBlock = helper.absolutePos(new BlockPos(6, 2, 2));
        helper.getLevel().setBlock(collisionBlock, Blocks.STONE.defaultBlockState(), 3);
        UnknownEntity boss = helper.spawn(
                EchoesShowThePast.UNKNOWN.get(),
                new Vec3(1.5D, 2.0D, 2.5D));
        boss.setDummy(true);
        Vec3 projectileOrigin = helper.absoluteVec(new Vec3(2.5D, 2.45D, 2.5D));
        MedievalRubbleProjectile projectile = new MedievalRubbleProjectile(
                helper.getLevel(),
                boss,
                projectileOrigin,
                new Vec3(1.0D, 0.0D, 0.0D));
        helper.assertTrue(
                helper.getLevel().addFreshEntity(projectile),
                "the authoritative rubble projectile must spawn");
        helper.runAfterDelay(12, () -> {
            helper.assertFalse(projectile.isAlive(), "rubble must despawn on its first block impact");
            helper.assertTrue(
                    helper.getLevel().getBlockState(collisionBlock).is(Blocks.STONE),
                    "rubble impact must not modify its collision block");
            AABB drops = new AABB(collisionBlock).inflate(3.0D);
            helper.assertTrue(
                    helper.getLevel().getEntitiesOfClass(ItemEntity.class, drops).isEmpty(),
                    "rubble impact must create no item drops");
            helper.succeed();
        });
    }

    private static List<StructureTemplate.StructureBlockInfo> medievalMarkerFixture() {
        return List.of(
                medievalMarker(UnknownMedievalVanguard.BOSS_SPAWN_MARKER, new BlockPos(4, 12, 4)),
                medievalMarker(UnknownMedievalVanguard.ROOF_MIN_MARKER, new BlockPos(1, 10, 1)),
                medievalMarker(UnknownMedievalVanguard.ROOF_MAX_MARKER, new BlockPos(8, 14, 8)),
                medievalMarker(UnknownMedievalVanguard.INNER_MIN_MARKER, new BlockPos(2, 1, 2)),
                medievalMarker(
                        UnknownMedievalVanguard.TRANSITION_LANDING_MARKER,
                        new BlockPos(5, 2, 7)),
                medievalMarker(UnknownMedievalVanguard.INNER_MAX_MARKER, new BlockPos(6, 8, 6)));
    }

    private static StructureTemplate.StructureBlockInfo medievalMarker(
            String name,
            BlockPos position) {
        CompoundTag marker = new CompoundTag();
        marker.putString("metadata", name);
        return new StructureTemplate.StructureBlockInfo(
                position,
                Blocks.STRUCTURE_BLOCK.defaultBlockState(),
                marker);
    }

    private static List<StructureTemplate.StructureEntityInfo> medievalVanguardFixture(
            boolean remoteFirstArcher) {
        List<StructureTemplate.StructureEntityInfo> entities = new ArrayList<>();
        int index = 0;
        for (int archer = 0; archer < 2; archer++) {
            entities.add(medievalEntityFixture(
                    index++,
                    medievalNpcData(
                            UnknownMedievalVanguard.OUTER_TAG,
                            UnknownMedievalVanguard.ARCHER_TAG,
                            remoteFirstArcher && archer == 0)));
        }
        for (int infantry = 0; infantry < 4; infantry++) {
            entities.add(medievalEntityFixture(
                    index++,
                    medievalNpcData(
                            UnknownMedievalVanguard.OUTER_TAG,
                            UnknownMedievalVanguard.INFANTRY_TAG,
                            false)));
        }
        for (int archer = 0; archer < 4; archer++) {
            entities.add(medievalEntityFixture(
                    index++,
                    medievalNpcData(
                            UnknownMedievalVanguard.INNER_TAG,
                            UnknownMedievalVanguard.ARCHER_TAG,
                            false)));
        }
        for (int infantry = 0; infantry < 2; infantry++) {
            entities.add(medievalEntityFixture(
                    index++,
                    medievalNpcData(
                            UnknownMedievalVanguard.INNER_TAG,
                            UnknownMedievalVanguard.INFANTRY_TAG,
                            false)));
        }
        return entities;
    }

    private static StructureTemplate.StructureEntityInfo medievalEntityFixture(
            int index,
            CompoundTag data) {
        BlockPos position = new BlockPos(index % 6, 2, index / 6);
        return new StructureTemplate.StructureEntityInfo(
                Vec3.atBottomCenterOf(position),
                position,
                data);
    }

    private static CompoundTag medievalNpcData(String group, String role, boolean unsafeSkin) {
        CompoundTag data = new CompoundTag();
        data.putString("id", "easy_npc:humanoid");
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(UnknownMedievalVanguard.TEMPORARY_TAG));
        tags.add(StringTag.valueOf(UnknownMedievalVanguard.VANGUARD_TAG));
        tags.add(StringTag.valueOf(group));
        tags.add(StringTag.valueOf(role));
        data.put("Tags", tags);
        SkinDataEntry skin = unsafeSkin
                ? new SkinDataEntry(
                        "remote-fixture",
                        "http://example.invalid/skin.png",
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        SkinType.INSECURE_REMOTE_URL,
                        false,
                        "",
                        0L)
                : new SkinDataEntry(
                        "embedded-fixture",
                        "",
                        UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        SkinType.CUSTOM,
                        false,
                        "embedded-png-data",
                        0L);
        data.put("SkinData", skin.createTag());
        return data;
    }

    private static boolean medievalHasTag(CompoundTag data, String expected) {
        for (Tag tag : data.getListOrEmpty("Tags")) {
            if (tag.asString().filter(expected::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static void collectMedievalEntityTree(
            CompoundTag data,
            List<CompoundTag> output) {
        output.add(data);
        for (Tag passenger : data.getListOrEmpty("Passengers")) {
            if (passenger instanceof CompoundTag passengerData) {
                collectMedievalEntityTree(passengerData, output);
            }
        }
    }

    private static void unknownDoryItem(GameTestHelper helper) {
        ItemStack dory = new ItemStack(EchoesShowThePast.DORY.get());
        helper.assertTrue(
                dory.has(DataComponents.KINETIC_WEAPON),
                "the dory spear components must remain so the Unknown can hold a real spear");
        helper.assertTrue(
                dory.has(DataComponents.PIERCING_WEAPON),
                "the dory must perform piercing stab attacks");
        helper.assertTrue(
                dory.has(DataComponents.ATTACK_RANGE),
                "the dory must use spear reach instead of sword reach");
        helper.assertTrue(
                dory.has(DataComponents.ATTRIBUTE_MODIFIERS),
                "the dory must expose its attack damage and speed");
        helper.assertValueEqual(
                dory.getMaxDamage(),
                net.minecraft.world.item.ToolMaterial.IRON.durability(),
                "the dory must use balanced iron-spear durability");

        UnknownEntity boss = EchoesShowThePast.UNKNOWN.get().create(
                helper.getLevel(),
                EntitySpawnReason.TRIGGERED);
        helper.assertTrue(boss != null, "the Unknown fixture must be spawnable");
        boss.setEra(UnknownEntity.ERA_GREEK);
        UnknownFightManager.equipEraWeapon(boss);
        helper.assertTrue(
                boss.getMainHandItem().is(EchoesShowThePast.DORY.get()),
                "the Greek Unknown must equip the authored dory");
        helper.assertTrue(
                boss.getOffhandItem().isEmpty(),
                "the aspis remains a later item slice; Greek combat must not use a vanilla shield stand-in");

        boss.setEra(UnknownEntity.ERA_EGYPTIAN);
        UnknownFightManager.equipEraWeapon(boss);
        helper.assertTrue(
                boss.getMainHandItem().is(EchoesShowThePast.KHOPESH.get()),
                "the Egyptian Unknown must equip the authored khopesh instead of a golden-sword stand-in");
        helper.assertTrue(
                boss.getOffhandItem().isEmpty(),
                "Egyptian combat holds only the khopesh");

        try (InputStream geo = EchoGameTests.class.getResourceAsStream(
                "/assets/echoes_show_the_past/geckolib/models/entity/unknown.geo.json");
                InputStream greekArmor = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/geckolib/models/entity/unknown_greek_armor.geo.json");
                InputStream egyptianArmor = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/geckolib/models/entity/unknown_egyptian_armor.geo.json")) {
            helper.assertTrue(geo != null, "the Unknown geo must be packaged");
            helper.assertTrue(greekArmor != null, "the Greek panoply must be a separate Unknown geo");
            helper.assertTrue(egyptianArmor != null, "the Egyptian panoply must be a separate Unknown geo");
            String geoSource = new String(geo.readAllBytes(), StandardCharsets.UTF_8);
            String greekSource = new String(greekArmor.readAllBytes(), StandardCharsets.UTF_8);
            String egyptianSource = new String(egyptianArmor.readAllBytes(), StandardCharsets.UTF_8);
            helper.assertFalse(
                    geoSource.contains("greek_general_"),
                    "baked Greek-general cubes must not remain in the live Unknown geo");
            helper.assertTrue(
                    geoSource.contains("\"name\": \"body\"")
                            && geoSource.contains("\"name\": \"armor_head\""),
                    "the live Unknown geo must keep the silhouette and empty armor sockets");
            helper.assertFalse(
                    geoSource.contains("greek_armor_") || geoSource.contains("egypt_armor_"),
                    "authored panoplies must not be baked into the live Unknown geo");
            helper.assertTrue(
                    greekSource.contains("\"name\": \"armor_head\"")
                            && greekSource.contains("\"origin\""),
                    "the Greek panoply geo must share the Unknown sockets");
            helper.assertTrue(
                    egyptianSource.contains("\"name\": \"armor_head\"")
                            && egyptianSource.contains("\"origin\""),
                    "the Egyptian panoply geo must share the Unknown sockets");
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect the packaged Unknown geos", exception);
        }

        try (InputStream voidTexture = EchoGameTests.class.getResourceAsStream(
                "/assets/echoes_show_the_past/textures/entity/unknown_void.png");
                InputStream obsoleteColoredTexture = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/textures/entity/unknown.png");
                InputStream greekTexture = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/textures/entity/unknown_greek_armor.png");
                InputStream egyptianTexture = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/textures/entity/unknown_egyptian_armor.png");
                InputStream doryModel = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/models/item/dory.json")) {
            helper.assertTrue(voidTexture != null, "the Unknown void body texture must be packaged");
            helper.assertTrue(
                    obsoleteColoredTexture == null,
                    "the obsolete colored Unknown preview texture must not be packaged");
            helper.assertTrue(greekTexture != null, "the Greek panoply texture must be packaged");
            helper.assertTrue(egyptianTexture != null, "the Egyptian panoply texture must be packaged");
            helper.assertTrue(doryModel != null, "the dory item model must be packaged");
            var body = ImageIO.read(voidTexture);
            helper.assertTrue(body != null, "the Unknown void body texture must decode");
            int pixel = body.getRGB(8, 8);
            helper.assertTrue(
                    ((pixel >> 16) & 0xFF) == 0
                            && ((pixel >> 8) & 0xFF) == 0
                            && (pixel & 0xFF) == 0
                            && ((pixel >>> 24) & 0xFF) == 255,
                    "the Unknown body islands must be opaque pure black");
            JsonObject display = JsonParser.parseString(
                    new String(doryModel.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonObject("display")
                    .getAsJsonObject("thirdperson_righthand");
            helper.assertValueEqual(
                    display.getAsJsonArray("translation").get(1).getAsFloat(),
                    -6.0F,
                    "the held dory must sit as a spear, not a sword");
            helper.assertFalse(
                    display.has("rotation"),
                    "third-person dory must keep the authored vertical spear hold");
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect Unknown body and dory hold", exception);
        }

        String[] lootPaths = {
            "data/echoes_show_the_past/loot_table/chests/erechtheion/butes.json",
            "data/echoes_show_the_past/loot_table/chests/coliseum/armory.json",
            "data/echoes_show_the_past/loot_table/chests/egyptian_temple/offering.json"
        };
        String[] bossOnlyItems = {
            "echoes_show_the_past:dory",
            "echoes_show_the_past:khopesh",
            "echoes_show_the_past:unknown_medieval_sword",
            "echoes_show_the_past:unknown_medieval_shield",
            "echoes_show_the_past:unknown_medieval_helmet",
            "echoes_show_the_past:unknown_medieval_chestplate",
            "echoes_show_the_past:unknown_medieval_leggings",
            "echoes_show_the_past:unknown_medieval_boots"
        };
        ClassLoader resources = EchoGameTests.class.getClassLoader();
        for (String path : lootPaths) {
            try (InputStream lootStream = resources.getResourceAsStream(path)) {
                helper.assertTrue(lootStream != null, "missing loot table " + path);
                String loot = new String(lootStream.readAllBytes(), StandardCharsets.UTF_8);
                for (String item : bossOnlyItems) {
                    helper.assertFalse(
                            loot.contains(item),
                            path + " must not drop the boss-only item " + item);
                }
            } catch (IOException exception) {
                throw new AssertionError("Could not inspect loot table " + path, exception);
            }
        }
        helper.succeed();
    }

    private static void unknownMedievalArmorItems(GameTestHelper helper) {
        ItemStack helmet = new ItemStack(EchoesShowThePast.UNKNOWN_MEDIEVAL_HELMET.get());
        ItemStack chest = new ItemStack(EchoesShowThePast.UNKNOWN_MEDIEVAL_CHESTPLATE.get());
        ItemStack legs = new ItemStack(EchoesShowThePast.UNKNOWN_MEDIEVAL_LEGGINGS.get());
        ItemStack boots = new ItemStack(EchoesShowThePast.UNKNOWN_MEDIEVAL_BOOTS.get());
        helper.assertTrue(helmet.has(DataComponents.EQUIPPABLE), "helmet must be equippable");
        helper.assertTrue(chest.has(DataComponents.EQUIPPABLE), "chestplate must be equippable");
        helper.assertTrue(legs.has(DataComponents.EQUIPPABLE), "leggings must be equippable");
        helper.assertTrue(boots.has(DataComponents.EQUIPPABLE), "boots must be equippable");
        helper.assertValueEqual(
                helmet.get(DataComponents.EQUIPPABLE).slot(),
                EquipmentSlot.HEAD,
                "helmet occupies the head slot");
        helper.assertValueEqual(
                chest.get(DataComponents.EQUIPPABLE).slot(),
                EquipmentSlot.CHEST,
                "chestplate occupies the chest slot");
        helper.assertValueEqual(
                legs.get(DataComponents.EQUIPPABLE).slot(),
                EquipmentSlot.LEGS,
                "leggings occupy the legs slot");
        helper.assertValueEqual(
                boots.get(DataComponents.EQUIPPABLE).slot(),
                EquipmentSlot.FEET,
                "boots occupy the feet slot");

        var player = helper.makeMockServerPlayerInLevel();
        helper.assertFalse(
                UnknownMedievalArmorItem.hidesJacketAndSleeves(player),
                "bare skin must keep the jacket overlay");
        helper.assertFalse(
                UnknownMedievalArmorItem.hidesPants(player),
                "bare skin must keep the pants overlay");
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        helper.assertFalse(
                UnknownMedievalArmorItem.hidesJacketAndSleeves(player),
                "the helmet must leave the body overlay alone so the hat layer can stay");
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        helper.assertTrue(
                UnknownMedievalArmorItem.hidesJacketAndSleeves(player),
                "the chestplate must hide jacket and sleeves");
        helper.assertFalse(
                UnknownMedievalArmorItem.hidesPants(player),
                "chestplate alone must not hide the pants overlay");
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        helper.assertTrue(
                UnknownMedievalArmorItem.hidesPants(player),
                "leggings must hide the pants overlay");
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        helper.assertTrue(
                UnknownMedievalArmorItem.hidesPants(player),
                "boots must hide the pants overlay so it does not clip through the sabatons");

        UnknownEntity boss = EchoesShowThePast.UNKNOWN.get().create(
                helper.getLevel(),
                EntitySpawnReason.TRIGGERED);
        helper.assertTrue(boss != null, "the medieval Unknown fixture must be spawnable");
        boss.setEra(UnknownEntity.ERA_MEDIEVAL);
        UnknownFightManager.equipEraWeapon(boss);
        helper.assertTrue(
                boss.getItemBySlot(EquipmentSlot.HEAD).is(EchoesShowThePast.UNKNOWN_MEDIEVAL_HELMET.get()),
                "the medieval Unknown must equip the authored crown");
        helper.assertTrue(
                boss.getItemBySlot(EquipmentSlot.CHEST)
                        .is(EchoesShowThePast.UNKNOWN_MEDIEVAL_CHESTPLATE.get()),
                "the medieval Unknown must equip the authored cuirass");
        helper.assertTrue(
                boss.getItemBySlot(EquipmentSlot.LEGS).is(EchoesShowThePast.UNKNOWN_MEDIEVAL_LEGGINGS.get()),
                "the medieval Unknown must equip the authored greaves");
        helper.assertTrue(
                boss.getItemBySlot(EquipmentSlot.FEET).is(EchoesShowThePast.UNKNOWN_MEDIEVAL_BOOTS.get()),
                "the medieval Unknown must equip the authored boots");

        try (InputStream helmetGeo = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/geckolib/models/armor/unknown_medieval_helmet.geo.json");
                InputStream chestGeo = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/geckolib/models/armor/unknown_medieval_chestplate.geo.json");
                InputStream legsGeo = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/geckolib/models/armor/unknown_medieval_leggings.geo.json");
                InputStream bootsGeo = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/geckolib/models/armor/unknown_medieval_boots.geo.json");
                InputStream helmetTex = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/textures/armor/unknown_medieval_helmet.png");
                InputStream chestTex = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/textures/armor/unknown_medieval_chestplate.png");
                InputStream bossGeo = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/geckolib/models/entity/unknown_medieval_armor.geo.json");
                InputStream bossTex = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/textures/entity/unknown_medieval_armor.png");
                InputStream itemIcon = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/textures/item/unknown_medieval_helmet.png");
                InputStream equipment = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/equipment/unknown_medieval.json")) {
            helper.assertTrue(helmetGeo != null, "helmet geo must be packaged");
            helper.assertTrue(chestGeo != null, "chestplate geo must be packaged");
            helper.assertTrue(legsGeo != null, "leggings geo must be packaged");
            helper.assertTrue(bootsGeo != null, "boots geo must be packaged");
            helper.assertTrue(helmetTex != null, "helmet worn texture must be packaged");
            helper.assertTrue(chestTex != null, "chestplate worn texture must be packaged");
            helper.assertTrue(bossGeo != null, "the unified medieval boss panoply must be packaged");
            helper.assertTrue(bossTex != null, "the unified medieval boss atlas must be packaged");
            helper.assertTrue(itemIcon != null, "helmet item icon must be packaged");
            helper.assertTrue(equipment != null, "equipment asset must be packaged");
            String helmetSource = new String(helmetGeo.readAllBytes(), StandardCharsets.UTF_8);
            String chestSource = new String(chestGeo.readAllBytes(), StandardCharsets.UTF_8);
            String legsSource = new String(legsGeo.readAllBytes(), StandardCharsets.UTF_8);
            String bootsSource = new String(bootsGeo.readAllBytes(), StandardCharsets.UTF_8);
            helper.assertTrue(
                    helmetSource.contains("\"name\": \"armorHead\""),
                    "helmet geo must use the GeckoLib head bone");
            helper.assertTrue(
                    chestSource.contains("\"name\": \"armorBody\"")
                            && chestSource.contains("\"name\": \"armorLeftArm\"")
                            && chestSource.contains("\"name\": \"armorRightArm\""),
                    "chestplate geo must bind body and both arms");
            helper.assertTrue(
                    legsSource.contains("\"name\": \"armorLeftLeg\"")
                            && legsSource.contains("\"name\": \"armorRightLeg\""),
                    "leggings geo must bind both legs");
            helper.assertTrue(
                    bootsSource.contains("\"name\": \"armorLeftBoot\"")
                            && bootsSource.contains("\"name\": \"armorRightBoot\""),
                    "boots geo must bind both feet");

            JsonObject chestGeometry = JsonParser.parseString(chestSource)
                    .getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry")
                    .get(0)
                    .getAsJsonObject();
            JsonArray cuirassCubes = chestGeometry
                    .getAsJsonArray("bones")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonArray("cubes");
            helper.assertValueEqual(
                    cuirassCubes.size(),
                    2,
                    "the cuirass must combine its authored shell with a lower-abdomen underlay");
            JsonObject abdomenUnderlay = cuirassCubes.get(0).getAsJsonObject();
            helper.assertTrue(
                    Math.abs(abdomenUnderlay.getAsJsonArray("size").get(0).getAsDouble() - 7.0D) < 0.001D
                            && Math.abs(abdomenUnderlay.getAsJsonArray("size").get(1).getAsDouble() - 6.1D)
                                    < 0.001D,
                    "the lower cuirass must close the transparent abdomen without covering the authored neckline");

            JsonObject legsGeometry = JsonParser.parseString(legsSource)
                    .getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry")
                    .get(0)
                    .getAsJsonObject();
            JsonArray bodyCubes = legsGeometry
                    .getAsJsonArray("bones")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonArray("cubes");
            helper.assertValueEqual(
                    bodyCubes.get(0).getAsJsonObject().getAsJsonArray("size").get(1).getAsInt(),
                    3,
                    "the leggings waist must be a short belt, never a second torso hiding the cuirass");
            JsonObject beltNorth = bodyCubes
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("uv")
                    .getAsJsonObject("north");
            helper.assertValueEqual(
                    beltNorth.getAsJsonArray("uv").get(1).getAsInt(),
                    10,
                    "the belt must sample the opaque metal band instead of the transparent leggings row");

            JsonObject bootsGeometry = JsonParser.parseString(bootsSource)
                    .getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry")
                    .get(0)
                    .getAsJsonObject();
            for (var bootBoneElement : bootsGeometry.getAsJsonArray("bones")) {
                JsonArray bootCubes = bootBoneElement.getAsJsonObject().getAsJsonArray("cubes");
                helper.assertValueEqual(
                        bootCubes.size(),
                        2,
                        "each medieval boot must have a short shaft and a separate sabaton");
                for (var bootCubeElement : bootCubes) {
                    helper.assertTrue(
                            bootCubeElement.getAsJsonObject().getAsJsonArray("size").get(1).getAsDouble()
                                    <= 5.0D,
                            "boots must end below the greaves instead of hiding the complete trouser legs");
                }
            }

            JsonObject bossGeometry = JsonParser.parseString(
                            new String(bossGeo.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry")
                    .get(0)
                    .getAsJsonObject();
            JsonObject bossDescription = bossGeometry.getAsJsonObject("description");
            helper.assertValueEqual(
                    bossDescription.get("identifier").getAsString(),
                    "geometry.echoes_show_the_past.unknown_medieval_armor",
                    "the boss panoply must use the medieval entity identifier");
            helper.assertValueEqual(
                    bossDescription.get("texture_width").getAsInt(),
                    128,
                    "the boss panoply must use the authored 128px atlas");
            int bossArmorCubes = 0;
            Set<String> populatedSockets = new java.util.HashSet<>();
            for (var boneElement : bossGeometry.getAsJsonArray("bones")) {
                JsonObject bone = boneElement.getAsJsonObject();
                if (bone.has("cubes")) {
                    bossArmorCubes += bone.getAsJsonArray("cubes").size();
                    populatedSockets.add(bone.get("name").getAsString());
                }
            }
            helper.assertValueEqual(bossArmorCubes, 16, "the unified boss panoply must contain all 16 armor cubes");
            helper.assertTrue(
                    populatedSockets.containsAll(Set.of(
                            "armor_head",
                            "armor_chest",
                            "armor_arm_right",
                            "armor_arm_left",
                            "armor_waist",
                            "armor_leg_right",
                            "armor_leg_left",
                            "armor_boot_right",
                            "armor_boot_left")),
                    "every medieval armor section must bind to a shared Unknown socket");

            var helmetImage = ImageIO.read(helmetTex);
            var bossAtlas = ImageIO.read(bossTex);
            helper.assertTrue(helmetImage != null, "the jeweled crown texture must decode");
            helper.assertTrue(bossAtlas != null, "the unified medieval boss atlas must decode");
            helper.assertValueEqual(helmetImage.getWidth(), 64, "the jeweled crown must remain 64px wide");
            helper.assertValueEqual(bossAtlas.getWidth(), 128, "the unified boss atlas must remain 128px wide");
            int goldPixels = 0;
            int gemPixels = 0;
            for (int y = 0; y < helmetImage.getHeight(); y++) {
                for (int x = 0; x < helmetImage.getWidth(); x++) {
                    int color = helmetImage.getRGB(x, y);
                    int alpha = (color >>> 24) & 0xFF;
                    int red = (color >>> 16) & 0xFF;
                    int green = (color >>> 8) & 0xFF;
                    int blue = color & 0xFF;
                    if (alpha > 0 && red > 140 && green > 80 && blue < 90) {
                        goldPixels++;
                    }
                    if (alpha > 0 && ((blue > 90 && blue > red) || (green > 80 && green > red))) {
                        gemPixels++;
                    }
                }
            }
            helper.assertTrue(goldPixels > 100, "the definitive crown must retain its gold metalwork");
            helper.assertTrue(gemPixels > 10, "the definitive crown must retain its blue and green gems");
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect medieval armor assets", exception);
        }
        helper.succeed();
    }

    private static void bigEchoPedestal(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 3));
        for (int x = 2; x <= 5; x++) {
            for (int z = 2; z <= 5; z++) {
                helper.getLevel().setBlock(
                        helper.absolutePos(new BlockPos(x, 1, z)),
                        Blocks.STONE.defaultBlockState(),
                        3);
            }
        }
        BlockState base = EchoesShowThePast.BIG_ECHO_PEDESTAL.get().defaultBlockState();
        for (BigEchoPedestalBlock.Part part : BigEchoPedestalBlock.Part.values()) {
            helper.getLevel().setBlock(
                    part.positionFrom(origin),
                    base.setValue(BigEchoPedestalBlock.PART, part),
                    3);
        }
        for (BigEchoPedestalBlock.Part part : BigEchoPedestalBlock.Part.values()) {
            helper.assertValueEqual(
                    helper.getLevel().getBlockState(part.positionFrom(origin))
                            .getValue(BigEchoPedestalBlock.PART),
                    part,
                    "every authored cell must belong to the one 2x2 altar");
        }
        helper.assertTrue(
                helper.getLevel().getBlockEntity(origin)
                        instanceof dev.alvar.echoespast.block.BigEchoPedestalBlockEntity,
                "ORIGIN must host the altar inventory block entity");

        helper.getLevel().setBlock(
                BigEchoPedestalBlock.Part.WEST.positionFrom(origin),
                Blocks.AIR.defaultBlockState(),
                3);
        helper.runAfterDelay(1, () -> {
            for (BigEchoPedestalBlock.Part part : BigEchoPedestalBlock.Part.values()) {
                helper.assertFalse(
                        helper.getLevel().getBlockState(part.positionFrom(origin))
                                .is(EchoesShowThePast.BIG_ECHO_PEDESTAL.get()),
                        "breaking any cell must remove the complete altar without orphan parts");
            }
            helper.succeed();
        });
    }

    private static void arenaWavePlayerRescue(GameTestHelper helper) {
        BlockPos floor = helper.absolutePos(new BlockPos(4, 1, 4));
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                helper.getLevel().setBlock(
                        floor.offset(x, 0, z),
                        Blocks.STONE.defaultBlockState(),
                        3);
            }
        }

        var player = helper.makeMockServerPlayerInLevel();
        Vec3 trappedPosition = Vec3.atBottomCenterOf(floor.above());
        player.snapTo(
                trappedPosition.x,
                trappedPosition.y,
                trappedPosition.z,
                37.0F,
                -8.0F);
        helper.getLevel().setBlock(floor.above(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(floor.above(2), Blocks.STONE.defaultBlockState(), 3);
        helper.assertFalse(
                helper.getLevel().noCollision(player),
                "the fixture must reproduce a player enclosed by reconstructed stone");

        BlockPos roofedFloor = floor.offset(-2, 0, 0);
        var roofedPlayer = helper.makeMockServerPlayerInLevel();
        Vec3 roofedPosition = Vec3.atBottomCenterOf(roofedFloor.above());
        roofedPlayer.snapTo(
                roofedPosition.x,
                roofedPosition.y,
                roofedPosition.z,
                -22.0F,
                5.0F);
        for (int y = 1; y <= 9; y++) {
            helper.getLevel().setBlock(
                    roofedFloor.above(y),
                    Blocks.STONE.defaultBlockState(),
                    3);
        }
        helper.assertFalse(
                helper.getLevel().noCollision(roofedPlayer),
                "the second fixture must block every preferred vertical escape");

        AABB arenaBounds = new AABB(
                floor.getX() - 4.0D,
                floor.getY() - 1.0D,
                floor.getZ() - 4.0D,
                floor.getX() + 5.0D,
                floor.getY() + 9.0D,
                floor.getZ() + 5.0D);
        int rescued = ArenaReconstructionWave.rescueCollidingPlayers(
                helper.getLevel(),
                arenaBounds);

        BlockPos bossFloor = floor.offset(2, 0, 2);
        var boss = helper.spawn(
                EchoesShowThePast.UNKNOWN.get(),
                new Vec3(6.5D, 2.0D, 6.5D));
        helper.getLevel().setBlock(bossFloor.above(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(bossFloor.above(2), Blocks.STONE.defaultBlockState(), 3);
        helper.assertFalse(
                helper.getLevel().noCollision(boss),
                "the fixture must reproduce a boss enclosed by reconstructed stone");
        helper.assertTrue(
                ArenaReconstructionWave.rescueCollidingEntity(
                        helper.getLevel(), boss, arenaBounds),
                "the arena wave must rescue the boss without snapping it to a pedestal");

        helper.assertValueEqual(rescued, 2, "every trapped player in the wave must be rescued");
        helper.assertTrue(
                helper.getLevel().noCollision(player),
                "the arena wave must leave the player outside all solid collision");
        helper.assertTrue(
                player.getY() >= floor.getY() + 3.0D,
                "an unobstructed position above the generated stone must be preferred");
        helper.assertValueEqual(player.getYRot(), 37.0F, "rescue must preserve player yaw");
        helper.assertValueEqual(player.getXRot(), -8.0F, "rescue must preserve player pitch");
        helper.assertTrue(
                helper.getLevel().noCollision(roofedPlayer),
                "a blocked vertical route must fall back to a collision-free lateral cell");
        helper.assertTrue(
                Math.abs(roofedPlayer.getX() - roofedPosition.x) >= 0.75D
                        || Math.abs(roofedPlayer.getZ() - roofedPosition.z) >= 0.75D,
                "the roofed player must be moved sideways rather than left inside stone");
        helper.assertValueEqual(
                roofedPlayer.getY(),
                roofedPosition.y,
                "a safe same-level lateral escape must not launch the player upwards");
        helper.assertTrue(
                helper.getLevel().noCollision(boss),
                "the reconstructed arena must not leave the boss suffocating");
        helper.assertTrue(
                boss.position().distanceToSqr(Vec3.atBottomCenterOf(bossFloor.above())) <= 64.0D,
                "boss rescue must remain a local correction instead of a visible arena teleport");
        helper.succeed();
    }

    private static void timelessFireRules(GameTestHelper helper) {
        helper.assertTrue(
                TimelessFireRules.freezesFireTick(
                        TimelessDimensions.TIMELESS_VOID,
                        Blocks.FIRE),
                "normal fire must not age, extinguish or spread in the timeless dimension");
        helper.assertTrue(
                TimelessFireRules.freezesFireTick(
                        TimelessDimensions.TIMELESS_VOID,
                        Blocks.SOUL_FIRE),
                "all fire variants must remain timeless");
        helper.assertFalse(
                TimelessFireRules.freezesFireTick(
                        TimelessDimensions.TIMELESS_VOID,
                        Blocks.TORCH),
                "non-fire light sources must retain their normal scheduled ticks");
        helper.assertFalse(
                TimelessFireRules.freezesFireTick(Level.OVERWORLD, Blocks.FIRE),
                "fire outside the timeless dimension must remain vanilla");
        helper.assertTrue(
                TimelessFireRules.suppressesLavaIgnition(
                        TimelessDimensions.TIMELESS_VOID,
                        net.minecraft.world.level.material.Fluids.LAVA.defaultFluidState()),
                "lava must not create new fire in the timeless dimension");
        helper.assertFalse(
                TimelessFireRules.suppressesLavaIgnition(
                        Level.OVERWORLD,
                        net.minecraft.world.level.material.Fluids.LAVA.defaultFluidState()),
                "lava ignition outside the timeless dimension must remain vanilla");
        helper.succeed();
    }

    private static void timelessAtmosphere(GameTestHelper helper) {
        var hub = TimelessAtmosphere.target(
                false,
                UnknownBossBarPayload.ERA_VOID,
                UnknownBossBarPayload.PHASE_IDLE);
        var greekPast = TimelessAtmosphere.target(
                true,
                UnknownBossBarPayload.ERA_GREEK,
                UnknownBossBarPayload.PHASE_PAST);
        var greekRuins = TimelessAtmosphere.target(
                true,
                UnknownBossBarPayload.ERA_GREEK,
                UnknownBossBarPayload.PHASE_RUINS);
        var egyptianPast = TimelessAtmosphere.target(
                true,
                UnknownBossBarPayload.ERA_EGYPTIAN,
                UnknownBossBarPayload.PHASE_PAST);
        helper.assertTrue(
                hub.veilStrength() > 0.0F && hub.starBrightness() > 0.0F,
                "the hub must retain atmospheric depth without floating epoch icons");
        helper.assertTrue(
                egyptianPast.gold() > greekPast.gold()
                        && egyptianPast.veilStrength() > greekPast.veilStrength(),
                "the Egyptian atmosphere must feel warmer and denser without literal skyline symbols");
        helper.assertTrue(
                greekRuins.instability() > greekPast.instability() * 4.0F
                        && greekRuins.veilStrength() > greekPast.veilStrength()
                        && greekRuins.horizonDistance() < greekPast.horizonDistance(),
                "Ruins must become visibly less stable and pull the void fog inward");

        String root = "/assets/echoes_show_the_past/";
        try (InputStream nebula = EchoGameTests.class.getResourceAsStream(
                        root + "textures/environment/timeless_nebula.png");
                InputStream skyVertex = EchoGameTests.class.getResourceAsStream(
                        root + "shaders/core/timeless_sky.vsh");
                InputStream skyFragment = EchoGameTests.class.getResourceAsStream(
                        root + "shaders/core/timeless_sky.fsh");
                InputStream veilShader = EchoGameTests.class.getResourceAsStream(
                         root + "shaders/core/timeless_veil.fsh");
                InputStream eclipseShader = EchoGameTests.class.getResourceAsStream(
                         root + "shaders/core/timeless_eclipse.fsh");
                InputStream coronaShader = EchoGameTests.class.getResourceAsStream(
                         root + "shaders/core/timeless_eclipse_corona.fsh");
                InputStream dimensionType = EchoGameTests.class.getResourceAsStream(
                        "/data/echoes_show_the_past/dimension_type/timeless_void.json");
                InputStream compatibilityClass = EchoGameTests.class.getResourceAsStream(
                        "/dev/alvar/echoespast/client/EchoShaderCompatibility.class");
                InputStream clientClass = EchoGameTests.class.getResourceAsStream(
                        "/dev/alvar/echoespast/client/EchoesShowThePastClient.class");
                InputStream skyRendererClass = EchoGameTests.class.getResourceAsStream(
                        "/dev/alvar/echoespast/client/TimelessSkyRenderer.class");
                InputStream hopliteRendererClass = EchoGameTests.class.getResourceAsStream(
                        "/dev/alvar/echoespast/client/SpectralHopliteRenderer.class");
                InputStream combatRendererClass = EchoGameTests.class.getResourceAsStream(
                        "/dev/alvar/echoespast/client/UnknownGreekCombatRenderer.class");
                InputStream altarEffectsClass = EchoGameTests.class.getResourceAsStream(
                        "/dev/alvar/echoespast/client/BigEchoPedestalOrbitEffects.class");
                InputStream fightManagerClass = EchoGameTests.class.getResourceAsStream(
                        "/dev/alvar/echoespast/server/UnknownFightManager.class")) {
            helper.assertTrue(
                    nebula != null
                            && skyVertex != null
                             && skyFragment != null
                             && veilShader != null
                             && eclipseShader != null
                             && coronaShader != null
                             && dimensionType != null
                             && compatibilityClass != null
                             && clientClass != null
                             && skyRendererClass != null
                             && hopliteRendererClass != null
                             && combatRendererClass != null
                             && altarEffectsClass != null
                             && fightManagerClass != null,
                    "the complete retained sky render stack must ship in the built mod");
            var image = ImageIO.read(nebula);
            helper.assertTrue(
                    image != null && image.getWidth() >= 512 && image.getHeight() >= 512,
                    "the nebula source must retain enough resolution for triplanar projection");
            String skySource = new String(skyFragment.readAllBytes(), StandardCharsets.UTF_8);
            String eclipseSource = new String(eclipseShader.readAllBytes(), StandardCharsets.UTF_8);
            String coronaSource = new String(coronaShader.readAllBytes(), StandardCharsets.UTF_8);
            helper.assertFalse(
                    skySource.contains("fract(shardCoordinate"),
                    "the sky must not rebuild Ruins as periodic straight stripes");
            helper.assertTrue(
                    skySource.contains("memoryVeil")
                            && eclipseSource.contains("discRadius")
                            && coronaSource.contains("coronalNoise"),
                    "the revised atmosphere must separate the deep sky, clean occulting disc, and organic corona");
            JsonObject dimension = JsonParser.parseReader(new java.io.InputStreamReader(
                            dimensionType,
                            StandardCharsets.UTF_8))
                    .getAsJsonObject();
            helper.assertTrue(
                    dimension.get("ambient_light").getAsFloat() >= 0.22F,
                    "the arena must receive real dimension light instead of a screen-space brightness wash");
            helper.assertValueEqual(
                    dimension.getAsJsonObject("attributes")
                            .get("neoforge:custom_skybox")
                            .getAsString(),
                    "echoes_show_the_past:timeless_void",
                    "the Timeless dimension type must select the custom sky renderer");

            String compatibilityBytecode = new String(
                    compatibilityClass.readAllBytes(), StandardCharsets.ISO_8859_1);
            String clientBytecode = new String(
                    clientClass.readAllBytes(), StandardCharsets.ISO_8859_1);
            String skyRendererBytecode = new String(
                    skyRendererClass.readAllBytes(), StandardCharsets.ISO_8859_1);
            String hopliteRendererBytecode = new String(
                    hopliteRendererClass.readAllBytes(), StandardCharsets.ISO_8859_1);
            String combatRendererBytecode = new String(
                    combatRendererClass.readAllBytes(), StandardCharsets.ISO_8859_1);
            String altarEffectsBytecode = new String(
                    altarEffectsClass.readAllBytes(), StandardCharsets.ISO_8859_1);
            String fightManagerBytecode = new String(
                    fightManagerClass.readAllBytes(), StandardCharsets.ISO_8859_1);
            helper.assertTrue(
                    compatibilityBytecode.contains("assignPipeline")
                            && compatibilityBytecode.contains("isShaderPackInUse")
                            && compatibilityBytecode.contains("isRenderingShadowPass"),
                    "Iris/Oculus integration must use its public pipeline and pass API through a soft dependency");
            helper.assertTrue(
                    clientBytecode.contains("WAVE_COLOR_PIPELINE")
                            && clientBytecode.contains("WAVE_MASK_PIPELINE")
                            && clientBytecode.contains("ALTAR_ORBIT_PIPELINE")
                            && clientBytecode.contains("UNKNOWN_STAB_PIPELINE")
                            && clientBytecode.contains("RA_JUDGMENT_SIGIL_PIPELINE")
                            && clientBytecode.contains("EGYPTIAN_JUDGMENT_PIPELINE")
                            && clientBytecode.contains("EGYPTIAN_SEKHMET_PIPELINE")
                            && clientBytecode.contains("SPECTRAL_HOPLITE_PIPELINE")
                            && clientBytecode.contains("SHADERPACK_COLOR_PIPELINE")
                            && clientBytecode.contains("SHADERPACK_COLOR_OCCLUDED_PIPELINE")
                            && clientBytecode.contains("SKY_TEXTURED")
                            && clientBytecode.contains("SKY_BASIC"),
                    "every custom boss pipeline must be registered and classified for Iris shader replacement");
            helper.assertFalse(
                    clientBytecode.contains("EGYPTIAN_ARCHITECTURE_PIPELINE")
                            || clientBytecode.contains("EGYPTIAN_CHARIOT_PIPELINE"),
                    "retired Egyptian renderers without shader assets must never enter resource reload");
            helper.assertTrue(
                    skyRendererBytecode.contains("buildVeilBands")
                            && skyRendererBytecode.contains("buildEclipseFallbackDisc")
                            && skyRendererBytecode.contains("buildEclipseFallbackCorona")
                            && skyRendererBytecode.contains("submitShaderpackGeometry")
                            && skyRendererBytecode.contains("emitShaderpackSky"),
                    "shaderpack mode must submit shaped sky layers through the world renderer instead of the main target");
            helper.assertTrue(
                    hopliteRendererBytecode.contains("isShaderPackActive")
                            && hopliteRendererBytecode.contains("entityTranslucent"),
                    "spectral hoplites need a vanilla entity material that shaderpacks can shade safely");
            helper.assertFalse(
                    hopliteRendererBytecode.contains("entityTranslucentEmissive"),
                    "spectral hoplites must not enter shaderpacks as emissive entities");
            helper.assertTrue(
                    combatRendererBytecode.contains("shaderPackExposureColor")
                            && altarEffectsBytecode.contains("shaderPackExposureColor")
                            && altarEffectsBytecode.contains("isShaderPackActive"),
                    "procedural combat and altar geometry must reserve HDR headroom under shaderpacks");
            helper.assertTrue(
                    fightManagerBytecode.contains("ensureArenaLighting")
                            && fightManagerBytecode.contains("LightBlock"),
                    "the arena needs real collisionless lightmap sources that shaderpacks can shade");
        } catch (IOException exception) {
            throw new AssertionError("unable to validate Timeless atmosphere assets", exception);
        }
        helper.succeed();
    }

    private static void unknownSpectralPhalanxVisibility(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(new BlockPos(1, 0, 2));
        BlockPos max = helper.absolutePos(new BlockPos(4, 7, 4));
        int minCx = Math.min(min.getX() >> 4, max.getX() >> 4);
        int maxCx = Math.max(min.getX() >> 4, max.getX() >> 4);
        int minCz = Math.min(min.getZ() >> 4, max.getZ() >> 4);
        int maxCz = Math.max(min.getZ() >> 4, max.getZ() >> 4);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                helper.getLevel().setChunkForced(cx, cz, true);
            }
        }
        for (int x = 1; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.getLevel().setBlock(
                        helper.absolutePos(new BlockPos(x, x <= 2 ? 1 : 0, z)),
                        Blocks.STONE.defaultBlockState(),
                        3);
                if (x > 2) {
                    helper.getLevel().setBlock(
                            helper.absolutePos(new BlockPos(x, 1, z)),
                            Blocks.AIR.defaultBlockState(),
                            3);
                }
                for (int y = 2; y <= 4; y++) {
                    helper.getLevel().setBlock(
                            helper.absolutePos(new BlockPos(x, y, z)),
                            Blocks.AIR.defaultBlockState(),
                            3);
                }
            }
        }
        for (int z = 2; z <= 4; z++) {
            helper.getLevel().setBlock(
                    helper.absolutePos(new BlockPos(2, 2, z)),
                    Blocks.STONE.defaultBlockState(),
                    3);
            helper.getLevel().setBlock(
                    helper.absolutePos(new BlockPos(2, 3, z)),
                    Blocks.STONE.defaultBlockState(),
                    3);
        }
        SpectralHopliteEntity hoplite = EchoesShowThePast.SPECTRAL_HOPLITE.get().create(
                helper.getLevel(),
                EntitySpawnReason.TRIGGERED);
        helper.assertTrue(hoplite != null, "the spectral hoplite type must be spawnable");
        Vec3 spawn = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 3)));
        hoplite.setPos(spawn);
        hoplite.configure(
                new Vec3(1.0D, 0.0D, 0.0D),
                Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 3))),
                UnknownGreekCombatGoal.PHALANX_SPEED_PAST,
                0,
                helper.getLevel().getGameTime() + 200L,
                true);
        helper.assertTrue(helper.getLevel().addFreshEntity(hoplite), "the spectral row must enter the level");
        helper.assertTrue(
                hoplite.beginMarch(),
                "visibility must not depend on finding a complete path during the spawn tick");
        SpectralHopliteEntity highGroundedHoplite = EchoesShowThePast.SPECTRAL_HOPLITE.get().create(
                helper.getLevel(),
                EntitySpawnReason.TRIGGERED);
        helper.assertTrue(highGroundedHoplite != null, "the ground-alignment fixture must be spawnable");
        Vec3 highSpawn = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 7, 2)));
        highGroundedHoplite.setPos(highSpawn);
        highGroundedHoplite.configure(
                new Vec3(1.0D, 0.0D, 0.0D),
                Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 7, 2))),
                UnknownGreekCombatGoal.PHALANX_SPEED_PAST,
                0,
                helper.getLevel().getGameTime() + 200L,
                true);
        helper.assertTrue(
                Math.abs(highGroundedHoplite.getY() - spawn.y) <= 0.02D,
                "a normal formation must resolve a distant local floor instead of inheriting"
                        + " the player's elevated Y (currentY=" + highGroundedHoplite.getY() + ")");
        SpectralHopliteEntity airborneHoplite = EchoesShowThePast.SPECTRAL_HOPLITE.get().create(
                helper.getLevel(),
                EntitySpawnReason.TRIGGERED);
        helper.assertTrue(airborneHoplite != null, "the airborne formation fixture must be spawnable");
        Vec3 airborneSpawn = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 7, 4)));
        airborneHoplite.setPos(airborneSpawn);
        airborneHoplite.configure(
                new Vec3(1.0D, 0.0D, 0.0D),
                Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 7, 4))),
                UnknownGreekCombatGoal.PHALANX_SPEED_PAST,
                0,
                helper.getLevel().getGameTime() + 200L,
                false);
        helper.assertValueEqual(
                airborneHoplite.getY(),
                airborneSpawn.y,
                "an anti-height formation must deliberately retain its airborne corridor");
        helper.succeedWhen(() -> {
            helper.assertTrue(hoplite.isAlive(), "a temporarily unavailable route must not erase the row");
            double expectedTravel = UnknownGreekCombatGoal.PHALANX_SPEED_PAST
                    * hoplite.tickCount;
            double loweredFloorX = helper.absolutePos(new BlockPos(3, 1, 3)).getX() + 0.5D;
            helper.assertTrue(
                    hoplite.tickCount > 0
                            && hoplite.getX() >= loweredFloorX - 0.02D
                            && hoplite.getX() >= spawn.x + expectedTravel - 0.02D,
                    "the spectral row must cross solid arena geometry at full march speed"
                            + " (start=" + spawn.x
                            + ", current=" + hoplite.getX()
                            + ", tickCount=" + hoplite.tickCount
                            + ", alive=" + hoplite.isAlive() + ")");
            helper.assertTrue(
                    Math.abs(hoplite.getY() - (spawn.y - 1.0D)) <= 0.02D,
                    "the formation must descend onto a lower clear floor without floating"
                            + " (startY=" + spawn.y + ", currentY=" + hoplite.getY() + ")");
        });
    }

    private static void unknownGreekCombatGeometry(GameTestHelper helper) {
        Vec3 forward = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 insideArc = new Vec3(
                Math.sin(Math.toRadians(69.0D)),
                0.0D,
                Math.cos(Math.toRadians(69.0D)));
        Vec3 outsideArc = new Vec3(
                Math.sin(Math.toRadians(71.0D)),
                0.0D,
                Math.cos(Math.toRadians(71.0D)));
        helper.assertTrue(
                UnknownGreekCombatMath.isInsideFrontArc(
                        forward, insideArc, UnknownGreekCombatGoal.GUARD_ARC_DEGREES),
                "the aspis must cover the inner edge of its reinforced frontal arc");
        helper.assertFalse(
                UnknownGreekCombatMath.isInsideFrontArc(
                        forward, outsideArc, UnknownGreekCombatGoal.GUARD_ARC_DEGREES),
                "a sufficiently lateral hit must still bypass the aspis");
        helper.assertFalse(
                UnknownGreekCombatMath.isInsideFrontArc(
                        forward, forward.reverse(), UnknownGreekCombatGoal.GUARD_ARC_DEGREES),
                "back attacks must remain fully exposed");

        Vec3 sweepStart = new Vec3(0.0D, 1.25D, 0.0D);
        Vec3 sweepEnd = new Vec3(4.0D, 1.25D, 0.0D);
        helper.assertTrue(
                UnknownGreekCombatMath.capsuleContains(
                        sweepStart,
                        sweepEnd,
                        new Vec3(2.0D, 1.25D, 0.7D),
                        0.72D,
                        1.3D),
                "the continuous dory capsule must include targets between server ticks");
        helper.assertFalse(
                UnknownGreekCombatMath.capsuleContains(
                        sweepStart,
                        sweepEnd,
                        new Vec3(2.0D, 1.25D, 0.8D),
                        0.72D,
                        1.3D),
                "the dory capsule must not become an oversized rectangular sweep");
        helper.assertFalse(
                UnknownGreekCombatMath.capsuleContains(
                        sweepStart,
                        sweepEnd,
                        new Vec3(2.0D, 4.0D, 0.0D),
                        0.72D,
                        1.3D),
                "the stab must not hit through a different arena floor");
        helper.assertValueEqual(
                UnknownGreekCombatMath.impaleLiftProgress(0, 24),
                0.0D,
                "the impale must begin at the player's current safe position");
        helper.assertTrue(
                UnknownGreekCombatMath.impaleLiftProgress(8, 24) > 0.55D
                        && UnknownGreekCombatMath.impaleLiftProgress(12, 48) > 0.90D
                        && UnknownGreekCombatMath.impaleLiftProgress(48, 48) == 1.0D,
                "doubling the impale must extend its hold without turning the lift into a slow elevator");
        Vec3 innerSpear = UnknownGreekCombatMath.spearRingPoint(
                Vec3.ZERO,
                0,
                0,
                UnknownGreekCombatGoal.spearEruptionSpearCount(0),
                UnknownGreekCombatGoal.SPEAR_ERUPTION_FIRST_RADIUS,
                UnknownGreekCombatGoal.SPEAR_ERUPTION_RING_SPACING,
                0.0D);
        helper.assertTrue(
                innerSpear.horizontalDistance() >= 1.4D
                        && innerSpear.horizontalDistance() <= 2.1D,
                "the irregular first spectral ring must still begin close around the planted dory");
        Vec3 repeatedInnerSpear = UnknownGreekCombatMath.spearRingPoint(
                Vec3.ZERO,
                0,
                0,
                UnknownGreekCombatGoal.spearEruptionSpearCount(0),
                UnknownGreekCombatGoal.SPEAR_ERUPTION_FIRST_RADIUS,
                UnknownGreekCombatGoal.SPEAR_ERUPTION_RING_SPACING,
                0.0D);
        helper.assertValueEqual(
                repeatedInnerSpear,
                innerSpear,
                "spectral spear irregularity must be deterministic so server and renderer agree");
        helper.assertTrue(
                Math.abs(innerSpear.horizontalDistance()
                                - UnknownGreekCombatGoal.SPEAR_ERUPTION_FIRST_RADIUS) > 0.01D
                        && Math.abs(UnknownGreekCombatMath.spearVisualTilt(0, 0, 0.0D).x) > 0.01D,
                "spectral spears need visible radial and rotational variation instead of a perfect fence");
        Vec3 outerSpear = UnknownGreekCombatMath.spearRingPoint(
                Vec3.ZERO,
                3,
                0,
                UnknownGreekCombatGoal.spearEruptionSpearCount(3),
                UnknownGreekCombatGoal.SPEAR_ERUPTION_FIRST_RADIUS,
                UnknownGreekCombatGoal.SPEAR_ERUPTION_RING_SPACING,
                0.0D);
        helper.assertTrue(
                outerSpear.horizontalDistance() > innerSpear.horizontalDistance(),
                "successive rings must expand rapidly from the boss toward the arena edge");
        helper.assertTrue(
                UnknownGreekCombatMath.spearRingContains(
                        Vec3.ZERO,
                        new Vec3(UnknownGreekCombatGoal.spearEruptionRingRadius(2), 1.0D, 0.0D),
                        UnknownGreekCombatGoal.spearEruptionRingRadius(2),
                        UnknownGreekCombatGoal.SPEAR_ERUPTION_HIT_HALF_THICKNESS,
                        3.0D),
                "a player standing on the expanding edge must be hit once");
        helper.assertFalse(
                UnknownGreekCombatMath.spearRingContains(
                        Vec3.ZERO,
                        new Vec3(UnknownGreekCombatGoal.spearEruptionRingRadius(2), 4.0D, 0.0D),
                        UnknownGreekCombatGoal.spearEruptionRingRadius(2),
                        UnknownGreekCombatGoal.SPEAR_ERUPTION_HIT_HALF_THICKNESS,
                        3.0D),
                "the ground eruption must not hit through another arena floor");
        helper.assertTrue(
                UnknownGreekCombatMath.spearFieldContains(
                        Vec3.ZERO,
                        new Vec3(4.0D, 1.0D, 0.0D),
                        UnknownGreekCombatGoal.spearEruptionRingRadius(3),
                        UnknownGreekCombatGoal.SPEAR_ERUPTION_FIRST_RADIUS,
                        UnknownGreekCombatGoal.SPEAR_ERUPTION_FIELD_MARGIN,
                        3.0D),
                "the risen spear field must slow players who enter between its concentric rows");
        helper.assertFalse(
                UnknownGreekCombatMath.spearFieldContains(
                        Vec3.ZERO,
                        new Vec3(0.4D, 1.0D, 0.0D),
                        UnknownGreekCombatGoal.spearEruptionRingRadius(3),
                        UnknownGreekCombatGoal.SPEAR_ERUPTION_FIRST_RADIUS,
                        UnknownGreekCombatGoal.SPEAR_ERUPTION_FIELD_MARGIN,
                        3.0D),
                "the clear center around the planted dory must not apply the spear-field slow");

        Vec3 locked = UnknownGreekCombatMath.horizontalDirection(
                Vec3.ZERO,
                new Vec3(4.0D, 0.0D, 0.0D),
                forward);
        Vec3 playerAfterLock = new Vec3(-3.0D, 0.0D, 5.0D);
        helper.assertValueEqual(
                locked,
                new Vec3(1.0D, 0.0D, 0.0D),
                "the tick-12 lock must preserve its original horizontal direction");
        helper.assertFalse(
                locked.equals(UnknownGreekCombatMath.horizontalDirection(
                        Vec3.ZERO,
                        playerAfterLock,
                        forward)),
                "moving after lock must produce a different aim without mutating the stored direction");
        helper.assertValueEqual(
                UnknownGreekCombatMath.predictHorizontal(
                        Vec3.ZERO,
                        new Vec3(1.0D, 0.4D, 0.0D),
                        10,
                        4.0D),
                new Vec3(4.0D, 0.0D, 0.0D),
                "charge prediction must clamp horizontal lead without inheriting vertical motion");
        helper.assertValueEqual(
                UnknownGreekCombatMath.snapToCardinal(new Vec3(0.8D, 0.0D, 0.4D)),
                new Vec3(1.0D, 0.0D, 0.0D),
                "phalanx rows must remain aligned to one stable world axis");
        Vec3 corridorPoint = UnknownGreekCombatMath.phalanxCorridorPoint(
                new Vec3(3.0D, 11.0D, -2.0D),
                new Vec3(1.0D, 0.0D, 0.0D),
                4.0D,
                13.0D,
                0.028D);
        helper.assertValueEqual(
                corridorPoint,
                new Vec3(16.0D, 11.028D, 2.0D),
                "the warning must remain on the exact flat line used by block-phasing hoplites");
        helper.assertTrue(
                UnknownGreekCombatMath.gapReachable(0.0D, 3.5D, 0.28D, 16),
                "the shifted Ruins gap must remain sprint-reachable in its 16-tick warning");
        helper.assertFalse(
                UnknownGreekCombatMath.gapReachable(0.0D, 5.0D, 0.28D, 16),
                "an unreachable second-row gap must be rejected by the combat contract");
        double firstGap = UnknownGreekCombatMath.initialPhalanxGap(4);
        double secondGap = UnknownGreekCombatMath.nextPhalanxGap(7, firstGap, Double.NaN);
        double thirdGap = UnknownGreekCombatMath.nextPhalanxGap(11, secondGap, firstGap);
        helper.assertTrue(
                Math.abs(secondGap - firstGap) <= 2.0D + 1.0E-6D
                        && Math.abs(thirdGap - secondGap) <= 2.0D + 1.0E-6D,
                "each Ruins row may only stay, step left, or step right by one lane");
        helper.assertTrue(
                UnknownGreekCombatMath.gapReachable(firstGap, secondGap, 0.28D, 16)
                        && UnknownGreekCombatMath.gapReachable(secondGap, thirdGap, 0.28D, 16),
                "every randomised opening must remain reachable before the next row arrives");
        helper.assertTrue(
                UnknownGreekCombatMath.isPhalanxGap(0.0D)
                        && !UnknownGreekCombatMath.isPhalanxGap(1.0D),
                "phalanx openings must snap to the authored lane table");
        helper.assertTrue(
                UnknownGreekCombatGoal.stabCutCount(false) == 2
                        && UnknownGreekCombatGoal.stabCutCount(true) == 3,
                "Greek melee must become a readable multi-thrust string");
        helper.assertTrue(
                UnknownGreekCombatGoal.stabCutStartTick(false, 1)
                        > UnknownGreekCombatGoal.stabWindupTicks(false),
                "the second dory thrust must wait for a recovery beat");
        helper.assertTrue(
                UnknownGreekCombatGoal.stabSequenceTicks(false)
                        > UnknownGreekCombatGoal.STAB_WINDUP_TICKS
                                + UnknownGreekCombatGoal.STAB_ACTIVE_TICKS
                                + UnknownGreekCombatGoal.STAB_RECOVERY_TICKS,
                "Past stab string duration must exceed the old single poke");
        helper.assertTrue(
                UnknownGreekCombatMath.isElevatedUnreachable(3.0D, false),
                "an elevated player without a valid navigation path must force the aerial pattern");
        helper.assertFalse(
                UnknownGreekCombatMath.isElevatedUnreachable(3.0D, true),
                "reachable stairs must preserve the complete attack pattern");
        helper.assertFalse(
                UnknownGreekCombatMath.isElevatedUnreachable(1.5D, false),
                "small arena height variations must not falsely trigger anti-cheese attacks");
        Vec3 javelinArcStart = new Vec3(1.0D, 2.0D, 3.0D);
        Vec3 javelinArcEnd = new Vec3(9.0D, 6.0D, 11.0D);
        helper.assertValueEqual(
                UnknownGreekCombatMath.javelinArcPoint(
                        javelinArcStart, javelinArcEnd, 0.0D, 6.0D),
                javelinArcStart,
                "the spectral throw must begin in Unknown's hand");
        helper.assertValueEqual(
                UnknownGreekCombatMath.javelinArcPoint(
                        javelinArcStart, javelinArcEnd, 1.0D, 6.0D),
                javelinArcEnd,
                "the spectral throw must end exactly at the locked impact point");
        helper.assertValueEqual(
                UnknownGreekCombatMath.javelinArcPoint(
                        javelinArcStart, javelinArcEnd, 0.5D, 6.0D),
                new Vec3(5.0D, 10.0D, 7.0D),
                "the midpoint must form a high, unmistakable airborne arc");
        double realisticLift = UnknownGreekCombatMath.javelinArcLift(
                javelinArcStart,
                javelinArcEnd);
        helper.assertTrue(
                realisticLift >= 2.0D && realisticLift < 4.0D,
                "a medium javelin throw must use a readable ballistic arc rather than a mortar trajectory");
        helper.assertTrue(
                UnknownGreekCombatMath.shouldShieldBash(
                        2.6D,
                        UnknownGreekCombatGoal.shieldBashPressureTicks(false),
                        UnknownGreekCombatGoal.shieldBashPressureTicks(false),
                        0),
                "sustained sword-range pressure must unlock the aspis counter");
        helper.assertFalse(
                UnknownGreekCombatMath.shouldShieldBash(
                                2.6D,
                                UnknownGreekCombatGoal.shieldBashPressureTicks(false) - 1,
                                UnknownGreekCombatGoal.shieldBashPressureTicks(false),
                                0)
                        || UnknownGreekCombatMath.shouldShieldBash(4.0D, 20, 4, 0)
                        || UnknownGreekCombatMath.shouldShieldBash(2.6D, 20, 4, 1),
                "the aspis counter must respect its telegraph pressure, range and cooldown");
        helper.assertTrue(
                UnknownGreekCombatGoal.isGuardActive(UnknownCombatState.NEUTRAL, 0, false)
                        && UnknownGreekCombatGoal.isGuardActive(UnknownCombatState.CHARGE, 40, false)
                        && UnknownGreekCombatGoal.isGuardActive(UnknownCombatState.PHALANX, 70, true)
                        && UnknownGreekCombatGoal.isGuardActive(
                                UnknownCombatState.SPEAR_ERUPTION, 60, true),
                "the raised aspis must remain authoritative through neutral and shield-forward attacks");
        helper.assertFalse(
                UnknownGreekCombatGoal.isGuardActive(UnknownCombatState.RECOVERY, 1, false)
                        || UnknownGreekCombatGoal.isGuardActive(
                                UnknownCombatState.CRASH_STUN, 1, true),
                "recovery and wall crash must remain real punish windows");
        helper.assertTrue(
                UnknownFightManager.ASPIS_BLOCK_GOLD_SPARKS >= 6
                        && UnknownFightManager.ASPIS_BLOCK_WHITE_SPARKS >= 3
                        && UnknownFightManager.ASPIS_BLOCK_RECOIL >= 0.3D,
                "a successful aspis block needs a compact spark burst and physical weapon rebound");
        helper.assertTrue(
                UnknownGreekCombatGoal.isElevationRangedState(UnknownCombatState.JAVELIN)
                        && UnknownGreekCombatGoal.isElevationRangedState(UnknownCombatState.PHALANX),
                "only javelin and phalanx may attack an unreachable elevated player");
        helper.assertFalse(
                UnknownGreekCombatGoal.isElevationRangedState(UnknownCombatState.STAB)
                        || UnknownGreekCombatGoal.isElevationRangedState(UnknownCombatState.CHARGE)
                        || UnknownGreekCombatGoal.isElevationRangedState(
                                UnknownCombatState.SPEAR_ERUPTION),
                "physical melee and ground eruptions must be excluded while the player is unreachable");

        helper.assertValueEqual(
                UnknownCombatState.values().length,
                22,
                "all attack states plus the appended Void execution must remain synchronized");
        helper.assertValueEqual(UnknownGreekCombatGoal.STAB_WINDUP_TICKS, 16, "Past stab windup");
        helper.assertValueEqual(UnknownGreekCombatGoal.STAB_LOCK_TICK, 12, "Past stab direction lock");
        helper.assertValueEqual(UnknownGreekCombatGoal.STAB_ACTIVE_TICKS, 7, "Past stab active ticks");
        helper.assertValueEqual(UnknownGreekCombatGoal.STAB_RECOVERY_TICKS, 20, "Past stab recovery");
        helper.assertValueEqual(UnknownGreekCombatGoal.STAB_DAMAGE, 8.0F, "Past stab raw damage");
        helper.assertValueEqual(UnknownGreekCombatGoal.CHARGE_WINDUP_PAST, 26, "Past charge windup");
        helper.assertValueEqual(UnknownGreekCombatGoal.CHARGE_WINDUP_RUINS, 22, "Ruins charge windup");
        helper.assertValueEqual(UnknownGreekCombatGoal.CRASH_STUN_TICKS, 32, "charge crash stun");
        helper.assertValueEqual(UnknownGreekCombatGoal.PHALANX_WARNING_PAST, 28, "Past phalanx warning");
        helper.assertValueEqual(UnknownGreekCombatGoal.PHALANX_WARNING_RUINS, 24, "Ruins phalanx warning");
        helper.assertValueEqual(UnknownGreekCombatGoal.PHALANX_SECOND_ROW_DELAY, 16, "Ruins row separation");
        helper.assertValueEqual(UnknownGreekCombatGoal.PHALANX_THIRD_ROW_DELAY, 32, "third Ruins row timing");
        helper.assertValueEqual(UnknownGreekCombatGoal.JAVELIN_WARNING_PAST, 24, "Past javelin warning");
        helper.assertValueEqual(UnknownGreekCombatGoal.JAVELIN_WARNING_RUINS, 20, "Ruins javelin warning");
        helper.assertValueEqual(UnknownGreekCombatGoal.JAVELIN_LOCK_PAST, 14, "Past javelin target lock");
        helper.assertValueEqual(UnknownGreekCombatGoal.JAVELIN_LOCK_RUINS, 12, "Ruins javelin target lock");
        helper.assertValueEqual(UnknownGreekCombatGoal.JAVELIN_DAMAGE, 10.0F, "javelin raw damage");
        helper.assertValueEqual(UnknownGreekCombatGoal.JAVELIN_BLOCKED_DAMAGE, 5.0F, "blocked javelin damage");
        helper.assertTrue(
                UnknownGreekCombatGoal.stabImpaleTicks(false) == 48
                        && UnknownGreekCombatGoal.stabImpaleTicks(true) == 44
                        && UnknownGreekCombatGoal.STAB_IMPALE_TICK_DAMAGE >= 4.0F
                        && UnknownGreekCombatGoal.STAB_IMPALE_LIFT >= 2.0D,
                "a clean stab must hold twice as long and make the impalement substantially dangerous");
        helper.assertTrue(
                UnknownGreekCombatGoal.spearEruptionRingCount(true)
                                > UnknownGreekCombatGoal.spearEruptionRingCount(false)
                        && UnknownGreekCombatGoal.spearEruptionTotalSpearCount(false) >= 180
                        && UnknownGreekCombatGoal.spearEruptionTotalSpearCount(true) >= 290,
                "the concentric eruption must contain a deliberately excessive mass of fine spears");
        helper.assertTrue(
                UnknownGreekCombatGoal.spearEruptionLockTicks(false)
                                < UnknownGreekCombatGoal.spearEruptionWindupTicks(false)
                        && UnknownGreekCombatGoal.spearEruptionRingDelayTicks(true) <= 1
                        && UnknownGreekCombatGoal.spearEruptionPersistTicks(false) >= 40
                        && UnknownGreekCombatGoal.SPEAR_ERUPTION_SLOW_AMPLIFIER >= 2
                        && UnknownGreekCombatGoal.spearEruptionCooldownTicks(true)
                                < UnknownGreekCombatGoal.spearEruptionCooldownTicks(false),
                "the rings must erupt extremely quickly, remain for seconds and accelerate in Ruins");
        helper.assertValueEqual(
                UnknownGreekCombatGoal.attackPatternLength(false),
                5,
                "Past must teach every Greek attack in a compact pattern");
        helper.assertValueEqual(
                UnknownGreekCombatGoal.attackPatternLength(true),
                13,
                "Ruins must weave the concentric spear eruption repeatedly into its pressure pattern");
        helper.assertValueEqual(
                UnknownGreekCombatGoal.phalanxRowCount(true),
                3,
                "Ruins phalanx must contain three separate attacks");
        helper.assertTrue(
                UnknownGreekCombatGoal.neutralDelayAfterAttack(true)
                        < UnknownGreekCombatGoal.neutralDelayAfterAttack(false),
                "Ruins must chain attacks more aggressively than Past");
        helper.assertTrue(
                UnknownGreekCombatGoal.neutralDelayAfterAttack(false) <= 6,
                "Past must resume pressure within six ticks after recovery");
        helper.assertTrue(
                UnknownGreekCombatGoal.neutralDelayAfterAttack(true) <= 1,
                "Ruins must resume pressure almost immediately after recovery");
        helper.assertTrue(
                UnknownGreekCombatGoal.PHALANX_PATH_ALLOWANCE_TICKS <= 6,
                "a completed spectral crossing must not leave the boss waiting for a long grace period");
        helper.assertTrue(
                UnknownGreekCombatGoal.PHALANX_RECOVERY_PAST <= 10
                        && UnknownGreekCombatGoal.PHALANX_RECOVERY_RUINS <= 8,
                "phalanx recovery must be brief in both stages");
        helper.assertTrue(
                UnknownGreekCombatGoal.initialAttackDelay(false) <= 10
                        && UnknownGreekCombatGoal.initialAttackDelay(true) <= 6,
                "both stages must open combat without a long idle delay");
        helper.assertTrue(
                UnknownGreekCombatGoal.phalanxCooldownTicks(false) <= 100
                        && UnknownGreekCombatGoal.phalanxCooldownTicks(true) <= 75,
                "the phalanx selection cooldown must expire by the next valid pattern window");
        helper.assertTrue(
                UnknownGreekCombatGoal.chargeCooldownTicks(false) <= 80
                        && UnknownGreekCombatGoal.chargeCooldownTicks(true) <= 55,
                "charge availability must keep pace with the faster neutral cadence");
        helper.assertTrue(
                UnknownGreekCombatGoal.javelinCooldownTicks(false) <= 70
                        && UnknownGreekCombatGoal.javelinCooldownTicks(true) <= 52,
                "the normal pattern and aerial fallback must receive javelins frequently");
        helper.assertTrue(
                UnknownGreekCombatGoal.chargeSpeed(true) > UnknownGreekCombatGoal.chargeSpeed(false),
                "Ruins must increase charge pressure without changing its rules");
        helper.assertValueEqual(
                UnknownGreekCombatGoal.STAB_LUNGE_DISTANCE,
                0.0D,
                "the dory stab must not translate the boss through the arena");
        helper.assertTrue(
                UnknownGreekCombatGoal.STAB_DORY_REACH >= 8.4D,
                "the stationary dory extension must preserve the accepted combat reach");
        helper.assertValueEqual(
                UnknownGreekCombatGoal.STAB_SEQUENCE_TICKS,
                UnknownGreekCombatGoal.stabSequenceTicks(false),
                "one animation must cover windup, extension and the complete recovery");
        helper.assertValueEqual(
                UnknownGreekCombatGoal.stateAtSequenceTick(35),
                UnknownCombatState.STAB,
                "the last fallback thrust tick must remain committed");
        helper.assertValueEqual(
                UnknownGreekCombatGoal.stateAtSequenceTick(36),
                UnknownCombatState.RECOVERY,
                "a missed or blocked string must enter recovery without restarting its clock");
        helper.assertValueEqual(
                UnknownGreekCombatGoal.stateAtSequenceTick(55),
                UnknownCombatState.RECOVERY,
                "the last authored recovery frame must remain exposed");
        helper.assertValueEqual(
                UnknownGreekCombatGoal.stateAtSequenceTick(56),
                UnknownCombatState.NEUTRAL,
                "the sequence must always release back to neutral for the next attack");
        helper.assertTrue(
                UnknownGreekCombatGoal.STAB_EFFECTIVE_REACH >= 8.4D,
                "the server capsule must reach the end of the rendered lane");
        helper.assertTrue(
                UnknownEgyptianCombatMath.sweptArcContains(
                        new Vec3(0.0D, 1.4D, 0.0D),
                        forward,
                        -24.0D,
                        28.0D,
                        new Vec3(0.0D, 1.4D, 3.6D),
                        UnknownEgyptianCombatGoal.INNER_RADIUS,
                        UnknownEgyptianCombatGoal.OUTER_RADIUS,
                        1.2D),
                "the khopesh hitbox must cover the same angular band swept by the rendered blade");
        helper.assertTrue(
                UnknownEgyptianCombatMath.sweptArcContains(
                        new Vec3(0.0D, 1.4D, 0.0D),
                        forward,
                        28.0D,
                        -24.0D,
                        new Vec3(0.0D, 1.4D, 3.6D),
                        UnknownEgyptianCombatGoal.INNER_RADIUS,
                        UnknownEgyptianCombatGoal.OUTER_RADIUS,
                        1.2D),
                "the reverse khopesh cut must use identical continuous geometry");
        helper.assertFalse(
                UnknownEgyptianCombatMath.sweptArcContains(
                        Vec3.ZERO,
                        forward,
                        -20.0D,
                        20.0D,
                        new Vec3(4.0D, 0.0D, 1.0D),
                        UnknownEgyptianCombatGoal.INNER_RADIUS,
                        UnknownEgyptianCombatGoal.OUTER_RADIUS,
                        1.2D),
                "the combo must not damage outside its visible fan");
        helper.assertFalse(
                UnknownEgyptianCombatMath.sweptArcContains(
                        Vec3.ZERO,
                        forward,
                        -20.0D,
                        20.0D,
                        new Vec3(0.0D, 3.0D, 3.0D),
                        UnknownEgyptianCombatGoal.INNER_RADIUS,
                        UnknownEgyptianCombatGoal.OUTER_RADIUS,
                        1.2D),
                "the khopesh must not cut through another arena level");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.sequenceTicks(false),
                72,
                "Egyptian Past khopesh clock must match the compact complete flurry");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.sequenceTicks(true),
                63,
                "Egyptian Ruins must accelerate the same authored khopesh animation by 15 percent");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.comboCutStartTick(false, 0),
                18,
                "the first server slash must land on the first authored khopesh swing");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.comboCutStartTick(false, 1),
                29,
                "the reverse server slash must land on the second authored khopesh swing");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.comboCutStartTick(false, 4),
                59,
                "the shield punish must retain all three late authored swings");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.comboCutStartTick(false, 2)
                                - (UnknownEgyptianCombatGoal.comboCutStartTick(false, 1)
                                        + UnknownEgyptianCombatGoal.activeTicks(false))
                        <= UnknownEgyptianCombatGoal.betweenCutsTicks(false),
                "an extended guard punish must not hide a one-second pause between cuts");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.comboVisualStopTick(false, 2),
                42,
                "an unextended combo must hand directly into its short authored recovery");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.comboRecoveryEndTick(false, 2),
                42,
                "a two-cut combo must release after its short visible recovery, not wait for unused cuts");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.comboRecoveryEndTick(true, 2),
                36,
                "Ruins must compress the two-cut recovery without skipping its final pose");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.initialAttackDelayTicks(false) <= 1
                        && UnknownEgyptianCombatGoal.initialAttackDelayTicks(true) == 0
                        && UnknownEgyptianCombatGoal.interAttackDelayTicks(false) == 0
                        && UnknownEgyptianCombatGoal.interAttackDelayTicks(true) == 0,
                "Egyptian combat must enter and chain attacks without an artificial neutral pause");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.defensiveComboMaxCuts(false) == 5
                        && UnknownEgyptianCombatGoal.defensiveComboMaxCuts(true) == 5,
                "continued shield blocks must extend into the complete five-cut animation");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.shieldBreakBlockCount(true) == 5,
                "blocking the complete authored flurry must disable the shield for five seconds");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.lockTick(false)
                        < UnknownEgyptianCombatGoal.windupTicks(false),
                "the player needs visible time after the khopesh direction lock");
        helper.assertValueEqual(
                UnknownEgyptianCombatMath.cardinalEscapeDirection(new Vec3(0.2D, 0.0D, -0.9D)),
                Direction.NORTH,
                "the Duat wall must snap to the dominant escape axis");
        helper.assertValueEqual(
                UnknownEgyptianCombatMath.cardinalEscapeDirection(new Vec3(-0.8D, 0.0D, 0.3D)),
                Direction.WEST,
                "east-west retreats must produce a north-south wall");
        BlockPos wallCenter = new BlockPos(10, 64, 10);
        List<BlockPos> wallCells = UnknownEgyptianCombatMath.duatWallCells(
                wallCenter,
                Direction.SOUTH,
                UnknownEgyptianCombatGoal.WALL_WIDTH,
                UnknownEgyptianCombatGoal.WALL_HEIGHT_BLOCKS);
        helper.assertValueEqual(
                wallCells.size(),
                UnknownEgyptianCombatGoal.WALL_WIDTH * UnknownEgyptianCombatGoal.WALL_HEIGHT_BLOCKS,
                "the physical Duat wall must contain one complete 9x4 collision grid");
        helper.assertValueEqual(
                wallCells.stream().distinct().count(),
                (long) wallCells.size(),
                "no physical wall cell may be written twice");
        helper.assertTrue(
                wallCells.stream().allMatch(pos -> pos.getZ() == wallCenter.getZ())
                        && wallCells.stream().mapToInt(BlockPos::getX).min().orElseThrow() == 6
                        && wallCells.stream().mapToInt(BlockPos::getX).max().orElseThrow() == 14
                        && wallCells.stream().mapToInt(BlockPos::getY).min().orElseThrow() == 64
                        && wallCells.stream().mapToInt(BlockPos::getY).max().orElseThrow() == 67,
                "a south-facing escape wall must remain one block thick, nine wide and four high");
        helper.assertTrue(
                UnknownEgyptianCombatMath.isEscaping(
                        Vec3.ZERO,
                        new Vec3(0.0D, 0.0D, 6.0D),
                        new Vec3(0.0D, 0.0D, 0.24D))
                        && !UnknownEgyptianCombatMath.isEscaping(
                                Vec3.ZERO,
                                new Vec3(0.0D, 0.0D, 6.0D),
                                new Vec3(0.24D, 0.0D, 0.0D)),
                "Duat route control must trigger for retreat, not ordinary strafing");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.GATE_PANEL_COUNT == 1,
                "one escape-intercepting wall must replace the enclosing three-sided cage");
        helper.assertTrue(
                UnknownEgyptianCombatMath.wallIsAhead(
                        new Vec3(2.0D, 64.0D, 3.0D),
                        Vec3.atCenterOf(wallCenter),
                        new Vec3(0.0D, 0.0D, 1.0D)),
                "the intercepting wall must never appear behind the fleeing player");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.sealSequenceTicks(false),
                UnknownEgyptianCombatGoal.gatePanelSpawnTick(false, 0)
                        + UnknownEgyptianCombatGoal.WALL_BUILD_TICKS,
                "Past Duat casts must release Unknown immediately after the intercepting wall appears");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.thresholdActiveTicks(false) >= 90
                        && UnknownEgyptianCombatGoal.MAX_ACTIVE_GATES_RUINS == 1,
                "only one route-control wall may linger, so separate casts cannot form a cage");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.WALL_BUILD_TICKS == UnknownEgyptianCombatGoal.WALL_HEIGHT_BLOCKS,
                "the physical wall must rise one collision row per tick instead of appearing inside the player");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.sealLockTick(false)
                        < UnknownEgyptianCombatGoal.sealWarningTicks(false),
                "the locked solar pattern must leave a visible dodge window");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.sealCooldownTicks(true)
                        < UnknownEgyptianCombatGoal.sealCooldownTicks(false),
                "Ruins must cycle solar pressure faster without changing the mechanic");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.FIRST_DAMAGE >= 14.0F
                        && UnknownEgyptianCombatGoal.JUDGMENT_DAMAGE >= 44.0F,
                "Judgment of Ra must be a near-fatal dodge check even through diamond armor");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.judgmentDamage(true) < 16.0F
                        && UnknownEgyptianCombatGoal.judgmentDamage(true)
                                < UnknownEgyptianCombatGoal.judgmentDamage(false) * 0.4F,
                "a correctly faced shield must prevent execution but break for five seconds");
        helper.assertTrue(
                UnknownEgyptianCombatMath.judgmentBeamContains(
                        Vec3.ZERO,
                        forward,
                        UnknownEgyptianCombatGoal.JUDGMENT_LENGTH,
                        UnknownEgyptianCombatGoal.judgmentHalfWidth(false),
                        new Vec3(1.44D, 7.0D, 20.0D),
                        UnknownEgyptianCombatGoal.JUDGMENT_VERTICAL_REACH),
                "Judgment of Ra must cover its complete rendered corridor at elevation");
        helper.assertFalse(
                UnknownEgyptianCombatMath.judgmentBeamContains(
                        Vec3.ZERO,
                        forward,
                        UnknownEgyptianCombatGoal.JUDGMENT_LENGTH,
                        UnknownEgyptianCombatGoal.judgmentHalfWidth(false),
                        new Vec3(1.46D, 0.0D, 20.0D),
                        UnknownEgyptianCombatGoal.JUDGMENT_VERTICAL_REACH),
                "stepping beyond the golden boundary must evade Judgment of Ra");
        helper.assertFalse(
                UnknownEgyptianCombatMath.judgmentBeamContains(
                        Vec3.ZERO,
                        forward,
                        UnknownEgyptianCombatGoal.JUDGMENT_LENGTH,
                        UnknownEgyptianCombatGoal.judgmentHalfWidth(false),
                        new Vec3(0.0D, 0.0D, -0.1D),
                        UnknownEgyptianCombatGoal.JUDGMENT_VERTICAL_REACH),
                "the solar corridor must never damage behind Unknown");
        helper.assertFalse(
                UnknownEgyptianCombatMath.judgmentBeamContains(
                        Vec3.ZERO,
                        forward,
                        UnknownEgyptianCombatGoal.JUDGMENT_LENGTH,
                        UnknownEgyptianCombatGoal.judgmentHalfWidth(false),
                        new Vec3(0.0D, 0.0D, 28.1D),
                        UnknownEgyptianCombatGoal.JUDGMENT_VERTICAL_REACH),
                "the solar corridor must end at its rendered arrow");
        double pastWaveStart = UnknownEgyptianCombatGoal.judgmentWaveDistance(false, 0.0D);
        double pastWaveMiddle = UnknownEgyptianCombatGoal.judgmentWaveDistance(false, 3.0D);
        double pastWaveNext = UnknownEgyptianCombatGoal.judgmentWaveDistance(false, 4.0D);
        double pastWaveEnd = UnknownEgyptianCombatGoal.judgmentWaveDistance(
                false,
                UnknownEgyptianCombatGoal.judgmentActiveTicks(false) - 1.0D);
        helper.assertTrue(
                Math.abs(pastWaveStart - UnknownEgyptianCombatGoal.JUDGMENT_WAVE_START) < 1.0E-6D
                        && pastWaveMiddle > pastWaveStart
                        && pastWaveNext > pastWaveMiddle
                        && Math.abs(pastWaveEnd - UnknownEgyptianCombatGoal.JUDGMENT_LENGTH)
                                < 1.0E-6D,
                "the authoritative solar front must cross the whole rendered lane monotonically");
        helper.assertTrue(
                UnknownEgyptianCombatMath.judgmentWavefrontContains(
                        Vec3.ZERO,
                        forward,
                        UnknownEgyptianCombatGoal.JUDGMENT_LENGTH,
                        UnknownEgyptianCombatGoal.judgmentHalfWidth(false),
                        new Vec3(0.0D, 0.0D, 12.0D),
                        UnknownEgyptianCombatGoal.JUDGMENT_VERTICAL_REACH,
                        pastWaveMiddle,
                        pastWaveNext,
                        0.3D),
                "Judgment damage must arrive with the visible white-gold front");
        helper.assertFalse(
                UnknownEgyptianCombatMath.judgmentWavefrontContains(
                        Vec3.ZERO,
                        forward,
                        UnknownEgyptianCombatGoal.JUDGMENT_LENGTH,
                        UnknownEgyptianCombatGoal.judgmentHalfWidth(false),
                        new Vec3(0.0D, 0.0D, 4.0D),
                        UnknownEgyptianCombatGoal.JUDGMENT_VERTICAL_REACH,
                        pastWaveMiddle,
                        pastWaveNext,
                        0.3D),
                "a player behind a successfully dodged front must not receive a delayed invisible hit");
        helper.assertTrue(
                UnknownEgyptianCombatMath.judgmentSurfaceContinuous(
                        new Vec3(0.0D, 64.0D, 0.0D),
                        new Vec3(0.5D, 65.0D, 0.0D),
                        UnknownEgyptianCombatGoal.JUDGMENT_MAX_VISUAL_SURFACE_STEP),
                "the solar blade must remain continuous over one-block stairs");
        helper.assertFalse(
                UnknownEgyptianCombatMath.judgmentSurfaceContinuous(
                        new Vec3(0.0D, 64.0D, 0.0D),
                        new Vec3(0.5D, 67.0D, 0.0D),
                        UnknownEgyptianCombatGoal.JUDGMENT_MAX_VISUAL_SURFACE_STEP),
                "the solar blade must not bridge a cliff with a floating diagonal sheet");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.judgmentSequenceTicks(false),
                54,
                "Egyptian Past Judgment must finish its strike and brisk authored recovery in 54 ticks");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.judgmentSequenceTicks(true),
                46,
                "Egyptian Ruins must compress Judgment without leaving a neutral tail");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.judgmentCooldownTicks(false)
                                > UnknownEgyptianCombatGoal.judgmentSequenceTicks(false)
                        && UnknownEgyptianCombatGoal.judgmentCooldownTicks(true)
                                > UnknownEgyptianCombatGoal.judgmentSequenceTicks(true),
                "Judgment must retain a real recast lock instead of becoming available on its final frame");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.isElevatedUnreachable(3.25D, false)
                        && !UnknownEgyptianCombatGoal.isElevatedUnreachable(3.25D, true)
                        && !UnknownEgyptianCombatGoal.isElevatedUnreachable(-4.0D, false),
                "ordinary stairs and a boss standing above the player must not trigger the anti-height attack");
        helper.assertFalse(
                UnknownEgyptianCombatGoal.canSelectJudgment(
                        true, false, true, 0, 8.0D),
                "Judgment of Ra must never select itself twice consecutively");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.canSelectJudgment(
                        true, false, false, 0, 8.0D),
                "a genuinely unreachable elevated player still needs one readable Judgment punish");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.judgmentLockTick(false)
                        < UnknownEgyptianCombatGoal.judgmentWarningTicks(false),
                "the solar line must remain fixed long enough for a deliberate lateral dodge");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.judgmentHalfWidth(true)
                        > UnknownEgyptianCombatGoal.judgmentHalfWidth(false),
                "Ruins must intensify the same beam with a wider readable corridor");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.huntMinimumBeatCount(false) == 2
                        && UnknownEgyptianCombatGoal.huntMaximumBeatCount(false) == 4
                        && UnknownEgyptianCombatGoal.huntMinimumBeatCount(true) == 4
                        && UnknownEgyptianCombatGoal.huntMaximumBeatCount(true) == 6,
                "Sekhmet's hunt must vary inside the authored 2-4 / 4-6 ranges");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.selectHuntBeatCount(
                        false, 3.0D, 0.02D, false, false, 1.0F, 0, 0),
                2,
                "a calm Past opening should use the minimum readable pursuit");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.selectHuntBeatCount(
                                false, 7.5D, 0.2D, false, false, 0.8F, 0, 0)
                        < UnknownEgyptianCombatGoal.selectHuntBeatCount(
                                false, 7.5D, 0.2D, false, false, 0.8F, 0, 1),
                "the same evasive situation must still admit tactical variation instead of a fixed count");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.selectHuntBeatCount(
                        false, 8.0D, 0.24D, true, true, 0.35F, 14, 2),
                4,
                "Past must spend its full four-cut hunt against a pressured evasive guard");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.selectHuntBeatCount(
                        true, 3.0D, 0.02D, false, false, 1.0F, 0, 0),
                4,
                "Ruins must never drop below four pursuit cuts");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.selectHuntBeatCount(
                        true, 8.0D, 0.24D, true, true, 0.35F, 14, 2),
                6,
                "a desperate Ruins Unknown should choose the complete six-cut execution");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.huntStrideTicks(true)
                                < UnknownEgyptianCombatGoal.huntStrideTicks(false)
                        && UnknownEgyptianCombatGoal.huntSequenceTicks(true)
                                > UnknownEgyptianCombatGoal.huntSequenceTicks(false),
                "Ruins must compress every hunt beat while sustaining the pursuit for longer");
        helper.assertValueEqual(
                UnknownEgyptianCombatGoal.huntSequenceTicks(true, 6),
                109,
                "a six-beat Ruins hunt must keep its synchronized recovery on the same clock");
        Vec3 firstFlank = UnknownEgyptianCombatMath.sekhmetFlankTarget(
                Vec3.ZERO, new Vec3(0.0D, 0.0D, 6.0D), new Vec3(0.0D, 0.0D, 0.2D), 0, 1.6D);
        Vec3 secondFlank = UnknownEgyptianCombatMath.sekhmetFlankTarget(
                Vec3.ZERO, new Vec3(0.0D, 0.0D, 6.0D), new Vec3(0.0D, 0.0D, 0.2D), 1, 1.6D);
        helper.assertTrue(
                firstFlank.x * secondFlank.x < 0.0D && firstFlank.z > 6.0D,
                "successive hunt cuts must recalculate on alternating flanks of the predicted player");
        Vec3 stationaryTarget = new Vec3(0.0D, 0.0D, 7.0D);
        Vec3 strikeDirection = UnknownEgyptianCombatMath.sekhmetStrikeDirection(
                firstFlank, stationaryTarget, forward);
        helper.assertTrue(
                UnknownGreekCombatMath.isInsideFrontArc(
                        strikeDirection,
                        stationaryTarget.subtract(firstFlank),
                        8.0D),
                "Sekhmet must lock the slash from its destination through a stationary player");
        helper.assertTrue(
                Math.abs(UnknownEgyptianCombatGoal.huntDashStepLength(
                                7.2D,
                                UnknownEgyptianCombatGoal.HUNT_DASH_PAST,
                                UnknownEgyptianCombatGoal.HUNT_DASH_STEP_PAST,
                                UnknownEgyptianCombatGoal.HUNT_MAX_DASH_STEP_PAST)
                        - 1.2D) < 1.0E-6D,
                "each hunt dash must divide the remaining route so it actually reaches its strike anchor");
        helper.assertTrue(
                UnknownEgyptianCombatMath.huntHitAllowed(100L, 111L,
                        UnknownEgyptianCombatGoal.HUNT_HIT_GRACE_TICKS)
                        && !UnknownEgyptianCombatMath.huntHitAllowed(100L, 106L,
                                UnknownEgyptianCombatGoal.HUNT_HIT_GRACE_TICKS),
                "separate hunt beats need a direct-hit grace window against unavoidable damage stacks");
        helper.assertTrue(
                UnknownEgyptianCombatGoal.SHIELD_BREAK_TICKS == 100,
                "a blocked heavy Judgment or full slash chain must disable shields for five seconds");
        try (InputStream animations = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/geckolib/animations/entity/unknown.animation.json");
                InputStream shader = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/shaders/core/unknown_stab_telegraph.fsh");
                InputStream judgmentShader = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/shaders/core/egyptian_solar_judgment.fsh");
                InputStream raSigilShader = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/shaders/core/ra_judgment_sigil.fsh");
                InputStream raSigilTexture = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/textures/effect/ra_judgment_sigil.png");
                InputStream sekhmetShader = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/shaders/core/egyptian_sekhmet_hunt.fsh");
                InputStream hopliteModel = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/geckolib/models/entity/spectral_hoplite.geo.json");
                InputStream hopliteShader = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/shaders/core/spectral_hoplite.fsh");
                InputStream altarOrbitShader = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/shaders/core/altar_orbit.fsh")) {
            helper.assertTrue(animations != null, "Unknown GeckoLib animations must be packaged");
            helper.assertTrue(shader != null, "the positional stab shader must be packaged");
            helper.assertTrue(judgmentShader != null, "Judgment of Ra must package its localized shader");
            helper.assertTrue(raSigilShader != null,
                    "Judgment of Ra must package a sigil shader independent from Horus");
            helper.assertTrue(raSigilTexture != null,
                    "Judgment of Ra must package its dedicated high-resolution glyph");
            helper.assertTrue(sekhmetShader != null,
                    "Sekhmet's hunt must package its own depth-tested ceremonial shader");
            helper.assertTrue(hopliteModel != null, "the spectral formation must have a dedicated hoplite rig");
            helper.assertTrue(hopliteShader != null, "the spectral formation must have a dedicated depth-stable shader");
            helper.assertTrue(altarOrbitShader != null, "the Echo Altar selected-fragment orbit shader must be packaged");
            JsonObject animationRoot = JsonParser.parseReader(new java.io.InputStreamReader(
                            animations,
                            StandardCharsets.UTF_8))
                    .getAsJsonObject();
            double animationLength = animationRoot
                    .getAsJsonObject("animations")
                    .getAsJsonObject("combat.greek.stab")
                    .get("animation_length")
                    .getAsDouble();
            helper.assertTrue(
                    Math.abs(animationLength - 2.15D) < 1.0E-6D,
                    "one GeckoLib sequence must finish extension and recovery across all 43 ticks");
            JsonObject greekAnimations = animationRoot.getAsJsonObject("animations");
            helper.assertTrue(
                    greekAnimations.has("combat.greek.charge")
                            && greekAnimations.has("combat.greek.crash")
                            && greekAnimations.has("combat.greek.impale")
                            && greekAnimations.has("combat.greek.javelin")
                            && greekAnimations.has("combat.greek.spear_eruption")
                            && greekAnimations.has("combat.greek.phalanx_summon")
                            && greekAnimations.has("combat.greek.phalanx_march"),
                    "every authoritative Greek state must have an authored GeckoLib pose");
            helper.assertTrue(
                    greekAnimations.has("combat.egypt.khopesh_combo")
                            && greekAnimations.has("combat.egypt.khopesh_recovery")
                            && greekAnimations.has("combat.egypt.khopesh_recovery_late")
                            && greekAnimations
                                            .getAsJsonObject("combat.egypt.khopesh_combo")
                                            .get("animation_length")
                                            .getAsDouble()
                                    >= 3.599D
                            && greekAnimations
                                            .getAsJsonObject("combat.egypt.khopesh_combo")
                                            .get("animation_length")
                                            .getAsDouble()
                                    <= 3.601D,
                    "the Egyptian chain needs one compact uninterrupted GeckoLib flurry");
            helper.assertTrue(
                    greekAnimations.has("combat.egypt.duat_gate")
                            && Math.abs(greekAnimations
                                            .getAsJsonObject("combat.egypt.duat_gate")
                                            .get("animation_length")
                                            .getAsDouble()
                                    - 3.05D) < 1.0E-6D,
                    "the complete Duat gate invocation needs one uninterrupted GeckoLib sequence");
            helper.assertTrue(
                    greekAnimations.has("combat.egypt.solar_judgment")
                            && Math.abs(greekAnimations
                                            .getAsJsonObject("combat.egypt.solar_judgment")
                                            .get("animation_length")
                                            .getAsDouble()
                                    - 2.7D) < 1.0E-6D,
                    "Judgment of Ra needs one uninterrupted GeckoLib sequence");
            helper.assertTrue(
                    greekAnimations.has("combat.egypt.sekhmet_recovery")
                            && Math.abs(greekAnimations
                                            .getAsJsonObject("combat.egypt.sekhmet_recovery")
                                            .get("animation_length")
                                            .getAsDouble()
                                    - 0.4D) < 1.0E-6D,
                    "Sekhmet's recovery must be visible but short enough to sustain final-phase pressure");
            helper.assertTrue(
                    greekAnimations.has("combat.egypt.sekhmet_hunt")
                            && Math.abs(greekAnimations
                                            .getAsJsonObject("combat.egypt.sekhmet_hunt")
                                            .get("animation_length")
                                            .getAsDouble()
                                    - 0.95D) < 1.0E-6D,
                    "Sekhmet's pursuit needs one authored GeckoLib beat that loops exactly with server timing");
            String shaderSource = new String(shader.readAllBytes(), StandardCharsets.UTF_8);
            helper.assertTrue(
                    shaderSource.contains("memoryWhite")
                            && shaderSource.contains("memoryGold")
                            && shaderSource.contains("rail"),
                    "the stab lane must use its dedicated white/gold shader instead of particles");
            helper.assertFalse(
                    shaderSource.contains("vec3 cyan"),
                    "Greek combat visuals must not retain the rejected cyan palette");
            helper.assertFalse(
                    shaderSource.contains("movingBand") || shaderSource.contains("runeCut"),
                    "the stab shader must stay visually quiet: no scrolling bands or rune noise");
            String judgmentShaderSource = new String(
                    judgmentShader.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    judgmentShaderSource.contains("sunGold")
                            && judgmentShaderSource.contains("sunWhite")
                            && judgmentShaderSource.contains("carvedBand"),
                    "Judgment must own a saturated Egyptian white/gold shader language");
            String raSigilShaderSource = new String(
                    raSigilShader.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    raSigilShaderSource.contains("raGold")
                            && raSigilShaderSource.contains("deepAmber")
                            && raSigilShaderSource.contains("sunWhite"),
                    "Ra's glyph must retain carved amber depth, saturated gold and a white-hot core");
            var raSigil = ImageIO.read(raSigilTexture);
            helper.assertTrue(
                    raSigil != null
                            && raSigil.getWidth() == 256
                            && raSigil.getHeight() == 256
                            && ((raSigil.getRGB(0, 0) >>> 24) & 0xFF) == 0,
                    "Ra's authored glyph must be a crisp 256px transparent texture");
            String sekhmetShaderSource = new String(
                    sekhmetShader.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    sekhmetShaderSource.contains("sekhmetGold")
                            && sekhmetShaderSource.contains("desertIvory")
                            && sekhmetShaderSource.contains("lapisInlay")
                            && sekhmetShaderSource.contains("bladeFlash"),
                    "the hunt shader must separate body, royal inlay and cutting edge without particles");
            String hopliteModelSource = new String(
                    hopliteModel.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    hopliteModelSource.contains("helmet_crest")
                            && hopliteModelSource.contains("aspis_gold")
                            && hopliteModelSource.contains("dory_tip"),
                    "helmet, aspis and dory gold accents must be authored into the rig, not intersecting item layers");
            String hopliteShaderSource = new String(
                    hopliteShader.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    hopliteShaderSource.contains("memoryGold")
                            && hopliteShaderSource.contains("goldMask")
                            && hopliteShaderSource.contains("vec3(1.0, 0.72, 0.10)"),
                    "the hoplite shader must use a deliberate, saturated white/gold hierarchy");
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect packaged Greek combat visuals", exception);
        }

        UnknownFightManager.ArenaBounds bounds =
                UnknownFightManager.arenaBounds(helper.getLevel());
        BlockPos origin = bounds.origin();
        Vec3i size = bounds.size();
        helper.assertTrue(
                UnknownFightManager.isInsideArenaVolume(helper.getLevel(), origin),
                "arena protection must include its minimum corner");
        helper.assertTrue(
                UnknownFightManager.isInsideArenaVolume(helper.getLevel(), origin.offset(
                        size.getX() - 1,
                        size.getY() - 1,
                        size.getZ() - 1)),
                "arena protection must include the last cell of the shared envelope");
        helper.assertFalse(
                UnknownFightManager.isInsideArenaVolume(
                        helper.getLevel(),
                        origin.offset(size.getX(), 0, 0)),
                "arena protection must clear exactly beyond the shared envelope");
        for (BlockPos pedestal : List.of(
                TimelessDimensions.PEDESTAL_GREEK,
                TimelessDimensions.PEDESTAL_EGYPTIAN,
                TimelessDimensions.PEDESTAL_MEDIEVAL)) {
            helper.assertTrue(
                    bounds.contains(pedestal)
                            && bounds.contains(pedestal.below())
                            && bounds.contains(pedestal.above(2))
                            && bounds.contains(UnknownFightManager.pedestalApproachFor(pedestal)),
                    "every pedestal station needs foundation, headroom and an in-bounds approach");
        }
        BlockPos bashFloor = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlock(bashFloor, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(bashFloor.east().above(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(bashFloor.east().above(2), Blocks.STONE.defaultBlockState(), 3);
        var bashTarget = helper.makeMockServerPlayerInLevel();
        bashTarget.snapTo(
                bashFloor.getX() + 0.68D,
                bashFloor.getY() + 1.0D,
                bashFloor.getZ() + 0.5D,
                0.0F,
                0.0F);
        helper.assertTrue(
                UnknownGreekCombatGoal.hasWallImmediatelyAhead(
                        helper.getLevel(), bashTarget, new Vec3(1.0D, 0.0D, 0.0D)),
                "aspis knockback must award wall damage only when the player's body meets a wall");
        helper.assertFalse(
                UnknownGreekCombatGoal.hasWallImmediatelyAhead(
                        helper.getLevel(), bashTarget, new Vec3(-1.0D, 0.0D, 0.0D)),
                "open-space aspis knockback must never invent wall-impact damage");
        bashTarget.discard();
        helper.succeed();
    }

    private static void unknownEgyptianPhysicalWall(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(6, 2, 6));
        for (int x = -5; x <= 5; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.getLevel().setBlock(
                        center.offset(x, -1, z),
                        Blocks.STONE.defaultBlockState(),
                        3);
            }
        }
        var boss = helper.spawn(
                EchoesShowThePast.UNKNOWN.get(),
                new Vec3(1.5D, 2.0D, 1.5D));
        var target = helper.makeMockServerPlayerInLevel();
        target.snapTo(
                center.getX() + 0.5D,
                center.getY(),
                center.getZ() + 0.5D,
                0.0F,
                0.0F);
        long now = helper.getLevel().getGameTime();
        TemporaryDuatWall wall = TemporaryDuatWall.atFixedPosition(
                helper.getLevel(),
                center,
                Direction.SOUTH,
                UnknownEgyptianCombatGoal.WALL_WIDTH,
                UnknownEgyptianCombatGoal.WALL_HEIGHT_BLOCKS,
                now - UnknownEgyptianCombatGoal.WALL_HEIGHT_BLOCKS + 1L,
                now + 20L);
        helper.assertTrue(wall != null, "the flat fixture must accept one physical Duat wall");
        helper.assertTrue(
                wall.tick(helper.getLevel(), boss, target),
                "all four sandstone rows must place without trapping either combatant");
        List<BlockPos> cells = UnknownEgyptianCombatMath.duatWallCells(
                center,
                Direction.SOUTH,
                UnknownEgyptianCombatGoal.WALL_WIDTH,
                UnknownEgyptianCombatGoal.WALL_HEIGHT_BLOCKS);
        helper.assertValueEqual(
                cells.stream()
                        .filter(pos -> helper.getLevel().getBlockState(pos).is(Blocks.CHISELED_SANDSTONE))
                        .count(),
                (long) cells.size(),
                "the warning must become thirty-six real chiseled-sandstone blocks");
        Vec3 wallFeet = Vec3.atLowerCornerOf(center).add(0.5D, 0.0D, 0.5D);
        AABB crossingBox = target.getBoundingBox().move(wallFeet.subtract(target.position()));
        helper.assertFalse(
                helper.getLevel().noCollision(target, crossingBox),
                "vanilla collision, not a post-movement correction, must stop traversal");
        helper.assertTrue(
                helper.getLevel().noCollision(target),
                "a player occupying the warning line must be moved to the same safe side before placement");
        wall.restore(helper.getLevel());
        helper.assertTrue(
                cells.stream().allMatch(pos -> helper.getLevel().getBlockState(pos).isAir()),
                "wall expiry must restore every original block state exactly");
        helper.assertTrue(
                helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class,
                        new AABB(center).inflate(8.0D)).isEmpty(),
                "temporary architecture must never create sandstone item drops");
        target.discard();
        boss.discard();
        helper.succeed();
    }

    private static void unknownBossSafeMovement(GameTestHelper helper) {
        // minecraft:empty is 1x1x1 with only ~5 blocks to the next GameTest
        // column. Keep the hunt corridor inside that padding so the dash
        // never samples a neighbouring barrier cage.
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        for (int x = -1; x <= 2; x++) {
            for (int z = -1; z <= 1; z++) {
                helper.getLevel().setBlock(
                        origin.offset(x, -1, z),
                        Blocks.STONE.defaultBlockState(),
                        3);
                helper.getLevel().setBlock(
                        origin.offset(x, 1, z),
                        Blocks.AIR.defaultBlockState(),
                        3);
                helper.getLevel().setBlock(
                        origin.offset(x, 2, z),
                        Blocks.AIR.defaultBlockState(),
                        3);
            }
        }
        var boss = helper.spawn(
                EchoesShowThePast.UNKNOWN.get(),
                new Vec3(2.5D, 1.0D, 2.5D));
        Vec3 blockedAnchor = Vec3.atBottomCenterOf(origin.offset(2, 0, 0));
        for (int z = -1; z <= 1; z++) {
            helper.getLevel().setBlock(origin.offset(1, 0, z), Blocks.CHISELED_SANDSTONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(origin.offset(1, 1, z), Blocks.CHISELED_SANDSTONE.defaultBlockState(), 3);
        }
        helper.assertFalse(
                UnknownBossMovementSafety.isStraightDashSafe(
                        helper.getLevel(), boss, boss.position(), blockedAnchor),
                "Sekhmet must reject the complete dash before locking onto an anchor behind a wall");

        for (int z = -1; z <= 1; z++) {
            helper.getLevel().setBlock(origin.offset(1, 1, z), Blocks.AIR.defaultBlockState(), 3);
            for (int x = 1; x <= 2; x++) {
                helper.getLevel().setBlock(
                        origin.offset(x, 0, z),
                        Blocks.CHISELED_SANDSTONE.defaultBlockState(),
                        3);
            }
        }
        var raisedAnchor = UnknownBossMovementSafety.resolveStraightDashAnchor(
                helper.getLevel(),
                boss,
                boss.position(),
                blockedAnchor.add(0.0D, 1.0D, 0.0D));
        helper.assertTrue(
                raisedAnchor.isPresent()
                        && Math.abs(raisedAnchor.orElseThrow().y - (boss.getY() + 1.0D)) < 0.01D,
                "a one-block rise must remain a valid hunt route and lock its final standing height");

        Vec3 start = boss.position();
        boss.setOnGround(true);
        helper.assertTrue(
                UnknownBossMovementSafety.moveDashStep(
                        helper.getLevel(), boss, new Vec3(1.2D, 0.0D, 0.0D)),
                "the live Sekhmet dash must execute the validated one-block climb");
        helper.assertTrue(
                boss.getX() > start.x + 0.9D && Math.abs(boss.getY() - (start.y + 1.0D)) < 0.02D,
                "the boss must physically finish on top of the full block");

        boss.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
        boss.setDeltaMovement(Vec3.ZERO);
        boss.setOnGround(true);
        for (int z = -1; z <= 1; z++) {
            for (int x = 1; x <= 2; x++) {
                helper.getLevel().setBlock(
                        origin.offset(x, 0, z),
                        Blocks.SANDSTONE_SLAB.defaultBlockState(),
                        3);
            }
        }
        helper.assertTrue(
                UnknownBossMovementSafety.moveDashStep(
                        helper.getLevel(), boss, new Vec3(1.2D, 0.0D, 0.0D)),
                "the live Sekhmet dash must treat a bottom slab as a valid step");
        helper.assertTrue(
                boss.getX() > start.x + 0.9D && Math.abs(boss.getY() - (start.y + 0.5D)) < 0.02D,
                "the boss must physically finish on the slab surface");

        boss.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
        boss.setDeltaMovement(Vec3.ZERO);
        boss.setOnGround(true);
        for (int z = -1; z <= 1; z++) {
            for (int x = 1; x <= 2; x++) {
                helper.getLevel().setBlock(origin.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(origin.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(origin.offset(x, 2, z), Blocks.AIR.defaultBlockState(), 3);
            }
            helper.getLevel().setBlock(
                    origin.offset(1, 0, z),
                    Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST),
                    3);
            helper.getLevel().setBlock(
                    origin.offset(2, 1, z),
                    Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST),
                    3);
        }
        Vec3 stairStep = new Vec3(1.65D, 0.0D, 0.0D);
        var stairAnchor = UnknownBossMovementSafety.resolveStraightDashAnchor(
                helper.getLevel(), boss, boss.position(), boss.position().add(stairStep));
        helper.assertTrue(
                stairAnchor.isPresent(),
                "the planner must accept consecutive stair rises from "
                        + boss.position() + " toward " + boss.position().add(stairStep));
        Vec3 firstMicroStep = stairStep.normalize().scale(0.275D);
        var plannedMicroStep = UnknownBossMovementSafety.resolveDashStep(
                helper.getLevel(), boss, firstMicroStep);
        helper.assertTrue(plannedMicroStep.isPresent(), "the first physical stair substep must validate");
        boss.move(net.minecraft.world.entity.MoverType.SELF, plannedMicroStep.orElseThrow());
        helper.assertTrue(
                boss.getX() > start.x + 0.001D,
                "vanilla collision must advance onto the first stair; position="
                        + boss.position() + ", onGround=" + boss.onGround()
                        + ", stepHeight=" + boss.maxUpStep());
        boss.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
        boss.setDeltaMovement(Vec3.ZERO);
        boss.setOnGround(true);
        boolean climbedStairs = UnknownBossMovementSafety.moveDashStep(
                helper.getLevel(), boss, stairStep);
        helper.assertTrue(
                climbedStairs,
                "one fast hunt impulse must execute consecutive stair rises; stopped at "
                        + boss.position() + " after planning " + stairAnchor.orElseThrow());
        helper.assertTrue(
                boss.getX() > start.x + 1.35D && boss.getY() > start.y + 1.35D,
                "the boss must physically climb the elevated second stair instead of colliding below it");

        boss.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
        boss.setDeltaMovement(Vec3.ZERO);
        boss.setOnGround(true);
        for (int z = -1; z <= 1; z++) {
            for (int x = 1; x <= 2; x++) {
                helper.getLevel().setBlock(origin.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(origin.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(origin.offset(x, 2, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        BlockPos contactCactus = origin.offset(1, 0, 0);
        helper.getLevel().setBlock(contactCactus.below(), Blocks.SAND.defaultBlockState(), 3);
        helper.getLevel().setBlock(contactCactus, Blocks.CACTUS.defaultBlockState(), 3);
        helper.getLevel().setBlock(contactCactus.above(), Blocks.CACTUS.defaultBlockState(), 3);
        helper.assertTrue(
                UnknownBossMovementSafety.destroyContactCacti(helper.getLevel(), boss),
                "ordinary Egyptian navigation must crush an adjacent cactus on body contact");
        helper.assertTrue(
                helper.getLevel().getBlockState(contactCactus).isAir()
                        && helper.getLevel().getBlockState(contactCactus.above()).isAir(),
                "contact cleanup must remove the whole column, including blocks above the boss's feet");
        helper.getLevel().setBlock(contactCactus, Blocks.CACTUS.defaultBlockState(), 3);
        helper.getLevel().setBlock(contactCactus.above(), Blocks.CACTUS.defaultBlockState(), 3);
        helper.assertTrue(
                UnknownBossMovementSafety.moveDashStep(
                        helper.getLevel(), boss, new Vec3(1.2D, 0.0D, 0.0D)),
                "Sekhmet's body sweep must destroy a cactus instead of colliding with it");
        helper.assertTrue(
                helper.getLevel().getBlockState(contactCactus).isAir()
                        && helper.getLevel().getBlockState(contactCactus.above()).isAir()
                        && boss.getX() > start.x + 0.9D,
                "the entire contacted cactus column must disappear before the boss advances");
        helper.assertTrue(
                helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class,
                        new AABB(contactCactus).inflate(2.0D)).isEmpty(),
                "crushed cacti must never litter the arena with item drops");

        BlockPos fire = origin.offset(0, 0, 1);
        BlockPos cactus = origin.offset(0, 0, -1);
        helper.getLevel().setBlock(fire, Blocks.FIRE.defaultBlockState(), 3);
        helper.getLevel().setBlock(cactus, Blocks.CACTUS.defaultBlockState(), 3);
        helper.assertTrue(
                UnknownBossMovementSafety.isDangerousBlock(
                        helper.getLevel(), fire, helper.getLevel().getBlockState(fire))
                        && UnknownBossMovementSafety.isDangerousBlock(
                                helper.getLevel(), cactus, helper.getLevel().getBlockState(cactus)),
                "fire and cactus remain hazards even though the Egyptian boss can crush cactus");
        helper.assertTrue(
                boss.getPathfindingMalus(PathType.FIRE) < 0.0F
                        && boss.getPathfindingMalus(PathType.FIRE_IN_NEIGHBOR) < 0.0F
                        && boss.getPathfindingMalus(PathType.DAMAGING) < 0.0F
                        && boss.getPathfindingMalus(PathType.DAMAGING_IN_NEIGHBOR) >= 0.0F,
                "Unknown must avoid standing in hazards but may approach a cactus closely enough to crush it");
        boss.discard();
        helper.succeed();
    }

    private static void unknownGreekArenaAssets(GameTestHelper helper) {
        StructureTemplate past = helper.getLevel()
                .getStructureManager()
                .get(Identifier.fromNamespaceAndPath(
                        EchoesShowThePast.MOD_ID,
                        "boss/greek_past"))
                .orElseThrow();
        StructureTemplate ruins = helper.getLevel()
                .getStructureManager()
                .get(Identifier.fromNamespaceAndPath(
                        EchoesShowThePast.MOD_ID,
                        "boss/greek_ruins"))
                .orElseThrow();
        StructureTemplate egyptPast = helper.getLevel()
                .getStructureManager()
                .get(Identifier.fromNamespaceAndPath(
                        EchoesShowThePast.MOD_ID,
                        "boss/egyptian_past"))
                .orElseThrow();
        StructureTemplate egyptRuins = helper.getLevel()
                .getStructureManager()
                .get(Identifier.fromNamespaceAndPath(
                        EchoesShowThePast.MOD_ID,
                        "boss/egyptian_ruins"))
                .orElseThrow();
        helper.assertValueEqual(
                past.getSize(),
                TimelessDimensions.ARENA_VOLUME,
                "Greek Past must retain the complete shared Axiom bounds");
        helper.assertValueEqual(
                ruins.getSize(),
                TimelessDimensions.ARENA_VOLUME,
                "Greek ruins must align exactly with Greek Past");
        helper.assertValueEqual(
                egyptPast.getSize(),
                new Vec3i(70, 23, 37),
                "Egyptian Past must keep the common footprint and its authored height");
        helper.assertValueEqual(
                egyptRuins.getSize(),
                new Vec3i(70, 23, 37),
                "Egyptian ruins must align exactly with Egyptian Past without a Y offset");

        List<StructureTemplate.StructureBlockInfo> pastBlocks =
                ((StructureTemplateAccessor) (Object) past)
                        .echoes$getPalettes()
                        .getFirst()
                        .blocks();
        List<StructureTemplate.StructureBlockInfo> ruinsBlocks =
                ((StructureTemplateAccessor) (Object) ruins)
                        .echoes$getPalettes()
                        .getFirst()
                        .blocks();
        List<StructureTemplate.StructureBlockInfo> egyptPastBlocks =
                ((StructureTemplateAccessor) (Object) egyptPast)
                        .echoes$getPalettes()
                        .getFirst()
                        .blocks();
        List<StructureTemplate.StructureBlockInfo> egyptRuinsBlocks =
                ((StructureTemplateAccessor) (Object) egyptRuins)
                        .echoes$getPalettes()
                        .getFirst()
                        .blocks();
        long authoredLegacyPedestals = pastBlocks.stream()
                .filter(block -> block.state().is(EchoesShowThePast.ECHO_PEDESTAL.get()))
                .count()
                + ruinsBlocks.stream()
                .filter(block -> block.state().is(EchoesShowThePast.ECHO_PEDESTAL.get()))
                .count()
                + egyptPastBlocks.stream()
                .filter(block -> block.state().is(EchoesShowThePast.ECHO_PEDESTAL.get()))
                .count()
                + egyptRuinsBlocks.stream()
                .filter(block -> block.state().is(EchoesShowThePast.ECHO_PEDESTAL.get()))
                .count();
        helper.assertValueEqual(
                authoredLegacyPedestals,
                0L,
                "boss blueprints must not reintroduce a stray single-block pedestal");
        helper.assertValueEqual(
                pastBlocks.size(),
                20_666,
                "Greek Past must package every authored solid and water block");
        helper.assertValueEqual(
                ruinsBlocks.size(),
                19_959,
                "Greek ruins must package every authored solid block");
        helper.assertValueEqual(
                egyptPastBlocks.size(),
                19_751,
                "Egyptian Past must package every authored block");
        helper.assertValueEqual(
                egyptRuinsBlocks.size(),
                19_498,
                "Egyptian ruins must package every authored block");
        helper.assertValueEqual(
                pastBlocks.stream().filter(block -> block.nbt() != null).count(),
                226L,
                "Greek Past must preserve its authored block entities");
        helper.assertValueEqual(
                ruinsBlocks.stream().filter(block -> block.nbt() != null).count(),
                47L,
                "Greek ruins must preserve its authored block entities");
        helper.assertValueEqual(
                egyptPastBlocks.stream().filter(block -> block.nbt() != null).count(),
                54L,
                "Egyptian Past must preserve its authored block entities");
        helper.assertValueEqual(
                egyptRuinsBlocks.stream().filter(block -> block.nbt() != null).count(),
                18L,
                "Egyptian ruins must preserve its authored block entities");
        helper.assertValueEqual(
                TimelessDimensions.ARENA_ORIGIN.getY() + 6,
                TimelessDimensions.FLOOR_Y,
                "Axiom surface Y=-8 must align with the boss arena floor");
        helper.assertValueEqual(
                TimelessDimensions.HUB_SPAWN,
                new BlockPos(-8, 68, 1),
                "the hub spawn block must remain at the authored fight entrance");
        helper.assertValueEqual(
                TimelessDimensions.BOSS_ENTRANCE_SPAWN,
                new Vec3(-7.5D, 68.0D, 1.0D),
                "fight entry must use the exact requested coordinates");
        helper.assertValueEqual(
                BlockPos.containing(TimelessDimensions.BOSS_ENTRANCE_SPAWN),
                TimelessDimensions.HUB_SPAWN,
                "block-based fallback and exact entrance position must agree");
        helper.assertValueEqual(
                TimelessDimensions.HUB_SPAWN.offset(0, -1, -3),
                TimelessDimensions.EXIT_PORTAL,
                "the exit pad must sit on the south hub rim, not the plaza centre");
        helper.assertTrue(
                TimelessDimensions.EXIT_PORTAL.getZ() < TimelessDimensions.HUB_SPAWN.getZ(),
                "the exit pad must stay south of hub spawn so cycle-end cannot eject the fighter");
        UnknownFightManager.ArenaBounds bounds =
                UnknownFightManager.arenaBounds(helper.getLevel());
        helper.assertValueEqual(
                bounds.origin(),
                TimelessDimensions.MEDIEVAL_ARENA_ORIGIN,
                "the shared envelope must include the complete Medieval subterranean selection");
        helper.assertTrue(
                bounds.contains(TimelessDimensions.ARENA_ORIGIN),
                "the Greek/Egyptian origin and central altar must remain inside the expanded envelope");
        helper.assertValueEqual(
                new Vec3i(bounds.size().getX(), 0, bounds.size().getZ()),
                new Vec3i(70, 0, 37),
                "the shared envelope must match the authored common footprint exactly");
        helper.assertFalse(
                UnknownFightManager.isOutsideCanonicalArenaFootprint(-40, -18),
                "the canonical north-west arena corner must keep its void floor");
        helper.assertFalse(
                UnknownFightManager.isOutsideCanonicalArenaFootprint(29, 18),
                "the canonical south-east arena corner must keep its void floor");
        helper.assertTrue(
                UnknownFightManager.isOutsideCanonicalArenaFootprint(30, 18),
                "the old five-block east strip must be removed");
        helper.assertTrue(
                UnknownFightManager.isOutsideCanonicalArenaFootprint(-40, -19),
                "the old seven-block north strip must be removed");
        helper.assertTrue(
                bounds.size().getY() >= TimelessDimensions.ARENA_VOLUME.getY(),
                "the shared envelope height must grow to the tallest loaded arena");
        Vec3 waveCenter = UnknownFightManager.pedestalCenterFor(
                TimelessDimensions.BOSS_PEDESTAL_ORIGIN);
        Vec3 waveExtents = ArenaReconstructionWave.volumeHalfExtents(
                bounds.origin(),
                bounds.size(),
                waveCenter);
        helper.assertValueEqual(
                new Vec3(waveExtents.x, 0.0D, waveExtents.z),
                new Vec3(66.0D, 0.0D, 19.0D),
                "the plaza-entry altar crest must enclose the full common arena without dead space");
        helper.assertTrue(
                Math.abs(waveExtents.x - Math.rint(waveExtents.x)) < 1.0E-9D,
                "a 2x2 altar centre must preserve the shader lattice on block boundaries");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.transitionTicks(21_089) > 120,
                "the full Greek rebuild must not be truncated to the old six-second clock");
        helper.assertValueEqual(
                PhilosophersStoneVisualTiming.transitionTicks(21_089, 3),
                48,
                "the Greek arena crest must run 1.5 times faster than its previous double-speed schedule");
        helper.assertValueEqual(
                PhilosophersStoneVisualTiming.mutationsPerTick(3),
                768,
                "triple-speed arena visuals need matching mutation throughput to stay synchronized");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.compareMutationOrder(0.1F, 0.9F, false) < 0,
                "an outward crest must consume pedestal-adjacent blocks first");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.compareMutationOrder(0.1F, 0.9F, true) > 0,
                "a returning crest must consume the arena perimeter first instead of stalling its queue");
        int heldClock = 40;
        for (int batch = 0; batch < 3; batch++) {
            heldClock = PhilosophersStoneVisualTiming.advanceServerClock(
                    heldClock,
                    139,
                    true);
        }
        helper.assertValueEqual(
                heldClock,
                40,
                "the shared crest clock must wait while a dense ready band drains across several batches");
        helper.assertValueEqual(
                PhilosophersStoneVisualTiming.advanceServerClock(
                        heldClock,
                        139,
                        false),
                41,
                "the shared crest clock may advance as soon as its ready mutation band is empty");
        helper.succeed();
    }

    private static void unknownPedestalApproach(GameTestHelper helper) {
        BlockPos origin = new BlockPos(-36, 64, 0);
        helper.assertValueEqual(
                TimelessDimensions.BOSS_PEDESTAL_ORIGIN,
                origin,
                "the authored 0,0,0 pedestal cell must remain fixed at the plaza entrance");
        helper.assertValueEqual(
                TimelessDimensions.PEDESTAL_GREEK,
                origin,
                "all eras must channel through the one shared altar");
        helper.assertValueEqual(TimelessDimensions.PEDESTAL_EGYPTIAN, origin, "Egypt must share the altar");
        helper.assertValueEqual(TimelessDimensions.PEDESTAL_MEDIEVAL, origin, "Medieval must share the altar");
        helper.assertValueEqual(
                UnknownFightManager.pedestalFootprint(),
                java.util.Set.of(
                        new BlockPos(-36, 64, 0),
                        new BlockPos(-37, 64, 0),
                        new BlockPos(-37, 64, 1),
                        new BlockPos(-36, 64, 1)),
                "the single altar must occupy exactly the four authored 2x2 cells");
        helper.assertValueEqual(
                UnknownFightManager.pedestalApproachFor(TimelessDimensions.PEDESTAL_GREEK),
                TimelessDimensions.PEDESTAL_GREEK.east(),
                "the shared approach must stay outside the altar on the plaza side");
        helper.assertValueEqual(
                UnknownFightManager.pedestalApproachFor(TimelessDimensions.PEDESTAL_EGYPTIAN),
                TimelessDimensions.PEDESTAL_EGYPTIAN.east(),
                "Egyptian phase must approach the same altar edge");
        helper.assertValueEqual(
                UnknownFightManager.pedestalApproachFor(TimelessDimensions.PEDESTAL_MEDIEVAL),
                TimelessDimensions.PEDESTAL_MEDIEVAL.east(),
                "Medieval phase must approach the same altar edge");

        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.getLevel().setBlock(
                        helper.absolutePos(new BlockPos(x, 1, z)),
                        Blocks.STONE.defaultBlockState(),
                        3);
            }
        }
        BlockPos pedestal = helper.absolutePos(new BlockPos(8, 2, 2));
        BlockPos approach = helper.absolutePos(new BlockPos(9, 2, 2));
        helper.getLevel().setBlock(
                pedestal,
                EchoesShowThePast.ECHO_PEDESTAL.get().defaultBlockState(),
                3);
        var boss = helper.spawn(
                EchoesShowThePast.UNKNOWN.get(),
                new Vec3(2.5D, 2.0D, 2.5D));
        boss.setDummy(true);
        Vec3 start = boss.position();
        boss.setPos(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D);
        helper.assertTrue(
                UnknownFightManager.isWithinPedestalChannelRange(boss, pedestal),
                "reaching a valid adjacent tile must always start the pedestal channel");
        boss.setPos(start);
        helper.runAfterDelay(2, () -> helper.assertTrue(
                UnknownFightManager.repathToPedestal(boss, pedestal, 0.95D),
                "Unknown must select a reachable pedestal side from its live position"));
        helper.runAfterDelay(18, () -> {
            helper.assertTrue(
                    boss.position().distanceToSqr(start) > 1.0D,
                    "Unknown must visibly walk toward the pedestal instead of remaining idle");
            helper.succeed();
        });
    }

    private static void unknownDamageGates(GameTestHelper helper) {
        float[] expectedFloors = {510.0F, 420.0F, 330.0F, 240.0F, 150.0F, 60.0F};
        for (int threshold = 0; threshold < expectedFloors.length; threshold++) {
            helper.assertValueEqual(
                    UnknownFightManager.healthFloorForThreshold(threshold),
                    expectedFloors[threshold],
                    "Unknown threshold " + threshold + " must use its authored HP boundary");
        }
        helper.assertValueEqual(
                UnknownFightManager.clampDamageToCurrentGate(600.0F, 200.0F, 0, false),
                90.0F,
                "one oversized hit must stop at the first boundary");
        helper.assertValueEqual(
                UnknownFightManager.clampDamageToCurrentGate(420.0F, 200.0F, 2, true),
                45.0F,
                "an intermediate void punish must stop halfway through the next segment");
        helper.assertValueEqual(
                UnknownFightManager.clampDamageToCurrentGate(60.0F, 200.0F, 6, true),
                200.0F,
                "the sixth threshold must not introduce a hidden seventh damage gate");

        UUID bossId = UUID.fromString("7a437b9e-cf53-4d33-a85c-d7cbb9ab2721");
        UUID ownerId = UUID.fromString("6f28a9b8-90f0-45e7-8df6-cfe216a1411b");
        UnknownEncounterSavedData state = new UnknownEncounterSavedData();
        state.begin(bossId, ownerId, 2);
        state.setEra(UnknownFightManager.Era.GREEK);
        state.setState(UnknownFightManager.Phase.PAST, UnknownFightManager.Action.COMBAT);
        state.setThresholdIndex(1);
        UUID outerNpc = UUID.fromString("772f5096-2643-4d75-9120-cf6b71505a54");
        UUID innerNpc = UUID.fromString("594a057a-4549-4b66-8db0-74c49c5d3687");
        state.setMedievalRooftopStarted(true);
        state.setMedievalInnerActive(true);
        state.setMedievalVanguardIds(List.of(outerNpc, innerNpc));
        var encoded = UnknownEncounterSavedData.CODEC
                .encodeStart(JsonOps.INSTANCE, state)
                .getOrThrow();
        UnknownEncounterSavedData decoded = UnknownEncounterSavedData.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();
        helper.assertTrue(decoded.controls(bossId), "boss ownership must survive a save round trip");
        helper.assertTrue(decoded.owns(ownerId), "player ownership must survive a save round trip");
        helper.assertValueEqual(decoded.era(), UnknownFightManager.Era.GREEK, "era save round trip");
        helper.assertValueEqual(decoded.phase(), UnknownFightManager.Phase.PAST, "phase save round trip");
        helper.assertValueEqual(decoded.action(), UnknownFightManager.Action.COMBAT, "action save round trip");
        helper.assertValueEqual(decoded.thresholdIndex(), 1, "threshold save round trip");
        helper.assertValueEqual(decoded.reviewEraCount(), 2, "review era count save round trip");
        helper.assertTrue(
                decoded.medievalRooftopStarted(),
                "legacy medieval rooftop flag must survive a save round trip");
        helper.assertTrue(
                decoded.medievalInnerActive(),
                "medieval inner-group activation must survive a save round trip");
        helper.assertValueEqual(
                decoded.medievalVanguardIds(),
                List.of(outerNpc, innerNpc),
                "temporary EasyNPC identities must survive until explicit cleanup");
        helper.succeed();
    }

    private static void unknownHostileDamageDelivery(GameTestHelper helper) {
        var level = helper.getLevel();
        var boss = helper.spawn(
                EchoesShowThePast.UNKNOWN.get(),
                new Vec3(2.5D, 2.0D, 2.5D));
        boss.setNoAi(true);
        var vanguard = helper.spawn(
                EntityType.ZOMBIE,
                new Vec3(3.5D, 2.0D, 2.5D));
        var damageProbe = helper.spawn(
                EntityType.VILLAGER,
                new Vec3(4.5D, 2.0D, 2.5D));
        damageProbe.setNoAi(true);
        UUID ownerId = UUID.fromString("8c6da56f-d508-433f-a5f0-f6ebf255c182");
        UUID unrelatedPlayer = UUID.fromString("812842dd-e214-4e2f-a836-da7e71291373");
        helper.assertTrue(
                UnknownFightManager.isLegalVanguardDamageTarget(
                        ownerId, ownerId),
                "the vanguard damage gate must admit the encounter owner");
        helper.assertFalse(
                UnknownFightManager.isLegalVanguardDamageTarget(
                        unrelatedPlayer, ownerId),
                "the vanguard damage gate must reject every non-owner player");

        // GameTest's embedded ServerPlayer deliberately stays in the
        // pre-client-loaded invulnerability state, so a normal living target
        // verifies the same server-authoritative mobAttack pipeline without
        // weakening production player protections.
        damageProbe.setHealth(damageProbe.getMaxHealth());
        damageProbe.invulnerableTime = 0;
        float beforeBossHit = damageProbe.getHealth();
        boolean bossHit = damageProbe.hurtServer(
                level,
                level.damageSources().mobAttack(boss),
                6.0F);
        helper.assertTrue(
                bossHit && damageProbe.getHealth() < beforeBossHit,
                "a server-authoritative Unknown mob attack must deliver living damage");

        damageProbe.setHealth(damageProbe.getMaxHealth());
        damageProbe.invulnerableTime = 0;
        float beforeVanguardHit = damageProbe.getHealth();
        boolean vanguardHit = damageProbe.hurtServer(
                level,
                level.damageSources().mobAttack(vanguard),
                6.0F);
        helper.assertTrue(
                vanguardHit && damageProbe.getHealth() < beforeVanguardHit,
                "the vanilla mobAttack route used by EasyNPC must deliver living damage");
        helper.succeed();
    }

    private static void unknownPeacefulPersistence(GameTestHelper helper) {
        var boss = helper.spawn(
                EchoesShowThePast.UNKNOWN.get(),
                new Vec3(2.5D, 2.0D, 2.5D));
        boss.setDummy(false);
        boss.setPersistenceRequired();

        var server = helper.getLevel().getServer();
        Difficulty previous = server.getWorldData().getDifficulty();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        try {
            helper.assertValueEqual(
                    helper.getLevel().getDifficulty(),
                    Difficulty.PEACEFUL,
                    "the Unknown probe must run under Peaceful despawn rules");
            boss.checkDespawn();
            helper.assertFalse(
                    boss.isRemoved(),
                    "Unknown must survive Peaceful checkDespawn or enter immediately ejects the fighter");
        } finally {
            server.setDifficulty(previous, true);
        }
        helper.succeed();
    }

    private static void captureBasic(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(origin.east(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.west(), Blocks.CHEST.defaultBlockState(), 3);

        EchoSnapshot snapshot = EchoCapture.capture(helper.getLevel(), origin, 2, 64).orElseThrow();
        boolean containsStone = snapshot.blocks().stream().anyMatch(block ->
                snapshot.worldPosition(block).equals(origin.east()) && snapshot.state(block).is(Blocks.STONE));
        boolean containsChest = snapshot.blocks().stream().anyMatch(block -> snapshot.worldPosition(block).equals(origin.west()));
        helper.assertTrue(containsStone, "Stone must survive capture");
        helper.assertTrue(
                containsChest,
                "the block state of a block entity must survive even though its inventory and NBT are excluded");
        helper.succeed();
    }

    private static void captureLimit(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(origin.east(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.west(), Blocks.DIRT.defaultBlockState(), 3);
        Optional<EchoSnapshot> snapshot = EchoCapture.capture(helper.getLevel(), origin, 2, 1);
        helper.assertTrue(snapshot.isEmpty(), "Capture must abort instead of exceeding its block limit");
        helper.succeed();
    }

    private static void captureEntities(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        Rotations raisedRightArm = new Rotations(-92.0F, 8.0F, 14.0F);
        ArmorStand armorStand = helper.spawn(EntityType.ARMOR_STAND, new Vec3(3.5, 2.0, 2.5));
        // The entity section index is updated after the tick that accepts a
        // fresh entity. Waiting two ticks makes the spatial capture assertion
        // deterministic on both clean and reused GameTest worlds.
        helper.runAfterDelay(5, () -> {
        armorStand.setRightArmPose(raisedRightArm);
        armorStand.setPose(Pose.CROUCHING);
        armorStand.yBodyRot = armorStand.yBodyRotO = 31.0F;
        armorStand.yHeadRot = armorStand.yHeadRotO = 47.0F;
        armorStand.walkAnimation.setSpeed(0.42F);
        armorStand.walkAnimation.update(0.42F, 1.0F, 1.0F);
        armorStand.oAttackAnim = armorStand.attackAnim = 0.65F;
        armorStand.swinging = true;
        armorStand.swingTime = 3;
        armorStand.tickCount = 37;

        EchoSnapshot snapshot = EchoCapture.capture(helper.getLevel(), origin, 3, 64).orElseThrow();
        helper.assertValueEqual(snapshot.entities().size(), 1, "one armor stand must be captured");
        SnapshotEntity captured = snapshot.entities().getFirst();
        helper.assertValueEqual(captured.pose(), Pose.CROUCHING, "entity pose must survive capture");
        helper.assertValueEqual(captured.ageInTicks(), 37, "animation age must be frozen");
        helper.assertValueEqual(captured.bodyYRot(), 31.0F, "body rotation must survive capture");
        helper.assertValueEqual(captured.headYRot(), 47.0F, "head rotation must survive capture");
        helper.assertValueEqual(
                captured.animation().walkPosition(),
                armorStand.walkAnimation.position(),
                "limb animation phase must survive capture");
        helper.assertValueEqual(captured.animation().walkSpeed(), 0.42F, "limb animation speed must survive capture");
        helper.assertValueEqual(captured.animation().attack(), 0.65F, "attack arm pose must survive capture");
        helper.assertTrue(captured.animation().swinging(), "swinging arm state must survive capture");
        helper.assertValueEqual(captured.animation().swingTime(), 3, "swinging arm phase must survive capture");
        ArmorStand.ArmorStandPose capturedPose = captured.data()
                .read("Pose", ArmorStand.ArmorStandPose.CODEC)
                .orElseThrow();
        helper.assertValueEqual(
                capturedPose.rightArm(),
                raisedRightArm,
                "armor-stand bone rotations must survive in entity NBT");

        var encoded = EchoSnapshot.CODEC.encodeStart(JsonOps.INSTANCE, snapshot).getOrThrow();
        EchoSnapshot decoded = EchoSnapshot.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        helper.assertValueEqual(decoded.entities().size(), 1, "entity list codec round trip");
        SnapshotEntity decodedEntity = decoded.entities().getFirst();
        helper.assertValueEqual(decodedEntity.offset(), captured.offset(), "entity position codec round trip");
        helper.assertValueEqual(decodedEntity.pose(), captured.pose(), "entity pose codec round trip");
        helper.assertValueEqual(
                decodedEntity.animation().walkPosition(),
                captured.animation().walkPosition(),
                "entity limb phase codec round trip");
        helper.assertValueEqual(
                decodedEntity.animation().attack(),
                captured.animation().attack(),
                "entity arm animation codec round trip");
        helper.assertValueEqual(
                decodedEntity.data()
                        .read("Pose", ArmorStand.ArmorStandPose.CODEC)
                        .orElseThrow()
                        .rightArm(),
                raisedRightArm,
                "entity bone data codec round trip");
        helper.succeed();
        });
    }

    private static void captureSkeletonAim(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        Skeleton skeleton = helper.spawnWithNoFreeWill(EntityType.SKELETON, new Vec3(3.5, 2.0, 2.5));
        helper.runAfterDelay(5, () -> {
        skeleton.snapTo(skeleton.getX(), skeleton.getY(), skeleton.getZ(), 38.0F, -12.0F);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        skeleton.setAggressive(true);

        EchoSnapshot snapshot = EchoCapture.capture(helper.getLevel(), origin, 3, 64).orElseThrow();
        helper.assertValueEqual(snapshot.entities().size(), 1, "one aiming skeleton must be captured");
        SnapshotEntity captured = snapshot.entities().getFirst();
        helper.assertTrue(
                captured.animation().aggressive(),
                "the transient aggressive flag that drives the skeleton bow pose must survive capture");

        var encoded = EchoSnapshot.CODEC.encodeStart(JsonOps.INSTANCE, snapshot).getOrThrow();
        EchoSnapshot decoded = EchoSnapshot.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        helper.assertTrue(
                decoded.entities().getFirst().animation().aggressive(),
                "the skeleton bow pose flag must survive the snapshot codec");
        helper.succeed();
        });
    }

    private static void verticalBounds(GameTestHelper helper) {
        BlockPos bottom = new BlockPos(0, helper.getLevel().getMinY(), 0);
        Optional<EchoSnapshot> snapshot = EchoCapture.capture(helper.getLevel(), bottom, 16, 16_384);
        helper.assertTrue(snapshot.isPresent(), "Capture at the build floor must not read outside the world");
        helper.succeed();
    }

    private static void sealedMemory(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        EchoSnapshot snapshot = EchoRuinTemplate.createSnapshot(helper.getLevel().dimension(), origin);
        helper.assertTrue(snapshot.sealed(), "Ruin memories must be sealed");
        helper.assertFalse(snapshot.canErase(), "Sealed memories must not be erasable");
        helper.assertTrue(!snapshot.blocks().isEmpty(), "Ruin memory must contain its intact template");
        helper.assertTrue(snapshot.blocks().stream().anyMatch(block ->
                        snapshot.worldPosition(block).equals(origin)
                                && snapshot.state(block).is(EchoesShowThePast.ECHO_PEDESTAL.get())),
                "Sealed memory must include the intact pedestal");
        helper.assertFalse(snapshot.blocks().stream().anyMatch(block -> {
            BlockPos position = snapshot.worldPosition(block);
            return position.equals(origin.offset(0, 0, -4))
                    || position.equals(origin.offset(0, 1, -4));
        }), "The intact ruin memory must preserve air inside the lost passage");

        SnapshotBlock packed = SnapshotBlock.of(-16, 16, 7, 0);
        helper.assertValueEqual(packed.offset(), new BlockPos(-16, 16, 7), "compact relative position round trip");
        helper.succeed();
    }

    private static void projectionGuards(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        EchoSnapshot snapshot = EchoRuinTemplate.createSnapshot(helper.getLevel().dimension(), origin);
        helper.assertValueEqual(
                EchoProjectionAccess.validate(helper.getLevel().dimension(), origin.getCenter(), snapshot, 32.0),
                EchoProjectionAccess.Result.ALLOWED,
                "projection in range");
        helper.assertValueEqual(
                EchoProjectionAccess.validate(Level.NETHER, origin.getCenter(), snapshot, 32.0),
                EchoProjectionAccess.Result.WRONG_DIMENSION,
                "projection dimension guard");
        helper.assertValueEqual(
                EchoProjectionAccess.validate(helper.getLevel().dimension(), Vec3.atCenterOf(origin.offset(33, 0, 0)), snapshot, 32.0),
                EchoProjectionAccess.Result.TOO_FAR,
                "projection range guard");
        helper.assertValueEqual(
                EchoProjectionBudget.ambientRadius(12),
                12,
                "a huge authored memory must retain the configured local pulse radius");
        helper.assertValueEqual(
                EchoProjectionBudget.ambientRadius(64),
                16,
                "no authored memory may silently exceed the Past Echo visual safety radius");
        EchoSnapshot enormousAuthoredMemory = EchoSnapshot.templateReference(
                helper.getLevel().dimension(),
                origin,
                Identifier.fromNamespaceAndPath(
                        EchoesShowThePast.MOD_ID,
                        "sites/enormous_projection_guard"),
                new BlockPos(-160, -24, -160),
                new BlockPos(160, 48, 160));
        helper.assertValueEqual(
                EchoProjectionAccess.validate(
                        helper.getLevel().dimension(),
                        origin.offset(150, 0, 0).getCenter(),
                        enormousAuthoredMemory,
                        32.0),
                EchoProjectionAccess.Result.ALLOWED,
                "a giant authored memory must be usable near any part of its bounds, not only near its origin");
        EchoSnapshot personalMemory = new EchoSnapshot(
                EchoSnapshot.CURRENT_VERSION,
                helper.getLevel().dimension(),
                origin,
                4,
                false,
                List.of(),
                List.of(),
                List.of());
        helper.assertTrue(
                personalMemory.containsWorldPosition(origin.offset(4, -4, 4))
                        && !personalMemory.containsWorldPosition(origin.offset(5, 0, 0)),
                "a personal memory must distinguish its captured cube from nearby unsaved space");
        helper.assertTrue(
                enormousAuthoredMemory.containsWorldPosition(origin.offset(160, 48, -160))
                        && !enormousAuthoredMemory.containsWorldPosition(origin.offset(161, 0, 0)),
                "an authored memory must only control its exact aligned bounds");
        helper.succeed();
    }

    private static void pulseTiming(GameTestHelper helper) {
        EchoPulseTiming small = EchoPulseTiming.forRadius(12.0);
        EchoPulseTiming large = EchoPulseTiming.forRadius(120.0);
        helper.assertTrue(
                Math.abs(Math.abs(small.outboundRadius(
                                        small.outboundStartSeconds() + 1.0)
                                - small.outboundRadius(
                                        small.outboundStartSeconds()))
                                - EchoPulseTiming.BLOCKS_PER_SECOND)
                        < 1.0E-9,
                "the outbound Past Echo crest must advance at a constant block speed");
        helper.assertTrue(
                Math.abs(Math.abs(large.outboundRadius(
                                        large.outboundStartSeconds() + 1.0)
                                - large.outboundRadius(
                                        large.outboundStartSeconds()))
                                - EchoPulseTiming.BLOCKS_PER_SECOND)
                        < 1.0E-9,
                "the same crest speed must apply to an authored island");
        helper.assertTrue(
                large.outboundEndSeconds() > small.outboundEndSeconds()
                        && large.effectEndSeconds() > small.effectEndSeconds(),
                "a larger memory must keep the complete Past Echo pulse alive longer");
        helper.assertValueEqual(
                large.returnRadius(large.returnStartSeconds() + 1.0),
                large.radius() - EchoPulseTiming.BLOCKS_PER_SECOND,
                "the returning crest must keep that same constant speed");
        EchoPulseTiming hold = EchoPulseTiming.forRadius(12.0);
        helper.assertTrue(
                Math.abs(hold.crestEnvelope(hold.outboundEndSeconds()) - 1.0F) < 1.0E-4F,
                "the outbound crest must still be fully lit when it first reaches the perimeter");
        helper.assertTrue(
                hold.crestEnvelope(hold.returnStartSeconds() - 1.0E-4) < 0.02F,
                "the perimeter hold must dissolve the outbound crest before it reverses");
        helper.assertTrue(
                hold.crestEnvelope(hold.returnStartSeconds()) < 0.02F
                        && hold.crestEnvelope(
                                hold.returnStartSeconds()
                                        + EchoPulseTiming.RETURN_ATTACK_SECONDS)
                                > 0.98F,
                "the inward crest must regain full intensity almost immediately");
        helper.succeed();
    }

    private static void codecRoundTrip(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        EchoSnapshot original = EchoRuinTemplate.createSnapshot(helper.getLevel().dimension(), origin);
        var encoded = EchoSnapshot.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        EchoSnapshot decoded = EchoSnapshot.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        helper.assertValueEqual(decoded, original, "echo snapshot codec round trip");

        EchoSnapshot resolvedAuthoredMemory = EchoSnapshot.templateReference(
                        helper.getLevel().dimension(),
                        origin,
                        Identifier.fromNamespaceAndPath(
                                EchoesShowThePast.MOD_ID,
                                "sites/codec_radius_regression"),
                        new BlockPos(-17, -2, -4),
                        new BlockPos(17, 8, 4))
                .resolved(List.of(), List.of(), List.of());
        var encodedAuthored = EchoSnapshot.CODEC
                .encodeStart(JsonOps.INSTANCE, resolvedAuthoredMemory)
                .getOrThrow();
        helper.assertValueEqual(
                EchoSnapshot.CODEC.parse(JsonOps.INSTANCE, encodedAuthored).getOrThrow(),
                resolvedAuthoredMemory,
                "resolved authored memories wider than player captures must survive network codec validation");

        List<SnapshotBlock> denseBlocks =
                new ArrayList<>(65_536);
        for (int index = 0; index < 65_536; index++) {
            denseBlocks.add(SnapshotBlock.of(
                    (index & 63) - 32,
                    ((index >> 6) & 31) - 16,
                    ((index >> 11) & 31) - 16,
                    0));
        }
        EchoSnapshot denseMemory = new EchoSnapshot(
                EchoSnapshot.CURRENT_VERSION,
                helper.getLevel().dimension(),
                origin,
                32,
                true,
                List.of(Blocks.STONE.defaultBlockState()),
                denseBlocks,
                List.of(),
                Optional.empty(),
                Optional.of(new BlockPos(-32, -16, -16)),
                Optional.of(new BlockPos(31, 15, 15)),
                Optional.empty());
        RegistryFriendlyByteBuf networkBuffer =
                new RegistryFriendlyByteBuf(
                        Unpooled.buffer(),
                        helper.getLevel().registryAccess(),
                        ConnectionType.NEOFORGE);
        try {
            ItemStack stack =
                    EchoesShowThePast.PAST_ECHO.get()
                            .getDefaultInstance();
            PastEchoMemory.setSnapshot(stack, denseMemory);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(
                    networkBuffer,
                    stack);
            helper.assertTrue(
                    networkBuffer.readableBytes() < 700_000,
                    "a 65536-block Past Echo item must remain a bounded compact packet");
            ItemStack decodedStack =
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(
                            networkBuffer);
            helper.assertValueEqual(
                    PastEchoMemory.getSnapshot(decodedStack),
                    denseMemory,
                    "dense Past Echo item must survive inventory synchronization");

            networkBuffer.clear();
            EchoStatePayload.STREAM_CODEC.encode(
                    networkBuffer,
                    EchoStatePayload.on(denseMemory));
            EchoStatePayload decodedPayload =
                    EchoStatePayload.STREAM_CODEC.decode(
                            networkBuffer);
            helper.assertValueEqual(
                    decodedPayload.snapshot().orElseThrow(),
                    denseMemory,
                    "dense Past Echo must survive projection payload synchronization");
        } finally {
            networkBuffer.release();
        }
        helper.succeed();
    }

    private static void waveVolume(GameTestHelper helper) {
        BlockPos snapshotOrigin = helper.absolutePos(new BlockPos(32, 2, 2));
        EchoSnapshot snapshot = EchoRuinTemplate.createSnapshot(helper.getLevel().dimension(), snapshotOrigin);
        Vec3 playerOrigin = helper.absolutePos(new BlockPos(2, 2, 2)).getCenter();
        EchoWaveVolume volume = EchoWaveVolume.aroundPlayer(snapshot, playerOrigin);

        helper.assertValueEqual(volume.center(), playerOrigin, "wave must remain centered on the activating player");
        for (var direction : net.minecraft.core.Direction.values()) {
            Vec3 inside = playerOrigin.add(
                    direction.getStepX() * (volume.radius() - 0.01),
                    direction.getStepY() * (volume.radius() - 0.01),
                    direction.getStepZ() * (volume.radius() - 0.01));
            helper.assertTrue(volume.contains(inside), "wave must cover " + direction + " from the player");
        }
        helper.assertTrue(
                volume.minBlock().getX() < playerOrigin.x - snapshot.radius(),
                "wave bounds must extend behind the player, away from the recorded structure");
        helper.assertTrue(
                volume.boundingCellCount() < 50_000L,
                "a normal Past Echo must not prepare a radius-31 client volume");
        BlockPos authoredOrigin = BlockPos.containing(playerOrigin);
        EchoSnapshot corridorMemory = new EchoSnapshot(
                EchoSnapshot.CURRENT_VERSION,
                helper.getLevel().dimension(),
                authoredOrigin,
                12,
                true,
                List.of(),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.of(new BlockPos(-2, -2, -2)),
                Optional.of(new BlockPos(2, 2, 20)),
                Optional.empty());
        EchoWaveVolume corridorVolume =
                EchoWaveVolume.aroundPlayer(
                        corridorMemory,
                        playerOrigin);
        helper.assertTrue(
                corridorVolume.contains(
                        authoredOrigin.offset(0, 0, 20)
                                .getCenter()),
                "authored corridors must remain in the acoustic domain beyond the ambient radius");
        helper.assertFalse(
                corridorVolume.contains(
                        authoredOrigin.offset(10, 0, 20)
                                .getCenter()),
                "the authored extension must follow exact memory bounds instead of another oversized sphere");
        helper.succeed();
    }

    private static void echoArrivalField(GameTestHelper helper) {
        int sizeX = 5;
        int sizeY = 3;
        int sizeZ = 3;
        boolean[] blocked = new boolean[sizeX * sizeY * sizeZ];
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                blocked[2 + sizeX * (y + sizeY * z)] = true;
            }
        }
        blocked[2 + sizeX * (1 + sizeY * 2)] = false;
        EchoArrivalSolver.Field throughOpening = EchoArrivalSolver.solve(
                sizeX,
                sizeY,
                sizeZ,
                0,
                1,
                1,
                16.0,
                index -> blocked[index]
                        ? Double.POSITIVE_INFINITY
                        : 1.0);
        float routedDistance = throughOpening.distance(4, 1, 1);
        helper.assertTrue(
                Float.isFinite(routedDistance)
                        && routedDistance > 4.0F,
                "the pressure field must reach the far room through its opening, not through the wall");

        blocked[2 + sizeX * (1 + sizeY * 2)] = true;
        EchoArrivalSolver.Field sealed = EchoArrivalSolver.solve(
                sizeX,
                sizeY,
                sizeZ,
                0,
                1,
                1,
                16.0,
                index -> blocked[index]
                        ? Double.POSITIVE_INFINITY
                        : 1.0);
        helper.assertFalse(
                Float.isFinite(sealed.distance(4, 1, 1)),
                "a sealed wall must prevent the Past Echo field from appearing in the far room");
        helper.assertTrue(
                Math.abs(EchoSurfaceCrestPath.distanceAtPoint(
                                8.0,
                                3.0,
                                3.25)
                                - 8.25)
                        < 1.0E-9,
                "a routed surface must retain sub-block distance variation so the crest remains a line");
        helper.assertTrue(
                EchoSurfaceCrestPath.distanceAtPoint(
                                8.0,
                                3.0,
                                2.75)
                        < EchoSurfaceCrestPath.distanceAtPoint(
                                8.0,
                                3.0,
                                3.25),
                "the geometric crest must cross a face continuously instead of lighting the complete block");
        Vec3 routedCenter = new Vec3(5.0, 1.0, 5.0);
        Vec3 routedGradient = new Vec3(0.0, 0.0, 1.0);
        helper.assertTrue(
                Math.abs(EchoSurfaceCrestPath.distanceAtPoint(
                                8.0,
                                routedCenter,
                                routedCenter.add(0.0, 0.0, 0.5),
                                routedGradient)
                                - 8.5)
                        < 1.0E-9,
                "a crest leaving an opening must follow the routed pressure gradient");
        helper.assertTrue(
                Math.abs(EchoSurfaceCrestPath.distanceAtPoint(
                                8.0,
                                routedCenter,
                                routedCenter.add(0.5, 0.0, 0.0),
                                routedGradient)
                                - 8.0)
                        < 1.0E-9,
                "a routed crest must not retain a false spherical direction after turning a corner");
        Vec3 radialGradient =
                routedCenter.normalize();
        Vec3 curvedPoint =
                routedCenter.add(
                        0.36,
                        -0.21,
                        0.44);
        helper.assertTrue(
                Math.abs(
                                EchoSurfaceCrestPath.distanceAtPoint(
                                                8.0,
                                                routedCenter,
                                                curvedPoint,
                                                radialGradient,
                                                Vec3.ZERO)
                                        - EchoSurfaceCrestPath.distanceAtPoint(
                                                8.0,
                                                routedCenter.length(),
                                                curvedPoint.length()))
                        < 1.0E-9,
                "an unobstructed routed crest must preserve the exact 0.1 sub-block curvature");

        List<BlockPos> historicalAirVolume = new ArrayList<>(1_000);
        Map<Long, Double> reachedSkin = new HashMap<>();
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                for (int z = 0; z < 10; z++) {
                    BlockPos position = new BlockPos(x, y, z);
                    historicalAirVolume.add(position);
                    if (y == 9) {
                        reachedSkin.put(position.asLong(), 10.0);
                    }
                }
            }
        }
        Map<Long, Double> penetrated = EchoOccluderPropagation.propagate(
                historicalAirVolume,
                reachedSkin,
                0.46);
        helper.assertValueEqual(
                penetrated.size(),
                1_000,
                "a reached skin must give every connected historical-air layer a return time");
        helper.assertTrue(
                penetrated.get(new BlockPos(4, 9, 4).asLong())
                                > penetrated.get(new BlockPos(4, 5, 4).asLong())
                        && penetrated.get(new BlockPos(4, 5, 4).asLong())
                                > penetrated.get(new BlockPos(4, 0, 4).asLong()),
                "a solid occupying remembered air must collapse progressively from its reached surface");

        EchoSurfaceCrestPath.FaceDistances leftDistances =
                new EchoSurfaceCrestPath.FaceDistances(
                        7.0,
                        8.0,
                        8.5,
                        7.5);
        EchoSurfaceCrestPath.FaceDistances rightDistances =
                new EchoSurfaceCrestPath.FaceDistances(
                        8.0,
                        9.0,
                        9.5,
                        8.5);
        Vec3 leftA = new Vec3(0.0, 0.0, 0.0);
        Vec3 leftB = new Vec3(1.0, 0.0, 0.0);
        Vec3 leftC = new Vec3(1.0, 1.0, 0.0);
        Vec3 leftD = new Vec3(0.0, 1.0, 0.0);
        Vec3 rightA = leftB;
        Vec3 rightB = new Vec3(2.0, 0.0, 0.0);
        Vec3 rightC = new Vec3(2.0, 1.0, 0.0);
        Vec3 rightD = leftC;
        double edgeV = 0.37;
        Vec3 sharedPoint = leftB.lerp(leftC, edgeV);
        double leftPhase = EchoSurfaceCrestPath.distanceAtPoint(
                leftDistances,
                1.0,
                edgeV,
                leftA,
                leftB,
                leftC,
                leftD,
                sharedPoint,
                new Vec3(0.0, 0.0, -2.0));
        double rightPhase = EchoSurfaceCrestPath.distanceAtPoint(
                rightDistances,
                0.0,
                edgeV,
                rightA,
                rightB,
                rightC,
                rightD,
                sharedPoint,
                new Vec3(0.0, 0.0, -2.0));
        helper.assertTrue(
                Math.abs(leftPhase - rightPhase) < 1.0E-9,
                "neighboring block faces must share one continuous crest phase at their common edge");
        helper.succeed();
    }

    private static void echoArrivalIncremental(GameTestHelper helper) {
        int sizeX = 9;
        int sizeY = 6;
        int sizeZ = 8;
        int volume = sizeX * sizeY * sizeZ;
        double[] traversalCosts = new double[volume];
        java.util.Arrays.fill(traversalCosts, 1.0);

        // A wall with two differently priced openings exercises blocked cells,
        // diagonal corner rejection and weighted routes in the same field.
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                traversalCosts[4 + sizeX * (y + sizeY * z)] =
                        Double.POSITIVE_INFINITY;
            }
        }
        traversalCosts[4 + sizeX * (1 + sizeY * 2)] = 1.0;
        traversalCosts[4 + sizeX * (4 + sizeY * 6)] = 2.75;
        for (int x = 5; x < sizeX; x++) {
            traversalCosts[x + sizeX * (2 + sizeY * 4)] = 1.6;
        }

        EchoArrivalSolver.Field synchronous = EchoArrivalSolver.solve(
                sizeX,
                sizeY,
                sizeZ,
                1,
                2,
                3,
                32.0,
                index -> traversalCosts[index]);
        EchoArrivalSolver.Incremental incremental = EchoArrivalSolver.incremental(
                sizeX,
                sizeY,
                sizeZ,
                1,
                2,
                3,
                32.0,
                index -> traversalCosts[index]);

        boolean rejectedPrematureResult = false;
        try {
            incremental.result();
        } catch (IllegalStateException expected) {
            rejectedPrematureResult = true;
        }
        helper.assertTrue(
                rejectedPrematureResult,
                "an incremental arrival field must not publish a partial result");

        int advances = 0;
        while (!incremental.advance(3)) {
            advances++;
            helper.assertFalse(
                    incremental.isComplete(),
                    "an unfinished incremental step must remain explicitly pending");
            helper.assertTrue(
                    advances <= volume,
                    "the incremental arrival solver must make progress on every budgeted advance");
        }
        advances++;
        helper.assertTrue(
                advances > 2,
                "a three-node budget must split a non-trivial arrival field across several advances");
        helper.assertTrue(
                incremental.isComplete() && incremental.advance(3),
                "completion must be terminal and subsequent advances must be idempotent");

        EchoArrivalSolver.Field cooperative = incremental.result();
        helper.assertValueEqual(
                cooperative.reachedCells(),
                synchronous.reachedCells(),
                "incremental routing must reach exactly the synchronous field's cells");
        helper.assertTrue(
                Math.abs(cooperative.farthestDistance()
                                - synchronous.farthestDistance())
                        <= 1.0E-5F,
                "incremental routing must preserve the synchronous farthest distance");
        for (int z = 0; z < sizeZ; z++) {
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    float expected = synchronous.distance(x, y, z);
                    float actual = cooperative.distance(x, y, z);
                    helper.assertTrue(
                            Float.isFinite(expected)
                                    ? Float.isFinite(actual)
                                            && Math.abs(actual - expected) <= 1.0E-5F
                                    : !Float.isFinite(actual),
                            "incremental routing must match solve() at "
                                    + x + "," + y + "," + z);
                }
            }
        }
        helper.succeed();
    }

    private static void blockChangeClassification(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.assertValueEqual(
                EchoBlockChange.classify(Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState()),
                EchoBlockChange.Kind.UNCHANGED,
                "an exactly equal block state must remain unchanged");
        helper.assertValueEqual(
                EchoBlockChange.classify(
                        Blocks.OAK_FENCE.defaultBlockState(),
                        Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true)),
                EchoBlockChange.Kind.REPLACED,
                "connection and other block-state properties must be part of the remembered structure");
        helper.assertValueEqual(
                EchoBlockChange.classify(Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState()),
                EchoBlockChange.Kind.REPLACED,
                "a different present block must be classified as a replacement");
        helper.assertValueEqual(
                EchoBlockChange.classify(Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState()),
                EchoBlockChange.Kind.MISSING,
                "remembered blocks removed from the world must remain visible as ghosts");
        helper.assertValueEqual(
                EchoBlockChange.classify(null, Blocks.STONE.defaultBlockState()),
                EchoBlockChange.Kind.ADDED,
                "new blocks in remembered air must be classified as additions");
        helper.assertValueEqual(
                EchoBlockChange.classify(null, Blocks.WATER.defaultBlockState()),
                EchoBlockChange.Kind.UNCHANGED,
                "the sea around a site was never added to it, so an echo must not drain it");
        helper.assertValueEqual(
                EchoBlockChange.classify(
                        null,
                        Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.WATERLOGGED, true)),
                EchoBlockChange.Kind.ADDED,
                "a waterlogged block is still something someone built in remembered air");
        helper.assertValueEqual(
                EchoBlockChange.classify(
                        Blocks.STONE.defaultBlockState(),
                        Blocks.WATER.defaultBlockState()),
                EchoBlockChange.Kind.REPLACED,
                "water that flooded a remembered block must still yield to its ghost");
        helper.assertValueEqual(
                EchoBlockChange.classify(null, Blocks.GRAVEL.defaultBlockState(), false),
                EchoBlockChange.Kind.UNCHANGED,
                "a cell no template describes is world terrain, not remembered air");
        helper.assertValueEqual(
                EchoBlockChange.classify(null, Blocks.GRAVEL.defaultBlockState(), true),
                EchoBlockChange.Kind.ADDED,
                "a cell the site does build in must still fade to reveal its memory");
        helper.assertValueEqual(
                EchoBlockChange.classify(
                        Blocks.DIRT.defaultBlockState(),
                        Blocks.STONE.defaultBlockState(),
                        false),
                EchoBlockChange.Kind.REPLACED,
                "fallen rubble over past dirt is a replacement even when the cell is not an addition");
        helper.assertValueEqual(
                EchoBlockChange.classify(
                        Blocks.SHORT_GRASS.defaultBlockState(),
                        Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState(),
                        false),
                EchoBlockChange.Kind.ADDED,
                "rubble on remembered grass is historical air, not a solid replacement");
        helper.assertValueEqual(
                EchoBlockChange.classify(
                        Blocks.DANDELION.defaultBlockState(),
                        Blocks.DEEPSLATE_TILE_WALL.defaultBlockState(),
                        false),
                EchoBlockChange.Kind.ADDED,
                "rubble on remembered flowers occupies past empty volume");
        helper.assertFalse(
                EchoBlockChange.claimsRememberedSolid(Blocks.SHORT_GRASS.defaultBlockState()),
                "plants do not claim solid past volume");
        helper.assertTrue(
                EchoBlockChange.claimsRememberedSolid(Blocks.DIRT.defaultBlockState()),
                "dirt still claims solid past volume for mound replacements");
        helper.assertFalse(
                EchoBlockChange.claimsRememberedSolid(Blocks.BARRIER.defaultBlockState()),
                "barrier masks are historical air, not solid past volume");
        helper.assertValueEqual(
                EchoBlockChange.classify(
                        Blocks.BARRIER.defaultBlockState(),
                        Blocks.COBBLESTONE.defaultBlockState(),
                        true),
                EchoBlockChange.Kind.ADDED,
                "rubble over a barrier mask is an addition into historical air");
        helper.assertTrue(
                EchoBlockChange.Kind.REPLACED.canFadePresentBlock(),
                "replaced present solids must be eligible for mesh hiding");
        helper.assertTrue(
                EchoBlockChange.Kind.ADDED.canFadePresentBlock(),
                "additions into historical air must be eligible for mesh hiding");
        helper.assertTrue(
                EchoBlockChange.obstructsPastSpace(
                        Blocks.STONE_SLAB.defaultBlockState(), helper.getLevel(), position),
                "non-full solid blocks still obscure remembered space");
        helper.assertFalse(
                EchoBlockChange.obstructsPastSpace(
                        Blocks.SHORT_GRASS.defaultBlockState(), helper.getLevel(), position),
                "pass-through plants must not be treated as solid occluders");
        helper.assertTrue(
                EchoBlockChange.shouldHidePresentGeometry(Blocks.SHORT_GRASS.defaultBlockState()),
                "a newly added plant must still disappear from remembered air");
        helper.assertTrue(
                EchoBlockChange.shouldHidePresentGeometry(Blocks.CHEST.defaultBlockState()),
                "a newly added block entity must disappear together with its renderer");
        assertFadeSeedNbtFormats(helper);
        helper.succeed();
    }

    /**
     * Canonical writers store {@code size} as int[3]; older files used a list.
     * Malformed size must fail visibly (empty Optional), never a silent zero seed.
     */
    private static void assertFadeSeedNbtFormats(GameTestHelper helper) {
        CompoundTag arrayRoot = new CompoundTag();
        arrayRoot.putIntArray("size", new int[] {2, 1, 2});
        arrayRoot.putIntArray("cells", new int[] {0, 3});
        EchoSiteAdditions fromArray = EchoSiteAdditions.parse(arrayRoot).orElseThrow();
        helper.assertValueEqual(
                fromArray.size(),
                2,
                "int-array size must load the packaged fade seed");
        helper.assertTrue(
                fromArray.contains(0, 0, 0) && fromArray.contains(1, 0, 1),
                "int-array size must preserve packed cell indices");

        ListTag sizeList = new ListTag();
        sizeList.add(IntTag.valueOf(2));
        sizeList.add(IntTag.valueOf(1));
        sizeList.add(IntTag.valueOf(2));
        CompoundTag listRoot = new CompoundTag();
        listRoot.put("size", sizeList);
        listRoot.putIntArray("cells", new int[] {1});
        EchoSiteAdditions fromList = EchoSiteAdditions.parse(listRoot).orElseThrow();
        helper.assertValueEqual(
                fromList.size(),
                1,
                "legacy list-of-int size must still load for backcompat");
        helper.assertTrue(
                fromList.contains(0, 0, 1),
                "legacy list size must preserve packed cell indices");

        CompoundTag badSize = new CompoundTag();
        badSize.putIntArray("size", new int[] {2, 0, 2});
        badSize.putIntArray("cells", new int[] {0});
        helper.assertTrue(
                EchoSiteAdditions.parse(badSize).isEmpty(),
                "non-positive size must fail visibly without throwing");

        CompoundTag missingCells = new CompoundTag();
        missingCells.putIntArray("size", new int[] {1, 1, 1});
        helper.assertTrue(
                EchoSiteAdditions.parse(missingCells).isEmpty(),
                "missing cells int-array must fail visibly without throwing");

        CompoundTag emptySize = new CompoundTag();
        emptySize.putIntArray("cells", new int[] {0});
        helper.assertTrue(
                EchoSiteAdditions.parse(emptySize).isEmpty(),
                "absent size tag must fail visibly without throwing");
    }

    private static void visualTiming(GameTestHelper helper) {
        EchoRadialWindow outboundWindow = EchoRadialWindow.forPulse(
                10.0,
                false,
                1.0,
                0.92,
                1.82,
                0.9);
        helper.assertTrue(
                Math.abs(outboundWindow.minimumDistance() - 7.28) < 1.0E-9
                        && Math.abs(outboundWindow.maximumDistance() - 11.82)
                                < 1.0E-9,
                "the optimized outbound cache query must include the full visible crest");
        EchoRadialWindow returnWindow = EchoRadialWindow.forPulse(
                10.0,
                true,
                1.0,
                0.92,
                1.82,
                0.9);
        helper.assertTrue(
                Math.abs(returnWindow.minimumDistance() - 8.18) < 1.0E-9
                        && Math.abs(returnWindow.maximumDistance() - 12.72)
                                < 1.0E-9,
                "the optimized return query must reverse the asymmetric crest supports");
        helper.assertValueEqual(
                EchoVisualTiming.OUTBOUND_START_SECONDS,
                0.12,
                "the restored 0.1 pulse must release almost immediately after the click");
        helper.assertValueEqual(
                EchoVisualTiming.OUTBOUND_END_SECONDS,
                1.94,
                "the restored 0.1 outbound crest must retain its original duration");
        helper.assertValueEqual(
                EchoVisualTiming.RETURN_START_SECONDS,
                2.30,
                "the restored 0.1 return must begin after its original short perimeter pause");
        helper.assertValueEqual(
                EchoVisualTiming.RECOVERY_START_SECONDS,
                3.15,
                "the restored 0.1 darkness must hold during the first half of the return");
        helper.assertValueEqual(
                EchoVisualTiming.EFFECT_END_SECONDS,
                4.30,
                "the restored 0.1 pulse and recovery must end together");
        helper.assertTrue(
                EchoVisualTiming.surfaceReturnArrival(
                                10.0,
                                12.0,
                                EchoMaterialResponse.STONE.delaySeconds())
                        < EchoVisualTiming.surfaceReturnArrival(
                                2.0,
                                12.0,
                                EchoMaterialResponse.STONE.delaySeconds()),
                "an inward return must cross distant surfaces before surfaces close to the player");
        helper.assertValueEqual(
                EchoVisualTiming.outboundRadius(EchoVisualTiming.OUTBOUND_START_SECONDS, 12.9),
                0.0,
                "the remembered echo must start its surface crest at the player");
        helper.assertTrue(
                Math.abs(EchoVisualTiming.outboundRadius(
                        (EchoVisualTiming.OUTBOUND_START_SECONDS
                                + EchoVisualTiming.OUTBOUND_END_SECONDS) * 0.5,
                        12.0) - 6.0) < 1.0E-9,
                "the remembered echo crest must cross half its visual radius at mid pulse");
        helper.assertValueEqual(
                EchoVisualTiming.outboundRadius(EchoVisualTiming.OUTBOUND_END_SECONDS, 12.9),
                12.9,
                "the remembered echo must reach its configured visual perimeter");
        helper.assertValueEqual(
                EchoVisualTiming.returnRadius(
                        EchoVisualTiming.RETURN_START_SECONDS,
                        12.9),
                12.9,
                "the returning crest must leave from the same perimeter reached by the outbound crest");
        helper.assertValueEqual(
                EchoVisualTiming.returnRadius(
                        EchoVisualTiming.RETURN_END_SECONDS,
                        12.9),
                0.0,
                "the returning crest must finish at the activating player");
        helper.assertValueEqual(
                EchoVisualTiming.itemAnimationFrame(EchoVisualTiming.OUTBOUND_START_SECONDS),
                0.0F,
                "the item must begin on frame zero with the outbound pulse");
        helper.assertTrue(
                EchoVisualTiming.itemAnimationFrame(
                        (EchoVisualTiming.OUTBOUND_START_SECONDS + EchoVisualTiming.OUTBOUND_END_SECONDS) * 0.5)
                        > 2.9F,
                "the item frames must advance during the outbound pulse");
        helper.assertValueEqual(
                EchoVisualTiming.itemAnimationFrame(EchoVisualTiming.OUTBOUND_END_SECONDS),
                6.0F,
                "the item must reach frame six at the perimeter");
        helper.assertValueEqual(
                EchoVisualTiming.itemAnimationFrame(EchoVisualTiming.RETURN_START_SECONDS),
                6.0F,
                "the item must hold frame six until the return begins");
        helper.assertTrue(
                EchoVisualTiming.itemAnimationFrame(
                        (EchoVisualTiming.RETURN_START_SECONDS
                                        + EchoVisualTiming.RETURN_END_SECONDS)
                                * 0.5)
                        < 3.1F,
                "the item frames must reverse during the returning pulse");
        helper.assertValueEqual(
                EchoVisualTiming.itemAnimationFrame(
                        EchoVisualTiming.RETURN_END_SECONDS),
                0.0F,
                "the item must settle back on frame zero");
        helper.assertValueEqual(EchoVisualTiming.shadowEnvelope(0.0), 0.0, "darkening must start from a clean frame");
        helper.assertValueEqual(
                EchoVisualTiming.shadowEnvelope(EchoVisualTiming.DARKEN_START_SECONDS),
                0.0,
                "the ignition frame must remain visually clean before the grade eases in");
        helper.assertTrue(
                EchoVisualTiming.shadowEnvelope(0.10) < 0.04
                        && EchoVisualTiming.shadowEnvelope(0.30) < 0.20,
                "darkening must build gradually throughout its first second");
        helper.assertValueEqual(
                EchoVisualTiming.shadowEnvelope(EchoVisualTiming.DARKEN_END_SECONDS),
                1.0,
                "darkening must eventually reach its full cinematic contrast");
        helper.assertTrue(
                EchoVisualTiming.shadowEnvelope(0.50) >= 0.45
                        && EchoVisualTiming.shadowEnvelope(0.50) <= 0.55,
                "screen darkening must be halfway through a smooth one-second entrance");
        helper.assertValueEqual(
                EchoVisualTiming.shadowEnvelope(1.0),
                1.0,
                "screen darkening must reach full strength after one second");
        helper.assertValueEqual(
                EchoVisualTiming.shadowEnvelope(EchoVisualTiming.EFFECT_END_SECONDS),
                0.0,
                "the effect must recover smoothly by the end of the return");
        helper.assertTrue(
                EchoVisualTiming.shadowEnvelope(
                                        (EchoVisualTiming.RECOVERY_START_SECONDS
                                                        + EchoVisualTiming.EFFECT_END_SECONDS)
                                                * 0.5)
                                >= 0.49
                        && EchoVisualTiming.shadowEnvelope(
                                        (EchoVisualTiming.RECOVERY_START_SECONDS
                                                        + EchoVisualTiming.EFFECT_END_SECONDS)
                                                * 0.5)
                                <= 0.51,
                "the restored 0.1 grade must recover smoothly over its final interval");
        helper.assertValueEqual(
                EchoVisualTiming.configuredShadowStrength(
                        EchoVisualTiming.DARKEN_END_SECONDS, 1.0F, 0.0F),
                0.0F,
                "screen darkening zero must disable the grade without changing the sonar pulse");
        helper.assertValueEqual(
                EchoVisualTiming.configuredShadowStrength(
                        EchoVisualTiming.DARKEN_END_SECONDS, 1.0F, 0.5F),
                0.25F,
                "screen darkening must scale across the full zero-to-two range");
        helper.assertValueEqual(
                EchoVisualTiming.configuredShadowStrength(
                        EchoVisualTiming.DARKEN_END_SECONDS, 1.0F, 1.0F),
                0.5F,
                "screen darkening one must remain an adjustable midpoint");
        helper.assertValueEqual(
                EchoVisualTiming.configuredShadowStrength(
                        EchoVisualTiming.DARKEN_END_SECONDS, 1.0F, 2.0F),
                1.0F,
                "screen darkening two must reach the pitch-black grade");
        helper.assertValueEqual(
                EchoScreenGrade.sceneMultiplier(1.0F, 1.0F),
                0.0F,
                "a full-strength grade must multiply the ordinary scene by exact zero");
        helper.assertValueEqual(
                EchoScreenGrade.waveMask(0.28F, 0.40F, 0.58F, 1.0F),
                0.0F,
                "blue moonlight must never be mistaken for the sonar");
        helper.assertValueEqual(
                EchoScreenGrade.waveMask(0.18F, 0.52F, 0.48F, 1.0F),
                0.0F,
                "oxidized copper must never be mistaken for the sonar");
        helper.assertTrue(
                EchoScreenGrade.waveMask(
                        0.05F,
                        0.95F,
                        1.0F,
                        EchoScreenGrade.encodedWaveAlpha(0.72F)) >= 0.71F
                        && EchoScreenGrade.waveMask(
                                0.05F,
                                0.95F,
                                1.0F,
                                EchoScreenGrade.encodedWaveAlpha(0.72F)) <= 0.73F,
                "the technical alpha marker must preserve only the sonar's real opacity");
        helper.assertValueEqual(
                EchoVisualTiming.presentOccluderOpacity(0.0F, 0.38F),
                1.0F,
                "blocks occupying remembered air must remain opaque before the return crosses them");
        Map<Long, Double> mergedHideDistances = EchoOccluderDistances.best(
                Map.of(1L, 40.0, 2L, 12.0),
                Map.of(1L, 8.0, 3L, 5.0));
        helper.assertValueEqual(
                mergedHideDistances.get(1L),
                8.0,
                "a nearer fade-seed distance must win over a longer local routed timing");
        helper.assertValueEqual(
                mergedHideDistances.get(2L),
                12.0,
                "local-only occluder distances must still hide when the seed omits them");
        helper.assertValueEqual(
                mergedHideDistances.get(3L),
                5.0,
                "seed-only occluder distances must hide without a local timing");
        Map<Long, Double> pulseDistances = new HashMap<>();
        pulseDistances.put(9L, 6.0);
        helper.assertTrue(
                EchoOccluderDistances.mergeMin(pulseDistances, Map.of(9L, 4.0, 10L, 11.0)),
                "publishing a nearer or new fade distance must report a change");
        helper.assertFalse(
                EchoOccluderDistances.mergeMin(pulseDistances, Map.of(9L, 20.0)),
                "a worse local timing must not overwrite a nearer seeded distance");
        helper.assertValueEqual(
                pulseDistances.get(9L),
                4.0,
                "seed coverage keeps the nearer distance after a worse scan publish");
        helper.assertValueEqual(
                pulseDistances.get(10L),
                11.0,
                "scan-only cells still register when the seed omits them");
        helper.assertValueEqual(
                EchoVisualTiming.presentOccluderOpacity(1.0F, 0.38F),
                0.38F,
                "blocks occupying remembered air must become textured transparency on the return");
        helper.assertValueEqual(
                EchoVisualTiming.presentOccluderOpacity(0.10F, 0.38F),
                1.0F,
                "the current texture must become readable before its transparency begins");
        helper.assertValueEqual(
                EchoVisualTiming.presentOccluderMinimumLight(1.0F),
                0,
                "present blocks made translucent must never receive invented light");
        helper.assertFalse(
                EchoPostEffects.contains(
                        List.of(Identifier.fromNamespaceAndPath("echoes_show_the_past", "echo_scan")),
                        null),
                "a cleared Minecraft post effect must not crash echo ownership checks");
        helper.assertValueEqual(
                EchoVisualTiming.presentOccluderReveal(
                        EchoVisualTiming.RETURN_START_SECONDS - 0.01,
                        12.0,
                        12.0),
                0.0F,
                "blocks occupying remembered air must not transition on the outbound impact");
        helper.assertValueEqual(
                EchoVisualTiming.presentOccluderReveal(
                        EchoVisualTiming.RETURN_START_SECONDS + 0.01,
                        2.0,
                        12.0),
                0.0F,
                "inner blocks must remain untouched until the returning front reaches them");
        double outerReturnArrival =
                EchoVisualTiming.surfaceReturnArrival(
                        10.0,
                        12.0,
                        EchoMaterialResponse.STONE
                                .delaySeconds());
        double innerReturnArrival =
                EchoVisualTiming.surfaceReturnArrival(
                        2.0,
                        12.0,
                        EchoMaterialResponse.STONE
                                .delaySeconds());
        double betweenReturnCrossings =
                (outerReturnArrival
                                + innerReturnArrival)
                        * 0.5;
        helper.assertTrue(
                EchoVisualTiming.presentOccluderReveal(
                                betweenReturnCrossings,
                                10.0,
                                12.0)
                        > 0.99F,
                "a distant surface must settle after the inbound crest has crossed it");
        helper.assertTrue(
                EchoVisualTiming.presentOccluderReveal(
                                betweenReturnCrossings,
                                2.0,
                                12.0)
                        < 0.02F,
                "a nearby surface must remain current until the inbound crest reaches the player");
        double responseDistance = 6.0;
        double layeredReturnTime =
                EchoVisualTiming.RETURN_START_SECONDS
                        + (EchoVisualTiming.EFFECT_END_SECONDS
                                        - EchoVisualTiming.RETURN_START_SECONDS)
                                * 0.525;
        float presentDissolve = EchoVisualTiming.presentOccluderReveal(
                layeredReturnTime, responseDistance, 12.0);
        float rememberedMaterialization = EchoVisualTiming.rememberedReveal(
                layeredReturnTime, responseDistance, 12.0);
        helper.assertTrue(
                presentDissolve > rememberedMaterialization,
                "the current material must begin yielding just before the remembered texture settles");
        helper.assertTrue(
                EchoMaterialResponse.METAL.reflectivity()
                        > EchoMaterialResponse.STONE.reflectivity()
                        && EchoMaterialResponse.STONE.reflectivity()
                        > EchoMaterialResponse.SOFT.reflectivity(),
                "metal and stone must answer more clearly than vegetation and wool");
        helper.assertTrue(
                EchoVisualTiming.surfaceResponseEnvelope(
                                EchoVisualTiming.surfaceReturnArrival(
                                                6.0,
                                                12.0,
                                                EchoMaterialResponse.STONE
                                                        .delaySeconds())
                                        + 0.08,
                                6.0,
                                12.0,
                                EchoMaterialResponse.STONE.delaySeconds(),
                                EchoMaterialResponse.STONE.widthScale())
                        > EchoVisualTiming.surfaceResponseEnvelope(
                                EchoVisualTiming.surfaceReturnArrival(
                                                6.0,
                                                12.0,
                                                EchoMaterialResponse.STONE
                                                        .delaySeconds())
                                        + 0.08,
                                6.0,
                                12.0,
                                EchoMaterialResponse.SOFT.delaySeconds(),
                                EchoMaterialResponse.SOFT.widthScale()),
                "soft materials must answer later and with a broader, quieter profile");
        helper.assertFalse(
                EchoFaceVisibility.cameraInsideBlock(new Vec3(4.5, 6.5, 8.5), new BlockPos(1, 2, 3)),
                "camera-inside helper stays false for an outside camera");
        helper.assertTrue(
                EchoFaceVisibility.cameraInsideBlock(new Vec3(1.5, 2.5, 3.5), new BlockPos(1, 2, 3)),
                "camera-inside helper detects when the camera sits in a cell");
        helper.assertValueEqual(
                EchoGhostOccupancy.targetVisibility(
                        new BlockPos(0, 0, 0),
                        new BlockPos(0, 0, 0)),
                0.0F,
                "the occupied ghost cell must clear so interior faces can read");
        helper.assertValueEqual(
                EchoGhostOccupancy.targetVisibility(
                        new BlockPos(1, 0, 1),
                        new BlockPos(0, 0, 0)),
                0.0F,
                "Chebyshev neighbours of the camera must clear with the occupied cell");
        helper.assertValueEqual(
                EchoGhostOccupancy.targetVisibility(
                        new BlockPos(2, 0, 0),
                        new BlockPos(0, 0, 0)),
                1.0F,
                "ghosts outside the occupancy neighbourhood keep their projection");
        helper.assertTrue(
                EchoGhostOccupancy.approach(1.0F, 0.0F, EchoGhostOccupancy.FADE_SECONDS * 0.25F)
                        < 0.85F
                        && EchoGhostOccupancy.approach(1.0F, 0.0F, EchoGhostOccupancy.FADE_SECONDS * 0.25F)
                                > 0.35F,
                "occupancy fade out must be gradual rather than instantaneous");
        helper.assertTrue(
                EchoGhostOccupancy.approach(1.0F, 0.0F, EchoGhostOccupancy.FADE_SECONDS * 2.0F)
                        < 0.05F,
                "a long fade interval must nearly finish clearing an occupied ghost");
        helper.assertTrue(
                EchoGhostOccupancy.occludesSharedFace(1.0F),
                "a fully visible neighbour must keep the shared interior face culled");
        helper.assertTrue(
                EchoGhostOccupancy.sharedFaceReveal(1.0F) <= 0.005F,
                "a solid neighbour contributes no shared-face reveal");
        helper.assertTrue(
                EchoGhostOccupancy.sharedFaceReveal(0.5F) > 0.2F
                        && EchoGhostOccupancy.sharedFaceReveal(0.5F) < 0.8F,
                "shared interior faces must ease in as the neighbour fades");
        helper.assertTrue(
                EchoGhostOccupancy.sharedFaceReveal(0.0F) >= 0.999F,
                "a cleared neighbour fully reveals the shared interior face");
        helper.assertFalse(
                EchoGhostOccupancy.occludesSharedFace(0.0F),
                "an occupancy-cleared neighbour must reveal the shared interior face");
        helper.assertTrue(
                EchoGhostOccupancy.isFadeImmune(EchoBlockChange.Kind.ADDED)
                        && EchoGhostOccupancy.isFadeImmune(EchoBlockChange.Kind.REPLACED),
                "added and replaced cells must keep their stable projection opacity");
        helper.assertFalse(
                EchoGhostOccupancy.isFadeImmune(EchoBlockChange.Kind.MISSING),
                "only missing historical shells may dissolve around the camera");
        Vec3 faceCenter = new Vec3(0.5, 0.5, 0.0);
        Vec3 camera = new Vec3(0.5, 0.5, -2.0);
        helper.assertTrue(
                EchoFaceVisibility.facePointsTowardCamera(camera, faceCenter, new Vec3(0.0, 0.0, -1.0)),
                "the ghost face looking at the camera must render");
        helper.assertFalse(
                EchoFaceVisibility.facePointsTowardCamera(camera, faceCenter, new Vec3(0.0, 0.0, 1.0)),
                "the rear ghost face must not render through the front face");
        helper.assertFalse(
                EchoFaceVisibility.facePointsTowardCamera(camera, faceCenter, new Vec3(1.0, 0.0, 0.0)),
                "an edge-on lateral face must not add transparency noise");
        helper.assertTrue(
                EchoFaceVisibility.facePointsTowardCamera(
                        new Vec3(0.5, 0.5, 2.0),
                        new Vec3(0.5, 0.5, 1.0),
                        new Vec3(0.0, 0.0, 1.0)),
                "inward shell faces remain readable after occupancy clears nearby cells");
        Map<BlockPos, BlockState> rememberedLight = Map.of(
                new BlockPos(0, 0, 0), Blocks.TORCH.defaultBlockState());
        Map<Long, Integer> propagatedLight = EchoPastLight.propagate(
                rememberedLight,
                new BlockPos(0, 0, 0),
                new BlockPos(3, 0, 0));
        helper.assertTrue(
                EchoPastLight.sample(propagatedLight, new BlockPos(0, 0, 0))
                        > EchoPastLight.sample(propagatedLight, new BlockPos(3, 0, 0)),
                "a remembered torch must illuminate only a fading neighborhood of the past");
        Map<Long, Integer> presentLightThroughRememberedAir = EchoPastLight.propagate(
                Map.of(),
                Map.of(new BlockPos(0, 0, 0), 14),
                new BlockPos(0, 0, 0),
                new BlockPos(3, 0, 0));
        helper.assertTrue(
                EchoPastLight.sample(presentLightThroughRememberedAir, new BlockPos(3, 0, 0)) > 0,
                "a present light source must travel through a position that was air in the remembered timeline");
        Map<Long, Integer> skyThroughRememberedAir = EchoPastLight.propagateSky(
                Map.of(),
                Map.of(new BlockPos(0, 3, 0), 15),
                new BlockPos(0, 0, 0),
                new BlockPos(0, 3, 0));
        helper.assertValueEqual(
                EchoPastLight.sample(skyThroughRememberedAir, new BlockPos(0, 0, 0)),
                15,
                "open remembered air must carry skylight downward without an artificial black shadow");
        helper.assertValueEqual(
                EchoPastLight.sample(
                        EchoPastLight.propagate(
                                Map.of(
                                        new BlockPos(0, 0, 0), Blocks.TORCH.defaultBlockState(),
                                        new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState()),
                                new BlockPos(0, 0, 0),
                                new BlockPos(2, 0, 0)),
                        new BlockPos(2, 0, 0)),
                0,
                "remembered opaque blocks must stop remembered block light");
        float replacedPresent = EchoProjectionStyle.presentTargetOpacity(
                EchoBlockChange.Kind.REPLACED, 1.0F);
        float addedPresent = EchoProjectionStyle.presentTargetOpacity(
                EchoBlockChange.Kind.ADDED, 1.0F);
        helper.assertTrue(
                replacedPresent <= 0.05F,
                "a replacement must almost completely yield to the remembered block");
        helper.assertTrue(
                addedPresent >= 0.22F && addedPresent <= 0.30F,
                "a block occupying remembered air must remain faintly textured and translucent");
        helper.assertTrue(
                EchoProjectionStyle.rememberedBaseOpacity(EchoBlockChange.Kind.REPLACED, 1.0F) >= 0.85F,
                "the remembered material in a replacement must dominate the final projection");
        helper.assertTrue(
                EchoProjectionStyle.rememberedBaseOpacity(EchoBlockChange.Kind.REPLACED, 1.0F)
                        > EchoProjectionStyle.rememberedBaseOpacity(EchoBlockChange.Kind.MISSING, 1.0F),
                "a replaced remembered block must be only slightly more opaque than a missing one");
        helper.assertValueEqual(
                EchoProjectionStyle.presentTargetOpacity(EchoBlockChange.Kind.REPLACED, 1.0F),
                0.0F,
                "the present material must fully yield after a remembered replacement settles");
        helper.assertTrue(
                EchoProjectionStyle.occludedOpacity(0.88F) == 0.0F,
                "remembered textures must never be forced through unrelated foreground blocks");
        helper.assertValueEqual(
                EchoProjectionStyle.planeGhostCoverage(0.0F),
                0.0F,
                "an unpainted ghost-plane texel must remain a real cutout, not a translucent rectangle");
        helper.assertValueEqual(
                EchoProjectionStyle.planeGhostCoverage(1.0F),
                1.0F,
                "a painted ghost-plane texel must receive the block condition opacity exactly once");
        int darkProjectionLight = EchoPastLight.projectionPackedLight(
                LightCoordsUtil.pack(0, 0), 0);
        helper.assertValueEqual(
                LightCoordsUtil.block(darkProjectionLight),
                0,
                "neutral projection exposure must never invent warm block light");
        helper.assertValueEqual(
                LightCoordsUtil.sky(darkProjectionLight),
                0,
                "ghost geometry must not receive an artificial sky-light floor");
        int currentTorchLight = EchoPastLight.projectionPackedLight(
                LightCoordsUtil.pack(12, 0), 0);
        helper.assertValueEqual(
                LightCoordsUtil.block(currentTorchLight),
                12,
                "present light sources must illuminate remembered geometry");
        int replacedSurfaceLight = EchoPastLight.brightestPackedLight(
                LightCoordsUtil.pack(0, 0),
                LightCoordsUtil.pack(0, 12));
        int missingSurfaceLight = EchoPastLight.brightestPackedLight(
                LightCoordsUtil.pack(0, 12),
                LightCoordsUtil.pack(0, 12));
        helper.assertValueEqual(
                EchoPastLight.projectionPackedLight(replacedSurfaceLight, 0),
                EchoPastLight.projectionPackedLight(missingSurfaceLight, 0),
                "a replacement embedded in the present block must use the same visible surface light as missing geometry");
        int translucentPresentLight = EchoPastLight.translucentFacePackedLight(
                LightCoordsUtil.pack(0, 0),
                LightCoordsUtil.pack(11, 14));
        helper.assertValueEqual(
                LightCoordsUtil.block(translucentPresentLight),
                11,
                "a translucent present block must receive block light from its exposed surface");
        helper.assertValueEqual(
                LightCoordsUtil.sky(translucentPresentLight),
                14,
                "a translucent present block must receive sky light from its exposed surface");
        helper.assertValueEqual(
                EchoPastLight.translucentFacePackedLight(
                        LightCoordsUtil.pack(0, 0),
                        LightCoordsUtil.pack(0, 0)),
                LightCoordsUtil.pack(0, 0),
                "light from one exposed face must not leak around to an unrelated dark face");
        int torchProjectionLight = EchoPastLight.projectionPackedLight(
                LightCoordsUtil.pack(0, 0), 14);
        helper.assertValueEqual(
                LightCoordsUtil.block(torchProjectionLight),
                14,
                "remembered emitters must illuminate remembered geometry");
        int sharedTimelineLight = EchoPastLight.combinedBlockLight(11, 14);
        helper.assertValueEqual(
                sharedTimelineLight,
                14,
                "remembered and present emitters must share the brighter block-light value");
        int rememberedAirLight = EchoPastLight.ghostPackedLight(
                EchoBlockChange.Kind.ADDED,
                LightCoordsUtil.pack(15, 15),
                15);
        helper.assertTrue(
                LightCoordsUtil.block(rememberedAirLight) > 0
                        && LightCoordsUtil.block(rememberedAirLight) <= 8
                        && LightCoordsUtil.sky(rememberedAirLight) > 0
                        && LightCoordsUtil.sky(rememberedAirLight) <= 8,
                "blocks occupying remembered air must be restrained, not luminous or pitch black");
        int replacedTimelineLight = EchoPastLight.ghostPackedLight(
                EchoBlockChange.Kind.REPLACED,
                LightCoordsUtil.pack(12, 4),
                14);
        helper.assertValueEqual(
                LightCoordsUtil.block(replacedTimelineLight),
                14,
                "replaced remembered geometry must receive the brighter light from either timeline");
        helper.succeed();
    }

    private static void postUniformLayout(GameTestHelper helper) {
        String path = "assets/echoes_show_the_past/post_effect/echo_scan.json";
        String shaderPath = "assets/echoes_show_the_past/shaders/post/echo_scan.fsh";
        String horusPath = "assets/echoes_show_the_past/post_effect/horus_vision.json";
        String horusShaderPath =
                "assets/echoes_show_the_past/shaders/post/horus_vision.fsh";
        String horusSigilShaderPath =
                "assets/echoes_show_the_past/shaders/core/horus_sigil.fsh";
        String horusSigilTexturePath =
                "assets/echoes_show_the_past/textures/effect/horus_sigil.png";
        String medusaPath = "assets/echoes_show_the_past/post_effect/medusa_gaze.json";
        String medusaShaderPath =
                "assets/echoes_show_the_past/shaders/post/medusa_gaze.fsh";
        String grailPath =
                "assets/echoes_show_the_past/post_effect/holy_grail.json";
        String grailShaderPath =
                "assets/echoes_show_the_past/shaders/post/holy_grail.fsh";
        String philosopherPath =
                "assets/echoes_show_the_past/post_effect/philosophers_stone.json";
        String philosopherShaderPath =
                "assets/echoes_show_the_past/shaders/post/philosophers_stone.fsh";
        String medusaStoneShaderPath =
                "assets/echoes_show_the_past/shaders/core/medusa_stone.fsh";
        String medusaCrackShaderPath =
                "assets/echoes_show_the_past/shaders/core/medusa_crack.fsh";
        String petrifiedItemPath =
                "assets/echoes_show_the_past/items/petrified_mob.json";
        String petrifiedBasePath =
                "assets/echoes_show_the_past/models/item/petrified_mob_base.json";
        String englishPath =
                "assets/echoes_show_the_past/lang/en_us.json";
        String spanishPath =
                "assets/echoes_show_the_past/lang/es_es.json";
        String mixinPath = "echoes_show_the_past.mixins.json";
        String sodiumMixinClassPath =
                "dev/alvar/echoespast/mixin/client/SodiumLevelSliceMixin.class";
        String itemCutoutCompatMixinClassPath =
                "dev/alvar/echoespast/mixin/client/ItemCutoutSamplerCompatMixin.class";
        String medusaRenderTypesClassPath =
                "dev/alvar/echoespast/client/MedusaRenderTypes.class";
        String petrifiedTextureCacheClassPath =
                "dev/alvar/echoespast/client/PetrifiedTextureCache.class";
        String submitNodeCollectionMixinClassPath =
                "dev/alvar/echoespast/mixin/client/SubmitNodeCollectionMixin.class";
        String ghostOrderedSubmitNodeCollectorClassPath =
                "dev/alvar/echoespast/client/GhostOrderedSubmitNodeCollector.class";
        String echoRenderTypesClassPath =
                "dev/alvar/echoespast/client/EchoRenderTypes.class";
        String clientEchoRendererClassPath =
                "dev/alvar/echoespast/client/ClientEchoRenderer.class";
        String clientTemplateProjectionClassPath =
                "dev/alvar/echoespast/client/ClientTemplateProjection.class";
        String echoPastLightClassPath =
                "dev/alvar/echoespast/visual/EchoPastLight.class";
        String clientEchoStateClassPath =
                "dev/alvar/echoespast/client/ClientEchoState.class";
        String clientEchoArrivalFieldClassPath =
                "dev/alvar/echoespast/client/ClientEchoArrivalField.class";
        String echoesClientClassPath =
                "dev/alvar/echoespast/client/EchoesShowThePastClient.class";
        try (InputStream stream = EchoGameTests.class.getClassLoader().getResourceAsStream(path);
                InputStream shaderStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(shaderPath);
                InputStream horusStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(horusPath);
                InputStream horusShaderStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(horusShaderPath);
                InputStream horusSigilShaderStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                horusSigilShaderPath);
                InputStream horusSigilTextureStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                horusSigilTexturePath);
                InputStream medusaStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(medusaPath);
                InputStream medusaShaderStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(medusaShaderPath);
                InputStream grailStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(grailPath);
                InputStream grailShaderStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(grailShaderPath);
                InputStream philosopherStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(philosopherPath);
                InputStream philosopherShaderStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                philosopherShaderPath);
                InputStream medusaStoneShaderStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(medusaStoneShaderPath);
                InputStream medusaCrackShaderStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(medusaCrackShaderPath);
                InputStream petrifiedItemStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(petrifiedItemPath);
                InputStream petrifiedBaseStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(petrifiedBasePath);
                InputStream englishStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(englishPath);
                InputStream spanishStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(spanishPath);
                InputStream mixinStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(mixinPath);
                InputStream sodiumMixinClassStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                sodiumMixinClassPath);
                InputStream itemCutoutCompatMixinClassStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                itemCutoutCompatMixinClassPath);
                InputStream medusaRenderTypesClassStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                medusaRenderTypesClassPath);
                InputStream petrifiedTextureCacheClassStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                petrifiedTextureCacheClassPath);
                InputStream submitNodeCollectionMixinClassStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                submitNodeCollectionMixinClassPath);
                InputStream ghostOrderedSubmitNodeCollectorClassStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                ghostOrderedSubmitNodeCollectorClassPath);
                InputStream echoRenderTypesClassStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                echoRenderTypesClassPath);
                InputStream clientEchoRendererClassStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                clientEchoRendererClassPath);
                InputStream clientTemplateProjectionClassStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                clientTemplateProjectionClassPath);
                InputStream echoPastLightClassStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                echoPastLightClassPath);
                InputStream clientEchoStateClassStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                clientEchoStateClassPath);
                InputStream clientEchoArrivalFieldClassStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                clientEchoArrivalFieldClassPath);
                InputStream echoesClientClassStream =
                        EchoGameTests.class.getClassLoader().getResourceAsStream(
                                echoesClientClassPath)) {
            helper.assertTrue(stream != null, "the final echo post chain must be packaged");
            helper.assertTrue(shaderStream != null, "the echo fragment shader must be packaged");
            helper.assertTrue(horusStream != null, "the Eye of Horus post chain must be packaged");
            helper.assertTrue(
                    horusShaderStream != null,
                    "the Eye of Horus fragment shader must be packaged");
            helper.assertTrue(
                    horusSigilShaderStream != null,
                    "the external Eye of Horus sigil material must be packaged");
            helper.assertTrue(
                    horusSigilTextureStream != null,
                    "the external Eye of Horus sigil texture must be packaged");
            helper.assertTrue(medusaStream != null, "the Medusa post chain must be packaged");
            helper.assertTrue(
                    medusaShaderStream != null,
                    "the Medusa gaze shader must be packaged");
            helper.assertTrue(
                    grailStream != null && grailShaderStream != null,
                    "the Holy Grail post chain and shader must be packaged");
            helper.assertTrue(
                    philosopherStream != null
                            && philosopherShaderStream != null,
                    "the Philosopher's Stone post chain and shader must be packaged");
            helper.assertTrue(
                    medusaStoneShaderStream != null,
                    "the Medusa stone material must be packaged");
            helper.assertTrue(
                    medusaCrackShaderStream != null,
                    "the statue mining crack material must be packaged");
            helper.assertTrue(
                    petrifiedItemStream != null && petrifiedBaseStream != null,
                    "the 3D petrified-creature item model must be packaged");
            helper.assertTrue(
                    englishStream != null && spanishStream != null,
                    "both supported tooltip languages must be packaged");
            helper.assertTrue(
                    mixinStream != null,
                    "the mixin manifest must be packaged");
            String rawJson = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String json = rawJson
                    .replaceAll("\\s+", "");
            helper.assertTrue(
                    json.contains("\"name\":\"TintAndStrength\",\"type\":\"vec4\""),
                    "post uniforms must group tint and strength into an aligned vec4");
            helper.assertTrue(
                    json.contains("\"name\":\"GradeSettings\",\"type\":\"vec4\""),
                    "post uniforms must group scalar grade settings into an aligned vec4");
            helper.assertFalse(
                    json.contains("\"name\":\"ShadowStrength\",\"type\":\"float\""),
                    "screen darkness must occupy an aligned vec4 lane instead of a trailing scalar");
            helper.assertFalse(
                    json.contains("\"name\":\"Tint\",\"type\":\"vec3\""),
                    "a padded vec3 followed by scalar JSON uniforms misaligns ShadowStrength on the GPU");
            JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
            JsonArray echoConfig = root.getAsJsonArray("passes")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("uniforms")
                    .getAsJsonArray("EchoConfig");
            helper.assertValueEqual(
                    echoConfig.size(),
                    56,
                    "EchoConfig must allocate the complete 896-byte std140 block");
            helper.assertTrue(
                    json.contains("\"name\":\"LowFrequencyAim\",\"type\":\"vec4\""),
                    "EchoConfig must expose the directional aim vector");
            helper.assertTrue(
                    json.contains("\"sampler_name\":\"WorldDepth\""),
                    "the post chain must expose preserved world depth");
            helper.assertTrue(
                    json.contains("\"sampler_name\":\"ForegroundDepth\"")
                            && json.contains("\"use_depth_buffer\":true"),
                    "the post chain must expose the live first-person depth buffer");
            helper.assertFalse(
                    json.contains("\"sampler_name\":\"ArrivalField\""),
                    "Past Echo routing must not replace the narrow surface crest with a full-screen arrival-field emission");
            helper.assertFalse(
                    json.contains("\"sampler_name\":\"SceneDepth\""),
                    "first-person occlusion must use the explicitly named foreground depth input");
            String shader = new String(shaderStream.readAllBytes(), StandardCharsets.UTF_8);
            helper.assertTrue(
                    shader.contains("mat4 InverseLevelProjection")
                            && shader.contains("LowFrequencyOrigins[16]")
                            && shader.contains("LowFrequencyStyles[16]")
                            && shader.contains("LowFrequencyColors[16]")
                            && shader.contains("vec4 LowFrequencyAim"),
                    "the GLSL and JSON low-frequency uniform layouts must describe the same block");
            helper.assertTrue(
                    shader.contains("LowFrequencyAim.w")
                            && shader.contains("smoothstep(LowFrequencyAim.w"),
                    "directional pulses must soft-mask the outbound crest to the aimed cone");
            helper.assertTrue(
                    shader.contains("uniform sampler2D WorldDepthSampler")
                            && shader.contains("worldDepth >= 0.9999"),
                    "distant waves must reconstruct from preserved world depth and reject the sky");
            helper.assertTrue(
                    shader.contains("uniform sampler2D ForegroundDepthSampler")
                            && shader.contains("foregroundVisibility")
                            && shader.contains("lowFrequencyEmission(viewPosition, worldDepth)"
                                    + " * foregroundVisibility"),
                    "held hands and items must occlude wave emission without replacing world depth");
            helper.assertTrue(
                    shader.contains("screenHandoffStart = LowFrequencyColors[index].a")
                            && shader.contains(
                                    "smoothstep(screenHandoffStart, screenHandoffStart + 3.0, surfaceRadius)"),
                    "the screen-space crest must cross-fade from routed geometry instead of replacing it abruptly");
            helper.assertTrue(
                    shader.contains("pixelFootprint = min(fwidth(delta), 1.5)")
                            && shader.contains("filteredGaussian"),
                    "a distant crest must retain sub-pixel coverage instead of breaking between fragments");
            helper.assertFalse(
                    shader.contains("uniform sampler2D ArrivalFieldSampler")
                            || shader.contains("traversableSurfaceArrival"),
                    "the post shader must consume the geometric crest mask, not illuminate complete arrival-field cells");
            helper.assertFalse(
                    shader.contains("SceneDepthSampler"),
                    "the first-person mask must stay distinct from preserved world depth");
            helper.assertTrue(
                    shader.contains("surfaceWaveGate = step(0.5, LowFrequencyMeta.z)")
                            && shader.contains(
                                    "waveAmount = encodedWave * markerRange"
                                            + " * surfaceWaveGate * worldSurface"),
                    "weather and cloud alpha over the sky must not impersonate a surface-wave marker");

            String horusJson = new String(
                    horusStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            JsonArray horusConfig = JsonParser.parseString(horusJson)
                    .getAsJsonObject()
                    .getAsJsonArray("passes")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("uniforms")
                    .getAsJsonArray("HorusConfig");
            helper.assertValueEqual(
                    horusConfig.size(),
                    4,
                    "HorusConfig must allocate the complete 64-byte std140 block");
            String horusShader = new String(
                    horusShaderStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    horusShader.contains("layout(std140) uniform HorusConfig")
                            && horusShader.contains("vec4 Phase;")
                            && horusShader.contains("vec4 Gold;")
                            && horusShader.contains("vec4 Style;")
                            && horusShader.contains("vec4 Reserved;"),
                    "the Eye shader and JSON must describe the same aligned uniform block");
            helper.assertTrue(
                    horusShader.contains("paleOutline")
                            && horusShader.contains("insideAperture")
                            && horusShader.contains("closingLine"),
                    "the Eye shader must retain target response and progressive opening and closing");
            String horusSigilShader = new String(
                    horusSigilShaderStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    horusSigilShader.contains("uniform sampler2D Sampler0")
                            && horusSigilShader.contains("sigilAlpha"),
                    "the external Eye manifestation must use its dedicated masked sigil");
            var horusSigil = ImageIO.read(horusSigilTextureStream);
            helper.assertValueEqual(
                    horusSigil.getWidth(),
                    128,
                    "the Horus sigil must retain its authored pixel resolution");
            helper.assertValueEqual(
                    horusSigil.getHeight(),
                    128,
                    "the Horus sigil must retain its authored pixel resolution");
            helper.assertValueEqual(
                    horusSigil.getRGB(0, 0) >>> 24,
                    0,
                    "the Horus sigil background must remain transparent");

            String medusaJson = new String(
                    medusaStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            JsonArray medusaConfig = JsonParser.parseString(medusaJson)
                    .getAsJsonObject()
                    .getAsJsonArray("passes")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("uniforms")
                    .getAsJsonArray("MedusaConfig");
            helper.assertValueEqual(
                    medusaConfig.size(),
                    5,
                    "MedusaConfig must allocate the complete 80-byte composite block");
            String medusaShader = new String(
                    medusaShaderStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    medusaShader.contains("layout(std140) uniform MedusaConfig")
                            && medusaShader.contains("vec4 Phase;")
                            && medusaShader.contains("vec4 Venom;")
                            && medusaShader.contains("vec4 Stone;")
                            && medusaShader.contains("vec4 Grail;"),
                    "the Medusa shader and JSON must share one aligned uniform layout");
            helper.assertTrue(
                    medusaShader.contains("fracture")
                            && medusaShader.contains("coils")
                            && medusaShader.contains("impactRadius"),
                    "the Medusa gaze must retain its mineral, serpentine and impact layers");
            helper.assertTrue(
                    medusaShader.contains("echoDarkening")
                            && medusaShader.contains("Reserved.y")
                            && medusaShader.contains("waveMarkerBase")
                            && medusaShader.contains("Reserved.w")
                            && medusaShader.contains("horusGrade")
                            && medusaShader.contains("sacredCaustic"),
                    "Medusa must compose with Echo, Horus, the Grail and the protected sonar crest");
            String grailJson = new String(
                    grailStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            JsonArray grailConfig = JsonParser.parseString(grailJson)
                    .getAsJsonObject()
                    .getAsJsonArray("passes")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("uniforms")
                    .getAsJsonArray("GrailConfig");
            helper.assertValueEqual(
                    grailConfig.size(),
                    5,
                    "GrailConfig must allocate its complete 80-byte std140 block");
            String grailShader = new String(
                    grailShaderStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    grailShader.contains("layout(std140) uniform GrailConfig")
                            && grailShader.contains("stillWater")
                            && grailShader.contains("sanctifiedReflection")
                            && grailShader.contains("releaseBloom")
                            && grailShader.contains("echoDarkening")
                            && grailShader.contains("sonarWave")
                            && grailShader.contains("horusGrade"),
                    "the Grail must use one coherent still-water reflection language and retain cross-effect composition");
            helper.assertFalse(
                    grailShader.contains("waterHeight")
                            || grailShader.contains("gatherLeft")
                            || grailShader.contains("gatherRight"),
                    "the Grail must not paint detached HUD waterlines or diagonal gathering strokes");
            String philosopherJson = new String(
                    philosopherStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            JsonArray philosopherConfig =
                    JsonParser.parseString(philosopherJson)
                            .getAsJsonObject()
                            .getAsJsonArray("passes")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("uniforms")
                            .getAsJsonArray("PhilosophersStoneConfig");
            helper.assertValueEqual(
                    philosopherConfig.size(),
                    13,
                    "the Stone must allocate Medusa's temporal phase instead of collapsing the gaze to one scalar");
            helper.assertTrue(
                    philosopherJson.contains("\"sampler_name\": \"WorldDepth\""),
                    "the Stone transition must use preserved world depth instead of distorting the sky and held item");
            String philosopherShader = new String(
                    philosopherShaderStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            String philosopherShaderCompact = philosopherShader
                    .replaceAll("\\s+", " ");
            helper.assertTrue(
                    philosopherShader.contains(
                                    "layout(std140) uniform PhilosophersStoneConfig")
                            && philosopherShader.contains(
                                    "mat4 InverseProjection")
                            && philosopherShader.contains(
                                    "reconstructViewPosition")
                            && philosopherShader.contains(
                                    "radialCoordinate")
                            && philosopherShader.contains("temporalSeam")
                            && philosopherShader.contains("memoryWake")
                            && philosopherShader.contains("STONE_HEART"),
                    "the Stone shader must anchor reversible temporal condensation to world geometry");
            helper.assertTrue(
                    philosopherShader.contains("blockRelativeCenter")
                            && philosopherShader.contains("latticeOffset")
                            && philosopherShader.contains("surfaceCoordinate")
                            && philosopherShader.contains("refractionField")
                            && philosopherShader.contains("anticipationField")
                            && philosopherShader.contains("settlingField")
                            && philosopherShader.contains("localBlocks")
                            && philosopherShader.contains("disagreement")
                            && philosopherShader.contains("temporalOffset"),
                    "the Stone must use the block-centred authoritative front while distorting incompatible timelines");
            helper.assertTrue(
                    philosopherShader.contains("filamentNetwork")
                            && philosopherShader.contains("invocationVeins")
                            && philosopherShader.contains("goldHarmonic")
                            && philosopherShader.contains("innerHarmonic")
                            && philosopherShader.contains("alchemicalSparks")
                            && philosopherShader.contains(
                                    "farEarlierExposure")
                            && philosopherShader.contains(
                                    "farLaterExposure")
                            && philosopherShader.contains(
                                    "prismaticExposure"),
                    "the Stone must retain its authored alchemical invocation, five-exposure temporal shear and resolving sparks");
            helper.assertTrue(
                    philosopherShader.contains("timelinePast")
                            && philosopherShader.contains(
                                    "timelinePresent")
                            && philosopherShader.contains(
                                    "stablePast")
                            && philosopherShader.contains(
                                    "chronalHorizon"),
                    "the Stone must give past, present and their shared horizon distinct world-anchored treatments");
            helper.assertTrue(
                    philosopherShader.contains("vec4 MedusaPhase;")
                            && philosopherShader.contains("medusaChannel")
                            && philosopherShader.contains("medusaFracture")
                            && philosopherShader.contains("medusaCoils")
                            && philosopherShader.contains("medusaPupil")
                            && philosopherShader.contains("medusaImpact"),
                    "Stone priority must preserve Medusa's fractures, coils, pupil and impact animation rather than a generic green tint");
            helper.assertFalse(
                    philosopherShader.contains("temporalBoundaryMarker")
                            || philosopherShader.contains("boundaryMarkerRange")
                            || philosopherShader.contains("boundaryVeil")
                            || philosopherShader.contains(
                                    "temporalBoundaryFilaments"),
                    "the temporal anomaly must not depend on painted block markers or the discarded ghost boundary");
            helper.assertTrue(
                    philosopherShader.contains(
                                    "roundedFootprintDistance")
                            && philosopherShader.contains(
                                    "roundedFootprintIntersection")
                            && philosopherShader.contains(
                                    "chronalCurtainIntersection")
                            && philosopherShader.contains(
                                    "CURTAIN_MARGIN")
                            && philosopherShader.contains(
                                    "CURTAIN_CORNER_FACTOR")
                            && philosopherShader.contains(
                                    "CURTAIN_MAX_CORNER")
                            && philosopherShader.contains("curtainCap")
                            && philosopherShader.contains(
                                    "curtainSurfaceWeight")
                            && philosopherShader.contains(
                                    "ghostPreservingRefraction")
                            && philosopherShader.contains("chronalHorizon")
                            && philosopherShader.contains("horizonFresnel")
                            && philosopherShader.contains("horizonFilaments")
                            && philosopherShader.contains("horizonEnvelope")
                            && philosopherShader.contains("horizonOcclusion")
                            && philosopherShader.contains("persistentBoundary")
                            && philosopherShader.contains("horizonDepthVisibility")
                            && philosopherShader.contains(
                                    "cameraInsidePast")
                            && philosopherShader.contains("horizonTide")
                            && philosopherShader.contains(
                                    "horizonConvergence")
                            && philosopherShader.contains("horizonLock")
                            && philosopherShader.contains(
                                    "horizonParallax"),
                    "the temporal curtain must tightly wrap the authored footprint and retain its world-anchored surface language");
            helper.assertTrue(
                    philosopherShaderCompact.contains(
                            "float horizonOcclusion = step(0.001, horizonDistance) * horizonDepthVisibility;"),
                    "the stable and travelling temporal boundary must both obey preserved world depth");
            helper.assertFalse(
                    philosopherShader.contains("persistentBoundary * 0.82"),
                    "the stable temporal boundary must not retain an x-ray visibility bypass");
            helper.assertFalse(
                    philosopherShader.contains(
                                    "rayEllipsoidIntersection")
                            || philosopherShader.contains(
                                    "roundedVolumeIntersection")
                            || philosopherShader.contains(
                                    "roundedVolumeField")
                            || philosopherShader.contains(
                                    "ROUNDED_VOLUME_SCALE")
                            || philosopherShader.contains(
                                    "horizonTimelineExposure")
                            || philosopherShader.contains(
                                    "PhilosophersStoneBoundary")
                            || philosopherShader.contains(
                                    "TEMPORAL_BOUNDARY_MARKER"),
                    "the Stone must retain neither enclosing lenses that copy current blocks nor any discarded block-border renderer");
            int stoneTextureSamples =
                    philosopherShader.split(
                                    "texture\\(",
                                    -1)
                            .length
                            - 1;
            helper.assertTrue(
                    philosopherShader.contains(
                                    "chronalHorizon <= 0.001")
                            && philosopherShader.contains(
                                    "stableExposure = undistortedSample.rgb")
                            && stoneTextureSamples <= 10,
                    "the Stone must skip expensive temporal sampling outside both the memory and horizon while reusing the central exposure");
            helper.assertTrue(
                    philosopherShader.contains("echoDarkening")
                            && philosopherShader.contains("sonarWave")
                            && philosopherShader.contains("horusGrade")
                            && philosopherShader.contains("medusa")
                            && philosopherShader.contains("grail"),
                    "the Stone must compose every established relic and Echo grade while it owns post priority");
            helper.assertFalse(
                    philosopherShader.contains("vignette")
                            || philosopherShader.contains("screenRing")
                            || philosopherShader.contains("realityLattice")
                            || philosopherShader.contains("alchemicalSeal")
                            || philosopherShader.contains("clarified"),
                    "the Stone must not fall back to a generic border or screen-space ring");
            String stoneShader = new String(
                    medusaStoneShaderStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    stoneShader.contains("StoneSampler")
                            && stoneShader.contains("textureSize(Sampler0, 0)")
                            && stoneShader.contains("fract(texCoord0 * sourceSize / 16.0)")
                            && stoneShader.contains("source.a")
                            && stoneShader.contains("lightMapColor")
                            && stoneShader.contains("#ifdef PER_FACE_LIGHTING")
                            && stoneShader.contains("gl_FrontFacing")
                            && stoneShader.contains("faceVertexColor.a"),
                    "petrified entities must use real, consistently scaled stone while preserving silhouette alpha");
            helper.assertFalse(
                    stoneShader.contains("overlayColor"),
                    "permanent stone must not require the optional entity overlay sampler under Iris");
            helper.assertTrue(
                    stoneShader.contains("vertexPerFaceColorFront")
                            && stoneShader.contains("vertexPerFaceColorBack"),
                    "two-sided clothing, wings, markings and flat model parts need independent face lighting");
            helper.assertFalse(
                    stoneShader.contains("float moss")
                            || stoneShader.contains("vec3 limestone")
                            || stoneShader.contains("result *= ColorModulator"),
                    "the stone material must not inherit mob tint or replace stone with overlay colour");
            String crackShader = new String(
                    medusaCrackShaderStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    crackShader.contains("CrackSampler")
                            && crackShader.contains("fissure")
                            && crackShader.contains("source.a")
                            && crackShader.contains("#ifdef PER_FACE_LIGHTING")
                            && crackShader.contains("gl_FrontFacing"),
                    "statue mining must provide a silhouette-correct progressive crack pass");
            helper.assertFalse(
                    crackShader.contains("overlayColor"),
                    "statue cracks must not retain an unused entity overlay sampler");
            String petrifiedItem = new String(
                    petrifiedItemStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            String petrifiedBase = new String(
                    petrifiedBaseStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    petrifiedItem.contains("\"type\": \"minecraft:special\"")
                            && petrifiedItem.contains(
                                    "\"type\": \"echoes_show_the_past:petrified_mob\""),
                    "petrified creature stacks must invoke their entity-backed special renderer");
            helper.assertTrue(
                    petrifiedBase.contains("\"gui\"")
                            && petrifiedBase.contains("\"ground\"")
                            && petrifiedBase.contains("\"fixed\"")
                            && petrifiedBase.contains("\"firstperson_righthand\"")
                            && petrifiedBase.contains("\"thirdperson_righthand\""),
                    "the 3D statue must be authored for inventory, world, frame and both hand views");
            String english = new String(
                    englishStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            String spanish = new String(
                    spanishStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    english.contains(
                                    "\"tooltip.echoes_show_the_past.eye_of_horus_recharge\"")
                            && english.contains(
                                    "\"tooltip.echoes_show_the_past.holy_grail_recharge\"")
                            && english.contains(
                                    "\"tooltip.echoes_show_the_past.relic_cancel\"")
                            && english.contains(
                                    "\"tooltip.echoes_show_the_past.low_frequency_console\"")
                            && english.contains(
                                    "\"tooltip.echoes_show_the_past.range_coil\"")
                            && english.contains(
                                    "\"tooltip.echoes_show_the_past.directional_matrix\"")
                            && english.contains(
                                    "\"tooltip.echoes_show_the_past.cycle_regulator\"")
                            && english.contains(
                                    "\"tooltip.echoes_show_the_past.harmonic_decoder\"")
                            && english.contains(
                                    "\"tooltip.echoes_show_the_past.harmonic_key\"")
                            && spanish.contains(
                                    "\"tooltip.echoes_show_the_past.eye_of_horus_recharge\"")
                            && spanish.contains(
                                    "\"tooltip.echoes_show_the_past.holy_grail_recharge\"")
                            && spanish.contains(
                                    "\"tooltip.echoes_show_the_past.relic_cancel\"")
                            && spanish.contains(
                                    "\"tooltip.echoes_show_the_past.low_frequency_console\"")
                            && spanish.contains(
                                    "\"tooltip.echoes_show_the_past.range_coil\"")
                            && spanish.contains(
                                    "\"tooltip.echoes_show_the_past.harmonic_key\""),
                    "Horus, Grail, dismiss, resonator console and module tooltips must exist in both languages");
            String mixins = new String(
                    mixinStream.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    mixins.contains("\"client.SubmitNodeCollectionMixin\""),
                    "petrification must cover every submitted entity model layer, not only the parent body");
            helper.assertTrue(
                    mixins.contains("\"client.SodiumLevelSliceMixin\""),
                    "remembered-air transparency must suppress Sodium's original opaque chunk mesh as well as vanilla's");
            helper.assertTrue(
                    sodiumMixinClassStream != null,
                    "the optional Sodium compatibility mixin must be packaged");
            helper.assertTrue(
                    mixins.contains("\"client.ItemCutoutSamplerCompatMixin\""),
                    "the Iris item-cutout overlay sampler compatibility mixin must be registered");
            helper.assertTrue(
                    itemCutoutCompatMixinClassStream != null,
                    "the Iris item-cutout overlay sampler compatibility mixin must be packaged");
            helper.assertTrue(
                    medusaRenderTypesClassStream != null
                            && petrifiedTextureCacheClassStream != null
                            && submitNodeCollectionMixinClassStream != null
                            && ghostOrderedSubmitNodeCollectorClassStream != null,
                    "the shader-compatible petrified texture material must be packaged");
            helper.assertTrue(
                    echoRenderTypesClassStream != null
                            && clientEchoRendererClassStream != null
                            && clientTemplateProjectionClassStream != null,
                    "the shader-compatible remembered-air replacement route must be packaged");
            String echoRenderTypesBytecode = new String(
                    echoRenderTypesClassStream.readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            String clientEchoRendererBytecode = new String(
                    clientEchoRendererClassStream.readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            helper.assertTrue(
                    echoPastLightClassStream != null,
                    "the cached Past Echo light field must be packaged");
            String echoPastLightBytecode = new String(
                    echoPastLightClassStream.readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            helper.assertTrue(
                    echoPastLightBytecode.contains("IntArrayFIFOQueue")
                            && !echoPastLightBytecode.contains("ArrayDeque"),
                    "Past Echo light must propagate through primitive dense storage instead of allocating positions and hash entries for every step");
            helper.assertTrue(
                    clientEchoStateClassStream != null
                            && clientEchoArrivalFieldClassStream != null
                            && echoesClientClassStream != null,
                    "the hitch-free Past Echo preparation path must be packaged");
            String clientEchoStateBytecode = new String(
                    clientEchoStateClassStream.readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            String clientEchoArrivalFieldBytecode = new String(
                    clientEchoArrivalFieldClassStream.readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            String echoesClientBytecode = new String(
                    echoesClientClassStream.readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            helper.assertTrue(
                    clientEchoStateBytecode.contains("sharedLightSections")
                            && clientEchoStateBytecode.contains("markSectionsDirty")
                            && clientEchoArrivalFieldBytecode.contains("prepareRuntime")
                            && echoPastLightBytecode.contains("prepareRuntime")
                            && echoesClientBytecode.contains("prepareRuntime"),
                    "activation and dismissal must reuse bounded light sections and prewarm routing instead of paying first-use allocation and JIT costs during play");
            helper.assertTrue(
                    echoRenderTypesBytecode.contains("PRESENT_SURFACE")
                            && echoRenderTypesBytecode.contains("entityTranslucent")
                            && (clientEchoRendererBytecode.contains("PRESENT_SURFACE")
                                    || (clientEchoRendererBytecode.contains("submitGhostBatch")
                                            && clientEchoRendererBytecode.contains(
                                                    "REMEMBERED_SURFACE"))),
                    "remembered-air present blocks must use an Iris-recognized vanilla translucent entity route");
            helper.assertTrue(
                    echoRenderTypesBytecode.contains("REMEMBERED_SURFACE")
                            && clientEchoRendererBytecode.contains("REMEMBERED_SURFACE")
                            && !echoRenderTypesBytecode.contains("PAST_SURFACE_PIPELINE")
                            && !echoRenderTypesBytecode.contains("PAST_SURFACE_INSIDE_PIPELINE")
                            && !echoRenderTypesBytecode.contains("PAST_SURFACE_PLANE_PIPELINE")
                            && !echoRenderTypesBytecode.contains("core/ghost_surface"),
                    "missing and replaced remembered blocks must use an Iris-recognized vanilla translucent entity route");
            String clientTemplateProjectionBytecode = new String(
                    clientTemplateProjectionClassStream.readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            helper.assertTrue(
                    clientEchoStateBytecode.contains("pendingPreparation")
                            && clientEchoStateBytecode.contains("advancePreparation")
                            && clientEchoStateBytecode.contains("PreparationPhase")
                            && clientEchoStateBytecode.contains(
                                    "PREPARATION_FRAME_BUDGET_NANOS")
                            && clientEchoStateBytecode.contains("budgetMs")
                            && clientEchoStateBytecode.contains(
                                    "advanceOutgoingSurfaces")
                            && clientEchoArrivalFieldBytecode.contains("Preparation")
                            && echoPastLightBytecode.contains("IncrementalPropagation")
                            && clientEchoRendererBytecode.contains(
                                    "isPreparationPending"),
                    "the first Past Echo click must schedule budgeted cooperative preparation instead of rebuilding every cache in one render frame");
            helper.assertTrue(
                    clientTemplateProjectionBytecode.contains("preloadStaticTemplates")
                            && clientTemplateProjectionBytecode.contains("clearCache")
                            && clientTemplateProjectionBytecode.contains(
                                    "advanceWaveTilePreparation")
                            && clientTemplateProjectionBytecode.contains(
                                    "advanceEntityCapture")
                            && echoesClientBytecode.contains("clientLogin"),
                    "authored template decoding, indexing and wave meshing must move from the first click into world/resource loading");
            helper.assertTrue(
                    clientTemplateProjectionBytecode.contains("visiblePresentModels")
                            && clientTemplateProjectionBytecode.contains("presentOccluderDistances")
                            && clientTemplateProjectionBytecode.contains("invalidatePresentSection"),
                    "large authored memories must stream present blocks over historical air by visible section instead of truncating them to the local acoustic sphere");
            helper.assertTrue(
                    clientTemplateProjectionBytecode.contains("registerReplacedOccluders")
                            && clientTemplateProjectionBytecode.contains("removeRememberedSection")
                            && clientTemplateProjectionBytecode.contains("REPLACED")
                            && clientTemplateProjectionBytecode.contains("seedFadeOccluders")
                            && clientEchoStateBytecode.contains("EchoOccluderDistances"),
                    "far replaced and added blocks must hide their present chunk mesh from a pulse-lifetime fade seed");
            helper.assertTrue(
                    clientEchoRendererBytecode.contains("submitGhostBatch")
                            && !clientEchoRendererBytecode.contains("submitGhostPass")
                            && !clientEchoRendererBytecode.contains("lightForGhost")
                            && clientEchoRendererBytecode.contains("travelDistance")
                            && clientEchoRendererBytecode.contains("preparedQuads"),
                    "a complete Past Echo must batch block geometry and reuse baked distance, light, tint and face data instead of rebuilding them per frame");
            String medusaRenderTypesBytecode = new String(
                    medusaRenderTypesClassStream.readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            helper.assertTrue(
                    medusaRenderTypesBytecode.contains("PetrifiedTextureCache")
                            && medusaRenderTypesBytecode.contains("entityCutout")
                            && medusaRenderTypesBytecode.contains("entityTranslucent"),
                    "petrified creatures must use Iris-recognized vanilla entity pipelines in world, hand and inventory contexts");
            String petrifiedTextureCacheBytecode = new String(
                    petrifiedTextureCacheClassStream.readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            helper.assertTrue(
                    petrifiedTextureCacheBytecode.contains("DynamicTexture")
                            && petrifiedTextureCacheBytecode.contains("TextureContents")
                            && petrifiedTextureCacheBytecode.contains("composePixel")
                            && petrifiedTextureCacheBytecode.contains("getPixels"),
                    "the Iris-compatible material must preserve static and downloaded-skin alpha in a cached stone texture");
            String submitNodeCollectionMixinBytecode = new String(
                    submitNodeCollectionMixinClassStream.readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            helper.assertTrue(
                    submitNodeCollectionMixinBytecode.contains("Redirect")
                            && submitNodeCollectionMixinBytecode.contains(
                                    "ModelFeatureRenderer$Storage"),
                    "a finished statue must replace its normal model pass instead of relying on translucent ordering");
            helper.assertTrue(
                    submitNodeCollectionMixinBytecode.contains("OverlayTexture")
                            && submitNodeCollectionMixinBytecode.contains("NO_OVERLAY"),
                    "petrified material passes must suppress the living entity hurt overlay without affecting ordinary creatures");
            String ghostOrderedSubmitNodeCollectorBytecode = new String(
                    ghostOrderedSubmitNodeCollectorClassStream.readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            helper.assertTrue(
                    medusaRenderTypesBytecode.contains("isEmissiveLayer")
                            && medusaRenderTypesBytecode.contains("EYES")
                            && medusaRenderTypesBytecode.contains(
                                    "ENTITY_TRANSLUCENT_EMISSIVE")
                            && submitNodeCollectionMixinBytecode.contains(
                                    "isEmissiveLayer")
                            && ghostOrderedSubmitNodeCollectorBytecode.contains(
                                    "isEmissiveLayer")
                            && ghostOrderedSubmitNodeCollectorBytecode.contains(
                                    "PERMANENT"),
                    "fully petrified eyes and other emissive creature layers must be removed before shaderpacks can relight them");
            String itemCutoutCompatBytecode = new String(
                    itemCutoutCompatMixinClassStream.readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            helper.assertTrue(
                    itemCutoutCompatBytecode.contains("pipeline/item_cutout")
                            && itemCutoutCompatBytecode.contains("pipeline/item_translucent")
                            && itemCutoutCompatBytecode.contains("Sampler1")
                            && itemCutoutCompatBytecode.contains("overlayTexture"),
                    "Iris item cutout and translucent programs must bind Minecraft's real overlay texture");
            String sodiumMixinBytecode = new String(
                    sodiumMixinClassStream.readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            helper.assertTrue(
                    sodiumMixinBytecode.contains("getBrightness")
                            && sodiumMixinBytecode.contains("combinedBlockLight")
                            && sodiumMixinBytecode.contains("combinedSkyLight"),
                    "Sodium must sample the shared past/present block and sky light fields instead of retaining the live solid block's shadow");
            helper.succeed();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect the echo post chain", exception);
        }
    }

    private static void lowFrequencySelection(GameTestHelper helper) {
        BlockPos sourceBlock = helper.absolutePos(new BlockPos(1, 2, 2));
        Vec3 source = sourceBlock.getCenter();
        BlockPos nearest = sourceBlock.east(4);
        BlockPos farther = sourceBlock.east(8);
        helper.getLevel().setBlock(
                nearest,
                EchoesShowThePast.ECHO_PEDESTAL.get()
                        .defaultBlockState()
                        .setValue(EchoPedestalBlock.SPENT, true),
                3);
        helper.getLevel().setBlock(
                farther,
                EchoesShowThePast.ECHO_PEDESTAL.get().defaultBlockState(),
                3);
        helper.getLevel().setBlock(nearest.west(), Blocks.WHITE_WOOL.defaultBlockState(), 3);

        EchoPedestalBlockEntity nearestPedestal =
                (EchoPedestalBlockEntity) helper.getLevel().getBlockEntity(nearest);
        EchoPedestalBlockEntity fartherPedestal =
                (EchoPedestalBlockEntity) helper.getLevel().getBlockEntity(farther);
        nearestPedestal.setEcho(PastEchoMemory.createFragment(
                EchoRuinTemplate.createSnapshot(helper.getLevel().dimension(), nearest),
                Optional.empty()));
        fartherPedestal.setEcho(PastEchoMemory.createFragment(
                EchoRuinTemplate.createSnapshot(helper.getLevel().dimension(), farther),
                Optional.empty()));

        EchoPedestalIndex index = EchoPedestalIndex.get(helper.getLevel());
        EchoPedestalIndex.register(helper.getLevel(), nearest);
        EchoPedestalIndex.register(helper.getLevel(), farther);
        Set<BlockPos> owned = Set.of(nearest, farther);
        List<EchoPedestalIndex.Candidate> candidates = index.candidates(source, 16.0)
                .stream()
                .filter(candidate -> owned.contains(candidate.position()))
                .toList();
        helper.assertValueEqual(candidates.getFirst().position(), nearest, "the nearest occupied pedestal answers first");
        BlockPos firstAccessible = candidates.stream()
                .filter(candidate -> !index.isBlockedFrom(candidate.position(), source))
                .map(EchoPedestalIndex.Candidate::position)
                .findFirst()
                .orElseThrow();
        helper.assertValueEqual(firstAccessible, farther, "blocked nearest pedestal must yield to the next");
        helper.assertValueEqual(
                index.candidates(source, 6.0).stream()
                        .filter(candidate -> owned.contains(candidate.position()))
                        .count(),
                1L,
                "range must exclude distant receivers");
        List<EchoPedestalIndex.Candidate> relayed =
                index.candidates(
                        helper.getLevel(),
                        nearest.getCenter(),
                        16.0,
                        nearest);
        helper.assertFalse(
                relayed.stream().anyMatch(candidate -> candidate.position().equals(nearest)),
                "a redstone relay must not answer its own pulse or recursively activate itself");
        helper.assertTrue(
                relayed.stream().anyMatch(candidate -> candidate.position().equals(farther)),
                "other pedestals may answer a relay without becoming new relay emitters");

        EchoPedestalIndex.unregister(helper.getLevel(), nearest);
        EchoPedestalIndex.unregister(helper.getLevel(), farther);
        helper.succeed();
    }

    private static void lowFrequencyWoolFaces(GameTestHelper helper) {
        BlockPos pedestal = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(
                pedestal,
                EchoesShowThePast.ECHO_PEDESTAL.get().defaultBlockState(),
                3);
        EchoPedestalBlockEntity pedestalEntity =
                (EchoPedestalBlockEntity) helper.getLevel().getBlockEntity(pedestal);
        pedestalEntity.setEcho(PastEchoMemory.createFragment(
                EchoRuinTemplate.createSnapshot(helper.getLevel().dimension(), pedestal),
                Optional.empty()));
        EchoPedestalIndex.register(helper.getLevel(), pedestal);
        EchoPedestalIndex index = EchoPedestalIndex.get(helper.getLevel());

        for (Direction direction : Direction.values()) {
            for (Direction cleanup : Direction.values()) {
                helper.getLevel().setBlock(pedestal.relative(cleanup), Blocks.AIR.defaultBlockState(), 3);
            }
            helper.getLevel().setBlock(
                    pedestal.relative(direction),
                    Blocks.WHITE_WOOL.defaultBlockState(),
                    3);
            EchoPedestalIndex.refresh(helper.getLevel(), pedestal);
            Vec3 blockedOrigin = pedestal.getCenter().add(
                    direction.getStepX() * 8.0,
                    direction.getStepY() * 8.0,
                    direction.getStepZ() * 8.0);
            Vec3 openOrigin = pedestal.getCenter().add(
                    direction.getOpposite().getStepX() * 8.0,
                    direction.getOpposite().getStepY() * 8.0,
                    direction.getOpposite().getStepZ() * 8.0);
            helper.assertTrue(
                    index.isBlockedFrom(pedestal, blockedOrigin),
                    "wool must block incoming face " + direction);
            helper.assertTrue(
                    !index.isBlockedFrom(pedestal, openOrigin),
                    "wool on " + direction + " must not block the opposite face");
        }

        EchoPedestalIndex.unregister(helper.getLevel(), pedestal);
        helper.succeed();
    }

    private static void lowFrequencyIndexLifecycle(GameTestHelper helper) {
        BlockPos pedestal = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos wool = pedestal.east();
        EchoPedestalIndex index = EchoPedestalIndex.get(helper.getLevel());
        EchoPedestalIndex.unregister(helper.getLevel(), pedestal);

        helper.getLevel().setBlock(
                pedestal,
                EchoesShowThePast.ECHO_PEDESTAL.get().defaultBlockState(),
                3);
        helper.assertFalse(index.contains(pedestal), "an empty pedestal must not answer sonar");
        helper.assertTrue(index.knows(pedestal), "the index must remember an empty pedestal as silent");
        helper.assertTrue(
                helper.getLevel().getBlockEntity(pedestal) instanceof EchoPedestalBlockEntity,
                "the pedestal must own a synchronized item holder");
        EchoPedestalBlockEntity pedestalEntity =
                (EchoPedestalBlockEntity) helper.getLevel().getBlockEntity(pedestal);
        ItemStack sealedMemory = PastEchoMemory.createSealedVessel(
                EchoRuinTemplate.createSnapshot(helper.getLevel().dimension(), pedestal));
        pedestalEntity.setEcho(sealedMemory);
        helper.assertTrue(pedestalEntity.hasEcho(), "a sealed dungeon memory must be stored");
        helper.assertTrue(
                pedestalEntity.echo().is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get()),
                "legacy Past Echo pedestal data must migrate to a Past Fragment");
        helper.assertTrue(index.contains(pedestal), "inserting the fragment must restore resonance");
        helper.assertFalse(
                helper.getLevel().getBlockState(pedestal).getValue(EchoPedestalBlock.SPENT),
                "an occupied pedestal must expose its unspent render state");
        ItemStack removedFragment = pedestalEntity.removeEcho();
        helper.assertTrue(
                removedFragment.is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get())
                        && removedFragment.has(EchoesShowThePast.ECHO_SNAPSHOT.get()),
                "taking the floating item must return the sealed Past Fragment");
        helper.assertTrue(
                helper.getLevel().getBlockState(pedestal).getValue(EchoPedestalBlock.SPENT),
                "an empty pedestal must expose its spent render state");
        helper.assertFalse(index.contains(pedestal), "taking the fragment must stop resonance immediately");
        helper.assertTrue(index.knows(pedestal), "a silent pedestal must remain known across chunk unloads");
        helper.assertFalse(
                index.candidates(pedestal.getCenter(), 4.0).stream()
                        .anyMatch(candidate -> candidate.position().equals(pedestal)),
                "an empty pedestal must not be returned as a sonar candidate");

        pedestalEntity.setEcho(removedFragment);
        helper.assertTrue(index.contains(pedestal), "reseating the same fragment must restore resonance");

        helper.getLevel().setBlock(wool, Blocks.WHITE_WOOL.defaultBlockState(), 3);
        EchoPedestalIndex.refresh(helper.getLevel(), pedestal);
        helper.assertTrue(
                index.isBlockedFrom(pedestal, pedestal.getCenter().add(8.0, 0.0, 0.0)),
                "a neighbor change must persist the new blocked-face mask");

        helper.getLevel().setBlock(pedestal, Blocks.AIR.defaultBlockState(), 3);
        helper.assertFalse(index.contains(pedestal), "removing a pedestal must silence it");
        helper.assertTrue(index.knows(pedestal), "removed generated receivers remain tombstoned against prediction");

        helper.getLevel().setBlock(
                pedestal,
                EchoesShowThePast.ECHO_PEDESTAL.get().defaultBlockState(),
                3);
        pedestalEntity = (EchoPedestalBlockEntity) helper.getLevel().getBlockEntity(pedestal);
        pedestalEntity.setEcho(removedFragment);
        EchoPedestalIndex.unregister(helper.getLevel(), pedestal);
        helper.assertFalse(index.contains(pedestal), "test setup must clear the live entry");
        index.synchronizeChunk(helper.getLevel(), helper.getLevel().getChunkAt(pedestal));
        helper.assertTrue(index.contains(pedestal), "chunk synchronization must rediscover an existing pedestal");
        helper.assertTrue(
                index.isBlockedFrom(pedestal, pedestal.getCenter().add(8.0, 0.0, 0.0)),
                "chunk synchronization must rebuild blocked-face masks");

        var encoded = EchoPedestalIndex.TYPE.codec()
                .encodeStart(JsonOps.INSTANCE, index)
                .getOrThrow();
        EchoPedestalIndex decoded = EchoPedestalIndex.TYPE.codec()
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();
        helper.assertTrue(decoded.contains(pedestal), "the synchronized index must persist");
        helper.assertTrue(
                decoded.isBlockedFrom(pedestal, pedestal.getCenter().add(8.0, 0.0, 0.0)),
                "blocked-face masks must persist");

        pedestalEntity.removeEcho();
        var encodedSilent = EchoPedestalIndex.TYPE.codec()
                .encodeStart(JsonOps.INSTANCE, index)
                .getOrThrow();
        EchoPedestalIndex decodedSilent = EchoPedestalIndex.TYPE.codec()
                .parse(JsonOps.INSTANCE, encodedSilent)
                .getOrThrow();
        helper.assertFalse(decodedSilent.contains(pedestal), "silent state must persist across a codec round trip");
        helper.assertTrue(decodedSilent.knows(pedestal), "silent tombstones must survive a codec round trip");

        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "the Nether dimension must be available to the test server");
        helper.assertFalse(
                EchoPedestalIndex.get(nether).contains(pedestal),
                "dimension-local indexes must not expose Overworld pedestals");

        helper.getLevel().setBlock(wool, Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(pedestal, Blocks.AIR.defaultBlockState(), 3);
        EchoPedestalIndex.unregister(helper.getLevel(), pedestal);
        helper.succeed();
    }

    private static void lowFrequencyCodecsTiming(GameTestHelper helper) {
        Vec3 origin = new Vec3(10.25, 64.0, -3.75);
        LowFrequencyPulseStartPayload start =
                new LowFrequencyPulseStartPayload(42L, origin, 1_024, 64.0, 900);
        var encodedStart = LowFrequencyPulseStartPayload.CODEC
                .encodeStart(JsonOps.INSTANCE, start)
                .getOrThrow();
        LowFrequencyPulseStartPayload decodedStart = LowFrequencyPulseStartPayload.CODEC
                .parse(JsonOps.INSTANCE, encodedStart)
                .getOrThrow();
        helper.assertValueEqual(decodedStart, start, "pulse-start payload codec round trip");

        BlockPos pedestal = helper.absolutePos(new BlockPos(3, 2, 3));
        LowFrequencyPulseResultPayload result = LowFrequencyPulseResultPayload.found(42L, pedestal);
        var encodedResult = LowFrequencyPulseResultPayload.CODEC
                .encodeStart(JsonOps.INSTANCE, result)
                .getOrThrow();
        LowFrequencyPulseResultPayload decodedResult = LowFrequencyPulseResultPayload.CODEC
                .parse(JsonOps.INSTANCE, encodedResult)
                .getOrThrow();
        helper.assertValueEqual(decodedResult, result, "pulse-result payload codec round trip");
        helper.assertTrue(
                LowFrequencyPulseResultPayload.none(42L).pedestal().isEmpty(),
                "no-response payload must omit a pedestal");
        LowFrequencyPulseCancelPayload cancel =
                new LowFrequencyPulseCancelPayload(42L);
        var encodedCancel = LowFrequencyPulseCancelPayload.CODEC
                .encodeStart(JsonOps.INSTANCE, cancel)
                .getOrThrow();
        helper.assertValueEqual(
                LowFrequencyPulseCancelPayload.CODEC
                        .parse(JsonOps.INSTANCE, encodedCancel)
                        .getOrThrow(),
                cancel,
                "pulse-cancel payload codec round trip");

        helper.assertValueEqual(
                LowFrequencySonarMath.travelTicks(1_024.0, 1_024.0, 96.0),
                214,
                "long-range outbound travel is distance / speed with no compression");
        helper.assertValueEqual(
                LowFrequencySonarMath.travelTicks(128.1, 96.0),
                27,
                "travel time must round up to the next server tick");
        helper.assertValueEqual(
                LowFrequencySonarMath.radius(2.0, 96.0, 1_024.0),
                192.0,
                "pulse radius must follow configured speed");
        helper.assertValueEqual(
                LowFrequencySonarMath.radius(20.0, 96.0, 1_024.0),
                1_024.0,
                "pulse radius must clamp to configured range");
        helper.assertValueEqual(
                LowFrequencySonarMath.expandingRadius(20.0, 96.0),
                1_920.0,
                "the visual crest keeps expanding past device range");
        helper.assertValueEqual(
                LowFrequencySonarMath.rangeEdgeFade(0.0, 1_024.0),
                1.0F,
                "edge fade is full near the origin");
        helper.assertValueEqual(
                LowFrequencySonarMath.rangeEdgeFade(500.0, 1_024.0),
                1.0F,
                "edge fade stays full before the final stretch");
        helper.assertTrue(
                LowFrequencySonarMath.rangeEdgeFade(980.0, 1_024.0) > 0.0F
                        && LowFrequencySonarMath.rangeEdgeFade(980.0, 1_024.0) < 1.0F,
                "edge fade softens through the last stretch of range");
        helper.assertValueEqual(
                LowFrequencySonarMath.rangeEdgeFade(1_024.0, 1_024.0),
                0.0F,
                "the crest is gone exactly at device range");
        helper.assertValueEqual(
                LowFrequencySonarMath.radius(3.0, 96.0, 1_024.0),
                288.0,
                "the return pulse must use the same constant blocks-per-second radius");
        helper.assertValueEqual(
                LowFrequencySonarMath.visibleRange(1_024, 12),
                192.0,
                "the outbound visual must reach the minimum of device range and render distance");
        helper.assertValueEqual(
                LowFrequencySonarMath.visibleRange(128, 12),
                128.0,
                "a short configured device range must remain the visual limit");
        helper.assertFalse(
                LowFrequencySonarMath.waveIntersectsVisibleRange(0.0, 1_000.0, 192.0, 10.0),
                "a crest past render distance must vanish for a camera still at the origin");
        helper.assertTrue(
                LowFrequencySonarMath.waveIntersectsVisibleRange(1_000.0, 1_000.0, 192.0, 10.0),
                "catching the same crest must restore it from the current camera");
        helper.assertTrue(
                LowFrequencySonarMath.waveIntersectsVisibleRange(0.0, 64.0, 192.0, 10.0),
                "a near crest remains visible around the pulse origin");
        helper.assertTrue(
                LowFrequencySonarMath.waveIntersectsVisibleRange(180.0, 200.0, 192.0, 48.0),
                "prewarm must keep building surfaces just before the crest enters view");
        helper.assertTrue(
                LowFrequencySonarMath.withinCone(
                        new Vec3(0.0, 64.0, 0.0),
                        new Vec3(0.0, 0.0, 1.0),
                        new Vec3(0.0, 64.0, 32.0),
                        48.0F),
                "targets inside the aimed cone must remain selectable");
        helper.assertFalse(
                LowFrequencySonarMath.withinCone(
                        new Vec3(0.0, 64.0, 0.0),
                        new Vec3(0.0, 0.0, 1.0),
                        new Vec3(64.0, 64.0, 0.0),
                        48.0F),
                "targets outside the aimed cone must be rejected");
        helper.assertTrue(
                LowFrequencySonarMath.withinCone(
                        new Vec3(0.0, 64.0, 0.0),
                        new Vec3(0.0, 0.0, 1.0),
                        new Vec3(64.0, 64.0, 0.0),
                        360.0F),
                "a 360 degree cone stays omnidirectional");
        helper.assertTrue(
                LowFrequencySonarMath.surfaceWidthScale(96.0) >= 4.0,
                "the low-frequency crest must remain wide enough to read cleanly");
        helper.assertValueEqual(
                EchoWaveTessellation.subdivisions(4.0, 96.0),
                1,
                "a broad long-range crest must use one GPU-interpolated quad per block face");
        helper.assertValueEqual(
                EchoWaveTessellation.verticesPerFace(1, 2),
                8,
                "the long-range path must not regenerate hundreds of vertices per face");
        helper.assertValueEqual(
                EchoWaveTessellation.subdivisions(1.0, 8.0),
                9,
                "the narrow close-range crest must preserve its detailed tessellation");
        helper.assertValueEqual(
                EchoWaveTessellation.subdivisions(1.0, 32.0),
                9,
                "the narrow Past Echo crest must not lose detail as it travels from its origin");
        helper.assertValueEqual(
                EchoWaveTessellation.subdivisions(1.0, 8.0, 3_500),
                9,
                "a dense scene must preserve the classic narrow Past Echo crest");
        helper.assertValueEqual(
                EchoWaveTessellation.verticesPerFace(9, 2),
                648,
                "the classic close-range crest must retain its original surface detail");
        helper.assertValueEqual(
                LowFrequencySonarMath.travelTicks(192.0, 96.0),
                40,
                "a twelve-chunk scene must show the outbound wave for two seconds at default speed");
        int responses = LowFrequencySonarMath.recordResponse(0, false);
        responses = LowFrequencySonarMath.recordResponse(responses, true);
        responses = LowFrequencySonarMath.recordResponse(responses, true);
        helper.assertValueEqual(
                responses,
                2,
                "one scan must retain every accessible pedestal response");
        helper.assertTrue(
                LowFrequencySonarMath.shouldSendNoResponse(0),
                "a scan without receivers must report no response");
        helper.assertFalse(
                LowFrequencySonarMath.shouldSendNoResponse(responses),
                "a scan with receivers must not erase them with a final no-response packet");
        helper.assertTrue(
                LowFrequencySonarMath.isCurrentResult(42L, 42L),
                "the active pulse result must be accepted");
        helper.assertFalse(
                LowFrequencySonarMath.isCurrentResult(42L, 41L),
                "a stale pulse result must be discarded");
        helper.assertTrue(
                LowFrequencySonarMath.withinCooldown(44.95, 900),
                "the beams must remain visible through tick 899");
        helper.assertFalse(
                LowFrequencySonarMath.withinCooldown(45.0, 900),
                "the pulse state must expire exactly with its cooldown");
        helper.assertValueEqual(
                LowFrequencySonarMath.listeningDurationSeconds(512.0, 96.0),
                512.0 / 96.0 * 2.0 + 1.0,
                "listening covers the constant-speed outbound and return journey plus linger");
        helper.assertValueEqual(
                LowFrequencySonarMath.listeningDurationSeconds(1_024.0, 96.0),
                1_024.0 / 96.0 * 2.0 + 1.0,
                "listening scales linearly with device range");
        helper.assertValueEqual(
                LowFrequencySonarMath.listeningEnvelope(0.5, 36.0),
                0.5F,
                "sensory focus must fade in gradually over one second");
        helper.assertValueEqual(
                LowFrequencySonarMath.listeningEnvelope(18.0, 36.0),
                1.0F,
                "sensory focus must remain steady while responses are in flight");
        helper.assertValueEqual(
                LowFrequencySonarMath.listeningEnvelope(35.5, 36.0),
                0.5F,
                "sensory focus must fade out instead of snapping off");
        helper.assertValueEqual(
                LowFrequencySonarMath.cancellationEnvelope(0.0),
                1.0F,
                "cancelling must begin from the current visual state");
        helper.assertValueEqual(
                LowFrequencySonarMath.cancellationEnvelope(
                        LowFrequencySonarMath.CANCELLATION_FADE_SECONDS),
                0.0F,
                "cancelling must finish with a smooth bounded fade");
        helper.assertTrue(
                LowFrequencySonarMath.pedestalHintEnvelope(0.3) > 0.95F,
                "a pedestal hint must become readable quickly");
        float earlyPedestalHint = LowFrequencySonarMath.pedestalHintEnvelope(0.3);
        float middlePedestalHint = LowFrequencySonarMath.pedestalHintEnvelope(2.5);
        float latePedestalHint = LowFrequencySonarMath.pedestalHintEnvelope(4.0);
        helper.assertTrue(
                earlyPedestalHint > middlePedestalHint
                        && middlePedestalHint > latePedestalHint,
                "a pedestal hint must lose energy continuously throughout its lifetime");
        helper.assertTrue(
                LowFrequencySonarMath.pedestalHintEnvelope(4.5) > 0.0F
                        && LowFrequencySonarMath.pedestalHintEnvelope(4.5) < 0.5F,
                "a pedestal hint must be fading before its five-second limit");
        helper.assertValueEqual(
                LowFrequencySonarMath.pedestalHintEnvelope(5.0),
                0.0F,
                "a pedestal hint must never remain beyond five seconds");
        helper.assertValueEqual(
                LowFrequencySonarMath.beamDisplayDistance(64.0, 128.0),
                64.0,
                "a nearby pedestal beam must remain at its exact world position");
        double distantBeam =
                LowFrequencySonarMath.beamDisplayDistance(1_024.0, 128.0);
        helper.assertTrue(
                distantBeam > 0.0 && distantBeam < 128.0,
                "an off-screen pedestal must retain a visible horizon marker");
        float nearSignal = LowFrequencySonarMath.signalAttenuation(0.0, 1_024.0);
        float distantSignal = LowFrequencySonarMath.signalAttenuation(1_024.0, 1_024.0);
        helper.assertTrue(
                nearSignal > distantSignal && distantSignal >= 0.2F,
                "distance must soften long-range waves without deleting their readable core");
        long placementSeed = 0x5EEDBEEFL;
        for (EchoSiteType site : EchoSiteType.generatedSites()) {
            net.minecraft.world.level.ChunkPos predicted =
                    EchoSitePlacement.candidateChunk(
                                    helper.getLevel(),
                                    site,
                                    placementSeed,
                                    -2,
                                    3)
                            .orElseThrow();
            helper.assertTrue(
                    EchoSitePlacement.isCandidate(
                            helper.getLevel(),
                            site,
                            placementSeed,
                            predicted.x(),
                            predicted.z()),
                    "the locator and world generator must agree for " + site.id());
        }
        assertAuthoredSiteResources(helper);

        helper.getLevel().setBlock(
                pedestal,
                EchoesShowThePast.ECHO_PEDESTAL.get().defaultBlockState(),
                3);
        EchoPedestalBlockEntity pedestalEntity =
                (EchoPedestalBlockEntity) helper.getLevel().getBlockEntity(pedestal);
        pedestalEntity.setEcho(PastEchoMemory.createFragment(
                EchoRuinTemplate.createSnapshot(helper.getLevel().dimension(), pedestal),
                Optional.empty()));
        EchoPedestalIndex.register(helper.getLevel(), pedestal);
        var encodedIndex = EchoPedestalIndex.TYPE.codec()
                .encodeStart(JsonOps.INSTANCE, EchoPedestalIndex.get(helper.getLevel()))
                .getOrThrow();
        EchoPedestalIndex decodedIndex = EchoPedestalIndex.TYPE.codec()
                .parse(JsonOps.INSTANCE, encodedIndex)
                .getOrThrow();
        helper.assertTrue(decodedIndex.contains(pedestal), "pedestal index must survive codec round trip");
        EchoPedestalIndex.unregister(helper.getLevel(), pedestal);
        helper.succeed();
    }

    private static void progressionCodecs(GameTestHelper helper) {
        ResonatorLoadout wide = new ResonatorLoadout(
                List.of(
                        ResonatorModule.RANGE_COIL,
                        ResonatorModule.RANGE_COIL,
                        ResonatorModule.RANGE_COIL),
                false);
        helper.assertValueEqual(wide.effectiveRange(), 2_048, "range coils must stack by 512 blocks");
        ResonatorLoadout directional = new ResonatorLoadout(
                List.of(
                        ResonatorModule.DIRECTIONAL_MATRIX,
                        ResonatorModule.DIRECTIONAL_MATRIX,
                        ResonatorModule.DIRECTIONAL_MATRIX),
                true);
        helper.assertValueEqual(directional.effectiveRange(), 6_656, "three matrices must reach 6656 blocks");
        helper.assertValueEqual(directional.coneDegrees(), 20.0F, "three matrices must narrow the cone to 20 degrees");
        helper.assertTrue(
                directional.effectiveDirectionalMode(),
                "installed matrices default into directional mode");
        ResonatorLoadout wideWithMatrix = directional.withDirectionalMode(false);
        helper.assertFalse(
                wideWithMatrix.effectiveDirectionalMode(),
                "the console can still force a wide field while matrices are installed");
        ResonatorLoadout regulated = new ResonatorLoadout(
                List.of(
                        ResonatorModule.CYCLE_REGULATOR,
                        ResonatorModule.CYCLE_REGULATOR,
                        ResonatorModule.CYCLE_REGULATOR),
                false);
        double baseSpeed = EchoesConfig.LOW_FREQUENCY_SPEED.getAsDouble();
        helper.assertValueEqual(
                baseSpeed + 3.0 * ResonatorLoadout.REGULATOR_SPEED_BONUS,
                regulated.effectiveSpeed(baseSpeed),
                "three regulators must raise pulse speed by 72 blocks per second");
        helper.assertValueEqual(
                LowFrequencySonarMath.listeningTicks(
                        regulated.effectiveRange(),
                        regulated.effectiveSpeed(baseSpeed)),
                regulated.cooldownTicks(),
                "item cooldown must equal the physical listening window");
        ResonatorLoadout coilAndMatrix = new ResonatorLoadout(
                List.of(ResonatorModule.DIRECTIONAL_MATRIX, ResonatorModule.RANGE_COIL),
                true);
        helper.assertValueEqual(coilAndMatrix.effectiveRange(), 3_072, "coils must also extend directional mode");
        ResonatorLoadout stock = ResonatorLoadout.EMPTY;
        helper.assertValueEqual(stock.effectiveRange(), 512, "stock resonator reach is 512 blocks");
        helper.assertValueEqual(
                LowFrequencySonarMath.listeningTicks(512, baseSpeed),
                stock.cooldownTicks(),
                "stock cooldown is the listening window for base range and speed");

        var encoded = ResonatorLoadout.CODEC.encodeStart(JsonOps.INSTANCE, coilAndMatrix).getOrThrow();
        helper.assertValueEqual(
                ResonatorLoadout.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(),
                coilAndMatrix,
                "resonator loadouts must survive their component codec");
        helper.assertTrue(
                ResonatorLoadout.CODEC.encodeStart(
                                JsonOps.INSTANCE,
                                new ResonatorLoadout(
                                        List.of(
                                                ResonatorModule.HARMONIC_DECODER,
                                                ResonatorModule.HARMONIC_DECODER),
                                        false))
                        .isError(),
                "a decoder may not be duplicated");

        ResonanceKnowledge knowledge = ResonanceKnowledge.EMPTY
                .discover(EchoSiteType.HORUS)
                .setColor(EchoSiteType.HORUS.id(), ResonanceColor.ROSE)
                .toggleIgnored(EchoSiteType.HORUS.id())
                .claimRelic(EchoSiteType.HORUS.id());
        helper.assertTrue(
                knowledge.ignored().contains(EchoSiteType.HORUS.id()),
                "toggleIgnored must mute a discovered site");
        ResonanceKnowledge unmuted = knowledge.setIgnored(List.of(EchoSiteType.HORUS.id()), false);
        helper.assertFalse(
                unmuted.ignored().contains(EchoSiteType.HORUS.id()),
                "setIgnored(false) must unmute every listed site");
        ResonanceKnowledge mutedAll = unmuted
                .discover(EchoSiteType.MEDUSA)
                .setIgnored(List.of(EchoSiteType.HORUS.id(), EchoSiteType.MEDUSA.id()), true);
        helper.assertTrue(
                mutedAll.ignored().contains(EchoSiteType.HORUS.id())
                        && mutedAll.ignored().contains(EchoSiteType.MEDUSA.id()),
                "setIgnored(true) must mute every listed site");
        helper.assertFalse(
                mutedAll.anyListening(List.of(EchoSiteType.HORUS.id(), EchoSiteType.MEDUSA.id())),
                "anyListening must be false when every listed site is muted");
        var encodedKnowledge = ResonanceKnowledge.CODEC
                .encodeStart(JsonOps.INSTANCE, knowledge)
                .getOrThrow();
        ResonanceKnowledge decodedKnowledge = ResonanceKnowledge.CODEC
                .parse(JsonOps.INSTANCE, encodedKnowledge)
                .getOrThrow();
        helper.assertValueEqual(decodedKnowledge, knowledge, "personal knowledge must survive save and reconnect");
        helper.assertTrue(
                decodedKnowledge.ignored().contains(EchoSiteType.HORUS.id()),
                "ignored signatures must be persistent");
        helper.assertTrue(
                decodedKnowledge.claimedRelics().contains(EchoSiteType.HORUS.id()),
                "personal relic claims must be persistent");
        helper.succeed();
    }

    private static long countPetrifiedStatues(StructureTemplate template) {
        return ((StructureTemplateAccessor) (Object) template)
                .echoes$getEntityInfoList()
                .stream()
                .filter(entity -> entity.nbt
                        .getCompoundOrEmpty("neoforge:attachments")
                        .getCompoundOrEmpty("echoes_show_the_past:petrified_pose")
                        .getCompoundOrEmpty("statue")
                        .getBooleanOr("permanent", false))
                .count();
    }

    private static List<StructureTemplate.StructureEntityInfo> authoredDungeonPickups(
            StructureTemplate template) {
        return ((StructureTemplateAccessor) (Object) template)
                .echoes$getEntityInfoList()
                .stream()
                .filter(entity -> "echoes_show_the_past:dungeon_pickup".equals(
                        entity.nbt.getStringOr("id", "")))
                .toList();
    }

    private static String authoredPickupItem(StructureTemplate.StructureEntityInfo pickup) {
        return pickup.nbt.getCompoundOrEmpty("Item").getStringOr("id", "");
    }

    /**
     * A memory stores only what its author built, so without this set an echo
     * reads every naturally generated block inside Medusa's bounds as something
     * added since, hides the seabed and the ocean and redraws them as ghosts.
     * The set must therefore stay a small, exact difference between the two
     * templates rather than drift towards the whole volume.
     */
    private static void assertMedusaAdditions(GameTestHelper helper, EchoSiteType site) {
        helper.assertTrue(
                EchoGameTests.class.getClassLoader().getResourceAsStream(
                        "assets/echoes_show_the_past/structure/sites/"
                                + "sanctuary_of_medusa_additions.nbt") != null,
                "the Medusa addition set must be packaged in client resources beside its template");
        EchoSiteAdditions additions = EchoSiteAdditions.load(
                        helper.getLevel().getServer().getResourceManager(),
                        EchoSiteAdditions.resourceFor(site.intactTemplate()))
                .orElseThrow();
        helper.assertValueEqual(
                additions.size(),
                10_642,
                "the Medusa fade seed must hold every present cell that yields to its memory");
        helper.assertTrue(
                additions.contains(49, 14, 40),
                "a cell the present template builds and the memory lacks must count as an addition");
        helper.assertFalse(
                additions.contains(0, 80, 0),
                "open water above the island belongs to the world, not to the site");
        helper.assertFalse(
                additions.contains(119, 10, 123),
                "seabed outside the ruin belongs to the world, not to the site");
        helper.assertFalse(
                additions.contains(-1, 0, 0),
                "a cell outside the memory volume can never be an addition");
    }

    private static void assertWatchtowerAdditions(GameTestHelper helper, EchoSiteType site) {
        helper.assertTrue(
                EchoGameTests.class.getClassLoader().getResourceAsStream(
                        "assets/echoes_show_the_past/structure/sites/"
                                + "medieval_watchtower_additions.nbt") != null,
                "the watchtower addition set must be packaged in client resources beside its template");
        EchoSiteAdditions additions = EchoSiteAdditions.load(
                        helper.getLevel().getServer().getResourceManager(),
                        EchoSiteAdditions.resourceFor(site.intactTemplate()))
                .orElseThrow();
        helper.assertValueEqual(
                additions.size(),
                1_731,
                "the watchtower fade seed must hold every present cell that yields to its memory");
        helper.assertFalse(
                additions.contains(16, 15, 40),
                "grass blend over intact barrier is worldgen collar, not historical-air rubble");
        helper.assertFalse(
                additions.contains(19, 12, 40),
                "dirt collar outside the past footprint must not fade as ADDED");
        helper.assertTrue(
                additions.contains(6, 12, 14),
                "stone rubble the present builds into empty past volume must still fade");
        helper.assertTrue(
                site.requiresElevatedTerrain(),
                "watchtower worldgen must keep the shared stable-land footing filter");
        helper.assertTrue(
                site.biome().isPresent()
                        && site.biome().orElseThrow().identifier().getPath().equals(
                                "watchtower_grounds"),
                "watchtower must paint a technical biome so trees do not regrow into the pad");
        helper.assertTrue(
                site.anchorHeight() == EchoSiteType.AnchorHeight.OCEAN_FLOOR,
                "watchtower must sit on solid ground rather than foliage height");
        helper.assertTrue(
                site.blendsIntoTerrain(),
                "watchtower dirt pad must ramp into nearby plains instead of leaving a cut cube");
        assertRandomSpread(
                helper,
                site,
                10,
                6,
                "watchtower must be denser than a village because plains pads fail the slope filter often");
        assertLandFootingLocateBudget(helper, site, "watchtower");
    }

    private static void assertColiseum(GameTestHelper helper, EchoSiteType site) {
        helper.assertTrue(
                EchoGameTests.class.getClassLoader().getResourceAsStream(
                        "assets/echoes_show_the_past/structure/sites/coliseum_additions.nbt") != null,
                "the coliseum addition set must be packaged in client resources beside its template");
        EchoSiteAdditions additions = EchoSiteAdditions.load(
                        helper.getLevel().getServer().getResourceManager(),
                        EchoSiteAdditions.resourceFor(site.intactTemplate()))
                .orElseThrow();
        helper.assertValueEqual(
                additions.size(),
                5_039,
                "the coliseum fade seed must hold every present cell that yields to its memory");
        helper.assertTrue(
                site.requiresElevatedTerrain(),
                "coliseum worldgen must keep the shared stable-land footing filter");
        helper.assertTrue(
                site.biome().isPresent()
                        && site.biome().orElseThrow().identifier().getPath().equals("coliseum_grounds"),
                "coliseum must paint a technical biome so cactus and trees do not grow into the arena");
        helper.assertTrue(
                site.anchorHeight() == EchoSiteType.AnchorHeight.OCEAN_FLOOR,
                "coliseum must sit on the sand/dirt arena floor rather than foliage height");
        helper.assertTrue(
                site.blendsIntoTerrain(),
                "coliseum arena must ramp into nearby sand or grass instead of leaving a cut cube");
        assertRandomSpread(
                helper,
                site,
                12,
                7,
                "coliseum must be denser than a village so flat-pad rejects still leave arenas to find");
        var coliseumStructure = helper.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .getValue(site.structure());
        var overworldBiomes = helper.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.BIOME);
        helper.assertTrue(coliseumStructure != null, "coliseum structure must be registered");
        for (String biomePath : List.of(
                "desert",
                "plains",
                "sunflower_plains",
                "savanna",
                "windswept_savanna",
                "badlands",
                "eroded_badlands")) {
            helper.assertTrue(
                    coliseumStructure.biomes().contains(overworldBiomes.getOrThrow(
                            ResourceKey.create(
                                    Registries.BIOME,
                                    Identifier.fromNamespaceAndPath("minecraft", biomePath)))),
                    "coliseum worldgen must include " + biomePath);
        }
        assertLandFootingLocateBudget(helper, site, "coliseum");
        helper.assertValueEqual(
                site.memoryMin(),
                new BlockPos(-23, -1, -19),
                "coliseum memory_min must place the arena floor on the world surface");
        helper.assertValueEqual(
                site.presentLoot().size(),
                9,
                "present coliseum barrels must all have loot tables");
        helper.assertValueEqual(
                site.pastLoot().size(),
                12,
                "intact coliseum barrels must all have loot tables");
        StructureTemplate presentTemplate = helper.getLevel()
                .getStructureManager()
                .get(site.presentTemplate())
                .orElseThrow();
        StructureTemplate intactTemplate = helper.getLevel()
                .getStructureManager()
                .get(site.intactTemplate())
                .orElseThrow();
        assertLootLandsOnEmptyBarrels(helper, site.presentLoot(), presentTemplate, "present");
        assertLootLandsOnEmptyBarrels(helper, site.pastLoot(), intactTemplate, "past");
    }

    private static void assertErechtheion(GameTestHelper helper, EchoSiteType site) {
        helper.assertTrue(
                EchoGameTests.class.getClassLoader().getResourceAsStream(
                        "assets/echoes_show_the_past/structure/sites/erechtheion_additions.nbt") != null,
                "the erechtheion addition set must be packaged in client resources beside its template");
        EchoSiteAdditions additions = EchoSiteAdditions.load(
                        helper.getLevel().getServer().getResourceManager(),
                        EchoSiteAdditions.resourceFor(site.intactTemplate()))
                .orElseThrow();
        helper.assertValueEqual(
                additions.size(),
                4_798,
                "the erechtheion fade seed must hold every present cell that yields to its memory");
        helper.assertTrue(
                site.requiresElevatedTerrain(),
                "erechtheion worldgen must keep the shared stable-land footing filter");
        helper.assertTrue(
                site.biome().isPresent()
                        && site.biome().orElseThrow().identifier().getPath().equals("erechtheion_grounds"),
                "erechtheion must paint a technical biome so trees do not grow into the temple");
        helper.assertTrue(
                site.anchorHeight() == EchoSiteType.AnchorHeight.OCEAN_FLOOR,
                "erechtheion must sit on the grass and stone porch rather than foliage height");
        helper.assertTrue(
                site.blendsIntoTerrain(),
                "erechtheion porch must ramp into nearby grass instead of leaving a cut cube");
        assertRandomSpread(
                helper,
                site,
                12,
                7,
                "erechtheion must be denser than a village so forest pads still appear while exploring");
        assertLandFootingLocateBudget(helper, site, "erechtheion");
        helper.assertValueEqual(
                site.memoryMin(),
                new BlockPos(-22, -7, -19),
                "erechtheion memory_min must place the porch six blocks above the template floor");
        helper.assertValueEqual(
                site.presentLoot().size(),
                4,
                "present erechtheion ground-floor altar barrels must have loot tables");
        helper.assertValueEqual(
                site.pastLoot().size(),
                6,
                "intact erechtheion altar barrels must have loot tables");
        StructureTemplate presentTemplate = helper.getLevel()
                .getStructureManager()
                .get(site.presentTemplate())
                .orElseThrow();
        StructureTemplate intactTemplate = helper.getLevel()
                .getStructureManager()
                .get(site.intactTemplate())
                .orElseThrow();
        assertLootLandsOnEmptyBarrels(helper, site.presentLoot(), presentTemplate, "present");
        assertLootLandsOnEmptyBarrels(helper, site.pastLoot(), intactTemplate, "past");
        helper.assertTrue(
                EchoGameTests.class.getClassLoader().getResourceAsStream(
                        "assets/echoes_show_the_past/textures/entity/easy_npc/humanoid/athenea.png") != null,
                "Athena's EasyNPC skin must be packaged for client install");
        helper.assertTrue(
                EchoGameTests.class.getClassLoader().getResourceAsStream(
                        "assets/echoes_show_the_past/textures/entity/easy_npc/humanoid_slim/cariatide.png") != null,
                "the Caryatid EasyNPC skin must be packaged for client install");
        List<CompoundTag> pastNpcs = ((StructureTemplateAccessor) (Object) intactTemplate)
                .echoes$getEntityInfoList()
                .stream()
                .map(root -> root.nbt)
                .filter(data -> data.getStringOr("id", "").startsWith("easy_npc:"))
                .toList();
        helper.assertValueEqual(
                pastNpcs.stream().filter(data ->
                        "athenea.png".equals(data.getCompoundOrEmpty("SkinData").getStringOr("Name", ""))).count(),
                1L,
                "the large Athena statue must bind the authored humanoid skin");
        helper.assertValueEqual(
                pastNpcs.stream().filter(data ->
                        "cariatide.png".equals(data.getCompoundOrEmpty("SkinData").getStringOr("Name", ""))).count(),
                6L,
                "the six Caryatids must bind the authored slim skin");
        helper.assertTrue(
                pastNpcs.stream().allMatch(data -> {
                    String file = data.getCompoundOrEmpty("SkinData").getStringOr("Name", "");
                    if (file.isEmpty()) {
                        return false;
                    }
                    int[] uuid = data.getCompoundOrEmpty("SkinData")
                            .getIntArray("UUID")
                            .orElse(new int[0]);
                    if (uuid.length != 4) {
                        return false;
                    }
                    java.util.UUID expected = java.util.UUID.nameUUIDFromBytes(
                            file.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    java.util.UUID actual = new java.util.UUID(
                            ((long) uuid[0] << 32) | (uuid[1] & 0xFFFFFFFFL),
                            ((long) uuid[2] << 32) | (uuid[3] & 0xFFFFFFFFL));
                    return expected.equals(actual);
                }),
                "EasyNPC CUSTOM skins must use the UUID derived from the PNG file name");
    }

    private static void assertAbandonedMine(GameTestHelper helper, EchoSiteType site) {
        helper.assertTrue(
                EchoGameTests.class.getClassLoader().getResourceAsStream(
                        "assets/echoes_show_the_past/structure/sites/abandoned_mine_additions.nbt") != null,
                "the abandoned mine addition set must be packaged in client resources beside its template");
        EchoSiteAdditions additions = EchoSiteAdditions.load(
                        helper.getLevel().getServer().getResourceManager(),
                        EchoSiteAdditions.resourceFor(site.intactTemplate()))
                .orElseThrow();
        helper.assertValueEqual(
                additions.size(),
                1_607,
                "the abandoned mine fade seed must hold every present cell that yields to its memory");
        helper.assertFalse(
                site.requiresElevatedTerrain(),
                "the mine stamps its own hill, so worldgen must not demand a flat 28x50 pad");
        helper.assertTrue(
                site.biome().isPresent()
                        && site.biome().orElseThrow().identifier().getPath().equals("mine_grounds"),
                "the mine must paint a technical biome so trees do not grow into the hill");
        helper.assertTrue(
                site.anchorHeight() == EchoSiteType.AnchorHeight.OCEAN_FLOOR,
                "the mine grass cap must sit on solid ground rather than foliage height");
        helper.assertTrue(
                site.blendsIntoTerrain(),
                "the mine hill must ramp into nearby grass instead of leaving a cut cube");
        helper.assertValueEqual(
                EchoSiteLandFooting.MAX_BLEND_SITE_RELIEF,
                12,
                "mine placement may follow rolling hills but must reject mountain faces");
        assertRandomSpread(
                helper,
                site,
                28,
                12,
                "abandoned mines must stay rarer than the other land ruins so forests are not carpeted with shafts");
        helper.assertValueEqual(
                site.memoryMin(),
                new BlockPos(-14, -23, -25),
                "mine memory_min must place the grass cap on the world surface");
        helper.assertValueEqual(
                site.anchorYOffset(),
                0,
                "mine grass at local Y 23 must line up with ocean_floor without an extra offset");
        helper.assertValueEqual(
                site.presentLoot().size(),
                4,
                "present mine barrels must all have loot tables");
        helper.assertValueEqual(
                site.pastLoot().size(),
                5,
                "intact mine barrels and chest minecarts must all have loot tables");
        StructureTemplate presentTemplate = helper.getLevel()
                .getStructureManager()
                .get(site.presentTemplate())
                .orElseThrow();
        StructureTemplate intactTemplate = helper.getLevel()
                .getStructureManager()
                .get(site.intactTemplate())
                .orElseThrow();
        List<StructureTemplate.StructureEntityInfo> minePickups =
                authoredDungeonPickups(presentTemplate);
        helper.assertValueEqual(
                minePickups.size(),
                1,
                "the ruined mine must keep the authored Grail fragment pickup");
        helper.assertValueEqual(
                authoredPickupItem(minePickups.getFirst()),
                "echoes_show_the_past:grail_fragment",
                "the ruined mine pickup must be the Grail fragment");
        List<StructureTemplate.StructureEntityInfo> intactMinePickups =
                authoredDungeonPickups(intactTemplate);
        helper.assertValueEqual(
                intactMinePickups.size(),
                1,
                "the intact mine must keep the authored Holy Grail pickup");
        helper.assertValueEqual(
                authoredPickupItem(intactMinePickups.getFirst()),
                "echoes_show_the_past:holy_grail",
                "the intact mine pickup must be the Holy Grail");
        helper.assertValueEqual(
                ((StructureTemplateAccessor) (Object) intactTemplate)
                        .echoes$getEntityInfoList()
                        .size(),
                4,
                "the intact mine must keep both chest carts, the empty cart and the Holy Grail");
        assertLootLandsOnAuthoredContainers(helper, site.presentLoot(), presentTemplate, "present");
        assertLootLandsOnAuthoredContainers(helper, site.pastLoot(), intactTemplate, "past");
    }

    private static void assertEgyptianTemple(GameTestHelper helper, EchoSiteType site) {
        helper.assertTrue(
                EchoGameTests.class.getClassLoader().getResourceAsStream(
                        "assets/echoes_show_the_past/structure/sites/egyptian_temple_additions.nbt") != null,
                "the egyptian temple addition set must be packaged in client resources beside its template");
        EchoSiteAdditions additions = EchoSiteAdditions.load(
                        helper.getLevel().getServer().getResourceManager(),
                        EchoSiteAdditions.resourceFor(site.intactTemplate()))
                .orElseThrow();
        helper.assertValueEqual(
                additions.size(),
                2_425,
                "the egyptian temple fade seed must hold every present cell that yields to its memory");
        helper.assertFalse(
                site.requiresElevatedTerrain(),
                "the temple stamps its own sand pad, so worldgen must not demand a flat 56x23 dune");
        helper.assertTrue(
                site.biome().isPresent()
                        && site.biome().orElseThrow().identifier().getPath().equals(
                                "egyptian_temple_grounds"),
                "the temple must paint a technical biome so cactus does not grow into the ruin");
        helper.assertTrue(
                site.anchorHeight() == EchoSiteType.AnchorHeight.OCEAN_FLOOR,
                "the temple sand cap must sit on solid ground rather than foliage height");
        helper.assertValueEqual(
                site.memoryMin(),
                new BlockPos(-28, -29, -11),
                "temple memory_min must place the sand cap on the world surface");
        helper.assertValueEqual(
                site.anchorYOffset(),
                0,
                "temple sand at local Y 29 must line up with ocean_floor without an extra offset");
        helper.assertTrue(
                site.blendsIntoTerrain(),
                "desert dunes must ramp into the authored sand cap instead of leaving a cut cube");
        helper.assertValueEqual(
                site.presentLoot().size(),
                12,
                "present temple barrels and chests must all have loot tables");
        helper.assertValueEqual(
                site.pastLoot().size(),
                8,
                "intact temple barrels and copper chests must all have loot tables");
        var templeStructure = helper.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .getValue(site.structure());
        var overworldBiomes = helper.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.BIOME);
        helper.assertTrue(templeStructure != null, "egyptian temple structure must be registered");
        helper.assertTrue(
                templeStructure.biomes().contains(overworldBiomes.getOrThrow(
                        ResourceKey.create(
                                Registries.BIOME,
                                Identifier.fromNamespaceAndPath("minecraft", "desert")))),
                "egyptian temple worldgen must include desert");
        for (String biomePath : List.of("badlands", "eroded_badlands", "wooded_badlands")) {
            helper.assertFalse(
                    templeStructure.biomes().contains(overworldBiomes.getOrThrow(
                            ResourceKey.create(
                                    Registries.BIOME,
                                    Identifier.fromNamespaceAndPath("minecraft", biomePath)))),
                    "egyptian temple must not cube-cut " + biomePath + " mesas");
        }
        helper.assertValueEqual(
                EchoSiteLandFooting.MAX_BLEND_SITE_RELIEF,
                12,
                "temple placement may follow dunes but must reject mountain faces");
        helper.assertValueEqual(
                EchoSiteLandFooting.MAX_STANDING_WATER,
                1,
                "a dead bush may sit on the pad; a two-block lake must not");
        helper.assertTrue(
                EchoSiteLandFooting.isDryColumn(70, 70),
                "dry sand must be accepted as a temple footing");
        helper.assertTrue(
                EchoSiteLandFooting.isDryColumn(71, 70),
                "a single dead bush must not reject a desert pad");
        helper.assertFalse(
                EchoSiteLandFooting.isDryColumn(73, 70),
                "a three-block lake must not seat the temple on the lake bed");
        helper.assertFalse(
                EchoSiteLandFooting.isDryColumn(64, 58),
                "a flooded shoreline must not count as dry desert");
        assertRandomSpread(
                helper,
                site,
                10,
                5,
                "egyptian temple must be denser than a village so dry-dune filters still leave ruins to find");
        assertLandFootingLocateBudget(helper, site, "egyptian temple");
        StructureTemplate presentTemplate = helper.getLevel()
                .getStructureManager()
                .get(site.presentTemplate())
                .orElseThrow();
        StructureTemplate intactTemplate = helper.getLevel()
                .getStructureManager()
                .get(site.intactTemplate())
                .orElseThrow();
        List<StructureTemplate.StructureEntityInfo> templePickups =
                authoredDungeonPickups(presentTemplate);
        helper.assertValueEqual(
                templePickups.size(),
                1,
                "the ruined temple must keep the authored Horus fragment pickup");
        helper.assertValueEqual(
                authoredPickupItem(templePickups.getFirst()),
                "echoes_show_the_past:horus_fragment",
                "the ruined temple pickup must be the Horus fragment");
        List<StructureTemplate.StructureEntityInfo> intactTemplePickups =
                authoredDungeonPickups(intactTemplate);
        helper.assertValueEqual(
                intactTemplePickups.size(),
                1,
                "the intact temple must keep the authored Eye of Horus pickup");
        helper.assertValueEqual(
                authoredPickupItem(intactTemplePickups.getFirst()),
                "echoes_show_the_past:eye_of_horus",
                "the intact temple pickup must be the Eye of Horus");
        helper.assertValueEqual(
                ((StructureTemplateAccessor) (Object) intactTemplate)
                        .echoes$getEntityInfoList()
                        .size(),
                1,
                "the intact temple must keep the Eye of Horus and no extra present-day fragment");
        assertLootLandsOnAuthoredContainers(helper, site.presentLoot(), presentTemplate, "present");
        assertLootLandsOnAuthoredContainers(helper, site.pastLoot(), intactTemplate, "past");
    }

    private static void assertRandomSpread(
            GameTestHelper helper,
            EchoSiteType site,
            int spacing,
            int separation,
            String densityNote) {
        String path = "data/echoes_show_the_past/worldgen/structure_set/"
                + site.id().getPath()
                + ".json";
        try (InputStream setStream = EchoGameTests.class.getClassLoader().getResourceAsStream(path)) {
            helper.assertTrue(setStream != null, site.id() + " structure set must be packaged");
            JsonObject placement = JsonParser.parseString(new String(
                            setStream.readAllBytes(),
                            StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonObject("placement");
            helper.assertValueEqual(placement.get("spacing").getAsInt(), spacing, densityNote);
            helper.assertValueEqual(
                    placement.get("separation").getAsInt(),
                    separation,
                    site.id() + " pads must keep a gap so neighbouring ruins do not overlap");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static void assertLandFootingLocateBudget(
            GameTestHelper helper,
            EchoSiteType site,
            String label) {
        List<BlockPos> offsets = EchoSiteLandFooting.sampleOffsets(site);
        helper.assertTrue(
                offsets.size() <= 12,
                label + " land footing must not sample a dense noise-column grid or /locate hangs");
        helper.assertTrue(
                offsets.contains(BlockPos.ZERO),
                label + " land footing must still sample the structure origin");
        helper.assertTrue(
                offsets.contains(new BlockPos(site.memoryMin().getX(), 0, site.memoryMin().getZ())),
                label + " land footing must still sample the north-west corner");
        helper.assertTrue(
                offsets.contains(new BlockPos(site.memoryMax().getX(), 0, site.memoryMin().getZ())),
                label + " land footing must still sample the north-east corner");
        helper.assertTrue(
                offsets.contains(new BlockPos(site.memoryMin().getX(), 0, site.memoryMax().getZ())),
                label + " land footing must still sample the south-west corner");
        helper.assertTrue(
                offsets.contains(new BlockPos(site.memoryMax().getX(), 0, site.memoryMax().getZ())),
                label + " land footing must still sample the south-east corner");
    }

    private static void assertLootLandsOnEmptyBarrels(
            GameTestHelper helper,
            List<EchoSiteType.LootPlacement> placements,
            StructureTemplate template,
            String phase) {
        for (EchoSiteType.LootPlacement placement : placements) {
            StructureTemplate.StructureBlockInfo container =
                    ((StructureTemplateAccessor) (Object) template)
                            .echoes$getPalettes()
                            .getFirst()
                            .blocks()
                            .stream()
                            .filter(block -> block.pos().equals(placement.offset()))
                            .findFirst()
                            .orElse(null);
            helper.assertTrue(
                    container != null && container.state().is(Blocks.BARREL) && container.nbt() != null,
                    phase + " loot zone " + placement.offset()
                            + " must land on an authored barrel");
            helper.assertTrue(
                    container.nbt().getListOrEmpty("Items").isEmpty(),
                    phase + " loot zone " + placement.offset()
                            + " must not overwrite a hand-filled barrel");
        }
    }

    private static void assertLootLandsOnAuthoredContainers(
            GameTestHelper helper,
            List<EchoSiteType.LootPlacement> placements,
            StructureTemplate template,
            String phase) {
        for (EchoSiteType.LootPlacement placement : placements) {
            StructureTemplate.StructureBlockInfo container =
                    ((StructureTemplateAccessor) (Object) template)
                            .echoes$getPalettes()
                            .getFirst()
                            .blocks()
                            .stream()
                            .filter(block -> block.pos().equals(placement.offset()))
                            .findFirst()
                            .orElse(null);
            if (container != null
                    && container.nbt() != null
                    && isAuthoredLootContainer(container.state())) {
                helper.assertTrue(
                        container.nbt().getListOrEmpty("Items").isEmpty(),
                        phase + " loot zone " + placement.offset()
                                + " must not overwrite a hand-filled container");
                continue;
            }
            StructureTemplate.StructureEntityInfo cart =
                    ((StructureTemplateAccessor) (Object) template)
                            .echoes$getEntityInfoList()
                            .stream()
                            .filter(entity -> BlockPos.containing(entity.pos)
                                    .equals(placement.offset()))
                            .filter(entity -> "minecraft:chest_minecart".equals(
                                    entity.nbt.getStringOr("id", "")))
                            .findFirst()
                            .orElse(null);
            helper.assertTrue(
                    cart != null && cart.nbt != null,
                    phase + " loot zone " + placement.offset()
                            + " must land on an authored barrel, chest or chest minecart");
            helper.assertTrue(
                    cart.nbt.getListOrEmpty("Items").isEmpty(),
                    phase + " loot zone " + placement.offset()
                            + " must not overwrite a hand-filled chest minecart");
        }
    }

    private static boolean isAuthoredLootContainer(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return "barrel".equals(path) || path.contains("chest");
    }

    private static void authoredSites(GameTestHelper helper) {
        var structures = helper.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.STRUCTURE);
        var structureSets = helper.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.STRUCTURE_SET);
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        for (EchoSiteType site : EchoSiteType.generatedSites()) {
            helper.assertTrue(
                    structures.getOptional(site.structure()).isPresent(),
                    "/locate must see " + site.id());
            helper.assertTrue(
                    structureSets.getOptional(site.id()).isPresent(),
                    "world generation must contain a placement for " + site.id());
            helper.assertValueEqual(
                    site.blendsIntoTerrain(),
                    !site.requiresOpenOcean() && !site.underground(),
                    site.id() + " exposed land ruins must blend; islands and underground sites must not");
            EchoSnapshot reference = EchoSnapshot.templateReference(
                    helper.getLevel().dimension(),
                    anchor,
                    site.intactTemplate(),
                    site.memoryMin(),
                    site.memoryMax());
            EchoSnapshot projected = EchoTemplateResolver.resolveForProjection(
                    helper.getLevel(),
                    reference,
                    anchor.getCenter(),
                    12);
            helper.assertTrue(
                    !projected.isTemplateReference()
                            && !projected.blocks().isEmpty(),
                    "the spatial projection index must resolve "
                            + site.id());
            helper.assertTrue(
                    projected.blocks().size() <= 65_536,
                    "authored projection views must respect the 65536-block packet budget");
            helper.assertValueEqual(
                    projected.radius(),
                    12,
                    "the ambient pulse must retain the configured local radius");
            if (!site.id().equals(EchoSiteType.MEDUSA.id())) {
                EchoSnapshot resolved = EchoTemplateResolver.resolve(helper.getLevel(), reference);
                helper.assertTrue(resolved != reference && !resolved.blocks().isEmpty(),
                        "the intact NBT template must resolve for " + site.id());
                if (!site.id().equals(EchoSiteType.COLISEUM.id())
                        && !site.id().equals(EchoSiteType.ERECHTHEION.id())
                        && !site.id().equals(EchoSiteType.ABANDONED_MINE.id())
                        && !site.id().equals(EchoSiteType.EGYPTIAN_TEMPLE.id())
                        && !site.id().equals(EchoSiteType.UNKNOWN_CRYPT.id())) {
                    helper.assertValueEqual(
                            projected.blocks().size(),
                            resolved.blocks().size(),
                            "a compact authored site must not be truncated by the ambient pulse radius");
                }
                if (site.id().equals(EchoSiteType.MEDIEVAL_WATCHTOWER.id())) {
                    assertWatchtowerAdditions(helper, site);
                    EchoTemplateResolver.MaterializationFootprint intactFootprint =
                            EchoTemplateResolver.resolveForMaterialization(
                                    helper.getLevel(),
                                    reference);
                    helper.assertTrue(
                            !intactFootprint.remembered().isEmpty(),
                            "watchtower materialization must remember solids");
                    EchoTemplateResolver.MaterializationCell sample =
                            intactFootprint.remembered().getFirst();
                    BlockPos sampleOffset = sample.position().subtract(anchor);
                    EchoSnapshot revised = reference.withRevision(
                            List.of(Blocks.AIR.defaultBlockState()),
                            List.of(EchoRevisionCell.of(
                                    sampleOffset.getX(),
                                    sampleOffset.getY(),
                                    sampleOffset.getZ(),
                                    0,
                                    null)),
                            List.of(),
                            false);
                    helper.assertTrue(
                            revised.isTemplateReference(),
                            "Stone edits must keep the authored template on the Past Echo");
                    EchoTemplateResolver.MaterializationFootprint revisedFootprint =
                            EchoTemplateResolver.resolveForMaterialization(
                                    helper.getLevel(),
                                    revised);
                    helper.assertTrue(
                            revisedFootprint.remembered().stream().noneMatch(cell ->
                                    cell.position().equals(sample.position())),
                            "an air overlay tombstone must remove the edited cell from later materializations");
                    helper.assertValueEqual(
                            intactFootprint.remembered().size() - 1,
                            revisedFootprint.remembered().size(),
                            "watchtower revision overlays must be sparse, not a full concrete rewrite");
                }
                if (site.id().equals(EchoSiteType.COLISEUM.id())) {
                    assertColiseum(helper, site);
                }
                if (site.id().equals(EchoSiteType.ERECHTHEION.id())) {
                    assertErechtheion(helper, site);
                }
                if (site.id().equals(EchoSiteType.ABANDONED_MINE.id())) {
                    assertAbandonedMine(helper, site);
                }
                if (site.id().equals(EchoSiteType.EGYPTIAN_TEMPLE.id())) {
                    assertEgyptianTemple(helper, site);
                }
            } else {
                helper.assertValueEqual(
                        -16,
                        site.anchorYOffset(),
                        "Medusa must sit one block lower so its authored waterline meets sea level");
                helper.assertFalse(
                        site.blendsIntoTerrain(),
                        "Medusa is an ocean island and must not grow a land collar around the shoreline");
                assertRandomSpread(
                        helper,
                        site,
                        24,
                        12,
                        "Medusa must stay rarer than a village but not a 64-chunk ocean hunt");
                helper.assertTrue(
                        projected.blocks().stream().allMatch(block -> {
                            BlockPos offset = block.offset();
                            return Math.abs(offset.getX()) <= 12
                                    && Math.abs(offset.getY()) <= 12
                                    && Math.abs(offset.getZ()) <= 12;
                        }),
                        "a giant authored Past Echo must transfer only the local pulse window");
                EchoTemplateResolver.MaterializationFootprint stoneFootprint =
                        EchoTemplateResolver.resolveForMaterialization(
                                helper.getLevel(),
                                reference);
                helper.assertTrue(
                        stoneFootprint.remembered().size() > projected.blocks().size(),
                        "Philosopher's Stone must materialize the full Medusa footprint, not the Past Echo clip");
                helper.assertTrue(
                        stoneFootprint.worldMinimum().equals(anchor.offset(site.memoryMin()))
                                && stoneFootprint.worldMaximum().equals(anchor.offset(site.memoryMax())),
                        "Stone materialization bounds must match the authored Medusa memory box");
                StructureTemplate template = helper.getLevel()
                        .getStructureManager()
                        .get(site.intactTemplate())
                        .orElseThrow();
                InputStream clientTemplateResource = EchoGameTests.class
                        .getClassLoader()
                        .getResourceAsStream(
                                "assets/echoes_show_the_past/structure/sites/"
                                        + "sanctuary_of_medusa_intact.nbt");
                helper.assertTrue(
                        clientTemplateResource != null,
                        "the Medusa past NBT must be packaged in client resources for local Past Echo streaming");
                StructureTemplate clientSource = EchoTemplateResourceLoader.load(
                                helper.getLevel().getServer().getResourceManager(),
                                helper.getLevel().registryAccess()
                                        .lookupOrThrow(Registries.BLOCK),
                                site.intactTemplate())
                        .orElseThrow();
                helper.assertValueEqual(
                        ((StructureTemplateAccessor) (Object) clientSource)
                                .echoes$getPalettes()
                                .getFirst()
                                .blocks()
                                .size(),
                        245_607,
                        "the client-side template decoder must retain Medusa's full authored block data");
                assertMedusaAdditions(helper, site);
                StructureTemplate presentTemplate = helper.getLevel()
                        .getStructureManager()
                        .get(site.presentTemplate())
                        .orElseThrow();
                helper.assertValueEqual(
                        ((StructureTemplateAccessor) (Object) template)
                                .echoes$getEntityInfoList()
                                .size(),
                        102,
                        "the Medusa past template must retain every authored statue, decoration and the mini-boss");
                helper.assertValueEqual(
                        ((StructureTemplateAccessor) (Object) presentTemplate)
                                .echoes$getEntityInfoList()
                                .size(),
                        27,
                        "the Medusa present template must retain its petrified entities and authored pickups");
                helper.assertTrue(
                        ((StructureTemplateAccessor) (Object) template)
                                .echoes$getEntityInfoList()
                                .stream()
                                .anyMatch(entity -> "echoes_show_the_past:medusa".equals(
                                        entity.nbt.getStringOr("id", ""))),
                        "the intact sanctuary must spawn the authored Medusa mini-boss");
                List<StructureTemplate.StructureEntityInfo> medusaPickups =
                        authoredDungeonPickups(presentTemplate);
                helper.assertValueEqual(
                        medusaPickups.size(),
                        2,
                        "the ruined sanctuary must keep the Medusa fragment and the petrified head");
                Set<String> pickupItems = new HashSet<>();
                for (StructureTemplate.StructureEntityInfo pickup : medusaPickups) {
                    pickupItems.add(authoredPickupItem(pickup));
                }
                helper.assertTrue(
                        pickupItems.contains("echoes_show_the_past:medusa_fragment")
                                && pickupItems.contains("echoes_show_the_past:medusa_petrified_head"),
                        "the ruined sanctuary pickups must be the Medusa fragment and the petrified head");
                // A statue only exists as an entity, so an Axiom export that
                // silently drops one is indistinguishable from one that never
                // authored it. Pin the count the converter is allowed to write.
                helper.assertTrue(
                        site.biome().isPresent()
                                && helper.getLevel().registryAccess()
                                        .lookupOrThrow(Registries.BIOME)
                                        .get(site.biome().orElseThrow())
                                        .isPresent(),
                        "Medusa must declare a technical biome that a datapack defines");
                // Loot zones are authored as template coordinates, so a moved
                // barrel would otherwise silently stop producing loot.
                helper.assertTrue(!site.presentLoot().isEmpty(), "Medusa must assign its authored loot zones");
                for (EchoSiteType.LootPlacement placement : site.presentLoot()) {
                    StructureTemplate.StructureBlockInfo container =
                            ((StructureTemplateAccessor) (Object) presentTemplate)
                                    .echoes$getPalettes()
                                    .getFirst()
                                    .blocks()
                                    .stream()
                                    .filter(block -> block.pos().equals(placement.offset()))
                                    .findFirst()
                                    .orElse(null);
                    helper.assertTrue(
                            container != null && container.nbt() != null,
                            "Medusa loot zone " + placement.offset()
                                    + " must land on an authored container");
                    helper.assertTrue(
                            container.nbt().getListOrEmpty("Items").isEmpty(),
                            "Medusa loot zone " + placement.offset()
                                    + " must not overwrite a hand-filled container");
                }
                helper.assertValueEqual(
                        countPetrifiedStatues(template),
                        98L,
                        "the Medusa past template must keep every authored statue and its pose");
                helper.assertValueEqual(
                        countPetrifiedStatues(presentTemplate),
                        25L,
                        "the Medusa present template must keep every authored statue and its pose");
                List<StructureTemplate.StructureEntityInfo> itemFrames =
                        ((StructureTemplateAccessor) (Object) template)
                                .echoes$getEntityInfoList()
                                .stream()
                                .filter(entity -> "minecraft:item_frame".equals(
                                        entity.nbt.getStringOr("id", "")))
                                .toList();
                helper.assertValueEqual(
                        2,
                        itemFrames.size(),
                        "the Medusa past template must retain its two item frames");
                helper.assertValueEqual(
                        presentTemplate.getSize().getX(),
                        120,
                        "the converted Medusa template width must remain authored");
                helper.assertValueEqual(
                        presentTemplate.getSize().getY(),
                        83,
                        "the converted Medusa template height must remain authored");
                helper.assertValueEqual(
                        presentTemplate.getSize().getZ(),
                        124,
                        "the converted Medusa template depth must remain authored");
                long carveBlocks = ((StructureTemplateAccessor) (Object) presentTemplate)
                        .echoes$getPalettes()
                        .getFirst()
                        .blocks()
                        .stream()
                        .filter(block -> block.state().is(Blocks.BARRIER))
                        .count();
                helper.assertTrue(
                        carveBlocks >= 3_840L,
                        "the present Medusa template must retain the dry-cave mask");
                // The sanctum behind the pedestal is sealed, but a shaft above
                // it crosses sea level. A carve mask that treats the waterline
                // as open sea reads that shaft as an entrance and floods the
                // whole chamber, so pin a cell deep inside it.
                helper.assertTrue(
                        ((StructureTemplateAccessor) (Object) presentTemplate)
                                .echoes$getPalettes()
                                .getFirst()
                                .blocks()
                                .stream()
                                .anyMatch(block -> block.pos().equals(new BlockPos(81, 34, 52))
                                        && block.state().is(Blocks.BARRIER)),
                        "the sealed Medusa chamber must stay dry even though a shaft above it crosses sea level");
                // Dips in the island surface sit below sea level and are open
                // to the sky, so no amount of sealing makes them enclosed. They
                // are still inside the island outline, where the only water
                // that belongs is the water the author drew.
                for (BlockPos dip : List.of(new BlockPos(70, 42, 58), new BlockPos(52, 42, 85))) {
                    helper.assertTrue(
                            ((StructureTemplateAccessor) (Object) presentTemplate)
                                    .echoes$getPalettes()
                                    .getFirst()
                                    .blocks()
                                    .stream()
                                    .anyMatch(block -> block.pos().equals(dip)
                                            && block.state().is(Blocks.BARRIER)),
                            "the Medusa surface dip at " + dip + " must not fill with sea water");
                }
                // The underground aquifer is authored water, not a copying
                // artefact: the converter used to delete it and the pool under
                // the island vanished from the generated site.
                long authoredWater = ((StructureTemplateAccessor) (Object) presentTemplate)
                        .echoes$getPalettes()
                        .getFirst()
                        .blocks()
                        .stream()
                        .filter(block -> block.state().is(Blocks.WATER))
                        .count();
                helper.assertValueEqual(
                        authoredWater,
                        136L,
                        "the present Medusa template must keep the authored underground aquifer");
                StructureTemplate.StructureBlockInfo carve =
                        ((StructureTemplateAccessor) (Object) presentTemplate)
                                .echoes$getPalettes()
                                .getFirst()
                                .blocks()
                                .stream()
                                .filter(block -> block.state().is(Blocks.BARRIER))
                                .findFirst()
                                .orElseThrow();
                BlockPos dryCavityTarget = helper.absolutePos(new BlockPos(40, 2, 2));
                helper.getLevel().setBlock(
                        dryCavityTarget,
                        Blocks.WATER.defaultBlockState(),
                        3);
                BlockPos templateOrigin = dryCavityTarget.subtract(carve.pos());
                presentTemplate.placeInWorld(
                        helper.getLevel(),
                        templateOrigin,
                        templateOrigin,
                        new StructurePlaceSettings()
                                .setIgnoreEntities(true)
                                .setBoundingBox(new BoundingBox(dryCavityTarget))
                                .addProcessor(BarrierToAirProcessor.INSTANCE),
                        RandomSource.create(0L),
                        3);
                helper.assertTrue(
                        helper.getLevel().getBlockState(dryCavityTarget).isAir(),
                        "a Medusa cave carve must replace ocean water with air");
                EchoSnapshot bounded = EchoTemplateResolver.resolve(helper.getLevel(), reference);
                helper.assertTrue(
                        bounded.blocks().size() <= EchoProjectionBudget.MAX_NETWORK_BLOCKS,
                        "a giant authored site must resolve as a bounded compact view");
                helper.assertTrue(
                        EchoProjectionManager.clientSnapshot(reference, projected)
                                .isTemplateReference(),
                        "a giant authored Past Echo must send its lightweight template reference to the client");
                EchoWaveVolume localClientVolume = EchoWaveVolume.aroundPlayer(
                        reference,
                        anchor.getCenter(),
                        12,
                        false);
                helper.assertTrue(
                        localClientVolume.boundingCellCount() < 96L * 96L * 96L,
                        "the full Medusa template must keep the client occlusion grid inside its safe axis budget");
                helper.assertTrue(
                        EchoProjectionBudget.MAX_CACHED_TEMPLATE_MODELS
                                >= EchoProjectionBudget.MAX_VISIBLE_GHOST_MODELS * 6,
                        "a template stream must retain multiple complete visible frames instead of evicting visible sections");
                helper.assertTrue(
                        !projected.entities().isEmpty(),
                        "the local Medusa Past Echo must project nearby static entities");
            }
        }
        helper.assertTrue(
                structures.getOptional(EchoSiteType.LEGACY_RUIN.structure()).isEmpty(),
                "retired test ruins must not register as worldgen structures");
        helper.assertTrue(
                structureSets.getOptional(Identifier.fromNamespaceAndPath(
                                EchoesShowThePast.MOD_ID,
                                "echo_ruins"))
                        .isEmpty(),
                "retired test ruins must not appear in structure sets");
        helper.assertTrue(
                structures.getOptional(EchoSiteType.HORUS.structure()).isEmpty(),
                "retired Horus test site must not register as a worldgen structure");
        helper.assertTrue(
                structures.getOptional(EchoSiteType.GRAIL.structure()).isEmpty(),
                "retired Grail test site must not register as a worldgen structure");
        helper.assertTrue(
                structures.getOptional(EchoSiteType.ENCLAVE.structure()).isEmpty(),
                "retired Enclave test site must not register as a worldgen structure");
        helper.succeed();
    }

    private static void unknownCryptAccess(GameTestHelper helper) {
        EchoSiteType site = EchoSiteType.byId(EchoSiteType.UNKNOWN_CRYPT.id());
        helper.assertTrue(site != null, "the Unknown crypt site must be installed from its manifest");
        helper.assertTrue(site.generated(), "the Unknown crypt must participate in world generation");
        helper.assertTrue(site.underground(), "the crypt's deepslate anchor must classify it as underground");
        helper.assertFalse(site.blendsIntoTerrain(), "a buried crypt must not flatten the terrain above it");
        helper.assertValueEqual(
                site.anchorHeight(),
                EchoSiteType.AnchorHeight.DEEP_SLATE,
                "the crypt body must target the deepslate layer instead of sitting just under grass");
        helper.assertValueEqual(site.anchorYOffset(), 0, "deepslate placement does not need an extra terrain offset");
        helper.assertValueEqual(
                EchoSiteType.DEEP_CRYPT_ANCHOR_Y,
                -40,
                "the crypt room must sit around Y -40 unless the surface is too low to cover it");
        helper.assertValueEqual(
                site.memoryMin(),
                new BlockPos(-19, -1, -19),
                "the authored chamber must keep its 39x31x39 selection around the portal");
        helper.assertValueEqual(
                site.memoryMax(),
                new BlockPos(19, 29, 19),
                "the authored chamber must keep its 39x31x39 selection around the portal");
        helper.assertValueEqual(
                site.harmonicSource(),
                new BlockPos(0, 3, 0),
                "the Harmonic Key must lock onto the authored Timeless Portal pad");
        helper.assertTrue(site.requiresHarmonicKey(), "the portal frequency must require the Harmonic Key");
        helper.assertFalse(
                ResonatorLoadout.EMPTY.has(ResonatorModule.HARMONIC_KEY),
                "an empty Resonator must not satisfy the crypt lock");
        helper.assertTrue(
                new ResonatorLoadout(List.of(ResonatorModule.HARMONIC_KEY), false)
                        .has(ResonatorModule.HARMONIC_KEY),
                "an installed Harmonic Key must satisfy the crypt lock");
        helper.assertTrue(
                site.biome().isPresent()
                        && site.biome().orElseThrow().equals(EchoSiteType.CRYPT_GROUNDS_BIOME),
                "the access footprint must suppress later trees and decoration with its technical biome");
        assertRandomSpread(
                helper,
                site,
                24,
                12,
                "the crypt must be uncommon without becoming impractical to locate");

        StructureTemplate present = helper.getLevel()
                .getStructureManager()
                .get(site.presentTemplate())
                .orElseThrow();
        BlockPos anchor = helper.absolutePos(new BlockPos(8, 4, 8));
        BlockPos templateOrigin = anchor.offset(site.memoryMin());
        List<BlockPos> portals = present.filterBlocks(
                        templateOrigin,
                        new StructurePlaceSettings(),
                        EchoesShowThePast.TIMELESS_PORTAL.get())
                .stream()
                .map(StructureTemplate.StructureBlockInfo::pos)
                .toList();
        helper.assertValueEqual(
                portals.size(),
                9,
                "the chamber must contain the authored 3x3 Timeless Portal pad");
        int minPortalX = portals.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maxPortalX = portals.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minPortalY = portals.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        int maxPortalY = portals.stream().mapToInt(BlockPos::getY).max().orElseThrow();
        int minPortalZ = portals.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxPortalZ = portals.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        helper.assertValueEqual(
                new BlockPos(
                        (minPortalX + maxPortalX) / 2,
                        (minPortalY + maxPortalY) / 2,
                        (minPortalZ + maxPortalZ) / 2),
                anchor.offset(site.harmonicSource()),
                "the Harmonic Key must lock onto the centre of the Timeless Portal pad");
        List<BlockPos> entries = CryptAccessGate.entryMarkers(
                present,
                templateOrigin,
                new StructurePlaceSettings());
        helper.assertValueEqual(entries.size(), 1, "the present crypt must author exactly one entry marker");
        BlockPos entry = entries.getFirst();
        helper.assertValueEqual(
                entry,
                anchor.offset(0, 1, -19),
                "the access must derive from the authored entry marker, not a Java coordinate");
        helper.assertValueEqual(
                CryptAccessGate.outwardDirection(anchor, entry),
                Direction.NORTH,
                "the shaft must extend away from the crypt centre");
        List<BlockPos> seal = CryptAccessGate.gateCells(anchor, entry);
        helper.assertValueEqual(seal.size(), 9, "the seal must fill a three-wide by three-high doorway");
        helper.assertValueEqual(
                seal.stream().distinct().count(),
                9L,
                "every seal cell must be unique");
        BoundingBox access = CryptAccessGate.accessBounds(anchor, entry, anchor.getY() + 12);
        helper.assertTrue(
                access.maxY() >= anchor.getY() + 13 && access.minY() == entry.getY() - 1,
                "the structure piece bounds must include the complete shaft and surface rim");
        BoundingBox deepAccess = CryptAccessGate.accessBounds(anchor, entry, anchor.getY() + 80);
        helper.assertTrue(
                deepAccess.maxY() >= anchor.getY() + 81 && deepAccess.minY() == entry.getY() - 1,
                "a deepslate crypt must keep a shaft that can reach a distant overworld surface");
        BoundingBox rim = CryptAccessGate.surfaceRimBounds(anchor, entry, anchor.getY() + 80);
        helper.assertTrue(
                rim.maxY() - rim.minY() <= 4 && rim.maxX() - rim.minX() <= 2 && rim.maxZ() - rim.minZ() <= 2,
                "the surface wellhead biome must stay compact instead of painting a biome pillar");

        BlockPos untouched = entry.relative(Direction.SOUTH);
        helper.getLevel().setBlock(untouched, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
        int surfaceY = anchor.getY() + 12;
        CryptAccessGate.build(helper.getLevel(), anchor, entry, surfaceY, access);
        helper.assertTrue(
                seal.stream().allMatch(cell -> helper.getLevel().getBlockState(cell)
                        .is(EchoesShowThePast.CRYPT_SEAL.get())),
                "worldgen must place every solid seal cell after carving the access corridor");
        BlockPos shaft = entry.relative(Direction.NORTH, 4);
        helper.assertTrue(
                helper.getLevel().getBlockState(shaft).is(Blocks.LADDER)
                        && helper.getLevel().getBlockState(shaft.atY(surfaceY)).is(Blocks.LADDER),
                "the shaft ladder must connect the crypt level to the surface rim");
        helper.assertTrue(
                helper.getLevel().getBlockState(entry.relative(Direction.NORTH).above()).isAir(),
                "the generated corridor must be physically traversable up to the seal");
        helper.assertTrue(
                EchoesShowThePast.CRYPT_SEAL.get().defaultBlockState()
                        .getDestroySpeed(helper.getLevel(), entry) < 0.0F,
                "the crypt seal must be unbreakable in survival");
        helper.assertTrue(
                CryptAccessGate.unlock(helper.getLevel(), site, anchor),
                "one real crypt response with its loaded anchor must remove the complete seal");
        helper.assertTrue(
                seal.stream().allMatch(cell -> helper.getLevel().getBlockState(cell).isAir()),
                "the opened doorway must stay physically passable");
        helper.assertTrue(
                helper.getLevel().getBlockState(untouched).is(Blocks.DEEPSLATE_BRICKS),
                "unlocking must never remove adjacent architecture or traps");
        helper.assertFalse(
                CryptAccessGate.unlock(helper.getLevel(), site, anchor),
                "unlocking must be idempotent and never retrigger rewards or effects");
        helper.succeed();
    }

    private static void giantTemplateProjectionIndex(
            GameTestHelper helper) {
        int sourceSize = 239_778;
        List<StructureTemplate.StructureBlockInfo> source =
                new ArrayList<>(sourceSize);
        for (int index = 0; index < sourceSize; index++) {
            int x = index & 255;
            int y = (index >> 8) & 15;
            int z = index >> 12;
            source.add(new StructureTemplate.StructureBlockInfo(
                    new BlockPos(x, y, z),
                    Blocks.STONE.defaultBlockState(),
                    null));
        }

        EchoTemplateProjectionIndex index =
                EchoTemplateProjectionIndex.build(source, List.of());
        helper.assertValueEqual(
                index.sourceBlockCount(),
                sourceSize,
                "the projection index must account for the complete authored template");
        helper.assertValueEqual(
                index.indexedBlockCount(),
                sourceSize,
                "terrain may not be discarded by guessing which solid blocks are important");
        EchoTemplateProjectionIndex.Query local = index.query(
                new BlockPos(96, 0, 16),
                new BlockPos(127, 15, 47));
        helper.assertTrue(
                local.visitedEntries() <= 16_384,
                "a local Past Echo activation must not revisit all 239778 stored blocks");
        helper.assertValueEqual(
                local.blocks().size(),
                16_384,
                "the local projection query must retain every solid block in its window");
        helper.succeed();
    }

    private static void templateWaveMeshing(GameTestHelper helper) {
        List<StructureTemplate.StructureBlockInfo> platform =
                new ArrayList<>(16 * 16);
        Map<Long, BlockState> states = new HashMap<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                BlockPos position = new BlockPos(x, 0, z);
                BlockState state = Blocks.STONE.defaultBlockState();
                platform.add(new StructureTemplate.StructureBlockInfo(
                        position,
                        state,
                        null));
                states.put(position.asLong(), state);
            }
        }

        List<EchoTemplateWaveMesher.Patch> patches =
                EchoTemplateWaveMesher.meshSection(platform, states);
        List<EchoTemplateWaveMesher.Patch> top = patches.stream()
                .filter(patch -> patch.direction() == Direction.UP)
                .toList();
        helper.assertValueEqual(
                top.size(),
                1,
                "a broad remembered surface must remain one continuous wave patch");
        EchoTemplateWaveMesher.Patch topPatch = top.getFirst();
        helper.assertValueEqual(
                topPatch.width(),
                16,
                "the continuous top patch must span its complete width");
        helper.assertValueEqual(
                topPatch.height(),
                16,
                "the continuous top patch must span its complete depth");
        List<EchoTemplateWaveMesher.Patch> tiles = topPatch.tiles(4);
        helper.assertValueEqual(
                tiles.size(),
                16,
                "a continuous patch must split into adjacent cullable tiles, never samples");
        helper.assertValueEqual(
                tiles.stream().mapToInt(EchoTemplateWaveMesher.Patch::area).sum(),
                256,
                "tiled rendering must retain the complete continuous top surface");
        helper.assertValueEqual(
                patches.stream().mapToInt(EchoTemplateWaveMesher.Patch::area).sum(),
                576,
                "meshing must preserve every exterior face instead of sampling points from it");
        EchoWaveTessellation.Grid tileGrid = EchoWaveTessellation.grid(
                1.0,
                128.0,
                4.0,
                4.0);
        helper.assertValueEqual(
                tileGrid.u(),
                36,
                "a far four-block tile must preserve the crest's nine samples per block");
        helper.assertValueEqual(
                tileGrid.v(),
                36,
                "far surface tiling must retain crest fidelity on its second axis");
        double localRadius = 12.9;
        double handoffStart = EchoWaveHandoff.screenStart(localRadius);
        helper.assertValueEqual(
                handoffStart,
                0.0,
                "the depth crest must start at the emitter when post-processing owns the wave");
        helper.assertValueEqual(
                EchoWaveHandoff.screenWeight(0.0, localRadius),
                1.0,
                "the depth representation must carry the complete crest from its first visible block");
        helper.assertValueEqual(
                EchoWaveHandoff.localWeight(localRadius * 0.5, localRadius),
                0.0,
                "shader-compatible depth rendering must not duplicate the unsupported local pipeline");
        helper.succeed();
    }

    private static void relicPersistence(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        RelicState state = new RelicState(Optional.of(owner), 3, 12L, 900L);
        var encoded = RelicState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        helper.assertValueEqual(
                RelicState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(),
                state,
                "relic owner, charges and cooldown must survive item persistence");
        helper.assertValueEqual(
                state.withCharges(8, 3).charges(),
                3,
                "relic charges must be server-clamped to their maximum");
        helper.assertValueEqual(
                state.withCharges(-2, 3).charges(),
                0,
                "relic charges may never become negative");

        RelicReturnSavedData pending = new RelicReturnSavedData();
        pending.add(owner, new ItemStack(EchoesShowThePast.EYE_OF_HORUS.get()));
        List<ItemStack> returned = pending.take(owner);
        helper.assertValueEqual(returned.size(), 1, "an offline relic return must be queued");
        helper.assertTrue(
                returned.getFirst().is(EchoesShowThePast.EYE_OF_HORUS.get()),
                "the pending return must preserve the exact relic");
        helper.assertTrue(pending.take(owner).isEmpty(), "a pending return must be delivered only once");
        helper.assertTrue(
                Blocks.TRIPWIRE.defaultBlockState().is(EyeRevealManager.TRAPS),
                "the Eye must recognize authored trap tags");
        helper.assertFalse(
                Blocks.DIAMOND_ORE.defaultBlockState().is(EyeRevealManager.TRAPS)
                        || Blocks.DIAMOND_ORE.defaultBlockState().is(EyeRevealManager.GLYPHS),
                "the Eye must never become an ore locator");
        helper.assertValueEqual(
                EyeOfHorusItem.MAX_CHARGES,
                5,
                "the Eye of Horus must hold five uses");
        RelicState depletedEye =
                new RelicState(Optional.of(owner), 0, 12L, 0L);
        RelicState dawnEye =
                EyeOfHorusItem.rechargeStateForDay(depletedEye, 13L);
        helper.assertValueEqual(
                dawnEye.charges(),
                EyeOfHorusItem.MAX_CHARGES,
                "the first dawn of a new day must restore every Eye charge");
        helper.assertValueEqual(
                EyeOfHorusItem.rechargeStateForDay(
                                dawnEye.withCharges(
                                        2,
                                        EyeOfHorusItem.MAX_CHARGES),
                                13L)
                        .charges(),
                2,
                "the Eye must recharge at most once per dawn");
        var dawnPlayer = helper.makeMockServerPlayerInLevel();
        long currentDay =
                helper.getLevel().getOverworldClockTime() / 24_000L;
        ItemStack carriedEye =
                new ItemStack(EchoesShowThePast.EYE_OF_HORUS.get());
        carriedEye.set(
                EchoesShowThePast.RELIC_STATE.get(),
                new RelicState(
                        Optional.of(dawnPlayer.getUUID()),
                        0,
                        currentDay - 1L,
                        0L));
        EchoesShowThePast.EYE_OF_HORUS.get().inventoryTick(
                carriedEye,
                helper.getLevel(),
                dawnPlayer,
                null);
        helper.assertValueEqual(
                carriedEye.getOrDefault(
                                EchoesShowThePast.RELIC_STATE.get(),
                                RelicState.EMPTY)
                        .charges(),
                EyeOfHorusItem.MAX_CHARGES,
                "a carried Eye must recharge automatically without being activated");

        var cooldownPlayer = helper.makeMockServerPlayerInLevel();
        ItemStack medusa = new ItemStack(EchoesShowThePast.MEDUSA_HEAD.get());
        medusa.set(
                EchoesShowThePast.RELIC_STATE.get(),
                new RelicState(Optional.of(owner), 0, 12L, 99_999L));
        cooldownPlayer.getInventory().add(medusa);
        ItemStack storedMedusa = ItemStack.EMPTY;
        for (int slot = 0;
                slot < cooldownPlayer.getInventory().getContainerSize();
                slot++) {
            ItemStack candidate = cooldownPlayer.getInventory().getItem(slot);
            if (candidate.is(EchoesShowThePast.MEDUSA_HEAD.get())) {
                storedMedusa = candidate;
                break;
            }
        }
        helper.assertFalse(
                storedMedusa.isEmpty(),
                "the command test must operate on the stack actually stored by the inventory");
        helper.assertFalse(
                cooldownPlayer.getCooldowns().isOnCooldown(storedMedusa),
                "persistent relic data alone must reproduce the reconnect bug");
        helper.assertValueEqual(
                RelicCooldownManager.synchronizePlayer(cooldownPlayer),
                1,
                "login synchronization must restore one persistent cooldown group");
        helper.assertTrue(
                cooldownPlayer.getCooldowns().isOnCooldown(storedMedusa),
                "login synchronization must restore the vanilla cooldown display");
        helper.assertValueEqual(
                EchoCommands.resetCooldowns(cooldownPlayer),
                1,
                "the cooldown command must report persistent relic timers");
        helper.assertFalse(
                cooldownPlayer.getCooldowns().isOnCooldown(storedMedusa),
                "the cooldown command must clear vanilla item cooldown groups");
        helper.assertValueEqual(
                storedMedusa.get(EchoesShowThePast.RELIC_STATE.get()).cooldownUntil(),
                0L,
                "the cooldown command must also clear persistent relic timers");

        CompoundTag blockEntity = new CompoundTag();
        blockEntity.putString("id", "minecraft:chest");
        MaterializedEchoSavedData.Journal journal =
                new MaterializedEchoSavedData.Journal(
                        owner,
                        List.of(new MaterializedEchoSavedData.PresentBlock(
                                BlockPos.ZERO.asLong(),
                                Blocks.CHEST.defaultBlockState(),
                                Optional.of(blockEntity))));
        var encodedJournal = MaterializedEchoSavedData.Journal.CODEC
                .encodeStart(JsonOps.INSTANCE, journal)
                .getOrThrow();
        MaterializedEchoSavedData.Journal decodedJournal =
                MaterializedEchoSavedData.Journal.CODEC
                        .parse(JsonOps.INSTANCE, encodedJournal)
                        .getOrThrow();
        helper.assertValueEqual(decodedJournal.owner(), owner, "restoration journal owner codec");
        helper.assertTrue(
                decodedJournal.present().getFirst().blockEntityData().isPresent(),
                "restoration journals must preserve block-entity NBT");
        helper.succeed();
    }

    private static void materializedEchoTransaction(GameTestHelper helper) {
        helper.assertTrue(
                PhilosophersStoneVisualTiming.nativeWorldOwnsBlock(
                        0.70F,
                        0.0F,
                        false,
                        false),
                "present blocks must remain native before the materializing front reaches them");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.nativeWorldOwnsBlock(
                        0.20F,
                        0.70F,
                        false,
                        false),
                "materialized blocks must remain native after the outgoing front replaces them");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.nativeWorldOwnsBlock(
                        0.20F,
                        0.0F,
                        true,
                        true),
                "physical history must remain native before the restoring front reaches it");
        helper.assertFalse(
                PhilosophersStoneVisualTiming.nativeWorldOwnsBlock(
                        0.90F,
                        0.70F,
                        true,
                        true),
                "restored present blocks must immediately yield to the still-active Past Echo");
        helper.assertValueEqual(
                EchoCacheHandoff.decide(true, true, true, false),
                EchoCacheHandoff.Action.HOLD,
                "a branch revision must wait while the Stone owns the physical world");
        helper.assertValueEqual(
                EchoCacheHandoff.decide(false, true, true, true),
                EchoCacheHandoff.Action.REBUILD_AND_REFRESH_SECTIONS,
                "the final hand-off must rebuild against the restored world and invalidate filtered sections");
        helper.assertValueEqual(
                EchoCacheHandoff.decide(false, false, true, true),
                EchoCacheHandoff.Action.REBUILD_AND_REFRESH_SECTIONS,
                "even an unedited past must refresh chunk sections when temporal ownership ends");
        helper.assertValueEqual(
                EchoCacheHandoff.decide(false, false, true, false),
                EchoCacheHandoff.Action.REBUILD,
                "ordinary world edits only need the coalesced cache rebuild");
        helper.assertValueEqual(
                PhilosophersStoneVisualTiming.boundaryEnvelope(
                        0.0F,
                        false),
                0.0F,
                "the temporal boundary must not flash on the invocation's first frame");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.boundaryEnvelope(
                                0.95F,
                                false)
                        > 0.95F,
                "the boundary must be fully established before the past becomes physical");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.boundaryEnvelope(
                                0.0F,
                                true)
                        > 0.95F,
                "restoration must begin with the established boundary still visible");
        helper.assertValueEqual(
                PhilosophersStoneVisualTiming.boundaryEnvelope(
                        1.0F,
                        true),
                0.0F,
                "the boundary must finish with a soft disappearance into the present");
        final double curtainMargin = 0.65;
        for (double halfExtent :
                new double[] {2.0, 4.0, 8.0, 12.0, 24.0, 128.0}) {
            double outerHalf = halfExtent + curtainMargin;
            double cornerRadius = Math.clamp(
                    halfExtent * 0.18,
                    0.45,
                    2.0);
            double cornerOffset =
                    halfExtent
                            - outerHalf
                            + cornerRadius;
            double roundedFootprintDistance =
                    Math.sqrt(
                                    2.0
                                            * Math.pow(
                                                    Math.max(
                                                            cornerOffset,
                                                            0.0),
                                                    2.0))
                            + Math.min(
                                    Math.max(
                                            cornerOffset,
                                            cornerOffset),
                                    0.0)
                            - cornerRadius;
            helper.assertTrue(
                    roundedFootprintDistance < 0.0,
                    "the vertical chronal curtain must contain every authored footprint corner at half-extent "
                            + halfExtent);
        }
        helper.assertValueEqual(
                PhilosophersStoneVisualTiming.transitionTicks(1),
                PhilosophersStoneVisualTiming.MIN_TRANSITION_TICKS,
                "even one changed block needs a readable temporal transition");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.transitionTicks(16_384)
                        > PhilosophersStoneVisualTiming.transitionTicks(1),
                "large memories must keep the seam alive through every mutation batch");
        helper.assertValueEqual(
                PhilosophersStoneVisualTiming.strength(0.0F),
                0.0F,
                "the first Stone frame must remain untouched");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.strength(0.5F) > 0.95F,
                "the middle of the Stone transition must carry its full practical effect");
        helper.assertValueEqual(
                PhilosophersStoneVisualTiming.strength(1.0F),
                0.0F,
                "the post chain must settle without a hard final-frame cut");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.front(0.0F, false) < 0.0F,
                "the condensation seam must begin just before the first block changes");
        helper.assertValueEqual(
                PhilosophersStoneVisualTiming.front(0.10F, false),
                PhilosophersStoneVisualTiming.SEAM_START,
                "the Stone must hold a readable invocation before transmutation begins");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.front(0.18F, false)
                        > PhilosophersStoneVisualTiming.SEAM_START,
                "the crest must begin travelling after the invocation answers");
        helper.assertFalse(
                PhilosophersStoneVisualTiming.shouldMutate(
                        0.0F,
                        0.10F,
                        false),
                "the server must not alter even the centre during the invocation");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.shouldMutate(
                        0.0F,
                        0.36F,
                        false),
                "the centre must mutate only once the travelling crest reaches it");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.shouldMutate(
                        PhilosophersStoneVisualTiming.front(
                                0.58F,
                                false),
                        0.58F,
                        false),
                "a materializing block under the visible crest must already be ready on the server");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.shouldMutate(
                        PhilosophersStoneVisualTiming.front(
                                0.58F,
                                true),
                        0.58F,
                        true),
                "a restoring block under the visible crest must already be ready on the server");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.front(0.90F, false) > 1.0F,
                "every outer block must finish before the Stone begins its visual fade");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.strength(0.90F) > 0.95F,
                "the temporal distortion must remain fully visible until materialization finishes");
        helper.assertValueEqual(
                PhilosophersStoneVisualTiming.front(0.0F, false),
                PhilosophersStoneVisualTiming.front(1.0F, true),
                "restoration must end on the exact coordinate where materialization starts");
        helper.assertValueEqual(
                PhilosophersStoneVisualTiming.front(1.0F, false),
                PhilosophersStoneVisualTiming.front(0.0F, true),
                "restoration must start on the exact coordinate where materialization ends");
        Vec3 timingCenter = new Vec3(4.0, 8.0, -2.0);
        Vec3 timingExtents = new Vec3(12.0, 3.0, 1.5);
        float longAxisCoordinate =
                PhilosophersStoneVisualTiming.normalizedCoordinate(
                        timingCenter.add(12.0, 0.0, 0.0),
                        timingCenter,
                        timingExtents);
        float shortAxisCoordinate =
                PhilosophersStoneVisualTiming.normalizedCoordinate(
                        timingCenter.add(0.0, 3.0, 0.0),
                        timingCenter,
                        timingExtents);
        helper.assertTrue(
                Math.abs(longAxisCoordinate - shortAxisCoordinate)
                        < 1.0E-6F,
                "elongated structures must use one ellipsoidal coordinate in every visual layer");
        helper.assertValueEqual(
                PhilosophersStoneVisualTiming.condensation(0.5F, 0.5F),
                0.5F,
                "a ghost block must be halfway condensed precisely under the shared seam");
        helper.assertValueEqual(
                PhilosophersStoneVisualTiming.ghostPresence(0.5F, 0.5F),
                0.5F,
                "past and present ghost representations must share one complementary hand-off");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.BLOCK_BLEND_WIDTH < 0.025F,
                "present dissolution and past fixation must fit inside the visible refractive crest");
        helper.assertTrue(
                PhilosophersStoneVisualTiming.front(0.25F, false)
                                < PhilosophersStoneVisualTiming.front(
                                        0.75F,
                                        false)
                        && PhilosophersStoneVisualTiming.front(
                                        0.25F,
                                        true)
                                > PhilosophersStoneVisualTiming.front(
                                        0.75F,
                                        true),
                "returning to the present must reverse the materialization sweep");
        PhilosophersStoneVisualPayload visualPayload =
                new PhilosophersStoneVisualPayload(
                        new Vec3(2.5, 4.0, -3.5),
                        new Vec3(7.0, 3.0, 5.0),
                        new Vec3(0.6, 0.2, 0.7),
                        PhilosophersStoneVisualPayload.RESTORE_PRESENT,
                        44);
        var encodedVisual = PhilosophersStoneVisualPayload.CODEC
                .encodeStart(JsonOps.INSTANCE, visualPayload)
                .getOrThrow();
        helper.assertValueEqual(
                PhilosophersStoneVisualPayload.CODEC
                        .parse(JsonOps.INSTANCE, encodedVisual)
                        .getOrThrow(),
                visualPayload,
                "the Stone's volume, direction and reversible phase must survive its payload codec");
        PhilosophersStoneVisualPayload stableVisual =
                new PhilosophersStoneVisualPayload(
                        visualPayload.center(),
                        visualPayload.halfExtents(),
                        visualPayload.direction(),
                        PhilosophersStoneVisualPayload.STABLE_PAST,
                        1);
        helper.assertValueEqual(
                PhilosophersStoneVisualPayload.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                PhilosophersStoneVisualPayload.CODEC
                                        .encodeStart(JsonOps.INSTANCE, stableVisual)
                                        .getOrThrow())
                        .getOrThrow(),
                stableVisual,
                "late multiplayer observers must receive the exact persistent arena boundary volume");

        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(position, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(position);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
        chest.setChanged();

        var player = helper.makeMockServerPlayerInLevel();
        player.snapTo(
                position.getX() + 0.5,
                position.getY() + 1.0,
                position.getZ() + 0.5,
                0.0F,
                0.0F);
        EchoSnapshot memory = new EchoSnapshot(
                EchoSnapshot.CURRENT_VERSION,
                helper.getLevel().dimension(),
                position,
                0,
                false,
                List.of(Blocks.GOLD_BLOCK.defaultBlockState()),
                List.of(SnapshotBlock.of(0, 0, 0, 0)),
                List.of());
        ItemStack echo = new ItemStack(EchoesShowThePast.PAST_ECHO.get());
        PastEchoMemory.setSnapshot(echo, memory);
        player.getInventory().add(echo);
        helper.assertTrue(
                EchoProjectionManager.toggle(player, memory),
                "the memory must be active before the Stone can revise its past branch");
        helper.assertTrue(
                MaterializedEchoManager.start(player),
                "the Stone must start a transaction for a changed block");

        helper.runAfterDelay(2, () -> helper.assertTrue(
                helper.getLevel().getBlockState(position).is(Blocks.CHEST),
                "the remembered block must not appear before the visual crest"));
        helper.runAfterDelay(
                PhilosophersStoneVisualTiming.MIN_TRANSITION_TICKS / 2,
                () -> {
            helper.assertTrue(
                    helper.getLevel().getBlockState(position).is(Blocks.GOLD_BLOCK),
                    "the shared crest must apply the remembered state directly");
        });
        helper.runAfterDelay(
                PhilosophersStoneVisualTiming.MIN_TRANSITION_TICKS + 2,
                () -> {
            helper.assertTrue(
                    MaterializedEchoManager.isPastEditable(
                            helper.getLevel(),
                            position),
                    "ordinary blocks must become editable only while the past is fully materialized");
            helper.getLevel().setBlock(
                    position,
                    Blocks.AIR.defaultBlockState(),
                    3);
            MaterializedEchoManager.abort(player);
            helper.assertTrue(
                    helper.getLevel().getBlockState(position).is(Blocks.CHEST),
                    "aborting must restore the complete present state immediately");
            ChestBlockEntity restored =
                    (ChestBlockEntity) helper.getLevel().getBlockEntity(position);
            helper.assertTrue(
                    restored != null
                            && restored.getItem(0).is(Items.DIAMOND)
                            && restored.getItem(0).getCount() == 7,
                    "the restoration journal must restore inventory NBT without duplication");
            EchoSnapshot revised = EchoProjectionManager
                    .activeSnapshot(player)
                    .orElseThrow();
            helper.assertTrue(
                    revised.blocks().isEmpty(),
                    "breaking a block in materialized history must remove it from the past branch");
            EchoSnapshot persisted = null;
            for (int slot = 0;
                    slot
                            < player.getInventory()
                                    .getContainerSize();
                    slot++) {
                EchoSnapshot candidate = PastEchoMemory.getSnapshot(
                        player.getInventory().getItem(slot));
                if (candidate != null) {
                    persisted = candidate;
                    break;
                }
            }
            helper.assertTrue(
                    persisted != null
                            && persisted.blocks().isEmpty(),
                    "the revised past branch must persist on the Past Echo item");
            EchoStatePayload revision =
                    EchoStatePayload.revision(revised);
            helper.assertFalse(
                    revision.replay(),
                    "a branch revision must not replay the scanner activation");
            var encodedRevision = EchoStatePayload.CODEC
                    .encodeStart(JsonOps.INSTANCE, revision)
                    .getOrThrow();
            helper.assertValueEqual(
                    EchoStatePayload.CODEC
                            .parse(JsonOps.INSTANCE, encodedRevision)
                            .getOrThrow(),
                    revision,
                    "a silent past-branch revision must survive its payload codec");
            EchoProjectionManager.stop(player);
            helper.assertTrue(
                    EchoProjectionManager.toggle(player, persisted),
                    "reactivating the Past Echo must use the revised past branch");
            helper.assertTrue(
                    EchoProjectionManager
                            .activeSnapshot(player)
                            .orElseThrow()
                            .blocks()
                            .isEmpty(),
                    "a block broken in the past must remain absent on the next replay");
            EchoProjectionManager.stop(player);
            helper.succeed();
        });
    }

    private static void materializedEchoEntities(
            GameTestHelper helper) {
        BlockPos origin =
                helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(
                origin,
                Blocks.GOLD_BLOCK.defaultBlockState(),
                3);

        ArmorStand movedPast = helper.spawn(
                EntityType.ARMOR_STAND,
                new Vec3(3.3, 2.5, 2.5));
        movedPast.setCustomName(
                Component.literal("Moved Past"));
        movedPast.setNoGravity(true);
        movedPast.setRightArmPose(
                new Rotations(-74.0F, 13.0F, 6.0F));
        movedPast.setItemSlot(
                EquipmentSlot.MAINHAND,
                new ItemStack(Items.CLOCK));
        var passenger = helper.spawn(
                EntityType.CHICKEN,
                new Vec3(3.3, 2.5, 2.5));
        passenger.setCustomName(
                Component.literal("Past Passenger"));
        passenger.setNoGravity(true);
        helper.assertTrue(
                passenger.startRiding(
                        movedPast,
                        true,
                        false),
                "the historical passenger hierarchy must be valid before capture");

        ArmorStand escapedPast = helper.spawn(
                EntityType.ARMOR_STAND,
                new Vec3(1.7, 2.5, 2.5));
        escapedPast.setCustomName(
                Component.literal("Escaped Past"));
        escapedPast.setNoGravity(true);

        helper.runAfterDelay(5, () -> {
            EchoSnapshot captured = EchoCapture.capture(
                            helper.getLevel(),
                            origin,
                            2,
                            256)
                    .orElseThrow();
            helper.assertValueEqual(
                    captured.entities().size(),
                    2,
                    "the memory must contain both historical roots");
            helper.assertValueEqual(
                    captured.entities()
                            .stream()
                            .mapToInt(entity ->
                                    entity.passengerFrames().size())
                            .sum(),
                    1,
                    "passenger transient poses must be captured with their root");
            EchoSnapshot decodedCaptured =
                    EchoSnapshot.CODEC.parse(
                                    JsonOps.INSTANCE,
                                    EchoSnapshot.CODEC
                                            .encodeStart(
                                                    JsonOps.INSTANCE,
                                                    captured)
                                            .getOrThrow())
                            .getOrThrow();
            helper.assertValueEqual(
                    decodedCaptured.entities()
                            .getFirst()
                            .passengerFrames()
                            .size()
                            + decodedCaptured.entities()
                                    .getLast()
                                    .passengerFrames()
                                    .size(),
                    1,
                    "passenger frames must survive the memory codec");
            MaterializedEchoSavedData.Journal entityJournal =
                    new MaterializedEchoSavedData.Journal(
                            UUID.randomUUID(),
                            List.of(),
                            origin,
                            List.of(
                                    captured.entities()
                                            .getFirst()));
            MaterializedEchoSavedData.Journal decodedJournal =
                    MaterializedEchoSavedData.Journal.CODEC
                            .parse(
                                    JsonOps.INSTANCE,
                                    MaterializedEchoSavedData
                                            .Journal.CODEC
                                            .encodeStart(
                                                    JsonOps.INSTANCE,
                                                    entityJournal)
                                            .getOrThrow())
                            .getOrThrow();
            helper.assertValueEqual(
                    decodedJournal.origin(),
                    origin,
                    "the crash journal must preserve the entity origin");
            helper.assertValueEqual(
                    decodedJournal.presentEntities()
                            .size(),
                    1,
                    "the crash journal must preserve the present population");

            List<Entity> oldPast =
                    movedPast.getSelfAndPassengers().toList();
            for (int index = oldPast.size() - 1;
                    index >= 0;
                    index--) {
                oldPast.get(index).discard();
            }
            escapedPast.discard();
            helper.getLevel().setBlock(
                    origin,
                    Blocks.STONE.defaultBlockState(),
                    3);

            ArmorStand present = helper.spawn(
                    EntityType.ARMOR_STAND,
                    new Vec3(2.5, 2.5, 3.4));
            present.setCustomName(
                    Component.literal("Present Guard"));
            present.setNoGravity(true);
            Rotations presentPose =
                    new Rotations(-31.0F, 22.0F, 9.0F);
            present.setLeftArmPose(presentPose);
            present.setItemSlot(
                    EquipmentSlot.HEAD,
                    new ItemStack(Items.IRON_HELMET));

            var player =
                    helper.makeMockServerPlayerInLevel();
            player.snapTo(
                    origin.getX() + 0.5,
                    origin.getY() + 1.0,
                    origin.getZ() + 0.5,
                    0.0F,
                    0.0F);
            ItemStack echo =
                    new ItemStack(
                            EchoesShowThePast.PAST_ECHO.get());
            PastEchoMemory.setSnapshot(echo, captured);
            player.getInventory().add(echo);
            helper.assertTrue(
                    EchoProjectionManager.toggle(
                            player,
                            captured),
                    "the entity memory must activate");
            helper.assertTrue(
                    MaterializedEchoManager.start(player),
                    "entity differences must start a Stone transaction");

            helper.runAfterDelay(
                    PhilosophersStoneVisualTiming
                                    .MIN_TRANSITION_TICKS
                            + 3,
                    () -> {
                helper.assertTrue(
                        findNamedEntity(
                                        helper,
                                        "Present Guard")
                                .isEmpty(),
                        "the present population must be journalled while history is physical");
                Entity moved = findNamedEntity(
                                helper,
                                "Moved Past")
                        .orElseThrow();
                Entity escaped = findNamedEntity(
                                helper,
                                "Escaped Past")
                        .orElseThrow();
                helper.assertTrue(
                        MaterializedEchoManager
                                .isTemporalEntity(moved),
                        "a materialized historical entity must carry a crash-safe temporal identity");
                helper.assertValueEqual(
                        moved.getPassengers().size(),
                        1,
                        "passengers must materialize with their historical root");
                helper.assertFalse(
                        MaterializedEchoManager
                                .isTemporalEntity(player),
                        "players must never be captured by the temporal population");

                moved.snapTo(
                        origin.getX() + 1.7,
                        origin.getY() + 0.5,
                        origin.getZ() + 0.5,
                        moved.getYRot(),
                        moved.getXRot());
                escaped.snapTo(
                        origin.getX() + 5.5,
                        origin.getY() + 0.5,
                        origin.getZ() + 0.5,
                        escaped.getYRot(),
                        escaped.getXRot());

                ArmorStand entrant = helper.spawn(
                        EntityType.ARMOR_STAND,
                        new Vec3(1.1, 2.5, 2.5));
                entrant.setCustomName(
                        Component.literal(
                                "Temporal Entrant"));
                entrant.setNoGravity(true);
                entrant.setHeadPose(
                        new Rotations(
                                16.0F,
                                -11.0F,
                                4.0F));

                helper.runAfterDelay(3, () -> {
                    helper.assertFalse(
                            MaterializedEchoManager
                                    .isTemporalEntity(
                                            escaped),
                            "a historical entity crossing the boundary must escape into the present");
                    helper.assertTrue(
                            MaterializedEchoManager
                                    .isTemporalEntity(
                                            entrant),
                            "an entity entering physical history must become crash-safe temporal state immediately");
                    MaterializedEchoManager.abort(player);

                    Entity restoredPresent =
                            findNamedEntity(
                                            helper,
                                            "Present Guard")
                                    .orElseThrow();
                    helper.assertTrue(
                            restoredPresent
                                    instanceof ArmorStand,
                            "the present entity type must be restored");
                    ArmorStand restoredStand =
                            (ArmorStand) restoredPresent;
                    helper.assertValueEqual(
                            restoredStand.getLeftArmPose(),
                            presentPose,
                            "the present pose must survive the journal");
                    helper.assertTrue(
                            restoredStand
                                    .getItemBySlot(
                                            EquipmentSlot.HEAD)
                                    .is(Items.IRON_HELMET),
                            "present equipment must survive the journal");
                    helper.assertTrue(
                            findNamedEntity(
                                            helper,
                                            "Moved Past")
                                    .isEmpty(),
                            "a historical entity remaining inside must return to memory");
                    helper.assertTrue(
                            findNamedEntity(
                                            helper,
                                            "Temporal Entrant")
                                    .isEmpty(),
                            "an entity entering history must remain trapped in that memory");
                    helper.assertTrue(
                            findNamedEntity(
                                            helper,
                                            "Escaped Past")
                                    .isPresent(),
                            "an entity leaving history must remain physically present");

                    EchoSnapshot revised =
                            EchoProjectionManager
                                    .activeSnapshot(player)
                                    .orElseThrow();
                    helper.assertValueEqual(
                            revised.entities().size(),
                            2,
                            "the revised branch must contain the moved historical root and the entrant");
                    helper.assertTrue(
                            revised.entities().stream()
                                    .anyMatch(entity ->
                                            entity.offset()
                                                            .subtract(
                                                                    new Vec3(
                                                                            1.7,
                                                                            0.5,
                                                                            0.5))
                                                            .lengthSqr()
                                                    < 0.20),
                            "the moved entity position must persist in history");
                    helper.assertTrue(
                            revised.entities().stream()
                                    .noneMatch(entity ->
                                            Math.abs(
                                                            entity.offset().x)
                                                    > 3.0),
                            "the escaped entity must be removed from history");

                    helper.assertTrue(
                            MaterializedEchoManager
                                    .start(player),
                            "the revised entity branch must materialize again");
                    helper.runAfterDelay(
                            PhilosophersStoneVisualTiming
                                            .MIN_TRANSITION_TICKS
                                    + 3,
                            () -> {
                        helper.assertTrue(
                                findNamedEntity(
                                                helper,
                                                "Moved Past")
                                        .isPresent(),
                                "the moved historical entity must return at its revised position");
                        helper.assertTrue(
                                findNamedEntity(
                                                helper,
                                                "Temporal Entrant")
                                        .isPresent(),
                                "the trapped entrant must return with the revised past");
                        long escapedCount = countNamedEntities(
                                helper,
                                "Escaped Past");
                        helper.assertValueEqual(
                                escapedCount,
                                1L,
                                "replaying history must not duplicate an escaped entity");
                        MaterializedEchoManager.abort(
                                player);
                        EchoProjectionManager.stop(
                                player);
                        helper.succeed();
                    });
                });
            });
        });
    }

    private static void relicControlActions(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        ItemStack resonator = new ItemStack(
                EchoesShowThePast.LOW_FREQUENCY_RESONATOR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, resonator);
        player.getCooldowns().addCooldown(resonator, 200);
        helper.assertTrue(
                player.getCooldowns().isOnCooldown(resonator),
                "the console test must run under an active ItemCooldown");
        helper.assertFalse(
                RelicControlManager.handle(player, InteractionHand.MAIN_HAND),
                "opening the resonator console must still require sneak");

        player.setShiftKeyDown(true);
        helper.assertTrue(
                RelicControlManager.handle(player, InteractionHand.MAIN_HAND),
                "sneak control must open the console even while cooldown blocks Item.use");
        helper.assertTrue(
                player.containerMenu instanceof ResonatorMenu,
                "the Resonator tuning UI must open from the cooldown-safe control path");
        ResonatorMenu openMenu = (ResonatorMenu) player.containerMenu;
        player.setData(
                EchoesShowThePast.RESONANCE_KNOWLEDGE.get(),
                ResonanceKnowledge.EMPTY
                        .discover(EchoSiteType.MEDIEVAL_WATCHTOWER)
                        .discover(EchoSiteType.MEDUSA));
        openMenu.getSlot(0).set(ResonatorModule.HARMONIC_DECODER.createStack());
        helper.assertTrue(
                openMenu.clickMenuButton(player, ResonatorMenu.MUTE_ALL_BUTTON),
                "Mute all must succeed with a decoder and known sites");
        ResonanceKnowledge afterMute =
                player.getData(EchoesShowThePast.RESONANCE_KNOWLEDGE.get());
        helper.assertFalse(
                afterMute.anyListening(openMenu.discoveredSites()),
                "Mute all must silence every known signature");
        helper.assertTrue(
                openMenu.clickMenuButton(player, ResonatorMenu.MUTE_ALL_BUTTON),
                "Listen all must succeed when every signature is muted");
        ResonanceKnowledge afterListen =
                player.getData(EchoesShowThePast.RESONANCE_KNOWLEDGE.get());
        helper.assertTrue(
                afterListen.anyListening(openMenu.discoveredSites()),
                "Listen all must restore every known signature");
        player.closeContainer();
        player.setShiftKeyDown(false);

        ItemStack eye = new ItemStack(EchoesShowThePast.EYE_OF_HORUS.get());
        eye.set(
                EchoesShowThePast.RELIC_STATE.get(),
                new RelicState(
                        Optional.of(player.getUUID()),
                        EyeOfHorusItem.MAX_CHARGES,
                        0L,
                        0L));
        player.setItemInHand(InteractionHand.MAIN_HAND, eye);
        EyeRevealManager.start(player, 160);
        long now = helper.getLevel().getGameTime();
        player.setData(EchoesShowThePast.HORUS_AURA_START.get(), now);
        player.setData(EchoesShowThePast.HORUS_AURA_UNTIL.get(), now + 160L);
        player.getCooldowns().addCooldown(eye, 160);
        helper.assertTrue(
                EyeRevealManager.isActive(player),
                "Eye dismiss needs an active vision session");
        helper.assertTrue(
                RelicControlManager.handle(player, InteractionHand.MAIN_HAND),
                "the Eye must dismiss through the cooldown-safe control path");
        helper.assertFalse(
                EyeRevealManager.isActive(player),
                "manual Eye dismiss must end the reveal session immediately");

        ItemStack grail = new ItemStack(EchoesShowThePast.HOLY_GRAIL.get());
        long day = helper.getLevel().getOverworldClockTime() / 24_000L;
        grail.set(
                EchoesShowThePast.RELIC_STATE.get(),
                new RelicState(
                        Optional.of(player.getUUID()),
                        2,
                        day,
                        0L));
        player.setItemInHand(InteractionHand.MAIN_HAND, grail);
        helper.assertTrue(
                HolyGrailItem.applyRitualEffects(
                        helper.getLevel(),
                        player,
                        grail),
                "Grail dismiss needs a live aura");
        helper.assertTrue(
                RelicEffects.isGrailAuraActive(player),
                "the completed ritual must leave an active aura");
        helper.assertTrue(
                player.getCooldowns().isOnCooldown(grail),
                "Grail dismiss must remain available during its recovery cooldown");
        helper.assertTrue(
                RelicControlManager.handle(player, InteractionHand.MAIN_HAND),
                "the Grail must dismiss through the cooldown-safe control path");
        helper.assertFalse(
                RelicEffects.isGrailAuraActive(player),
                "manual Grail dismiss must stop aura gameplay immediately");
        helper.succeed();
    }

    private static void philosophersStoneCancel(
            GameTestHelper helper) {
        BlockPos position =
                helper.absolutePos(
                        new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(
                position,
                Blocks.STONE.defaultBlockState(),
                3);
        var player =
                helper.makeMockServerPlayerInLevel();
        player.snapTo(
                position.getX() + 0.5,
                position.getY() + 1.0,
                position.getZ() + 0.5,
                0.0F,
                0.0F);
        EchoSnapshot memory = new EchoSnapshot(
                EchoSnapshot.CURRENT_VERSION,
                helper.getLevel().dimension(),
                position,
                0,
                false,
                List.of(
                        Blocks.GOLD_BLOCK
                                .defaultBlockState()),
                List.of(
                        SnapshotBlock.of(
                                0,
                                0,
                                0,
                                0)),
                List.of());
        ItemStack echo =
                new ItemStack(
                        EchoesShowThePast.PAST_ECHO.get());
        PastEchoMemory.setSnapshot(echo, memory);
        player.getInventory().add(echo);
        helper.assertTrue(
                EchoProjectionManager.toggle(
                        player,
                        memory),
                "the cancellation test needs an active memory");
        helper.assertTrue(
                MaterializedEchoManager.start(player),
                "the Stone must begin before it can be cancelled");
        helper.assertTrue(
                MaterializedEchoManager.hasSession(player),
                "a started Stone must expose its active transaction");

        helper.runAfterDelay(
                PhilosophersStoneVisualTiming
                                .MIN_TRANSITION_TICKS
                        + 3,
                () -> {
            helper.assertTrue(
                    helper.getLevel()
                            .getBlockState(position)
                            .is(Blocks.GOLD_BLOCK),
                    "history must be physical before the manual cancellation");
            helper.assertTrue(
                    MaterializedEchoManager.cancel(player),
                    "using the active Stone must request a graceful restoration");
            helper.assertTrue(
                    MaterializedEchoManager.hasSession(player),
                    "manual cancellation must retain the journal until its returning front completes");
            helper.runAfterDelay(
                    PhilosophersStoneVisualTiming
                                    .MIN_TRANSITION_TICKS
                            + 4,
                    () -> {
                helper.assertTrue(
                        helper.getLevel()
                                .getBlockState(position)
                                .is(Blocks.STONE),
                        "manual cancellation must restore the exact present");
                helper.assertFalse(
                        MaterializedEchoManager
                                .hasSession(player),
                        "the temporal transaction must close after the cancellation fade");
                EchoProjectionManager.stop(player);
                helper.succeed();
            });
        });
    }

    private static void assertMaterializedEchoTerrainMask(GameTestHelper helper) {
        EchoSiteType site = EchoSiteType.MEDIEVAL_WATCHTOWER;
        BlockPos origin = helper.absolutePos(new BlockPos(24, 32, 24));
        EchoSnapshot memory = EchoSnapshot.templateReference(
                helper.getLevel().dimension(),
                origin,
                site.intactTemplate(),
                site.memoryMin(),
                site.memoryMax(),
                Optional.of(site.id()));
        EchoTemplateResolver.MaterializationFootprint footprint =
                EchoTemplateResolver.resolveForMaterialization(
                        helper.getLevel(),
                        memory);
        Map<Long, BlockState> targets = new HashMap<>();
        for (EchoTemplateResolver.MaterializationCell cell : footprint.remembered()) {
            targets.put(cell.position().asLong(), cell.state());
        }

        BlockPos lowerCorner = origin.offset(site.memoryMin());
        BlockPos historicalAirAddition = lowerCorner.offset(6, 12, 14);
        BlockPos naturalTerrain = lowerCorner.offset(16, 15, 40);
        helper.assertTrue(
                targets.getOrDefault(
                                historicalAirAddition.asLong(),
                                Blocks.BARRIER.defaultBlockState())
                        .isAir(),
                "a ruin addition proven to occupy historical air must be removed");
        helper.assertFalse(
                targets.containsKey(naturalTerrain.asLong()),
                "terrain inside the bounding box but outside the structure mask must remain world-owned");
        helper.assertTrue(
                footprint.authoredTemplate(),
                "site memories must never infer air from sparse authored bounds");
    }

    private static void philosophersStonePedestalActivation(
            GameTestHelper helper) {
        assertMaterializedEchoTerrainMask(helper);
        BlockPos pedestalPosition = helper.absolutePos(new BlockPos(2, 2, 2));
        // Authored structures store the template anchor as their memory origin;
        // the pedestal marker is normally elsewhere inside that template.
        BlockPos structureAnchor = pedestalPosition.west();
        BlockPos rememberedAir = structureAnchor.west();
        BlockPos naturalTerrain = rememberedAir.west();
        helper.getLevel().setBlock(
                pedestalPosition,
                EchoesShowThePast.ECHO_PEDESTAL.get().defaultBlockState(),
                3);
        helper.getLevel().setBlock(
                structureAnchor,
                Blocks.STONE.defaultBlockState(),
                3);
        helper.getLevel().setBlock(
                rememberedAir,
                Blocks.DIAMOND_BLOCK.defaultBlockState(),
                3);
        helper.getLevel().setBlock(
                naturalTerrain,
                Blocks.DIRT.defaultBlockState(),
                3);
        EchoPedestalBlockEntity pedestal = (EchoPedestalBlockEntity)
                helper.getLevel().getBlockEntity(pedestalPosition);

        var player = helper.makeMockServerPlayerInLevel();
        player.snapTo(
                pedestalPosition.getX() + 0.5,
                pedestalPosition.getY() + 1.0,
                pedestalPosition.getZ() + 2.5,
                180.0F,
                0.0F);
        ItemStack stone = new ItemStack(EchoesShowThePast.PHILOSOPHERS_STONE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(pedestalPosition).add(0.0, 0.5, 0.0),
                Direction.UP,
                pedestalPosition,
                false);

        InteractionResult emptyResult = player.gameMode.useItemOn(
                player,
                helper.getLevel(),
                stone,
                InteractionHand.MAIN_HAND,
                hit);
        helper.assertTrue(
                emptyResult.consumesAction(),
                "the Stone click must be handled even when the pedestal has no memory");
        helper.assertFalse(
                MaterializedEchoManager.hasSession(player),
                "an empty pedestal must not start a materialization");

        EchoSnapshot memory = new EchoSnapshot(
                EchoSnapshot.CURRENT_VERSION,
                helper.getLevel().dimension(),
                structureAnchor,
                0,
                true,
                List.of(
                        Blocks.GOLD_BLOCK.defaultBlockState(),
                        Blocks.AIR.defaultBlockState()),
                List.of(
                        SnapshotBlock.of(0, 0, 0, 0),
                        SnapshotBlock.of(-1, 0, 0, 1)),
                List.of(),
                Optional.empty(),
                Optional.of(new BlockPos(-2, 0, 0)),
                Optional.of(BlockPos.ZERO),
                Optional.of(EchoSiteType.UNKNOWN_CRYPT.id()));
        ItemStack fragment = PastEchoMemory.createFragment(memory, Optional.empty());
        pedestal.setEcho(fragment);
        helper.assertFalse(
                EchoProjectionManager.activeSnapshot(player).isPresent(),
                "test setup must not rely on a projected Past Echo");

        InteractionResult activationResult = player.gameMode.useItemOn(
                player,
                helper.getLevel(),
                stone,
                InteractionHand.MAIN_HAND,
                hit);
        helper.assertValueEqual(
                activationResult,
                InteractionResult.SUCCESS_SERVER,
                "right-clicking the occupied pedestal must activate the Stone");
        helper.assertTrue(
                MaterializedEchoManager.hasSession(player),
                "the pedestal memory must start the Stone's transaction directly");
        helper.assertTrue(
                MaterializedEchoManager.hasSessionAtPedestal(
                        helper.getLevel(),
                        pedestalPosition),
                "the transaction must be bound to the pedestal that physically holds its catalyst");
        helper.assertTrue(
                pedestal.hasEcho()
                        && pedestal.echo().is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get())
                        && memory.equals(pedestal.echo().get(EchoesShowThePast.ECHO_SNAPSHOT.get())),
                "activation must read the seated fragment without removing or mutating it");
        helper.assertTrue(
                pedestal.hasStone()
                        && pedestal.stone().is(EchoesShowThePast.PHILOSOPHERS_STONE.get())
                        && player.getCooldowns().isOnCooldown(pedestal.stone())
                        && player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                "activation must move the cooldown-bearing Stone out of the hand and visibly seat it in the pedestal");

        helper.runAfterDelay(
                PhilosophersStoneVisualTiming.MIN_TRANSITION_TICKS + 3,
                () -> {
            helper.assertTrue(
                    helper.getLevel().getBlockState(structureAnchor).is(Blocks.GOLD_BLOCK),
                    "the structural memory must materialize at its authored anchor, not at the pedestal marker");
            helper.assertTrue(
                    helper.getLevel().getBlockState(rememberedAir).isAir(),
                    "a block explicitly recorded as historical air must disappear");
            helper.assertTrue(
                    helper.getLevel().getBlockState(naturalTerrain).is(Blocks.DIRT),
                    "terrain merely enclosed by authored bounds must remain untouched");
            helper.assertTrue(
                    helper.getLevel().getBlockState(pedestalPosition)
                            .is(EchoesShowThePast.ECHO_PEDESTAL.get()),
                    "materializing a structure must leave its separately positioned pedestal in place");
            ItemStack emptyHand = ItemStack.EMPTY;
            player.setItemInHand(InteractionHand.MAIN_HAND, emptyHand);
            InteractionResult retrievalResult = player.gameMode.useItemOn(
                    player,
                    helper.getLevel(),
                    emptyHand,
                    InteractionHand.MAIN_HAND,
                    hit);
            helper.assertTrue(
                    retrievalResult.consumesAction()
                            && !pedestal.hasStone()
                            && pedestal.hasEcho(),
                    "the second pedestal click must return only the Stone and leave the fragment seated");
            int carriedStones = 0;
            for (int slot = 0;
                    slot < player.getInventory().getContainerSize();
                    slot++) {
                if (player.getInventory().getItem(slot)
                        .is(EchoesShowThePast.PHILOSOPHERS_STONE.get())) {
                    carriedStones += player.getInventory().getItem(slot).getCount();
                }
            }
            helper.assertValueEqual(
                    carriedStones,
                    1,
                    "retrieval must return exactly the one Stone that powered the pedestal");
            helper.assertTrue(
                    MaterializedEchoManager.hasSession(player),
                    "retrieval must preserve the journal until the returning visual crest finishes");
            helper.runAfterDelay(
                    PhilosophersStoneVisualTiming.MIN_TRANSITION_TICKS + 4,
                    () -> {
                helper.assertTrue(
                        helper.getLevel().getBlockState(structureAnchor).is(Blocks.STONE)
                                && helper.getLevel().getBlockState(rememberedAir)
                                        .is(Blocks.DIAMOND_BLOCK)
                                && helper.getLevel().getBlockState(naturalTerrain)
                                        .is(Blocks.DIRT),
                        "restoration must recover both replaced solids and blocks temporarily turned to air");
                helper.assertFalse(
                        MaterializedEchoManager.hasSession(player),
                        "the pedestal transaction must close after its returning crest");
                EchoPedestalBlockEntity restored = (EchoPedestalBlockEntity)
                        helper.getLevel().getBlockEntity(pedestalPosition);
                helper.assertTrue(
                        restored != null
                                && !restored.hasStone()
                                && restored.hasEcho()
                                && memory.equals(restored.echo().get(
                                        EchoesShowThePast.ECHO_SNAPSHOT.get())),
                        "the original fragment must remain available after the Stone is recovered");
            helper.assertTrue(
                    helper.getLevel().getEntitiesOfClass(
                            ItemEntity.class,
                            new AABB(pedestalPosition).inflate(3.0D),
                            item -> item.getItem().is(
                                    EchoesShowThePast.PAST_FRAGMENT_ITEM.get()))
                            .isEmpty(),
                    "temporary pedestal replacement must not drop a duplicate memory fragment");
                helper.succeed();
            });
        });
    }

    private static Optional<Entity> findNamedEntity(
            GameTestHelper helper,
            String name) {
        for (Entity entity :
                helper.getLevel().getAllEntities()) {
            if (entity.getName()
                    .getString()
                    .equals(name)) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    private static long countNamedEntities(
            GameTestHelper helper,
            String name) {
        long count = 0L;
        for (Entity entity :
                helper.getLevel().getAllEntities()) {
            if (entity.getName()
                    .getString()
                    .equals(name)) {
                count++;
            }
        }
        return count;
    }

    private static void eyeHazardClassification(GameTestHelper helper) {
        var dispenser = Blocks.DISPENSER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DispenserBlock.FACING, Direction.EAST);
        var dispenserHazard = EyeHazardClassifier.classify(dispenser).orElseThrow();
        helper.assertValueEqual(
                dispenserHazard.type(),
                EyeHazardType.PROJECTILE,
                "dispensers must expose a projectile hazard");
        helper.assertValueEqual(
                dispenserHazard.direction(),
                Direction.EAST,
                "a dispenser hazard must preserve its firing direction");
        helper.assertValueEqual(
                EyeHazardClassifier.classify(Blocks.LAVA.defaultBlockState())
                        .orElseThrow()
                        .type(),
                EyeHazardType.LAVA,
                "lava must be revealed as a thermal hazard");
        helper.assertValueEqual(
                EyeHazardClassifier.classify(Blocks.POINTED_DRIPSTONE.defaultBlockState())
                        .orElseThrow()
                        .type(),
                EyeHazardType.SPIKES,
                "pointed dripstone must be revealed as spikes");
        helper.assertValueEqual(
                EyeHazardClassifier.classify(Blocks.TRIPWIRE.defaultBlockState())
                        .orElseThrow()
                        .type(),
                EyeHazardType.TRIGGER,
                "tripwire must be revealed as a trigger");
        helper.assertValueEqual(
                EyeHazardClassifier.classify(Blocks.TNT.defaultBlockState())
                        .orElseThrow()
                        .type(),
                EyeHazardType.EXPLOSIVE,
                "TNT must be revealed as an explosive");
        helper.assertValueEqual(
                EyeHazardClassifier.classify(Blocks.CACTUS.defaultBlockState())
                        .orElseThrow()
                        .type(),
                EyeHazardType.CONTACT,
                "contact-damage blocks must be classified");
        helper.assertTrue(
                EyeHazardClassifier.classify(Blocks.STONE.defaultBlockState()).isEmpty(),
                "ordinary construction blocks must never become false hazards");
        helper.assertTrue(
                EyeHazardClassifier.classify(Blocks.CHISELED_SANDSTONE.defaultBlockState())
                        .isEmpty(),
                "chiseled sandstone is desert masonry, not a trap");
        helper.assertTrue(
                EyeHazardClassifier.classify(Blocks.CHISELED_STONE_BRICKS.defaultBlockState())
                        .isEmpty()
                        && EyeHazardClassifier.classify(
                                        Blocks.CHISELED_RED_SANDSTONE.defaultBlockState())
                                .isEmpty(),
                "chiseled facing blocks must not light up as false glyphs");
        helper.succeed();
    }

    private static void medusaGazeGeometry(GameTestHelper helper) {
        assertReliquaryRetired(helper);
        var posePlayer = helper.makeMockServerPlayerInLevel();
        ItemStack heldHead = new ItemStack(EchoesShowThePast.MEDUSA_HEAD.get());
        posePlayer.setItemInHand(InteractionHand.MAIN_HAND, heldHead);
        helper.assertFalse(
                MedusaHeadItem.rendersActivePose(posePlayer, heldHead),
                "the held head must remain floor-facing before use begins");
        posePlayer.startUsingItem(InteractionHand.MAIN_HAND);
        helper.assertTrue(
                MedusaHeadItem.rendersActivePose(posePlayer, heldHead),
                "using the head must select its forward-facing render pose");
        helper.assertFalse(
                MedusaHeadItem.rendersActivePose(
                        posePlayer,
                        new ItemStack(Items.STONE)),
                "using Medusa must not rotate unrelated rendered items");
        helper.assertTrue(
                dev.alvar.echoespast.relic.MedusaHeadItem.MAX_CHANNEL_TICKS
                        > dev.alvar.echoespast.relic.MedusaHeadItem.WARMUP_TICKS * 4,
                "Medusa must reach full strength and then remain active while use is held");
        Vec3 origin = Vec3.ZERO;
        Vec3 look = new Vec3(0.0, 0.0, 1.0);
        helper.assertTrue(
                MedusaGazeMath.contains(
                        origin,
                        look,
                        new Vec3(0.0, 0.0, 16.0),
                        16.0,
                        0.82),
                "the gaze must include its forward range boundary");
        helper.assertTrue(
                MedusaGazeMath.contains(
                        origin,
                        look,
                        new Vec3(5.0, 0.0, 10.0),
                        16.0,
                        0.82),
                "the gaze must include targets inside its intended cone");
        helper.assertFalse(
                MedusaGazeMath.contains(
                        origin,
                        look,
                        new Vec3(10.0, 0.0, 5.0),
                        16.0,
                        0.82),
                "the gaze must reject targets outside its cone");
        helper.assertFalse(
                MedusaGazeMath.contains(
                        origin,
                        look,
                        new Vec3(0.0, 0.0, -4.0),
                        16.0,
                        0.82),
                "the gaze must never petrify targets behind the user");
        helper.assertFalse(
                MedusaGazeMath.contains(
                        origin,
                        look,
                        new Vec3(0.0, 0.0, 16.01),
                        16.0,
                        0.82),
                "the gaze must enforce its exact maximum range");
        helper.assertValueEqual(
                MedusaHeadAimMath.activationPoseBlend(0.0F),
                0.0F,
                "the held head must begin in its floor-facing pose");
        helper.assertValueEqual(
                MedusaHeadAimMath.activationPoseBlend(3.0F),
                0.5F,
                "the head must turn smoothly rather than snap at activation");
        helper.assertValueEqual(
                MedusaHeadAimMath.activationPoseBlend(6.0F),
                1.0F,
                "the eyes must finish turning forward before gaze warmup completes");
        helper.assertValueEqual(
                MedusaHeadAimMath.rotation(0.0F).z(),
                -90.0F,
                "the resting renderer must use the authored hand roll");
        helper.assertValueEqual(
                MedusaHeadAimMath.rotation(1.0F).z(),
                -90.0F,
                "the active hand pose must keep the authored roll");
        helper.assertValueEqual(
                MedusaHeadAimMath.rotation(0.0F).y(),
                0.0F,
                "the authored hand pose must not keep the old yaw tilt");
        helper.assertValueEqual(
                MedusaHeadAimMath.HAND_GRIP_Y,
                -2.0F / 16.0F,
                "the palm must hold the severed neck instead of empty geo space");
        helper.assertValueEqual(
                MedusaHeadAimMath.REST.addAxis('x', 15.0F).x(),
                15.0F,
                "debug rest offsets must rotate a single authored axis");
        helper.assertValueEqual(
                MedusaHeadAimMath.ACTIVE.addAxis('z', -20.0F).z(),
                -110.0F,
                "debug active offsets must rotate a single authored axis");
        helper.assertValueEqual(
                new MedusaHeadAimMath.PoseEuler(-90.0F, -90.0F, -180.0F)
                        .canonical(),
                new MedusaHeadAimMath.PoseEuler(0.0F, -90.0F, -90.0F),
                "gimbal-locked idle triples must collapse to one Euler pose");
        try (InputStream model = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/models/item/medusa_head.json");
                InputStream geo = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/geckolib/models/item/medusa_head.geo.json");
                InputStream animation = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/geckolib/animations/item/medusa_head.animation.json")) {
            helper.assertTrue(model != null, "Medusa's display model must be packaged");
            helper.assertTrue(geo != null, "Medusa's held geo must be packaged");
            helper.assertTrue(animation != null, "Medusa's held animations must be packaged");
            JsonObject display = JsonParser.parseReader(new java.io.InputStreamReader(
                            model,
                            StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonObject("display");
            JsonObject thirdRight = display.getAsJsonObject("thirdperson_righthand");
            JsonObject thirdLeft = display.getAsJsonObject("thirdperson_lefthand");
            JsonObject firstRight = display.getAsJsonObject("firstperson_righthand");
            JsonObject firstLeft = display.getAsJsonObject("firstperson_lefthand");
            JsonObject gui = display.getAsJsonObject("gui");
            helper.assertValueEqual(
                    thirdRight.getAsJsonArray("rotation").toString(),
                    "[0,0,90]",
                    "third-person rest must use only the authored floor-facing quarter-turn");
            helper.assertValueEqual(
                    thirdLeft.getAsJsonArray("rotation").toString(),
                    "[0,0,-90]",
                    "left-hand third-person rest must be the vanilla-mirrored quarter-turn");
            helper.assertValueEqual(
                    firstRight.getAsJsonArray("rotation").toString(),
                    "[0,0,90]",
                    "first-person rest must use only the authored floor-facing quarter-turn");
            helper.assertValueEqual(
                    firstLeft.getAsJsonArray("rotation").toString(),
                    "[0,0,-90]",
                    "left-hand first-person rest must be the vanilla-mirrored quarter-turn");
            double thirdScale = thirdRight.getAsJsonArray("scale").get(0).getAsDouble();
            double firstScale = firstRight.getAsJsonArray("scale").get(0).getAsDouble();
            double guiScale = gui.getAsJsonArray("scale").get(0).getAsDouble();
            helper.assertTrue(
                    thirdScale >= 0.74 && thirdScale <= 0.82,
                    "the third-person head must retain a full, readable severed-head silhouette");
            helper.assertTrue(
                    firstScale >= 0.82 && firstScale <= 0.9,
                    "the first-person head must be substantial without covering the crosshair");
            helper.assertTrue(
                    guiScale >= 0.86 && guiScale <= 0.94,
                    "the animated head must fill its hotbar cell without clipping");
            helper.assertTrue(
                    Math.abs(
                                    firstRight.getAsJsonArray("translation").get(0).getAsDouble()
                                            + firstLeft.getAsJsonArray("translation").get(0).getAsDouble())
                            < 1.0E-6,
                    "first-person hand placement must be mirrored instead of offset to one side");
            helper.assertTrue(
                    Math.abs(
                                    thirdRight.getAsJsonArray("translation").get(0).getAsDouble()
                                            + thirdLeft.getAsJsonArray("translation").get(0).getAsDouble())
                            < 1.0E-6,
                    "third-person hand placement must be mirrored instead of offset to one side");
            helper.assertTrue(
                    firstRight.getAsJsonArray("translation").get(1).getAsDouble()
                            <= 0.5,
                    "first-person grip must sit in the palm after the authored roll");
            helper.assertTrue(
                    thirdRight.getAsJsonArray("translation").get(1).getAsDouble()
                            <= 0.5,
                    "third-person grip must sit in the palm after the authored roll");
            String animationSource = new String(
                    animation.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    animationSource.contains("animation.model.idle")
                            && animationSource.contains("animation.model.active"),
                    "the corrected transforms must retain both animated head states");
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect Medusa's held-item assets", exception);
        }
        ArmorStand pumpkinWard = helper.spawn(
                EntityType.ARMOR_STAND,
                new Vec3(2.5, 2.0, 2.5));
        helper.assertFalse(
                MedusaHeadItem.isProtectedByPumpkin(pumpkinWard),
                "an empty head slot must never count as a Medusa ward");
        pumpkinWard.setItemSlot(
                EquipmentSlot.HEAD,
                new ItemStack(Items.CARVED_PUMPKIN));
        helper.assertTrue(
                MedusaHeadItem.isProtectedByPumpkin(pumpkinWard),
                "a carved pumpkin on the head must be the Medusa ward");
        pumpkinWard.setItemSlot(
                EquipmentSlot.HEAD,
                new ItemStack(Items.PUMPKIN));
        helper.assertFalse(
                MedusaHeadItem.isProtectedByPumpkin(pumpkinWard),
                "an uncarved pumpkin must not ward the gaze");
        WitherBoss wither = helper.spawn(
                EntityType.WITHER,
                new Vec3(5.5, 2.0, 2.5));
        helper.assertTrue(
                MedusaHeadItem.isBoss(wither),
                "the wither must count as a Medusa boss target");
        helper.assertFalse(
                MedusaHeadItem.isBoss(pumpkinWard),
                "ordinary mobs must not inherit boss immunity rules");
        helper.succeed();
    }

    private static void medusaBossKit(GameTestHelper helper) {
        Vec3 origin = new Vec3(0.0, 1.6, 0.0);
        Vec3 south = new Vec3(0.0, 0.0, 1.0);
        Vec3 north = new Vec3(0.0, 0.0, -1.0);
        Vec3 medusaEyes = new Vec3(0.0, 1.6, 4.0);
        helper.assertTrue(
                MedusaBossMath.isLookingAtFace(
                        origin,
                        south,
                        medusaEyes,
                        north,
                        MedusaBossMath.GAZE_RANGE),
                "looking into Medusa's face must start the petrifying lock");
        helper.assertFalse(
                MedusaBossMath.isLookingAtFace(
                        origin,
                        south,
                        medusaEyes,
                        south,
                        MedusaBossMath.GAZE_RANGE),
                "looking at Medusa's back must never petrify");
        helper.assertFalse(
                MedusaBossMath.isLookingAtFace(
                        origin,
                        north,
                        medusaEyes,
                        north,
                        MedusaBossMath.GAZE_RANGE),
                "looking away from Medusa must never petrify");
        helper.assertFalse(
                MedusaBossMath.isLookingAtFace(
                        origin,
                        new Vec3(0.65, 0.0, 0.76),
                        medusaEyes,
                        north,
                        MedusaBossMath.GAZE_RANGE),
                "looking beside Medusa must not count as meeting her eyes");
        helper.assertTrue(
                MedusaBossMath.isLookingAtFace(
                        origin,
                        new Vec3(0.20, 0.0, 0.98),
                        medusaEyes,
                        north,
                        MedusaBossMath.GAZE_RANGE),
                "a slight offset toward her face must still lock");
        helper.assertTrue(
                MedusaBossMath.isLookingAtFace(
                        origin,
                        south,
                        new Vec3(0.0, 1.6, 24.0),
                        north,
                        MedusaBossMath.GAZE_RANGE),
                "looking at her eyes from mid range must still petrify");
        helper.assertFalse(
                MedusaBossMath.isLookingAtFace(
                        origin,
                        south,
                        new Vec3(0.0, 1.6, 40.0),
                        north,
                        MedusaBossMath.GAZE_RANGE),
                "the boss gaze must respect its maximum range");
        helper.assertValueEqual(
                MedusaBossMath.nextGazeLock(0, true, false),
                1,
                "a first glance must only charge the lock by one tick");
        helper.assertValueEqual(
                MedusaBossMath.nextGazeLock(10, false, false),
                8,
                "looking away must decay the lock instead of resetting it instantly");
        helper.assertTrue(
                MedusaBossMath.gazeCompletes(
                        MedusaBossMath.nextGazeLock(
                                MedusaBossMath.GAZE_LOCK_TICKS - 1,
                                true,
                                true)),
                "Medusa's petrify pose must complete a nearly charged lock");
        helper.assertTrue(
                MedusaBossMath.isSnakeHitTick(MedusaBossMath.SNAKE_HIT_TICK)
                        && !MedusaBossMath.isSnakeHitTick(0),
                "the snake strike must land on its authored contact frame");
        helper.assertTrue(
                MedusaBossMath.snakeStrikeReaches(
                        origin,
                        south,
                        new Vec3(0.0, 1.6, 2.5),
                        MedusaBossMath.SNAKE_REACH),
                "the snake strike must reach a target in front of Medusa");
        helper.assertFalse(
                MedusaBossMath.snakeStrikeReaches(
                        origin,
                        south,
                        new Vec3(0.0, 1.6, -1.5),
                        MedusaBossMath.SNAKE_REACH),
                "the snake strike must not hit behind Medusa");
        helper.assertTrue(
                MedusaBossMath.isInGazeBeam(
                        origin,
                        south,
                        medusaEyes,
                        MedusaBossMath.GAZE_RANGE),
                "Medusa's own look must catch prey in front of her");
        helper.assertFalse(
                MedusaBossMath.isInGazeBeam(
                        origin,
                        north,
                        medusaEyes,
                        MedusaBossMath.GAZE_RANGE),
                "prey behind Medusa must not be in her gaze");
        helper.assertTrue(
                MedusaBossMath.mobGazeCompletes(
                        MedusaBossMath.nextMobGazeLock(
                                MedusaBossMath.MOB_GAZE_LOCK_TICKS - 2,
                                true)),
                "mobs in her beam must petrify within a few ticks");

        MedusaEntity medusa = helper.spawn(
                EchoesShowThePast.MEDUSA.get(),
                new Vec3(4.5, 2.0, 4.5));
        helper.assertTrue(
                MedusaEntity.isMedusa(medusa),
                "the sanctuary gorgon must identify as Medusa");
        helper.assertTrue(
                MedusaHeadItem.isBoss(medusa),
                "Medusa must count as a boss so the petrified head cannot freeze her");
        helper.assertValueEqual(
                medusa.getMaxHealth(),
                MedusaEntity.MAX_HEALTH,
                "Medusa must spawn with her authored boss health");
        medusa.checkDespawn();
        helper.assertFalse(
                medusa.isRemoved(),
                "Medusa must ignore vanilla distance and Peaceful despawn");
        helper.assertFalse(
                medusa.canHunt(medusa),
                "Medusa must never hunt herself");

        var dummy = helper.spawn(EntityType.CREEPER, new Vec3(4.5, 2.0, 6.5));
        dummy.setNoAi(true);
        helper.assertTrue(
                medusa.canHunt(dummy),
                "Medusa must hunt any living creature, not only players");
        ArmorStand decoration = helper.spawn(
                EntityType.ARMOR_STAND,
                new Vec3(6.5, 2.0, 4.5));
        helper.assertFalse(
                medusa.canHunt(decoration),
                "authored statues and armor stands are not prey");
        medusa.setYRot(0.0F);
        medusa.setYHeadRot(0.0F);
        dummy.setYRot(0.0F);
        dummy.setYHeadRot(0.0F);
        helper.assertTrue(
                medusa.isCaughtByGaze(dummy),
                "a mob facing away must still petrify if Medusa is looking at it");
        medusa.trySnakeStrike(dummy);
        helper.assertTrue(
                dummy.hasEffect(MobEffects.POISON),
                "a connected snake strike must inject venom");
        helper.assertTrue(
                dummy.getHealth() < dummy.getMaxHealth(),
                "a connected snake strike must deal damage");

        var wielder = helper.makeMockServerPlayerInLevel();
        MedusaHeadItem livingHead = EchoesShowThePast.MEDUSA_HEAD.get();
        MedusaHeadItem brittleHead = EchoesShowThePast.MEDUSA_PETRIFIED_HEAD.get();
        helper.assertTrue(
                livingHead.canRelicPetrify(medusa, wielder),
                "the living Head of Medusa must be able to petrify her");
        helper.assertFalse(
                brittleHead.canRelicPetrify(medusa, wielder),
                "the petrified head must still be too brittle to freeze Medusa");

        var prey = helper.spawn(EntityType.PIG, new Vec3(4.5, 2.0, 8.0));
        prey.setNoAi(true);
        prey.setYRot(0.0F);
        prey.setYHeadRot(0.0F);
        helper.assertTrue(
                medusa.isCaughtByGaze(prey),
                "Medusa must catch ordinary animals in her gaze beam");
        medusa.setNoAi(true);
        medusa.setYRot(0.0F);
        medusa.setYHeadRot(0.0F);
        for (int tick = 0; tick < 20 && !RelicEffects.isPermanentlyPetrified(prey); tick++) {
            medusa.setYRot(0.0F);
            medusa.setYHeadRot(0.0F);
            medusa.tick();
        }
        medusa.setNoAi(false);
        helper.assertTrue(
                RelicEffects.isPermanentlyPetrified(prey),
                "a mob in Medusa's gaze must turn to stone quickly");

        var victim = helper.makeMockServerPlayerInLevel();
        Vec3 victimPos = helper.absoluteVec(new Vec3(4.5, 2.0, 2.5));
        victim.snapTo(victimPos.x, victimPos.y, victimPos.z, 0.0F, 0.0F);
        helper.assertTrue(
                PetrifiedMobManager.leavePlayerStatueAndKill(
                        helper.getLevel(),
                        medusa,
                        victim),
                "Medusa's gaze must leave a memorial when it completes");
        var memorials = helper.getLevel().getEntitiesOfClass(
                Mannequin.class,
                victim.getBoundingBox().inflate(4.0),
                RelicEffects::isPermanentlyPetrified);
        helper.assertTrue(
                !memorials.isEmpty(),
                "a completed Medusa gaze must leave a stone memorial of the victim");

        try (InputStream geo = EchoGameTests.class.getResourceAsStream(
                "/assets/echoes_show_the_past/geckolib/models/entity/medusa.geo.json");
                InputStream animation = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/geckolib/animations/entity/medusa.animation.json");
                InputStream texture = EchoGameTests.class.getResourceAsStream(
                        "/assets/echoes_show_the_past/textures/entity/medusa.png")) {
            helper.assertTrue(geo != null, "Medusa's geo must be packaged");
            helper.assertTrue(animation != null, "Medusa's authored animations must be packaged");
            helper.assertTrue(texture != null, "Medusa's texture must be packaged");
            String geoSource = new String(geo.readAllBytes(), StandardCharsets.UTF_8);
            String animationSource = new String(
                    animation.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    geoSource.contains("\"name\": \"cabezon\"")
                            && geoSource.contains("\"name\": \"sec8\""),
                    "Medusa's geo must keep her head and coiled tail");
            helper.assertTrue(
                    animationSource.contains("animation.model.idle")
                            && animationSource.contains("animation.model.moving")
                            && animationSource.contains("animation.model.petrifiaction_attack")
                            && animationSource.contains("animation.model.front_snake_attack"),
                    "Medusa must keep idle, slither, gaze and snake-strike animations");
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect Medusa's packaged model", exception);
        }

        MedusaEntity brokenGorgon = helper.spawn(
                EchoesShowThePast.MEDUSA.get(),
                new Vec3(8.5, 2.0, 2.5));
        RelicEffects.petrifyPermanently(brokenGorgon);
        var statueMiner = helper.makeMockServerPlayerInLevel();
        Vec3 minerPos = helper.absoluteVec(new Vec3(7.5, 2.0, 2.5));
        statueMiner.snapTo(minerPos.x, minerPos.y, minerPos.z, 0.0F, 0.0F);
        statueMiner.setItemInHand(
                InteractionHand.MAIN_HAND,
                new ItemStack(Items.DIAMOND_PICKAXE));
        ItemEntity brokenDrop = PetrifiedMobManager.dropStatue(statueMiner, brokenGorgon)
                .orElseThrow();
        helper.assertTrue(brokenGorgon.isRemoved(), "mining Medusa must collect the statue");
        PetrifiedMobData brokenData =
                brokenDrop.getItem().get(EchoesShowThePast.PETRIFIED_MOB_DATA.get());
        helper.assertTrue(
                brokenData != null && brokenData.headless(),
                "ordinary mining must strike Medusa's head off the statue");
        helper.assertTrue(
                !helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class,
                        brokenDrop.getBoundingBox().inflate(3.0),
                        item -> item.getItem().is(
                                EchoesShowThePast.MEDUSA_PETRIFIED_HEAD.get()))
                        .isEmpty(),
                "the severed stone head must drop beside the headless statue");

        MedusaEntity silkGorgon = helper.spawn(
                EchoesShowThePast.MEDUSA.get(),
                new Vec3(2.5, 2.0, 8.5));
        RelicEffects.petrifyPermanently(silkGorgon);
        ItemStack silkPick = new ItemStack(Items.DIAMOND_PICKAXE);
        helper.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH)
                .ifPresent(silk -> silkPick.enchant(silk, 1));
        helper.assertTrue(
                PetrifiedMobManager.hasSilkTouch(helper.getLevel(), silkPick),
                "the silk-touch probe pickaxe must actually carry Silk Touch");
        statueMiner.setItemInHand(InteractionHand.MAIN_HAND, silkPick);
        ItemEntity silkDrop = PetrifiedMobManager.dropStatue(statueMiner, silkGorgon)
                .orElseThrow();
        PetrifiedMobData silkData =
                silkDrop.getItem().get(EchoesShowThePast.PETRIFIED_MOB_DATA.get());
        helper.assertTrue(
                silkData != null && !silkData.headless(),
                "Silk Touch must keep Medusa's statue intact");
        helper.succeed();
    }

    private static void medusaPlayerMemorial(GameTestHelper helper) {
        var attacker = helper.makeMockServerPlayerInLevel();
        var victim = helper.makeMockServerPlayerInLevel();
        victim.snapTo(3.5, 2.0, 2.5, 90.0F, 0.0F);
        victim.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        victim.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        helper.assertTrue(
                PetrifiedMobManager.leavePlayerStatueAndKill(
                        helper.getLevel(),
                        attacker,
                        victim),
                "Medusa must leave a memorial before killing the player");
        helper.assertFalse(
                victim.isAlive(),
                "a gazed-at player must die immediately");
        helper.assertTrue(
                victim.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                        && victim.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(),
                "worn gear must move onto the memorial instead of duplicating on death");
        var memorials = helper.getLevel().getEntitiesOfClass(
                Mannequin.class,
                victim.getBoundingBox().inflate(2.0),
                RelicEffects::isPermanentlyPetrified);
        helper.assertTrue(
                !memorials.isEmpty(),
                "a permanent mannequin statue must remain at the death site");
        Mannequin memorial = memorials.getFirst();
        helper.assertTrue(
                memorial.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                "the memorial must wear the victim's helmet");
        helper.assertTrue(
                memorial.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.DIAMOND_SWORD),
                "the memorial must hold the victim's weapon");
        helper.succeed();
    }

    private static void petrifiedPeacefulPersistence(GameTestHelper helper) {
        Zombie statue = helper.spawn(
                EntityType.ZOMBIE,
                new Vec3(2.5, 2.0, 2.5));
        statue.setNoAi(true);
        RelicEffects.petrifyPermanently(statue);
        helper.assertTrue(
                RelicEffects.isPermanentlyPetrified(statue),
                "the peaceful-persistence probe needs a finished statue");
        helper.assertTrue(
                statue.isPersistenceRequired(),
                "petrification must mark the mob as persistent world content");

        var server = helper.getLevel().getServer();
        Difficulty previous = server.getWorldData().getDifficulty();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        try {
            helper.assertValueEqual(
                    helper.getLevel().getDifficulty(),
                    Difficulty.PEACEFUL,
                    "the probe must run under Peaceful despawn rules");
            statue.checkDespawn();
            helper.assertFalse(
                    statue.isRemoved(),
                    "a Medusa statue must survive Peaceful checkDespawn");
        } finally {
            server.setDifficulty(previous, true);
        }
        helper.succeed();
    }

    private static void medusaStatueRoundTrip(GameTestHelper helper) {
        Rotations raisedArm = new Rotations(-86.0F, 11.0F, 18.0F);
        ArmorStand original = helper.spawn(
                EntityType.ARMOR_STAND,
                new Vec3(2.5, 2.0, 2.5));
        original.setRightArmPose(raisedArm);
        original.setPose(Pose.CROUCHING);
        original.setYRot(17.0F);
        original.yBodyRot = original.yBodyRotO = 29.0F;
        original.yHeadRot = original.yHeadRotO = 51.0F;
        original.walkAnimation.setSpeed(0.36F);
        original.walkAnimation.update(0.36F, 1.0F, 1.0F);
        original.oAttackAnim = original.attackAnim = 0.72F;
        UUID oldUuid = original.getUUID();
        Skeleton attacker = helper.spawn(
                EntityType.SKELETON,
                new Vec3(2.5, 2.0, 5.5));
        attacker.setTarget(original);
        helper.assertValueEqual(
                attacker.getTarget(),
                original,
                "a living creature must be a valid target before petrification");

        RelicEffects.petrifyPermanently(original);
        helper.assertTrue(
                original.isInvulnerable(),
                "a permanent statue must use vanilla invulnerability targeting semantics");
        helper.assertFalse(
                attacker.canAttack(original),
                "mobs must never consider a permanent statue attackable");
        helper.assertTrue(
                attacker.getTarget() == null,
                "a mob already targeting a creature must drop it when it becomes a statue");
        PetrifiedPose frozen = original.getData(EchoesShowThePast.PETRIFIED_POSE.get());
        helper.assertTrue(frozen.permanent(), "a non-player statue must be permanent");
        helper.assertValueEqual(frozen.pose(), Pose.CROUCHING, "the permanent pose must be captured");
        helper.assertValueEqual(
                frozen.animation().attack(),
                0.72F,
                "transient attack pose must be captured");
        original.hurtTime = 7;
        original.hurtDuration = 10;
        frozen.freezeCommon(original);
        helper.assertValueEqual(
                original.hurtTime,
                0,
                "a permanent statue must not freeze a damage flash forever");
        helper.assertValueEqual(
                original.hurtDuration,
                0,
                "a permanent statue must discard the transient damage duration");
        original.hurtTime = 8;
        original.hurtDuration = 10;
        SnapshotEntity legacyDamagedStatue = SnapshotEntityIO.capture(
                        original,
                        original.blockPosition())
                .orElseThrow();
        LivingEntity rememberedStatue = (LivingEntity) SnapshotEntityIO.load(
                        legacyDamagedStatue,
                        helper.getLevel(),
                        original.blockPosition(),
                        false)
                .orElseThrow();
        helper.assertValueEqual(
                rememberedStatue.hurtTime,
                0,
                "loading an old Past Echo statue must sanitize its captured damage flash");
        BakedModelPose bakedPose = new BakedModelPose(List.of(
                new BakedModelPose.Part(
                        "root/body/right_wing",
                        1.0F,
                        2.0F,
                        3.0F,
                        0.35F,
                        -0.42F,
                        0.18F,
                        1.0F,
                        1.0F,
                        1.0F,
                        true,
                        false)));
        frozen = frozen.withModelPose(bakedPose);
        original.setData(EchoesShowThePast.PETRIFIED_POSE.get(), frozen);
        original.addEffect(new MobEffectInstance(
                MobEffects.GLOWING,
                160,
                0,
                false,
                false));
        RelicEffects.onEntityTick(new EntityTickEvent.Pre(original));
        helper.assertFalse(
                original.hasEffect(MobEffects.GLOWING),
                "finite Horus glow must never become permanent on a statue");

        ItemStack statue = PetrifiedMobManager.extract(original).orElseThrow();
        helper.assertTrue(original.isRemoved(), "the extracted entity must be removed");
        helper.assertTrue(
                PetrifiedMobManager.extract(original).isEmpty(),
                "the same statue cannot be extracted twice");
        PetrifiedMobData data = statue.get(EchoesShowThePast.PETRIFIED_MOB_DATA.get());
        helper.assertTrue(data != null, "the statue item must carry complete entity data");
        helper.assertValueEqual(
                data.modelPose(),
                bakedPose,
                "the evaluated bone pose must be stored in the statue item");
        helper.assertTrue(
                statue.has(DataComponents.CUSTOM_NAME)
                        && statue.get(DataComponents.CUSTOM_NAME)
                                .getString()
                                .contains(original.getName().getString()),
                "the statue item name must identify its creature");

        var encoded = PetrifiedMobData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        PetrifiedMobData decoded = PetrifiedMobData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        ArmorStand restored = (ArmorStand) PetrifiedMobManager.place(
                        helper.getLevel(),
                        decoded,
                        new Vec3(5.5, 2.0, 2.5),
                        140.0F)
                .orElseThrow();
        helper.assertFalse(
                restored.getUUID().equals(oldUuid),
                "placing a statue must assign a fresh UUID");
        helper.assertTrue(
                RelicEffects.isPermanentlyPetrified(restored),
                "the placed creature must remain a permanent statue");
        helper.assertValueEqual(restored.getPose(), Pose.CROUCHING, "placed statue pose must survive");
        helper.assertValueEqual(
                restored.getRightArmPose(),
                raisedArm,
                "type-specific armor-stand bone rotations must survive mine and place");
        PetrifiedPose restoredPose = restored.getData(EchoesShowThePast.PETRIFIED_POSE.get());
        helper.assertValueEqual(
                restoredPose.yRot(),
                140.0F,
                "a placed statue must face the placing player");
        helper.assertValueEqual(
                restoredPose.bodyYRot(),
                152.0F,
                "facing the player must rotate the whole frozen pose");
        helper.assertValueEqual(
                restoredPose.headYRot(),
                174.0F,
                "facing the player must preserve the captured head/body offset");
        helper.assertValueEqual(
                restoredPose.modelPose(),
                bakedPose,
                "the exact evaluated bone pose must survive mine, codec and placement");
        helper.assertValueEqual(
                restoredPose.animation().walkPosition(),
                frozen.animation().walkPosition(),
                "limb animation phase must survive mine and place");
        helper.assertValueEqual(
                restoredPose.animation().attack(),
                frozen.animation().attack(),
                "attack pose must survive mine and place");
        helper.assertTrue(
                PetrifiedMobManager.miningIncrement(new ItemStack(Items.WOODEN_PICKAXE)) < 0.10F,
                "a statue must require a visible mining sequence, never one click");
        ArmorStand creativeTarget = helper.spawn(
                EntityType.ARMOR_STAND,
                new Vec3(3.5, 2.0, 2.5));
        RelicEffects.petrifyPermanently(creativeTarget);
        var creativeMiner = helper.makeMockServerPlayerInLevel();
        creativeMiner.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        Vec3 minerPos = creativeTarget.position().add(-1.0, 0.0, 0.0);
        creativeMiner.setPos(minerPos.x, minerPos.y, minerPos.z);
        helper.assertTrue(
                creativeMiner.level() == helper.getLevel(),
                "the creative mining probe must share the statue's GameTest level");
        helper.assertTrue(
                creativeMiner.level().getEntity(creativeTarget.getId()) == creativeTarget,
                "the statue must be addressable by network id from the miner");
        helper.assertTrue(
                creativeMiner.distanceToSqr(creativeTarget) <= 36.0,
                "the creative miner must stand within statue reach");
        helper.assertTrue(
                creativeMiner.isCreative(),
                "the creative mining probe must actually be in Creative");
        helper.assertFalse(
                creativeMiner.getMainHandItem().is(net.minecraft.tags.ItemTags.PICKAXES),
                "creative extraction must not depend on holding a pickaxe");
        PetrifiedMobManager.mine(creativeMiner, creativeTarget.getId());
        helper.assertTrue(
                creativeTarget.isRemoved(),
                "Creative must break a petrified creature instantly into its statue item");
        var miner = helper.makeMockServerPlayerInLevel();
        int inventoryCount = miner.getInventory().countItem(EchoesShowThePast.PETRIFIED_MOB.get());
        var dropped = PetrifiedMobManager.dropStatue(miner, restored).orElseThrow();
        helper.assertValueEqual(
                miner.getInventory().countItem(EchoesShowThePast.PETRIFIED_MOB.get()),
                inventoryCount,
                "mining a statue must not insert it directly into the inventory");
        helper.assertTrue(
                dropped.isAlive()
                        && dropped.getItem().is(EchoesShowThePast.PETRIFIED_MOB.get()),
                "mining must create one physical petrified-creature item entity");
        helper.assertTrue(
                dropped.getItem().has(DataComponents.CUSTOM_NAME),
                "the physical drop must retain the creature name");
        float largeScale = PetrifiedItemLayout.fitScale(8.0F, 20.0F);
        helper.assertTrue(
                20.0F * largeScale <= 0.82F,
                "even exceptionally tall creatures must fit inside the item frame");
        helper.assertTrue(
                PetrifiedItemLayout.fitScale(0.5F, 0.5F)
                        > PetrifiedItemLayout.fitScale(2.0F, 2.0F),
                "small creatures must receive a larger, readable inventory presentation");
        helper.assertValueEqual(
                PetrifiedItemLayout.baseY(20.0F, largeScale)
                        + 20.0F * largeScale * 0.5F,
                0.5F,
                "the fitted creature must remain vertically centered");

        BlockPos oldBed = helper.absolutePos(new BlockPos(1, 2, 4));
        helper.getLevel().setBlock(oldBed, Blocks.RED_BED.defaultBlockState(), 3);
        ArmorStand sleeping = helper.spawn(
                EntityType.ARMOR_STAND,
                new Vec3(1.5, 2.0, 4.5));
        sleeping.startSleeping(oldBed);
        RelicEffects.petrifyPermanently(sleeping);
        ItemStack sleepingStatue = PetrifiedMobManager.extract(sleeping).orElseThrow();
        PetrifiedMobData sleepingData =
                sleepingStatue.get(EchoesShowThePast.PETRIFIED_MOB_DATA.get());
        helper.assertTrue(sleepingData != null, "a sleeping statue must remain serializable");
        helper.assertValueEqual(
                sleepingData.entity().pose(),
                Pose.SLEEPING,
                "the visual sleeping pose must be preserved");
        helper.assertFalse(
                sleepingData.entity().data().contains("sleeping_pos"),
                "a statue item must not retain a live link to its old bed");

        Vec3 sleepingTarget = new Vec3(4.5, 2.0, 4.5);
        ArmorStand restoredSleeping = (ArmorStand) PetrifiedMobManager.place(
                        helper.getLevel(),
                        sleepingData,
                        sleepingTarget,
                        25.0F)
                .orElseThrow();
        PetrifiedPose restoredSleepingPose =
                restoredSleeping.getData(EchoesShowThePast.PETRIFIED_POSE.get());
        helper.assertValueEqual(
                restoredSleepingPose.pose(),
                Pose.SLEEPING,
                "the detached statue must still render its captured sleeping pose");
        helper.assertValueEqual(
                restoredSleeping.getPose(),
                Pose.STANDING,
                "sleeping must be visual-only so it cannot use vanilla's tiny sleeping hitbox");
        helper.assertTrue(
                restoredSleeping.getBbWidth() > 0.2F
                        && restoredSleeping.getBbHeight() > 0.2F,
                "a sleeping statue must keep the creature's full collision box");
        helper.assertTrue(
                restoredSleeping.getSleepingPos().isEmpty(),
                "a placed statue must be detached from all previous beds");
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    restoredSleeping.position().distanceToSqr(sleepingTarget) < 0.0001,
                    "a detached statue must not teleport back to its old interaction");
            helper.succeed();
        });
    }

    private static void holyGrailRitual(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos ritualPosition = helper.absolutePos(new BlockPos(2, 2, 2));
        player.snapTo(
                ritualPosition.getX() + 0.5,
                ritualPosition.getY(),
                ritualPosition.getZ() + 0.5,
                0.0F,
                0.0F);
        player.setHealth(4.0F);
        player.addEffect(new MobEffectInstance(
                MobEffects.POISON,
                200,
                0,
                false,
                false));
        player.addEffect(new MobEffectInstance(
                MobEffects.REGENERATION,
                200,
                0,
                false,
                false));

        ItemStack grail = new ItemStack(EchoesShowThePast.HOLY_GRAIL.get());
        long day = helper.getLevel().getOverworldClockTime() / 24_000L;
        helper.assertValueEqual(
                HolyGrailItem.MAX_CHARGES,
                5,
                "the Grail must hold five uses");
        RelicState partial = new RelicState(
                Optional.of(player.getUUID()),
                1,
                day - 1L,
                0L);
        RelicState recharged =
                HolyGrailItem.rechargeStateForDay(partial, day);
        helper.assertValueEqual(
                recharged.charges(),
                HolyGrailItem.MAX_CHARGES,
                "one valid water recharge must completely fill the Grail");
        helper.assertValueEqual(
                HolyGrailItem.rechargeStateForDay(
                                recharged.withCharges(2, HolyGrailItem.MAX_CHARGES),
                                day)
                        .charges(),
                2,
                "the Grail must not recharge twice on the same day");
        RelicState clockReset = new RelicState(
                Optional.of(player.getUUID()),
                0,
                day + 7L,
                0L);
        RelicState resetRecharge =
                HolyGrailItem.rechargeStateForDay(clockReset, day);
        helper.assertValueEqual(
                resetRecharge.charges(),
                HolyGrailItem.MAX_CHARGES,
                "setting the world clock backwards must not lock Grail recharge for several days");
        helper.assertValueEqual(
                resetRecharge.lastRechargeDay(),
                day,
                "a recharge after a clock reset must adopt the new day identity");
        BlockPos waterTarget = ritualPosition.offset(0, 1, 2);
        helper.getLevel().setBlock(
                waterTarget,
                Blocks.WATER.defaultBlockState(),
                3);
        helper.assertTrue(
                HolyGrailItem.targetedWaterSource(
                                helper.getLevel(),
                                player)
                        .isPresent(),
                "normal use aimed through water must select its source rather than the solid block behind it");
        grail.set(
                EchoesShowThePast.RELIC_STATE.get(),
                new RelicState(
                        Optional.of(player.getUUID()),
                        2,
                        day,
                        0L));
        helper.assertTrue(
                HolyGrailItem.applyRitualEffects(
                        helper.getLevel(),
                        player,
                        grail),
                "a charged Grail must complete its authoritative ritual");

        RelicState spent =
                grail.get(EchoesShowThePast.RELIC_STATE.get());
        helper.assertTrue(spent != null, "the Grail must retain relic state");
        helper.assertValueEqual(
                spent.charges(),
                1,
                "a completed ritual must consume exactly one charge");
        helper.assertValueEqual(
                player.getHealth(),
                16.0F,
                "the Grail must restore exactly six hearts immediately");
        helper.assertFalse(
                player.hasEffect(MobEffects.POISON),
                "the Grail must purge harmful effects");
        helper.assertTrue(
                player.hasEffect(MobEffects.REGENERATION),
                "the Grail must preserve beneficial effects");
        helper.assertTrue(
                player.getCooldowns().isOnCooldown(grail),
                "the completed ritual must apply its short recovery");

        long auraStart =
                player.getData(EchoesShowThePast.GRAIL_AURA_START.get());
        long auraUntil =
                player.getData(EchoesShowThePast.GRAIL_AURA_UNTIL.get());
        helper.assertValueEqual(
                auraUntil - auraStart,
                (long) HolyGrailItem.AURA_TICKS,
                "the synchronized aura must last exactly eight seconds");
        helper.assertTrue(
                HolyGrailItem.CHANNEL_TICKS >= 20,
                "the ritual must have a readable channel instead of firing instantly");

        player.setHealth(10.0F);
        Zombie undead = helper.spawn(
                EntityType.ZOMBIE,
                new Vec3(3.5, 2.0, 2.5));
        undead.setNoAi(true);
        float undeadHealth = undead.getHealth();
        double undeadDistance =
                undead.position().distanceTo(player.position());
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(
                    player.getHealth() > 10.0F,
                    "the persistent aura must continue helping its owner");
            helper.assertTrue(
                    undead.getHealth() < undeadHealth,
                    "the same aura must damage nearby undead");
            helper.assertTrue(
                    undead.position().distanceTo(player.position())
                            > undeadDistance,
                    "the sanctified area must physically expel undead instead of only recolouring or damaging them");
            helper.succeed();
        });
    }

    private static void assertAuthoredSiteResources(GameTestHelper helper) {
        StructureTemplate.StructureBlockInfo barrier = new StructureTemplate.StructureBlockInfo(
                BlockPos.ZERO,
                Blocks.BARRIER.defaultBlockState(),
                null);
        StructureTemplate.StructureBlockInfo carved = BarrierToAirProcessor.INSTANCE.processBlock(
                helper.getLevel(),
                BlockPos.ZERO,
                BlockPos.ZERO,
                barrier,
                barrier,
                new StructurePlaceSettings());
        helper.assertTrue(
                carved != null && carved.state().isAir(),
                "barrier authoring masks must carve air when an Echo site is placed");
        helper.assertValueEqual(
                EchoTemplateProjectionIndex.build(List.of(barrier), List.of()).indexedBlockCount(),
                0,
                "barrier authoring masks must never become Past Echo geometry");
        CompoundTag staleAttachment = new CompoundTag();
        staleAttachment.putIntArray("block_pos", new int[] {3, 70, 636});
        StructureTemplate.StructureEntityInfo hanging =
                new StructureTemplate.StructureEntityInfo(
                        new Vec3(5.96875, 7.5, 9.5),
                        new BlockPos(5, 7, 9),
                        staleAttachment);
        StructureTemplate.StructureEntityInfo rebased =
                BarrierToAirProcessor.INSTANCE.processEntity(
                        helper.getLevel(),
                        BlockPos.ZERO,
                        hanging,
                        hanging,
                        new StructurePlaceSettings(),
                        new StructureTemplate());
        helper.assertValueEqual(
                rebased.nbt.getIntOr("TileX", 0),
                5,
                "structure placement must rebase a hanging entity X attachment");
        helper.assertValueEqual(
                rebased.nbt.getIntOr("TileY", 0),
                7,
                "structure placement must rebase a hanging entity Y attachment");
        helper.assertValueEqual(
                rebased.nbt.getIntOr("TileZ", 0),
                9,
                "structure placement must rebase a hanging entity Z attachment");
        for (EchoSiteType site : EchoSiteType.generatedSites()) {
            String path = site.id().getPath();
            String structurePath =
                    "data/echoes_show_the_past/worldgen/structure/" + path + ".json";
            String setPath =
                    "data/echoes_show_the_past/worldgen/structure_set/" + path + ".json";
            String manifestPath =
                    "data/echoes_show_the_past/echo_sites/" + path + ".json";
            try (InputStream structureStream =
                            EchoGameTests.class.getClassLoader().getResourceAsStream(structurePath);
                    InputStream setStream =
                            EchoGameTests.class.getClassLoader().getResourceAsStream(setPath);
                    InputStream manifestStream =
                            EchoGameTests.class.getClassLoader().getResourceAsStream(manifestPath)) {
                helper.assertTrue(structureStream != null, "missing structure data for " + site.id());
                helper.assertTrue(setStream != null, "missing structure set for " + site.id());
                helper.assertTrue(manifestStream != null, "missing site manifest for " + site.id());
                String structure = new String(
                        structureStream.readAllBytes(),
                        StandardCharsets.UTF_8);
                JsonObject set = JsonParser.parseString(new String(
                                setStream.readAllBytes(),
                                StandardCharsets.UTF_8))
                        .getAsJsonObject();
                var manifest = JsonParser.parseString(new String(
                                manifestStream.readAllBytes(),
                                StandardCharsets.UTF_8));
                EchoSiteType.Definition definition = EchoSiteType.Definition.CODEC
                        .parse(JsonOps.INSTANCE, manifest)
                        .getOrThrow();
                helper.assertTrue(
                        structure.contains("\"type\": \"echoes_show_the_past:echo_site\""),
                        "authored structures must use the generic site type");
                helper.assertTrue(
                        set.getAsJsonObject("placement").get("type").getAsString()
                                .equals("minecraft:random_spread"),
                        "the Resonator requires random_spread placement for " + site.id());
                helper.assertValueEqual(
                        definition.bind(site.id()),
                        site,
                        "manifest data must be the source of truth for " + site.id());
                for (EchoSiteType.LootPlacement placement : site.loot()) {
                    Identifier lootId = placement.lootTable().identifier();
                    String lootPath = "data/"
                            + lootId.getNamespace()
                            + "/loot_table/"
                            + lootId.getPath()
                            + ".json";
                    try (InputStream lootStream =
                            EchoGameTests.class.getClassLoader().getResourceAsStream(lootPath)) {
                        helper.assertTrue(
                                lootStream != null,
                                "missing loot table " + lootId + " for " + site.id());
                        String loot = new String(lootStream.readAllBytes(), StandardCharsets.UTF_8);
                        helper.assertTrue(
                                loot.contains("echoes_show_the_past:past_echo"),
                                lootId + " must be able to drop a Past Echo");
                        helper.assertTrue(
                                loot.contains("echoes_show_the_past:resonant_filament"),
                                lootId + " must be able to drop Resonant Filament");
                        helper.assertFalse(
                                loot.contains("echoes_show_the_past:harmonic_key"),
                                lootId + " must not skip the three-fragment Harmonic Key craft");
                        helper.assertFalse(
                                loot.contains("echoes_show_the_past:dory")
                                        || loot.contains("echoes_show_the_past:khopesh")
                                        || loot.contains("echoes_show_the_past:unknown_medieval_"),
                                lootId + " must not drop Unknown era weapons or armor");
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Could not inspect authored site resources for " + site.id(),
                        exception);
            }
        }
    }

    private static void assertReliquaryRetired(GameTestHelper helper) {
        Identifier reliquaryId = Identifier.fromNamespaceAndPath(
                EchoesShowThePast.MOD_ID,
                "relic_reliquary");
        Identifier filamentId = Identifier.fromNamespaceAndPath(
                EchoesShowThePast.MOD_ID,
                "resonant_filament");
        helper.assertFalse(
                BuiltInRegistries.BLOCK.containsKey(reliquaryId),
                "the retired Relic Reliquary block must not remain registered");
        helper.assertFalse(
                BuiltInRegistries.ITEM.containsKey(reliquaryId),
                "the retired Relic Reliquary item must not remain registered");
        helper.assertFalse(
                BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(reliquaryId),
                "the retired Relic Reliquary block entity must not remain registered");
        helper.assertTrue(
                BuiltInRegistries.ITEM.containsKey(filamentId),
                "Resonant Filament must remain registered when Reliquary is retired");

        ClassLoader resources = EchoGameTests.class.getClassLoader();
        String[] retiredResources = {
            "assets/echoes_show_the_past/blockstates/relic_reliquary.json",
            "assets/echoes_show_the_past/items/relic_reliquary.json",
            "assets/echoes_show_the_past/models/block/relic_reliquary.json",
            "data/echoes_show_the_past/loot_table/blocks/relic_reliquary.json"
        };
        for (String path : retiredResources) {
            helper.assertTrue(
                    resources.getResource(path) == null,
                    "retired Reliquary resource must not be packaged: " + path);
        }

        try (InputStream filamentModel = resources.getResourceAsStream(
                        "assets/echoes_show_the_past/items/resonant_filament.json");
                InputStream harmonicKeyRecipe = resources.getResourceAsStream(
                        "data/echoes_show_the_past/recipe/harmonic_key.json");
                InputStream medusaLoot = resources.getResourceAsStream(
                        "data/echoes_show_the_past/loot_table/entities/medusa.json")) {
            helper.assertTrue(
                    filamentModel != null,
                    "Resonant Filament's item definition must remain packaged");
            helper.assertTrue(
                    harmonicKeyRecipe != null,
                    "the Harmonic Key recipe that consumes Filament must remain packaged");
            helper.assertTrue(
                    medusaLoot != null,
                    "Medusa's replacement reward path must be packaged");
            String recipeSource = new String(
                    harmonicKeyRecipe.readAllBytes(),
                    StandardCharsets.UTF_8);
            String lootSource = new String(
                    medusaLoot.readAllBytes(),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    recipeSource.contains("echoes_show_the_past:resonant_filament"),
                    "retiring Reliquary must not remove Filament from progression");
            helper.assertTrue(
                    lootSource.contains("echoes_show_the_past:medusa_fragment")
                            && lootSource.contains("echoes_show_the_past:medusa_head"),
                    "defeating Medusa must now provide both her progression fragment and living head");
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect the Reliquary retirement assets", exception);
        }
    }

    private static void echoPedestalReseating(GameTestHelper helper) {
        BlockPos firstPedestalPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos secondPedestalPos = helper.absolutePos(new BlockPos(4, 2, 2));
        helper.getLevel().setBlock(
                firstPedestalPos,
                EchoesShowThePast.ECHO_PEDESTAL.get().defaultBlockState(),
                3);
        helper.getLevel().setBlock(
                secondPedestalPos,
                EchoesShowThePast.ECHO_PEDESTAL.get().defaultBlockState(),
                3);
        EchoPedestalBlockEntity firstPedestal = (EchoPedestalBlockEntity)
                helper.getLevel().getBlockEntity(firstPedestalPos);
        EchoPedestalBlockEntity secondPedestal = (EchoPedestalBlockEntity)
                helper.getLevel().getBlockEntity(secondPedestalPos);

        var player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.snapTo(
                firstPedestalPos.getX() + 0.5,
                firstPedestalPos.getY() + 1.0,
                firstPedestalPos.getZ() + 2.5,
                180.0F,
                0.0F);
        BlockHitResult firstHit = new BlockHitResult(
                Vec3.atCenterOf(firstPedestalPos).add(0.0, 0.5, 0.0),
                Direction.UP,
                firstPedestalPos,
                false);
        BlockHitResult secondHit = new BlockHitResult(
                Vec3.atCenterOf(secondPedestalPos).add(0.0, 0.5, 0.0),
                Direction.UP,
                secondPedestalPos,
                false);

        ItemStack emptyFragment = PastEchoMemory.createEmptyFragment();
        player.setItemInHand(InteractionHand.MAIN_HAND, emptyFragment);
        InteractionResult emptyResult = player.gameMode.useItemOn(
                player,
                helper.getLevel(),
                emptyFragment,
                InteractionHand.MAIN_HAND,
                firstHit);
        helper.assertTrue(
                emptyResult.consumesAction(),
                "an empty fragment click must be handled");
        helper.assertFalse(
                firstPedestal.hasEcho(),
                "an empty fragment must not seat on a pedestal");

        BlockPos siteAnchor = firstPedestalPos.offset(8, -4, 3);
        EchoSnapshot sealed = EchoRuinTemplate.createSnapshot(
                helper.getLevel().dimension(),
                siteAnchor);
        helper.assertTrue(
                !sealed.origin().equals(firstPedestalPos)
                        && !sealed.origin().equals(secondPedestalPos),
                "worldgen memories remember the site anchor, not the pedestal block");
        ItemStack sealedFragment = PastEchoMemory.createFragment(sealed, Optional.empty());
        player.setItemInHand(InteractionHand.MAIN_HAND, sealedFragment);
        InteractionResult seatResult = player.gameMode.useItemOn(
                player,
                helper.getLevel(),
                sealedFragment,
                InteractionHand.MAIN_HAND,
                firstHit);
        helper.assertValueEqual(
                seatResult,
                InteractionResult.SUCCESS_SERVER,
                "right-clicking must seat a memory whose origin is not the pedestal");
        helper.assertTrue(
                firstPedestal.hasEcho()
                        && sealed.equals(firstPedestal.echo().get(EchoesShowThePast.ECHO_SNAPSHOT.get())),
                "the first pedestal must hold the seated dungeon memory");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult takeResult = player.gameMode.useItemOn(
                player,
                helper.getLevel(),
                ItemStack.EMPTY,
                InteractionHand.MAIN_HAND,
                firstHit);
        helper.assertValueEqual(
                takeResult,
                InteractionResult.SUCCESS_SERVER,
                "empty-hand click must take the seated fragment");
        helper.assertFalse(firstPedestal.hasEcho(), "taking must empty the pedestal");
        ItemStack recovered = findHeldOrInventoriedFragment(player);
        helper.assertTrue(
                recovered.is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get())
                        && sealed.equals(recovered.get(EchoesShowThePast.ECHO_SNAPSHOT.get())),
                "taking must return the same memory");

        player.setItemInHand(InteractionHand.MAIN_HAND, recovered.copy());
        recovered.setCount(0);
        InteractionResult reseatResult = player.gameMode.useItemOn(
                player,
                helper.getLevel(),
                player.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                secondHit);
        helper.assertValueEqual(
                reseatResult,
                InteractionResult.SUCCESS_SERVER,
                "the recovered memory must seat on a different pedestal");
        helper.assertTrue(
                secondPedestal.hasEcho()
                        && sealed.equals(secondPedestal.echo().get(EchoesShowThePast.ECHO_SNAPSHOT.get())),
                "any pedestal must accept any memory");
        helper.assertFalse(firstPedestal.hasEcho(), "the original pedestal must stay empty");

        EchoSnapshot personal = new EchoSnapshot(
                EchoSnapshot.CURRENT_VERSION,
                helper.getLevel().dimension(),
                firstPedestalPos.offset(12, 0, -6),
                4,
                false,
                List.of(),
                List.of(),
                List.of());
        ItemStack personalFragment = PastEchoMemory.createFragment(
                personal,
                Optional.of(ResonanceColor.AMBER));
        player.setItemInHand(InteractionHand.MAIN_HAND, personalFragment);
        InteractionResult personalResult = player.gameMode.useItemOn(
                player,
                helper.getLevel(),
                personalFragment,
                InteractionHand.MAIN_HAND,
                firstHit);
        helper.assertValueEqual(
                personalResult,
                InteractionResult.SUCCESS_SERVER,
                "a personal captured memory must also seat on any empty pedestal");
        helper.assertTrue(
                firstPedestal.hasEcho()
                        && personal.equals(firstPedestal.echo().get(EchoesShowThePast.ECHO_SNAPSHOT.get())),
                "the empty pedestal must accept a living memory");

        helper.succeed();
    }

    private static ItemStack findHeldOrInventoriedFragment(net.minecraft.server.level.ServerPlayer player) {
        if (player.getMainHandItem().is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get())) {
            return player.getMainHandItem();
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void pastFragmentVessel(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        EchoSnapshot personal = new EchoSnapshot(
                EchoSnapshot.CURRENT_VERSION,
                helper.getLevel().dimension(),
                origin,
                2,
                false,
                List.of(Blocks.STONE.defaultBlockState()),
                List.of(SnapshotBlock.of(0, 0, 0, 0)),
                List.of());

        ItemStack legacy = new ItemStack(EchoesShowThePast.PAST_ECHO.get());
        legacy.set(EchoesShowThePast.ECHO_SNAPSHOT.get(), personal);
        PastEchoMemory.ensureMigrated(legacy);
        helper.assertTrue(
                !legacy.has(EchoesShowThePast.ECHO_SNAPSHOT.get()),
                "legacy vessel snapshots must migrate off the Past Echo");
        helper.assertValueEqual(
                PastEchoMemory.getSnapshot(legacy),
                personal,
                "migrated vessels must keep the original memory inside a fragment");

        ItemStack vessel = new ItemStack(EchoesShowThePast.PAST_ECHO.get());
        PastEchoMemory.setSnapshot(vessel, personal);
        ItemStack extracted = PastEchoMemory.getFragment(vessel).copy();
        PastEchoMemory.clearFragment(vessel);
        helper.assertTrue(
                PastEchoMemory.getSnapshot(vessel) == null,
                "removing the fragment must empty the vessel");
        helper.assertTrue(
                extracted.has(EchoesShowThePast.ECHO_SNAPSHOT.get()),
                "extracted fragments must carry the memory");
        PastEchoMemory.setFragment(vessel, extracted);
        helper.assertValueEqual(
                PastEchoMemory.getSnapshot(vessel),
                personal,
                "reseating a fragment must restore projection memory");

        EchoSnapshot sealed = EchoRuinTemplate.createSnapshot(
                helper.getLevel().dimension(),
                origin);
        ItemStack sealedFragment = PastEchoMemory.createFragment(sealed, Optional.empty());
        helper.assertTrue(sealed.site().isPresent(), "ruin memories must remember their site");
        helper.assertValueEqual(
                PastEchoMemory.resolveColor(sealedFragment),
                EchoSiteType.LEGACY_RUIN.defaultColor(),
                "dungeon fragments must tint from their site color");

        ItemStack erasable = PastEchoMemory.createFragment(personal, Optional.of(ResonanceColor.AMBER));
        PastEchoMemory.purgeFragmentMemory(erasable);
        helper.assertTrue(
                !erasable.has(EchoesShowThePast.ECHO_SNAPSHOT.get()),
                "the forget ritual must empty non-sealed fragments");
        helper.assertTrue(
                !erasable.has(EchoesShowThePast.RESONANCE_COLOR.get()),
                "forgetting must also clear the personal resonance color");
        helper.assertTrue(
                PastEchoMemory.createEmptyFragment().getMaxStackSize() > 1,
                "empty fragment shells must be stackable");
        helper.assertValueEqual(
                erasable.getMaxStackSize(),
                PastEchoMemory.createEmptyFragment().getMaxStackSize(),
                "purged fragments must recover the empty-shell stack size");

        ItemStack sealedCopy = sealedFragment.copy();
        PastEchoMemory.purgeFragmentMemory(sealedCopy);
        helper.assertTrue(
                sealedCopy.has(EchoesShowThePast.ECHO_SNAPSHOT.get()),
                "sealed dungeon fragments must refuse the forget ritual");

        ItemStack blankSocket = new ItemStack(EchoesShowThePast.PAST_ECHO.get());
        PastEchoMemory.setFragment(blankSocket, PastEchoMemory.createEmptyFragment());
        PastEchoMemory.setSnapshot(blankSocket, personal);
        helper.assertValueEqual(
                PastEchoMemory.getSnapshot(blankSocket),
                personal,
                "capturing into an empty seated fragment must refill that shell");

        BlockPos pedestalPos = origin;
        helper.getLevel().setBlock(
                pedestalPos,
                EchoesShowThePast.ECHO_PEDESTAL.get().defaultBlockState()
                        .setValue(EchoPedestalBlock.SPENT, true),
                3);
        EchoPedestalBlockEntity pedestalEntity =
                (EchoPedestalBlockEntity) helper.getLevel().getBlockEntity(pedestalPos);
        ItemStack sealedFragmentOnly = PastEchoMemory.createFragment(sealed, Optional.empty());
        pedestalEntity.setEcho(PastEchoMemory.createSealedVessel(sealed));
        helper.assertTrue(
                pedestalEntity.hasEcho()
                        && pedestalEntity.echo().is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get()),
                "legacy pedestal vessels must normalize to sealed Past Fragments");
        ItemStack reclaimed = pedestalEntity.removeEcho();
        helper.assertTrue(
                reclaimed.is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get())
                        && reclaimed.has(EchoesShowThePast.ECHO_SNAPSHOT.get()),
                "removing from a pillar must return the seated fragment");
        pedestalEntity.setEcho(sealedFragmentOnly.copy());
        helper.assertTrue(
                pedestalEntity.echo().is(EchoesShowThePast.PAST_FRAGMENT_ITEM.get()),
                "echo pillars must accept sealed Past Fragments directly");

        helper.succeed();
    }

    private EchoGameTests() {
    }
}
