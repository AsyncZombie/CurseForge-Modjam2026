package dev.alvar.echoespast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import dev.alvar.echoespast.mixin.client.RenderSetupAccessor;
import dev.alvar.echoespast.mixin.client.RenderTypeAccessor;
import dev.alvar.echoespast.mixin.client.TextureBindingAccessor;

/**
 * Converts every node emitted by an entity renderer to the same translucent
 * memory material. Vanilla only fades the primary living model when an entity
 * is invisible; armor, held items and special item models are independent
 * nodes and therefore have to be transformed at their common collector.
 */
final class GhostSubmitNodeCollector extends GhostOrderedSubmitNodeCollector
        implements SubmitNodeCollector {

    GhostSubmitNodeCollector(SubmitNodeCollector delegate, float opacity) {
        super(delegate, opacity);
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        SubmitNodeCollector delegate = (SubmitNodeCollector) this.delegate;
        return new GhostOrderedSubmitNodeCollector(delegate.order(order), this.opacity);
    }
}

class GhostOrderedSubmitNodeCollector implements OrderedSubmitNodeCollector {
    protected final OrderedSubmitNodeCollector delegate;
    protected final float opacity;

    GhostOrderedSubmitNodeCollector(OrderedSubmitNodeCollector delegate, float opacity) {
        this.delegate = delegate;
        this.opacity = Math.clamp(opacity, 0.0F, 1.0F);
    }

