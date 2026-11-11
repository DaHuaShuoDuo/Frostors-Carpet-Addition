package top.frostor.carpet.easyplace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.frostor.carpet.easyplace.EasyPlaceExtraServer;
import top.frostor.carpet.easyplace.EasyPlacePlacementRequest;
import top.frostor.carpet.easyplace.EasyPlaceStateSync;

@Mixin(StandingAndWallBlockItem.class)
public abstract class StandingAndWallBlockItemMixin extends BlockItem {
    @Final
    @Shadow
    protected Block wallBlock;

    protected StandingAndWallBlockItemMixin(Block block, Properties properties) {
        super(block, properties);
    }

    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void frostorsCarpet$applyExtraPlaceState(
            BlockPlaceContext context,
            CallbackInfoReturnable<BlockState> cir
    ) {
        if(context.getLevel().isClientSide()
                || !(context.getPlayer() instanceof ServerPlayer player)
                || !EasyPlaceExtraServer.isSupported(player)) return;

        EasyPlacePlacementRequest.Snapshot request = EasyPlacePlacementRequest.current();
        if(request == null) return;

        int rawValue = request.rawValue();
        boolean wallPlacement = context.getClickedFace().getAxis().isHorizontal();
        int alternateRawValue = request.alternateRawValue();
        if(alternateRawValue >= 0
                && ((rawValue & 1) != 0) != wallPlacement
                && ((alternateRawValue & 1) != 0) == wallPlacement) {
            rawValue = alternateRawValue;
        }
        BlockState state = cir.getReturnValue();
        if(state == null) {
            boolean wall = (rawValue & 1) != 0;
            state = (wall ? wallBlock : getBlock()).defaultBlockState();
        }

        BlockState adjusted = EasyPlaceStateSync.applyStandingOrWall(
                getBlock(),
                wallBlock,
                state,
                context,
                rawValue
        );
        boolean validPlacement = adjusted != null
                && canPlace(context.getLevel(), adjusted, context.getClickedPos());
        if(!validPlacement
                && adjusted != null
                && adjusted.getBlock() instanceof WallHangingSignBlock) {
            validPlacement = context.canPlace()
                    && context.getLevel().isUnobstructed(
                            adjusted,
                            context.getClickedPos(),
                            CollisionContext.placementContext(context.getPlayer())
                    );
        }
        if(!validPlacement) {
            cir.setReturnValue(null);
        } else {
            cir.setReturnValue(adjusted);
        }
    }

    @Shadow
    protected abstract boolean canPlace(LevelReader level, BlockState state, BlockPos pos);
}
