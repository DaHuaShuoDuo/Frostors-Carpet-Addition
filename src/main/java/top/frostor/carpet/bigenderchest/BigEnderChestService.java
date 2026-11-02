package top.frostor.carpet.bigenderchest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import top.frostor.carpet.FrostorSettings;
import top.frostor.carpet.FrostorsCarpetAddition;
import top.frostor.carpet.bigenderchest.capacity.BigEnderChestCapacity;
import top.frostor.carpet.bigenderchest.index.BigEnderChestIndex;
import top.frostor.carpet.bigenderchest.index.ItemTemplateKey;
import top.frostor.carpet.bigenderchest.index.StorageEntry;
import top.frostor.carpet.bigenderchest.index.StorageLocation;
import top.frostor.carpet.bigenderchest.page.FakePageDataStore;
import top.frostor.carpet.bigenderchest.page.PageMetadata;
import top.frostor.carpet.bigenderchest.page.PageRegistry;
import top.frostor.carpet.bigenderchest.page.SortPreference;
import top.frostor.carpet.bigenderchest.page.StoragePage;
import top.frostor.carpet.bigenderchest.page.StoragePageState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Main-thread storage facade shared by the vanilla menu and the terminal protocol.
 */
public final class BigEnderChestService {
    private final ServerPlayer owner;
    private final UUID storageOwner;
    private final PageRegistry pageRegistry;
    private final FakePageDataStore dataStore;
    private final NavigableMap<Integer, StoragePage> pages = new TreeMap<>();
    private final BigEnderChestIndex index = new BigEnderChestIndex();
    private final Set<StorageLocation> pendingChanges = new LinkedHashSet<>();
    private final List<Integer> pendingPageAdds = new ArrayList<>();

    private final ItemStack[] lastPlayerPage = new ItemStack[StorageLocation.SLOTS_PER_PAGE];
    private long revision;
    private long pendingBaseRevision = -1;
    private boolean closed;

    public BigEnderChestService(
            ServerPlayer owner,
            PageRegistry pageRegistry,
            FakePageDataStore dataStore
    ) {
        this(owner, owner.getUUID(), pageRegistry, dataStore);
    }

    public BigEnderChestService(
            ServerPlayer owner,
            UUID storageOwner,
            PageRegistry pageRegistry,
            FakePageDataStore dataStore
    ) {
        this.owner = owner;
        this.storageOwner = storageOwner;
        this.pageRegistry = pageRegistry;
        this.dataStore = dataStore;

        PageMetadata playerMetadata = new PageMetadata(
                owner.getUUID(),
                0,
                owner.getUUID(),
                owner.getGameProfile().name()
        );
        pages.put(
                0,
                StoragePage.playerPage(playerMetadata, owner::getEnderChestInventory)
        );
        loadExtraPages();
        rebuildIndex();
        rememberPlayerPage();
        prewarmInitialPages();
    }

    public ServerPlayer owner() {
        return owner;
    }

    public long revision() {
        return revision;
    }

    public SortPreference sortPreference() {
        return pageRegistry.sortPreference(owner.getUUID());
    }

    public void saveSortPreference(String field, String direction) {
        pageRegistry.setSortPreference(owner.getUUID(), field, direction);
    }

    public boolean isOpen() {
        return !closed
                && FrostorSettings.bigEnderChest
                && owner.isAlive()
                && !owner.hasDisconnected();
    }

    public void close() {
        if(closed) return;
        saveDirtyPages();
        closed = true;
    }

    public List<StorageEntry> entries() {
        return index.entries();
    }

    public int templateCount() {
        return index.templateCount();
    }

    public long amount(ItemTemplateKey key) {
        StorageEntry entry = index.entry(key);
        return entry == null ? 0 : entry.totalAmount();
    }

    public ItemTemplateKey keyAt(StorageLocation location) {
        return index.keyAt(location);
    }

    public ItemStack stackAt(StorageLocation location) {
        StoragePage page = pages.get(location.storagePage());
        return page != null && page.isReady() ? page.container().getItem(location.slot()) : ItemStack.EMPTY;
    }

    public int readyPageCount() {
        return (int) pages.values().stream().filter(StoragePage::isReady).count();
    }

