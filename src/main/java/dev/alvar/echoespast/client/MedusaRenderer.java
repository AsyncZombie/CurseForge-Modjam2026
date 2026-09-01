package dev.alvar.echoespast.client;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.entity.MedusaEntity;
import dev.alvar.echoespast.relic.PetrifiedPose;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class MedusaRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<MedusaEntity, R> {
    private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "medusa");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "textures/entity/medusa.png");
    private static final String HEAD_BONE = "cabezon";

    public MedusaRenderer(EntityRendererProvider.Context context) {
        super(context, createModel());
        withScale(1.0F);
        this.shadowRadius = 0.55F;
    }

    @Override
    public void extractRenderState(MedusaEntity entity, R state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.setRenderData(MedusaRenderState.ENTITY_ID, entity.getId());
        PetrifiedPose pose = entity.getExistingDataOrNull(
                EchoesShowThePast.PETRIFIED_POSE.get());
        boolean permanent = pose != null && pose.permanent();
        state.setRenderData(MedusaRenderState.PERMANENT, permanent);
        state.setRenderData(
                MedusaRenderState.HEADLESS,
                pose != null && pose.headless());
    }

    @Override
    public RenderType getRenderType(R renderState, Identifier texture) {
        Integer entityId = renderState.getRenderData(MedusaRenderState.ENTITY_ID);
        boolean permanent = Boolean.TRUE.equals(
                renderState.getRenderData(MedusaRenderState.PERMANENT));
        float petrification = entityId == null
                ? 0.0F
                : ClientMedusaVision.petrificationProgress(entityId, permanent);
        if (petrification >= 0.998F) {
            Identifier material = PetrifiedTextureCache.getOrCreate(texture);
            return material != null
                    ? RenderTypes.entityCutout(material, false)
                    : MedusaRenderTypes.stone(texture, false);
        }
        return super.getRenderType(renderState, texture);
    }

    @Override
    public void submitRenderTasks(
            RenderPassInfo<R> renderPassInfo,
            OrderedSubmitNodeCollector renderTasks,
            RenderType renderType) {
        super.submitRenderTasks(renderPassInfo, renderTasks, renderType);
        Integer entityId = renderPassInfo.renderState()
                .getRenderData(MedusaRenderState.ENTITY_ID);
        boolean permanent = Boolean.TRUE.equals(
                renderPassInfo.renderState().getRenderData(MedusaRenderState.PERMANENT));
        if (entityId == null || !permanent) {
            return;
        }
        float mining = ClientPetrifiedMining.progress(entityId);
        if (mining <= 0.001F) {
            return;
        }
        int stage = Mth.clamp((int) (mining * 10.0F), 0, 9);
        super.submitRenderTasks(
                renderPassInfo,
                renderTasks,
                MedusaRenderTypes.crack(TEXTURE, stage));
    }

    @Override
    public void adjustModelBonesForRender(
            RenderPassInfo<R> renderPassInfo,
            BoneSnapshots snapshots) {
        if (!Boolean.TRUE.equals(
                renderPassInfo.renderState().getRenderData(MedusaRenderState.HEADLESS))) {
            return;
        }
        snapshots.ifPresent(
                HEAD_BONE,
                snapshot -> snapshot.skipRender(true).skipChildrenRender(true));
    }

    @Override
    protected float getDeathMaxRotation(GeoRenderState renderState) {
        return 90.0F;
    }

    private static DefaultedEntityGeoModel<MedusaEntity> createModel() {
        return new DefaultedEntityGeoModel<MedusaEntity>(MODEL_ID) {
            @Override
            public Identifier getTextureResource(GeoRenderState renderState) {
                return TEXTURE;
            }
        };
    }
}
