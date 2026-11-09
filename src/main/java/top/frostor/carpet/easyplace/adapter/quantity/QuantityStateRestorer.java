package top.frostor.carpet.easyplace.adapter.quantity;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.LeafLitterBlock;
import net.minecraft.world.level.block.SegmentableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import top.frostor.carpet.easyplace.EasyPlaceQuantityRules;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;
import org.jetbrains.annotations.Nullable;

public final class QuantityStateRestorer implements EasyPlaceStateRestorer {
    private static final Direction[] VERTICAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    public static boolean matches(EasyPlaceRestoreContext context) {
        return EasyPlaceQuantityRules.getCountProperty(context.block()) != null;
    }

    @Override
    public @Nullable BlockState restore(EasyPlaceRestoreContext context) {
        IntegerProperty property = EasyPlaceQuantityRules.getCountProperty(context.block());
        if(property == null) return null;
        int requestedAmount = EasyPlaceQuantityRules.requestedAmount(context.block(), context.rawValue());
        BlockState state = context.placementState();
        if(context.block() instanceof FlowerBedBlock) {
            state = state.setValue(
                    FlowerBedBlock.FACING,
                    VERTICAL_DIRECTIONS[(context.rawValue() & 0xc) >>> 2]
            );
        } else if(context.block() instanceof LeafLitterBlock) {
            state = state.setValue(
                    LeafLitterBlock.FACING,
                    VERTICAL_DIRECTIONS[(context.rawValue() & 0xc) >>> 2]
            );
        }
        return EasyPlaceStateSupport.validateIncremental(state, property, requestedAmount);
    }
}
