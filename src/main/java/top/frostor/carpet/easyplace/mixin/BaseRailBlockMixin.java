package top.frostor.carpet.easyplace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.frostor.carpet.easyplace.EasyPlaceUpdateSuppressionGuard;

@Mixin(BaseRailBlock.class)
public abstract class BaseRailBlockMixin {
    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void frostorsCarpet$keepEasyPlaceRailNeighborhood(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            Orientation orientation,
            boolean movedByPiston,
            CallbackInfo ci
    ) {
        if(EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                EasyPlaceUpdateSuppressionGuard.Family.RAIL,
                pos
        )) {
            top.frostor.carpet.FrostorsCarpetAddition.LOGGER.debug(
                    "[EasyPlace] Suppressed rail neighborChanged at {}",
                    pos
            );
            ci.cancel();
        }
    }

    @Inject(
            method = "updateState(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void frostorsCarpet$keepEasyPlaceRailShape(
            BlockState state,
            Level level,
            BlockPos pos,
            boolean movedByPiston,
            CallbackInfoReturnable<BlockState> cir
    ) {
        if(EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                EasyPlaceUpdateSuppressionGuard.Family.RAIL,
                pos
        )) {
            top.frostor.carpet.FrostorsCarpetAddition.LOGGER.debug(
                    "[EasyPlace] Suppressed rail updateState at {}",
                    pos
            );
            cir.setReturnValue(state);
        }
    }

    @Inject(
            method = "updateDir(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void frostorsCarpet$keepEasyPlaceRailDirection(
            Level level,
            BlockPos pos,
            BlockState state,
            boolean forceUpdate,
            CallbackInfoReturnable<BlockState> cir
    ) {
        if(EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                EasyPlaceUpdateSuppressionGuard.Family.RAIL,
                pos
        )) {
            top.frostor.carpet.FrostorsCarpetAddition.LOGGER.debug(
                    "[EasyPlace] Suppressed rail updateDir at {}",
                    pos
            );
            cir.setReturnValue(state);
        }
    }
}
