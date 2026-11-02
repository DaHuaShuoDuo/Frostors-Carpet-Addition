package top.frostor.carpet.bigenderchest.menu;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import top.frostor.carpet.bigenderchest.BigEnderChestService;
import top.frostor.carpet.bigenderchest.index.StorageEntry;
import top.frostor.carpet.bigenderchest.page.SortPreference;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class BigEnderChestView {
    private static final String VIRTUAL_MENU_KEY = "FrostorsCarpetAdditionVirtualMenu";
    private static final int VIRTUAL_MENU_MAGIC = 0x464341;
    public static final int VIEW_COLUMNS = 9;
    public static final int VIEW_ROWS = 5;
    public static final int ENTRY_SLOTS = VIEW_COLUMNS * VIEW_ROWS;
    public static final int CONTROL_SLOTS = 9;
    public static final int MENU_SLOTS = ENTRY_SLOTS + CONTROL_SLOTS;

    private final BigEnderChestService service;
    private final SimpleContainer container = new SimpleContainer(MENU_SLOTS);
    private List<StorageEntry> entries = List.of();
    private SortField sortField = SortField.NAME;
    private SortDirection sortDirection = SortDirection.ASCENDING;
    private int viewPage;

    public BigEnderChestView(BigEnderChestService service) {
        this.service = service;
        SortPreference preference = service.sortPreference();
        sortField = "amount".equals(preference.field()) ? SortField.AMOUNT : SortField.NAME;
        sortDirection = "descending".equals(preference.direction())
                ? SortDirection.DESCENDING
                : SortDirection.ASCENDING;
        refresh();
    }

    public SimpleContainer container() {
        return container;
    }

    public static boolean isVirtualMenu(AbstractContainerMenu menu) {
        // Custom client menus such as the creative inventory throw from getType().
        if(menu.slots.size() != BigEnderChestMenu.PLAYER_END) {
            return false;
        }
        CustomData data = menu.getSlot(ENTRY_SLOTS).getItem()
                .get(DataComponents.CUSTOM_DATA);
        return data != null
                && data.copyTag().getIntOr(VIRTUAL_MENU_KEY, 0) == VIRTUAL_MENU_MAGIC;
    }

    public void refresh() {
        List<StorageEntry> refreshed = new ArrayList<>(service.entries());
        Comparator<StorageEntry> comparator = switch(sortField) {
            case NAME -> Comparator.comparing(entry -> entry.template().registryName());
            case AMOUNT -> Comparator.comparingLong(StorageEntry::totalAmount);
        };
        if(sortDirection == SortDirection.ASCENDING) {
            comparator = comparator.thenComparing(entry -> entry.template().registryName());
        }else {
            comparator = comparator.reversed().thenComparing(
                    entry -> entry.template().registryName()
            );
        }
        refreshed.sort(comparator);
        entries = List.copyOf(refreshed);
        viewPage = Math.clamp(viewPage, 0, Math.max(0, pageCount() - 1));

        for(int slot = 0; slot < ENTRY_SLOTS; slot++) {
            StorageEntry entry = entryAtDisplaySlot(slot);
            container.setItem(slot, entry == null ? ItemStack.EMPTY : displayStack(entry));
        }
        refreshControls();
        container.setChanged();
    }

    public StorageEntry entryAtDisplaySlot(int slot) {
        int index = viewPage * ENTRY_SLOTS + slot;
        return slot >= 0 && slot < ENTRY_SLOTS && index < entries.size()
                ? entries.get(index)
                : null;
    }

    public int pageCount() {
        return Math.max(1, Math.ceilDiv(entries.size(), ENTRY_SLOTS));
    }

    public int viewPage() {
        return viewPage;
    }

    public void firstPage() {
        viewPage = 0;
        refresh();
    }

    public void previousPage() {
        viewPage = Math.max(0, viewPage - 1);
        refresh();
    }

    public void nextPage() {
        viewPage = Math.min(pageCount() - 1, viewPage + 1);
        refresh();
    }

    public void lastPage() {
        viewPage = pageCount() - 1;
        refresh();
    }

    public void cycleSortField() {
        sortField = sortField == SortField.NAME ? SortField.AMOUNT : SortField.NAME;
        saveSortPreference();
        refresh();
    }

    public void cycleSortDirection() {
        sortDirection = sortDirection == SortDirection.ASCENDING
                ? SortDirection.DESCENDING
                : SortDirection.ASCENDING;
        saveSortPreference();
        refresh();
    }

    public SortField sortField() {
        return sortField;
    }

    public SortDirection sortDirection() {
        return sortDirection;
    }

    private void saveSortPreference() {
        service.saveSortPreference(
                sortField.name().toLowerCase(Locale.ROOT),
                sortDirection.name().toLowerCase(Locale.ROOT)
        );
    }

    private void refreshControls() {
        int pages = pageCount();
        setControlItem(45, ViewControl.HOME.display(viewPage > 0, pageDetail()));
        setControlItem(46, ViewControl.PREVIOUS.display(viewPage > 0, pageDetail()));
        setControlItem(47, pageItem(pages));
        setControlItem(48, ViewControl.NEXT.display(viewPage + 1 < pages, pageDetail()));
        setControlItem(49, ViewControl.LAST.display(viewPage + 1 < pages, pageDetail()));
        setControlItem(
                50,
                ViewControl.SORT_FIELD.display(
                        true,
                        Component.translatable(
                                "big_ender_chest.sort." + sortField.name().toLowerCase(Locale.ROOT)
                        ).getString()
                )
        );
        setControlItem(
                51,
                ViewControl.SORT_DIRECTION.display(
                        true,
                        Component.translatable(
                                "big_ender_chest.direction."
                                        + sortDirection.name().toLowerCase(Locale.ROOT)
                        ).getString()
                )
        );
        setControlItem(52, ViewControl.REFRESH.display(true, pageDetail()));
        setControlItem(53, ViewControl.RESERVED.placeholder());
    }

    private void setControlItem(int slot, ItemStack stack) {
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.putInt(VIRTUAL_MENU_KEY, VIRTUAL_MENU_MAGIC)
        );
        container.setItem(slot, stack);
    }

    private ItemStack pageItem(int pageCount) {
        ItemStack stack = ViewControl.PAGE.display(true, pageDetail());
        stack.set(
                DataComponents.CUSTOM_NAME,
                Component.translatable(
                        "big_ender_chest.control.page_value",
                        viewPage + 1,
                        pageCount
                )
        );
        return stack;
    }

    private String pageDetail() {
        return Component.translatable(
                "big_ender_chest.control.page_value",
                viewPage + 1,
                pageCount()
        ).getString();
    }

    private static ItemStack displayStack(StorageEntry entry) {
        ItemStack stack = entry.template().template();
        stack.setCount((int) Math.min(entry.totalAmount(), stack.getMaxStackSize()));

        List<Component> lore = new ArrayList<>();
        ItemLore existingLore = stack.get(DataComponents.LORE);
        if(existingLore != null) {
            lore.addAll(existingLore.lines());
        }
        lore.add(Component.translatable(
                "big_ender_chest.entry.amount",
                NumberFormat.getIntegerInstance(Locale.ROOT).format(entry.totalAmount())
        ));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    public enum SortField {
        NAME,
        AMOUNT
    }

    public enum SortDirection {
        ASCENDING,
        DESCENDING
    }
}
