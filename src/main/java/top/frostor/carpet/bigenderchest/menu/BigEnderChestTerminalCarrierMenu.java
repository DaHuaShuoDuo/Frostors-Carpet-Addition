package top.frostor.carpet.bigenderchest.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import top.frostor.carpet.bigenderchest.BigEnderChestService;
import top.frostor.carpet.bigenderchest.BigEnderChestSessions;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol;

/**
 * Generic 9x6 carrier for the protocol-driven terminal.
 */
public final class BigEnderChestTerminalCarrierMenu extends ChestMenu {
    private static final int CARRIER_SLOT_END =
            BigEnderChestProtocol.TERMINAL_CARRIER_CONTAINER_SLOTS;
    private static final int PLAYER_SLOT_END =
            BigEnderChestProtocol.TERMINAL_CARRIER_HOTBAR_SLOT_START + 9;

    private final Player owner;
    private final BigEnderChestService service;

    public BigEnderChestTerminalCarrierMenu(
            int syncId,
            Inventory inventory,
            BigEnderChestService service
    ) {
        super(
                MenuType.GENERIC_9x6,
                syncId,
                inventory,
                new SimpleContainer(BigEnderChestProtocol.TERMINAL_CARRIER_CONTAINER_SLOTS),
                6
        );
        this.owner = inventory.player;
        this.service = service;
        BigEnderChestSessions.attachMenu(this, service);
    }

    public BigEnderChestService service() {
        return service;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if(slotId >= 0 && slotId < CARRIER_SLOT_END) {
            return;
        }
        if(slotId >= BigEnderChestProtocol.TERMINAL_PLAYER_SLOT_START
                && slotId < PLAYER_SLOT_END
                && input == ContainerInput.QUICK_MOVE) {
            quickMoveStack(player, slotId);
            return;
        }
        super.clicked(slotId, button, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if(service == null
                || index < BigEnderChestProtocol.TERMINAL_PLAYER_SLOT_START
                || index >= PLAYER_SLOT_END
                || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(index);
        if(!source.hasItem() || !source.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack remaining = source.getItem().copy();
        if(service.insert(remaining)) {
            source.setByPlayer(remaining);
            source.setChanged();
            broadcastChanges();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.index >= CARRIER_SLOT_END && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index >= CARRIER_SLOT_END
                && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if(service != null) {
            BigEnderChestSessions.menuRemoved(player, this);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner
                && (service == null || service.isOpen())
                && (!(player instanceof ServerPlayer serverPlayer)
                || BigEnderChestSessions.isWithinSourceRange(serverPlayer));
    }
}
