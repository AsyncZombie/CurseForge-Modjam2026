package dev.alvar.echoespast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alvar.echoespast.entity.DungeonPickupEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/** Camera-facing item display for {@link DungeonPickupEntity}. */
public final class DungeonPickupRenderer
        extends EntityRenderer<DungeonPickupEntity, DungeonPickupRenderer.State> {
    private static final float BASE_SCALE = 0.85F;
    private static final float BASE_SHADOW = 0.15F;
    private final ItemModelResolver itemModelResolver;

    public DungeonPickupRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemModelResolver = context.getItemModelResolver();
        shadowRadius = BASE_SHADOW;
        shadowStrength = 0.55F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DungeonPickupEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        itemModelResolver.updateForNonLiving(
                state.item,
                entity.getItem(),
                ItemDisplayContext.GROUND,
                entity);
        state.displayScale = entity.getDisplayScale();
        state.shadowRadius = BASE_SHADOW * state.displayScale;
        state.bob = Mth.sin((entity.tickCount + partialTicks) / 10.0F)
                * 0.08F
                * state.displayScale
                + 0.08F * state.displayScale;
    }

    @Override
    public void submit(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (state.item.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        float scale = BASE_SCALE * state.displayScale;
        poseStack.translate(0.0F, (0.18F + state.bob) * state.displayScale, 0.0F);
        poseStack.mulPose(camera.orientation);
        poseStack.scale(scale, scale, scale);
        state.item.submit(
                poseStack,
                collector,
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    public static final class State extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public float bob;
        public float displayScale = 1.0F;
    }
}
