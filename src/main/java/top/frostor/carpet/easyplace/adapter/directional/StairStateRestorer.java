package top.frostor.carpet.easyplace.adapter.directional;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class StairStateRestorer implements EasyPlaceStateRestorer {
    private static final Direction[] VERTICAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof StairBlock;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        int rawValue = context.rawValue();
        return context.placementState()
                .setValue(StairBlock.FACING, VERTICAL_DIRECTIONS[rawValue & 0x3])
                .setValue(StairBlock.HALF, (rawValue & 0x4) != 0 ? Half.TOP : Half.BOTTOM)
                .setValue(StairBlock.SHAPE, StairsShape.values()[(rawValue & 0x38) >>> 3]);
    }
}