    public boolean isPageReady(int pageIndex) {
        StoragePage page = pages.get(pageIndex);
        return page != null && page.isReady();
    }

    public List<Integer> readyPageIndices() {
        return pages.values().stream()
                .filter(StoragePage::isReady)
                .map(StoragePage::index)
                .toList();
    }

    public Map<Integer, Container> readyContainers() {
        Map<Integer, Container> result = new TreeMap<>();
        pages.values().stream()
                .filter(StoragePage::isReady)
                .forEach(page -> result.put(page.index(), page.container()));
        return result;
    }

    public StorageEntry entryForSlot(StorageLocation location) {
        ItemTemplateKey key = index.keyAt(location);
        return key == null ? null : index.entry(key);
    }

    public boolean insert(ItemStack stack) {
        if(stack.isEmpty()) return true;
        return insertAll(List.of(stack));
    }

    /**
     * Atomically inserts into all supplied stacks. Capacity is checked before any source is changed.
     */
    public boolean insertAll(List<ItemStack> stacks) {
        if(!isOpen()) return false;
        List<ItemStack> nonEmpty = stacks.stream()
                .filter(stack -> !stack.isEmpty())
                .toList();
        if(nonEmpty.isEmpty()) return true;
        if(nonEmpty.size() > BigEnderChestCapacity.MAX_BATCH_SLOTS) return false;
        if(!reserveCapacity(nonEmpty)) {
            commitPendingPageAdds();
            return false;
        }

        long baseRevision = ensureBatchStarted();
        InsertionPlan plan = buildInsertionPlan(nonEmpty);
        for(ItemStack source : nonEmpty) {
            insertWithPlan(source, plan);
        }
        finishMutation(baseRevision);
        return true;
    }

    public ItemStack take(ItemTemplateKey key, int requestedAmount) {
        if(!isOpen() || key == null || requestedAmount <= 0) return ItemStack.EMPTY;
        List<LocatedStack> candidates = findMatchingStacks(key);
        if(candidates.isEmpty()) return ItemStack.EMPTY;

        candidates.sort(
                Comparator.comparingInt(LocatedStack::count)
                        .thenComparing(LocatedStack::location)
        );
        long baseRevision = ensureBatchStarted();
        int remaining = requestedAmount;
        ItemStack result = ItemStack.EMPTY;
        int resultCount = 0;
        for(LocatedStack candidate : candidates) {
            if(remaining <= 0) break;
            Container container = pages.get(candidate.location().storagePage()).container();
            ItemStack removed = container.removeItem(
                    candidate.location().slot(),
                    Math.min(remaining, candidate.stack().getCount())
            );
            if(result.isEmpty()) result = removed.copyWithCount(1);
            resultCount += removed.getCount();
            remaining -= removed.getCount();
            pageChanged(candidate.location().storagePage());
            index.update(candidate.location(), container.getItem(candidate.location().slot()));
            if(candidate.location().storagePage() == 0) {
                lastPlayerPage[candidate.location().slot()] =
                        container.getItem(candidate.location().slot()).copy();
            }
            pendingChanges.add(candidate.location());
        }
        if(resultCount == 0) return ItemStack.EMPTY;
        result.setCount(resultCount);
        finishMutation(baseRevision);
        return result;
    }

    public ItemStack takeOne(ItemTemplateKey key) {
        return take(key, 1);
    }

    public ItemStack takeStack(ItemTemplateKey key) {
        if(key == null) return ItemStack.EMPTY;
        int max = key.template().getMaxStackSize();
        return take(key, Math.max(1, max));
    }

    public void tick() {
        if(!isOpen()) return;
        if(refreshPlayerPage()) {
            long baseRevision = ensureBatchStarted();
            finishMutation(baseRevision);
        }
        if(pages.values().stream().anyMatch(page -> page.state() == StoragePageState.ERROR)) {
            return;
        }
        long totalSlots = (long) readyPageCount() * StorageLocation.SLOTS_PER_PAGE;
        int readyEmptyPages = countReadyEmptyExtraPages();
        int growth = BigEnderChestCapacity.growthPages(
                readyEmptyPages,
                index.occupiedSlotCount(),
                Math.toIntExact(Math.min(Integer.MAX_VALUE, totalSlots))
        );
        if(growth > 0) {
            int pagesBefore = pendingPageAdds.size();
            growPages(growth);
            if(pendingPageAdds.size() > pagesBefore) commitPendingPageAdds();
        }
    }

