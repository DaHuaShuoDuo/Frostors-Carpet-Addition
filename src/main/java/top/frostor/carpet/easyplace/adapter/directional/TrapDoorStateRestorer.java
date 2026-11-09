package top.frostor.carpet.easyplace.adapter.directional;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class TrapDoorStateRestorer implements EasyPlaceStateRestorer {
    private static final Direction[] VERTICAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof TrapDoorBlock;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        int rawValue = context.rawValue();
        BlockState result = context.placementState()
                .setValue(TrapDoorBlock.FACING, VERTICAL_DIRECTIONS[rawValue & 0x3])
                .setValue(TrapDoorBlock.HALF, (rawValue & 0x4) != 0 ? Half.TOP : Half.BOTTOM);
        if(!context.block().equals(Blocks.IRON_TRAPDOOR)) {
            result = result.setValue(TrapDoorBlock.OPEN, (rawValue & 0x8) != 0);
        }
        return result;
    }
}
