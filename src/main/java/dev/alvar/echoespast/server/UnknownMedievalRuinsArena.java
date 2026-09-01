package dev.alvar.echoespast.server;

import dev.alvar.echoespast.entity.UnknownEntity;
import dev.alvar.echoespast.mixin.server.StructureTemplateAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Geometry contract and runtime anchors for Medieval Ruins. */
public final class UnknownMedievalRuinsArena {
    public static final String BOSS_SPAWN_MARKER =
            "unknown_medieval_ruins_boss_spawn";
    public static final String PLAYER_SPAWN_MARKER =
            "unknown_medieval_ruins_player_spawn";
    public static final String RUBBLE_KICK_MARKER =
            "unknown_medieval_rubble_kick";

    private static UUID cachedBossId;
    private static Layout cachedLayout;

    private UnknownMedievalRuinsArena() {
    }

    public static Validation validate(StructureTemplate template, BlockPos worldOrigin) {
        List<StructureTemplate.Palette> palettes =
                ((StructureTemplateAccessor) (Object) template).echoes$getPalettes();
        List<StructureTemplate.StructureBlockInfo> blocks = palettes.isEmpty()
                ? List.of()
                : palettes.getFirst().blocks();
        return validateAuthoredData(blocks, worldOrigin);
    }

    /** Public fixture seam for the marker-contract GameTest. */
    public static Validation validateAuthoredData(
            List<StructureTemplate.StructureBlockInfo> blocks,
            BlockPos worldOrigin) {
        Map<String, List<BlockPos>> markers = collectMarkers(blocks, worldOrigin);
        List<String> errors = new ArrayList<>();
        requireExactlyOne(markers, BOSS_SPAWN_MARKER, errors);
        requireExactlyOne(markers, PLAYER_SPAWN_MARKER, errors);
        int rubbleCount = markers.getOrDefault(RUBBLE_KICK_MARKER, List.of()).size();
        if (rubbleCount < 1) {
            errors.add("marker " + RUBBLE_KICK_MARKER + " must appear at least once");
        }
        Optional<Layout> layout = errors.isEmpty()
                ? Optional.of(new Layout(
                        markers.get(BOSS_SPAWN_MARKER).getFirst(),
                        markers.get(PLAYER_SPAWN_MARKER).getFirst(),
                        List.copyOf(markers.get(RUBBLE_KICK_MARKER))))
                : Optional.empty();
        return new Validation(layout, List.copyOf(errors));
    }

    /** Uses only authored markers; normal progression must call this method. */
    public static boolean initialize(
            ServerLevel level,
            UnknownEntity boss,
            ServerPlayer owner) {
        Optional<StructureTemplate> template =
                level.getStructureManager().get(UnknownFightManager.MEDIEVAL_RUINS);
        if (template.isEmpty()) {
            return false;
        }
        Validation validation = validate(
                template.get(),
                UnknownFightManager.arenaTemplateOrigin(UnknownFightManager.MEDIEVAL_RUINS));
        if (!validation.valid() || validation.layout().isEmpty()) {
            return false;
        }
        cache(boss, validation.layout().orElseThrow());
        placeActors(boss, owner, validation.layout().orElseThrow());
        return true;
    }

    public static Optional<BlockPos> selectRubbleMarker(
            ServerLevel level,
            UnknownEntity boss,
            ServerPlayer target,
            double maximumBossDistance) {
        Layout layout = layout(level, boss);
        if (layout == null) {
            return Optional.empty();
        }
        Vec3 targetPoint = target.position().add(0.0D, target.getBbHeight() * 0.38D, 0.0D);
        return layout.rubbleKickMarkers().stream()
                .filter(marker -> Vec3.atBottomCenterOf(marker)
                        .distanceToSqr(boss.position())
                        <= maximumBossDistance * maximumBossDistance)
                .filter(marker -> trajectoryClear(
                        level,
                        boss,
                        projectileOrigin(marker),
                        targetPoint))
                .min(Comparator.comparingDouble(marker ->
                        Vec3.atBottomCenterOf(marker).distanceToSqr(boss.position())));
    }

