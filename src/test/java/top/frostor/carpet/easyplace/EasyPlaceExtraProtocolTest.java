package top.frostor.carpet.easyplace;

public final class EasyPlaceExtraProtocolTest {
    private static int checks;

    private EasyPlaceExtraProtocolTest() {
    }

    public static void main(String[] args) {
        check(
                EasyPlaceExtraPayload.TYPE.id().equals(EasyPlaceExtraPayload.CHANNEL),
                "public payload type must use the shared protocol channel"
        );
        check(
                EasyPlaceExtraPayload.PROTOCOL_VERSION == 1,
                "public payload API must preserve protocol version 1"
        );
        for(int raw = 0; raw < 0b1000_0000; raw++) {
            int extra = EasyPlaceExtraProtocol.toExtraValue(raw);
            check(EasyPlaceExtraProtocol.isExtra(extra), "every encoded value must carry the marker");
            check(
                    EasyPlaceExtraProtocol.toRawValue(extra) == raw,
                    "V2-Extra values must round trip through their marker bit"
            );
        }
        System.out.println("EasyPlaceExtraProtocolTest passed " + checks + " checks");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }
}
