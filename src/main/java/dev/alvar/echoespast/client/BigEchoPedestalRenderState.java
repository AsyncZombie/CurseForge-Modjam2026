package dev.alvar.echoespast.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;

public final class BigEchoPedestalRenderState extends BlockEntityRenderState {
    final ItemClusterRenderState[] fragments = new ItemClusterRenderState[6];
    final float[] fragmentScale = new float[6];
    final float[] fragmentLift = new float[6];
    ItemClusterRenderState stone;
    float stoneScale = 1.0F;
    float stoneLift;
    float age;
    int orbitEraIndex = -1;
    boolean hasStone;
}
