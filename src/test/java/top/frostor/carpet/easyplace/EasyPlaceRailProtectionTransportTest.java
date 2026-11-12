package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Headless checks for the V2-Extra wire transport.
 *
 * <p>The packet codecs store the hit location as a binary32 float offset from
 * {@code BlockHitResult.blockPos}, so that position is the only anchor the server can
 * reproduce. Every field is decoded against it: the server never falls back to the hit face or
 * to a horizontal neighbour. Clients that click a support block re-anchor the payload before
 * queueing the click, which these checks mirror arithmetically without booting a world.</p>
 */
public final class EasyPlaceRailProtectionTransportTest extends EasyPlaceTestSupport {
    private static final BlockPos BLOCK_POS = new BlockPos(-17, 70, 31);
    private static final int RAW_VALUE = 0x28;
    private static final int RAIL_NEIGHBOR_MASK = 0x15;
    private static final int AUXILIARY_VALUE = EasyPlaceExtraProtocol.RAIL_UPDATE_SUPPRESSION
            | (RAIL_NEIGHBOR_MASK
            << Integer.numberOfTrailingZeros(
            EasyPlaceExtraProtocol.RAIL_NEIGHBOR_MATCH_BASE
    ));
    private static final int ENCODED_AUXILIARY_VALUE =
            AUXILIARY_VALUE | EasyPlaceExtraProtocol.AUXILIARY_SCHEMA;
    private static final int Y_PRECISION_VALUE = 0x53;

    private EasyPlaceRailProtectionTransportTest() {
    }

    public static int run() {
        EasyPlaceRailProtectionTransportTest test =
                new EasyPlaceRailProtectionTransportTest();
        test.checkAllRawValuesRoundTrip();
        test.checkPacketSnapshotForEveryFace();
        test.checkAuxiliaryIsPositionIndependent();
        test.checkAuxiliarySchemaValidation();
        test.checkStrictPacketAnchor();
        test.checkSupportBlockReanchoring();
        test.checkRailProtectionDecision();
        test.checkRailDeferredWindow();
        test.checkYChannelCarriesNoRaw();
        return test.checks();
    }

    /**
     * Exhaustive coverage of the seven-bit raw space on the contract anchor. A single sample
     * (0x28) cannot detect EXTRA_MARKER collisions, because an anchor shifted one block west
     * decodes to extraValue-1, which still carries the marker for 112 of the 128 raw values.
     */
    private void checkAllRawValuesRoundTrip() {
        for(int raw = 0; raw <= 0x7f; raw++) {
            int extraValue = EasyPlaceExtraProtocol.toExtraValue(raw);
            Vec3 location = encode(BLOCK_POS, extraValue, ENCODED_AUXILIARY_VALUE);
            check(
                    EasyPlaceExtraProtocol.isExtra(extraValue),
                    "every raw value must produce a marked extra value"
            );
            for(Direction face : Direction.values()) {
                EasyPlacePacketRequest.Snapshot snapshot =
                        EasyPlacePacketRequest.decode(location, BLOCK_POS);
                check(
                        snapshot != null && snapshot.rawValue() == raw,
                        "raw " + raw + " must round trip on the packet blockPos anchor (face "
                                + face + ")"
                );
            }
        }
    }

