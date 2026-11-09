package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.HangingSignBlock;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeafLitterBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SegmentableBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.StructureMode;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACHED;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.BITES;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.COPPER_GOLEM_POSE;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HALF;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.UP;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.DOWN;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST_REDSTONE;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH_REDSTONE;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.ROTATION_16;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH_REDSTONE;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST_REDSTONE;

/**
 * Server-side V2-Extra block-state restoration, adapted from EarlyCompat by chenjunfu2 (CC0-1.0).
 */
public final class EasyPlaceStateSync {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Direction[] VERTICAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private EasyPlaceStateSync() {
    }

    public static @Nullable BlockState apply(Block block, BlockState state, BlockPlaceContext context, int rawValue) {
        if(block instanceof AbstractBannerBlock) {
            return state.hasProperty(ROTATION_16) ? replaceSlot(state, ROTATION_16, rawValue & 0xf) : state;
        }
        if(block instanceof BedBlock) {
            Direction facing = directionFromProtocol(state, rawValue, context);
            BlockPos headPos = context.getClickedPos().relative(facing);
            if(!context.getLevel().getBlockState(headPos).canBeReplaced(context)) return null;
            return state.setValue(HORIZONTAL_FACING, facing);
        }
        if(block instanceof BellBlock) {
            BlockState generic = applyGeneric(state, rawValue, context);
            return replaceSlot(
                    generic,
                    BellBlock.ATTACHMENT,
                    BellAttachType.values()[(rawValue & 0b0011_0000) >>> 4]
            );
        }
        if(block instanceof CampfireBlock) {
            return replaceSlot(applyGeneric(state, rawValue, context), CampfireBlock.LIT, (rawValue & 0x10) != 0);
        }
        if(block instanceof CakeBlock) {
            return replaceSlot(state, BITES, rawValue & 0x7);
        }
        if(block instanceof CandleBlock) {
            return validateIncremental(state, CandleBlock.CANDLES, (rawValue & 0x3) + 1);
        }
        if(block instanceof ChestBlock) {
            return EasyPlaceChestSync.applyFacing(
                    context.getLevel(),
                    context.getClickedPos(),
                    state,
                    directionFromProtocol(state, rawValue, context),
                    rawValue
            );
        }
        if(block instanceof CommandBlock) {
            return replaceSlot(applyGeneric(state, rawValue, context), CommandBlock.CONDITIONAL, (rawValue & 0x10) != 0);
        }
        if(block instanceof CopperBulbBlock) {
            return state
                    .setValue(CopperBulbBlock.LIT, (rawValue & 0x1) != 0)
                    .setValue(CopperBulbBlock.POWERED, (rawValue & 0x2) != 0);
        }
        if(block instanceof CopperGolemStatueBlock) {
            BlockState generic = applyGeneric(state, rawValue, context);
            CopperGolemStatueBlock.Pose[] poses = CopperGolemStatueBlock.Pose.values();
            return replaceSlot(generic, COPPER_GOLEM_POSE, poses[(rawValue >>> 4) & 0x3]);
        }
        if(block instanceof CrafterBlock) {
            FrontAndTop[] values = FrontAndTop.values();
            return replaceSlot(state, BlockStateProperties.ORIENTATION, values[Math.floorMod(rawValue, values.length)]);
        }
        if(block instanceof DaylightDetectorBlock) {
            return replaceSlot(state, DaylightDetectorBlock.INVERTED, (rawValue & 1) != 0);
        }
        if(block instanceof DetectorRailBlock) {
            RailShape[] values = RailShape.values();
            return replaceSlot(state, DetectorRailBlock.SHAPE, values[(rawValue & 0x7) % 6]);
        }
        if(block instanceof DoorBlock) {
            BlockState result = state
                    .setValue(HORIZONTAL_FACING, VERTICAL_DIRECTIONS[rawValue & 0x3])
                    .setValue(DoorBlock.HINGE, DoorHingeSide.values()[(rawValue & 0x4) >>> 2])
                    .setValue(DoorBlock.HALF, (rawValue & 0x10) != 0
                            ? DoubleBlockHalf.UPPER
                            : DoubleBlockHalf.LOWER);
            if(!block.equals(Blocks.IRON_DOOR)) result = result.setValue(DoorBlock.OPEN, (rawValue & 0x8) != 0);
            return result;
        }
        if(block instanceof FenceGateBlock) {
            return replaceSlot(applyGeneric(state, rawValue, context), FenceGateBlock.OPEN, (rawValue & 0x10) != 0);
        }
        if(block instanceof FlowerBedBlock) {
            Direction facing = VERTICAL_DIRECTIONS[(rawValue & 0xC) >>> 2];
            int amount = (rawValue & 0x3) + 1;
            BlockState result = state.setValue(FlowerBedBlock.FACING, facing);
            return validateIncremental(result, FlowerBedBlock.AMOUNT, amount);
        }
        if(block instanceof HangingSignBlock) {
            BlockState result = state;
            if(result.hasProperty(ROTATION_16)) {
                result = result.setValue(ROTATION_16, rawValue & 0xf);
            }
            if(result.hasProperty(ATTACHED)) {
                result = result.setValue(ATTACHED, (rawValue & 0x10) != 0);
            }
            return result;
        }
        if(block instanceof JigsawBlock) {
            return replaceSlot(
                    state,
                    JigsawBlock.ORIENTATION,
                    FrontAndTop.values()[(rawValue & 0xf) % FrontAndTop.values().length]
            );
        }
        if(block instanceof LanternBlock) {
            return replaceSlot(state, LanternBlock.HANGING, (rawValue & 1) != 0);
        }
        if(block instanceof LeverBlock) {
            BlockState result = applyGeneric(state, rawValue, context);
            result = replaceSlot(
                    result,
                    FaceAttachedHorizontalDirectionalBlock.FACE,
                    AttachFace.values()[((rawValue & 0x30) >>> 4) % 3]
            );
            return replaceSlot(result, LeverBlock.POWERED, (rawValue & 0x40) != 0);
        }
        if(block instanceof LightBlock) {
            return replaceSlot(state, LightBlock.LEVEL, rawValue & 0xf);
        }
        if(block instanceof LeafLitterBlock) {
            Direction facing = VERTICAL_DIRECTIONS[(rawValue & 0xC) >>> 2];
            int amount = (rawValue & 0x3) + 1;
            BlockState result = state.setValue(LeafLitterBlock.FACING, facing);
            return validateIncremental(result, SegmentableBlock.AMOUNT, amount);
        }
        if(MultiFaceBlockRules.isMultiFaceBlock(block)) {
            return MultiFaceBlockRules.applyPlacementState(block, state, context, rawValue);
        }
        if(block instanceof HugeMushroomBlock) {
            return state
                    .setValue(NORTH, (rawValue & 0x01) != 0)
                    .setValue(EAST, (rawValue & 0x02) != 0)
                    .setValue(SOUTH, (rawValue & 0x04) != 0)
                    .setValue(WEST, (rawValue & 0x08) != 0)
                    .setValue(UP, (rawValue & 0x10) != 0)
                    .setValue(DOWN, (rawValue & 0x20) != 0);
        }
        if(block instanceof NoteBlock) {
            return replaceSlot(state, NoteBlock.NOTE, (rawValue & 0x1f) % 25);
        }
        if(block instanceof PoweredRailBlock) {
            return replaceSlot(state, PoweredRailBlock.SHAPE, railShape(state, rawValue & 0x7));
        }
        if(block instanceof RailBlock) {
            return replaceSlot(state, RailBlock.SHAPE, railShape(state, rawValue & 0xf));
        }
        if(block instanceof RedstoneWallTorchBlock) {
            return replaceSlot(state, RedstoneWallTorchBlock.LIT, (rawValue & 1) != 0);
        }
        if(block instanceof RedstoneTorchBlock) {
            return replaceSlot(state, RedstoneTorchBlock.LIT, (rawValue & 1) != 0);
        }
        if(block == Blocks.REDSTONE_WIRE) {
            if((rawValue & 1) == 0) return state;
            return state
                    .setValue(NORTH_REDSTONE, RedstoneSide.NONE)
                    .setValue(EAST_REDSTONE, RedstoneSide.NONE)
                    .setValue(SOUTH_REDSTONE, RedstoneSide.NONE)
                    .setValue(WEST_REDSTONE, RedstoneSide.NONE);
        }
        if(block instanceof SeaPickleBlock) {
            return validateIncremental(state, SeaPickleBlock.PICKLES, (rawValue & 0x3) + 1);
        }
        if(block instanceof SignBlock) {
            return state.hasProperty(ROTATION_16) ? replaceSlot(state, ROTATION_16, rawValue & 0xf) : state;
        }
        if(block instanceof SkullBlock) {
            return state.hasProperty(SkullBlock.ROTATION)
                    ? replaceSlot(state, SkullBlock.ROTATION, rawValue & 0xf)
                    : state;
        }
        if(block instanceof SnowLayerBlock) {
            return validateIncremental(state, SnowLayerBlock.LAYERS, (rawValue & 0x7) + 1);
        }
        if(block instanceof StairBlock) {
            return state
                    .setValue(StairBlock.FACING, VERTICAL_DIRECTIONS[rawValue & 0x3])
                    .setValue(StairBlock.HALF, halfFromBit(rawValue))
                    .setValue(StairBlock.SHAPE, StairsShape.values()[(rawValue & 0x38) >>> 3]);
        }
        if(block instanceof StructureBlock) {
            return replaceSlot(
                    state,
                    StructureBlock.MODE,
                    StructureMode.values()[rawValue & 0x3]
            );
        }
        if(block instanceof TrapDoorBlock) {
            BlockState result = state
                    .setValue(HORIZONTAL_FACING, VERTICAL_DIRECTIONS[rawValue & 0x3])
                    .setValue(TrapDoorBlock.HALF, halfFromBit(rawValue));
            if(!block.equals(Blocks.IRON_TRAPDOOR)) result = result.setValue(TrapDoorBlock.OPEN, (rawValue & 0x8) != 0);
            return result;
        }
        if(block instanceof TurtleEggBlock) {
            return validateIncremental(state, TurtleEggBlock.EGGS, (rawValue & 0x3) + 1);
        }
        if(block instanceof FaceAttachedHorizontalDirectionalBlock) {
            return replaceSlot(
                    applyGeneric(state, rawValue, context),
                    FaceAttachedHorizontalDirectionalBlock.FACE,
                    AttachFace.values()[((rawValue & 0x30) >>> 4) % 3]
            );
        }
        return applyGeneric(state, rawValue, context);
    }