        @Override
        public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
            // A frozen memory must not cast a second present-day shadow.
        }

        @Override
        public void submitNameTag(
                PoseStack poseStack,
                @Nullable Vec3 nameTagAttachment,
                int offset,
                Component name,
                boolean seeThrough,
                int lightCoords,
                double distanceToCameraSq,
                CameraRenderState camera) {
            // Name tags are UI, not part of the captured physical appearance.
        }

        @Override
        public void submitText(
                PoseStack poseStack,
                float x,
                float y,
                FormattedCharSequence string,
                boolean dropShadow,
                Font.DisplayMode displayMode,
                int lightCoords,
                int color,
                int backgroundColor,
                int outlineColor) {
            // See submitNameTag.
        }

        @Override
        public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
            // Fire is transient world VFX and is intentionally not duplicated.
        }

        @Override
        public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
            // Leashes reference a live second endpoint and are unsafe in a frozen snapshot.
        }

        @Override
        public <S> void submitModel(
                Model<? super S> model,
                S state,
                PoseStack poseStack,
                RenderType renderType,
                int lightCoords,
                int overlayCoords,
                int tintedColor,
                @Nullable TextureAtlasSprite sprite,
                int outlineColor,
                ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
            if (isGlint(renderType)
                    || isPetrifiedEmissiveLayer(state, renderType)) {
                return;
            }
            this.delegate.submitModel(
                    model,
                    state,
                    poseStack,
                    translucentModelType(renderType),
                    lightCoords,
                    overlayCoords,
                    ghostColor(tintedColor, this.opacity),
                    sprite,
                    0,
                    crumblingOverlay);
        }

        @Override
        public void submitModelPart(
                ModelPart modelPart,
                PoseStack poseStack,
                RenderType renderType,
                int lightCoords,
                int overlayCoords,
                @Nullable TextureAtlasSprite sprite,
                boolean sheeted,
                boolean hasFoil,
                int tintedColor,
                ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
                int outlineColor) {
            if (isGlint(renderType)) {
                return;
            }
            this.delegate.submitModelPart(
                    modelPart,
                    poseStack,
                    translucentModelType(renderType),
                    lightCoords,
                    overlayCoords,
                    sprite,
                    false,
                    false,
                    ghostColor(tintedColor, this.opacity),
                    crumblingOverlay,
                    0);
        }

        @Override
        public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState) {
            this.delegate.submitMovingBlock(poseStack, movingBlockRenderState);
        }

        @Override
        public void submitBlockModel(
                PoseStack poseStack,
                RenderType renderType,
                List<BlockStateModelPart> parts,
                int[] tintLayers,
                int lightCoords,
                int overlayCoords,
                int outlineColor) {
            int[] ghostTints = tintLayers.clone();
            for (int index = 0; index < ghostTints.length; index++) {
                ghostTints[index] = ghostColor(ghostTints[index], this.opacity);
            }
            this.delegate.submitBlockModel(
                    poseStack,
                    translucentModelType(renderType),
                    parts,
                    ghostTints,
                    lightCoords,
                    overlayCoords,
                    0);
        }

        @Override
        public void submitBreakingBlockModel(PoseStack poseStack, BlockStateModel model, long seed, int progress) {
            // A remembered entity never carries an active block-breaking overlay.
        }

        @Override
        public void submitItem(
                PoseStack poseStack,
                ItemDisplayContext displayContext,
                int lightCoords,
                int overlayCoords,
                int outlineColor,
                int[] tintLayers,
                List<BakedQuad> quads,
                ItemStackRenderState.FoilType foilType) {
            List<BakedQuad> ghostQuads = new ArrayList<>(quads.size());
            int[] ghostTints = new int[quads.size()];
            for (int index = 0; index < quads.size(); index++) {
                BakedQuad quad = quads.get(index);
                BakedQuad.MaterialInfo material = quad.materialInfo();
                int sourceColor = material.isTinted()
                                && material.tintIndex() >= 0
                                && material.tintIndex() < tintLayers.length
                        ? tintLayers[material.tintIndex()]
                        : -1;
                ghostTints[index] = ghostColor(sourceColor, this.opacity);
                RenderType itemRenderType = material.sprite().atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS)
                        ? Sheets.translucentBlockItemSheet()
                        : Sheets.translucentItemSheet();
                BakedQuad.MaterialInfo ghostMaterial = new BakedQuad.MaterialInfo(
                        material.sprite(),
                        material.layer(),
                        itemRenderType,
                        index,
                        material.shade(),
                        material.lightEmission(),
                        material.ambientOcclusion());
                ghostQuads.add(new BakedQuad(
                        quad.position0(),
                        quad.position1(),
                        quad.position2(),
                        quad.position3(),
                        quad.packedUV0(),
                        quad.packedUV1(),
                        quad.packedUV2(),
                        quad.packedUV3(),
                        quad.direction(),
                        ghostMaterial,
                        quad.bakedNormals(),
                        quad.bakedColors()));
            }
            this.delegate.submitItem(
                    poseStack,
                    displayContext,
                    lightCoords,
                    overlayCoords,
                    0,
                    ghostTints,
                    List.copyOf(ghostQuads),
                    ItemStackRenderState.FoilType.NONE);
        }

        @Override
        public void submitCustomGeometry(
                PoseStack poseStack,
                RenderType renderType,
                SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
            this.delegate.submitCustomGeometry(
                    poseStack,
                    translucentModelType(renderType),
                    customGeometryRenderer);
        }

        @Override
        public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer) {
            // Entity-attached particles are not part of the frozen entity.
        }

        private static int ghostColor(int color, float opacity) {
            if (color == 0) {
                return 0;
            }
            int rgb = color == -1 ? 0xFFFFFF : color & 0xFFFFFF;
            return ARGB.color(opacity, rgb);
        }

        private static boolean isGlint(RenderType renderType) {
            return renderType.toString().contains("glint");
        }

        private static boolean isPetrifiedEmissiveLayer(
                Object state,
                RenderType renderType) {
            return state instanceof LivingEntityRenderState livingState
                    && Boolean.TRUE.equals(
                            livingState.getRenderData(
                                    MedusaRenderState.PERMANENT))
                    && MedusaRenderTypes.isEmissiveLayer(renderType);
        }

        private static RenderType translucentModelType(RenderType original) {
            Identifier texture = texture(original);
            if (texture == null) {
                return original;
            }
            if (original.toString().contains("armor_")) {
                return RenderTypes.armorTranslucent(texture);
            }
            return RenderTypes.entityTranslucent(texture, false);
        }

        private static @Nullable Identifier texture(RenderType renderType) {
            RenderSetup setup = ((RenderTypeAccessor) (Object) renderType)
                    .echoesShowThePast$getState();
            Object binding = ((RenderSetupAccessor) (Object) setup)
                    .echoesShowThePast$getTextureBindings()
                    .get("Sampler0");
            if (binding instanceof TextureBindingAccessor accessor) {
                return accessor.echoesShowThePast$getLocation();
            }
            return null;
        }
}
