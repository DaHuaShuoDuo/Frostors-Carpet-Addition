package top.frostor.carpet.easyplace;

import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;
import top.frostor.carpet.easyplace.adapter.wall.WallStateRestorer;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;

public final class EasyPlaceWallStateTest extends EasyPlaceTestSupport {
    private EasyPlaceWallStateTest() {
    }

    public static int run() {
        EasyPlaceWallStateTest test = new EasyPlaceWallStateTest();
        test.checkAllSideCombinations();
        test.checkPostState();
        test.checkInvalidRawValues();
        test.checkWaterloggedStateIsPreserved();
        test.checkRegistryOwnership();
        test.checkCrafterAuxiliaryIsolation();
        return test.checks();
    }

    private void checkAllSideCombinations() {
        BlockState placementState = Blocks.STONE_BRICK_WALL.defaultBlockState();
        WallSide[] sides = WallSide.values();
        for(int rawValue = 0; rawValue <= 80; rawValue++) {
            BlockState restored = EasyPlaceStateSync.apply(
                    Blocks.STONE_BRICK_WALL,
                    placementState,
                    null,
                    rawValue,
                    0
            );
            check(restored != null, "every valid wall side combination must restore");
            if(restored == null) continue;
            check(
                    restored.getValue(WallBlock.NORTH) == sides[rawValue % 3],
                    "wall north side must decode from raw value " + rawValue
            );
            check(
                    restored.getValue(WallBlock.EAST) == sides[(rawValue / 3) % 3],
                    "wall east side must decode from raw value " + rawValue
            );
            check(
                    restored.getValue(WallBlock.SOUTH) == sides[(rawValue / 9) % 3],
                    "wall south side must decode from raw value " + rawValue
            );
            check(
                    restored.getValue(WallBlock.WEST) == sides[(rawValue / 27) % 3],
                    "wall west side must decode from raw value " + rawValue
            );
        }
    }

    private void checkPostState() {
        BlockState withoutPost = EasyPlaceStateSync.apply(
                Blocks.STONE_BRICK_WALL,
                Blocks.STONE_BRICK_WALL.defaultBlockState().setValue(WallBlock.UP, true),
                null,
                0,
                0
        );
        check(withoutPost != null && !withoutPost.getValue(WallBlock.UP), "auxiliary bit zero must clear UP");

        BlockState withPost = EasyPlaceStateSync.apply(
                Blocks.STONE_BRICK_WALL,
                Blocks.STONE_BRICK_WALL.defaultBlockState(),
                null,
                0,
                EasyPlaceExtraProtocol.WALL_AUXILIARY_SCHEMA
                        | EasyPlaceExtraProtocol.WALL_UP
        );
        check(withPost != null && withPost.getValue(WallBlock.UP), "auxiliary bit zero must set UP");

        BlockState schemaWithoutPost = EasyPlaceStateSync.apply(
                Blocks.STONE_BRICK_WALL,
                Blocks.STONE_BRICK_WALL.defaultBlockState().setValue(WallBlock.UP, true),
                null,
                0,
                EasyPlaceExtraProtocol.WALL_AUXILIARY_SCHEMA
        );
        check(
                schemaWithoutPost != null && !schemaWithoutPost.getValue(WallBlock.UP),
                "wall schema without the UP bit must clear UP"
        );

        BlockState absentAuxiliary = EasyPlaceStateSync.apply(
                Blocks.STONE_BRICK_WALL,
                Blocks.STONE_BRICK_WALL.defaultBlockState().setValue(WallBlock.UP, true),
                null,
                0,
                -1
        );
        check(
                absentAuxiliary != null && !absentAuxiliary.getValue(WallBlock.UP),
                "an absent Z field must decode as UP=false"
        );
    }

    private void checkInvalidRawValues() {
        for(int rawValue = 81; rawValue <= 0x7f; rawValue++) {
            check(
                    EasyPlaceStateSync.apply(
                            Blocks.STONE_BRICK_WALL,
                            Blocks.STONE_BRICK_WALL.defaultBlockState(),
                            null,
                            rawValue,
                            0
                    ) == null,
                    "wall raw value " + rawValue + " must be rejected"
            );
        }
    }

    private void checkWaterloggedStateIsPreserved() {
        BlockState waterlogged = Blocks.STONE_BRICK_WALL.defaultBlockState()
                .setValue(WallBlock.WATERLOGGED, true);
        BlockState restoredWaterlogged = EasyPlaceStateSync.apply(
                Blocks.STONE_BRICK_WALL,
                waterlogged,
                null,
                80,
                1
        );
        check(
                restoredWaterlogged != null && restoredWaterlogged.getValue(WallBlock.WATERLOGGED),
                "wall restoration must leave placement-state WATERLOGGED unchanged"
        );

        BlockState dry = Blocks.STONE_BRICK_WALL.defaultBlockState()
                .setValue(WallBlock.WATERLOGGED, false);
        BlockState restoredDry = EasyPlaceStateSync.apply(
                Blocks.STONE_BRICK_WALL,
                dry,
                null,
                80,
                1
        );
        check(
                restoredDry != null && !restoredDry.getValue(WallBlock.WATERLOGGED),
                "auxiliary wall state must not force placement-state WATERLOGGED"
        );
    }

    private void checkRegistryOwnership() {
        BlockState state = Blocks.STONE_BRICK_WALL.defaultBlockState();
        check(
                WallStateRestorer.matches(new EasyPlaceRestoreContext(
                        Blocks.STONE_BRICK_WALL,
                        state,
                        null,
                        0,
                        0
                )),
                "the wall matcher must own every WallBlock variant"
        );
        BlockState restored = EasyPlaceStateSync.apply(
                Blocks.COBBLESTONE_WALL,
                Blocks.COBBLESTONE_WALL.defaultBlockState(),
                null,
                53,
                0
        );
        check(
                restored != null && restored.getValue(WallBlock.WEST) != WallSide.NONE,
                "the wall restorer must win over the generic fallback"
        );
    }

    private void checkCrafterAuxiliaryIsolation() {
        BlockState crafter = EasyPlaceStateSync.apply(
                Blocks.CRAFTER,
                Blocks.CRAFTER.defaultBlockState(),
                null,
                1,
                0x1ab
        );
        check(
                crafter != null
                        && crafter.getValue(BlockStateProperties.ORIENTATION) == FrontAndTop.values()[1],
                "crafters must consume raw orientation without decoding wall auxiliary bits"
        );
    }
}
