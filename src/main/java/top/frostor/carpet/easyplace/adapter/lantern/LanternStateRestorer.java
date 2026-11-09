package top.frostor.carpet.easyplace.adapter.lantern;

import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class LanternStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof LanternBlock;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        return EasyPlaceStateSupport.replaceSlot(
                context.placementState(),
                LanternBlock.HANGING,
                (context.rawValue() & 1) != 0
        );
    }
}
