package top.frostor.carpet.bigenderchest.compat;

import net.kyrptonaught.quickshulker.QuickShulkerMod;
import net.kyrptonaught.quickshulker.api.QuickOpenableRegistry;
import net.kyrptonaught.quickshulker.api.QuickShulkerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.EnderChestBlock;
import top.frostor.carpet.bigenderchest.BigEnderChestSessions;

/**
 * Uses Quick Shulker's public provider API so both its keybinding and hand-use paths
 * reach the same server-authoritative big ender chest menu.
 */
public final class QuickShulkerIntegration {
    private static boolean registered;

    private QuickShulkerIntegration() {
    }

    public static void register() {
        if(registered || !QuickShulkerMod.getConfig().quickEChest) return;
        registered = true;
        registerProvider();
    }

    static void registerProvider() {
        new QuickOpenableRegistry.Builder(new QuickShulkerData.QuickEnderData())
                .setItem(EnderChestBlock.class)
                .supportsBundleing(true)
                .ignoreSingleStackCheck(true)
                .getSound((stack, isOpenSound) -> isOpenSound
                        ? SoundEvents.ENDER_CHEST_OPEN
                        : SoundEvents.ENDER_CHEST_CLOSE)
                .setOpenAction(QuickShulkerIntegration::open)
                .register();
    }

    private static void open(Player player, net.minecraft.world.item.ItemStack stack) {
        if(player instanceof ServerPlayer serverPlayer
                && BigEnderChestSessions.open(serverPlayer)) {
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (syncId, inventory, ignored) -> ChestMenu.threeRows(
                        syncId,
                        inventory,
                        player.getEnderChestInventory()
                ),
                Component.translatable("container.enderchest")
        ));
    }
}