    public static Vec3 projectileOrigin(BlockPos marker) {
        return Vec3.atBottomCenterOf(marker).add(0.0D, 0.28D, 0.0D);
    }

    public static boolean trajectoryClear(
            ServerLevel level,
            UnknownEntity boss,
            Vec3 from,
            Vec3 to) {
        return level.clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                boss)).getType() == HitResult.Type.MISS;
    }

    public static boolean hasAuthoredLayout(ServerLevel level, UnknownEntity boss) {
        layout(level, boss);
        return cachedLayout != null;
    }

    public static void clear() {
        cachedBossId = null;
        cachedLayout = null;
    }

    private static void cache(UnknownEntity boss, Layout layout) {
        cachedBossId = boss.getUUID();
        cachedLayout = layout;
    }

    private static Layout layout(ServerLevel level, UnknownEntity boss) {
        if (boss.getUUID().equals(cachedBossId) && cachedLayout != null) {
            return cachedLayout;
        }
        Optional<StructureTemplate> template =
                level.getStructureManager().get(UnknownFightManager.MEDIEVAL_RUINS);
        if (template.isEmpty()) {
            return null;
        }
        Validation validation = validate(
                template.get(),
                UnknownFightManager.arenaTemplateOrigin(UnknownFightManager.MEDIEVAL_RUINS));
        if (!validation.valid() || validation.layout().isEmpty()) {
            return null;
        }
        cache(boss, validation.layout().orElseThrow());
        return cachedLayout;
    }

    private static void placeActors(
            UnknownEntity boss,
            ServerPlayer owner,
            Layout layout) {
        Vec3 bossPosition = Vec3.atBottomCenterOf(layout.bossSpawn());
        boss.snapTo(bossPosition.x, bossPosition.y, bossPosition.z, boss.getYRot(), 0.0F);
        boss.setDeltaMovement(Vec3.ZERO);
        boss.fallDistance = 0.0F;
        if (owner != null) {
            Vec3 playerPosition = Vec3.atBottomCenterOf(layout.playerSpawn());
            owner.teleportTo(playerPosition.x, playerPosition.y, playerPosition.z);
            owner.setDeltaMovement(Vec3.ZERO);
            owner.resetFallDistance();
        }
    }

    private static Map<String, List<BlockPos>> collectMarkers(
            List<StructureTemplate.StructureBlockInfo> blocks,
            BlockPos worldOrigin) {
        Map<String, List<BlockPos>> markers = new LinkedHashMap<>();
        for (StructureTemplate.StructureBlockInfo block : blocks) {
            if (!block.state().is(Blocks.STRUCTURE_BLOCK) || block.nbt() == null) {
                continue;
            }
            String metadata = block.nbt().getStringOr("metadata", "");
            if (metadata.equals(BOSS_SPAWN_MARKER)
                    || metadata.equals(PLAYER_SPAWN_MARKER)
                    || metadata.equals(RUBBLE_KICK_MARKER)) {
                markers.computeIfAbsent(metadata, ignored -> new ArrayList<>())
                        .add(worldOrigin.offset(block.pos()));
            }
        }
        return markers;
    }

    private static void requireExactlyOne(
            Map<String, List<BlockPos>> markers,
            String marker,
            List<String> errors) {
        int count = markers.getOrDefault(marker, List.of()).size();
        if (count != 1) {
            errors.add("marker " + marker + " must appear exactly once (found " + count + ")");
        }
    }

    public record Layout(
            BlockPos bossSpawn,
            BlockPos playerSpawn,
            List<BlockPos> rubbleKickMarkers) {
    }

    public record Validation(Optional<Layout> layout, List<String> errors) {
        public boolean valid() {
            return errors.isEmpty() && layout.isPresent();
        }

        public String describe() {
            return errors.isEmpty() ? "ok" : String.join("; ", errors);
        }
    }
}
