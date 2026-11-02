package top.frostor.carpet.bigenderchest.index;

import java.util.List;

public record StorageEntry(
        ItemTemplateKey template,
        long totalAmount,
        List<StorageLocation> locations
) {
    public StorageEntry {
        if(totalAmount < 0) {
            throw new IllegalArgumentException("Total amount must not be negative");
        }
        locations = List.copyOf(locations);
    }
}
