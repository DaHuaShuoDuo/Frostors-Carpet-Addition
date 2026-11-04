package top.frostor.carpet.bigenderchest;

import carpet.patches.EntityPlayerMPFake;
import carpet.patches.FakeClientConnection;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
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
import java.util.HashSet;
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
    private static final long AUTO_FAKE_IDLE_TICKS = 200;

    private final ServerPlayer owner;
    private final UUID storageOwner;
    private final PageRegistry pageRegistry;
    private final FakePageDataStore dataStore;
    private final NavigableMap<Integer, StoragePage> pages = new TreeMap<>();
    private final Map<Integer, Container> offlinePageContainers = new HashMap<>();
    private final Map<Integer, Container> onlinePageContainers = new HashMap<>();
    private final Map<Integer, ItemStack[]> onlinePageSnapshots = new HashMap<>();
    private final Set<Integer> onlinePages = new HashSet<>();
    private final Set<Integer> autoSpawnedPages = new HashSet<>();
    private final Set<Integer> pendingAutoPages = new HashSet<>();
    private final Map<Integer, Long> lastAutoSpawnAttempts = new HashMap<>();
    private final Map<Integer, Long> lastAutoPageUses = new HashMap<>();
    private final BigEnderChestIndex index = new BigEnderChestIndex();
    private final Set<StorageLocation> pendingChanges = new LinkedHashSet<>();
    private final List<Integer> pendingPageAdds = new ArrayList<>();

    private final ItemStack[] lastPlayerPage = new ItemStack[StorageLocation.SLOTS_PER_PAGE];
    private long revision;
    private long pageRevision = 1;
    private long pendingBaseRevision = -1;
    private long serviceTick;
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

    public long pageRevision() {
        return pageRevision;
    }

    public int pageCount() {
        return pages.size();
    }

    public boolean isReadOnly() {
        return pages.values().stream().anyMatch(page ->
                page.state() == StoragePageState.UNLOADED
                        || page.state() == StoragePageState.LOADING
                        || page.state() == StoragePageState.ERROR);
    }

    public boolean hasPageErrors() {
        return pages.values().stream()
                .anyMatch(page -> page.state() == StoragePageState.ERROR);
    }

    public List<PageStatus> pageCatalog() {
        List<PageStatus> result = new ArrayList<>(pages.size());
        for(StoragePage page : pages.values()) {
            int occupied = 0;
            if(page.isReady()) {
                Container container = page.container();
                for(int slot = 0; slot < StorageLocation.SLOTS_PER_PAGE; slot++) {
                    if(!container.getItem(slot).isEmpty()) occupied++;
                }
            }
            result.add(new PageStatus(
                    page.index(),
                    page.state(),
                    page.isReady() && occupied == 0,
                    occupied,
                    page.contentVersion()
            ));
        }
        return List.copyOf(result);
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
        stopAutoSpawnedPages();
        saveDirtyPages();
        closed = true;
    }

    public List<StorageEntry> entries() {
        return index.entries();
    }

    public List<BigEnderChestIndex.AggregateEntry> aggregateEntries() {
        return index.aggregates();
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
        // The terminal protocol gates loading pages; the vanilla menu may use already loaded pages.
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
        // The terminal protocol gates loading pages; the vanilla menu may use already loaded pages.
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
            updatePlayerPageSnapshot(
                    candidate.location(),
                    container.getItem(candidate.location().slot())
            );
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
        serviceTick++;
        maintainLiveFakePages();
        refreshOnlinePageState();
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

    void refreshOnlinePageState() {
        if(refreshOnlinePages()) {
            long baseRevision = ensureBatchStarted();
            finishMutation(baseRevision);
        }
        if(refreshPlayerPage()) {
            long baseRevision = ensureBatchStarted();
            finishMutation(baseRevision);
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
                .filter(page -> !onlinePages.contains(page.index()))
                .filter(page -> onlinePagePlayer(page.metadata()) == null)
                .filter(page -> page.state() == StoragePageState.DIRTY)
                .forEach(page -> {
                    long version = page.beginSaving();
                    pageRevision++;
                    dataStore.saveAsync(
                            page.metadata(),
                            (net.minecraft.world.inventory.PlayerEnderChestContainer) page.container(),
                            success -> {
                                if(success) {
                                    page.finishSaving(version);
                                    pageRevision++;
                                }else {
                                    page.failSaving();
                                    pageRevision++;
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

    public boolean onPagePlayerJoin(ServerPlayer pagePlayer) {
        StoragePage page = pageForPlayer(pagePlayer.getUUID());
        if(page == null || onlinePages.contains(page.index())) return false;
        bindOnlinePage(page, pagePlayer.getEnderChestInventory());
        long baseRevision = ensureBatchStarted();
        finishMutation(baseRevision);
        return true;
    }

    public boolean onPagePlayerDisconnect(ServerPlayer pagePlayer) {
        StoragePage page = pageForPlayer(pagePlayer.getUUID());
        if(page == null || !onlinePages.contains(page.index())) return false;
        boolean autoSpawned = autoSpawnedPages.contains(page.index());
        unbindOnlinePage(page, pagePlayer.getEnderChestInventory());
        long baseRevision = ensureBatchStarted();
        finishMutation(baseRevision);
        if(autoSpawned
                && !closed
                && FrostorSettings.bigEnderChestLiveFakePlayers) {
            pendingAutoPages.add(page.index());
        }
        return true;
    }

    private void loadExtraPages() {
        for(PageMetadata metadata : pageRegistry.pages(storageOwner)) {
            net.minecraft.world.inventory.PlayerEnderChestContainer container =
                    new net.minecraft.world.inventory.PlayerEnderChestContainer();
            int pageIndex = metadata.storagePage();
            offlinePageContainers.put(pageIndex, container);
            StoragePage page = StoragePage.fakePage(
                    metadata,
                    () -> activePageContainer(metadata),
                    StoragePageState.LOADING
            );
            pages.put(metadata.storagePage(), page);
            pageRevision++;
            ServerPlayer online = onlinePagePlayer(metadata);
            if(online != null) {
                bindOnlinePage(page, online.getEnderChestInventory());
                page.setState(StoragePageState.READY);
                continue;
            }
            dataStore.loadAsync(
                    metadata,
                    loaded -> {
                        if(closed) return;
                        copyItems(loaded, container);
                        page.setState(StoragePageState.READY);
                        index.replacePage(metadata.storagePage(), page.container());
                        pageRevision++;
                        revision++;
                    },
                    exception -> {
                        if(closed) return;
                        ServerPlayer nowOnline = onlinePagePlayer(metadata);
                        if(nowOnline != null) {
                            bindOnlinePage(page, nowOnline.getEnderChestInventory());
                            page.setState(StoragePageState.READY);
                            index.replacePage(metadata.storagePage(), page.container());
                            pageRevision++;
                            revision++;
                            return;
                        }
                        page.setState(StoragePageState.ERROR);
                        pageRevision++;
                        FrostorsCarpetAddition.LOGGER.error(
                                "Failed to load big ender chest page {} for {}",
                                metadata.storagePage(),
                                owner.getScoreboardName(),
                                exception
                        );
                    }
            );
        }
    }

    private boolean refreshOnlinePages() {
        boolean changed = false;
        for(StoragePage page : pages.values()) {
            if(page.index() == 0) continue;
            ServerPlayer online = onlinePagePlayer(page.metadata());
            if(online != null) {
                Container live = online.getEnderChestInventory();
                if(!onlinePages.contains(page.index())) {
                    bindOnlinePage(page, live);
                    changed = true;
                }else {
                    onlinePageContainers.put(page.index(), live);
                    if(page.isReady() && refreshOnlinePageContent(page, live)) {
                        changed = true;
                    }
                }
            }else if(onlinePages.contains(page.index())) {
                unbindOnlinePage(page, onlinePageContainers.get(page.index()));
                changed = true;
            }
        }
        return changed;
    }

    void maintainLiveFakePages() {
        maintainLiveFakePages(FrostorSettings.bigEnderChestLiveFakePlayers);
    }

    void maintainLiveFakePages(boolean enabled) {
        if(!enabled) {
            pendingAutoPages.clear();
            stopAutoSpawnedPages();
            return;
        }
        releaseIdleAutoPages();
        for(int pageIndex : List.copyOf(pendingAutoPages)) {
            StoragePage page = pages.get(pageIndex);
            if(page == null || !page.isReady()) continue;
            PageMetadata metadata = page.metadata();
            if(onlinePagePlayer(metadata) != null) {
                pendingAutoPages.remove(pageIndex);
                continue;
            }
            long lastAttempt = lastAutoSpawnAttempts.getOrDefault(
                    pageIndex,
                    Long.MIN_VALUE
            );
            if(lastAttempt != Long.MIN_VALUE && serviceTick - lastAttempt < 100) continue;
            lastAutoSpawnAttempts.put(pageIndex, serviceTick);
            if(spawnLivePageFake(page)) {
                autoSpawnedPages.add(pageIndex);
                pendingAutoPages.remove(pageIndex);
            }else {
                FrostorsCarpetAddition.LOGGER.warn(
                        "Failed to spawn big ender chest page fake player {} for {}",
                        metadata.pageName(),
                        owner.getScoreboardName()
                );
            }
        }
    }

    private boolean spawnLivePageFake(StoragePage page) {
        PageMetadata metadata = page.metadata();
        try {
            GameProfile profile = new GameProfile(
                    metadata.pageUuid(),
                    metadata.pageName()
            );
            ClientInformation clientInformation = ClientInformation.createDefault();
            EntityPlayerMPFake fake = EntityPlayerMPFake.respawnFake(
                    owner.level().getServer(),
                    owner.level(),
                    profile,
                    clientInformation
            );
            owner.level().getServer().getPlayerList()
                    .loadPlayerData(fake.nameAndId())
                    .ifPresent(tag -> fake.load(TagValueInput.create(
                            ProblemReporter.DISCARDING,
                            owner.level().getServer().registryAccess(),
                            tag
                    )));
            Container offline = offlinePageContainers.get(page.index());
            if(page.state() == StoragePageState.DIRTY && offline != null) {
                copyItems(offline, fake.getEnderChestInventory());
            }
            owner.level().getServer().getPlayerList().placeNewPlayer(
                    new FakeClientConnection(PacketFlow.SERVERBOUND),
                    fake,
                    new CommonListenerCookie(profile, 0, clientInformation, false)
            );
            fake.setGameMode(GameType.SPECTATOR);
            fake.getAbilities().flying = true;
            fake.onUpdateAbilities();
            fake.teleportTo(
                    owner.level(),
                    owner.getX(),
                    owner.getY() + 1.0,
                    owner.getZ(),
                    Set.of(),
                    owner.getYRot(),
                    owner.getXRot(),
                    true
            );
            return owner.level().getServer().getPlayerList()
                    .getPlayer(metadata.pageUuid()) != null;
        }catch(RuntimeException exception) {
            FrostorsCarpetAddition.LOGGER.error(
                    "Failed to create live big ender chest page {} for {}",
                    metadata.pageName(),
                    owner.getScoreboardName(),
                    exception
            );
            return false;
        }
    }

    private void releaseIdleAutoPages() {
        for(int pageIndex : List.copyOf(autoSpawnedPages)) {
            long lastUse = lastAutoPageUses.getOrDefault(
                    pageIndex,
                    Long.MIN_VALUE
            );
            if(lastUse != Long.MIN_VALUE
                    && serviceTick - lastUse < AUTO_FAKE_IDLE_TICKS) {
                continue;
            }
            StoragePage page = pages.get(pageIndex);
            if(page == null) {
                autoSpawnedPages.remove(pageIndex);
                lastAutoPageUses.remove(pageIndex);
                continue;
            }
            ServerPlayer pagePlayer = onlinePagePlayer(page.metadata());
            if(pagePlayer != null) {
                releaseAutoSpawnedPage(page, pagePlayer);
            }
        }
    }

    private void stopAutoSpawnedPages() {
        for(int pageIndex : List.copyOf(autoSpawnedPages)) {
            StoragePage page = pages.get(pageIndex);
            if(page == null) continue;
            ServerPlayer pagePlayer = onlinePagePlayer(page.metadata());
            if(pagePlayer == null || pagePlayer.connection == null) continue;
            releaseAutoSpawnedPage(page, pagePlayer);
        }
        autoSpawnedPages.clear();
        lastAutoPageUses.clear();
    }

    private void releaseAutoSpawnedPage(StoragePage page, ServerPlayer pagePlayer) {
        autoSpawnedPages.remove(page.index());
        lastAutoPageUses.remove(page.index());
        if(onlinePages.contains(page.index())) {
            unbindOnlinePage(page, pagePlayer.getEnderChestInventory());
            long baseRevision = ensureBatchStarted();
            finishMutation(baseRevision);
        }
        if(pagePlayer instanceof EntityPlayerMPFake fakePlayer) {
            fakePlayer.kill(
                    Component.translatable("big_ender_chest.fake_page.disconnect")
            );
        }else if(pagePlayer.connection != null) {
            pagePlayer.connection.disconnect(
                    Component.translatable("big_ender_chest.fake_page.disconnect")
            );
        }
    }

    private boolean refreshOnlinePageContent(StoragePage page, Container container) {
        ItemStack[] snapshot = onlinePageSnapshots.get(page.index());
        if(snapshot == null) return false;
        boolean changed = false;
        for(int slot = 0; slot < StorageLocation.SLOTS_PER_PAGE; slot++) {
            ItemStack current = container.getItem(slot);
            if(ItemStack.matches(snapshot[slot], current)) continue;
            StorageLocation location = new StorageLocation(page.index(), slot);
            snapshot[slot] = current.copy();
            index.update(location, current);
            pendingChanges.add(location);
            changed = true;
        }
        if(changed) {
            page.markChanged();
            pageRevision++;
            if(autoSpawnedPages.contains(page.index())) {
                lastAutoPageUses.put(page.index(), serviceTick);
            }
        }
        return changed;
    }

    private void bindOnlinePage(StoragePage page, Container container) {
        Container offline = offlinePageContainers.get(page.index());
        if(page.state() == StoragePageState.DIRTY && offline != null && offline != container) {
            copyItems(offline, container);
        }
        onlinePages.add(page.index());
        onlinePageContainers.put(page.index(), container);
        onlinePageSnapshots.put(page.index(), snapshot(container));
        if(page.state() != StoragePageState.LOADING) {
            page.setState(StoragePageState.READY);
        }
        if(page.isReady()) {
            index.replacePage(page.index(), container);
            pageRevision++;
            addPageToPendingDelta(page.index());
        }
    }

    private void unbindOnlinePage(StoragePage page, Container live) {
        Container offline = offlinePageContainers.get(page.index());
        if(live != null && offline != null && live != offline) {
            copyItems(live, offline);
        }
        onlinePages.remove(page.index());
        onlinePageContainers.remove(page.index());
        onlinePageSnapshots.remove(page.index());
        page.markChanged();
        index.replacePage(page.index(), page.container());
        pageRevision++;
        addPageToPendingDelta(page.index());
    }

    private Container activePageContainer(PageMetadata metadata) {
        int pageIndex = metadata.storagePage();
        if(onlinePages.contains(pageIndex)) {
            Container online = onlinePageContainers.get(pageIndex);
            if(online != null) return online;
        }
        return offlinePageContainers.get(pageIndex);
    }

    private ServerPlayer onlinePagePlayer(PageMetadata metadata) {
        return owner.level().getServer().getPlayerList().getPlayer(metadata.pageUuid());
    }

    private StoragePage pageForPlayer(UUID playerUuid) {
        for(StoragePage page : pages.values()) {
            if(page.index() > 0 && page.metadata().pageUuid().equals(playerUuid)) {
                return page;
            }
        }
        return null;
    }

    private void addPageToPendingDelta(int pageIndex) {
        for(int slot = 0; slot < StorageLocation.SLOTS_PER_PAGE; slot++) {
            pendingChanges.add(new StorageLocation(pageIndex, slot));
        }
    }

    private static ItemStack[] snapshot(Container container) {
        ItemStack[] snapshot = new ItemStack[StorageLocation.SLOTS_PER_PAGE];
        for(int slot = 0; slot < snapshot.length; slot++) {
            snapshot[slot] = container.getItem(slot).copy();
        }
        return snapshot;
    }

    private static void copyItems(Container source, Container target) {
        for(int slot = 0; slot < StorageLocation.SLOTS_PER_PAGE; slot++) {
            target.setItem(slot, source.getItem(slot).copy());
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
            pageRevision++;
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
            return;
        }
        ItemStack[] snapshot = onlinePageSnapshots.get(location.storagePage());
        if(snapshot != null) {
            snapshot[location.slot()] = stack.copy();
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

    void pageChanged(int pageIndex) {
        StoragePage page = pages.get(pageIndex);
        if(page != null && page.index() > 0 && page.isReady()) {
            page.markChanged();
            pageRevision++;
            if(autoSpawnedPages.contains(page.index())) {
                lastAutoPageUses.put(page.index(), serviceTick);
            }
            if(FrostorSettings.bigEnderChestLiveFakePlayers
                    && onlinePagePlayer(page.metadata()) == null) {
                requestLiveFakePage(page.index());
            }
        }
    }

    void requestLiveFakePage(int pageIndex) {
        if(pageIndex <= 0 || !pages.containsKey(pageIndex)) return;
        pendingAutoPages.add(pageIndex);
        lastAutoPageUses.put(pageIndex, serviceTick);
    }

    public record SlotChange(StorageLocation location, ItemStack stack) {
    }

    public record PageStatus(
            int pageId,
            StoragePageState state,
            boolean empty,
            int occupiedSlots,
            long contentRevision
    ) {
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
