package top.frostor.carpet.onlineprofile;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import top.frostor.carpet.FrostorsCarpetAddition;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves and caches Mojang profiles by username for online-mode login fallback.
 *
 * <p>This intentionally does not prove account ownership. It exists only for private servers
 * where the administrator accepts username-based identity trust.</p>
 */
public final class OnlineProfileFallback {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final long CACHE_REFRESH_MILLIS = Duration.ofHours(24).toMillis();
    private static final Object LOOKUP_LOCK = new Object();
    private static final ConcurrentHashMap<String, StoredProfile> PROFILES = new ConcurrentHashMap<>();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path cachePath;
    private static boolean initialized;

    private OnlineProfileFallback() {
    }

    public static void initialize() {
        if(initialized || !isDedicatedServer()) return;
        initialized = true;
        cachePath = FabricLoader.getInstance().getConfigDir()
                .resolve(FrostorsCarpetAddition.MOD_ID)
                .resolve("online-profile-cache.json");
        loadCache();
    }

    public static boolean isDedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }

    public static GameProfile resolve(String username) {
        if(!initialized || username == null || username.isBlank()) return null;

        String key = normalize(username);
        StoredProfile cached = PROFILES.get(key);
        if(isFreshWithTextures(cached)) return cached.toGameProfile();

        synchronized(LOOKUP_LOCK) {
            cached = PROFILES.get(key);
            if(isFreshWithTextures(cached)) return cached.toGameProfile();

            StoredProfile resolved = lookupProfile(username);
            if(resolved != null) {
                PROFILES.put(key, resolved);
                saveCache();
                return resolved.toGameProfile();
            }
            return cached == null ? null : cached.toGameProfile();
        }
    }

    public static void remember(GameProfile profile) {
        if(!initialized || profile == null || profile.id() == null || profile.name() == null) return;

        synchronized(LOOKUP_LOCK) {
            String key = normalize(profile.name());
            StoredProfile previous = PROFILES.get(key);
            StoredProfile current = StoredProfile.fromGameProfile(profile);

            // A bare UUID lookup must not replace a cached profile that already contains textures.
            if(current.properties.isEmpty() && previous != null && previous.hasTextures()) return;

            PROFILES.put(key, current);
            saveCache();
        }
    }

    private static boolean isFreshWithTextures(StoredProfile profile) {
        return profile != null
                && profile.hasTextures()
                && System.currentTimeMillis() - profile.resolvedAt < CACHE_REFRESH_MILLIS;
    }

    private static StoredProfile lookupProfile(String username) {
        try {
            String encodedName = URLEncoder.encode(username, StandardCharsets.UTF_8);
            JsonObject basic = getJson(URI.create(
                    "https://api.mojang.com/users/profiles/minecraft/" + encodedName
            ));
            if(basic == null || !basic.has("id")) return null;

            UUID id = parseUuid(basic.get("id").getAsString());
            String canonicalName = basic.has("name") ? basic.get("name").getAsString() : username;
            JsonObject full = getJson(URI.create(
                    "https://sessionserver.mojang.com/session/minecraft/profile/"
                            + id.toString().replace("-", "")
                            + "?unsigned=false"
            ));

            ArrayList<StoredProperty> properties = new ArrayList<>();
            if(full != null && full.has("properties") && full.get("properties").isJsonArray()) {
                JsonArray values = full.getAsJsonArray("properties");
                for(JsonElement element : values) {
                    if(!element.isJsonObject()) continue;
                    JsonObject property = element.getAsJsonObject();
                    if(!property.has("name") || !property.has("value")) continue;
                    String signature = property.has("signature") && !property.get("signature").isJsonNull()
                            ? property.get("signature").getAsString()
                            : null;
                    properties.add(new StoredProperty(
                            property.get("name").getAsString(),
                            property.get("value").getAsString(),
                            signature
                    ));
                }
            }

            if(full != null && full.has("name")) canonicalName = full.get("name").getAsString();
            return new StoredProfile(id.toString(), canonicalName, System.currentTimeMillis(), properties);
        }catch(IOException | InterruptedException | RuntimeException e) {
            if(e instanceof InterruptedException) Thread.currentThread().interrupt();
            StoredProfile cached = PROFILES.get(normalize(username));
            FrostorsCarpetAddition.LOGGER.warn(
                    "Could not resolve Mojang profile for '{}'{}",
                    username,
                    cached == null ? "" : "; using stale cached profile",
                    e
            );
            return null;
        }
    }

    private static JsonObject getJson(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", FrostorsCarpetAddition.MOD_ID + "/" + FrostorsCarpetAddition.VERSION)
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if(response.statusCode() == 204 || response.statusCode() == 404) return null;
        if(response.statusCode() != 200) {
            throw new IOException("Mojang API returned HTTP " + response.statusCode());
        }
        JsonElement parsed = GSON.fromJson(response.body(), JsonElement.class);
        return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
    }

    private static UUID parseUuid(String value) {
        if(value.indexOf('-') >= 0) return UUID.fromString(value);
        if(value.length() != 32) throw new IllegalArgumentException("Invalid Mojang UUID length");
        return UUID.fromString(
                value.substring(0, 8)
                        + "-" + value.substring(8, 12)
                        + "-" + value.substring(12, 16)
                        + "-" + value.substring(16, 20)
                        + "-" + value.substring(20)
        );
    }

    private static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private static void loadCache() {
        if(cachePath == null || !Files.isRegularFile(cachePath)) return;
        try {
            StoredCache stored = GSON.fromJson(Files.readString(cachePath), StoredCache.class);
            if(stored == null || stored.profiles == null) return;
            for(StoredProfile profile : stored.profiles) {
                if(profile == null || profile.name == null || profile.id == null) continue;
                profile.properties = profile.properties == null ? new ArrayList<>() : profile.properties;
                PROFILES.put(normalize(profile.name), profile);
            }
        }catch(IOException | RuntimeException e) {
            FrostorsCarpetAddition.LOGGER.warn("Could not read online profile cache '{}'", cachePath, e);
        }
    }

    private static void saveCache() {
        if(cachePath == null) return;
        try {
            Files.createDirectories(cachePath.getParent());
            StoredCache stored = new StoredCache();
            stored.profiles = new ArrayList<>(PROFILES.values());
            Path temporary = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(stored));
            try {
                Files.move(temporary, cachePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }catch(AtomicMoveNotSupportedException e) {
                Files.move(temporary, cachePath, StandardCopyOption.REPLACE_EXISTING);
            }
        }catch(IOException e) {
            FrostorsCarpetAddition.LOGGER.warn("Could not save online profile cache '{}'", cachePath, e);
        }
    }

    private static final class StoredCache {
        private List<StoredProfile> profiles = new ArrayList<>();
    }

    private static final class StoredProfile {
        private String id;
        private String name;
        private long resolvedAt;
        private List<StoredProperty> properties = new ArrayList<>();

        private StoredProfile() {
        }

        private StoredProfile(String id, String name, long resolvedAt, List<StoredProperty> properties) {
            this.id = id;
            this.name = name;
            this.resolvedAt = resolvedAt;
            this.properties = properties;
        }

        private static StoredProfile fromGameProfile(GameProfile profile) {
            ArrayList<StoredProperty> properties = new ArrayList<>();
            for(Property property : profile.properties().values()) {
                properties.add(new StoredProperty(property.name(), property.value(), property.signature()));
            }
            return new StoredProfile(
                    profile.id().toString(),
                    profile.name(),
                    System.currentTimeMillis(),
                    properties
            );
        }

        private boolean hasTextures() {
            return properties.stream().anyMatch(property -> "textures".equals(property.name));
        }

        private GameProfile toGameProfile() {
            Multimap<String, Property> values = LinkedHashMultimap.create();
            for(StoredProperty property : properties) {
                values.put(property.name, new Property(property.name, property.value, property.signature));
            }
            return new GameProfile(UUID.fromString(id), name, new PropertyMap(values));
        }
    }

    private static final class StoredProperty {
        private String name;
        private String value;
        private String signature;

        private StoredProperty() {
        }

        private StoredProperty(String name, String value, String signature) {
            this.name = name;
            this.value = value;
            this.signature = signature;
        }
    }
}
