package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public final class EasyPlaceChestSync {
    public static final int TYPE_MARKER = 0x40;
    private static final int TYPE_SHIFT = 4;
    private static final int TYPE_MASK = 0x3;

    private EasyPlaceChestSync() {
    }

    public static BlockState applyFacing(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction facing,
            int protocolValue
    ) {
        BlockState faced = state.setValue(ChestBlock.FACING, facing);
        if((protocolValue & TYPE_MARKER) != 0) {
            return applyRequestedType(level, pos, faced, (protocolValue >>> TYPE_SHIFT) & TYPE_MASK);
        }
        return applyLegacyType(level, pos, faced);
    }

    public static void reconcileAfterPlacement(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if(!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return;
        }

        Direction partnerDirection = ChestBlock.getConnectedDirection(state);
        BlockPos partnerPos = pos.relative(partnerDirection);
        BlockState partner = level.getBlockState(partnerPos);
        if(!isMatchingChest(partner, state.getValue(ChestBlock.FACING))) {
            setType(level, pos, state, ChestType.SINGLE);
            return;
        }

        ChestType partnerType = state.getValue(ChestBlock.TYPE).getOpposite();
        if(partner.getValue(ChestBlock.TYPE) != partnerType
                && isAvailablePartner(partner, partnerPos, pos, state.getValue(ChestBlock.FACING))) {
            setType(level, partnerPos, partner, partnerType);
        }
    }

    private static BlockState applyRequestedType(
            Level level,
            BlockPos pos,
            BlockState faced,
            int requestedIndex
    ) {
        ChestType requested = switch(requestedIndex) {
            case 0 -> ChestType.SINGLE;
            case 1 -> ChestType.LEFT;
            case 2 -> ChestType.RIGHT;
            default -> null;
        };
        if(requested == null) return null;
        if(requested == ChestType.SINGLE) return faced.setValue(ChestBlock.TYPE, ChestType.SINGLE);

        Direction facing = faced.getValue(ChestBlock.FACING);
        Direction partnerDirection = requested == ChestType.LEFT
                ? facing.getClockWise()
                : facing.getCounterClockWise();
        BlockPos partnerPos = pos.relative(partnerDirection);
        BlockState partner = level.getBlockState(partnerPos);
        if(!isMatchingChest(partner, facing)) {
            return faced.setValue(ChestBlock.TYPE, ChestType.SINGLE);
        }
        if(!isAvailablePartner(partner, partnerPos, pos, facing)) return null;
        return faced.setValue(ChestBlock.TYPE, requested);
    }

    private static BlockState applyLegacyType(Level level, BlockPos pos, BlockState faced) {
        ChestType baseType = faced.getValue(ChestBlock.TYPE);
        Direction facing = faced.getValue(ChestBlock.FACING);
        Direction clockwise = facing.getClockWise();
        Direction counterClockwise = facing.getCounterClockWise();
        boolean clockwisePartner = isAvailablePartner(
                level.getBlockState(pos.relative(clockwise)),
                pos.relative(clockwise),
                pos,
                facing
        );
        boolean counterClockwisePartner = isAvailablePartner(
                level.getBlockState(pos.relative(counterClockwise)),
                pos.relative(counterClockwise),
                pos,
                facing
        );

        ChestType type;
        if(clockwisePartner && counterClockwisePartner) {
            if(baseType == ChestType.LEFT) {
                type = ChestType.LEFT;
            } else if(baseType == ChestType.RIGHT) {
                type = ChestType.RIGHT;
            } else if(isSingleChest(level, pos.relative(clockwise), facing)) {
                type = ChestType.LEFT;
            } else if(isSingleChest(level, pos.relative(counterClockwise), facing)) {
                type = ChestType.RIGHT;
            } else {
                type = ChestType.SINGLE;
            }
        } else if(clockwisePartner) {
            type = ChestType.LEFT;
        } else if(counterClockwisePartner) {
            type = ChestType.RIGHT;
        } else {
            type = ChestType.SINGLE;
        }

        return faced.setValue(ChestBlock.TYPE, type);
    }

    private static boolean isAvailablePartner(
            BlockState state,
            BlockPos partnerPos,
            BlockPos currentPos,
            Direction facing
    ) {
        if(!isMatchingChest(state, facing)) return false;
        ChestType type = state.getValue(ChestBlock.TYPE);
        if(type == ChestType.SINGLE) return true;
        return partnerPos.relative(ChestBlock.getConnectedDirection(state)).equals(currentPos);
    }

    private static boolean isMatchingChest(BlockState state, Direction facing) {
        return state.getBlock() instanceof ChestBlock
                && state.getValue(ChestBlock.FACING) == facing;
    }

    private static boolean isSingleChest(Level level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ChestBlock
                && state.getValue(ChestBlock.FACING) == facing
                && state.getValue(ChestBlock.TYPE) == ChestType.SINGLE;
    }

    private static void setType(
            Level level,
            BlockPos pos,
            BlockState state,
            ChestType type
    ) {
        level.setBlock(
                pos,
                state.setValue(ChestBlock.TYPE, type),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
        );
    }
}
