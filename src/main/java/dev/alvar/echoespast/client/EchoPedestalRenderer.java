package dev.alvar.echoespast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alvar.echoespast.block.EchoPedestalBlockEntity;
import dev.alvar.echoespast.item.PastEchoMemory;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class EchoPedestalRenderer
        implements BlockEntityRenderer<EchoPedestalBlockEntity, EchoPedestalRenderState> {
    private final ItemModelResolver itemModelResolver;
    private final RandomSource random = RandomSource.create();

    public EchoPedestalRenderer(BlockEntityRendererProvider.Context context) {
        itemModelResolver = context.itemModelResolver();
    }

    @Override
    public EchoPedestalRenderState createRenderState() {
        return new EchoPedestalRenderState();
    }

    @Override
    public void extractRenderState(
            EchoPedestalBlockEntity pedestal,
            EchoPedestalRenderState state,
            float partialTicks,
            Vec3 camera,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(
                pedestal,
                state,
                partialTicks,
                camera,
                breakProgress);
        state.displayItem = null;
        state.stoneItem = null;
        state.hasStone = false;
        ItemStack echo = pedestal.echo();
        if (pedestal.getLevel() == null) {
            return;
        }
        float age = pedestal.getLevel().getGameTime() + partialTicks;
        state.age = age;
        state.spin = age * 1.65F;
        state.bob = (float) Math.sin(age * 0.075F) * 0.055F;
        if (!echo.isEmpty()) {
            state.displayItem = itemState(
                    echo,
                    pedestal,
                    0);
            state.resonanceColor = PastEchoMemory.resolveColor(echo);
        }
        ItemStack stone = pedestal.stone();
        if (!stone.isEmpty()) {
            state.hasStone = true;
            state.stoneItem = itemState(
                    stone,
                    pedestal,
                    64);
        }
    }

    @Override
    public void submit(
            EchoPedestalRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (state.displayItem == null && state.stoneItem == null) {
            return;
        }
        float stoneY = 1.36F
                + (float) Math.sin(state.age * 0.08F) * 0.025F;
        if (state.stoneItem != null) {
            submitItem(
                    state.stoneItem,
                    poseStack,
                    collector,
                    state.lightCoords,
                    0.5F,
                    stoneY,
                    0.5F,
                    state.age * 0.9F,
                    0.84F);
        }
        if (state.displayItem != null) {
            float fragmentX = 0.5F;
            float fragmentY = 1.38F + state.bob;
            float fragmentZ = 0.5F;
            float fragmentScale = 0.88F;
            if (state.hasStone) {
                float orbit = state.age * 0.075F;
                fragmentX += (float) Math.cos(orbit) * 0.31F;
                fragmentZ += (float) Math.sin(orbit) * 0.31F;
                fragmentY = 1.64F
                        + (float) Math.sin(state.age * 0.11F) * 0.035F;
                fragmentScale = 0.76F;
            }
            submitItem(
                    state.displayItem,
                    poseStack,
                    collector,
                    state.lightCoords,
                    fragmentX,
                    fragmentY,
                    fragmentZ,
                    state.spin,
                    fragmentScale);
            if (state.hasStone) {
                BigEchoPedestalOrbitEffects.submit(
                        poseStack,
                        collector,
                        state.age,
                        state.blockPos,
                        0.5F,
                        1.48F,
                        0.5F,
                        stoneY,
                        new float[] {fragmentX},
                        new float[] {fragmentY + 0.18F},
                        new float[] {fragmentZ},
                        1,
                        state.resonanceColor);
            }
        }
    }

    private ItemClusterRenderState itemState(
            ItemStack stack,
            EchoPedestalBlockEntity pedestal,
            int seed) {
        ItemClusterRenderState result = new ItemClusterRenderState();
        itemModelResolver.updateForTopItem(
                result.item,
                stack,
                ItemDisplayContext.GROUND,
                pedestal.getLevel(),
                null,
                seed);
        result.count = 1;
        result.seed = ItemClusterRenderState.getSeedForItemStack(stack);
        return result;
    }

    private void submitItem(
            ItemClusterRenderState item,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            float x,
            float y,
            float z,
            float spin,
            float scale) {
        random.setSeed(item.seed);
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.scale(scale, scale, scale);
        ItemEntityRenderer.renderMultipleFromCount(
                poseStack,
                collector,
                light,
                item,
                random);
        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(EchoPedestalBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(1.1, 0.0, 1.1)
                .expandTowards(0.0, 2.1, 0.0);
    }
}
