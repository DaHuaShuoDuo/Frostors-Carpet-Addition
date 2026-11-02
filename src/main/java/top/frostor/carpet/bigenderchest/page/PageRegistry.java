package top.frostor.carpet.bigenderchest.page;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.storage.LevelResource;
import top.frostor.carpet.FrostorsCarpetAddition;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * World-level metadata registry for extra storage pages. Item contents stay in playerdata.
 */
public final class PageRegistry {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "big_ender_chest_pages.json";

    private final MinecraftServer server;
    private final Path path;
    private final Map<UUID, NavigableMap<Integer, PageMetadata>> pages = new java.util.HashMap<>();
    private final Map<UUID, SortPreference> sortPreferences = new java.util.HashMap<>();
    private final Set<String> usedNames = new HashSet<>();
    private final Set<UUID> usedPageUuids = new HashSet<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Frostors-BigEnderChest-Metadata");
        thread.setDaemon(true);
        return thread;
    });

    public PageRegistry(MinecraftServer server) {
        this.server = server;
        this.path = server.getWorldPath(LevelResource.DATA)
                .resolve(FrostorsCarpetAddition.MOD_ID)
                .resolve(FILE_NAME);
        load();
    }

    public synchronized List<PageMetadata> pages(UUID owner) {
        NavigableMap<Integer, PageMetadata> owned = pages.get(owner);
        return owned == null ? List.of() : List.copyOf(owned.values());
    }

    public synchronized int extraPageCount(UUID owner) {
        NavigableMap<Integer, PageMetadata> owned = pages.get(owner);
        return owned == null ? 0 : owned.size();
    }

    public synchronized SortPreference sortPreference(UUID owner) {
        return sortPreferences.getOrDefault(owner, SortPreference.DEFAULT);
    }

    public synchronized void setSortPreference(
            UUID owner,
            String field,
            String direction
    ) {
        SortPreference preference = new SortPreference(field, direction);
        SortPreference previous = sortPreferences.get(owner);
        if(preference.equals(previous)
                || preference.equals(SortPreference.DEFAULT)
                && previous == null) {
            return;
        }
        if(preference.equals(SortPreference.DEFAULT)) {
            sortPreferences.remove(owner);
        }else {
            sortPreferences.put(owner, preference);
        }
        requestSave();
    }

    public synchronized PageMetadata allocate(
            UUID owner,
            String ownerName,
            int requestedPage
    ) {
        NavigableMap<Integer, PageMetadata> owned = pages.computeIfAbsent(
                owner,
                ignored -> new TreeMap<>()
        );
        if(owned.containsKey(requestedPage)) return owned.get(requestedPage);

        PageMetadata metadata = createMetadata(owner, ownerName, requestedPage);
        owned.put(requestedPage, metadata);
        usedNames.add(metadata.pageName());
        usedPageUuids.add(metadata.pageUuid());
        server.services().nameToIdCache().add(new NameAndId(metadata.pageUuid(), metadata.pageName()));
        requestSave();
        return metadata;
    }

    public synchronized boolean save() {
        return writeJson(toJson());
    }

    public void flush() {
        try {
            ioExecutor.submit(() -> {
            }).get(10, TimeUnit.SECONDS);
        }catch(Exception exception) {
            FrostorsCarpetAddition.LOGGER.error(
                    "Timed out flushing big ender chest page metadata",
                    exception
            );
        }
    }

    private synchronized String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", FORMAT_VERSION);
        JsonArray owners = new JsonArray();
        pages.forEach((owner, ownedPages) -> {
            JsonObject ownerJson = new JsonObject();
            ownerJson.addProperty("owner", owner.toString());
            JsonArray pageArray = new JsonArray();
            ownedPages.values().forEach(metadata -> {
                JsonObject page = new JsonObject();
                page.addProperty("page", metadata.storagePage());
                page.addProperty("uuid", metadata.pageUuid().toString());
                page.addProperty("name", metadata.pageName());
                pageArray.add(page);
            });
            ownerJson.add("pages", pageArray);
            owners.add(ownerJson);
        });
        root.add("owners", owners);

        JsonArray preferences = new JsonArray();
        sortPreferences.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    JsonObject preference = new JsonObject();
                    preference.addProperty("player", entry.getKey().toString());
                    preference.addProperty("field", entry.getValue().field());
                    preference.addProperty("direction", entry.getValue().direction());
                    preferences.add(preference);
                });
        root.add("sort_preferences", preferences);
        return GSON.toJson(root);
    }

    private boolean writeJson(String json) {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(FILE_NAME + ".tmp");
            try(Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
            moveReplacing(temporary, path);
            return true;
        }catch(IOException exception) {
            FrostorsCarpetAddition.LOGGER.error("Failed to save big ender chest page metadata", exception);
            return false;
        }
    }

    public synchronized void release(PageMetadata metadata) {
        NavigableMap<Integer, PageMetadata> owned = pages.get(metadata.owner());
        if(owned == null || !metadata.equals(owned.get(metadata.storagePage()))) return;
        owned.remove(metadata.storagePage());
        usedNames.remove(metadata.pageName());
        usedPageUuids.remove(metadata.pageUuid());
        if(owned.isEmpty()) pages.remove(metadata.owner());
        requestSave();
    }

    private void requestSave() {
        String json = toJson();
        ioExecutor.execute(() -> writeJson(json));
    }

    private void load() {
        if(!Files.isRegularFile(path)) return;
        try(Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if(root == null || root.get("format_version").getAsInt() != FORMAT_VERSION) {
                FrostorsCarpetAddition.LOGGER.warn(
                        "Ignoring incompatible big ender chest page metadata at {}",
                        path
                );
                return;
            }

            JsonArray owners = root.getAsJsonArray("owners");
            if(owners == null) return;
            Set<UUID> pageUuids = new HashSet<>();
            for(JsonElement ownerElement : owners) {
                JsonObject ownerJson = ownerElement.getAsJsonObject();
                UUID owner = UUID.fromString(ownerJson.get("owner").getAsString());
                NavigableMap<Integer, PageMetadata> owned = pages.computeIfAbsent(
                        owner,
                        ignored -> new TreeMap<>()
                );
                JsonArray pageArray = ownerJson.getAsJsonArray("pages");
                if(pageArray == null) continue;
                for(JsonElement pageElement : pageArray) {
                    JsonObject pageJson = pageElement.getAsJsonObject();
                    int storagePage = pageJson.get("page").getAsInt();
                    UUID pageUuid = UUID.fromString(pageJson.get("uuid").getAsString());
                    String pageName = pageJson.get("name").getAsString();
                    if(storagePage < 1
                            || pageUuid.equals(owner)
                            || !pageUuids.add(pageUuid)
                            || !usedNames.add(pageName)) {
                        FrostorsCarpetAddition.LOGGER.warn(
                                "Skipping invalid or duplicate fake page {} for {}",
                                storagePage,
                                owner
                        );
                        continue;
                    }
                    owned.put(
                            storagePage,
                            new PageMetadata(owner, storagePage, pageUuid, pageName)
                    );
                    usedPageUuids.add(pageUuid);
                    server.services().nameToIdCache().add(new NameAndId(pageUuid, pageName));
                }
            }

            JsonArray preferences = root.getAsJsonArray("sort_preferences");
            if(preferences == null) return;
            for(JsonElement preferenceElement : preferences) {
                JsonObject preferenceJson = preferenceElement.getAsJsonObject();
                UUID player = UUID.fromString(preferenceJson.get("player").getAsString());
                SortPreference preference = new SortPreference(
                        preferenceJson.get("field").getAsString(),
                        preferenceJson.get("direction").getAsString()
                );
                if(!preference.equals(SortPreference.DEFAULT)) {
                    sortPreferences.put(player, preference);
                }
            }
        }catch(IOException | RuntimeException exception) {
            FrostorsCarpetAddition.LOGGER.error(
                    "Failed to load big ender chest page metadata from {}",
                    path,
                    exception
            );
        }
    }

    private PageMetadata createMetadata(UUID owner, String ownerName, int storagePage) {
        String worldIdentity = server.getWorldPath(LevelResource.ROOT)
                .toAbsolutePath()
                .normalize()
                .toString();
        String pageCode = Integer.toString(storagePage, 36).toLowerCase(java.util.Locale.ROOT);
        if(pageCode.length() < 2) pageCode = "0" + pageCode;
        for(int salt = 0; salt < 65_536; salt++) {
            String digest = digest(worldIdentity + ':' + owner + ':' + storagePage + ':' + salt);
            String name = readablePageName(ownerName, pageCode, digest);
            UUID pageUuid = UUID.nameUUIDFromBytes(
                    ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)
            );
            if(!usedPageUuids.contains(pageUuid)
                    && isNameAvailable(name)
                    && server.getPlayerList().getPlayer(pageUuid) == null) {
                return new PageMetadata(owner, storagePage, pageUuid, name);
            }
        }
        throw new IllegalStateException("Unable to allocate a unique fake page profile name");
    }

    private static String readablePageName(String ownerName, String pageCode, String digest) {
        String base = "fec_p" + pageCode + '_';
        int ownerLength = 16 - base.length() - 4;
        if(ownerLength > 0) {
            return base + readableOwnerPrefix(ownerName, ownerLength) + digest.substring(0, 4);
        }
        int hashLength = Math.max(4, 16 - 4 - pageCode.length());
        return "fecp" + pageCode + digest.substring(0, hashLength);
    }

    private static String readableOwnerPrefix(String ownerName, int maxLength) {
        String sanitized = ownerName == null
                ? ""
                : ownerName.replaceAll("[^A-Za-z0-9_]", "");
        if(sanitized.isEmpty()) sanitized = "player";
        if(sanitized.length() >= maxLength) return sanitized.substring(0, maxLength);
        return (sanitized + "xxxxxxxxxxxxxxxx").substring(0, maxLength);
    }

    private boolean isNameAvailable(String name) {
        net.minecraft.server.level.ServerPlayer online = server.getPlayerList().getPlayer(name);
        boolean absentFromPlayerList = online == null || !online.getScoreboardName().equals(name);
        return !usedNames.contains(name) && absentFromPlayerList;
    }

    private static String digest(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for(byte value : hash) {
                result.append(Character.forDigit((value >>> 4) & 0xf, 16));
                result.append(Character.forDigit(value & 0xf, 16));
            }
            return result.toString();
        }catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
}
