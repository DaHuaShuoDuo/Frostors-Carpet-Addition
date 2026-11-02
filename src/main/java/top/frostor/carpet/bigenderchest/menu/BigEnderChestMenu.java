package top.frostor.carpet.bigenderchest.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import top.frostor.carpet.bigenderchest.BigEnderChestService;
import top.frostor.carpet.bigenderchest.BigEnderChestSessions;
import top.frostor.carpet.bigenderchest.index.StorageEntry;

public final class BigEnderChestMenu extends ChestMenu {
    public static final int PLAYER_MAIN_START = 54;
    public static final int PLAYER_HOTBAR_START = 81;
    public static final int PLAYER_END = 90;

    private final BigEnderChestService service;
    private final BigEnderChestView view;
    private VirtualAction pendingVirtualAction;
    private int pendingVirtualActionCount;
    private int rejectedVirtualActionCount;

    public BigEnderChestMenu(
            int syncId,
            Inventory playerInventory,
            BigEnderChestService service,
            BigEnderChestView view
    ) {
        super(MenuType.GENERIC_9x6, syncId, playerInventory, view.container(), 6);
        this.service = service;
        this.view = view;
        BigEnderChestSessions.attachMenu(this, service);
        this.view.refresh();
    }

    public BigEnderChestService service() {
        return service;
    }

    public BigEnderChestView view() {
        return view;
    }

    public int rejectedVirtualActionCount() {
        return rejectedVirtualActionCount;
    }

    public ItemStack moveToPlayerInventory(ItemStack stack) {
        moveItemStackTo(stack, PLAYER_MAIN_START, PLAYER_END, true);
        return stack;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if(slotId == SLOT_CLICKED_OUTSIDE) {
            super.clicked(slotId, button, input, player);
            return;
        }

        if(slotId >= 0 && slotId < BigEnderChestView.MENU_SLOTS) {
            handleProtectedClick(slotId, button, input);
            return;
        }

        if(slotId >= PLAYER_MAIN_START && slotId < PLAYER_END) {
            if(input == ContainerInput.QUICK_MOVE) {
                quickMoveStack(player, slotId);
                return;
            }
        }
        super.clicked(slotId, button, input, player);
    }

    public void handleProtectedClick(int slotId, int button, ContainerInput input) {
        if(slotId >= BigEnderChestView.ENTRY_SLOTS
                && slotId < BigEnderChestView.MENU_SLOTS) {
            handleControlClick(slotId, button, input);
        }else if(slotId >= 0 && slotId < BigEnderChestView.ENTRY_SLOTS) {
            handleViewClick(slotId, button, input);
        }else {
            rejectVirtualAction();
        }
    }

    private void handleControlClick(int slotId, int button, ContainerInput input) {
        ViewControl control = ViewControl.at(slotId - BigEnderChestView.ENTRY_SLOTS);
        if(input != ContainerInput.PICKUP
                || button != 0
                || !getCarried().isEmpty()
                || control == null) {
            rejectVirtualAction();
            return;
        }
        queueVirtualAction(slotId, button, input);
    }

    private void handleViewClick(int slotId, int button, ContainerInput input) {
        boolean allowed = (input == ContainerInput.PICKUP && button == 0)
                || (input == ContainerInput.QUICK_MOVE && getCarried().isEmpty())
                || (input == ContainerInput.THROW && (button == 0 || button == 1));
        if(allowed) {
            queueVirtualAction(slotId, button, input);
        }else {
            rejectVirtualAction();
        }
    }

    private void queueVirtualAction(int slotId, int button, ContainerInput input) {
        pendingVirtualActionCount++;
        if(pendingVirtualActionCount == 1) {
            pendingVirtualAction = new VirtualAction(slotId, button, input);
        }else {
            pendingVirtualAction = null;
        }
    }

    public void tickVirtualActions() {
        VirtualAction action = pendingVirtualAction;
        int actionCount = pendingVirtualActionCount;
        pendingVirtualAction = null;
        pendingVirtualActionCount = 0;
        if(actionCount > 1) {
            rejectVirtualAction();
            return;
        }
        if(action == null) return;

        if(action.slotId() >= BigEnderChestView.ENTRY_SLOTS) {
            executeControlClick(action.slotId() - BigEnderChestView.ENTRY_SLOTS);
        }else if(action.input() == ContainerInput.THROW) {
            dropFromView(action.slotId(), action.button());
        }else if(action.input() == ContainerInput.QUICK_MOVE) {
            quickMoveStack(service.owner(), action.slotId());
        }else if(getCarried().isEmpty()) {
            takeIntoCarried(action.slotId());
        }else {
            insertCarried();
        }
    }

