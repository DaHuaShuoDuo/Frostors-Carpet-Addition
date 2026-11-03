package top.frostor.carpet.bigenderchest;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import top.frostor.carpet.FrostorSettings;
import top.frostor.carpet.FrostorsCarpetAddition;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol;
import top.frostor.carpet.bigenderchest.capacity.BigEnderChestCapacity;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol.ActionRequest;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol.ActionResult;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol.Delta;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol.Hello;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol.HelloAck;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol.Payload;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol.SessionOpen;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol.SlotRecord;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol.SnapshotBegin;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol.SnapshotChunk;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol.TemplateRecord;
import top.frostor.carpet.bigenderchest.index.BigEnderChestIndex.AggregateEntry;
import top.frostor.carpet.bigenderchest.index.ItemTemplateKey;
import top.frostor.carpet.bigenderchest.index.StorageEntry;
import top.frostor.carpet.bigenderchest.index.StorageLocation;
import top.frostor.carpet.bigenderchest.menu.BigEnderChestMenu;
import top.frostor.carpet.bigenderchest.menu.BigEnderChestTerminalMenu;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

public final class BigEnderChestProtocolServer {
    private static final int MAX_ACTIONS_PER_TICK = 8;
    private static final int RECENT_RESULT_LIMIT = 64;
    private static final int RESYNC_COOLDOWN_TICKS = 20;
    private static final int SNAPSHOT_ACK_TIMEOUT_TICKS = 100;
    private static final int SNAPSHOT_WORKER_COUNT = 2;
    private static final AtomicLong SNAPSHOT_IDS = new AtomicLong();
    private static final AtomicInteger SNAPSHOT_THREAD_IDS = new AtomicInteger();
    private static final Map<UUID, ClientState> CLIENTS = new ConcurrentHashMap<>();
    private static final ExecutorService SNAPSHOT_EXECUTOR = Executors.newFixedThreadPool(
            SNAPSHOT_WORKER_COUNT,
            runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "Frostors-BigEnderChest-Snapshot-"
                                + SNAPSHOT_THREAD_IDS.incrementAndGet()
                );
                thread.setDaemon(true);
                return thread;
            }
    );
    private static boolean registered;

    private BigEnderChestProtocolServer() {
    }

    public static void register() {
        if(registered) return;
        registered = true;
        PayloadTypeRegistry.serverboundPlay().register(Payload.TYPE, Payload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Payload.TYPE, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, ignored) ->
                CLIENTS.remove(handler.player.getUUID()));
    }

    public static void sessionOpened(BigEnderChestSessions.Session session) {
        ClientState state = CLIENTS.get(session.owner().getUUID());
        if(state == null || !state.accepted) return;
        state.closedSessionId = 0;
        sendSessionOpen(session, state);
        state.snapshotPending = true;
        if(!session.service().isReadOnly()) {
            startSnapshot(session, state);
        }
    }

    public static void sessionClosed(BigEnderChestSessions.Session session) {
        ClientState state = CLIENTS.get(session.owner().getUUID());
        if(state != null) {
            if(state.closedSessionId == session.sessionId()) return;
            state.closedSessionId = session.sessionId();
            if((state.capabilities & BigEnderChestProtocol.CAPABILITY_SESSION_CLOSE) != 0) {
                sendPayload(
                        session.owner(),
                        BigEnderChestProtocol.SESSION_CLOSE,
                        new BigEnderChestProtocol.SessionClose(
                                session.sessionId(),
                                0
                        ).toTag()
                );
            }
            state.clearSnapshot();
        }
    }

    public static void tick(MinecraftServer server) {
        for(Map.Entry<UUID, ClientState> entry : CLIENTS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ClientState state = entry.getValue();
            if(player == null || !state.accepted) continue;
            state.actionsThisTick = 0;
            BigEnderChestSessions.Session session = BigEnderChestSessions.session(player);
            if(session == null || !session.service().isOpen()) {
                state.clearSnapshot();
                continue;
            }
            if(state.snapshotBuilding) {
                continue;
            }
            if(session.service().isReadOnly()) {
                if((state.capabilities & BigEnderChestProtocol.CAPABILITY_PAGE_CATALOG) != 0
                        && session.service().pageRevision() != state.sentPageRevision) {
                    sendPageCatalog(session, state);
                }
                continue;
            }
            if(state.snapshotPending) {
                startSnapshot(session, state);
                continue;
            }
            if(!state.snapshotChunks.isEmpty()) {
                if(state.awaitingSnapshotAck
                        && ++state.snapshotAckTicks >= SNAPSHOT_ACK_TIMEOUT_TICKS) {
                    startSnapshot(session, state);
                }else {
                    sendNextSnapshotChunk(session, state);
                }
                continue;
            }
            if(session.service().revision() != state.sentRevision) {
                sendDeltaOrSnapshot(session, state);
            }
            if((state.capabilities & BigEnderChestProtocol.CAPABILITY_PAGE_CATALOG) != 0
                    && session.service().pageRevision() != state.sentPageRevision) {
                sendPageCatalog(session, state);
            }
        }
    }

    static boolean isHandshaken(ServerPlayer player) {
        ClientState state = CLIENTS.get(player.getUUID());
        return state != null && state.accepted;
    }

    static boolean isTerminalClient(ServerPlayer player) {
        ClientState state = CLIENTS.get(player.getUUID());
        return state != null
                && state.accepted
                && (state.capabilities
                & BigEnderChestProtocol.CAPABILITY_TERMINAL_MENU) != 0;
    }

    private static void handle(ServerPlayer player, Payload payload) {
        if(!FrostorSettings.bigEnderChest) return;
        switch(payload.messageType()) {
            case BigEnderChestProtocol.HELLO -> handleHello(player, payload.data());
            case BigEnderChestProtocol.ACTION_REQUEST -> handleAction(
                    player,
                    ActionRequest.fromTag(payload.data())
            );
            case BigEnderChestProtocol.SNAPSHOT_ACK -> handleSnapshotAck(
                    player,
                    BigEnderChestProtocol.SnapshotAck.fromTag(payload.data())
            );
            case BigEnderChestProtocol.REQUEST_RESYNC -> handleResyncRequest(
                    player,
                    BigEnderChestProtocol.RequestResync.fromTag(payload.data())
            );
            case BigEnderChestProtocol.OPEN_TERMINAL_REQUEST -> handleOpenTerminalRequest(
                    player,
                    BigEnderChestProtocol.OpenTerminalRequest.fromTag(payload.data())
            );
            default -> {
            }
        }
    }

    private static void handleHello(ServerPlayer player, CompoundTag data) {
        Hello hello = Hello.fromTag(data);
        boolean accepted = hello.protocolVersion() == BigEnderChestProtocol.PROTOCOL_VERSION
                && hello.maxChunkBytes() > 0
                && (hello.capabilities() & BigEnderChestProtocol.CAPABILITY_SNAPSHOT) != 0
                && (hello.capabilities() & BigEnderChestProtocol.CAPABILITY_ACTIONS) != 0;
        int chunkLimit = Math.min(
                BigEnderChestProtocol.MAX_CHUNK_BYTES,
                Math.max(1024, hello.maxChunkBytes())
        );
        int capabilities = hello.capabilities() & BigEnderChestProtocol.CAPABILITIES;
        ClientState state = accepted
                ? CLIENTS.computeIfAbsent(player.getUUID(), ignored -> new ClientState())
                : CLIENTS.get(player.getUUID());
        if(state != null) {
            state.accepted = accepted;
            state.chunkByteLimit = chunkLimit;
            state.capabilities = capabilities;
        }
        if(ServerPlayNetworking.canSend(player, Payload.TYPE)) {
            ServerPlayNetworking.send(player, new Payload(
                    BigEnderChestProtocol.HELLO_ACK,
                    new HelloAck(
                            accepted ? BigEnderChestProtocol.PROTOCOL_VERSION : 0,
                            capabilities,
                            chunkLimit,
                            accepted
                    ).toTag()
            ));
        }
        if(!accepted) {
            CLIENTS.remove(player.getUUID());
            return;
        }
        BigEnderChestSessions.Session session = BigEnderChestSessions.session(player);
        if(session != null) sessionOpened(session);
    }

    private static void sendSessionOpen(
            BigEnderChestSessions.Session session,
            ClientState state
    ) {
        state.clearSnapshot();
        state.recentResults.clear();
        state.lastActionSequence = 0;
        state.lastResyncTick = Long.MIN_VALUE;
        sendPayload(
                session.owner(),
                BigEnderChestProtocol.SESSION_OPEN,
                new SessionOpen(
                        session.sessionId(),
                        session.containerId(),
                        session.service().revision(),
                        FrostorSettings.bigEnderChestPageLimit,
                        session.service().pageCount(),
                        session.service().pageRevision(),
                        session.service().isReadOnly(),
                        state.capabilities,
                        session.menu() instanceof top.frostor.carpet.bigenderchest.menu.BigEnderChestTerminalMenu
                                ? session.containerId()
                                : -1,
                        0
                ).toTag()
        );
        state.sentPageRevision = session.service().pageRevision();
        if((state.capabilities & BigEnderChestProtocol.CAPABILITY_PAGE_CATALOG) != 0) {
            sendPageCatalog(session, state);
        }
    }

    private static void sendPageCatalog(
            BigEnderChestSessions.Session session,
            ClientState state
    ) {
        long pageRevision = session.service().pageRevision();
        List<BigEnderChestProtocol.PageInfo> pages = session.service().pageCatalog().stream()
                .map(page -> new BigEnderChestProtocol.PageInfo(
                        page.pageId(),
                        page.state().name(),
                        page.empty(),
                        page.occupiedSlots(),
                        page.contentRevision()
                ))
                .toList();
        sendPayload(
                session.owner(),
                BigEnderChestProtocol.PAGE_CATALOG,
                new BigEnderChestProtocol.PageCatalog(
                        session.sessionId(),
                        pageRevision,
                        session.service().pageCount() * StorageLocation.SLOTS_PER_PAGE,
                        pages.stream()
                                .mapToInt(BigEnderChestProtocol.PageInfo::occupiedSlots)
                                .sum(),
                        session.service().isReadOnly(),
                        pages
                ).toTag()
        );
        state.sentPageRevision = pageRevision;
    }

    private static void startSnapshot(
            BigEnderChestSessions.Session session,
            ClientState state
    ) {
        if(state.snapshotBuilding) {
            state.snapshotPending = true;
            return;
        }
        state.clearSnapshot();
        session.service().takePendingDelta();
        state.snapshotPending = false;
        List<AggregateEntry> aggregates = session.service().aggregateEntries();
        long snapshotRevision = session.service().revision();
        long snapshotId = SNAPSHOT_IDS.incrementAndGet();
        state.snapshotId = snapshotId;
        state.snapshotRevision = snapshotRevision;
        state.nextChunk = 0;
        state.snapshotBuilding = true;
        HolderLookup.Provider registries = session.owner().registryAccess();
        MinecraftServer server = session.owner().level().getServer();
        String ownerName = session.owner().getScoreboardName();
        int chunkByteLimit = state.chunkByteLimit;
        SNAPSHOT_EXECUTOR.execute(() -> {
            SnapshotBuild build;
            try {
                build = buildSnapshot(registries, aggregates, chunkByteLimit);
            }catch(RuntimeException exception) {
                FrostorsCarpetAddition.LOGGER.error(
                        "Failed to build big ender chest snapshot for {}",
                        ownerName,
                        exception
                );
                server.execute(() -> {
                    state.snapshotBuilding = false;
                    state.clearSnapshot();
                });
                return;
            }
            server.execute(() -> {
                if(state.snapshotId != snapshotId || state.snapshotRevision < 0) return;
                state.serials.clear();
                state.serials.putAll(build.serials());
                state.keysBySerial.clear();
                state.keysBySerial.putAll(build.keysBySerial());
                state.snapshotAmounts.clear();
                state.snapshotAmounts.putAll(build.amounts());
                state.nextSerial = build.nextSerial();
                state.snapshotChunks.addAll(build.chunks());
                state.snapshotBuilding = false;
                sendPayload(
                        session.owner(),
                        BigEnderChestProtocol.SNAPSHOT_BEGIN,
                        new SnapshotBegin(
                                session.sessionId(),
                                snapshotId,
                                state.snapshotRevision,
                                build.entryCount(),
                                0,
                                build.chunks().size()
                        ).toTag()
                );
            });
        });
    }

    private static SnapshotBuild buildSnapshot(
            HolderLookup.Provider registries,
            List<AggregateEntry> aggregates,
            int chunkByteLimit
    ) {
        Map<ItemTemplateKey, Long> serials = new HashMap<>();
        Map<Long, ItemTemplateKey> keysBySerial = new HashMap<>();
        Map<ItemTemplateKey, Long> amounts = new HashMap<>();
        List<BigEnderChestProtocol.EntryRecord> entries =
                new ArrayList<>(aggregates.size());
        long nextSerial = 1;
        for(AggregateEntry aggregate : aggregates) {
            long serial = nextSerial++;
            serials.put(aggregate.template(), serial);
            keysBySerial.put(serial, aggregate.template());
            amounts.put(aggregate.template(), aggregate.totalAmount());
            entries.add(new BigEnderChestProtocol.EntryRecord(
                    serial,
                    aggregate.template().template(),
                    aggregate.totalAmount(),
                    List.of()
            ));
        }
        List<byte[]> chunks = new ArrayList<>();
        ListTag currentEntries = new ListTag();
        for(BigEnderChestProtocol.EntryRecord record : entries) {
            CompoundTag recordTag = record.toTag(registries);
            if(!currentEntries.isEmpty()
                    && recordTag.sizeInBytes() + currentEntries.sizeInBytes()
                    >= chunkByteLimit) {
                chunks.add(encodeChunk(currentEntries));
                currentEntries = new ListTag();
            }
            currentEntries.add(recordTag);
        }
        if(!currentEntries.isEmpty() || chunks.isEmpty()) {
            chunks.add(encodeChunk(currentEntries));
        }
        return new SnapshotBuild(
                List.copyOf(chunks),
                Map.copyOf(serials),
                Map.copyOf(keysBySerial),
                Map.copyOf(amounts),
                entries.size(),
                nextSerial
        );
    }

    private record SnapshotBuild(
            List<byte[]> chunks,
            Map<ItemTemplateKey, Long> serials,
            Map<Long, ItemTemplateKey> keysBySerial,
            Map<ItemTemplateKey, Long> amounts,
            int entryCount,
            long nextSerial
    ) {
    }

    private static byte[] encodeChunk(ListTag entries) {
        CompoundTag chunk = new CompoundTag();
        chunk.put("entries", entries);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try(DataOutputStream output = new DataOutputStream(bytes)) {
                NbtIo.write(chunk, output);
            }
            return bytes.toByteArray();
        }catch(IOException exception) {
            throw new IllegalStateException("Failed to encode big ender chest snapshot", exception);
        }
    }

    private static void sendNextSnapshotChunk(
            BigEnderChestSessions.Session session,
            ClientState state
    ) {
        if(state.snapshotChunks.isEmpty()) return;
        if(!state.retransmitChunks.isEmpty()) {
            sendSnapshotChunk(session, state, state.retransmitChunks.removeFirst());
            return;
        }
        if(state.nextChunk < state.snapshotChunks.size()) {
            sendSnapshotChunk(session, state, state.nextChunk++);
            return;
        }
        if(state.snapshotEndSent) return;

        sendPayload(
                session.owner(),
                BigEnderChestProtocol.SNAPSHOT_END,
                new BigEnderChestProtocol.SnapshotEnd(
                        session.sessionId(),
                        state.snapshotId,
                        state.snapshotRevision,
                        state.snapshotAmounts.size(),
                        state.snapshotChunks.size()
                ).toTag()
        );
        state.snapshotEndSent = true;
        if((state.capabilities & BigEnderChestProtocol.CAPABILITY_SNAPSHOT_ACK) == 0) {
            completeSnapshot(session, state);
        }else {
            state.awaitingSnapshotAck = true;
            state.snapshotAckTicks = 0;
        }
    }

    private static void sendSnapshotChunk(
            BigEnderChestSessions.Session session,
            ClientState state,
            int index
    ) {
        if(index < 0 || index >= state.snapshotChunks.size()) return;
        byte[] data = state.snapshotChunks.get(index);
        CRC32 checksum = new CRC32();
        checksum.update(data);
        sendPayload(
                session.owner(),
                BigEnderChestProtocol.SNAPSHOT_CHUNK,
                new SnapshotChunk(
                        session.sessionId(),
                        state.snapshotId,
                        index,
                        checksum.getValue(),
                        data
                ).toTag()
        );
    }

    private static void completeSnapshot(
            BigEnderChestSessions.Session session,
            ClientState state
    ) {
        state.sentAmounts.clear();
        state.sentAmounts.putAll(state.snapshotAmounts);
        state.sentRevision = state.snapshotRevision;
        state.clearSnapshot();
        if(session.service().revision() != state.sentRevision) {
            startSnapshot(session, state);
        }
    }

    private static void sendDeltaOrSnapshot(
            BigEnderChestSessions.Session session,
            ClientState state
    ) {
        if((state.capabilities & BigEnderChestProtocol.CAPABILITY_DELTA) == 0) {
            startSnapshot(session, state);
            return;
        }
        BigEnderChestService.Delta pending = session.service().takePendingDelta();
        if(pending == null || pending.baseRevision() != state.sentRevision) {
            startSnapshot(session, state);
            return;
        }

        Map<ItemTemplateKey, Long> currentAmounts = new HashMap<>();
        for(StorageEntry entry : session.service().entries()) {
            currentAmounts.put(entry.template(), entry.totalAmount());
        }
        List<BigEnderChestProtocol.EntryRecord> entryChanges = new ArrayList<>();
        for(StorageEntry entry : session.service().entries()) {
            long previous = state.sentAmounts.getOrDefault(entry.template(), 0L);
            if(previous == entry.totalAmount()) continue;
            long serial = state.serials.computeIfAbsent(
                    entry.template(),
                    ignored -> state.nextSerial++
            );
            state.keysBySerial.put(serial, entry.template());
            entryChanges.add(new BigEnderChestProtocol.EntryRecord(
                    serial,
                    entry.template().template(),
                    entry.totalAmount(),
                    List.of()
            ));
        }
        List<Long> removed = state.sentAmounts.keySet().stream()
                .filter(key -> !currentAmounts.containsKey(key))
                .map(state.serials::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        for(long serial : removed) {
            ItemTemplateKey removedKey = state.keysBySerial.remove(serial);
            if(removedKey != null) state.serials.remove(removedKey);
        }
        List<BigEnderChestProtocol.PageInfo> pageChanges = session.service()
                .pageCatalog()
                .stream()
                .map(page -> new BigEnderChestProtocol.PageInfo(
                        page.pageId(),
                        page.state().name(),
                        page.empty(),
                        page.occupiedSlots(),
                        page.contentRevision()
                ))
                .toList();
        BigEnderChestProtocol.Delta delta = new BigEnderChestProtocol.Delta(
                session.sessionId(),
                pending.baseRevision(),
                pending.revision(),
                entryChanges,
                removed,
                pageChanges,
                pending.pageAdds(),
                !pending.pageAdds().isEmpty(),
                session.service().pageRevision(),
                session.service().isReadOnly()
        );
        CompoundTag data;
        try {
            data = delta.toTag(session.owner().registryAccess());
        }catch(RuntimeException exception) {
            FrostorsCarpetAddition.LOGGER.warn(
                    "Failed to encode big ender chest delta for {}, sending a snapshot",
                    session.owner().getScoreboardName(),
                    exception
            );
            startSnapshot(session, state);
            return;
        }
        if(data.sizeInBytes() > state.chunkByteLimit) {
            startSnapshot(session, state);
            return;
        }
        sendPayload(session.owner(), BigEnderChestProtocol.DELTA, data);
        state.sentAmounts.clear();
        state.sentAmounts.putAll(currentAmounts);
        state.sentRevision = pending.revision();
    }

    private static void handleSnapshotAck(
            ServerPlayer player,
            BigEnderChestProtocol.SnapshotAck ack
    ) {
        ClientState state = CLIENTS.get(player.getUUID());
        BigEnderChestSessions.Session session = BigEnderChestSessions.session(player);
        if(state == null || session == null || !state.accepted) return;
        if(ack.sessionId() != session.sessionId()
                || ack.snapshotId() != state.snapshotId
                || !state.snapshotEndSent) {
            return;
        }
        state.snapshotAckTicks = 0;
        if(ack.missingChunks().size() > state.snapshotChunks.size()) {
            startSnapshot(session, state);
            return;
        }
        if(ack.missingChunks().isEmpty()) {
            completeSnapshot(session, state);
            return;
        }
        state.awaitingSnapshotAck = false;
        state.snapshotEndSent = false;
        for(int index : ack.missingChunks()) {
            if(index >= 0
                    && index < state.snapshotChunks.size()
                    && !state.retransmitChunks.contains(index)) {
                state.retransmitChunks.addLast(index);
            }
        }
    }

    private static void handleResyncRequest(
            ServerPlayer player,
            BigEnderChestProtocol.RequestResync request
    ) {
        ClientState state = CLIENTS.get(player.getUUID());
        BigEnderChestSessions.Session session = BigEnderChestSessions.session(player);
        if(state == null
                || session == null
                || !state.accepted
                || request.sessionId() != session.sessionId()) {
            return;
        }
        long now = player.level().getGameTime();
        if(state.lastResyncTick != Long.MIN_VALUE
                && now - state.lastResyncTick < RESYNC_COOLDOWN_TICKS) {
            return;
        }
        state.lastResyncTick = now;
        startSnapshot(session, state);
    }

    private static void handleOpenTerminalRequest(
            ServerPlayer player,
            BigEnderChestProtocol.OpenTerminalRequest request
    ) {
        ClientState state = CLIENTS.get(player.getUUID());
        BigEnderChestSessions.Session session = BigEnderChestSessions.session(player);
        if(state == null
                || !state.accepted
                || session == null
                || request.sessionId() != session.sessionId()
                || player.containerMenu != session.menu()
                || !session.isWithinSourceRange()
                || (state.capabilities
                & BigEnderChestProtocol.CAPABILITY_TERMINAL_MENU) == 0) {
            sendActionResult(
                    player,
                    request.requestId(),
                    BigEnderChestProtocol.RESULT_PERMISSION_DENIED,
                    session == null ? -1 : session.service().revision()
            );
            return;
        }
        if(session.menu() instanceof BigEnderChestTerminalMenu) {
            sendActionResult(
                    player,
                    request.requestId(),
                    BigEnderChestProtocol.RESULT_OK,
                    session.service().revision()
            );
            return;
        }
        sendActionResult(
                player,
                request.requestId(),
                BigEnderChestProtocol.RESULT_OK,
                session.service().revision()
        );
        BigEnderChestSessions.open(player);
    }

    private static void handleAction(ServerPlayer player, ActionRequest request) {
        BigEnderChestSessions.Session session = BigEnderChestSessions.session(player);
        ClientState state = CLIENTS.get(player.getUUID());
        if(state == null || !state.accepted) {
            sendActionFailure(player, request, BigEnderChestProtocol.RESULT_SESSION_EXPIRED, session);
            return;
        }
        boolean idempotent = request.requestId() != 0;
        ActionResult cached = idempotent
                ? state.recentResults.get(request.requestId())
                : null;
        if(cached != null
                && session != null
                && cached.sessionId() == session.sessionId()) {
            sendPayload(
                    player,
                    BigEnderChestProtocol.ACTION_RESULT,
                    cached.toTag()
            );
            return;
        }
        if(idempotent) state.recentResults.remove(request.requestId());
        int status;
        if(state.actionsThisTick >= MAX_ACTIONS_PER_TICK) {
            status = BigEnderChestProtocol.RESULT_RATE_LIMITED;
        }else {
            state.actionsThisTick++;
            status = validateAction(player, session, request, state);
        }
        if(status != BigEnderChestProtocol.RESULT_OK) {
            cacheAndSendResult(
                    player,
                    state,
                    session,
                    request,
                    status
            );
            return;
        }

        AbstractContainerMenu menu = session.menu();
        BigEnderChestService service = session.service();
        ItemTemplateKey targetKey = resolveTargetKey(service, state, request);
        int requestedAmount = (int) Math.clamp(
                request.amount(),
                1L,
                Integer.MAX_VALUE
        );
        try {
            status = switch(request.action()) {
                case BigEnderChestProtocol.ACTION_TAKE_ONE ->
                        takeToCarried(menu, service, targetKey, 1);
                case BigEnderChestProtocol.ACTION_TAKE_STACK ->
                        takeToCarried(menu, service, targetKey, maxStack(targetKey));
                case BigEnderChestProtocol.ACTION_TAKE_AMOUNT ->
                        takeToCarried(menu, service, targetKey, requestedAmount);
                case BigEnderChestProtocol.ACTION_PUT_ONE ->
                        putCarried(menu, service, true);
                case BigEnderChestProtocol.ACTION_PUT_STACK ->
                        putCarried(menu, service, false);
                case BigEnderChestProtocol.ACTION_QUICK_MOVE_TO_PLAYER ->
                        quickMoveToPlayer(service, targetKey, maxStack(targetKey));
                case BigEnderChestProtocol.ACTION_QUICK_MOVE_FROM_PLAYER ->
                        quickMoveFromPlayer(service, request.button());
                case BigEnderChestProtocol.ACTION_DROP_ONE ->
                        dropFromStorage(service, targetKey, 1);
                case BigEnderChestProtocol.ACTION_DROP_STACK ->
                        dropFromStorage(service, targetKey, maxStack(targetKey));
                case BigEnderChestProtocol.ACTION_BATCH_PUT_PLAYER_SLOTS ->
                        batchPutPlayerSlots(service, request.playerSlots());
                case BigEnderChestProtocol.ACTION_BATCH_STORAGE_TO_PLAYER ->
                        quickMoveToPlayer(service, targetKey, requestedAmount);
                default -> BigEnderChestProtocol.RESULT_PERMISSION_DENIED;
            };
        }catch(RuntimeException exception) {
            FrostorsCarpetAddition.LOGGER.error(
                    "Big ender chest protocol action failed for {}",
                    player.getScoreboardName(),
                    exception
            );
            status = BigEnderChestProtocol.RESULT_INTERNAL_ERROR;
        }
        if(request.actionSequence() > 0) {
            state.lastActionSequence = request.actionSequence();
        }
        if(menu instanceof BigEnderChestMenu vanillaMenu) {
            vanillaMenu.view().refresh();
        }
        menu.broadcastChanges();
        cacheAndSendResult(
                player,
                state,
                session,
                request,
                status,
                service.revision()
        );
    }

    private static int validateAction(
            ServerPlayer player,
            BigEnderChestSessions.Session session,
            ActionRequest request,
            ClientState state
    ) {
        if(!FrostorSettings.bigEnderChest
                || !isHandshaken(player)
                || session == null
                || session.menu() == null
                || !session.service().isOpen()) {
            return BigEnderChestProtocol.RESULT_SESSION_EXPIRED;
        }
        if(request.sessionId() != session.sessionId()
                || request.containerId() != session.containerId()
                || player.containerMenu != session.menu()) {
            return BigEnderChestProtocol.RESULT_SESSION_EXPIRED;
        }
        if(!session.isWithinSourceRange()) {
            return BigEnderChestProtocol.RESULT_PERMISSION_DENIED;
        }
        if(session.service().isReadOnly()) {
            return session.service().hasPageErrors()
                    ? BigEnderChestProtocol.RESULT_PAGE_LOAD_FAILED
                    : BigEnderChestProtocol.RESULT_FEATURE_READ_ONLY;
        }
        int requiredCapabilities = requiredActionCapabilities(request.action());
        if(requiredCapabilities < 0
                || (state.capabilities & requiredCapabilities) != requiredCapabilities) {
            return BigEnderChestProtocol.RESULT_PROTOCOL_REJECTED;
        }
        boolean sequenced = request.actionSequence() > 0;
        if(sequenced
                && (state.capabilities
                & BigEnderChestProtocol.CAPABILITY_SEQUENCED_ACTIONS) == 0) {
            return BigEnderChestProtocol.RESULT_PROTOCOL_REJECTED;
        }
        if(sequenced && request.actionSequence() <= state.lastActionSequence) {
            return BigEnderChestProtocol.RESULT_DUPLICATE_REQUEST;
        }
        if(request.knownRevision() < 0
                || (!sequenced && request.knownRevision() != session.service().revision())
                || request.knownRevision() > session.service().revision()) {
            return BigEnderChestProtocol.RESULT_REVISION_STALE;
        }
        if(requiresStorageTarget(request.action())) {
            if(request.expectedTemplateSerial() > 0) {
                if(!state.keysBySerial.containsKey(request.expectedTemplateSerial())) {
                    return BigEnderChestProtocol.RESULT_RESYNC_REQUIRED;
                }
            }else {
                if(request.storagePage() < 0
                        || request.slot() < 0
                        || request.slot() >= StorageLocation.SLOTS_PER_PAGE) {
                    return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
                }
                if(!session.service().isPageReady(request.storagePage())) {
                    return BigEnderChestProtocol.RESULT_PAGE_NOT_READY;
                }
                ItemTemplateKey target = session.service().keyAt(
                        new StorageLocation(request.storagePage(), request.slot())
                );
                if(target == null) {
                    return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
                }
            }
        }
        return BigEnderChestProtocol.RESULT_OK;
    }

    private static int requiredActionCapabilities(int action) {
        return switch(action) {
            case BigEnderChestProtocol.ACTION_TAKE_ONE,
                    BigEnderChestProtocol.ACTION_TAKE_STACK,
                    BigEnderChestProtocol.ACTION_PUT_ONE,
                    BigEnderChestProtocol.ACTION_PUT_STACK,
                    BigEnderChestProtocol.ACTION_QUICK_MOVE_TO_PLAYER,
                    BigEnderChestProtocol.ACTION_QUICK_MOVE_FROM_PLAYER,
                    BigEnderChestProtocol.ACTION_TAKE_AMOUNT ->
                    BigEnderChestProtocol.CAPABILITY_ACTIONS;
            case BigEnderChestProtocol.ACTION_DROP_ONE,
                    BigEnderChestProtocol.ACTION_DROP_STACK ->
                    BigEnderChestProtocol.CAPABILITY_ACTIONS
                            | BigEnderChestProtocol.CAPABILITY_DROP_ACTIONS;
            case BigEnderChestProtocol.ACTION_BATCH_PUT_PLAYER_SLOTS ->
                    BigEnderChestProtocol.CAPABILITY_ACTIONS
                            | BigEnderChestProtocol.CAPABILITY_BATCH_ACTIONS
                            | BigEnderChestProtocol.CAPABILITY_REGION_ACTIONS;
            case BigEnderChestProtocol.ACTION_BATCH_STORAGE_TO_PLAYER ->
                    BigEnderChestProtocol.CAPABILITY_ACTIONS
                            | BigEnderChestProtocol.CAPABILITY_BATCH_ACTIONS;
            default -> -1;
        };
    }

    private static boolean requiresStorageTarget(int action) {
        return switch(action) {
            case BigEnderChestProtocol.ACTION_PUT_ONE,
                    BigEnderChestProtocol.ACTION_PUT_STACK,
                    BigEnderChestProtocol.ACTION_QUICK_MOVE_FROM_PLAYER,
                    BigEnderChestProtocol.ACTION_BATCH_PUT_PLAYER_SLOTS -> false;
            default -> true;
        };
    }

    private static ItemTemplateKey resolveTargetKey(
            BigEnderChestService service,
            ClientState state,
            ActionRequest request
    ) {
        if(request.expectedTemplateSerial() > 0) {
            return state.keysBySerial.get(request.expectedTemplateSerial());
        }
        if(request.storagePage() < 0 || request.slot() < 0) return null;
        return service.keyAt(new StorageLocation(request.storagePage(), request.slot()));
    }

    private static int takeToCarried(
            AbstractContainerMenu menu,
            BigEnderChestService service,
            ItemTemplateKey key,
            int amount
    ) {
        if(key == null || !menu.getCarried().isEmpty()) {
            return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
        }
        ItemStack taken = service.take(key, Math.min(amount, maxStack(key)));
        if(taken.isEmpty()) return takeFailure(service, key);
        menu.setCarried(taken);
        return BigEnderChestProtocol.RESULT_OK;
    }

    private static int putCarried(
            AbstractContainerMenu menu,
            BigEnderChestService service,
            boolean single
    ) {
        ItemStack carried = menu.getCarried();
        if(carried.isEmpty()) return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
        int amount = single ? 1 : carried.getCount();
        ItemStack offered = carried.copyWithCount(amount);
        if(!service.insert(offered) || !offered.isEmpty()) {
            menu.setCarried(carried);
            return BigEnderChestProtocol.RESULT_CAPACITY_FULL;
        }
        carried.shrink(amount);
        menu.setCarried(carried);
        return BigEnderChestProtocol.RESULT_OK;
    }

    private static int quickMoveToPlayer(
            BigEnderChestService service,
            ItemTemplateKey key,
            int amount
    ) {
        if(key == null) {
            return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
        }
        int movable = Math.min(
                Math.max(1, amount),
                playerInventoryCapacity(service.owner(), key)
        );
        if(movable <= 0) {
            return BigEnderChestProtocol.RESULT_INVENTORY_FULL;
        }
        ItemStack taken = service.take(key, movable);
        if(taken.isEmpty()) return takeFailure(service, key);
        if(!insertIntoPlayerInventory(service.owner(), key, taken)) {
            if(!service.insert(taken)) {
                dropAtPlayer(service.owner(), taken);
            }
            return BigEnderChestProtocol.RESULT_INVENTORY_FULL;
        }
        return BigEnderChestProtocol.RESULT_OK;
    }

    private static int playerInventoryCapacity(
            ServerPlayer player,
            ItemTemplateKey key
    ) {
        Inventory inventory = player.getInventory();
        int maxStack = maxStack(key);
        long capacity = 0;
        for(int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack current = inventory.getItem(slot);
            if(current.isEmpty()) {
                capacity += maxStack;
            }else if(key.matches(current)) {
                capacity += Math.max(0, current.getMaxStackSize() - current.getCount());
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    private static boolean insertIntoPlayerInventory(
            ServerPlayer player,
            ItemTemplateKey key,
            ItemStack source
    ) {
        Inventory inventory = player.getInventory();
        for(int slot = 0; slot < Inventory.INVENTORY_SIZE && !source.isEmpty(); slot++) {
            ItemStack current = inventory.getItem(slot);
            if(current.isEmpty() || !key.matches(current)) continue;
            int room = current.getMaxStackSize() - current.getCount();
            if(room <= 0) continue;
            int moved = Math.min(room, source.getCount());
            ItemStack updated = current.copy();
            updated.grow(moved);
            inventory.setItem(slot, updated);
            source.shrink(moved);
        }
        for(int slot = 0; slot < Inventory.INVENTORY_SIZE && !source.isEmpty(); slot++) {
            if(!inventory.getItem(slot).isEmpty()) continue;
            int moved = Math.min(key.template().getMaxStackSize(), source.getCount());
            inventory.setItem(slot, source.copyWithCount(moved));
            source.shrink(moved);
        }
        inventory.setChanged();
        return source.isEmpty();
    }

    private static int quickMoveFromPlayer(
            BigEnderChestService service,
            byte button
    ) {
        int inventorySlot = Byte.toUnsignedInt(button);
        if(inventorySlot >= Inventory.INVENTORY_SIZE) {
            return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
        }
        ItemStack stack = service.owner().getInventory().getItem(inventorySlot);
        if(stack.isEmpty()) return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
        ItemStack remaining = stack.copy();
        if(!service.insert(remaining)) {
            return BigEnderChestProtocol.RESULT_CAPACITY_FULL;
        }
        service.owner().getInventory().setItem(inventorySlot, remaining);
        return BigEnderChestProtocol.RESULT_OK;
    }

    private static int batchPutPlayerSlots(
            BigEnderChestService service,
            List<Integer> playerSlots
    ) {
        if(playerSlots.isEmpty()
                || playerSlots.size() > BigEnderChestCapacity.MAX_BATCH_SLOTS) {
            return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
        }
        List<Integer> uniqueSlots = playerSlots.stream().distinct().toList();
        if(uniqueSlots.size() != playerSlots.size()) {
            return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
        }
        List<ItemStack> copies = new ArrayList<>(uniqueSlots.size());
        for(int slot : uniqueSlots) {
            if(slot < 0 || slot >= Inventory.INVENTORY_SIZE) {
                return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
            }
            ItemStack stack = service.owner().getInventory().getItem(slot);
            if(stack.isEmpty()) return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
            copies.add(stack.copy());
        }
        if(!service.insertAll(copies)) {
            return BigEnderChestProtocol.RESULT_CAPACITY_FULL;
        }
        for(int slot : uniqueSlots) {
            service.owner().getInventory().setItem(slot, ItemStack.EMPTY);
        }
        service.owner().getInventory().setChanged();
        return BigEnderChestProtocol.RESULT_OK;
    }

    private static int dropFromStorage(
            BigEnderChestService service,
            ItemTemplateKey key,
            int amount
    ) {
        if(key == null) return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
        ItemStack dropped = service.take(key, amount);
        if(dropped.isEmpty()) return takeFailure(service, key);
        dropAtPlayer(service.owner(), dropped);
        return BigEnderChestProtocol.RESULT_OK;
    }

    private static int takeFailure(BigEnderChestService service, ItemTemplateKey key) {
        return key != null && service.amount(key) == 0
                ? BigEnderChestProtocol.RESULT_ENTRY_EMPTY
                : BigEnderChestProtocol.RESULT_CAPACITY_FULL;
    }

    private static void dropAtPlayer(ServerPlayer player, ItemStack stack) {
        ItemEntity itemEntity = new ItemEntity(
                player.level(),
                player.getX(),
                player.getEyeY() - 0.3,
                player.getZ(),
                stack
        );
        Vec3 look = player.getLookAngle();
        itemEntity.setDeltaMovement(look.x * 0.3, 0.1, look.z * 0.3);
        itemEntity.setThrower(player);
        itemEntity.setDefaultPickUpDelay();
        player.level().addFreshEntity(itemEntity);
    }

    private static int maxStack(ItemTemplateKey key) {
        return key == null ? 1 : Math.max(1, key.template().getMaxStackSize());
    }

    private static void sendActionFailure(
            ServerPlayer player,
            ActionRequest request,
            int status,
            BigEnderChestSessions.Session session
    ) {
        sendActionResult(
                player,
                request.requestId(),
                status,
                session == null ? -1 : session.service().revision()
        );
    }

    private static void cacheAndSendResult(
            ServerPlayer player,
            ClientState state,
            BigEnderChestSessions.Session session,
            ActionRequest request,
            int status
    ) {
        cacheAndSendResult(
                player,
                state,
                session,
                request,
                status,
                session == null ? -1 : session.service().revision()
        );
    }

    private static void cacheAndSendResult(
            ServerPlayer player,
            ClientState state,
            BigEnderChestSessions.Session session,
            ActionRequest request,
            int status,
            long revision
    ) {
        ActionResult result = new ActionResult(
                session == null ? 0 : session.sessionId(),
                request.requestId(),
                status,
                revision
        );
        if(request.requestId() != 0) {
            state.recentResults.put(request.requestId(), result);
            while(state.recentResults.size() > RECENT_RESULT_LIMIT) {
                state.recentResults.remove(state.recentResults.keySet().iterator().next());
            }
        }
        sendPayload(
                player,
                BigEnderChestProtocol.ACTION_RESULT,
                result.toTag()
        );
    }

    private static void sendActionResult(
            ServerPlayer player,
            long requestId,
            int status,
            long revision
    ) {
        BigEnderChestSessions.Session session = BigEnderChestSessions.session(player);
        long sessionId = session == null ? 0 : session.sessionId();
        sendPayload(
                player,
                BigEnderChestProtocol.ACTION_RESULT,
                new ActionResult(sessionId, requestId, status, revision).toTag()
        );
    }

    private static void sendPayload(ServerPlayer player, int type, CompoundTag data) {
        if(!ServerPlayNetworking.canSend(player, Payload.TYPE)) return;
        ClientState state = CLIENTS.get(player.getUUID());
        if(state == null || !state.accepted) return;
        ServerPlayNetworking.send(player, new Payload(type, data));
    }

    private static final class ClientState {
        private boolean accepted;
        private int capabilities;
        private int chunkByteLimit = BigEnderChestProtocol.MAX_CHUNK_BYTES;
        private final Map<ItemTemplateKey, Long> serials = new HashMap<>();
        private final Map<Long, ItemTemplateKey> keysBySerial = new HashMap<>();
        private final Map<ItemTemplateKey, Long> sentAmounts = new HashMap<>();
        private final Map<ItemTemplateKey, Long> snapshotAmounts = new HashMap<>();
        private long nextSerial = 1;
        private long sentRevision = -1;
        private long sentPageRevision = -1;
        private long snapshotId;
        private long snapshotRevision = -1;
        private final List<byte[]> snapshotChunks = new ArrayList<>();
        private final Map<Long, ActionResult> recentResults = new LinkedHashMap<>(128, 0.75f, true);
        private final Deque<Integer> retransmitChunks = new ArrayDeque<>();
        private int nextChunk;
        private boolean snapshotEndSent;
        private boolean awaitingSnapshotAck;
        private boolean snapshotBuilding;
        private boolean snapshotPending;
        private int snapshotAckTicks;
        private long lastActionSequence;
        private long lastResyncTick = Long.MIN_VALUE;
        private long closedSessionId;
        private int actionsThisTick;

        private void clearSnapshot() {
            snapshotChunks.clear();
            retransmitChunks.clear();
            nextChunk = 0;
            snapshotId = 0;
            snapshotRevision = -1;
            snapshotEndSent = false;
            awaitingSnapshotAck = false;
            snapshotBuilding = false;
            snapshotPending = false;
            snapshotAckTicks = 0;
            snapshotAmounts.clear();
        }
    }
}
