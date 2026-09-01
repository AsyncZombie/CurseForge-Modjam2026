package dev.alvar.echoespast.mixin.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PostPass.class)
public interface PostPassAccessor {
    @Accessor("customUniforms")
    Map<String, GpuBuffer> echoesShowThePast$getCustomUniforms();

    @Accessor("inputs")
    List<PostPass.Input> echoesShowThePast$getInputs();
}
