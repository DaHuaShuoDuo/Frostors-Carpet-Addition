package top.frostor.carpet.easyplace;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

public final class EasyPlaceHandshakeTest extends EasyPlaceTestSupport {
    private EasyPlaceHandshakeTest() {
    }

    public static int run() {
        EasyPlaceHandshakeTest test = new EasyPlaceHandshakeTest();
        test.checkAckPayloadShape();
        test.checkHelloValidation();
        test.checkYPrecisionRequiresValidHello();
        test.checkRelativeAuxCapability();
        return test.checks();
    }

    private void checkAckPayloadShape() {
        CompoundTag ack = EasyPlaceExtraServer.capabilityData(true, "test");
        check(
                EasyPlaceExtraServer.acceptsHello(ack),
                "an enabled capability payload must contain the complete base handshake"
        );
        check(
                !EasyPlaceExtraServer.acceptsHello(
                        EasyPlaceExtraServer.capabilityData(false, "test")
                ),
                "a disabled capability payload must not validate as a HELLO"
        );
        check(
                EasyPlaceExtraServer.supportsYPrecision(ack),
                "the enabled ACK must advertise the y8 capability"
        );
        check(
                ack.getIntOr(EasyPlaceExtraServer.NBT_PISTON_PROTECTION_BITS, -1)
                        == EasyPlaceExtraProtocol.PISTON_PROTECTION_BITS,
                "the ACK must declare the 12-bit piston mask width"
        );
        check(
                ack.getBooleanOr(EasyPlaceExtraServer.CAPABILITY_PISTON_PROTECTION, false),
                "the ACK must advertise piston protection"
        );
        check(
                ack.getBooleanOr(EasyPlaceExtraServer.CAPABILITY_PACKET_ANCHOR, false)
                        && ack.getBooleanOr(
                        EasyPlaceExtraServer.CAPABILITY_EASY_PLACE_V2_EXTRA,
                        false
                ),
                "the ACK must advertise the packet anchor explicitly"
        );
    }

    private void checkHelloValidation() {
        check(
                EasyPlaceExtraServer.acceptsHello(hello()),
                "a complete client HELLO must be accepted"
        );

        CompoundTag noVersion = hello();
        noVersion.remove(EasyPlaceExtraServer.NBT_PROTOCOL_VERSION);
        check(
                !EasyPlaceExtraServer.acceptsHello(noVersion),
                "a HELLO without protocol_version must be rejected"
        );

        CompoundTag wrongVersion = hello();
        wrongVersion.putInt(EasyPlaceExtraServer.NBT_PROTOCOL_VERSION, 2);
        check(
                !EasyPlaceExtraServer.acceptsHello(wrongVersion),
                "a HELLO from a different protocol version must be rejected"
        );

        CompoundTag noBoolean = hello();
        noBoolean.remove(EasyPlaceExtraServer.CAPABILITY_EASY_PLACE_V2_EXTRA);
        check(
                !EasyPlaceExtraServer.acceptsHello(noBoolean),
                "a HELLO without the base capability boolean must be rejected"
        );

        CompoundTag falseBoolean = hello();
        falseBoolean.putBoolean(EasyPlaceExtraServer.CAPABILITY_EASY_PLACE_V2_EXTRA, false);
        check(
                !EasyPlaceExtraServer.acceptsHello(falseBoolean),
                "a HELLO with a disabled base capability must be rejected"
        );

        CompoundTag noListEntry = hello();
        noListEntry.put(EasyPlaceExtraServer.NBT_CAPABILITIES, new ListTag());
        check(
                !EasyPlaceExtraServer.acceptsHello(noListEntry),
                "the base capability must also appear in the capabilities list"
        );

        CompoundTag legacyAnchor = hello();
        legacyAnchor.put(
                EasyPlaceExtraServer.NBT_CAPABILITIES,
                listOf(EasyPlaceExtraServer.CAPABILITY_EASY_PLACE_V2_EXTRA)
        );
        check(
                !EasyPlaceExtraServer.acceptsHello(legacyAnchor),
                "a client that does not declare the packet anchor contract must be rejected"
        );

        CompoundTag anchorListOnly = hello();
        anchorListOnly.remove(EasyPlaceExtraServer.CAPABILITY_PACKET_ANCHOR);
        check(
                !EasyPlaceExtraServer.acceptsHello(anchorListOnly),
                "the packet anchor must also appear as an explicit boolean"
        );

        CompoundTag anchorBooleanOnly = hello();
        anchorBooleanOnly.put(
                EasyPlaceExtraServer.NBT_CAPABILITIES,
                listOf(EasyPlaceExtraServer.CAPABILITY_EASY_PLACE_V2_EXTRA)
        );
        anchorBooleanOnly.putBoolean(EasyPlaceExtraServer.CAPABILITY_PACKET_ANCHOR, true);
        check(
                !EasyPlaceExtraServer.acceptsHello(anchorBooleanOnly),
                "the packet anchor must also appear in the capabilities list"
        );

        check(
                !EasyPlaceExtraServer.acceptsHello(new CompoundTag()),
                "an empty hand-built HELLO must be rejected"
        );
    }

