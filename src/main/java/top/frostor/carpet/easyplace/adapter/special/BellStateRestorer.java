package top.frostor.carpet.easyplace.adapter.special;

import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class BellStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof BellBlock;
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
                BellBlock.ATTACHMENT,
                BellAttachType.values()[(context.rawValue() & 0x30) >>> 4]
        );
    }
}
