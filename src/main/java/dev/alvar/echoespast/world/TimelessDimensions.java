package dev.alvar.echoespast.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;

public final class TimelessDimensions {
    public static final ResourceKey<Level> TIMELESS_VOID = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath("echoes_show_the_past", "timeless_void"));
    public static final ResourceKey<DimensionType> TIMELESS_VOID_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            Identifier.fromNamespaceAndPath("echoes_show_the_past", "timeless_void"));

    /** Arena entrance used when a player starts the Unknown fight. */
    public static final Vec3 BOSS_ENTRANCE_SPAWN = new Vec3(-7.5D, 68.0D, 1.0D);
    public static final BlockPos HUB_SPAWN = BlockPos.containing(BOSS_ENTRANCE_SPAWN);
    /**
     * Canonical walking surface. Local Axiom surface Y=6 becomes world Y=63.
     */
    public static final int FLOOR_Y = 63;
    /**
     * Walk-in exit pad on the south rim, one block above the shared floor.
     * The portal has no collision, so it must sit on the floor rather than
     * replace it. Never toward the plaza center, so it cannot appear under
     * the player when a cycle ends.
     */
    public static final BlockPos EXIT_PORTAL = new BlockPos(
            HUB_SPAWN.getX(),
            FLOOR_Y + 1,
            HUB_SPAWN.getZ() - 3);
    /** Authored north-east/root cell of the single 2x2 final-boss altar. */
    public static final BlockPos BOSS_PEDESTAL_ORIGIN = new BlockPos(-36, 64, 0);
    public static final BlockPos PEDESTAL_GREEK = BOSS_PEDESTAL_ORIGIN;
    public static final BlockPos PEDESTAL_EGYPTIAN = BOSS_PEDESTAL_ORIGIN;
    public static final BlockPos PEDESTAL_MEDIEVAL = BOSS_PEDESTAL_ORIGIN;
    public static final BlockPos BOSS_SPAWN = new BlockPos(0, 65, 8);

    /**
     * Canonical horizontal footprint and current Greek height. Every arena is
     * authored at this origin with the same X/Z size, while Y may vary. Runtime
     * clearing and protection derive the maximum loaded height and also include
     * the three permanent pedestal stations.
     *
     * <p>Each Axiom selection is rebased from its own exported minimum. This
     * removes authoring-origin drift while keeping the canonical Greek Past
     * placement: local surface Y=6 becomes walkable Y=63.</p>
     */
    public static final BlockPos ARENA_ORIGIN = new BlockPos(-40, 57, -18);
    /**
     * Medieval keeps four authored subterranean layers below the common
     * foundation. Place the complete 39-block-tall selection lower instead of
     * rebasing/cropping it; its plaza surface still lands on {@link #FLOOR_Y}.
     */
    public static final BlockPos MEDIEVAL_ARENA_ORIGIN = ARENA_ORIGIN.below(4);
    public static final Vec3i ARENA_VOLUME = new Vec3i(70, 24, 37);

    private TimelessDimensions() {
    }
}
