package top.frostor.carpet.easyplace.adapter.crafter;

import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class CrafterStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof CrafterBlock;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        FrontAndTop[] values = FrontAndTop.values();
        return EasyPlaceStateSupport.replaceSlot(
                context.placementState(),
                BlockStateProperties.ORIENTATION,
                values[Math.floorMod(context.rawValue(), values.length)]
        );
    }
}
