package top.frostor.carpet.easyplace.adapter.container;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;
import org.jetbrains.annotations.Nullable;

public final class BedStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof BedBlock;
    }

    @Override
    public @Nullable BlockState restore(EasyPlaceRestoreContext context) {
        BlockPlaceContext placeContext = context.placeContext();
        if(placeContext == null) return null;
        Direction facing = EasyPlaceStateSupport.directionFromProtocol(
                context.placementState(),
                context.rawValue(),
                placeContext
        );
        BlockPos headPos = placeContext.getClickedPos().relative(facing);
        if(!placeContext.getLevel().getBlockState(headPos).canBeReplaced(placeContext)) return null;
        return context.placementState().setValue(BedBlock.FACING, facing);
    }
}
