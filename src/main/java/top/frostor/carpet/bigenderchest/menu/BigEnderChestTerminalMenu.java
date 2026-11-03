package top.frostor.carpet.bigenderchest.menu;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import top.frostor.carpet.bigenderchest.BigEnderChestService;
import top.frostor.carpet.bigenderchest.BigEnderChestSessions;
import top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol;

public final class BigEnderChestTerminalMenu extends AbstractContainerMenu {
    public static final int PLAYER_SLOT_COUNT = 36;
    private static final MenuType<BigEnderChestTerminalMenu> MENU_TYPE = Registry.register(
            BuiltInRegistries.MENU,
            BigEnderChestProtocol.TERMINAL_MENU_ID,
            new ExtendedMenuType<BigEnderChestTerminalMenu, Unit>(
                    (syncId, inventory, ignored) ->
                            new BigEnderChestTerminalMenu(syncId, inventory),
                    StreamCodec.unit(Unit.INSTANCE)
            )
    );

    private final Player owner;
    private final BigEnderChestService service;

    public BigEnderChestTerminalMenu(int syncId, Inventory inventory) {
        this(syncId, inventory, null);
    }

    public BigEnderChestTerminalMenu(
            int syncId,
            Inventory inventory,
            BigEnderChestService service
    ) {
        super(MENU_TYPE, syncId);
        this.owner = inventory.player;
        this.service = service;
        addStandardInventorySlots(inventory, 8, 84);
        if(service != null) {
            BigEnderChestSessions.attachMenu(this, service);
        }
    }

    public static MenuType<BigEnderChestTerminalMenu> menuType() {
        return MENU_TYPE;
    }

    public BigEnderChestService service() {
        return service;
    }

    public ItemStack moveToPlayerInventory(ItemStack stack) {
        moveItemStackTo(stack, 0, PLAYER_SLOT_COUNT, true);
        return stack;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if(service == null || index < 0 || index >= slots.size()) {
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
