package top.frostor.carpet.bigenderchest.api;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import top.frostor.carpet.FrostorsCarpetAddition;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable client-tool protocol DTOs. The GUI itself lives in a separate client project.
 */
public final class BigEnderChestProtocol {
    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath(
            FrostorsCarpetAddition.MOD_ID,
            "big_ender_chest"
    );
    public static final int PROTOCOL_VERSION = 2;
    public static final int MAX_CHUNK_BYTES = 256 * 1024;
    public static final int UNLIMITED_PAGE_LIMIT = -1;
    public static final Identifier TERMINAL_MENU_ID = Identifier.fromNamespaceAndPath(
            FrostorsCarpetAddition.MOD_ID,
            "big_ender_chest_terminal"
    );
    public static final int TERMINAL_PLAYER_SLOT_COUNT = 36;
    public static final int TERMINAL_MAIN_SLOT_START = 0;
    public static final int TERMINAL_HOTBAR_SLOT_START = 27;

    public static final int CAPABILITY_SNAPSHOT = 1;
    public static final int CAPABILITY_DELTA = 1 << 1;
    public static final int CAPABILITY_ACTIONS = 1 << 2;
    public static final int CAPABILITY_SEARCH_LOCAL = 1 << 3;
    public static final int CAPABILITY_PAGE_CATALOG = 1 << 4;
    public static final int CAPABILITY_TERMINAL_MENU = 1 << 5;
    public static final int CAPABILITY_BATCH_ACTIONS = 1 << 6;
    public static final int CAPABILITY_REGION_ACTIONS = 1 << 7;
    public static final int CAPABILITY_DROP_ACTIONS = 1 << 8;
    public static final int CAPABILITY_SEQUENCED_ACTIONS = 1 << 9;
    public static final int CAPABILITY_SNAPSHOT_ACK = 1 << 10;
    public static final int CAPABILITY_SESSION_CLOSE = 1 << 11;
    public static final int CAPABILITY_RULE_STATE_ACK = 1 << 12;
    public static final int CAPABILITY_TERMINAL_MENU_ACTIVE = 1 << 13;
    public static final int CAPABILITIES = CAPABILITY_SNAPSHOT
            | CAPABILITY_DELTA
            | CAPABILITY_ACTIONS
            | CAPABILITY_SEARCH_LOCAL
            | CAPABILITY_PAGE_CATALOG
            | CAPABILITY_TERMINAL_MENU
            | CAPABILITY_BATCH_ACTIONS
            | CAPABILITY_REGION_ACTIONS
            | CAPABILITY_DROP_ACTIONS
            | CAPABILITY_SEQUENCED_ACTIONS
            | CAPABILITY_SNAPSHOT_ACK
            | CAPABILITY_SESSION_CLOSE
            | CAPABILITY_RULE_STATE_ACK
            | CAPABILITY_TERMINAL_MENU_ACTIVE;

    public static final int HELLO = 1;
    public static final int HELLO_ACK = 2;
    public static final int SESSION_OPEN = 3;
    public static final int SNAPSHOT_BEGIN = 4;
    public static final int SNAPSHOT_CHUNK = 5;
    public static final int DELTA = 6;
    public static final int ACTION_REQUEST = 7;
    public static final int ACTION_RESULT = 8;
    public static final int SESSION_CLOSE = 9;
    public static final int PAGE_CATALOG = 10;
    public static final int SNAPSHOT_END = 11;
    public static final int SNAPSHOT_ACK = 12;
    public static final int REQUEST_RESYNC = 13;
    public static final int OPEN_TERMINAL_REQUEST = 14;

    public static final int ACTION_TAKE_ONE = 1;
    public static final int ACTION_TAKE_STACK = 2;
    public static final int ACTION_PUT_ONE = 3;
    public static final int ACTION_PUT_STACK = 4;
    public static final int ACTION_QUICK_MOVE_TO_PLAYER = 5;
    public static final int ACTION_QUICK_MOVE_FROM_PLAYER = 6;
    public static final int ACTION_TAKE_AMOUNT = 7;
    public static final int ACTION_DROP_ONE = 8;
    public static final int ACTION_DROP_STACK = 9;
    public static final int ACTION_BATCH_PUT_PLAYER_SLOTS = 10;
    public static final int ACTION_BATCH_PLAYER_SLOTS_TO_STORAGE = ACTION_BATCH_PUT_PLAYER_SLOTS;
    public static final int ACTION_BATCH_STORAGE_TO_PLAYER = 11;

