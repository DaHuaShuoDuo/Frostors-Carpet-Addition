package top.frostor.carpet.easyplace;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Stable public payload API for the V2-Extra handshake.
 *
 * <p>FCA owns this type on integrated clients and dedicated servers. Client tools may compile
 * against it, but must provide their own wire-compatible payload when connecting without FCA.</p>
 */
public record EasyPlaceExtraPayload(int packetId, CompoundTag data) implements CustomPacketPayload {
    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath("earlycompat", "network");
    public static final int PROTOCOL_VERSION = 1;
    public static final int HELLO = 0;
    public static final int HELLO_ACK = 0;
    public static final String NBT_PROTOCOL_VERSION = "protocol_version";
    public static final String NBT_CAPABILITIES = "capabilities";
    public static final String NBT_Y_PRECISION_BITS = "y_precision_bits";
    public static final String NBT_PISTON_PROTECTION_BITS = "piston_protection_bits";
    public static final String CAPABILITY_EASY_PLACE_V2_EXTRA = "easy_place_v2_extra";
    public static final String CAPABILITY_HIT_POSITION_RELAXATION = "hit_position_relaxation";
    public static final String CAPABILITY_PACKET_ANCHOR = "easy_place_packet_anchor";
    public static final String CAPABILITY_Y_PRECISION = "easy_place_y_precision";
    public static final String CAPABILITY_PISTON_PROTECTION = "easy_place_piston_protection";
    public static final Type<EasyPlaceExtraPayload> TYPE = new Type<>(CHANNEL);
    public static final StreamCodec<RegistryFriendlyByteBuf, EasyPlaceExtraPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EasyPlaceExtraPayload::packetId,
                    ByteBufCodecs.COMPOUND_TAG,
                    EasyPlaceExtraPayload::data,
                    EasyPlaceExtraPayload::new
            );

    @Override
    public Type<EasyPlaceExtraPayload> type() {
        return TYPE;
    }
}
