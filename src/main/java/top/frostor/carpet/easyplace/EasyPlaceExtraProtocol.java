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
    /**
     * Marks a deliberately encoded Z auxiliary field. Both client and server are released in
     * lockstep, so this schema bit replaces the fragile "decoded value >= 0" test and lets the
     * server pick the correct Z anchor without guessing from the X channel.
     */
    public static final int AUXILIARY_SCHEMA = 1 << 24;
    /**
     * Relative-aux mode keeps only the low 24 bits of the auxiliary value in Z and moves the
     * three high bits into the X payload. A 24-bit payload keeps the binary32 wire offset at or
     * below 2^25, and every even integer up to 2^25 is exact; a 25-bit payload already loses its
     * lowest bit. The in-memory auxiliary layout stays unchanged, so every guard and state
     * adapter keeps reading bits 24/26/27.
     */
    public static final int AUX_LOW_MASK = 0x00ff_ffff;
    public static final int X_AUX_SCHEMA_BIT = 1 << 8;
    public static final int X_WALL_SCHEMA_BIT = 1 << 9;
    public static final int X_WALL_UP_BIT = 1 << 10;
    public static final int X_AUX_HIGH_MASK =
            X_AUX_SCHEMA_BIT | X_WALL_SCHEMA_BIT | X_WALL_UP_BIT;
    public static final int RAIL_POWERED_MASK = 1 << 4;
    public static final int RAIL_PROTECTION_FLAG_POWERED = 1 << 3;
    public static final int RAIL_PROTECTION_FLAG_REGULAR = 1 << 4;
    public static final int Y_PRECISION_BITS = 8;
    public static final int Y_PRECISION_SCALE = 1 << Y_PRECISION_BITS;
    public static final int Y_PRECISION_MASK = Y_PRECISION_SCALE - 1;

    public static final int PISTON_TARGET_MATCH = 1;
    public static final int PISTON_NEIGHBOR_MATCH_MASK = 0x3f << 1;
    public static final int PISTON_QC_TARGET_MATCH_BASE = 1 << 7;
    public static final int PISTON_QC_TARGET_MATCH_MASK = 0x1f << 7;
    public static final int PISTON_PROTECTION_MASK = 0xfff;
    public static final int PISTON_PROTECTION_BITS = 12;
    private static final BlockPos[] PISTON_QC_TARGET_OFFSETS = {
            new BlockPos(0, -2, 0),
            new BlockPos(0, -1, -1),
            new BlockPos(0, -1, 1),
            new BlockPos(-1, -1, 0),
            new BlockPos(1, -1, 0)
    };

    /**
     * Y carries the 8-bit hit height in bits 0-7. Bits 8-19 carry the 12-bit piston protection
     * mask from the archived placement-context branch, and bit20 marks that the mask is present.
     * The hit location travels as a binary32 float offset relative to the packet blockPos, so the
     * encoded offset {@code 2 + 2 * payload} must stay below 2^24. The maximum payload here is
     * 2^21 - 1, which leaves two bits of headroom; a packet without piston data still encodes
     * exactly the same Y value as the plain precision layout.
     */
    public static final int Y_PISTON_MASK_SHIFT = Y_PRECISION_BITS;
    public static final int Y_PISTON_SCHEMA = 1 << 20;
    public static final int Y_PAYLOAD_MASK = Y_PRECISION_MASK
            | (PISTON_PROTECTION_MASK << Y_PISTON_MASK_SHIFT)
            | Y_PISTON_SCHEMA;

    private EasyPlaceExtraProtocol() {
    }

    /**
     * X raw value relative to the packet's {@code BlockHitResult.blockPos}. Compiled clients
     * re-anchor their payload to that position before queueing the click, so this is the only
     * anchor the server has to support; no alternative anchor is attempted.
     */
    public static int decode(Vec3 hitPos, BlockPos clickedPos) {
        return decodeDimension(hitPos.x - clickedPos.getX());
    }

    public static int decodeZ(Vec3 hitPos, BlockPos clickedPos) {
        return decodeDimension(hitPos.z - clickedPos.getZ());
    }

    /**
     * The Z auxiliary field is anchored at world Z = 0 rather than at the click position. Client
     * encoding paths do not all agree on which block position the packet will carry (Litematica's
     * main/legacy/printer paths pass different positions), so a relative anchor made the decoded
     * auxiliary value shift by one bit depending on the hit face.
     */
    public static int decodeAuxiliary(Vec3 hitPos) {
        return decodeDimension(hitPos.z);
    }

    /**
     * Low 24 bits of the auxiliary field, anchored at the packet {@code blockPos}.
     *
     * <p>Whether an auxiliary field is present at all is signalled by
     * {@link #X_AUX_SCHEMA_BIT} in the X payload, not by this value.</p>
     *
     * @return the clamped 24-bit payload, or {@code -1} when the offset holds no payload
     */
    public static int decodeRelativeAuxiliary(Vec3 hitPos, BlockPos clickedPos) {
        int value = decodeDimension(hitPos.z - clickedPos.getZ());
        return value < 0 ? -1 : value & AUX_LOW_MASK;
    }

    /**
     * Reassembles the in-memory auxiliary value from the split wire fields.
     *
     * <p>The three high bits travel in the X payload, so the value every consumer sees is
     * identical to the legacy layout.</p>
     */
    public static int joinAuxiliary(int xPayload, int zPayload) {
        if(zPayload < 0) {
            return -1;
        }
        int auxiliaryValue = zPayload & AUX_LOW_MASK;
        if((xPayload & X_AUX_SCHEMA_BIT) != 0) auxiliaryValue |= AUXILIARY_SCHEMA;
        if((xPayload & X_WALL_SCHEMA_BIT) != 0) auxiliaryValue |= WALL_AUXILIARY_SCHEMA;
        if((xPayload & X_WALL_UP_BIT) != 0) auxiliaryValue |= WALL_UP;
        return auxiliaryValue;
    }

    /** Extracts the three auxiliary bits that travel in the X payload. */
    public static int splitAuxiliaryHigh(int auxiliaryValue) {
        int high = 0;
        if((auxiliaryValue & AUXILIARY_SCHEMA) != 0) high |= X_AUX_SCHEMA_BIT;
        if((auxiliaryValue & WALL_AUXILIARY_SCHEMA) != 0) high |= X_WALL_SCHEMA_BIT;
        if((auxiliaryValue & WALL_UP) != 0) high |= X_WALL_UP_BIT;
        return high;
    }

    public static int decodeY(Vec3 hitPos, BlockPos clickedPos) {
        return decodeDimension(hitPos.y - clickedPos.getY());
    }

    public static Vec3 encodeY(BlockPos pos, Vec3 hitVec) {
        return encodeY(pos, hitVec, 0);
    }

    public static Vec3 encodeY(BlockPos pos, Vec3 hitVec, int pistonProtectionMask) {
        int precisionValue = quantizeRelativeY(hitVec.y - pos.getY());
        int payload = encodeYPistonPayload(precisionValue, pistonProtectionMask);
        return new Vec3(
                hitVec.x,
                pos.getY() + 2.0 + payload * 2.0,
                hitVec.z
        );
    }

    public static int encodeYPistonPayload(int precisionValue, int pistonProtectionMask) {
        int mask = pistonProtectionMask & PISTON_PROTECTION_MASK;
        if(mask == 0) {
            return precisionValue & Y_PRECISION_MASK;
        }
        return (precisionValue & Y_PRECISION_MASK)
                | (mask << Y_PISTON_MASK_SHIFT)
                | Y_PISTON_SCHEMA;
    }

    public static boolean hasYPistonSchema(int packedY) {
        return packedY >= 0 && (packedY & Y_PISTON_SCHEMA) != 0;
    }

    public static int decodeYPiston(int packedY) {
        if(!hasYPistonSchema(packedY)) {
            return 0;
        }
        return (packedY >>> Y_PISTON_MASK_SHIFT) & PISTON_PROTECTION_MASK;
    }

    public static int pistonNeighborMatchMask(int pistonProtectionMask) {
        if(pistonProtectionMask < 0) return 0;
        return (pistonProtectionMask & PISTON_NEIGHBOR_MATCH_MASK) >>> 1;
    }

    public static BlockPos[] pistonQcTargetOffsets() {
        return PISTON_QC_TARGET_OFFSETS.clone();
    }

    public static int pistonQcTargetMatchBit(BlockPos placementPos, BlockPos pos) {
        for(int index = 0; index < PISTON_QC_TARGET_OFFSETS.length; index++) {
            BlockPos offset = PISTON_QC_TARGET_OFFSETS[index];
            if(pos.equals(placementPos.offset(
                    offset.getX(),
                    offset.getY(),
                    offset.getZ()
            ))) {
                return 1 << (
                        Integer.numberOfTrailingZeros(PISTON_QC_TARGET_MATCH_BASE)
                                + index
                );
            }
        }
        return 0;
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
        int payload = (int) relativeHit - 2;
        // A payload shifted by one block yields a negative value; the old unconditional
        // ">>> 1" turned that into 0x7fffffff and silently produced a bogus raw value.
        return payload < 0 ? -1 : payload >>> 1;
    }

    public static boolean isExtra(int protocolValue) {
        return protocolValue >= 0 && (protocolValue & EXTRA_MARKER) != 0;
    }

    public static boolean hasAuxiliarySchema(int auxiliaryValue) {
        return auxiliaryValue >= 0 && (auxiliaryValue & AUXILIARY_SCHEMA) != 0;
    }

    public static int stripAuxiliarySchema(int auxiliaryValue) {
        if(!hasAuxiliarySchema(auxiliaryValue)) {
            return -1;
        }
        return auxiliaryValue & ~AUXILIARY_SCHEMA;
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
        if(rawValue < 0 || block == null) {
            return false;
        }
        if(block instanceof PoweredRailBlock || block instanceof DetectorRailBlock) {
            return (rawValue & RAIL_PROTECTION_FLAG_POWERED) != 0;
        }
        if(block instanceof RailBlock) {
            return (rawValue & RAIL_PROTECTION_FLAG_REGULAR) != 0;
        }
        return false;
    }

    /**
     * Single source of truth for the RAIL family request: the auxiliary Z field carries the
     * explicit suppression flag and the neighbour match mask, while the raw X field carries a
     * rail-only fallback for clients that cannot send the Z field.
     */
    public static boolean requestsRailProtection(
            net.minecraft.world.level.block.Block block,
            int rawValue,
            int auxiliaryValue
    ) {
        return requestsRailUpdateSuppression(auxiliaryValue)
                || railNeighborMatchMask(auxiliaryValue) != 0
                || requestsRailUpdateSuppression(block, rawValue);
    }

    public static boolean wallUp(int auxiliaryValue) {
        if(auxiliaryValue < 0) return false;
        if((auxiliaryValue & WALL_AUXILIARY_SCHEMA) != 0) {
            return (auxiliaryValue & WALL_UP) != 0;
        }
        return (auxiliaryValue & 0x1) != 0;
    }
}
