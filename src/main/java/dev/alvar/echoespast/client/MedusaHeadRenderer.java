package dev.alvar.echoespast.client;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.constant.DataTickets;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.math.Axis;
import dev.alvar.echoespast.relic.MedusaHeadAimMath;
import dev.alvar.echoespast.relic.MedusaHeadItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;

/** Authored hand Euler plus a neck-to-palm translation. */
public final class MedusaHeadRenderer<T extends MedusaHeadItem> extends GeoItemRenderer<T> {
    public MedusaHeadRenderer(T item) {
        super(item);
    }

    public MedusaHeadRenderer(GeoModel<T> model) {
        super(model);
    }

    @Override
    public void captureDefaultRenderState(
            T item,
            RenderData renderData,
            GeoRenderState renderState,
            float partialTick) {
        super.captureDefaultRenderState(item, renderData, renderState, partialTick);
        LivingEntity owner = renderData.itemOwner() == null
                ? null
                : renderData.itemOwner().asLivingEntity();
        if (owner == null) {
            owner = Minecraft.getInstance().player;
        }
        boolean active = isHandPerspective(renderData.renderPerspective())
                && MedusaHeadItem.rendersActivePose(
                        owner,
                        renderData.itemStack());
        renderState.addGeckolibData(MedusaHeadItem.RENDER_ACTIVE, active);
        float elapsedUseTicks = active
                ? MedusaHeadItem.MAX_CHANNEL_TICKS
                        - owner.getUseItemRemainingTicks()
                        + partialTick
                : 0.0F;
        renderState.addGeckolibData(
                MedusaHeadItem.RENDER_POSE_BLEND,
                MedusaHeadAimMath.activationPoseBlend(elapsedUseTicks));
    }

    @Override
    public void preRenderPass(
            RenderPassInfo<GeoRenderState> renderPassInfo,
            SubmitNodeCollector submitNodeCollector) {
        super.preRenderPass(renderPassInfo, submitNodeCollector);
        AnimatableManager<?> manager = renderPassInfo.renderState()
                .getGeckolibData(DataTickets.ANIMATABLE_MANAGER);
        if (manager != null) {
            manager.setAnimatableData(
                    MedusaHeadItem.RENDER_ACTIVE,
                    renderPassInfo.getOrDefaultGeckolibData(
                            MedusaHeadItem.RENDER_ACTIVE,
                            false));
        }
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<GeoRenderState> renderPassInfo) {
        super.adjustRenderPose(renderPassInfo);
        ItemDisplayContext perspective = renderPassInfo.getGeckolibData(
                DataTickets.ITEM_RENDER_PERSPECTIVE);
        if (!isHandPerspective(perspective)) {
            return;
        }
        float blend = renderPassInfo.getOrDefaultGeckolibData(
                MedusaHeadItem.RENDER_POSE_BLEND,
                0.0F);
        MedusaHeadAimMath.PoseEuler pose = ClientMedusaHeadPoseDebug.rotation(blend);
        renderPassInfo.poseStack().mulPose(Axis.XP.rotationDegrees(pose.x()));
        renderPassInfo.poseStack().mulPose(Axis.YP.rotationDegrees(pose.y()));
        renderPassInfo.poseStack().mulPose(Axis.ZP.rotationDegrees(pose.z()));
        renderPassInfo.poseStack().translate(
                0.0F,
                MedusaHeadAimMath.HAND_GRIP_Y,
                0.0F);
    }

    private static boolean isHandPerspective(ItemDisplayContext perspective) {
        return perspective == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || perspective == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || perspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || perspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }
}
