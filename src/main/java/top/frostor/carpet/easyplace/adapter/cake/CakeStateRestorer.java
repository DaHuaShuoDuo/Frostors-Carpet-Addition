package top.frostor.carpet.easyplace.adapter.cake;

import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class CakeStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof CakeBlock;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        return EasyPlaceStateSupport.replaceSlot(
                context.placementState(),
                BlockStateProperties.BITES,
                context.rawValue() & 0x7
        );
    }
}
