package dev.alvar.echoespast.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.builtin.ItemInHandGeoLayer;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.entity.UnknownEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public final class UnknownRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<UnknownEntity, R> {
    static final DataTicket<Byte> ERA = DataTicket.create("echoes_unknown_era", Byte.class);
    private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "unknown");
    private static final Identifier VOID_TEXTURE = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID,
            "textures/entity/unknown_void.png");

    public UnknownRenderer(EntityRendererProvider.Context context) {
        super(context, createModel());
        withRenderLayer(new ItemInHandGeoLayer<>(
                context,
                this,
                "main_hand",
                "off_hand"));
        withRenderLayer(new UnknownEraAppearanceLayer<>(this));
        withScale(1.25F);
        this.shadowRadius = 0.625F;
    }

    @Override
    public void addRenderData(
            UnknownEntity animatable,
            Void relatedObject,
            R renderState,
            float partialTick) {
        super.addRenderData(animatable, relatedObject, renderState, partialTick);
        renderState.addGeckolibData(ERA, animatable.getEra());
    }

    /** The authored death animation supplies the fall; vanilla rotation would double it. */
    @Override
    protected float getDeathMaxRotation(GeoRenderState renderState) {
        return 0.0F;
    }

    private static DefaultedEntityGeoModel<UnknownEntity> createModel() {
        return new DefaultedEntityGeoModel<UnknownEntity>(MODEL_ID) {
            @Override
            public Identifier getTextureResource(GeoRenderState renderState) {
                return VOID_TEXTURE;
            }
        };
    }
}
