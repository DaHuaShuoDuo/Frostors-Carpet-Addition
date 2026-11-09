package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Set;

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
        WALL(EnumSet.allOf(Direction.class));

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
    private static final ThreadLocal<Deque<BlockPos>> PLACEMENT_POSITIONS =
            ThreadLocal.withInitial(ArrayDeque::new);

    private EasyPlaceUpdateSuppressionGuard() {
    }

    public static void beginPlacement(BlockPos placementPos) {
        int depth = PLACE_DEPTH.get();
        if(depth == 0) {
            SUPPRESSED_FAMILIES.remove();
            PLACEMENT_POSITIONS.remove();
        }
        PLACEMENT_POSITIONS.get().push(placementPos);
        PLACE_DEPTH.set(depth + 1);
    }

    public static void endPlacement() {
        int depth = PLACE_DEPTH.get() - 1;
        if(depth <= 0) {
            PLACE_DEPTH.remove();
            SUPPRESSED_FAMILIES.remove();
            PLACEMENT_POSITIONS.remove();
        } else {
            PLACEMENT_POSITIONS.get().pop();
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
        Deque<BlockPos> placementPositions = PLACEMENT_POSITIONS.get();
        placementPositions.pop();
        placementPositions.push(placementPos);
    }

    public static boolean shouldSuppress(Family family, BlockPos pos) {
        if(PLACE_DEPTH.get() <= 0 || !SUPPRESSED_FAMILIES.get().contains(family)) {
            return false;
        }
        for(BlockPos placementPos : PLACEMENT_POSITIONS.get()) {
            if(family.covers(placementPos, pos)) {
                return true;
            }
        }
        return false;
    }
}
