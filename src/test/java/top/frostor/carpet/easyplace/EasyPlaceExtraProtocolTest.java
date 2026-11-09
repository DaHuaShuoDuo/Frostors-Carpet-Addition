package top.frostor.carpet.easyplace;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import top.frostor.carpet.easyplace.registry.EasyPlaceRegistryTest;

public final class EasyPlaceExtraProtocolTest {
    private EasyPlaceExtraProtocolTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EasyPlaceRegistryTest.run();

        int checks = EasyPlaceWireProtocolTest.run()
                + EasyPlaceMultifaceStateTest.run()
                + EasyPlaceHangingSignStateTest.run()
                + EasyPlaceQuantityStateTest.run()
                + EasyPlaceMushroomStateTest.run()
                + EasyPlaceRailStateTest.run()
                + EasyPlaceSpecialStateTest.run()
                + EasyPlaceDirectionalStateTest.run()
                + EasyPlaceAttachmentStateTest.run();

        System.out.println("EasyPlaceExtraProtocolTest passed " + checks + " checks");
    }
}
