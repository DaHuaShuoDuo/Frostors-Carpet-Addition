package top.frostor.carpet.easyplace.adapter.rail;

import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

import java.util.Collection;
import java.util.List;

public final class RailStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof PoweredRailBlock
                || context.block() instanceof RailBlock;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        int mask = context.block() instanceof PoweredRailBlock ? 0x7 : 0xf;
        return EasyPlaceStateSupport.replaceSlot(
                context.placementState(),
                railShapeProperty(context.placementState()),
                railShape(context.placementState(), context.rawValue() & mask)
        );
    }

    private static Property<RailShape> railShapeProperty(BlockState state) {
        return state.getBlock() instanceof PoweredRailBlock
                ? PoweredRailBlock.SHAPE
                : RailBlock.SHAPE;
    }

    private static RailShape railShape(BlockState state, int index) {
        Collection<RailShape> values = railShapeProperty(state).getPossibleValues();
        List<RailShape> shapes = values.stream().toList();
        return shapes.get(Math.floorMod(index, shapes.size()));
    }
}
