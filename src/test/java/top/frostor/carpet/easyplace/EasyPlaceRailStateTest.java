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
        test.checkProtectionFlags();
        test.checkRailStateRestoration();
        test.checkActivatorRailStateRestoration();
        return test.checks();
    }

    private void checkProtectionFlags() {
        check(
                EasyPlaceExtraProtocol.requestsRailUpdateSuppression(
                        Blocks.RAIL,
                        EasyPlaceExtraProtocol.RAIL_PROTECTION_FLAG_REGULAR
                ),
                "regular rail protection must be recoverable from raw"
        );
        check(
                EasyPlaceExtraProtocol.requestsRailUpdateSuppression(
                        Blocks.POWERED_RAIL,
                        EasyPlaceExtraProtocol.RAIL_PROTECTION_FLAG_POWERED
                ),
                "powered rail protection must be recoverable from raw"
        );
        check(
                EasyPlaceExtraProtocol.requestsRailUpdateSuppression(
                        Blocks.DETECTOR_RAIL,
                        EasyPlaceExtraProtocol.RAIL_PROTECTION_FLAG_POWERED
                ),
                "detector rail protection must be recoverable from raw"
        );
        check(
                !EasyPlaceExtraProtocol.requestsRailUpdateSuppression(Blocks.RAIL, 0),
                "rail protection must not be inferred without a raw request flag"
        );
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
                poweredRailShape.ordinal()
                        | 0x8
                        | EasyPlaceExtraProtocol.RAIL_POWERED_MASK
        );
        check(
                restoredPoweredRail != null
                        && restoredPoweredRail.getValue(PoweredRailBlock.SHAPE) == poweredRailShape
                        && restoredPoweredRail.getValue(PoweredRailBlock.POWERED),
                "powered rails must preserve shape and redstone state"
        );
    }

    private void checkActivatorRailStateRestoration() {
        check(
                Blocks.ACTIVATOR_RAIL instanceof PoweredRailBlock,
                "activator rails must use the shared PoweredRailBlock implementation"
        );

        RailShape activatorRailShape = RailShape.EAST_WEST;
        BlockState restoredActivatorRail = EasyPlaceStateSync.apply(
                Blocks.ACTIVATOR_RAIL,
                Blocks.ACTIVATOR_RAIL.defaultBlockState(),
                null,
                activatorRailShape.ordinal()
                        | 0x8
                        | EasyPlaceExtraProtocol.RAIL_POWERED_MASK
        );
        check(
                restoredActivatorRail != null
                        && restoredActivatorRail.getValue(PoweredRailBlock.SHAPE)
                        == activatorRailShape
                        && restoredActivatorRail.getValue(PoweredRailBlock.POWERED),
                "activator rails must preserve shape and redstone state"
        );
    }
}
