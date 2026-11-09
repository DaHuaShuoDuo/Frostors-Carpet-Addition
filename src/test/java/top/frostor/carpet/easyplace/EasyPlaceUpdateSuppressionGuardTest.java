package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import top.frostor.carpet.easyplace.EasyPlaceUpdateSuppressionGuard.Family;

public final class EasyPlaceUpdateSuppressionGuardTest extends EasyPlaceTestSupport {
    private static final BlockPos ORIGIN = new BlockPos(10, 64, -4);

    private EasyPlaceUpdateSuppressionGuardTest() {
    }

    public static int run() {
        EasyPlaceUpdateSuppressionGuardTest test = new EasyPlaceUpdateSuppressionGuardTest();
        test.checkFamilyIsolation();
        test.checkSpatialScope();
        test.checkPlacementPositionUpdate();
        test.checkNestingAndCleanup();
        return test.checks();
    }

    private void checkFamilyIsolation() {
        EasyPlaceUpdateSuppressionGuard.request(Family.RAIL);
        check(
                !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL, ORIGIN),
                "requests outside a placement must not arm rail suppression"
        );

        EasyPlaceUpdateSuppressionGuard.beginPlacement(ORIGIN);
        try {
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL, ORIGIN)
                            && !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL, ORIGIN),
                    "suppression must start inactive for both families"
            );
            EasyPlaceUpdateSuppressionGuard.request(Family.RAIL);
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL, ORIGIN),
                    "a rail request must suppress rails"
            );
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL, ORIGIN),
                    "a rail request must not suppress walls"
            );
            EasyPlaceUpdateSuppressionGuard.request(Family.WALL);
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL, ORIGIN),
                    "a wall request must suppress walls independently"
            );
        } finally {
            EasyPlaceUpdateSuppressionGuard.endPlacement();
        }
        check(
                !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL, ORIGIN)
                        && !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL, ORIGIN),
                "ending placement must clear every family"
        );
    }

    private void checkSpatialScope() {
        EasyPlaceUpdateSuppressionGuard.beginPlacement(ORIGIN);
        try {
            EasyPlaceUpdateSuppressionGuard.request(Family.RAIL);
            EasyPlaceUpdateSuppressionGuard.request(Family.WALL);
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL, ORIGIN)
                            && EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL, ORIGIN),
                    "the placed position must be inside the suppression scope"
            );
            for(Direction direction : Direction.values()) {
                BlockPos neighbor = ORIGIN.relative(direction);
                boolean railExpected = direction != Direction.DOWN;
                check(
                        EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL, neighbor)
                                == railExpected,
                        "rail scope must cover horizontal and upward neighbors only"
                );
                check(
                        EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL, neighbor),
                        "wall scope must cover all six face neighbors"
                );
            }
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            Family.RAIL,
                            ORIGIN.offset(1, 0, 1)
                    ),
                    "diagonal positions must remain outside the suppression scope"
            );
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            Family.WALL,
                            ORIGIN.offset(2, 0, 0)
                    ),
                    "positions two blocks away must remain outside the suppression scope"
            );
        } finally {
            EasyPlaceUpdateSuppressionGuard.endPlacement();
        }
    }

    private void checkPlacementPositionUpdate() {
        BlockPos finalPosition = ORIGIN.offset(4, 0, 0);
        EasyPlaceUpdateSuppressionGuard.beginPlacement(ORIGIN);
        try {
            EasyPlaceUpdateSuppressionGuard.request(Family.RAIL);
            EasyPlaceUpdateSuppressionGuard.request(Family.WALL);
            EasyPlaceUpdateSuppressionGuard.updatePlacementPosition(finalPosition);
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL, finalPosition)
                            && EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                                    Family.WALL,
                                    finalPosition.below()
                            ),
                    "the final placement position must replace the initial context position"
            );
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL, ORIGIN),
                    "the superseded initial position must leave the suppression scope"
            );
        } finally {
            EasyPlaceUpdateSuppressionGuard.endPlacement();
        }
    }

    private void checkNestingAndCleanup() {
        BlockPos nestedOrigin = ORIGIN.above();
        EasyPlaceUpdateSuppressionGuard.beginPlacement(ORIGIN);
        try {
            EasyPlaceUpdateSuppressionGuard.request(Family.RAIL);
            EasyPlaceUpdateSuppressionGuard.beginPlacement(nestedOrigin);
            try {
                EasyPlaceUpdateSuppressionGuard.request(Family.WALL);
            } finally {
                EasyPlaceUpdateSuppressionGuard.endPlacement();
            }
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL, ORIGIN)
                            && EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL, ORIGIN),
                    "nested placement cleanup must retain the outer placement's active families"
            );
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            Family.WALL,
                            nestedOrigin.above()
                    ),
                    "nested placement cleanup must remove the inner placement scope"
            );
        } finally {
            EasyPlaceUpdateSuppressionGuard.endPlacement();
        }

        EasyPlaceUpdateSuppressionGuard.beginPlacement(ORIGIN);
        try {
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL, ORIGIN)
                            && !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL, ORIGIN),
                    "a new placement must not inherit a previous family state"
            );
        } finally {
            EasyPlaceUpdateSuppressionGuard.endPlacement();
        }
    }
}
