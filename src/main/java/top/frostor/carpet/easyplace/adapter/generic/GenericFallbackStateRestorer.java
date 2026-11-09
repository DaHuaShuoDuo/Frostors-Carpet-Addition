package top.frostor.carpet.easyplace.adapter.generic;

import net.minecraft.world.level.block.state.BlockState;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class GenericFallbackStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return true;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        return EasyPlaceStateSupport.applyGeneric(
                context.placementState(),
                context.rawValue(),
                context.placeContext()
        );
    }
}
