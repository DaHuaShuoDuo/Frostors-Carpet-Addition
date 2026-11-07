package top.frostor.carpet.playerladder;

/** Keeps a successful carry-on Shift press from being reused as a dismount press. */
public final class CarryOnDismountState {
    private boolean awaitingRelease;

    public void onSuccessfulInteraction() {
        awaitingRelease = true;
    }

    public boolean shouldDismount(boolean shiftKeyDown) {
        if(awaitingRelease) {
            if(!shiftKeyDown) awaitingRelease = false;
            return false;
        }
        return shiftKeyDown;
    }

    public boolean isAwaitingRelease() {
        return awaitingRelease;
    }
}
