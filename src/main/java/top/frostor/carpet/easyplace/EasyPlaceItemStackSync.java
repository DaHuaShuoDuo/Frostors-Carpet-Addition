package top.frostor.carpet.easyplace;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

/**
 * Applies client-requested item components only for the duration of one placement.
 */
public final class EasyPlaceItemStackSync {
    private static final String DISABLED_SLOTS_TAG = "disabled_slots";
    private static final int SLOT_COUNT = 9;
    private static final ThreadLocal<Integer> PLACE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<SavedComponent> SAVED_COMPONENT = new ThreadLocal<>();

    private EasyPlaceItemStackSync() {
    }

    public static void beginPlacement() {
        PLACE_DEPTH.set(PLACE_DEPTH.get() + 1);
    }

    public static void endPlacement() {
        int depth = PLACE_DEPTH.get() - 1;
        if(depth <= 0) {
            restore();
            PLACE_DEPTH.remove();
        } else {
            PLACE_DEPTH.set(depth);
        }
    }

    public static void applyCrafterSlots(Block block, ItemStack stack, int disabledMask) {
        if(PLACE_DEPTH.get() <= 0
                || !(block instanceof CrafterBlock)
                || stack.getItem() != Items.CRAFTER
                || disabledMask < 0
                || (disabledMask & 0x1ff) == 0) return;

        @SuppressWarnings("unchecked")
        TypedEntityData<BlockEntityType<?>> existing = (TypedEntityData<BlockEntityType<?>>)
                stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if(existing != null && existing.contains(DISABLED_SLOTS_TAG)) return;

        CompoundTag tag = existing != null ? existing.copyTagWithoutId() : new CompoundTag();
        tag.putIntArray(DISABLED_SLOTS_TAG, decodeSlots(disabledMask));
        stack.set(
                DataComponents.BLOCK_ENTITY_DATA,
                TypedEntityData.of((BlockEntityType<?>) BlockEntityTypes.CRAFTER, tag)
        );
        SAVED_COMPONENT.set(new SavedComponent(stack, existing));
    }

    private static void restore() {
        SavedComponent saved = SAVED_COMPONENT.get();
        if(saved != null && saved.stack().getItem() == Items.CRAFTER) {
            if(saved.component() == null) {
                saved.stack().remove(DataComponents.BLOCK_ENTITY_DATA);
            } else {
                saved.stack().set(DataComponents.BLOCK_ENTITY_DATA, saved.component());
            }
        }
        SAVED_COMPONENT.remove();
    }

    static int[] decodeSlots(int disabledMask) {
        int[] slots = new int[Integer.bitCount(disabledMask & 0x1ff)];
        int index = 0;
        for(int slot = 0; slot < SLOT_COUNT; slot++) {
            if((disabledMask & (1 << slot)) != 0) {
                slots[index++] = slot;
            }
        }
        return slots;
    }

    private record SavedComponent(
            ItemStack stack,
            TypedEntityData<BlockEntityType<?>> component
    ) {
    }
}
