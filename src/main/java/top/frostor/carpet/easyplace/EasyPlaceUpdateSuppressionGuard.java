package top.frostor.carpet.easyplace;

import java.util.EnumSet;

/**
 * Suppresses structural recalculation for block families during one Easy Place item placement.
 */
public final class EasyPlaceUpdateSuppressionGuard {
    public enum Family {
        RAIL,
        WALL
    }

    private static final ThreadLocal<Integer> PLACE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<EnumSet<Family>> SUPPRESSED_FAMILIES =
            ThreadLocal.withInitial(() -> EnumSet.noneOf(Family.class));

    private EasyPlaceUpdateSuppressionGuard() {
    }

    public static void beginPlacement() {
        int depth = PLACE_DEPTH.get();
        if(depth == 0) {
            SUPPRESSED_FAMILIES.remove();
        }
        PLACE_DEPTH.set(depth + 1);
    }

    public static void endPlacement() {
        int depth = PLACE_DEPTH.get() - 1;
        if(depth <= 0) {
            PLACE_DEPTH.remove();
            SUPPRESSED_FAMILIES.remove();
        } else {
            PLACE_DEPTH.set(depth);
        }
    }

    public static void request(Family family) {
        if(PLACE_DEPTH.get() > 0) {
            SUPPRESSED_FAMILIES.get().add(family);
        }
    }

    public static boolean shouldSuppress(Family family) {
        return PLACE_DEPTH.get() > 0 && SUPPRESSED_FAMILIES.get().contains(family);
    }
}
