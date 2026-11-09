package top.frostor.carpet.easyplace.adapter.copper;

import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.state.BlockState;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class CopperBulbStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof CopperBulbBlock;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        int rawValue = context.rawValue();
        return context.placementState()
                .setValue(CopperBulbBlock.LIT, (rawValue & 0x1) != 0)
                .setValue(CopperBulbBlock.POWERED, (rawValue & 0x2) != 0);
    }
}
