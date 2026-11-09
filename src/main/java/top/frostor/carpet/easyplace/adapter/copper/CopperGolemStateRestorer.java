package top.frostor.carpet.easyplace.adapter.copper;

import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.state.BlockState;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class CopperGolemStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof CopperGolemStatueBlock;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        BlockState generic = EasyPlaceStateSupport.applyGeneric(
                context.placementState(),
                context.rawValue(),
                context.placeContext()
        );
        CopperGolemStatueBlock.Pose[] poses = CopperGolemStatueBlock.Pose.values();
        return EasyPlaceStateSupport.replaceSlot(
                generic,
                CopperGolemStatueBlock.POSE,
                poses[(context.rawValue() >>> 4) & 0x3]
        );
    }
}
