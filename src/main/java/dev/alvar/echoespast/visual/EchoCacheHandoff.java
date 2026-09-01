package dev.alvar.echoespast.visual;

/**
 * Decides when the client may compare remembered blocks with the live world.
 * While the Philosopher's Stone owns that world, every comparison would be
 * made against the temporarily materialized past instead of the present.
 */
public final class EchoCacheHandoff {
    public enum Action {
        HOLD,
        REBUILD,
        REBUILD_AND_REFRESH_SECTIONS
    }

    public static Action decide(
            boolean stoneControlsCaches,
            boolean revisionPending,
            boolean worldStateDirty,
            boolean stoneControlReleased) {
        if (stoneControlsCaches) {
            return Action.HOLD;
        }
        if (revisionPending || stoneControlReleased) {
            return Action.REBUILD_AND_REFRESH_SECTIONS;
        }
        return worldStateDirty
                ? Action.REBUILD
                : Action.HOLD;
    }

    private EchoCacheHandoff() {
    }
}
