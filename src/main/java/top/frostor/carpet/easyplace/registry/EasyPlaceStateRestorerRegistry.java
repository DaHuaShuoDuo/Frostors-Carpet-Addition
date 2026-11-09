package top.frostor.carpet.easyplace.registry;

import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class EasyPlaceStateRestorerRegistry {
    private final List<Entry> registrations = new ArrayList<>();
    private final Set<String> ids = new HashSet<>();
    private final Set<Integer> priorities = new HashSet<>();
    private List<Entry> frozenEntries = List.of();
    private boolean frozen;

    public synchronized void register(
            String id,
            int priority,
            EasyPlaceMatcher matcher,
            EasyPlaceStateRestorer restorer
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(matcher, "matcher");
        Objects.requireNonNull(restorer, "restorer");
        if(frozen) throw new IllegalStateException("Easy Place restorer registry is frozen");
        if(!ids.add(id)) throw new IllegalArgumentException("Duplicate adapter id: " + id);
        if(!priorities.add(priority)) {
            throw new IllegalArgumentException("Duplicate adapter priority: " + priority);
        }
        registrations.add(new Entry(id, priority, matcher, restorer));
    }

    public synchronized void freeze() {
        if(frozen) return;
        if(registrations.isEmpty()) throw new IllegalStateException("Registry must not be empty");
        registrations.sort(Comparator.comparingInt(Entry::priority).reversed());
        frozenEntries = List.copyOf(registrations);
        frozen = true;
    }

    public @Nullable BlockState restore(EasyPlaceRestoreContext context) {
        Objects.requireNonNull(context, "context");
        if(!frozen) throw new IllegalStateException("Easy Place restorer registry is not frozen");
        for(Entry entry : frozenEntries) {
            if(entry.matcher().matches(context)) return entry.restorer().restore(context);
        }
        return null;
    }

    public synchronized boolean isFrozen() {
        return frozen;
    }

    private record Entry(
            String id,
            int priority,
            EasyPlaceMatcher matcher,
            EasyPlaceStateRestorer restorer
    ) {
    }
}
