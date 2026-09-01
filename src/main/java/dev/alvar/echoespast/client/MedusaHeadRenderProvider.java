package dev.alvar.echoespast.client;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.model.DefaultedItemGeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import com.google.common.base.Suppliers;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.relic.MedusaHeadItem;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

/** Stable client renderer registration for {@link MedusaHeadItem}. */
public final class MedusaHeadRenderProvider implements GeoRenderProvider {
    private static final Supplier<GeoItemRenderer<MedusaHeadItem>> RENDERER =
            Suppliers.memoize(MedusaHeadRenderProvider::createRenderer);

    private static GeoItemRenderer<MedusaHeadItem> createRenderer() {
        Identifier model = Identifier.fromNamespaceAndPath(
                EchoesShowThePast.MOD_ID,
                "medusa_head");
        return new MedusaHeadRenderer<>(new DefaultedItemGeoModel<MedusaHeadItem>(model));
    }

    @Override
    public GeoItemRenderer<?> getGeoItemRenderer() {
        return RENDERER.get();
    }
}
