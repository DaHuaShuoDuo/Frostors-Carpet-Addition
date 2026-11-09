package top.frostor.carpet.easyplace.adapter.sign;

import net.minecraft.world.level.block.HangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class HangingSignStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof HangingSignBlock;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        BlockState result = context.placementState();
        if(result.hasProperty(BlockStateProperties.ROTATION_16)) {
            result = EasyPlaceStateSupport.replaceSlot(
                    result,
                    BlockStateProperties.ROTATION_16,
                    context.rawValue() & 0xf
            );
        }
        if(result.hasProperty(BlockStateProperties.ATTACHED)) {
            result = result.setValue(
                    BlockStateProperties.ATTACHED,
                    (context.rawValue() & 0x10) != 0
            );
        }
        return result;
    }
}
