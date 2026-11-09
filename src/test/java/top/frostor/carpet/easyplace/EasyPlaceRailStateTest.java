package top.frostor.carpet.easyplace;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

public final class EasyPlaceRailStateTest extends EasyPlaceTestSupport {
    private EasyPlaceRailStateTest() {
    }

    public static int run() {
        EasyPlaceRailStateTest test = new EasyPlaceRailStateTest();
        test.checkRailStateRestoration();
        return test.checks();
    }

    private void checkRailStateRestoration() {
        RailShape railShape = RailShape.SOUTH_EAST;
        BlockState restoredRail = EasyPlaceStateSync.apply(
                Blocks.RAIL,
                Blocks.RAIL.defaultBlockState(),
                null,
                railShape.ordinal()
        );
        check(
                restoredRail != null
                        && restoredRail.getValue(RailBlock.SHAPE) == railShape,
                "normal rails must restore the projected shape through the rail adapter"
        );

        RailShape poweredRailShape = RailShape.NORTH_SOUTH;
        BlockState restoredPoweredRail = EasyPlaceStateSync.apply(
                Blocks.POWERED_RAIL,
                Blocks.POWERED_RAIL.defaultBlockState(),
                null,
                poweredRailShape.ordinal() | 0x8
        );
        check(
                restoredPoweredRail != null
                        && restoredPoweredRail.getValue(PoweredRailBlock.SHAPE) == poweredRailShape,
                "powered rails must preserve shape while ignoring the no-update wire bit"
        );
    }
}
