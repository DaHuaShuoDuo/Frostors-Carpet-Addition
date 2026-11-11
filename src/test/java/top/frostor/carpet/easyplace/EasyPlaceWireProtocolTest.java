package top.frostor.carpet.easyplace;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

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
        CompoundTag yCapability = EasyPlaceExtraServer.capabilityData(true, "test");
        test.check(
                EasyPlaceExtraServer.supportsYPrecision(yCapability),
                "server capability data must advertise eight-bit Y precision"
        );
        CompoundTag disabledYCapability = EasyPlaceExtraServer.capabilityData(false, "test");
        test.check(
                !EasyPlaceExtraServer.supportsYPrecision(disabledYCapability),
                "disabled server capabilities must not advertise eight-bit Y precision"
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
        int wallMatchMask = (1 << Direction.NORTH.ordinal())
                | (1 << Direction.UP.ordinal());
        int wallAuxiliary = EasyPlaceExtraProtocol.WALL_UPDATE_SUPPRESSION
                | (wallMatchMask << 11);
        test.check(
                EasyPlaceExtraProtocol.wallNeighborMatchMask(wallAuxiliary) == wallMatchMask,
                "wall neighbor match bits must survive the auxiliary field"
        );
        test.check(
                EasyPlaceExtraProtocol.wallNeighborMatchMask(-1) == 0,
                "an absent Z field must not mark wall neighbors as matching"
        );
        int observerAuxiliary = EasyPlaceExtraProtocol.OBSERVER_UPDATE_SUPPRESSION;
        test.check(
                EasyPlaceExtraProtocol.requestsObserverUpdateSuppression(observerAuxiliary)
                        && !EasyPlaceExtraProtocol.requestsObserverUpdateSuppression(-1),
                "observer suppression must require an explicit positive auxiliary flag"
        );
        int tntAuxiliary = EasyPlaceExtraProtocol.TNT_UPDATE_SUPPRESSION;
        test.check(
                EasyPlaceExtraProtocol.requestsTntUpdateSuppression(tntAuxiliary)
                        && !EasyPlaceExtraProtocol.requestsTntUpdateSuppression(-1),
                "TNT suppression must require an explicit positive auxiliary flag"
        );
        int railNeighborMask = (1 << Direction.UP.ordinal())
                | (1 << Direction.NORTH.ordinal());
        int railAuxiliary = EasyPlaceExtraProtocol.RAIL_UPDATE_SUPPRESSION
                | EasyPlaceExtraProtocol.RAIL_STATE_UPDATE_SUPPRESSION
                | (railNeighborMask << 19);
        test.check(
                EasyPlaceExtraProtocol.railNeighborMatchMask(railAuxiliary) == railNeighborMask
                        && EasyPlaceExtraProtocol.requestsRailStateUpdateSuppression(railAuxiliary)
                        && EasyPlaceExtraProtocol.railNeighborMatchMask(-1) == 0,
                "rail neighbor and state protection bits must survive the auxiliary field"
        );
        int detectorRailShape = 0x5;
        int poweredDetectorRail = detectorRailShape
                | EasyPlaceExtraProtocol.RAIL_POWERED_MASK;
        test.check(
                (poweredDetectorRail & 0x7) == detectorRailShape
                        && (poweredDetectorRail
                        & EasyPlaceExtraProtocol.RAIL_POWERED_MASK) != 0,
                "detector rail power must use a raw bit independent of shape"
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
