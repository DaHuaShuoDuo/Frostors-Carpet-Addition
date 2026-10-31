package top.frostor.carpet;

import carpet.api.settings.Rule;
import carpet.api.settings.RuleCategory;
import top.frostor.carpet.playerladder.ClickMode;

/**
 * Carpet rules provided by this extension.
 *
 * <p>The rule name shown in {@code /carpet} is the field name, and rule lookups are case
 * sensitive, so keep the field names as-is. Every rule must have a
 * {@code carpet.rule.<field name>.name} and {@code carpet.rule.<field name>.desc} translation,
 * otherwise Carpet refuses to register it.</p>
 */
public class FrostorSettings {
    /* ---------- Player Ladder ---------- */

    /**
     * Master switch of the Player Ladder feature. Off by default.
     */
    @Rule(categories = RuleCategory.FEATURE)
    public static boolean playerLadder = false;

    /**
     * What a right click on an entity does: ride it, pick it up, or nothing.
     */
    @Rule(categories = RuleCategory.FEATURE)
    public static ClickMode playerLadderClickMode = ClickMode.RIDE;

    /**
     * Maximum number of stacked passengers considered when picking up an entity.
     */
    @Rule(categories = RuleCategory.FEATURE)
    public static int playerLadderPickUpLimit = 16;

    /**
     * Maximum stack height climbed when riding an entity.
     */
    @Rule(categories = RuleCategory.FEATURE)
    public static int playerLadderStepUpLimit = 16;

    /**
     * Whether non-player living entities can also be ridden or picked up.
     */
    @Rule(categories = RuleCategory.FEATURE)
    public static boolean playerLadderInteractWithAnyLiving = false;

    /**
     * Comma separated entity ids / tags (prefix with {@code #}) that can never be ridden or picked up.
     */
    @Rule(categories = RuleCategory.FEATURE)
    public static String playerLadderExcludedEntities =
            "minecraft:wither,minecraft:ender_dragon,minecraft:warden,minecraft:minecart,#minecraft:boat,#minecraft:dismounts_underwater";

    /**
     * Whether passengers are dismounted when the carrier changes game mode.
     */
    @Rule(categories = RuleCategory.FEATURE)
    public static boolean playerLadderDismountOnGameModeChange = false;

    /**
     * Whether 3 or more players may be stacked. When disabled only two players may ride each other.
     */
    @Rule(categories = RuleCategory.FEATURE)
    public static boolean playerLadderRideExtension = true;

    /**
     * Whether Carpet fake players can be ridden or picked up.
     */
    @Rule(categories = RuleCategory.FEATURE)
    public static boolean playerLadderRideFakePlayer = false;

    /**
     * Client side aiming fix that keeps riders selectable while stacked. Client only.
     */
    @Rule(categories = {RuleCategory.FEATURE, RuleCategory.CLIENT})
    public static boolean playerLadderClientInteractions = true;
}
