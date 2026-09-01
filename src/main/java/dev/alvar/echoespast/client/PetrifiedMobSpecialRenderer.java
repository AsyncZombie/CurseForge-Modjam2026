package dev.alvar.echoespast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.relic.PetrifiedMobData;
import dev.alvar.echoespast.relic.PetrifiedPose;
import dev.alvar.echoespast.visual.PetrifiedItemLayout;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Renders the serialized creature itself in every item display context. Preview
 * entities are client-only, cached by component identity and never enter a
 * level's entity collection.
 */
public final class PetrifiedMobSpecialRenderer
        implements SpecialModelRenderer<PetrifiedMobData> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_CACHED_PREVIEWS = 64;
    private static final Identifier STONE_TEXTURE =
            Identifier.withDefaultNamespace("textures/block/stone.png");
    private static final CameraRenderState ITEM_CAMERA = new CameraRenderState();
    private static final ModelPart FALLBACK_MODEL = createFallbackModel();

    private final Map<PetrifiedMobData, Preview> previews = new IdentityHashMap<>();
    private final Set<PetrifiedMobData> failed =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private @Nullable ClientLevel cachedLevel;

    @Override
    public void submit(
            @Nullable PetrifiedMobData data,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            int overlayCoords,
            boolean hasFoil,
            int outlineColor) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (data == null || level == null) {
            submitFallback(
                    poseStack,
                    submitNodeCollector,
                    lightCoords,
                    overlayCoords,
                    outlineColor);
            return;
        }
        if (level != cachedLevel) {
            cachedLevel = level;
            previews.clear();
            failed.clear();
        }
        if (failed.contains(data)) {
            submitFallback(
                    poseStack,
                    submitNodeCollector,
                    lightCoords,
                    overlayCoords,
                    outlineColor);
            return;
        }

        Preview preview = previews.get(data);
        if (preview == null) {
            preview = createPreview(level, data);
            if (preview == null) {
                failed.add(data);
                submitFallback(
                        poseStack,
                        submitNodeCollector,
                        lightCoords,
                        overlayCoords,
                        outlineColor);
                return;
            }
            if (previews.size() >= MAX_CACHED_PREVIEWS) {
                previews.clear();
            }
            previews.put(data, preview);
        }

        Vec3 position = minecraft.player == null
                ? Vec3.ZERO
                : minecraft.player.position();
        preview.entity().setPos(position);
        ClientPetrifiedPose.freeze(preview.entity(), preview.pose());

        try {
            submitPreview(
                    preview,
                    data,
                    poseStack,
                    submitNodeCollector,
                    lightCoords);
        } catch (RuntimeException | LinkageError exception) {
            failed.add(data);
            previews.remove(data);
            LOGGER.warn(
                    "Falling back from the 3D petrified item renderer for {}",
                    preview.entity().getType(),
                    exception);
            submitFallback(
                    poseStack,
                    submitNodeCollector,
                    lightCoords,
                    overlayCoords,
                    outlineColor);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void submitPreview(
            Preview preview,
            PetrifiedMobData data,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords) {
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderer renderer =
                minecraft.getEntityRenderDispatcher().getRenderer(preview.entity());
        if (renderer == null) {
            throw new IllegalStateException(
                    "No entity renderer for " + preview.entity().getType());
        }
        EntityRenderState state =
                renderer.createRenderState(preview.entity(), 1.0F);
        state.shadowPieces.clear();
        state.nameTag = null;
        state.scoreText = null;
        state.leashStates = null;
        state.displayFireAnimation = false;
        state.outlineColor = 0;
        state.lightCoords = lightCoords;
        state.setRenderData(MedusaRenderState.ENTITY_ID, preview.entity().getId());
        state.setRenderData(MedusaRenderState.PERMANENT, true);
        state.setRenderData(MedusaRenderState.HEADLESS, data.headless());
        state.setRenderData(
                MedusaRenderState.ITEM_PREVIEW_POSE,
                data.modelPose());

        float scale = PetrifiedItemLayout.fitScale(
                preview.entity().getBbWidth(),
                preview.entity().getBbHeight());
        float baseY = PetrifiedItemLayout.baseY(
                preview.entity().getBbHeight(),
                scale);
        poseStack.pushPose();
        try {
            poseStack.translate(0.5F, baseY, 0.5F);
            poseStack.scale(scale, scale, scale);
            renderer.submit(
                    state,
                    poseStack,
                    submitNodeCollector,
                    ITEM_CAMERA);
        } finally {
            // A broken third-party renderer must not leak its transform into
            // the fallback icon or the next inventory slot.
            poseStack.popPose();
        }
    }

    private static @Nullable Preview createPreview(
            ClientLevel level,
            PetrifiedMobData data) {
        try {
            Entity root = EntityType.loadEntityRecursive(
                    PetrifiedMobData.detachInteractionData(data.entity().data()),
                    level,
                    EntitySpawnReason.LOAD,
                    entity -> entity);
            if (!(root instanceof LivingEntity living)) {
                return null;
            }

            float capturedYaw = Float.isFinite(data.entity().yRot())
                    ? data.entity().yRot()
                    : 0.0F;
            float displayYaw = 180.0F;
            float facingDelta = Mth.wrapDegrees(displayYaw - capturedYaw);
            PetrifiedPose pose = new PetrifiedPose(
                    true,
                    data.entity().pose(),
                    data.entity().ageInTicks(),
                    displayYaw,
                    data.entity().xRot(),
                    data.entity().bodyYRot() + facingDelta,
                    data.entity().headYRot() + facingDelta,
                    data.entity().animation(),
                    data.modelPose(),
                    data.headless());
            living.setId(-1 - (System.identityHashCode(data) & 0x3FFFFFFF));
            living.setData(EchoesShowThePast.PETRIFIED_POSE.get(), pose);
            living.snapTo(0.0, 0.0, 0.0, displayYaw, data.entity().xRot());
            ClientPetrifiedPose.freeze(living, pose);
            return new Preview(living, pose);
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.warn("Could not create a petrified item preview", exception);
            return null;
        }
    }

    private static void submitFallback(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            int overlayCoords,
            int outlineColor) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.52F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(28.0F));
        poseStack.scale(0.72F, 0.72F, 0.72F);
        submitNodeCollector.submitModelPart(
                FALLBACK_MODEL,
                poseStack,
                RenderTypes.entitySolid(STONE_TEXTURE),
                lightCoords,
                overlayCoords,
                null,
                -1,
                null);
        poseStack.popPose();
    }

    private static ModelPart createFallbackModel() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
                "unknown_statue",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.0F, -7.0F, -3.0F, 8.0F, 10.0F, 6.0F)
                        .texOffs(0, 0)
                        .addBox(-3.0F, -13.0F, -3.0F, 6.0F, 6.0F, 6.0F)
                        .texOffs(0, 0)
                        .addBox(-6.0F, 3.0F, -5.0F, 12.0F, 3.0F, 10.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16).bakeRoot();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0.0F, 0.0F, 0.0F));
        output.accept(new Vector3f(1.0F, 1.0F, 1.0F));
    }

    @Override
    public @Nullable PetrifiedMobData extractArgument(ItemStack stack) {
        return stack.get(EchoesShowThePast.PETRIFIED_MOB_DATA.get());
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<PetrifiedMobData> {
        public static final MapCodec<Unbaked> MAP_CODEC =
                MapCodec.unit(new Unbaked());

        @Override
        public PetrifiedMobSpecialRenderer bake(
                SpecialModelRenderer.BakingContext context) {
            return new PetrifiedMobSpecialRenderer();
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked<PetrifiedMobData>>
                type() {
            return MAP_CODEC;
        }
    }

    private record Preview(LivingEntity entity, PetrifiedPose pose) {
    }
}
