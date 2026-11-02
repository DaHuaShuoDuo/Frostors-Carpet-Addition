package top.frostor.carpet.bigenderchest.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.frostor.carpet.FrostorSettings;
import top.frostor.carpet.bigenderchest.BigEnderChestSessions;

/**
 * Routes direct vanilla ender-chest menu construction through FCA. QuickShulker opens this path
 * directly instead of emitting a block-use interaction.
 */
@Mixin(ChestMenu.class)
public abstract class ChestMenuMixin {
    @ModifyReturnValue(
            method = "threeRows(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;)Lnet/minecraft/world/inventory/ChestMenu;",
            at = @At("RETURN")
    )
    private static ChestMenu playerladder$replaceQuickShulkerEnderChestMenu(
            ChestMenu original,
            int syncId,
            Inventory inventory,
            Container container
    ) {
        if(!FrostorSettings.bigEnderChest
                || !(container instanceof PlayerEnderChestContainer)
                || !(inventory.player instanceof ServerPlayer)) {
            return original;
        }
        ChestMenu replacement = BigEnderChestSessions.createExternalMenu(syncId, inventory);
        return replacement == null ? original : replacement;
    }
}
