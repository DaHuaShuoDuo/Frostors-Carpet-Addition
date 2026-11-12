package top.frostor.carpet.easyplace.registry;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public record EasyPlaceRestoreContext(
        Block block,
        BlockState placementState,
        @Nullable BlockPlaceContext placeContext,
        int rawValue,
        int auxiliaryValue
) {
}
