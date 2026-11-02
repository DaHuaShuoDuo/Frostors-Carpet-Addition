package top.frostor.carpet.bigenderchest.page;

import java.util.UUID;

public record PageMetadata(
        UUID owner,
        int storagePage,
        UUID pageUuid,
        String pageName
) {
    public PageMetadata {
        if(storagePage < 0) {
            throw new IllegalArgumentException("Storage page must not be negative");
        }
        if(pageName.length() < 3 || pageName.length() > 16) {
            throw new IllegalArgumentException("Fake page name must be 3..16 characters");
        }
    }
}
