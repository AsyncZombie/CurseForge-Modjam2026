package dev.alvar.echoespast.client;

import dev.alvar.echoespast.visual.EchoPastLight;
import com.google.common.reflect.TypeToken;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.network.EchoFadeDebugPayload;
import dev.alvar.echoespast.network.EchoStatePayload;
import dev.alvar.echoespast.network.EyeOfHorusVisualPayload;
import dev.alvar.echoespast.network.EyeHazardSignalsPayload;
import dev.alvar.echoespast.network.HolyGrailVisualPayload;
import dev.alvar.echoespast.network.LowFrequencyPulseResultPayload;
import dev.alvar.echoespast.network.LowFrequencyPulseStartPayload;
import dev.alvar.echoespast.network.LowFrequencyPulseCancelPayload;
import dev.alvar.echoespast.network.MedusaGazeVisualPayload;
import dev.alvar.echoespast.network.MedusaHeadPoseDebugPayload;
import dev.alvar.echoespast.network.MedusaPetrifyPayload;
import dev.alvar.echoespast.network.PetrifiedMobMiningVisualPayload;
import dev.alvar.echoespast.network.PhilosophersStoneVisualPayload;
import dev.alvar.echoespast.network.PhilosophersStoneVisualProgressPayload;
import dev.alvar.echoespast.network.RelicControlPayload;
import dev.alvar.echoespast.network.UnknownAltarFragmentExplodePayload;
import dev.alvar.echoespast.network.UnknownBossBarPayload;
import dev.alvar.echoespast.network.UnknownCombatImpactPayload;
import dev.alvar.echoespast.network.UnknownEnterCinematicPayload;
import dev.alvar.echoespast.relic.HolyGrailItem;
import dev.alvar.echoespast.item.UnknownMedievalArmorItem;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.minecraft.world.level.material.FogType;

@Mod(value = EchoesShowThePast.MOD_ID, dist = Dist.CLIENT)
public final class EchoesShowThePastClient {
    /**
     * Vanilla re-fires use while the button stays down. Toggle items and
     * dismissible relics must only react to the rising edge of that press.
     */
    private boolean useInteractionLatched;

