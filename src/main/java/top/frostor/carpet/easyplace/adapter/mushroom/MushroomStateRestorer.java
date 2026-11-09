package top.frostor.carpet.easyplace.adapter.mushroom;

import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class MushroomStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof HugeMushroomBlock;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        int rawValue = context.rawValue();
        return context.placementState()
                .setValue(BlockStateProperties.NORTH, (rawValue & 0x01) != 0)
                .setValue(BlockStateProperties.EAST, (rawValue & 0x02) != 0)
                .setValue(BlockStateProperties.SOUTH, (rawValue & 0x04) != 0)
                .setValue(BlockStateProperties.WEST, (rawValue & 0x08) != 0)
                .setValue(BlockStateProperties.UP, (rawValue & 0x10) != 0)
                .setValue(BlockStateProperties.DOWN, (rawValue & 0x20) != 0);
    }
}
