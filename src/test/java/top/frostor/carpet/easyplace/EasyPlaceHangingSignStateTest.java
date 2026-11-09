package top.frostor.carpet.easyplace;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class EasyPlaceHangingSignStateTest extends EasyPlaceTestSupport {
    private EasyPlaceHangingSignStateTest() {
    }

    public static int run() {
        EasyPlaceHangingSignStateTest test = new EasyPlaceHangingSignStateTest();
        BlockState wallHangingSign = Blocks.OAK_WALL_HANGING_SIGN.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        BlockState wallResult = EasyPlaceStateSync.apply(
                Blocks.OAK_WALL_HANGING_SIGN,
                wallHangingSign,
                null,
                0x10
        );
        test.check(
                wallResult != null
                        && wallResult.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST,
                "wall hanging signs must preserve facing without ceiling-only properties"
        );

        BlockState encodedWallResult = EasyPlaceStateSync.applyStandingOrWall(
                Blocks.OAK_HANGING_SIGN,
                Blocks.OAK_WALL_HANGING_SIGN,
                wallHangingSign,
                null,
                0x7
        );
        test.check(
                encodedWallResult != null
                        && encodedWallResult.getValue(BlockStateProperties.HORIZONTAL_FACING)
                        == Direction.EAST,
                "wall hanging signs must restore encoded facing without attachment support"
        );

        BlockState attachedCeilingHangingSign = Blocks.OAK_HANGING_SIGN.defaultBlockState()
                .setValue(BlockStateProperties.ROTATION_16, 11)
                .setValue(BlockStateProperties.ATTACHED, true);
        BlockState attachedCeilingResult = EasyPlaceStateSync.applyStandingOrWall(
                Blocks.OAK_HANGING_SIGN,
                Blocks.OAK_WALL_HANGING_SIGN,
                attachedCeilingHangingSign,
                null,
                0x5c
        );
        test.check(
                attachedCeilingResult != null
                        && attachedCeilingResult.getValue(BlockStateProperties.ROTATION_16) == 11
                        && attachedCeilingResult.getValue(BlockStateProperties.ATTACHED),
                "attached ceiling hanging signs must restore encoded rotation and attachment"
        );

        BlockState ceilingHangingSign = Blocks.OAK_HANGING_SIGN.defaultBlockState()
                .setValue(BlockStateProperties.ROTATION_16, 2)
                .setValue(BlockStateProperties.ATTACHED, false);
        BlockState ceilingResult = EasyPlaceStateSync.apply(
                Blocks.OAK_HANGING_SIGN,
                ceilingHangingSign,
                null,
                0x13
        );
        test.check(
                ceilingResult != null
                        && ceilingResult.getValue(BlockStateProperties.ROTATION_16) == 3
                        && ceilingResult.getValue(BlockStateProperties.ATTACHED),
                "ceiling hanging signs must preserve rotation and attachment"
        );

        return test.checks();
    }
}