    public EchoesShowThePastClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::registerPayloadHandlers);
        modBus.addListener(this::registerRenderPipelines);
        modBus.addListener(this::registerEnvironmentRenderers);
        modBus.addListener(this::registerSpecialModelRenderers);
        modBus.addListener(this::registerEntityRenderers);
        modBus.addListener(this::addEntityLayers);
        modBus.addListener(this::registerRenderStateModifiers);
        modBus.addListener(this::registerItemModelProperties);
        modBus.addListener(this::registerMenuScreens);
        modBus.addListener(this::registerItemColors);
        NeoForge.EVENT_BUS.addListener(this::extractEchoRenderState);
        NeoForge.EVENT_BUS.addListener(this::submitEchoGeometry);
        NeoForge.EVENT_BUS.addListener(this::clientTick);
        NeoForge.EVENT_BUS.addListener(this::renderFrame);
        NeoForge.EVENT_BUS.addListener(this::interactionKeyTriggered);
        NeoForge.EVENT_BUS.addListener(this::movementInput);
        NeoForge.EVENT_BUS.addListener(this::renderHand);
        NeoForge.EVENT_BUS.addListener(this::cameraAngles);
        NeoForge.EVENT_BUS.addListener(this::cameraFov);
        NeoForge.EVENT_BUS.addListener(this::fogColor);
        NeoForge.EVENT_BUS.addListener(this::renderFog);
        NeoForge.EVENT_BUS.addListener(this::clientLogin);
        NeoForge.EVENT_BUS.addListener(this::clientLogout);
        NeoForge.EVENT_BUS.addListener(this::clientClone);
        NeoForge.EVENT_BUS.addListener(this::clientResourcesLoaded);
        NeoForge.EVENT_BUS.addListener(UnknownBossBarOverlay::onBossBar);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(EchoesShowThePast.RESONATOR_MENU.get(), ResonatorScreen::new);
        event.register(EchoesShowThePast.PAST_ECHO_MENU.get(), PastEchoScreen::new);
    }

    private void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(EchoStatePayload.TYPE, (payload, context) -> ClientEchoState.receive(payload));
        event.register(
                EchoFadeDebugPayload.TYPE,
                (payload, context) -> ClientEchoFadeDebug.dumpLookTarget());
        event.register(
                MedusaHeadPoseDebugPayload.TYPE,
                (payload, context) -> ClientMedusaHeadPoseDebug.receive(payload));
        event.register(
                EyeOfHorusVisualPayload.TYPE,
                (payload, context) -> ClientHorusVision.receive(payload));
        event.register(
                EyeHazardSignalsPayload.TYPE,
                (payload, context) -> ClientHorusHazards.receive(payload));
        event.register(
                MedusaGazeVisualPayload.TYPE,
                (payload, context) -> ClientMedusaVision.receive(payload));
        event.register(
                MedusaPetrifyPayload.TYPE,
                (payload, context) -> ClientMedusaVision.receive(payload));
        event.register(
                HolyGrailVisualPayload.TYPE,
                (payload, context) -> ClientHolyGrailVision.receive(payload));
        event.register(
                PhilosophersStoneVisualPayload.TYPE,
                (payload, context) ->
                        ClientPhilosophersStoneVision.receive(payload));
        event.register(
                PhilosophersStoneVisualProgressPayload.TYPE,
                (payload, context) ->
                        ClientPhilosophersStoneVision.receiveProgress(payload));
        event.register(
                PetrifiedMobMiningVisualPayload.TYPE,
                (payload, context) -> ClientPetrifiedMining.receive(payload));
        event.register(
                LowFrequencyPulseStartPayload.TYPE,
                (payload, context) -> ClientLowFrequencySonarState.receive(payload));
        event.register(
                LowFrequencyPulseResultPayload.TYPE,
                (payload, context) -> ClientLowFrequencySonarState.receive(payload));
        event.register(
                LowFrequencyPulseCancelPayload.TYPE,
                (payload, context) -> ClientLowFrequencySonarState.receive(payload));
        event.register(
                UnknownBossBarPayload.TYPE,
                (payload, context) -> ClientUnknownBossBar.receive(payload));
        event.register(
                UnknownCombatImpactPayload.TYPE,
                (payload, context) -> ClientUnknownCombatImpact.receive(payload));
        event.register(
                UnknownAltarFragmentExplodePayload.TYPE,
                (payload, context) -> ClientUnknownAltarEffects.receive(payload));
        event.register(
                UnknownEnterCinematicPayload.TYPE,
                (payload, context) -> ClientUnknownEnterCinematic.receive(payload));
    }

    private void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(EchoRenderTypes.WAVE_COLOR_PIPELINE);
        event.registerPipeline(EchoRenderTypes.WAVE_MASK_PIPELINE);
        event.registerPipeline(EchoRenderTypes.LOW_FREQUENCY_BEAM_PIPELINE);
        event.registerPipeline(EchoRenderTypes.HORUS_HAZARD_OCCLUDED_PIPELINE);
        event.registerPipeline(EchoRenderTypes.HORUS_HAZARD_VISIBLE_PIPELINE);
        event.registerPipeline(EchoRenderTypes.MEDUSA_GAZE_PIPELINE);
        event.registerPipeline(EchoRenderTypes.HOLY_GRAIL_RITUAL_PIPELINE);
        event.registerPipeline(EchoRenderTypes.HOLY_GRAIL_GLOW_PIPELINE);
        event.registerPipeline(EchoRenderTypes.ALTAR_ORBIT_PIPELINE);
        event.registerPipeline(EchoRenderTypes.ALTAR_ORBIT_GLOW_PIPELINE);
        event.registerPipeline(EchoRenderTypes.HORUS_SIGIL_PIPELINE);
        event.registerPipeline(EchoRenderTypes.HORUS_SIGIL_GLOW_PIPELINE);
        event.registerPipeline(EchoRenderTypes.RA_JUDGMENT_SIGIL_PIPELINE);
        event.registerPipeline(EchoRenderTypes.RA_JUDGMENT_SIGIL_GLOW_PIPELINE);
        event.registerPipeline(EchoRenderTypes.UNKNOWN_STAB_PIPELINE);
        event.registerPipeline(EchoRenderTypes.EGYPTIAN_JUDGMENT_PIPELINE);
        event.registerPipeline(EchoRenderTypes.EGYPTIAN_JUDGMENT_OCCLUDED_PIPELINE);
        event.registerPipeline(EchoRenderTypes.EGYPTIAN_SEKHMET_PIPELINE);
        event.registerPipeline(EchoRenderTypes.SPECTRAL_HOPLITE_PIPELINE);
        event.registerPipeline(EchoRenderTypes.SHADERPACK_COLOR_PIPELINE);
        event.registerPipeline(EchoRenderTypes.SHADERPACK_COLOR_OCCLUDED_PIPELINE);
        event.registerPipeline(TimelessSkyRenderTypes.BACKGROUND);
        event.registerPipeline(TimelessSkyRenderTypes.VEILS);
        event.registerPipeline(TimelessSkyRenderTypes.STARS);
        event.registerPipeline(TimelessSkyRenderTypes.ECLIPSE);
        event.registerPipeline(TimelessSkyRenderTypes.ECLIPSE_CORONA);
        event.registerPipeline(MedusaRenderTypes.STONE_PIPELINE);
        event.registerPipeline(MedusaRenderTypes.CRACK_PIPELINE);

        registerIrisPipelineCompatibility();
    }

    /**
     * Iris cannot infer a shader-pack program for modded core pipelines. These
     * mappings preserve every gameplay silhouette and depth rule by describing
     * the closest vanilla semantic; custom programs remain active without a
     * shader pack.
     */
    private static void registerIrisPipelineCompatibility() {
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.WAVE_COLOR_PIPELINE, "TEXTURED");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.WAVE_MASK_PIPELINE, "TEXTURED");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.LOW_FREQUENCY_BEAM_PIPELINE, "BASIC");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.HORUS_HAZARD_OCCLUDED_PIPELINE, "BASIC");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.HORUS_HAZARD_VISIBLE_PIPELINE, "BASIC");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.MEDUSA_GAZE_PIPELINE, "BASIC");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.HOLY_GRAIL_RITUAL_PIPELINE, "BASIC");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.HOLY_GRAIL_GLOW_PIPELINE, "BASIC");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.ALTAR_ORBIT_PIPELINE, "BASIC");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.ALTAR_ORBIT_GLOW_PIPELINE, "BASIC");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.HORUS_SIGIL_PIPELINE, "TEXTURED");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.HORUS_SIGIL_GLOW_PIPELINE, "TEXTURED");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.RA_JUDGMENT_SIGIL_PIPELINE, "TEXTURED");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.RA_JUDGMENT_SIGIL_GLOW_PIPELINE, "TEXTURED");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.UNKNOWN_STAB_PIPELINE, "BASIC");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.EGYPTIAN_JUDGMENT_PIPELINE, "BASIC");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.EGYPTIAN_JUDGMENT_OCCLUDED_PIPELINE, "BASIC");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.EGYPTIAN_SEKHMET_PIPELINE, "BASIC");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.SPECTRAL_HOPLITE_PIPELINE, "BASIC");
        EchoShaderCompatibility.assignPipeline(EchoRenderTypes.SHADERPACK_COLOR_PIPELINE, "BASIC");
        EchoShaderCompatibility.assignPipeline(
                EchoRenderTypes.SHADERPACK_COLOR_OCCLUDED_PIPELINE,
                "BASIC");

        EchoShaderCompatibility.assignPipeline(TimelessSkyRenderTypes.BACKGROUND, "SKY_TEXTURED");
        EchoShaderCompatibility.assignPipeline(TimelessSkyRenderTypes.VEILS, "SKY_BASIC");
        EchoShaderCompatibility.assignPipeline(TimelessSkyRenderTypes.STARS, "SKY_BASIC");
        EchoShaderCompatibility.assignPipeline(TimelessSkyRenderTypes.ECLIPSE, "SKY_BASIC");
        EchoShaderCompatibility.assignPipeline(TimelessSkyRenderTypes.ECLIPSE_CORONA, "SKY_BASIC");
    }

    private void registerEnvironmentRenderers(
            RegisterCustomEnvironmentEffectRendererEvent event) {
        event.registerSkyboxRenderer(
                TimelessSkyRenderer.ENVIRONMENT_ID,
                TimelessSkyRenderer.INSTANCE);
    }

    private void registerSpecialModelRenderers(
            RegisterSpecialModelRendererEvent event) {
        event.register(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        EchoesShowThePast.MOD_ID,
                        "petrified_mob"),
                PetrifiedMobSpecialRenderer.Unbaked.MAP_CODEC);
    }

    private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                EchoesShowThePast.ECHO_PEDESTAL_BLOCK_ENTITY.get(),
                EchoPedestalRenderer::new);
        event.registerBlockEntityRenderer(
                EchoesShowThePast.BIG_ECHO_PEDESTAL_BLOCK_ENTITY.get(),
                BigEchoPedestalRenderer::new);
        event.registerEntityRenderer(EchoesShowThePast.UNKNOWN.get(), UnknownRenderer::new);
        event.registerEntityRenderer(EchoesShowThePast.MEDUSA.get(), MedusaRenderer::new);
        event.registerEntityRenderer(
                EchoesShowThePast.SPECTRAL_HOPLITE.get(),
                SpectralHopliteRenderer::new);
        event.registerEntityRenderer(
                EchoesShowThePast.MEDIEVAL_RUBBLE_PROJECTILE.get(),
                context -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(
                        context,
                        0.85F,
                        false));
        event.registerEntityRenderer(
                EchoesShowThePast.DUNGEON_PICKUP.get(),
                DungeonPickupRenderer::new);
    }

    private void addEntityLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            var renderer = event.getPlayerRenderer(skin);
            renderer.addLayer(new HorusSigilLayer(renderer));
        }
    }

    private void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                new TypeToken<
                        net.minecraft.client.renderer.entity.LivingEntityRenderer<
                                net.minecraft.world.entity.LivingEntity,
                                net.minecraft.client.renderer.entity.state.LivingEntityRenderState,
                                ?>>() {
                },
                (entity, state) -> {
                    state.setRenderData(MedusaRenderState.ENTITY_ID, entity.getId());
                    var pose = entity.getExistingDataOrNull(EchoesShowThePast.PETRIFIED_POSE.get());
                    state.setRenderData(
                            MedusaRenderState.PERMANENT,
                            pose != null && pose.permanent());
                    if (pose != null && pose.permanent()) {
                        // Physics uses a safe, detached pose; rendering keeps
                        // the exact captured posture (including sleeping).
                        state.pose = pose.pose();
                    }
                    if (entity instanceof net.minecraft.world.entity.player.Player) {
                        if (state
                                instanceof
                                net.minecraft.client.renderer.entity.state.AvatarRenderState
                                        avatar) {
                            if (UnknownMedievalArmorItem.hidesJacketAndSleeves(entity)) {
                                avatar.showJacket = false;
                                avatar.showLeftSleeve = false;
                                avatar.showRightSleeve = false;
                            }
                            if (UnknownMedievalArmorItem.hidesPants(entity)) {
                                avatar.showLeftPants = false;
                                avatar.showRightPants = false;
                            }
                        }
                        long start = entity.getData(
                                EchoesShowThePast.HORUS_AURA_START.get());
                        long until = entity.getData(
                                EchoesShowThePast.HORUS_AURA_UNTIL.get());
                        float now = entity.level().getGameTime() + state.partialTick;
                        float opening = smooth((now - start) / 9.0F);
                        float closing = smooth((until - now) / 14.0F);
                        float strength = now >= start && now < until
                                ? Math.min(opening, closing)
                                : 0.0F;
                        state.setRenderData(
                                HorusRenderState.AURA_STRENGTH,
                                strength);
                    }
                });
    }

    private void registerItemModelProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        EchoesShowThePast.MOD_ID,
                        "echo_animation"),
                EchoAnimationProperty.MAP_CODEC);
        event.register(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        EchoesShowThePast.MOD_ID,
                        "fragment_animation"),
                FragmentAnimationProperty.MAP_CODEC);
    }

    private void registerItemColors(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        EchoesShowThePast.MOD_ID,
                        "fragment_tint"),
                FragmentTintSource.MAP_CODEC);
    }

    private void extractEchoRenderState(ExtractLevelRenderStateEvent event) {
        ClientEchoRenderer.extract(event);
        LowFrequencySonarRenderer.extract(event);
        ClientPhilosophersStoneVision.extract(event);
        ClientEchoState.publishExtractedPostFrame();
    }

    private void submitEchoGeometry(SubmitCustomGeometryEvent event) {
        TimelessSkyRenderer.INSTANCE.submitShaderpackGeometry(event);
        ClientEchoRenderer.submit(event);
        LowFrequencySonarRenderer.submit(event);
        HorusHazardRenderer.submit(event);
        MedusaGazeRenderer.submit(event);
        HolyGrailRenderer.submit(event);
        UnknownGreekCombatRenderer.submit(event);
    }

    private void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientTemplateProjection.preloadStaticTemplates(minecraft);
        ClientEchoState.prepareRenderRuntime(minecraft);
        if (minecraft.options == null || !minecraft.options.keyUse.isDown()) {
            useInteractionLatched = false;
        }
        // Release Stone geometry first so Echo caches and chunk dirtiness can
        // settle during this same client tick.
        ClientPhilosophersStoneVision.tick();
        ClientEchoState.tick();
        ClientLowFrequencySonarState.tick();
        ClientHorusVision.tick();
        ClientMedusaVision.tick();
        ClientHolyGrailVision.tick();
        ClientPetrifiedPose.freezeAll();
        ClientPetrifiedMining.tick();
        ClientUnknownEnterCinematic.tick();
        TimelessSkyRenderer.INSTANCE.tick();
    }

    private void renderFrame(RenderFrameEvent.Pre event) {
        ClientEchoState.advancePreparation();
        boolean stonePriority =
                ClientPhilosophersStoneVision.hasPostEffectPriority();
        if (!stonePriority
                && !ClientMedusaVision.hasPostEffectPriority()
                && !ClientHolyGrailVision.hasPostEffectPriority()
                && !ClientHorusVision.hasPostEffectPriority()) {
            ClientEchoState.renderFrame();
        }
        if (!stonePriority
                && !ClientMedusaVision.hasPostEffectPriority()
                && !ClientHolyGrailVision.hasPostEffectPriority()) {
            ClientHorusVision.renderFrame();
        }
        if (!stonePriority
                && !ClientMedusaVision.hasPostEffectPriority()) {
            ClientHolyGrailVision.renderFrame();
        }
        if (!stonePriority) {
            ClientMedusaVision.renderFrame();
        }
        ClientPhilosophersStoneVision.renderFrame();
    }

    private void interactionKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.isUseItem() || minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (ClientUnknownEnterCinematic.isControlling()) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        var stack = minecraft.player.getItemInHand(event.getHand());
        boolean holdSensitive = stack.is(EchoesShowThePast.PAST_ECHO.get())
                || stack.is(EchoesShowThePast.EYE_OF_HORUS.get())
                || stack.is(EchoesShowThePast.HOLY_GRAIL.get())
                || stack.is(EchoesShowThePast.PHILOSOPHERS_STONE.get())
                || stack.is(EchoesShowThePast.LOW_FREQUENCY_RESONATOR.get());
        if (useInteractionLatched) {
            if (holdSensitive) {
                // Swallow vanilla's hold-repeat so toggles cannot chatter and
                // an activation cannot immediately dismiss on the same press.
                event.setSwingHand(false);
                event.setCanceled(true);
            }
            return;
        }
        if (minecraft.player.isShiftKeyDown()
                && stack.is(EchoesShowThePast.LOW_FREQUENCY_RESONATOR.get())) {
            long pulseId = ClientLowFrequencySonarState.requestCancellation(System.nanoTime());
            if (pulseId >= 0L) {
                ClientPacketDistributor.sendToServer(new LowFrequencyPulseCancelPayload(pulseId));
            } else {
                // Vanilla rejects useItem before the item sees Shift while a
                // cooldown is active. The server still validates this held
                // stack before opening the menu.
                ClientPacketDistributor.sendToServer(new RelicControlPayload(
                        event.getHand() == net.minecraft.world.InteractionHand.OFF_HAND));
            }
            useInteractionLatched = true;
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        boolean dismissEye = stack.is(EchoesShowThePast.EYE_OF_HORUS.get())
                && ClientHorusVision.isActive();
        boolean dismissGrail = stack.is(EchoesShowThePast.HOLY_GRAIL.get())
                && ClientHolyGrailVision.isLocalAuraActive()
                // Preserve water-recharge priority from HolyGrailItem.use.
                && HolyGrailItem.targetedWaterSource(minecraft.level, minecraft.player)
                        .isEmpty();
        boolean dismissStone = stack.is(EchoesShowThePast.PHILOSOPHERS_STONE.get())
                && ClientPhilosophersStoneVision.isActive();
        if (dismissEye || dismissGrail || dismissStone) {
            ClientPacketDistributor.sendToServer(new RelicControlPayload(
                    event.getHand() == net.minecraft.world.InteractionHand.OFF_HAND));
            useInteractionLatched = true;
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (holdSensitive) {
            // Allow the first vanilla use, then block repeats for this press.
            useInteractionLatched = true;
        }
    }

    private void movementInput(MovementInputUpdateEvent event) {
        ClientUnknownEnterCinematic.freezeInput(event);
    }

    private void renderHand(RenderHandEvent event) {
        ClientUnknownEnterCinematic.hideHands(event);
    }

    private void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        ClientUnknownEnterCinematic.onAngles(event);
        ClientUnknownCombatImpact.onAngles(event);
    }

    private void cameraFov(ViewportEvent.ComputeFov event) {
        ClientUnknownEnterCinematic.onFov(event);
    }

    private void fogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !minecraft.level.dimension().equals(
                        dev.alvar.echoespast.world.TimelessDimensions.TIMELESS_VOID)
                || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        var atmosphere = TimelessSkyRenderer.INSTANCE.profile();
        event.setRed(atmosphere.fogR());
        event.setGreen(atmosphere.fogG());
        event.setBlue(atmosphere.fogB());
    }

    private void renderFog(ViewportEvent.RenderFog event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !minecraft.level.dimension().equals(
                        dev.alvar.echoespast.world.TimelessDimensions.TIMELESS_VOID)
                || event.getType() != FogType.NONE) {
            return;
        }
        float horizon = TimelessSkyRenderer.INSTANCE.profile().horizonDistance();
        event.setNearPlaneDistance(Math.max(36.0F, horizon * 0.42F));
        event.setFarPlaneDistance(horizon);
    }

    private void clientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientTemplateProjection.preloadStaticTemplates(minecraft);
        ClientEchoState.prepareRenderRuntime(minecraft);
    }

    private void clientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientEchoState.clearImmediately();
        ClientLowFrequencySonarState.clear();
        ClientHorusVision.clearImmediately();
        ClientMedusaVision.clearImmediately();
        ClientHolyGrailVision.clearImmediately();
        ClientPhilosophersStoneVision.clearImmediately();
        ClientPetrifiedMining.clear();
        ClientUnknownBossBar.clear();
        ClientUnknownEnterCinematic.clear();
        TimelessSkyRenderer.INSTANCE.clearResources();
        ClientTemplateProjection.clearCache();
    }

    private void clientClone(ClientPlayerNetworkEvent.Clone event) {
        ClientMedusaVision.clearImmediately();
    }

    private void clientResourcesLoaded(ClientResourceLoadFinishedEvent event) {
        TimelessSkyRenderer.INSTANCE.clearResources();
        PetrifiedTextureCache.clear();
        ClientTemplateProjection.clearCache();
        ClientTemplateProjection.preloadStaticTemplates(Minecraft.getInstance());
        ClientEchoState.invalidateRenderRuntime();
        ClientEchoState.prepareRenderRuntime(Minecraft.getInstance());
        ClientEchoArrivalField.prepareRuntime();
        EchoPastLight.prepareRuntime();
        ClientEchoState.preparePostEffect();
        ClientHorusVision.preparePostEffect();
        ClientMedusaVision.preparePostEffect();
        ClientHolyGrailVision.preparePostEffect();
        ClientPhilosophersStoneVision.preparePostEffect();
        EasyNpcAuthoredSkins.install(Minecraft.getInstance().getResourceManager());
    }

    private static float smooth(float value) {
        float clamped = Math.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