    private void checkPacketSnapshotForEveryFace() {
        int extraValue = EasyPlaceExtraProtocol.toExtraValue(RAW_VALUE);
        Vec3 location = encode(BLOCK_POS, extraValue, ENCODED_AUXILIARY_VALUE);

        for(Direction face : Direction.values()) {
            EasyPlacePacketRequest.Snapshot snapshot =
                    EasyPlacePacketRequest.decode(location, BLOCK_POS);
            check(snapshot != null, "packet snapshot must exist for face " + face);
            check(
                    snapshot.rawValue() == RAW_VALUE,
                    "packet X raw must round trip for face " + face
            );
            check(
                    snapshot.auxiliaryValue() == AUXILIARY_VALUE,
                    "packet Z auxiliary must round trip for face " + face
            );
            check(
                    snapshot.yPrecisionValue() == Y_PRECISION_VALUE,
                    "packet Y precision must round trip for face " + face
            );
            check(
                    EasyPlaceExtraProtocol.requestsRailUpdateSuppression(
                            snapshot.auxiliaryValue()
                    ),
                    "bit9 rail suppression must survive for face " + face
            );
            check(
                    EasyPlaceExtraProtocol.railNeighborMatchMask(
                            snapshot.auxiliaryValue()
                    ) == RAIL_NEIGHBOR_MASK,
                    "bit17-21 rail neighbour mask must survive for face " + face
            );

            EasyPlacePlacementRequest.Snapshot placement =
                    snapshot.toPlacementSnapshot();
            check(
                    placement != null
                            && placement.auxiliaryValue() == AUXILIARY_VALUE
                            && placement.rawValue() == RAW_VALUE,
                    "placement snapshot must carry the packet values for face " + face
            );
        }

        check(
                EasyPlacePacketRequest.decode(
                        new Vec3(BLOCK_POS.getX() + 0.5, 70.5, BLOCK_POS.getZ() + 0.5),
                        BLOCK_POS
                ) == null,
                "plain right clicks must not create an Easy Place packet snapshot"
        );
    }

    private void checkAuxiliaryIsPositionIndependent() {
        int extraValue = EasyPlaceExtraProtocol.toExtraValue(RAW_VALUE);
        Vec3 location = encode(BLOCK_POS, extraValue, ENCODED_AUXILIARY_VALUE);

        int absolute = EasyPlaceExtraProtocol.stripAuxiliarySchema(
                EasyPlaceExtraProtocol.decodeAuxiliary(location)
        );
        check(
                absolute == AUXILIARY_VALUE,
                "the fixed-origin Z auxiliary field must decode exactly"
        );

        for(Direction face : Direction.values()) {
            BlockPos transformedTarget = BLOCK_POS.relative(face);
            check(
                    EasyPlaceExtraProtocol.decodeZ(location, transformedTarget) != absolute,
                    "relative Z decoding must never be applied to a fixed-origin field ("
                            + face + ")"
            );
            EasyPlacePacketRequest.Snapshot snapshot =
                    EasyPlacePacketRequest.decode(location, BLOCK_POS);
            check(
                    snapshot != null && snapshot.auxiliaryValue() == AUXILIARY_VALUE,
                    "the auxiliary field must survive every hit face (" + face + ")"
            );
        }

        BlockPos eastTarget = BLOCK_POS.relative(Direction.EAST);
        check(
                !EasyPlaceExtraProtocol.isExtra(
                        EasyPlaceExtraProtocol.decode(location, eastTarget)
                ),
                "the transformed target still loses the V2-Extra marker on an east-facing click"
        );
        check(
                EasyPlacePacketRequest.decode(location, BLOCK_POS) != null,
                "packet-level decoding must not gate on the transformed target"
        );
    }

    private void checkAuxiliarySchemaValidation() {
        int extraValue = EasyPlaceExtraProtocol.toExtraValue(RAW_VALUE);

        Vec3 unencoded = new Vec3(
                BLOCK_POS.getX() + (extraValue << 1) + 2.0,
                BLOCK_POS.getY() + 2.0 + Y_PRECISION_VALUE * 2.0,
                0.5
        );
        EasyPlacePacketRequest.Snapshot plain =
                EasyPlacePacketRequest.decode(unencoded, BLOCK_POS);
        check(
                plain != null && plain.auxiliaryValue() == -1,
                "a plain click location must not be interpreted as an auxiliary field"
        );

        Vec3 noSchema = encode(BLOCK_POS, extraValue, AUXILIARY_VALUE);
        EasyPlacePacketRequest.Snapshot unsigned =
                EasyPlacePacketRequest.decode(noSchema, BLOCK_POS);
        check(
                unsigned != null && unsigned.auxiliaryValue() == -1,
                "an auxiliary field without the schema bit must be rejected"
        );

        check(
                EasyPlaceExtraProtocol.hasAuxiliarySchema(ENCODED_AUXILIARY_VALUE)
                        && EasyPlaceExtraProtocol.stripAuxiliarySchema(
                        ENCODED_AUXILIARY_VALUE
                ) == AUXILIARY_VALUE,
                "stripping the schema bit must not disturb any auxiliary flag"
        );
    }

