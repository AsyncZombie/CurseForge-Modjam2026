package dev.alvar.echoespast.server;

import de.markusbordihn.easynpc.api.handler.EasyNPCEntityHandler;
import de.markusbordihn.easynpc.data.npc.NPCRemovalReason;
import de.markusbordihn.easynpc.data.skin.SkinDataEntry;
import de.markusbordihn.easynpc.data.skin.SkinType;
import de.markusbordihn.easynpc.data.objective.ObjectiveDataEntry;
import de.markusbordihn.easynpc.data.objective.ObjectiveType;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.ai.goal.BowAttackGoal;
import de.markusbordihn.easynpc.entity.easynpc.ai.goal.CustomMeleeAttackGoal;
import de.markusbordihn.easynpc.entity.easynpc.data.NavigationDataCapable;
import dev.alvar.echoespast.entity.UnknownEntity;
import dev.alvar.echoespast.mixin.server.StructureTemplateAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the authored Easy NPC defenders and geometry-driven triggers for
 * Medieval Past.
 *
 * <p>The blueprint is the source of truth. No boss, roof or castle trigger
 * coordinate is duplicated in Java; all of them come from data-mode structure
 * blocks which the placement processor turns into air.</p>
 */
public final class UnknownMedievalVanguard {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnknownMedievalVanguard.class);

    public static final String TEMPORARY_TAG = "echoes_unknown_medieval_temporary";
    public static final String VANGUARD_TAG = "echoes_unknown_medieval_vanguard";
    public static final String OUTER_TAG = "echoes_unknown_medieval_outer";
    public static final String INNER_TAG = "echoes_unknown_medieval_inner";
    public static final String INFANTRY_TAG = "infantry";
    public static final String ARCHER_TAG = "archer";

    public static final String BOSS_SPAWN_MARKER = "unknown_medieval_boss_spawn";
    public static final String ROOF_MIN_MARKER = "unknown_medieval_roof_min";
    public static final String ROOF_MAX_MARKER = "unknown_medieval_roof_max";
    public static final String INNER_MIN_MARKER = "unknown_medieval_inner_min";
    public static final String INNER_MAX_MARKER = "unknown_medieval_inner_max";
    public static final String TRANSITION_LANDING_MARKER =
            "unknown_medieval_transition_landing";

    private static final Set<String> PAST_MARKERS = Set.of(
            BOSS_SPAWN_MARKER,
            ROOF_MIN_MARKER,
            ROOF_MAX_MARKER,
            INNER_MIN_MARKER,
            INNER_MAX_MARKER,
            TRANSITION_LANDING_MARKER);
    private static final Set<String> GROUP_TAGS = Set.of(OUTER_TAG, INNER_TAG);
    private static final Set<String> ROLE_TAGS = Set.of(INFANTRY_TAG, ARCHER_TAG);
    private static final int EXPECTED_OUTER_INFANTRY = 4;
    private static final int EXPECTED_OUTER_ARCHERS = 2;
    private static final int EXPECTED_INNER_INFANTRY = 2;
    private static final int EXPECTED_INNER_ARCHERS = 4;

    private static UUID cachedBossId;
    private static Layout cachedLayout;
    private static final Set<UUID> refreshedObjectiveIds = new HashSet<>();
    private static final Set<UUID> ownerTargetObjectiveIds = new HashSet<>();

    private UnknownMedievalVanguard() {
    }

    /** Marker names are shared with the placement processor. */
    public static boolean isFightMarker(String metadata) {
        return metadata != null && metadata.startsWith("unknown_medieval_");
    }

    public static boolean isVanguard(Entity entity) {
        return entity != null && entity.entityTags().contains(VANGUARD_TAG);
    }

    public static boolean isTemporary(Entity entity) {
        return entity != null && entity.entityTags().contains(TEMPORARY_TAG);
    }

    public static boolean bossFootprintInside(AABB volume, AABB bossBounds) {
        return volume.contains(bossBounds.getCenter())
                && bossBounds.minX >= volume.minX
                && bossBounds.maxX <= volume.maxX
                && bossBounds.minZ >= volume.minZ
                && bossBounds.maxZ <= volume.maxZ;
    }

    /** Strict authoring seam retained for roof-specific tests and diagnostics. */
    public static boolean futureBossFootprintInsideRooftop(
            ServerLevel level,
            UnknownEntity boss,
            Vec3 horizontalStep) {
        Layout layout = layout(level, boss);
        return layout != null && bossFootprintInside(
                layout.rooftop(),
                boss.getBoundingBox().move(horizontalStep.x, 0.0D, horizontalStep.z));
    }

    /**
     * Validates the complete future footprint in whichever authored duel area
     * is active. Roof height remains roof-locked; the definitive lower-plaza
     * placement uses the arena bounds and cannot fall through to another mode.
     */
    public static boolean futureBossFootprintInsideCombatVolume(
            ServerLevel level,
            UnknownEntity boss,
            Vec3 horizontalStep) {
        AABB volume = activeCombatVolume(level, boss);
        return volume != null && bossFootprintInside(
                volume,
                boss.getBoundingBox().move(horizontalStep.x, 0.0D, horizontalStep.z));
    }

    /** Authored centre of the main plaza used as the Past duel / Ruins landing. */
    public static Optional<BlockPos> transitionLanding(ServerLevel level, UnknownEntity boss) {
        Layout layout = layout(level, boss);
        return layout == null ? Optional.empty() : Optional.of(layout.transitionLanding());
    }

    /**
     * Validates the complete authoring contract before the first entity is
     * created. This prevents a half-authored encounter from leaking persistent
     * Easy NPC records into a save.
     */
    public static Validation validate(StructureTemplate template, BlockPos worldOrigin) {
        List<StructureTemplate.Palette> palettes =
                ((StructureTemplateAccessor) (Object) template).echoes$getPalettes();
        List<StructureTemplate.StructureBlockInfo> blocks = palettes.isEmpty()
                ? List.of()
                : palettes.getFirst().blocks();
        List<StructureTemplate.StructureEntityInfo> roots =
                ((StructureTemplateAccessor) (Object) template).echoes$getEntityInfoList();
        return validateAuthoredData(blocks, roots, worldOrigin);
    }

    /** Geometry-only seam retained for marker-specific tests and diagnostics. */
    public static Validation validateLayout(StructureTemplate template, BlockPos worldOrigin) {
        List<StructureTemplate.Palette> palettes =
                ((StructureTemplateAccessor) (Object) template).echoes$getPalettes();
        List<StructureTemplate.StructureBlockInfo> blocks = palettes.isEmpty()
                ? List.of()
                : palettes.getFirst().blocks();
        return validateLayoutData(blocks, worldOrigin);
    }

    /** Public fixture seam used by dedicated authoring-contract GameTests. */
    public static Validation validateAuthoredData(
            List<StructureTemplate.StructureBlockInfo> blocks,
            List<StructureTemplate.StructureEntityInfo> roots,
            BlockPos worldOrigin) {
        Validation layoutValidation = validateLayoutData(blocks, worldOrigin);
        List<String> errors = new ArrayList<>(layoutValidation.errors());

        Map<GroupRole, Integer> counts = new HashMap<>();
        for (StructureTemplate.StructureEntityInfo root : roots) {
            CompoundTag rootData = root.nbt;
            if (rootData == null) {
                continue;
            }
            validateEntityTree(rootData, counts, errors);
        }

        expectCount(errors, counts, OUTER_TAG, INFANTRY_TAG, EXPECTED_OUTER_INFANTRY);
        expectCount(errors, counts, OUTER_TAG, ARCHER_TAG, EXPECTED_OUTER_ARCHERS);
        expectCount(errors, counts, INNER_TAG, INFANTRY_TAG, EXPECTED_INNER_INFANTRY);
        expectCount(errors, counts, INNER_TAG, ARCHER_TAG, EXPECTED_INNER_ARCHERS);

        return new Validation(layoutValidation.layout(), List.copyOf(errors));
    }

    private static Validation validateLayoutData(
            List<StructureTemplate.StructureBlockInfo> blocks,
            BlockPos worldOrigin) {
        List<String> errors = new ArrayList<>();
        Map<String, List<BlockPos>> markers = collectMarkers(blocks, worldOrigin);
        for (String required : PAST_MARKERS) {
            int count = markers.getOrDefault(required, List.of()).size();
            if (count != 1) {
                errors.add("marker " + required + " must appear exactly once (found " + count + ")");
            }
        }
        Optional<Layout> layout = errors.isEmpty()
                ? Optional.of(layoutFromMarkers(markers))
                : Optional.empty();
        return new Validation(layout, List.copyOf(errors));
    }

    /** Called after a validated Medieval Past template has placed its entities. */
    public static boolean initialize(
            ServerLevel level,
            UnknownEntity boss,
            UnknownEncounterSavedData encounter) {
        Optional<StructureTemplate> template =
                level.getStructureManager().get(UnknownFightManager.MEDIEVAL_PAST);
        if (template.isEmpty()) {
            return false;
        }
        Validation validation = validate(
                template.get(),
                UnknownFightManager.arenaTemplateOrigin(UnknownFightManager.MEDIEVAL_PAST));
        if (!validation.valid() || validation.layout().isEmpty()) {
            LOGGER.error(
                    "Medieval Past authoring contract failed: {}",
                    validation.describe());
            return false;
        }

        Layout layout = validation.layout().orElseThrow();
        cachedBossId = boss.getUUID();
        cachedLayout = layout;
        refreshedObjectiveIds.clear();
        ownerTargetObjectiveIds.clear();
        encounter.setMedievalRooftopStarted(false);
        encounter.setMedievalInnerActive(false);

        // Plaza fight: stand on the lower-plaza landing, not the authored rooftop spawn.
        Vec3 plazaSpawn = Vec3.atBottomCenterOf(layout.transitionLanding());
        boss.snapTo(plazaSpawn.x, plazaSpawn.y, plazaSpawn.z, boss.getYRot(), 0.0F);
        boss.setDeltaMovement(Vec3.ZERO);
        boss.fallDistance = 0.0F;
        boss.getNavigation().stop();
        boss.setTarget(null);
        boss.setInvulnerable(false);

        List<Entity> temporaryEntities = loadedTemporary(level);
        List<String> liveErrors = validateLiveVanguard(temporaryEntities);
        if (!liveErrors.isEmpty()) {
            LOGGER.error("Medieval vanguard did not materialize completely: {}", liveErrors);
            return false;
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (Entity entity : temporaryEntities) {
            ids.add(entity.getUUID());
            if (isVanguard(entity)) {
                configureEntity(entity, encounter, null);
            }
        }
        encounter.setMedievalVanguardIds(List.copyOf(ids));
        return true;
    }

    /** Adds newly placed Ruins ambience to the same deterministic cleanup ledger. */
    public static void trackTemporaryEntities(
            ServerLevel level,
            UnknownEncounterSavedData encounter) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>(encounter.medievalVanguardIds());
        loadedTemporary(level).forEach(entity -> ids.add(entity.getUUID()));
        encounter.setMedievalVanguardIds(List.copyOf(ids));
    }

    /** Maintains owner-only hostility and opens the inner castle trigger. */
    public static void tick(
            ServerLevel level,
            UnknownEntity boss,
            UnknownEncounterSavedData encounter,
            ServerPlayer owner) {
        if (encounter.era() != UnknownFightManager.Era.MEDIEVAL
                || encounter.phase() != UnknownFightManager.Phase.PAST
                || encounter.action() != UnknownFightManager.Action.COMBAT) {
            return;
        }
        Layout layout = layout(level, boss);
        if (layout == null) {
            return;
        }

        if (owner != null
                && !encounter.medievalInnerActive()
                && layout.innerTrigger().contains(owner.position())) {
            encounter.setMedievalInnerActive(true);
        }
        if (owner != null && owner.isAlive() && !owner.isSpectator()) {
            boss.setInvulnerable(false);
            boss.setTarget(owner);
        }

        for (UUID id : encounter.medievalVanguardIds()) {
            Entity entity = level.getEntity(id);
            if (entity != null && isVanguard(entity)) {
                configureEntity(entity, encounter, owner);
            }
        }
    }

    /**
     * Removes loaded entities and asks EasyNPC to forget tracked unloaded NPCs.
     * Repeated calls are deliberately harmless.
     */
    public static void clear(ServerLevel level, UnknownEncounterSavedData encounter) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>(encounter.medievalVanguardIds());
        for (Entity entity : loadedTemporary(level)) {
            ids.add(entity.getUUID());
        }
        for (UUID id : ids) {
            Entity entity = level.getEntity(id);
            if (entity instanceof EasyNPC<?> easyNPC) {
                EasyNPCEntityHandler.despawn(easyNPC, NPCRemovalReason.KILLED);
            } else if (!EasyNPCEntityHandler.despawn(id, level, NPCRemovalReason.KILLED)
                    && entity != null) {
                entity.discard();
            }
        }
        encounter.setMedievalVanguardIds(List.of());
        encounter.setMedievalInnerActive(false);
        encounter.setMedievalRooftopStarted(false);
        cachedBossId = null;
        cachedLayout = null;
        refreshedObjectiveIds.clear();
        ownerTargetObjectiveIds.clear();
    }

    private static void configureEntity(
            Entity entity,
            UnknownEncounterSavedData encounter,
            ServerPlayer owner) {
        boolean innerDormant = entity.entityTags().contains(INNER_TAG)
                && !encounter.medievalInnerActive();
        if (entity instanceof EasyNPC<?> easyNPC) {
            Mob mob = easyNPC.getMob();
            mob.setNoAi(innerDormant);
            // Structure placement loads the authored ObjectiveData correctly,
            // but its delayed registration can run before the fresh UUID is in
            // EasyNPC's live index. Refresh once per encounter so bow/melee
            // goals are built from the untouched .bp configuration.
            if (refreshedObjectiveIds.add(entity.getUUID())) {
                easyNPC.getEasyNPCObjectiveData().refreshCustomObjectives();
                if (entity.entityTags().contains(ARCHER_TAG)) {
                    easyNPC.getEntityGoalSelector().addGoal(
                            1,
                            new BowAttackGoal<>(easyNPC, 0.9D, 20, 20.0F));
                } else {
                    easyNPC.getEntityGoalSelector().addGoal(
                            1,
                            new CustomMeleeAttackGoal<>(easyNPC, 0.95D, false));
                }
            }
            NavigationDataCapable<?> navigation = easyNPC.getEasyNPCNavigationData();
            if (!navigation.hasHomePosition()) {
                navigation.setHomePosition(entity.blockPosition());
            }
            if (innerDormant || owner == null || !owner.isAlive() || owner.isSpectator()) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            } else {
                if (ownerTargetObjectiveIds.add(entity.getUUID())) {
                    ObjectiveDataEntry ownerTarget = new ObjectiveDataEntry(
                            ObjectiveType.ATTACK_PLAYER_BY_NAME,
                            1);
                    ownerTarget.setTargetPlayerName(owner.getName().getString());
                    easyNPC.getEasyNPCObjectiveData().addOrUpdateCustomObjective(ownerTarget);
                }
                mob.setTarget(owner);
            }
            return;
        }
        if (entity instanceof Mob mob) {
            mob.setNoAi(innerDormant);
            if (innerDormant) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
        }
    }

    private static List<String> validateLiveVanguard(List<Entity> entities) {
        Map<GroupRole, Integer> counts = new HashMap<>();
        for (Entity entity : entities) {
            if (!isVanguard(entity)) {
                continue;
            }
            Set<String> tags = entity.entityTags();
            List<String> groups = GROUP_TAGS.stream().filter(tags::contains).toList();
            List<String> roles = ROLE_TAGS.stream().filter(tags::contains).toList();
            if (groups.size() == 1 && roles.size() == 1) {
                counts.merge(new GroupRole(groups.getFirst(), roles.getFirst()), 1, Integer::sum);
            }
        }
        List<String> errors = new ArrayList<>();
        expectCount(errors, counts, OUTER_TAG, INFANTRY_TAG, EXPECTED_OUTER_INFANTRY);
        expectCount(errors, counts, OUTER_TAG, ARCHER_TAG, EXPECTED_OUTER_ARCHERS);
        expectCount(errors, counts, INNER_TAG, INFANTRY_TAG, EXPECTED_INNER_INFANTRY);
        expectCount(errors, counts, INNER_TAG, ARCHER_TAG, EXPECTED_INNER_ARCHERS);
        return errors;
    }

    private static Layout layout(ServerLevel level, UnknownEntity boss) {
        if (boss.getUUID().equals(cachedBossId) && cachedLayout != null) {
            return cachedLayout;
        }
        Optional<StructureTemplate> template =
                level.getStructureManager().get(UnknownFightManager.MEDIEVAL_PAST);
        if (template.isEmpty()) {
            return null;
        }
        Validation validation = validateLayout(
                template.get(),
                UnknownFightManager.arenaTemplateOrigin(UnknownFightManager.MEDIEVAL_PAST));
        if (!validation.valid() || validation.layout().isEmpty()) {
            return null;
        }
        cachedBossId = boss.getUUID();
        cachedLayout = validation.layout().orElseThrow();
        return cachedLayout;
    }

    private static AABB activeCombatVolume(ServerLevel level, UnknownEntity boss) {
        Layout layout = layout(level, boss);
        if (layout == null) {
            return null;
        }
        AABB rooftop = layout.rooftop();
        boolean atRooftopHeight = boss.getY() >= rooftop.minY - 1.0D
                && boss.getY() <= rooftop.maxY + 1.0D;
        if (atRooftopHeight) {
            return rooftop;
        }
        UnknownFightManager.ArenaBounds arena = UnknownFightManager.arenaBounds(level);
        BlockPos origin = arena.origin();
        Vec3i size = arena.size();
        return new AABB(
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                origin.getX() + size.getX(),
                origin.getY() + size.getY(),
                origin.getZ() + size.getZ());
    }

    private static List<Entity> loadedTemporary(ServerLevel level) {
        return loadedTagged(level, UnknownMedievalVanguard::isTemporary);
    }

    private static List<Entity> loadedTagged(
            ServerLevel level,
            java.util.function.Predicate<Entity> selector) {
        UnknownFightManager.ArenaBounds bounds = UnknownFightManager.arenaBounds(level);
        BlockPos origin = bounds.origin();
        Vec3i size = bounds.size();
        AABB search = new AABB(
                origin.getX() - 4.0D,
                origin.getY() - 4.0D,
                origin.getZ() - 4.0D,
                origin.getX() + size.getX() + 4.0D,
                origin.getY() + size.getY() + 4.0D,
                origin.getZ() + size.getZ() + 4.0D);
        return level.getEntitiesOfClass(Entity.class, search, selector);
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
            if (isFightMarker(metadata)) {
                markers.computeIfAbsent(metadata, ignored -> new ArrayList<>())
                        .add(worldOrigin.offset(block.pos()));
            }
        }
        return markers;
    }

    private static Layout layoutFromMarkers(Map<String, List<BlockPos>> markers) {
        BlockPos bossSpawn = markers.get(BOSS_SPAWN_MARKER).getFirst();
        AABB rooftop = inclusiveVolume(
                markers.get(ROOF_MIN_MARKER).getFirst(),
                markers.get(ROOF_MAX_MARKER).getFirst());
        AABB inner = inclusiveVolume(
                markers.get(INNER_MIN_MARKER).getFirst(),
                markers.get(INNER_MAX_MARKER).getFirst());
        BlockPos transitionLanding = markers.get(TRANSITION_LANDING_MARKER).getFirst();
        return new Layout(bossSpawn, rooftop, inner, transitionLanding);
    }

    private static AABB inclusiveVolume(BlockPos first, BlockPos second) {
        return new AABB(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()) + 1.0D,
                Math.max(first.getY(), second.getY()) + 1.0D,
                Math.max(first.getZ(), second.getZ()) + 1.0D);
    }

    private static void validateEntityTree(
            CompoundTag data,
            Map<GroupRole, Integer> counts,
            List<String> errors) {
        Set<String> tags = entityTags(data);
        boolean vanguard = tags.contains(VANGUARD_TAG);
        long groupCount = GROUP_TAGS.stream().filter(tags::contains).count();
        long roleCount = ROLE_TAGS.stream().filter(tags::contains).count();
        if (vanguard) {
            if (!tags.contains(TEMPORARY_TAG)) {
                errors.add("each vanguard entity needs " + TEMPORARY_TAG
                        + " so every attempt can clean it safely");
            }
            if (groupCount != 1) {
                errors.add("each vanguard entity needs exactly one outer/inner tag");
            }
            if (roleCount != 1) {
                errors.add("each vanguard entity needs exactly one role tag");
            }
            if (groupCount == 1 && roleCount == 1) {
                String group = GROUP_TAGS.stream().filter(tags::contains).findFirst().orElseThrow();
                String role = ROLE_TAGS.stream().filter(tags::contains).findFirst().orElseThrow();
                counts.merge(new GroupRole(group, role), 1, Integer::sum);
                validateSkin(data, role, errors);
            }
        } else if (groupCount > 0 || roleCount > 0) {
            errors.add("group/role-tagged entity is missing " + VANGUARD_TAG);
        }

        for (Tag passenger : data.getListOrEmpty("Passengers")) {
            if (passenger instanceof CompoundTag passengerData) {
                validateEntityTree(passengerData, counts, errors);
            }
        }
    }

    private static void validateSkin(CompoundTag data, String role, List<String> errors) {
        String entityId = data.getStringOr("id", "");
        if (!entityId.startsWith("easy_npc:")) {
            errors.add("role " + role + " must be an EasyNPC entity (found " + entityId + ")");
            return;
        }
        if (!data.contains("SkinData")) {
            errors.add("EasyNPC role " + role + " is missing SkinData");
            return;
        }
        SkinDataEntry skin = new SkinDataEntry(data.getCompoundOrEmpty("SkinData"));
        SkinType type = skin.type();
        if (type == SkinType.NONE) {
            errors.add("EasyNPC role " + role + " has no local skin configured");
            return;
        }
        if (type == SkinType.SECURE_REMOTE_URL) {
            if (skin.url().isBlank() || !skin.url().startsWith("https://")) {
                errors.add("EasyNPC role " + role + " has an invalid secure skin URL");
            }
            return;
        }
        if (type == SkinType.INSECURE_REMOTE_URL || type == SkinType.PLAYER_SKIN) {
            errors.add("EasyNPC role " + role + " uses an insecure/player skin (" + type + ")");
            return;
        }
        if (type == SkinType.CUSTOM && skin.content().isBlank()) {
            errors.add("EasyNPC role " + role + " has a custom skin without embedded Content");
        }
    }

    private static Set<String> entityTags(CompoundTag data) {
        Set<String> tags = new HashSet<>();
        for (Tag tag : data.getListOrEmpty("Tags")) {
            tag.asString().ifPresent(tags::add);
        }
        return tags;
    }

    private static void expectCount(
            List<String> errors,
            Map<GroupRole, Integer> counts,
            String group,
            String role,
            int expected) {
        int actual = counts.getOrDefault(new GroupRole(group, role), 0);
        if (actual != expected) {
            errors.add(group + "/" + role + " expected " + expected + " (found " + actual + ")");
        }
    }

    public record Layout(
            BlockPos bossSpawn,
            AABB rooftop,
            AABB innerTrigger,
            BlockPos transitionLanding) {
    }

    public record Validation(Optional<Layout> layout, List<String> errors) {
        public boolean valid() {
            return errors.isEmpty() && layout.isPresent();
        }

        public String describe() {
            return errors.isEmpty() ? "valid" : String.join("; ", errors);
        }
    }

    private record GroupRole(String group, String role) {
    }
}
