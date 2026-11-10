package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
import top.frostor.carpet.easyplace.EasyPlaceUpdateSuppressionGuard.Family;

public final class EasyPlacePlacementLifecycleTest extends EasyPlaceTestSupport {
    private static final BlockPos ORIGIN = new BlockPos(2, 64, 6);

    private EasyPlacePlacementLifecycleTest() {
    }

    public static int run() {
        EasyPlacePlacementLifecycleTest test = new EasyPlacePlacementLifecycleTest();
        test.checkExceptionCleanup();
        test.checkNestedExceptionCleanup();
        test.checkSuccessfulCleanup();
        return test.checks();
    }

    private void checkExceptionCleanup() {
        boolean thrown = false;
        try {
            EasyPlacePlacementLifecycle.run(ORIGIN, () -> {
                EasyPlaceUpdateSuppressionGuard.request(Family.RAIL);
                throw new IllegalStateException("test placement failure");
            });
        } catch(IllegalStateException exception) {
            thrown = true;
        }
        check(thrown, "placement lifecycle must propagate action failures");
        check(
                cleanupCompleted(),
                "placement lifecycle must clean guard and item-stack state after exceptions"
        );
    }

    private void checkNestedExceptionCleanup() {
        BlockPos nested = ORIGIN.above();
        EasyPlacePlacementLifecycle.run(ORIGIN, () -> {
            EasyPlaceUpdateSuppressionGuard.request(Family.RAIL);
            try {
                EasyPlacePlacementLifecycle.run(nested, () -> {
                    EasyPlaceUpdateSuppressionGuard.request(Family.WALL);
                    throw new IllegalStateException("nested placement failure");
                });
            } catch(IllegalStateException ignored) {
            }
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL, ORIGIN)
                            && !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL, ORIGIN),
                    "nested exception cleanup must retain only the outer placement state"
            );
            check(
                    EasyPlaceItemStackSync.isPlacementActive(),
                    "nested exception cleanup must retain the outer item-stack depth"
            );
            return null;
        });
        check(cleanupCompleted(), "outer lifecycle must clean up after a handled nested exception");
    }

    private void checkSuccessfulCleanup() {
        Integer result = EasyPlacePlacementLifecycle.run(ORIGIN, () -> {
            EasyPlaceUpdateSuppressionGuard.request(Family.WALL);
            return 7;
        });
        check(result == 7, "placement lifecycle must return the action result");
        check(cleanupCompleted(), "successful placement must release all temporary state");
    }

    private static boolean cleanupCompleted() {
        return !EasyPlaceItemStackSync.isPlacementActive()
                && !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL, ORIGIN)
                && !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL, ORIGIN)
                && !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL_STATE, ORIGIN);
    }
}