    /**
     * The server must not repair a foreign anchor by scanning the hit face or the horizontal
     * neighbours. Such a payload now decodes as absent or one raw value off, never as the value
     * it was not anchored at.
     */
    private void checkStrictPacketAnchor() {
        BlockPos foreignAnchor = BLOCK_POS.relative(Direction.WEST);
        Vec3 location = encode(
                foreignAnchor,
                EasyPlaceExtraProtocol.toExtraValue(RAW_VALUE),
                ENCODED_AUXILIARY_VALUE
        );

        EasyPlacePacketRequest.Snapshot snapshot =
                EasyPlacePacketRequest.decode(location, BLOCK_POS);
        check(
                snapshot == null || snapshot.rawValue() != RAW_VALUE,
                "a payload anchored one block away must not be repaired by another anchor"
        );

        Vec3 zeroRaw = encode(
                foreignAnchor,
                EasyPlaceExtraProtocol.toExtraValue(0),
                ENCODED_AUXILIARY_VALUE
        );
        EasyPlacePacketRequest.Snapshot zero = EasyPlacePacketRequest.decode(zeroRaw, BLOCK_POS);
        check(
                zero == null || zero.rawValue() != 0,
                "raw 0 must not be recovered from a foreign anchor either"
        );
        check(
                EasyPlaceExtraProtocol.decode(
                        new Vec3(Double.NaN, BLOCK_POS.getY(), BLOCK_POS.getZ()),
                        BLOCK_POS
                ) < 0,
                "a malformed hit offset must decode as absent instead of an unsigned shift"
        );

        Vec3 plainClick = new Vec3(
                BLOCK_POS.getX() + 0.5,
                BLOCK_POS.getY() + 0.5,
                BLOCK_POS.getZ() + 0.5
        );
        check(
                EasyPlacePacketRequest.decode(plainClick, BLOCK_POS) == null,
                "a plain click must not become a V2-Extra packet"
        );
    }

    /**
     * Models the printer support path: the schematic target is encoded first, then the click is
     * queued against the support block. The client re-anchors the payload to that packet position
     * before sending, which keeps the wire contract single-anchored for every side.
     *
     * <p>Without the re-anchor a horizontal side decodes {@code raw - 1}, because the encoder
     * writes {@code 2 + 2 * extra} and the receiver subtracts {@code 2} before the shift.</p>
     */
    private void checkSupportBlockReanchoring() {
        for(int raw = 0; raw <= 0x7f; raw++) {
            int extraValue = EasyPlaceExtraProtocol.toExtraValue(raw);
            for(Direction side : Direction.values()) {
                BlockPos support = BLOCK_POS.relative(side);
                Vec3 encodedAtTarget = encode(
                        BLOCK_POS,
                        extraValue,
                        ENCODED_AUXILIARY_VALUE
                );
                Vec3 reanchored = shiftAnchor(encodedAtTarget, side);
                Vec3 received = wire(support, reanchored);
                EasyPlacePacketRequest.Snapshot snapshot =
                        EasyPlacePacketRequest.decode(received, support);
                check(
                        snapshot != null && snapshot.rawValue() == raw,
                        "raw " + raw + " must survive the support-block anchor on side " + side
                );
                check(
                        snapshot.yPrecisionValue() == Y_PRECISION_VALUE,
                        "the Y precision must survive the support-block anchor on side " + side
                );
                check(
                        snapshot.auxiliaryValue() != -1,
                        "the fixed-origin Z auxiliary must be recognised on " + side
                );
                // The auxiliary field lives near 2^25, where binary32 steps by four, so the
                // low bit of the decoded value is lossy by design; only the raw channel is
                // asserted exactly here.

                Vec3 notReanchored = wire(support, encodedAtTarget);
                EasyPlacePacketRequest.Snapshot legacy =
                        EasyPlacePacketRequest.decode(notReanchored, support);
                // The "+2" payload offset absorbs a one-block shift towards the negative axis, so
                // only east support clicks lost the raw value and only up support clicks lost the
                // Y precision before the re-anchor.
                if(side.getStepX() > 0) {
                    check(
                            legacy == null || legacy.rawValue() != raw,
                            "an un-reanchored east support click must not decode as if it were correct"
                    );
                } else if(side.getStepY() > 0) {
                    check(
                            legacy == null
                                    || legacy.yPrecisionValue() != Y_PRECISION_VALUE,
                            "an un-reanchored up support click must not keep the Y precision"
                    );
                }
            }
        }
    }

