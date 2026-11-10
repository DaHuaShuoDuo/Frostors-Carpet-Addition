package top.frostor.carpet.easyplace.adapter.observer;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class ObserverStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof ObserverBlock;
    }

    @Override
    public @Nullable BlockState restore(EasyPlaceRestoreContext context) {
        int rawValue = context.rawValue();
        if(rawValue < 0 || rawValue >= Direction.values().length) return null;
        BlockState state = EasyPlaceStateSupport.replaceSlot(
                context.placementState(),
                ObserverBlock.FACING,
                Direction.values()[rawValue]
        );
        if(state == null) return null;
        return state.setValue(ObserverBlock.POWERED, false);
    }
}
