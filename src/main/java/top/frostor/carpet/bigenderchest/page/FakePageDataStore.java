package top.frostor.carpet.bigenderchest.page;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import top.frostor.carpet.FrostorsCarpetAddition;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Reads and writes standard playerdata files without constructing a ServerPlayer.
 */
public final class FakePageDataStore {
    private static final String ENDER_ITEMS = "EnderItems";

    private final MinecraftServer server;
    private final Path playerDataDirectory;
    private final Map<UUID, CompoundTag> basePlayerData = new ConcurrentHashMap<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Frostors-BigEnderChest-IO");
        thread.setDaemon(true);
        return thread;
    });

    public FakePageDataStore(MinecraftServer server) {
        this.server = server;
        this.playerDataDirectory = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
    }

    public PlayerEnderChestContainer load(PageMetadata metadata) throws IOException {
        NameAndId identity = identity(metadata);
        if(server.getPlayerList().getPlayer(metadata.pageUuid()) != null) {
            throw new IOException("Fake page profile is currently online: " + metadata.pageName());
        }

        CompoundTag cached = basePlayerData.get(metadata.pageUuid());
        CompoundTag playerData = cached == null
                ? server.getPlayerList().loadPlayerData(identity)
                        .orElseGet(FakePageDataStore::emptyPlayerData)
                : cached.copy();
        basePlayerData.put(metadata.pageUuid(), playerData.copy());
        PlayerEnderChestContainer container = new PlayerEnderChestContainer();
        try {
            ValueInput input = TagValueInput.create(
                    ProblemReporter.DISCARDING,
                    server.registryAccess(),
                    playerData
            );
            container.fromSlots(input.listOrEmpty(ENDER_ITEMS, ItemStackWithSlot.CODEC));
        }catch(RuntimeException exception) {
            throw new IOException("Failed to decode fake page " + metadata.pageName(), exception);
        }
        return container;
    }

    public void save(PageMetadata metadata, PlayerEnderChestContainer container) throws IOException {
        NameAndId identity = identity(metadata);
        if(server.getPlayerList().getPlayer(metadata.pageUuid()) != null) {
            throw new IOException("Refusing to save an online fake page: " + metadata.pageName());
        }

        CompoundTag playerData = server.getPlayerList().loadPlayerData(identity)
                .orElseGet(FakePageDataStore::emptyPlayerData);
        try {
            TagValueOutput output = TagValueOutput.createWithContext(
                    ProblemReporter.DISCARDING,
                    server.registryAccess()
            );
            container.storeAsSlots(
                    output.list(ENDER_ITEMS, ItemStackWithSlot.CODEC)
            );
            CompoundTag encoded = output.buildResult();
            if(encoded.contains(ENDER_ITEMS)) {
                playerData.put(ENDER_ITEMS, encoded.get(ENDER_ITEMS));
            }else {
                playerData.put(ENDER_ITEMS, new ListTag());
            }
        }catch(RuntimeException exception) {
            throw new IOException("Failed to encode fake page " + metadata.pageName(), exception);
        }
        playerData.putInt(
                SharedConstants.DATA_VERSION_TAG,
                SharedConstants.getCurrentVersion().dataVersion().version()
        );

        writeAtomically(metadata.pageUuid(), playerData);
    }

    public void createEmptyAsync(PageMetadata metadata) {
        CompoundTag snapshot = emptyPlayerData();
        basePlayerData.put(metadata.pageUuid(), snapshot.copy());
        executeIo("create empty page " + metadata.pageName(), () ->
                writeAtomically(metadata.pageUuid(), snapshot), null);
    }

    public void saveAsync(
            PageMetadata metadata,
            PlayerEnderChestContainer container,
            Consumer<Boolean> completion
    ) {
        CompoundTag snapshot;
        try {
            snapshot = snapshot(metadata, container);
        }catch(IOException exception) {
            FrostorsCarpetAddition.LOGGER.error(
                    "Failed to snapshot big ender chest page {}",
                    metadata.pageName(),
                    exception
            );
            completion.accept(false);
            return;
        }
        executeIo("save page " + metadata.pageName(), () ->
                writeAtomically(metadata.pageUuid(), snapshot), completion);
    }

    public void flush() {
        try {
            ioExecutor.submit(() -> {
            }).get(10, TimeUnit.SECONDS);
        }catch(Exception exception) {
            FrostorsCarpetAddition.LOGGER.error(
                    "Timed out flushing big ender chest page data",
                    exception
            );
        }
    }

    private void writeAtomically(java.util.UUID pageUuid, CompoundTag playerData) throws IOException {
        Files.createDirectories(playerDataDirectory);
        Path target = playerDataDirectory.resolve(pageUuid + ".dat");
        Path backup = playerDataDirectory.resolve(pageUuid + ".dat_old");
        Path temporary = playerDataDirectory.resolve(pageUuid + ".dat.tmp");
        try {
            NbtIo.writeCompressed(playerData, temporary);
            if(Files.isRegularFile(target)) {
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            moveReplacing(temporary, target);
        }finally {
            try {
                Files.deleteIfExists(temporary);
            }catch(IOException exception) {
                FrostorsCarpetAddition.LOGGER.warn(
                        "Failed to remove temporary fake page data {}",
                        temporary,
                        exception
                );
            }
        }
    }

    private static CompoundTag emptyPlayerData() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(
                SharedConstants.DATA_VERSION_TAG,
                SharedConstants.getCurrentVersion().dataVersion().version()
        );
        tag.put(ENDER_ITEMS, new ListTag());
        return tag;
    }

    private static NameAndId identity(PageMetadata metadata) {
        return new NameAndId(metadata.pageUuid(), metadata.pageName());
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }catch(AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private CompoundTag snapshot(
            PageMetadata metadata,
            PlayerEnderChestContainer container
    ) throws IOException {
        CompoundTag playerData = basePlayerData.get(metadata.pageUuid());
        if(playerData == null) {
            playerData = server.getPlayerList().loadPlayerData(identity(metadata))
                    .orElseGet(FakePageDataStore::emptyPlayerData);
            basePlayerData.put(metadata.pageUuid(), playerData.copy());
        }else {
            playerData = playerData.copy();
        }
        try {
            TagValueOutput output = TagValueOutput.createWithContext(
                    ProblemReporter.DISCARDING,
                    server.registryAccess()
            );
            container.storeAsSlots(
                    output.list(ENDER_ITEMS, ItemStackWithSlot.CODEC)
            );
            CompoundTag encoded = output.buildResult();
            if(encoded.contains(ENDER_ITEMS)) {
                playerData.put(ENDER_ITEMS, encoded.get(ENDER_ITEMS));
            }else {
                playerData.put(ENDER_ITEMS, new ListTag());
            }
        }catch(RuntimeException exception) {
            throw new IOException("Failed to encode fake page " + metadata.pageName(), exception);
        }
        playerData.putInt(
                SharedConstants.DATA_VERSION_TAG,
                SharedConstants.getCurrentVersion().dataVersion().version()
        );
        basePlayerData.put(metadata.pageUuid(), playerData.copy());
        return playerData;
    }

    private void executeIo(String description, IoTask task, Consumer<Boolean> completion) {
        ioExecutor.execute(() -> {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            try {
                task.run();
            }catch(Throwable throwable) {
                failure.set(throwable);
                FrostorsCarpetAddition.LOGGER.error(
                        "Failed to {} asynchronously",
                        description,
                        throwable
                );
            }
            if(completion != null) {
                boolean success = failure.get() == null;
                server.execute(() -> completion.accept(success));
            }
        });
    }

    @FunctionalInterface
    private interface IoTask {
        void run() throws IOException;
    }
}
