package top.frostor.carpet.easyplace;

import net.minecraft.core.Direction;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.SegmentableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class EasyPlaceExtraProtocolTest {
    private static int checks;

    private EasyPlaceExtraProtocolTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        check(
                EasyPlaceExtraPayload.TYPE.id().equals(EasyPlaceExtraPayload.CHANNEL),
                "public payload type must use the shared protocol channel"
        );
        check(
                EasyPlaceExtraPayload.PROTOCOL_VERSION == 1,
                "public payload API must preserve protocol version 1"
        );
        for(int raw = 0; raw < 0b1000_0000; raw++) {
            int extra = EasyPlaceExtraProtocol.toExtraValue(raw);
            check(EasyPlaceExtraProtocol.isExtra(extra), "every encoded value must carry the marker");
            check(
                    EasyPlaceExtraProtocol.toRawValue(extra) == raw,
                    "V2-Extra values must round trip through their marker bit"
            );
        }
        Direction[] multifaceDirections = {
                Direction.DOWN, Direction.UP, Direction.NORTH,
                Direction.SOUTH, Direction.WEST, Direction.EAST
        };
        for(int index = 0; index < multifaceDirections.length; index++) {
            check(
                    MultiFaceBlockRules.wireDirection(Blocks.GLOW_LICHEN, index)
                            == multifaceDirections[index],
                    "multiface protocol index " + index + " must match the EarlyCompat direction order"
            );
        }
        check(
                MultiFaceBlockRules.wireDirection(Blocks.GLOW_LICHEN, 6) == null,
                "multiface index 6 must be rejected"
        );
        check(
                MultiFaceBlockRules.wireDirection(Blocks.GLOW_LICHEN, 7) == null,
                "multiface index 7 must be rejected"
        );

        Direction[] vineDirections = {
                Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
        };
        for(int index = 0; index < vineDirections.length; index++) {
            check(
                    MultiFaceBlockRules.wireDirection(Blocks.VINE, index)
                            == vineDirections[index],
                    "vine protocol index " + index + " must match the EarlyCompat direction order"
            );
        }
        check(
                MultiFaceBlockRules.wireDirection(Blocks.VINE, 5) == null,
                "vine index 5 must be rejected"
        );
        check(
                MultiFaceBlockRules.wireDirection(Blocks.VINE, 6) == null,
                "vine index 6 must be rejected"
        );
        check(
                MultiFaceBlockRules.wireDirection(Blocks.VINE, 7) == null,
                "vine index 7 must be rejected"
        );
        BlockState singleVineFace = MultiFaceBlockRules.applyVineFace(
                Blocks.VINE,
                Blocks.AIR.defaultBlockState(),
                Direction.UP
        );
        check(
                singleVineFace != null
                        && singleVineFace.getValue(VineBlock.UP),
                "server vine increment must ignore a pre-expanded placement state"
        );
        BlockState vineSouth = Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true);
        BlockState vineSouthUp = MultiFaceBlockRules.applyVineFace(Blocks.VINE, vineSouth, Direction.UP);
        check(
                vineSouthUp != null
                        && vineSouthUp.getValue(VineBlock.SOUTH)
                        && vineSouthUp.getValue(VineBlock.UP),
                "server vine increment must preserve the current face set"
        );

        BlockState wallHangingSign = Blocks.OAK_WALL_HANGING_SIGN.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        BlockState wallResult = EasyPlaceStateSync.apply(
                Blocks.OAK_WALL_HANGING_SIGN,
                wallHangingSign,
                null,
                0x10
        );
        check(
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
        check(
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
        check(
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
        check(
                ceilingResult != null
                        && ceilingResult.getValue(BlockStateProperties.ROTATION_16) == 3
                        && ceilingResult.getValue(BlockStateProperties.ATTACHED),
                "ceiling hanging signs must preserve rotation and attachment"
        );

        BlockState leafLitterOne = Blocks.LEAF_LITTER.defaultBlockState();
        BlockState leafLitterThree = leafLitterOne.setValue(SegmentableBlock.AMOUNT, 3);
        check(
                EasyPlaceStateSync.validateIncremental(
                        leafLitterOne,
                        SegmentableBlock.AMOUNT,
                        3
                ) != null,
                "leaf litter must allow incremental placement below the target amount"
        );
        check(
                EasyPlaceStateSync.validateIncremental(
                        leafLitterThree,
                        SegmentableBlock.AMOUNT,
                        2
                ) == null,
                "leaf litter must reject placement beyond the projected amount"
        );
        checkQuantityIncrement(Blocks.CANDLE);
        checkQuantityIncrement(Blocks.PINK_PETALS);
        checkQuantityIncrement(Blocks.LEAF_LITTER);
        checkQuantityIncrement(Blocks.SEA_PICKLE);
        checkQuantityIncrement(Blocks.SNOW);
        checkQuantityIncrement(Blocks.TURTLE_EGG);

        System.out.println("EasyPlaceExtraProtocolTest passed " + checks + " checks");
    }

    private static void checkQuantityIncrement(Block block) {
        IntegerProperty property = EasyPlaceQuantityRules.getCountProperty(block);
        check(property != null, block + " must expose a supported quantity property");
        BlockState one = block.defaultBlockState().setValue(property, 1);
        int nextAmountRaw = block instanceof SegmentableBlock ? 0x4 : 0x1;
        check(
                EasyPlaceQuantityRules.canIncrement(one, block, nextAmountRaw),
                block + " must allow the next quantity level"
        );
        check(
                !EasyPlaceQuantityRules.canIncrement(one, block, 0),
                block + " must reject an unchanged quantity"
        );
        BlockState two = one.setValue(property, 2);
        check(
                !EasyPlaceQuantityRules.canIncrement(two, block, nextAmountRaw),
                block + " must reject a target below the current quantity"
        );
    }

    private static void check(boolean condition, String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }
}
