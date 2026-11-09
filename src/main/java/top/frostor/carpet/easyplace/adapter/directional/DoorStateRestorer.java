package top.frostor.carpet.easyplace.adapter.directional;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class DoorStateRestorer implements EasyPlaceStateRestorer {
    private static final Direction[] VERTICAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof DoorBlock;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        int rawValue = context.rawValue();
        BlockState result = context.placementState()
                .setValue(DoorBlock.FACING, VERTICAL_DIRECTIONS[rawValue & 0x3])
                .setValue(DoorBlock.HINGE, DoorHingeSide.values()[(rawValue & 0x4) >>> 2])
                .setValue(DoorBlock.HALF, (rawValue & 0x10) != 0
                        ? DoubleBlockHalf.UPPER
                        : DoubleBlockHalf.LOWER);
        if(!context.block().equals(Blocks.IRON_DOOR)) {
            result = result.setValue(DoorBlock.OPEN, (rawValue & 0x8) != 0);
        }
        return result;
    }
}
