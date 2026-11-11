package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;

import java.util.function.Supplier;

/**
 * Owns temporary placement state and guarantees paired cleanup on every return path.
 */
public final class EasyPlacePlacementLifecycle {
    private EasyPlacePlacementLifecycle() {
    }

    public static <T> T run(BlockPos placementPos, Supplier<T> action) {
        EasyPlaceUpdateSuppressionGuard.beginPlacement(placementPos);
        EasyPlaceItemStackSync.beginPlacement();
        try {
            return action.get();
        } finally {
            try {
                EasyPlaceItemStackSync.endPlacement();
            } finally {
                EasyPlaceUpdateSuppressionGuard.endPlacement();
            }
        }
    }

    public static <T> T run(
            BlockPos placementPos,
            EasyPlacePlacementRequest.Snapshot request,
            Supplier<T> action
    ) {
        EasyPlacePlacementRequest.begin(request);
        try {
            return run(placementPos, action);
        } finally {
            EasyPlacePlacementRequest.end();
        }
    }
}
