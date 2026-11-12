package top.frostor.carpet.easyplace.registry;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

public final class EasyPlaceRegistryTest {
    private static int checks;

    private EasyPlaceRegistryTest() {
    }

    public static void run() {
        checkPriorityOrder();
        checkDuplicateRegistration();
        checkFreeze();
        checkNotFrozenLookup();
        System.out.println("EasyPlaceRegistryTest passed " + checks + " checks");
    }

    private static void checkPriorityOrder() {
        EasyPlaceStateRestorerRegistry registry = new EasyPlaceStateRestorerRegistry();
        registry.register(
                "generic",
                100,
                context -> true,
                context -> context.placementState().setValue(
                        BlockStateProperties.SLAB_TYPE,
                        SlabType.DOUBLE
                )
        );
        registry.register(
                "family",
                500,
                context -> context.block() == Blocks.STONE_SLAB,
                context -> context.placementState().setValue(
                        BlockStateProperties.SLAB_TYPE,
                        SlabType.BOTTOM
                )
        );
        registry.register("exact", 1000, context -> true, context -> context.placementState().setValue(
                BlockStateProperties.SLAB_TYPE,
                SlabType.TOP
        ));
        registry.freeze();

        BlockState result = registry.restore(context());
        check(result != null, "a matching restorer must produce a state");
        check(
                result.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP,
                "higher-priority exact adapters must win over family and generic adapters"
        );
    }

    private static void checkDuplicateRegistration() {
        EasyPlaceStateRestorerRegistry duplicatePriority = new EasyPlaceStateRestorerRegistry();
        duplicatePriority.register("first", 500, context -> true, context -> context.placementState());
        checkThrows(
                () -> duplicatePriority.register("second", 500, context -> true, context -> context.placementState()),
                "duplicate priorities must be rejected"
        );

        EasyPlaceStateRestorerRegistry duplicateId = new EasyPlaceStateRestorerRegistry();
        duplicateId.register("same", 500, context -> true, context -> context.placementState());
        checkThrows(
                () -> duplicateId.register("same", 400, context -> true, context -> context.placementState()),
                "duplicate adapter ids must be rejected"
        );
    }

    private static void checkFreeze() {
        EasyPlaceStateRestorerRegistry registry = new EasyPlaceStateRestorerRegistry();
        registry.register("legacy", 0, context -> true, context -> context.placementState());
        registry.freeze();
        check(registry.isFrozen(), "the registry must expose its frozen state");
        checkThrows(
                () -> registry.register("late", 100, context -> true, context -> context.placementState()),
                "registration after freezing must be rejected"
        );
    }

    private static void checkNotFrozenLookup() {
        EasyPlaceStateRestorerRegistry registry = new EasyPlaceStateRestorerRegistry();
        registry.register("legacy", 0, context -> true, context -> context.placementState());
        checkThrows(
                () -> registry.restore(context()),
                "lookups before freezing must be rejected"
        );
    }

    private static EasyPlaceRestoreContext context() {
        return new EasyPlaceRestoreContext(
                Blocks.STONE_SLAB,
                Blocks.STONE_SLAB.defaultBlockState(),
                null,
                0,
                0
        );
    }

    private static void checkThrows(Runnable action, String message) {
        checks++;
        try {
            action.run();
        } catch(IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }
}
