package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class EasyPlacePistonYPayloadTest extends EasyPlaceTestSupport {
    private static final BlockPos BLOCK_POS = new BlockPos(-17, 70, 31);
    private static final BlockPos ORIGIN = new BlockPos(4, 64, 8);

    private EasyPlacePistonYPayloadTest() {
    }

    public static int run() {
        EasyPlacePistonYPayloadTest test = new EasyPlacePistonYPayloadTest();
        test.checkFloatBudget();
        test.checkPayloadRoundTrip();
        test.checkWireRoundTrip();
        test.checkSchemaValidation();
        test.checkPacketSnapshot();
        test.checkGuardCoverage();
        test.checkQcRelay();
        return test.checks();
    }

    private void checkFloatBudget() {
        long maxOffset = 2L + 2L * EasyPlaceExtraProtocol.Y_PAYLOAD_MASK;
        check(
                maxOffset == (1L << 22),
                "the maximum Y payload must encode to an offset of exactly 2^22"
        );
        check(
                maxOffset <= (1L << 24),
                "the Y payload must stay inside the exact-integer range of a binary32 float offset"
        );
        check(
                (EasyPlaceExtraProtocol.PISTON_PROTECTION_MASK
                        << EasyPlaceExtraProtocol.Y_PISTON_MASK_SHIFT)
                        < EasyPlaceExtraProtocol.Y_PISTON_SCHEMA,
                "the piston mask must not overlap the Y schema bit"
        );
    }

    private void checkPayloadRoundTrip() {
        for(int mask : new int[]{0, 1, 0x5a3, EasyPlaceExtraProtocol.PISTON_PROTECTION_MASK}) {
            for(int precision : new int[]{0, 0x53, 0x7f, 0xff}) {
                int payload = EasyPlaceExtraProtocol.encodeYPistonPayload(precision, mask);
                check(
                        (payload & EasyPlaceExtraProtocol.Y_PRECISION_MASK) == precision,
                        "Y payload must preserve the 8-bit hit height"
                );
                check(
                        EasyPlaceExtraProtocol.decodeYPiston(payload)
                                == (mask & EasyPlaceExtraProtocol.PISTON_PROTECTION_MASK),
                        "Y payload must round trip the complete 12-bit piston mask"
                );
                check(
                        EasyPlaceExtraProtocol.hasYPistonSchema(payload) == (mask != 0),
                        "the Y schema bit must only appear when piston data is present"
                );
                check(
                        (payload & ~EasyPlaceExtraProtocol.Y_PAYLOAD_MASK) == 0,
                        "the Y payload must not use bits outside the declared layout"
                );
            }
        }
    }

    private void checkWireRoundTrip() {
        Vec3 original = new Vec3(2.5, 70.73, 6.5);
        int expectedPrecision = EasyPlaceExtraProtocol.quantizeRelativeY(0.73);
        for(int mask : new int[]{0, 1, 0x5a3, EasyPlaceExtraProtocol.PISTON_PROTECTION_MASK}) {
            Vec3 encoded = EasyPlaceExtraProtocol.encodeY(BLOCK_POS, original, mask);
            Vec3 wire = throughWire(encoded, BLOCK_POS);
            int packedY = EasyPlaceExtraProtocol.decodeY(wire, BLOCK_POS);
            check(
                    (packedY & EasyPlaceExtraProtocol.Y_PRECISION_MASK) == expectedPrecision,
                    "the 8-bit hit height must survive the binary32 network offset"
            );
            check(
                    EasyPlaceExtraProtocol.decodeYPiston(packedY)
                            == (mask & EasyPlaceExtraProtocol.PISTON_PROTECTION_MASK),
                    "the piston mask must survive the binary32 network offset"
            );
            if(mask == 0) {
                check(
                        encoded.equals(EasyPlaceExtraProtocol.encodeY(BLOCK_POS, original)),
                        "a packet without piston data must keep the legacy Y encoding bit-identical"
                );
            }
        }
    }

    private void checkSchemaValidation() {
        int precision = EasyPlaceExtraProtocol.quantizeRelativeY(0.73);
        int plain = EasyPlaceExtraProtocol.encodeYPistonPayload(precision, 0);
        check(
                !EasyPlaceExtraProtocol.hasYPistonSchema(plain)
                        && EasyPlaceExtraProtocol.decodeYPiston(plain) == 0,
                "a plain precision value must not be mistaken for piston data"
        );
        check(
                EasyPlaceExtraProtocol.decodeYPiston(-1) == 0,
                "an unencoded Y channel must not request piston protection"
        );
        int schemaOnly = EasyPlaceExtraProtocol.Y_PISTON_SCHEMA | precision;
        check(
                EasyPlaceExtraProtocol.hasYPistonSchema(schemaOnly)
                        && EasyPlaceExtraProtocol.decodeYPiston(schemaOnly) == 0,
                "a schema bit without mask bits must decode to an empty mask"
        );
        int oversized = EasyPlaceExtraProtocol.encodeYPistonPayload(
                precision,
                EasyPlaceExtraProtocol.PISTON_PROTECTION_MASK | 0x7000
        );
        check(
                EasyPlaceExtraProtocol.decodeYPiston(oversized)
                        == EasyPlaceExtraProtocol.PISTON_PROTECTION_MASK,
                "piston mask bits outside the 12-bit field must be discarded"
        );
    }

    private void checkPacketSnapshot() {
        int rawValue = 0x37;
        int railNeighborMask = 0b10101;
        int auxiliaryValue = EasyPlaceExtraProtocol.RAIL_UPDATE_SUPPRESSION
                | (railNeighborMask << 17)
                | EasyPlaceExtraProtocol.AUXILIARY_SCHEMA;
        int pistonMask = EasyPlaceExtraProtocol.PISTON_TARGET_MATCH
                | (1 << (Direction.NORTH.ordinal() + 1))
                | (1 << 11);
        int precision = EasyPlaceExtraProtocol.quantizeRelativeY(0.73);
        int payload = EasyPlaceExtraProtocol.encodeYPistonPayload(precision, pistonMask);
        Vec3 location = new Vec3(
                BLOCK_POS.getX()
                        + (EasyPlaceExtraProtocol.toExtraValue(rawValue) << 1) + 2.0,
                BLOCK_POS.getY() + 2.0 + payload * 2.0,
                (auxiliaryValue << 1) + 2.0
        );

        EasyPlacePacketRequest.Snapshot snapshot =
                EasyPlacePacketRequest.decode(location, BLOCK_POS, Direction.UP);
        check(snapshot != null, "the packet-level decoder must accept a piston-carrying packet");
        check(
                snapshot.rawValue() == rawValue,
                "the piston payload must not disturb the X raw channel"
        );
        check(
                snapshot.auxiliaryValue()
                        == (auxiliaryValue & ~EasyPlaceExtraProtocol.AUXILIARY_SCHEMA),
                "the piston payload must not disturb the fixed-origin Z auxiliary channel"
        );
        check(
                snapshot.yPrecisionValue() == precision,
                "the piston payload must preserve the low 8 bits for hit-height restoration"
        );
        check(
                snapshot.pistonProtectionMask() == pistonMask,
                "the packet snapshot must carry the piston mask through the placement lifecycle"
        );
        EasyPlacePlacementRequest.Snapshot placement = snapshot.toPlacementSnapshot();
        check(
                placement != null && placement.pistonProtectionMask() == pistonMask,
                "the placement snapshot must forward the piston mask unchanged"
        );
        check(
                EasyPlaceExtraProtocol.railNeighborMatchMask(snapshot.auxiliaryValue())
                        == railNeighborMask,
                "rail neighbour protection must remain intact alongside the piston mask"
        );
    }

    private void checkGuardCoverage() {
        EasyPlaceUpdateSuppressionGuard.beginPlacement(ORIGIN);
        try {
            int mask = EasyPlaceExtraProtocol.PISTON_TARGET_MATCH
                    | (1 << (Direction.NORTH.ordinal() + 1));
            EasyPlaceUpdateSuppressionGuard.requestPistonProtection(mask);
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            EasyPlaceUpdateSuppressionGuard.Family.PISTON, ORIGIN
                    ),
                    "the target match bit must protect the placement position"
            );
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            EasyPlaceUpdateSuppressionGuard.Family.PISTON,
                            ORIGIN.relative(Direction.NORTH)
                    ),
                    "a matching face neighbour must be protected"
            );
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            EasyPlaceUpdateSuppressionGuard.Family.PISTON,
                            ORIGIN.relative(Direction.SOUTH)
                    ),
                    "an unmatched face neighbour must keep vanilla updates"
            );
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            EasyPlaceUpdateSuppressionGuard.Family.PISTON,
                            ORIGIN.above(2)
                    ),
                    "piston protection must not extend beyond the direct neighbourhood"
            );
        } finally {
            EasyPlaceUpdateSuppressionGuard.endPlacement();
        }

        EasyPlaceUpdateSuppressionGuard.beginPlacement(ORIGIN);
        try {
            EasyPlaceUpdateSuppressionGuard.requestPistonProtection(0);
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            EasyPlaceUpdateSuppressionGuard.Family.PISTON, ORIGIN
                    ),
                    "a zero mask must not arm the PISTON family"
            );
        } finally {
            EasyPlaceUpdateSuppressionGuard.endPlacement();
        }
    }

    private void checkQcRelay() {
        BlockPos piston = ORIGIN.offset(1, -1, 0);
        check(
                EasyPlaceExtraProtocol.pistonQcTargetMatchBit(ORIGIN, piston) != 0,
                "a diagonal second-hop piston must map to a QC mask bit"
        );
        check(
                EasyPlaceUpdateSuppressionGuard.hasPoweredQcRelay(
                        ORIGIN, piston, pos -> true, pos -> true
                ),
                "a powered conductor above the piston must relay QC protection"
        );
        check(
                !EasyPlaceUpdateSuppressionGuard.hasPoweredQcRelay(
                        ORIGIN, piston, pos -> false, pos -> true
                ),
                "a non-conductor above the piston must not relay QC protection"
        );
        check(
                !EasyPlaceUpdateSuppressionGuard.hasPoweredQcRelay(
                        ORIGIN, piston, pos -> true, pos -> false
                ),
                "an unpowered block above the piston must not relay QC protection"
        );
        check(
                !EasyPlaceUpdateSuppressionGuard.hasPoweredQcRelay(
                        ORIGIN, ORIGIN.offset(2, 0, 0), pos -> true, pos -> true
                ),
                "a piston outside the QC shell must not receive relay protection"
        );
    }

    private static Vec3 throughWire(Vec3 encoded, BlockPos pos) {
        float wireX = (float) (encoded.x - pos.getX());
        float wireY = (float) (encoded.y - pos.getY());
        float wireZ = (float) (encoded.z - pos.getZ());
        return new Vec3(
                pos.getX() + (double) wireX,
                pos.getY() + (double) wireY,
                pos.getZ() + (double) wireZ
        );
    }
}
