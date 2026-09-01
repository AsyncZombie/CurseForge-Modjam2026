package dev.alvar.echoespast.visual;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class EchoBlockChange {
    public static Kind classify(@Nullable BlockState remembered, BlockState present) {
        return classify(remembered, present, true);
    }

    /**
     * @param authoredBySite whether the site itself places a block at this
     *     cell. A memory only stores what its author built, so a cell that no
     *     template describes is world terrain rather than remembered air:
     *     reading it as air makes an echo hide the seabed and every naturally
     *     generated block around a site and redraw them as ghosts. Callers with
     *     no way to tell pass {@code true} and keep the coarser reading.
     */
    public static Kind classify(
            @Nullable BlockState remembered,
            BlockState present,
            boolean authoredBySite) {
        if (!claimsRememberedSolid(remembered)) {
            // Standing liquid is scenery no memory ever claimed. An island
            // authored in a dry editor world remembers no ocean, so the sea is
            // never something that was added to it. A liquid that replaced
            // something the author did build still counts as a change below.
            // Explicit air, barriers and non-colliding decoration (grass,
            // flowers, carpets) do not occupy past volume as solids: rubble that
            // lands on them is an addition into historical air, not a
            // replacement of remembered terrain.
            if (present.isAir() || present.liquid()) {
                return Kind.UNCHANGED;
            }
            if (remembered != null && !remembered.isAir() && !remembered.is(Blocks.BARRIER)) {
                // The template mentioned this cell as pass-through decoration.
                // Present solids there are additions into empty past space.
                return Kind.ADDED;
            }
            return authoredBySite ? Kind.ADDED : Kind.UNCHANGED;
        }
        if (present.isAir()) {
            return Kind.MISSING;
        }
        return remembered.equals(present) ? Kind.UNCHANGED : Kind.REPLACED;
    }

    /**
     * Whether a remembered state claims solid past volume that a present block
     * can replace. Barriers are authoring masks for historical air. Plants and
     * other empty-collision decoration never filled that volume as solids.
     */
    public static boolean claimsRememberedSolid(@Nullable BlockState remembered) {
        if (remembered == null
                || remembered.isAir()
                || remembered.is(Blocks.BARRIER)) {
            return false;
        }
        return !remembered.getCollisionShape(
                        EmptyBlockGetter.INSTANCE,
                        BlockPos.ZERO)
                .isEmpty();
    }

    public static boolean obstructsPastSpace(BlockState present, BlockGetter level, BlockPos position) {
        return !present.isAir()
                && present.getFluidState().isEmpty()
                && present.getRenderShape() == RenderShape.MODEL
                && !present.getCollisionShape(level, position).isEmpty();
    }

    /**
     * Every visible present block must yield when the remembered state is air or
     * a different block. Collision and render shape are deliberately irrelevant:
     * plants and block entities occupy the reconstruction just as cubes do.
     */
    public static boolean shouldHidePresentGeometry(BlockState present) {
        return !present.isAir();
    }

    public enum Kind {
        UNCHANGED(false, false),
        MISSING(true, false),
        REPLACED(true, true),
        ADDED(false, true);

        private final boolean rendersRememberedBlock;
        private final boolean canFadePresentBlock;

        Kind(boolean rendersRememberedBlock, boolean canFadePresentBlock) {
            this.rendersRememberedBlock = rendersRememberedBlock;
            this.canFadePresentBlock = canFadePresentBlock;
        }

        public boolean rendersRememberedBlock() {
            return rendersRememberedBlock;
        }

        public boolean canFadePresentBlock() {
            return canFadePresentBlock;
        }
    }

    private EchoBlockChange() {
    }
}