    public Delta takePendingDelta() {
        if(pendingBaseRevision < 0) return null;
        List<SlotChange> changes = new ArrayList<>(pendingChanges.size());
        for(StorageLocation location : pendingChanges) {
            StoragePage page = pages.get(location.storagePage());
            ItemStack stack = page != null && page.isReady()
                    ? page.container().getItem(location.slot())
                    : ItemStack.EMPTY;
            changes.add(new SlotChange(location, stack.copy()));
        }
        Delta delta = new Delta(
                pendingBaseRevision,
                revision,
                List.copyOf(changes),
                List.copyOf(pendingPageAdds)
        );
        pendingChanges.clear();
        pendingPageAdds.clear();
        pendingBaseRevision = -1;
        return delta;
    }

    public void saveDirtyPages() {
        pages.values().stream()
                .filter(page -> page.index() > 0)
                .filter(page -> page.state() == StoragePageState.DIRTY)
                .forEach(page -> {
                    long version = page.beginSaving();
                    dataStore.saveAsync(
                            page.metadata(),
                            (net.minecraft.world.inventory.PlayerEnderChestContainer) page.container(),
                            success -> {
                                if(success) {
                                    page.finishSaving(version);
                                }else {
                                    page.failSaving();
                                    FrostorsCarpetAddition.LOGGER.error(
                                            "Failed to save big ender chest page {} for {}",
                                            page.index(),
                                            owner.getScoreboardName()
                                    );
                                }
                            }
                    );
                });
    }

    private void loadExtraPages() {
        for(PageMetadata metadata : pageRegistry.pages(storageOwner)) {
            try {
                net.minecraft.world.inventory.PlayerEnderChestContainer container = dataStore.load(metadata);
                pages.put(
                        metadata.storagePage(),
                        StoragePage.fakePage(metadata, () -> container, StoragePageState.READY)
                );
            }catch(IOException exception) {
                pages.put(
                        metadata.storagePage(),
                        StoragePage.fakePage(
                                metadata,
                                net.minecraft.world.inventory.PlayerEnderChestContainer::new,
                                StoragePageState.ERROR
                        )
                );
                FrostorsCarpetAddition.LOGGER.error(
                        "Failed to load big ender chest page {} for {}",
                        metadata.storagePage(),
                        owner.getScoreboardName(),
                        exception
                );
            }
        }
    }

    private void prewarmInitialPages() {
        int missing = BigEnderChestCapacity.INITIAL_WARM_PAGES
                - pageRegistry.extraPageCount(storageOwner);
        if(missing > 0) growPages(missing);
    }

    private boolean growPages(int requestedPages) {
        for(int count = 0; count < requestedPages; count++) {
            int nextPage = nextPageIndex();
            if(!BigEnderChestCapacity.canCreateExtraPage(
                    pageRegistry.extraPageCount(storageOwner),
                    FrostorSettings.bigEnderChestPageLimit
            )) {
                return false;
            }
            PageMetadata metadata;
            try {
                metadata = pageRegistry.allocate(
                        storageOwner,
                        owner.getGameProfile().name(),
                        nextPage
                );
            }catch(RuntimeException exception) {
                FrostorsCarpetAddition.LOGGER.error(
                        "Failed to persist metadata for big ender chest page {} of {}",
                        nextPage,
                        owner.getScoreboardName(),
                        exception
                );
                return false;
            }
            net.minecraft.world.inventory.PlayerEnderChestContainer container =
                    new net.minecraft.world.inventory.PlayerEnderChestContainer();
            dataStore.createEmptyAsync(metadata);
            pages.put(
                    nextPage,
                    StoragePage.fakePage(metadata, () -> container, StoragePageState.READY)
            );
            pendingPageAdds.add(nextPage);
        }
        return true;
    }

    private int nextPageIndex() {
        int candidate = 1;
        while(pages.containsKey(candidate)) candidate++;
        return candidate;
    }

