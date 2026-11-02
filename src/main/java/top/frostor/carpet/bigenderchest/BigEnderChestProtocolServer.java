package top.frostor.carpet.bigenderchest;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.frostor.carpet.FrostorSettings;
import top.frostor.carpet.FrostorsCarpetAddition;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol;
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
import top.frostor.carpet.bigenderchest.index.ItemTemplateKey;
import top.frostor.carpet.bigenderchest.index.StorageLocation;
import top.frostor.carpet.bigenderchest.menu.BigEnderChestMenu;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;

public final class BigEnderChestProtocolServer {
    private static final Map<UUID, ClientState> CLIENTS = new ConcurrentHashMap<>();
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
        sendSessionOpen(session, state);
        startSnapshot(session, state);
    }

    public static void sessionClosed(BigEnderChestSessions.Session session) {
        ClientState state = CLIENTS.get(session.owner().getUUID());
        if(state != null) {
            state.clearSnapshot();
        }
    }

    public static void tick(MinecraftServer server) {
        for(Map.Entry<UUID, ClientState> entry : CLIENTS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ClientState state = entry.getValue();
            if(player == null || !state.accepted) continue;
            BigEnderChestSessions.Session session = BigEnderChestSessions.session(player);
            if(session == null || !session.service().isOpen()) {
                state.clearSnapshot();
                continue;
            }
            if(!state.snapshotChunks.isEmpty()) {
                sendNextSnapshotChunk(session, state);
                continue;
            }
            if(session.service().revision() != state.sentRevision) {
                sendDeltaOrSnapshot(session, state);
            }
        }
    }

    static boolean isHandshaken(ServerPlayer player) {
        ClientState state = CLIENTS.get(player.getUUID());
        return state != null && state.accepted;
    }

    private static void handle(ServerPlayer player, Payload payload) {
        if(!FrostorSettings.bigEnderChest) return;
        switch(payload.messageType()) {
            case BigEnderChestProtocol.HELLO -> handleHello(player, payload.data());
            case BigEnderChestProtocol.ACTION_REQUEST -> handleAction(
                    player,
                    ActionRequest.fromTag(payload.data())
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
        ClientState state = accepted
                ? CLIENTS.computeIfAbsent(player.getUUID(), ignored -> new ClientState())
                : CLIENTS.get(player.getUUID());
        if(state != null) {
            state.accepted = accepted;
            state.chunkByteLimit = chunkLimit;
        }
        if(ServerPlayNetworking.canSend(player, Payload.TYPE)) {
            ServerPlayNetworking.send(player, new Payload(
                    BigEnderChestProtocol.HELLO_ACK,
                    new HelloAck(
                            accepted ? BigEnderChestProtocol.PROTOCOL_VERSION : 0,
                            BigEnderChestProtocol.CAPABILITIES,
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
        sendPayload(
                session.owner(),
                BigEnderChestProtocol.SESSION_OPEN,
                new SessionOpen(
                        session.sessionId(),
                        session.containerId(),
                        session.service().revision(),
                        FrostorSettings.bigEnderChestPageLimit,
                        0
                ).toTag()
        );
    }

    private static void startSnapshot(
            BigEnderChestSessions.Session session,
            ClientState state
    ) {
        session.service().takePendingDelta();
        Map<ItemTemplateKey, Long> templates = new HashMap<>();
        List<TemplateRecord> templateRecords = new ArrayList<>();
        List<SlotRecord> slotRecords = new ArrayList<>();
        long nextSerial = 1;

        for(Map.Entry<Integer, net.minecraft.world.Container> page
                : session.service().readyContainers().entrySet()) {
            for(int slot = 0; slot < StorageLocation.SLOTS_PER_PAGE; slot++) {
                ItemStack stack = page.getValue().getItem(slot);
                ItemTemplateKey key = ItemTemplateKey.of(stack);
                if(key == null) continue;
                long serial = templates.computeIfAbsent(key, ignored -> 0L);
                if(serial == 0) {
                    serial = nextSerial++;
                    templates.put(key, serial);
                    templateRecords.add(new TemplateRecord(serial, stack));
                }
                slotRecords.add(new SlotRecord(
                        page.getKey(),
                        slot,
                        serial,
                        stack.getCount()
                ));
            }
        }

        List<byte[]> chunks = buildSnapshotChunks(
                session.owner(),
                templateRecords,
                slotRecords,
                state.chunkByteLimit
        );
        long snapshotId = session.owner().getRandom().nextLong();
        state.serials.clear();
        state.serials.putAll(templates);
        state.nextSerial = nextSerial;
        state.snapshotId = snapshotId;
        state.snapshotRevision = session.service().revision();
        state.snapshotChunks.addAll(chunks);
        state.nextChunk = 0;
        sendPayload(
                session.owner(),
                BigEnderChestProtocol.SNAPSHOT_BEGIN,
                new SnapshotBegin(
                        session.sessionId(),
                        snapshotId,
                        state.snapshotRevision,
                        templateRecords.size(),
                        slotRecords.size(),
                        chunks.size()
                ).toTag()
        );
    }

    private static List<byte[]> buildSnapshotChunks(
            ServerPlayer player,
            List<TemplateRecord> templates,
            List<SlotRecord> slots,
            int chunkByteLimit
    ) {
        List<byte[]> chunks = new ArrayList<>();
        ListTag currentTemplates = new ListTag();
        ListTag currentSlots = new ListTag();
        for(TemplateRecord record : templates) {
            CompoundTag recordTag = record.toTag(player.registryAccess());
            if(!canAppend(currentTemplates, currentSlots, recordTag, chunkByteLimit)) {
                chunks.add(encodeChunk(currentTemplates, currentSlots));
                currentTemplates = new ListTag();
                currentSlots = new ListTag();
            }
            currentTemplates.add(recordTag);
        }
        for(SlotRecord record : slots) {
            CompoundTag recordTag = record.toTag();
            if(!canAppend(currentTemplates, currentSlots, recordTag, chunkByteLimit)) {
                chunks.add(encodeChunk(currentTemplates, currentSlots));
                currentTemplates = new ListTag();
                currentSlots = new ListTag();
            }
            currentSlots.add(recordTag);
        }
        if(!currentTemplates.isEmpty() || !currentSlots.isEmpty() || chunks.isEmpty()) {
            chunks.add(encodeChunk(currentTemplates, currentSlots));
        }
        return chunks;
    }

    private static boolean canAppend(
            ListTag templates,
            ListTag slots,
            CompoundTag record,
            int limit
    ) {
        int records = templates.size() + slots.size();
        return records == 0 || record.sizeInBytes() + templates.sizeInBytes()
                + slots.sizeInBytes() < limit;
    }

    private static byte[] encodeChunk(ListTag templates, ListTag slots) {
        CompoundTag chunk = new CompoundTag();
        chunk.put("templates", templates);
        chunk.put("slots", slots);
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
        int index = state.nextChunk++;
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
        if(state.nextChunk >= state.snapshotChunks.size()) {
            state.clearSnapshot();
            state.sentRevision = state.snapshotRevision;
            if(session.service().revision() != state.sentRevision) {
                startSnapshot(session, state);
            }
        }
    }

    private static void sendDeltaOrSnapshot(
            BigEnderChestSessions.Session session,
            ClientState state
    ) {
        BigEnderChestService.Delta pending = session.service().takePendingDelta();
        if(pending == null || pending.baseRevision() != state.sentRevision) {
            startSnapshot(session, state);
            return;
        }

        List<TemplateRecord> templates = new ArrayList<>();
        List<SlotRecord> changes = new ArrayList<>();
        for(BigEnderChestService.SlotChange change : pending.changes()) {
            ItemTemplateKey key = ItemTemplateKey.of(change.stack());
            long serial = 0;
            if(key != null) {
                serial = state.serials.getOrDefault(key, 0L);
                if(serial == 0) {
                    serial = state.nextSerial++;
                    state.serials.put(key, serial);
                    templates.add(new TemplateRecord(serial, change.stack()));
                }
            }
            changes.add(new SlotRecord(
                    change.location().storagePage(),
                    change.location().slot(),
                    serial,
                    change.stack().getCount()
            ));
        }
        Delta delta = new Delta(
                session.sessionId(),
                pending.baseRevision(),
                pending.revision(),
                templates,
                changes,
                pending.pageAdds(),
                !pending.pageAdds().isEmpty()
        );
        CompoundTag data = delta.toTag(session.owner().registryAccess());
        if(data.sizeInBytes() > state.chunkByteLimit) {
            startSnapshot(session, state);
            return;
        }
        sendPayload(session.owner(), BigEnderChestProtocol.DELTA, data);
        state.sentRevision = pending.revision();
    }

    private static void handleAction(ServerPlayer player, ActionRequest request) {
        BigEnderChestSessions.Session session = BigEnderChestSessions.session(player);
        int status = validateAction(player, session, request);
        if(status != BigEnderChestProtocol.RESULT_OK) {
            sendActionFailure(player, request, status, session);
            return;
        }

        BigEnderChestMenu menu = session.menu();
        BigEnderChestService service = session.service();
        StorageLocation location = new StorageLocation(request.storagePage(), request.slot());
        ItemTemplateKey targetKey = service.keyAt(location);
        try {
            status = switch(request.action()) {
                case BigEnderChestProtocol.ACTION_TAKE_ONE ->
                        takeToCarried(menu, service, targetKey, 1);
                case BigEnderChestProtocol.ACTION_TAKE_STACK ->
                        takeToCarried(menu, service, targetKey, maxStack(targetKey));
                case BigEnderChestProtocol.ACTION_PUT_ONE ->
                        putCarried(menu, service, true);
                case BigEnderChestProtocol.ACTION_PUT_STACK ->
                        putCarried(menu, service, false);
                case BigEnderChestProtocol.ACTION_QUICK_MOVE_TO_PLAYER ->
                        quickMoveToPlayer(menu, service, targetKey);
                case BigEnderChestProtocol.ACTION_QUICK_MOVE_FROM_PLAYER ->
                        quickMoveFromPlayer(menu, service, request.button());
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
        menu.view().refresh();
        menu.broadcastChanges();
        sendActionResult(
                player,
                request.requestId(),
                status,
                service.revision()
        );
    }

    private static int validateAction(
            ServerPlayer player,
            BigEnderChestSessions.Session session,
            ActionRequest request
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
        if(request.knownRevision() != session.service().revision()) {
            return BigEnderChestProtocol.RESULT_REVISION_STALE;
        }
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
        long expected = target == null ? 0 : serialFor(session, target);
        if(request.expectedTemplateSerial() != expected) {
            return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
        }
        return BigEnderChestProtocol.RESULT_OK;
    }

    private static long serialFor(
            BigEnderChestSessions.Session session,
            ItemTemplateKey key
    ) {
        ClientState state = CLIENTS.get(session.owner().getUUID());
        return state == null ? Long.MIN_VALUE : state.serials.getOrDefault(key, Long.MIN_VALUE);
    }

    private static int takeToCarried(
            BigEnderChestMenu menu,
            BigEnderChestService service,
            ItemTemplateKey key,
            int amount
    ) {
        if(key == null || !menu.getCarried().isEmpty()) {
            return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
        }
        ItemStack taken = service.take(key, amount);
        if(taken.isEmpty()) return BigEnderChestProtocol.RESULT_CAPACITY_FULL;
        menu.setCarried(taken);
        return BigEnderChestProtocol.RESULT_OK;
    }

    private static int putCarried(
            BigEnderChestMenu menu,
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
            BigEnderChestMenu menu,
            BigEnderChestService service,
            ItemTemplateKey key
    ) {
        if(key == null || !menu.getCarried().isEmpty()) {
            return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
        }
        ItemStack taken = service.take(key, maxStack(key));
        if(taken.isEmpty()) return BigEnderChestProtocol.RESULT_CAPACITY_FULL;
        menu.moveToPlayerInventory(taken);
        if(!taken.isEmpty() && !service.insert(taken)) {
            menu.service().owner().getInventory().add(taken);
            return BigEnderChestProtocol.RESULT_INVENTORY_FULL;
        }
        return taken.isEmpty()
                ? BigEnderChestProtocol.RESULT_OK
                : BigEnderChestProtocol.RESULT_INVENTORY_FULL;
    }

    private static int quickMoveFromPlayer(
            BigEnderChestMenu menu,
            BigEnderChestService service,
            byte button
    ) {
        int inventorySlot = Byte.toUnsignedInt(button);
        if(inventorySlot >= menu.service().owner().getInventory().getContainerSize()) {
            return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
        }
        ItemStack stack = menu.service().owner().getInventory().getItem(inventorySlot);
        if(stack.isEmpty()) return BigEnderChestProtocol.RESULT_SLOT_MISMATCH;
        ItemStack remaining = stack.copy();
        if(!service.insert(remaining)) {
            return BigEnderChestProtocol.RESULT_CAPACITY_FULL;
        }
        menu.service().owner().getInventory().setItem(inventorySlot, remaining);
        return BigEnderChestProtocol.RESULT_OK;
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
        private int chunkByteLimit = BigEnderChestProtocol.MAX_CHUNK_BYTES;
        private final Map<ItemTemplateKey, Long> serials = new HashMap<>();
        private long nextSerial = 1;
        private long sentRevision = -1;
        private long snapshotId;
        private long snapshotRevision = -1;
        private final List<byte[]> snapshotChunks = new ArrayList<>();
        private int nextChunk;

        private void clearSnapshot() {
            snapshotChunks.clear();
            nextChunk = 0;
            snapshotId = 0;
            snapshotRevision = -1;
        }
    }
}
