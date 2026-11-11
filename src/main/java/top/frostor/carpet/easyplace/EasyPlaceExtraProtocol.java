package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.phys.Vec3;

/**
 * V2-Extra protocol layout, adapted from EarlyCompat by chenjunfu2 (CC0-1.0).
 */
public final class EasyPlaceExtraProtocol {
    public static final int EXTRA_MARKER = 0b0000_1000;
    public static final int CRAFTER_SLOT_MASK = 0x1ff;
    public static final int RAIL_UPDATE_SUPPRESSION = 1 << 9;
    public static final int WALL_UPDATE_SUPPRESSION = 1 << 10;
    public static final int WALL_NEIGHBOR_MATCH_BASE = 1 << 11;
    public static final int WALL_NEIGHBOR_MATCH_MASK = 0x3f << 11;
    public static final int RAIL_NEIGHBOR_MATCH_BASE = 1 << 17;
    public static final int RAIL_NEIGHBOR_MATCH_MASK = 0x1f << 17;
    public static final int OBSERVER_UPDATE_SUPPRESSION = 1 << 22;
    public static final int TNT_UPDATE_SUPPRESSION = 1 << 23;
    public static final int WALL_AUXILIARY_SCHEMA = 1 << 26;
    public static final int WALL_UP = 1 << 27;
    public static final int RAIL_POWERED_MASK = 1 << 4;
    public static final int RAIL_PROTECTION_FLAG_POWERED = 1 << 3;
    public static final int RAIL_PROTECTION_FLAG_REGULAR = 1 << 4;
    public static final int Y_PRECISION_BITS = 8;
    public static final int Y_PRECISION_SCALE = 1 << Y_PRECISION_BITS;
    public static final int Y_PRECISION_MASK = Y_PRECISION_SCALE - 1;

    private EasyPlaceExtraProtocol() {
    }

    public static int decode(Vec3 hitPos, BlockPos clickedPos) {
        return decodeDimension(hitPos.x - clickedPos.getX());
    }

    public static int decodeZ(Vec3 hitPos, BlockPos clickedPos) {
        return decodeDimension(hitPos.z - clickedPos.getZ());
    }

    public static int decodeY(Vec3 hitPos, BlockPos clickedPos) {
        return decodeDimension(hitPos.y - clickedPos.getY());
    }

    public static Vec3 encodeY(BlockPos pos, Vec3 hitVec) {
        int precisionValue = quantizeRelativeY(hitVec.y - pos.getY());
        return new Vec3(
                hitVec.x,
                pos.getY() + 2.0 + precisionValue * 2.0,
                hitVec.z
        );
    }

    public static int quantizeRelativeY(double relativeHitY) {
        double clamped = Math.max(0.0, Math.min(1.0, relativeHitY));
        int value = Math.min(
                Y_PRECISION_MASK,
                (int) Math.floor(clamped * Y_PRECISION_SCALE)
        );
        if(clamped <= 0.5) {
            value = Math.min(value, (Y_PRECISION_SCALE / 2) - 1);
        }
        return value;
    }

    public static double restoreRelativeY(int precisionValue) {
        int clamped = Math.max(0, Math.min(Y_PRECISION_MASK, precisionValue));
        return (clamped + 0.5) / Y_PRECISION_SCALE;
    }

    private static int decodeDimension(double relativeHit) {
        if(relativeHit < 2.0) return -1;
        return ((int) relativeHit - 2) >>> 1;
    }

    public static boolean isExtra(int protocolValue) {
        return protocolValue >= 0 && (protocolValue & EXTRA_MARKER) != 0;
    }

    public static int toRawValue(int extraProtocolValue) {
        return ((extraProtocolValue & 0b1111_0000) >>> 1) | (extraProtocolValue & 0b0000_0111);
    }

    public static int toExtraValue(int rawProtocolValue) {
        return ((rawProtocolValue & 0b0111_1000) << 1)
                | (rawProtocolValue & 0b0000_0111)
                | EXTRA_MARKER;
    }

    public static boolean requestsRailUpdateSuppression(int auxiliaryValue) {
        return auxiliaryValue >= 0 && (auxiliaryValue & RAIL_UPDATE_SUPPRESSION) != 0;
    }

    public static boolean requestsWallUpdateSuppression(int auxiliaryValue) {
        return auxiliaryValue >= 0 && (auxiliaryValue & WALL_UPDATE_SUPPRESSION) != 0;
    }

    public static int wallNeighborMatchMask(int auxiliaryValue) {
        if(auxiliaryValue < 0) return 0;
        return (auxiliaryValue & WALL_NEIGHBOR_MATCH_MASK) >>> 11;
    }

    public static boolean requestsObserverUpdateSuppression(int auxiliaryValue) {
        return auxiliaryValue >= 0 && (auxiliaryValue & OBSERVER_UPDATE_SUPPRESSION) != 0;
    }

    public static boolean requestsTntUpdateSuppression(int auxiliaryValue) {
        return auxiliaryValue >= 0 && (auxiliaryValue & TNT_UPDATE_SUPPRESSION) != 0;
    }

    public static int railNeighborMatchMask(int auxiliaryValue) {
        if(auxiliaryValue < 0) return 0;
        return (auxiliaryValue & RAIL_NEIGHBOR_MATCH_MASK) >>> 17;
    }

    public static boolean requestsRailUpdateSuppression(
            net.minecraft.world.level.block.Block block,
            int rawValue
    ) {
        if(block instanceof PoweredRailBlock || block instanceof DetectorRailBlock) {
            return (rawValue & RAIL_PROTECTION_FLAG_POWERED) != 0;
        }
        if(block instanceof RailBlock) {
            return (rawValue & RAIL_PROTECTION_FLAG_REGULAR) != 0;
        }
        return false;
    }

    public static boolean wallUp(int auxiliaryValue) {
        if(auxiliaryValue < 0) return false;
        if((auxiliaryValue & WALL_AUXILIARY_SCHEMA) != 0) {
            return (auxiliaryValue & WALL_UP) != 0;
        }
        return (auxiliaryValue & 0x1) != 0;
    }
}
