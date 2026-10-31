package top.frostor.carpet;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.frostor.carpet.playerladder.PlayerLadder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * Entry point of Frostor's Carpet Addition.
 *
 * <p>Carpet 26.x no longer discovers extensions through an entrypoint, so the extension has to be
 * registered manually from a {@code main} entrypoint. Carpet's own {@code server}/{@code client}
 * entrypoints run after every {@code main} entrypoint, which guarantees that
 * {@link #onGameStarted()} is still called afterwards.</p>
 */
public class FrostorsCarpetAddition implements ModInitializer, CarpetExtension {
    public static final String MOD_ID = "frostors-carpet-addition";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CarpetServer.manageExtension(this);
        PlayerLadder.register();
    }

    @Override
    public void onGameStarted() {
        CarpetServer.settingsManager.parseSettingsClass(FrostorSettings.class);
    }

    @Override
    public String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
    }

    @Override
    public Map<String, String> canHasTranslations(String lang) {
        String path = String.format("assets/%s/lang/%s.json", MOD_ID, lang);
        InputStream stream = FrostorsCarpetAddition.class.getClassLoader().getResourceAsStream(path);
        if(stream == null) return Collections.emptyMap();

        try(InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Map<String, String> translations = new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
            return translations == null ? Collections.emptyMap() : translations;
        }catch(IOException e) {
            LOGGER.warn("Failed to read translations for '{}'", lang, e);
            return Collections.emptyMap();
        }
    }
}