    private boolean reserveCapacity(List<ItemStack> stacks) {
        Map<ItemTemplateKey, Long> requested = new HashMap<>();
        for(ItemStack stack : stacks) {
            requested.merge(ItemTemplateKey.of(stack), (long) stack.getCount(), Long::sum);
        }

        Map<ItemTemplateKey, Long> freeByTemplate = new HashMap<>();
        long emptySlots = 0;
        for(StoragePage page : pages.values()) {
            if(!page.isReady()) continue;
            Container container = page.container();
            for(int slot = 0; slot < StorageLocation.SLOTS_PER_PAGE; slot++) {
                ItemStack current = container.getItem(slot);
                if(current.isEmpty()) {
                    emptySlots++;
                    continue;
                }
                int max = current.getMaxStackSize();
                if(current.getCount() < max) {
                    ItemTemplateKey key = index.keyAt(
                            new StorageLocation(page.index(), slot)
                    );
                    if(key != null && requested.containsKey(key)) {
                        freeByTemplate.merge(
                                key,
                                (long) max - current.getCount(),
                                Long::sum
                        );
                    }
                }
            }
        }

        long slotsNeeded = 0;
        for(Map.Entry<ItemTemplateKey, Long> request : requested.entrySet()) {
            long remaining = request.getValue();
            remaining -= Math.min(remaining, freeByTemplate.getOrDefault(request.getKey(), 0L));
            if(remaining > 0) {
                int max = nodeMaxStack(request.getKey());
                slotsNeeded += Math.ceilDiv(remaining, max);
            }
        }

        if(slotsNeeded <= emptySlots) return true;
        int additionalPages = BigEnderChestCapacity.pagesForSlots(
                Math.toIntExact(slotsNeeded - emptySlots)
        );
        return growPages(additionalPages);
    }

    private static int nodeMaxStack(ItemTemplateKey key) {
        return Math.max(1, key.template().getMaxStackSize());
    }

    private InsertionPlan buildInsertionPlan(List<ItemStack> stacks) {
        Set<ItemTemplateKey> requested = stacks.stream()
                .map(ItemTemplateKey::of)
                .collect(java.util.stream.Collectors.toSet());
        Map<ItemTemplateKey, Deque<StorageLocation>> mergeSlots = new HashMap<>();
        Deque<StorageLocation> emptySlots = new ArrayDeque<>();
        for(StoragePage page : pages.values()) {
            if(!page.isReady()) continue;
            Container container = page.container();
            for(int slot = 0; slot < StorageLocation.SLOTS_PER_PAGE; slot++) {
                StorageLocation location = new StorageLocation(page.index(), slot);
                ItemStack current = container.getItem(slot);
                if(current.isEmpty()) {
                    emptySlots.addLast(location);
                    continue;
                }
                ItemTemplateKey key = index.keyAt(location);
                if(key != null && requested.contains(key)
                        && current.getCount() < current.getMaxStackSize()) {
                    mergeSlots.computeIfAbsent(key, ignored -> new ArrayDeque<>())
                            .addLast(location);
                }
            }
        }
        return new InsertionPlan(mergeSlots, emptySlots);
    }

    private void insertWithPlan(ItemStack source, InsertionPlan plan) {
        ItemTemplateKey key = ItemTemplateKey.of(source);
        if(key == null) return;

        Deque<StorageLocation> mergeCandidates = plan.mergeSlots.get(key);
        while(!source.isEmpty() && mergeCandidates != null && !mergeCandidates.isEmpty()) {
            StorageLocation location = mergeCandidates.removeFirst();
            Container container = pages.get(location.storagePage()).container();
            ItemStack current = container.getItem(location.slot());
            if(!key.matches(current)) continue;
            int room = current.getMaxStackSize() - current.getCount();
            if(room <= 0) continue;
            int moved = Math.min(room, source.getCount());
            current.grow(moved);
            source.shrink(moved);
            container.setItem(location.slot(), current);
            pageChanged(location.storagePage());
            index.update(location, current);
            updatePlayerPageSnapshot(location, current);
            pendingChanges.add(location);
            if(current.getCount() < current.getMaxStackSize()) {
                mergeCandidates.addFirst(location);
            }
        }

        while(!source.isEmpty() && !plan.emptySlots.isEmpty()) {
            StorageLocation location = plan.emptySlots.removeFirst();
            Container container = pages.get(location.storagePage()).container();
            ItemStack placed = source.copyWithCount(
                    Math.min(source.getMaxStackSize(), source.getCount())
            );
            source.shrink(placed.getCount());
            container.setItem(location.slot(), placed);
            pageChanged(location.storagePage());
            index.update(location, placed);
            updatePlayerPageSnapshot(location, placed);
            pendingChanges.add(location);
            if(placed.getCount() < placed.getMaxStackSize()) {
                plan.mergeSlots.computeIfAbsent(key, ignored -> new ArrayDeque<>())
                        .addFirst(location);
            }
        }
    }

