package top.frostor.carpet.easyplace;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class EasyPlaceMushroomStateTest extends EasyPlaceTestSupport {
    private EasyPlaceMushroomStateTest() {
    }

    public static int run() {
        EasyPlaceMushroomStateTest test = new EasyPlaceMushroomStateTest();
        Block[] hugeMushroomBlocks = {Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK};
        for(Block block : hugeMushroomBlocks) {
            test.check(block instanceof HugeMushroomBlock, block + " must use HugeMushroomBlock");
            for(int mask = 0; mask < 64; mask++) {
                BlockState applied = EasyPlaceStateSync.apply(
                        block,
                        block.defaultBlockState(),
                        null,
                        mask
                );
                test.check(applied != null, "huge mushroom protocol state must apply");
                test.check(
                        applied.getValue(BlockStateProperties.NORTH) == ((mask & 0x01) != 0)
                                && applied.getValue(BlockStateProperties.EAST) == ((mask & 0x02) != 0)
                                && applied.getValue(BlockStateProperties.SOUTH) == ((mask & 0x04) != 0)
                                && applied.getValue(BlockStateProperties.WEST) == ((mask & 0x08) != 0)
                                && applied.getValue(BlockStateProperties.UP) == ((mask & 0x10) != 0)
                                && applied.getValue(BlockStateProperties.DOWN) == ((mask & 0x20) != 0),
                        "huge mushroom six-face state must apply"
                );
            }
        }

        BlockState smallMushroom = Blocks.BROWN_MUSHROOM.defaultBlockState();
        test.check(
                EasyPlaceStateSync.apply(Blocks.BROWN_MUSHROOM, smallMushroom, null, 0) == smallMushroom,
                "small mushrooms must retain their placement state"
        );

        return test.checks();
    }
}
