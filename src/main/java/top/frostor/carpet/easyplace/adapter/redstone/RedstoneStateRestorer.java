package top.frostor.carpet.easyplace.adapter.redstone;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class RedstoneStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof RedstoneWallTorchBlock
                || context.block() instanceof RedstoneTorchBlock
                || context.block() == Blocks.REDSTONE_WIRE;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        int rawValue = context.rawValue();
        if(context.block() instanceof RedstoneWallTorchBlock) {
            return EasyPlaceStateSupport.replaceSlot(
                    context.placementState(),
                    RedstoneWallTorchBlock.LIT,
                    (rawValue & 1) != 0
            );
        }
        if(context.block() instanceof RedstoneTorchBlock) {
            return EasyPlaceStateSupport.replaceSlot(
                    context.placementState(),
                    RedstoneTorchBlock.LIT,
                    (rawValue & 1) != 0
            );
        }
        if((rawValue & 1) == 0) return context.placementState();
        return context.placementState()
                .setValue(BlockStateProperties.NORTH_REDSTONE, RedstoneSide.NONE)
                .setValue(BlockStateProperties.EAST_REDSTONE, RedstoneSide.NONE)
                .setValue(BlockStateProperties.SOUTH_REDSTONE, RedstoneSide.NONE)
                .setValue(BlockStateProperties.WEST_REDSTONE, RedstoneSide.NONE);
    }
}
