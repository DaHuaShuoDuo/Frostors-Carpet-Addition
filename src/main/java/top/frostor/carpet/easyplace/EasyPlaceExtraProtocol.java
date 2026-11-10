package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
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
    public static final int OBSERVER_UPDATE_SUPPRESSION = 1 << 17;
    public static final int TNT_UPDATE_SUPPRESSION = 1 << 18;
    public static final int DETECTOR_RAIL_POWERED_MASK = 1 << 4;

    private EasyPlaceExtraProtocol() {
    }

    public static int decode(Vec3 hitPos, BlockPos clickedPos) {
        return decodeDimension(hitPos.x - clickedPos.getX());
    }

    public static int decodeZ(Vec3 hitPos, BlockPos clickedPos) {
        return decodeDimension(hitPos.z - clickedPos.getZ());
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
}
