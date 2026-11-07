package top.frostor.carpet.playerladder;

import java.util.Locale;

public final class PlayerLadderInteractionModeTest {
    private static int checks;

    private PlayerLadderInteractionModeTest() {
    }

    public static void main(String[] args) {
        allowsTheLegacyDirectClickPath();
        requiresShiftWithBothHandsEmptyForCarryOn();
        usesLowercaseCarpetValues();

        System.out.println("PlayerLadderInteractionModeTest passed " + checks + " checks");
    }

    private static void allowsTheLegacyDirectClickPath() {
        check(PlayerLadderInteractionMode.PLAYER_LADDER.matches(false, false, false),
                "the legacy path must keep accepting a direct right click");
        check(PlayerLadderInteractionMode.PLAYER_LADDER.matches(true, true, true),
                "the legacy path must not become dependent on the carry-on gesture");
    }

    private static void requiresShiftWithBothHandsEmptyForCarryOn() {
        check(PlayerLadderInteractionMode.CARRY_ON.matches(true, true, true),
                "carry-on must match shift with both hands empty");
        check(!PlayerLadderInteractionMode.CARRY_ON.matches(false, true, true),
                "carry-on must reject a non-shifting click");
        check(!PlayerLadderInteractionMode.CARRY_ON.matches(true, false, true),
                "carry-on must reject a full main hand");
        check(!PlayerLadderInteractionMode.CARRY_ON.matches(true, true, false),
                "carry-on must reject a full off hand");
    }

    private static void usesLowercaseCarpetValues() {
        check(PlayerLadderInteractionMode.PLAYER_LADDER.name().toLowerCase(Locale.ROOT).equals("player_ladder"),
                "the legacy Carpet value must be player_ladder");
        check(PlayerLadderInteractionMode.CARRY_ON.name().toLowerCase(Locale.ROOT).equals("carry_on"),
                "the alternate Carpet value must be carry_on");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }
}
