package dev.alvar.echoespast.client;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.entity.UnknownEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Overlays the authored era panoply as a second geo so {@code unknown.geo.json}
 * stays the black silhouette.
 */
public final class UnknownEraAppearanceLayer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoRenderLayer<UnknownEntity, Void, R> {
    private static final Identifier UNKNOWN_ANIMS = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "unknown");
    private static final Identifier MEDIEVAL_TEXTURE = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "textures/entity/unknown_medieval_armor.png");
    private static final Identifier GREEK_TEXTURE = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "textures/entity/unknown_greek_armor.png");
    private static final Identifier EGYPTIAN_TEXTURE = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "textures/entity/unknown_egyptian_armor.png");

    private final GeoModel<UnknownEntity> medievalArmor;
    private final GeoModel<UnknownEntity> greekArmor;
    private final GeoModel<UnknownEntity> egyptianArmor;

    public UnknownEraAppearanceLayer(GeoRenderer<UnknownEntity, Void, R> renderer) {
        super(renderer);
        this.medievalArmor = armorModel("unknown_medieval_armor");
        this.greekArmor = armorModel("unknown_greek_armor");
        this.egyptianArmor = armorModel("unknown_egyptian_armor");
    }

    @Override
    public void preRender(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
        GeoModel<UnknownEntity> armorModel = modelForEra(
                renderPassInfo.renderState().getGeckolibData(UnknownRenderer.ERA));
        if (armorModel == null) {
            return;
        }
        BakedGeoModel armor = armorModel.getBakedModel(
                armorModel.getModelResource(renderPassInfo.renderState()));
        renderPassInfo.addBoneUpdater((info, snapshots) -> {
            for (GeoBone root : armor.topLevelBones()) {
                copySnapshots(snapshots::get, root);
            }
        });
    }

    @Override
    public void submitRenderTask(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
        Byte era = renderPassInfo.renderState().getGeckolibData(UnknownRenderer.ERA);
        GeoModel<UnknownEntity> armorModel = modelForEra(era);
        if (armorModel == null) {
            return;
        }
        Identifier texture = textureForEra(era);
        RenderType renderType = getRenderer().getRenderType(renderPassInfo.renderState(), texture);
        if (renderType == null) {
            return;
        }
        BakedGeoModel armor = armorModel.getBakedModel(
                armorModel.getModelResource(renderPassInfo.renderState()));
        int packedLight = renderPassInfo.packedLight();
        int packedOverlay = renderPassInfo.packedOverlay();
        int renderColor = renderPassInfo.renderColor();
        renderTasks.submitCustomGeometry(
                renderPassInfo.poseStack(),
                renderType,
                (pose, vertexConsumer) -> {
                    PoseStack poseStack = renderPassInfo.poseStack();
                    poseStack.pushPose();
                    poseStack.last().set(pose);
                    renderPassInfo.renderPosed(() -> armor.render(
                            renderPassInfo,
                            vertexConsumer,
                            packedLight,
                            packedOverlay,
                            renderColor));
                    poseStack.popPose();
                });
    }

    private GeoModel<UnknownEntity> modelForEra(Byte era) {
        if (era != null && era == UnknownEntity.ERA_MEDIEVAL) {
            return this.medievalArmor;
        }
        if (era != null && era == UnknownEntity.ERA_GREEK) {
            return this.greekArmor;
        }
        if (era != null && era == UnknownEntity.ERA_EGYPTIAN) {
            return this.egyptianArmor;
        }
        return null;
    }

    private static Identifier textureForEra(Byte era) {
        if (era != null && era == UnknownEntity.ERA_MEDIEVAL) {
            return MEDIEVAL_TEXTURE;
        }
        if (era != null && era == UnknownEntity.ERA_EGYPTIAN) {
            return EGYPTIAN_TEXTURE;
        }
        return GREEK_TEXTURE;
    }

    private static GeoModel<UnknownEntity> armorModel(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, name);
        return new DefaultedEntityGeoModel<UnknownEntity>(id).withAltAnimations(UNKNOWN_ANIMS);
    }

    private static void copySnapshots(
            java.util.function.Function<String, java.util.Optional<BoneSnapshot>> snapshots,
            GeoBone dest) {
        snapshots.apply(dest.name()).ifPresent(src -> BoneSnapshot.create(dest)
                .setTranslation(src.getTranslateX(), src.getTranslateY(), src.getTranslateZ())
                .setRotation(src.getRotX(), src.getRotY(), src.getRotZ())
                .setScale(src.getScaleX(), src.getScaleY(), src.getScaleZ())
                .apply());
        for (GeoBone child : dest.children()) {
            copySnapshots(snapshots, child);
        }
    }
}
