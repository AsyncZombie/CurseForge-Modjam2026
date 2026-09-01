package dev.alvar.echoespast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alvar.echoespast.entity.UnknownEntity;
import dev.alvar.echoespast.entity.ai.UnknownEgyptianCombatGoal;
import dev.alvar.echoespast.entity.ai.UnknownGreekCombatGoal;
import dev.alvar.echoespast.entity.ai.UnknownMedievalCombatGoal;
import dev.alvar.echoespast.entity.combat.UnknownCombatState;
import dev.alvar.echoespast.entity.combat.UnknownEgyptianCombatMath;
import dev.alvar.echoespast.entity.combat.UnknownGreekCombatMath;
import dev.alvar.echoespast.world.TimelessDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * World-space Greek combat language. Every shape is continuous rendered
 * geometry; no server particle is used as a telegraph or impact stand-in.
 */
public final class UnknownGreekCombatRenderer {
    private static final double MAX_RENDER_DISTANCE_SQR = 72.0D * 72.0D;
    private static final double LANE_LENGTH = 8.5D;
    private static final int LANE_SEGMENTS = 24;
    private static final double[] PHALANX_LANES =
            {-8.0D, -6.0D, -4.0D, -2.0D, 0.0D, 2.0D, 4.0D, 6.0D, 8.0D};
    private static final int WHITE = 0xFFFFFF;
    private static final int GOLD = 0xFFD447;
    private static final int SOLAR_GOLD = 0xFFB000;
    private static final int SOLAR_WHITE = 0xFFF8DE;
    private static final int NILE_BLUE = 0x2F78D0;
    private static final int ROYAL_VIOLET = 0x8E58D4;
    private static final int DESERT_STONE = 0xD8B879;
    private static final int DESERT_STONE_LIGHT = 0xF0D9A0;
    private static final int DESERT_STONE_SHADOW = 0x8B6739;
    private static final int LAPIS_INLAY = 0x174D8F;
    private static final int MEDIEVAL_STEEL = 0xF0F3F5;
    private static final int MEDIEVAL_CORAL = 0xD87568;

