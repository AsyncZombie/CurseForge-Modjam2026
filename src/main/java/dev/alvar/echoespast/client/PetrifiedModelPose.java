package dev.alvar.echoespast.client;

import dev.alvar.echoespast.mixin.client.ModelPartAccessor;
import dev.alvar.echoespast.relic.BakedModelPose;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

final class PetrifiedModelPose {
    static BakedModelPose capture(Model<?> model) {
        List<BakedModelPose.Part> parts = new ArrayList<>();
        capturePart("root", model.root(), parts);
        return new BakedModelPose(parts);
    }

    static void apply(Model<?> model, BakedModelPose pose) {
        if (pose.isEmpty()) {
            return;
        }
        Map<String, BakedModelPose.Part> byPath = new HashMap<>();
        for (BakedModelPose.Part part : pose.parts()) {
            byPath.put(part.path(), part);
        }
        applyPart("root", model.root(), byPath);
    }

    private static void capturePart(
            String path,
            ModelPart modelPart,
            List<BakedModelPose.Part> output) {
        if (output.size() >= BakedModelPose.MAX_PARTS) {
            return;
        }
        output.add(new BakedModelPose.Part(
                path,
                modelPart.x,
                modelPart.y,
                modelPart.z,
                modelPart.xRot,
                modelPart.yRot,
                modelPart.zRot,
                modelPart.xScale,
                modelPart.yScale,
                modelPart.zScale,
                modelPart.visible,
                modelPart.skipDraw));
        for (Map.Entry<String, ModelPart> child
                : children(modelPart).entrySet()) {
            capturePart(path + "/" + child.getKey(), child.getValue(), output);
        }
    }

    private static void applyPart(
            String path,
            ModelPart modelPart,
            Map<String, BakedModelPose.Part> byPath) {
        BakedModelPose.Part frozen = byPath.get(path);
        if (frozen != null) {
            modelPart.x = frozen.x();
            modelPart.y = frozen.y();
            modelPart.z = frozen.z();
            modelPart.xRot = frozen.xRot();
            modelPart.yRot = frozen.yRot();
            modelPart.zRot = frozen.zRot();
            modelPart.xScale = frozen.xScale();
            modelPart.yScale = frozen.yScale();
            modelPart.zScale = frozen.zScale();
            modelPart.visible = frozen.visible();
            modelPart.skipDraw = frozen.skipDraw();
        }
        for (Map.Entry<String, ModelPart> child
                : children(modelPart).entrySet()) {
            applyPart(path + "/" + child.getKey(), child.getValue(), byPath);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ModelPart> children(ModelPart part) {
        return ((ModelPartAccessor) (Object) part)
                .echoesShowThePast$getChildren();
    }

    private PetrifiedModelPose() {
    }
}
