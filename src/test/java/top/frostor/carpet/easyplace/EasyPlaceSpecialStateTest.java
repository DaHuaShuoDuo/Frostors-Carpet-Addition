package top.frostor.carpet.easyplace;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.RedstoneSide;

public final class EasyPlaceSpecialStateTest extends EasyPlaceTestSupport {
    private EasyPlaceSpecialStateTest() {
    }

    public static int run() {
        EasyPlaceSpecialStateTest test = new EasyPlaceSpecialStateTest();
        test.checkStateFamilies();
        return test.checks();
    }

    private void checkStateFamilies() {
        BlockState bell = EasyPlaceStateSync.apply(
                Blocks.BELL,
                Blocks.BELL.defaultBlockState(),
                null,
                0x12
        );
        check(
                bell != null
                        && bell.getValue(BellBlock.ATTACHMENT) == BellAttachType.values()[1]
                        && bell.getValue(BellBlock.FACING) == Direction.NORTH,
                "bells must restore facing and attachment"
        );

        BlockState campfire = EasyPlaceStateSync.apply(
                Blocks.CAMPFIRE,
                Blocks.CAMPFIRE.defaultBlockState(),
                null,
                0x10
        );
        check(campfire != null && campfire.getValue(CampfireBlock.LIT), "campfires must restore lit state");

        Block copperBulbBlock = Blocks.COPPER_BULB.asList().getFirst();
        BlockState copperBulb = EasyPlaceStateSync.apply(
                copperBulbBlock,
                copperBulbBlock.defaultBlockState(),
                null,
                0x3
        );
        check(
                copperBulb != null
                        && copperBulb.getValue(CopperBulbBlock.LIT)
                        && copperBulb.getValue(CopperBulbBlock.POWERED),
                "copper bulbs must restore lit and powered state"
        );

        Block copperGolemBlock = Blocks.COPPER_GOLEM_STATUE.asList().getFirst();
        CopperGolemStatueBlock.Pose pose = CopperGolemStatueBlock.Pose.values()[2];
        BlockState copperGolem = EasyPlaceStateSync.apply(
                copperGolemBlock,
                copperGolemBlock.defaultBlockState(),
                null,
                pose.ordinal() << 4
        );
        check(
                copperGolem != null
                        && copperGolem.getValue(BlockStateProperties.COPPER_GOLEM_POSE) == pose,
                "copper golem statues must restore pose"
        );

        BlockState crafter = EasyPlaceStateSync.apply(
                Blocks.CRAFTER,
                Blocks.CRAFTER.defaultBlockState(),
                null,
                1
        );
        check(
                crafter != null
                        && crafter.getValue(BlockStateProperties.ORIENTATION)
                        == net.minecraft.core.FrontAndTop.values()[1],
                "crafters must restore orientation"
        );

        BlockState daylight = EasyPlaceStateSync.apply(
                Blocks.DAYLIGHT_DETECTOR,
                Blocks.DAYLIGHT_DETECTOR.defaultBlockState(),
                null,
                1
        );
        check(
                daylight != null && daylight.getValue(BlockStateProperties.INVERTED),
                "daylight detectors must restore inverted state"
        );

        RailShape detectorShape = RailShape.values()[3];
        BlockState detectorRail = EasyPlaceStateSync.apply(
                Blocks.DETECTOR_RAIL,
                Blocks.DETECTOR_RAIL.defaultBlockState(),
                null,
                detectorShape.ordinal()
        );
        check(
                detectorRail != null
                        && detectorRail.getValue(DetectorRailBlock.SHAPE) == detectorShape
                        && !detectorRail.getValue(DetectorRailBlock.POWERED),
                "detector rails must restore shape without inventing powered state"
        );
        BlockState poweredDetectorRail = EasyPlaceStateSync.apply(
                Blocks.DETECTOR_RAIL,
                Blocks.DETECTOR_RAIL.defaultBlockState(),
                null,
                detectorShape.ordinal() | EasyPlaceExtraProtocol.RAIL_POWERED_MASK
        );
        check(
                poweredDetectorRail != null
                        && poweredDetectorRail.getValue(DetectorRailBlock.SHAPE) == detectorShape
                        && poweredDetectorRail.getValue(DetectorRailBlock.POWERED),
                "powered detector rails must restore powered state and shape"
        );

        BlockState lantern = EasyPlaceStateSync.apply(
                Blocks.LANTERN,
                Blocks.LANTERN.defaultBlockState(),
                null,
                1
        );
        check(lantern != null && lantern.getValue(LanternBlock.HANGING), "lanterns must restore hanging state");

        BlockState wallTorch = EasyPlaceStateSync.apply(
                Blocks.REDSTONE_WALL_TORCH,
                Blocks.REDSTONE_WALL_TORCH.defaultBlockState(),
                null,
                1
        );
        check(
                wallTorch != null && wallTorch.getValue(RedstoneWallTorchBlock.LIT),
                "redstone wall torches must restore lit state"
        );

        BlockState wire = EasyPlaceStateSync.apply(
                Blocks.REDSTONE_WIRE,
                Blocks.REDSTONE_WIRE.defaultBlockState(),
                null,
                1
        );
        check(
                wire != null
                        && wire.getValue(BlockStateProperties.NORTH_REDSTONE) == RedstoneSide.NONE
                        && wire.getValue(BlockStateProperties.EAST_REDSTONE) == RedstoneSide.NONE
                        && wire.getValue(BlockStateProperties.SOUTH_REDSTONE) == RedstoneSide.NONE
                        && wire.getValue(BlockStateProperties.WEST_REDSTONE) == RedstoneSide.NONE,
                "redstone wire must restore the disconnected state"
        );
    }
}
