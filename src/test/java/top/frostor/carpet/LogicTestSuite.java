package top.frostor.carpet;

import top.frostor.carpet.bigenderchest.BigEnderChestCoreTest;
import top.frostor.carpet.bigenderchest.compat.QuickShulkerIntegrationTest;
import top.frostor.carpet.playerladder.CarryOnDismountStateTest;
import top.frostor.carpet.playerladder.PlayerLadderCommandTest;
import top.frostor.carpet.playerladder.PlayerLadderInteractionModeTest;
import top.frostor.carpet.playerladder.PlayerLadderSettingsTest;
import top.frostor.carpet.playerladder.PlayerStackTest;

public final class LogicTestSuite {
    private LogicTestSuite() {
    }

    public static void main(String[] args) throws Exception {
        CarryOnDismountStateTest.main(args);
        PlayerLadderCommandTest.main(args);
        PlayerLadderInteractionModeTest.main(args);
        PlayerLadderSettingsTest.main(args);
        PlayerStackTest.main(args);
        BigEnderChestCoreTest.main(args);
        QuickShulkerIntegrationTest.run();
    }
}
