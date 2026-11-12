package top.frostor.carpet.easyplace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.frostor.carpet.easyplace.EasyPlaceUpdateSuppressionGuard;

@Mixin(TntBlock.class)
public abstract class TntBlockMixin {
    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
    private void easyPlace$preventTntPlacementActivation(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean movedByPiston,
            CallbackInfo ci
    ) {
        if(EasyPlaceUpdateSuppressionGuard.shouldSuppressTnt(level, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void easyPlace$preventTntNeighborActivation(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            Orientation orientation,
            boolean movedByPiston,
            CallbackInfo ci
    ) {
        if(EasyPlaceUpdateSuppressionGuard.shouldSuppressTnt(level, pos)) {
            ci.cancel();
        }
    }
}
