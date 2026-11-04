package top.frostor.carpet.easyplace;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import top.frostor.carpet.FrostorSettings;
import top.frostor.carpet.FrostorsCarpetAddition;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EasyPlaceExtraServer {
    public static final int PROTOCOL_VERSION = EasyPlaceExtraPayload.PROTOCOL_VERSION;
    public static final String NBT_PROTOCOL_VERSION = EasyPlaceExtraPayload.NBT_PROTOCOL_VERSION;
    public static final String NBT_CAPABILITIES = EasyPlaceExtraPayload.NBT_CAPABILITIES;
    public static final String CAPABILITY_EASY_PLACE_V2_EXTRA =
            EasyPlaceExtraPayload.CAPABILITY_EASY_PLACE_V2_EXTRA;
    public static final String CAPABILITY_HIT_POSITION_RELAXATION =
            EasyPlaceExtraPayload.CAPABILITY_HIT_POSITION_RELAXATION;
    private static final Set<UUID> SUPPORTED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static boolean lastRuleState;

    private EasyPlaceExtraServer() {
    }

    public static void register() {
        if(FabricLoader.getInstance().isModLoaded("earlycompat")) {
            FrostorsCarpetAddition.LOGGER.info(
                    "Skipping Easy Place V2-Extra receiver because EarlyCompat already owns it"
            );
            return;
        }

        registerPayloadType();
        ServerPlayNetworking.registerGlobalReceiver(
                EasyPlaceExtraPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handle(context.player(), payload))
        );
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SUPPORTED_PLAYERS.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            boolean enabled = FrostorSettings.easyPlaceProtocolExtra;
            if(enabled != lastRuleState) {
                broadcastCapability(server, enabled);
                if(!enabled) SUPPORTED_PLAYERS.clear();
            }
            lastRuleState = enabled;
        });
    }

    public static boolean isSupported(ServerPlayer player) {
        if(!FrostorSettings.easyPlaceProtocolExtra) {
            SUPPORTED_PLAYERS.remove(player.getUUID());
            return false;
        }
        return SUPPORTED_PLAYERS.contains(player.getUUID());
    }

    private static void handle(ServerPlayer player, EasyPlaceExtraPayload payload) {
        if(!FrostorSettings.easyPlaceProtocolExtra
                || payload.packetId() != EasyPlaceExtraPayload.HELLO) return;

        FrostorsCarpetAddition.LOGGER.info("[EasyPlace] Received V2-Extra hello from {}", player.getScoreboardName());
        acceptHello(player);
    }

    static void acceptHello(ServerPlayer player) {
        SUPPORTED_PLAYERS.add(player.getUUID());
        if(player.connection == null || !ServerPlayNetworking.canSend(player, EasyPlaceExtraPayload.TYPE)) return;
        sendAck(player, true);
    }

    private static void sendAck(ServerPlayer player, boolean enabled) {
        ServerPlayNetworking.send(
                player,
                new EasyPlaceExtraPayload(EasyPlaceExtraPayload.HELLO_ACK, capabilityData(enabled))
        );
    }

    static CompoundTag capabilityData() {
        return capabilityData(true);
    }

    static CompoundTag capabilityData(boolean enabled) {
        CompoundTag data = new CompoundTag();
        String version = FabricLoader.getInstance().getModContainer(FrostorsCarpetAddition.MOD_ID)
                .orElseThrow()
                .getMetadata()
                .getVersion()
                .getFriendlyString();
        data.putString("mod_version", version);
        data.putInt(NBT_PROTOCOL_VERSION, PROTOCOL_VERSION);
        data.putBoolean(CAPABILITY_EASY_PLACE_V2_EXTRA, enabled);
        ListTag capabilities = new ListTag();
        capabilities.add(StringTag.valueOf(CAPABILITY_EASY_PLACE_V2_EXTRA));
        capabilities.add(StringTag.valueOf(CAPABILITY_HIT_POSITION_RELAXATION));
        data.put(NBT_CAPABILITIES, capabilities);
        return data;
    }

    static void forget(ServerPlayer player) {
        SUPPORTED_PLAYERS.remove(player.getUUID());
    }

    private static void broadcastCapability(
            net.minecraft.server.MinecraftServer server,
            boolean enabled
    ) {
        int count = 0;
        for(ServerPlayer player : server.getPlayerList().getPlayers()) {
            if(!ServerPlayNetworking.canSend(player, EasyPlaceExtraPayload.TYPE)) continue;
            if(enabled) SUPPORTED_PLAYERS.add(player.getUUID());
            sendAck(player, enabled);
            count++;
        }
        FrostorsCarpetAddition.LOGGER.info(
                "[EasyPlace] Broadcast V2-Extra capability={} to {} online client(s)",
                enabled,
                count
        );
    }

    private static void registerPayloadType() {
        PayloadTypeRegistry.serverboundPlay().register(
                EasyPlaceExtraPayload.TYPE,
                EasyPlaceExtraPayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
                EasyPlaceExtraPayload.TYPE,
                EasyPlaceExtraPayload.CODEC
        );
    }
}
