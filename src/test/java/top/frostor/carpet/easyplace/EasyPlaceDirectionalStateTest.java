package top.frostor.carpet.easyplace;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class EasyPlaceDirectionalStateTest extends EasyPlaceTestSupport {
    private EasyPlaceDirectionalStateTest() {
    }

    public static int run() {
        EasyPlaceDirectionalStateTest test = new EasyPlaceDirectionalStateTest();
        BlockState gate = Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(FenceGateBlock.FACING, Direction.SOUTH)
                .setValue(FenceGateBlock.OPEN, false);
        BlockState opened = EasyPlaceStateSync.apply(
                Blocks.OAK_FENCE_GATE,
                gate,
                null,
                0x12
        );
        test.check(
                opened != null
                        && opened.getValue(FenceGateBlock.FACING) == Direction.NORTH
                        && opened.getValue(FenceGateBlock.OPEN),
                "fence gate protocol must restore facing and open state"
        );

        BlockState nullContextResult = EasyPlaceStateSync.apply(
                Blocks.OAK_FENCE_GATE,
                gate,
                null,
                0x16
        );
        test.check(
                nullContextResult != null
                        && nullContextResult.getValue(FenceGateBlock.FACING) == Direction.SOUTH
                        && nullContextResult.getValue(FenceGateBlock.OPEN),
                "fence gate interaction decoding must not require a placement context for protocol index six"
        );
        return test.checks();
    }
}
