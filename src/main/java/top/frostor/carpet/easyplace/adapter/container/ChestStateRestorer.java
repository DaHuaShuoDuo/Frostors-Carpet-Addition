package top.frostor.carpet.easyplace.adapter.container;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import top.frostor.carpet.easyplace.EasyPlaceChestSync;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;
import org.jetbrains.annotations.Nullable;

public final class ChestStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof ChestBlock;
    }

    @Override
    public @Nullable BlockState restore(EasyPlaceRestoreContext context) {
        BlockPlaceContext placeContext = context.placeContext();
        if(placeContext == null) return null;
        BlockState directed = EasyPlaceStateSupport.applyGeneric(
                context.placementState(),
                context.rawValue(),
                placeContext
        );
        Direction facing = directed.getValue(ChestBlock.FACING);
        return EasyPlaceChestSync.applyFacing(
                placeContext.getLevel(),
                placeContext.getClickedPos(),
                directed,
                facing,
                context.rawValue()
        );
    }
}
