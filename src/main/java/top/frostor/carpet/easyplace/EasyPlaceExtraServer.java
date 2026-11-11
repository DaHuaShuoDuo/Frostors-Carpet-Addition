package top.frostor.carpet.easyplace;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import carpet.CarpetServer;
import carpet.api.settings.SettingsManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import top.frostor.carpet.FrostorSettings;
import top.frostor.carpet.FrostorsCarpetAddition;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EasyPlaceExtraServer {
    public static final int PROTOCOL_VERSION = EasyPlaceExtraPayload.PROTOCOL_VERSION;
    public static final String NBT_PROTOCOL_VERSION = EasyPlaceExtraPayload.NBT_PROTOCOL_VERSION;
    public static final String NBT_CAPABILITIES = EasyPlaceExtraPayload.NBT_CAPABILITIES;
    public static final String NBT_Y_PRECISION_BITS = EasyPlaceExtraPayload.NBT_Y_PRECISION_BITS;
    public static final String CAPABILITY_EASY_PLACE_V2_EXTRA =
            EasyPlaceExtraPayload.CAPABILITY_EASY_PLACE_V2_EXTRA;
    public static final String CAPABILITY_HIT_POSITION_RELAXATION =
            EasyPlaceExtraPayload.CAPABILITY_HIT_POSITION_RELAXATION;
    public static final String CAPABILITY_Y_PRECISION =
            EasyPlaceExtraPayload.CAPABILITY_Y_PRECISION;
    private static final Set<UUID> SUPPORTED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> Y_PRECISION_PLAYERS = ConcurrentHashMap.newKeySet();
    private static SettingsManager observedManager;

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
                forget(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                EasyPlaceUpdateSuppressionGuard.clearRailProtection());
    }

    public static void registerRuleObserver(SettingsManager settingsManager) {
        if(FabricLoader.getInstance().isModLoaded("earlycompat")) return;
        if(settingsManager == observedManager) return;
        observedManager = settingsManager;
        settingsManager.registerRuleObserver((source, rule, newValue) -> {
            if(!"easyPlaceProtocolExtra".equals(rule.name())) return;
            MinecraftServer server = source == null
                    ? CarpetServer.minecraft_server
                    : source.getServer();
            boolean enabled = rule.value() instanceof Boolean value
                    ? value
                    : FrostorSettings.easyPlaceProtocolExtra;
            onRuleChanged(server, enabled);
        });
    }

    public static void onRuleChanged(MinecraftServer server, boolean enabled) {
        if(server == null) return;
        broadcastCapability(server, enabled);
        if(!enabled) SUPPORTED_PLAYERS.clear();
    }

    public static boolean isSupported(ServerPlayer player) {
        if(!FrostorSettings.easyPlaceProtocolExtra) {
            SUPPORTED_PLAYERS.remove(player.getUUID());
            return false;
        }
        return SUPPORTED_PLAYERS.contains(player.getUUID());
    }

    public static boolean supportsYPrecision(ServerPlayer player) {
        return isSupported(player) && Y_PRECISION_PLAYERS.contains(player.getUUID());
    }

    public static Vec3 restoreYPrecisionLocation(
            ServerPlayer player,
            net.minecraft.core.BlockPos pos,
            Vec3 location
    ) {
        if(!supportsYPrecision(player)) {
            return location;
        }
        int protocolValue = EasyPlaceExtraProtocol.decode(location, pos);
        int packedY = EasyPlaceExtraProtocol.decodeY(location, pos);
        if(!EasyPlaceExtraProtocol.isExtra(protocolValue) || packedY < 0) {
            return location;
        }

        int precisionValue = packedY & EasyPlaceExtraProtocol.Y_PRECISION_MASK;
        double restoredY = pos.getY()
                + EasyPlaceExtraProtocol.restoreRelativeY(precisionValue);
        return new Vec3(location.x, restoredY, location.z);
    }

    private static void handle(ServerPlayer player, EasyPlaceExtraPayload payload) {
        if(payload.packetId() != EasyPlaceExtraPayload.HELLO) return;
        if(supportsYPrecision(payload.data())) {
            Y_PRECISION_PLAYERS.add(player.getUUID());
        }
        if(!FrostorSettings.easyPlaceProtocolExtra) return;

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
        String version = FabricLoader.getInstance().getModContainer(FrostorsCarpetAddition.MOD_ID)
                .orElseThrow()
                .getMetadata()
                .getVersion()
                .getFriendlyString();
        return capabilityData(enabled, version);
    }

    static CompoundTag capabilityData(boolean enabled, String version) {
        CompoundTag data = new CompoundTag();
        data.putString("mod_version", version);
        data.putInt(NBT_PROTOCOL_VERSION, PROTOCOL_VERSION);
        data.putBoolean(CAPABILITY_EASY_PLACE_V2_EXTRA, enabled);
        data.putBoolean(CAPABILITY_Y_PRECISION, enabled);
        data.putInt(NBT_Y_PRECISION_BITS, EasyPlaceExtraProtocol.Y_PRECISION_BITS);
        ListTag capabilities = new ListTag();
        capabilities.add(StringTag.valueOf(CAPABILITY_EASY_PLACE_V2_EXTRA));
        capabilities.add(StringTag.valueOf(CAPABILITY_HIT_POSITION_RELAXATION));
        if(enabled) {
            capabilities.add(StringTag.valueOf(CAPABILITY_Y_PRECISION));
        }
        data.put(NBT_CAPABILITIES, capabilities);
        return data;
    }

    static void forget(ServerPlayer player) {
        SUPPORTED_PLAYERS.remove(player.getUUID());
        Y_PRECISION_PLAYERS.remove(player.getUUID());
    }

    static boolean supportsYPrecision(CompoundTag data) {
        return data.getIntOr(NBT_Y_PRECISION_BITS, -1)
                == EasyPlaceExtraProtocol.Y_PRECISION_BITS
                && data.getBooleanOr(CAPABILITY_Y_PRECISION, false)
                && advertisesCapability(data, CAPABILITY_Y_PRECISION);
    }

    private static boolean advertisesCapability(CompoundTag data, String capability) {
        ListTag capabilities = data.getListOrEmpty(NBT_CAPABILITIES);
        for(int index = 0; index < capabilities.size(); index++) {
            if(capability.equals(capabilities.getStringOr(index, ""))) {
                return true;
            }
        }
        return false;
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
