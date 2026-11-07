package top.frostor.carpet.playerladder.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.frostor.carpet.playerladder.SharedHandler;

@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void playerladder$suppressCarryOnDismount(CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        if(!player.level().isClientSide() && SharedHandler.shouldSuppressCarryOnDismount(player))
            cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void playerladder$onTick(CallbackInfo callbackInfo) {
        SharedHandler.onPlayerTick((Player) (Object) this);
    }
}
