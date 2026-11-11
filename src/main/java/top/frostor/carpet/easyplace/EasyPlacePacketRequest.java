package top.frostor.carpet.easyplace;

import org.jetbrains.annotations.Nullable;

/**
 * Server packet-local data decoded before the placement target can be transformed.
 */
public final class EasyPlacePacketRequest {
    private static final ThreadLocal<Snapshot> REQUEST = new ThreadLocal<>();

    private EasyPlacePacketRequest() {
    }

    public static void begin(Snapshot snapshot) {
        REQUEST.set(snapshot);
    }

    public static void end() {
        REQUEST.remove();
    }

    public static @Nullable Snapshot current() {
        return REQUEST.get();
    }

    public record Snapshot(int rawValue, int yPrecisionValue) {
    }
}
