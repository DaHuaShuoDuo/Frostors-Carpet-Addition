package top.frostor.carpet.easyplace;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class EasyPlacePistonStateTest extends EasyPlaceTestSupport {
    private EasyPlacePistonStateTest() {
    }

    public static int run() {
        EasyPlacePistonStateTest test = new EasyPlacePistonStateTest();
        test.checkPistonStateSemantics();
        test.checkProtectionMaskLayout();
        return test.checks();
    }

    private void checkPistonStateSemantics() {
        for(Block block : new Block[]{
                Blocks.PISTON,
                Blocks.STICKY_PISTON
        }) {
            BlockState placementState = block.defaultBlockState()
                    .setValue(PistonBaseBlock.FACING, Direction.NORTH)
                    .setValue(PistonBaseBlock.EXTENDED, false);
            BlockState restored = EasyPlaceStateSync.apply(
                    block,
                    placementState,
                    null,
                    Direction.EAST.get3DDataValue()
            );
            check(
                    restored != null
                            && restored.getValue(PistonBaseBlock.FACING) == Direction.EAST,
                    "piston generic state handling must restore the projected facing"
            );
            check(
                    restored != null
                            && !restored.getValue(PistonBaseBlock.EXTENDED),
                    "piston state handling must not restore or force EXTENDED"
            );
        }
    }

    private void checkProtectionMaskLayout() {
        int allNeighbors = 0;
        for(Direction direction : Direction.values()) {
            allNeighbors |= 1 << (direction.ordinal() + 1);
        }
        check(
                EasyPlaceExtraProtocol.PISTON_TARGET_MATCH == 1,
                "piston target match must use mask bit zero"
        );
        check(
                allNeighbors == EasyPlaceExtraProtocol.PISTON_NEIGHBOR_MATCH_MASK,
                "piston neighbors must use the six bits following the target bit"
        );
        check(
                EasyPlaceExtraProtocol.pistonNeighborMatchMask(
                        EasyPlaceExtraProtocol.PISTON_PROTECTION_MASK
                ) == 0x3f,
                "piston neighbor mask decoding must exclude the target bit"
        );
        check(
                EasyPlaceExtraProtocol.pistonNeighborMatchMask(-1) == 0,
                "an invalid piston mask must not mark neighbors as matching"
        );
        check(
                EasyPlaceExtraProtocol.PISTON_PROTECTION_MASK == 0xfff,
                "piston protection must reserve target, face-neighbor, and QC bits"
        );
        check(
                EasyPlaceExtraProtocol.pistonQcTargetOffsets().length == 5,
                "the QC second-hop shell must contain only the five positions around the block above"
        );
        int qcMask = 0;
        for(net.minecraft.core.BlockPos offset :
                EasyPlaceExtraProtocol.pistonQcTargetOffsets()) {
            int bit = EasyPlaceExtraProtocol.pistonQcTargetMatchBit(
                    net.minecraft.core.BlockPos.ZERO,
                    net.minecraft.core.BlockPos.ZERO.offset(
                            offset.getX(),
                            offset.getY(),
                            offset.getZ()
                    )
            );
            check(bit != 0, "every QC target offset must map to a mask bit");
            check((qcMask & bit) == 0, "QC target offsets must use unique mask bits");
            qcMask |= bit;
        }
        check(
                qcMask == EasyPlaceExtraProtocol.PISTON_QC_TARGET_MATCH_MASK,
                "the five QC target bits must fill the complete QC mask"
        );
        check(
                EasyPlaceExtraProtocol.pistonQcTargetMatchBit(
                        net.minecraft.core.BlockPos.ZERO,
                        new net.minecraft.core.BlockPos(0, -2, 0)
                ) != 0,
                "a piston two blocks below the placed source must map to a QC bit"
        );
        check(
                EasyPlaceExtraProtocol.pistonQcTargetMatchBit(
                        net.minecraft.core.BlockPos.ZERO,
                        new net.minecraft.core.BlockPos(1, -1, 0)
                ) != 0,
                "a diagonal QC candidate must map to a relay bit"
        );
        check(
                EasyPlaceExtraProtocol.pistonQcTargetMatchBit(
                        net.minecraft.core.BlockPos.ZERO,
                        new net.minecraft.core.BlockPos(2, 0, 0)
                ) == 0,
                "horizontal second-hop positions unrelated to the block above must not map"
        );
    }
}
