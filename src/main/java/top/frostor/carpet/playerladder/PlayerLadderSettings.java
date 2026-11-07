package top.frostor.carpet.playerladder;

import carpet.api.settings.Rule;
import carpet.api.settings.RuleCategory;
import carpet.api.settings.SettingsManager;

/**
 * Player Ladder settings managed by a dedicated Carpet settings manager.
 *
 * <p>The generated command tree is registered as {@code /playerladder}; the feature master
 * switch remains the Carpet rule {@code playerLadder}.</p>
 */
public final class PlayerLadderSettings {
    public static final String CATEGORY = "playerladder";
    public static final SettingsManager MANAGER =
            new SettingsManager("1", "playerladder", "Player Ladder");

    public static final PlayerLadderInteractionMode DEFAULT_INTERACTION_MODE =
            PlayerLadderInteractionMode.PLAYER_LADDER;
    public static final ClickMode DEFAULT_CLICK_MODE = ClickMode.RIDE;
    public static final int DEFAULT_PICKUP_DEPTH_LIMIT = 16;
    public static final int DEFAULT_RIDE_DEPTH_LIMIT = 16;
    public static final boolean DEFAULT_ALLOW_NON_PLAYERS = false;
    public static final String DEFAULT_IGNORED_ENTITIES =
            "minecraft:wither,minecraft:ender_dragon,minecraft:warden,minecraft:minecart,#minecraft:boat,#minecraft:dismounts_underwater";
    public static final boolean DEFAULT_DISMOUNT_ON_GAME_MODE_CHANGE = false;
    public static final boolean DEFAULT_ALLOW_RIDER_STACKING = true;
    public static final boolean DEFAULT_ALLOW_FAKE_PLAYERS = false;

    @Rule(categories = {RuleCategory.FEATURE, CATEGORY})
    public static PlayerLadderInteractionMode interactionMode = DEFAULT_INTERACTION_MODE;

    @Rule(categories = {RuleCategory.FEATURE, CATEGORY})
    public static ClickMode clickMode = DEFAULT_CLICK_MODE;

    @Rule(categories = {RuleCategory.FEATURE, CATEGORY})
    public static int pickupDepthLimit = DEFAULT_PICKUP_DEPTH_LIMIT;

    @Rule(categories = {RuleCategory.FEATURE, CATEGORY})
    public static int rideDepthLimit = DEFAULT_RIDE_DEPTH_LIMIT;

    @Rule(categories = {RuleCategory.FEATURE, CATEGORY})
    public static boolean allowNonPlayers = DEFAULT_ALLOW_NON_PLAYERS;

    @Rule(categories = {RuleCategory.FEATURE, CATEGORY})
    public static String ignoredEntities = DEFAULT_IGNORED_ENTITIES;

    @Rule(categories = {RuleCategory.FEATURE, CATEGORY})
    public static boolean dismountOnGameModeChange = DEFAULT_DISMOUNT_ON_GAME_MODE_CHANGE;

    @Rule(categories = {RuleCategory.FEATURE, CATEGORY})
    public static boolean allowRiderStacking = DEFAULT_ALLOW_RIDER_STACKING;

    @Rule(categories = {RuleCategory.FEATURE, CATEGORY})
    public static boolean allowFakePlayers = DEFAULT_ALLOW_FAKE_PLAYERS;

    private static boolean initialized;

    private PlayerLadderSettings() {
    }

    public static synchronized void initialize() {
        if(initialized) return;
        MANAGER.parseSettingsClass(PlayerLadderSettings.class);
        initialized = true;
    }
}
