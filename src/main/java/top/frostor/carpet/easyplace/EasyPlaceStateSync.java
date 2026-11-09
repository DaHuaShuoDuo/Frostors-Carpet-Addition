package top.frostor.carpet.easyplace;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;
import top.frostor.carpet.easyplace.adapter.directional.DoorStateRestorer;
import top.frostor.carpet.easyplace.adapter.directional.FenceGateStateRestorer;
import top.frostor.carpet.easyplace.adapter.directional.GenericDirectionalStateRestorer;
import top.frostor.carpet.easyplace.adapter.directional.StairStateRestorer;
import top.frostor.carpet.easyplace.adapter.directional.TrapDoorStateRestorer;
import top.frostor.carpet.easyplace.adapter.attachment.FaceAttachedStateRestorer;
import top.frostor.carpet.easyplace.adapter.legacy.LegacyStateRestorer;
import top.frostor.carpet.easyplace.adapter.multiface.MultiFaceStateRestorer;
import top.frostor.carpet.easyplace.adapter.sign.HangingSignStateRestorer;
import top.frostor.carpet.easyplace.adapter.sign.SignStateRestorer;
import top.frostor.carpet.easyplace.adapter.special.AdministrativeStateRestorer;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorerRegistry;

public final class EasyPlaceStateSync {
    private static final Direction[] VERTICAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final EasyPlaceStateRestorerRegistry REGISTRY = createRegistry();

    private EasyPlaceStateSync() {
    }

    public static @Nullable BlockState apply(Block block, BlockState state, BlockPlaceContext context, int rawValue) {
        return REGISTRY.restore(new EasyPlaceRestoreContext(block, state, context, rawValue));
    }

    public static @Nullable BlockState applyStandingOrWall(
            Block standingBlock,
            Block wallBlock,
            BlockState standingState,
            BlockPlaceContext context,
            int rawValue
    ) {
        boolean wall = (rawValue & 1) != 0;
        Direction facing = VERTICAL_DIRECTIONS[(rawValue >>> 1) & 0x3];
        int stateValue = rawValue >>> 3;
        if(standingBlock instanceof HangingSignBlock && (rawValue & 0x4) != 0) {
            stateValue |= 0x10;
        }
        Block block = wall ? wallBlock : standingBlock;
        BlockState state = standingState.is(block)
                ? standingState
                : block.getStateForPlacement(context);
        if(state == null && wall) state = wallBlock.defaultBlockState();
        if(state == null) return null;
        state = setFirstDirection(state, facing);
        return apply(block, state, context, stateValue);
    }

    static BlockState applyGeneric(BlockState state, int protocolValue, BlockPlaceContext context) {
        return LegacyEasyPlaceStateSync.applyGeneric(state, protocolValue, context);
    }

    static @Nullable BlockState validateIncremental(
            BlockState state,
            Property<Integer> property,
            int requestedMaximum
    ) {
        return LegacyEasyPlaceStateSync.validateIncremental(state, property, requestedMaximum);
    }

    private static EasyPlaceStateRestorerRegistry createRegistry() {
        EasyPlaceStateRestorerRegistry registry = new EasyPlaceStateRestorerRegistry();
        registry.register("door", 700, DoorStateRestorer::matches, new DoorStateRestorer());
        registry.register("trapdoor", 690, TrapDoorStateRestorer::matches, new TrapDoorStateRestorer());
        registry.register("fence_gate", 680, FenceGateStateRestorer::matches, new FenceGateStateRestorer());
        registry.register("stair", 670, StairStateRestorer::matches, new StairStateRestorer());
        registry.register("sign", 660, SignStateRestorer::matches, new SignStateRestorer());
        registry.register(
                "hanging_sign",
                659,
                HangingSignStateRestorer::matches,
                new HangingSignStateRestorer()
        );
        registry.register(
                "face_attached",
                658,
                FaceAttachedStateRestorer::matches,
                new FaceAttachedStateRestorer()
        );
        registry.register(
                "multiface",
                657,
                MultiFaceStateRestorer::matches,
                new MultiFaceStateRestorer()
        );
        registry.register(
                "administrative",
                650,
                AdministrativeStateRestorer::matches,
                new AdministrativeStateRestorer()
        );
        registry.register(
                "generic_directional",
                100,
                GenericDirectionalStateRestorer::matches,
                new GenericDirectionalStateRestorer()
        );
        registry.register("legacy", 0, context -> true, new LegacyStateRestorer());
        registry.freeze();
        return registry;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setFirstDirection(BlockState state, Direction direction) {
        for(Property<?> property : state.getProperties()) {
            if(property.getPossibleValues().stream().anyMatch(value -> value instanceof Direction)
                    && property.getPossibleValues().contains(direction)) {
                return state.setValue((Property) property, direction);
            }
        }
        return state;
    }
}
