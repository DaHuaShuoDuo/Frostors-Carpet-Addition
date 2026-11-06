package top.frostor.carpet.bigenderchest;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol.Hello;
import top.frostor.carpet.bigenderchest.capacity.BigEnderChestCapacity;
import top.frostor.carpet.bigenderchest.index.BigEnderChestIndex;
import top.frostor.carpet.bigenderchest.index.ItemTemplateKey;
import top.frostor.carpet.bigenderchest.index.StorageEntry;
import top.frostor.carpet.bigenderchest.index.StorageLocation;
import top.frostor.carpet.bigenderchest.page.SortPreference;

import java.util.List;
import java.util.Map;

public final class BigEnderChestCoreTest {
    private static int checks;

    private BigEnderChestCoreTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bindTestComponents(Items.DIRT, "dirt");
        bindTestComponents(Items.STONE, "stone");

        testTemplateIdentity();
        testAggregateIndex();
        testCapacityMath();
        testViewPagingMath();
        testSortPreference();
        testProtocolDtos();
        System.out.println("BigEnderChestCoreTest passed " + checks + " checks");
    }

    private static void testTemplateIdentity() {
        ItemStack first = new ItemStack(Items.DIRT, 1);
        ItemStack same = new ItemStack(Items.DIRT, 64);
        ItemTemplateKey firstKey = ItemTemplateKey.of(first);
        ItemTemplateKey sameKey = ItemTemplateKey.of(same);
        check(firstKey.equals(sameKey), "stack size must not affect template identity");
        check(firstKey.matches(same), "the template must match different stack sizes");

        ItemStack named = new ItemStack(Items.DIRT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Named dirt"));
        ItemTemplateKey namedKey = ItemTemplateKey.of(named);
        check(!firstKey.equals(namedKey), "data components must affect template identity");
        check(!firstKey.matches(named), "component differences must not merge stacks");
        check(ItemTemplateKey.of(ItemStack.EMPTY) == null, "empty stacks have no template");
    }

    private static void testAggregateIndex() {
        SimpleContainer page0 = new SimpleContainer(StorageLocation.SLOTS_PER_PAGE);
        SimpleContainer page1 = new SimpleContainer(StorageLocation.SLOTS_PER_PAGE);
        page0.setItem(0, new ItemStack(Items.DIRT, 32));
        page0.setItem(1, new ItemStack(Items.STONE, 64));
        page1.setItem(3, new ItemStack(Items.DIRT, 16));

        BigEnderChestIndex index = new BigEnderChestIndex();
        index.rebuild(Map.of(0, page0, 1, page1));
        ItemTemplateKey dirt = ItemTemplateKey.of(new ItemStack(Items.DIRT));
        StorageEntry dirtEntry = index.entry(dirt);
        check(dirtEntry != null, "the aggregate index must contain dirt");
        check(dirtEntry.totalAmount() == 48, "aggregate amount must include stack sizes");
        check(dirtEntry.locations().equals(List.of(
                new StorageLocation(0, 0),
                new StorageLocation(1, 3)
        )), "locations must be sorted and complete");
        check(index.templateCount() == 2, "only unique templates may be indexed");
        check(index.occupiedSlotCount() == 3, "every occupied slot must be indexed");

        page1.setItem(3, ItemStack.EMPTY);
        index.update(new StorageLocation(1, 3), ItemStack.EMPTY);
        check(index.entry(dirt).totalAmount() == 32, "exact updates must decrease amounts");
        check(index.keyAt(new StorageLocation(1, 3)) == null, "removed locations must disappear");

        index.rebuild(Map.of(0, page0, 1, page1));
        check(index.entry(dirt).totalAmount() == 32, "rebuild must recover the source of truth");

        page1.setItem(5, new ItemStack(Items.DIRT, 8));
        index.replacePage(1, page1);
        check(
                index.entry(dirt).totalAmount() == 40,
                "replacing one page must update that page without rebuilding others"
        );
        check(
                index.entry(ItemTemplateKey.of(new ItemStack(Items.STONE))).totalAmount() == 64,
                "incremental page replacement must preserve other pages"
        );
        check(
                index.aggregates().stream()
                        .filter(entry -> entry.template().equals(dirt))
                        .mapToLong(BigEnderChestIndex.AggregateEntry::totalAmount)
                        .sum() == 40,
                "lightweight aggregate snapshots must retain total amounts"
        );
    }

    private static void testCapacityMath() {
        check(BigEnderChestCapacity.pagesForSlots(0) == 0, "zero slots need no pages");
        check(BigEnderChestCapacity.pagesForSlots(1) == 1, "one slot needs one page");
        check(
                BigEnderChestCapacity.pagesForSlots(28) == 2,
                "page reservations must be whole 27-slot pages"
        );
        check(
                BigEnderChestCapacity.canCreateExtraPage(63, 64),
                "the configured page limit is inclusive"
        );
        check(
                !BigEnderChestCapacity.canCreateExtraPage(64, 64),
                "the next page must be rejected at the limit"
        );
        check(
                !BigEnderChestCapacity.canCreateExtraPage(0, 0),
                "out-of-range limits must reject page creation"
        );
        check(
                BigEnderChestCapacity.hasValidPageLimit(
                        BigEnderChestCapacity.UNLIMITED_PAGE_LIMIT
                ),
                "the unlimited sentinel must be a valid page limit"
        );
        check(
                BigEnderChestCapacity.canCreateExtraPage(1_000_000, -1),
                "unlimited mode must not enforce the finite page cap"
        );
        check(
                !BigEnderChestCapacity.canCreateExtraPage(100, 32),
                "a lowered limit must stop further expansion for existing owners"
        );
    }

    private static void testViewPagingMath() {
        check(Math.ceilDiv(0, 45) == 0, "an empty index has no data pages");
        check(Math.max(1, Math.ceilDiv(0, 45)) == 1, "the vanilla view always has page one");
        check(Math.ceilDiv(46, 45) == 2, "46 entries must span two view pages");
        check(45 == 5 * 9, "the aggregate view must contain five rows of nine");
        check(54 == 45 + 9, "the vanilla menu must reserve one full control row");
    }

    private static void testSortPreference() {
        SortPreference preference = new SortPreference("AMOUNT", "DESCENDING");
        check(preference.field().equals("amount"), "sort fields must be normalized");
        check(preference.direction().equals("descending"), "sort directions must be normalized");
        check(
                new SortPreference("invalid", "invalid").equals(SortPreference.DEFAULT),
                "invalid sort preferences must fall back to the default"
        );
    }

    private static void testProtocolDtos() {
        check(BigEnderChestProtocol.PROTOCOL_VERSION == 3, "protocol version must be 3");
        check(
                BigEnderChestProtocol.CAPABILITY_GENERIC_TERMINAL_CARRIER == 1 << 14,
                "generic terminal carrier capability must use the next free bit"
        );
        check(
                (BigEnderChestProtocol.CAPABILITIES
                        & BigEnderChestProtocol.CAPABILITY_GENERIC_TERMINAL_CARRIER) != 0,
                "generic terminal carrier must be advertised"
        );
        check(
                BigEnderChestProtocol.TERMINAL_CARRIER_CONTAINER_SLOTS == 54,
                "generic terminal carrier must reserve a full 9x6 container"
        );
        check(
                BigEnderChestProtocol.TERMINAL_PLAYER_SLOT_START == 54
                        && BigEnderChestProtocol.TERMINAL_CARRIER_MAIN_SLOT_START == 54
                        && BigEnderChestProtocol.TERMINAL_CARRIER_HOTBAR_SLOT_START == 81,
                "generic terminal carrier player slots must follow the container slots"
        );
        check(
                (BigEnderChestProtocol.CAPABILITIES
                        & BigEnderChestProtocol.CAPABILITY_RULE_STATE_ACK) != 0,
                "protocol must advertise event-driven rule-state acknowledgements"
        );
        check(
                (BigEnderChestProtocol.CAPABILITIES
                        & BigEnderChestProtocol.CAPABILITY_TERMINAL_MENU_ACTIVE) != 0,
                "protocol must advertise dynamic terminal-menu activity"
        );
        Hello hello = new Hello(BigEnderChestProtocol.PROTOCOL_VERSION, 0b1111, 64 * 1024);
        check(Hello.fromTag(hello.toTag()).equals(hello), "hello DTOs must round trip");
        BigEnderChestProtocol.SessionOpen sessionOpen = new BigEnderChestProtocol.SessionOpen(
                42,
                7,
                8,
                64,
                3,
                5,
                false,
                BigEnderChestProtocol.CAPABILITIES,
                -1,
                BigEnderChestProtocol.SESSION_FLAG_GENERIC_TERMINAL_CARRIER
        );
        check(
                (sessionOpen.flags()
                        & BigEnderChestProtocol.SESSION_FLAG_GENERIC_TERMINAL_CARRIER) != 0,
                "session-open flags must retain the generic terminal carrier marker"
        );
        check(
                BigEnderChestProtocol.SessionOpen.fromTag(sessionOpen.toTag())
                        .equals(sessionOpen),
                "session-open DTOs must round trip terminal metadata"
        );

        BigEnderChestProtocol.PageCatalog catalog = new BigEnderChestProtocol.PageCatalog(
                42,
                5,
                List.of(
                        new BigEnderChestProtocol.PageInfo(0, "READY", false, 3, 4),
                        new BigEnderChestProtocol.PageInfo(1, "READY", true, 0, 2)
                )
        );
        check(
                BigEnderChestProtocol.PageCatalog.fromTag(catalog.toTag()).equals(catalog),
                "page catalogs must round trip"
        );

        BigEnderChestProtocol.SnapshotAck ack = new BigEnderChestProtocol.SnapshotAck(
                42,
                9,
                List.of(1, 3)
        );
        check(
                BigEnderChestProtocol.SnapshotAck.fromTag(ack.toTag()).equals(ack),
                "snapshot ACKs must retain missing chunks"
        );

        BigEnderChestProtocol.SnapshotBegin snapshotBegin =
                new BigEnderChestProtocol.SnapshotBegin(42, 9, 8, 2, 0, 2);
        check(
                BigEnderChestProtocol.SnapshotBegin.fromTag(snapshotBegin.toTag())
                        .equals(snapshotBegin),
                "snapshot begin metadata must round trip"
        );
        BigEnderChestProtocol.SnapshotEnd snapshotEnd =
                new BigEnderChestProtocol.SnapshotEnd(42, 9, 8, 2, 2);
        check(
                BigEnderChestProtocol.SnapshotEnd.fromTag(snapshotEnd.toTag())
                        .equals(snapshotEnd),
                "snapshot end metadata must round trip"
        );

        BigEnderChestProtocol.EntryRecord entryRecord =
                new BigEnderChestProtocol.EntryRecord(
                        3,
                        new ItemStack(Items.DIRT),
                        64,
                        List.of(new BigEnderChestProtocol.SlotRecord(1, 2, 3, 64))
                );
        BigEnderChestProtocol.EntryRecord decodedEntry =
                BigEnderChestProtocol.EntryRecord.fromTag(
                        entryRecord.toTag(MinecraftServerContext.registries()),
                        MinecraftServerContext.registries()
                );
        check(
                decodedEntry.templateSerial() == entryRecord.templateSerial()
                        && decodedEntry.totalAmount() == entryRecord.totalAmount()
                        && decodedEntry.locations().equals(entryRecord.locations()),
                "aggregate entry records must retain serial, total, and locations"
        );
        check(
                ItemTemplateKey.of(decodedEntry.item()).equals(
                        ItemTemplateKey.of(entryRecord.item())
                ),
                "aggregate entry records must round trip item templates"
        );

        BigEnderChestProtocol.ActionRequest request =
                new BigEnderChestProtocol.ActionRequest(
                        42,
                        10,
                        7,
                        8,
                        -1,
                        -1,
                        3,
                        BigEnderChestProtocol.ACTION_BATCH_STORAGE_TO_PLAYER,
                        (byte) 0,
                        128,
                        11,
                        List.of(9, 10, 11)
                );
        check(
                BigEnderChestProtocol.ActionRequest.fromTag(request.toTag())
                        .equals(request),
                "template actions must round trip amount, sequence, and slot lists"
        );
        BigEnderChestProtocol.Delta delta = new BigEnderChestProtocol.Delta(
                42,
                7,
                8,
                List.of(new BigEnderChestProtocol.EntryRecord(
                        3,
                        new ItemStack(Items.DIRT),
                        64,
                        List.of(new BigEnderChestProtocol.SlotRecord(1, 2, 3, 64))
                )),
                List.of(),
                List.of(new BigEnderChestProtocol.PageInfo(2, "READY", false, 1, 1)),
                List.of(2),
                true,
                2,
                false
        );
        check(
                delta.entryChanges().getFirst().totalAmount() == 64,
                "aggregate deltas must retain 64-bit quantities"
        );
        check(delta.capacityChanged(), "page additions must be visible in delta DTOs");
        BigEnderChestProtocol.Delta decodedDelta = BigEnderChestProtocol.Delta.fromTag(
                delta.toTag(MinecraftServerContext.registries()),
                MinecraftServerContext.registries()
        );
        check(
                decodedDelta.sessionId() == delta.sessionId()
                        && decodedDelta.baseRevision() == delta.baseRevision()
                        && decodedDelta.revision() == delta.revision()
                        && decodedDelta.removedTemplateSerials()
                        .equals(delta.removedTemplateSerials())
                        && decodedDelta.pageChanges().equals(delta.pageChanges())
                        && decodedDelta.pageAdded().equals(delta.pageAdded())
                        && decodedDelta.capacityChanged() == delta.capacityChanged()
                        && decodedDelta.pageRevision() == delta.pageRevision()
                        && decodedDelta.readOnly() == delta.readOnly()
                        && decodedDelta.entryChanges().size() == delta.entryChanges().size(),
                "aggregate deltas must round trip metadata"
        );
        check(
                ItemTemplateKey.of(decodedDelta.entryChanges().getFirst().item()).equals(
                        ItemTemplateKey.of(delta.entryChanges().getFirst().item())
                )
                        && decodedDelta.entryChanges().getFirst().totalAmount()
                        == delta.entryChanges().getFirst().totalAmount(),
                "aggregate deltas must round trip item templates and totals"
        );

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ByteBufCodecs.VAR_INT.encode(buffer, Integer.MAX_VALUE);
        ByteBufCodecs.VAR_LONG.encode(buffer, Long.MAX_VALUE);
        check(
                ByteBufCodecs.VAR_INT.decode(buffer) == Integer.MAX_VALUE,
                "VarInt must round trip"
        );
        check(
                ByteBufCodecs.VAR_LONG.decode(buffer) == Long.MAX_VALUE,
                "VarLong must round trip"
        );
        buffer.release();
    }

    private static void check(boolean condition, String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }

    @SuppressWarnings("deprecation")
    private static void bindTestComponents(Item item, String name) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64)
                .set(DataComponents.ITEM_NAME, Component.literal(name))
                .build());
    }

    private static final class MinecraftServerContext {
        private static net.minecraft.core.HolderLookup.Provider registries() {
            return net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                    net.minecraft.core.registries.BuiltInRegistries.REGISTRY
            );
        }
    }
}
