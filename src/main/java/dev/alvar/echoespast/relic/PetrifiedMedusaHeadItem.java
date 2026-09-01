package dev.alvar.echoespast.relic;

import com.geckolib.animatable.client.GeoRenderProvider;
import dev.alvar.echoespast.client.PetrifiedMedusaHeadRenderProvider;
import java.util.function.Consumer;
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
        consumer.accept(new PetrifiedMedusaHeadRenderProvider());
    }
}
