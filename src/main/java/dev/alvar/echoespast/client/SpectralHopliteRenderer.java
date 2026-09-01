package dev.alvar.echoespast.client;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.entity.SpectralHopliteEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Depth-stable ivory hoplite with authored gold equipment and trim. */
public final class SpectralHopliteRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<SpectralHopliteEntity, R> {
    private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "unknown");
    private static final Identifier HOPLITE_MODEL_ID = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "spectral_hoplite");

    public SpectralHopliteRenderer(EntityRendererProvider.Context context) {
        super(context, createModel());
        withScale(1.0F);
        this.shadowRadius = 0.0F;
    }

    private static DefaultedEntityGeoModel<SpectralHopliteEntity> createModel() {
        return new DefaultedEntityGeoModel<SpectralHopliteEntity>(MODEL_ID)
                .withAltModel(HOPLITE_MODEL_ID);
    }

    @Override
    public int getRenderColor(
            SpectralHopliteEntity animatable,
            Void relatedObject,
            float partialTick) {
        int alpha = Math.clamp(Math.round(animatable.fadeAlpha(partialTick) * 224.0F), 0, 224);
        return alpha << 24 | 0xFFFDF7;
    }

    @Override
    public RenderType getRenderType(R renderState, Identifier texture) {
        // Under Iris this must be a genuinely lightmapped entity. Marking the
        // white/gold rig emissive makes shaderpack bloom blow the whole row out.
        if (EchoShaderCompatibility.isShaderPackActive()) {
            return RenderTypes.entityTranslucent(texture, false);
        }
        return EchoRenderTypes.SPECTRAL_HOPLITE;
    }

    @Override
    protected float getDeathMaxRotation(GeoRenderState renderState) {
        return 0.0F;
    }
}
