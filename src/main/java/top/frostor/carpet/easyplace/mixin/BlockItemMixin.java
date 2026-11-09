package top.frostor.carpet.easyplace.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.frostor.carpet.easyplace.EasyPlaceExtraProtocol;
import top.frostor.carpet.easyplace.EasyPlaceExtraServer;
import top.frostor.carpet.easyplace.EasyPlaceChestSync;
import top.frostor.carpet.easyplace.EasyPlaceItemStackSync;
import top.frostor.carpet.easyplace.EasyPlaceQuantityRules;
import top.frostor.carpet.easyplace.EasyPlaceStateSync;
import top.frostor.carpet.easyplace.EasyPlaceUpdateSuppressionGuard;
import top.frostor.carpet.easyplace.MultiFaceBlockRules;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Shadow
    protected abstract boolean canPlace(BlockPlaceContext context, BlockState state);

    @WrapOperation(
            method = "canPlace",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;canSurvive(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"
            )
    )
    private boolean frostorsCarpet$allowUnsupportedCandlePlacement(
            BlockState state,
            LevelReader level,
            BlockPos pos,
            Operation<Boolean> original,
            @Local(argsOnly = true) BlockPlaceContext context
    ) {
        if(original.call(state, level, pos)) return true;
        if(!(state.getBlock() instanceof CandleBlock)
                || context.getLevel().isClientSide()
                || !(context.getPlayer() instanceof ServerPlayer player)
                || !EasyPlaceExtraServer.isSupported(player)) {
            return false;
        }

        int protocolValue = EasyPlaceExtraProtocol.decode(
                context.getClickLocation(),
                context.getClickedPos()
        );
        return EasyPlaceExtraProtocol.isExtra(protocolValue);
    }

    @WrapOperation(
            method = "place",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/context/BlockPlaceContext;canPlace()Z"
            )
    )
    private boolean frostorsCarpet$allowIncrementalQuantity(
            BlockPlaceContext context,
            Operation<Boolean> original
    ) {
        if(original.call(context)) return true;
        if(context.getLevel().isClientSide()
                || !(context.getPlayer() instanceof ServerPlayer player)
                || !EasyPlaceExtraServer.isSupported(player)) {
            return false;
        }

        int protocolValue = EasyPlaceExtraProtocol.decode(
                context.getClickLocation(),
                context.getClickedPos()
        );
        if(!EasyPlaceExtraProtocol.isExtra(protocolValue)) return false;

        BlockState current = context.getLevel().getBlockState(context.getClickedPos());
        Block block = ((BlockItem) (Object) this).getBlock();
        return context.getItemInHand().is(block.asItem())
                && EasyPlaceQuantityRules.canIncrement(
                        current,
                        block,
                        EasyPlaceExtraProtocol.toRawValue(protocolValue)
                );
    }

    @Inject(method = "place", at = @At("HEAD"))
    private void frostorsCarpet$beginEasyPlace(
            BlockPlaceContext context,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir
    ) {
        EasyPlaceUpdateSuppressionGuard.beginPlacement();
        EasyPlaceItemStackSync.beginPlacement();
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void frostorsCarpet$endEasyPlace(
            BlockPlaceContext context,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir
    ) {
        EasyPlaceItemStackSync.endPlacement();
        EasyPlaceUpdateSuppressionGuard.endPlacement();
        if(cir.getReturnValue().consumesAction()
                && !context.getLevel().isClientSide()
                && context.getPlayer() instanceof ServerPlayer player
                && EasyPlaceExtraServer.isSupported(player)
                && context.getLevel().getBlockState(context.getClickedPos()).getBlock()
                instanceof net.minecraft.world.level.block.ChestBlock) {
            EasyPlaceChestSync.reconcileAfterPlacement(
                    context.getLevel(),
                    context.getClickedPos()
            );
        }
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
            if(MultiFaceBlockRules.isMultiFaceBlock(item.getBlock())) {
                state = item.getBlock().defaultBlockState();
            } else {
                state = item.getBlock().getStateForPlacement(context);
            }
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
        int auxiliaryValue = EasyPlaceExtraProtocol.decodeZ(
                context.getClickLocation(),
                context.getClickedPos()
        );
        EasyPlaceItemStackSync.applyCrafterSlots(
                ((BlockItem) (Object) this).getBlock(),
                context.getItemInHand(),
                auxiliaryValue
        );
        BlockState adjusted = EasyPlaceStateSync.apply(
                ((BlockItem) (Object) this).getBlock(),
                state,
                context,
                rawValue,
                auxiliaryValue
        );
        boolean placeable = adjusted != null && canPlace(context, adjusted);
        if(!placeable) {
            return null;
        }

        if(rail) {
            EasyPlaceUpdateSuppressionGuard.request(EasyPlaceUpdateSuppressionGuard.Family.RAIL);
            top.frostor.carpet.FrostorsCarpetAddition.LOGGER.debug(
                    "[EasyPlace] Requested rail shape preservation at {} (raw=0x{})",
                    context.getClickedPos(),
                    Integer.toHexString(rawValue)
            );
        }
        if(adjusted.getBlock() instanceof WallBlock) {
            EasyPlaceUpdateSuppressionGuard.request(EasyPlaceUpdateSuppressionGuard.Family.WALL);
            top.frostor.carpet.FrostorsCarpetAddition.LOGGER.debug(
                    "[EasyPlace] Requested wall state preservation at {} (raw=0x{}, auxiliary=0x{})",
                    context.getClickedPos(),
                    Integer.toHexString(rawValue),
                    Integer.toHexString(auxiliaryValue)
            );
        }
        return adjusted;
    }
}