    private void updatePlayerPageSnapshot(StorageLocation location, ItemStack stack) {
        if(location.storagePage() == 0) {
            lastPlayerPage[location.slot()] = stack.copy();
        }
    }

    private List<LocatedStack> findMatchingStacks(ItemTemplateKey key) {
        List<LocatedStack> result = new ArrayList<>();
        for(StoragePage page : pages.values()) {
            if(!page.isReady()) continue;
            Container container = page.container();
            for(int slot = 0; slot < StorageLocation.SLOTS_PER_PAGE; slot++) {
                ItemStack stack = container.getItem(slot);
                if(key.matches(stack)) {
                    result.add(new LocatedStack(
                            new StorageLocation(page.index(), slot),
                            stack.copy()
                    ));
                }
            }
        }
        return result;
    }

    private boolean refreshPlayerPage() {
        boolean changed = false;
        Container playerContainer = pages.get(0).container();
        for(int slot = 0; slot < StorageLocation.SLOTS_PER_PAGE; slot++) {
            ItemStack current = playerContainer.getItem(slot);
            if(ItemStack.matches(lastPlayerPage[slot], current)) continue;
            StorageLocation location = new StorageLocation(0, slot);
            index.update(location, current);
            pendingChanges.add(location);
            lastPlayerPage[slot] = current.copy();
            pageChanged(0);
            changed = true;
        }
        return changed;
    }

    private void rememberPlayerPage() {
        Container container = pages.get(0).container();
        for(int slot = 0; slot < StorageLocation.SLOTS_PER_PAGE; slot++) {
            lastPlayerPage[slot] = container.getItem(slot).copy();
        }
    }

    private void rebuildIndex() {
        Map<Integer, Container> ready = new HashMap<>();
        pages.forEach((pageIndex, page) -> {
            if(page.isReady()) ready.put(pageIndex, page.container());
        });
        index.rebuild(ready);
    }

    private int countReadyEmptyExtraPages() {
        int result = 0;
        for(StoragePage page : pages.values()) {
            if(page.index() == 0 || !page.isReady()) continue;
            Container container = page.container();
            boolean empty = true;
            for(int slot = 0; slot < StorageLocation.SLOTS_PER_PAGE; slot++) {
                if(!container.getItem(slot).isEmpty()) {
                    empty = false;
                    break;
                }
            }
            if(empty) result++;
        }
        return result;
    }

    private long ensureBatchStarted() {
        if(pendingBaseRevision < 0) pendingBaseRevision = revision;
        return pendingBaseRevision;
    }

    private void commitPendingPageAdds() {
        if(pendingPageAdds.isEmpty() || pendingBaseRevision >= 0) return;
        long baseRevision = ensureBatchStarted();
        finishMutation(baseRevision);
    }

    private void finishMutation(long baseRevision) {
        if(baseRevision != pendingBaseRevision) {
            throw new IllegalStateException("Nested or overlapping storage transaction");
        }
        revision++;
    }

    private void pageChanged(int pageIndex) {
        StoragePage page = pages.get(pageIndex);
        if(page != null && page.index() > 0 && page.isReady()) {
            page.markChanged();
        }
    }

    public record SlotChange(StorageLocation location, ItemStack stack) {
    }

    public record Delta(
            long baseRevision,
            long revision,
            List<SlotChange> changes,
            List<Integer> pageAdds
    ) {
        public Delta {
            changes = List.copyOf(changes);
            pageAdds = List.copyOf(pageAdds);
        }
    }

    private record LocatedStack(StorageLocation location, ItemStack stack) {
        private int count() {
            return stack.getCount();
        }
    }

    private record InsertionPlan(
            Map<ItemTemplateKey, Deque<StorageLocation>> mergeSlots,
            Deque<StorageLocation> emptySlots
    ) {
    }
}
