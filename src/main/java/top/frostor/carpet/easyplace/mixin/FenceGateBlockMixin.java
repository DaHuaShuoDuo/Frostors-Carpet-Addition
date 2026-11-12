package top.frostor.carpet.easyplace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.frostor.carpet.easyplace.EasyPlaceExtraProtocol;
import top.frostor.carpet.easyplace.EasyPlaceExtraServer;
import top.frostor.carpet.easyplace.EasyPlaceStateSync;

@Mixin(FenceGateBlock.class)
public abstract class FenceGateBlockMixin {
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void frostorsCarpet$applyEasyPlaceOpenState(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if(level.isClientSide()
                || !(player instanceof ServerPlayer serverPlayer)
                || !EasyPlaceExtraServer.isSupported(serverPlayer)) {
            return;
        }

        int protocolValue = EasyPlaceExtraProtocol.decode(
                hitResult.getLocation(),
                hitResult.getBlockPos()
        );
        if(!EasyPlaceExtraProtocol.isExtra(protocolValue)) {
            return;
        }

        BlockState adjusted = EasyPlaceStateSync.apply(
                state.getBlock(),
                state,
                null,
                EasyPlaceExtraProtocol.toRawValue(protocolValue)
        );
        if(adjusted == null) {
            return;
        }

        level.setBlock(
                pos,
                adjusted,
                Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE
        );
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
