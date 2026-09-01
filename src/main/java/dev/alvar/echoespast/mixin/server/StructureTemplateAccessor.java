package dev.alvar.echoespast.mixin.server;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the already-decoded structure data so projection setup never has to
 * serialize a giant template to NBT and immediately decode it again.
 */
@Mixin(StructureTemplate.class)
public interface StructureTemplateAccessor {
    @Accessor("palettes")
    List<StructureTemplate.Palette> echoes$getPalettes();

    /**
     * Author-created template entities are not blocks and therefore do not
     * appear in the palette. The past projection reads this immutable decoded
     * list directly so statues retain their exact serialized attachments.
     */
    @Accessor("entityInfoList")
    List<StructureTemplate.StructureEntityInfo> echoes$getEntityInfoList();
}
