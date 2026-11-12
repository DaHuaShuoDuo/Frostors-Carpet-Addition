package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server packet-local data decoded from the unmodified {@code ServerboundUseItemOnPacket}.
 *
 * <p>Every V2-Extra wire field is owned by the packet's own {@code BlockHitResult.blockPos}: the
 * hit location is serialised as a binary32 float offset from that position, so the only anchor
 * the server can reproduce byte-for-byte is the packet position itself. Clients that need to
 * click a support block therefore re-anchor their payload before queueing the click; the server
 * never tries alternative anchors.</p>
 *
 * <p>This stays inside one packet on one thread: no cross-packet correlation, no context id and
 * no pending queue.</p>
 */
public final class EasyPlacePacketRequest {
    private static final ThreadLocal<Snapshot> REQUEST = new ThreadLocal<>();

    private EasyPlacePacketRequest() {
    }

    public static void begin(Snapshot snapshot) {
        REQUEST.set(snapshot);
    }

    public static void end() {
        REQUEST.remove();
    }

    public static @Nullable Snapshot current() {
        return REQUEST.get();
    }

    /**
     * Decodes all V2-Extra wire fields from the packet hit result.
     *
     * <p>The X raw value, the Y hit height and the Z auxiliary field are all decoded against
     * {@code blockPos}. The Z schema bit is the authoritative "this is a V2-Extra packet" marker
     * and is required before the auxiliary field may be trusted.</p>
     *
     * @return {@code null} when the packet carries neither the Z schema nor an X marker
     */
    public static @Nullable Snapshot decode(Vec3 location, BlockPos blockPos) {
        return decode(location, blockPos, false);
    }

    /**
     * Decodes a packet that may use the split, blockPos-relative auxiliary layout.
     *
     * <p>In relative-aux mode only the low 24 bits travel in Z; the schema, wall-schema and
     * wall-up bits ride in the X payload, so X is the sole marker carrier and the auxiliary
     * presence test moves there as well. Clients that did not negotiate the capability keep the
     * legacy fixed-origin field.</p>
     */
    public static @Nullable Snapshot decode(
            Vec3 location,
            BlockPos blockPos,
            boolean relativeAux
    ) {
        int primaryValue = EasyPlaceExtraProtocol.decode(location, blockPos);
        int rawValue = EasyPlaceExtraProtocol.isExtra(primaryValue)
                ? EasyPlaceExtraProtocol.toRawValue(primaryValue)
                : -1;

        int auxiliaryValue;
        if(relativeAux) {
            if(rawValue < 0) {
                return null;
            }
            int relativePayload = EasyPlaceExtraProtocol.decodeRelativeAuxiliary(
                    location,
                    blockPos
            );
            auxiliaryValue = (primaryValue & EasyPlaceExtraProtocol.X_AUX_SCHEMA_BIT) == 0
                    || relativePayload < 0
                    ? -1
                    : EasyPlaceExtraProtocol.joinAuxiliary(primaryValue, relativePayload);
        } else {
            auxiliaryValue = EasyPlaceExtraProtocol.stripAuxiliarySchema(
                    EasyPlaceExtraProtocol.decodeAuxiliary(location)
            );
            if(auxiliaryValue < 0 && rawValue < 0) {
                return null;
            }
        }

        int packedY = EasyPlaceExtraProtocol.decodeY(location, blockPos);
        return new Snapshot(
                rawValue,
                auxiliaryValue,
                packedY < 0 ? -1 : packedY & EasyPlaceExtraProtocol.Y_PRECISION_MASK,
                packedY < 0 ? 0 : EasyPlaceExtraProtocol.decodeYPiston(packedY)
        );
    }

    public record Snapshot(
            int rawValue,
            int auxiliaryValue,
            int yPrecisionValue,
            int pistonProtectionMask
    ) {
        public @Nullable EasyPlacePlacementRequest.Snapshot toPlacementSnapshot() {
            if(rawValue < 0) {
                return null;
            }
            return new EasyPlacePlacementRequest.Snapshot(
                    rawValue,
                    auxiliaryValue,
                    yPrecisionValue,
                    pistonProtectionMask
            );
        }
    }
}
