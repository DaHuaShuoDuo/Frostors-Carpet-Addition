package top.frostor.carpet.bigenderchest.menu;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public enum ViewControl {
    HOME(0, Items.ENDER_EYE, "home"),
    PREVIOUS(1, Items.ARROW, "previous"),
    PAGE(2, Items.BOOK, "page"),
    NEXT(3, Items.ARROW, "next"),
    LAST(4, Items.ENDER_PEARL, "last"),
    SORT_FIELD(5, Items.HOPPER, "sort_field"),
    SORT_DIRECTION(6, Items.COMPASS, "sort_direction"),
    REFRESH(7, Items.CLOCK, "refresh"),
    RESERVED(8, Items.STAINED_GLASS_PANE.gray(), "reserved");

    private static final ViewControl[] BY_OFFSET = values();
    private final int offset;
    private final Item icon;
    private final String translationKey;

    ViewControl(int offset, Item icon, String translationKey) {
        this.offset = offset;
        this.icon = icon;
        this.translationKey = translationKey;
    }

    public static ViewControl at(int offset) {
        return offset >= 0 && offset < BY_OFFSET.length ? BY_OFFSET[offset] : null;
    }

    public ItemStack display(boolean enabled, String detail) {
        ItemStack stack = enabled ? new ItemStack(icon) : new ItemStack(Items.GLASS_PANE);
        stack.set(
                DataComponents.CUSTOM_NAME,
                Component.translatable("big_ender_chest.control." + translationKey)
                        .withStyle(enabled ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
        );
        stack.set(
                DataComponents.LORE,
                new ItemLore(List.of(
                        Component.translatable(
                                enabled
                                        ? "big_ender_chest.control.click"
                                        : "big_ender_chest.control.disabled"
                        ).withStyle(enabled ? ChatFormatting.GRAY : ChatFormatting.DARK_RED),
                        Component.literal(detail).withStyle(ChatFormatting.DARK_GRAY)
                ))
        );
        return stack;
    }

    public ItemStack placeholder() {
        return new ItemStack(icon);
    }
}
