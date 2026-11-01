package top.frostor.carpet.easyplace.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import top.frostor.carpet.FrostorSettings;
import top.frostor.carpet.easyplace.EasyPlaceExtraServer;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

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
