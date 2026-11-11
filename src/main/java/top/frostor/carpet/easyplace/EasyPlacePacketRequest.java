package top.frostor.carpet.easyplace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server packet-local data decoded from the unmodified {@code ServerboundUseItemOnPacket}.
 *
 * <p>The client encodes X/Z/Y against a block position that is not necessarily the one vanilla
 * later reports as {@code BlockPlaceContext.getClickedPos()} (that one is shifted by the hit
 * face, and {@code BlockPlaceContext.at(...)} may rebuild the click location from a block
 * centre). Decoding the auxiliary Z field there corrupts the bit layout in a direction-dependent
 * way, so every wire value is captured here while the original hit result is still available.
 * This stays inside one packet on one thread: no cross-packet correlation, no context id and no
 * pending queue.</p>
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
     * @return {@code null} when the packet does not carry the V2-Extra marker
     */
    public static @Nullable Snapshot decode(Vec3 location, BlockPos blockPos, Direction face) {
        int protocolValue = EasyPlaceExtraProtocol.decode(location, blockPos);
        BlockPos alternatePos = blockPos.relative(face);
        int alternateProtocolValue = EasyPlaceExtraProtocol.decode(location, alternatePos);

        boolean primaryExtra = EasyPlaceExtraProtocol.isExtra(protocolValue);
        boolean alternateExtra = EasyPlaceExtraProtocol.isExtra(alternateProtocolValue);
        if(!primaryExtra && !alternateExtra) {
            return null;
        }

        // Z uses a fixed origin on both sides, so the hit face and the placement target cannot
        // shift its bit layout. The schema bit distinguishes a real auxiliary field from an
        // unencoded click location.
        int auxiliaryValue = EasyPlaceExtraProtocol.stripAuxiliarySchema(
                EasyPlaceExtraProtocol.decodeAuxiliary(location)
        );
        int packedY = EasyPlaceExtraProtocol.decodeY(location, blockPos);
        return new Snapshot(
                primaryExtra ? EasyPlaceExtraProtocol.toRawValue(protocolValue) : -1,
                alternateExtra ? EasyPlaceExtraProtocol.toRawValue(alternateProtocolValue) : -1,
                auxiliaryValue,
                packedY < 0 ? -1 : packedY & EasyPlaceExtraProtocol.Y_PRECISION_MASK
        );
    }

    public record Snapshot(
            int rawValue,
            int alternateRawValue,
            int auxiliaryValue,
            int yPrecisionValue
    ) {
        /** The raw value decoded against the client's actual X encoding anchor. */
        public int resolvedRawValue() {
            return rawValue >= 0 ? rawValue : alternateRawValue;
        }

        public @Nullable EasyPlacePlacementRequest.Snapshot toPlacementSnapshot() {
            int raw = resolvedRawValue();
            if(raw < 0) {
                return null;
            }
            return new EasyPlacePlacementRequest.Snapshot(
                    raw,
                    alternateRawValue,
                    auxiliaryValue,
                    yPrecisionValue
            );
        }
    }
}
