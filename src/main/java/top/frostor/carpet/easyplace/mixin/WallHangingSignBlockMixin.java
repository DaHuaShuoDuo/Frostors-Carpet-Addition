package top.frostor.carpet.easyplace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.frostor.carpet.easyplace.EasyPlaceExtraProtocol;
import top.frostor.carpet.easyplace.EasyPlaceExtraServer;

@Mixin(WallHangingSignBlock.class)
public abstract class WallHangingSignBlockMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void frostorsCarpet$suppressEasyPlaceChaining(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if(!(stack.getItem() instanceof HangingSignItem)
                || !(player instanceof ServerPlayer serverPlayer)
                || !EasyPlaceExtraServer.isSupported(serverPlayer)) {
            return;
        }

        int protocolValue = EasyPlaceExtraProtocol.decode(
                hitResult.getLocation(),
                hitResult.getBlockPos()
        );
        if(EasyPlaceExtraProtocol.isExtra(protocolValue)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
