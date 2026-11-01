package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Suppresses rail shape recalculation only while an easy-place item is being placed.
 */
public final class EasyPlaceRailUpdateGuard {
    private static final long SUPPRESSION_TICKS = 2;
    private static final ThreadLocal<Integer> PLACE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> SUPPRESS_UPDATE = ThreadLocal.withInitial(() -> false);
    private static final Map<ResourceKey<Level>, Map<BlockPos, Long>> SUPPRESSED_POSITIONS =
            new ConcurrentHashMap<>();

    private EasyPlaceRailUpdateGuard() {
    }

    public static void beginPlacement() {
        PLACE_DEPTH.set(PLACE_DEPTH.get() + 1);
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

    public static void requestSuppression(Level level, BlockPos pos) {
        requestSuppression();
        SUPPRESSED_POSITIONS.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
                .put(pos.immutable(), level.getGameTime() + SUPPRESSION_TICKS);
    }

    public static boolean shouldSuppressUpdate(Level level, BlockPos pos) {
        if(PLACE_DEPTH.get() > 0 && SUPPRESS_UPDATE.get()) return true;

        Map<BlockPos, Long> positions = SUPPRESSED_POSITIONS.get(level.dimension());
        if(positions == null) return false;

        Long expiresAt = positions.get(pos);
        if(expiresAt == null) return false;
        if(level.getGameTime() <= expiresAt) return true;

        positions.remove(pos);
        if(positions.isEmpty()) SUPPRESSED_POSITIONS.remove(level.dimension(), positions);
        return false;
    }
}
