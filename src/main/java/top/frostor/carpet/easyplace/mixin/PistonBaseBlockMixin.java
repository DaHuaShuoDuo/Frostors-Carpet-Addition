package top.frostor.carpet.easyplace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.frostor.carpet.easyplace.EasyPlaceUpdateSuppressionGuard;

@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {
    @Inject(method = "setPlacedBy", at = @At("HEAD"), cancellable = true)
    private void easyPlace$preservePistonSetPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            LivingEntity placer,
            ItemStack stack,
            CallbackInfo ci
    ) {
        if(easyPlace$shouldSuppress(level, pos, "setPlacedBy")) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
    private void easyPlace$preservePistonOnPlace(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean movedByPiston,
            CallbackInfo ci
    ) {
        if(easyPlace$shouldSuppress(level, pos, "onPlace")) {
            ci.cancel();
        }
    }

    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void easyPlace$preservePistonNeighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            Orientation orientation,
            boolean movedByPiston,
            CallbackInfo ci
    ) {
        if(easyPlace$shouldSuppress(level, pos, "neighborChanged")) {
            ci.cancel();
        }
    }

    private static boolean easyPlace$shouldSuppress(
            Level level,
            BlockPos pos,
            String source
    ) {
        if(level.isClientSide()
                || !EasyPlaceUpdateSuppressionGuard.shouldSuppressPiston(level, pos)) {
            return false;
        }
        top.frostor.carpet.FrostorsCarpetAddition.LOGGER.debug(
                "[EasyPlace] Suppressed piston {} at {}",
                source,
                pos
        );
        return true;
    }
}
