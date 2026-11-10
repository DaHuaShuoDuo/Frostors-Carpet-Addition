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
        test.checkDetectorRailScope();
        test.checkWallNeighborMatchMask();
        test.checkPoweredConductorRelay();
        test.checkPlacementPositionUpdate();
        test.checkNestingAndCleanup();
        return test.checks();
    }

    private void checkDetectorRailScope() {
        EasyPlaceUpdateSuppressionGuard.beginPlacement(ORIGIN);
        try {
            EasyPlaceUpdateSuppressionGuard.request(Family.DETECTOR_RAIL);
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.DETECTOR_RAIL, ORIGIN),
                    "powered detector rail protection must cover the placed position"
            );
            for(Direction direction : Direction.values()) {
                check(
                        !EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                                Family.DETECTOR_RAIL,
                                ORIGIN.relative(direction)
                        ),
                        "detector rail protection must not cover face neighbors"
                );
            }
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            Family.DETECTOR_RAIL,
                            ORIGIN.offset(1, 0, 1)
                    ),
                    "detector rail protection must not cover diagonal positions"
            );
        } finally {
            EasyPlaceUpdateSuppressionGuard.endPlacement();
        }
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
            EasyPlaceUpdateSuppressionGuard.request(Family.OBSERVER);
            EasyPlaceUpdateSuppressionGuard.request(Family.TNT);
            EasyPlaceUpdateSuppressionGuard.requestWallNeighborMatches(0x3f);
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
                check(
                        EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.OBSERVER, neighbor),
                        "observer scope must cover all six face neighbors"
                );
                check(
                        EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.TNT, neighbor),
                        "TNT scope must cover all six face neighbors"
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

    private void checkWallNeighborMatchMask() {
        int mask = (1 << Direction.NORTH.ordinal()) | (1 << Direction.UP.ordinal());
        EasyPlaceUpdateSuppressionGuard.beginPlacement(ORIGIN);
        try {
            EasyPlaceUpdateSuppressionGuard.request(Family.WALL);
            EasyPlaceUpdateSuppressionGuard.requestWallNeighborMatches(mask);
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL, ORIGIN),
                    "the placed wall position must remain protected"
            );
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            Family.WALL,
                            ORIGIN.north()
                    )
                            && EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            Family.WALL,
                            ORIGIN.above()
                    ),
                    "matching wall neighbors must stay protected"
            );
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            Family.WALL,
                            ORIGIN.east()
                    ),
                    "mismatched wall neighbors must not inherit neighbor protection"
            );
        } finally {
            EasyPlaceUpdateSuppressionGuard.endPlacement();
        }
    }

    private void checkPoweredConductorRelay() {
        BlockPos origin = ORIGIN;
        BlockPos relay = origin.east();
        BlockPos tnt = relay.east();
        check(
                EasyPlaceUpdateSuppressionGuard.hasPoweredConductorRelay(
                        origin,
                        tnt,
                        pos -> pos.equals(relay),
                        (pos, directionFromTnt) ->
                                pos.equals(relay) && directionFromTnt == Direction.WEST
                ),
                "any powered redstone-conductor relay must protect the second-hop TNT"
        );
        check(
                !EasyPlaceUpdateSuppressionGuard.hasPoweredConductorRelay(
                        origin,
                        tnt,
                        pos -> false,
                        (pos, direction) -> true
                ),
                "a non-conductor must not relay protection"
        );
        check(
                !EasyPlaceUpdateSuppressionGuard.hasPoweredConductorRelay(
                        origin,
                        tnt,
                        pos -> pos.equals(relay),
                        (pos, direction) -> false
                ),
                "an unpowered conductor must not relay protection"
        );
        check(
                !EasyPlaceUpdateSuppressionGuard.hasPoweredConductorRelay(
                        origin,
                        origin.east(2).north(),
                        pos -> pos.equals(relay),
                        (pos, direction) -> true
                ),
                "the TNT must share the relay as a face neighbor"
        );
    }

    private void checkPlacementPositionUpdate() {
        BlockPos finalPosition = ORIGIN.offset(4, 0, 0);
        EasyPlaceUpdateSuppressionGuard.beginPlacement(ORIGIN);
        try {
            EasyPlaceUpdateSuppressionGuard.request(Family.RAIL);
            EasyPlaceUpdateSuppressionGuard.request(Family.WALL);
            EasyPlaceUpdateSuppressionGuard.requestWallNeighborMatches(0x3f);
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
        int outerWallMask = 1 << Direction.NORTH.ordinal();
        int innerWallMask = 1 << Direction.UP.ordinal();
        EasyPlaceUpdateSuppressionGuard.beginPlacement(ORIGIN);
        try {
            EasyPlaceUpdateSuppressionGuard.request(Family.RAIL);
            EasyPlaceUpdateSuppressionGuard.request(Family.WALL);
            EasyPlaceUpdateSuppressionGuard.requestWallNeighborMatches(outerWallMask);
            EasyPlaceUpdateSuppressionGuard.beginPlacement(nestedOrigin);
            try {
                check(
                        EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL, ORIGIN),
                        "inner placements must retain the outer placement's active families"
                );
                EasyPlaceUpdateSuppressionGuard.request(Family.WALL);
                EasyPlaceUpdateSuppressionGuard.request(Family.OBSERVER);
                EasyPlaceUpdateSuppressionGuard.request(Family.TNT);
                EasyPlaceUpdateSuppressionGuard.request(Family.DETECTOR_RAIL);
                EasyPlaceUpdateSuppressionGuard.requestWallNeighborMatches(innerWallMask);
                check(
                        EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL, nestedOrigin),
                        "inner wall requests must apply to the inner placement"
                );
            } finally {
                EasyPlaceUpdateSuppressionGuard.endPlacement();
            }
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.RAIL, ORIGIN)
                            && EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.WALL, ORIGIN),
                    "nested placement cleanup must retain only the outer placement's families"
            );
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            Family.WALL,
                            ORIGIN.north()
                    ) && !EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            Family.WALL,
                            ORIGIN.above()
                    ),
                    "nested wall match masks must not replace the outer placement's mask"
            );
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.OBSERVER, ORIGIN)
                            && !EasyPlaceUpdateSuppressionGuard.shouldSuppress(Family.TNT, ORIGIN)
                            && !EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                                    Family.DETECTOR_RAIL,
                                    ORIGIN
                            ),
                    "inner-only family requests must not leak into the outer placement"
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
