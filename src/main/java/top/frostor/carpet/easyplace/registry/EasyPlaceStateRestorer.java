package top.frostor.carpet.easyplace.registry;

import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface EasyPlaceStateRestorer {
    @Nullable BlockState restore(EasyPlaceRestoreContext context);
}