    private void checkYPrecisionRequiresValidHello() {
        CompoundTag incomplete = new CompoundTag();
        incomplete.putInt(
                EasyPlaceExtraServer.NBT_Y_PRECISION_BITS,
                EasyPlaceExtraProtocol.Y_PRECISION_BITS
        );
        incomplete.putBoolean(EasyPlaceExtraServer.CAPABILITY_Y_PRECISION, true);
        ListTag capabilities = new ListTag();
        capabilities.add(StringTag.valueOf(EasyPlaceExtraServer.CAPABILITY_Y_PRECISION));
        incomplete.put(EasyPlaceExtraServer.NBT_CAPABILITIES, capabilities);
        check(
                !EasyPlaceExtraServer.supportsYPrecision(incomplete),
                "y8 fields alone must not grant capabilities without a valid base hello"
        );

        CompoundTag complete = hello();
        complete.putInt(
                EasyPlaceExtraServer.NBT_Y_PRECISION_BITS,
                EasyPlaceExtraProtocol.Y_PRECISION_BITS
        );
        complete.putBoolean(EasyPlaceExtraServer.CAPABILITY_Y_PRECISION, true);
        ListTag completeCapabilities = new ListTag();
        completeCapabilities.add(
                StringTag.valueOf(EasyPlaceExtraServer.CAPABILITY_EASY_PLACE_V2_EXTRA)
        );
        completeCapabilities.add(
                StringTag.valueOf(EasyPlaceExtraServer.CAPABILITY_PACKET_ANCHOR)
        );
        completeCapabilities.add(
                StringTag.valueOf(EasyPlaceExtraServer.CAPABILITY_Y_PRECISION)
        );
        complete.put(EasyPlaceExtraServer.NBT_CAPABILITIES, completeCapabilities);
        check(
                EasyPlaceExtraServer.supportsYPrecision(complete),
                "a valid HELLO advertising y8 must enable the y8 capability"
        );
    }

    /**
     * The relative-aux layout is optional: a client that does not declare it keeps the legacy
     * fixed-origin field, and a half-declared capability must not be honoured either.
     */
    private void checkRelativeAuxCapability() {
        check(
                EasyPlaceExtraServer.supportsRelativeAux(hello(true)),
                "a HELLO that declares the capability in both places must enable it"
        );
        check(
                !EasyPlaceExtraServer.supportsRelativeAux(hello(false)),
                "a HELLO without the capability must stay on the legacy layout"
        );

        CompoundTag booleanOnly = hello(true);
        booleanOnly.put(
                EasyPlaceExtraServer.NBT_CAPABILITIES,
                listOf(
                        EasyPlaceExtraServer.CAPABILITY_EASY_PLACE_V2_EXTRA,
                        EasyPlaceExtraServer.CAPABILITY_PACKET_ANCHOR
                )
        );
        check(
                !EasyPlaceExtraServer.supportsRelativeAux(booleanOnly),
                "the capability must also appear in the capabilities list"
        );

        CompoundTag listOnly = hello(false);
        listOnly.put(
                EasyPlaceExtraServer.NBT_CAPABILITIES,
                listOf(
                        EasyPlaceExtraServer.CAPABILITY_EASY_PLACE_V2_EXTRA,
                        EasyPlaceExtraServer.CAPABILITY_PACKET_ANCHOR,
                        EasyPlaceExtraServer.CAPABILITY_RELATIVE_AUX
                )
        );
        check(
                !EasyPlaceExtraServer.supportsRelativeAux(listOnly),
                "a list entry without the explicit boolean must not enable the layout"
        );

        CompoundTag ack = EasyPlaceExtraServer.capabilityData(true, "test");
        check(
                ack.getBooleanOr(EasyPlaceExtraServer.CAPABILITY_RELATIVE_AUX, false),
                "the enabled ACK must advertise the relative auxiliary layout"
        );
    }

    private static ListTag listOf(String... capabilities) {
        ListTag list = new ListTag();
        for(String capability : capabilities) {
            list.add(StringTag.valueOf(capability));
        }
        return list;
    }

    private static CompoundTag hello() {
        return hello(false);
    }

    private static CompoundTag hello(boolean relativeAux) {
        CompoundTag data = new CompoundTag();
        data.putInt(
                EasyPlaceExtraServer.NBT_PROTOCOL_VERSION,
                EasyPlaceExtraServer.PROTOCOL_VERSION
        );
        data.putBoolean(EasyPlaceExtraServer.CAPABILITY_EASY_PLACE_V2_EXTRA, true);
        data.putBoolean(EasyPlaceExtraServer.CAPABILITY_PACKET_ANCHOR, true);
        ListTag capabilities = listOf(
                EasyPlaceExtraServer.CAPABILITY_EASY_PLACE_V2_EXTRA,
                EasyPlaceExtraServer.CAPABILITY_PACKET_ANCHOR
        );
        if(relativeAux) {
            data.putBoolean(EasyPlaceExtraServer.CAPABILITY_RELATIVE_AUX, true);
            capabilities.add(
                    StringTag.valueOf(EasyPlaceExtraServer.CAPABILITY_RELATIVE_AUX)
            );
        }
        data.put(EasyPlaceExtraServer.NBT_CAPABILITIES, capabilities);
        return data;
    }
}
