package top.frostor.carpet.easyplace;

import io.netty.buffer.ByteBuf;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.frostor.carpet.FrostorsCarpetAddition;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Uses TriWasa's payload implementation in integrated clients so the shared channel has one codec.
 */
final class EasyPlacePayloadBridge {
    private static final String TRIWASA_MOD_ID = "triwasa";
    private static final ExternalPayload EXTERNAL = loadExternalPayload();

    private EasyPlacePayloadBridge() {
    }

    static CustomPacketPayload.Type<?> type() {
        return EXTERNAL == null ? EasyPlaceExtraPayload.TYPE : EXTERNAL.type();
    }

    @SuppressWarnings("unchecked")
    static StreamCodec<? super RegistryFriendlyByteBuf, ? extends CustomPacketPayload> codec() {
        return EXTERNAL == null ? EasyPlaceExtraPayload.CODEC : EXTERNAL.codec();
    }

    static CustomPacketPayload create(int packetId, CompoundTag data) {
        if(EXTERNAL == null) return new EasyPlaceExtraPayload(packetId, data);
        try {
            return (CustomPacketPayload) EXTERNAL.constructor().newInstance(packetId, data);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Unable to create TriWasa easy-place payload", exception);
        }
    }

    static int packetId(CustomPacketPayload payload) {
        if(payload instanceof EasyPlaceExtraPayload payloadRecord) return payloadRecord.packetId();
        return (int) invokeAccessor(EXTERNAL.packetIdMethod(), payload);
    }

    static CompoundTag data(CustomPacketPayload payload) {
        if(payload instanceof EasyPlaceExtraPayload payloadRecord) return payloadRecord.data();
        return (CompoundTag) invokeAccessor(EXTERNAL.dataMethod(), payload);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ExternalPayload loadExternalPayload() {
        if(!FabricLoader.getInstance().isModLoaded(TRIWASA_MOD_ID)) return null;

        try {
            Class<?> payloadClass = Class.forName("top.frostor.triwasa.easyplace.EasyPlacePayload");
            Field typeField = payloadClass.getField("TYPE");
            Field codecField = payloadClass.getField("CODEC");
            return new ExternalPayload(
                    payloadClass,
                    payloadClass.getConstructor(int.class, CompoundTag.class),
                    (CustomPacketPayload.Type<?>) typeField.get(null),
                    (StreamCodec<? super ByteBuf, ? extends CustomPacketPayload>) codecField.get(null),
                    payloadClass.getMethod("packetId"),
                    payloadClass.getMethod("data")
            );
        } catch (ReflectiveOperationException | ClassCastException exception) {
            FrostorsCarpetAddition.LOGGER.error(
                    "TriWasa is loaded but its easy-place payload API is incompatible",
                    exception
            );
            return null;
        }
    }

    private static Object invokeAccessor(Method method, Object payload) {
        try {
            return method.invoke(payload);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Unable to read TriWasa easy-place payload", exception);
        }
    }

    private record ExternalPayload(
            Class<?> payloadClass,
            Constructor<?> constructor,
            CustomPacketPayload.Type<?> type,
            StreamCodec<? super ByteBuf, ? extends CustomPacketPayload> codec,
            Method packetIdMethod,
            Method dataMethod
    ) {
    }
}
