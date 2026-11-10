package top.frostor.carpet.easyplace.adapter.rail;

import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import top.frostor.carpet.easyplace.EasyPlaceExtraProtocol;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class DetectorRailStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof DetectorRailBlock;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        BlockState state = EasyPlaceStateSupport.replaceSlot(
                context.placementState(),
                DetectorRailBlock.SHAPE,
                RailShape.values()[(context.rawValue() & 0x7) % 6]
        );
        if(state == null) return null;
        return state.setValue(
                DetectorRailBlock.POWERED,
                (context.rawValue() & EasyPlaceExtraProtocol.DETECTOR_RAIL_POWERED_MASK) != 0
        );
    }
}
