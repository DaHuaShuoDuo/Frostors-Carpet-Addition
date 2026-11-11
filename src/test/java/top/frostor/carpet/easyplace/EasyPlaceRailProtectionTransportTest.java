package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Headless checks for the V2-Extra rail protection transport.
 *
 * <p>The client encodes X/Z/Y against the {@code BlockHitResult.blockPos} that goes on the wire.
 * The server's {@code BlockPlaceContext} shifts that position by the hit face, which used to
 * corrupt the auxiliary Z field (bit9 + rail neighbour mask) in a direction-dependent way. These
 * checks mirror the client encoder arithmetically without booting a client or a world.</p>
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
        test.checkAlternateAnchorFallback();
        test.checkVersionIndependentWireAnchors();
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
                        EasyPlacePacketRequest.decode(location, BLOCK_POS, face);
                check(
                        snapshot != null && snapshot.resolvedRawValue() == raw,
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
                    EasyPlacePacketRequest.decode(location, BLOCK_POS, face);
            check(snapshot != null, "packet snapshot must exist for face " + face);
            check(
                    snapshot.resolvedRawValue() == RAW_VALUE,
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
                        BLOCK_POS,
                        Direction.UP
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
                    EasyPlacePacketRequest.decode(location, BLOCK_POS, face);
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
                EasyPlacePacketRequest.decode(location, BLOCK_POS, Direction.EAST) != null,
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
                EasyPlacePacketRequest.decode(unencoded, BLOCK_POS, Direction.UP);
        check(
                plain != null && plain.auxiliaryValue() == -1,
                "a plain click location must not be interpreted as an auxiliary field"
        );

        Vec3 noSchema = encode(BLOCK_POS, extraValue, AUXILIARY_VALUE);
        EasyPlacePacketRequest.Snapshot unsigned =
                EasyPlacePacketRequest.decode(noSchema, BLOCK_POS, Direction.UP);
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

    private void checkAlternateAnchorFallback() {
        BlockPos westPos = BLOCK_POS.relative(Direction.WEST);
        int extraValue = EasyPlaceExtraProtocol.toExtraValue(RAW_VALUE);
        Vec3 location = encode(westPos, extraValue, ENCODED_AUXILIARY_VALUE);

        EasyPlacePacketRequest.Snapshot snapshot =
                EasyPlacePacketRequest.decode(location, BLOCK_POS, Direction.WEST);
        check(snapshot != null, "an alternate X anchor must still be recognised");
        check(
                snapshot.rawValue() < 0 && snapshot.alternateRawValue() == RAW_VALUE,
                "only the alternate X anchor may carry the marker for a wall-attached target"
        );
        check(
                snapshot.resolvedRawValue() == RAW_VALUE
                        && snapshot.auxiliaryValue() == AUXILIARY_VALUE,
                "resolved values must follow the recognised anchor"
        );
    }

    /**
     * Minecraft 26.2 uses Litematica 0.28.6 and a plain ServerboundUseItemOnPacket, while 26.3
     * uses Litematica 0.29.1 and a record packet whose BlockHitResult codec stores the location
     * as a float offset from blockPos. Both must decode identically; an offset X anchor (bell
     * placement plans, printer adapters) may not change the decoded values.
     */
    private void checkVersionIndependentWireAnchors() {
        int extraValue = EasyPlaceExtraProtocol.toExtraValue(RAW_VALUE);
        BlockPos offsetAnchor = BLOCK_POS.relative(Direction.WEST);
        Vec3 shifted = new Vec3(
                offsetAnchor.getX() + (extraValue << 1) + 2.0,
                BLOCK_POS.getY() + 2.0 + Y_PRECISION_VALUE * 2.0,
                (ENCODED_AUXILIARY_VALUE << 1) + 2.0
        );

        for(Direction face : Direction.values()) {
            EasyPlacePacketRequest.Snapshot snapshot =
                    EasyPlacePacketRequest.decode(shifted, BLOCK_POS, face);
            check(snapshot != null, "an offset X anchor must still be a V2-Extra packet (" + face + ")");
            check(
                    snapshot.auxiliaryValue() == AUXILIARY_VALUE,
                    "Z auxiliary must not depend on the X anchor (" + face + ")"
            );
            EasyPlacePlacementRequest.Snapshot placement = snapshot.toPlacementSnapshot();
            check(
                    placement != null && placement.rawValue() == RAW_VALUE,
                    "the X candidate search must recover the raw value (" + face + ")"
            );
        }

        Vec3 plainClick = new Vec3(
                BLOCK_POS.getX() + 0.5,
                BLOCK_POS.getY() + 0.5,
                BLOCK_POS.getZ() + 0.5
        );
        for(Direction face : Direction.values()) {
            check(
                    EasyPlacePacketRequest.decode(plainClick, BLOCK_POS, face) == null,
                    "a plain click must not become a V2-Extra packet (" + face + ")"
            );
        }
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
