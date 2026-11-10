package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Suppresses structural recalculation for block families during one Easy Place item placement.
 */
public final class EasyPlaceUpdateSuppressionGuard {
    public enum Family {
        RAIL(EnumSet.of(
                Direction.NORTH,
                Direction.SOUTH,
                Direction.WEST,
                Direction.EAST,
                Direction.UP
        )),
        WALL(EnumSet.allOf(Direction.class)),
        OBSERVER(EnumSet.allOf(Direction.class)),
        TNT(EnumSet.allOf(Direction.class)),
        DETECTOR_RAIL(EnumSet.noneOf(Direction.class));

        private final Set<Direction> coveredDirections;

        Family(Set<Direction> coveredDirections) {
            this.coveredDirections = Set.copyOf(coveredDirections);
        }

        public boolean covers(BlockPos placementPos, BlockPos pos) {
            if(pos.equals(placementPos)) {
                return true;
            }
            for(Direction direction : coveredDirections) {
                if(pos.equals(placementPos.relative(direction))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final ThreadLocal<Integer> PLACE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<EnumSet<Family>> SUPPRESSED_FAMILIES =
            ThreadLocal.withInitial(() -> EnumSet.noneOf(Family.class));
    private static final ThreadLocal<Deque<PlacementScope>> PLACEMENT_SCOPES =
            ThreadLocal.withInitial(ArrayDeque::new);

    private EasyPlaceUpdateSuppressionGuard() {
    }

    public static void beginPlacement(BlockPos placementPos) {
        int depth = PLACE_DEPTH.get();
        if(depth == 0) {
            SUPPRESSED_FAMILIES.remove();
            PLACEMENT_SCOPES.remove();
        }
        PLACEMENT_SCOPES.get().push(new PlacementScope(placementPos, 0));
        PLACE_DEPTH.set(depth + 1);
    }

    public static void endPlacement() {
        int depth = PLACE_DEPTH.get() - 1;
        if(depth <= 0) {
            PLACE_DEPTH.remove();
            SUPPRESSED_FAMILIES.remove();
            PLACEMENT_SCOPES.remove();
        } else {
            PLACEMENT_SCOPES.get().pop();
            PLACE_DEPTH.set(depth);
        }
    }

    public static void request(Family family) {
        if(PLACE_DEPTH.get() > 0) {
            SUPPRESSED_FAMILIES.get().add(family);
        }
    }

    public static void updatePlacementPosition(BlockPos placementPos) {
        if(PLACE_DEPTH.get() <= 0) {
            return;
        }
        Deque<PlacementScope> placementScopes = PLACEMENT_SCOPES.get();
        PlacementScope previous = placementScopes.pop();
        placementScopes.push(new PlacementScope(placementPos, previous.wallNeighborMatchMask()));
    }

    public static void requestWallNeighborMatches(int wallNeighborMatchMask) {
        if(PLACE_DEPTH.get() <= 0) {
            return;
        }
        Deque<PlacementScope> placementScopes = PLACEMENT_SCOPES.get();
        PlacementScope previous = placementScopes.pop();
        placementScopes.push(new PlacementScope(
                previous.placementPos(),
                wallNeighborMatchMask & 0x3f
        ));
    }

    public static boolean shouldSuppress(Family family, BlockPos pos) {
        if(PLACE_DEPTH.get() <= 0 || !SUPPRESSED_FAMILIES.get().contains(family)) {
            return false;
        }
        for(PlacementScope scope : PLACEMENT_SCOPES.get()) {
            if(family == Family.WALL
                    ? coversWallNeighbors(scope, pos)
                    : family.covers(scope.placementPos(), pos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldSuppressTnt(Level level, BlockPos pos) {
        if(PLACE_DEPTH.get() <= 0 || !SUPPRESSED_FAMILIES.get().contains(Family.TNT)) {
            return false;
        }
        if(shouldSuppress(Family.TNT, pos)) {
            return true;
        }
        for(PlacementScope scope : PLACEMENT_SCOPES.get()) {
            if(hasPoweredConductorRelay(
                    scope.placementPos(),
                    pos,
                    relayPos -> {
                        BlockState relayState = level.getBlockState(relayPos);
                        return relayState.isRedstoneConductor(level, relayPos);
                    },
                    (relayPos, tntToRelay) -> level.getSignal(relayPos, tntToRelay) > 0
            )) {
                return true;
            }
        }
        return false;
    }

    static boolean hasPoweredConductorRelay(
            BlockPos placementPos,
            BlockPos tntPos,
            Predicate<BlockPos> isRedstoneConductor,
            BiPredicate<BlockPos, Direction> hasSignalTowardTnt
    ) {
        for(Direction originToRelay : Direction.values()) {
            BlockPos relayPos = placementPos.relative(originToRelay);
            if(relayPos.equals(tntPos) || !isRedstoneConductor.test(relayPos)) {
                continue;
            }
            for(Direction relayToTnt : Direction.values()) {
                if(!tntPos.equals(relayPos.relative(relayToTnt))) {
                    continue;
                }
                if(hasSignalTowardTnt.test(relayPos, relayToTnt.getOpposite())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean coversWallNeighbors(PlacementScope scope, BlockPos pos) {
        if(pos.equals(scope.placementPos())) {
            return true;
        }
        for(Direction direction : Direction.values()) {
            if(pos.equals(scope.placementPos().relative(direction))) {
                return (scope.wallNeighborMatchMask() & (1 << direction.ordinal())) != 0;
            }
        }
        return false;
    }

    private record PlacementScope(BlockPos placementPos, int wallNeighborMatchMask) {
    }
}
