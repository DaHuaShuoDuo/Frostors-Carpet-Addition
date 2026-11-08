package top.frostor.carpet.easyplace;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SegmentableBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

public final class EasyPlaceQuantityRules {
    private EasyPlaceQuantityRules() {
    }

    public static boolean canIncrement(BlockState currentState, Block expectedBlock, int rawValue) {
        if(currentState.getBlock() != expectedBlock) return false;
        IntegerProperty property = getCountProperty(expectedBlock);
        return property != null
                && currentState.hasProperty(property)
                && currentState.getValue(property) < requestedAmount(expectedBlock, rawValue);
    }

    public static int requestedAmount(Block block, int rawValue) {
        if(block instanceof SegmentableBlock) {
            return ((rawValue >>> 2) & 0x3) + 1;
        }
        if(block instanceof SnowLayerBlock) {
            return (rawValue & 0x7) + 1;
        }
        if(block instanceof CandleBlock
                || block instanceof SeaPickleBlock
                || block instanceof TurtleEggBlock) {
            return (rawValue & 0x3) + 1;
        }
        return 0;
    }

    public static @Nullable IntegerProperty getCountProperty(Block block) {
        if(block instanceof SegmentableBlock segmentableBlock) {
            return segmentableBlock.getSegmentAmountProperty();
        }
        if(block instanceof CandleBlock) return CandleBlock.CANDLES;
        if(block instanceof SeaPickleBlock) return SeaPickleBlock.PICKLES;
        if(block instanceof SnowLayerBlock) return SnowLayerBlock.LAYERS;
        if(block instanceof TurtleEggBlock) return TurtleEggBlock.EGGS;
        return null;
    }
}
