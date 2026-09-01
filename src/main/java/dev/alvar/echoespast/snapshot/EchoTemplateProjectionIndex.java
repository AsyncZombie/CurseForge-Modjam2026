package dev.alvar.echoespast.snapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Immutable spatial view of the projectable blocks in an authored memory.
 * Air and authoring markers are omitted; solid terrain remains until aligned
 * present/intact templates can prove that it is unchanged.
 */
public final class EchoTemplateProjectionIndex {
    private final Map<Long, List<StructureTemplate.StructureBlockInfo>>
            sections;
    private final int sourceBlockCount;
    private final int indexedBlockCount;
    private final List<StructureTemplate.StructureEntityInfo> entities;

    public static EchoTemplateProjectionIndex build(
            List<StructureTemplate.StructureBlockInfo> source,
            List<StructureTemplate.StructureEntityInfo> entities) {
        Map<Long, List<StructureTemplate.StructureBlockInfo>>
                mutableSections = new HashMap<>();
        int indexed = 0;
        for (StructureTemplate.StructureBlockInfo block : source) {
            if (block.state().isAir()
                    || block.state().is(Blocks.STRUCTURE_BLOCK)
                    || block.state().is(Blocks.BARRIER)) {
                continue;
            }
            BlockPos position = block.pos();
            long section = SectionPos.asLong(
                    SectionPos.blockToSectionCoord(position.getX()),
                    SectionPos.blockToSectionCoord(position.getY()),
                    SectionPos.blockToSectionCoord(position.getZ()));
            mutableSections
                    .computeIfAbsent(
                            section,
                            ignored -> new ArrayList<>())
                    .add(block);
            indexed++;
        }
        Map<Long, List<StructureTemplate.StructureBlockInfo>>
                frozenSections = new HashMap<>(
                        mutableSections.size());
        mutableSections.forEach((section, blocks) ->
                frozenSections.put(
                        section,
                        List.copyOf(blocks)));
        return new EchoTemplateProjectionIndex(
                Map.copyOf(frozenSections),
                source.size(),
                indexed,
                List.copyOf(entities));
    }

    public Query query(BlockPos minimum, BlockPos maximum) {
        List<StructureTemplate.StructureBlockInfo> result =
                new ArrayList<>();
        int visited = 0;
        int minimumSectionX =
                SectionPos.blockToSectionCoord(
                        minimum.getX());
        int minimumSectionY =
                SectionPos.blockToSectionCoord(
                        minimum.getY());
        int minimumSectionZ =
                SectionPos.blockToSectionCoord(
                        minimum.getZ());
        int maximumSectionX =
                SectionPos.blockToSectionCoord(
                        maximum.getX());
        int maximumSectionY =
                SectionPos.blockToSectionCoord(
                        maximum.getY());
        int maximumSectionZ =
                SectionPos.blockToSectionCoord(
                        maximum.getZ());
        for (int sectionX = minimumSectionX;
                sectionX <= maximumSectionX;
                sectionX++) {
            for (int sectionY = minimumSectionY;
                    sectionY <= maximumSectionY;
                    sectionY++) {
                for (int sectionZ = minimumSectionZ;
                        sectionZ <= maximumSectionZ;
                        sectionZ++) {
                    List<StructureTemplate.StructureBlockInfo>
                            candidates = sections.get(
                                    SectionPos.asLong(
                                            sectionX,
                                            sectionY,
                                            sectionZ));
                    if (candidates == null) {
                        continue;
                    }
                    visited += candidates.size();
                    for (StructureTemplate.StructureBlockInfo block
                            : candidates) {
                        BlockPos position = block.pos();
                        if (position.getX()
                                        >= minimum.getX()
                                && position.getY()
                                        >= minimum.getY()
                                && position.getZ()
                                        >= minimum.getZ()
                                && position.getX()
                                        <= maximum.getX()
                                && position.getY()
                                        <= maximum.getY()
                                && position.getZ()
                                        <= maximum.getZ()) {
                            result.add(block);
                        }
                    }
                }
            }
        }
        return new Query(List.copyOf(result), visited);
    }

    public int sourceBlockCount() {
        return sourceBlockCount;
    }

    public int indexedBlockCount() {
        return indexedBlockCount;
    }

    public List<StructureTemplate.StructureEntityInfo> entities() {
        return entities;
    }

    /**
     * The client keeps authored data sectioned too: it can inspect only the
     * frustum-visible cells instead of turning a large island into one model
     * build on activation.
     */
    public Collection<Long> sectionKeys() {
        return sections.keySet();
    }

    public List<StructureTemplate.StructureBlockInfo> sectionBlocks(
            long section) {
        return sections.getOrDefault(section, List.of());
    }

    public record Query(
            List<StructureTemplate.StructureBlockInfo> blocks,
            int visitedEntries) {
    }

    private EchoTemplateProjectionIndex(
            Map<Long, List<StructureTemplate.StructureBlockInfo>>
                    sections,
            int sourceBlockCount,
            int indexedBlockCount,
            List<StructureTemplate.StructureEntityInfo> entities) {
        this.sections = sections;
        this.sourceBlockCount = sourceBlockCount;
        this.indexedBlockCount = indexedBlockCount;
        this.entities = entities;
    }
}