    public static @Nullable BlockState applyStandingOrWall(
            Block standingBlock,
            Block wallBlock,
            BlockState standingState,
            BlockPlaceContext context,
            int rawValue
    ) {
        boolean wall = (rawValue & 1) != 0;
        Direction facing = VERTICAL_DIRECTIONS[(rawValue >>> 1) & 0x3];
        int stateValue = rawValue >>> 3;
        if(standingBlock instanceof HangingSignBlock && (rawValue & 0x4) != 0) {
            stateValue |= 0x10;
        }
        Block block = wall ? wallBlock : standingBlock;
        BlockState state = standingState.is(block)
                ? standingState
                : block.getStateForPlacement(context);
        if(state == null && wall) state = wallBlock.defaultBlockState();
        if(state == null) return null;
        state = setFirstDirection(state, facing);
        return apply(block, state, context, stateValue);
    }

    static BlockState applyGeneric(BlockState state, int protocolValue, BlockPlaceContext context) {
        BlockState result = setFirstDirection(state, directionFromProtocol(state, protocolValue, context));
        if(result == state && state.hasProperty(AXIS)) {
            result = result.setValue(AXIS, Direction.Axis.values()[Math.floorMod(protocolValue, 3)]);
        }

        if(result.hasProperty(SLAB_TYPE) && result.getValue(SLAB_TYPE) != SlabType.DOUBLE) {
            SlabType targetType = (protocolValue & 0x10) != 0 ? SlabType.TOP : SlabType.BOTTOM;
            result = result.setValue(SLAB_TYPE, targetType);
        }
        if(result.hasProperty(HALF)) {
            result = result.setValue(HALF, (protocolValue & 0x10) != 0 ? Half.TOP : Half.BOTTOM);
        }

        int highBits = protocolValue & ~0xf;
        if(result.hasProperty(RepeaterBlock.DELAY) && highBits >= 0x10) {
            int delay = highBits >>> 4;
            if(delay >= 1 && delay <= 4) result = result.setValue(RepeaterBlock.DELAY, delay);
        } else if(highBits == 0x10) {
            if(result.hasProperty(ComparatorBlock.MODE)
                    && result.getValue(ComparatorBlock.MODE) == ComparatorMode.COMPARE) {
                result = result.setValue(ComparatorBlock.MODE, ComparatorMode.SUBTRACT);
            }
        }
        return result;
    }

