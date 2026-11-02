package top.frostor.carpet.bigenderchest.index;

public record StorageLocation(int storagePage, int slot) implements Comparable<StorageLocation> {
    public static final int SLOTS_PER_PAGE = 27;

    public StorageLocation {
        if(storagePage < 0) {
            throw new IllegalArgumentException("Storage page must not be negative");
        }
        if(slot < 0 || slot >= SLOTS_PER_PAGE) {
            throw new IllegalArgumentException("Storage slot must be in 0..26");
        }
    }

    @Override
    public int compareTo(StorageLocation other) {
        int page = Integer.compare(storagePage, other.storagePage);
        return page != 0 ? page : Integer.compare(slot, other.slot);
    }
}
