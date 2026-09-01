package dev.alvar.echoespast.relic;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.model.DefaultedItemGeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import com.google.common.base.Suppliers;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.client.MedusaHeadRenderer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Petrified Head of Medusa — same gaze as the living head, but brittle: five
 * vanilla durability uses, cannot petrify bosses, always affects players.
 */
public final class PetrifiedMedusaHeadItem extends MedusaHeadItem {
    public static final int MAX_USES = 5;

    public PetrifiedMedusaHeadItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean canPetrifyPlayers() {
        return true;
    }

    @Override
    protected boolean canPetrifyBosses() {
        return false;
    }

    @Override
    protected boolean canActivate(ItemStack stack) {
        return stack.getDamageValue() < stack.getMaxDamage();
    }

    @Override
    protected void onSuccessfulActivation(
            ItemStack stack, ServerLevel level, ServerPlayer user) {
        stack.hurtAndBreak(1, user, user.getUsedItemHand());
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private final Supplier<GeoItemRenderer<PetrifiedMedusaHeadItem>> renderer =
                    Suppliers.memoize(() -> {
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
                    });

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                return renderer.get();
            }
        });
    }
}
