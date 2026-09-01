package dev.alvar.echoespast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alvar.echoespast.block.BigEchoPedestalBlockEntity;
import dev.alvar.echoespast.resonance.ResonanceColor;
import dev.alvar.echoespast.visual.AltarOfferingMotion;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Levitating altar offerings orbit the geometric center of the 2×2 multiblock.
 * Fragments begin orbiting as soon as they are seated; the active era pair
 * rises once the Philosopher's Stone is present and receives shader FX.
 */
public final class BigEchoPedestalRenderer
        implements BlockEntityRenderer<BigEchoPedestalBlockEntity, BigEchoPedestalRenderState> {
    /**
     * ORIGIN is the north-east cell. Multiblock AABB is X[ox-1,ox+1] × Z[oz,oz+2],
     * so the shared center sits at local (0, 1) from the ORIGIN SW corner.
     * {@link #submitItem} adds the usual +0.5 block-center bias, so these offsets
     * land on that shared center.
     */
    private static final float CENTER_X = -0.5F;
    private static final float CENTER_Z = 0.5F;
    private static final float BASE_Y = 1.45F;
    private static final float STONE_Y = 1.75F;
    private static final float FRAGMENT_SCALE = 1.28F;
    private static final float ORBIT_FRAGMENT_SCALE = 1.42F;
    private static final float STONE_SCALE = 1.15F;
    /**
     * Ground-context fragment items sit with most of their mass above the
     * translate point; lift FX so the capsule engulfs the sprite, not its base.
     */
    private static final float ORBIT_FRAGMENT_VISUAL_CENTER_Y = 0.24F;

    private final ItemModelResolver itemModelResolver;
    private final RandomSource random = RandomSource.create();

    public BigEchoPedestalRenderer(BlockEntityRendererProvider.Context context) {
        itemModelResolver = context.itemModelResolver();
    }

    @Override
    public BigEchoPedestalRenderState createRenderState() {
        return new BigEchoPedestalRenderState();
    }

    @Override
    public void extractRenderState(
            BigEchoPedestalBlockEntity altar,
            BigEchoPedestalRenderState state,
            float partialTicks,
            Vec3 camera,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(
                altar, state, partialTicks, camera, breakProgress);
        for (int i = 0; i < state.fragments.length; i++) {
            state.fragments[i] = null;
            state.fragmentScale[i] = 0.0F;
            state.fragmentLift[i] = 0.0F;
        }
        state.stone = null;
        state.stoneScale = 0.0F;
        state.stoneLift = 0.0F;
        state.hasStone = false;
        state.orbitEraIndex = altar.orbitEraIndex();
        Level level = altar.getLevel();
        if (level == null) {
            return;
        }
        state.age = level.getGameTime() + partialTicks;

        for (int i = 0; i < BigEchoPedestalBlockEntity.FRAGMENT_SLOTS; i++) {
            ItemStack stack = altar.fragment(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemClusterRenderState item = new ItemClusterRenderState();
            itemModelResolver.updateForTopItem(
                    item.item,
                    stack,
                    ItemDisplayContext.GROUND,
                    level,
                    null,
                    i);
            item.count = 1;
            item.seed = ItemClusterRenderState.getSeedForItemStack(stack);
            state.fragments[i] = item;
        }

        ItemStack stone = altar.stone();
        if (!stone.isEmpty()) {
            state.hasStone = true;
            state.stone = new ItemClusterRenderState();
            itemModelResolver.updateForTopItem(
                    state.stone.item,
                    stone,
                    ItemDisplayContext.GROUND,
                    level,
                    null,
                    64);
            state.stone.count = 1;
            state.stone.seed = ItemClusterRenderState.getSeedForItemStack(stone);
        }

        applyOfferingMotion(altar.getBlockPos(), state);

        if (level.isClientSide()
                && state.hasStone
                && state.orbitEraIndex >= 0
                && !EchoShaderCompatibility.isShaderPackActive()
                && (level.getGameTime() % 3L) == 0L) {
            spawnOrbitDust(altar, state.orbitEraIndex);
        }
    }

    @Override
    public void submit(
            BigEchoPedestalRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        float stoneBob = (float) Math.sin(state.age * 0.08F) * 0.05F;
        float stoneY = STONE_Y + stoneBob + state.stoneLift;
        if (state.stone != null && state.stoneScale > 0.012F) {
            submitItem(
                    state.stone,
                    poseStack,
                    collector,
                    state.lightCoords,
                    CENTER_X,
                    stoneY,
                    CENTER_Z,
                    state.age * 1.35F,
                    STONE_SCALE * state.stoneScale);
        }

        float[] elevatedX = new float[2];
        float[] elevatedY = new float[2];
        float[] elevatedZ = new float[2];
        int elevatedCount = 0;
        ResonanceColor eraColor = ResonanceColor.PALE_BLUE;

        // Clock-hand rates: both readable; tip hand faster without frantic spin.
        final float slowHandRate = 0.14F;
        final float fastHandRate = 0.26F;
        final float slowHandRadius = 0.70F;
        final float fastHandRadius = 0.96F;

        for (int i = 0; i < state.fragments.length; i++) {
            ItemClusterRenderState item = state.fragments[i];
            if (item == null || state.fragmentScale[i] <= 0.012F) {
                continue;
            }
            boolean elevated = state.hasStone
                    && state.orbitEraIndex >= 0
                    && i / 2 == state.orbitEraIndex
                    && state.fragmentScale[i] > 0.45F;
            float x;
            float z;
            float height;
            if (elevated) {
                // Past slot = slower/shorter hand; Ruins slot = faster/longer tip.
                boolean fastHand = (i % 2) == 1;
                float handRate = fastHand ? fastHandRate : slowHandRate;
                float handRadius = fastHand ? fastHandRadius : slowHandRadius;
                float angle = state.age * handRate
                        + (fastHand ? 0.0F : 1.15F);
                x = CENTER_X + Mth.cos(angle) * handRadius;
                z = CENTER_Z + Mth.sin(angle) * handRadius;
                height = BASE_Y
                        + 0.52F
                        + (float) Math.sin(state.age * 0.12F + i) * 0.03F
                        + (fastHand ? 0.06F : 0.0F);
                if (elevatedCount < 2) {
                    elevatedX[elevatedCount] = 0.5F + x;
                    elevatedY[elevatedCount] = height + ORBIT_FRAGMENT_VISUAL_CENTER_Y;
                    elevatedZ[elevatedCount] = 0.5F + z;
                    elevatedCount++;
                    eraColor = BigEchoPedestalBlockEntity.colorForFightSlot(i);
                }
            } else {
                float spinRate = 0.065F;
                float angle = (float) (i * (Math.PI * 2.0D / 6.0D) + state.age * spinRate);
                float radius = 0.74F;
                height = BASE_Y
                        + (float) Math.sin(state.age * 0.1F + i * 0.85F) * 0.06F
                        + 0.08F;
                x = CENTER_X + Mth.cos(angle) * radius;
                z = CENTER_Z + Mth.sin(angle) * radius;
            }
            submitItem(
                    item,
                    poseStack,
                    collector,
                    state.lightCoords,
                    x,
                    height + state.fragmentLift[i],
                    z,
                    state.age * (elevated ? 5.0F : 2.6F) + i * 28.0F,
                    (elevated ? ORBIT_FRAGMENT_SCALE : FRAGMENT_SCALE)
                            * state.fragmentScale[i]);
        }

        if (elevatedCount > 0) {
            BigEchoPedestalOrbitEffects.submit(
                    poseStack,
                    collector,
                    state.age,
                    state.blockPos,
                    0.5F + CENTER_X,
                    BASE_Y + 0.55F,
                    0.5F + CENTER_Z,
                    stoneY,
                    elevatedX,
                    elevatedY,
                    elevatedZ,
                    elevatedCount,
                    eraColor);
        }
    }

    private static void applyOfferingMotion(
            net.minecraft.core.BlockPos origin,
            BigEchoPedestalRenderState state) {
        for (int i = 0; i < state.fragments.length; i++) {
            AltarOfferingMotion.Pose pose = ClientAltarOfferingMotion.extract(
                    origin,
                    i,
                    state.fragments[i] != null,
                    state.fragments[i],
                    state.age);
            if (state.fragments[i] == null && pose.visible()) {
                state.fragments[i] = ClientAltarOfferingMotion.ghost(origin, i);
            }
            if (state.fragments[i] == null || !pose.visible()) {
                state.fragmentScale[i] = 0.0F;
                state.fragmentLift[i] = 0.0F;
                continue;
            }
            state.fragmentScale[i] = pose.scale();
            state.fragmentLift[i] = pose.heightBias();
        }
        AltarOfferingMotion.Pose stonePose = ClientAltarOfferingMotion.extract(
                origin,
                ClientAltarOfferingMotion.STONE_SLOT,
                state.stone != null,
                state.stone,
                state.age);
        if (state.stone == null && stonePose.visible()) {
            state.stone = ClientAltarOfferingMotion.ghost(
                    origin, ClientAltarOfferingMotion.STONE_SLOT);
        }
        if (state.stone == null || !stonePose.visible()) {
            state.stoneScale = 0.0F;
            state.stoneLift = 0.0F;
            state.hasStone = false;
            return;
        }
        state.hasStone = true;
        state.stoneScale = stonePose.scale();
        state.stoneLift = stonePose.heightBias();
    }

    private void submitItem(
            ItemClusterRenderState item,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            float x,
            float y,
            float z,
            float spinDegrees,
            float scale) {
        random.setSeed(item.seed);
        poseStack.pushPose();
        poseStack.translate(0.5F + x, y, 0.5F + z);
        poseStack.mulPose(Axis.YP.rotationDegrees(spinDegrees));
        poseStack.scale(scale, scale, scale);
        ItemEntityRenderer.renderMultipleFromCount(poseStack, collector, light, item, random);
        poseStack.popPose();
    }

    private static void spawnOrbitDust(BigEchoPedestalBlockEntity altar, int eraIndex) {
        Level level = altar.getLevel();
        if (level == null) {
            return;
        }
        ResonanceColor color = BigEchoPedestalBlockEntity.colorForFightSlot(eraIndex * 2);
        // Dust keeps a hint of era color mixed toward alchemical gold.
        int rgb = mixDust(color.rgb(), 0xFF9525, 0.55F);
        DustParticleOptions dust = new DustParticleOptions(rgb, 0.85F);
        var origin = altar.getBlockPos();
        double cx = origin.getX() + 0.5D + CENTER_X;
        double cy = origin.getY() + BASE_Y + 0.55D;
        double cz = origin.getZ() + 0.5D + CENTER_Z;
        var random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = 0.55D + random.nextDouble() * 0.45D;
        level.addParticle(
                dust,
                cx + Math.cos(angle) * radius,
                cy + random.nextDouble() * 0.35D,
                cz + Math.sin(angle) * radius,
                (random.nextDouble() - 0.5D) * 0.02D,
                0.02D + random.nextDouble() * 0.03D,
                (random.nextDouble() - 0.5D) * 0.02D);
    }

    private static int mixDust(int from, int to, float amount) {
        float t = Math.clamp(amount, 0.0F, 1.0F);
        int r = Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
        int g = Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
        int b = Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(BigEchoPedestalBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos())
                .expandTowards(-1.8, 3.0, 1.8)
                .expandTowards(0.8, 0.0, -0.8);
    }
}
