package top.frostor.carpet.easyplace;

/**
 * Suppresses rail shape recalculation for the whole easy-place item placement call.
 *
 * <p>This mirrors EarlyCompat's placement-wide suppression: any rail neighbor
 * update triggered while the guarded placement is active is covered, including
 * diagonal and vertical rail neighbors.</p>
 */
public final class EasyPlaceRailUpdateGuard {
    private static final ThreadLocal<Integer> PLACE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> SUPPRESS_UPDATE = ThreadLocal.withInitial(() -> false);

    private EasyPlaceRailUpdateGuard() {
    }

    public static void beginPlacement() {
        int depth = PLACE_DEPTH.get();
        if(depth == 0) {
            SUPPRESS_UPDATE.remove();
        }
        PLACE_DEPTH.set(depth + 1);
    }

    public static void endPlacement() {
        int depth = PLACE_DEPTH.get() - 1;
        if(depth <= 0) {
            PLACE_DEPTH.remove();
            SUPPRESS_UPDATE.remove();
        } else {
            PLACE_DEPTH.set(depth);
        }
    }

    public static void requestSuppression() {
        if(PLACE_DEPTH.get() > 0) {
            SUPPRESS_UPDATE.set(true);
        }
    }

    public static boolean shouldSuppressUpdate() {
        return PLACE_DEPTH.get() > 0 && SUPPRESS_UPDATE.get();
    }
}
