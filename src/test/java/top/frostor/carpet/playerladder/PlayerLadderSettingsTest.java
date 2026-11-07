package top.frostor.carpet.playerladder;

public final class PlayerLadderSettingsTest {
    private static int checks;

    private PlayerLadderSettingsTest() {
    }

    public static void main(String[] args) {
        check(PlayerLadderSettings.interactionMode == PlayerLadderInteractionMode.PLAYER_LADDER,
                "the default interaction mode must remain player_ladder");
        check(PlayerLadderSettings.clickMode == ClickMode.RIDE,
                "the default click mode must remain ride");
        check(PlayerLadderSettings.pickupDepthLimit == 16,
                "the default pickup depth must remain 16");
        check(PlayerLadderSettings.rideDepthLimit == 16,
                "the default ride depth must remain 16");
        check(!PlayerLadderSettings.allowNonPlayers,
                "non-player targets must remain disabled by default");
        check(PlayerLadderSettings.ignoredEntities.equals(PlayerLadderSettings.DEFAULT_IGNORED_ENTITIES),
                "the default ignored-entity list must be preserved");
        check(!PlayerLadderSettings.dismountOnGameModeChange,
                "game-mode-change dismounting must remain disabled by default");
        check(PlayerLadderSettings.allowRiderStacking,
                "rider stacking must remain enabled by default");
        check(!PlayerLadderSettings.allowFakePlayers,
                "Carpet fake-player interaction must remain disabled by default");

        System.out.println("PlayerLadderSettingsTest passed " + checks + " checks");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }
}
