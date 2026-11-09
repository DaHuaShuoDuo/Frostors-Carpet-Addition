package top.frostor.carpet.easyplace;

import top.frostor.carpet.easyplace.EasyPlaceUpdateSuppressionGuard.Family;

public final class EasyPlaceUpdateSuppressionGuardTest extends EasyPlaceTestSupport {
    private EasyPlaceUpdateSuppressionGuardTest() {
    }

    public static int run() {
        EasyPlaceUpdateSuppressionGuardTest test = new EasyPlaceUpdateSuppressionGuardTest();
        test.checkFamilyIsolation();
        test.checkNestingAndCleanup();
        return test.checks();
    }

    private void checkFamilyIsolation() {
        EasyPlaceUpdateSuppressionGuard.request(Family.RAIL);
        check(
                !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL),
                "requests outside a placement must not arm rail suppression"
        );

        EasyPlaceUpdateSuppressionGuard.beginPlacement();
        try {
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL)
                            && !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL),
                    "suppression must start inactive for both families"
            );
            EasyPlaceUpdateSuppressionGuard.request(Family.RAIL);
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL),
                    "a rail request must suppress rails"
            );
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL),
                    "a rail request must not suppress walls"
            );
            EasyPlaceUpdateSuppressionGuard.request(Family.WALL);
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL),
                    "a wall request must suppress walls independently"
            );
        } finally {
            EasyPlaceUpdateSuppressionGuard.endPlacement();
        }
        check(
                !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL)
                        && !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL),
                "ending placement must clear every family"
        );
    }

    private void checkNestingAndCleanup() {
        EasyPlaceUpdateSuppressionGuard.beginPlacement();
        try {
            EasyPlaceUpdateSuppressionGuard.request(Family.RAIL);
            EasyPlaceUpdateSuppressionGuard.beginPlacement();
            try {
                EasyPlaceUpdateSuppressionGuard.request(Family.WALL);
            } finally {
                EasyPlaceUpdateSuppressionGuard.endPlacement();
            }
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL)
                            && EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL),
                    "nested placement cleanup must retain the outer placement's active families"
            );
        } finally {
            EasyPlaceUpdateSuppressionGuard.endPlacement();
        }

        EasyPlaceUpdateSuppressionGuard.beginPlacement();
        try {
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL)
                            && !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL),
                    "a new placement must not inherit a previous family state"
            );
        } finally {
            EasyPlaceUpdateSuppressionGuard.endPlacement();
        }
    }
}
