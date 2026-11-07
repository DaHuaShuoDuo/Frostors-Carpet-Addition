package top.frostor.carpet.playerladder;

/**
 * Selects which vanilla-visible input path activates Player Ladder interactions.
 */
public enum PlayerLadderInteractionMode {
    CARRY_ON,
    PLAYER_LADDER;

    public boolean matches(boolean shiftKeyDown, boolean mainHandEmpty, boolean offHandEmpty) {
        return switch (this) {
            case CARRY_ON -> shiftKeyDown && mainHandEmpty && offHandEmpty;
            case PLAYER_LADDER -> true;
        };
    }
}
