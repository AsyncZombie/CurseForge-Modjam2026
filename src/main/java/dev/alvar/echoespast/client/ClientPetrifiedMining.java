package dev.alvar.echoespast.client;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.network.PetrifiedMobMinePayload;
import dev.alvar.echoespast.network.PetrifiedMobMiningVisualPayload;
import dev.alvar.echoespast.relic.RelicEffects;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class ClientPetrifiedMining {
    private static final long VISUAL_TIMEOUT_NANOS = 550_000_000L;
    private static final Map<Integer, MiningVisual> VISUALS = new HashMap<>();
    private static int swingCooldown;

    static void receive(PetrifiedMobMiningVisualPayload payload) {
        long now = System.nanoTime();
        if (payload.progress() <= 0.0F) {
            VISUALS.remove(payload.entityId());
            return;
        }
        VISUALS.put(
                payload.entityId(),
                new MiningVisual(
                        Math.clamp(payload.progress(), 0.0F, 1.0F),
                        now + VISUAL_TIMEOUT_NANOS,
                        payload.impact() ? now : 0L));
    }

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        Iterator<MiningVisual> iterator = VISUALS.values().iterator();
        while (iterator.hasNext()) {
            if (now >= iterator.next().expiresAtNanos) {
                iterator.remove();
            }
        }
        if (swingCooldown > 0) {
            swingCooldown--;
        }
        if (minecraft.player == null
                || minecraft.level == null
                || minecraft.screen != null
                || !minecraft.options.keyAttack.isDown()
                || !(minecraft.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity living)
                || !RelicEffects.isPermanentlyPetrified(living)) {
            return;
        }
        boolean creative = minecraft.player.getAbilities().instabuild;
        if (!creative && !minecraft.player.getMainHandItem().is(ItemTags.PICKAXES)) {
            return;
        }
        ClientPacketDistributor.sendToServer(new PetrifiedMobMinePayload(living.getId()));
        if (swingCooldown == 0) {
            minecraft.player.swing(InteractionHand.MAIN_HAND);
            swingCooldown = creative ? 4 : 6;
        }
    }

    public static float progress(int entityId) {
        MiningVisual visual = VISUALS.get(entityId);
        return visual == null ? 0.0F : visual.progress;
    }

    public static float impact(int entityId) {
        MiningVisual visual = VISUALS.get(entityId);
        if (visual == null || visual.impactAtNanos == 0L) {
            return 0.0F;
        }
        float elapsed = (System.nanoTime() - visual.impactAtNanos) / 160_000_000.0F;
        float clamped = Math.clamp(1.0F - elapsed, 0.0F, 1.0F);
        return clamped * clamped;
    }

    static void clear() {
        VISUALS.clear();
        swingCooldown = 0;
    }

    private record MiningVisual(
            float progress,
            long expiresAtNanos,
            long impactAtNanos) {
    }

    private ClientPetrifiedMining() {
    }
}
