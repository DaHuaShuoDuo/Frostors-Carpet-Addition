package top.frostor.carpet.easyplace.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.frostor.carpet.easyplace.EasyPlaceExtraProtocol;
import top.frostor.carpet.easyplace.EasyPlaceExtraServer;
import top.frostor.carpet.easyplace.EasyPlaceItemStackSync;
import top.frostor.carpet.easyplace.EasyPlaceRailUpdateGuard;
import top.frostor.carpet.easyplace.EasyPlaceStateSync;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Shadow
    protected abstract boolean canPlace(BlockPlaceContext context, BlockState state);

    @Inject(method = "place", at = @At("HEAD"))
    private void frostorsCarpet$beginEasyPlace(
            BlockPlaceContext context,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir
    ) {
        EasyPlaceRailUpdateGuard.beginPlacement();
        EasyPlaceItemStackSync.beginPlacement();
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void frostorsCarpet$endEasyPlace(
            BlockPlaceContext context,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir
    ) {
        EasyPlaceItemStackSync.endPlacement();
        EasyPlaceRailUpdateGuard.endPlacement();
    }

    @WrapOperation(
            method = "place",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/BlockItem;getPlacementState(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    private BlockState frostorsCarpet$applyExtraPlaceState(
            BlockItem item,
            BlockPlaceContext context,
            Operation<BlockState> original
    ) {
        if((Object) this instanceof StandingAndWallBlockItem
                || context.getLevel().isClientSide()
                || !(context.getPlayer() instanceof ServerPlayer player)
                || !EasyPlaceExtraServer.isSupported(player)) {
            return original.call(item, context);
        }

        int protocolValue = EasyPlaceExtraProtocol.decode(context.getClickLocation(), context.getClickedPos());
        BlockState state = original.call(item, context);
        if(state == null && EasyPlaceExtraProtocol.isExtra(protocolValue)) {
            state = item.getBlock().getStateForPlacement(context);
        }
        boolean rail = state != null && state.getBlock() instanceof BaseRailBlock;
        if(rail) {
            top.frostor.carpet.FrostorsCarpetAddition.LOGGER.debug(
                    "[EasyPlace] Rail placement protocol at {} decoded={} click={}",
                    context.getClickedPos(),
                    protocolValue,
                    context.getClickLocation()
            );
        }
        if(!EasyPlaceExtraProtocol.isExtra(protocolValue)) return state;

        if(state == null) return null;

        int rawValue = EasyPlaceExtraProtocol.toRawValue(protocolValue);
        int crafterDisabledMask = EasyPlaceExtraProtocol.decodeZ(
                context.getClickLocation(),
                context.getClickedPos()
        );
        EasyPlaceItemStackSync.applyCrafterSlots(
                ((BlockItem) (Object) this).getBlock(),
                context.getItemInHand(),
                crafterDisabledMask
        );
        BlockState adjusted = EasyPlaceStateSync.apply(
                ((BlockItem) (Object) this).getBlock(),
                state,
                context,
                rawValue
        );
        if(adjusted == null || !canPlace(context, adjusted)) {
            return null;
        }

        if(rail) {
            EasyPlaceRailUpdateGuard.requestSuppression(context.getLevel(), context.getClickedPos());
            top.frostor.carpet.FrostorsCarpetAddition.LOGGER.debug(
                    "[EasyPlace] Requested rail shape preservation at {} (raw=0x{})",
                    context.getClickedPos(),
                    Integer.toHexString(rawValue)
            );
        }
        return adjusted;
    }
}