    private void dropFromView(int slotId, int button) {
        StorageEntry entry = view.entryAtDisplaySlot(slotId);
        if(entry == null) return;
        ItemStack dropped = button == 0
                ? service.takeOne(entry.template())
                : service.takeStack(entry.template());
        if(dropped.isEmpty()) return;
        Player owner = service.owner();
        Vec3 look = owner.getLookAngle();
        ItemEntity itemEntity = new ItemEntity(
                owner.level(),
                owner.getX(),
                owner.getEyeY() - 0.3,
                owner.getZ(),
                dropped
        );
        itemEntity.setDeltaMovement(look.x * 0.3, 0.1, look.z * 0.3);
        itemEntity.setThrower(owner);
        itemEntity.setDefaultPickUpDelay();
        owner.level().addFreshEntity(itemEntity);
        view.refresh();
        broadcastChanges();
    }

    private void executeControlClick(int controlOffset) {
        ViewControl control = ViewControl.at(controlOffset);
        if(control == null) {
            rejectVirtualAction();
            return;
        }
        switch(control) {
            case HOME -> {
                if(view.viewPage() > 0) view.firstPage();
            }
            case PREVIOUS -> {
                if(view.viewPage() > 0) view.previousPage();
            }
            case PAGE -> {
            }
            case NEXT -> {
                if(view.viewPage() + 1 < view.pageCount()) view.nextPage();
            }
            case LAST -> {
                if(view.viewPage() + 1 < view.pageCount()) view.lastPage();
            }
            case SORT_FIELD -> view.cycleSortField();
            case SORT_DIRECTION -> view.cycleSortDirection();
            case REFRESH -> view.refresh();
            case RESERVED -> {
            }
        }
        broadcastChanges();
    }

    private void insertCarried() {
        ItemStack carried = getCarried();
        if(carried.isEmpty()) return;
        if(service.insert(carried)) {
            setCarried(carried);
            view.refresh();
            broadcastChanges();
        }
    }

    private void takeIntoCarried(int slotId) {
        if(!getCarried().isEmpty()) return;
        StorageEntry entry = view.entryAtDisplaySlot(slotId);
        if(entry == null) return;
        ItemStack taken = service.takeStack(entry.template());
        if(taken.isEmpty()) return;
        setCarried(taken);
        view.refresh();
        broadcastChanges();
    }

    private void rejectVirtualAction() {
        rejectedVirtualActionCount++;
        pendingVirtualAction = null;
        pendingVirtualActionCount = 0;
        resetQuickCraft();
        incrementStateId();
        view.refresh();
        broadcastFullState();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if(index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if(!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY;

        if(index < BigEnderChestView.ENTRY_SLOTS) {
            StorageEntry entry = view.entryAtDisplaySlot(index);
            if(entry == null) return ItemStack.EMPTY;
            ItemStack taken = service.takeStack(entry.template());
            if(taken.isEmpty()) return ItemStack.EMPTY;
            moveItemStackTo(taken, PLAYER_MAIN_START, PLAYER_END, true);
            if(!taken.isEmpty() && !service.insert(taken)) {
                player.getInventory().add(taken);
            }
            view.refresh();
            broadcastChanges();
            return ItemStack.EMPTY;
        }

        if(index >= PLAYER_MAIN_START && index < PLAYER_END) {
            ItemStack sourceStack = source.getItem();
            ItemStack remaining = sourceStack.copy();
            if(service.insert(remaining)) {
                source.setByPlayer(remaining);
                source.setChanged();
                view.refresh();
                broadcastChanges();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.container != view.container() && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != view.container()
                && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void removed(Player player) {
        resetQuickCraft();
        super.removed(player);
        BigEnderChestSessions.menuRemoved(player, this);
    }

    @Override
    public boolean stillValid(Player player) {
        return player == service.owner() && service.isOpen();
    }

    private record VirtualAction(int slotId, int button, ContainerInput input) {
    }
}
