package top.frostor.carpet.easyplace;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import top.frostor.carpet.easyplace.adapter.observer.ObserverStateRestorer;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;

public final class EasyPlaceObserverStateTest extends EasyPlaceTestSupport {
    private EasyPlaceObserverStateTest() {
    }

    public static int run() {
        EasyPlaceObserverStateTest test = new EasyPlaceObserverStateTest();
        test.checkFacingRoundTrip();
        test.checkInvalidRawValues();
        test.checkRegistryOwnership();
        return test.checks();
    }

    private void checkFacingRoundTrip() {
        for(Direction direction : Direction.values()) {
            BlockState restored = EasyPlaceStateSync.apply(
                    Blocks.OBSERVER,
                    Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.POWERED, true),
                    null,
                    direction.get3DDataValue()
            );
            check(
                    restored != null && restored.getValue(ObserverBlock.FACING) == direction,
                    "observer facing must round trip"
            );
            check(
                    restored != null && !restored.getValue(ObserverBlock.POWERED),
                    "easy-placed observers must not restore a powered pulse"
            );
        }
    }

    private void checkInvalidRawValues() {
        for(int rawValue = Direction.values().length; rawValue <= 0x7f; rawValue++) {
            check(
                    EasyPlaceStateSync.apply(
                            Blocks.OBSERVER,
                            Blocks.OBSERVER.defaultBlockState(),
                            null,
                            rawValue
                    ) == null,
                    "observer raw value " + rawValue + " must be rejected"
            );
        }
    }

    private void checkRegistryOwnership() {
        BlockState state = Blocks.OBSERVER.defaultBlockState();
        check(
                ObserverStateRestorer.matches(new EasyPlaceRestoreContext(
                        Blocks.OBSERVER,
                        state,
                        null,
                        0,
                        0
                )),
                "the observer restorer must own ObserverBlock"
        );
    }
}
