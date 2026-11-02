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
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_CHUNK_BYTES = 256 * 1024;
    public static final int UNLIMITED_PAGE_LIMIT = -1;

    public static final int CAPABILITY_SNAPSHOT = 1;
    public static final int CAPABILITY_DELTA = 1 << 1;
    public static final int CAPABILITY_ACTIONS = 1 << 2;
    public static final int CAPABILITY_SEARCH_LOCAL = 1 << 3;
    public static final int CAPABILITIES = CAPABILITY_SNAPSHOT
            | CAPABILITY_DELTA
            | CAPABILITY_ACTIONS
            | CAPABILITY_SEARCH_LOCAL;

    public static final int HELLO = 1;
    public static final int HELLO_ACK = 2;
    public static final int SESSION_OPEN = 3;
    public static final int SNAPSHOT_BEGIN = 4;
    public static final int SNAPSHOT_CHUNK = 5;
    public static final int DELTA = 6;
    public static final int ACTION_REQUEST = 7;
    public static final int ACTION_RESULT = 8;

    public static final int ACTION_TAKE_ONE = 1;
    public static final int ACTION_TAKE_STACK = 2;
    public static final int ACTION_PUT_ONE = 3;
    public static final int ACTION_PUT_STACK = 4;
    public static final int ACTION_QUICK_MOVE_TO_PLAYER = 5;
    public static final int ACTION_QUICK_MOVE_FROM_PLAYER = 6;

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

    public record SessionOpen(
            long sessionId,
            int containerId,
            long revision,
            int pageLimit,
            int flags
    ) {
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("session_id", sessionId);
            tag.putInt("container_id", containerId);
            tag.putLong("revision", revision);
            tag.putInt("page_limit", pageLimit);
            tag.putInt("flags", flags);
            return tag;
        }

        public static SessionOpen fromTag(CompoundTag tag) {
            return new SessionOpen(
                    tag.getLongOr("session_id", 0),
                    tag.getIntOr("container_id", -1),
                    tag.getLongOr("revision", -1),
                    tag.getIntOr("page_limit", 0),
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
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("session_id", sessionId);
            tag.putLong("snapshot_id", snapshotId);
            tag.putLong("revision", revision);
            tag.putInt("template_count", templateCount);
            tag.putInt("slot_count", slotCount);
            tag.putInt("chunk_count", chunkCount);
            return tag;
        }

        public static SnapshotBegin fromTag(CompoundTag tag) {
            return new SnapshotBegin(
                    tag.getLongOr("session_id", 0),
                    tag.getLongOr("snapshot_id", 0),
                    tag.getLongOr("revision", -1),
                    tag.getIntOr("template_count", 0),
                    tag.getIntOr("slot_count", 0),
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

    public record Delta(
            long sessionId,
            long baseRevision,
            long revision,
            List<TemplateRecord> templateAdds,
            List<SlotRecord> slotChanges,
            List<Integer> pageAdded,
            boolean capacityChanged
    ) {
        public Delta {
            templateAdds = List.copyOf(templateAdds);
            slotChanges = List.copyOf(slotChanges);
            pageAdded = List.copyOf(pageAdded);
        }

        public CompoundTag toTag(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("session_id", sessionId);
            tag.putLong("base_revision", baseRevision);
            tag.putLong("revision", revision);
            ListTag templates = new ListTag();
            templateAdds.forEach(record -> templates.add(record.toTag(registries)));
            tag.put("template_adds", templates);
            ListTag slots = new ListTag();
            slotChanges.forEach(record -> slots.add(record.toTag()));
            tag.put("slot_changes", slots);
            tag.put("page_added", new IntArrayTag(pageAdded.stream().mapToInt(Integer::intValue).toArray()));
            tag.putBoolean("capacity_changed", capacityChanged);
            return tag;
        }

        public static Delta fromTag(CompoundTag tag, HolderLookup.Provider registries) {
            List<TemplateRecord> templates = new ArrayList<>();
            tag.getListOrEmpty("template_adds").forEach(element ->
                    templates.add(TemplateRecord.fromTag((CompoundTag) element, registries)));
            List<SlotRecord> slots = new ArrayList<>();
            tag.getListOrEmpty("slot_changes").forEach(element ->
                    slots.add(SlotRecord.fromTag((CompoundTag) element)));
            List<Integer> pages = new ArrayList<>();
            tag.getIntArray("page_added").ifPresent(array ->
                    java.util.Arrays.stream(array).forEach(pages::add));
            return new Delta(
                    tag.getLongOr("session_id", 0),
                    tag.getLongOr("base_revision", -1),
                    tag.getLongOr("revision", -1),
                    templates,
                    slots,
                    pages,
                    tag.getBooleanOr("capacity_changed", false)
            );
        }
    }

    public record ActionRequest(
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
            return tag;
        }

        public static ActionRequest fromTag(CompoundTag tag) {
            return new ActionRequest(
                    tag.getLongOr("session_id", 0),
                    tag.getLongOr("request_id", 0),
                    tag.getIntOr("container_id", -1),
                    tag.getLongOr("known_revision", -1),
                    tag.getIntOr("storage_page", -1),
                    tag.getIntOr("slot", -1),
                    tag.getLongOr("expected_template_serial", 0),
                    tag.getIntOr("action", 0),
                    tag.getByteOr("button", (byte) 0)
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
}
