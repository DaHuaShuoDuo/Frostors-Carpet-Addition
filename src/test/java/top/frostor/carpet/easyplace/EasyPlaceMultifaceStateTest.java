package top.frostor.carpet.easyplace;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class EasyPlaceMultifaceStateTest extends EasyPlaceTestSupport {
    private EasyPlaceMultifaceStateTest() {
    }

    public static int run() {
        EasyPlaceMultifaceStateTest test = new EasyPlaceMultifaceStateTest();
        Direction[] multifaceDirections = {
                Direction.DOWN, Direction.UP, Direction.NORTH,
                Direction.SOUTH, Direction.WEST, Direction.EAST
        };
        for(int index = 0; index < multifaceDirections.length; index++) {
            test.check(
                    MultiFaceBlockRules.wireDirection(Blocks.GLOW_LICHEN, index)
                            == multifaceDirections[index],
                    "multiface protocol index " + index + " must match the EarlyCompat direction order"
            );
        }
        test.check(
                MultiFaceBlockRules.wireDirection(Blocks.GLOW_LICHEN, 6) == null,
                "multiface index 6 must be rejected"
        );
        test.check(
                MultiFaceBlockRules.wireDirection(Blocks.GLOW_LICHEN, 7) == null,
                "multiface index 7 must be rejected"
        );

        Direction[] vineDirections = {
                Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
        };
        for(int index = 0; index < vineDirections.length; index++) {
            test.check(
                    MultiFaceBlockRules.wireDirection(Blocks.VINE, index)
                            == vineDirections[index],
                    "vine protocol index " + index + " must match the EarlyCompat direction order"
            );
        }
        test.check(
                MultiFaceBlockRules.wireDirection(Blocks.VINE, 5) == null,
                "vine index 5 must be rejected"
        );
        test.check(
                MultiFaceBlockRules.wireDirection(Blocks.VINE, 6) == null,
                "vine index 6 must be rejected"
        );
        test.check(
                MultiFaceBlockRules.wireDirection(Blocks.VINE, 7) == null,
                "vine index 7 must be rejected"
        );
        BlockState singleVineFace = MultiFaceBlockRules.applyVineFace(
                Blocks.VINE,
                Blocks.AIR.defaultBlockState(),
                Direction.UP
        );
        test.check(
                singleVineFace != null
                        && singleVineFace.getValue(VineBlock.UP),
                "server vine increment must ignore a pre-expanded placement state"
        );
        BlockState vineSouth = Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true);
        BlockState vineSouthUp = MultiFaceBlockRules.applyVineFace(Blocks.VINE, vineSouth, Direction.UP);
        test.check(
                vineSouthUp != null
                        && vineSouthUp.getValue(VineBlock.SOUTH)
                        && vineSouthUp.getValue(VineBlock.UP),
                "server vine increment must preserve the current face set"
        );

        return test.checks();
    }
}
