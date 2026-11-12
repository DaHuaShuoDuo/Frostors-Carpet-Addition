package top.frostor.carpet.easyplace.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import top.frostor.carpet.easyplace.EasyPlaceExtraProtocol;
import top.frostor.carpet.easyplace.EasyPlaceExtraServer;
import top.frostor.carpet.easyplace.EasyPlacePacketRequest;
import top.frostor.carpet.easyplace.EasyPlacePlacementRequest;

@Mixin(UseOnContext.class)
public abstract class UseOnContextMixin {
    @WrapMethod(method = "getClickLocation")
    private Vec3 easyPlace$restoreYPrecision(Operation<Vec3> original) {
        Vec3 location = original.call();
        UseOnContext context = (UseOnContext) (Object) this;
        if(context.getLevel().isClientSide()
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return location;
        }
        EasyPlacePacketRequest.Snapshot packetRequest = EasyPlacePacketRequest.current();
        if(packetRequest != null && packetRequest.yPrecisionValue() >= 0) {
            // BlockPlaceContext.getClickedPos() already resolves to the placement target for both
            // replaceable and non-replaceable clicks, and the encoder anchors its Y payload at
            // that same target, so this is the correct base for the restored height.
            return new Vec3(
                    location.x,
                    context.getClickedPos().getY()
                            + EasyPlaceExtraProtocol.restoreRelativeY(
                            packetRequest.yPrecisionValue()
                    ),
                    location.z
            );
        }
        EasyPlacePlacementRequest.Snapshot request = EasyPlacePlacementRequest.current();
        if(request != null && request.yPrecisionValue() >= 0) {
            return new Vec3(
                    location.x,
                    context.getClickedPos().getY()
                            + EasyPlaceExtraProtocol.restoreRelativeY(
                            request.yPrecisionValue()
                    ),
                    location.z
            );
        }
        return EasyPlaceExtraServer.restoreYPrecisionLocation(
                player,
                context.getClickedPos(),
                location
        );
    }
}
