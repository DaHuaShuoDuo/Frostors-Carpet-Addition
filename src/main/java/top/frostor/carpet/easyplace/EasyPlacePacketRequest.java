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
    private static final Direction[] X_ANCHOR_CANDIDATES = {Direction.EAST, Direction.WEST};
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
    /**
     * Decodes all V2-Extra wire fields from the packet hit result.
     *
     * <p>The wire contract is anchored at {@code blockPos} on both sides, so this is identical
     * for Minecraft 26.2 and 26.3 even though 26.3 turns {@code ServerboundUseItemOnPacket}
     * into a record and uses the generic {@code BlockHitResult.STREAM_CODEC}. The Z schema bit
     * is the authoritative "this is a V2-Extra packet" marker; X candidates are kept as a
     * fallback for client paths (printer adapters, bell placement plans in some Litematica
     * versions) that resolve the X anchor one block away from the packet position.</p>
     *
     * @return {@code null} when the packet carries neither the Z schema nor an X marker
     */
    public static @Nullable Snapshot decode(Vec3 location, BlockPos blockPos, Direction face) {
        int auxiliaryValue = EasyPlaceExtraProtocol.stripAuxiliarySchema(
                EasyPlaceExtraProtocol.decodeAuxiliary(location)
        );
        boolean schemaPresent = auxiliaryValue >= 0;

        int primaryValue = EasyPlaceExtraProtocol.decode(location, blockPos);
        int primaryRaw = EasyPlaceExtraProtocol.isExtra(primaryValue)
                ? EasyPlaceExtraProtocol.toRawValue(primaryValue)
                : -1;

        int faceValue = EasyPlaceExtraProtocol.decode(location, blockPos.relative(face));
        int alternateRaw = EasyPlaceExtraProtocol.isExtra(faceValue)
                ? EasyPlaceExtraProtocol.toRawValue(faceValue)
                : -1;
        if(alternateRaw < 0) {
            for(Direction direction : X_ANCHOR_CANDIDATES) {
                int candidate = EasyPlaceExtraProtocol.decode(
                        location,
                        blockPos.relative(direction)
                );
                if(EasyPlaceExtraProtocol.isExtra(candidate)) {
                    alternateRaw = EasyPlaceExtraProtocol.toRawValue(candidate);
                    break;
                }
            }
        }
        if(!schemaPresent && primaryRaw < 0 && alternateRaw < 0) {
            return null;
        }

        int packedY = EasyPlaceExtraProtocol.decodeY(location, blockPos);
        return new Snapshot(
                primaryRaw,
                alternateRaw,
                auxiliaryValue,
                packedY < 0 ? -1 : packedY & EasyPlaceExtraProtocol.Y_PRECISION_MASK,
                packedY < 0 ? 0 : EasyPlaceExtraProtocol.decodeYPiston(packedY)
        );
    }

    public record Snapshot(
            int rawValue,
            int alternateRawValue,
            int auxiliaryValue,
            int yPrecisionValue,
            int pistonProtectionMask
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
                    yPrecisionValue,
                    pistonProtectionMask
            );
        }
    }
}