    private static Direction directionFromProtocol(
            BlockState state,
            int protocolValue,
            BlockPlaceContext context
    ) {
        int index = protocolValue & 0xf;
        if(index <= 5) return DIRECTIONS[index];
        if(index == 6) return context.getHorizontalDirection().getOpposite();
        return state.hasProperty(HORIZONTAL_FACING)
                ? state.getValue(HORIZONTAL_FACING)
                : Direction.NORTH;
    }

    private static @Nullable DirectionPropertyValue getDirectionProperty(BlockState state) {
        for(Property<?> property : state.getProperties()) {
            Collection<?> values = property.getPossibleValues();
            if(!values.isEmpty() && values.iterator().next() instanceof Direction) {
                return new DirectionPropertyValue(property, values);
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setFirstDirection(BlockState state, Direction direction) {
        DirectionPropertyValue property = getDirectionProperty(state);
        if(property == null || !property.values().contains(direction)) return state;
        return state.setValue((Property) property.property(), direction);
    }

    private static RailShape railShape(BlockState state, int index) {
        for(Property<?> property : state.getProperties()) {
            Collection<?> values = property.getPossibleValues();
            if(!values.isEmpty() && values.iterator().next() instanceof RailShape) {
                List<RailShape> shapes = values.stream().map(RailShape.class::cast).toList();
                return shapes.get(Math.floorMod(index, shapes.size()));
            }
        }
        return RailShape.NORTH_SOUTH;
    }

    private static Half halfFromBit(int protocolValue) {
        return (protocolValue & 0x4) != 0 ? Half.TOP : Half.BOTTOM;
    }

    private static <T extends Comparable<T>> @Nullable BlockState replaceSlot(
            BlockState state,
            Property<T> property,
            T value
    ) {
        if(!state.hasProperty(property) || !property.getPossibleValues().contains(value)) return null;
        return state.setValue(property, value);
    }

    static @Nullable BlockState validateIncremental(
            BlockState state,
            Property<Integer> property,
            int requestedMaximum
    ) {
        if(!state.hasProperty(property)) return null;
        return state.getValue(property) <= requestedMaximum ? state : null;
    }

    private record DirectionPropertyValue(Property<?> property, Collection<?> values) {
    }
}