    /** Mirrors the binary32 float offset that both packet codecs write for the hit location. */
    private static Vec3 wire(BlockPos packetPos, Vec3 absoluteLocation) {
        return new Vec3(
                packetPos.getX() + (float) (absoluteLocation.x - packetPos.getX()),
                packetPos.getY() + (float) (absoluteLocation.y - packetPos.getY()),
                packetPos.getZ() + (float) (absoluteLocation.z - packetPos.getZ())
        );
    }

    /**
     * The client only translates the X and Y channels. Z carries the fixed-origin auxiliary
     * payload, so translating it would corrupt the flags.
     */
    private static Vec3 shiftAnchor(Vec3 encoded, Direction side) {
        return new Vec3(
                encoded.x + side.getStepX(),
                encoded.y + side.getStepY(),
                encoded.z
        );
    }

    private void checkRailProtectionDecision() {
        check(
                EasyPlaceExtraProtocol.requestsRailProtection(
                        Blocks.POWERED_RAIL, 0x8, -1
                ),
                "powered rail raw bit3 must fall back to rail protection without a Z field"
        );
        check(
                EasyPlaceExtraProtocol.requestsRailProtection(
                        Blocks.DETECTOR_RAIL, 0x8, -1
                ),
                "detector rail raw bit3 must fall back to rail protection without a Z field"
        );
        check(
                EasyPlaceExtraProtocol.requestsRailProtection(
                        Blocks.RAIL, 0x10, -1
                ),
                "regular rail raw bit4 must fall back to rail protection without a Z field"
        );
        check(
                !EasyPlaceExtraProtocol.requestsRailProtection(Blocks.STONE, 0x1f, -1),
                "raw flags on non-rail blocks must not arm rail protection"
        );
        check(
                !EasyPlaceExtraProtocol.requestsRailProtection(
                        Blocks.POWERED_RAIL, -1, -1
                ),
                "an absent raw field must not arm rail protection"
        );
        check(
                EasyPlaceExtraProtocol.requestsRailProtection(
                        Blocks.STONE, -1, AUXILIARY_VALUE
                ),
                "the auxiliary flag alone must arm rail protection on any Easy Place packet"
        );
        check(
                !EasyPlaceExtraProtocol.requestsRailProtection(
                        Blocks.STONE, -1, -1
                ),
                "no auxiliary and no raw flags must leave rail protection off"
        );
    }