    public static void submit(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        if (EchoShaderCompatibility.isShadowPass()) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof UnknownEntity boss)) {
                continue;
            }
            UnknownCombatState state = boss.getCombatState();
            float fxAge = boss.getCombatFxTick() < 0
                    ? Float.POSITIVE_INFINITY
                    : level.getGameTime() + partialTick - boss.getCombatFxTick();
            float fxLifetime = combatFxLifetime(boss.getCombatFxKind());
            boolean rendersAttack = state == UnknownCombatState.STAB
                    || state == UnknownCombatState.CHARGE
                    || state == UnknownCombatState.PHALANX
                    || state == UnknownCombatState.JAVELIN
                    || state == UnknownCombatState.SPEAR_ERUPTION
                    || state == UnknownCombatState.SHIELD_BASH
                    || state == UnknownCombatState.KHOPESH_COMBO
                    || state == UnknownCombatState.DUAT_GATE
                    || state == UnknownCombatState.SOLAR_JUDGMENT
                    || state == UnknownCombatState.SEKHMET_HUNT
                    || (state == UnknownCombatState.MEDIEVAL_COMBO
                            && !boss.isRuinsCombatVariant());
            if (!rendersAttack && fxAge > fxLifetime) {
                continue;
            }

            Vec3 origin = new Vec3(
                    Mth.lerp(partialTick, boss.xo, boss.getX()),
                    Mth.lerp(partialTick, boss.yo, boss.getY()),
                    Mth.lerp(partialTick, boss.zo, boss.getZ()));
            if (origin.distanceToSqr(camera) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            Vec3 direction = boss.getLockedCombatDirection();
            if (direction.horizontalDistanceSqr() <= 1.0E-6D) {
                direction = boss.getLookAngle();
            }
            direction = new Vec3(direction.x, 0.0D, direction.z).normalize();
            if (direction.horizontalDistanceSqr() <= 1.0E-6D) {
                continue;
            }
            float attackAge = rendersAttack
                    ? boss.combatElapsedTicks(level.getGameTime()) + partialTick
                    : Float.POSITIVE_INFINITY;
            Frame frame = new Frame(
                    origin,
                    direction,
                    state,
                    boss.isRuinsCombatVariant(),
                    attackAge,
                    boss.getCombatAnchor(),
                    boss.getCombatGapOffset(),
                    boss.getCombatCorridorLength(),
                    fxAge,
                    boss.getCombatFxKind(),
                    boss.getCombatFxPosition(),
                    boss.getCombatVariant());
            submitPass(
                    event.getSubmitNodeCollector(),
                    event.getPoseStack(),
                    camera,
                    level,
                    frame);
        }
    }

    private static void submitEgyptianFields(
            SubmitNodeCollector collector,
            PoseStack poseStack,
            Vec3 camera,
            ClientLevel level,
            Frame frame,
            float partialTick) {
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        collector.submitCustomGeometry(
                poseStack,
                EchoRenderTypes.EGYPTIAN_ARCHITECTURE,
                (pose, consumer) -> renderPersistentWalls(
                        level, pose, consumer, frame, level.getGameTime() + partialTick));
        collector.submitCustomGeometry(
                poseStack,
                EchoRenderTypes.EGYPTIAN_CHARIOT,
                (pose, consumer) -> renderPersistentChariots(
                        level, pose, consumer, frame, level.getGameTime() + partialTick));
        poseStack.popPose();
    }

    private static void renderPersistentWalls(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            float gameTime) {
        for (ClientUnknownEgyptianEffects.Wall wall : ClientUnknownEgyptianEffects.walls()) {
            if (gameTime >= wall.expireGameTime()) {
                continue;
            }
            float life = (float) (wall.expireGameTime() - gameTime);
            float fade = smooth(Math.clamp(life / 10.0F, 0.0F, 1.0F));
            float rise = smooth(Math.clamp(
                    (gameTime - wall.spawnGameTime())
                            / UnknownEgyptianCombatGoal.GATE_COLLISION_RISE_TICKS,
                    0.0F,
                    1.0F));
            solidDuatWall(
                    level,
                    pose,
                    consumer,
                    wall.center(),
                    wall.direction(),
                    wall.halfSpan(),
                    rise,
                    fade);
        }
    }

    private static void solidDuatWall(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 center,
            Vec3 direction,
            double halfSpan,
            float rise,
            float fade) {
        Vec3 flat = new Vec3(direction.x, 0.0D, direction.z);
        if (flat.lengthSqr() <= 1.0E-8D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            flat = flat.normalize();
        }
        Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);
        Vec3 base = groundPoint(level, center).add(0.0D, 0.02D, 0.0D);
        double halfThick = UnknownEgyptianCombatMath.WALL_HALF_THICK;
        double fullHeight = UnknownEgyptianCombatMath.WALL_HEIGHT;
        double height = fullHeight * rise;
        if (height <= 0.02D) {
            return;
        }
        Vec3 emergedBase = base.add(0.0D, height - fullHeight, 0.0D);
        int bayCount = Math.max(3, (int) Math.round(halfSpan * 0.78D));
        double bayWidth = halfSpan * 2.0D / bayCount;
        int stone = color(0.99F * fade, DESERT_STONE);
        int lightStone = color(0.99F * fade, DESERT_STONE_LIGHT);
        int shadow = color(0.99F * fade, DESERT_STONE_SHADOW);
        for (int bay = 0; bay < bayCount; bay++) {
            double across = -halfSpan + bayWidth * (bay + 0.5D);
            Vec3 bayCenter = emergedBase
                    .add(side.scale(across))
                    .add(0.0D, height * 0.5D, 0.0D);
            orientedBox(
                    pose,
                    consumer,
                    bayCenter,
                    flat,
                    side,
                    bayWidth * 0.485D,
                    halfThick,
                    height * 0.5D,
                    (bay & 1) == 0 ? stone : lightStone);
            if (height > 1.25D) {
                double glyphHeight = Math.min(2.35D, height * 0.62D);
                Vec3 glyphCenter = emergedBase
                        .add(side.scale(across))
                        .add(flat.scale(halfThick + 0.018D))
                        .add(0.0D, Math.min(height * 0.53D, height - glyphHeight * 0.5D), 0.0D);
                hieroglyphBay(
                        pose, consumer, glyphCenter, side, flat,
                        bayWidth * 0.62D, glyphHeight, bay, fade);
                hieroglyphBay(
                        pose,
                        consumer,
                        glyphCenter.subtract(flat.scale((halfThick + 0.018D) * 2.0D)),
                        side.scale(-1.0D),
                        flat.scale(-1.0D),
                        bayWidth * 0.62D,
                        glyphHeight,
                        bay + 3,
                        fade);
            }
        }
        // Heavy plinth, lintel and square jambs sell real sandstone mass.
        orientedBox(
                pose, consumer,
                emergedBase.add(0.0D, 0.18D, 0.0D),
                flat, side,
                halfSpan + 0.28D, halfThick + 0.16D, 0.18D, shadow);
        orientedBox(
                pose, consumer,
                emergedBase.add(0.0D, height - 0.22D, 0.0D),
                flat, side,
                halfSpan + 0.36D, halfThick + 0.18D, 0.22D, lightStone);
        for (double jamb : new double[] {-halfSpan, halfSpan}) {
            Vec3 post = emergedBase.add(side.scale(jamb)).add(0.0D, height * 0.5D, 0.0D);
            orientedBox(
                    pose, consumer, post, flat, side,
                    0.22D, halfThick + 0.14D, height * 0.5D, shadow);
            orientedBox(
                    pose, consumer, post.add(0.0D, height * 0.5D - 0.16D, 0.0D),
                    flat, side,
                    0.38D, halfThick + 0.24D, 0.16D,
                    color(0.99F * fade, SOLAR_GOLD));
        }
    }

    private static void hieroglyphBay(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 center,
            Vec3 horizontal,
            Vec3 normal,
            double width,
            double height,
            int variant,
            float fade) {
        Vec3 side = horizontal.normalize();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        double halfWidth = width * 0.5D;
        double halfHeight = height * 0.5D;
        int carved = color(0.9F * fade, DESERT_STONE_SHADOW);
        int gold = color(0.98F * fade, SOLAR_GOLD);
        int lapis = color(0.96F * fade, LAPIS_INLAY);
        Vec3 lowerLeft = center.subtract(side.scale(halfWidth)).subtract(up.scale(halfHeight));
        Vec3 upperLeft = center.subtract(side.scale(halfWidth)).add(up.scale(halfHeight));
        Vec3 upperRight = center.add(side.scale(halfWidth)).add(up.scale(halfHeight));
        Vec3 lowerRight = center.add(side.scale(halfWidth)).subtract(up.scale(halfHeight));
        ribbonInPlane(consumer, pose, lowerLeft, upperLeft, normal, 0.025D, 0.0F, carved);
        ribbonInPlane(consumer, pose, upperLeft, upperRight, normal, 0.025D, 0.2F, carved);
        ribbonInPlane(consumer, pose, upperRight, lowerRight, normal, 0.025D, 0.4F, carved);
        ribbonInPlane(consumer, pose, lowerRight, lowerLeft, normal, 0.025D, 0.6F, carved);
        switch (Math.floorMod(variant, 4)) {
            case 0 -> {
                // Ankh: loop, staff and crossbar.
                Vec3 loop = center.add(up.scale(height * 0.22D));
                circularVerticalRibbon(
                        pose, consumer, loop, side, up,
                        Math.min(width, height) * 0.17D, 0.035D, gold);
                ribbonInPlane(
                        consumer, pose,
                        loop.subtract(up.scale(height * 0.02D)),
                        center.subtract(up.scale(height * 0.38D)),
                        normal, 0.04D, 0.0F, lapis);
                ribbonInPlane(
                        consumer, pose,
                        center.subtract(side.scale(width * 0.25D)),
                        center.add(side.scale(width * 0.25D)),
                        normal, 0.035D, 0.2F, gold);
            }
            case 1 -> hieroglyphEye(
                    pose, consumer, center, side, up, normal,
                    width * 0.76D, height * 0.34D, gold, lapis);
            case 2 -> {
                // Scarab with a solid ceremonial axis and articulated legs.
                Vec3 body = center.add(up.scale(height * 0.05D));
                circularVerticalRibbon(
                        pose, consumer, body, side, up,
                        Math.min(width, height) * 0.18D, 0.05D, lapis);
                ribbonInPlane(
                        consumer, pose,
                        body.subtract(up.scale(height * 0.28D)),
                        body.add(up.scale(height * 0.28D)),
                        normal, 0.035D, 0.0F, gold);
                for (double sign : new double[] {-1.0D, 1.0D}) {
                    for (int leg = -1; leg <= 1; leg++) {
                        Vec3 root = body.add(up.scale(leg * height * 0.13D));
                        Vec3 knee = root.add(side.scale(sign * width * 0.24D));
                        Vec3 foot = knee
                                .add(side.scale(sign * width * 0.16D))
                                .add(up.scale(leg * height * 0.08D));
                        ribbonInPlane(consumer, pose, root, knee, normal, 0.028D, leg * 0.1F, carved);
                        ribbonInPlane(consumer, pose, knee, foot, normal, 0.028D, leg * 0.1F, gold);
                    }
                }
            }
            default -> {
                // Reed and water bars, deliberately asymmetric like carved text.
                Vec3 reedBottom = center.subtract(up.scale(height * 0.34D));
                Vec3 reedTop = center.add(up.scale(height * 0.34D));
                ribbonInPlane(consumer, pose, reedBottom, reedTop, normal, 0.04D, 0.0F, lapis);
                for (int leaf = 0; leaf < 3; leaf++) {
                    Vec3 root = reedTop.subtract(up.scale(leaf * height * 0.11D));
                    double sign = (leaf & 1) == 0 ? 1.0D : -1.0D;
                    ribbonInPlane(
                            consumer, pose, root,
                            root.add(side.scale(sign * width * 0.31D)).add(up.scale(height * 0.08D)),
                            normal, 0.03D, leaf * 0.12F, gold);
                }
                for (int bar = -1; bar <= 1; bar++) {
                    Vec3 line = center
                            .subtract(up.scale(height * 0.28D))
                            .add(up.scale(bar * height * 0.075D));
                    ribbonInPlane(
                            consumer, pose,
                            line.subtract(side.scale(width * 0.34D)),
                            line.add(side.scale(width * 0.34D)),
                            normal, 0.025D, bar * 0.1F, carved);
                }
            }
        }
    }

    private static void hieroglyphEye(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 center,
            Vec3 side,
            Vec3 up,
            Vec3 normal,
            double width,
            double height,
            int outline,
            int iris) {
        Vec3 previousTop = center.subtract(side.scale(width * 0.5D));
        Vec3 previousBottom = previousTop;
        for (int segment = 1; segment <= 12; segment++) {
            double t = segment / 12.0D;
            double x = (t - 0.5D) * width;
            double arch = Math.sin(Math.PI * t) * height * 0.5D;
            Vec3 top = center.add(side.scale(x)).add(up.scale(arch));
            Vec3 bottom = center.add(side.scale(x)).subtract(up.scale(arch));
            ribbonInPlane(consumer, pose, previousTop, top, normal, 0.03D, (float) t, outline);
            ribbonInPlane(consumer, pose, previousBottom, bottom, normal, 0.03D, (float) t, outline);
            previousTop = top;
            previousBottom = bottom;
        }
        circularVerticalRibbon(
                pose, consumer, center, side, up,
                Math.min(width, height) * 0.22D, 0.04D, iris);
        ribbonInPlane(
                consumer, pose,
                center.subtract(up.scale(height * 0.18D)),
                center.subtract(up.scale(height * 0.55D)).add(side.scale(width * 0.18D)),
                normal, 0.035D, 0.0F, outline);
    }

    private static void renderPersistentChariots(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            float gameTime) {
        ClientUnknownEgyptianEffects.ChariotRaid raid = ClientUnknownEgyptianEffects.chariotRaid();
        if (!raid.active()) {
            return;
        }
        boolean ruins = raid.ruins();
        int count = UnknownEgyptianCombatGoal.chariotCount(ruins);
        float age = gameTime - raid.startGameTime();
        float remaining = raid.expireGameTime() - gameTime;
        float summon = smooth(Math.clamp((age + UnknownEgyptianCombatGoal.chariotWarningTicks(ruins)) / 12.0F,
                0.0F, 1.0F));
        float dismiss = smooth(Math.clamp(remaining / 12.0F, 0.0F, 1.0F));
        float bodyAlpha = summon * dismiss;
        for (int chariot = 0; chariot < count; chariot++) {
            double progress = Math.max(0.0D, age)
                    / UnknownEgyptianCombatGoal.chariotTravelTicks(ruins);
            Vec3 rawPosition = UnknownEgyptianCombatMath.chariotPerimeterPoint(
                    raid.minimumX(), raid.maximumX(), raid.minimumZ(), raid.maximumZ(),
                    chariot, count, raid.seed(), progress);
            Vec3 travel = UnknownEgyptianCombatMath.chariotPerimeterDirection(
                    raid.minimumX(), raid.maximumX(), raid.minimumZ(), raid.maximumZ(),
                    chariot, count, raid.seed(), progress);
            Vec3 grounded = chariotGroundPoint(level, rawPosition, frame.origin.y)
                    .add(0.0D, 0.06D, 0.0D);
            spectralChariot(
                    pose,
                    consumer,
                    grounded,
                    travel,
                    bodyAlpha * 0.98F,
                    chariot,
                    age);
            chariotStandard(pose, consumer, grounded, travel, bodyAlpha, chariot);
        }
        for (ClientUnknownEgyptianEffects.ChariotArrow activeArrow
                : ClientUnknownEgyptianEffects.chariotArrows()) {
            float arrowAge = gameTime - activeArrow.launchGameTime();
            float flight = Math.max(1.0F, activeArrow.impactGameTime() - activeArrow.launchGameTime());
            if (arrowAge < 0.0F || arrowAge > flight + 2.0F) {
                continue;
            }
            double progress = Math.clamp(arrowAge / flight, 0.0F, 1.0F);
            double sign = ((activeArrow.id() + raid.seed()) & 1) == 0 ? 1.0D : -1.0D;
            Vec3 arrow = UnknownEgyptianCombatMath.chariotArrowPoint(
                    activeArrow.start(), activeArrow.end(), progress, sign);
            Vec3 next = UnknownEgyptianCombatMath.chariotArrowPoint(
                    activeArrow.start(), activeArrow.end(), Math.min(1.0D, progress + 0.025D), sign);
            Vec3 tangent = next.subtract(arrow);
            if (tangent.lengthSqr() <= 1.0E-8D) {
                tangent = activeArrow.end().subtract(activeArrow.start());
            }
            solarArrow3d(
                    pose,
                    consumer,
                    arrow,
                    tangent.normalize(),
                    1.7D,
                    0.98F,
                    activeArrow.chariotIndex(),
                    false);
        }
    }

    private static void spectralChariot(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 ground,
            Vec3 travel,
            float alpha,
            int index,
            float age) {
        if (alpha <= 0.01F) {
            return;
        }
        Vec3 forward = new Vec3(travel.x, 0.0D, travel.z).normalize();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        int gold = color(alpha, SOLAR_GOLD);
        int pale = color(alpha * 0.98F, SOLAR_WHITE);
        int blue = color(alpha * 0.96F, LAPIS_INLAY);
        int darkGold = color(alpha * 0.98F, 0xB86F12);
        Vec3 cart = ground.subtract(forward.scale(0.78D));

        for (double wheelSide : new double[] {-0.82D, 0.82D}) {
            Vec3 wheel = cart.add(side.scale(wheelSide)).add(0.0D, 0.56D, 0.0D);
            circularVerticalRibbon(pose, consumer, wheel, forward, up, 0.61D, 0.105D, darkGold);
            orientedBox(pose, consumer, wheel, forward, side,
                    0.12D, 0.13D, 0.12D, gold);
            for (int spoke = 0; spoke < 8; spoke++) {
                double angle = spoke * Math.PI / 4.0D + age * 0.18D;
                Vec3 rim = wheel
                        .add(forward.scale(Math.cos(angle) * 0.52D))
                        .add(up.scale(Math.sin(angle) * 0.52D));
                ribbonOnPlane(consumer, pose, wheel, rim, side, 0.032D, spoke * 0.1F, gold);
            }
        }
        ribbonInPlane(
                consumer,
                pose,
                cart.subtract(side.scale(0.92D)).add(0.0D, 0.56D, 0.0D),
                cart.add(side.scale(0.92D)).add(0.0D, 0.56D, 0.0D),
                up,
                0.045D,
                0.0F,
                gold);
        orientedBox(pose, consumer, cart.add(0.0D, 0.77D, 0.0D), forward, side,
                0.86D, 0.72D, 0.48D, blue);
        orientedBox(pose, consumer, cart.subtract(forward.scale(0.58D)).add(0.0D, 1.2D, 0.0D),
                forward, side, 0.9D, 0.14D, 0.58D, pale);
        ribbonInPlane(
                consumer,
                pose,
                cart.subtract(side.scale(0.78D)).add(0.0D, 1.17D, 0.0D),
                cart.add(side.scale(0.78D)).add(0.0D, 1.17D, 0.0D),
                forward,
                0.055D,
                0.0F,
                gold);

        float gallop = Mth.sin(age * 0.72F + index * 1.7F);
        for (double horseSide : new double[] {-0.52D, 0.52D}) {
            Vec3 horse = ground.add(forward.scale(1.78D)).add(side.scale(horseSide * 1.18D));
            orientedBox(pose, consumer, horse.add(0.0D, 0.92D, 0.0D), forward, side,
                    0.34D, 0.76D, 0.52D, pale);
            Vec3 neck = horse.add(forward.scale(0.62D)).add(0.0D, 1.34D, 0.0D);
            orientedBox(pose, consumer, neck, forward, side,
                    0.24D, 0.34D, 0.46D, pale);
            orientedBox(
                    pose,
                    consumer,
                    neck.add(forward.scale(0.37D)).add(0.0D, 0.43D, 0.0D),
                    forward,
                    side,
                    0.23D,
                    0.37D,
                    0.3D,
                    pale);
            orientedBox(pose, consumer,
                    neck.subtract(forward.scale(0.08D)).add(0.0D, 0.35D, 0.0D),
                    forward, side, 0.27D, 0.1D, 0.5D, gold);
            for (double ear : new double[] {-0.13D, 0.13D}) {
                orientedBox(pose, consumer,
                        neck.add(forward.scale(0.38D)).add(side.scale(ear)).add(0.0D, 0.79D, 0.0D),
                        forward, side, 0.045D, 0.08D, 0.14D, darkGold);
            }
            for (int leg = 0; leg < 4; leg++) {
                double longitudinal = leg < 2 ? -0.42D : 0.4D;
                double lateral = (leg & 1) == 0 ? -0.18D : 0.18D;
                double stride = ((leg + index) & 1) == 0 ? gallop * 0.22D : -gallop * 0.22D;
                Vec3 hip = horse
                        .add(forward.scale(longitudinal))
                        .add(side.scale(lateral))
                        .add(0.0D, 0.72D, 0.0D);
                Vec3 legCenter = hip
                        .add(forward.scale(stride * 0.5D))
                        .add(0.0D, -0.34D, 0.0D);
                orientedBox(pose, consumer, legCenter, forward, side,
                        0.075D, 0.09D, 0.34D, darkGold);
            }
            ribbonInPlane(
                    consumer,
                    pose,
                    cart.add(forward.scale(0.42D)).add(side.scale(horseSide * 0.7D)).add(0.0D, 0.92D, 0.0D),
                    neck.add(0.0D, 0.12D, 0.0D),
                    up,
                    0.022D,
                    0.0F,
                    gold);
        }

        Vec3 rider = cart.subtract(forward.scale(0.08D));
        orientedBox(pose, consumer, rider.add(0.0D, 1.58D, 0.0D), forward, side,
                0.31D, 0.22D, 0.52D, blue);
        orientedBox(pose, consumer, rider.add(0.0D, 2.15D, 0.0D), forward, side,
                0.2D, 0.2D, 0.22D, pale);
        orientedBox(pose, consumer, rider.subtract(forward.scale(0.08D)).add(0.0D, 2.18D, 0.0D),
                forward, side, 0.28D, 0.18D, 0.27D, gold);
        ribbonInPlane(
                consumer, pose,
                rider.subtract(forward.scale(0.22D)).add(0.0D, 2.17D, 0.0D),
                rider.subtract(forward.scale(0.5D)).add(0.0D, 1.72D, 0.0D),
                side, 0.1D, 0.0F, blue);
        Vec3 bowCenter = rider.add(forward.scale(0.42D)).add(side.scale(0.34D)).add(0.0D, 1.73D, 0.0D);
        Vec3 bowTop = bowCenter.add(side.scale(0.3D)).add(0.0D, 0.46D, 0.0D);
        Vec3 bowBottom = bowCenter.subtract(side.scale(0.3D)).add(0.0D, -0.46D, 0.0D);
        Vec3 bowGrip = bowCenter.add(forward.scale(0.18D));
        ribbonInPlane(consumer, pose, bowTop, bowGrip, forward, 0.026D, 0.0F, gold);
        ribbonInPlane(consumer, pose, bowGrip, bowBottom, forward, 0.026D, 0.3F, gold);
        ribbonInPlane(consumer, pose, bowTop, bowBottom, forward, 0.012D, 0.0F, pale);
    }

    private static void chariotStandard(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 ground,
            Vec3 travel,
            float alpha,
            int index) {
        if (alpha <= 0.01F) {
            return;
        }
        Vec3 forward = new Vec3(travel.x, 0.0D, travel.z).normalize();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 base = ground.subtract(forward.scale(1.42D)).add(side.scale(index % 2 == 0 ? -0.58D : 0.58D));
        Vec3 top = base.add(0.0D, 3.15D, 0.0D);
        int gold = color(alpha * 0.98F, SOLAR_GOLD);
        int lapis = color(alpha * 0.96F, LAPIS_INLAY);
        int ivory = color(alpha * 0.98F, SOLAR_WHITE);
        ribbonInPlane(consumer, pose, base.add(0.0D, 0.55D, 0.0D), top, side,
                0.045D, 0.0F, gold);
        Vec3 disk = top.add(0.0D, 0.12D, 0.0D);
        circularVerticalRibbon(pose, consumer, disk, side, up, 0.31D, 0.07D, gold);
        Vec3 bannerTop = top.subtract(up.scale(0.38D));
        Vec3 bannerBottom = top.subtract(up.scale(1.34D));
        texturedQuad(
                consumer,
                pose,
                bannerTop.subtract(side.scale(0.42D)),
                bannerBottom.subtract(side.scale(0.31D)),
                bannerBottom.add(side.scale(0.31D)),
                bannerTop.add(side.scale(0.42D)),
                0.0F, 0.0F, 1.0F, 1.0F,
                lapis);
        hieroglyphEye(
                pose,
                consumer,
                top.subtract(up.scale(0.84D)).add(forward.scale(0.012D)),
                side,
                up,
                forward,
                0.56D,
                0.24D,
                gold,
                ivory);
    }

    private static void solarJudgment(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        int warning = UnknownEgyptianCombatGoal.judgmentWarningTicks(frame.ruins);
        int active = UnknownEgyptianCombatGoal.judgmentActiveTicks(frame.ruins);
        if (frame.attackAge >= warning + active) {
            return;
        }
        float charge = smooth(frame.attackAge / warning);
        judgmentGroundLane(level, pose, consumer, frame, charge);
        if (frame.attackAge >= warning) {
            float activeAge = frame.attackAge - warning;
            judgmentTerrainCleave(level, pose, consumer, frame, activeAge, active);
        }
    }

    private static void judgmentGroundLane(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            float charge) {
        int lock = UnknownEgyptianCombatGoal.judgmentLockTick(frame.ruins);
        boolean locked = frame.attackAge >= lock;
        float reveal = smooth(frame.attackAge / Math.max(1.0F, lock));
        double length = Mth.lerp(
                reveal,
                2.4D,
                UnknownEgyptianCombatGoal.JUDGMENT_LENGTH);
        double halfWidth = UnknownEgyptianCombatGoal.judgmentHalfWidth(frame.ruins);
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        int segments = 64;
        double startDistance = UnknownEgyptianCombatGoal.JUDGMENT_WAVE_START;
        Vec3 previousRaw = frame.origin.add(frame.direction.scale(startDistance));
        Vec3 previousCenter = judgmentGroundPoint(level, previousRaw);
        Vec3 previousLeft = judgmentGroundPoint(level, previousRaw.add(side.scale(halfWidth)));
        Vec3 previousRight = judgmentGroundPoint(level, previousRaw.subtract(side.scale(halfWidth)));
        double previousDistance = startDistance;
        float pulse = 0.90F + 0.10F * Mth.sin(frame.attackAge * 0.52F);
        float lockFlash = locked
                ? 0.88F + 0.12F * Mth.sin((frame.attackAge - lock) * 0.9F)
                : 0.72F;
        float railAlpha = (0.38F + charge * 0.42F) * pulse * lockFlash;
        for (int segment = 1; segment <= segments; segment++) {
            double distance = startDistance + (length - startDistance) * segment / segments;
            Vec3 raw = frame.origin.add(frame.direction.scale(distance));
            Vec3 leftRaw = raw.add(side.scale(halfWidth));
            Vec3 rightRaw = raw.subtract(side.scale(halfWidth));
            Vec3 center = judgmentGroundPoint(
                    level, new Vec3(raw.x, previousCenter.y, raw.z));
            Vec3 left = judgmentGroundPoint(
                    level, new Vec3(leftRaw.x, previousLeft.y, leftRaw.z));
            Vec3 right = judgmentGroundPoint(
                    level, new Vec3(rightRaw.x, previousRight.y, rightRaw.z));
            if (judgmentSurfaceContinuous(previousCenter, center)
                    && judgmentSurfaceContinuous(previousLeft, left)
                    && judgmentSurfaceContinuous(previousRight, right)) {
                texturedQuad(
                        consumer,
                        pose,
                        previousLeft,
                        previousRight,
                        right,
                        left,
                        (float) (previousDistance * 0.21D - frame.attackAge * 0.08D),
                        0.0F,
                        (float) (distance * 0.21D - frame.attackAge * 0.08D),
                        1.0F,
                        color((0.045F + charge * 0.075F) * pulse, SOLAR_GOLD));
            }
            if (judgmentSurfaceContinuous(previousLeft, left)) {
                ribbonOnPlane(consumer, pose, previousLeft, left, side, 0.09D,
                        segment * 0.11F - frame.attackAge * 0.05F,
                        color(railAlpha, SOLAR_GOLD));
                ribbonOnPlane(consumer, pose,
                        previousLeft.add(0.0D, 0.009D, 0.0D),
                        left.add(0.0D, 0.009D, 0.0D),
                        side, 0.026D,
                        segment * 0.15F - frame.attackAge * 0.08F,
                        color(railAlpha * 0.88F, SOLAR_WHITE));
            }
            if (judgmentSurfaceContinuous(previousRight, right)) {
                ribbonOnPlane(consumer, pose, previousRight, right, side, 0.09D,
                        segment * 0.11F - frame.attackAge * 0.05F,
                        color(railAlpha, SOLAR_GOLD));
                ribbonOnPlane(consumer, pose,
                        previousRight.add(0.0D, 0.009D, 0.0D),
                        right.add(0.0D, 0.009D, 0.0D),
                        side, 0.026D,
                        segment * 0.15F - frame.attackAge * 0.08F,
                        color(railAlpha * 0.88F, SOLAR_WHITE));
            }
            if (locked && segment % 8 == 0) {
                Vec3 tip = center.add(frame.direction.scale(0.24D));
                Vec3 leftRootRaw = raw
                        .subtract(frame.direction.scale(0.42D))
                        .add(side.scale(halfWidth * 0.48D));
                Vec3 rightRootRaw = raw
                        .subtract(frame.direction.scale(0.42D))
                        .subtract(side.scale(halfWidth * 0.48D));
                Vec3 leftRoot = judgmentGroundPoint(
                        level, new Vec3(leftRootRaw.x, center.y, leftRootRaw.z));
                Vec3 rightRoot = judgmentGroundPoint(
                        level, new Vec3(rightRootRaw.x, center.y, rightRootRaw.z));
                if (judgmentSurfaceContinuous(leftRoot, tip)) {
                    ribbonInPlane(consumer, pose, leftRoot, tip, up, 0.024D,
                            segment * 0.07F, color(railAlpha * 0.74F, SOLAR_GOLD));
                }
                if (judgmentSurfaceContinuous(rightRoot, tip)) {
                    ribbonInPlane(consumer, pose, rightRoot, tip, up, 0.024D,
                            segment * 0.07F, color(railAlpha * 0.74F, SOLAR_GOLD));
                }
            }
            previousCenter = center;
            previousLeft = left;
            previousRight = right;
            previousDistance = distance;
        }
        if (locked) {
            Vec3 tipRaw = frame.origin.add(frame.direction.scale(length));
            Vec3 backRaw = tipRaw.subtract(frame.direction.scale(1.15D));
            Vec3 tip = previousCenter;
            Vec3 backLeftRaw = backRaw.add(side.scale(halfWidth * 0.62D));
            Vec3 backRightRaw = backRaw.subtract(side.scale(halfWidth * 0.62D));
            Vec3 backLeft = judgmentGroundPoint(
                    level, new Vec3(backLeftRaw.x, previousLeft.y, backLeftRaw.z));
            Vec3 backRight = judgmentGroundPoint(
                    level, new Vec3(backRightRaw.x, previousRight.y, backRightRaw.z));
            ribbonInPlane(consumer, pose, tip, backLeft, up,
                    0.065D, 0.0F, color(0.86F, SOLAR_GOLD));
            ribbonInPlane(consumer, pose, tip, backRight, up,
                    0.065D, 0.0F, color(0.86F, SOLAR_GOLD));
            ribbonInPlane(consumer, pose,
                    tip.add(0.0D, 0.01D, 0.0D),
                    backLeft.add(0.0D, 0.01D, 0.0D),
                    up, 0.022D, 0.0F, color(0.92F, SOLAR_WHITE));
            ribbonInPlane(consumer, pose,
                    tip.add(0.0D, 0.01D, 0.0D),
                    backRight.add(0.0D, 0.01D, 0.0D),
                    up, 0.022D, 0.0F, color(0.92F, SOLAR_WHITE));
        }
    }

    /**
     * A terrain-bound solar verdict. The luminous wake occupies the real
     * hitbox and its leading bar is the exact authoritative damage front.
     */
    private static void judgmentTerrainCleave(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            float activeAge,
            int activeTicks) {
        float fade = 1.0F - smooth(Math.clamp(
                activeAge - (activeTicks - 1.0F),
                0.0F,
                1.0F));
        float pulse = 0.92F + 0.08F * Mth.sin(activeAge * 2.15F);
        double length = UnknownEgyptianCombatGoal.JUDGMENT_LENGTH;
        double halfWidth = UnknownEgyptianCombatGoal.judgmentHalfWidth(frame.ruins);
        double startDistance = UnknownEgyptianCombatGoal.JUDGMENT_WAVE_START;
        double visibleDistance = UnknownEgyptianCombatGoal.judgmentWaveDistance(
                frame.ruins, activeAge);
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        float phase = -frame.attackAge * 0.42F;
        int segments = 72;

        Vec3 previousRaw = frame.origin.add(frame.direction.scale(startDistance));
        Vec3 previousBase = judgmentGroundPoint(level, previousRaw);
        Vec3 previousLeft = judgmentGroundPoint(
                level, previousRaw.add(side.scale(halfWidth)));
        Vec3 previousRight = judgmentGroundPoint(
                level, previousRaw.subtract(side.scale(halfWidth)));
        Vec3 impact = previousBase;
        Vec3 impactLeft = previousLeft;
        Vec3 impactRight = previousRight;

        for (int segment = 1; segment <= segments; segment++) {
            double distance = startDistance + (length - startDistance) * segment / segments;
            if (distance > visibleDistance + 0.001D) {
                break;
            }
            Vec3 raw = frame.origin.add(frame.direction.scale(distance));
            Vec3 base = judgmentGroundPoint(
                    level, new Vec3(raw.x, previousBase.y, raw.z));
            Vec3 leftRaw = raw.add(side.scale(halfWidth));
            Vec3 rightRaw = raw.subtract(side.scale(halfWidth));
            Vec3 left = judgmentGroundPoint(
                    level, new Vec3(leftRaw.x, previousLeft.y, leftRaw.z));
            Vec3 right = judgmentGroundPoint(
                    level, new Vec3(rightRaw.x, previousRight.y, rightRaw.z));
            boolean continuous = judgmentSurfaceContinuous(previousBase, base)
                    && judgmentSurfaceContinuous(previousLeft, left)
                    && judgmentSurfaceContinuous(previousRight, right)
                    && judgmentSurfaceContinuous(left, right);
            if (continuous) {
                double behindFront = Math.max(0.0D, visibleDistance - distance);
                float frontHeat = (float) Math.exp(-behindFront / 4.8D);
                float wakeAlpha = (0.16F + frontHeat * 0.56F) * fade * pulse;
                texturedQuad(
                        consumer,
                        pose,
                        previousLeft.add(0.0D, 0.026D, 0.0D),
                        previousRight.add(0.0D, 0.026D, 0.0D),
                        right.add(0.0D, 0.026D, 0.0D),
                        left.add(0.0D, 0.026D, 0.0D),
                        phase + (float) ((distance - (length / segments)) * 0.19D),
                        0.0F,
                        phase + (float) (distance * 0.19D),
                        1.0F,
                        color(wakeAlpha, SOLAR_GOLD));

                Vec3 previousSeam = previousBase.add(0.0D, 0.042D, 0.0D);
                Vec3 seam = base.add(0.0D, 0.042D, 0.0D);
                ribbonOnPlane(
                        consumer,
                        pose,
                        previousSeam,
                        seam,
                        side,
                        0.105D,
                        phase + segment * 0.08F,
                        color((0.52F + frontHeat * 0.35F) * fade, SOLAR_GOLD));
                ribbonOnPlane(
                        consumer,
                        pose,
                        previousSeam.add(0.0D, 0.009D, 0.0D),
                        seam.add(0.0D, 0.009D, 0.0D),
                        side,
                        0.032D,
                        phase + segment * 0.13F,
                        color((0.74F + frontHeat * 0.24F) * fade, SOLAR_WHITE));
                if (segment % 7 == 0) {
                    double sign = ((segment / 7) & 1) == 0 ? 1.0D : -1.0D;
                    double reach = halfWidth * (0.42D + (segment % 3) * 0.13D);
                    Vec3 fractureRaw = raw
                            .subtract(frame.direction.scale(0.18D + (segment % 2) * 0.12D))
                            .add(side.scale(sign * reach));
                    Vec3 fracture = judgmentGroundPoint(
                            level,
                            new Vec3(
                                    fractureRaw.x,
                                    base.y,
                                    fractureRaw.z));
                    if (judgmentSurfaceContinuous(base, fracture)) {
                        ribbonInPlane(
                                consumer, pose,
                                seam,
                                fracture.add(0.0D, 0.042D, 0.0D),
                                up,
                                0.026D,
                                phase + segment * 0.11F,
                                color((0.34F + frontHeat * 0.45F) * fade, SOLAR_GOLD));
                    }
                }
            }

            previousBase = base;
            previousLeft = left;
            previousRight = right;
            impact = base;
            impactLeft = left;
            impactRight = right;
        }

        if (judgmentSurfaceContinuous(impactLeft, impactRight)) {
            ribbonInPlane(
                    consumer, pose,
                    impactLeft.add(0.0D, 0.065D, 0.0D),
                    impactRight.add(0.0D, 0.065D, 0.0D),
                    up,
                    0.17D,
                    phase,
                    color(0.94F * fade, SOLAR_GOLD));
            ribbonInPlane(
                    consumer, pose,
                    impactLeft.add(0.0D, 0.078D, 0.0D),
                    impactRight.add(0.0D, 0.078D, 0.0D),
                    up,
                    0.052D,
                    phase + 0.25F,
                    color(0.98F * fade, SOLAR_WHITE));
        }

        double impactRadius = 0.58D + 0.12D * pulse;
        circularGroundRibbon(
                level,
                pose,
                consumer,
                impact.add(0.0D, 0.055D, 0.0D),
                impactRadius,
                0.055D,
                color(0.90F * fade, SOLAR_WHITE));
        circularGroundRibbon(
                level,
                pose,
                consumer,
                impact.add(0.0D, 0.048D, 0.0D),
                impactRadius * 1.62D,
                0.038D,
                color(0.72F * fade, SOLAR_GOLD));
        for (int ray = 0; ray < 8; ray++) {
            double angle = Math.PI * 2.0D * ray / 8.0D;
            Vec3 radial = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3 rayStart = judgmentGroundPoint(
                    level, impact.add(radial.scale(impactRadius * 0.82D)));
            Vec3 rayEnd = judgmentGroundPoint(
                    level, impact.add(radial.scale(impactRadius * (1.52D + (ray & 1) * 0.34D))));
            if (judgmentSurfaceContinuous(rayStart, rayEnd)) {
                ribbonInPlane(
                        consumer, pose,
                        rayStart.add(0.0D, 0.055D, 0.0D),
                        rayEnd.add(0.0D, 0.055D, 0.0D),
                        up,
                        0.025D,
                        phase + ray * 0.17F,
                        color((ray & 1) == 0 ? 0.82F * fade : 0.62F * fade,
                                (ray & 1) == 0 ? SOLAR_WHITE : SOLAR_GOLD));
            }
        }
    }

    private static void judgmentRaSigil(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            boolean glow) {
        int lock = UnknownEgyptianCombatGoal.judgmentLockTick(frame.ruins);
        int warning = UnknownEgyptianCombatGoal.judgmentWarningTicks(frame.ruins);
        int active = UnknownEgyptianCombatGoal.judgmentActiveTicks(frame.ruins);
        if (frame.attackAge >= warning + active) {
            return;
        }
        float materialize = smooth(frame.attackAge / Math.max(1.0F, lock + 3.0F));
        float lockFlash = 1.0F - smooth(Math.abs(frame.attackAge - lock) / 4.0F);
        float activeAge = Math.max(0.0F, frame.attackAge - warning);
        float activeFlash = frame.attackAge >= warning
                ? 1.0F - smooth(activeAge / Math.max(1.0F, active - 1.0F))
                : 0.0F;
        Vec3 center = judgmentEyeCenter(frame);
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        float pulse = 1.0F + 0.018F * Mth.sin(frame.attackAge * 0.44F);
        float verdictSnap = 1.0F + lockFlash * 0.055F + activeFlash * 0.075F;
        float width = (float) Mth.lerp(materialize,
                glow ? 0.9D : 0.72D,
                glow ? 6.25D : 5.55D) * pulse * verdictSnap;
        float height = (float) Mth.lerp(materialize,
                glow ? 0.9D : 0.72D,
                glow ? 6.25D : 5.55D) * pulse * verdictSnap;
        float alpha = glow
                ? 0.055F + materialize * 0.22F + lockFlash * 0.08F + activeFlash * 0.20F
                : 0.16F + materialize * 0.82F;
        eyeQuad(
                pose,
                consumer,
                glow ? center.subtract(frame.direction.scale(0.035D)) : center,
                side,
                up,
                width,
                height,
                color(alpha, glow ? SOLAR_GOLD : WHITE));
    }

    private static Vec3 judgmentEyeCenter(Frame frame) {
        return frame.origin.add(frame.direction.scale(0.48D)).add(0.0D, 4.45D, 0.0D);
    }

    private static void eyeQuad(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 center,
            Vec3 side,
            Vec3 up,
            float width,
            float height,
            int color) {
        Vec3 horizontal = side.scale(width * 0.5D);
        Vec3 vertical = up.scale(height * 0.5D);
        texturedQuad(
                consumer,
                pose,
                center.subtract(horizontal).subtract(vertical),
                center.subtract(horizontal).add(vertical),
                center.add(horizontal).add(vertical),
                center.add(horizontal).subtract(vertical),
                // Flip V so the generated Eye of Ra remains upright in world space.
                0.0F,
                1.0F,
                1.0F,
                0.0F,
                color);
    }

    private static void chariotRaidCast(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        int warning = UnknownEgyptianCombatGoal.chariotWarningTicks(frame.ruins);
        float reveal = smooth(Math.clamp(frame.attackAge / Math.max(1.0F, warning), 0.0F, 1.0F));
        Vec3 center = groundPoint(level, frame.origin).add(0.0D, 0.025D, 0.0D);
        circularGroundRibbon(
                level,
                pose,
                consumer,
                center,
                1.05D + reveal * 1.15D,
                0.075D,
                color(0.42F + reveal * 0.48F, SOLAR_GOLD));
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        for (int ray = 0; ray < 8; ray++) {
            double angle = ray * Math.PI * 0.25D + frame.attackAge * 0.025D;
            Vec3 radial = frame.direction.scale(Math.cos(angle)).add(side.scale(Math.sin(angle)));
            Vec3 start = center.add(radial.scale(0.62D));
            Vec3 end = center.add(radial.scale(1.2D + reveal * 1.0D));
            ribbonOnPlane(
                    consumer,
                    pose,
                    start,
                    end,
                    new Vec3(0.0D, 1.0D, 0.0D),
                    0.035D,
                    ray * 0.12F,
                    color((0.25F + reveal * 0.55F), ray % 2 == 0 ? SOLAR_GOLD : SOLAR_WHITE));
        }
    }

    private static void solarArrow3d(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 center,
            Vec3 direction,
            double size,
            float alpha,
            int variant,
            boolean occluded) {
        Vec3 axis = direction.lengthSqr() <= 1.0E-8D
                ? new Vec3(0.0D, -1.0D, 0.0D)
                : direction.normalize();
        Vec3 reference = Math.abs(axis.y) > 0.92D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = axis.cross(reference).normalize();
        Vec3 up = axis.cross(side).normalize();
        double length = size * 1.55D;
        double tipLength = length * 0.22D;
        double shaftRadius = 0.018D * Math.max(0.85D, size * 0.55D);
        Vec3 tip = center.add(axis.scale(length * 0.52D));
        Vec3 nock = center.subtract(axis.scale(length * 0.48D));
        Vec3 tipBase = tip.subtract(axis.scale(tipLength));
        int shaftColor = color(alpha, SOLAR_GOLD);
        int coreColor = color(alpha, SOLAR_WHITE);
        int fletchColor = color(alpha * 0.9F, NILE_BLUE);
        Vec3[] corners = {
            side.scale(shaftRadius).add(up.scale(shaftRadius * 0.55D)),
            side.scale(-shaftRadius).add(up.scale(shaftRadius * 0.55D)),
            side.scale(-shaftRadius).add(up.scale(-shaftRadius * 0.55D)),
            side.scale(shaftRadius).add(up.scale(-shaftRadius * 0.55D))
        };
        for (int i = 0; i < 4; i++) {
            Vec3 a = corners[i];
            Vec3 b = corners[(i + 1) % 4];
            texturedQuad(consumer, pose, nock.add(a), tipBase.add(a), tipBase.add(b), nock.add(b),
                    0.0F, 0.0F, 1.0F, 1.0F, shaftColor);
        }
        for (int i = 0; i < 4; i++) {
            Vec3 a = tipBase.add(corners[i].scale(1.8D));
            Vec3 b = tipBase.add(corners[(i + 1) % 4].scale(1.8D));
            texturedQuad(consumer, pose, tip, a, b, tip, 0.0F, 0.0F, 1.0F, 1.0F, coreColor);
        }
        double finLen = length * 0.14D;
        double finWidth = shaftRadius * 4.2D;
        for (int fin = 0; fin < 3; fin++) {
            double angle = fin * (Math.PI * 2.0D / 3.0D);
            Vec3 finDir = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).normalize();
            Vec3 root = nock.add(axis.scale(length * 0.08D));
            Vec3 outer = root.add(finDir.scale(finWidth)).subtract(axis.scale(finLen * 0.35D));
            Vec3 trailing = root.subtract(axis.scale(finLen));
            ribbonOnPlane(consumer, pose, root, outer, axis, 0.008D, fin * 0.2F, fletchColor);
            ribbonOnPlane(consumer, pose, outer, trailing, axis, 0.006D, fin * 0.2F, fletchColor);
        }
        if (!occluded) {
            ribbonOnPlane(consumer, pose, nock, tip, up, shaftRadius * 0.45D, 0.0F,
                    color(alpha * 0.65F, SOLAR_WHITE));
        }
    }

    private static void horusFlightPath(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            Vec3 start,
            Vec3 end,
            double sign,
            float reveal,
            float flightProgress,
            boolean occluded) {
        int segments = 28;
        Vec3 previous = start;
        for (int segment = 1; segment <= segments; segment++) {
            double progress = reveal * segment / segments;
            Vec3 current = UnknownEgyptianCombatMath.horusVolleyPoint(
                    start, end, progress, sign,
                    UnknownEgyptianCombatGoal.HORUS_CURVE,
                    UnknownEgyptianCombatGoal.HORUS_LIFT);
            float headGlow = 1.0F - Math.min(1.0F, Math.abs((float) progress - flightProgress) * 5.0F);
            ribbonInPlane(consumer, pose, previous, current, frame.direction,
                    0.026D + headGlow * 0.035D,
                    segment * 0.13F - frame.attackAge * 0.08F,
                    color((occluded ? 0.07F : 0.18F + headGlow * 0.36F),
                            segment % 5 == 0 ? SOLAR_WHITE : SOLAR_GOLD));
            previous = current;
        }
    }

    private static void spectralFalconFeather(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 center,
            Vec3 direction,
            double size,
            float alpha,
            int variant) {
        Vec3 axis = direction.lengthSqr() <= 1.0E-8D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : direction.normalize();
        Vec3 reference = Math.abs(axis.y) > 0.88D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = axis.cross(reference).normalize();
        Vec3 tail = center.subtract(axis.scale(size * 0.92D));
        Vec3 tip = center.add(axis.scale(size * 0.92D));
        int shaft = color(alpha, SOLAR_GOLD);
        int vane = color(alpha * 0.86F, variant % 2 == 0 ? SOLAR_WHITE : NILE_BLUE);
        ribbonOnPlane(consumer, pose, tail, tip, side, size * 0.045D, 0.0F, shaft);
        for (int barb = 0; barb < 5; barb++) {
            double along = -0.62D + barb * 0.27D;
            double width = size * (0.48D - Math.abs(along) * 0.18D);
            Vec3 root = center.add(axis.scale(along * size));
            Vec3 backward = axis.scale(-size * 0.25D);
            ribbonInPlane(consumer, pose, root,
                    root.add(backward).add(side.scale(width)), axis,
                    size * 0.027D, barb * 0.12F, vane);
            ribbonInPlane(consumer, pose, root,
                    root.add(backward).subtract(side.scale(width)), axis,
                    size * 0.027D, barb * 0.12F, vane);
        }
    }

    private static void horusTargetMark(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        Vec3 center = groundPoint(level, frame.anchor);
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        double radius = 0.72D;
        Vec3 north = center.add(frame.direction.scale(radius));
        Vec3 east = center.add(side.scale(radius));
        Vec3 south = center.subtract(frame.direction.scale(radius));
        Vec3 west = center.subtract(side.scale(radius));
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        int mark = color(0.72F, SOLAR_GOLD);
        ribbonInPlane(consumer, pose, north, east, up, 0.045D, 0.0F, mark);
        ribbonInPlane(consumer, pose, east, south, up, 0.045D, 0.2F, mark);
        ribbonInPlane(consumer, pose, south, west, up, 0.045D, 0.4F, mark);
        ribbonInPlane(consumer, pose, west, north, up, 0.045D, 0.6F, mark);
    }

    private static void maatScales(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            boolean occluded) {
        int warning = UnknownEgyptianCombatGoal.maatWarningTicks(frame.ruins);
        if (frame.attackAge >= warning + UnknownEgyptianCombatGoal.MAAT_ACTIVE_TICKS) {
            return;
        }
        float charge = smooth(frame.attackAge / warning);
        double radius = frame.corridorLength > 0.1F
                ? frame.corridorLength
                : UnknownEgyptianCombatGoal.maatRadius(frame.ruins);
        double unsafeSign = frame.gapOffset < 0.0F ? -1.0D : 1.0D;
        Vec3 center = groundPoint(level, frame.anchor);
        maatGroundTrial(
                level, pose, consumer, frame, center, radius, unsafeSign, charge, occluded);
        if (!occluded) {
            maatMonument(pose, consumer, frame, center, radius, unsafeSign, charge, warning);
        }
    }

    private static void maatGroundTrial(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            Vec3 center,
            double radius,
            double unsafeSign,
            float charge,
            boolean occluded) {
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        float pulse = 0.88F + 0.12F * Mth.sin(frame.attackAge * 0.38F);
        float outlineAlpha = (occluded ? 0.11F : 0.46F + charge * 0.34F) * pulse;
        circularGroundRibbon(level, pose, consumer, center, radius, 0.085D,
                color(outlineAlpha, SOLAR_GOLD));

        Vec3 dividerStart = groundPoint(level, center.subtract(frame.direction.scale(radius)));
        Vec3 dividerEnd = groundPoint(level, center.add(frame.direction.scale(radius)));
        ribbonOnPlane(
                consumer,
                pose,
                dividerStart,
                dividerEnd,
                side,
                0.075D,
                -frame.attackAge * 0.06F,
                color(outlineAlpha, SOLAR_WHITE));
        if (occluded) {
            return;
        }

        float impact = frame.attackAge >= UnknownEgyptianCombatGoal.maatWarningTicks(frame.ruins)
                ? 1.0F
                        - smooth((frame.attackAge
                                        - UnknownEgyptianCombatGoal.maatWarningTicks(frame.ruins))
                                / UnknownEgyptianCombatGoal.MAAT_ACTIVE_TICKS)
                : 0.0F;
        for (int ring = 1; ring <= 6; ring++) {
            double ringRadius = radius * ring / 6.0D;
            Vec3 previous = groundPoint(level, center.add(frame.direction.scale(ringRadius)));
            for (int segment = 1; segment <= 20; segment++) {
                double angle = unsafeSign * Math.PI * segment / 20.0D;
                Vec3 radial = frame.direction.scale(Math.cos(angle))
                        .add(side.scale(Math.sin(angle)));
                Vec3 current = groundPoint(level, center.add(radial.scale(ringRadius)));
                Vec3 widthAxis = previous.add(current).scale(0.5D).subtract(center);
                ribbonOnPlane(
                        consumer,
                        pose,
                        previous,
                        current,
                        widthAxis,
                        0.038D + impact * 0.035D,
                        ring * 0.16F + segment * 0.05F - frame.attackAge * 0.04F,
                        color((0.10F + charge * 0.18F + impact * 0.44F) * pulse,
                                SOLAR_GOLD));
                previous = current;
            }
        }

        Vec3 heartCenter = groundPoint(
                level,
                center.add(side.scale(unsafeSign * radius * 0.48D)));
        heartGlyph(
                pose,
                consumer,
                heartCenter,
                frame.direction,
                side.scale(unsafeSign),
                up,
                radius * 0.19D,
                0.055D,
                color(0.54F + charge * 0.36F, SOLAR_GOLD));

        Vec3 featherCenter = groundPoint(
                level,
                center.subtract(side.scale(unsafeSign * radius * 0.48D)));
        featherGlyph(
                pose,
                consumer,
                featherCenter,
                frame.direction,
                side.scale(-unsafeSign),
                up,
                radius * 0.20D,
                color(0.48F + charge * 0.38F, SOLAR_WHITE));

        if (impact > 0.0F) {
            double waveRadius = radius * Mth.lerp(smooth(1.0F - impact), 0.18D, 1.0D);
            circularGroundRibbon(level, pose, consumer, center, waveRadius, 0.12D,
                    color(0.82F * impact, SOLAR_WHITE));
        }
    }

    private static void maatMonument(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            Vec3 center,
            double radius,
            double unsafeSign,
            float charge,
            int warning) {
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        float alpha = 0.34F + charge * 0.56F;
        Vec3 pivot = center.add(0.0D, 7.8D, 0.0D);
        Vec3 staffBase = center.add(0.0D, 0.12D, 0.0D);
        ribbonInPlane(consumer, pose, staffBase, pivot.add(0.0D, 0.65D, 0.0D),
                frame.direction, 0.11D, 0.0F, color(alpha, SOLAR_GOLD));
        circularVerticalRibbon(pose, consumer, pivot, side, up, 0.34D, 0.055D,
                color(alpha, SOLAR_WHITE));

        Vec3 unsafeArm = pivot
                .add(side.scale(unsafeSign * 4.15D))
                .add(0.0D, -charge * 0.72D, 0.0D);
        Vec3 safeArm = pivot
                .subtract(side.scale(unsafeSign * 4.15D))
                .add(0.0D, charge * 0.72D, 0.0D);
        ribbonInPlane(consumer, pose, unsafeArm, pivot, frame.direction, 0.12D, 0.0F,
                color(alpha, SOLAR_GOLD));
        ribbonInPlane(consumer, pose, pivot, safeArm, frame.direction, 0.12D, 0.0F,
                color(alpha, SOLAR_WHITE));

        Vec3 unsafePan = unsafeArm.add(0.0D, -1.35D, 0.0D);
        Vec3 safePan = safeArm.add(0.0D, -1.35D, 0.0D);
        maatPan(pose, consumer, frame, unsafeArm, unsafePan, alpha, SOLAR_GOLD);
        maatPan(pose, consumer, frame, safeArm, safePan, alpha, SOLAR_WHITE);

        float descent = smooth((frame.attackAge - (warning - 6.0F)) / 6.0F);
        float impactAge = Math.max(0.0F, frame.attackAge - warning);
        float impactFade = 1.0F
                - smooth(impactAge / UnknownEgyptianCombatGoal.MAAT_ACTIVE_TICKS);
        Vec3 heartGround = center
                .add(side.scale(unsafeSign * radius * 0.48D))
                .add(0.0D, 1.3D, 0.0D);
        Vec3 heartPosition = unsafePan
                .add(0.0D, 0.55D, 0.0D)
                .lerp(heartGround, descent);
        heartGlyph(
                pose,
                consumer,
                heartPosition,
                side,
                up,
                frame.direction,
                Mth.lerp(descent, 0.82D, 2.45D),
                0.085D + descent * 0.055D,
                color((0.72F + descent * 0.24F) * impactFade, SOLAR_GOLD));
        featherGlyph(
                pose,
                consumer,
                safePan.add(0.0D, 0.5D, 0.0D),
                side,
                up,
                frame.direction,
                1.25D,
                color(0.82F, SOLAR_WHITE));
    }

    private static void maatPan(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            Vec3 arm,
            Vec3 pan,
            float alpha,
            int rgb) {
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        ribbonInPlane(consumer, pose, arm, pan.add(side.scale(0.72D)), frame.direction,
                0.038D, 0.0F, color(alpha, rgb));
        ribbonInPlane(consumer, pose, arm, pan.subtract(side.scale(0.72D)), frame.direction,
                0.038D, 0.0F, color(alpha, rgb));
        circularRibbon(pose, consumer, pan, 0.92D, 0.065D, color(alpha, rgb));
    }

    private static void heartGlyph(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 center,
            Vec3 horizontalAxis,
            Vec3 verticalAxis,
            Vec3 planeNormal,
            double size,
            double width,
            int color) {
        final int segments = 32;
        Vec3 previous = heartPoint(center, horizontalAxis, verticalAxis, 0.0D, size);
        for (int segment = 1; segment <= segments; segment++) {
            double angle = Math.PI * 2.0D * segment / segments;
            Vec3 current = heartPoint(center, horizontalAxis, verticalAxis, angle, size);
            ribbonInPlane(consumer, pose, previous, current, planeNormal, width,
                    segment * 0.07F, color);
            previous = current;
        }
    }

    private static Vec3 heartPoint(
            Vec3 center,
            Vec3 horizontalAxis,
            Vec3 verticalAxis,
            double angle,
            double size) {
        double sine = Math.sin(angle);
        double x = sine * sine * sine;
        double y = (13.0D * Math.cos(angle)
                        - 5.0D * Math.cos(2.0D * angle)
                        - 2.0D * Math.cos(3.0D * angle)
                        - Math.cos(4.0D * angle))
                / 17.0D;
        return center
                .add(horizontalAxis.normalize().scale(x * size))
                .add(verticalAxis.normalize().scale(y * size));
    }

    private static void featherGlyph(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 center,
            Vec3 widthAxis,
            Vec3 lengthAxis,
            Vec3 planeNormal,
            double size,
            int color) {
        Vec3 length = lengthAxis.normalize();
        Vec3 width = widthAxis.normalize();
        Vec3 bottom = center.subtract(length.scale(size * 0.72D));
        Vec3 top = center.add(length.scale(size * 0.72D));
        ribbonInPlane(consumer, pose, bottom, top, planeNormal, size * 0.035D,
                0.0F, color);
        for (int barb = 0; barb < 6; barb++) {
            double along = -0.46D + barb * 0.18D;
            double spread = (0.58D - Math.abs(along) * 0.42D) * size;
            Vec3 root = center.add(length.scale(along * size));
            Vec3 forward = length.scale(size * 0.18D);
            ribbonInPlane(consumer, pose, root, root.add(forward).add(width.scale(spread)),
                    planeNormal, size * 0.026D, barb * 0.11F, color);
            ribbonInPlane(consumer, pose, root, root.add(forward).subtract(width.scale(spread)),
                    planeNormal, size * 0.026D, barb * 0.11F, color);
        }
    }

    private static void submitPass(
            SubmitNodeCollector collector,
            PoseStack poseStack,
            Vec3 camera,
            ClientLevel level,
            Frame frame) {
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        collector.submitCustomGeometry(
                poseStack,
                EchoRenderTypes.shaderSafe(EchoRenderTypes.UNKNOWN_STAB),
                (pose, consumer) -> geometry(level, pose, consumer, frame));
        if (frame.state == UnknownCombatState.SOLAR_JUDGMENT) {
            collector.submitCustomGeometry(
                    poseStack,
                    EchoRenderTypes.shaderSafe(EchoRenderTypes.EGYPTIAN_JUDGMENT),
                    (pose, consumer) -> solarJudgment(level, pose, consumer, frame));
            collector.submitCustomGeometry(
                    poseStack,
                    EchoRenderTypes.shaderSafeGlow(
                            EchoRenderTypes.RA_JUDGMENT_SIGIL_GLOW,
                            EchoRenderTypes.RA_JUDGMENT_SIGIL),
                    (pose, consumer) -> judgmentRaSigil(pose, consumer, frame, true));
            collector.submitCustomGeometry(
                    poseStack,
                    EchoRenderTypes.RA_JUDGMENT_SIGIL,
                    (pose, consumer) -> judgmentRaSigil(pose, consumer, frame, false));
        } else if (frame.state == UnknownCombatState.SEKHMET_HUNT) {
            collector.submitCustomGeometry(
                    poseStack,
                    EchoRenderTypes.shaderSafe(EchoRenderTypes.EGYPTIAN_SEKHMET),
                    (pose, consumer) -> sekhmetHunt(level, pose, consumer, frame));
        }
        poseStack.popPose();
    }

    private static void geometry(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        switch (frame.state) {
            case STAB -> {
                if (frame.gapOffset > 1.0F) {
                    impaleSpear(pose, consumer, frame);
                    break;
                }
                int cutCount = frame.corridorLength > 1.5F
                        ? Math.clamp(Math.round(frame.corridorLength), 2, 3)
                        : UnknownGreekCombatGoal.stabCutCount(frame.ruins);
                int active = UnknownGreekCombatGoal.stabActiveTicks(frame.ruins);
                for (int cut = 0; cut < cutCount; cut++) {
                    int start = UnknownGreekCombatGoal.stabCutStartTick(frame.ruins, cut);
                    if (cut == 0 && frame.attackAge < start + active) {
                        groundLane(level, pose, consumer, frame);
                    }
                    if (cut > 0
                            && frame.attackAge >= start
                                    - UnknownGreekCombatGoal.stabBetweenCutsTicks(frame.ruins)
                            && frame.attackAge < start) {
                        groundLane(level, pose, consumer, frame);
                    }
                    if (frame.attackAge >= start && frame.attackAge < start + active) {
                        doryStreak(pose, consumer, frame, start);
                    }
                }
            }
            case CHARGE -> chargeLane(level, pose, consumer, frame);
            case PHALANX -> phalanxLanes(pose, consumer, frame);
            case JAVELIN -> spectralJavelin(pose, consumer, frame);
            case SPEAR_ERUPTION -> spearEruptionField(level, pose, consumer, frame);
            case SHIELD_BASH -> shieldBashTelegraph(level, pose, consumer, frame);
            case KHOPESH_COMBO -> khopeshCombo(level, pose, consumer, frame);
            case DUAT_GATE -> duatGate(level, pose, consumer, frame);
            case MEDIEVAL_COMBO -> {
                if (!frame.ruins) {
                    medievalCombo(pose, consumer, frame);
                }
            }
            default -> {
            }
        }
        if (frame.fxAge >= 0.0F && frame.fxAge <= combatFxLifetime(frame.fxKind)) {
            impactGlyph(pose, consumer, frame);
        }
    }

    private static void medievalCombo(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        if (frame.variant == UnknownEntity.COMBAT_VARIANT_OPENING) {
            medievalTelegraphGlint(
                    pose,
                    consumer,
                    frame,
                    UnknownMedievalCombatGoal.COMBO_FIRST_ACTIVE_START_TICK,
                    UnknownMedievalCombatGoal.COMBO_FIRST_START_DEGREES,
                    UnknownMedievalCombatGoal.COMBO_FIRST_END_DEGREES);
            medievalCutTrail(
                    pose,
                    consumer,
                    frame,
                    UnknownMedievalCombatGoal.COMBO_FIRST_ACTIVE_START_TICK,
                    UnknownMedievalCombatGoal.COMBO_FIRST_START_DEGREES,
                    UnknownMedievalCombatGoal.COMBO_FIRST_END_DEGREES,
                    false);
            return;
        }

        boolean chase = frame.variant == UnknownEntity.COMBAT_VARIANT_MEDIEVAL_CHASE;
        int activeStart = chase
                ? UnknownMedievalCombatGoal.COMBO_CHASE_ACTIVE_START_TICK
                : UnknownMedievalCombatGoal.COMBO_SWEEP_ACTIVE_START_TICK;
        double startDegrees = chase
                ? UnknownMedievalCombatGoal.COMBO_CHASE_START_DEGREES
                : UnknownMedievalCombatGoal.COMBO_SWEEP_START_DEGREES;
        double endDegrees = chase
                ? UnknownMedievalCombatGoal.COMBO_CHASE_END_DEGREES
                : UnknownMedievalCombatGoal.COMBO_SWEEP_END_DEGREES;
        if (frame.attackAge >= UnknownMedievalCombatGoal.COMBO_BRANCH_LOCK_TICK + 3.0F
                && frame.attackAge < UnknownMedievalCombatGoal.COMBO_BRANCH_LOCK_TICK
                        + UnknownMedievalCombatGoal.COMBO_BRANCH_READ_TICKS) {
            medievalTelegraphArc(
                    pose,
                    consumer,
                    frame,
                    startDegrees,
                    endDegrees,
                    smooth((frame.attackAge
                                    - UnknownMedievalCombatGoal.COMBO_BRANCH_LOCK_TICK
                                    - 3.0F)
                            / 3.0F));
        }
        medievalCutTrail(
                pose,
                consumer,
                frame,
                activeStart,
                startDegrees,
                endDegrees,
                chase);
    }

    private static void medievalTelegraphGlint(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            int activeStart,
            double startDegrees,
            double endDegrees) {
        float revealStart = activeStart - 3.0F;
        if (frame.attackAge < revealStart || frame.attackAge >= activeStart) {
            return;
        }
        medievalTelegraphArc(
                pose,
                consumer,
                frame,
                startDegrees,
                endDegrees,
                smooth((frame.attackAge - revealStart) / 3.0F));
    }

    private static void medievalTelegraphArc(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            double startDegrees,
            double endDegrees,
            float reveal) {
        Vec3 center = frame.origin.add(0.0D, 1.18D, 0.0D);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 previous = center.add(UnknownEgyptianCombatMath.rotateHorizontal(
                frame.direction,
                startDegrees).scale(UnknownMedievalCombatGoal.SWORD_REACH));
        for (int segment = 1; segment <= 18; segment++) {
            float progress = segment / 18.0F;
            if (progress > reveal) {
                break;
            }
            double angle = Mth.lerp(progress, startDegrees, endDegrees);
            Vec3 point = center.add(UnknownEgyptianCombatMath.rotateHorizontal(
                    frame.direction,
                    angle).scale(UnknownMedievalCombatGoal.SWORD_REACH));
            ribbonOnPlane(
                    consumer,
                    pose,
                    previous,
                    point,
                    up,
                    0.014D,
                    progress,
                    color(0.16F + reveal * 0.28F, MEDIEVAL_STEEL));
            previous = point;
        }
        if (reveal <= 0.01F) {
            return;
        }
        // A compact glint rides the revealed edge. The body remains the main
        // telegraph; this only clarifies which side of the blade will arrive.
        float tipProgress = Math.clamp(reveal, 0.0F, 1.0F);
        double tipAngle = Mth.lerp(tipProgress, startDegrees, endDegrees);
        Vec3 radial = UnknownEgyptianCombatMath.rotateHorizontal(frame.direction, tipAngle);
        Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);
        Vec3 tip = center.add(radial.scale(UnknownMedievalCombatGoal.SWORD_REACH));
        float glintAlpha = 0.34F + 0.46F * (1.0F - Math.abs(tipProgress * 2.0F - 1.0F));
        ribbonOnPlane(
                consumer,
                pose,
                tip.subtract(tangent.scale(0.105D)),
                tip.add(tangent.scale(0.105D)),
                up,
                0.018D,
                tipProgress,
                color(glintAlpha, MEDIEVAL_STEEL));
        ribbonOnPlane(
                consumer,
                pose,
                tip.subtract(up.scale(0.105D)),
                tip.add(up.scale(0.105D)),
                tangent,
                0.018D,
                tipProgress + 0.25F,
                color(glintAlpha * 0.82F, MEDIEVAL_STEEL));
    }

    private static void medievalCutTrail(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            int activeStart,
            double startDegrees,
            double endDegrees,
            boolean diagonal) {
        float localAge = frame.attackAge - activeStart;
        if (localAge < 0.0F || localAge >= UnknownMedievalCombatGoal.COMBO_ACTIVE_TICKS + 3.0F) {
            return;
        }
        float current = Math.clamp(
                localAge / UnknownMedievalCombatGoal.COMBO_ACTIVE_TICKS,
                0.0F,
                1.0F);
        float previous = Math.max(
                0.0F,
                (localAge - 2.8F) / UnknownMedievalCombatGoal.COMBO_ACTIVE_TICKS);
        float fade = localAge <= UnknownMedievalCombatGoal.COMBO_ACTIVE_TICKS
                ? 1.0F
                : 1.0F - smooth((localAge - UnknownMedievalCombatGoal.COMBO_ACTIVE_TICKS) / 3.0F);
        // Broad translucent steel carries the weight of the blade, while a
        // shorter bright edge prevents the trail from reading like magic.
        medievalTrailStrip(
                pose,
                consumer,
                frame,
                startDegrees,
                endDegrees,
                previous,
                current,
                diagonal,
                0.115D,
                color(0.32F * fade, MEDIEVAL_STEEL));
        float edgePrevious = Math.max(
                0.0F,
                (localAge - 1.35F) / UnknownMedievalCombatGoal.COMBO_ACTIVE_TICKS);
        medievalTrailStrip(
                pose,
                consumer,
                frame,
                startDegrees,
                endDegrees,
                edgePrevious,
                current,
                diagonal,
                0.034D,
                color(0.9F * fade, MEDIEVAL_STEEL));

        float coralAge = localAge - 1.0F;
        if (coralAge < 0.0F) {
            return;
        }
        float coralCurrent = Math.clamp(
                coralAge / UnknownMedievalCombatGoal.COMBO_ACTIVE_TICKS,
                0.0F,
                1.0F);
        float coralPrevious = Math.max(
                0.0F,
                (coralAge - 2.2F) / UnknownMedievalCombatGoal.COMBO_ACTIVE_TICKS);
        medievalTrailStrip(
                pose,
                consumer,
                frame,
                startDegrees,
                endDegrees,
                coralPrevious,
                coralCurrent,
                diagonal,
                0.052D,
                color(0.36F * fade, MEDIEVAL_CORAL));
    }

    private static void medievalTrailStrip(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            double startDegrees,
            double endDegrees,
            float startProgress,
            float endProgress,
            boolean diagonal,
            double width,
            int trailColor) {
        if (endProgress <= startProgress + 1.0E-4F) {
            return;
        }
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 previous = medievalTrailPoint(
                frame,
                startDegrees,
                endDegrees,
                startProgress,
                diagonal);
        for (int segment = 1; segment <= 14; segment++) {
            float progress = Mth.lerp(segment / 14.0F, startProgress, endProgress);
            Vec3 point = medievalTrailPoint(
                    frame,
                    startDegrees,
                    endDegrees,
                    progress,
                    diagonal);
            ribbonOnPlane(
                    consumer,
                    pose,
                    previous,
                    point,
                    up,
                    width * (0.42D + segment / 24.0D),
                    progress * 1.4F,
                    trailColor);
            previous = point;
        }
    }

    private static Vec3 medievalTrailPoint(
            Frame frame,
            double startDegrees,
            double endDegrees,
            float progress,
            boolean diagonal) {
        double angle = Mth.lerp(progress, startDegrees, endDegrees);
        double height = diagonal ? Mth.lerp(progress, 1.72D, 0.72D) : 1.18D;
        return frame.origin
                .add(0.0D, height, 0.0D)
                .add(UnknownEgyptianCombatMath.rotateHorizontal(
                        frame.direction,
                        angle).scale(UnknownMedievalCombatGoal.SWORD_REACH));
    }

    private static void duatGate(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        for (int panel = 0; panel < UnknownEgyptianCombatGoal.GATE_PANEL_COUNT; panel++) {
            int spawn = UnknownEgyptianCombatGoal.gatePanelSpawnTick(frame.ruins, panel);
            if (frame.attackAge >= spawn) {
                continue;
            }
            int telegraphStart = Math.max(
                    UnknownEgyptianCombatGoal.sealLockTick(frame.ruins),
                    spawn - 12);
            float reveal = smooth(Math.clamp(
                    (frame.attackAge - telegraphStart) / Math.max(1.0F, spawn - telegraphStart),
                    0.0F,
                    1.0F));
            Vec3 center = frame.anchor;
            Vec3 direction = frame.direction;
            double halfSpan = UnknownEgyptianCombatGoal.WALL_WIDTH * 0.5D;
            duatPanelFootprint(
                    level,
                    pose,
                    consumer,
                    center,
                    direction,
                    halfSpan,
                    0.28F + reveal * 0.7F,
                    panel);
            Vec3 castStart = frame.origin.add(0.0D, 1.45D, 0.0D);
            Vec3 castEnd = groundPoint(level, center).add(0.0D, 0.08D, 0.0D);
            ribbonInPlane(
                    consumer,
                    pose,
                    castStart,
                    castEnd,
                    new Vec3(0.0D, 1.0D, 0.0D),
                    0.018D + reveal * 0.018D,
                    panel * 0.22F,
                    color(reveal * 0.58F, panel == 0 ? SOLAR_GOLD : NILE_BLUE));
        }
    }

    private static void duatPanelFootprint(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 center,
            Vec3 direction,
            double halfSpan,
            float alpha,
            int panel) {
        Vec3 forward = new Vec3(direction.x, 0.0D, direction.z).normalize();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 leftFront = groundPoint(level, center.subtract(side.scale(halfSpan)).add(forward.scale(0.3D)));
        Vec3 rightFront = groundPoint(level, center.add(side.scale(halfSpan)).add(forward.scale(0.3D)));
        Vec3 leftBack = groundPoint(level, center.subtract(side.scale(halfSpan)).subtract(forward.scale(0.3D)));
        Vec3 rightBack = groundPoint(level, center.add(side.scale(halfSpan)).subtract(forward.scale(0.3D)));
        int gold = color(alpha, SOLAR_GOLD);
        ribbonOnPlane(consumer, pose, leftFront, rightFront, forward, 0.065D, panel * 0.1F, gold);
        ribbonOnPlane(consumer, pose, rightFront, rightBack, side, 0.065D, panel * 0.1F, gold);
        ribbonOnPlane(consumer, pose, rightBack, leftBack, forward, 0.065D, panel * 0.1F, gold);
        ribbonOnPlane(consumer, pose, leftBack, leftFront, side, 0.065D, panel * 0.1F, gold);
        for (int mark = -2; mark <= 2; mark++) {
            Vec3 rune = groundPoint(level, center.add(side.scale(mark * halfSpan / 2.5D)))
                    .add(0.0D, 0.012D, 0.0D);
            Vec3 a = rune.subtract(side.scale(0.17D)).subtract(forward.scale(0.16D));
            Vec3 b = rune.add(forward.scale(0.2D));
            Vec3 c = rune.add(side.scale(0.17D)).subtract(forward.scale(0.16D));
            ribbonOnPlane(consumer, pose, a, b, new Vec3(0.0D, 1.0D, 0.0D),
                    0.028D, mark * 0.07F, color(alpha * 0.85F, SOLAR_WHITE));
            ribbonOnPlane(consumer, pose, b, c, new Vec3(0.0D, 1.0D, 0.0D),
                    0.028D, mark * 0.07F, color(alpha * 0.85F, SOLAR_WHITE));
        }
    }

    private static void safeThresholdMark(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            Vec3 center,
            int lane) {
        float alpha = 0.18F + 0.08F * Mth.sin((frame.attackAge + lane) * 0.35F);
        cartoucheOutline(
                pose,
                consumer,
                frame,
                center,
                UnknownEgyptianCombatGoal.SEAL_PANEL_HALF_WIDTH * 0.72D,
                UnknownEgyptianCombatGoal.SEAL_PANEL_HALF_LENGTH * 0.9D,
                0.04D,
                color(alpha, WHITE));
    }

    private static void raThresholdWarning(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            Vec3 center,
            int lane,
            int warning) {
        float progress = smooth(frame.attackAge / Math.max(1.0F, warning));
        float pulse = 0.82F + 0.18F * Mth.sin((frame.attackAge + lane * 2.4F) * 0.42F);
        float alpha = (0.55F + progress * 0.4F) * pulse;
        double halfW = UnknownEgyptianCombatGoal.SEAL_PANEL_HALF_WIDTH;
        double halfL = UnknownEgyptianCombatGoal.SEAL_PANEL_HALF_LENGTH;
        cartoucheOutline(pose, consumer, frame, center, halfW, halfL, 0.07D,
                color(alpha, SOLAR_GOLD));
        cartoucheOutline(pose, consumer, frame, center.add(0.0D, 0.02D, 0.0D),
                halfW * 0.7D, halfL * 0.7D, 0.035D, color(alpha * 0.75F, WHITE));
        // Rising cartouche frame posts (door jambs).
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        double height = Mth.lerp(progress, 0.4D, UnknownEgyptianCombatGoal.THRESHOLD_HEIGHT);
        Vec3 left = center.subtract(side.scale(halfW));
        Vec3 right = center.add(side.scale(halfW));
        ribbonInPlane(consumer, pose, left, left.add(0.0D, height, 0.0D), frame.direction,
                0.06D, lane * 0.1F, color(alpha, SOLAR_GOLD));
        ribbonInPlane(consumer, pose, right, right.add(0.0D, height, 0.0D), frame.direction,
                0.06D, lane * 0.1F, color(alpha, SOLAR_GOLD));
        ribbonInPlane(consumer, pose,
                left.add(0.0D, height, 0.0D),
                right.add(0.0D, height, 0.0D),
                frame.direction,
                0.05D,
                lane * 0.1F,
                color(alpha * 0.9F, WHITE));
        // Ground danger strip across the sealed street mouth.
        groundQuad(
                consumer,
                pose,
                center.subtract(frame.direction.scale(halfL)),
                center.add(frame.direction.scale(halfL)),
                side,
                halfW,
                0.0D,
                1.0D,
                color(alpha * 0.45F, SOLAR_GOLD));
    }

    private static void raThresholdSheet(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            Vec3 center,
            int lane,
            int warning,
            int active) {
        float age = frame.attackAge - warning;
        float rise = smooth(Math.min(1.0F, age / 2.2F));
        float fade = 1.0F - smooth(age / Math.max(1.0F, active));
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        double halfW = UnknownEgyptianCombatGoal.SEAL_PANEL_HALF_WIDTH;
        double height = UnknownEgyptianCombatGoal.THRESHOLD_HEIGHT * rise;
        Vec3 baseLeft = center.subtract(side.scale(halfW));
        Vec3 baseRight = center.add(side.scale(halfW));
        Vec3 topLeft = baseLeft.add(0.0D, height, 0.0D);
        Vec3 topRight = baseRight.add(0.0D, height, 0.0D);
        texturedQuad(consumer, pose, baseLeft, topLeft, topRight, baseRight,
                0.0F, 0.0F, 1.0F, 1.0F, color(0.88F * fade, SOLAR_GOLD));
        texturedQuad(consumer, pose,
                baseLeft.add(frame.direction.scale(0.04D)),
                topLeft.add(frame.direction.scale(0.04D)),
                topRight.add(frame.direction.scale(0.04D)),
                baseRight.add(frame.direction.scale(0.04D)),
                0.0F, 0.0F, 1.0F, 1.0F, color(0.72F * fade, SOLAR_WHITE));
        // Back face for solid wall read.
        texturedQuad(consumer, pose, baseRight, topRight, topLeft, baseLeft,
                0.0F, 0.0F, 1.0F, 1.0F, color(0.82F * fade, SOLAR_GOLD));
        ribbonInPlane(consumer, pose, baseLeft, topLeft, frame.direction, 0.09D,
                lane * 0.12F, color(0.98F * fade, SOLAR_GOLD));
        ribbonInPlane(consumer, pose, baseRight, topRight, frame.direction, 0.09D,
                lane * 0.12F, color(0.98F * fade, SOLAR_GOLD));
        ribbonInPlane(consumer, pose, topLeft, topRight, frame.direction, 0.1D,
                lane * 0.12F, color(1.0F * fade, WHITE));
        cartoucheOutline(pose, consumer, frame, center, halfW,
                UnknownEgyptianCombatGoal.SEAL_PANEL_HALF_LENGTH, 0.11D,
                color(0.95F * fade, SOLAR_GOLD));
    }

    private static void cartoucheOutline(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            Vec3 center,
            double halfWidth,
            double halfLength,
            double lineWidth,
            int lineColor) {
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        double bevel = Math.min(0.24D, Math.min(halfWidth, halfLength) * 0.32D);
        Vec3[] points = {
            center.add(frame.direction.scale(halfLength)).subtract(side.scale(halfWidth - bevel)),
            center.add(frame.direction.scale(halfLength - bevel)).add(side.scale(halfWidth)),
            center.subtract(frame.direction.scale(halfLength - bevel)).add(side.scale(halfWidth)),
            center.subtract(frame.direction.scale(halfLength)).add(side.scale(halfWidth - bevel)),
            center.subtract(frame.direction.scale(halfLength)).subtract(side.scale(halfWidth - bevel)),
            center.subtract(frame.direction.scale(halfLength - bevel)).subtract(side.scale(halfWidth)),
            center.add(frame.direction.scale(halfLength - bevel)).subtract(side.scale(halfWidth)),
            center.add(frame.direction.scale(halfLength)).add(side.scale(halfWidth - bevel))
        };
        for (int point = 0; point < points.length; point++) {
            ribbonInPlane(consumer, pose, points[point], points[(point + 1) % points.length],
                    up, lineWidth, point * 0.11F, lineColor);
        }
    }

    private static void khopeshCombo(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        int windup = UnknownEgyptianCombatGoal.windupTicks(frame.ruins);
        int active = UnknownEgyptianCombatGoal.activeTicks(frame.ruins);
        int cutCount = frame.corridorLength > 1.5F
                ? Math.clamp(
                        Math.round(frame.corridorLength),
                        2,
                        UnknownEgyptianCombatGoal.defensiveComboMaxCuts(frame.ruins))
                : UnknownEgyptianCombatGoal.comboCutCount(frame.ruins, false);
        float lockFlash = frame.attackAge >= UnknownEgyptianCombatGoal.lockTick(frame.ruins)
                ? 1.0F - Math.clamp(
                        (frame.attackAge - UnknownEgyptianCombatGoal.lockTick(frame.ruins)) / 4.0F,
                        0.0F,
                        1.0F)
                : 0.0F;

        if (frame.attackAge < windup) {
            float reveal = smooth(frame.attackAge / windup);
            sweepTelegraph(
                    level,
                    pose,
                    consumer,
                    frame,
                    UnknownEgyptianCombatGoal.FIRST_START_ANGLE,
                    UnknownEgyptianCombatGoal.FIRST_END_ANGLE,
                    0.42F + reveal * 0.18F + lockFlash * 0.16F,
                    reveal);
        }
        for (int cut = 0; cut < cutCount; cut++) {
            int start = UnknownEgyptianCombatGoal.comboCutStartTick(frame.ruins, cut);
            boolean leftToRight = (cut & 1) == 0;
            double startAngle = leftToRight
                    ? UnknownEgyptianCombatGoal.FIRST_START_ANGLE
                    : UnknownEgyptianCombatGoal.SECOND_START_ANGLE;
            double endAngle = leftToRight
                    ? UnknownEgyptianCombatGoal.FIRST_END_ANGLE
                    : UnknownEgyptianCombatGoal.SECOND_END_ANGLE;
            if (frame.attackAge >= start - UnknownEgyptianCombatGoal.betweenCutsTicks(frame.ruins)
                    && frame.attackAge < start) {
                float anticipation = smooth((frame.attackAge
                                - start
                                + UnknownEgyptianCombatGoal.betweenCutsTicks(frame.ruins))
                        / Math.max(1.0F, UnknownEgyptianCombatGoal.betweenCutsTicks(frame.ruins)));
                sweepTelegraph(level, pose, consumer, frame, startAngle, endAngle,
                        0.24F + anticipation * 0.34F, anticipation);
            }
            if (frame.attackAge >= start && frame.attackAge < start + active) {
                khopeshSweep(pose, consumer, frame, startAngle, endAngle,
                        (frame.attackAge - start) / active);
            }
        }
    }

    private static void sweepTelegraph(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            double startDegrees,
            double endDegrees,
            float alpha,
            float reveal) {
        int segments = 30;
        double revealedEnd = Mth.lerp(
                smooth(reveal),
                startDegrees,
                endDegrees);
        Vec3 previousOuter = groundPoint(level, frame.origin.add(
                UnknownEgyptianCombatMath.rotateHorizontal(frame.direction, startDegrees)
                        .scale(UnknownEgyptianCombatGoal.OUTER_RADIUS)));
        Vec3 previousInner = groundPoint(level, frame.origin.add(
                UnknownEgyptianCombatMath.rotateHorizontal(frame.direction, startDegrees)
                        .scale(UnknownEgyptianCombatGoal.OUTER_RADIUS - 0.42D)));
        for (int segment = 1; segment <= segments; segment++) {
            double angle = Mth.lerp(segment / (double) segments, startDegrees, revealedEnd);
            Vec3 radial = UnknownEgyptianCombatMath.rotateHorizontal(frame.direction, angle);
            Vec3 outer = groundPoint(level, frame.origin.add(
                    radial.scale(UnknownEgyptianCombatGoal.OUTER_RADIUS)));
            Vec3 inner = groundPoint(level, frame.origin.add(
                    radial.scale(UnknownEgyptianCombatGoal.OUTER_RADIUS - 0.42D)));
            ribbonOnPlane(
                    consumer,
                    pose,
                    previousOuter,
                    outer,
                    radial,
                    0.055D,
                    segment * 0.08F,
                    color(alpha, GOLD));
            ribbonOnPlane(
                    consumer,
                    pose,
                    previousInner,
                    inner,
                    radial,
                    0.035D,
                    segment * 0.08F,
                    color(alpha * 0.72F, WHITE));
            previousOuter = outer;
            previousInner = inner;
        }

        Vec3 startDirection = UnknownEgyptianCombatMath.rotateHorizontal(frame.direction, startDegrees);
        Vec3 endDirection = UnknownEgyptianCombatMath.rotateHorizontal(frame.direction, revealedEnd);
        groundQuad(
                consumer,
                pose,
                groundPoint(level, frame.origin.add(startDirection.scale(0.72D))),
                groundPoint(level, frame.origin.add(startDirection.scale(
                        UnknownEgyptianCombatGoal.OUTER_RADIUS))),
                new Vec3(-startDirection.z, 0.0D, startDirection.x),
                0.035D,
                0.0D,
                1.0D,
                color(alpha * 0.68F, WHITE));
        groundQuad(
                consumer,
                pose,
                groundPoint(level, frame.origin.add(endDirection.scale(0.72D))),
                groundPoint(level, frame.origin.add(endDirection.scale(
                        UnknownEgyptianCombatGoal.OUTER_RADIUS))),
                new Vec3(-endDirection.z, 0.0D, endDirection.x),
                0.045D,
                0.0D,
                1.0D,
                color(alpha, GOLD));
    }

    private static void khopeshSweep(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            double startDegrees,
            double endDegrees,
            float progress) {
        float eased = smooth(progress);
        double currentAngle = Mth.lerp(eased, startDegrees, endDegrees);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 hand = frame.origin.add(0.0D, 1.34D, 0.0D);
        Vec3 bladeDirection = UnknownEgyptianCombatMath.rotateHorizontal(
                frame.direction,
                currentAngle);
        Vec3 bladeBase = hand.add(bladeDirection.scale(0.62D));
        Vec3 bladeTip = hand.add(bladeDirection.scale(UnknownEgyptianCombatGoal.OUTER_RADIUS));
        ribbonOnPlane(
                consumer,
                pose,
                bladeBase,
                bladeTip,
                up,
                0.12D,
                progress * 2.0F,
                color(0.94F, SOLAR_GOLD));
        ribbonOnPlane(
                consumer,
                pose,
                bladeBase.add(0.0D, 0.025D, 0.0D),
                bladeTip.add(0.0D, 0.025D, 0.0D),
                up,
                0.042D,
                progress * 2.0F,
                color(0.98F, SOLAR_WHITE));

        int trailSegments = 16;
        double trailDegrees = 58.0D;
        double sign = Math.signum(endDegrees - startDegrees);
        Vec3 previous = bladeTip;
        for (int segment = 1; segment <= trailSegments; segment++) {
            float falloff = 1.0F - segment / (float) (trailSegments + 1);
            double angle = currentAngle - sign * trailDegrees * segment / trailSegments;
            Vec3 point = hand.add(UnknownEgyptianCombatMath.rotateHorizontal(
                    frame.direction,
                    angle).scale(UnknownEgyptianCombatGoal.OUTER_RADIUS));
            ribbonOnPlane(
                    consumer,
                    pose,
                    previous,
                    point,
                    up,
                    0.09D * falloff,
                    segment * 0.12F,
                    color(0.62F * falloff, segment < 6 ? SOLAR_WHITE : SOLAR_GOLD));
            previous = point;
        }

        Vec3 hookDirection = UnknownEgyptianCombatMath.rotateHorizontal(
                bladeDirection,
                -sign * 36.0D);
        ribbonOnPlane(
                consumer,
                pose,
                bladeTip,
                bladeTip.add(hookDirection.scale(0.52D)),
                up,
                0.09D,
                progress,
                color(0.9F, GOLD));
    }

    private static void groundLane(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        int windupTicks = UnknownGreekCombatGoal.stabWindupTicks(frame.ruins);
        int lockTick = UnknownGreekCombatGoal.stabLockTick(frame.ruins);
        int activeTicks = UnknownGreekCombatGoal.stabActiveTicks(frame.ruins);
        float windup = smooth(frame.attackAge / windupTicks);
        boolean locked = frame.attackAge >= lockTick;
        double visibleLength = locked
                ? LANE_LENGTH
                : Mth.lerp(windup, 2.4D, LANE_LENGTH - 0.55D);
        float activeFade = frame.attackAge <= windupTicks
                ? 1.0F
                : 1.0F - (frame.attackAge - windupTicks) / activeTicks;
        float lockFlash = locked
                ? 1.0F - Math.clamp(
                        (frame.attackAge - lockTick) / 4.0F,
                        0.0F,
                        1.0F)
                : 0.0F;
        float alpha = 0.52F
                * activeFade
                * (0.74F + windup * 0.26F + lockFlash * 0.16F);
        double halfWidth = 0.22D;
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        Vec3 previous = groundPoint(level, frame.origin.add(frame.direction.scale(0.65D)));
        double previousDistance = 0.65D;
        for (int index = 1; index <= LANE_SEGMENTS; index++) {
            double distance = 0.65D + (visibleLength - 0.65D) * index / LANE_SEGMENTS;
            Vec3 current = groundPoint(level, frame.origin.add(frame.direction.scale(distance)));
            groundQuad(
                    consumer,
                    pose,
                    previous,
                    current,
                    side,
                    halfWidth,
                    previousDistance * 0.48D - frame.attackAge * 0.045D,
                    distance * 0.48D - frame.attackAge * 0.045D,
                    color(alpha, WHITE));
            previous = current;
            previousDistance = distance;
        }

        Vec3 tip = groundPoint(level, frame.origin.add(frame.direction.scale(visibleLength)));
        Vec3 arrowBack = tip.subtract(frame.direction.scale(0.72D));
        ribbonOnPlane(
                consumer,
                pose,
                tip,
                arrowBack.add(side.scale(0.42D)),
                side,
                0.055D,
                0.0F,
                color(alpha, WHITE));
        ribbonOnPlane(
                consumer,
                pose,
                tip,
                arrowBack.subtract(side.scale(0.42D)),
                side,
                0.055D,
                0.0F,
                color(alpha, WHITE));

        if (locked) {
            Vec3 crossCenter = groundPoint(level, frame.origin.add(frame.direction.scale(4.35D)));
            ribbonOnPlane(
                    consumer,
                    pose,
                    crossCenter.subtract(side.scale(0.62D)),
                    crossCenter.add(side.scale(0.62D)),
                    frame.direction,
                    0.045D,
                    0.0F,
                    color(alpha * (0.8F + lockFlash * 0.35F), WHITE));
        }
    }

    private static void chargeLane(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        int windup = UnknownGreekCombatGoal.chargeWindupTicks(frame.ruins);
        int lock = UnknownGreekCombatGoal.chargeLockTicks(frame.ruins);
        int active = UnknownGreekCombatGoal.chargeActiveTicks(frame.ruins);
        if (frame.attackAge >= windup + active) {
            return;
        }
        double fullLength = UnknownGreekCombatGoal.chargeSpeed(frame.ruins) * active;
        float grow = smooth(frame.attackAge / windup);
        double visibleLength = Mth.lerp(grow, 3.0D, fullLength);
        boolean locked = frame.attackAge >= lock;
        if (locked) {
            visibleLength = fullLength;
        }
        float fade = frame.attackAge <= windup
                ? 1.0F
                : 1.0F - (frame.attackAge - windup) / active;
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        Vec3 previous = groundPoint(level, frame.origin.add(frame.direction.scale(0.7D)));
        double previousDistance = 0.7D;
        int segments = Math.max(LANE_SEGMENTS, (int) Math.ceil(visibleLength * 2.0D));
        for (int index = 1; index <= segments; index++) {
            double distance = 0.7D + (visibleLength - 0.7D) * index / segments;
            Vec3 current = groundPoint(level, frame.origin.add(frame.direction.scale(distance)));
            groundQuad(
                    consumer,
                    pose,
                    previous,
                    current,
                    side,
                    0.68D,
                    previousDistance * 0.2D,
                    distance * 0.2D,
                    color(0.34F * fade, WHITE));
            previous = current;
            previousDistance = distance;
        }
        Vec3 tip = groundPoint(level, frame.origin.add(frame.direction.scale(visibleLength)));
        Vec3 back = tip.subtract(frame.direction.scale(1.0D));
        ribbonOnPlane(consumer, pose, tip, back.add(side.scale(0.72D)), side, 0.07D, 0.0F,
                color(0.58F * fade, WHITE));
        ribbonOnPlane(consumer, pose, tip, back.subtract(side.scale(0.72D)), side, 0.07D, 0.0F,
                color(0.58F * fade, WHITE));
    }

    private static void phalanxLanes(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        int warning = UnknownGreekCombatGoal.phalanxWarningTicks(frame.ruins);
        float end = warning + (frame.ruins ? UnknownGreekCombatGoal.PHALANX_THIRD_ROW_DELAY : 0);
        if (frame.attackAge >= end || frame.corridorLength <= 0.0F) {
            return;
        }
        float localWarningAge = frame.attackAge;
        float localWarningDuration = warning;
        if (frame.ruins && frame.attackAge >= warning) {
            localWarningAge = (frame.attackAge - warning)
                    % UnknownGreekCombatGoal.PHALANX_SECOND_ROW_DELAY;
            localWarningDuration = UnknownGreekCombatGoal.PHALANX_SECOND_ROW_DELAY;
        }
        float grow = smooth(localWarningAge / localWarningDuration);
        double visibleLength = Mth.lerp(grow, 2.0D, frame.corridorLength);
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        float alpha = 0.30F + grow * 0.12F;
        for (double lane : PHALANX_LANES) {
            if (Math.abs(lane - frame.gapOffset)
                    < UnknownGreekCombatGoal.PHALANX_GAP_WIDTH * 0.5D) {
                continue;
            }
            Vec3 previous = UnknownGreekCombatMath.phalanxCorridorPoint(
                    frame.anchor,
                    frame.direction,
                    lane,
                    0.0D,
                    0.028D);
            int segments = Math.max(12, (int) Math.ceil(visibleLength));
            for (int index = 1; index <= segments; index++) {
                double distance = visibleLength * index / segments;
                Vec3 current = UnknownGreekCombatMath.phalanxCorridorPoint(
                        frame.anchor,
                        frame.direction,
                        lane,
                        distance,
                        0.028D);
                groundQuad(
                        consumer,
                        pose,
                        previous,
                        current,
                        side,
                        0.56D,
                        distance * 0.18D - 0.18D,
                        distance * 0.18D,
                        color(alpha, WHITE));
                previous = current;
            }
        }
    }

    private static void impaleSpear(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        float localAge = Math.max(0.0F, frame.attackAge - frame.gapOffset);
        float duration = Math.max(1.0F, frame.corridorLength);
        float pulse = 0.88F + 0.12F * Mth.sin(localAge * 0.82F);
        float releaseFade = 1.0F - smooth(Math.clamp(
                (localAge - duration + 4.0F) / 4.0F,
                0.0F,
                1.0F));
        float alpha = pulse * releaseFade;
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 hand = frame.origin
                .add(0.0D, 1.34D, 0.0D)
                .add(frame.direction.scale(0.42D));
        Vec3 contact = frame.anchor.add(0.0D, 0.98D, 0.0D);
        Vec3 tip = contact.add(frame.direction.scale(0.62D));
        Vec3 bladeBase = contact.subtract(frame.direction.scale(0.28D));

        ribbonOnPlane(consumer, pose, hand, bladeBase, side, 0.065D, 0.0F,
                color(0.84F * alpha, WHITE));
        ribbonOnPlane(consumer, pose, hand, bladeBase, up, 0.045D, 0.0F,
                color(0.62F * alpha, GOLD));
        texturedQuad(
                consumer,
                pose,
                tip,
                bladeBase.add(side.scale(0.31D)),
                contact,
                bladeBase.subtract(side.scale(0.31D)),
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                color(0.96F * alpha, GOLD));
        texturedQuad(
                consumer,
                pose,
                tip,
                bladeBase.add(up.scale(0.31D)),
                contact,
                bladeBase.subtract(up.scale(0.31D)),
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                color(0.78F * alpha, WHITE));
        circularVerticalRibbon(
                pose,
                consumer,
                contact,
                side,
                up,
                0.46D + 0.035D * Mth.sin(localAge * 0.72F),
                0.038D,
                color(0.58F * alpha, GOLD));
    }

    private static void doryStreak(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            int activeStart) {
        int activeTicks = UnknownGreekCombatGoal.stabActiveTicks(frame.ruins);
        float active = (frame.attackAge - activeStart + 1.0F) / activeTicks;
        float extension = smooth(active);
        float alpha = 0.82F * (1.0F - Math.clamp(active, 0.0F, 1.0F) * 0.55F);
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        Vec3 start = frame.origin.add(0.0D, 1.28D, 0.0D).add(frame.direction.scale(0.35D));
        Vec3 end = start.add(frame.direction.scale(
                (UnknownGreekCombatGoal.STAB_DORY_REACH + 0.65D) * extension));
        ribbonOnPlane(consumer, pose, start, end, side, 0.055D, 0.0F, color(alpha, WHITE));
    }

    private static void spearEruptionField(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        int ringCount = UnknownGreekCombatGoal.spearEruptionRingCount(frame.ruins);
        int windup = UnknownGreekCombatGoal.spearEruptionWindupTicks(frame.ruins);
        int ringDelay = UnknownGreekCombatGoal.spearEruptionRingDelayTicks(frame.ruins);
        int finalRingTick = windup + (ringCount - 1) * ringDelay;
        int persistence = UnknownGreekCombatGoal.spearEruptionPersistTicks(frame.ruins);
        int fadeStart = finalRingTick
                + persistence
                - UnknownGreekCombatGoal.SPEAR_ERUPTION_FADE_TICKS;
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        float warningGrow = smooth(frame.attackAge / Math.max(1.0F, windup));
        float fieldFade = 1.0F - smooth(Math.clamp(
                (frame.attackAge - fadeStart)
                        / UnknownGreekCombatGoal.SPEAR_ERUPTION_FADE_TICKS,
                0.0F,
                1.0F));

        Vec3 planted = groundPoint(level, frame.anchor).add(0.0D, 0.025D, 0.0D);
        if (frame.attackAge < windup + 3.0F) {
            float plantAlpha = 0.28F + warningGrow * 0.38F;
            circularGroundRibbon(
                    level,
                    pose,
                    consumer,
                    planted,
                    0.48D + warningGrow * 0.52D,
                    0.045D,
                    color(plantAlpha, GOLD));
            ribbonOnPlane(
                    consumer,
                    pose,
                    planted.subtract(frame.direction.scale(0.78D)),
                    planted.add(frame.direction.scale(0.78D)),
                    side,
                    0.04D,
                    0.0F,
                    color(plantAlpha * 0.86F, WHITE));
        }

        for (int ring = 0; ring < ringCount; ring++) {
            double radius = UnknownGreekCombatGoal.spearEruptionRingRadius(ring);
            float spawnTick = windup + ring * ringDelay;
            if (frame.attackAge < spawnTick) {
                float imminence = smooth(1.0F - Math.clamp(
                        (spawnTick - frame.attackAge) / Math.max(1.0F, windup),
                        0.0F,
                        1.0F));
                circularGroundRibbon(
                        level,
                        pose,
                        consumer,
                        frame.anchor,
                        radius,
                        0.018D,
                        color(0.08F + warningGrow * 0.10F + imminence * 0.24F,
                                (ring & 1) == 0 ? WHITE : GOLD));
                continue;
            }

            float ringAge = frame.attackAge - spawnTick;
            float rise = smooth(Math.clamp(ringAge / 1.6F, 0.0F, 1.0F));
            if (ringAge < 3.0F) {
                circularGroundRibbon(
                        level,
                        pose,
                        consumer,
                        frame.anchor,
                        radius,
                        0.035D,
                        color((1.0F - ringAge / 3.0F) * 0.72F, GOLD));
            }
            int spearCount = UnknownGreekCombatGoal.spearEruptionSpearCount(ring);
            for (int spear = 0; spear < spearCount; spear++) {
                Vec3 raw = UnknownGreekCombatMath.spearRingPoint(
                        frame.anchor,
                        ring,
                        spear,
                        spearCount,
                        UnknownGreekCombatGoal.SPEAR_ERUPTION_FIRST_RADIUS,
                        UnknownGreekCombatGoal.SPEAR_ERUPTION_RING_SPACING,
                        frame.gapOffset);
                Vec3 base = groundPoint(level, raw).add(0.0D, 0.018D, 0.0D);
                double variation = 0.82D
                        + 0.18D * (0.5D + 0.5D * Math.sin(ring * 19.0D + spear * 7.0D));
                fineSpectralSpear(
                        pose,
                        consumer,
                        base,
                        UnknownGreekCombatMath.spearVisualTilt(
                                ring, spear, frame.gapOffset),
                        UnknownGreekCombatGoal.SPEAR_ERUPTION_VISUAL_HEIGHT * variation * rise,
                        Math.clamp(fieldFade * rise, 0.0F, 1.0F),
                        (ring + spear) % 3 == 0);
            }
        }
    }

    private static void fineSpectralSpear(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 base,
            Vec3 shaftDirection,
            double height,
            float alpha,
            boolean goldAccent) {
        if (height <= 0.01D || alpha <= 0.01F) {
            return;
        }
        Vec3 direction = shaftDirection.lengthSqr() > 1.0E-6D
                ? shaftDirection.normalize()
                : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 axisX = direction.cross(new Vec3(0.0D, 0.0D, 1.0D));
        if (axisX.lengthSqr() <= 1.0E-6D) {
            axisX = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            axisX = axisX.normalize();
        }
        Vec3 axisZ = direction.cross(axisX).normalize();
        Vec3 tip = base.add(direction.scale(height));
        Vec3 bladeBase = tip.subtract(direction.scale(0.28D));
        Vec3 shaftTop = bladeBase.subtract(direction.scale(0.06D));
        int shaftColor = color(0.62F * alpha, goldAccent ? GOLD : WHITE);
        int edgeColor = color(0.84F * alpha, goldAccent ? WHITE : GOLD);
        ribbonOnPlane(consumer, pose, base, shaftTop, axisX, 0.014D, 0.0F, shaftColor);
        ribbonOnPlane(consumer, pose, base, shaftTop, axisZ, 0.014D, 0.0F, shaftColor);
        texturedQuad(
                consumer,
                pose,
                tip,
                bladeBase.add(axisX.scale(0.085D)),
                shaftTop,
                bladeBase.subtract(axisX.scale(0.085D)),
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                edgeColor);
        texturedQuad(
                consumer,
                pose,
                tip,
                bladeBase.add(axisZ.scale(0.085D)),
                shaftTop,
                bladeBase.subtract(axisZ.scale(0.085D)),
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                edgeColor);
    }

    private static void shieldBashTelegraph(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        int windup = UnknownGreekCombatGoal.shieldBashWindupTicks(frame.ruins);
        int activeTicks = UnknownGreekCombatGoal.SHIELD_BASH_ACTIVE_TICKS;
        if (frame.attackAge >= windup + activeTicks) {
            return;
        }
        float charge = smooth(frame.attackAge / Math.max(1.0F, windup));
        float active = Math.clamp((frame.attackAge - windup) / activeTicks, 0.0F, 1.0F);
        float fade = 1.0F - active;
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        double reach = UnknownGreekCombatGoal.SHIELD_BASH_REACH;
        double halfWidth = Mth.lerp(charge, 0.38D, 1.18D);
        Vec3 groundStart = groundPoint(level, frame.origin.add(frame.direction.scale(0.55D)));
        Vec3 groundEnd = groundPoint(level, frame.origin.add(frame.direction.scale(reach)));
        float railAlpha = (0.26F + charge * 0.34F) * fade;
        ribbonOnPlane(consumer, pose,
                groundStart.add(side.scale(0.24D)),
                groundEnd.add(side.scale(halfWidth)),
                side, 0.045D, 0.0F, color(railAlpha, WHITE));
        ribbonOnPlane(consumer, pose,
                groundStart.subtract(side.scale(0.24D)),
                groundEnd.subtract(side.scale(halfWidth)),
                side, 0.045D, 0.0F, color(railAlpha, WHITE));
        ribbonOnPlane(consumer, pose,
                groundEnd.subtract(side.scale(halfWidth)),
                groundEnd.add(side.scale(halfWidth)),
                frame.direction, 0.075D, 0.0F, color(railAlpha * 1.12F, GOLD));

        Vec3 shieldCenter = frame.origin
                .add(frame.direction.scale(0.58D + active * 1.05D))
                .add(0.0D, 1.28D, 0.0D);
        double shieldRadius = Mth.lerp(charge, 0.38D, 0.78D);
        circularVerticalRibbon(pose, consumer, shieldCenter, side, up,
                shieldRadius, 0.065D, color((0.42F + charge * 0.42F) * fade, GOLD));
        circularVerticalRibbon(pose, consumer, shieldCenter, side, up,
                shieldRadius * 0.62D, 0.035D, color((0.28F + charge * 0.36F) * fade, WHITE));
    }

    private static void spectralJavelin(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        int lock = UnknownGreekCombatGoal.javelinLockTicks(frame.ruins);
        int warning = UnknownGreekCombatGoal.javelinWarningTicks(frame.ruins);
        if (frame.attackAge >= warning + 4.0F) {
            return;
        }

        Vec3 launch = frame.origin
                .add(frame.direction.scale(0.58D))
                .add(0.0D, 1.52D, 0.0D);
        Vec3 impact = frame.anchor.add(0.0D, 0.18D, 0.0D);
        double lift = UnknownGreekCombatMath.javelinArcLift(launch, impact);
        float charge = smooth(frame.attackAge / Math.max(1.0F, lock));
        // Constant horizontal progress makes the parabola read as a thrown spear;
        // smooth-step made it visibly brake in mid-air before impact.
        float flight = Math.clamp(
                (frame.attackAge - lock) / Math.max(1.0F, warning - lock),
                0.0F,
                1.0F);
        float impactProgress = frame.attackAge < warning
                ? 0.0F
                : smooth((frame.attackAge - warning) / 4.0F);

        javelinTargetSigil(pose, consumer, frame, impact, charge, impactProgress);
        javelinArc(pose, consumer, frame, launch, impact, lift, charge, flight);

        if (frame.attackAge < lock) {
            Vec3 readyDirection = frame.direction.add(0.0D, 0.42D, 0.0D).normalize();
            Vec3 readyTip = launch.add(readyDirection.scale(1.0D + charge * 0.42D));
            spectralSpear(pose, consumer, readyTip, readyDirection, 0.42F + charge * 0.48F);
            return;
        }

        Vec3 tangent = javelinArcTangent(launch, impact, lift, flight);
        if (frame.attackAge < warning) {
            for (int echo = 2; echo >= 1; echo--) {
                double echoProgress = Math.max(0.0D, flight - echo * 0.075D);
                Vec3 echoTip = UnknownGreekCombatMath.javelinArcPoint(
                        launch, impact, echoProgress, lift);
                spectralSpear(
                        pose,
                        consumer,
                        echoTip,
                        javelinArcTangent(launch, impact, lift, echoProgress),
                        echo == 1 ? 0.23F : 0.11F);
            }
            Vec3 tip = UnknownGreekCombatMath.javelinArcPoint(launch, impact, flight, lift);
            spectralSpear(pose, consumer, tip, tangent, 0.94F);
            return;
        }

        Vec3 embeddedDirection = javelinArcTangent(launch, impact, lift, 1.0D);
        spectralSpear(
                pose,
                consumer,
                impact.subtract(embeddedDirection.scale(impactProgress * 0.32D)),
                embeddedDirection,
                0.72F * (1.0F - impactProgress));
        javelinImpactBurst(pose, consumer, frame, impact, impactProgress);
    }

    private static void javelinArc(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            Vec3 launch,
            Vec3 impact,
            double lift,
            float charge,
            float flight) {
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        double visibleEnd = frame.attackAge < UnknownGreekCombatGoal.javelinLockTicks(frame.ruins)
                ? Mth.lerp(charge, 0.16D, 1.0D)
                : 1.0D;
        int segments = 32;
        Vec3 previous = launch;
        for (int segment = 1; segment <= segments; segment++) {
            double progress = visibleEnd * segment / segments;
            Vec3 current = UnknownGreekCombatMath.javelinArcPoint(
                    launch, impact, progress, lift);
            float remainingGlow = frame.attackAge < UnknownGreekCombatGoal.javelinLockTicks(frame.ruins)
                    ? 1.0F
                    : 0.42F + (float) Math.max(0.0D, progress - flight) * 0.32F;
            int arcColor = color(
                    (segment % 5 == 0 ? 0.34F : 0.18F) * remainingGlow,
                    segment % 5 == 0 ? GOLD : WHITE);
            ribbonOnPlane(
                    consumer, pose, previous, current, side, 0.026D,
                    segment * 0.17F, arcColor);
            ribbonInPlane(
                    consumer, pose, previous, current, side, 0.018D,
                    segment * 0.17F, arcColor);
            previous = current;
        }
    }

    private static void javelinTargetSigil(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            Vec3 impact,
            float charge,
            float impactProgress) {
        Vec3 marker = new Vec3(impact.x, frame.anchor.y + 0.045D, impact.z);
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        float locked = frame.attackAge >= UnknownGreekCombatGoal.javelinLockTicks(frame.ruins)
                ? 1.0F
                : charge;
        float fade = 1.0F - impactProgress;
        double outerRadius = Mth.lerp(locked,
                0.42D,
                UnknownGreekCombatGoal.JAVELIN_IMPACT_RADIUS);
        circularRibbon(pose, consumer, marker, outerRadius, 0.07D,
                color((0.46F + locked * 0.25F) * fade, GOLD));
        circularRibbon(pose, consumer, marker, outerRadius * 0.47D, 0.035D,
                color((0.28F + locked * 0.22F) * fade, WHITE));

        double diamondRadius = outerRadius * 0.72D;
        texturedQuad(
                consumer,
                pose,
                marker.add(frame.direction.scale(diamondRadius)),
                marker.add(side.scale(diamondRadius)),
                marker.subtract(frame.direction.scale(diamondRadius)),
                marker.subtract(side.scale(diamondRadius)),
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                color(0.16F * fade, WHITE));

        if (frame.attackAge >= UnknownGreekCombatGoal.javelinLockTicks(frame.ruins)) {
            Vec3 columnTop = marker.add(0.0D, 2.4D * fade, 0.0D);
            ribbonOnPlane(consumer, pose, marker, columnTop, side, 0.035D, 0.0F,
                    color(0.24F * fade, GOLD));
            ribbonOnPlane(consumer, pose, marker, columnTop, frame.direction, 0.035D, 0.0F,
                    color(0.20F * fade, WHITE));
        }
    }

    private static void javelinImpactBurst(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            Vec3 impact,
            float progress) {
        float fade = 1.0F - progress;
        Vec3 center = new Vec3(impact.x, frame.anchor.y + 0.055D, impact.z);
        double radius = Mth.lerp(progress,
                UnknownGreekCombatGoal.JAVELIN_IMPACT_RADIUS,
                3.25D);
        circularRibbon(pose, consumer, center, radius, 0.09D,
                color(0.86F * fade, GOLD));
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        for (int ray = 0; ray < 8; ray++) {
            double angle = Math.PI * 2.0D * ray / 8.0D;
            Vec3 direction = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3 rayStart = center.add(direction.scale(0.48D));
            Vec3 rayEnd = center.add(direction.scale(radius * 1.18D));
            Vec3 width = new Vec3(-direction.z, 0.0D, direction.x);
            ribbonOnPlane(consumer, pose, rayStart, rayEnd, width, 0.045D, 0.0F,
                    color(0.62F * fade, ray % 2 == 0 ? GOLD : WHITE));
        }
        Vec3 beamTop = center.add(0.0D, Mth.lerp(progress, 5.8D, 1.2D), 0.0D);
        ribbonOnPlane(consumer, pose, center, beamTop, side, 0.12D, 0.0F,
                color(0.48F * fade, GOLD));
        ribbonOnPlane(consumer, pose, center, beamTop, frame.direction, 0.12D, 0.0F,
                color(0.42F * fade, WHITE));
    }

    private static void spectralSpear(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 tip,
            Vec3 direction,
            float alpha) {
        Vec3 axis = direction.normalize();
        if (axis.lengthSqr() <= 1.0E-8D || alpha <= 0.0F) {
            return;
        }
        Vec3 reference = Math.abs(axis.y) > 0.88D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = axis.cross(reference).normalize();
        Vec3 normal = axis.cross(side).normalize();
        Vec3 bladeBase = tip.subtract(axis.scale(0.62D));
        Vec3 shaftHead = tip.subtract(axis.scale(0.78D));
        Vec3 tail = tip.subtract(axis.scale(3.45D));
        int shaftColor = color(alpha, WHITE);
        int bladeColor = color(Math.min(1.0F, alpha * 1.08F), GOLD);
        ribbonOnPlane(consumer, pose, tail, shaftHead, side, 0.085D, 0.0F, shaftColor);
        ribbonOnPlane(consumer, pose, tail, shaftHead, normal, 0.085D, 0.0F, shaftColor);
        texturedQuad(
                consumer,
                pose,
                tip,
                bladeBase.add(side.scale(0.27D)),
                shaftHead,
                bladeBase.subtract(side.scale(0.27D)),
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                bladeColor);
        texturedQuad(
                consumer,
                pose,
                tip,
                bladeBase.add(normal.scale(0.27D)),
                shaftHead,
                bladeBase.subtract(normal.scale(0.27D)),
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                bladeColor);
        Vec3 butt = tail.subtract(axis.scale(0.22D));
        ribbonOnPlane(consumer, pose, butt, tail.add(axis.scale(0.16D)), side, 0.14D, 0.0F,
                bladeColor);
        ribbonOnPlane(consumer, pose, butt, tail.add(axis.scale(0.16D)), normal, 0.14D, 0.0F,
                bladeColor);
    }

    private static Vec3 javelinArcTangent(
            Vec3 launch,
            Vec3 impact,
            double lift,
            double progress) {
        double before = Math.max(0.0D, progress - 0.0125D);
        double after = Math.min(1.0D, progress + 0.0125D);
        return UnknownGreekCombatMath.javelinArcPoint(launch, impact, after, lift)
                .subtract(UnknownGreekCombatMath.javelinArcPoint(
                        launch, impact, before, lift))
                .normalize();
    }

    private static void circularVerticalRibbon(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 center,
            Vec3 horizontalAxis,
            Vec3 verticalAxis,
            double radius,
            double halfWidth,
            int color) {
        final int segments = 40;
        Vec3 previous = center.add(horizontalAxis.scale(radius));
        for (int segment = 1; segment <= segments; segment++) {
            double angle = Math.PI * 2.0D * segment / segments;
            Vec3 current = center
                    .add(horizontalAxis.scale(Math.cos(angle) * radius))
                    .add(verticalAxis.scale(Math.sin(angle) * radius));
            Vec3 radial = previous.add(current).scale(0.5D).subtract(center);
            ribbonOnPlane(
                    consumer,
                    pose,
                    previous,
                    current,
                    radial,
                    halfWidth,
                    segment * 0.1F,
                    color);
            previous = current;
        }
    }

    private static void circularGroundRibbon(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 center,
            double radius,
            double halfWidth,
            int color) {
        final int segments = 32;
        Vec3 previous = groundPoint(level, center.add(radius, 0.0D, 0.0D));
        for (int segment = 1; segment <= segments; segment++) {
            double angle = Math.PI * 2.0D * segment / segments;
            Vec3 current = groundPoint(level, center.add(
                    Math.cos(angle) * radius,
                    0.0D,
                    Math.sin(angle) * radius));
            Vec3 radial = previous.add(current).scale(0.5D).subtract(center);
            ribbonOnPlane(
                    consumer,
                    pose,
                    previous,
                    current,
                    radial,
                    halfWidth,
                    segment * 0.12F,
                    color);
            previous = current;
        }
    }

    private static void circularRibbon(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 center,
            double radius,
            double halfWidth,
            int color) {
        final int segments = 32;
        Vec3 previous = center.add(radius, 0.0D, 0.0D);
        for (int segment = 1; segment <= segments; segment++) {
            double angle = Math.PI * 2.0D * segment / segments;
            Vec3 current = center.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
            Vec3 midpoint = previous.add(current).scale(0.5D).subtract(center);
            ribbonOnPlane(
                    consumer,
                    pose,
                    previous,
                    current,
                    midpoint,
                    halfWidth,
                    segment * 0.12F,
                    color);
            previous = current;
        }
    }

    private static void impactGlyph(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        if (frame.fxKind == UnknownEntity.COMBAT_FX_MINE_TETHER) {
            tetherCage(pose, consumer, frame);
            return;
        }
        if (frame.fxKind == UnknownEntity.COMBAT_FX_MEDIEVAL_CUT_HIT
                || frame.fxKind == UnknownEntity.COMBAT_FX_MEDIEVAL_CUT_BLOCK) {
            medievalImpact(pose, consumer, frame);
            return;
        }
        float progress = smooth(frame.fxAge / 5.0F);
        float alpha = 0.88F * (1.0F - progress);
        double radius = Mth.lerp(progress, 0.16D, 0.62D);
        Vec3 side = new Vec3(-frame.direction.z, 0.0D, frame.direction.x);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 normal = frame.direction;
        Vec3 diagonalA = side.add(up).normalize().scale(radius * 0.82D);
        Vec3 diagonalB = side.subtract(up).normalize().scale(radius * 0.82D);
        ribbonInPlane(
                consumer,
                pose,
                frame.fxPosition.subtract(diagonalA),
                frame.fxPosition.add(diagonalA),
                normal,
                0.035D,
                0.0F,
                color(alpha, impactColor(frame.fxKind)));
        ribbonInPlane(
                consumer,
                pose,
                frame.fxPosition.subtract(diagonalB),
                frame.fxPosition.add(diagonalB),
                normal,
                0.035D,
                0.0F,
                color(alpha, impactColor(frame.fxKind)));
    }

    private static void medievalImpact(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        boolean blocked = frame.fxKind == UnknownEntity.COMBAT_FX_MEDIEVAL_CUT_BLOCK;
        float lifetime = blocked ? 6.0F : 5.0F;
        float progress = smooth(frame.fxAge / lifetime);
        float fade = 1.0F - progress;
        Vec3 forward = frame.direction;
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        if (blocked) {
            double radius = Mth.lerp(progress, 0.18D, 0.72D);
            Vec3 previous = frame.fxPosition.add(side.scale(radius));
            for (int segment = 1; segment <= 20; segment++) {
                double angle = Math.PI * 2.0D * segment / 20.0D;
                Vec3 point = frame.fxPosition
                        .add(side.scale(Math.cos(angle) * radius))
                        .add(up.scale(Math.sin(angle) * radius));
                ribbonInPlane(
                        consumer,
                        pose,
                        previous,
                        point,
                        forward,
                        0.027D,
                        segment * 0.08F,
                        color(0.9F * fade, MEDIEVAL_STEEL));
                previous = point;
            }
        } else {
            // A physical cut leaves a short directional scar at the contact
            // point; coral stays subordinate to the steel-white impact.
            Vec3 slash = side.scale(0.38D).add(up.scale(0.22D));
            ribbonInPlane(
                    consumer,
                    pose,
                    frame.fxPosition.subtract(slash),
                    frame.fxPosition.add(slash),
                    forward,
                    0.046D,
                    0.0F,
                    color(0.88F * fade, MEDIEVAL_STEEL));
            Vec3 coralSlash = side.scale(0.25D).add(up.scale(0.14D));
            ribbonInPlane(
                    consumer,
                    pose,
                    frame.fxPosition.subtract(coralSlash).subtract(forward.scale(0.012D)),
                    frame.fxPosition.add(coralSlash).subtract(forward.scale(0.012D)),
                    forward,
                    0.024D,
                    0.23F,
                    color(0.42F * fade, MEDIEVAL_CORAL));
        }
        int sparks = blocked ? 12 : 7;
        for (int spark = 0; spark < sparks; spark++) {
            double angle = Math.PI * 2.0D * spark / sparks + (spark % 3) * 0.17D;
            Vec3 radial = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).normalize();
            double length = (blocked ? 0.76D : 0.52D)
                    * (0.65D + (spark % 4) * 0.11D)
                    * (0.4D + progress);
            Vec3 start = frame.fxPosition.add(radial.scale(0.08D));
            Vec3 end = frame.fxPosition.add(radial.scale(length));
            ribbonInPlane(
                    consumer,
                    pose,
                    start,
                    end,
                    forward,
                    blocked ? 0.031D : 0.024D,
                    spark * 0.13F,
                    color(
                            (blocked ? 0.95F : 0.76F) * fade,
                            spark % 4 == 0 ? MEDIEVAL_CORAL : MEDIEVAL_STEEL));
        }
    }

    private static void tetherCage(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        float fade = 1.0F - smooth(Math.max(0.0F, frame.fxAge - 14.0F) / 6.0F);
        float close = smooth(frame.fxAge / 4.0F);
        double radius = Mth.lerp(close, 1.35D, 0.72D);
        Vec3 baseCenter = frame.fxPosition.add(0.0D, -0.86D, 0.0D);
        Vec3 topCenter = baseCenter.add(0.0D, 2.25D, 0.0D);
        for (int post = 0; post < 4; post++) {
            Vec3 radial = UnknownEgyptianCombatMath.rotateHorizontal(frame.direction, post * 90.0D);
            Vec3 base = baseCenter.add(radial.scale(radius));
            Vec3 top = topCenter.add(radial.scale(radius * 0.76D));
            ribbonInPlane(consumer, pose, base, top, radial, 0.045D,
                    post * 0.2F - frame.fxAge * 0.04F,
                    color(0.72F * fade, post % 2 == 0 ? NILE_BLUE : SOLAR_GOLD));
            ribbonInPlane(consumer, pose, top, topCenter, radial, 0.035D,
                    post * 0.2F, color(0.54F * fade, SOLAR_WHITE));
        }
        circularRibbon(pose, consumer, baseCenter, radius, 0.055D,
                color(0.62F * fade, NILE_BLUE));
        circularRibbon(pose, consumer, topCenter, radius * 0.76D, 0.045D,
                color(0.66F * fade, SOLAR_GOLD));
    }

    private static Vec3 groundPoint(ClientLevel level, Vec3 raw) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int startY = Mth.floor(raw.y + 0.5D);
        int minimumY = Mth.floor(raw.y) - 6;
        for (int y = startY; y >= minimumY; y--) {
            cursor.set(Mth.floor(raw.x), y, Mth.floor(raw.z));
            if (level.getBlockState(cursor).isFaceSturdy(level, cursor, Direction.UP)) {
                return new Vec3(raw.x, y + 1.018D, raw.z);
            }
        }
        return new Vec3(raw.x, raw.y + 0.018D, raw.z);
    }

    private static Vec3 judgmentGroundPoint(ClientLevel level, Vec3 raw) {
        var viewer = Minecraft.getInstance().player;
        if (viewer == null) {
            return groundPoint(level, raw);
        }
        // Each sample starts from the preceding surface height. This follows a
        // staircase progressively while ignoring roofs and temple platforms
        // that are far above the combat floor.
        Vec3 bottom = raw.add(0.0D, -4.0D, 0.0D);
        Vec3 rayStart = raw.add(0.0D, 1.45D, 0.0D);
        // Ignore destructible arena dressing so the blade remains attached to
        // sandstone beneath cactus columns instead of riding their top face.
        for (int ignoredHazard = 0; ignoredHazard < 8; ignoredHazard++) {
            HitResult hit = level.clip(new ClipContext(
                    rayStart,
                    bottom,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    viewer));
            if (hit.getType() == HitResult.Type.MISS) {
                return groundPoint(level, raw);
            }
            if (!(hit instanceof BlockHitResult blockHit)) {
                break;
            }
            var state = level.getBlockState(blockHit.getBlockPos());
            if (!state.is(Blocks.CACTUS) && !state.is(Blocks.FIRE)) {
                return new Vec3(raw.x, hit.getLocation().y + 0.018D, raw.z);
            }
            rayStart = hit.getLocation().add(0.0D, -0.025D, 0.0D);
        }
        return groundPoint(level, raw);
    }

    private static boolean judgmentSurfaceContinuous(Vec3 first, Vec3 second) {
        return UnknownEgyptianCombatMath.judgmentSurfaceContinuous(
                first,
                second,
                UnknownEgyptianCombatGoal.JUDGMENT_MAX_VISUAL_SURFACE_STEP);
    }

    private static void sekhmetHunt(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame) {
        int stride = UnknownEgyptianCombatGoal.huntStrideTicks(frame.ruins);
        int plannedBeats = synchronizedHuntBeatCount(frame);
        int beat = Math.min(
                plannedBeats - 1,
                Math.max(0, (int) frame.attackAge / stride));
        float beatAge = frame.attackAge - beat * stride;
        int lock = UnknownEgyptianCombatGoal.huntLockTick(frame.ruins);
        int windup = UnknownEgyptianCombatGoal.huntWindupTicks(frame.ruins);
        int slashStart = windup + UnknownEgyptianCombatGoal.huntDashTicks(frame.ruins);
        Vec3 groundBoss = groundPoint(level, frame.origin).add(0.0D, 0.025D, 0.0D);
        Vec3 groundFlank = groundPoint(level, frame.anchor).add(0.0D, 0.028D, 0.0D);
        Vec3 lane = groundFlank.subtract(groundBoss);
        Vec3 laneDirection = lane.horizontalDistanceSqr() <= 1.0E-8D
                ? frame.direction
                : new Vec3(lane.x, 0.0D, lane.z).normalize();
        Vec3 side = new Vec3(-laneDirection.z, 0.0D, laneDirection.x);
        float reveal = smooth(Math.clamp(beatAge / Math.max(1.0F, lock), 0.0F, 1.0F));
        sekhmetPredatorPath(
                pose, consumer, groundBoss, groundFlank, laneDirection, side, reveal, beat);
        sekhmetDestinationSeal(
                level, pose, consumer, groundFlank, laneDirection, side,
                reveal, beat, plannedBeats);

        if (beatAge >= windup && beatAge < slashStart) {
            float dash = smooth((beatAge - windup)
                    / Math.max(1.0F, UnknownEgyptianCombatGoal.huntDashTicks(frame.ruins)));
            for (int echo = 1; echo <= 4; echo++) {
                Vec3 echoFeet = groundPoint(level, frame.origin
                        .subtract(laneDirection.scale(0.38D + echo * 0.46D)))
                        .add(0.0D, 0.022D, 0.0D);
                sekhmetFigure(
                        pose,
                        consumer,
                        echoFeet,
                        frame.direction,
                        (0.28F - echo * 0.045F) * (0.5F + dash * 0.5F),
                        beat + echo);
            }
        }
        if (beatAge >= slashStart
                && beatAge < slashStart + UnknownEgyptianCombatGoal.HUNT_ACTIVE_TICKS) {
            boolean leftToRight = (beat & 1) == 0;
            khopeshSweep(
                    pose,
                    consumer,
                    frame,
                    leftToRight ? -72.0D : 72.0D,
                    leftToRight ? 72.0D : -72.0D,
                    (beatAge - slashStart) / UnknownEgyptianCombatGoal.HUNT_ACTIVE_TICKS);
            sekhmetClawSweep(
                    pose,
                    consumer,
                    frame,
                    leftToRight,
                    (beatAge - slashStart) / UnknownEgyptianCombatGoal.HUNT_ACTIVE_TICKS);
        }
    }

    private static int synchronizedHuntBeatCount(Frame frame) {
        int minimum = UnknownEgyptianCombatGoal.huntMinimumBeatCount(frame.ruins);
        int maximum = UnknownEgyptianCombatGoal.huntMaximumBeatCount(frame.ruins);
        int synchronizedCount = Math.round(frame.corridorLength);
        return synchronizedCount >= minimum && synchronizedCount <= maximum
                ? synchronizedCount
                : minimum;
    }

    /** One broad ivory route with royal rails remains legible below the boss model. */
    private static void sekhmetPredatorPath(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 start,
            Vec3 end,
            Vec3 forward,
            Vec3 side,
            float reveal,
            int beat) {
        groundQuad(consumer, pose, start, end, side, 0.32D,
                beat * 0.29D, beat * 0.29D + start.distanceTo(end),
                color(0.08F + reveal * 0.14F, SOLAR_WHITE));
        for (double railSide : new double[] {-0.34D, 0.34D}) {
            ribbonOnPlane(
                    consumer,
                    pose,
                    start.add(side.scale(railSide)),
                    end.add(side.scale(railSide)),
                    side,
                    0.028D,
                    beat * 0.37F,
                    color(0.42F + reveal * 0.46F, SOLAR_GOLD));
        }
        for (int chevron = 1; chevron <= 3; chevron++) {
            double progress = chevron / 4.0D;
            Vec3 tip = start.lerp(end, progress).add(forward.scale(0.2D));
            Vec3 back = tip.subtract(forward.scale(0.38D));
            int chevronColor = color((0.22F + reveal * 0.5F) * (0.78F + chevron * 0.06F),
                    chevron == 2 ? LAPIS_INLAY : SOLAR_GOLD);
            ribbonInPlane(consumer, pose, tip, back.add(side.scale(0.24D)),
                    new Vec3(0.0D, 1.0D, 0.0D), 0.035D,
                    chevron * 0.17F, chevronColor);
            ribbonInPlane(consumer, pose, tip, back.subtract(side.scale(0.24D)),
                    new Vec3(0.0D, 1.0D, 0.0D), 0.035D,
                    chevron * 0.17F, chevronColor);
        }
    }

    /** The solar seal communicates the target point and how long the hunt can continue. */
    private static void sekhmetDestinationSeal(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 center,
            Vec3 forward,
            Vec3 side,
            float reveal,
            int beat,
            int plannedBeats) {
        circularGroundRibbon(level, pose, consumer, center, 0.76D, 0.045D,
                color(0.34F + reveal * 0.58F, SOLAR_GOLD));
        circularGroundRibbon(level, pose, consumer, center, 0.49D, 0.025D,
                color(0.22F + reveal * 0.52F, LAPIS_INLAY));
        for (int claw = -1; claw <= 1; claw++) {
            Vec3 clawCenter = center.add(side.scale(claw * 0.18D));
            Vec3 clawStart = clawCenter.subtract(forward.scale(0.33D));
            Vec3 clawEnd = clawCenter.add(forward.scale(0.37D + 0.05D * (1 - Math.abs(claw))));
            ribbonInPlane(consumer, pose, clawStart, clawEnd,
                    new Vec3(0.0D, 1.0D, 0.0D), 0.025D,
                    claw * 0.15F, color(0.42F + reveal * 0.48F, SOLAR_WHITE));
        }
        for (int pip = 0; pip < plannedBeats; pip++) {
            double angle = Math.toRadians(-112.0D + pip * (224.0D / Math.max(1, plannedBeats - 1)));
            Vec3 radial = forward.scale(Math.cos(angle)).add(side.scale(Math.sin(angle)));
            Vec3 pipCenter = center.add(radial.scale(0.98D));
            Vec3 pipStart = pipCenter.subtract(radial.scale(0.075D));
            Vec3 pipEnd = pipCenter.add(radial.scale(0.075D));
            int pipColor = pip < beat ? LAPIS_INLAY : (pip == beat ? SOLAR_WHITE : SOLAR_GOLD);
            ribbonOnPlane(consumer, pose, pipStart, pipEnd,
                    new Vec3(-radial.z, 0.0D, radial.x), pip == beat ? 0.075D : 0.05D,
                    pip * 0.21F, color(pip <= beat ? 0.92F : 0.54F, pipColor));
        }
    }

    /** Articulated lioness, nemes and hooked khopesh: a readable Sekhmet apparition. */
    private static void sekhmetFigure(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 feet,
            Vec3 direction,
            float alpha,
            int variant) {
        Vec3 forward = new Vec3(direction.x, 0.0D, direction.z).normalize();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        double stride = (variant & 1) == 0 ? 0.11D : -0.11D;
        int ivory = color(alpha * 0.82F, SOLAR_WHITE);
        int gold = color(alpha, SOLAR_GOLD);
        int lapis = color(alpha * 0.9F, LAPIS_INLAY);

        orientedBox(pose, consumer,
                feet.add(side.scale(0.13D)).add(forward.scale(stride)).add(0.0D, 0.42D, 0.0D),
                forward, side, 0.075D, 0.09D, 0.4D, ivory);
        orientedBox(pose, consumer,
                feet.subtract(side.scale(0.13D)).subtract(forward.scale(stride)).add(0.0D, 0.42D, 0.0D),
                forward, side, 0.075D, 0.09D, 0.4D, ivory);
        orientedBox(pose, consumer, feet.add(0.0D, 0.83D, 0.0D), forward, side,
                0.3D, 0.16D, 0.19D, lapis);
        orientedBox(pose, consumer, feet.add(0.0D, 1.22D, 0.0D), forward, side,
                0.255D, 0.15D, 0.35D, ivory);
        orientedBox(pose, consumer, feet.add(0.0D, 1.48D, 0.0D), forward, side,
                0.34D, 0.17D, 0.065D, gold);

        Vec3 shoulder = feet.add(0.0D, 1.37D, 0.0D);
        Vec3 weaponHand = shoulder.add(side.scale((variant & 1) == 0 ? 0.47D : -0.47D))
                .add(forward.scale(0.2D)).add(0.0D, -0.23D, 0.0D);
        Vec3 freeHand = shoulder.add(side.scale((variant & 1) == 0 ? -0.44D : 0.44D))
                .add(forward.scale(0.31D)).add(0.0D, -0.06D, 0.0D);
        ribbonInPlane(consumer, pose,
                shoulder.add(side.scale((variant & 1) == 0 ? 0.26D : -0.26D)),
                weaponHand, forward, 0.075D, variant * 0.13F, ivory);
        ribbonInPlane(consumer, pose,
                shoulder.add(side.scale((variant & 1) == 0 ? -0.26D : 0.26D)),
                freeHand, forward, 0.075D, variant * 0.13F + 0.2F, ivory);

        Vec3 head = feet.add(forward.scale(0.035D)).add(0.0D, 1.82D, 0.0D);
        orientedBox(pose, consumer, head, forward, side, 0.18D, 0.2D, 0.2D, ivory);
        orientedBox(pose, consumer, head.add(forward.scale(0.22D)).add(0.0D, -0.035D, 0.0D),
                forward, side, 0.13D, 0.13D, 0.085D, gold);
        orientedBox(pose, consumer, head.subtract(forward.scale(0.12D)).add(side.scale(0.2D))
                        .add(0.0D, -0.18D, 0.0D),
                forward, side, 0.085D, 0.11D, 0.25D, lapis);
        orientedBox(pose, consumer, head.subtract(forward.scale(0.12D)).subtract(side.scale(0.2D))
                        .add(0.0D, -0.18D, 0.0D),
                forward, side, 0.085D, 0.11D, 0.25D, lapis);
        for (double earSide : new double[] {-1.0D, 1.0D}) {
            Vec3 earBase = head.add(side.scale(earSide * 0.11D)).add(0.0D, 0.16D, 0.0D);
            Vec3 earTip = head.add(side.scale(earSide * 0.25D)).add(0.0D, 0.34D, 0.0D);
            ribbonOnPlane(consumer, pose, earBase, earTip, forward,
                    0.045D, variant * 0.19F, gold);
            ribbonOnPlane(consumer, pose, earTip,
                    head.add(side.scale(earSide * 0.19D)).add(0.0D, 0.12D, 0.0D),
                    forward, 0.035D, variant * 0.19F + 0.15F, gold);
        }
        Vec3 sun = head.add(0.0D, 0.47D, 0.0D);
        circularVerticalRibbon(pose, consumer, sun, side, up,
                0.24D, 0.052D, gold);
        circularVerticalRibbon(pose, consumer, sun, side, up,
                0.11D, 0.028D, color(alpha * 0.9F, SOLAR_WHITE));
        ribbonInPlane(consumer, pose, sun.add(up.scale(0.07D)),
                sun.add(up.scale(0.31D)).add(forward.scale(0.05D)), side,
                0.032D, variant * 0.11F, lapis);
        sekhmetKhopesh(pose, consumer, weaponHand, forward, side, alpha, variant);
    }

    private static void sekhmetKhopesh(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 hand,
            Vec3 forward,
            Vec3 side,
            float alpha,
            int variant) {
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        double handedness = (variant & 1) == 0 ? 1.0D : -1.0D;
        Vec3 bladeForward = UnknownEgyptianCombatMath.rotateHorizontal(forward, handedness * 12.0D);
        Vec3 hilt = hand.add(bladeForward.scale(0.27D));
        Vec3 shoulder = hilt.add(bladeForward.scale(0.46D));
        Vec3 belly = shoulder.add(UnknownEgyptianCombatMath.rotateHorizontal(
                bladeForward, handedness * 17.0D).scale(0.43D));
        Vec3 hook = belly.add(UnknownEgyptianCombatMath.rotateHorizontal(
                bladeForward, handedness * 48.0D).scale(0.38D));
        ribbonOnPlane(consumer, pose, hand, hilt, up, 0.055D,
                variant * 0.17F, color(alpha * 0.92F, LAPIS_INLAY));
        ribbonOnPlane(consumer, pose, hilt, shoulder, up, 0.08D,
                variant * 0.17F + 0.2F, color(alpha, SOLAR_GOLD));
        ribbonOnPlane(consumer, pose, shoulder, belly, up, 0.095D,
                variant * 0.17F + 0.55F, color(alpha, SOLAR_GOLD));
        ribbonOnPlane(consumer, pose, belly, hook, up, 0.085D,
                variant * 0.17F + 0.9F, color(alpha, SOLAR_WHITE));
        ribbonOnPlane(consumer, pose, hand.subtract(side.scale(handedness * 0.13D)),
                hand.add(side.scale(handedness * 0.13D)), up,
                0.045D, 0.0F, color(alpha, SOLAR_GOLD));
    }

    private static void sekhmetClawSweep(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Frame frame,
            boolean leftToRight,
            float progress) {
        float eased = smooth(progress);
        double sign = leftToRight ? 1.0D : -1.0D;
        double currentAngle = Mth.lerp(eased, -72.0D * sign, 72.0D * sign);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        for (int claw = 0; claw < 3; claw++) {
            double radius = UnknownEgyptianCombatGoal.HUNT_SLASH_RADIUS - claw * 0.28D;
            double height = 0.76D + claw * 0.34D;
            Vec3 previous = frame.origin.add(0.0D, height, 0.0D).add(
                    UnknownEgyptianCombatMath.rotateHorizontal(
                            frame.direction, currentAngle).scale(radius));
            for (int segment = 1; segment <= 10; segment++) {
                double trailAngle = currentAngle - sign * segment * 5.2D;
                Vec3 point = frame.origin.add(0.0D, height, 0.0D).add(
                        UnknownEgyptianCombatMath.rotateHorizontal(
                                frame.direction, trailAngle).scale(radius));
                float falloff = 1.0F - segment / 11.0F;
                ribbonOnPlane(consumer, pose, previous, point, up,
                        0.055D * falloff, claw * 0.21F + segment * 0.08F,
                        color(0.78F * falloff, claw == 1 ? SOLAR_GOLD : SOLAR_WHITE));
                previous = point;
            }
        }
    }

    private static Vec3 chariotGroundPoint(ClientLevel level, Vec3 raw, double referenceY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int startY = Math.max(Mth.floor(referenceY + 10.0D), TimelessDimensions.FLOOR_Y + 8);
        int minimumY = Math.min(Mth.floor(referenceY - 12.0D), TimelessDimensions.FLOOR_Y - 4);
        for (int y = startY; y >= minimumY; y--) {
            cursor.set(Mth.floor(raw.x), y, Mth.floor(raw.z));
            if (level.getBlockState(cursor).isFaceSturdy(level, cursor, Direction.UP)) {
                return new Vec3(raw.x, y + 1.018D, raw.z);
            }
        }
        return new Vec3(raw.x, referenceY + 0.018D, raw.z);
    }

    private static void groundQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            Vec3 side,
            double halfWidth,
            double u0,
            double u1,
            int color) {
        Vec3 offset = side.scale(halfWidth);
        texturedQuad(
                consumer,
                pose,
                start.add(offset),
                start.subtract(offset),
                end.subtract(offset),
                end.add(offset),
                (float) u0,
                0.0F,
                (float) u1,
                1.0F,
                color);
    }

    private static void ribbonOnPlane(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            Vec3 widthAxis,
            double width,
            float phase,
            int color) {
        Vec3 offset = widthAxis.normalize().scale(width);
        texturedQuad(
                consumer,
                pose,
                start.add(offset),
                start.subtract(offset),
                end.subtract(offset),
                end.add(offset),
                phase,
                0.0F,
                phase + (float) start.distanceTo(end),
                1.0F,
                color);
    }

    private static void ribbonInPlane(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            Vec3 planeNormal,
            double width,
            float phase,
            int color) {
        Vec3 line = end.subtract(start);
        if (line.lengthSqr() <= 1.0E-8D) {
            return;
        }
        Vec3 widthAxis = line.normalize().cross(planeNormal).normalize();
        ribbonOnPlane(consumer, pose, start, end, widthAxis, width, phase, color);
    }

    private static void orientedBox(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 center,
            Vec3 forward,
            Vec3 side,
            double halfWidth,
            double halfLength,
            double halfHeight,
            int color) {
        Vec3 f = forward.normalize().scale(halfLength);
        Vec3 s = side.normalize().scale(halfWidth);
        Vec3 u = new Vec3(0.0D, halfHeight, 0.0D);
        Vec3 fsl = center.add(f).add(s).subtract(u);
        Vec3 fsu = center.add(f).add(s).add(u);
        Vec3 fnl = center.add(f).subtract(s).subtract(u);
        Vec3 fnu = center.add(f).subtract(s).add(u);
        Vec3 bsl = center.subtract(f).add(s).subtract(u);
        Vec3 bsu = center.subtract(f).add(s).add(u);
        Vec3 bnl = center.subtract(f).subtract(s).subtract(u);
        Vec3 bnu = center.subtract(f).subtract(s).add(u);
        texturedQuad(consumer, pose, fsl, fsu, fnu, fnl, 0, 0, 1, 1, color);
        texturedQuad(consumer, pose, bnl, bnu, bsu, bsl, 0, 0, 1, 1, color);
        texturedQuad(consumer, pose, bsl, bsu, fsu, fsl, 0, 0, 1, 1, color);
        texturedQuad(consumer, pose, fnl, fnu, bnu, bnl, 0, 0, 1, 1, color);
        texturedQuad(consumer, pose, bsu, bnu, fnu, fsu, 0, 0, 1, 1, color);
        texturedQuad(consumer, pose, bnl, bsl, fsl, fnl, 0, 0, 1, 1, color);
    }

    private static void texturedQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            float u0,
            float v0,
            float u1,
            float v1,
            int color) {
        consumer.addVertex(pose, (float) a.x, (float) a.y, (float) a.z)
                .setUv(u0, v0)
                .setColor(color);
        consumer.addVertex(pose, (float) b.x, (float) b.y, (float) b.z)
                .setUv(u0, v1)
                .setColor(color);
        consumer.addVertex(pose, (float) c.x, (float) c.y, (float) c.z)
                .setUv(u1, v1)
                .setColor(color);
        consumer.addVertex(pose, (float) d.x, (float) d.y, (float) d.z)
                .setUv(u1, v0)
                .setColor(color);
    }

    private static int color(float alpha, int rgb) {
        // These values were authored for the mod's SDR material. Shaderpacks
        // apply their own exposure/bloom, so keep the hue but cap white/gold
        // energy and reduce opacity to avoid burning floor telegraphs to white.
        return EchoShaderCompatibility.shaderPackExposureColor(alpha, rgb, 0.72F, 0.62F);
    }

    private static int impactColor(byte kind) {
        return switch (kind) {
            case UnknownEntity.COMBAT_FX_PLAYER_BLOCK -> 0xFFC21A;
            case UnknownEntity.COMBAT_FX_ASPIS_BLOCK -> 0xFFD447;
            case UnknownEntity.COMBAT_FX_WALL_SLAM -> 0xFFB000;
            case UnknownEntity.COMBAT_FX_MINE_BLAST -> SOLAR_GOLD;
            case UnknownEntity.COMBAT_FX_MINE_TETHER -> NILE_BLUE;
            case UnknownEntity.COMBAT_FX_MINE_WEAKNESS -> ROYAL_VIOLET;
            case UnknownEntity.COMBAT_FX_MINE_LAUNCH -> SOLAR_WHITE;
            case UnknownEntity.COMBAT_FX_MEDIEVAL_BLOCK -> 0xC9D0D8;
            case UnknownEntity.COMBAT_FX_MEDIEVAL_CUT_HIT -> MEDIEVAL_STEEL;
            case UnknownEntity.COMBAT_FX_MEDIEVAL_CUT_BLOCK -> MEDIEVAL_STEEL;
            default -> 0xF5F1E7;
        };
    }

    private static float combatFxLifetime(byte kind) {
        if (kind == UnknownEntity.COMBAT_FX_MINE_TETHER) {
            return 20.0F;
        }
        return kind == UnknownEntity.COMBAT_FX_MEDIEVAL_CUT_BLOCK ? 6.0F : 5.0F;
    }

    private static float smooth(float value) {
        float clamped = Math.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private record Frame(
            Vec3 origin,
            Vec3 direction,
            UnknownCombatState state,
            boolean ruins,
            float attackAge,
            Vec3 anchor,
            float gapOffset,
            float corridorLength,
            float fxAge,
            byte fxKind,
            Vec3 fxPosition,
            byte variant) {
    }

    private UnknownGreekCombatRenderer() {
    }
}
