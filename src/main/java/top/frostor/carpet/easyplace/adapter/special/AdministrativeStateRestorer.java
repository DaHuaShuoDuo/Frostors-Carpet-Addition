package top.frostor.carpet.easyplace.adapter.special;

import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.StructureMode;
import top.frostor.carpet.easyplace.EasyPlaceStateSupport;
import top.frostor.carpet.easyplace.registry.EasyPlaceRestoreContext;
import top.frostor.carpet.easyplace.registry.EasyPlaceStateRestorer;

public final class AdministrativeStateRestorer implements EasyPlaceStateRestorer {
    public static boolean matches(EasyPlaceRestoreContext context) {
        return context.block() instanceof CommandBlock
                || context.block() instanceof JigsawBlock
                || context.block() instanceof LightBlock
                || context.block() instanceof NoteBlock
                || context.block() instanceof StructureBlock;
    }

    @Override
    public BlockState restore(EasyPlaceRestoreContext context) {
        BlockState state = context.placementState();
        int rawValue = context.rawValue();
        if(context.block() instanceof CommandBlock) {
            BlockState generic = EasyPlaceStateSupport.applyGeneric(
                    state,
                    rawValue,
                    context.placeContext()
            );
            return EasyPlaceStateSupport.replaceSlot(
                    generic,
                    CommandBlock.CONDITIONAL,
                    (rawValue & 0x10) != 0
            );
        }
        if(context.block() instanceof JigsawBlock) {
            FrontAndTop[] values = FrontAndTop.values();
            return EasyPlaceStateSupport.replaceSlot(
                    state,
                    JigsawBlock.ORIENTATION,
                    values[(rawValue & 0xf) % values.length]
            );
        }
        if(context.block() instanceof LightBlock) {
            return EasyPlaceStateSupport.replaceSlot(state, LightBlock.LEVEL, rawValue & 0xf);
        }
        if(context.block() instanceof NoteBlock) {
            return EasyPlaceStateSupport.replaceSlot(state, NoteBlock.NOTE, (rawValue & 0x1f) % 25);
        }
        return EasyPlaceStateSupport.replaceSlot(
                state,
                StructureBlock.MODE,
                StructureMode.values()[rawValue & 0x3]
        );
    }
}
