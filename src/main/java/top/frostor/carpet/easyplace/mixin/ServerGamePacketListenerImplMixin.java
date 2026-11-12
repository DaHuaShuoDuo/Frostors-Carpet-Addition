package top.frostor.carpet.easyplace.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.frostor.carpet.FrostorSettings;
import top.frostor.carpet.easyplace.EasyPlaceExtraServer;
import top.frostor.carpet.easyplace.EasyPlacePacketRequest;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @ModifyVariable(
            method = "handleUseItemOn",
            at = @At(value = "STORE"),
            ordinal = 0
    )
    private BlockHitResult easyPlace$capturePacketRequest(BlockHitResult hitResult) {
        if(FrostorSettings.easyPlaceProtocolExtra && EasyPlaceExtraServer.isSupported(player)) {
            EasyPlacePacketRequest.Snapshot snapshot = EasyPlacePacketRequest.decode(
                    hitResult.getLocation(),
                    hitResult.getBlockPos(),
                    EasyPlaceExtraServer.supportsRelativeAux(player)
            );
            if(snapshot != null) {
                EasyPlacePacketRequest.begin(snapshot);
            } else {
                EasyPlacePacketRequest.end();
            }
        } else {
            EasyPlacePacketRequest.end();
        }
        return hitResult;
    }

    /**
     * The packet snapshot must not survive an exception from block interaction or a downstream
     * mod. A RETURN injector would skip the cleanup in that case and leak a stale snapshot onto
     * the server thread, so wrap the whole handler with try/finally instead.
     */
    @WrapMethod(method = "handleUseItemOn")
    private void easyPlace$guardPacketRequest(
            ServerboundUseItemOnPacket packet,
            Operation<Void> original
    ) {
        try {
            original.call(packet);
        } finally {
            EasyPlacePacketRequest.end();
        }
    }

    @WrapOperation(
            method = "handleUseItemOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 frostorsCarpet$allowEncodedEasyPlaceHit(
            Vec3 hitPos,
            Vec3 blockCenter,
            Operation<Vec3> original
    ) {
        if(FrostorSettings.easyPlaceProtocolExtra && EasyPlaceExtraServer.isSupported(player)) {
            return Vec3.ZERO;
        }
        return original.call(hitPos, blockCenter);
    }
}
