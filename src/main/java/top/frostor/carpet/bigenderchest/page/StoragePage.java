package top.frostor.carpet.bigenderchest.page;

import net.minecraft.world.Container;

import java.util.Objects;
import java.util.function.Supplier;

public final class StoragePage {
    private final int index;
    private final PageMetadata metadata;
    private final Supplier<? extends Container> containerSupplier;
    private StoragePageState state;
    private long contentVersion;

    private StoragePage(
            int index,
            PageMetadata metadata,
            Supplier<? extends Container> containerSupplier,
            StoragePageState state
    ) {
        this.index = index;
        this.metadata = metadata;
        this.containerSupplier = Objects.requireNonNull(containerSupplier, "containerSupplier");
        this.state = state;
    }

    public static StoragePage playerPage(
            PageMetadata metadata,
            Supplier<? extends Container> containerSupplier
    ) {
        return new StoragePage(0, metadata, containerSupplier, StoragePageState.READY);
    }

    public static StoragePage fakePage(
            PageMetadata metadata,
            Supplier<? extends Container> containerSupplier,
            StoragePageState state
    ) {
        if(metadata.storagePage() < 1) {
            throw new IllegalArgumentException("Extra pages start at index 1");
        }
        return new StoragePage(metadata.storagePage(), metadata, containerSupplier, state);
    }

    public int index() {
        return index;
    }

    public PageMetadata metadata() {
        return metadata;
    }

    public Container container() {
        return containerSupplier.get();
    }

    public StoragePageState state() {
        return state;
    }

    public long contentVersion() {
        return contentVersion;
    }

    public boolean isReady() {
        return state == StoragePageState.READY
                || state == StoragePageState.DIRTY
                || state == StoragePageState.SAVING;
    }

    public void setState(StoragePageState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public long markChanged() {
        state = StoragePageState.DIRTY;
        return ++contentVersion;
    }

    public long beginSaving() {
        state = StoragePageState.SAVING;
        return contentVersion;
    }

    public void finishSaving(long savedVersion) {
        state = contentVersion == savedVersion
                ? StoragePageState.READY
                : StoragePageState.DIRTY;
    }

    public void failSaving() {
        state = StoragePageState.ERROR;
    }
}
