package top.frostor.carpet.easyplace.adapter.multiface;

import net.minecraft.world.level.block.state.BlockState;
import top.frostor.carpet.easyplace.MultiFaceBlockRules;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class MultiFaceStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return MultiFaceBlockRules.isMultiFaceBlock(context.block());
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        return MultiFaceBlockRules.applyPlacementState(
                context.block(),
                context.placementState(),
                context.placeContext(),
                context.rawValue()
        );
    }
}
