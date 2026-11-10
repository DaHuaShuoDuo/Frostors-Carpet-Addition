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
import top.frostor.carpet.easyplace.adapter.directional.StairStateRestorer;
import top.frostor.carpet.easyplace.adapter.directional.TrapDoorStateRestorer;
import top.frostor.carpet.easyplace.adapter.attachment.FaceAttachedStateRestorer;
import top.frostor.carpet.easyplace.adapter.cake.CakeStateRestorer;
import top.frostor.carpet.easyplace.adapter.campfire.CampfireStateRestorer;
import top.frostor.carpet.easyplace.adapter.container.ChestStateRestorer;
import top.frostor.carpet.easyplace.adapter.container.BedStateRestorer;
import top.frostor.carpet.easyplace.adapter.copper.CopperBulbStateRestorer;
import top.frostor.carpet.easyplace.adapter.copper.CopperGolemStateRestorer;
import top.frostor.carpet.easyplace.adapter.crafter.CrafterStateRestorer;
import top.frostor.carpet.easyplace.adapter.daylight.DaylightDetectorStateRestorer;
import top.frostor.carpet.easyplace.adapter.generic.GenericFallbackStateRestorer;
import top.frostor.carpet.easyplace.adapter.lantern.LanternStateRestorer;
import top.frostor.carpet.easyplace.adapter.multiface.MultiFaceStateRestorer;
import top.frostor.carpet.easyplace.adapter.mushroom.MushroomStateRestorer;
import top.frostor.carpet.easyplace.adapter.observer.ObserverStateRestorer;
import top.frostor.carpet.easyplace.adapter.quantity.QuantityStateRestorer;
import top.frostor.carpet.easyplace.adapter.rail.DetectorRailStateRestorer;
import top.frostor.carpet.easyplace.adapter.rail.RailStateRestorer;
import top.frostor.carpet.easyplace.adapter.redstone.RedstoneStateRestorer;
import top.frostor.carpet.easyplace.adapter.special.BellStateRestorer;
import top.frostor.carpet.easyplace.adapter.sign.HangingSignStateRestorer;
import top.frostor.carpet.easyplace.adapter.sign.SignStateRestorer;
import top.frostor.carpet.easyplace.adapter.special.AdministrativeStateRestorer;
import top.frostor.carpet.easyplace.adapter.wall.WallStateRestorer;
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
        return apply(block, state, context, rawValue, 0);
    }

    public static @Nullable BlockState apply(
            Block block,
            BlockState state,
            BlockPlaceContext context,
            int rawValue,
            int auxiliaryValue
    ) {
        return REGISTRY.restore(
                new EasyPlaceRestoreContext(block, state, context, rawValue, auxiliaryValue)
        );
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
        state = EasyPlaceStateSupport.setFirstDirection(state, facing);
        return apply(block, state, context, stateValue, 0);
    }

    static BlockState applyGeneric(BlockState state, int protocolValue, BlockPlaceContext context) {
        return EasyPlaceStateSupport.applyGeneric(state, protocolValue, context);
    }

    static @Nullable BlockState validateIncremental(
            BlockState state,
            Property<Integer> property,
            int requestedMaximum
    ) {
        return EasyPlaceStateSupport.validateIncremental(state, property, requestedMaximum);
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
                "quantity",
                656,
                QuantityStateRestorer::matches,
                new QuantityStateRestorer()
        );
        registry.register("rail", 655, RailStateRestorer::matches, new RailStateRestorer());
        registry.register("wall", 652, WallStateRestorer::matches, new WallStateRestorer());
        registry.register(
                "observer",
                651,
                ObserverStateRestorer::matches,
                new ObserverStateRestorer()
        );
        registry.register(
                "mushroom",
                654,
                MushroomStateRestorer::matches,
                new MushroomStateRestorer()
        );
        registry.register(
                "container",
                653,
                ChestStateRestorer::matches,
                new ChestStateRestorer()
        );
        registry.register(
                "redstone",
                649,
                RedstoneStateRestorer::matches,
                new RedstoneStateRestorer()
        );
        registry.register("bell", 648, BellStateRestorer::matches, new BellStateRestorer());
        registry.register(
                "campfire",
                647,
                CampfireStateRestorer::matches,
                new CampfireStateRestorer()
        );
        registry.register("cake", 646, CakeStateRestorer::matches, new CakeStateRestorer());
        registry.register(
                "copper_bulb",
                645,
                CopperBulbStateRestorer::matches,
                new CopperBulbStateRestorer()
        );
        registry.register(
                "copper_golem",
                644,
                CopperGolemStateRestorer::matches,
                new CopperGolemStateRestorer()
        );
        registry.register("crafter", 643, CrafterStateRestorer::matches, new CrafterStateRestorer());
        registry.register(
                "daylight_detector",
                642,
                DaylightDetectorStateRestorer::matches,
                new DaylightDetectorStateRestorer()
        );
        registry.register(
                "detector_rail",
                641,
                DetectorRailStateRestorer::matches,
                new DetectorRailStateRestorer()
        );
        registry.register("lantern", 640, LanternStateRestorer::matches, new LanternStateRestorer());
        registry.register("bed", 639, BedStateRestorer::matches, new BedStateRestorer());
        registry.register(
                "administrative",
                650,
                AdministrativeStateRestorer::matches,
                new AdministrativeStateRestorer()
        );
        registry.register(
                "generic_fallback",
                50,
                GenericFallbackStateRestorer::matches,
                new GenericFallbackStateRestorer()
        );
        registry.freeze();
        return registry;
    }
}
