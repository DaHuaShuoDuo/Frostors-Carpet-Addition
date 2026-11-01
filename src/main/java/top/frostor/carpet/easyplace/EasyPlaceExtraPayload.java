package top.frostor.carpet.easyplace;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Wire-compatible with EarlyCompat's Fanetlib payload.
 */
public record EasyPlaceExtraPayload(int packetId, CompoundTag data) implements CustomPacketPayload {
    public static final int HELLO = 0;
    public static final int HELLO_ACK = 0;
    public static final Type<EasyPlaceExtraPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("earlycompat", "network")
    );
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
