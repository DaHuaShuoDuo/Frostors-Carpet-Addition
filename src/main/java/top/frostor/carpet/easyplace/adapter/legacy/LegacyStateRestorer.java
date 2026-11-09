package top.frostor.carpet.easyplace.adapter.legacy;

import net.minecraft.world.level.block.state.BlockState;
import top.frostor.carpet.easyplace.LegacyEasyPlaceStateSync;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class LegacyStateRestorer implements EasyPlaceStateRestorer {
    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        return LegacyEasyPlaceStateSync.apply(
                context.block(),
                context.placementState(),
                context.placeContext(),
                context.rawValue()
        );
    }
}
