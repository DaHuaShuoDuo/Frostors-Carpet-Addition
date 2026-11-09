package top.frostor.carpet.easyplace;

import net.minecraft.core.Direction;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeafLitterBlock;
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
        checkHugeMushroomStates();
        checkRailUpdateGuardScope();
        checkFenceGateOpenState();
        checkSegmentableFacingRetention();

        checkQuantityIncrement(Blocks.CANDLE);
        checkQuantityIncrement(Blocks.PINK_PETALS);
        checkQuantityIncrement(Blocks.LEAF_LITTER);
        checkQuantityIncrement(Blocks.SEA_PICKLE);
        checkQuantityIncrement(Blocks.SNOW);
        checkQuantityIncrement(Blocks.TURTLE_EGG);

        System.out.println("EasyPlaceExtraProtocolTest passed " + checks + " checks");
    }

    private static void checkHugeMushroomStates() {
        Block[] hugeMushroomBlocks = {Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK};
        for(Block block : hugeMushroomBlocks) {
            check(block instanceof HugeMushroomBlock, block + " must use HugeMushroomBlock");
            for(int mask = 0; mask < 64; mask++) {
                BlockState applied = EasyPlaceStateSync.apply(
                        block,
                        block.defaultBlockState(),
                        null,
                        mask
                );
                check(applied != null, "huge mushroom protocol state must apply");
                check(
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
        check(
                EasyPlaceStateSync.apply(Blocks.BROWN_MUSHROOM, smallMushroom, null, 0) == smallMushroom,
                "small mushrooms must retain their placement state"
        );
    }
    private static void checkSegmentableFacingRetention() {
        BlockState flowerBedPlacement = Blocks.PINK_PETALS.defaultBlockState()
                .setValue(FlowerBedBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(FlowerBedBlock.AMOUNT, 1);
        BlockState flowerBedResult = EasyPlaceStateSync.apply(
                Blocks.PINK_PETALS,
                flowerBedPlacement,
                null,
                0x9
        );
        check(
                flowerBedResult != null
                        && flowerBedResult.getValue(FlowerBedBlock.FACING)
                        == net.minecraft.core.Direction.WEST,
                "flower bed increments must apply the projected facing"
        );

        BlockState leafLitterPlacement = Blocks.LEAF_LITTER.defaultBlockState()
                .setValue(LeafLitterBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(SegmentableBlock.AMOUNT, 1);
        BlockState leafLitterResult = EasyPlaceStateSync.apply(
                Blocks.LEAF_LITTER,
                leafLitterPlacement,
                null,
                0x9
        );
        check(
                leafLitterResult != null
                        && leafLitterResult.getValue(LeafLitterBlock.FACING)
                        == net.minecraft.core.Direction.WEST,
                "leaf litter increments must apply the projected facing"
        );
    }
    private static void checkFenceGateOpenState() {
        BlockState gate = Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(FenceGateBlock.FACING, Direction.SOUTH)
                .setValue(FenceGateBlock.OPEN, false);
        BlockState opened = EasyPlaceStateSync.apply(
                Blocks.OAK_FENCE_GATE,
                gate,
                null,
                0x12
        );
        check(
                opened != null
                        && opened.getValue(FenceGateBlock.FACING) == Direction.NORTH
                        && opened.getValue(FenceGateBlock.OPEN),
                "fence gate protocol must restore facing and open state"
        );
    }
    private static void checkRailUpdateGuardScope() {
        EasyPlaceRailUpdateGuard.beginPlacement();
        try {
            check(!EasyPlaceRailUpdateGuard.shouldSuppressUpdate(), "rail suppression must start inactive");
            EasyPlaceRailUpdateGuard.requestSuppression();
            check(
                    EasyPlaceRailUpdateGuard.shouldSuppressUpdate(),
                    "rail suppression must cover any rail update during the guarded placement"
            );
        } finally {
            EasyPlaceRailUpdateGuard.endPlacement();
        }
        check(!EasyPlaceRailUpdateGuard.shouldSuppressUpdate(), "rail suppression must end with placement");

        EasyPlaceRailUpdateGuard.beginPlacement();
        try {
            check(
                    !EasyPlaceRailUpdateGuard.shouldSuppressUpdate(),
                    "a new rail placement must not inherit a previous suppression flag"
            );
        } finally {
            EasyPlaceRailUpdateGuard.endPlacement();
        }
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