    public static final int RESULT_OK = 0;
    public static final int RESULT_SESSION_EXPIRED = 1;
    public static final int RESULT_REVISION_STALE = 2;
    public static final int RESULT_PAGE_NOT_READY = 3;
    public static final int RESULT_SLOT_MISMATCH = 4;
    public static final int RESULT_CAPACITY_FULL = 5;
    public static final int RESULT_INVENTORY_FULL = 6;
    public static final int RESULT_PERMISSION_DENIED = 7;
    public static final int RESULT_INTERNAL_ERROR = 8;
    public static final int RESULT_PROTOCOL_REJECTED = 9;
    public static final int RESULT_PAGE_LOAD_FAILED = 10;
    public static final int RESULT_DISK_FULL = 11;
    public static final int RESULT_FEATURE_READ_ONLY = 12;
    public static final int RESULT_INDEX_INVALID = 13;
    public static final int RESULT_RESYNC_REQUIRED = 14;
    public static final int RESULT_RATE_LIMITED = 15;
    public static final int RESULT_DUPLICATE_REQUEST = 16;
    public static final int RESULT_ENTRY_EMPTY = 17;

    private BigEnderChestProtocol() {
    }

    public record Payload(int messageType, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT,
                        Payload::messageType,
                        ByteBufCodecs.compoundTagCodec(
                                () -> NbtAccounter.create(MAX_CHUNK_BYTES * 2L)
                        ),
                        Payload::data,
                        Payload::new
                );

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }

    public record Hello(int protocolVersion, int capabilities, int maxChunkBytes) {
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("protocol_version", protocolVersion);
            tag.putInt("capabilities", capabilities);
            tag.putInt("max_chunk_bytes", maxChunkBytes);
            return tag;
        }

        public static Hello fromTag(CompoundTag tag) {
            return new Hello(
                    tag.getIntOr("protocol_version", -1),
                    tag.getIntOr("capabilities", 0),
                    tag.getIntOr("max_chunk_bytes", 0)
            );
        }
    }

    public record HelloAck(
            int protocolVersion,
            int capabilities,
            int chunkByteLimit,
            boolean accepted
    ) {
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("protocol_version", protocolVersion);
            tag.putInt("capabilities", capabilities);
            tag.putInt("chunk_byte_limit", chunkByteLimit);
            tag.putBoolean("accepted", accepted);
            return tag;
        }

        public static HelloAck fromTag(CompoundTag tag) {
            return new HelloAck(
                    tag.getIntOr("protocol_version", -1),
                    tag.getIntOr("capabilities", 0),
                    tag.getIntOr("chunk_byte_limit", 0),
                    tag.getBooleanOr("accepted", false)
            );
        }
    }

    public record PageInfo(
            int pageId,
            String state,
            boolean empty,
            int occupiedSlots,
            long contentRevision
    ) {
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("page_id", pageId);
            tag.putString("state", state);
            tag.putBoolean("empty", empty);
            tag.putInt("occupied_slots", occupiedSlots);
            tag.putLong("content_revision", contentRevision);
            return tag;
        }

        public static PageInfo fromTag(CompoundTag tag) {
            return new PageInfo(
                    tag.getIntOr("page_id", -1),
                    tag.getStringOr("state", "ERROR"),
                    tag.getBooleanOr("empty", true),
                    tag.getIntOr("occupied_slots", 0),
                    tag.getLongOr("content_revision", 0)
            );
        }
    }

    public record PageCatalog(
            long sessionId,
            long pageRevision,
            int totalSlots,
            int occupiedSlots,
            boolean readOnly,
            List<PageInfo> pages
    ) {
        public PageCatalog(long sessionId, long pageRevision, List<PageInfo> pages) {
            this(
                    sessionId,
                    pageRevision,
                    pages.size() * 27,
                    pages.stream().mapToInt(PageInfo::occupiedSlots).sum(),
                    pages.stream().anyMatch(page ->
                            !page.state().equals("READY")
                                    && !page.state().equals("DIRTY")
                                    && !page.state().equals("SAVING")),
                    pages
            );
        }

        public PageCatalog {
            pages = List.copyOf(pages);
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("session_id", sessionId);
            tag.putLong("page_revision", pageRevision);
            tag.putInt("total_slots", totalSlots);
            tag.putInt("occupied_slots", occupiedSlots);
            tag.putBoolean("read_only", readOnly);
            ListTag pageList = new ListTag();
            pages.forEach(page -> pageList.add(page.toTag()));
            tag.put("pages", pageList);
            return tag;
        }

        public static PageCatalog fromTag(CompoundTag tag) {
            List<PageInfo> pages = new ArrayList<>();
            tag.getListOrEmpty("pages").forEach(element ->
                    pages.add(PageInfo.fromTag((CompoundTag) element)));
            return new PageCatalog(
                    tag.getLongOr("session_id", 0),
                    tag.getLongOr("page_revision", 0),
                    tag.getIntOr("total_slots", 0),
                    tag.getIntOr("occupied_slots", 0),
                    tag.getBooleanOr("read_only", false),
                    pages
            );
        }
    }

    public record SessionOpen(
            long sessionId,
            int containerId,
            long revision,
            int pageLimit,
            int currentPageCount,
            long pageRevision,
            boolean readOnly,
            int capabilities,
            int terminalMenuId,
            int flags
    ) {
        public SessionOpen(
                long sessionId,
                int containerId,
                long revision,
                int pageLimit,
                int flags
        ) {
            this(
                    sessionId,
                    containerId,
                    revision,
                    pageLimit,
                    1,
                    revision,
                    false,
                    0,
                    containerId,
                    flags
            );
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("session_id", sessionId);
            tag.putInt("container_id", containerId);
            tag.putLong("revision", revision);
            tag.putInt("page_limit", pageLimit);
            tag.putInt("current_page_count", currentPageCount);
            tag.putLong("page_revision", pageRevision);
            tag.putBoolean("read_only", readOnly);
            tag.putInt("capabilities", capabilities);
            tag.putInt("terminal_menu_id", terminalMenuId);
            tag.putInt("flags", flags);
            return tag;
        }

        public static SessionOpen fromTag(CompoundTag tag) {
            return new SessionOpen(
                    tag.getLongOr("session_id", 0),
                    tag.getIntOr("container_id", -1),
                    tag.getLongOr("revision", -1),
                    tag.getIntOr("page_limit", 0),
                    tag.getIntOr("current_page_count", 1),
                    tag.getLongOr("page_revision", -1),
                    tag.getBooleanOr("read_only", false),
                    tag.getIntOr("capabilities", 0),
                    tag.getIntOr("terminal_menu_id", -1),
                    tag.getIntOr("flags", 0)
            );
        }
    }

    public record SnapshotBegin(
            long sessionId,
            long snapshotId,
            long revision,
            int templateCount,
            int slotCount,
            int chunkCount
    ) {
        public int entryCount() {
            return templateCount;
        }

        public int locationCount() {
            return slotCount;
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("session_id", sessionId);
            tag.putLong("snapshot_id", snapshotId);
            tag.putLong("revision", revision);
            tag.putInt("template_count", templateCount);
            tag.putInt("slot_count", slotCount);
            tag.putInt("entry_count", templateCount);
            tag.putInt("location_count", slotCount);
            tag.putInt("chunk_count", chunkCount);
            return tag;
        }

        public static SnapshotBegin fromTag(CompoundTag tag) {
            return new SnapshotBegin(
                    tag.getLongOr("session_id", 0),
                    tag.getLongOr("snapshot_id", 0),
                    tag.getLongOr("revision", -1),
                    tag.getIntOr("entry_count", tag.getIntOr("template_count", 0)),
                    tag.getIntOr("location_count", tag.getIntOr("slot_count", 0)),
                    tag.getIntOr("chunk_count", 0)
            );
        }
    }

    public record TemplateRecord(long templateSerial, ItemStack item) {
        public TemplateRecord {
            item = item.copyWithCount(1);
        }

        public CompoundTag toTag(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("template_serial", templateSerial);
            tag.store(
                    "item",
                    ItemStack.OPTIONAL_CODEC,
                    registries.createSerializationContext(NbtOps.INSTANCE),
                    item.copyWithCount(1)
            );
            return tag;
        }

        public static TemplateRecord fromTag(CompoundTag tag, HolderLookup.Provider registries) {
            ItemStack item = tag.read(
                    "item",
                    ItemStack.OPTIONAL_CODEC,
                    registries.createSerializationContext(NbtOps.INSTANCE)
            ).orElse(ItemStack.EMPTY).copyWithCount(1);
            return new TemplateRecord(tag.getLongOr("template_serial", 0), item);
        }
    }

    public record EntryRecord(
            long templateSerial,
            ItemStack item,
            long totalAmount,
            List<SlotRecord> locations
    ) {
        public EntryRecord {
            item = item.copyWithCount(1);
            locations = locations == null ? List.of() : List.copyOf(locations);
        }

        public CompoundTag toTag(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("template_serial", templateSerial);
            tag.store(
                    "item",
                    ItemStack.OPTIONAL_CODEC,
                    registries.createSerializationContext(NbtOps.INSTANCE),
                    item
            );
            tag.putLong("total_amount", totalAmount);
            ListTag locationList = new ListTag();
            locations.forEach(location -> locationList.add(location.toTag()));
            tag.put("locations", locationList);
            return tag;
        }

        public static EntryRecord fromTag(
                CompoundTag tag,
                HolderLookup.Provider registries
        ) {
            ItemStack item = tag.read(
                    "item",
                    ItemStack.OPTIONAL_CODEC,
                    registries.createSerializationContext(NbtOps.INSTANCE)
            ).orElse(ItemStack.EMPTY).copyWithCount(1);
            List<SlotRecord> locations = new ArrayList<>();
            tag.getListOrEmpty("locations").forEach(element ->
                    locations.add(SlotRecord.fromTag((CompoundTag) element)));
            return new EntryRecord(
                    tag.getLongOr("template_serial", 0),
                    item,
                    tag.getLongOr("total_amount", 0),
                    locations
            );
        }
    }

    public record SlotRecord(int storagePage, int slot, long templateSerial, long amount) {
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("storage_page", storagePage);
            tag.putInt("slot", slot);
            tag.putLong("template_serial", templateSerial);
            tag.putLong("amount", amount);
            return tag;
        }

        public static SlotRecord fromTag(CompoundTag tag) {
            return new SlotRecord(
                    tag.getIntOr("storage_page", -1),
                    tag.getIntOr("slot", -1),
                    tag.getLongOr("template_serial", 0),
                    tag.getLongOr("amount", 0)
            );
        }
    }

    public record SnapshotChunk(
            long sessionId,
            long snapshotId,
            int chunkIndex,
            long checksum,
            byte[] data
    ) {
        public SnapshotChunk {
            data = data.clone();
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("session_id", sessionId);
            tag.putLong("snapshot_id", snapshotId);
            tag.putInt("chunk_index", chunkIndex);
            tag.putLong("checksum", checksum);
            tag.putByteArray("data", data);
            return tag;
        }

        public static SnapshotChunk fromTag(CompoundTag tag) {
            return new SnapshotChunk(
                    tag.getLongOr("session_id", 0),
                    tag.getLongOr("snapshot_id", 0),
                    tag.getIntOr("chunk_index", -1),
                    tag.getLongOr("checksum", 0),
                    tag.getByteArray("data").orElse(new byte[0])
            );
        }
    }

    public record SnapshotEnd(
            long sessionId,
            long snapshotId,
            long revision,
            int entryCount,
            int chunkCount
    ) {
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("session_id", sessionId);
            tag.putLong("snapshot_id", snapshotId);
            tag.putLong("revision", revision);
            tag.putInt("entry_count", entryCount);
            tag.putInt("chunk_count", chunkCount);
            return tag;
        }

        public static SnapshotEnd fromTag(CompoundTag tag) {
            return new SnapshotEnd(
                    tag.getLongOr("session_id", 0),
                    tag.getLongOr("snapshot_id", 0),
                    tag.getLongOr("revision", -1),
                    tag.getIntOr("entry_count", 0),
                    tag.getIntOr("chunk_count", 0)
            );
        }
    }

    public record SnapshotAck(
            long sessionId,
            long snapshotId,
            List<Integer> missingChunks
    ) {
        public SnapshotAck {
            missingChunks = List.copyOf(missingChunks);
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("session_id", sessionId);
            tag.putLong("snapshot_id", snapshotId);
            tag.put("missing_chunks", new IntArrayTag(
                    missingChunks.stream().mapToInt(Integer::intValue).toArray()
            ));
            return tag;
        }

        public static SnapshotAck fromTag(CompoundTag tag) {
            List<Integer> missing = new ArrayList<>();
            tag.getIntArray("missing_chunks").ifPresent(array ->
                    java.util.Arrays.stream(array).forEach(missing::add));
            return new SnapshotAck(
                    tag.getLongOr("session_id", 0),
                    tag.getLongOr("snapshot_id", 0),
                    missing
            );
        }
    }

    public record Delta(
            long sessionId,
            long baseRevision,
            long revision,
            List<EntryRecord> entryChanges,
            List<Long> removedTemplateSerials,
            List<PageInfo> pageChanges,
            List<Integer> pageAdded,
            boolean capacityChanged,
            long pageRevision,
            boolean readOnly
    ) {
        public Delta {
            entryChanges = List.copyOf(entryChanges);
            removedTemplateSerials = List.copyOf(removedTemplateSerials);
            pageChanges = List.copyOf(pageChanges);
            pageAdded = List.copyOf(pageAdded);
        }

        public CompoundTag toTag(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("session_id", sessionId);
            tag.putLong("base_revision", baseRevision);
            tag.putLong("revision", revision);
            ListTag entries = new ListTag();
            entryChanges.forEach(record -> entries.add(record.toTag(registries)));
            tag.put("entry_changes", entries);
            ListTag removed = new ListTag();
            removedTemplateSerials.forEach(serial -> {
                CompoundTag value = new CompoundTag();
                value.putLong("template_serial", serial);
                removed.add(value);
            });
            tag.put("removed_template_serials", removed);
            ListTag pageList = new ListTag();
            pageChanges.forEach(page -> pageList.add(page.toTag()));
            tag.put("page_changes", pageList);
            tag.put("page_added", new IntArrayTag(
                    pageAdded.stream().mapToInt(Integer::intValue).toArray()
            ));
            tag.putBoolean("capacity_changed", capacityChanged);
            tag.putLong("page_revision", pageRevision);
            tag.putBoolean("read_only", readOnly);
            return tag;
        }

        public static Delta fromTag(CompoundTag tag, HolderLookup.Provider registries) {
            List<EntryRecord> entries = new ArrayList<>();
            tag.getListOrEmpty("entry_changes").forEach(element ->
                    entries.add(EntryRecord.fromTag((CompoundTag) element, registries)));
            List<Long> removed = new ArrayList<>();
            tag.getListOrEmpty("removed_template_serials").forEach(element ->
                    removed.add(((CompoundTag) element)
                            .getLongOr("template_serial", 0)));
            List<PageInfo> pages = new ArrayList<>();
            tag.getListOrEmpty("page_changes").forEach(element ->
                    pages.add(PageInfo.fromTag((CompoundTag) element)));
            List<Integer> pageAdded = new ArrayList<>();
            tag.getIntArray("page_added").ifPresent(array ->
                    java.util.Arrays.stream(array).forEach(pageAdded::add));
            return new Delta(
                    tag.getLongOr("session_id", 0),
                    tag.getLongOr("base_revision", -1),
                    tag.getLongOr("revision", -1),
                    entries,
                    removed,
                    pages,
                    pageAdded,
                    tag.getBooleanOr("capacity_changed", false),
                    tag.getLongOr("page_revision", -1),
                    tag.getBooleanOr("read_only", false)
            );
        }
    }

    /**
     * {@code playerSlots} and {@code button} always use the player's Inventory indices 0..35:
     * 0..26 are main inventory and 27..35 are the hotbar. They are never menu slot indices.
     */
    public record ActionRequest(
            long sessionId,
            long requestId,
            int containerId,
            long knownRevision,
            int storagePage,
            int slot,
            long expectedTemplateSerial,
            int action,
            byte button,
            long amount,
            long actionSequence,
            List<Integer> playerSlots
    ) {
        public ActionRequest(
                long sessionId,
                long requestId,
                int containerId,
                long knownRevision,
                int storagePage,
                int slot,
                long expectedTemplateSerial,
                int action,
                byte button
        ) {
            this(
                    sessionId,
                    requestId,
                    containerId,
                    knownRevision,
                    storagePage,
                    slot,
                    expectedTemplateSerial,
                    action,
                    button,
                    0,
                    0,
                    List.of()
            );
        }

        public ActionRequest {
            playerSlots = playerSlots == null ? List.of() : List.copyOf(playerSlots);
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("session_id", sessionId);
            tag.putLong("request_id", requestId);
            tag.putInt("container_id", containerId);
            tag.putLong("known_revision", knownRevision);
            tag.putInt("storage_page", storagePage);
            tag.putInt("slot", slot);
            tag.putLong("expected_template_serial", expectedTemplateSerial);
            tag.putInt("action", action);
            tag.putByte("button", button);
            tag.putLong("amount", amount);
            tag.putLong("action_sequence", actionSequence);
            tag.put("player_slots", new IntArrayTag(
                    playerSlots.stream().mapToInt(Integer::intValue).toArray()
            ));
            return tag;
        }

        public static ActionRequest fromTag(CompoundTag tag) {
            List<Integer> slots = new ArrayList<>();
            tag.getIntArray("player_slots").ifPresent(array ->
                    java.util.Arrays.stream(array).forEach(slots::add));
            return new ActionRequest(
                    tag.getLongOr("session_id", 0),
                    tag.getLongOr("request_id", 0),
                    tag.getIntOr("container_id", -1),
                    tag.getLongOr("known_revision", -1),
                    tag.getIntOr("storage_page", -1),
                    tag.getIntOr("slot", -1),
                    tag.getLongOr("expected_template_serial", 0),
                    tag.getIntOr("action", 0),
                    tag.getByteOr("button", (byte) 0),
                    tag.getLongOr("amount", 0),
                    tag.getLongOr("action_sequence", 0),
                    slots
            );
        }
    }

    public record ActionResult(
            long sessionId,
            long requestId,
            int status,
            long revision
    ) {
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("session_id", sessionId);
            tag.putLong("request_id", requestId);
            tag.putInt("status", status);
            tag.putLong("revision", revision);
            return tag;
        }

        public static ActionResult fromTag(CompoundTag tag) {
            return new ActionResult(
                    tag.getLongOr("session_id", 0),
                    tag.getLongOr("request_id", 0),
                    tag.getIntOr("status", RESULT_INTERNAL_ERROR),
                    tag.getLongOr("revision", -1)
            );
        }
    }

    public record SessionClose(long sessionId, int reason) {
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("session_id", sessionId);
            tag.putInt("reason", reason);
            return tag;
        }

        public static SessionClose fromTag(CompoundTag tag) {
            return new SessionClose(
                    tag.getLongOr("session_id", 0),
                    tag.getIntOr("reason", 0)
            );
        }
    }

    public record RequestResync(long sessionId) {
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("session_id", sessionId);
            return tag;
        }

        public static RequestResync fromTag(CompoundTag tag) {
            return new RequestResync(tag.getLongOr("session_id", 0));
        }
    }

    public record OpenTerminalRequest(
            long requestId,
            long sessionId,
            int sourceAction
    ) {
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("request_id", requestId);
            tag.putLong("session_id", sessionId);
            tag.putInt("source_action", sourceAction);
            return tag;
        }

        public static OpenTerminalRequest fromTag(CompoundTag tag) {
            return new OpenTerminalRequest(
                    tag.getLongOr("request_id", 0),
                    tag.getLongOr("session_id", 0),
                    tag.getIntOr("source_action", 0)
            );
        }
    }
}
