package top.frostor.carpet.easyplace;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

public final class EasyPlaceStateSupport {
    private static final Direction[] DIRECTIONS = Direction.values();

    private EasyPlaceStateSupport() {
    }

    public static BlockState applyGeneric(BlockState state, int protocolValue, BlockPlaceContext context) {
        BlockState result = setFirstDirection(state, directionFromProtocol(state, protocolValue, context));
        if(result == state && state.hasProperty(BlockStateProperties.AXIS)) {
            result = result.setValue(
                    BlockStateProperties.AXIS,
                    Direction.Axis.values()[Math.floorMod(protocolValue, 3)]
            );
        }
        if(result.hasProperty(BlockStateProperties.SLAB_TYPE)
                && result.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE) {
            SlabType targetType = (protocolValue & 0x10) != 0 ? SlabType.TOP : SlabType.BOTTOM;
            result = result.setValue(BlockStateProperties.SLAB_TYPE, targetType);
        }
        if(result.hasProperty(BlockStateProperties.HALF)) {
            result = result.setValue(
                    BlockStateProperties.HALF,
                    (protocolValue & 0x10) != 0 ? Half.TOP : Half.BOTTOM
            );
        }
        int highBits = protocolValue & ~0xf;
        if(result.hasProperty(RepeaterBlock.DELAY) && highBits >= 0x10) {
            int delay = highBits >>> 4;
            if(delay >= 1 && delay <= 4) result = result.setValue(RepeaterBlock.DELAY, delay);
        } else if(highBits == 0x10
                && result.hasProperty(ComparatorBlock.MODE)
                && result.getValue(ComparatorBlock.MODE) == ComparatorMode.COMPARE) {
            result = result.setValue(ComparatorBlock.MODE, ComparatorMode.SUBTRACT);
        }
        return result;
    }

    public static Direction directionFromProtocol(
            BlockState state,
            int protocolValue,
            BlockPlaceContext context
    ) {
        int index = protocolValue & 0xf;
        if(index <= 5) return DIRECTIONS[index];
        if(index == 6 && context != null) {
            return context.getHorizontalDirection().getOpposite();
        }
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : Direction.NORTH;
    }

    public static Half halfFromBit(int protocolValue) {
        return (protocolValue & 0x4) != 0 ? Half.TOP : Half.BOTTOM;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static BlockState setFirstDirection(BlockState state, Direction direction) {
        for(Property<?> property : state.getProperties()) {
            if(property.getPossibleValues().stream().anyMatch(value -> value instanceof Direction)
                    && property.getPossibleValues().contains(direction)) {
                return state.setValue((Property) property, direction);
            }
        }
        return state;
    }

    public static <T extends Comparable<T>> @Nullable BlockState replaceSlot(
            BlockState state,
            Property<T> property,
            T value
    ) {
        if(!state.hasProperty(property) || !property.getPossibleValues().contains(value)) return null;
        return state.setValue(property, value);
    }

    public static @Nullable BlockState validateIncremental(
            BlockState state,
            Property<Integer> property,
            int requestedMaximum
    ) {
        if(!state.hasProperty(property)) return null;
        return state.getValue(property) <= requestedMaximum ? state : null;
    }

}
