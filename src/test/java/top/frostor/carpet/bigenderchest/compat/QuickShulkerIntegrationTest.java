package top.frostor.carpet.bigenderchest.compat;

import net.kyrptonaught.quickshulker.api.QuickOpenableRegistry;
import net.kyrptonaught.quickshulker.api.QuickShulkerData;
import net.minecraft.world.item.Items;

public final class QuickShulkerIntegrationTest {
    private QuickShulkerIntegrationTest() {
    }

    public static void run() {
        QuickShulkerIntegration.registerProvider();
        QuickShulkerData provider = QuickOpenableRegistry.getQuickie(Items.ENDER_CHEST);
        check(provider instanceof QuickShulkerData.QuickEnderData,
                "ender chest must use Quick Shulker's ender data provider");
        check(provider.openConsumer != null,
                "the FCA provider must install a quick-open action");
        check(provider.supportsBundleing,
                "quick container actions must retain ender chest support");
        check(provider.ignoreSingleStackCheck,
                "stacked ender chests must remain quick-openable");
        System.out.println("QuickShulkerIntegrationTest passed 4 checks");
    }

    private static void check(boolean condition, String message) {
        if(!condition) throw new AssertionError(message);
    }
}
