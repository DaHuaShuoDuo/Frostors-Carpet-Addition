package top.frostor.carpet.easyplace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.frostor.carpet.easyplace.EasyPlaceUpdateSuppressionGuard;

@Mixin(DetectorRailBlock.class)
public abstract class DetectorRailBlockMixin {
    @Inject(
            method = "checkPressed(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void easyPlace$preserveDetectorRailPower(
            Level level,
            BlockPos pos,
            BlockState state,
            CallbackInfo ci
    ) {
        if(EasyPlaceUpdateSuppressionGuard.shouldSuppressRail(level, pos)) {
            ci.cancel();
        }
    }
}
