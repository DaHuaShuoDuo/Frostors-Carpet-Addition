package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class MultiFaceBlockRules {
    private static final Direction[] DIRECTIONS = {
            Direction.DOWN, Direction.UP, Direction.NORTH,
            Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final Direction[] VINE_DIRECTIONS = Arrays.stream(DIRECTIONS)
            .filter(direction -> direction != Direction.DOWN)
            .toArray(Direction[]::new);

    private MultiFaceBlockRules() {
    }

    public static boolean isMultiFaceBlock(Block block) {
        return block instanceof VineBlock || block instanceof MultifaceBlock;
    }

    public static @Nullable BlockState applyPlacementState(
            Block block,
            BlockState state,
            BlockPlaceContext context,
            int rawValue
    ) {
        Direction direction = wireDirection(block, rawValue & 0x7);
        if(direction == null) return null;

        BlockPos targetPos = context.getClickedPos();
        BlockState currentState = context.getLevel().getBlockState(targetPos);
        if(block instanceof MultifaceBlock multifaceBlock) {
            return multifaceBlock.getStateForPlacement(
                    currentState,
                    context.getLevel(),
                    targetPos,
                    direction
            );
        }
        if(block instanceof VineBlock) {
            if(!canAttach(block, context.getLevel(), targetPos, direction)) {
                return null;
            }
            return applyVineFace(block, currentState, direction);
        }
        return null;
    }

    static @Nullable BlockState applyVineFace(
            Block block,
            BlockState currentState,
            Direction direction
    ) {
        Property<Boolean> property = VineBlock.getPropertyForFace(direction);
        BlockState base = currentState.is(block) ? currentState : block.defaultBlockState();
        if(!base.hasProperty(property) || base.getValue(property)) {
            return null;
        }
        return base.setValue(property, true);
    }

    public static boolean canAttach(
            Block block,
            BlockGetter level,
            BlockPos targetPos,
            Direction direction
    ) {
        if(block instanceof MultifaceBlock) {
            return MultifaceBlock.canAttachTo(level, targetPos, direction);
        }
        if(direction == Direction.DOWN) return false;

        BlockPos supportPos = targetPos.relative(direction);
        if(VineBlock.isAcceptableNeighbour(level, supportPos, direction)) return true;
        if(direction.getAxis() == Direction.Axis.Y) return false;

        BlockState above = level.getBlockState(targetPos.above());
        return above.is(Blocks.VINE) && above.getValue(VineBlock.getPropertyForFace(direction));
    }

    public static @Nullable Direction wireDirection(Block block, int index) {
        Direction[] directions = block instanceof VineBlock ? VINE_DIRECTIONS : DIRECTIONS;
        int face = index & 0x7;
        return face < directions.length ? directions[face] : null;
    }
}
