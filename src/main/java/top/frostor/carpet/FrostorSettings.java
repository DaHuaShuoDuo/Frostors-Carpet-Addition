package top.frostor.carpet;

import carpet.api.settings.Rule;
import carpet.api.settings.RuleCategory;

/**
 * Carpet rules provided by this extension.
 *
 * <p>The rule name shown in {@code /carpet} is the field name, and rule lookups are case
 * sensitive, so keep the field names as-is. Every rule must have a
 * {@code carpet.rule.<field name>.name} and {@code carpet.rule.<field name>.desc} translation,
 * otherwise Carpet refuses to register it.</p>
 */
public class FrostorSettings {
    public static final String FCA = "fca";

    /* ---------- Big Ender Chest ---------- */

    /**
     * Server-side large ender chest with a vanilla 6x9 menu and optional client-tool protocol.
     */
    @Rule(categories = {RuleCategory.FEATURE, FCA})
    public static boolean bigEnderChest = false;

    /**
     * Maximum number of extra fake-player storage pages, excluding the player's vanilla page 0.
     */
    @Rule(categories = {RuleCategory.FEATURE, FCA})
    public static int bigEnderChestPageLimit = 64;

    /**
     * Whether pages touched by real storage mutations should be backed by live fake players.
     */
    @Rule(categories = {RuleCategory.FEATURE, FCA})
    public static boolean bigEnderChestLiveFakePlayers = false;

    /* ---------- Easy Place V2-Extra ---------- */

    /**
     * Server-side V2-Extra easy-place handshake and block-state restoration.
     */
    @Rule(categories = {RuleCategory.FEATURE, FCA})
    public static boolean easyPlaceProtocolExtra = false;

    /* ---------- Online Profile Fallback ---------- */

    /**
     * Trusts usernames during online-mode login and resolves their Mojang UUID and textures.
     *
     * <p>This does not verify account ownership and must only be used on private servers.</p>
     */
    @Rule(categories = {RuleCategory.FEATURE, FCA})
    public static boolean onlineProfileFallback = false;

    /* ---------- Player Ladder ---------- */

    /**
     * Master switch of the Player Ladder feature. Off by default.
     */
    @Rule(categories = {RuleCategory.FEATURE, FCA})
    public static boolean playerLadder = false;
}
