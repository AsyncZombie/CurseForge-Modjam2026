package dev.alvar.echoespast.snapshot;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Decodes an authored structure directly from a data resource. This is shared
 * by the dedicated server and the client so a large Past Echo can remain a
 * tiny network reference instead of a huge packet.
 */
public final class EchoTemplateResourceLoader {
    private static final long MAX_TEMPLATE_NBT_BYTES = 64L * 1024L * 1024L;

    public static Optional<StructureTemplate> load(
            ResourceProvider resources,
            HolderGetter<Block> blocks,
            Identifier template) {
        Identifier resource = template.withPrefix("structure/")
                .withSuffix(".nbt");
        try (InputStream input = resources.open(resource)) {
            StructureTemplate decoded = new StructureTemplate();
            decoded.load(
                    blocks,
                    NbtIo.readCompressed(
                            input,
                            NbtAccounter.create(
                                    MAX_TEMPLATE_NBT_BYTES)));
            return Optional.of(decoded);
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private EchoTemplateResourceLoader() {
    }
}
