package top.frostor.carpet.bigenderchest.index;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Rebuildable aggregate index. It is a cache; page containers remain the source of truth.
 */
public final class BigEnderChestIndex {
    private final NavigableMap<ItemTemplateKey, MutableEntry> entries = new TreeMap<>();
    private final Map<StorageLocation, ItemTemplateKey> keysByLocation = new HashMap<>();
    private final Map<StorageLocation, Long> amountsByLocation = new HashMap<>();

    public void rebuild(Map<Integer, ? extends Container> pages) {
        entries.clear();
        keysByLocation.clear();
        amountsByLocation.clear();
        pages.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(page -> replacePage(page.getKey(), page.getValue()));
    }

    /**
     * Replaces only one page in the aggregate index.
     */
    public void replacePage(int storagePage, Container container) {
        for(int slot = 0; slot < StorageLocation.SLOTS_PER_PAGE; slot++) {
            update(
                    new StorageLocation(storagePage, slot),
                    container.getItem(slot)
            );
        }
    }

    public void update(StorageLocation location, ItemStack stack) {
        ItemTemplateKey oldKey = keysByLocation.remove(location);
        Long removedAmount = amountsByLocation.remove(location);
        long oldAmount = removedAmount == null ? 0 : removedAmount;
        if(oldKey != null) {
            MutableEntry oldEntry = entries.get(oldKey);
            if(oldEntry != null) {
                oldEntry.locations.remove(location);
                oldEntry.totalAmount -= oldAmount;
                if(oldEntry.locations.isEmpty()) entries.remove(oldKey);
            }
        }

        ItemTemplateKey newKey = ItemTemplateKey.of(stack);
        if(newKey == null) return;
        MutableEntry newEntry = entries.computeIfAbsent(newKey, MutableEntry::new);
        newEntry.locations.add(location);
        newEntry.totalAmount += stack.getCount();
        keysByLocation.put(location, newKey);
        amountsByLocation.put(location, (long) stack.getCount());
    }

    public List<StorageEntry> entries() {
        List<StorageEntry> result = new ArrayList<>(entries.size());
        entries.forEach((key, entry) -> result.add(entry.snapshot(key)));
        result.sort(Comparator.comparing(StorageEntry::template));
        return List.copyOf(result);
    }

    public StorageEntry entry(ItemTemplateKey key) {
        MutableEntry entry = entries.get(key);
        return entry == null ? null : entry.snapshot(key);
    }

    public ItemTemplateKey keyAt(StorageLocation location) {
        return keysByLocation.get(location);
    }

    public int templateCount() {
        return entries.size();
    }

    public int occupiedSlotCount() {
        return keysByLocation.size();
    }

    /**
     * Lightweight immutable snapshot for protocol work that only needs totals.
     */
    public List<AggregateEntry> aggregates() {
        List<AggregateEntry> result = new ArrayList<>(entries.size());
        entries.forEach((key, entry) ->
                result.add(new AggregateEntry(key, entry.totalAmount)));
        return List.copyOf(result);
    }

    public record AggregateEntry(ItemTemplateKey template, long totalAmount) {
        public AggregateEntry {
            if(totalAmount < 0) {
                throw new IllegalArgumentException("Total amount must not be negative");
            }
        }
    }

    private static final class MutableEntry {
        private final List<StorageLocation> locations = new ArrayList<>();
        private long totalAmount;

        private MutableEntry(ItemTemplateKey ignored) {
        }

        private StorageEntry snapshot(ItemTemplateKey key) {
            locations.sort(StorageLocation::compareTo);
            return new StorageEntry(key, totalAmount, List.copyOf(locations));
        }
    }
}
