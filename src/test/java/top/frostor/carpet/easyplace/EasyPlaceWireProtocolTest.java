package top.frostor.carpet.easyplace;

public final class EasyPlaceWireProtocolTest extends EasyPlaceTestSupport {
    private EasyPlaceWireProtocolTest() {
    }

    public static int run() {
        EasyPlaceWireProtocolTest test = new EasyPlaceWireProtocolTest();

        test.check(
                EasyPlaceExtraPayload.TYPE.id().equals(EasyPlaceExtraPayload.CHANNEL),
                "public payload type must use the shared protocol channel"
        );
        test.check(
                EasyPlaceExtraPayload.PROTOCOL_VERSION == 1,
                "public payload API must preserve protocol version 1"
        );
        for(int raw = 0; raw < 0b1000_0000; raw++) {
            int extra = EasyPlaceExtraProtocol.toExtraValue(raw);
            test.check(EasyPlaceExtraProtocol.isExtra(extra), "every encoded value must carry the marker");
            test.check(
                    EasyPlaceExtraProtocol.toRawValue(extra) == raw,
                    "V2-Extra values must round trip through their marker bit"
            );
        }

        return test.checks();
    }
}
