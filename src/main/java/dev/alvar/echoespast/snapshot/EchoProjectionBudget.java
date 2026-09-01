package dev.alvar.echoespast.snapshot;

/**
 * Hard budgets for the client-facing view of a memory. Authored memories may
 * be arbitrarily large on disk; one activation only transfers the window that
 * can contribute to the current pulse.
 */
public final class EchoProjectionBudget {
    public static final int MAX_NETWORK_BLOCKS = 65_536;
    public static final int MAX_VISUAL_RADIUS = 16;
    public static final int MAX_COMPACT_QUERY_RADIUS = 32;
    public static final int LARGE_MEMORY_THRESHOLD = 16_384;
    public static final int MAX_VISIBLE_GHOST_MODELS = 4_096;
    public static final int MAX_CACHED_TEMPLATE_MODELS = 24_576;
    /**
     * The authored bounds describe what the memory stores, not how much work
     * one rendered pulse may perform. Keeping those concepts separate prevents
     * a large template from silently turning a radius-12 scanner into a
     * radius-31 synchronous client rebuild.
     */
    public static int ambientRadius(
            int configuredRadius) {
        return Math.max(
                1,
                Math.min(
                        configuredRadius,
                        MAX_VISUAL_RADIUS));
    }

    private EchoProjectionBudget() {
    }
}
