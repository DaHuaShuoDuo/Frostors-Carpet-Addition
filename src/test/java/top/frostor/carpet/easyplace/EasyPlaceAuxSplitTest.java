package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Headless checks for the split auxiliary layout.
 *
 * <p>The relative-aux mode carries only the low 24 bits of the auxiliary value in Z and moves the
 * schema, wall-schema and wall-up bits into the X payload. Twenty-four bits keep the binary32 wire
 * offset at or below 2^25, where every even integer is exact; a 25-bit payload already does not
 * round trip.</p>
 */
public final class EasyPlaceAuxSplitTest extends EasyPlaceTestSupport {
    private static final BlockPos[] POSITIONS = {
            new BlockPos(0, 64, 0),
            new BlockPos(-17, 70, -1),
            new BlockPos(4, 64, 30000001),
            new BlockPos(4, 64, -29999999),
            new BlockPos(4, 64, 16777215),
            new BlockPos(4, 64, 16777216)
    };

    private EasyPlaceAuxSplitTest() {
    }

    public static int run() {
        EasyPlaceAuxSplitTest test = new EasyPlaceAuxSplitTest();
        test.checkSplitJoinRoundTrip();
        test.checkPayloadWidth();
        test.checkRelativeTransport();
        test.checkRawStillReadsLowByte();
        test.checkSnapshotModes();
        return test.checks();
    }

    /**
     * The snapshot decoder must only assemble the split layout when the client negotiated it;
     * the legacy path must not read a relative payload as a fixed-origin field.
     */
    private void checkSnapshotModes() {
        BlockPos pos = new BlockPos(-17, 70, 31);
        int raw = 0x2a;
        int auxiliaryValue = EasyPlaceExtraProtocol.AUX_LOW_MASK
                | EasyPlaceExtraProtocol.AUXILIARY_SCHEMA
                | EasyPlaceExtraProtocol.WALL_AUXILIARY_SCHEMA
                | EasyPlaceExtraProtocol.WALL_UP;
        int xPayload = EasyPlaceExtraProtocol.toExtraValue(raw)
                | EasyPlaceExtraProtocol.splitAuxiliaryHigh(auxiliaryValue);
        double absoluteX = pos.getX() + 2.0 + 2.0 * xPayload;
        double absoluteZ = pos.getZ() + 2.0
                + 2.0 * (auxiliaryValue & EasyPlaceExtraProtocol.AUX_LOW_MASK);
        Vec3 location = new Vec3(
                (double) pos.getX() + (float) (absoluteX - pos.getX()),
                pos.getY(),
                (double) pos.getZ() + (float) (absoluteZ - pos.getZ())
        );

        EasyPlacePacketRequest.Snapshot relative =
                EasyPlacePacketRequest.decode(location, pos, true);
        check(
                relative != null && relative.rawValue() == raw,
                "the relative layout must still carry the raw value"
        );
        check(
                relative.auxiliaryValue() == auxiliaryValue,
                "the relative layout must reassemble the in-memory auxiliary value"
        );

        EasyPlacePacketRequest.Snapshot legacy =
                EasyPlacePacketRequest.decode(location, pos, false);
        check(
                legacy == null || legacy.auxiliaryValue() != auxiliaryValue,
                "the legacy decoder must not interpret a relative payload"
        );
    }

