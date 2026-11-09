package top.frostor.carpet.easyplace.adapter.attachment;

import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class FaceAttachedStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof FaceAttachedHorizontalDirectionalBlock
                && !(context.block() instanceof RedstoneWallTorchBlock);
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        BlockState generic = EasyPlaceStateSupport.applyGeneric(
                context.placementState(),
                context.rawValue(),
                context.placeContext()
        );
        BlockState result = EasyPlaceStateSupport.replaceSlot(
                generic,
                FaceAttachedHorizontalDirectionalBlock.FACE,
                AttachFace.values()[((context.rawValue() & 0x30) >>> 4) % 3]
        );
        if(context.block() instanceof LeverBlock) {
            return EasyPlaceStateSupport.replaceSlot(
                    result,
                    LeverBlock.POWERED,
                    (context.rawValue() & 0x40) != 0
            );
        }
        return result;
    }
}
