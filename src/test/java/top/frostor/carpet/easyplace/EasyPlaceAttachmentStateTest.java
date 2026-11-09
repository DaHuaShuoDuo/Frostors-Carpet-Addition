package top.frostor.carpet.easyplace;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

public final class EasyPlaceAttachmentStateTest extends EasyPlaceTestSupport {
    private EasyPlaceAttachmentStateTest() {
    }

    public static int run() {
        EasyPlaceAttachmentStateTest test = new EasyPlaceAttachmentStateTest();
        Direction[] directions = {
                Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
        };
        for(int faceIndex = 0; faceIndex < AttachFace.values().length; faceIndex++) {
            for(int directionIndex = 0; directionIndex < directions.length; directionIndex++) {
                int directionData = directions[directionIndex].get3DDataValue();
                int rawValue = (faceIndex << 4) | directionData;
                BlockState result = EasyPlaceStateSync.apply(
                        Blocks.LEVER,
                        Blocks.LEVER.defaultBlockState(),
                        null,
                        rawValue
                );
                test.check(
                        result != null
                                && result.getValue(FaceAttachedHorizontalDirectionalBlock.FACE)
                                == AttachFace.values()[faceIndex]
                                && result.getValue(FaceAttachedHorizontalDirectionalBlock.FACING)
                                == directions[directionIndex],
                        "face-attached buttons and levers must restore every face/facing combination"
                );
            }
        }

        BlockState poweredLever = EasyPlaceStateSync.apply(
                Blocks.LEVER,
                Blocks.LEVER.defaultBlockState(),
                null,
                0x65
        );
        test.check(
                poweredLever != null
                        && poweredLever.getValue(FaceAttachedHorizontalDirectionalBlock.FACE)
                        == AttachFace.CEILING
                        && poweredLever.getValue(FaceAttachedHorizontalDirectionalBlock.FACING)
                        == Direction.EAST
                        && poweredLever.getValue(LeverBlock.POWERED),
                "lever protocol must preserve face, facing, and powered state"
        );
        return test.checks();
    }
}
