package top.frostor.carpet.easyplace.adapter.directional;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import top.frostor.carpet.easyplace.LegacyEasyPlaceStateSync;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class GenericDirectionalStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        BlockState state = context.placementState();
        return !LegacyEasyPlaceStateSync.hasSpecializedHandling(context.block())
                && (hasDirectionProperty(state)
                || state.hasProperty(BlockStateProperties.AXIS)
                || state.hasProperty(BlockStateProperties.SLAB_TYPE)
                || state.hasProperty(BlockStateProperties.HALF)
                || state.hasProperty(RepeaterBlock.DELAY)
                || state.hasProperty(ComparatorBlock.MODE));
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        return LegacyEasyPlaceStateSync.applyGeneric(
                context.placementState(),
                context.rawValue(),
                context.placeContext()
        );
    }

    private static boolean hasDirectionProperty(BlockState state) {
        for(Property<?> property : state.getProperties()) {
            if(property.getPossibleValues().stream().anyMatch(value -> value instanceof Direction)) {
                return true;
            }
        }
        return false;
    }
}