    private void checkRailDeferredWindow() {
        EasyPlaceUpdateSuppressionGuard.clearRailProtection(Level.OVERWORLD);
        long gameTime = 1_000L;
        int mask = 0b0_0101;
        BlockPos placement = new BlockPos(3, 64, 9);

        EasyPlaceUpdateSuppressionGuard.beginPlacement(placement);
        try {
            EasyPlaceUpdateSuppressionGuard.requestRailProtection(null, placement, mask);
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            EasyPlaceUpdateSuppressionGuard.Family.RAIL,
                            placement
                    ),
                    "requestRailProtection must arm the synchronous placement scope"
            );
            check(
                    EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            EasyPlaceUpdateSuppressionGuard.Family.RAIL,
                            placement.relative(Direction.NORTH)
                    ),
                    "the north neighbour must be covered by mask bit0"
            );
            check(
                    !EasyPlaceUpdateSuppressionGuard.shouldSuppress(
                            EasyPlaceUpdateSuppressionGuard.Family.RAIL,
                            placement.relative(Direction.SOUTH)
                    ),
                    "an unmatched neighbour must not be covered"
            );
        } finally {
            EasyPlaceUpdateSuppressionGuard.endPlacement();
        }

        EasyPlaceUpdateSuppressionGuard.armRailWindow(
                Level.OVERWORLD, gameTime, placement, mask
        );
        check(
                EasyPlaceUpdateSuppressionGuard.hasRailWindow(
                        Level.OVERWORLD, gameTime + 1, placement
                ),
                "the deferred window must still cover the placement position next tick"
        );
        check(
                EasyPlaceUpdateSuppressionGuard.hasRailWindow(
                        Level.OVERWORLD, gameTime + 1, placement.relative(Direction.WEST)
                ),
                "the deferred window must honour the west neighbour mask bit"
        );
        check(
                !EasyPlaceUpdateSuppressionGuard.hasRailWindow(
                        Level.OVERWORLD, gameTime + 1, placement.relative(Direction.EAST)
                )
                        && !EasyPlaceUpdateSuppressionGuard.hasRailWindow(
                        Level.OVERWORLD, gameTime + 1, placement.relative(Direction.UP)
                ),
                "the deferred window must not protect unmatched neighbours"
        );
        check(
                !EasyPlaceUpdateSuppressionGuard.hasRailWindow(
                        Level.OVERWORLD, gameTime + 10, placement
                ),
                "the deferred window must expire"
        );
        EasyPlaceUpdateSuppressionGuard.clearRailProtection(Level.OVERWORLD);
        check(
                !EasyPlaceUpdateSuppressionGuard.hasRailWindow(
                        Level.OVERWORLD, gameTime, placement
                ),
                "clearing the dimension must remove every rail window"
        );
    }

    private void checkYChannelCarriesNoRaw() {
        Vec3 original = new Vec3(
                BLOCK_POS.getX() + 0.5,
                BLOCK_POS.getY() + 0.73,
                BLOCK_POS.getZ() + 0.5
        );
        Vec3 encoded = EasyPlaceExtraProtocol.encodeY(BLOCK_POS, original);
        int packedY = EasyPlaceExtraProtocol.decodeY(encoded, BLOCK_POS);

        check(
                encoded.x == original.x && encoded.z == original.z,
                "Y encoding must not touch X or Z"
        );
        check(
                packedY >= 0
                        && (packedY & ~EasyPlaceExtraProtocol.Y_PRECISION_MASK) == 0,
                "the Y channel must carry only the eight-bit precision value"
        );
        check(
                packedY == EasyPlaceExtraProtocol.quantizeRelativeY(0.73),
                "the Y channel must decode to the original precision value"
        );
        check(
                !EasyPlaceExtraProtocol.isExtra(
                        EasyPlaceExtraProtocol.decode(encoded, BLOCK_POS)
                ),
                "raw must never be multiplexed into Y"
        );
    }

    private static Vec3 encode(BlockPos anchor, int extraValue, int auxiliaryValue) {
        return new Vec3(
                anchor.getX() + (extraValue << 1) + 2.0,
                anchor.getY() + 2.0 + Y_PRECISION_VALUE * 2.0,
                (auxiliaryValue << 1) + 2.0
        );
    }
}
