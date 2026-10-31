package top.frostor.carpet.playerladder.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.frostor.carpet.FrostorSettings;
import top.frostor.carpet.playerladder.SharedHandler;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Inject(method = "setGameMode", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V", shift = At.Shift.AFTER))
    private void playerladder$onGameModeChange(GameType gameType, CallbackInfoReturnable<Boolean> cir) {
        if(!FrostorSettings.playerLadder) return;
        SharedHandler.onGameModeChange((Player) (Object) this);
    }
}
