package top.frostor.carpet.easyplace;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Server-local snapshot decoded before the vanilla placement context can transform its target.
 */
public final class EasyPlacePlacementRequest {
    private static final ThreadLocal<Deque<Snapshot>> REQUESTS = new ThreadLocal<>();

    private EasyPlacePlacementRequest() {
    }

    public static void begin(Snapshot snapshot) {
        Deque<Snapshot> requests = REQUESTS.get();
        if(requests == null) {
            requests = new ArrayDeque<>();
            REQUESTS.set(requests);
        }
        requests.push(snapshot);
    }

    public static void end() {
        Deque<Snapshot> requests = REQUESTS.get();
        if(requests != null && !requests.isEmpty()) {
            requests.pop();
        }
        if(requests == null || requests.isEmpty()) {
            REQUESTS.remove();
        }
    }

    public static @Nullable Snapshot current() {
        Deque<Snapshot> requests = REQUESTS.get();
        return requests == null ? null : requests.peek();
    }

    public record Snapshot(
            int rawValue,
            int auxiliaryValue,
            int yPrecisionValue
    ) {
    }
}
