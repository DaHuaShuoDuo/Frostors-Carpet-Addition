package top.frostor.carpet.easyplace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.frostor.carpet.easyplace.EasyPlaceUpdateSuppressionGuard;

@Mixin(PoweredRailBlock.class)
public abstract class PoweredRailBlockMixin {
    @Inject(
            method = "updateState(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void easyPlace$preserveRailState(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            CallbackInfo ci
    ) {
        if(EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                EasyPlaceUpdateSuppressionGuard.Family.RAIL_STATE,
                pos
        )) {
            ci.cancel();
        }
    }
}
