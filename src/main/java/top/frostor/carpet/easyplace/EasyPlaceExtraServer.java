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
    public static final String CAPABILITY_PACKET_ANCHOR =
            EasyPlaceExtraPayload.CAPABILITY_PACKET_ANCHOR;
    public static final String CAPABILITY_RELATIVE_AUX =
            EasyPlaceExtraPayload.CAPABILITY_RELATIVE_AUX;
    public static final String CAPABILITY_Y_PRECISION =
            EasyPlaceExtraPayload.CAPABILITY_Y_PRECISION;
    public static final String CAPABILITY_PISTON_PROTECTION =
            EasyPlaceExtraPayload.CAPABILITY_PISTON_PROTECTION;
    public static final String NBT_PISTON_PROTECTION_BITS =
            EasyPlaceExtraPayload.NBT_PISTON_PROTECTION_BITS;
    /**
     * Players that completed a valid V2-Extra HELLO. Kept independently of the rule toggle so
     * re-enabling the rule only restores clients that actually negotiated the protocol.
     */
    private static final Set<UUID> VALID_HELLO_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> Y_PRECISION_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> RELATIVE_AUX_PLAYERS = ConcurrentHashMap.newKeySet();
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
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            EasyPlaceUpdateSuppressionGuard.clearRailProtection();
            EasyPlacePacketRequest.end();
            // A stopped server may be the same JVM as the next one (integrated server restart,
            // test harness); leftover UUIDs would let a player be treated as already negotiated
            // before its new HELLO arrives, and would keep the previous auxiliary layout.
            VALID_HELLO_PLAYERS.clear();
            Y_PRECISION_PLAYERS.clear();
            RELATIVE_AUX_PLAYERS.clear();
        });
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
        // A rule toggle must never grant support on its own: only clients that already sent a
        // valid HELLO are in VALID_HELLO_PLAYERS, and isSupported() gates on the rule as well.
        broadcastCapability(server, enabled);
        if(!enabled) {
            EasyPlaceUpdateSuppressionGuard.clearRailProtection();
        }
    }

    public static boolean isSupported(ServerPlayer player) {
        return FrostorSettings.easyPlaceProtocolExtra
                && VALID_HELLO_PLAYERS.contains(player.getUUID());
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
        CompoundTag data = payload.data();
        // Inbound HELLO validation is deliberately separate from the outbound ACK generated by
        // capabilityData(). A hand-built or incompatible HELLO must not grant hit-position
        // relaxation, so reject it before recording any capability for this player.
        if(!acceptsHello(data)) {
            forget(player);
            return;
        }
        VALID_HELLO_PLAYERS.add(player.getUUID());
        if(supportsYPrecision(data)) {
            Y_PRECISION_PLAYERS.add(player.getUUID());
        } else {
            Y_PRECISION_PLAYERS.remove(player.getUUID());
        }
        if(supportsRelativeAux(data)) {
            RELATIVE_AUX_PLAYERS.add(player.getUUID());
        } else {
            RELATIVE_AUX_PLAYERS.remove(player.getUUID());
        }
        if(!FrostorSettings.easyPlaceProtocolExtra) return;

        FrostorsCarpetAddition.LOGGER.info("[EasyPlace] Received V2-Extra hello from {}", player.getScoreboardName());
        acceptHello(player);
    }

    /** Validates an inbound client HELLO. Never used to validate our own ACK payloads. */
    static boolean acceptsHello(CompoundTag data) {
        // The packet-anchor contract is mandatory: a client that still anchors its payload at a
        // support block while sending a different blockPos would decode one raw value off, so it
        // must not be granted V2-Extra at all. Rejecting it falls back to unmodified easy place
        // instead of silently misplacing blocks.
        return data.getIntOr(NBT_PROTOCOL_VERSION, -1) == PROTOCOL_VERSION
                && data.getBooleanOr(CAPABILITY_EASY_PLACE_V2_EXTRA, false)
                && advertisesCapability(data, CAPABILITY_EASY_PLACE_V2_EXTRA)
                && data.getBooleanOr(CAPABILITY_PACKET_ANCHOR, false)
                && advertisesCapability(data, CAPABILITY_PACKET_ANCHOR);
    }

    static void acceptHello(ServerPlayer player) {
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
        data.putBoolean(CAPABILITY_PACKET_ANCHOR, enabled);
        data.putBoolean(CAPABILITY_RELATIVE_AUX, enabled);
        data.putBoolean(CAPABILITY_Y_PRECISION, enabled);
        data.putBoolean(CAPABILITY_PISTON_PROTECTION, enabled);
        data.putInt(NBT_Y_PRECISION_BITS, EasyPlaceExtraProtocol.Y_PRECISION_BITS);
        data.putInt(
                NBT_PISTON_PROTECTION_BITS,
                EasyPlaceExtraProtocol.PISTON_PROTECTION_BITS
        );
        ListTag capabilities = new ListTag();
        capabilities.add(StringTag.valueOf(CAPABILITY_EASY_PLACE_V2_EXTRA));
        capabilities.add(StringTag.valueOf(CAPABILITY_HIT_POSITION_RELAXATION));
        capabilities.add(StringTag.valueOf(CAPABILITY_PACKET_ANCHOR));
        if(enabled) {
            capabilities.add(StringTag.valueOf(CAPABILITY_Y_PRECISION));
            capabilities.add(StringTag.valueOf(CAPABILITY_PISTON_PROTECTION));
            capabilities.add(StringTag.valueOf(CAPABILITY_RELATIVE_AUX));
        }
        data.put(NBT_CAPABILITIES, capabilities);
        return data;
    }

    static void forget(ServerPlayer player) {
        VALID_HELLO_PLAYERS.remove(player.getUUID());
        Y_PRECISION_PLAYERS.remove(player.getUUID());
        RELATIVE_AUX_PLAYERS.remove(player.getUUID());
    }

    /**
     * Whether the client splits the auxiliary value into a 24-bit relative Z payload plus three
     * bits in X. This is an optional capability: clients that do not declare it keep the legacy
     * fixed-origin field, so the flag is never required by {@link #acceptsHello(CompoundTag)}.
     */
    public static boolean supportsRelativeAux(ServerPlayer player) {
        return isSupported(player) && RELATIVE_AUX_PLAYERS.contains(player.getUUID());
    }

    static boolean supportsRelativeAux(CompoundTag data) {
        return acceptsHello(data)
                && data.getBooleanOr(CAPABILITY_RELATIVE_AUX, false)
                && advertisesCapability(data, CAPABILITY_RELATIVE_AUX);
    }

    static boolean supportsYPrecision(CompoundTag data) {
        return acceptsHello(data)
                && data.getIntOr(NBT_Y_PRECISION_BITS, -1)
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
        int grantedCount = 0;
        for(ServerPlayer player : server.getPlayerList().getPlayers()) {
            if(!ServerPlayNetworking.canSend(player, EasyPlaceExtraPayload.TYPE)) continue;
            // The rule toggle must not grant support to a client whose HELLO was rejected: the
            // server would keep ignoring its payload while the client starts encoding.
            boolean granted = enabled && VALID_HELLO_PLAYERS.contains(player.getUUID());
            sendAck(player, granted);
            if(granted) grantedCount++;
            count++;
        }
        FrostorsCarpetAddition.LOGGER.info(
                "[EasyPlace] Broadcast V2-Extra capability={} (granted {}) to {} online client(s)",
                enabled,
                grantedCount,
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
