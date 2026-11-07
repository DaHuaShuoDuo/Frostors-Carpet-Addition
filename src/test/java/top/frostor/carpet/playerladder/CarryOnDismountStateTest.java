package top.frostor.carpet.playerladder;

public final class CarryOnDismountStateTest {
    private static int checks;

    private CarryOnDismountStateTest() {
    }

    public static void main(String[] args) {
        heldShiftDoesNotDismountOnTheNextTick();
        nextShiftPressAfterReleaseDismounts();
        legacyDismountDoesNotRequireAnExtraPress();

        System.out.println("CarryOnDismountStateTest passed " + checks + " checks");
    }

    private static void heldShiftDoesNotDismountOnTheNextTick() {
        CarryOnDismountState state = new CarryOnDismountState();
        state.onSuccessfulInteraction();

        check(!state.shouldDismount(true), "the tick immediately after a carry-on interaction must keep the passenger");
        check(!state.shouldDismount(true), "holding the same Shift press must keep the passenger on later ticks");
    }

    private static void nextShiftPressAfterReleaseDismounts() {
        CarryOnDismountState state = new CarryOnDismountState();
        state.onSuccessfulInteraction();

        check(!state.shouldDismount(false), "releasing Shift alone must not dismount");
        check(!state.isAwaitingRelease(), "releasing Shift must end carry-on protection");
        check(state.shouldDismount(true), "the next Shift press after release must dismount");
        check(state.shouldDismount(true), "holding the dismount Shift press must retain legacy behavior");
    }

    private static void legacyDismountDoesNotRequireAnExtraPress() {
        CarryOnDismountState state = new CarryOnDismountState();

        check(state.shouldDismount(true), "a Shift press without carry-on protection must dismount immediately");
        check(!state.shouldDismount(false), "not pressing Shift must not dismount");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }
}
