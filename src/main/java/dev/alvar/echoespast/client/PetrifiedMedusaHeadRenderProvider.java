package dev.alvar.echoespast.client;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.model.DefaultedItemGeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import com.google.common.base.Suppliers;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.relic.PetrifiedMedusaHeadItem;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

/** Stable client renderer registration for {@link PetrifiedMedusaHeadItem}. */
public final class PetrifiedMedusaHeadRenderProvider implements GeoRenderProvider {
    private static final Supplier<GeoItemRenderer<PetrifiedMedusaHeadItem>> RENDERER =
            Suppliers.memoize(PetrifiedMedusaHeadRenderProvider::createRenderer);

    private static GeoItemRenderer<PetrifiedMedusaHeadItem> createRenderer() {
        Identifier shared = Identifier.fromNamespaceAndPath(
                EchoesShowThePast.MOD_ID,
                "medusa_head");
        Identifier texture = Identifier.fromNamespaceAndPath(
                EchoesShowThePast.MOD_ID,
                "medusa_petrified_head");
        return new MedusaHeadRenderer<>(
                new DefaultedItemGeoModel<PetrifiedMedusaHeadItem>(texture)
                        .withAltModel(shared)
                        .withAltAnimations(shared));
    }

    @Override
    public GeoItemRenderer<?> getGeoItemRenderer() {
        return RENDERER.get();
    }
}
