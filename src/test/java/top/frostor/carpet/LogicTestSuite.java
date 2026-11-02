package top.frostor.carpet;

import top.frostor.carpet.bigenderchest.BigEnderChestCoreTest;
import top.frostor.carpet.playerladder.PlayerStackTest;

public final class LogicTestSuite {
    private LogicTestSuite() {
    }

    public static void main(String[] args) {
        PlayerStackTest.main(args);
        BigEnderChestCoreTest.main(args);
    }
}
