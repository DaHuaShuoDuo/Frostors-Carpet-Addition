package top.frostor.carpet.easyplace.adapter.daylight;

import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.state.BlockState;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class DaylightDetectorStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof DaylightDetectorBlock;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        return EasyPlaceStateSupport.replaceSlot(
                context.placementState(),
                DaylightDetectorBlock.INVERTED,
                (context.rawValue() & 1) != 0
        );
    }
}
