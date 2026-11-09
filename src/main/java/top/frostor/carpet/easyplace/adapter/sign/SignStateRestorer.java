package top.frostor.carpet.easyplace.adapter.sign;

import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.HangingSignBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class SignStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return (context.block() instanceof AbstractBannerBlock
                || context.block() instanceof SignBlock
                || context.block() instanceof SkullBlock)
                && !(context.block() instanceof HangingSignBlock);
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        BlockState state = context.placementState();
        int rawValue = context.rawValue();
        if(context.block() instanceof AbstractBannerBlock) {
            return state.hasProperty(BlockStateProperties.ROTATION_16)
                    ? EasyPlaceStateSupport.replaceSlot(
                    state,
                    BlockStateProperties.ROTATION_16,
                    rawValue & 0xf
            )
                    : state;
        }
        if(context.block() instanceof SignBlock) {
            return state.hasProperty(BlockStateProperties.ROTATION_16)
                    ? EasyPlaceStateSupport.replaceSlot(
                    state,
                    BlockStateProperties.ROTATION_16,
                    rawValue & 0xf
            )
                    : state;
        }
        return state.hasProperty(SkullBlock.ROTATION)
                ? EasyPlaceStateSupport.replaceSlot(state, SkullBlock.ROTATION, rawValue & 0xf)
                : state;
    }
}
