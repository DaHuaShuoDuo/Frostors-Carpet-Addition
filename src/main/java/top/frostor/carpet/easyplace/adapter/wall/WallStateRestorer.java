package top.frostor.carpet.easyplace.adapter.wall;

import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.jetbrains.annotations.Nullable;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class WallStateRestorer implements EasyPlaceStateRestorer {
    private static final WallSide[] WALL_SIDES = WallSide.values();

    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof WallBlock;
    }

    @Override
    public @Nullable BlockState restore(EasyPlaceRestoreContext context) {
        int rawValue = context.rawValue();
        if(rawValue < 0 || rawValue > 80) return null;

        BlockState state = context.placementState();
        state = EasyPlaceStateSupport.replaceSlot(
                state,
                WallBlock.NORTH,
                WALL_SIDES[rawValue % 3]
        );
        if(state == null) return null;
        state = EasyPlaceStateSupport.replaceSlot(
                state,
                WallBlock.EAST,
                WALL_SIDES[(rawValue / 3) % 3]
        );
        if(state == null) return null;
        state = EasyPlaceStateSupport.replaceSlot(
                state,
                WallBlock.SOUTH,
                WALL_SIDES[(rawValue / 9) % 3]
        );
        if(state == null) return null;
        state = EasyPlaceStateSupport.replaceSlot(
                state,
                WallBlock.WEST,
                WALL_SIDES[(rawValue / 27) % 3]
        );
        if(state == null) return null;
        return EasyPlaceStateSupport.replaceSlot(
                state,
                WallBlock.UP,
                context.auxiliaryValue() >= 0 && (context.auxiliaryValue() & 0x1) != 0
        );
    }
}
