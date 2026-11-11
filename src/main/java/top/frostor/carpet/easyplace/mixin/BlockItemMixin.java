package top.frostor.carpet.easyplace.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
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
import top.frostor.carpet.easyplace.EasyPlacePacketRequest;
import top.frostor.carpet.easyplace.EasyPlacePlacementLifecycle;
import top.frostor.carpet.easyplace.EasyPlacePlacementRequest;
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

        return EasyPlacePlacementRequest.current() != null;
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

        EasyPlacePlacementRequest.Snapshot request = EasyPlacePlacementRequest.current();
        if(request == null) return false;

        BlockState current = context.getLevel().getBlockState(context.getClickedPos());
        Block block = ((BlockItem) (Object) this).getBlock();
        return context.getItemInHand().is(block.asItem())
                && EasyPlaceQuantityRules.canIncrement(
                        current,
                        block,
                        request.rawValue()
                );
    }

    @WrapMethod(method = "place")
    private net.minecraft.world.InteractionResult easyPlace$wrapPlacementLifecycle(
            BlockPlaceContext context,
            Operation<net.minecraft.world.InteractionResult> original
    ) {
        EasyPlacePlacementRequest.Snapshot request = easyPlace$snapshot(context);
        net.minecraft.world.InteractionResult result = request == null
                ? EasyPlacePlacementLifecycle.run(
                context.getClickedPos(),
                () -> original.call(context)
        )
                : EasyPlacePlacementLifecycle.run(
                context.getClickedPos(),
                request,
                () -> original.call(context)
        );
        if(result.consumesAction()
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
        return result;
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

        EasyPlacePlacementRequest.Snapshot request = EasyPlacePlacementRequest.current();
        BlockState state = original.call(item, context);
        if(state == null && request != null) {
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
                    request == null ? -1 : request.rawValue(),
                    context.getClickLocation()
            );
        }
        if(request == null) return state;

        if(state == null) return null;

        int rawValue = request.rawValue();
        int auxiliaryValue = request.auxiliaryValue();
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

        boolean preserveRail = EasyPlaceExtraProtocol.requestsRailProtection(
                adjusted.getBlock(),
                rawValue,
                auxiliaryValue
        );
        boolean preserveWall = EasyPlaceExtraProtocol.requestsWallUpdateSuppression(auxiliaryValue);
        if(rail && preserveRail) {
            int railNeighborMatchMask =
                    EasyPlaceExtraProtocol.railNeighborMatchMask(auxiliaryValue);
            top.frostor.carpet.FrostorsCarpetAddition.LOGGER.info(
                    "[EasyPlace-Rail] request target={} raw=0x{} auxiliary=0x{} neighborMask=0x{}",
                    context.getClickedPos(),
                    Integer.toHexString(rawValue),
                    Integer.toHexString(auxiliaryValue),
                    Integer.toHexString(railNeighborMatchMask)
            );
            EasyPlaceUpdateSuppressionGuard.requestRailProtection(
                    context.getLevel(),
                    context.getClickedPos(),
                    railNeighborMatchMask
            );
        }
        if(adjusted.getBlock() instanceof WallBlock && preserveWall) {
            top.frostor.carpet.FrostorsCarpetAddition.LOGGER.debug(
                    "[EasyPlace] Requested wall state preservation at {} (raw=0x{}, auxiliary=0x{})",
                    context.getClickedPos(),
                    Integer.toHexString(rawValue),
                    Integer.toHexString(auxiliaryValue)
            );
        }
        return adjusted;
    }

    @Inject(
            method = "place",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/BlockItem;getPlacementState(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;",
                    shift = At.Shift.BEFORE
            )
    )
    private void easyPlace$requestUniversalPlacementProtections(
            BlockPlaceContext context,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir,
            @Local(argsOnly = false, ordinal = 0) BlockPlaceContext placementContext
    ) {
        if(placementContext.getLevel().isClientSide()
                || !(placementContext.getPlayer() instanceof ServerPlayer player)
                || !EasyPlaceExtraServer.isSupported(player)) {
            return;
        }
        EasyPlacePlacementRequest.Snapshot request = EasyPlacePlacementRequest.current();
        if(request == null) {
            return;
        }

        int auxiliaryValue = request.auxiliaryValue();
        int railNeighborMatchMask =
                EasyPlaceExtraProtocol.railNeighborMatchMask(auxiliaryValue);
        net.minecraft.world.level.block.Block placedBlock =
                placementContext.getItemInHand().getItem() instanceof BlockItem blockItem
                        ? blockItem.getBlock()
                        : null;
        boolean preserveRail = EasyPlaceExtraProtocol.requestsRailProtection(
                placedBlock,
                request.rawValue(),
                auxiliaryValue
        );
        top.frostor.carpet.FrostorsCarpetAddition.LOGGER.debug(
                "[EasyPlace-Rail] universal target={} auxiliary=0x{} request={} mask=0x{}",
                placementContext.getClickedPos(),
                Integer.toHexString(auxiliaryValue),
                preserveRail,
                Integer.toHexString(railNeighborMatchMask)
        );
        EasyPlaceUpdateSuppressionGuard.updatePlacementPosition(placementContext.getClickedPos());
        if(preserveRail) {
            EasyPlaceUpdateSuppressionGuard.requestRailProtection(
                    placementContext.getLevel(),
                    placementContext.getClickedPos(),
                    railNeighborMatchMask
            );
        }
        if(EasyPlaceExtraProtocol.requestsWallUpdateSuppression(auxiliaryValue)) {
            EasyPlaceUpdateSuppressionGuard.request(EasyPlaceUpdateSuppressionGuard.Family.WALL);
            EasyPlaceUpdateSuppressionGuard.requestWallNeighborMatches(
                    EasyPlaceExtraProtocol.wallNeighborMatchMask(auxiliaryValue)
            );
        }
        if(EasyPlaceExtraProtocol.requestsObserverUpdateSuppression(auxiliaryValue)) {
            EasyPlaceUpdateSuppressionGuard.request(EasyPlaceUpdateSuppressionGuard.Family.OBSERVER);
        }
        if(EasyPlaceExtraProtocol.requestsTntUpdateSuppression(auxiliaryValue)) {
            EasyPlaceUpdateSuppressionGuard.request(EasyPlaceUpdateSuppressionGuard.Family.TNT);
        }
    }

    private static EasyPlacePlacementRequest.Snapshot easyPlace$snapshot(
            BlockPlaceContext context
    ) {
        if(context.getLevel().isClientSide()
                || !(context.getPlayer() instanceof ServerPlayer player)
                || !EasyPlaceExtraServer.isSupported(player)) {
            return null;
        }

        // The packet hit result is the only place where the raw X field still has its original
        // anchor. BlockPlaceContext.getClickedPos() may be shifted by the hit face and
        // BlockPlaceContext.at(...) may rebuild the click location entirely, so prefer the
        // packet snapshot whenever one exists.
        EasyPlacePacketRequest.Snapshot packetRequest = EasyPlacePacketRequest.current();
        if(packetRequest != null) {
            return packetRequest.toPlacementSnapshot();
        }

        // Fallback for paths that do not pass through handleUseItemOn.
        int protocolValue = EasyPlaceExtraProtocol.decode(
                context.getClickLocation(),
                context.getClickedPos()
        );
        if(!EasyPlaceExtraProtocol.isExtra(protocolValue)) return null;
        int packedY = EasyPlaceExtraProtocol.decodeY(
                context.getClickLocation(),
                context.getClickedPos()
        );
        return new EasyPlacePlacementRequest.Snapshot(
                EasyPlaceExtraProtocol.toRawValue(protocolValue),
                -1,
                EasyPlaceExtraProtocol.stripAuxiliarySchema(
                        EasyPlaceExtraProtocol.decodeAuxiliary(
                                context.getClickLocation()
                        )
                ),
                packedY < 0
                        ? -1
                        : packedY & EasyPlaceExtraProtocol.Y_PRECISION_MASK
        );
    }
}
