package top.frostor.carpet.bigenderchest.capacity;

import top.frostor.carpet.bigenderchest.index.StorageLocation;

public final class BigEnderChestCapacity {
    public static final int MIN_PAGE_LIMIT = 1;
    public static final int MAX_PAGE_LIMIT = 1024;
    public static final int UNLIMITED_PAGE_LIMIT = -1;
    public static final int INITIAL_WARM_PAGES = 2;
    public static final int BUFFER_WARM_PAGES = 2;
    public static final int GROWTH_CHUNK_PAGES = 2;
    public static final int PREWARM_PERCENT = 75;
    public static final int MAX_BATCH_SLOTS = 4096;

    private BigEnderChestCapacity() {
    }

    public static boolean hasValidPageLimit(int configuredLimit) {
        return configuredLimit == UNLIMITED_PAGE_LIMIT
                || configuredLimit >= MIN_PAGE_LIMIT
                && configuredLimit <= MAX_PAGE_LIMIT;
    }

    public static boolean canCreateExtraPage(int existingExtraPages, int configuredLimit) {
        if(configuredLimit == UNLIMITED_PAGE_LIMIT) return true;
        return hasValidPageLimit(configuredLimit) && existingExtraPages < configuredLimit;
    }

    public static int pagesForSlots(int requestedSlots) {
        if(requestedSlots < 0 || requestedSlots > MAX_BATCH_SLOTS) {
            throw new IllegalArgumentException("Batch slot count outside supported range");
        }
        return Math.ceilDiv(requestedSlots, StorageLocation.SLOTS_PER_PAGE);
    }

    public static int growthPages(int readyEmptyPages, int occupiedSlots, int totalSlots) {
        boolean lowBuffer = readyEmptyPages < BUFFER_WARM_PAGES;
        boolean highWater = totalSlots > 0
                && occupiedSlots * 100L >= (long) totalSlots * PREWARM_PERCENT;
        return lowBuffer || highWater ? GROWTH_CHUNK_PAGES : 0;
    }
}
