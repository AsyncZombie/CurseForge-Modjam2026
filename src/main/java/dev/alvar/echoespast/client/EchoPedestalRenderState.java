package dev.alvar.echoespast.client;

import dev.alvar.echoespast.resonance.ResonanceColor;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;

public final class EchoPedestalRenderState extends BlockEntityRenderState {
    ItemClusterRenderState displayItem;
    ItemClusterRenderState stoneItem;
    ResonanceColor resonanceColor = ResonanceColor.ICE;
    boolean hasStone;
    float age;
    float spin;
    float bob;
}