    private void checkSplitJoinRoundTrip() {
        int[] samples = {
                0,
                1,
                EasyPlaceExtraProtocol.CRAFTER_SLOT_MASK,
                EasyPlaceExtraProtocol.RAIL_UPDATE_SUPPRESSION,
                EasyPlaceExtraProtocol.WALL_UPDATE_SUPPRESSION,
                0x3f << 11,
                0x1f << 17,
                EasyPlaceExtraProtocol.OBSERVER_UPDATE_SUPPRESSION,
                EasyPlaceExtraProtocol.TNT_UPDATE_SUPPRESSION,
                EasyPlaceExtraProtocol.AUXILIARY_SCHEMA,
                EasyPlaceExtraProtocol.WALL_AUXILIARY_SCHEMA,
                EasyPlaceExtraProtocol.WALL_UP,
                EasyPlaceExtraProtocol.AUXILIARY_SCHEMA
                        | EasyPlaceExtraProtocol.WALL_AUXILIARY_SCHEMA
                        | EasyPlaceExtraProtocol.WALL_UP,
                EasyPlaceExtraProtocol.AUX_LOW_MASK,
                EasyPlaceExtraProtocol.AUX_LOW_MASK
                        | EasyPlaceExtraProtocol.AUXILIARY_SCHEMA
                        | EasyPlaceExtraProtocol.WALL_AUXILIARY_SCHEMA
                        | EasyPlaceExtraProtocol.WALL_UP
        };
        for(int auxiliaryValue : samples) {
            int high = EasyPlaceExtraProtocol.splitAuxiliaryHigh(auxiliaryValue);
            check(
                    (high & ~EasyPlaceExtraProtocol.X_AUX_HIGH_MASK) == 0,
                    "the split high bits must stay inside the X payload bits"
            );
            int joined = EasyPlaceExtraProtocol.joinAuxiliary(
                    high,
                    auxiliaryValue & EasyPlaceExtraProtocol.AUX_LOW_MASK
            );
            check(
                    joined == auxiliaryValue,
                    "aux 0x" + Integer.toHexString(auxiliaryValue)
                            + " must round trip through the split layout"
            );
        }
        check(
                EasyPlaceExtraProtocol.joinAuxiliary(
                        EasyPlaceExtraProtocol.X_AUX_SCHEMA_BIT,
                        -1
                ) == -1,
                "a missing relative payload must stay absent instead of becoming all ones"
        );
    }

    private void checkPayloadWidth() {
        check(
                EasyPlaceExtraProtocol.AUX_LOW_MASK == 0x00ff_ffff,
                "the relative Z payload must be exactly 24 bits"
        );
        check(
                (EasyPlaceExtraProtocol.X_AUX_HIGH_MASK & 0xff) == 0,
                "the moved auxiliary bits must not disturb the raw byte"
        );
        check(
                EasyPlaceExtraProtocol.X_AUX_HIGH_MASK == 0x700,
                "the three moved bits must occupy X payload bits 8-10"
        );

        int maxPayload = EasyPlaceExtraProtocol.AUX_LOW_MASK;
        double maxOffset = 2.0 + 2.0 * maxPayload;
        check(
                maxOffset == (1 << 25),
                "a full 24-bit payload must land exactly on 2^25"
        );
        check(
                (double) (float) maxOffset == maxOffset,
                "the 24-bit offset bound must still be exact in binary32"
        );
        double overflowing = 2.0 + 2.0 * (maxPayload + 1);
        check(
                (double) (float) overflowing != overflowing,
                "a 25-bit payload must already lose precision"
        );
    }

    private void checkRelativeTransport() {
        int[] payloads = {
                0,
                1,
                EasyPlaceExtraProtocol.CRAFTER_SLOT_MASK,
                EasyPlaceExtraProtocol.AUX_LOW_MASK,
                EasyPlaceExtraProtocol.AUX_LOW_MASK - 1,
                0x00c0_0000,
                0x0080_0002
        };
        for(BlockPos pos : POSITIONS) {
            for(int payload : payloads) {
                double absoluteZ = pos.getZ() + 2.0 + payload * 2.0;
                float wire = (float) (absoluteZ - pos.getZ());
                // Add in double: an int + float sum would round the reconstructed absolute
                // coordinate in binary32 before the decoder ever sees it.
                Vec3 received = new Vec3(0.0, pos.getY(), (double) pos.getZ() + wire);
                check(
                        EasyPlaceExtraProtocol.decodeRelativeAuxiliary(received, pos) == payload,
                        "payload 0x" + Integer.toHexString(payload)
                                + " must survive the wire round trip at z=" + pos.getZ()
                );
            }
        }
    }

    private void checkRawStillReadsLowByte() {
        for(int raw = 0; raw <= 0x7f; raw++) {
            int payload = EasyPlaceExtraProtocol.toExtraValue(raw)
                    | EasyPlaceExtraProtocol.X_AUX_HIGH_MASK;
            check(
                    EasyPlaceExtraProtocol.isExtra(payload),
                    "raw " + raw + " must stay marked with the high payload bits set"
            );
            check(
                    EasyPlaceExtraProtocol.toRawValue(payload) == raw,
                    "raw " + raw + " must be read from the low byte only"
            );
        }
    }
}
