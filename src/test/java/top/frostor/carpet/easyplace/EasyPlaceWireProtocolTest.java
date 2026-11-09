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
        test.check(
                (EasyPlaceExtraProtocol.RAIL_UPDATE_SUPPRESSION
                        & EasyPlaceExtraProtocol.CRAFTER_SLOT_MASK) == 0
                        && (EasyPlaceExtraProtocol.WALL_UPDATE_SUPPRESSION
                        & EasyPlaceExtraProtocol.CRAFTER_SLOT_MASK) == 0,
                "update suppression flags must not overlap crafter slot bits"
        );
        int updateFlags = EasyPlaceExtraProtocol.RAIL_UPDATE_SUPPRESSION
                | EasyPlaceExtraProtocol.WALL_UPDATE_SUPPRESSION
                | 0x1ab;
        test.check(
                EasyPlaceExtraProtocol.requestsRailUpdateSuppression(updateFlags)
                        && EasyPlaceExtraProtocol.requestsWallUpdateSuppression(updateFlags),
                "both update suppression flags must survive alongside a crafter mask"
        );
        test.check(
                !EasyPlaceExtraProtocol.requestsRailUpdateSuppression(-1)
                        && !EasyPlaceExtraProtocol.requestsWallUpdateSuppression(-1),
                "an absent Z field must not enable either update suppression flag"
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
