package top.frostor.carpet.easyplace.adapter.directional;

import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class FenceGateStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof FenceGateBlock;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        BlockState generic = EasyPlaceStateSupport.applyGeneric(
                context.placementState(),
                context.rawValue(),
                context.placeContext()
        );
        return EasyPlaceStateSupport.replaceSlot(
                generic,
                FenceGateBlock.OPEN,
                (context.rawValue() & 0x10) != 0
        );
    }
}
