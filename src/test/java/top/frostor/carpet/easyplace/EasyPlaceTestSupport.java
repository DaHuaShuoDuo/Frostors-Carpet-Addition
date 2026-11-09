package top.frostor.carpet.easyplace;

abstract class EasyPlaceTestSupport {
    private int checks;

    protected final void check(boolean condition, String message) {
        checks++;
        if(!condition) {
            throw new AssertionError(message);
        }
    }

    protected final int checks() {
        return checks;
    }
}
