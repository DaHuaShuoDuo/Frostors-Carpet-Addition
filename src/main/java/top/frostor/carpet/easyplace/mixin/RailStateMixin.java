package top.frostor.carpet.easyplace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RailState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.frostor.carpet.easyplace.EasyPlaceUpdateSuppressionGuard;

@Mixin(RailState.class)
public abstract class RailStateMixin {
    @Shadow
    private Level level;

    @Shadow
    private BlockPos pos;

    @Inject(method = "place(ZZLnet/minecraft/world/level/block/state/properties/RailShape;)Lnet/minecraft/world/level/block/RailState;", at = @At("HEAD"), cancellable = true)
    private void frostorsCarpet$preserveEasyPlaceRail(
            boolean powered,
            boolean placing,
            RailShape shape,
            CallbackInfoReturnable<RailState> cir
    ) {
        if(EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                EasyPlaceUpdateSuppressionGuard.Family.RAIL,
                pos
        )) {
            top.frostor.carpet.FrostorsCarpetAddition.LOGGER.debug(
                    "[EasyPlace] Suppressed RailState.place at {}",
                    pos
            );
            cir.setReturnValue((RailState) (Object) this);
        }
    }
}
