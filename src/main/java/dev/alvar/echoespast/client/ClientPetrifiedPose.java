package dev.alvar.echoespast.client;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.mixin.client.LivingEntityAccessor;
import dev.alvar.echoespast.mixin.client.WalkAnimationStateAccessor;
import dev.alvar.echoespast.network.PetrifiedPoseCapturePayload;
import dev.alvar.echoespast.relic.BakedModelPose;
import dev.alvar.echoespast.relic.PetrifiedPose;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.model.Model;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Restores private animation fields omitted from normal entity synchronization.
 * This runs after the client tick and before render-state extraction.
 */
public final class ClientPetrifiedPose {
    private static final Map<UUID, BakedModelPose> PENDING_MODEL_POSES =
            new HashMap<>();
    private static ClientLevel knownLevel;

    static void freezeAll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != knownLevel) {
            knownLevel = minecraft.level;
            PENDING_MODEL_POSES.clear();
        }
        if (minecraft.level == null) {
            return;
        }
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            PetrifiedPose pose = living.getExistingDataOrNull(EchoesShowThePast.PETRIFIED_POSE.get());
            if (pose == null || !pose.permanent()) {
                continue;
            }
            freeze(living, pose);
        }
    }

    public static void freeze(LivingEntity living, PetrifiedPose pose) {
        pose.freezeCommon(living);
        var animation = pose.animation();
        WalkAnimationStateAccessor walk = (WalkAnimationStateAccessor) living.walkAnimation;
        walk.echoesShowThePast$setSpeedOld(animation.walkSpeed());
        walk.echoesShowThePast$setSpeed(animation.walkSpeed());
        walk.echoesShowThePast$setPosition(animation.walkPosition() + animation.walkSpeed());
        walk.echoesShowThePast$setPositionScale(1.0F);
        if (animation.usingItem()) {
            living.startUsingItem(animation.usingItemOffHand()
                    ? InteractionHand.OFF_HAND
                    : InteractionHand.MAIN_HAND);
            ((LivingEntityAccessor) living).echoesShowThePast$setUseItemRemaining(
                    animation.useItemRemainingTicks());
        } else if (living.isUsingItem()) {
            living.stopUsingItem();
        }
    }

    /**
     * Runs after vanilla {@code setupAnim}. The first rendered frame becomes
     * the canonical statue pose; subsequent body and equipment models receive
     * that same named-part pose after their own animation setup.
     */
    public static void applyOrCaptureModel(Object renderState, Model<?> model) {
        if (!(renderState instanceof LivingEntityRenderState livingState)) {
            return;
        }
        BakedModelPose itemPreviewPose =
                livingState.getRenderData(MedusaRenderState.ITEM_PREVIEW_POSE);
        if (itemPreviewPose != null && !itemPreviewPose.isEmpty()) {
            PetrifiedModelPose.apply(model, itemPreviewPose);
            return;
        }
        Integer entityId = livingState.getRenderData(MedusaRenderState.ENTITY_ID);
        Minecraft minecraft = Minecraft.getInstance();
        if (entityId == null
                || minecraft.level == null
                || !(minecraft.level.getEntity(entityId) instanceof LivingEntity living)) {
            return;
        }
        PetrifiedPose pose = living.getExistingDataOrNull(
                EchoesShowThePast.PETRIFIED_POSE.get());
        if (pose == null || !pose.permanent()) {
            PENDING_MODEL_POSES.remove(living.getUUID());
            return;
        }

        BakedModelPose modelPose = pose.modelPose();
        if (!modelPose.isEmpty()) {
            PENDING_MODEL_POSES.put(living.getUUID(), modelPose);
            PetrifiedModelPose.apply(model, modelPose);
            return;
        }

        BakedModelPose pending = PENDING_MODEL_POSES.get(living.getUUID());
        if (pending == null) {
            pending = PetrifiedModelPose.capture(model);
            if (pending.isEmpty()) {
                return;
            }
            PENDING_MODEL_POSES.put(living.getUUID(), pending);
            ClientPacketDistributor.sendToServer(
                    new PetrifiedPoseCapturePayload(entityId, pending));
        }
        PetrifiedModelPose.apply(model, pending);
    }

    private ClientPetrifiedPose() {
    }
}
