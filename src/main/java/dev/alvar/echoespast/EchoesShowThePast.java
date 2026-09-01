package dev.alvar.echoespast;

import com.mojang.serialization.Codec;
import dev.alvar.echoespast.item.PastEchoItem;
import dev.alvar.echoespast.item.PastFragmentItem;
import dev.alvar.echoespast.item.LowFrequencyResonatorItem;
import dev.alvar.echoespast.item.PetrifiedMobItem;
import dev.alvar.echoespast.item.ResonatorModuleItem;
import dev.alvar.echoespast.item.UnknownMedievalArmorItem;
import dev.alvar.echoespast.command.EchoCommands;
import dev.alvar.echoespast.menu.PastEchoMenu;
import dev.alvar.echoespast.menu.ResonatorMenu;
import dev.alvar.echoespast.resonance.ResonanceColor;
import dev.alvar.echoespast.resonance.ResonanceKnowledge;
import dev.alvar.echoespast.resonance.EchoSiteCatalog;
import dev.alvar.echoespast.resonance.ResonatorLoadout;
import dev.alvar.echoespast.block.EchoPedestalBlock;
import dev.alvar.echoespast.block.EchoPedestalBlockEntity;
import dev.alvar.echoespast.block.BigEchoPedestalBlock;
import dev.alvar.echoespast.block.BigEchoPedestalBlockEntity;
import dev.alvar.echoespast.block.CryptSealBlock;
import dev.alvar.echoespast.block.TimelessPortalBlock;
import dev.alvar.echoespast.entity.DungeonPickupEntity;
import dev.alvar.echoespast.entity.MedusaEntity;
import dev.alvar.echoespast.entity.MedievalRubbleProjectile;
import dev.alvar.echoespast.entity.UnknownEntity;
import dev.alvar.echoespast.entity.SpectralHopliteEntity;
import dev.alvar.echoespast.relic.EyeOfHorusItem;
import dev.alvar.echoespast.relic.EyeRevealManager;
import dev.alvar.echoespast.relic.HolyGrailItem;
import dev.alvar.echoespast.relic.MedusaHeadItem;
import dev.alvar.echoespast.relic.PetrifiedMedusaHeadItem;
import dev.alvar.echoespast.relic.PhilosophersStoneItem;
import dev.alvar.echoespast.relic.PetrifiedMobData;
import dev.alvar.echoespast.relic.PetrifiedPose;
import dev.alvar.echoespast.relic.RelicEffects;
import dev.alvar.echoespast.relic.RelicReturnManager;
import dev.alvar.echoespast.relic.RelicState;
import dev.alvar.echoespast.network.EchoFadeDebugPayload;
import dev.alvar.echoespast.network.EchoStatePayload;
import dev.alvar.echoespast.network.EyeOfHorusVisualPayload;
import dev.alvar.echoespast.network.HolyGrailVisualPayload;
import dev.alvar.echoespast.network.EyeHazardSignalsPayload;
import dev.alvar.echoespast.network.LowFrequencyPulseResultPayload;
import dev.alvar.echoespast.network.LowFrequencyPulseStartPayload;
import dev.alvar.echoespast.network.LowFrequencyPulseCancelPayload;
import dev.alvar.echoespast.network.RelicControlPayload;
import dev.alvar.echoespast.network.MedusaGazeVisualPayload;
import dev.alvar.echoespast.network.MedusaHeadPoseDebugPayload;
import dev.alvar.echoespast.network.MedusaPetrifyPayload;
import dev.alvar.echoespast.network.PetrifiedMobMinePayload;
import dev.alvar.echoespast.network.PetrifiedMobMiningVisualPayload;
import dev.alvar.echoespast.network.PetrifiedPoseCapturePayload;
import dev.alvar.echoespast.network.PhilosophersStoneVisualPayload;
import dev.alvar.echoespast.network.PhilosophersStoneVisualProgressPayload;
import dev.alvar.echoespast.network.UnknownAltarFragmentExplodePayload;
import dev.alvar.echoespast.network.UnknownEnterCinematicPayload;
import dev.alvar.echoespast.network.UnknownBossBarPayload;
import dev.alvar.echoespast.network.UnknownCombatImpactPayload;
import dev.alvar.echoespast.server.EchoProjectionManager;
import dev.alvar.echoespast.server.LowFrequencySonarManager;
import dev.alvar.echoespast.server.RelicControlManager;
import dev.alvar.echoespast.server.MaterializedEchoManager;
import dev.alvar.echoespast.server.ResonanceDiscoveryManager;
import dev.alvar.echoespast.server.UnknownFightManager;
import dev.alvar.echoespast.server.ArenaReconstructionWave;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import dev.alvar.echoespast.snapshot.EchoSnapshotStreamCodec;
import dev.alvar.echoespast.snapshot.EchoTemplateResolver;
import dev.alvar.echoespast.world.CryptAccessGate;
import dev.alvar.echoespast.world.EchoPedestalIndex;
import dev.alvar.echoespast.world.EchoSitePiece;
import dev.alvar.echoespast.world.EchoSiteStructure;
import dev.alvar.echoespast.gametest.EchoGameTests;
import dev.alvar.echoespast.recipe.PastFragmentForgetRecipe;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(EchoesShowThePast.MOD_ID)
public final class EchoesShowThePast {
    public static final String MOD_ID = "echoes_show_the_past";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EchoSnapshot>> ECHO_SNAPSHOT =
            COMPONENTS.registerComponentType("echo_snapshot", builder -> builder
                    .persistent(EchoSnapshot.CODEC)
                    .networkSynchronized(EchoSnapshotStreamCodec.STREAM_CODEC)
                    .cacheEncoding());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>>
            PAST_FRAGMENT = COMPONENTS.registerComponentType(
                    "past_fragment",
                    builder -> builder
                            .persistent(ItemContainerContents.CODEC)
                            .networkSynchronized(ItemContainerContents.STREAM_CODEC)
                            .cacheEncoding());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResonanceColor>>
            RESONANCE_COLOR = COMPONENTS.registerComponentType(
                    "resonance_color",
                    builder -> builder
                            .persistent(ResonanceColor.CODEC)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.fromCodec(
                                    ResonanceColor.CODEC))
                            .cacheEncoding());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResonatorLoadout>>
            RESONATOR_LOADOUT = COMPONENTS.registerComponentType(
                    "resonator_loadout",
                    builder -> builder
                            .persistent(ResonatorLoadout.CODEC)
                            .cacheEncoding());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RelicState>>
            RELIC_STATE = COMPONENTS.registerComponentType(
                    "relic_state",
                    builder -> builder
                            .persistent(RelicState.CODEC)
                            .cacheEncoding());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PetrifiedMobData>>
            PETRIFIED_MOB_DATA = COMPONENTS.registerComponentType(
                    "petrified_mob",
                    builder -> builder
                            .persistent(PetrifiedMobData.CODEC)
                            .cacheEncoding());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ResonanceKnowledge>>
            RESONANCE_KNOWLEDGE = ATTACHMENTS.register(
                    "resonance_knowledge",
                    () -> AttachmentType.builder(() -> ResonanceKnowledge.EMPTY)
                            .serialize(ResonanceKnowledge.CODEC.fieldOf("knowledge"))
                            .copyOnDeath()
                            .sync(
                                    (holder, player) -> holder == player,
                                    net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(
                                            ResonanceKnowledge.CODEC))
                            .build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PetrifiedPose>>
            PETRIFIED_POSE = ATTACHMENTS.register(
                    "petrified_pose",
                    () -> AttachmentType.builder(() -> PetrifiedPose.EMPTY)
                            .serialize(
                                    PetrifiedPose.CODEC.fieldOf("statue"),
                                    PetrifiedPose::permanent)
                            .sync(net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(
                                    PetrifiedPose.CODEC))
                            .build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>>
            HORUS_AURA_START = ATTACHMENTS.register(
                    "horus_aura_start",
                    () -> AttachmentType.builder(() -> 0L)
                            .sync(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG)
                            .build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>>
            HORUS_AURA_UNTIL = ATTACHMENTS.register(
                    "horus_aura_until",
                    () -> AttachmentType.builder(() -> 0L)
                            .sync(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG)
                            .build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>>
            GRAIL_AURA_START = ATTACHMENTS.register(
                    "grail_aura_start",
                    () -> AttachmentType.builder(() -> 0L)
                            .sync(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG)
                            .build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>>
            GRAIL_AURA_UNTIL = ATTACHMENTS.register(
                    "grail_aura_until",
                    () -> AttachmentType.builder(() -> 0L)
                            .sync(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG)
                            .build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GlobalPos>>
            TIMELESS_RETURN = ATTACHMENTS.register(
                    "timeless_return",
                    () -> AttachmentType.builder(() -> GlobalPos.of(
                                    net.minecraft.world.level.Level.OVERWORLD,
                                    net.minecraft.core.BlockPos.ZERO))
                            .serialize(GlobalPos.CODEC.fieldOf("return"))
                            .copyOnDeath()
                            .build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<List<GlobalPos>>>
            TIMELESS_CONSUMED_PORTAL = ATTACHMENTS.register(
                    "timeless_consumed_portal",
                    () -> AttachmentType.builder(() -> List.<GlobalPos>of())
                            .serialize(GlobalPos.CODEC.listOf().fieldOf("cells"))
                            .copyOnDeath()
                            .build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>>
            TIMELESS_DEATH_RETURN = ATTACHMENTS.register(
                    "timeless_death_return",
                    () -> AttachmentType.builder(() -> false)
                            .serialize(Codec.BOOL.fieldOf("pending"))
                            .copyOnDeath()
                            .build());

    public static final DeferredHolder<EntityType<?>, EntityType<UnknownEntity>> UNKNOWN =
            ENTITY_TYPES.registerEntityType(
                    "unknown",
                    UnknownEntity::new,
                    MobCategory.MONSTER,
                    builder -> builder.sized(0.75F, 2.25F).clientTrackingRange(10));
    public static final DeferredHolder<EntityType<?>, EntityType<MedusaEntity>> MEDUSA =
            ENTITY_TYPES.registerEntityType(
                    "medusa",
                    MedusaEntity::new,
                    MobCategory.MONSTER,
                    builder -> builder
                            .sized(0.9F, 2.8F)
                            .eyeHeight(2.45F)
                            .clientTrackingRange(12));
    public static final DeferredHolder<EntityType<?>, EntityType<SpectralHopliteEntity>>
            SPECTRAL_HOPLITE = ENTITY_TYPES.registerEntityType(
                    "spectral_hoplite",
                    SpectralHopliteEntity::new,
                    MobCategory.MISC,
                    builder -> builder
                            .sized(0.65F, 2.0F)
                            .clientTrackingRange(12)
                            .updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<MedievalRubbleProjectile>>
            MEDIEVAL_RUBBLE_PROJECTILE = ENTITY_TYPES.registerEntityType(
                    "medieval_rubble_projectile",
                    MedievalRubbleProjectile::new,
                    MobCategory.MISC,
                    builder -> builder
                            .sized(0.42F, 0.42F)
                            .clientTrackingRange(8)
                            .updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<DungeonPickupEntity>>
            DUNGEON_PICKUP = ENTITY_TYPES.registerEntityType(
                    "dungeon_pickup",
                    DungeonPickupEntity::new,
                    MobCategory.MISC,
                    builder -> builder
                            .sized(0.45F, 0.45F)
                            .eyeHeight(0.225F)
                            .clientTrackingRange(8)
                            .updateInterval(Integer.MAX_VALUE));

    public static final DeferredItem<PastEchoItem> PAST_ECHO = ITEMS.registerItem(
            "past_echo",
            PastEchoItem::new,
            properties -> properties.stacksTo(1));
    public static final DeferredItem<PastFragmentItem> PAST_FRAGMENT_ITEM = ITEMS.registerItem(
            "past_fragment",
            PastFragmentItem::new,
            // Empty shells stack; filled memories force MAX_STACK_SIZE=1 via component.
            properties -> properties.stacksTo(64));
    public static final DeferredItem<LowFrequencyResonatorItem> LOW_FREQUENCY_RESONATOR = ITEMS.registerItem(
            "low_frequency_resonator",
            LowFrequencyResonatorItem::new,
            properties -> properties
                    .stacksTo(1)
                    .component(RESONATOR_LOADOUT.get(), ResonatorLoadout.EMPTY));
    public static final DeferredItem<net.minecraft.world.item.Item> RESONANT_FILAMENT =
            ITEMS.registerSimpleItem("resonant_filament");
    public static final DeferredItem<ResonatorModuleItem> RANGE_COIL = ITEMS.registerItem(
            "range_coil",
            properties -> new ResonatorModuleItem(properties, "range_coil"),
            properties -> properties.stacksTo(16));
    public static final DeferredItem<ResonatorModuleItem> DIRECTIONAL_MATRIX = ITEMS.registerItem(
            "directional_matrix",
            properties -> new ResonatorModuleItem(properties, "directional_matrix"),
            properties -> properties.stacksTo(16));
    public static final DeferredItem<ResonatorModuleItem> CYCLE_REGULATOR = ITEMS.registerItem(
            "cycle_regulator",
            properties -> new ResonatorModuleItem(properties, "cycle_regulator"),
            properties -> properties.stacksTo(16));
    public static final DeferredItem<ResonatorModuleItem> HARMONIC_DECODER = ITEMS.registerItem(
            "harmonic_decoder",
            properties -> new ResonatorModuleItem(properties, "harmonic_decoder"),
            properties -> properties.stacksTo(1));
    public static final DeferredItem<ResonatorModuleItem> HARMONIC_KEY = ITEMS.registerItem(
            "harmonic_key",
            properties -> new ResonatorModuleItem(properties, "harmonic_key"),
            properties -> properties.stacksTo(1).fireResistant());
    public static final DeferredItem<net.minecraft.world.item.Item> HORUS_FRAGMENT =
            ITEMS.registerSimpleItem("horus_fragment", properties -> properties.fireResistant());
    public static final DeferredItem<net.minecraft.world.item.Item> MEDUSA_FRAGMENT =
            ITEMS.registerSimpleItem("medusa_fragment", properties -> properties.fireResistant());
    public static final DeferredItem<net.minecraft.world.item.Item> GRAIL_FRAGMENT =
            ITEMS.registerSimpleItem("grail_fragment", properties -> properties.fireResistant());
    public static final DeferredItem<net.minecraft.world.item.Item> DORY =
            ITEMS.registerSimpleItem(
                    "dory",
                    properties -> properties
                            // Boss-only Greek prop. Kept registered so the Unknown
                            // can hold it; it is not a player drop or creative item.
                            .spear(
                                    ToolMaterial.IRON,
                                    0.95F,
                                    0.95F,
                                    0.6F,
                                    2.5F,
                                    11.0F,
                                    6.75F,
                                    5.1F,
                                    11.25F,
                                    4.6F)
                            .rarity(Rarity.UNCOMMON));
    public static final DeferredItem<net.minecraft.world.item.Item> KHOPESH =
            ITEMS.registerSimpleItem(
                    "khopesh",
                    properties -> properties
                            // Boss-only Egyptian prop. Not listed for players.
                            .sword(ToolMaterial.IRON, 3.0F, -2.4F)
                            .rarity(Rarity.UNCOMMON));
    /** Boss-only prop. Its placeholder model is isolated under models/item/internal/. */
    public static final DeferredItem<net.minecraft.world.item.Item> UNKNOWN_MEDIEVAL_SWORD =
            ITEMS.registerSimpleItem(
                    "unknown_medieval_sword",
                    properties -> properties
                            .sword(ToolMaterial.IRON, 3.0F, -2.4F)
                            .stacksTo(1));
    /** Boss-only prop; defense is controlled by the server combat state. */
    public static final DeferredItem<net.minecraft.world.item.Item> UNKNOWN_MEDIEVAL_SHIELD =
            ITEMS.registerSimpleItem(
                    "unknown_medieval_shield",
                    properties -> properties.stacksTo(1));
    public static final DeferredItem<EyeOfHorusItem> EYE_OF_HORUS = ITEMS.registerItem(
            "eye_of_horus",
            EyeOfHorusItem::new,
            properties -> properties
                    .stacksTo(1)
                    .fireResistant()
                    .component(RELIC_STATE.get(), RelicState.EMPTY));
    public static final DeferredItem<MedusaHeadItem> MEDUSA_HEAD = ITEMS.registerItem(
            "medusa_head",
            MedusaHeadItem::new,
            properties -> properties
                    .stacksTo(1)
                    .fireResistant()
                    .component(RELIC_STATE.get(), RelicState.EMPTY));
    public static final DeferredItem<PetrifiedMedusaHeadItem> MEDUSA_PETRIFIED_HEAD = ITEMS.registerItem(
            "medusa_petrified_head",
            PetrifiedMedusaHeadItem::new,
            properties -> properties
                    .stacksTo(1)
                    .fireResistant()
                    .durability(PetrifiedMedusaHeadItem.MAX_USES)
                    .component(RELIC_STATE.get(), RelicState.EMPTY));
    public static final DeferredItem<PetrifiedMobItem> PETRIFIED_MOB = ITEMS.registerItem(
            "petrified_mob",
            PetrifiedMobItem::new,
            properties -> properties.stacksTo(1).fireResistant());
    public static final DeferredItem<HolyGrailItem> HOLY_GRAIL = ITEMS.registerItem(
            "holy_grail",
            HolyGrailItem::new,
            properties -> properties
                    .stacksTo(1)
                    .fireResistant()
                    .component(RELIC_STATE.get(), RelicState.EMPTY));
    public static final DeferredItem<PhilosophersStoneItem> PHILOSOPHERS_STONE = ITEMS.registerItem(
            "philosophers_stone",
            PhilosophersStoneItem::new,
            properties -> properties
                    .stacksTo(1)
                    .fireResistant()
                    .component(RELIC_STATE.get(), RelicState.EMPTY));
    /** Boss-only panoply piece. Kept registered so the Unknown can wear it. */
    public static final DeferredItem<UnknownMedievalArmorItem> UNKNOWN_MEDIEVAL_HELMET =
            ITEMS.registerItem(
                    "unknown_medieval_helmet",
                    UnknownMedievalArmorItem::new,
                    properties -> properties
                            .humanoidArmor(UnknownMedievalArmorItem.MATERIAL, ArmorType.HELMET)
                            .rarity(Rarity.UNCOMMON));
    public static final DeferredItem<UnknownMedievalArmorItem> UNKNOWN_MEDIEVAL_CHESTPLATE =
            ITEMS.registerItem(
                    "unknown_medieval_chestplate",
                    UnknownMedievalArmorItem::new,
                    properties -> properties
                            .humanoidArmor(
                                    UnknownMedievalArmorItem.MATERIAL, ArmorType.CHESTPLATE)
                            .rarity(Rarity.UNCOMMON));
    public static final DeferredItem<UnknownMedievalArmorItem> UNKNOWN_MEDIEVAL_LEGGINGS =
            ITEMS.registerItem(
                    "unknown_medieval_leggings",
                    UnknownMedievalArmorItem::new,
                    properties -> properties
                            .humanoidArmor(UnknownMedievalArmorItem.MATERIAL, ArmorType.LEGGINGS)
                            .rarity(Rarity.UNCOMMON));
    public static final DeferredItem<UnknownMedievalArmorItem> UNKNOWN_MEDIEVAL_BOOTS =
            ITEMS.registerItem(
                    "unknown_medieval_boots",
                    UnknownMedievalArmorItem::new,
                    properties -> properties
                            .humanoidArmor(UnknownMedievalArmorItem.MATERIAL, ArmorType.BOOTS)
                            .rarity(Rarity.UNCOMMON));
    public static final DeferredHolder<MenuType<?>, MenuType<ResonatorMenu>> RESONATOR_MENU =
            MENU_TYPES.register(
                    "resonator",
                    () -> new MenuType<>(ResonatorMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<PastEchoMenu>> PAST_ECHO_MENU =
            MENU_TYPES.register(
                    "past_echo",
                    () -> new MenuType<>(PastEchoMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PastFragmentForgetRecipe>>
            PAST_FRAGMENT_FORGET_SERIALIZER = RECIPE_SERIALIZERS.register(
                    "past_fragment_forget",
                    () -> PastFragmentForgetRecipe.SERIALIZER);

    public static final DeferredBlock<EchoPedestalBlock> ECHO_PEDESTAL = BLOCKS.registerBlock(
            "echo_pedestal",
            EchoPedestalBlock::new,
            properties -> properties
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(2.8F, 6.0F)
                    .sound(SoundType.DEEPSLATE)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> ECHO_PEDESTAL_ITEM =
            ITEMS.registerSimpleBlockItem("echo_pedestal", ECHO_PEDESTAL);
    public static final DeferredBlock<BigEchoPedestalBlock> BIG_ECHO_PEDESTAL = BLOCKS.registerBlock(
            "big_echo_pedestal",
            BigEchoPedestalBlock::new,
            properties -> properties
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.0F, 1_200.0F)
                    .sound(SoundType.DEEPSLATE)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> BIG_ECHO_PEDESTAL_ITEM =
            ITEMS.registerSimpleBlockItem("big_echo_pedestal", BIG_ECHO_PEDESTAL);
    /** Internal worldgen-only seal: no item, recipe, creative entry or loot. */
    public static final DeferredBlock<CryptSealBlock> CRYPT_SEAL =
            BLOCKS.registerBlock(
                    "crypt_seal",
                    CryptSealBlock::new,
                    properties -> properties
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(-1.0F, 3_600_000.0F)
                            .sound(SoundType.AMETHYST)
                            .lightLevel(state -> 5)
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));
    public static final DeferredBlock<TimelessPortalBlock> TIMELESS_PORTAL = BLOCKS.registerBlock(
            "timeless_portal",
            TimelessPortalBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 8)
                    .noOcclusion()
                    .noCollision());
    public static final DeferredItem<BlockItem> TIMELESS_PORTAL_ITEM =
            ITEMS.registerSimpleBlockItem("timeless_portal", TIMELESS_PORTAL);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB =
            CREATIVE_MODE_TABS.register(
                    "past_echoes",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.echoes_show_the_past"))
                            .icon(() -> PAST_ECHO.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                output.accept(PAST_ECHO.get());
                                output.accept(PAST_FRAGMENT_ITEM.get());
                                output.accept(LOW_FREQUENCY_RESONATOR.get());
                                output.accept(ECHO_PEDESTAL_ITEM.get());
                                output.accept(BIG_ECHO_PEDESTAL_ITEM.get());
                                output.accept(TIMELESS_PORTAL_ITEM.get());
                                output.accept(RESONANT_FILAMENT.get());
                                output.accept(RANGE_COIL.get());
                                output.accept(DIRECTIONAL_MATRIX.get());
                                output.accept(CYCLE_REGULATOR.get());
                                output.accept(HARMONIC_DECODER.get());
                                output.accept(HARMONIC_KEY.get());
                                output.accept(HORUS_FRAGMENT.get());
                                output.accept(MEDUSA_FRAGMENT.get());
                                output.accept(GRAIL_FRAGMENT.get());
                                output.accept(EYE_OF_HORUS.get());
                                output.accept(MEDUSA_HEAD.get());
                                output.accept(MEDUSA_PETRIFIED_HEAD.get());
                                output.accept(HOLY_GRAIL.get());
                                output.accept(PHILOSOPHERS_STONE.get());
                            })
                            .build());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EchoPedestalBlockEntity>>
            ECHO_PEDESTAL_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
                    "echo_pedestal",
                    () -> new BlockEntityType<>(
                            EchoPedestalBlockEntity::new,
                            ECHO_PEDESTAL.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BigEchoPedestalBlockEntity>>
            BIG_ECHO_PEDESTAL_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
                    "big_echo_pedestal",
                    () -> new BlockEntityType<>(
                            BigEchoPedestalBlockEntity::new,
                            BIG_ECHO_PEDESTAL.get()));
    public static final DeferredHolder<StructureType<?>, StructureType<EchoSiteStructure>>
            ECHO_SITE_STRUCTURE_TYPE = STRUCTURE_TYPES.register(
                    "echo_site",
                    () -> () -> EchoSiteStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType>
            ECHO_SITE_PIECE_TYPE = STRUCTURE_PIECE_TYPES.register(
                    "echo_site",
                    () -> (StructurePieceType.StructureTemplateType) EchoSitePiece::new);
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_IMPULSE = sound("echo_impulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_SWEEP = sound("echo_sweep");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_RETURN = sound("echo_return");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOW_FREQUENCY_IMPULSE =
            sound("low_frequency_impulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> PEDESTAL_PING =
            fixedRangeSound("pedestal_ping", 8_192.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> LOW_FREQUENCY_RETURN =
            sound("low_frequency_return");
    public static final DeferredHolder<SoundEvent, SoundEvent> UNKNOWN_MEDIEVAL_SWORD_ATTACK =
            sound("unknown_medieval_sword_attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> UNKNOWN_MEDIEVAL_SWORD_CLASH =
            sound("unknown_medieval_sword_clash");

    static {
        EchoGameTests.register(TEST_FUNCTIONS);
    }

    public EchoesShowThePast(IEventBus modBus, ModContainer container) {
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        MENU_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        CREATIVE_MODE_TABS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECE_TYPES.register(modBus);
        TEST_FUNCTIONS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(this::registerPayloads);
        modBus.addListener(this::addCreativeTabContents);
        modBus.addListener(this::registerEntityAttributes);

        NeoForge.EVENT_BUS.register(EchoProjectionManager.class);
        NeoForge.EVENT_BUS.register(LowFrequencySonarManager.class);
        NeoForge.EVENT_BUS.register(CryptAccessGate.class);
        NeoForge.EVENT_BUS.register(EchoPedestalIndex.class);
        NeoForge.EVENT_BUS.register(RelicEffects.class);
        NeoForge.EVENT_BUS.register(EyeRevealManager.class);
        NeoForge.EVENT_BUS.register(RelicReturnManager.class);
        NeoForge.EVENT_BUS.register(MaterializedEchoManager.class);
        NeoForge.EVENT_BUS.register(ResonanceDiscoveryManager.class);
        NeoForge.EVENT_BUS.register(EchoSiteCatalog.class);
        NeoForge.EVENT_BUS.register(EchoTemplateResolver.class);
        NeoForge.EVENT_BUS.register(UnknownFightManager.class);
        NeoForge.EVENT_BUS.register(ArenaReconstructionWave.class);
        NeoForge.EVENT_BUS.addListener(EchoCommands::register);
        container.registerConfig(ModConfig.Type.COMMON, EchoesConfig.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, EchoesConfig.CLIENT_SPEC);
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.TOOLS_AND_UTILITIES) {
            return;
        }

        event.accept(LOW_FREQUENCY_RESONATOR);
        event.accept(RANGE_COIL);
        event.accept(DIRECTIONAL_MATRIX);
        event.accept(CYCLE_REGULATOR);
        event.accept(HARMONIC_DECODER);
        event.accept(HARMONIC_KEY);
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(UNKNOWN.get(), UnknownEntity.createAttributes().build());
        event.put(MEDUSA.get(), MedusaEntity.createAttributes().build());
        event.put(SPECTRAL_HOPLITE.get(), SpectralHopliteEntity.createAttributes().build());
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToClient(EchoStatePayload.TYPE, EchoStatePayload.STREAM_CODEC);
        registrar.playToClient(EchoFadeDebugPayload.TYPE, EchoFadeDebugPayload.STREAM_CODEC);
        registrar.playToClient(
                MedusaHeadPoseDebugPayload.TYPE,
                MedusaHeadPoseDebugPayload.STREAM_CODEC);
        registrar.playToClient(EyeOfHorusVisualPayload.TYPE, EyeOfHorusVisualPayload.STREAM_CODEC);
        registrar.playToClient(HolyGrailVisualPayload.TYPE, HolyGrailVisualPayload.STREAM_CODEC);
        registrar.playToClient(
                PhilosophersStoneVisualPayload.TYPE,
                PhilosophersStoneVisualPayload.STREAM_CODEC);
        registrar.playToClient(
                PhilosophersStoneVisualProgressPayload.TYPE,
                PhilosophersStoneVisualProgressPayload.STREAM_CODEC);
        registrar.playToClient(EyeHazardSignalsPayload.TYPE, EyeHazardSignalsPayload.STREAM_CODEC);
        registrar.playToClient(MedusaGazeVisualPayload.TYPE, MedusaGazeVisualPayload.STREAM_CODEC);
        registrar.playToClient(MedusaPetrifyPayload.TYPE, MedusaPetrifyPayload.STREAM_CODEC);
        registrar.playToClient(
                PetrifiedMobMiningVisualPayload.TYPE,
                PetrifiedMobMiningVisualPayload.STREAM_CODEC);
        registrar.playToServer(
                PetrifiedMobMinePayload.TYPE,
                PetrifiedMobMinePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        dev.alvar.echoespast.relic.PetrifiedMobManager.mine(
                                player,
                                payload.entityId());
                    }
                });
        registrar.playToServer(
                PetrifiedPoseCapturePayload.TYPE,
                PetrifiedPoseCapturePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        dev.alvar.echoespast.relic.PetrifiedMobManager.acceptModelPose(
                                player,
                                payload.entityId(),
                                payload.modelPose());
                    }
                });
        registrar.playToClient(LowFrequencyPulseStartPayload.TYPE, LowFrequencyPulseStartPayload.STREAM_CODEC);
        registrar.playToClient(LowFrequencyPulseResultPayload.TYPE, LowFrequencyPulseResultPayload.STREAM_CODEC);
        registrar.playToClient(UnknownBossBarPayload.TYPE, UnknownBossBarPayload.STREAM_CODEC);
        registrar.playToClient(
                UnknownCombatImpactPayload.TYPE,
                UnknownCombatImpactPayload.STREAM_CODEC);
        registrar.playToClient(
                UnknownAltarFragmentExplodePayload.TYPE,
                UnknownAltarFragmentExplodePayload.STREAM_CODEC);
        registrar.playToClient(
                UnknownEnterCinematicPayload.TYPE,
                UnknownEnterCinematicPayload.STREAM_CODEC);
        registrar.playToServer(
                RelicControlPayload.TYPE,
                RelicControlPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        RelicControlManager.handle(
                                player,
                                payload.offHand()
                                        ? net.minecraft.world.InteractionHand.OFF_HAND
                                        : net.minecraft.world.InteractionHand.MAIN_HAND);
                    }
                });
        registrar.playBidirectional(
                LowFrequencyPulseCancelPayload.TYPE,
                LowFrequencyPulseCancelPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        LowFrequencySonarManager.cancel(player, payload.pulseId());
                    }
                });
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> fixedRangeSound(String name, float range) {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, name);
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(id, range));
    }
}
